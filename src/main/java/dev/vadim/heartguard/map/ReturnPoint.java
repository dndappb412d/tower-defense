package dev.vadim.heartguard.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.Heartguard;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Where a player stood before entering the arena, so leaving puts them back. Saved with the player. */
public record ReturnPoint(Identifier dimension, double x, double y, double z, float yaw, float pitch) {
	public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
		Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
		Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
		Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z),
		Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yaw),
		Codec.FLOAT.fieldOf("pitch").forGetter(ReturnPoint::pitch)
	).apply(i, ReturnPoint::new));

	public static final AttachmentType<ReturnPoint> ATTACHMENT = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "return_point"), CODEC);

	/** Loads the class so the attachment type is registered before any player data is read. */
	public static void init() {}

	/** Remembers the player's spot, unless they are already in the arena (playing again keeps the first point). */
	public static void remember(ServerPlayer player) {
		if (player.level().dimension().equals(MapLoader.ARENA)) {
			return;
		}
		player.setAttached(ATTACHMENT, new ReturnPoint(player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
	}

	/** Moves the player back to the remembered spot, or to the overworld spawn when there is none. */
	public static void restore(MinecraftServer server, ServerPlayer player) {
		ReturnPoint point = player.getAttached(ATTACHMENT);
		ServerLevel level = point == null ? null : server.getLevel(ResourceKey.<Level>create(Registries.DIMENSION, point.dimension()));
		if (point != null && level != null) {
			player.teleport(new TeleportTransition(level, new Vec3(point.x(), point.y(), point.z()), Vec3.ZERO, point.yaw(), point.pitch(), TeleportTransition.DO_NOTHING));
			return;
		}
		ServerLevel overworld = server.overworld();
		var spawn = overworld.getRespawnData().pos();
		player.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
	}
}
