package dev.vadim.heartguard.mob;

import com.mojang.serialization.Codec;
import dev.vadim.heartguard.Heartguard;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/** Per-mob values the spawner stamps on a wave mob and the goals read back, saved with the mob. */
public final class MobAttachments {
	/** Heart HP one hit of this mob takes. Absent on plain vanilla wave mobs, which use the default. */
	public static final AttachmentType<Integer> HEART_DAMAGE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "heart_damage"), Codec.INT);

	/** HP of tower damage and wall breaking power of this mob. */
	public static final AttachmentType<Integer> STRUCTURE_DAMAGE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "structure_damage"), Codec.INT);

	private MobAttachments() {}

	/** Loads the class so the attachment type is registered before any world is read. */
	public static void init() {}
}
