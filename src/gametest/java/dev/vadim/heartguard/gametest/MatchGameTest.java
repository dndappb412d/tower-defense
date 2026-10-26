package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import dev.vadim.heartguard.match.MatchData.StartCheck;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;

/** Match rules: pure state transitions, persistence encoding, and the commands that drive them. */
public class MatchGameTest {
	private static final BlockPos HEART = new BlockPos(10, 64, 10);
	private static final BlockPos SPAWN = new BlockPos(40, 64, 10);

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static MatchData configured() {
		return MatchData.EMPTY.withHeart(HEART).withSpawn(SPAWN);
	}

	@GameTest
	public void startNeedsHeartAndSpawn(GameTestHelper context) {
		expect(context, MatchData.EMPTY.checkStart() == StartCheck.NO_HEART, "empty match has no heart");
		expect(context, MatchData.EMPTY.withHeart(HEART).checkStart() == StartCheck.NO_SPAWNS, "heart alone is not enough");
		expect(context, configured().checkStart() == StartCheck.OK, "heart and spawn are enough");
		expect(context, configured().started().checkStart() == StartCheck.ALREADY_RUNNING, "cannot start twice");
		context.succeed();
	}

	@GameTest
	public void phasesCycleThroughBuildWavePayout(GameTestHelper context) {
		MatchData d = configured().started();
		expect(context, d.phase() == Phase.BUILD && d.wave() == 0, "starts in build phase before wave 1");
		expect(context, d.heartHp() == MatchData.MAX_HEART_HP, "starts with a full Heart");

		d = d.ticked();
		expect(context, d.ticksLeft() == MatchData.BUILD_TICKS - 1, "build phase counts down");

		d = d.advanced();
		expect(context, d.phase() == Phase.WAVE && d.wave() == 1, "skipping build starts wave 1");
		expect(context, d.ticked() == d, "a wave does not end on a timer");

		d = d.advanced();
		expect(context, d.phase() == Phase.PAYOUT && d.ticksLeft() == MatchData.PAYOUT_TICKS, "ending a wave pays out");

		for (int i = 0; i < MatchData.PAYOUT_TICKS; i++) {
			d = d.ticked();
		}
		expect(context, d.phase() == Phase.BUILD && d.wave() == 1, "payout ends in the next build phase");
		expect(context, d.ticksLeft() == MatchData.BUILD_TICKS, "next build phase has a full timer");
		context.succeed();
	}

	@GameTest
	public void buildTimerStartsTheWave(GameTestHelper context) {
		MatchData d = configured().started();
		for (int i = 0; i < MatchData.BUILD_TICKS; i++) {
			d = d.ticked();
		}
		expect(context, d.phase() == Phase.WAVE && d.wave() == 1, "build timer running out starts the wave, was " + d.phase());
		context.succeed();
	}

	@GameTest
	public void stoppedKeepsSetupButResetsProgress(GameTestHelper context) {
		MatchData d = configured().started().advanced().stopped();
		expect(context, d.phase() == Phase.IDLE && d.wave() == 0, "stop returns to idle at wave 0");
		expect(context, d.heart().isPresent() && d.spawns().size() == 1, "stop keeps the Heart and spawn setup");
		context.succeed();
	}

	@GameTest
	public void stateSurvivesEncodingRoundTrip(GameTestHelper context) {
		MatchData original = configured().started().advanced().advanced();
		var encoded = MatchData.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
		MatchData decoded = MatchData.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
		expect(context, decoded.equals(original), "decoded state should equal the original: " + decoded + " vs " + original);
		context.succeed();
	}

	@GameTest
	public void commandsDriveTheMatchOnTheServer(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack();
		try {
			Match.update(server, d -> MatchData.EMPTY);
			server.getCommands().performPrefixedCommand(source, "heartguard setheart");
			server.getCommands().performPrefixedCommand(source, "heartguard start");
			expect(context, Match.get(server).phase() == Phase.IDLE, "start is refused without a spawn point");

			server.getCommands().performPrefixedCommand(source, "heartguard addspawn");
			server.getCommands().performPrefixedCommand(source, "heartguard start");
			expect(context, Match.get(server).phase() == Phase.BUILD, "start works once Heart and spawn exist");

			server.getCommands().performPrefixedCommand(source, "heartguard skip");
			expect(context, Match.get(server).phase() == Phase.WAVE && Match.get(server).wave() == 1, "skip starts wave 1");

			server.getCommands().performPrefixedCommand(source, "heartguard stop");
			expect(context, Match.get(server).phase() == Phase.IDLE, "stop returns to idle");
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}
}
