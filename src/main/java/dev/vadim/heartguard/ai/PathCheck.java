package dev.vadim.heartguard.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Whether a ground mob can get to the Heart. The Heart is a solid block, so a good path ends beside it and
 * {@code Path.canReach()} is false even then; what counts is the path ending within the attack reach of the Heart.
 */
public final class PathCheck {
	private PathCheck() {}

	public static boolean blocked(Mob mob, BlockPos heart) {
		Path path = mob.getNavigation().createPath(heart, 1);
		if (path == null || path.getEndNode() == null) {
			return true;
		}
		return path.getEndNode().asVec3().distanceTo(heart.getCenter()) > HeartSeekGoal.REACH + 0.5;
	}
}
