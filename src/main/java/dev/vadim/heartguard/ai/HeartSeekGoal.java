package dev.vadim.heartguard.ai;

import dev.vadim.heartguard.mob.MobAttachments;
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
	public static final double REACH = 2.5;
	/** How far above the Heart a flier crosses the map, and how close to it, horizontally, it starts to drop. */
	public static final double CRUISE_HEIGHT = 8.0;
	public static final double DESCEND_RADIUS = 6.0;
	private static final int ATTACK_INTERVAL = 20;
	private static final int REPATH_INTERVAL = 10;
	/** Damage per hit. One value for every mob until mob stats move into data files. */
	public static final int HIT_DAMAGE = 5;

	private final Mob mob;
	private final Target target;
	/** Fliers go straight to the Heart through their move control instead of pathing over the ground. */
	private final boolean flying;
	private final double cruiseHeight;
	private int attackCooldown;
	private int repathCooldown;

	public HeartSeekGoal(Mob mob, Target target, boolean flying, double cruiseHeight) {
		this.mob = mob;
		this.target = target;
		this.flying = flying;
		this.cruiseHeight = cruiseHeight;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public static void attach(Mob mob, Target target) {
		attach(mob, target, false);
	}

	public static void attach(Mob mob, Target target, boolean flying) {
		attach(mob, target, flying, CRUISE_HEIGHT);
	}

	public static void attach(Mob mob, Target target, boolean flying, double cruiseHeight) {
		mob.goalSelector.addGoal(PRIORITY, new HeartSeekGoal(mob, target, flying, cruiseHeight));
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

	/**
	 * Climbs straight up to cruising height, crosses at that height so walls and towers are flown over rather than into,
	 * and drops onto the Heart once close.
	 */
	private void flyToward(Vec3 heart) {
		// Never above the top of the world, or the flier sits against the ceiling and goes nowhere.
		double cruiseY = Math.min(heart.y + cruiseHeight, mob.level().getMaxY() - 2.0);
		double horizontal = Math.hypot(heart.x - mob.getX(), heart.z - mob.getZ());
		if (horizontal <= DESCEND_RADIUS) {
			mob.getMoveControl().setWantedPosition(heart.x, heart.y + 0.5, heart.z, 1.0);
		} else if (mob.getY() < cruiseY - 1.0) {
			mob.getMoveControl().setWantedPosition(mob.getX(), cruiseY, mob.getZ(), 1.0);
		} else {
			mob.getMoveControl().setWantedPosition(heart.x, cruiseY, heart.z, 1.0);
		}
	}

	@Override
	public void tick() {
		Optional<BlockPos> heart = target.heart();
		if (heart.isEmpty()) {
			return;
		}
		// Fire resistance stops the damage from sunlight but not the flames; put them out so no mob is drawn burning.
		if (mob.isOnFire()) {
			mob.clearFire();
		}
		Vec3 centre = heart.get().getCenter();
		mob.getLookControl().setLookAt(centre);

		if (mob.distanceToSqr(centre) <= REACH * REACH) {
			mob.getNavigation().stop();
			if (flying) {
				mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
			}
			if (--attackCooldown <= 0) {
				attackCooldown = ATTACK_INTERVAL;
				mob.swing(InteractionHand.MAIN_HAND);
				target.damage(mob.getAttachedOrElse(MobAttachments.HEART_DAMAGE, HIT_DAMAGE));
			}
		} else if (flying) {
			flyToward(centre);
		} else if (--repathCooldown <= 0 || mob.getNavigation().isDone()) {
			repathCooldown = REPATH_INTERVAL;
			mob.getNavigation().moveTo(centre.x, heart.get().getY(), centre.z, SPEED);
		}
	}
}
