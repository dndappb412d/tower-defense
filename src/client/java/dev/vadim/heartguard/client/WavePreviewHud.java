package dev.vadim.heartguard.client;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.wave.WavePreview;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The next wave as the server last described it, listed under the coin count while the players are building. */
public final class WavePreviewHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int GREY = 0xFFAAAAAA;
	private static volatile WavePreview preview = WavePreview.NONE;

	private WavePreviewHud() {}

	public static void set(WavePreview next) {
		preview = next;
	}

	public static WavePreview get() {
		return preview;
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "wave_preview"),
			(graphics, deltaTracker) -> {
				Minecraft minecraft = Minecraft.getInstance();
				WavePreview shown = preview;
				if (minecraft.player == null || minecraft.options.hideGui || shown.isEmpty()) {
					return;
				}
				int y = 20;
				graphics.text(minecraft.font, "Next: wave " + shown.wave() + "/" + shown.total(), 6, y, GREY);
				for (WavePreview.Entry entry : shown.entries()) {
					y += 11;
					String line = entry.count() + " x " + entry.name() + (entry.flies() ? " (flies)" : "");
					graphics.text(minecraft.font, entry.boss() ? "BOSS " + line : line, 6, y, entry.boss() ? RED : WHITE);
				}
			}
		);
	}
}
