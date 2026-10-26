package dev.vadim.heartguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.Heartguard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Client-side commander view state: a top-down camera over a pannable, zoomable point.
 * Purely visual, no gameplay authority. The view is active exactly while {@link CommanderScreen} is open.
 */
public final class CommanderView {
	/** Yaw that puts north at the top of the screen. */
	public static final float YAW = 180.0F;
	/** Straight down. */
	public static final float PITCH = 90.0F;
	public static final double MIN_HEIGHT = 8.0;
	public static final double MAX_HEIGHT = 80.0;
	private static final double DEFAULT_HEIGHT = 30.0;

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "main"));
	private static final KeyMapping TOGGLE =
		new KeyMapping("key.heartguard.toggle_commander", InputConstants.KEY_G, CATEGORY);

	private static double centerX;
	private static double centerZ;
	/** Camera height above the player eyes, in blocks. */
	private static double height = DEFAULT_HEIGHT;

	/** Block under the cursor, refreshed every frame while the view is active. */
	private static @Nullable BlockHitResult cursorHit;

	private CommanderView() {}

	public static @Nullable BlockHitResult cursorHit() {
		return cursorHit;
	}

	public static void setCursorHit(@Nullable BlockHitResult hit) {
		cursorHit = hit;
	}

	public static boolean isActive() {
		return Minecraft.getInstance().screen instanceof CommanderScreen;
	}

	public static double centerX() {
		return centerX;
	}

	public static double centerZ() {
		return centerZ;
	}

	public static double height() {
		return height;
	}

	public static void pan(double dx, double dz) {
		centerX += dx;
		centerZ += dz;
	}

	public static void zoom(double delta) {
		height = Mth.clamp(height + delta, MIN_HEIGHT, MAX_HEIGHT);
	}

	public static boolean isToggleKey(KeyEvent event) {
		return TOGGLE.matches(event);
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(TOGGLE);
		ClientTickEvents.END_CLIENT_TICK.register(CommanderView::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.screen != null) {
			return;
		}
		while (TOGGLE.consumeClick()) {
			centerX = minecraft.player.getX();
			centerZ = minecraft.player.getZ();
			minecraft.setScreen(new CommanderScreen());
			Heartguard.LOGGER.info("Commander view on");
		}
	}
}
