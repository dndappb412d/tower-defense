package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.ai.PathCheck;
import dev.vadim.heartguard.ai.StructureDamage;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.mob.MobDef;
import dev.vadim.heartguard.mob.MobFactory;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.tower.TowerService;
import dev.vadim.heartguard.tower.TowerService.RepairResult;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hybrid pathing: heavy mobs break player walls and attack towers when the way to the Heart is blocked, never touch the
 * map's own walls, sappers blow up on towers, walls and the Heart, and towers can be hurt and repaired by hand.
 * All of it runs in a 24x16x11 test box with a stone brick border.
 */
public class PathingGameTest {
	private static final String BOX = "heartguard:flight_arena";
	private static final Identifier BRUTE = Identifier.fromNamespaceAndPath("heartguard", "brute");
	private static final Identifier RUNNER = Identifier.fromNamespaceAndPath("heartguard", "runner");
	private static final Identifier SAPPER = Identifier.fromNamespaceAndPath("heartguard", "sapper");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	// --- the box ---

	/** A stone floor with a stone brick border three high, so nothing leaves, and the Heart at the east end. */
	private static BlockPos heartInBox(GameTestHelper context) {
		TestArena.forceLoad(context, new BlockPos(-2, 0, -2), new BlockPos(25, 12, 12));
		for (int x = 0; x <= 23; x++) {
			for (int z = 0; z <= 10; z++) {
				context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x <= 23; x++) {
				context.setBlock(new BlockPos(x, y, 0), Blocks.STONE_BRICKS);
				context.setBlock(new BlockPos(x, y, 10), Blocks.STONE_BRICKS);
			}
			for (int z = 0; z <= 10; z++) {
				context.setBlock(new BlockPos(0, y, z), Blocks.STONE_BRICKS);
				context.setBlock(new BlockPos(23, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos heart = new BlockPos(20, 1, 5);
		context.setBlock(heart, ModBlocks.HEART);
		return heart;
	}

	/** A wall of the given block across the whole box at {@code x}, joined to the border so there is no way round. */
	private static void wallAcross(GameTestHelper context, Block block, int x) {
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z <= 9; z++) {
				context.setBlock(new BlockPos(x, y, z), block);
			}
		}
	}

	private static int count(GameTestHelper context, Block block, int x) {
		int n = 0;
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z <= 9; z++) {
				if (context.getLevel().getBlockState(context.absolutePos(new BlockPos(x, y, z))).is(block)) {
					n++;
				}
			}
		}
		return n;
	}

