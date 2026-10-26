package dev.vadim.heartguard.client;

import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.shop.BuyPayload;
import dev.vadim.heartguard.shop.OpenShopPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Quartermaster as the client shows it: one button per offer, in two columns. Clicking sends the index of the
 * offer; the server decides whether you can afford it and how close you are, so this screen holds no rules.
 */
public class ShopScreen extends Screen {
	private static final int COLUMNS = 2;
	private static final int ICON = 22;
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 22;
	private static final int GAP = 4;
	private static final int TOP = 40;

	private final List<OpenShopPayload.Offer> offers;
	private final List<Button> buttons = new ArrayList<>();

	public ShopScreen(List<OpenShopPayload.Offer> offers) {
		super(Component.literal("Quartermaster"));
		this.offers = offers;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		buttons.clear();
		int rowWidth = COLUMNS * (ICON + BUTTON_WIDTH) + (COLUMNS - 1) * GAP;
		int left = (width - rowWidth) / 2;
		for (int i = 0; i < offers.size(); i++) {
			int index = i;
			int x = left + (i % COLUMNS) * (ICON + BUTTON_WIDTH + GAP);
			int y = TOP + (i / COLUMNS) * (BUTTON_HEIGHT + GAP);
			OpenShopPayload.Offer offer = offers.get(i);
			String count = offer.stack().getCount() > 1 ? " x" + offer.stack().getCount() : "";
			Button button = Button.builder(
				Component.literal(offer.stack().getHoverName().getString() + count + "  " + offer.price()),
				b -> ClientPlayNetworking.send(new BuyPayload(index))
			).bounds(x + ICON, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
			buttons.add(button);
			addRenderableWidget(button);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		var font = Minecraft.getInstance().font;
		graphics.centeredText(font, getTitle(), width / 2, 12, 0xFFFFFFFF);
		var player = Minecraft.getInstance().player;
		if (player != null) {
			graphics.centeredText(font, Component.literal("Coins: " + Coins.balance(player)), width / 2, 24, 0xFFFFD700);
		}
		for (int i = 0; i < buttons.size(); i++) {
			graphics.item(offers.get(i).stack(), buttons.get(i).getX() - ICON + 3, buttons.get(i).getY() + 3);
		}
	}

	/** Where to put the mouse, in window coordinates, to click the {@code index}th offer. For tests. */
	public double[] windowCentreOf(int index) {
		Button button = buttons.get(index);
		var window = Minecraft.getInstance().getWindow();
		double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		return new double[] {(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
	}

	public int offerCount() {
		return offers.size();
	}
}
