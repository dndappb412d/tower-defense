package dev.vadim.heartguard.item;

import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Use it during a match to put {@link #REPAIR_AMOUNT} HP back into the Heart. It is kept when the Heart needs nothing. */
public class HeartRepairKitItem extends Item {
	public static final int REPAIR_AMOUNT = 20;

	public HeartRepairKitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		MatchData match = Match.get(serverLevel.getServer());
		if (!match.running() || match.heartHp() >= MatchData.MAX_HEART_HP) {
			serverPlayer.sendSystemMessage(Component.literal("The Heart does not need repairs"), true);
			return InteractionResult.FAIL;
		}
		Match.update(serverLevel.getServer(), d -> d.repaired(REPAIR_AMOUNT));
		player.getItemInHand(hand).shrink(1);
		serverPlayer.sendSystemMessage(Component.literal("Heart repaired"), true);
		return InteractionResult.SUCCESS;
	}
}
