package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerBlockEntity.Targeting;
import dev.vadim.heartguard.tower.TowerCombat;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.tower.TowerTargeting;
import dev.vadim.heartguard.tower.TowerTargeting.Candidate;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Tower behaviour: who they pick, what each type does to it, how often, and who gets paid for kills. */
public class TowerCombatGameTest implements CustomTestMethodInvoker {
	private static final BlockPos TOWER = new BlockPos(0, 1, 0);

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static void buildFloor(GameTestHelper context) {
		for (int x = -4; x <= 10; x++) {
			for (int z = -4; z <= 4; z++) {
				context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static TowerBlockEntity placeTower(GameTestHelper context, Identifier type, UUID owner) {
		ServerLevel level = context.getLevel();
		BlockPos pos = context.absolutePos(TOWER);
		level.setBlock(pos, ModBlocks.towerBlock(type).defaultBlockState(), Block.UPDATE_ALL);
		TowerBlockEntity tower = (TowerBlockEntity) level.getBlockEntity(pos);
		tower.initialize(owner, TowerDefs.get(type));
		return tower;
	}

	/** A husk with no AI and no random gear, so damage numbers are exact. Tagged as a wave mob unless told not to be. */
	private static Mob mob(GameTestHelper context, double x, boolean waveMob) {
		ServerLevel level = context.getLevel();
		Mob husk = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
		var at = context.absolutePos(new BlockPos(0, 1, 0)).getCenter().add(x, -0.5, 0.0);
		husk.setPos(at);
		husk.setNoAi(true);
		if (waveMob) {
			husk.addTag(WaveSpawner.WAVE_MOB_TAG);
		}
		level.addFreshEntity(husk);
		return husk;
	}

	private static boolean fire(GameTestHelper context, TowerBlockEntity tower, Optional<BlockPos> heart) {
		return TowerCombat.tick(context.getLevel(), context.absolutePos(TOWER), tower, heart);
	}

	private static void discardAll(List<? extends net.minecraft.world.entity.Entity> entities) {
		entities.forEach(net.minecraft.world.entity.Entity::discard);
	}

	// --- targeting rules ---

	@GameTest
	public void targetingPicksByMode(GameTestHelper context) {
		var nearTower = new Candidate<>("nearTower", 2.0, 20.0, 10.0);
		var nearHeart = new Candidate<>("nearHeart", 9.0, 3.0, 5.0);
		var tough = new Candidate<>("tough", 6.0, 12.0, 40.0);
		List<Candidate<String>> all = List.of(nearTower, nearHeart, tough);

		expect(context, TowerTargeting.choose(Targeting.FIRST, all).mob().equals("nearHeart"), "FIRST picks the mob closest to the Heart");
		expect(context, TowerTargeting.choose(Targeting.CLOSEST, all).mob().equals("nearTower"), "CLOSEST picks the mob closest to the tower");
		expect(context, TowerTargeting.choose(Targeting.STRONGEST, all).mob().equals("tough"), "STRONGEST picks the healthiest mob");
		expect(context, TowerTargeting.choose(Targeting.FIRST, List.<Candidate<String>>of()) == null, "nothing in range means no target");
		context.succeed();
	}

	@GameTest
	public void targetingTiesFallBackToTheCloserMob(GameTestHelper context) {
		var far = new Candidate<>("far", 8.0, Double.POSITIVE_INFINITY, 20.0);
		var close = new Candidate<>("close", 3.0, Double.POSITIVE_INFINITY, 20.0);
		expect(context, TowerTargeting.choose(Targeting.FIRST, List.of(far, close)).mob().equals("close"), "with no Heart FIRST falls back to the closer mob");
		expect(context, TowerTargeting.choose(Targeting.STRONGEST, List.of(far, close)).mob().equals("close"), "equal health falls back to the closer mob");
		context.succeed();
	}

	// --- arrow ---

	@GameTest
	public void arrowHitsOneMobForTierDamage(GameTestHelper context) {
		buildFloor(context);
		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, UUID.randomUUID());
		Mob near = mob(context, 4.0, true);
		Mob far = mob(context, 6.0, true);
		float damage = (float) TowerDefs.get(TowerDefs.ARROW).tier(1).damage();

		expect(context, fire(context, tower, Optional.empty()), "the tower should fire at a mob in range");
		expect(context, near.getHealth() == 20.0F - damage, "the chosen mob takes the tier damage, health " + near.getHealth());
		expect(context, far.getHealth() == 20.0F, "only one mob is hit, the other has " + far.getHealth());
		discardAll(List.of(near, far));
		context.succeed();
	}

	@GameTest
	public void upgradedTowersHitHarder(GameTestHelper context) {
		buildFloor(context);
		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, UUID.randomUUID());
		tower.setTier(2);
		Mob target = mob(context, 4.0, true);
		float damage = (float) TowerDefs.get(TowerDefs.ARROW).tier(2).damage();

		fire(context, tower, Optional.empty());
		expect(context, target.getHealth() == 20.0F - damage, "a tier 2 arrow tower uses tier 2 damage " + damage + ", health " + target.getHealth());
		target.discard();
		context.succeed();
	}

	@GameTest
	public void towersIgnoreWhatTheyShouldNot(GameTestHelper context) {
		buildFloor(context);
		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, UUID.randomUUID());
		Mob plain = mob(context, 4.0, false);
		Mob outOfRange = mob(context, 3.0 + TowerDefs.get(TowerDefs.ARROW).tier(1).range(), true);

		expect(context, !fire(context, tower, Optional.empty()), "no wave mob in range means no shot");
		expect(context, plain.getHealth() == 20.0F, "a mob that is not a wave mob is never hurt");
		expect(context, outOfRange.getHealth() == 20.0F, "a mob out of range is never hurt");
		discardAll(List.of(plain, outOfRange));
		context.succeed();
	}

