package dev.vadim.heartguard.mob;

import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * What makes a boss a boss: a boss bar for every player in the level and, on a timer, reinforcements. It runs beside
 * the Heart-seeking goal and takes none of the mob's control.
 */
public class BossAbilityGoal extends Goal {
	/** Scoreboard tag on mobs a boss summoned, so the cap counts only them. */
	public static final String SUMMONED_TAG = "heartguard_summoned";

	private static final Map<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();

	private final Mob boss;
	private final MobDef.Ability ability;
	private final HeartSeekGoal.Target target;
	private final ServerBossEvent bar;
	private int ticks;

	public BossAbilityGoal(Mob boss, String name, MobDef.Ability ability, HeartSeekGoal.Target target) {
		this.boss = boss;
		this.ability = ability;
		this.target = target;
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(name), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
		BARS.put(boss.getUUID(), bar);
		setFlags(EnumSet.noneOf(Flag.class));
	}

	/** Cleans the boss bar up when its boss dies or is removed, so no bar is left on screen. */
	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> clear(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> clear(entity));
	}

	private static void clear(Entity entity) {
		ServerBossEvent bar = BARS.remove(entity.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	public static ServerBossEvent barOf(Entity boss) {
		return BARS.get(boss.getUUID());
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(boss.level() instanceof ServerLevel level)) {
			return;
		}
		ticks++;
		bar.setProgress(Math.max(0.0F, Math.min(1.0F, boss.getHealth() / boss.getMaxHealth())));
		if (ticks % 20 == 1) {
			for (ServerPlayer player : level.players()) {
				bar.addPlayer(player);
			}
		}
		if (ticks % ability.intervalTicks() == 0) {
			summon(level);
		}
	}

	private void summon(ServerLevel level) {
		int alive = 0;
		for (Entity entity : level.getAllEntities()) {
			if (entity.isAlive() && entity.entityTags().contains(SUMMONED_TAG)) {
				alive++;
			}
		}
		int room = Math.max(0, ability.maxAlive() - alive);
		int count = Math.min(ability.count(), room);
		for (int i = 0; i < count; i++) {
			double angle = (Math.PI * 2.0) * i / Math.max(1, count);
			Vec3 at = boss.position().add(Math.cos(angle) * 3.0, 0.0, Math.sin(angle) * 3.0);
			Entity summoned = MobFactory.spawnWaveMob(level, ability.summon(), net.minecraft.core.BlockPos.containing(at), 0, target);
			if (summoned != null) {
				summoned.addTag(SUMMONED_TAG);
			}
		}
	}
}
