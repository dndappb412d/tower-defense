package dev.vadim.heartguard.placement;

import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.tower.TowerBlock;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Server-side authority over building towers. The client only sends intents; every rule is checked here, and coins
 * are spent only once everything else has passed. The rules are fixed constants until they move into data files.
 */
public final class PlacementService {
	/** Farthest a tower may be from the requesting player, in blocks (covers the commander camera view). */
	public static final double MAX_DISTANCE = 128.0;

	public enum Result {
		PLACED,
		UNKNOWN_TOWER,
		OUT_OF_RANGE,
		NOT_LOADED,
		OUT_OF_WORLD,
		OBSTRUCTED,
		NO_SUPPORT,
		NOT_ENOUGH_COINS
	}

	private PlacementService() {}

	public static Result tryPlace(ServerPlayer player, Identifier towerId, BlockPos pos) {
		TowerDef def = TowerDefs.get(towerId);
		TowerBlock block = TowerBlock.forTower(towerId);
		if (def == null || block == null) {
			return Result.UNKNOWN_TOWER;
		}

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
		if (!Coins.spend(player, def.cost())) {
			return Result.NOT_ENOUGH_COINS;
		}

		level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof TowerBlockEntity tower) {
			tower.initialize(player.getUUID(), def);
		}
		return Result.PLACED;
	}
}
