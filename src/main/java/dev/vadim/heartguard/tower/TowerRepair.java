package dev.vadim.heartguard.tower;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Repairing a tower is done in first person, which is the point of the design: it pulls you off the commander screen and
 * out of the mine. Right-click a damaged tower with an empty hand. With anything in hand the click is left alone, so
 * building against a tower with blocks still works.
 */
public final class TowerRepair {
	private TowerRepair() {}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof TowerBlock)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				tell(server, TowerService.repair(server, hit.getBlockPos()));
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void tell(ServerPlayer player, TowerService.RepairResult result) {
		String text = switch (result) {
			case REPAIRED -> "Tower repaired";
			case NOT_A_TOWER -> "That is not a tower";
			case ALREADY_FULL -> "The tower is undamaged";
			case NOT_ENOUGH_COINS -> "Not enough coins to repair";
			case TOO_FAR -> "Too far away";
		};
		player.sendSystemMessage(Component.literal(text), true);
	}
}
