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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.vadim.heartguard.block.ModBlocks;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;

/** Match rules: pure state transitions, persistence encoding, and the commands that drive them. */
public class MatchGameTest {
	private static final BlockPos HEART = new BlockPos(10, 64, 10);
	private static final BlockPos SPAWN = new BlockPos(40, 64, 10);
	private static final int TOTAL_WAVES = 3;

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
		expect(context, configured().started(TOTAL_WAVES).checkStart() == StartCheck.ALREADY_RUNNING, "cannot start twice");
		context.succeed();
	}

	@GameTest
	public void phasesCycleThroughBuildWavePayout(GameTestHelper context) {
		MatchData d = configured().started(TOTAL_WAVES);
		expect(context, d.phase() == Phase.BUILD && d.wave() == 0, "starts in build phase before wave 1");
		expect(context, d.heartHp() == MatchData.MAX_HEART_HP, "starts with a full Heart");

		d = d.ticked();
		expect(context, d.ticksLeft() == MatchData.BUILD_TICKS - 1, "build phase counts down");

		d = d.advanced();
		expect(context, d.phase() == Phase.WAVE && d.wave() == 1, "skipping build starts wave 1");
		expect(context, d.ticked().phase() == Phase.WAVE && d.ticked().waveTick() == 1, "a wave counts up and does not end on a timer");

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
	public void clearingTheLastWaveWinsTheMatch(GameTestHelper context) {
		MatchData d = configured().started(1).advanced().advanced();
		expect(context, d.phase() == Phase.PAYOUT && d.wave() == 1, "last wave pays out first");
		d = d.advanced();
		expect(context, d.phase() == Phase.WON, "after the last payout the match is won, was " + d.phase());
		expect(context, !d.running() && d.advanced() == d && d.damaged(5) == d, "a won match is over");
		context.succeed();
	}

	@GameTest
	public void buildTimerStartsTheWave(GameTestHelper context) {
		MatchData d = configured().started(TOTAL_WAVES);
		for (int i = 0; i < MatchData.BUILD_TICKS; i++) {
			d = d.ticked();
		}
		expect(context, d.phase() == Phase.WAVE && d.wave() == 1, "build timer running out starts the wave, was " + d.phase());
		context.succeed();
	}

	@GameTest
	public void stoppedKeepsSetupButResetsProgress(GameTestHelper context) {
		MatchData d = configured().started(TOTAL_WAVES).advanced().stopped();
		expect(context, d.phase() == Phase.IDLE && d.wave() == 0, "stop returns to idle at wave 0");
		expect(context, d.heart().isPresent() && d.spawns().size() == 1, "stop keeps the Heart and spawn setup");
		context.succeed();
	}

	@GameTest
	public void stateSurvivesEncodingRoundTrip(GameTestHelper context) {
		MatchData original = configured().started(TOTAL_WAVES).advanced().advanced();
		var encoded = MatchData.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
		MatchData decoded = MatchData.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
		expect(context, decoded.equals(original), "decoded state should equal the original: " + decoded + " vs " + original);
		context.succeed();
	}

	@GameTest
	public void heartTakesDamageDuringAMatch(GameTestHelper context) {
		MatchData d = configured().started(TOTAL_WAVES).damaged(30);
		expect(context, d.heartHp() == MatchData.MAX_HEART_HP - 30 && d.phase() == Phase.BUILD, "damage lowers HP without ending the match");
		expect(context, MatchData.EMPTY.withHeart(HEART).damaged(30).heartHp() == MatchData.MAX_HEART_HP, "no damage outside a running match");
		expect(context, configured().started(TOTAL_WAVES).damaged(0).heartHp() == MatchData.MAX_HEART_HP, "zero damage changes nothing");
		context.succeed();
	}

	@GameTest
	public void heartAtZeroHpLosesTheMatch(GameTestHelper context) {
		MatchData d = configured().started(TOTAL_WAVES).advanced().damaged(MatchData.MAX_HEART_HP + 50);
		expect(context, d.heartHp() == 0, "HP never goes below zero, was " + d.heartHp());
		expect(context, d.phase() == Phase.LOST, "0 HP loses the match, was " + d.phase());
		expect(context, !d.running() && d.ticked() == d && d.advanced() == d, "a lost match no longer advances");
		expect(context, d.damaged(10) == d, "a lost match takes no more damage");
		MatchData again = d.started(TOTAL_WAVES);
		expect(context, again.phase() == Phase.BUILD && again.heartHp() == MatchData.MAX_HEART_HP && again.wave() == 0, "a lost match can be restarted fresh");
		context.succeed();
	}

	@GameTest
	public void repairHealsUpToTheMaximum(GameTestHelper context) {
		MatchData hurt = configured().started(TOTAL_WAVES).damaged(40);
		expect(context, hurt.repaired(15).heartHp() == MatchData.MAX_HEART_HP - 25, "repair adds HP");
		expect(context, hurt.repaired(1000).heartHp() == MatchData.MAX_HEART_HP, "repair stops at the maximum");
		context.succeed();
	}

	@GameTest
	public void damagingThroughTheServerLosesTheMatch(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		try {
			Match.update(server, d -> configured().started(TOTAL_WAVES));
			Match.damageHeart(server, 40);
			expect(context, Match.get(server).heartHp() == MatchData.MAX_HEART_HP - 40, "server-side damage lowers HP");
			Match.damageHeart(server, MatchData.MAX_HEART_HP);
			expect(context, Match.get(server).phase() == Phase.LOST, "server-side lethal damage loses the match");
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	@GameTest
	public void heartBlockIsPlacedMovedAndUnbreakable(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel overworld = server.overworld();
		BlockPos first = new BlockPos(200, 90, 200);
		BlockPos second = new BlockPos(204, 90, 200);
		try {
			Match.update(server, d -> MatchData.EMPTY);
			CommandSourceStack source = server.createCommandSourceStack();
			server.getCommands().performPrefixedCommand(source.withPosition(first.getCenter()), "heartguard setheart");
			expect(context, overworld.getBlockState(first).is(ModBlocks.HEART), "setheart places the Heart block");
			expect(context, Match.get(server).heart().equals(Optional.of(first)), "the match remembers where the Heart is");
			expect(context, overworld.getBlockState(first).getDestroySpeed(overworld, first) < 0.0F, "the Heart block is unbreakable");

			server.getCommands().performPrefixedCommand(source.withPosition(second.getCenter()), "heartguard setheart");
			expect(context, overworld.getBlockState(second).is(ModBlocks.HEART), "moving the Heart places it at the new position");
			expect(context, overworld.getBlockState(first).isAir(), "moving the Heart removes the old block");
		} finally {
			overworld.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			overworld.setBlock(second, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	@GameTest
	public void updateReturnsTheNewState(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		try {
			MatchData returned = Match.update(server, d -> configured().started(TOTAL_WAVES));
			expect(context, returned.phase() == Phase.BUILD && returned.equals(Match.get(server)), "update returns the state it just stored, got " + returned.phase());
			MatchData next = Match.update(server, MatchData::advanced);
			expect(context, next.phase() == Phase.WAVE && next.wave() == 1, "and again after the next change, got " + next.phase());
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
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
