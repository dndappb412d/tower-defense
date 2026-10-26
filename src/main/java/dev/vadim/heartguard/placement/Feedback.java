package dev.vadim.heartguard.placement;

import dev.vadim.heartguard.tower.TowerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The one-line message a player sees above the hotbar when the server refuses a build or tower action. */
public final class Feedback {
	private Feedback() {}

	public static void send(ServerPlayer player, PlacementService.Result result) {
		String text = switch (result) {
			case PLACED -> null;
			case UNKNOWN_TOWER -> "Unknown tower";
			case OUT_OF_RANGE -> "Too far away";
			case NOT_LOADED -> "That area is not loaded";
			case OUT_OF_WORLD -> "Outside the world";
			case OBSTRUCTED -> "Something is in the way";
			case NO_SUPPORT -> "Needs solid ground";
			case NOT_ENOUGH_COINS -> "Not enough coins";
			case COMMAND_POST_REQUIRED -> "Stand at a Command Post to build during a wave";
		};
		show(player, text);
	}

	public static void send(ServerPlayer player, TowerService.Result result) {
		String text = switch (result) {
			case DONE -> null;
			case NOT_A_TOWER -> "No tower there";
			case NOT_YOURS -> "That tower belongs to someone else";
			case OUT_OF_RANGE -> "Too far away";
			case COMMAND_POST_REQUIRED -> "Stand at a Command Post to command during a wave";
			case MAX_TIER -> "Already at the highest tier";
			case NOT_ENOUGH_COINS -> "Not enough coins";
		};
		show(player, text);
	}

	private static void show(ServerPlayer player, String text) {
		if (text != null) {
			player.sendSystemMessage(Component.literal(text), true);
		}
	}
}
