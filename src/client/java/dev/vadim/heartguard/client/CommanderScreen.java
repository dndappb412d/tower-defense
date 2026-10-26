package dev.vadim.heartguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.vadim.heartguard.Heartguard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Transparent screen drawn over the world during commander view. Its only jobs for now are to free the cursor,
 * keep clicks away from the world, and pan/zoom the camera. Closing it (Esc or the toggle key) leaves the view.
 */
public class CommanderScreen extends Screen {
	/** Pan speed in heights per second, so panning feels the same at any zoom. */
	private static final double PAN_SPEED = 0.8;
	private static final double ZOOM_STEP = 3.0;

	private long lastFrameNanos = System.nanoTime();

	public CommanderScreen() {
		super(Component.empty());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Keep the world fully visible: no dimming gradient.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastFrameNanos) / 1.0e9, 0.1);
		lastFrameNanos = now;
		panFromKeys(dt);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void panFromKeys(double dt) {
		Window window = Minecraft.getInstance().getWindow();
		double right = axis(window, InputConstants.KEY_D, InputConstants.KEY_A);
		// North is -Z and sits at the top of the screen (see CommanderView.YAW).
		double down = axis(window, InputConstants.KEY_S, InputConstants.KEY_W);
		if (right == 0.0 && down == 0.0) {
			return;
		}
		double boost = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) ? 2.0 : 1.0;
		double step = PAN_SPEED * CommanderView.height() * boost * dt;
		CommanderView.pan(right * step, down * step);
	}

	private static double axis(Window window, int positive, int negative) {
		return (InputConstants.isKeyDown(window, positive) ? 1.0 : 0.0)
			- (InputConstants.isKeyDown(window, negative) ? 1.0 : 0.0);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		CommanderView.zoom(-scrollY * ZOOM_STEP);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (CommanderView.isToggleKey(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		Heartguard.LOGGER.info("Commander view off");
	}
}
