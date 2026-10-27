package dev.vadim.heartguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.Heartguard;
import net.minecraft.client.Minecraft;
import dev.vadim.heartguard.placement.PlaceTowerPayload;
import dev.vadim.heartguard.tower.TowerActionPayload;
import dev.vadim.heartguard.tower.TowerBlock;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import net.minecraft.core.BlockPos;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.input.MouseButtonEvent;
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
		drawPalette(graphics);
	}

	/** One line under the coin counter: the selected tower and its price, plus the keys for the others. */
	private void drawPalette(GuiGraphicsExtractor graphics) {
		var font = Minecraft.getInstance().font;
		var selected = CommanderView.selectedTower();
		TowerDef def = ClientTowers.get(selected);
		String price = def == null ? "?" : String.valueOf(def.cost());
		graphics.text(font, "Build: " + selected.getPath() + " (" + price + " coins)", 6, 20, 0xFFFFFFFF);
		StringBuilder keys = new StringBuilder();
		for (int i = 0; i < CommanderView.TOWERS.size(); i++) {
			keys.append(i == 0 ? "" : "   ").append('[').append(i + 1).append("] ").append(CommanderView.TOWERS.get(i).getPath());
		}
		graphics.text(font, keys.toString(), 6, 32, 0xFFAAAAAA);
		drawSelectedTower(graphics, font);
	}

	/** The right-clicked tower: its tier and mode, and what upgrading and selling would do. */
	private void drawSelectedTower(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font) {
		BlockPos pos = CommanderView.selectedTowerPos();
		var level = Minecraft.getInstance().level;
		if (pos == null || level == null) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof TowerBlockEntity tower) || !(level.getBlockState(pos).getBlock() instanceof TowerBlock block)) {
			CommanderView.selectTowerAt(null);
			return;
		}
		TowerDef def = ClientTowers.get(block.towerId());
		String name = block.towerId().getPath() + " tower, tier " + tower.tier() + (def == null ? "" : "/" + def.maxTier())
			+ ", targets " + tower.targeting().name().toLowerCase(java.util.Locale.ROOT)
			+ (def == null ? "" : ", hp " + tower.hp() + "/" + def.hp());
		graphics.text(font, name, 6, 50, 0xFF9BE89B);
		if (def != null) {
			String upgrade = tower.tier() >= def.maxTier() ? "max tier" : "[U] upgrade " + def.tier(tower.tier() + 1).upgradeCost();
			graphics.text(font, upgrade + "   [X] sell +" + def.sellValue(tower.tier()) + "   [T] change mode", 6, 62, 0xFFAAAAAA);
		}
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

	/** Left click builds on the picked top face, right click selects a tower; the server decides what is allowed. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		BlockHitResult hit = CommanderView.cursorHit();
		var level = Minecraft.getInstance().level;
		boolean onTower = hit != null && level != null && level.getBlockState(hit.getBlockPos()).getBlock() instanceof TowerBlock;
		if (event.button() == 0 && hit != null && hit.getDirection() == Direction.UP && !onTower) {
			ClientPlayNetworking.send(new PlaceTowerPayload(CommanderView.selectedTower(), hit.getBlockPos().above()));
		} else if (event.button() == 1) {
			CommanderView.selectTowerAt(onTower ? hit.getBlockPos() : null);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (CommanderView.isToggleKey(event)) {
			onClose();
			return true;
		}
		int digit = event.key() - InputConstants.KEY_1;
		if (digit >= 0 && digit < CommanderView.TOWERS.size()) {
			CommanderView.selectTower(digit);
			return true;
		}
		BlockPos selected = CommanderView.selectedTowerPos();
		if (selected != null) {
			TowerActionPayload.Action action = switch (event.key()) {
				case InputConstants.KEY_U -> TowerActionPayload.Action.UPGRADE;
				case InputConstants.KEY_X -> TowerActionPayload.Action.SELL;
				case InputConstants.KEY_T -> TowerActionPayload.Action.CYCLE_TARGETING;
				default -> null;
			};
			if (action != null) {
				ClientPlayNetworking.send(new TowerActionPayload(selected, action));
				if (action == TowerActionPayload.Action.SELL) {
					CommanderView.selectTowerAt(null);
				}
				return true;
			}
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
		CommanderView.selectTowerAt(null);
		Heartguard.LOGGER.info("Commander view off");
	}
}
