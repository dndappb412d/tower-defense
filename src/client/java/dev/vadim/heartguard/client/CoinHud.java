package dev.vadim.heartguard.client;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.economy.Coins;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Shows the synced coin balance in the top-left corner. */
public final class CoinHud {
	private static final int GOLD = 0xFFFFD700;

	private CoinHud() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "coins"),
			(graphics, deltaTracker) -> {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft.player == null || minecraft.options.hideGui) {
					return;
				}
				graphics.text(minecraft.font, "Coins: " + Coins.balance(minecraft.player), 6, 6, GOLD);
			}
		);
	}
}
