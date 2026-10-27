package dev.vadim.heartguard.wave;

import dev.vadim.heartguard.match.Match;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends every client the next-wave preview when a player joins and whenever the match changes phase or wave. */
public final class WavePreviewSync {
	private WavePreviewSync() {}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(WavePreview.TYPE, WavePreview.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(current(server)));
	}

	public static WavePreview current(MinecraftServer server) {
		return WavePreview.of(Match.get(server), WaveSets.active());
	}

	public static void broadcast(MinecraftServer server) {
		WavePreview preview = current(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, preview);
		}
	}
}
