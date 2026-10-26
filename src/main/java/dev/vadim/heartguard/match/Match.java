package dev.vadim.heartguard.match;

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
		MatchData after = level.modifyAttached(MatchData.ATTACHMENT, change);
		if (after.phase() != before.phase() || after.wave() != before.wave()) {
			announce(server, after);
		}
		return after;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (get(server).running()) {
				update(server, MatchData::ticked);
			}
		});
	}

	private static void announce(MinecraftServer server, MatchData data) {
		Component message = switch (data.phase()) {
			case BUILD -> Component.literal("Build phase: wave " + (data.wave() + 1) + " is next");
			case WAVE -> Component.literal("Wave " + data.wave() + " begins");
			case PAYOUT -> Component.literal("Wave " + data.wave() + " cleared");
			case LOST -> Component.literal("The Heart has fallen");
			case IDLE -> Component.literal("Match stopped");
		};
		server.getPlayerList().broadcastSystemMessage(message, false);
	}
}
