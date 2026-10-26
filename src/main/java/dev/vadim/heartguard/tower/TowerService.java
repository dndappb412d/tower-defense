package dev.vadim.heartguard.tower;

import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.placement.BuildRules;
import dev.vadim.heartguard.placement.PlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Server-side authority over towers that already stand: upgrading, selling and changing their targeting. Only the
 * owner may act on a tower, from within build range, and under the same Command Post rule as building. Coins move
 * only after every check has passed.
 */
public final class TowerService {
	public enum Result {
		DONE,
		NOT_A_TOWER,
		NOT_YOURS,
		OUT_OF_RANGE,
		COMMAND_POST_REQUIRED,
		MAX_TIER,
		NOT_ENOUGH_COINS
	}

	private TowerService() {}

	public static Result upgrade(ServerPlayer player, BlockPos pos) {
		Result gate = check(player, pos);
		if (gate != Result.DONE) {
			return gate;
		}
		TowerBlockEntity tower = (TowerBlockEntity) player.level().getBlockEntity(pos);
		TowerDef def = tower.def();
		if (def == null || tower.tier() >= def.maxTier()) {
			return Result.MAX_TIER;
		}
		int next = tower.tier() + 1;
		if (!Coins.spend(player, def.tier(next).upgradeCost())) {
			return Result.NOT_ENOUGH_COINS;
		}
		tower.setTier(next);
		tower.setHp(def.hp());
		return Result.DONE;
	}

	/** Removes the tower and pays back its refund share of everything spent on it. */
	public static Result sell(ServerPlayer player, BlockPos pos) {
		Result gate = check(player, pos);
		if (gate != Result.DONE) {
			return gate;
		}
		ServerLevel level = player.level();
		TowerBlockEntity tower = (TowerBlockEntity) level.getBlockEntity(pos);
		TowerDef def = tower.def();
		int refund = def == null ? 0 : def.sellValue(tower.tier());
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		Coins.add(player, refund);
		return Result.DONE;
	}

	/** Moves to the next targeting mode: first, strongest, closest, and round again. */
	public static Result cycleTargeting(ServerPlayer player, BlockPos pos) {
		Result gate = check(player, pos);
		if (gate != Result.DONE) {
			return gate;
		}
		TowerBlockEntity tower = (TowerBlockEntity) player.level().getBlockEntity(pos);
		TowerBlockEntity.Targeting[] modes = TowerBlockEntity.Targeting.values();
		tower.setTargeting(modes[(tower.targeting().ordinal() + 1) % modes.length]);
		return Result.DONE;
	}

	private static Result check(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof TowerBlockEntity tower)) {
			return Result.NOT_A_TOWER;
		}
		if (!tower.owner().equals(java.util.Optional.of(player.getUUID()))) {
			return Result.NOT_YOURS;
		}
		if (pos.getCenter().distanceToSqr(player.position()) > PlacementService.MAX_DISTANCE * PlacementService.MAX_DISTANCE) {
			return Result.OUT_OF_RANGE;
		}
		if (!BuildRules.canCommandNow(player)) {
			return Result.COMMAND_POST_REQUIRED;
		}
		return Result.DONE;
	}
}
