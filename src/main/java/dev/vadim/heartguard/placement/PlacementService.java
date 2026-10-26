package dev.vadim.heartguard.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Server-side authority over marker placement. The client only sends intents; every rule is checked here.
 * The marker is a placeholder block until real towers exist, and the rules are fixed constants until
 * they move into data files with the rest of the content.
 */
public final class PlacementService {
	/** Farthest a marker may be from the requesting player, in blocks (covers the commander camera view). */
	public static final double MAX_DISTANCE = 128.0;

	public static final Block MARKER = Blocks.GOLD_BLOCK;

	public enum Result {
		PLACED,
		OUT_OF_RANGE,
		NOT_LOADED,
		OUT_OF_WORLD,
		OBSTRUCTED,
		NO_SUPPORT
	}

	private PlacementService() {}

	public static Result tryPlace(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		if (!level.isInWorldBounds(pos)) {
			return Result.OUT_OF_WORLD;
		}
		if (pos.getCenter().distanceToSqr(player.position()) > MAX_DISTANCE * MAX_DISTANCE) {
			return Result.OUT_OF_RANGE;
		}
		if (!level.isLoaded(pos)) {
			return Result.NOT_LOADED;
		}
		if (!level.getBlockState(pos).isAir()) {
			return Result.OBSTRUCTED;
		}
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return Result.NO_SUPPORT;
		}
		level.setBlock(pos, MARKER.defaultBlockState(), Block.UPDATE_ALL);
		return Result.PLACED;
	}
}
