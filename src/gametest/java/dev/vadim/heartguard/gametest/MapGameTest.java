package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.map.MapDef;
import dev.vadim.heartguard.map.MapLoader;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.tower.TowerBlock;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.vein.VeinData;
import dev.vadim.heartguard.vein.VeinDef;
import dev.vadim.heartguard.vein.Veins;
import dev.vadim.heartguard.wave.WaveSets;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Maps: that the shipped one is consistent with its template, that building one puts everything where the file says
 * and resets what was there, and that mobs can really walk from every spawn to the Heart. Built in the GameTest world,
 * since the arena only exists in real worlds; the arena itself is covered by {@code ArenaClientTest}.
 */
public class MapGameTest {
	private static final Identifier MEADOW = Identifier.fromNamespaceAndPath("heartguard", "meadow");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static MapDef meadow() {
		return MapDef.get(MEADOW);
	}

	private static StructureTemplate template(GameTestHelper context, MapDef def) {
		return context.getLevel().getServer().getStructureManager().get(def.template()).orElseThrow();
	}

	/** A far corner of the test world, so a map does not collide with other tests' arenas. */
	private static BlockPos origin(GameTestHelper context, int slot) {
		return context.absolutePos(new BlockPos(0, 40, 0)).offset(700 + slot * 100, 0, 700);
	}

	// --- the data ---

	@GameTest
	public void theShippedMapMatchesItsTemplateAndWaves(GameTestHelper context) {
		MapDef def = meadow();
		expect(context, def != null, "the meadow map should be loaded from the datapack, have " + MapDef.all().keySet());
		StructureTemplate template = template(context, def);
		expect(context, def.firstOutside(template.getSize()) == null, "every position in the map file is inside the template: " + def.firstOutside(template.getSize()));
		expect(context, WaveSets.get(def.waves()) != null, "the wave set the map names exists");
		expect(context, !def.spawns().isEmpty() && def.spawns().stream().noneMatch(s -> s.equals(def.heart())), "spawns exist and are not on the Heart");
		expect(context, def.veins().stream().allMatch(v -> ModBlocks.veinBlock(v.kind()) != null && VeinDef.get(v.kind()) != null), "every vein names a known kind");
		long distinct = def.allPositions().stream().distinct().count();
		expect(context, distinct == def.allPositions().size(), "no two things share a position");
		context.succeed();
	}

