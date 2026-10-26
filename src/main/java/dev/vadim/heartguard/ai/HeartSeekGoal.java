package dev.vadim.heartguard.ai;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Sends a mob to the Heart and makes it hit the Heart once in reach. It outranks the vanilla melee goals, so wave
 * mobs ignore players. There is no wall breaking: a mob with no path simply keeps trying until sappers and
 * brutes exist.
 */
public class HeartSeekGoal extends Goal {
	/** What the goal needs from the match; the real match and tests each provide one. */
	public interface Target {
		/** Whether mobs should be marching on the Heart right now. */
		boolean active();

		Optional<BlockPos> heart();

		void damage(int amount);
	}

	private static final int PRIORITY = 1;
	private static final double SPEED = 1.0;
	/** How close, in blocks from the Heart centre, a mob has to be to hit it. */
	private static final double REACH = 2.5;
	private static final int ATTACK_INTERVAL = 20;
	private static final int REPATH_INTERVAL = 10;
	/** Damage per hit. One value for every mob until mob stats move into data files. */
	public static final int HIT_DAMAGE = 5;

	private final Mob mob;
	private final Target target;
	private int attackCooldown;
	private int repathCooldown;

	public HeartSeekGoal(Mob mob, Target target) {
		this.mob = mob;
		this.target = target;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public static void attach(Mob mob, Target target) {
		mob.goalSelector.addGoal(PRIORITY, new HeartSeekGoal(mob, target));
	}

	@Override
	public boolean canUse() {
		return target.active() && target.heart().isPresent();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		attackCooldown = 0;
		repathCooldown = 0;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		Optional<BlockPos> heart = target.heart();
		if (heart.isEmpty()) {
			return;
		}
		Vec3 centre = heart.get().getCenter();
		mob.getLookControl().setLookAt(centre);

		if (mob.distanceToSqr(centre) <= REACH * REACH) {
			mob.getNavigation().stop();
			if (--attackCooldown <= 0) {
				attackCooldown = ATTACK_INTERVAL;
				mob.swing(InteractionHand.MAIN_HAND);
				target.damage(HIT_DAMAGE);
			}
		} else if (--repathCooldown <= 0 || mob.getNavigation().isDone()) {
			repathCooldown = REPATH_INTERVAL;
			mob.getNavigation().moveTo(centre.x, heart.get().getY(), centre.z, SPEED);
		}
	}
}
