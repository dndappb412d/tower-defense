package dev.vadim.heartguard.placement;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * When a player may build, upgrade or sell. Between waves the commander view works from anywhere; during a wave the
 * player's body has to be parked at a Command Post, so being in command costs something. The rule is checked on the
 * server for every tower action, whatever the client shows.
 */
public final class BuildRules {
	/** How close, in blocks, the player must stand to a Command Post during a wave. */
	public static final int COMMAND_POST_REACH = 4;

	private BuildRules() {}

	public static boolean canCommandNow(ServerPlayer player) {
		if (Match.get(player.level().getServer()).phase() != MatchData.Phase.WAVE) {
			return true;
		}
		return isNearCommandPost(player);
	}

	public static boolean isNearCommandPost(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos centre = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
			centre.offset(-COMMAND_POST_REACH, -COMMAND_POST_REACH, -COMMAND_POST_REACH),
			centre.offset(COMMAND_POST_REACH, COMMAND_POST_REACH, COMMAND_POST_REACH)
		)) {
			if (level.getBlockState(pos).is(ModBlocks.COMMAND_POST)) {
				return true;
			}
		}
		return false;
	}
}
