package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Whole matches in real time on a real client and server, with real chunks: the husks walk the whole way, the server
 * ticks the match by itself, and nothing calls the match tick by hand. Runs in its own world, so it cannot disturb
 * the unit tests that share the global match.
 */
public class MatchEndToEndClientTest implements FabricClientGameTest {
	private static final Identifier LOSS_SET = Identifier.fromNamespaceAndPath("heartguard-test", "e2e_loss");
	private static final Identifier WIN_SET = Identifier.fromNamespaceAndPath("heartguard-test", "e2e_win");
	private static final int KILL_REWARD = 3;
	private static final int CLEAR_BONUS = 60;
	private static final int WIN_MOBS = 3;
	/** Ten seconds of walking is plenty for 20 blocks; the limits only stop a broken run from hanging. */
	private static final int LOSS_TIMEOUT_TICKS = 1200;
	private static final int WIN_TIMEOUT_TICKS = 600;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			BlockPos origin = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos heart = origin.offset(10, 0, 0);
			BlockPos spawn = origin.offset(-10, 0, 0);

			try {
				loseAMatch(context, world, heart, spawn);
				winAMatch(context, world, heart, spawn);
			} finally {
				world.getServer().runOnServer(server -> {
					WaveSets.clearOverride(LOSS_SET);
					WaveSets.clearOverride(WIN_SET);
					WaveSets.setActive(WaveSets.DEFAULT_ID);
					Match.update(server, d -> MatchData.EMPTY);
					discardWaveMobs(server.overworld());
				});
			}
		}
	}

	/** Nobody defends: the husks walk to the Heart and beat it down. */
	private void loseAMatch(ClientGameTestContext context, TestSingleplayerContext world, BlockPos heart, BlockPos spawn) {
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(List.of(new WaveSet.SpawnEntry(0, EntityType.HUSK, 4, 10, 0)))));
		world.getServer().runOnServer(server -> {
			WaveSets.override(LOSS_SET, set);
			WaveSets.setActive(LOSS_SET);
			server.overworld().setBlock(heart, ModBlocks.HEART.defaultBlockState(), Block.UPDATE_ALL);
			Match.update(server, d -> MatchData.EMPTY.withHeart(heart).withSpawn(spawn).started(1).advanced());
		});

		Phase phase = Phase.WAVE;
		int hpWhenFirstHit = MatchData.MAX_HEART_HP;
		for (int tick = 0; tick < LOSS_TIMEOUT_TICKS && phase == Phase.WAVE; tick++) {
			context.waitTick();
			MatchData data = world.getServer().computeOnServer(Match::get);
			phase = data.phase();
			hpWhenFirstHit = Math.min(hpWhenFirstHit, data.heartHp());
		}
		MatchData end = world.getServer().computeOnServer(Match::get);
		check(end.phase() == Phase.LOST, "an undefended Heart should fall, phase was " + end.phase() + " with " + end.heartHp() + " HP");
		check(end.heartHp() == 0, "a lost match has no Heart HP left, had " + end.heartHp());
		check(hpWhenFirstHit < MatchData.MAX_HEART_HP, "the Heart should have taken damage on the way down");
		context.takeScreenshot("e2e-heart-lost");

		world.getServer().runOnServer(server -> {
			discardWaveMobs(server.overworld());
			Match.update(server, d -> MatchData.EMPTY.withHeart(heart).withSpawn(spawn));
		});
	}

	/** A defender kills every mob as it arrives: the wave clears, pays out and the match is won. */
	private void winAMatch(ClientGameTestContext context, TestSingleplayerContext world, BlockPos heart, BlockPos spawn) {
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(
			List.of(new WaveSet.SpawnEntry(0, EntityType.HUSK, WIN_MOBS, 10, 0, KILL_REWARD)),
			CLEAR_BONUS
		)));
		int coinsBefore = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
		world.getServer().runOnServer(server -> {
			WaveSets.override(WIN_SET, set);
			WaveSets.setActive(WIN_SET);
			Match.update(server, d -> MatchData.EMPTY.withHeart(heart).withSpawn(spawn).started(1).advanced());
		});

		Phase phase = Phase.WAVE;
		int killed = 0;
		int maxTicks = WIN_TIMEOUT_TICKS + MatchData.PAYOUT_TICKS;
		for (int tick = 0; tick < maxTicks && phase != Phase.WON && phase != Phase.LOST; tick++) {
			context.waitTick();
			killed += world.getServer().computeOnServer(MatchEndToEndClientTest::killWaveMobsAsPlayer);
			phase = world.getServer().computeOnServer(server -> Match.get(server).phase());
		}
		MatchData end = world.getServer().computeOnServer(Match::get);
		check(end.phase() == Phase.WON, "a defended Heart should win, phase was " + end.phase());
		check(killed == WIN_MOBS, "every wave mob should have been killed once, killed " + killed);
		check(end.heartHp() == MatchData.MAX_HEART_HP, "the defender kept the Heart untouched, HP " + end.heartHp());

		int gained = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0))) - coinsBefore;
		int expected = WIN_MOBS * KILL_REWARD + CLEAR_BONUS;
		check(gained == expected, "kills (" + WIN_MOBS + " x " + KILL_REWARD + ") plus the clear bonus (" + CLEAR_BONUS + ") should pay "
			+ expected + ", paid " + gained);
		context.waitTicks(5);
		context.takeScreenshot("e2e-match-won");
	}

	/** Kills every living wave mob as the first player, so rewards are paid; returns how many died. */
	private static int killWaveMobsAsPlayer(net.minecraft.server.MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		int killed = 0;
		for (Entity entity : List.copyOf(toList(level))) {
			if (entity instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()
				&& entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
				living.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
				killed++;
			}
		}
		return killed;
	}

	private static List<Entity> toList(ServerLevel level) {
		java.util.ArrayList<Entity> all = new java.util.ArrayList<>();
		level.getAllEntities().forEach(all::add);
		return all;
	}

	private static void discardWaveMobs(ServerLevel level) {
		for (Entity entity : toList(level)) {
			if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
				entity.discard();
			}
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
