package dev.vadim.heartguard.vein;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.data.DataRegistry;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

/**
 * One kind of gold vein, parsed from {@code data/<namespace>/veins/<name>.json}: {@code { "payout": 10 }}. The payout is
 * the coins paid for mining it once; a map decides how rich its veins are by choosing which kind to place where.
 */
public record VeinDef(int payout) {
	public static final Codec<VeinDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		ExtraCodecs.POSITIVE_INT.fieldOf("payout").forGetter(VeinDef::payout)
	).apply(i, VeinDef::new));

	public static final Identifier POOR = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "poor");
	public static final Identifier RICH = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "rich");

	private static final DataRegistry<VeinDef> REGISTRY = new DataRegistry<>("veins", CODEC);

	public static @Nullable VeinDef get(Identifier id) {
		return REGISTRY.get(id);
	}

	public static Map<Identifier, VeinDef> all() {
		return REGISTRY.all();
	}

	public static void override(Identifier id, VeinDef def) {
		REGISTRY.override(id, def);
	}

	public static void clearOverride(Identifier id) {
		REGISTRY.clearOverride(id);
	}

	public static void init() {
		REGISTRY.register();
	}
}
