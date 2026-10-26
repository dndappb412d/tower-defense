package dev.vadim.heartguard.wave;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.ai.MatchHeartTarget;
import dev.vadim.heartguard.match.MatchData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;

/** Spawns the mobs of the current wave on schedule and decides when the wave is over. Stateless: it works from the match data. */
public final class WaveSpawner {
	/** Scoreboard tag on every mob the spawner creates; wave end waits for all of them to be gone. */
	public static final String WAVE_MOB_TAG = "heartguard_wave_mob";
	/** How often the world is scanned for surviving wave mobs, in ticks. */
	private static final int CLEAR_CHECK_INTERVAL = 10;

	private WaveSpawner() {}

	/**
	 * Runs one tick of the current wave: spawns whatever is due at {@code data.waveTick()}, then reports whether the
	 * wave is over (everything spawned and nothing left alive). Call before advancing the match tick.
	 */
	public static boolean tick(ServerLevel level, MatchData data, WaveSet set) {
		int index = data.wave() - 1;
		if (index < 0 || index >= set.waves().size()) {
			return false;
		}
		WaveSet.Wave wave = set.waves().get(index);
		int now = data.waveTick();

		for (WaveSet.Scheduled due : wave.schedule()) {
			if (due.tick() == now) {
				spawn(level, data, due.entry());
			}
		}

		return now > wave.lastSpawnTick() && now % CLEAR_CHECK_INTERVAL == 0 && countAlive(level) == 0;
	}

	public static int countAlive(ServerLevel level) {
		int alive = 0;
		for (Entity entity : level.getAllEntities()) {
			if (entity.isAlive() && entity.entityTags().contains(WAVE_MOB_TAG)) {
				alive++;
			}
		}
		return alive;
	}

	private static void spawn(ServerLevel level, MatchData data, WaveSet.SpawnEntry entry) {
		if (entry.spawn() >= data.spawns().size()) {
			Heartguard.LOGGER.warn("Wave entry uses spawn point {} but the match only has {}", entry.spawn(), data.spawns().size());
			return;
		}
		BlockPos at = data.spawns().get(entry.spawn());
		Entity entity = entry.mob().spawn(level, at, EntitySpawnReason.EVENT);
		if (entity == null) {
			Heartguard.LOGGER.warn("Could not spawn {} at {}", entry.mob(), at);
			return;
		}
		entity.addTag(WAVE_MOB_TAG);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			HeartSeekGoal.attach(mob, new MatchHeartTarget(level.getServer()));
		}
	}
}
