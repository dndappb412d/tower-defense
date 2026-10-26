package dev.vadim.heartguard.economy;

import com.mojang.serialization.Codec;
import dev.vadim.heartguard.Heartguard;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The coin balance, stored on the player as a persistent attachment that is synced to that player only. It is not
 * a vanilla item, so gold farms, piglin bartering and loot chests stay out of the economy. All changes go through
 * the server; the client only reads the synced value.
 */
public final class Coins {
	public static final AttachmentType<Integer> BALANCE = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.copyOnDeath()
		.initializer(() -> 0)
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "coins"));

	private Coins() {}

	/** Loads the class so the attachment type is registered before any world or player data is read. */
	public static void init() {}

	/** Works on both sides: on the client it is the last value the server synced. */
	public static int balance(Entity player) {
		return player.getAttachedOrElse(BALANCE, 0);
	}

	/** Adds coins. Non-positive amounts are ignored. */
	public static void add(ServerPlayer player, int amount) {
		if (amount > 0) {
			player.setAttached(BALANCE, balance(player) + amount);
		}
	}

	/** Takes coins if the player can afford them; returns whether it did. */
	public static boolean spend(ServerPlayer player, int amount) {
		if (amount < 0 || balance(player) < amount) {
			return false;
		}
		player.setAttached(BALANCE, balance(player) - amount);
		return true;
	}

	public static void addToAll(MinecraftServer server, int amount) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			add(player, amount);
		}
	}
}
