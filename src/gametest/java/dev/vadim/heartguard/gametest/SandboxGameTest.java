package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;

/** Sandbox rules, as pure transitions: nothing can be lost, nothing times out, and the waves go round again. */
public class SandboxGameTest {
	private static final int TOTAL_WAVES = 3;

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static MatchData sandbox() {
		return MatchData.EMPTY.withHeart(new BlockPos(10, 64, 10)).withSpawn(new BlockPos(40, 64, 10)).withSandbox(true).started(TOTAL_WAVES);
	}

	@GameTest
	public void theHeartCannotBeHurt(GameTestHelper context) {
		MatchData hit = sandbox().damaged(1000);
		expect(context, hit.heartHp() == MatchData.MAX_HEART_HP && hit.phase() == Phase.BUILD, "a sandbox Heart takes no damage, was " + hit.heartHp());
		MatchData normal = sandbox().withSandbox(false).damaged(1000);
		expect(context, normal.phase() == Phase.LOST, "a normal match is still lost at 0 HP");
		context.succeed();
	}

	@GameTest
	public void buildNeverTimesOut(GameTestHelper context) {
		MatchData waiting = sandbox();
		for (int i = 0; i < MatchData.BUILD_TICKS * 2; i++) {
			waiting = waiting.ticked();
		}
		expect(context, waiting.phase() == Phase.BUILD && waiting.wave() == 0, "the build phase waits for the player, was " + waiting.phase());
		expect(context, waiting.advanced().phase() == Phase.WAVE && waiting.advanced().wave() == 1, "advancing starts wave 1");
		context.succeed();
	}

	@GameTest
	public void wavesRepeatInsteadOfWinning(GameTestHelper context) {
		MatchData data = sandbox();
		for (int round = 0; round < TOTAL_WAVES * 2; round++) {
			data = data.advanced();
			expect(context, data.phase() == Phase.WAVE && data.wave() >= 1 && data.wave() <= TOTAL_WAVES, "wave " + data.wave() + " runs in round " + round);
			data = data.advanced().advanced();
			expect(context, data.phase() == Phase.BUILD, "back to build after wave, was " + data.phase());
		}
		expect(context, data.phase() != Phase.WON, "a sandbox match is never won");
		context.succeed();
	}

	@GameTest
	public void jumpStartsAnyWaveBetweenWaves(GameTestHelper context) {
		MatchData jumped = sandbox().jumpedTo(3);
		expect(context, jumped.phase() == Phase.WAVE && jumped.wave() == 3 && jumped.waveTick() == 0, "jump to wave 3 from build");
		expect(context, jumped.jumpedTo(1) == jumped, "not while a wave is running");
		expect(context, sandbox().jumpedTo(4).equals(sandbox()) && sandbox().jumpedTo(0).equals(sandbox()), "outside the set does nothing");
		MatchData payout = jumped.advanced();
		expect(context, payout.phase() == Phase.PAYOUT && payout.jumpedTo(2).wave() == 2, "and from the pause after a wave");
		MatchData normal = sandbox().withSandbox(false);
		expect(context, normal.jumpedTo(2) == normal, "a normal match cannot jump");
		context.succeed();
	}

	@GameTest
	public void flagSurvivesSavingAndStopResetsIt(GameTestHelper context) {
		MatchData data = sandbox();
		MatchData loaded = MatchData.CODEC.parse(NbtOps.INSTANCE, MatchData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
		expect(context, loaded.sandbox() && loaded.equals(data), "the flag is saved with the match");
		expect(context, !data.stopped().sandbox(), "stopping ends sandbox mode");
		expect(context, !MatchData.EMPTY.sandbox(), "and nothing starts in it");
		context.succeed();
	}
}
