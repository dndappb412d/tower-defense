package dev.vadim.heartguard.match;

import dev.vadim.heartguard.map.MapLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The end of a match: every player gets a win or lose screen with the result, and its two buttons come back here as
 * intents. The server only acts on them while the match is actually over, so a stale or forged click does nothing.
 */
public final class EndScreens {
	private EndScreens() {}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(EndScreenPayload.TYPE, EndScreenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EndActionPayload.TYPE, EndActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EndActionPayload.TYPE, (payload, context) -> {
			String problem = act(context.server(), context.player(), payload.action());
			if (problem != null) {
				context.player().sendSystemMessage(Component.literal(problem), true);
			}
		});
	}

	/** The summary a player is shown for a finished match. */
	public static EndScreenPayload summary(MatchData data) {
		return new EndScreenPayload(data.phase() == MatchData.Phase.WON, data.wave(), data.totalWaves(), data.heartHp(), data.map().isPresent());
	}

	/** Called when the match changes phase: a win or a loss opens the end screen for everyone. */
	public static void onPhase(MinecraftServer server, MatchData before, MatchData after) {
		boolean over = after.phase() == MatchData.Phase.WON || after.phase() == MatchData.Phase.LOST;
		if (!over || before.phase() == after.phase()) {
			return;
		}
		EndScreenPayload payload = summary(after);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Runs a button press. Returns a message for the player when it could not be done, or null on success. */
	public static String act(MinecraftServer server, ServerPlayer player, EndActionPayload.Action action) {
		MatchData data = Match.get(server);
		if (data.phase() != MatchData.Phase.WON && data.phase() != MatchData.Phase.LOST) {
			return "The match is not over";
		}
		switch (action) {
			case LEAVE -> MapLoader.leave(server, player);
			case REPLAY -> {
				if (data.map().isEmpty()) {
					return "There is no map to play again";
				}
				MapLoader.PlayResult result = MapLoader.play(server, player, data.map().get(), false);
				if (result != MapLoader.PlayResult.STARTED) {
					return "Cannot play again: " + result;
				}
			}
		}
		return null;
	}
}