	@GameTest
	public void towersFireOncePerInterval(GameTestHelper context) {
		buildFloor(context);
		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, UUID.randomUUID());
		Mob target = mob(context, 4.0, true);
		int interval = TowerDefs.get(TowerDefs.ARROW).tier(1).fireIntervalTicks();

		int shots = 0;
		for (int tick = 0; tick < 3 * (interval + 1); tick++) {
			target.invulnerableTime = 0;
			if (fire(context, tower, Optional.empty())) {
				shots++;
			}
		}
		expect(context, shots == 3, "three intervals mean three shots, was " + shots);
		target.discard();
		context.succeed();
	}

	@GameTest
	public void modesChooseDifferentTargetsInTheWorld(GameTestHelper context) {
		buildFloor(context);
		BlockPos heart = context.absolutePos(new BlockPos(8, 1, 0));
		float damage = (float) TowerDefs.get(TowerDefs.ARROW).tier(1).damage();

		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, UUID.randomUUID());
		Mob nearTower = mob(context, 2.0, true);
		Mob nearHeart = mob(context, 6.0, true);
		nearTower.setHealth(10.0F);

		tower.setTargeting(Targeting.FIRST);
		fire(context, tower, Optional.of(heart));
		expect(context, nearHeart.getHealth() == 20.0F - damage && nearTower.getHealth() == 10.0F, "FIRST shoots the mob nearest the Heart");

		tower.setCooldown(0);
		nearHeart.invulnerableTime = 0;
		tower.setTargeting(Targeting.CLOSEST);
		fire(context, tower, Optional.of(heart));
		expect(context, nearTower.getHealth() == 10.0F - damage, "CLOSEST shoots the mob nearest the tower, health " + nearTower.getHealth());

		tower.setCooldown(0);
		nearTower.invulnerableTime = 0;
		float healthyBefore = nearHeart.getHealth();
		tower.setTargeting(Targeting.STRONGEST);
		fire(context, tower, Optional.of(heart));
		expect(context, nearHeart.getHealth() == healthyBefore - damage, "STRONGEST shoots the healthier mob");
		discardAll(List.of(nearTower, nearHeart));
		context.succeed();
	}

	// --- kill credit ---

	@GameTest
	public void towerKillsPayTheOwner(GameTestHelper context) {
		buildFloor(context);
		ServerPlayer owner = context.makeMockServerPlayerInLevel();
		ServerPlayer bystander = context.makeMockServerPlayerInLevel();
		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, owner.getUUID());
		Mob target = mob(context, 4.0, true);
		target.setHealth(1.0F);
		Rewards.setKillReward(target, 7);

		fire(context, tower, Optional.empty());
		expect(context, !target.isAlive(), "the arrow should have killed the husk");
		expect(context, Coins.balance(owner) == 7, "the owner is paid the kill reward, had " + Coins.balance(owner));
		expect(context, Coins.balance(bystander) == 0, "nobody else is paid, had " + Coins.balance(bystander));
		context.succeed();
	}

	@GameTest
	public void killsByATowerWhoseOwnerIsGonePayNobody(GameTestHelper context) {
		buildFloor(context);
		ServerPlayer someone = context.makeMockServerPlayerInLevel();
		TowerBlockEntity tower = placeTower(context, TowerDefs.ARROW, UUID.randomUUID());
		Mob target = mob(context, 4.0, true);
		target.setHealth(1.0F);
		Rewards.setKillReward(target, 7);

		fire(context, tower, Optional.empty());
		expect(context, !target.isAlive(), "the tower still kills");
		expect(context, Coins.balance(someone) == 0, "an owner who is offline gets nothing and nobody else does, had " + Coins.balance(someone));
		context.succeed();
	}

	// --- cannon ---

	@GameTest
	public void cannonHurtsEveryWaveMobAroundTheTargetAndNothingElse(GameTestHelper context) {
		buildFloor(context);
		TowerBlockEntity tower = placeTower(context, TowerDefs.CANNON, UUID.randomUUID());
		TowerDef.Tier tier = TowerDefs.get(TowerDefs.CANNON).tier(1);
		Mob target = mob(context, 4.0, true);
		Mob beside = mob(context, 4.0 + tier.radius() - 0.5, true);
		Mob beyond = mob(context, 4.0 + tier.radius() + 1.0, true);
		Mob bystander = mob(context, 4.5, false);
		BlockPos wall = context.absolutePos(new BlockPos(4, 1, 2));
		context.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

		fire(context, tower, Optional.empty());
		float damage = (float) tier.damage();
		expect(context, target.getHealth() == 20.0F - damage, "the target takes the blast, health " + target.getHealth());
		expect(context, beside.getHealth() == 20.0F - damage, "a wave mob inside the radius takes it too, health " + beside.getHealth());
		expect(context, beyond.getHealth() == 20.0F, "a wave mob outside the radius is spared, health " + beyond.getHealth());
		expect(context, bystander.getHealth() == 20.0F, "a mob that is not a wave mob is spared, health " + bystander.getHealth());
		expect(context, context.getLevel().getBlockState(wall).is(Blocks.STONE), "the cannon never damages terrain");
		discardAll(List.of(target, beside, beyond, bystander));
		context.succeed();
	}

	// --- frost ---

	@GameTest
	public void frostSlowsMobsAroundTheTargetWithoutHurtingThem(GameTestHelper context) {
		buildFloor(context);
		TowerBlockEntity tower = placeTower(context, TowerDefs.FROST, UUID.randomUUID());
		TowerDef.Tier tier = TowerDefs.get(TowerDefs.FROST).tier(1);
		Mob target = mob(context, 4.0, true);
		Mob beside = mob(context, 4.0 + tier.radius() - 1.0, true);
		Mob beyond = mob(context, 4.0 + tier.radius() + 1.0, true);
		double baseSpeed = target.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);

		expect(context, fire(context, tower, Optional.empty()), "the frost tower should fire");
		for (Mob slowed : List.of(target, beside)) {
			expect(context, slowed.hasEffect(MobEffects.SLOWNESS), "mobs inside the radius are slowed");
			double ratio = slowed.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed;
			double expected = 1.0 - tier.slowPercent() / 100.0;
			expect(context, Math.abs(ratio - expected) < 0.01, "speed should drop to " + expected + " of normal, was " + ratio);
			expect(context, slowed.getHealth() == 20.0F, "frost does no damage, health " + slowed.getHealth());
		}
		expect(context, !beyond.hasEffect(MobEffects.SLOWNESS), "mobs outside the radius are not slowed");
		discardAll(List.of(target, beside, beyond));
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