	private static String holes(GameTestHelper context, int x) {
		StringBuilder out = new StringBuilder();
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z <= 9; z++) {
				if (!context.getLevel().getBlockState(context.absolutePos(new BlockPos(x, y, z))).is(Blocks.COBBLESTONE)) {
					out.append("(").append(y).append(",").append(z).append(")");
				}
			}
		}
		return out.toString();
	}

	private static Mob spawn(GameTestHelper context, Identifier id, BlockPos relative, FakeHeartTarget target, int reward) {
		return (Mob) MobFactory.spawnWaveMob(context.getLevel(), id, context.absolutePos(relative), reward, target);
	}

	private static FakeHeartTarget targetAt(GameTestHelper context, BlockPos heartRelative) {
		return new FakeHeartTarget(context.getLevel(), context.absolutePos(heartRelative));
	}

	// --- the data ---

	@GameTest
	public void structureDamageAndRepairNumbersAreData(GameTestHelper context) {
		MobDef runner = MobDef.get(RUNNER);
		MobDef brute = MobDef.get(BRUTE);
		MobDef sapper = MobDef.get(SAPPER);
		expect(context, runner.structureDamage() == 10, "structure damage defaults to 10, was " + runner.structureDamage());
		expect(context, brute.structureDamage() > runner.structureDamage() && brute.has(MobDef.Trait.BREAKS_WALLS), "brutes break walls and hit structures harder");
		expect(context, sapper.base() == EntityType.CREEPER && sapper.has(MobDef.Trait.EXPLODES), "the sapper is a creeper that explodes");
		expect(context, sapper.structureDamage() >= TowerDefs.get(TowerDefs.ARROW).hp(), "one sapper is enough to destroy a cheap tower");
		expect(context, MobDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{ \"base\": \"minecraft:husk\", \"health\": 5, \"structure_damage\": 0 }")).result().isEmpty(), "zero structure damage is rejected");

		TowerDef arrow = TowerDefs.get(TowerDefs.ARROW);
		expect(context, arrow.repairHp() == 10 && arrow.repairCost() == 5, "towers repair 10 HP for 5 coins by default");
		var custom = TowerDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
			"{ \"kind\": \"single_target\", \"cost\": 50, \"hp\": 40, \"repair_hp\": 20, \"repair_cost\": 0, \"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }")).result().orElseThrow();
		expect(context, custom.repairHp() == 20 && custom.repairCost() == 0, "a tower file can set its own repair numbers, including free repair");
		expect(context, TowerDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
			"{ \"kind\": \"single_target\", \"cost\": 50, \"hp\": 40, \"repair_hp\": 0, \"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }")).result().isEmpty(), "repairing nothing is rejected");
		context.succeed();
	}

	@GameTest
	public void onlyPlayerWallsAreBreakable(GameTestHelper context) {
		expect(context, StructureDamage.isBreakableWall(Blocks.COBBLESTONE.defaultBlockState()), "cobblestone, which the shop sells, is breakable");
		expect(context, StructureDamage.isBreakableWall(Blocks.OAK_PLANKS.defaultBlockState()), "so are planks");
		expect(context, !StructureDamage.isBreakableWall(Blocks.STONE_BRICKS.defaultBlockState()), "the map's stone brick walls are not");
		expect(context, !StructureDamage.isBreakableWall(Blocks.STONE.defaultBlockState()) && !StructureDamage.isBreakableWall(Blocks.GRASS_BLOCK.defaultBlockState()), "and neither is terrain");
		expect(context, !StructureDamage.isBreakableWall(ModBlocks.HEART.defaultBlockState()), "and certainly not the Heart");
		context.succeed();
	}

	// --- towers: damage and repair ---

	private static TowerBlockEntity tower(GameTestHelper context, BlockPos relative, Identifier type) {
		ServerLevel level = context.getLevel();
		BlockPos pos = context.absolutePos(relative);
		level.setBlock(pos, ModBlocks.towerBlock(type).defaultBlockState(), Block.UPDATE_ALL);
		TowerBlockEntity entity = (TowerBlockEntity) level.getBlockEntity(pos);
		entity.initialize(UUID.randomUUID(), TowerDefs.get(type));
		return entity;
	}

	@GameTest
	public void towersLoseHpAndFallAtZero(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		TowerBlockEntity arrow = tower(context, new BlockPos(1, 1, 1), TowerDefs.ARROW);
		BlockPos pos = context.absolutePos(new BlockPos(1, 1, 1));
		int full = arrow.hp();

		TowerService.damage(level, pos, 15);
		expect(context, arrow.hp() == full - 15, "damage lowers HP, was " + arrow.hp());
		TowerService.damage(level, pos, 0);
		TowerService.damage(level, pos, -5);
		expect(context, arrow.hp() == full - 15, "zero and negative damage change nothing");
		TowerService.damage(level, context.absolutePos(new BlockPos(5, 1, 1)), 99);
		expect(context, level.getBlockState(pos).is(ModBlocks.ARROW_TOWER), "damage to a spot with no tower does nothing");

		TowerService.damage(level, pos, full);
		expect(context, level.getBlockState(pos).isAir(), "a tower at 0 HP is destroyed");
		context.succeed();
	}

	@GameTest
	public void repairingCostsCoinsAndStopsAtFullHealth(GameTestHelper context) {
		TowerBlockEntity arrow = tower(context, new BlockPos(1, 1, 1), TowerDefs.ARROW);
		BlockPos pos = context.absolutePos(new BlockPos(1, 1, 1));
		TowerDef def = TowerDefs.get(TowerDefs.ARROW);
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(pos.getCenter().add(2.0, 0.0, 0.0));

		expect(context, TowerService.repair(player, pos) == RepairResult.ALREADY_FULL, "an undamaged tower is not repaired");
		arrow.setHp(def.hp() - 25);
		expect(context, TowerService.repair(player, pos) == RepairResult.NOT_ENOUGH_COINS, "a player with no coins cannot repair");
		expect(context, arrow.hp() == def.hp() - 25, "and nothing changes");

		Coins.add(player, 20);
		expect(context, TowerService.repair(player, pos) == RepairResult.REPAIRED, "with coins it works");
		expect(context, arrow.hp() == def.hp() - 25 + def.repairHp() && Coins.balance(player) == 20 - def.repairCost(), "it restores the file's HP for the file's price");

		arrow.setHp(def.hp() - 3);
		TowerService.repair(player, pos);
		expect(context, arrow.hp() == def.hp(), "repair never goes past full health, was " + arrow.hp());

		player.setPos(pos.getCenter().add(TowerService.REPAIR_REACH + 3.0, 0.0, 0.0));
		arrow.setHp(1);
		expect(context, TowerService.repair(player, pos) == RepairResult.TOO_FAR, "a player must be near the tower");
		expect(context, TowerService.repair(player, context.absolutePos(new BlockPos(9, 1, 1))) == RepairResult.NOT_A_TOWER, "and there must be a tower");
		context.succeed();
	}

	@GameTest
	public void repairIsARightClickWithAnEmptyHand(GameTestHelper context) {
		TowerBlockEntity arrow = tower(context, new BlockPos(1, 1, 1), TowerDefs.ARROW);
		BlockPos pos = context.absolutePos(new BlockPos(1, 1, 1));
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(pos.getCenter().add(2.0, 0.0, 0.0));
		Coins.add(player, 50);
		arrow.setHp(10);
		BlockHitResult hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 4));
		InteractionResult withBlocks = UseBlockCallback.EVENT.invoker().interact(player, context.getLevel(), InteractionHand.MAIN_HAND, hit);
		expect(context, withBlocks == InteractionResult.PASS && arrow.hp() == 10, "holding something leaves the click alone, so building still works");

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		InteractionResult empty = UseBlockCallback.EVENT.invoker().interact(player, context.getLevel(), InteractionHand.MAIN_HAND, hit);
		expect(context, empty == InteractionResult.SUCCESS && arrow.hp() > 10, "an empty hand repairs, hp " + arrow.hp());
		context.succeed();
	}

	// --- blocked paths ---

	@GameTest(maxTicks = 100, structure = BOX)
	public void aWalledInHeartIsBlockedAndAnOpenOneIsNot(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos heart = heartInBox(context);
		FakeHeartTarget target = targetAt(context, heart);
		Mob walker = spawn(context, RUNNER, new BlockPos(2, 1, 5), target, 0);
		walker.setNoAi(true);
		boolean[] stage = {false};

		context.succeedWhen(() -> {
			expect(context, level.isPositionEntityTicking(context.absolutePos(new BlockPos(2, 1, 5))), "waiting for the chunks");
			if (!stage[0]) {
				stage[0] = true;
				expect(context, !PathCheck.blocked(walker, context.absolutePos(heart)), "with a clear field the Heart can be reached");
				wallAcross(context, Blocks.STONE_BRICKS, 10);
			}
			expect(context, PathCheck.blocked(walker, context.absolutePos(heart)), "with a solid wall all the way across it cannot");
			walker.discard();
		});
	}

	// --- wall breaking ---

	@GameTest(maxTicks = 1800, structure = BOX)
	public void brutesBreakAPlayerWallToReachTheHeart(GameTestHelper context) {
		BlockPos heart = heartInBox(context);
		wallAcross(context, Blocks.COBBLESTONE, 10);
		int before = count(context, Blocks.COBBLESTONE, 10);
		FakeHeartTarget target = targetAt(context, heart);
		Mob brute = spawn(context, BRUTE, new BlockPos(2, 1, 5), target, 0);

		context.succeedWhen(() -> {
			expect(context, target.damage.get() > 0, "the brute should break through and reach the Heart; walls left " + count(context, Blocks.COBBLESTONE, 10) + " of " + before + " brute at " + brute.blockPosition().subtract(context.absolutePos(BlockPos.ZERO)) + " blocked " + PathCheck.blocked(brute, context.absolutePos(heart)) + " alive " + brute.isAlive() + " holes " + holes(context, 10));
			expect(context, count(context, Blocks.COBBLESTONE, 10) < before, "which means the wall has a hole in it");
			brute.discard();
		});
	}

	@GameTest(maxTicks = 700, structure = BOX)
	public void runnersCannotBreakWallsAndGoNowhere(GameTestHelper context) {
		BlockPos heart = heartInBox(context);
		wallAcross(context, Blocks.COBBLESTONE, 10);
		int before = count(context, Blocks.COBBLESTONE, 10);
		FakeHeartTarget target = targetAt(context, heart);
		Mob runner = spawn(context, RUNNER, new BlockPos(2, 1, 5), target, 0);
		int[] ticks = {0};

		context.succeedWhen(() -> {
			expect(context, ++ticks[0] > 600, "giving the runner 600 ticks to try");
			expect(context, target.damage.get() == 0, "a runner has no way through a solid wall");
			expect(context, count(context, Blocks.COBBLESTONE, 10) == before, "and breaks nothing");
			runner.discard();
		});
	}

	@GameTest(maxTicks = 1400, structure = BOX)
	public void bruteLeavesTheMapsOwnWallsAlone(GameTestHelper context) {
		BlockPos heart = heartInBox(context);
		wallAcross(context, Blocks.STONE_BRICKS, 10);
		int before = count(context, Blocks.STONE_BRICKS, 10);
		FakeHeartTarget target = targetAt(context, heart);
		Mob brute = spawn(context, BRUTE, new BlockPos(2, 1, 5), target, 0);
		int[] ticks = {0};

		context.succeedWhen(() -> {
			expect(context, ++ticks[0] > 1200, "giving the brute 1200 ticks to try");
			expect(context, target.damage.get() == 0, "stone bricks are map walls: no way through");
			expect(context, count(context, Blocks.STONE_BRICKS, 10) == before, "and none of them is damaged");
			brute.discard();
		});
	}

	@GameTest(maxTicks = 1200, structure = BOX)
	public void bruteBlockedFromTheHeartAttacksTheNearestTower(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos heart = heartInBox(context);
		wallAcross(context, Blocks.STONE_BRICKS, 14);
		BlockPos towerRelative = new BlockPos(8, 1, 5);
		TowerBlockEntity tower = tower(context, towerRelative, TowerDefs.ARROW);
		BlockPos towerPos = context.absolutePos(towerRelative);
		FakeHeartTarget target = targetAt(context, heart);
		Mob brute = spawn(context, BRUTE, new BlockPos(2, 1, 5), target, 0);

		context.succeedWhen(() -> {
			expect(context, !level.getBlockState(towerPos).is(ModBlocks.ARROW_TOWER) || tower.hp() < TowerDefs.get(TowerDefs.ARROW).hp(),
				"the brute should be attacking the tower: hp " + tower.hp());
			expect(context, level.getBlockState(towerPos).isAir(), "and wear it down until it falls, hp " + tower.hp());
			expect(context, target.damage.get() == 0, "all the while the Heart is out of reach");
			brute.discard();
		});
	}

	// --- sappers ---

	@GameTest(maxTicks = 800, structure = BOX)
	public void sappersBlastTowersAndBreakNearbyWallsOnly(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos heart = heartInBox(context);
		TowerBlockEntity tower = tower(context, new BlockPos(8, 1, 5), TowerDefs.CANNON);
		int cannonHp = TowerDefs.get(TowerDefs.CANNON).hp();
		// On the sapper's side of the tower: a player's cobblestone and a map brick, both close enough to be caught in the blast.
		BlockPos cobble = context.absolutePos(new BlockPos(7, 1, 4));
		BlockPos brick = context.absolutePos(new BlockPos(7, 1, 6));
		level.setBlock(cobble, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(brick, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
		ServerPlayer bystander = context.makeMockServerPlayerInLevel();
		FakeHeartTarget target = targetAt(context, heart);
		Mob sapper = spawn(context, SAPPER, new BlockPos(2, 1, 5), target, 7);

		context.succeedWhen(() -> {
			expect(context, !sapper.isAlive(), "the sapper should have blown itself up on the tower");
			expect(context, tower.hp() == cannonHp - MobDef.get(SAPPER).structureDamage(), "the tower took the sapper's structure damage, hp " + tower.hp());
			expect(context, level.getBlockState(cobble).isAir(), "the player's cobblestone beside it is gone");
			expect(context, level.getBlockState(brick).is(Blocks.STONE_BRICKS), "the map brick beside it is not");
			expect(context, target.damage.get() == 0, "the Heart was not touched");
			expect(context, Coins.balance(bystander) == 0, "a sapper's own blast pays nobody");
		});
	}

	@GameTest(maxTicks = 800, structure = BOX)
	public void sappersWithNothingToWreckBlowUpOnTheHeart(GameTestHelper context) {
		BlockPos heart = heartInBox(context);
		FakeHeartTarget target = targetAt(context, heart);
		Mob sapper = spawn(context, SAPPER, new BlockPos(2, 1, 5), target, 0);

		context.succeedWhen(() -> {
			expect(context, !sapper.isAlive(), "the sapper should reach the Heart and blow up");
			expect(context, target.damage.get() == MobDef.get(SAPPER).heartDamage(), "the Heart takes its Heart damage once, " + target.damage);
		});
	}

	@GameTest(maxTicks = 1000, structure = BOX)
	public void sappersBlastAWallThatBlocksTheWay(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos heart = heartInBox(context);
		wallAcross(context, Blocks.COBBLESTONE, 10);
		int before = count(context, Blocks.COBBLESTONE, 10);
		FakeHeartTarget target = targetAt(context, heart);
		Mob sapper = spawn(context, SAPPER, new BlockPos(2, 1, 5), target, 0);

		context.succeedWhen(() -> {
			expect(context, !sapper.isAlive(), "the sapper should have blown itself up on the wall");
			expect(context, count(context, Blocks.COBBLESTONE, 10) < before, "leaving a gap in it, left " + count(context, Blocks.COBBLESTONE, 10) + " of " + before);
			expect(context, target.damage.get() == 0, "without hurting the Heart");
		});
	}

	@GameTest
	public void aSappersKillRewardIsNotAnEntityItCanDrop(GameTestHelper context) {
		// Sanity check on what the tests above rely on: a wave mob that is not killed by a player pays nobody.
		ServerLevel level = context.getLevel();
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		Entity mob = EntityType.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		mob.setPos(context.absolutePos(new BlockPos(1, 1, 1)).getCenter());
		level.addFreshEntity(mob);
		Rewards.setKillReward(mob, 9);
		mob.kill(level);
		expect(context, Coins.balance(player) == 0, "kill() with no killer pays nobody");
		context.succeed();
	}
}
