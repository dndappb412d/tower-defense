package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.client.EndScreen;
import dev.vadim.heartguard.map.MapLoader;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Losing and winning the meadow in a real world: the screen opens on the client with the right result, "Play again"
 * starts a fresh match in the arena, and "Leave" returns the player to the overworld. Both buttons are clicked with the
 * mouse, so the intents and the server's checks are exercised for real.
 */
public class EndScreensClientTest implements FabricClientGameTest {
	private TestSingleplayerContext world;
	private ClientGameTestContext context;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		try (TestSingleplayerContext created = context.worldBuilder().create()) {
			this.world = created;
			world.getClientLevel().waitForChunksRender();

			// Lose: the Heart takes more than it has.
			run("heartguard play meadow survival");
			waitForDimension(MapLoader.ARENA);
			world.getServer().runOnServer(server -> Match.damageHeart(server, 1000));
			EndScreen lost = waitForScreen();
			check(!lost.result().won() && lost.result().canReplay(), "the defeat screen opens, with replay offered: " + lost.result());
			check(phase() == Phase.LOST, "the match is lost, was " + phase());
			context.takeScreenshot("end-defeat");

			click(true);
			context.waitTicks(10);
			MatchData again = world.getServer().computeOnServer(Match::get);
			check(again.phase() == Phase.BUILD && again.wave() == 0 && again.heartHp() == MatchData.MAX_HEART_HP, "Play again starts a fresh match, was " + again.phase() + " Heart " + again.heartHp());
			check(again.dimension().equals(MapLoader.ARENA_ID), "in the arena");
			check(context.computeOnClient(mc -> mc.screen == null), "and the screen closed");

			// Win: every wave cleared.
			world.getServer().runOnServer(server -> {
				for (int i = 0; i < 9; i++) {
					Match.update(server, MatchData::advanced);
				}
			});
			EndScreen won = waitForScreen();
			check(won.result().won() && won.result().wave() == won.result().total(), "the victory screen opens: " + won.result());
			context.takeScreenshot("end-victory");

			click(false);
			waitForDimension(Level.OVERWORLD);
			check(phase() == Phase.IDLE, "Leave stops the match, was " + phase());
		}
	}

	private EndScreen waitForScreen() {
		for (int tick = 0; tick < 100; tick++) {
			context.waitTick();
			EndScreen screen = context.computeOnClient(mc -> mc.screen instanceof EndScreen s ? s : null);
			if (screen != null) {
				context.waitTicks(3);
				return screen;
			}
		}
		throw new AssertionError("the end screen never opened");
	}

	private void click(boolean playAgain) {
		double[] at = context.computeOnClient(mc -> ((EndScreen) mc.screen).windowCentreOf(playAgain));
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(0);
		context.waitTicks(5);
	}

	private void run(String command) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player), command);
		});
		context.waitTicks(3);
	}

	private void waitForDimension(ResourceKey<Level> dimension) {
		boolean there = false;
		for (int tick = 0; tick < 200 && !there; tick++) {
			context.waitTick();
			there = context.computeOnClient(mc -> mc.level != null && mc.level.dimension().equals(dimension) && mc.player != null);
		}
		check(there, "the client should end up in " + dimension.identifier());
		context.waitTicks(5);
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
