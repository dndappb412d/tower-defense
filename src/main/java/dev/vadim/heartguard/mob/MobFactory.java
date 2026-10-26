package dev.vadim.heartguard.mob;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.wave.WaveSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/** Turns a mob id from a wave file into a living wave mob: spawned, tuned, tagged, and marching on the Heart. */
public final class MobFactory {
	/** Flying speed in blocks per tick at {@code speed} 1, before the definition's multiplier. About seven blocks a second. */
	public static final double BASE_FLY_SPEED = 0.35;

	private MobFactory() {}

	/**
	 * Spawns one wave mob. {@code id} is a mob definition or a vanilla entity id; the result is null when it names
	 * neither or the entity cannot be created. Everything is added after spawning, never from a load handler.
	 */
	public static @Nullable Entity spawnWaveMob(ServerLevel level, Identifier id, BlockPos at, int reward, HeartSeekGoal.Target heart) {
		MobDef def = MobDef.get(id);
		EntityType<?> type = def != null ? def.base() : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		if (type == null) {
			return null;
		}
		Entity entity = type.spawn(level, at, EntitySpawnReason.EVENT);
		if (entity == null) {
			Heartguard.LOGGER.warn("Could not spawn {} at {}", id, at);
			return null;
		}
		entity.addTag(WaveSpawner.WAVE_MOB_TAG);
		Rewards.setKillReward(entity, reward);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			// Wave mobs never burn in daylight, whatever they are based on.
			mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false), null);
			if (def != null) {
				apply(def, mob);
			}
			attachGoals(def, mob, heart);
		}
		return entity;
	}

	/** Applies a definition's numbers and looks to a freshly spawned mob. */
	public static void apply(MobDef def, Mob mob) {
		set(mob, Attributes.MAX_HEALTH, def.health());
		mob.setHealth((float) def.health());
		set(mob, Attributes.ARMOR, def.armor());
		set(mob, Attributes.KNOCKBACK_RESISTANCE, def.knockbackResistance());
		set(mob, Attributes.SCALE, def.scale());
		if (!def.has(MobDef.Trait.FLIES)) {
			AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.setBaseValue(speed.getBaseValue() * def.speed());
			}
		}
		for (MobDef.Gear gear : def.equipment()) {
			mob.setItemSlot(gear.slot(), new ItemStack(gear.item()));
			mob.setDropChance(gear.slot(), 0.0F);
		}

		mob.setAttached(MobAttachments.HEART_DAMAGE, def.heartDamage());
	}

	private static void set(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void attachGoals(@Nullable MobDef def, Mob mob, HeartSeekGoal.Target heart) {
		boolean flies = def != null && def.has(MobDef.Trait.FLIES);
		if (flies) {
			// A flier's own AI hunts players on its own terms. Replace all of it with a straight flight to the Heart.
			mob.goalSelector.removeAllGoals(goal -> true);
			mob.targetSelector.removeAllGoals(goal -> true);
			mob.moveControl = new HeartFlightControl(mob, BASE_FLY_SPEED * def.speed());
		}
		HeartSeekGoal.attach(mob, heart, flies, def != null ? def.cruiseHeight() : HeartSeekGoal.CRUISE_HEIGHT);
		if (def != null && def.ability().isPresent()) {
			mob.goalSelector.addGoal(2, new BossAbilityGoal(mob, def.name(), def.ability().get(), heart));
		}
	}
}
