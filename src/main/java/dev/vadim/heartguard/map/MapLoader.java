package dev.vadim.heartguard.map;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.vein.VeinData;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Builds a map into a level and starts a match on it. A build always starts by emptying the map area, so playing
 * again is the same as resetting: no leftover towers, mobs, empty veins or edited terrain.
 */
public final class MapLoader {
	public static final Identifier ARENA_ID = Identifier.fromNamespaceAndPath("heartguard", "arena");
	public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION, ARENA_ID);
	/** Where the template goes in the arena. The floor is at this height, so feet are one block above. */
	public static final BlockPos ARENA_ORIGIN = new BlockPos(0, 64, 0);

	public enum PlayResult {
		STARTED,
		UNKNOWN_MAP,
		TEMPLATE_MISSING,
		WAVES_MISSING,
		NO_ARENA,
		MAP_OUTSIDE_TEMPLATE,
		UNKNOWN_MOBS
	}

	/** Where everything ended up, in the level's own coordinates. */
	public record Layout(BlockPos origin, BlockPos playerStart, BlockPos heart, List<BlockPos> spawns, BlockPos commandPost, BlockPos quartermaster) {}

	private MapLoader() {}

	public static @Nullable ServerLevel arena(MinecraftServer server) {
		return server.getLevel(ARENA);
	}

	/** Clears the map area, places the template and puts the Heart, veins, Command Post and Quartermaster on it. */
	public static Layout build(ServerLevel level, MapDef def, StructureTemplate template, BlockPos origin) {
		Vec3i size = template.getSize();
		forceLoad(level, origin, origin.offset(size));
		clear(level, origin, size);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), Block.UPDATE_CLIENTS);

		BlockPos heart = origin.offset(def.heart());
		level.setBlock(heart, ModBlocks.HEART.defaultBlockState(), Block.UPDATE_ALL);
		for (MapDef.VeinSpot vein : def.veins()) {
			Block block = ModBlocks.veinBlock(vein.kind());
			if (block != null) {
				level.setBlock(origin.offset(vein.pos()), block.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		BlockPos post = origin.offset(def.commandPost());
		BlockPos shop = origin.offset(def.quartermaster());
		level.setBlock(post, ModBlocks.COMMAND_POST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(shop, ModBlocks.QUARTERMASTER.defaultBlockState(), Block.UPDATE_ALL);

		List<BlockPos> spawns = new ArrayList<>();
		for (BlockPos spawn : def.spawns()) {
			spawns.add(origin.offset(spawn));
		}
		return new Layout(origin, origin.offset(def.playerStart()), heart, spawns, post, shop);
	}

	/** Empties the box a map of this size occupies, with a margin for what mobs and towers leave around it. */
	public static void clear(ServerLevel level, BlockPos origin, Vec3i size) {
		BlockPos min = origin.offset(-2, -2, -2);
		BlockPos max = origin.offset(size.getX() + 2, size.getY() + 20, size.getZ() + 2);
		AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
			entity.discard();
		}
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		VeinData veins = level.getAttachedOrCreate(VeinData.ATTACHMENT);
		level.setAttached(VeinData.ATTACHMENT, veins.without(veins.depleted().stream().filter(e ->
			e.pos().getX() >= min.getX() && e.pos().getX() <= max.getX() && e.pos().getZ() >= min.getZ() && e.pos().getZ() <= max.getZ()).toList()));
	}

	/**
	 * Starts a match of a map for a player: builds it in the arena, points the match at it, resets the player's coins to
	 * the map's starting amount, and moves them in. Anything already running is stopped first.
	 */
	public static PlayResult play(MinecraftServer server, ServerPlayer player, Identifier mapId) {
		MapDef def = MapDef.get(mapId);
		if (def == null) {
			return PlayResult.UNKNOWN_MAP;
		}
		StructureTemplate template = server.getStructureManager().get(def.template()).orElse(null);
		if (template == null) {
			return PlayResult.TEMPLATE_MISSING;
		}
		WaveSet waves = WaveSets.get(def.waves());
		if (waves == null) {
			return PlayResult.WAVES_MISSING;
		}
		ServerLevel arena = arena(server);
		if (arena == null) {
			return PlayResult.NO_ARENA;
		}
		if (def.firstOutside(template.getSize()) != null) {
			return PlayResult.MAP_OUTSIDE_TEMPLATE;
		}
		if (!waves.unknownMobs().isEmpty()) {
			return PlayResult.UNKNOWN_MOBS;
		}

		Match.update(server, MatchData::stopped);
		Layout layout = build(arena, def, template, ARENA_ORIGIN);

		WaveSets.setActive(def.waves());
		Match.update(server, d -> MatchData.EMPTY.withHeart(layout.heart()).withDimension(ARENA_ID));
		for (BlockPos spawn : layout.spawns()) {
			Match.update(server, d -> d.withSpawn(spawn));
		}
		Match.update(server, d -> d.started(waves.waves().size()));

		ReturnPoint.remember(player);
		player.setAttached(Coins.BALANCE, def.startingCoins());
		player.teleport(new TeleportTransition(arena, Vec3.atBottomCenterOf(layout.playerStart()), Vec3.ZERO, 90.0F, 0.0F, TeleportTransition.DO_NOTHING));
		return PlayResult.STARTED;
	}

	/** Stops the match and sends the player back to where they came from. */
	public static void leave(MinecraftServer server, ServerPlayer player) {
		Match.update(server, MatchData::stopped);
		ReturnPoint.restore(server, player);
	}

	private static void forceLoad(ServerLevel level, BlockPos from, BlockPos to) {
		for (int cx = (from.getX() - 2) >> 4; cx <= (to.getX() + 2) >> 4; cx++) {
			for (int cz = (from.getZ() - 2) >> 4; cz <= (to.getZ() + 2) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
	}
}
