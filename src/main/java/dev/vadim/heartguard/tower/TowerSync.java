package dev.vadim.heartguard.tower;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Keeps every client's tower list equal to the server's: sent when a player joins and again after each data reload. */
public final class TowerSync {
	private TowerSync() {}

	public static TowerListPayload snapshot() {
		return new TowerListPayload(TowerDefs.all());
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TowerListPayload.TYPE, TowerListPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(snapshot()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				broadcast(server);
			}
		});
	}

	public static void broadcast(MinecraftServer server) {
		TowerListPayload snapshot = snapshot();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, snapshot);
		}
	}
}
