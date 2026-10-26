package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.mob.BossAbilityGoal;
import dev.vadim.heartguard.mob.MobAttachments;
import dev.vadim.heartguard.mob.MobDef;
import dev.vadim.heartguard.mob.MobFactory;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The wave roster: that each mob is what its file says and demands a different answer (fast, armored, airborne,
 * summoning), and that the new behaviors work in real time.
 */
public class MobGameTest {
	private static final Identifier RUNNER = Identifier.fromNamespaceAndPath("heartguard", "runner");
	private static final Identifier BRUTE = Identifier.fromNamespaceAndPath("heartguard", "brute");
	private static final Identifier FLYER = Identifier.fromNamespaceAndPath("heartguard", "flyer");
	private static final Identifier WARLORD = Identifier.fromNamespaceAndPath("heartguard", "warlord");
	private static final Identifier LOW_FLYER = Identifier.fromNamespaceAndPath("heartguard-test", "low_flyer");
	private static final Identifier TEST_BOSS = Identifier.fromNamespaceAndPath("heartguard-test", "test_boss");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static MobDef def(Identifier id) {
		MobDef def = MobDef.get(id);
		if (def == null) {
			throw new AssertionError("mob " + id + " should be loaded, have " + MobDef.all().keySet());
		}
		return def;
	}

