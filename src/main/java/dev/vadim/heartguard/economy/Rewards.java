package dev.vadim.heartguard.economy;

import com.mojang.serialization.Codec;
import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.match.MatchData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** What kills and cleared waves pay. The amounts come from the wave files; this only applies them. */
public final class Rewards {
	/** Coins a wave mob pays when a player kills it. Set by the spawner from the wave file. */
	public static final AttachmentType<Integer> KILL_REWARD = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "kill_reward"),
		Codec.INT
	);

	private Rewards() {}

	public static void init() {
		// A kill pays the player who made it. Kills by anything else (towers, later) pay nobody until
		// towers have owners; a mob with no reward attachment is not a wave mob and pays nothing.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Integer reward = entity.getAttached(KILL_REWARD);
			if (reward != null && source.getEntity() instanceof ServerPlayer killer) {
				Coins.add(killer, reward);
			}
		});
	}

	public static void setKillReward(Entity mob, int reward) {
		mob.setAttached(KILL_REWARD, reward);
	}

	/**
	 * The wave-clear bonus: the wave base, scaled between half (Heart nearly dead) and full (Heart untouched), so
	 * a clean defense pays more.
	 */
	public static int clearBonus(int base, int heartHp) {
		double health = Math.clamp(heartHp, 0, MatchData.MAX_HEART_HP) / (double) MatchData.MAX_HEART_HP;
		return (int) Math.round(base * (0.5 + 0.5 * health));
	}
}
