package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.map.MapDef;
import dev.vadim.heartguard.map.MapLoader;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import dev.vadim.heartguard.placement.PlaceTowerPayload;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDefs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The arena in a real new world, played the way a player would start it: {@code /heartguard play meadow survival}
 * builds the map, moves the player in, and starts a match; the first wave is held by two towers; {@code leave} brings
 * the player back; and playing again resets everything. The arena dimension only exists in real worlds, so this is
 * where the map is proven end to end.
 */
public class ArenaClientTest implements FabricClientGameTest {
	private static final Identifier MEADOW = Identifier.fromNamespaceAndPath("heartguard", "meadow");

	private TestSingleplayerContext world;
	private ClientGameTestContext context;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		try (TestSingleplayerContext created = context.worldBuilder().create()) {
			this.world = created;
			world.getClientLevel().waitForChunksRender();

			check(world.getServer().computeOnServer(server -> server.getLevel(MapLoader.ARENA) != null), "the heartguard:arena dimension should exist in a new world");
			MapDef def = MapDef.get(MEADOW);
			BlockPos origin = MapLoader.ARENA_ORIGIN;

			run("heartguard play meadow survival");
			waitForClientDimension(MapLoader.ARENA);
			world.getClientLevel().waitForChunksRender();
			context.takeScreenshot("map-start");

			// The match is on and everything the file names is there.
			MatchData match = world.getServer().computeOnServer(Match::get);
			check(match.phase() == Phase.BUILD && match.wave() == 0, "playing starts a match in the build phase, was " + match.phase());
			check(match.dimension().equals(MapLoader.ARENA_ID), "the match is set to run in the arena");
			check(match.heart().equals(java.util.Optional.of(origin.offset(def.heart()))), "the Heart is where the map says, was " + match.heart());
			check(match.spawns().size() == def.spawns().size(), "every spawn point is registered");
			check(coins() == def.startingCoins(), "the player starts with the map's coins, had " + coins());
			boolean placed = world.getServer().computeOnServer(server -> {
				ServerLevel arena = server.getLevel(MapLoader.ARENA);
				boolean ok = arena.getBlockState(origin.offset(def.heart())).is(ModBlocks.HEART)
					&& arena.getBlockState(origin.offset(def.commandPost())).is(ModBlocks.COMMAND_POST)
					&& arena.getBlockState(origin.offset(def.quartermaster())).is(ModBlocks.QUARTERMASTER);
				for (MapDef.VeinSpot vein : def.veins()) {
					ok &= arena.getBlockState(origin.offset(vein.pos())).is(ModBlocks.veinBlock(vein.kind()));
				}
				return ok;
			});
			check(placed, "the Heart, Command Post, Quartermaster and every vein are on the map");

			// Two towers at the choke hold the first wave.
			BlockPos first = origin.offset(47, 1, 17);
			BlockPos second = origin.offset(47, 1, 23);
			build(TowerDefs.ARROW, first);
			build(TowerDefs.ARROW, second);
			context.takeScreenshot("map-towers");

			world.getServer().runOnServer(server -> Match.update(server, MatchData::advanced));
			int ticks = 0;
			while (phase() == Phase.WAVE && ticks < 4000) {
				context.waitTick();
				ticks++;
			}
			MatchData afterWave = world.getServer().computeOnServer(Match::get);
			Heartguard.LOGGER.info("[arena] wave 1 on the meadow: phase {}, Heart {}/{}, {} ticks", afterWave.phase(), afterWave.heartHp(), MatchData.MAX_HEART_HP, ticks);
			check(afterWave.phase() == Phase.PAYOUT, "two arrow towers should hold wave 1 on the meadow, phase " + afterWave.phase() + " with Heart " + afterWave.heartHp());
			context.takeScreenshot("map-wave-cleared");

			// Leaving brings the player back and stops the match.
			run("heartguard leave");
			waitForClientDimension(Level.OVERWORLD);
			check(phase() == Phase.IDLE, "leaving stops the match, phase " + phase());

			// Playing again resets the map: no towers, a full Heart, a fresh build phase.
			run("heartguard play meadow survival");
			waitForClientDimension(MapLoader.ARENA);
			boolean towersGone = world.getServer().computeOnServer(server -> {
				ServerLevel arena = server.getLevel(MapLoader.ARENA);
				return arena.getBlockState(first).isAir() && arena.getBlockState(second).isAir() && !(arena.getBlockEntity(first) instanceof TowerBlockEntity);
			});
			check(towersGone, "playing again removes the towers from the last match");
			MatchData again = world.getServer().computeOnServer(Match::get);
			check(again.phase() == Phase.BUILD && again.heartHp() == MatchData.MAX_HEART_HP && again.wave() == 0, "and starts a fresh match, was " + again.phase() + " with Heart " + again.heartHp());
			check(coins() == def.startingCoins(), "with the starting coins again, had " + coins());
			run("heartguard leave");
			waitForClientDimension(Level.OVERWORLD);
		}
	}

	private void run(String command) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), command);
		});
		context.waitTicks(3);
	}

	private void waitForClientDimension(net.minecraft.resources.ResourceKey<Level> dimension) {
		boolean there = false;
		for (int tick = 0; tick < 200 && !there; tick++) {
			context.waitTick();
			there = context.computeOnClient(mc -> mc.level != null && mc.level.dimension().equals(dimension) && mc.player != null);
		}
		check(there, "the client should end up in " + dimension.identifier());
		context.waitTicks(5);
	}

	private void build(Identifier tower, BlockPos pos) {
		context.runOnClient(mc -> ClientPlayNetworking.send(new PlaceTowerPayload(tower, pos)));
		context.waitTicks(5);
		boolean placed = world.getServer().computeOnServer(server -> server.getLevel(MapLoader.ARENA).getBlockEntity(pos) instanceof TowerBlockEntity);
		check(placed, "the " + tower.getPath() + " tower should have been built at " + pos + ", coins " + coins());
	}

	private int coins() {
		return world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
	}

	private Phase phase() {
		return world.getServer().computeOnServer(server -> Match.get(server).phase());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
