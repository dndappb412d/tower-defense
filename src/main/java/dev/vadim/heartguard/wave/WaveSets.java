package dev.vadim.heartguard.wave;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.data.DataRegistry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** All loaded wave sets (see {@link DataRegistry}), plus which one new matches use. */
public final class WaveSets {
	public static final Identifier DEFAULT_ID = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "survival");

	private static final DataRegistry<WaveSet> REGISTRY = new DataRegistry<>("waves", WaveSet.CODEC);
	private static volatile Identifier active = DEFAULT_ID;

	private WaveSets() {}

	public static @Nullable WaveSet get(Identifier id) {
		return REGISTRY.get(id);
	}

	public static @Nullable WaveSet active() {
		return get(active);
	}

	public static Identifier activeId() {
		return active;
	}

	/** Chooses the wave set a new match uses. The lobby will call this once it can pick a map. */
	public static void setActive(Identifier id) {
		active = id;
	}

	/** Adds a set in code (tests); it shadows loaded sets and survives reloads. */
	public static void override(Identifier id, WaveSet set) {
		REGISTRY.override(id, set);
	}

	public static void clearOverride(Identifier id) {
		REGISTRY.clearOverride(id);
	}

	public static void init() {
		REGISTRY.register();
	}
}
