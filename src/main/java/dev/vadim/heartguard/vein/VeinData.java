package dev.vadim.heartguard.vein;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.Heartguard;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** The veins mined empty and waiting to regrow: where each is and which vein block it was. Saved with the world. */
public record VeinData(List<Entry> depleted) {
	public record Entry(BlockPos pos, Identifier block) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			Identifier.CODEC.fieldOf("block").forGetter(Entry::block)
		).apply(i, Entry::new));
	}

	public static final Codec<VeinData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Entry.CODEC.listOf().fieldOf("depleted").forGetter(VeinData::depleted)
	).apply(i, VeinData::new));

	public static final VeinData EMPTY = new VeinData(List.of());

	public static final AttachmentType<VeinData> ATTACHMENT = AttachmentRegistry.<VeinData>builder()
		.persistent(CODEC)
		.initializer(() -> EMPTY)
		.buildAndRegister(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "veins"));

	public VeinData {
		depleted = List.copyOf(depleted);
	}

	/** Loads the class so the attachment type is registered before any world is read. */
	public static void init() {}

	public VeinData with(Entry entry) {
		List<Entry> next = new ArrayList<>(depleted);
		next.removeIf(e -> e.pos().equals(entry.pos()));
		next.add(entry);
		return new VeinData(next);
	}

	public VeinData without(List<Entry> done) {
		List<Entry> next = new ArrayList<>(depleted);
		next.removeAll(done);
		return new VeinData(next);
	}
}