	/** A stone floor and force-loaded chunks, so mobs can walk and fly and chunks tick. */
	private static void arena(GameTestHelper context, int fromX, int toX, int fromZ, int toZ) {
		TestArena.forceLoad(context, new BlockPos(fromX - 2, 0, fromZ - 2), new BlockPos(toX + 2, 12, toZ + 2));
		for (int x = fromX; x <= toX; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static Entity spawn(GameTestHelper context, Identifier id, BlockPos relative, FakeHeartTarget target) {
		return MobFactory.spawnWaveMob(context.getLevel(), id, context.absolutePos(relative), 4, target);
	}

	// --- the roster as data ---

	@GameTest
	public void theRosterDemandsDifferentAnswers(GameTestHelper context) {
		MobDef runner = def(RUNNER);
		MobDef brute = def(BRUTE);
		MobDef flyer = def(FLYER);
		MobDef warlord = def(WARLORD);

		expect(context, runner.speed() > 1.0 && runner.health() < brute.health() && runner.armor() == 0.0, "runners are fast, fragile and unarmored");
		expect(context, brute.speed() < 1.0 && brute.armor() > 0.0 && brute.knockbackResistance() > 0.0 && brute.has(MobDef.Trait.BREAKS_WALLS), "brutes are slow, armored, heavy and break walls");
		expect(context, flyer.has(MobDef.Trait.FLIES) && flyer.base() == EntityType.PHANTOM, "flyers are phantoms that fly");
		expect(context, warlord.has(MobDef.Trait.BOSS) && warlord.ability().isPresent() && warlord.knockbackResistance() == 1.0, "the boss is a boss with an ability and no knockback");
		expect(context, warlord.health() > brute.health() * 3, "the boss is far tougher than a brute");
		expect(context, warlord.heartDamage() > brute.heartDamage() && brute.heartDamage() > runner.heartDamage(), "heavier mobs hit the Heart harder");

		MobDef.Ability ability = warlord.ability().get();
		expect(context, MobDef.isKnown(ability.summon()) && ability.maxAlive() >= ability.count(), "the boss summons something that exists, within its cap");
		context.succeed();
	}

	@GameTest
	public void badMobFilesAreRejected(GameTestHelper context) {
		String ok = "\"base\": \"minecraft:husk\", \"health\": 10";
		List<String> bad = List.of(
			"{ \"health\": 10 }",
			"{ \"base\": \"minecraft:no_such\", \"health\": 10 }",
			"{ \"base\": \"minecraft:husk\" }",
			"{ \"base\": \"minecraft:husk\", \"health\": 0 }",
			"{ " + ok + ", \"speed\": 0 }",
			"{ " + ok + ", \"speed\": 9 }",
			"{ " + ok + ", \"armor\": -1 }",
			"{ " + ok + ", \"knockback_resistance\": 2 }",
			"{ " + ok + ", \"scale\": 0 }",
			"{ " + ok + ", \"heart_damage\": 0 }",
			"{ " + ok + ", \"traits\": [\"teleports\"] }",
			"{ " + ok + ", \"equipment\": [ { \"slot\": \"head\", \"item\": \"minecraft:no_such\" } ] }",
			"{ " + ok + ", \"ability\": { \"summon\": \"heartguard:runner\", \"count\": 0, \"interval_ticks\": 100, \"max_alive\": 3 } }",
			"{ " + ok + ", \"ability\": { \"summon\": \"heartguard:runner\", \"count\": 1, \"interval_ticks\": 5, \"max_alive\": 3 } }"
		);
		for (String json : bad) {
			expect(context, MobDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isEmpty(), "this mob should be rejected: " + json);
		}
		MobDef minimal = MobDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{ " + ok + " }")).result().orElseThrow();
		expect(context, minimal.speed() == 1.0 && minimal.armor() == 0.0 && minimal.scale() == 1.0 && minimal.traits().isEmpty() && minimal.ability().isEmpty(), "everything but base and health has a default");
		context.succeed();
	}

	@GameTest
	public void waveEntriesNameMobsByDefinitionOrVanillaId(GameTestHelper context) {
		String entry = "{ \"waves\": [ { \"spawns\": [ { \"spawn\": 0, \"mob\": \"%s\", \"count\": 1 } ] } ] }";
		expect(context, WaveSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.formatted("heartguard:runner"))).result().isPresent(), "a mob definition id is accepted");
		expect(context, WaveSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.formatted("minecraft:husk"))).result().isPresent(), "a vanilla entity id is accepted");
		expect(context, WaveSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.formatted("minecraft:no_such_mob"))).result().isEmpty(), "an unknown vanilla id is rejected when the file is read");

		WaveSet ghost = WaveSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.formatted("heartguard:ghost"))).result().orElseThrow();
		expect(context, ghost.unknownMobs().equals(List.of(Identifier.fromNamespaceAndPath("heartguard", "ghost"))), "an undefined mob id is reported when a match would start, " + ghost.unknownMobs());
		expect(context, WaveSets.get(WaveSets.DEFAULT_ID).unknownMobs().isEmpty(), "the shipped Survival waves name only known mobs");
		context.succeed();
	}

	// --- spawning ---

	@GameTest
	public void spawningAppliesTheDefinition(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		FakeHeartTarget target = new FakeHeartTarget(level, context.absolutePos(new BlockPos(9, 1, 0)));
		MobDef runnerDef = def(RUNNER);
		MobDef bruteDef = def(BRUTE);
		Mob runner = (Mob) spawn(context, RUNNER, new BlockPos(0, 1, 0), target);
		Mob brute = (Mob) spawn(context, BRUTE, new BlockPos(0, 1, 2), target);
		double husk = EntityType.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT).getAttributeBaseValue(Attributes.MOVEMENT_SPEED);

		expect(context, runner.getMaxHealth() == runnerDef.health() && runner.getHealth() == runnerDef.health(), "the runner has the file's health, " + runner.getHealth());
		expect(context, Math.abs(runner.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - husk * runnerDef.speed()) < 1e-6, "the runner walks at the file's multiple of the base speed");
		expect(context, runner.getAttributeValue(Attributes.SCALE) == runnerDef.scale(), "and has the file's scale");
		expect(context, brute.getAttributeBaseValue(Attributes.ARMOR) == bruteDef.armor(), "the brute has the file's natural armor, base " + brute.getAttributeBaseValue(Attributes.ARMOR));
		double knockback = brute.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		// Zombie types add a small random resistance of their own when they spawn.
		expect(context, knockback >= bruteDef.knockbackResistance() && knockback <= bruteDef.knockbackResistance() + 0.1, "and its knockback resistance, " + knockback);
		expect(context, brute.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && brute.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "and wears its iron");
		expect(context, brute.getAttached(MobAttachments.HEART_DAMAGE) == bruteDef.heartDamage(), "the Heart damage is stamped on the mob");

		for (Mob mob : List.of(runner, brute)) {
			expect(context, mob.entityTags().contains(WaveSpawner.WAVE_MOB_TAG), "it is a wave mob");
			expect(context, mob.getAttached(Rewards.KILL_REWARD) == 4, "it carries the reward the entry gave");
			expect(context, mob.hasEffect(MobEffects.FIRE_RESISTANCE), "it does not burn in daylight");
			expect(context, mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).anyMatch(g -> g instanceof HeartSeekGoal), "it marches on the Heart");
			mob.discard();
		}
		context.succeed();
	}

	@GameTest
	public void vanillaIdsStillSpawnAsWaveMobs(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		FakeHeartTarget target = new FakeHeartTarget(level, context.absolutePos(new BlockPos(9, 1, 0)));
		Mob zombie = (Mob) spawn(context, Identifier.withDefaultNamespace("zombie"), new BlockPos(0, 1, 0), target);
		expect(context, zombie != null && zombie.getType() == EntityType.ZOMBIE, "a plain vanilla id still spawns");
		expect(context, zombie.entityTags().contains(WaveSpawner.WAVE_MOB_TAG) && zombie.hasEffect(MobEffects.FIRE_RESISTANCE), "and is tagged and fireproof like the rest");
		expect(context, zombie.getAttached(MobAttachments.HEART_DAMAGE) == null, "but carries no definition values, so it hits for the default");
		zombie.discard();
		expect(context, spawn(context, Identifier.fromNamespaceAndPath("heartguard", "nothing_like_it"), new BlockPos(0, 1, 0), target) == null, "an id that names nothing spawns nothing");
		context.succeed();
	}

	@GameTest
	public void armorBluntsDamageSoBrutesNeedStrongerTowers(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		FakeHeartTarget target = new FakeHeartTarget(level, context.absolutePos(new BlockPos(9, 1, 0)));
		ServerPlayer attacker = context.makeMockServerPlayerInLevel();
		Mob runner = (Mob) spawn(context, RUNNER, new BlockPos(0, 1, 0), target);
		Mob brute = (Mob) spawn(context, BRUTE, new BlockPos(0, 1, 2), target);
		runner.setNoAi(true);
		brute.setNoAi(true);
		float runnerBefore = runner.getHealth();
		float bruteBefore = brute.getHealth();

		runner.hurtServer(level, level.damageSources().playerAttack(attacker), 4.0F);
		brute.hurtServer(level, level.damageSources().playerAttack(attacker), 4.0F);
		float runnerLost = runnerBefore - runner.getHealth();
		float bruteLost = bruteBefore - brute.getHealth();
		expect(context, runnerLost == 4.0F, "an unarmored runner takes the full 4, took " + runnerLost);
		expect(context, bruteLost > 0.0F && bruteLost < 4.0F, "an armored brute takes less than 4 from the same hit, took " + bruteLost);
		runner.discard();
		brute.discard();
		context.succeed();
	}

	@GameTest(maxTicks = 200)
	public void waveMobsNeverBurnInDaylight(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		arena(context, 0, 12, 0, 6);
		FakeHeartTarget target = new FakeHeartTarget(level, context.absolutePos(new BlockPos(30, 1, 3)));
		Mob zombie = (Mob) spawn(context, Identifier.withDefaultNamespace("zombie"), new BlockPos(1, 1, 3), target);
		float full = zombie.getHealth();
		int[] ticks = {0};

		context.succeedWhen(() -> {
			expect(context, ++ticks[0] > 120, "letting daylight work on the zombie for 120 ticks");
			expect(context, zombie.getHealth() == full, "a vanilla zombie in the open takes no sun damage, health " + zombie.getHealth());
			expect(context, !zombie.isOnFire(), "and is not drawn burning");
			zombie.discard();
		});
	}

	// --- behavior in real time ---

	@GameTest(maxTicks = 1200)
	public void runnersReachTheHeartBeforeBrutes(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		arena(context, -4, 16, -4, 8);
		BlockPos heart = new BlockPos(14, 1, 2);
		FakeHeartTarget runnerTarget = new FakeHeartTarget(level, context.absolutePos(heart));
		FakeHeartTarget bruteTarget = new FakeHeartTarget(level, context.absolutePos(heart));
		context.setBlock(heart, dev.vadim.heartguard.block.ModBlocks.HEART);
		Entity runner = spawn(context, RUNNER, new BlockPos(-2, 1, 0), runnerTarget);
		Entity brute = spawn(context, BRUTE, new BlockPos(-2, 1, 4), bruteTarget);

		context.succeedWhen(() -> {
			expect(context, runnerTarget.firstHit.get() >= 0 && bruteTarget.firstHit.get() >= 0, "both should reach the Heart; runner " + runnerTarget.firstHit + ", brute " + bruteTarget.firstHit);
			expect(context, runnerTarget.firstHit.get() < bruteTarget.firstHit.get(), "the runner should arrive first: " + runnerTarget.firstHit + " vs " + bruteTarget.firstHit);
			expect(context, runnerTarget.damage.get() > 0 && bruteTarget.damage.get() % def(BRUTE).heartDamage() == 0, "hits are the file's Heart damage");
			runner.discard();
			brute.discard();
		});
	}

	@GameTest(maxTicks = 600, structure = "heartguard:flight_arena")
	public void flyersCrossAWallThatStopsGroundMobs(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		arena(context, 0, 23, 0, 10);
		// Walls round the edge and one across the middle, three high: nothing on the ground gets from one side to the other.
		for (int y = 1; y <= 3; y++) {
			for (int z = 0; z <= 10; z++) {
				context.setBlock(new BlockPos(11, y, z), Blocks.STONE_BRICKS);
			}
			for (int x = 0; x <= 23; x++) {
				context.setBlock(new BlockPos(x, y, 0), Blocks.STONE_BRICKS);
				context.setBlock(new BlockPos(x, y, 10), Blocks.STONE_BRICKS);
			}
		}
		BlockPos heart = new BlockPos(20, 1, 5);
		context.setBlock(heart, dev.vadim.heartguard.block.ModBlocks.HEART);
		FakeHeartTarget flyerTarget = new FakeHeartTarget(level, context.absolutePos(heart));
		FakeHeartTarget walkerTarget = new FakeHeartTarget(level, context.absolutePos(heart));
		MobDef.override(LOW_FLYER, new MobDef(EntityType.PHANTOM, "Low Flyer", 14.0, 1.0, 0.0, 0.0, 1.0, 6, 5.0, List.of(MobDef.Trait.FLIES), List.of(), java.util.Optional.empty()));
		Entity flyer = spawn(context, LOW_FLYER, new BlockPos(2, 1, 4), flyerTarget);
		Entity walker = spawn(context, BRUTE, new BlockPos(2, 1, 6), walkerTarget);

		context.succeedWhen(() -> {
			expect(context, flyerTarget.damage.get() > 0, "the flyer should fly over the wall and hit the Heart; damage " + flyerTarget.damage + ", at " + flyer.position()
				+ ", velocity " + flyer.getDeltaMovement() + ", hCollision " + flyer.horizontalCollision + ", vCollision " + flyer.verticalCollision
				+ ", east " + level.getBlockState(flyer.blockPosition().east()));
			expect(context, walkerTarget.damage.get() == 0, "the ground mob must not get through a solid wall");
			flyer.discard();
			walker.discard();
			MobDef.clearOverride(LOW_FLYER);
		});
	}

	@GameTest(maxTicks = 500)
	public void bossesSummonWithinTheirCapAndShowABar(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		arena(context, -4, 12, -4, 8);
		FakeHeartTarget target = new FakeHeartTarget(level, context.absolutePos(new BlockPos(10, 1, 0)));
		target.active.set(false); // everything stays put; only the summoning is under test
		MobDef.override(TEST_BOSS, new MobDef(EntityType.HUSK, "Test Boss", 100.0, 1.0, 0.0, 1.0, 1.0, 10, 8.0, List.of(MobDef.Trait.BOSS), List.of(),
			java.util.Optional.of(new MobDef.Ability(RUNNER, 2, 40, 4))));
		Mob boss = (Mob) spawn(context, TEST_BOSS, new BlockPos(0, 1, 0), target);
		boss.setNoAi(false);
		expect(context, BossAbilityGoal.barOf(boss) != null, "the boss has a boss bar from the moment it spawns");
		boolean[] killed = {false};

		context.succeedWhen(() -> {
			List<Entity> summoned = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity.isAlive() && entity.entityTags().contains(BossAbilityGoal.SUMMONED_TAG)) {
					summoned.add(entity);
				}
			}
			expect(context, summoned.size() > 0, "the boss should have summoned reinforcements by now");
			expect(context, summoned.size() <= 4, "never more than the cap of 4 at once, saw " + summoned.size());
			expect(context, summoned.stream().allMatch(e -> e.entityTags().contains(WaveSpawner.WAVE_MOB_TAG) && e.getType() == EntityType.HUSK),
				"summoned mobs are wave mobs of the named kind");
			expect(context, BossAbilityGoal.barOf(boss).getProgress() == 1.0F, "the bar shows full health");

			if (!killed[0]) {
				killed[0] = true;
				boss.hurtServer(level, level.damageSources().generic(), 1000.0F);
			}
			expect(context, !boss.isAlive() && BossAbilityGoal.barOf(boss) == null, "when the boss dies its bar goes with it");
			summoned.forEach(Entity::discard);
			MobDef.clearOverride(TEST_BOSS);
		});
	}
}
