package dev.vadim.heartguard.tower;

import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a tower does on a tick: wait out its cooldown, pick a wave mob in range, and fire. Only mobs the wave spawner
 * created are ever targeted or hurt, so a tower never touches players, pets or the rest of the world, and a cannon
 * never breaks blocks. Damage is dealt as the owner, so kills pay the owner through the normal reward event.
 */
public final class TowerCombat {
	/** Vanilla slowness takes this many percent of walking speed per level. */
	private static final int SLOWNESS_PERCENT_PER_LEVEL = 15;

	private TowerCombat() {}

	/** Server tick of a placed tower, using the real match Heart for the {@code FIRST} targeting mode. */
	public static void tick(ServerLevel level, BlockPos pos, TowerBlockEntity tower) {
		tick(level, pos, tower, Match.get(level.getServer()).heart());
	}

	/** Same, with the Heart given explicitly. Returns whether the tower fired this tick. */
	public static boolean tick(ServerLevel level, BlockPos pos, TowerBlockEntity tower, Optional<BlockPos> heart) {
		TowerDef def = tower.def();
		if (def == null) {
			return false;
		}
		if (tower.cooldown() > 0) {
			tower.setCooldown(tower.cooldown() - 1);
			return false;
		}

		TowerDef.Tier tier = def.tier(tower.tier());
		Vec3 from = pos.getCenter();
		Optional<Vec3> heartCentre = heart.map(BlockPos::getCenter);
		List<TowerTargeting.Candidate<LivingEntity>> candidates = new ArrayList<>();
		for (LivingEntity mob : waveMobsNear(level, from, tier.range())) {
			double toHeart = heartCentre.map(h -> mob.position().distanceTo(h)).orElse(Double.POSITIVE_INFINITY);
			candidates.add(new TowerTargeting.Candidate<>(mob, mob.position().distanceTo(from), toHeart, mob.getHealth()));
		}
		TowerTargeting.Candidate<LivingEntity> chosen = TowerTargeting.choose(tower.targeting(), candidates);
		if (chosen == null) {
			return false;
		}

		LivingEntity target = chosen.mob();
		DamageSource source = damageSourceFor(level, tower);
		switch (def.kind()) {
			case SINGLE_TARGET -> shoot(level, from, target, tier, source);
			case AREA -> explode(level, from, target, tier, source);
			case SLOW -> chill(level, from, target, tier);
		}
		tower.setCooldown(tier.fireIntervalTicks());
		return true;
	}

	/** Live wave mobs within {@code range} of {@code centre}. */
	public static List<LivingEntity> waveMobsNear(ServerLevel level, Vec3 centre, double range) {
		AABB box = new AABB(centre, centre).inflate(range);
		return level.getEntitiesOfClass(
			LivingEntity.class,
			box,
			mob -> mob.isAlive() && mob.entityTags().contains(WaveSpawner.WAVE_MOB_TAG) && mob.position().distanceTo(centre) <= range
		);
	}

	/** The owner when they are online, so their kills pay them; otherwise a plain source and the kill pays nobody. */
	private static DamageSource damageSourceFor(ServerLevel level, TowerBlockEntity tower) {
		ServerPlayer owner = tower.owner().map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
		return owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().generic();
	}

	private static void shoot(ServerLevel level, Vec3 from, LivingEntity target, TowerDef.Tier tier, DamageSource source) {
		trail(level, from, target.position().add(0.0, target.getBbHeight() / 2.0, 0.0));
		level.playSound(null, BlockPos.containing(from), SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 0.8F, 1.2F);
		hit(level, target, source, tier.damage());
	}

	private static void explode(ServerLevel level, Vec3 from, LivingEntity target, TowerDef.Tier tier, DamageSource source) {
		Vec3 centre = target.position();
		trail(level, from, centre);
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.5, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.4F);
		// A plain list, because hurting a mob can kill it and change what the world returns next.
		for (LivingEntity mob : List.copyOf(waveMobsNear(level, centre, tier.radius()))) {
			hit(level, mob, source, tier.damage());
		}
	}

	/**
	 * Tower damage ignores vanilla's hurt immunity. Otherwise a mob another tower hit a few ticks ago shrugs this hit off,
	 * and two towers on one target would deal the damage of one.
	 */
	private static void hit(ServerLevel level, LivingEntity mob, DamageSource source, double damage) {
		mob.invulnerableTime = 0;
		mob.hurtServer(level, source, (float) damage);
	}

	private static void chill(ServerLevel level, Vec3 from, LivingEntity target, TowerDef.Tier tier) {
		Vec3 centre = target.position();
		level.playSound(null, BlockPos.containing(from), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.BLOCKS, 0.6F, 1.0F);
		int amplifier = Math.max(0, Math.round(tier.slowPercent() / (float) SLOWNESS_PERCENT_PER_LEVEL) - 1);
		for (LivingEntity mob : waveMobsNear(level, centre, tier.radius())) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, tier.slowTicks(), amplifier, false, true), null);
			level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + 1.0, mob.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		}
	}

	private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		int steps = Math.max(2, (int) from.distanceTo(to));
		for (int i = 1; i < steps; i++) {
			Vec3 at = from.lerp(to, i / (double) steps);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
