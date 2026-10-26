package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.ai.HeartSeekGoal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A Heart that only records hits, so a test can watch mobs march without touching the one global match. */
public final class FakeHeartTarget implements HeartSeekGoal.Target {
	public final BlockPos heart;
	public final AtomicBoolean active = new AtomicBoolean(true);
	public final AtomicInteger damage = new AtomicInteger();
	/** Game time of the first hit, or -1 while there has been none. */
	public final AtomicLong firstHit = new AtomicLong(-1);
	private final ServerLevel clock;

	public FakeHeartTarget(ServerLevel clock, BlockPos heart) {
		this.clock = clock;
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
		firstHit.compareAndSet(-1, clock.getGameTime());
	}
}
