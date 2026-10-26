package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.placement.PlacementService;
import dev.vadim.heartguard.placement.PlacementService.Result;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Server-side placement rules: the client only asks, these tests pin down what the server answers and charges. */
public class PlacementGameTest implements CustomTestMethodInvoker {
	private static final BlockPos GROUND = new BlockPos(0, 0, 0);
	private static final BlockPos TARGET = new BlockPos(0, 1, 0);
	private static final Identifier ARROW = TowerDefs.ARROW;

	/** A mock player standing next to the target, in range and with plenty of coins. */
	private static ServerPlayer richPlayerNear(GameTestHelper context) {
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(TARGET).getCenter());
		Coins.add(player, 1000);
		return player;
	}

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static void expectResult(GameTestHelper context, Result expected, Result actual) {
		expect(context, actual == expected, "expected " + expected + " but was " + actual);
	}

	@GameTest
	public void buildsATowerOnSolidGroundAndChargesIt(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = richPlayerNear(context);
		TowerDef arrow = TowerDefs.get(ARROW);

		Result result = PlacementService.tryPlace(player, ARROW, context.absolutePos(TARGET));
		expectResult(context, Result.PLACED, result);
		context.assertBlockPresent(ModBlocks.ARROW_TOWER, TARGET);
		expect(context, Coins.balance(player) == 1000 - arrow.cost(), "the tower costs its price, balance " + Coins.balance(player));

		TowerBlockEntity tower = (TowerBlockEntity) context.getLevel().getBlockEntity(context.absolutePos(TARGET));
		expect(context, tower != null, "a tower block has a block entity");
		expect(context, tower.owner().equals(java.util.Optional.of(player.getUUID())), "the builder owns the tower");
		expect(context, tower.tier() == 1 && tower.hp() == arrow.hp(), "a new tower is tier 1 at full HP, was tier " + tower.tier() + " hp " + tower.hp());
		context.succeed();
	}

	@GameTest
	public void eachTowerTypeBuildsItsOwnBlock(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(new BlockPos(2, 0, 0), Blocks.STONE);
		context.setBlock(new BlockPos(4, 0, 0), Blocks.STONE);
		ServerPlayer player = richPlayerNear(context);
		expectResult(context, Result.PLACED, PlacementService.tryPlace(player, TowerDefs.ARROW, context.absolutePos(new BlockPos(0, 1, 0))));
		expectResult(context, Result.PLACED, PlacementService.tryPlace(player, TowerDefs.CANNON, context.absolutePos(new BlockPos(2, 1, 0))));
		expectResult(context, Result.PLACED, PlacementService.tryPlace(player, TowerDefs.FROST, context.absolutePos(new BlockPos(4, 1, 0))));
		context.assertBlockPresent(ModBlocks.ARROW_TOWER, new BlockPos(0, 1, 0));
		context.assertBlockPresent(ModBlocks.CANNON_TOWER, new BlockPos(2, 1, 0));
		context.assertBlockPresent(ModBlocks.FROST_TOWER, new BlockPos(4, 1, 0));
		int spent = TowerDefs.get(TowerDefs.ARROW).cost() + TowerDefs.get(TowerDefs.CANNON).cost() + TowerDefs.get(TowerDefs.FROST).cost();
		expect(context, Coins.balance(player) == 1000 - spent, "all three prices were charged, balance " + Coins.balance(player));
		context.succeed();
	}

	@GameTest
	public void refusesWhenTheBuilderCannotAffordIt(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(TARGET).getCenter());
		Coins.add(player, TowerDefs.get(ARROW).cost() - 1);

		Result result = PlacementService.tryPlace(player, ARROW, context.absolutePos(TARGET));
		expectResult(context, Result.NOT_ENOUGH_COINS, result);
		context.assertBlockNotPresent(ModBlocks.ARROW_TOWER, TARGET);
		expect(context, Coins.balance(player) == TowerDefs.get(ARROW).cost() - 1, "a refused build takes no coins");
		context.succeed();
	}

	@GameTest
	public void refusalsOtherThanMoneyAlsoTakeNoCoins(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.AIR);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = richPlayerNear(context);
		expectResult(context, Result.NO_SUPPORT, PlacementService.tryPlace(player, ARROW, context.absolutePos(TARGET)));
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.DIRT);
		expectResult(context, Result.OBSTRUCTED, PlacementService.tryPlace(player, ARROW, context.absolutePos(TARGET)));
		expect(context, Coins.balance(player) == 1000, "no coins were taken for refused builds, balance " + Coins.balance(player));
		context.succeed();
	}

	@GameTest
	public void rejectsUnknownTowers(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = richPlayerNear(context);
		Identifier bogus = Identifier.fromNamespaceAndPath("heartguard", "death_ray");
		expectResult(context, Result.UNKNOWN_TOWER, PlacementService.tryPlace(player, bogus, context.absolutePos(TARGET)));
		expect(context, Coins.balance(player) == 1000, "an unknown tower costs nothing");
		context.succeed();
	}

	@GameTest
	public void rejectsPositionFarFromPlayer(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = richPlayerNear(context);
		player.setPos(player.position().add(PlacementService.MAX_DISTANCE + 10.0, 0.0, 0.0));
		expectResult(context, Result.OUT_OF_RANGE, PlacementService.tryPlace(player, ARROW, context.absolutePos(TARGET)));
		context.assertBlockNotPresent(ModBlocks.ARROW_TOWER, TARGET);
		context.succeed();
	}

	@GameTest
	public void rejectsPositionOutsideWorld(GameTestHelper context) {
		BlockPos belowWorld = context.absolutePos(new BlockPos(0, -1000, 0));
		expectResult(context, Result.OUT_OF_WORLD, PlacementService.tryPlace(richPlayerNear(context), ARROW, belowWorld));
		context.succeed();
	}

	@GameTest
	public void towerStateSurvivesSavingAndLoading(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = richPlayerNear(context);
		BlockPos pos = context.absolutePos(TARGET);
		expectResult(context, Result.PLACED, PlacementService.tryPlace(player, ARROW, pos));

		TowerBlockEntity original = (TowerBlockEntity) context.getLevel().getBlockEntity(pos);
		original.setTier(2);
		original.setTargeting(TowerBlockEntity.Targeting.STRONGEST);
		original.setHp(17);

		var saved = original.saveWithFullMetadata(context.getLevel().registryAccess());
		TowerBlockEntity restored = new TowerBlockEntity(pos, original.getBlockState());
		restored.setLevel(context.getLevel());
		try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(com.mojang.logging.LogUtils.getLogger())) {
			restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(reporter, context.getLevel().registryAccess(), saved));
		}

		expect(context, restored.owner().equals(original.owner()), "the owner is saved");
		expect(context, restored.tier() == 2 && restored.hp() == 17, "tier and HP are saved, got tier " + restored.tier() + " hp " + restored.hp());
		expect(context, restored.targeting() == TowerBlockEntity.Targeting.STRONGEST, "the targeting mode is saved");
		context.succeed();
	}

	/** Remove any towers the test built before the next tick, so they cannot shoot other tests' mobs. */
	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		try {
			method.invoke(this, context);
		} finally {
			TowerCleanup.clear(context);
		}
	}
}
