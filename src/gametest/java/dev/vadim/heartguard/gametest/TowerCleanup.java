package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.tower.TowerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Towers shoot on their own, so one a test leaves behind would shoot mobs in the tests around it and steal their kills.
 * Tests that build towers in a single tick call this when they finish, so the towers never get a tick of their own.
 */
public final class TowerCleanup {
	private TowerCleanup() {}

	public static void clear(GameTestHelper context) {
		for (int x = -6; x <= 12; x++) {
			for (int y = 0; y <= 4; y++) {
				for (int z = -6; z <= 6; z++) {
					BlockPos pos = context.absolutePos(new BlockPos(x, y, z));
					if (context.getLevel().getBlockState(pos).getBlock() instanceof TowerBlock) {
						context.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
	}
}