	@GameTest
	public void badMapFilesAreRejected(GameTestHelper context) {
		String good = "\"template\": \"heartguard:meadow\", \"waves\": \"heartguard:survival\", \"player_start\": [1,1,1], \"heart\": [5,1,5], "
			+ "\"command_post\": [2,1,2], \"quartermaster\": [3,1,3]";
		List<String> bad = List.of(
			"{ " + good + ", \"spawns\": [] }",
			"{ " + good + " }",
			"{ \"waves\": \"heartguard:survival\", \"player_start\": [1,1,1], \"heart\": [5,1,5], \"spawns\": [[1,1,1]], \"command_post\": [2,1,2], \"quartermaster\": [3,1,3] }",
			"{ " + good + ", \"spawns\": [[1,1,1]], \"starting_coins\": -5 }",
			"{ " + good + ", \"spawns\": [[1,1,1]], \"veins\": [ { \"pos\": [4,1,4] } ] }"
		);
		for (String json : bad) {
			expect(context, MapDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isEmpty(), "this map should be rejected: " + json);
		}
		var ok = MapDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{ " + good + ", \"spawns\": [[1,1,1]] }")).result().orElseThrow();
		expect(context, ok.startingCoins() == 0 && ok.veins().isEmpty(), "starting coins and veins have defaults");
		context.succeed();
	}

	@GameTest
	public void positionsOutsideTheTemplateAreCaught(GameTestHelper context) {
		MapDef def = meadow();
		var tiny = new net.minecraft.core.Vec3i(10, 10, 10);
		expect(context, def.firstOutside(tiny) != null, "the meadow does not fit in a 10x10x10 template");
		expect(context, def.firstOutside(template(context, def).getSize()) == null, "but fits in its own");
		context.succeed();
	}

	// --- building ---

	@GameTest
	public void buildingPutsEverythingWhereTheFileSays(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		MapDef def = meadow();
		BlockPos origin = origin(context, 0);
		MapLoader.Layout layout = MapLoader.build(level, def, template(context, def), origin);

		expect(context, level.getBlockState(layout.heart()).is(ModBlocks.HEART), "the Heart block is at " + layout.heart());
		expect(context, level.getBlockState(layout.heart().below()).is(Blocks.POLISHED_ANDESITE), "on the Heart pad");
		expect(context, level.getBlockState(layout.commandPost()).is(ModBlocks.COMMAND_POST), "the Command Post is placed");
		expect(context, level.getBlockState(layout.quartermaster()).is(ModBlocks.QUARTERMASTER), "so is the Quartermaster");
		for (MapDef.VeinSpot vein : def.veins()) {
			expect(context, level.getBlockState(origin.offset(vein.pos())).is(ModBlocks.veinBlock(vein.kind())), "the " + vein.kind() + " vein is at " + vein.pos());
		}
		for (BlockPos spawn : layout.spawns()) {
			expect(context, level.getBlockState(spawn).isAir() && level.getBlockState(spawn.below()).isSolid(), "a spawn point has air to stand in and ground under it: " + spawn);
		}
		expect(context, level.getBlockState(layout.playerStart()).isAir() && level.getBlockState(layout.playerStart().below()).isSolid(), "the player start is standable");
		expect(context, layout.spawns().size() == def.spawns().size() && layout.origin().equals(origin), "the layout reports the same spawns and origin");
		context.succeed();
	}

	@GameTest(maxTicks = 200)
	public void buildingAgainResetsTheArea(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		MinecraftServer server = level.getServer();
		MapDef def = meadow();
		StructureTemplate template = template(context, def);
		BlockPos origin = origin(context, 1);
		MapLoader.Layout layout = MapLoader.build(level, def, template, origin);

		// The chunks are only force-loaded when build returns; entities and navigation need them to be entity-ticking.
		boolean[] done = {false};
		context.succeedWhen(() -> {
			expect(context, level.isPositionEntityTicking(layout.playerStart()) && level.isPositionEntityTicking(layout.heart()), "waiting for the map chunks to tick");
			if (!done[0]) {
				done[0] = true;
				resetsAfterAMess(context, level, def, template, origin, layout);
			}
		});
	}

	private static void resetsAfterAMess(GameTestHelper context, ServerLevel level, MapDef def, StructureTemplate template, BlockPos origin, MapLoader.Layout layout) {
		// Leave a mess: a tower, a mob, a mined-out vein, and dug-up terrain.
		BlockPos towerPos = layout.heart().offset(-6, 0, 0);
		level.setBlock(towerPos, TowerBlock.forTower(TowerDefs.ARROW).defaultBlockState(), Block.UPDATE_ALL);
		Mob mob = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
		mob.setPos(layout.playerStart().getCenter());
		mob.setNoAi(true);
		level.addFreshEntity(mob);
		ServerPlayer miner = context.makeMockServerPlayerInLevel();
		BlockPos veinPos = origin.offset(def.veins().get(0).pos());
		Veins.mine(miner, level, veinPos, level.getBlockState(veinPos));
		BlockPos dug = origin.offset(10, 0, 10);
		level.setBlock(dug, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		expect(context, level.getAttachedOrCreate(VeinData.ATTACHMENT).depleted().stream().anyMatch(e -> e.pos().equals(veinPos)), "setup: the vein is recorded as depleted");

		MapLoader.build(level, def, template, origin);

		expect(context, level.getBlockState(towerPos).isAir(), "the tower is gone after a rebuild");
		expect(context, !mob.isAlive() || mob.isRemoved(), "the mob is gone after a rebuild");
		expect(context, level.getBlockState(veinPos).is(ModBlocks.veinBlock(def.veins().get(0).kind())), "the mined-out vein is a fresh vein again");
		expect(context, level.getAttachedOrCreate(VeinData.ATTACHMENT).depleted().stream().noneMatch(e -> e.pos().equals(veinPos)), "and is no longer waiting to regrow");
		expect(context, level.getBlockState(dug).is(Blocks.GRASS_BLOCK), "dug-up terrain is restored");
	}

	@GameTest(maxTicks = 200)
	public void mobsCanWalkFromEverySpawnToTheHeart(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		MapDef def = meadow();
		MapLoader.Layout layout = MapLoader.build(level, def, template(context, def), origin(context, 2));
		boolean[] done = {false};
		context.succeedWhen(() -> {
			boolean ticking = level.isPositionEntityTicking(layout.heart());
			for (BlockPos spawn : layout.spawns()) {
				ticking &= level.isPositionEntityTicking(spawn);
			}
			expect(context, ticking, "waiting for the map chunks to tick");
			if (!done[0]) {
				done[0] = true;
				checkPaths(context, level, layout);
			}
		});
	}

	private static void checkPaths(GameTestHelper context, ServerLevel level, MapLoader.Layout layout) {
		for (BlockPos spawn : layout.spawns()) {
			Mob walker = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
			walker.setPos(spawn.getCenter().add(0.0, -0.5, 0.0));
			walker.setNoAi(true);
			level.addFreshEntity(walker);
			Path path = walker.getNavigation().createPath(layout.heart(), 1);
			// The Heart is a solid block, so a good path ends beside it; what counts is ending within the goal's reach.
			boolean reaches = path != null && path.getEndNode() != null
				&& path.getEndNode().asVec3().distanceTo(layout.heart().getCenter()) <= HeartSeekGoal.REACH + 0.5;
			walker.discard();
			expect(context, reaches, "a mob should have a path from " + spawn + " to within reach of the Heart at " + layout.heart()
				+ ", path " + (path == null ? "none" : "ends at " + path.getEndNode() + " canReach " + path.canReach()));
		}
	}

	// --- starting a match ---

	@GameTest
	public void playingNeedsAKnownCompleteMapAndAnArena(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		Identifier bogus = Identifier.fromNamespaceAndPath("heartguard-test", "missing_template");
		Identifier noWaves = Identifier.fromNamespaceAndPath("heartguard-test", "missing_waves");
		Identifier tooBig = Identifier.fromNamespaceAndPath("heartguard-test", "outside");
		MapDef base = meadow();
		try {
			MapDef.override(bogus, new MapDef(Identifier.fromNamespaceAndPath("heartguard-test", "nope"), base.waves(), 0, base.playerStart(), base.heart(), base.spawns(), base.veins(), base.commandPost(), base.quartermaster()));
			MapDef.override(noWaves, new MapDef(base.template(), Identifier.fromNamespaceAndPath("heartguard-test", "nope"), 0, base.playerStart(), base.heart(), base.spawns(), base.veins(), base.commandPost(), base.quartermaster()));
			MapDef.override(tooBig, new MapDef(base.template(), base.waves(), 0, base.playerStart(), new BlockPos(999, 1, 1), base.spawns(), base.veins(), base.commandPost(), base.quartermaster()));

			expect(context, MapLoader.play(server, player, Identifier.fromNamespaceAndPath("heartguard-test", "unknown")) == MapLoader.PlayResult.UNKNOWN_MAP, "an unknown map is refused");
			expect(context, MapLoader.play(server, player, bogus) == MapLoader.PlayResult.TEMPLATE_MISSING, "a map whose template is missing is refused");
			expect(context, MapLoader.play(server, player, noWaves) == MapLoader.PlayResult.WAVES_MISSING, "a map whose wave set is missing is refused");
			if (MapLoader.arena(server) == null) {
				expect(context, MapLoader.play(server, player, MEADOW) == MapLoader.PlayResult.NO_ARENA, "without the arena dimension a map cannot be played");
			} else {
				expect(context, MapLoader.play(server, player, tooBig) == MapLoader.PlayResult.MAP_OUTSIDE_TEMPLATE, "a map with a position outside its template is refused");
			}
			expect(context, !Match.get(server).running(), "none of the refusals started a match");
		} finally {
			MapDef.clearOverride(bogus);
			MapDef.clearOverride(noWaves);
			MapDef.clearOverride(tooBig);
		}
		context.succeed();
	}

	// --- where the match runs ---

	@GameTest
	public void theMatchLevelFollowsTheStoredDimension(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		try {
			Match.update(server, d -> MatchData.EMPTY);
			expect(context, Match.level(server) == server.overworld(), "by default the match runs in the overworld");

			Match.update(server, d -> d.withDimension(Identifier.fromNamespaceAndPath("heartguard", "not_loaded")));
			expect(context, Match.level(server) == server.overworld(), "a dimension that is not loaded falls back to the overworld");

			MatchData moved = MatchData.EMPTY.withHeart(new BlockPos(1, 2, 3)).withDimension(MapLoader.ARENA_ID);
			var encoded = MatchData.CODEC.encodeStart(NbtOps.INSTANCE, moved).result().orElseThrow();
			expect(context, MatchData.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow().equals(moved), "the dimension survives saving");
			expect(context, moved.started(2).dimension().equals(MapLoader.ARENA_ID) && moved.stopped().dimension().equals(MapLoader.ARENA_ID), "starting and stopping keep it");
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}
}
