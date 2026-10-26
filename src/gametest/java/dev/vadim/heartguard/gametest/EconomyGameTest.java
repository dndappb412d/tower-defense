package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** The coin balance, kill rewards and the wave-clear bonus. */
public class EconomyGameTest {
	private static final Identifier TEST_SET = Identifier.fromNamespaceAndPath("heartguard-test", "paying_wave");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	@GameTest
	public void balanceAddsAndSpends(GameTestHelper context) {
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		expect(context, Coins.balance(player) == 0, "a new player starts with no coins, had " + Coins.balance(player));

		Coins.add(player, 25);
		expect(context, Coins.balance(player) == 25, "adding 25 gives 25, was " + Coins.balance(player));

		Coins.add(player, -10);
		Coins.add(player, 0);
		expect(context, Coins.balance(player) == 25, "adding zero or negative changes nothing");

		expect(context, Coins.spend(player, 10), "spending 10 of 25 succeeds");
		expect(context, Coins.balance(player) == 15, "15 left, was " + Coins.balance(player));
		expect(context, !Coins.spend(player, 16), "spending more than the balance fails");
		expect(context, Coins.balance(player) == 15, "a failed spend takes nothing");
		expect(context, !Coins.spend(player, -5), "a negative spend is refused");
		expect(context, Coins.spend(player, 15) && Coins.balance(player) == 0, "spending the exact balance empties it");
		context.succeed();
	}

	@GameTest
	public void clearBonusScalesWithHeartHealth(GameTestHelper context) {
		expect(context, Rewards.clearBonus(100, MatchData.MAX_HEART_HP) == 100, "an untouched Heart pays the full bonus");
		expect(context, Rewards.clearBonus(100, 0) == 50, "a nearly dead Heart pays half");
		expect(context, Rewards.clearBonus(100, 50) == 75, "half health pays three quarters, was " + Rewards.clearBonus(100, 50));
		expect(context, Rewards.clearBonus(0, 80) == 0, "a zero base pays nothing");
		expect(context, Rewards.clearBonus(100, 500) == 100 && Rewards.clearBonus(100, -5) == 50, "out of range HP is clamped");
		context.succeed();
	}

	private static Mob spawnRewardHusk(GameTestHelper context, int reward) {
		Mob husk = (Mob) EntityType.HUSK.spawn(context.getLevel(), context.absolutePos(new BlockPos(0, 1, 0)), EntitySpawnReason.EVENT);
		husk.setNoAi(true);
		Rewards.setKillReward(husk, reward);
		return husk;
	}

	@GameTest
	public void killingAWaveMobPaysTheKiller(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		ServerPlayer killer = context.makeMockServerPlayerInLevel();
		ServerPlayer bystander = context.makeMockServerPlayerInLevel();
		Mob husk = spawnRewardHusk(context, 7);

		husk.hurtServer(level, level.damageSources().playerAttack(killer), 1000.0F);
		expect(context, !husk.isAlive(), "the husk should be dead");
		expect(context, Coins.balance(killer) == 7, "the killer gets the reward, had " + Coins.balance(killer));
		expect(context, Coins.balance(bystander) == 0, "other players get nothing, had " + Coins.balance(bystander));
		context.succeed();
	}

	@GameTest
	public void otherDeathsPayNothing(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		Mob husk = spawnRewardHusk(context, 7);

		husk.hurtServer(level, level.damageSources().generic(), 1000.0F);
		expect(context, !husk.isAlive(), "the husk should be dead");
		expect(context, Coins.balance(player) == 0, "a kill by no player pays nobody, had " + Coins.balance(player));

		Mob plain = (Mob) EntityType.HUSK.spawn(level, context.absolutePos(new BlockPos(1, 1, 0)), EntitySpawnReason.EVENT);
		plain.setNoAi(true);
		plain.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
		expect(context, Coins.balance(player) == 0, "a mob that is not a wave mob pays nothing, had " + Coins.balance(player));
		context.succeed();
	}

