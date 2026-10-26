package dev.vadim.heartguard.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.data.DataRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

/**
 * One playable map, parsed from {@code data/<namespace>/maps/<name>.json}. The terrain is a structure template; this
 * file says what the match needs on top of it. Every position is relative to the template origin:
 * <pre>
 * { "template": "heartguard:meadow", "waves": "heartguard:survival", "starting_coins": 120,
 *   "player_start": [50, 1, 12], "heart": [56, 1, 20], "spawns": [[4, 1, 12], [4, 1, 28]],
 *   "veins": [ { "pos": [30, 1, 6], "kind": "heartguard:rich" } ],
 *   "command_post": [50, 1, 22], "quartermaster": [50, 1, 18] }
 * </pre>
 * A new map is a new template and a new file, with no code.
 */
public record MapDef(
	Identifier template,
	Identifier waves,
	int startingCoins,
	BlockPos playerStart,
	BlockPos heart,
	List<BlockPos> spawns,
	List<VeinSpot> veins,
	BlockPos commandPost,
	BlockPos quartermaster
) {
	public record VeinSpot(BlockPos pos, Identifier kind) {
		public static final Codec<VeinSpot> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(VeinSpot::pos),
			Identifier.CODEC.fieldOf("kind").forGetter(VeinSpot::kind)
		).apply(i, VeinSpot::new));
	}

	public static final Codec<MapDef> CODEC = RecordCodecBuilder.<MapDef>create(i -> i.group(
		Identifier.CODEC.fieldOf("template").forGetter(MapDef::template),
		Identifier.CODEC.fieldOf("waves").forGetter(MapDef::waves),
		ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("starting_coins", 0).forGetter(MapDef::startingCoins),
		BlockPos.CODEC.fieldOf("player_start").forGetter(MapDef::playerStart),
		BlockPos.CODEC.fieldOf("heart").forGetter(MapDef::heart),
		BlockPos.CODEC.listOf().fieldOf("spawns").forGetter(MapDef::spawns),
		VeinSpot.CODEC.listOf().optionalFieldOf("veins", List.of()).forGetter(MapDef::veins),
		BlockPos.CODEC.fieldOf("command_post").forGetter(MapDef::commandPost),
		BlockPos.CODEC.fieldOf("quartermaster").forGetter(MapDef::quartermaster)
	).apply(i, MapDef::new)).validate(def -> def.spawns.isEmpty()
		? DataResult.<MapDef>error(() -> "A map needs at least one spawn point")
		: DataResult.success(def));

	private static final DataRegistry<MapDef> REGISTRY = new DataRegistry<>("maps", CODEC);

	public MapDef {
		spawns = List.copyOf(spawns);
		veins = List.copyOf(veins);
	}

	/** Every position the map names, so a caller can check them against the template. */
	public List<BlockPos> allPositions() {
		List<BlockPos> all = new ArrayList<>(List.of(playerStart, heart, commandPost, quartermaster));
		all.addAll(spawns);
		veins.forEach(v -> all.add(v.pos()));
		return all;
	}

	/** The first named position outside a template of the given size, or null when they all fit. */
	public @Nullable BlockPos firstOutside(Vec3i size) {
		for (BlockPos pos : allPositions()) {
			if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= size.getX() || pos.getY() >= size.getY() || pos.getZ() >= size.getZ()) {
				return pos;
			}
		}
		return null;
	}

	public static @Nullable MapDef get(Identifier id) {
		return REGISTRY.get(id);
	}

	public static Map<Identifier, MapDef> all() {
		return REGISTRY.all();
	}

	public static void override(Identifier id, MapDef def) {
		REGISTRY.override(id, def);
	}

	public static void clearOverride(Identifier id) {
		REGISTRY.clearOverride(id);
	}

	public static void register() {
		REGISTRY.register();
	}
}
