package dev.vadim.heartguard.ai;

import dev.vadim.heartguard.tower.TowerService;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hybrid pathing rule for heavy mobs: while a wave is on and there is no way to the Heart, attack the nearest
 * player-built wall in the way, or the nearest tower. It does nothing while a path exists, and nothing to blocks outside
 * {@link StructureDamage#BREAKABLE_WALLS}, so the map itself can never be chewed through.
 */
public class BreakWallGoal extends Goal {
	private static final int PRIORITY = 0;
	private static final int RECHECK_TICKS = 20;
	private static final double ATTACK_RANGE = 3.0;
	private static final int TOWER_HIT_INTERVAL = 20;
	/** Ticks to break a block of hardness 1 with a structure damage of 10. */
	private static final double BREAK_TICKS_PER_HARDNESS = 30.0;

	private final Mob mob;
	private final HeartSeekGoal.Target heart;
	private final int structureDamage;
	private int recheck;
	private boolean blocked;
	private StructureDamage.Target target;
	private double progress;
	private int towerCooldown;

	public BreakWallGoal(Mob mob, HeartSeekGoal.Target heart, int structureDamage) {
		this.mob = mob;
		this.heart = heart;
		this.structureDamage = structureDamage;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public static void attach(Mob mob, HeartSeekGoal.Target heart, int structureDamage) {
		mob.goalSelector.addGoal(PRIORITY, new BreakWallGoal(mob, heart, structureDamage));
	}

	@Override
	public boolean canUse() {
		if (!heart.active() || heart.heart().isEmpty() || !(mob.level() instanceof ServerLevel)) {
			return false;
		}
		if (--recheck <= 0) {
			recheck = RECHECK_TICKS;
			blocked = PathCheck.blocked(mob, heart.heart().get());
		}
		if (!blocked) {
			return false;
		}
		target = StructureDamage.nearestTarget(mob, heart.heart().get().getCenter(), false);
		return target != null;
	}

	@Override
	public boolean canContinueToUse() {
		return heart.active() && target != null && stillThere(target) && (--recheck > 0 || recheckBlocked());
	}

	private boolean recheckBlocked() {
		recheck = RECHECK_TICKS;
		blocked = heart.heart().isPresent() && PathCheck.blocked(mob, heart.heart().get());
		return blocked;
	}

	private boolean stillThere(StructureDamage.Target t) {
		BlockState state = mob.level().getBlockState(t.pos());
		return t.kind() == StructureDamage.Kind.TOWER
			? state.getBlock() instanceof dev.vadim.heartguard.tower.TowerBlock
			: StructureDamage.isBreakableWall(state);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		progress = 0.0;
		towerCooldown = 0;
	}

	@Override
	public void stop() {
		if (target != null && mob.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(mob.getId(), target.pos(), -1);
		}
		mob.getNavigation().stop();
		target = null;
	}

	@Override
	public void tick() {
		if (target == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = target.pos();
		mob.getLookControl().setLookAt(pos.getCenter());
		if (mob.getEyePosition().distanceTo(pos.getCenter()) > ATTACK_RANGE) {
			mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
			return;
		}
		mob.getNavigation().stop();
		if (target.kind() == StructureDamage.Kind.TOWER) {
			if (--towerCooldown <= 0) {
				towerCooldown = TOWER_HIT_INTERVAL;
				mob.swing(InteractionHand.MAIN_HAND);
				TowerService.damage(level, pos, structureDamage);
			}
			return;
		}
		// A wall: crack it a little each tick, faster for a harder-hitting mob and slower for a harder block.
		float hardness = Math.max(0.5F, level.getBlockState(pos).getDestroySpeed(level, pos));
		double needed = hardness * BREAK_TICKS_PER_HARDNESS * 10.0 / structureDamage;
		progress += 1.0;
		if (mob.tickCount % 8 == 0) {
			mob.swing(InteractionHand.MAIN_HAND);
		}
		level.destroyBlockProgress(mob.getId(), pos, (int) Math.min(9, progress / needed * 10.0));
		if (progress >= needed) {
			level.destroyBlock(pos, false, mob);
			level.destroyBlockProgress(mob.getId(), pos, -1);
			progress = 0.0;
			target = null;
			recheck = 0;
		}
	}
}
