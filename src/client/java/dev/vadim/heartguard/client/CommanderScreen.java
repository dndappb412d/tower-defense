package dev.vadim.heartguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.Heartguard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Transparent screen drawn over the world during commander view. Its only jobs for now are to free the cursor,
 * keep clicks away from the world, and pan/zoom the camera. Closing it (Esc or the toggle key) leaves the view.
 */
public class CommanderScreen extends Screen {
	/** Pan speed in heights per second, so panning feels the same at any zoom. */
	private static final double PAN_SPEED = 0.8;
	private static final double ZOOM_STEP = 3.0;

	/** Keys currently held, from screen key events (a screen gets no KeyMapping state, and tests inject events). */
	private final Set<Integer> held = new HashSet<>();
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
		double right = axis(InputConstants.KEY_D, InputConstants.KEY_A);
		// North is -Z and sits at the top of the screen (see CommanderView.YAW).
		double down = axis(InputConstants.KEY_S, InputConstants.KEY_W);
		if (right == 0.0 && down == 0.0) {
			return;
		}
		double boost = held.contains(InputConstants.KEY_LSHIFT) ? 2.0 : 1.0;
		double step = PAN_SPEED * CommanderView.height() * boost * dt;
		CommanderView.pan(right * step, down * step);
	}

	private double axis(int positive, int negative) {
		return (held.contains(positive) ? 1.0 : 0.0) - (held.contains(negative) ? 1.0 : 0.0);
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
		held.add(event.key());
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		held.remove(event.key());
		return super.keyReleased(event);
	}

	@Override
	public void removed() {
		Heartguard.LOGGER.info("Commander view off");
	}
}