	@GameTest
	public void theSpawnerStampsTheRewardFromTheFile(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(0, 1, 0));
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(List.of(new WaveSet.SpawnEntry(0, EntityType.HUSK, 1, 20, 0, 11)))));
		MatchData data = MatchData.EMPTY.withHeart(spawn.east(10)).withSpawn(spawn).started(1).advanced();
		try {
			WaveSpawner.tick(level, data, set);
			Integer reward = null;
			for (Entity entity : level.getAllEntities()) {
				if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
					reward = entity.getAttached(Rewards.KILL_REWARD);
				}
			}
			expect(context, reward != null && reward == 11, "the spawned mob should carry the file reward 11, was " + reward);
		} finally {
			for (Entity entity : level.getAllEntities()) {
				if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
					entity.discard();
				}
			}
		}
		context.succeed();
	}

	@GameTest
	public void clearingAWavePaysEveryPlayerTheScaledBonus(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel level = server.overworld();
		ServerPlayer first = context.makeMockServerPlayerInLevel();
		ServerPlayer second = context.makeMockServerPlayerInLevel();
		int firstBefore = Coins.balance(first);
		int secondBefore = Coins.balance(second);
		BlockPos spawn = context.absolutePos(new BlockPos(0, 1, 0));
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(
			List.of(new WaveSet.SpawnEntry(0, EntityType.HUSK, 1, 5, 0)),
			40
		)));
		Identifier previous = WaveSets.activeId();
		try {
			WaveSets.override(TEST_SET, set);
			WaveSets.setActive(TEST_SET);
			Match.update(server, d -> MatchData.EMPTY.withHeart(spawn.east(5)).withSpawn(spawn).started(1).advanced().damaged(50));
			expect(context, Match.get(server).heartHp() == 50, "setup: Heart at half health");

			int ticks = 0;
			while (Match.get(server).phase() == Phase.WAVE && ticks < 200) {
				Match.tick(server);
				ticks++;
				if (WaveSpawner.countAlive(level) > 0) {
					for (Entity entity : level.getAllEntities()) {
						if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
							entity.discard();
						}
					}
				}
			}
			expect(context, Match.get(server).phase() == Phase.PAYOUT, "the wave should have been cleared, phase " + Match.get(server).phase());

			int expected = Rewards.clearBonus(40, 50);
			expect(context, expected == 30, "40 at half health is 30, was " + expected);
			expect(context, Coins.balance(first) - firstBefore == expected, "first player gets the bonus, gained " + (Coins.balance(first) - firstBefore));
			expect(context, Coins.balance(second) - secondBefore == expected, "second player gets the bonus, gained " + (Coins.balance(second) - secondBefore));
		} finally {
			for (Entity entity : level.getAllEntities()) {
				if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
					entity.discard();
				}
			}
			WaveSets.setActive(previous);
			WaveSets.clearOverride(TEST_SET);
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	@GameTest
	public void waveFilesCanSetRewardsAndBonuses(GameTestHelper context) {
		WaveSet set = WaveSet.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("""
			{ "waves": [
				{ "clear_bonus": 77, "spawns": [ { "spawn": 0, "mob": "minecraft:husk", "count": 1, "reward": 9 } ] },
				{ "spawns": [ { "spawn": 0, "mob": "minecraft:husk", "count": 1 } ] }
			] }
			""")).result().orElseThrow();
		expect(context, set.waves().get(0).clearBonus() == 77 && set.waves().get(0).spawns().get(0).reward() == 9, "explicit values are read");
		expect(context, set.waves().get(1).clearBonus() == WaveSet.Wave.DEFAULT_CLEAR_BONUS, "clear_bonus has a default");
		expect(context, set.waves().get(1).spawns().get(0).reward() == WaveSet.SpawnEntry.DEFAULT_REWARD, "reward has a default");
		context.succeed();
	}
}
