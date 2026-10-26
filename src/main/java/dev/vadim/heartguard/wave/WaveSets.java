package dev.vadim.heartguard.wave;

import dev.vadim.heartguard.Heartguard;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/** All loaded wave sets, replaced on every data reload so datapacks can add or override them. */
public final class WaveSets {
	public static final Identifier DEFAULT_ID = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "survival");

	private static volatile Map<Identifier, WaveSet> loaded = Map.of();
	/** Sets added in code (tests); they survive reloads and shadow loaded sets of the same id. */
	private static final Map<Identifier, WaveSet> overrides = new ConcurrentHashMap<>();
	private static volatile Identifier active = DEFAULT_ID;

	private WaveSets() {}

	public static @Nullable WaveSet get(Identifier id) {
		WaveSet override = overrides.get(id);
		return override != null ? override : loaded.get(id);
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

	public static void override(Identifier id, WaveSet set) {
		overrides.put(id, set);
	}

	public static void clearOverride(Identifier id) {
		overrides.remove(id);
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
			Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "waves"),
			new Loader()
		);
	}

	private static final class Loader extends SimpleJsonResourceReloadListener<WaveSet> {
		Loader() {
			super(WaveSet.CODEC, FileToIdConverter.json("waves"));
		}

		@Override
		protected void apply(Map<Identifier, WaveSet> prepared, ResourceManager manager, ProfilerFiller profiler) {
			loaded = Map.copyOf(prepared);
			Heartguard.LOGGER.info("Loaded {} wave set(s): {}", prepared.size(), prepared.keySet());
		}
	}
}
