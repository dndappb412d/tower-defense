package dev.vadim.heartguard.client;

import dev.vadim.heartguard.match.EndActionPayload;
import dev.vadim.heartguard.match.EndScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Victory or defeat, with how far the match got. The buttons are intents: the server checks the match is over and does
 * the replay or the leave. Closing the screen leaves you standing in the arena to look around.
 */
public class EndScreen extends Screen {
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int BUTTON_WIDTH = 120;
	private static final int BUTTON_HEIGHT = 20;

	private final EndScreenPayload result;
	private Button replay;
	private Button leave;

	public EndScreen(EndScreenPayload result) {
		super(Component.literal(result.won() ? "Victory" : "Defeat"));
		this.result = result;
	}

	public EndScreenPayload result() {
		return result;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH - 4;
		int y = height / 2 + 20;
		replay = Button.builder(Component.literal("Play again"), b -> {
			ClientPlayNetworking.send(new EndActionPayload(EndActionPayload.Action.REPLAY));
			onClose();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		replay.active = result.canReplay();
		leave = Button.builder(Component.literal("Leave"), b -> {
			ClientPlayNetworking.send(new EndActionPayload(EndActionPayload.Action.LEAVE));
			onClose();
		}).bounds(width / 2 + 4, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		addRenderableWidget(replay);
		addRenderableWidget(leave);
		addRenderableWidget(Button.builder(Component.literal("Stay and look around"), b -> onClose())
			.bounds(width / 2 - BUTTON_WIDTH - 4, y + BUTTON_HEIGHT + 4, 2 * BUTTON_WIDTH + 8, BUTTON_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		var font = Minecraft.getInstance().font;
		graphics.centeredText(font, getTitle(), width / 2, height / 2 - 50, result.won() ? GREEN : RED);
		String line = result.won()
			? "All " + result.total() + " waves cleared with the Heart at " + result.heartHp() + "/100"
			: result.wave() == 0
				? "The Heart fell before the first wave"
				: "The Heart fell during wave " + result.wave() + " of " + result.total();
		graphics.centeredText(font, Component.literal(line), width / 2, height / 2 - 30, WHITE);
	}

	/** Where to put the mouse, in window coordinates, to click Play again or Leave. For tests. */
	public double[] windowCentreOf(boolean playAgain) {
		Button button = playAgain ? replay : leave;
		var window = Minecraft.getInstance().getWindow();
		double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		return new double[] {(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
	}
}
