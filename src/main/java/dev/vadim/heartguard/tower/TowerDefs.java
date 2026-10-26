package dev.vadim.heartguard.tower;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.data.DataRegistry;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** All loaded tower types (see {@link DataRegistry}). */
public final class TowerDefs {
	public static final Identifier ARROW = id("arrow");
	public static final Identifier CANNON = id("cannon");
	public static final Identifier FROST = id("frost");

	private static final DataRegistry<TowerDef> REGISTRY = new DataRegistry<>("towers", TowerDef.CODEC);

	private TowerDefs() {}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, name);
	}

	public static @Nullable TowerDef get(Identifier id) {
		return REGISTRY.get(id);
	}

	public static Map<Identifier, TowerDef> all() {
		return REGISTRY.all();
	}

	/** Adds a tower type in code (tests); it shadows loaded types and survives reloads. */
	public static void override(Identifier id, TowerDef def) {
		REGISTRY.override(id, def);
	}

	public static void clearOverride(Identifier id) {
		REGISTRY.clearOverride(id);
	}

	public static void init() {
		REGISTRY.register();
	}
}
