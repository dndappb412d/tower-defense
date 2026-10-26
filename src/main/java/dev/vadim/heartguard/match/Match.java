package dev.vadim.heartguard.match;

import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.vein.Veins;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server-side access to the single match, which lives on the overworld. */
public final class Match {
	private Match() {}

	public static MatchData get(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(MatchData.ATTACHMENT);
	}

	/** Applies a change and announces a phase change to all players. Returns the new state. */
	public static MatchData update(MinecraftServer server, UnaryOperator<MatchData> change) {
		ServerLevel level = server.overworld();
		MatchData before = level.getAttachedOrCreate(MatchData.ATTACHMENT);
		level.modifyAttached(MatchData.ATTACHMENT, change);
		// modifyAttached hands back the old value, so read the new one.
		MatchData after = level.getAttachedOrCreate(MatchData.ATTACHMENT);
		if (after.phase() == MatchData.Phase.PAYOUT && before.phase() != MatchData.Phase.PAYOUT) {
			Veins.regenerate(server);
		}
		if (after.phase() != before.phase() || after.wave() != before.wave()) {
			announce(server, after);
		}
		return after;
	}

	/** The one entry point for hurting the Heart; mobs, traps and tests all go through here. */
	public static MatchData damageHeart(MinecraftServer server, int amount) {
		return update(server, d -> d.damaged(amount));
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Match::tick);
	}

	/** One server tick of the match: spawn due mobs, end a cleared wave, then count the tick. */
	public static void tick(MinecraftServer server) {
		MatchData data = get(server);
		if (!data.running()) {
			return;
		}
		if (data.phase() == MatchData.Phase.WAVE) {
			WaveSet set = WaveSets.active();
			if (set != null && WaveSpawner.tick(server.overworld(), data, set)) {
				int bonus = Rewards.clearBonus(set.waves().get(data.wave() - 1).clearBonus(), data.heartHp());
				Coins.addToAll(server, bonus);
				server.getPlayerList().broadcastSystemMessage(Component.literal("Wave bonus: +" + bonus + " coins"), false);
				update(server, MatchData::advanced);
				return;
			}
		}
		update(server, MatchData::ticked);
	}

	private static void announce(MinecraftServer server, MatchData data) {
		Component message = switch (data.phase()) {
			case BUILD -> Component.literal("Build phase: wave " + (data.wave() + 1) + " is next");
			case WAVE -> Component.literal("Wave " + data.wave() + " begins");
			case PAYOUT -> Component.literal("Wave " + data.wave() + " cleared");
			case WON -> Component.literal("All waves cleared: victory!");
			case LOST -> Component.literal("The Heart has fallen");
			case IDLE -> Component.literal("Match stopped");
		};
		server.getPlayerList().broadcastSystemMessage(message, false);
	}
}
