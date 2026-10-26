package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.ai.MatchHeartTarget;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;

/** The Heart-seeking goal: mobs walk to the Heart and hurt it, but only while a wave is on. */
public class HeartSeekGoalGameTest {
	/** A target that records hits instead of touching the global match, so these tests cannot disturb each other. */
	private static final class FakeTarget implements HeartSeekGoal.Target {
		final BlockPos heart;
		final AtomicBoolean active = new AtomicBoolean(true);
		final AtomicInteger damage = new AtomicInteger();

		FakeTarget(BlockPos heart) {
			this.heart = heart;
		}

		@Override
		public boolean active() {
			return active.get();
		}

		@Override
		public Optional<BlockPos> heart() {
			return Optional.of(heart);
		}

		@Override
		public void damage(int amount) {
			damage.addAndGet(amount);
		}
	}

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	/** A flat stone floor so navigation has something to walk on; small, to stay inside the chunks the test keeps loaded. */
	private static void buildFloor(GameTestHelper context) {
		for (int x = -4; x <= 8; x++) {
			for (int z = -4; z <= 4; z++) {
				context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static Mob spawnHusk(GameTestHelper context, BlockPos relative, FakeTarget target) {
		Mob husk = (Mob) EntityType.HUSK.spawn(context.getLevel(), context.absolutePos(relative), EntitySpawnReason.EVENT);
		husk.setPersistenceRequired();
		HeartSeekGoal.attach(husk, target);
		// Vanilla AI would chase any player nearby, including mock players other tests leave in the world.
		husk.goalSelector.removeAllGoals(goal -> !(goal instanceof HeartSeekGoal));
		return husk;
	}

	@GameTest(maxTicks = 600)
	public void aMobWalksToTheHeartAndHurtsIt(GameTestHelper context) {
		buildFloor(context);
		BlockPos heartRelative = new BlockPos(6, 1, 0);
		context.setBlock(heartRelative, ModBlocks.HEART);
		FakeTarget target = new FakeTarget(context.absolutePos(heartRelative));
		Mob husk = spawnHusk(context, new BlockPos(-3, 1, 0), target);

		context.succeedWhen(() -> {
			expect(context, target.damage.get() >= HeartSeekGoal.HIT_DAMAGE, "the husk should have reached the Heart and hit it, damage so far " + target.damage.get()
				+ ", husk alive=" + husk.isAlive() + " noAi=" + husk.isNoAi() + " pos=" + husk.position() + " heart=" + target.heart
				+ " navDone=" + husk.getNavigation().isDone() + " onGround=" + husk.onGround());
			husk.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void mobsStayPutWhenNoWaveIsOn(GameTestHelper context) {
		buildFloor(context);
		BlockPos heartRelative = new BlockPos(6, 1, 0);
		context.setBlock(heartRelative, ModBlocks.HEART);
		FakeTarget target = new FakeTarget(context.absolutePos(heartRelative));
		target.active.set(false);
		Mob husk = spawnHusk(context, new BlockPos(-3, 1, 0), target);
		double startX = husk.getX();

		context.runAfterDelay(80, () -> {
			double moved = Math.abs(husk.getX() - startX);
			husk.discard();
			expect(context, moved < 2.0, "the husk should not march while no wave is on, moved " + moved);
			expect(context, target.damage.get() == 0, "no wave means no damage, was " + target.damage.get());
			context.succeed();
		});
	}

	@GameTest
	public void theMatchTargetFollowsThePhaseAndHurtsTheHeart(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		BlockPos heart = new BlockPos(300, 80, 300);
		BlockPos spawn = new BlockPos(310, 80, 300);
		MatchHeartTarget target = new MatchHeartTarget(server);
		try {
			Match.update(server, d -> MatchData.EMPTY.withHeart(heart).withSpawn(spawn).started(1));
			expect(context, !target.active(), "mobs do not march during the build phase");

			Match.update(server, MatchData::advanced);
			expect(context, target.active(), "mobs march during the wave");
			expect(context, target.heart().equals(Optional.of(heart)), "the target is the match Heart");

			target.damage(HeartSeekGoal.HIT_DAMAGE);
			expect(context, Match.get(server).heartHp() == MatchData.MAX_HEART_HP - HeartSeekGoal.HIT_DAMAGE, "a hit lowers the match Heart HP");
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	@GameTest
	public void spawnedWaveMobsGetTheGoal(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos spawn = context.absolutePos(new BlockPos(0, 1, 0));
		WaveSet set = new WaveSet(List.of(new WaveSet.Wave(List.of(new WaveSet.SpawnEntry(0, EntityType.HUSK, 1, 20, 0)))));
		MatchData data = MatchData.EMPTY.withHeart(spawn.east(10)).withSpawn(spawn).started(1).advanced();
		try {
			WaveSpawner.tick(level, data, set);
			Mob spawned = null;
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob mob && mob.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
					spawned = mob;
				}
			}
			expect(context, spawned != null, "the spawner should have spawned a wave mob");
			boolean hasGoal = spawned.goalSelector.getAvailableGoals().stream()
				.map(WrappedGoal::getGoal)
				.anyMatch(goal -> goal instanceof HeartSeekGoal);
			expect(context, hasGoal, "wave mobs should be sent to the Heart");
		} finally {
			for (Entity entity : level.getAllEntities()) {
				if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
					entity.discard();
				}
			}
		}
		context.succeed();
	}
}
