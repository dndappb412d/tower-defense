package dev.vadim.heartguard.client;

import dev.vadim.heartguard.tower.TowerDef;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The tower types as the server last sent them. The client never reads tower numbers from anywhere else. */
public final class ClientTowers {
	private static volatile Map<Identifier, TowerDef> towers = Map.of();

	private ClientTowers() {}

	public static void set(Map<Identifier, TowerDef> next) {
		towers = Map.copyOf(next);
	}

	public static @Nullable TowerDef get(Identifier id) {
		return towers.get(id);
	}

	public static Map<Identifier, TowerDef> all() {
		return towers;
	}
}
