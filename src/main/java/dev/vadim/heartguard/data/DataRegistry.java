package dev.vadim.heartguard.data;

import com.mojang.serialization.Codec;
import dev.vadim.heartguard.Heartguard;
import java.util.HashMap;
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

/**
 * Game content loaded from datapack JSON: every {@code data/<namespace>/<directory>/<name>.json} is parsed with the
 * codec and indexed by {@code namespace:name}. The set is replaced on each data reload, so datapacks can add or
 * override entries. Files that fail to parse are logged and skipped, never half-loaded.
 */
public final class DataRegistry<T> {
	private final Identifier listenerId;
	private final String directory;
	private final Codec<T> codec;
	private volatile Map<Identifier, T> loaded = Map.of();
	/** Entries added in code (tests); they survive reloads and shadow loaded entries of the same id. */
	private final Map<Identifier, T> overrides = new ConcurrentHashMap<>();

	public DataRegistry(String directory, Codec<T> codec) {
		this.listenerId = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, directory);
		this.directory = directory;
		this.codec = codec;
	}

	public @Nullable T get(Identifier id) {
		T override = overrides.get(id);
		return override != null ? override : loaded.get(id);
	}

	/** Every entry, with overrides applied. */
	public Map<Identifier, T> all() {
		Map<Identifier, T> merged = new HashMap<>(loaded);
		merged.putAll(overrides);
		return Map.copyOf(merged);
	}

	public void override(Identifier id, T value) {
		overrides.put(id, value);
	}

	public void clearOverride(Identifier id) {
		overrides.remove(id);
	}

	/** Hooks this registry into server data reloads. Call once from mod initialization. */
	public void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(listenerId, new Loader());
	}

	private final class Loader extends SimpleJsonResourceReloadListener<T> {
		Loader() {
			super(codec, FileToIdConverter.json(directory));
		}

		@Override
		protected void apply(Map<Identifier, T> prepared, ResourceManager manager, ProfilerFiller profiler) {
			loaded = Map.copyOf(prepared);
			Heartguard.LOGGER.info("Loaded {} {} file(s): {}", prepared.size(), directory, prepared.keySet());
		}
	}
}
