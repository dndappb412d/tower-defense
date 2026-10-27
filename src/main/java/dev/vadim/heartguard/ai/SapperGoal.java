package dev.vadim.heartguard.ai;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A sapper goes for towers first: the nearest one within sight, from any side, then blows itself up on it. When there is
 * no tower near and the way to the Heart is blocked, it blasts the wall in the way instead. With nothing to wreck and a
 * clear path it leaves the walking to {@link HeartSeekGoal}, which blows it up on the Heart.
 */
public class SapperGoal extends Goal {
	private static final int PRIORITY = 0;
	private static final int RECHECK_TICKS = 20;
	private static final double BLAST_RANGE = 1.8;
	public static final double BLAST_RADIUS = 3.5;

	private final Mob mob;
	private final HeartSeekGoal.Target heart;
	private final int structureDamage;
	private int recheck;
	private StructureDamage.Target target;

	public SapperGoal(Mob mob, HeartSeekGoal.Target heart, int structureDamage) {
		this.mob = mob;
		this.heart = heart;
		this.structureDamage = structureDamage;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public static void attach(Mob mob, HeartSeekGoal.Target heart, int structureDamage) {
		mob.goalSelector.addGoal(PRIORITY, new SapperGoal(mob, heart, structureDamage));
	}

	@Override
	public boolean canUse() {
		if (!heart.active() || !(mob.level() instanceof ServerLevel) || heart.heart().isEmpty()) {
			return false;
		}
		if (--recheck > 0) {
			return false;
		}
		recheck = RECHECK_TICKS;
		Vec3 heartCentre = heart.heart().get().getCenter();
		StructureDamage.Target tower = StructureDamage.nearestTarget(mob, heartCentre, true);
		if (tower != null) {
			target = tower;
			return true;
		}
		if (PathCheck.blocked(mob, heart.heart().get())) {
			target = StructureDamage.nearestTarget(mob, heartCentre, false);
		} else {
			target = null;
		}
		return target != null;
	}

	@Override
	public boolean canContinueToUse() {
		return heart.active() && target != null && !mob.level().getBlockState(target.pos()).isAir();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		target = null;
		recheck = 0;
	}

	@Override
	public void tick() {
		if (target == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = target.pos();
		mob.getLookControl().setLookAt(pos.getCenter());
		if (mob.position().distanceTo(pos.getCenter()) > BLAST_RANGE) {
			mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.2);
			return;
		}
		StructureDamage.blast(level, mob, mob.position(), BLAST_RADIUS, structureDamage);
		StructureDamage.spend(level, mob);
	}
}
