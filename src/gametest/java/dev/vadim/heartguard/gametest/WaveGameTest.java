package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Wave files: parsing and validation, the spawn schedule, and the spawner running a wave end to end. */
public class WaveGameTest {
	private static final Identifier TEST_SET = Identifier.fromNamespaceAndPath("heartguard-test", "two_husks");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static DataResult<WaveSet> parse(String json) {
		return WaveSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	private static WaveSet.SpawnEntry husks(int spawn, int count, int interval, int delay) {
		return new WaveSet.SpawnEntry(spawn, EntityType.HUSK, count, interval, delay);
	}

	private static void discardWaveMobs(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
				entity.discard();
			}
		}
	}

	@GameTest
	public void parsesAValidWaveFile(GameTestHelper context) {
		WaveSet set = parse("""
			{ "waves": [
				{ "spawns": [ { "spawn": 1, "mob": "minecraft:husk", "count": 5, "interval_ticks": 40, "delay_ticks": 10 } ] },
				{ "spawns": [ { "spawn": 0, "mob": "minecraft:spider", "count": 2 } ] }
			] }
			""").result().orElseThrow();
		expect(context, set.waves().size() == 2, "two waves expected, was " + set.waves().size());
		WaveSet.SpawnEntry first = set.waves().get(0).spawns().get(0);
		expect(context, first.spawn() == 1 && first.mob() == EntityType.HUSK && first.count() == 5, "first entry fields: " + first);
		expect(context, first.intervalTicks() == 40 && first.delayTicks() == 10, "first entry timing: " + first);
		WaveSet.SpawnEntry second = set.waves().get(1).spawns().get(0);
		expect(context, second.intervalTicks() == 20 && second.delayTicks() == 0, "timing defaults to 20 and 0: " + second);
		context.succeed();
	}

	@GameTest
	public void rejectsBadWaveFiles(GameTestHelper context) {
		List<String> bad = List.of(
			"{ \"waves\": [] }",
			"{ \"waves\": [ { \"spawns\": [] } ] }",
			"{ \"waves\": [ { \"spawns\": [ { \"spawn\": 0, \"mob\": \"minecraft:no_such_mob\", \"count\": 1 } ] } ] }",
			"{ \"waves\": [ { \"spawns\": [ { \"spawn\": 0, \"mob\": \"minecraft:husk\", \"count\": 0 } ] } ] }",
			"{ \"waves\": [ { \"spawns\": [ { \"spawn\": -1, \"mob\": \"minecraft:husk\", \"count\": 1 } ] } ] }",
			"{ \"waves\": [ { \"spawns\": [ { \"spawn\": 0, \"count\": 1 } ] } ] }",
			"{ \"waves\": [ { \"spawns\": [ { \"spawn\": 0, \"mob\": \"minecraft:husk\", \"count\": 1, \"interval_ticks\": -5 } ] } ] }",
			"{ }"
		);
		for (String json : bad) {
			expect(context, parse(json).result().isEmpty(), "this file should be rejected: " + json);
		}
		context.succeed();
	}

	@GameTest
	public void scheduleFollowsTheTimingInTheFile(GameTestHelper context) {
		WaveSet.Wave wave = new WaveSet.Wave(List.of(husks(0, 3, 10, 5), new WaveSet.SpawnEntry(0, EntityType.SPIDER, 2, 30, 0)));
		List<WaveSet.Scheduled> schedule = wave.schedule();
		expect(context, schedule.size() == 5, "5 spawns expected, was " + schedule.size());
		List<Integer> ticks = schedule.stream().map(WaveSet.Scheduled::tick).toList();
		expect(context, ticks.equals(List.of(0, 5, 15, 25, 30)), "spawn ticks should be sorted and follow the file, was " + ticks);
		expect(context, wave.lastSpawnTick() == 30, "last spawn is at tick 30, was " + wave.lastSpawnTick());
		context.succeed();
	}

	@GameTest
	public void theShippedWavesLoadFromTheDatapack(GameTestHelper context) {
		WaveSet set = WaveSets.get(WaveSets.DEFAULT_ID);
		expect(context, set != null, "heartguard:survival should be loaded from the mod data");
		expect(context, set.waves().size() == 3, "survival ships 3 waves, was " + set.waves().size());
		context.succeed();
	}

	@GameTest
	public void spawnerSpawnsOnScheduleAndEndsWhenCleared(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(0, 1, 0));
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(List.of(husks(0, 3, 10, 5)))));
		MatchData data = MatchData.EMPTY.withHeart(spawn.east(5)).withSpawn(spawn).started(1).advanced();
		expect(context, data.phase() == Phase.WAVE && data.wave() == 1, "setup should be in wave 1");
		try {
			int[] aliveAt = new int[40];
			boolean clearedWhileAlive = false;
			for (int t = 0; t < 40; t++) {
				boolean cleared = WaveSpawner.tick(level, data, set);
				aliveAt[t] = WaveSpawner.countAlive(level);
				clearedWhileAlive |= cleared && aliveAt[t] > 0;
				data = data.ticked();
			}
			expect(context, aliveAt[4] == 0, "nothing spawns before the first delay, was " + aliveAt[4]);
			expect(context, aliveAt[5] == 1 && aliveAt[14] == 1, "first mob appears at tick 5, was " + aliveAt[5]);
			expect(context, aliveAt[15] == 2 && aliveAt[25] == 3, "then one every 10 ticks, was " + aliveAt[15] + "/" + aliveAt[25]);
			expect(context, !clearedWhileAlive, "the wave is not over while its mobs are alive");

			discardWaveMobs(level);
			boolean cleared = false;
			for (int t = 0; t < 20 && !cleared; t++) {
				cleared = WaveSpawner.tick(level, data, set);
				data = data.ticked();
			}
			expect(context, cleared, "the wave ends once everything is spawned and dead");
		} finally {
			discardWaveMobs(level);
		}
		context.succeed();
	}

	@GameTest
	public void aMatchRunsThroughAWaveToVictory(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel level = server.overworld();
		BlockPos spawn = context.absolutePos(new BlockPos(0, 1, 0));
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(List.of(husks(0, 2, 5, 0)))));
		Identifier previous = WaveSets.activeId();
		try {
			WaveSets.override(TEST_SET, set);
			WaveSets.setActive(TEST_SET);
			Match.update(server, d -> MatchData.EMPTY.withHeart(spawn.east(5)).withSpawn(spawn).started(1).advanced());
			expect(context, Match.get(server).phase() == Phase.WAVE, "setup should be in the wave phase");

			int ticks = 0;
			int spawned = 0;
			while (Match.get(server).phase() == Phase.WAVE && ticks < 200) {
				Match.tick(server);
				ticks++;
				int alive = WaveSpawner.countAlive(level);
				spawned = Math.max(spawned, alive);
				if (alive == 2) {
					discardWaveMobs(level);
				}
			}
			expect(context, spawned == 2, "both wave mobs should have been spawned, saw " + spawned);
			expect(context, Match.get(server).phase() == Phase.PAYOUT, "killing the wave moves the match to payout, was " + Match.get(server).phase());

			for (int t = 0; t < MatchData.PAYOUT_TICKS && Match.get(server).phase() == Phase.PAYOUT; t++) {
				Match.tick(server);
			}
			expect(context, Match.get(server).phase() == Phase.WON, "the only wave cleared, so the match is won, was " + Match.get(server).phase());
		} finally {
			discardWaveMobs(level);
			WaveSets.setActive(previous);
			WaveSets.clearOverride(TEST_SET);
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}
}
