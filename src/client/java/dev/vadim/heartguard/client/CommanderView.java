package dev.vadim.heartguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.Heartguard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Client-side commander view state: a top-down camera that follows the player. Purely visual, no gameplay authority. */
public final class CommanderView {
	/** Camera height above the player's eyes, in blocks. */
	public static final double HEIGHT = 30.0;
	/** Yaw that puts north at the top of the screen. */
	public static final float YAW = 180.0F;
	/** Straight down. */
	public static final float PITCH = 90.0F;

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "main"));
	private static final KeyMapping TOGGLE =
		new KeyMapping("key.heartguard.toggle_commander", InputConstants.KEY_G, CATEGORY);

	private static boolean active;

	private CommanderView() {}

	public static boolean isActive() {
		return active;
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(TOGGLE);
		ClientTickEvents.END_CLIENT_TICK.register(CommanderView::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null) {
			active = false;
			return;
		}
		while (TOGGLE.consumeClick()) {
			active = !active;
			Heartguard.LOGGER.info("Commander view {}", active ? "on" : "off");
		}
	}
}
