package dev.vadim.heartguard.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * Tests that need mobs to walk or block entities to tick rely on the chunks under them being entity-ticking. The
 * framework only forces the chunks around the test structure, and the structure lands at a different offset in its chunk
 * on each run, so an arena that sticks out of it is sometimes in a chunk that never ticks. Forcing the chunks the arena
 * covers makes the result the same on every run.
 */
public final class TestArena {
	private TestArena() {}

	public static void forceLoad(GameTestHelper context, BlockPos relativeMin, BlockPos relativeMax) {
		ServerLevel level = context.getLevel();
		BlockPos min = context.absolutePos(relativeMin);
		BlockPos max = context.absolutePos(relativeMax);
		for (int chunkX = Math.min(min.getX(), max.getX()) >> 4; chunkX <= Math.max(min.getX(), max.getX()) >> 4; chunkX++) {
			for (int chunkZ = Math.min(min.getZ(), max.getZ()) >> 4; chunkZ <= Math.max(min.getZ(), max.getZ()) >> 4; chunkZ++) {
				level.setChunkForced(chunkX, chunkZ, true);
			}
		}
	}
}
