package dev.vadim.heartguard.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.client.CommanderScreen;
import dev.vadim.heartguard.client.CommanderView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Camera;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.client.ClientTowers;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.economy.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

/** Drives a real client: opens a world, toggles commander view, and checks camera state, pan and zoom. */
public class CommanderViewClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			TestInput input = context.getInput();

			double eyeY = context.computeOnClient(mc -> mc.player.getEyeY());

			input.pressKey(InputConstants.KEY_G);
			context.waitForScreen(CommanderScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("commander-view-open");

			double height = context.computeOnClient(mc -> CommanderView.height());
			double camY = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().y);
			float pitch = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().xRot());
			check(Math.abs(camY - (eyeY + height)) < 0.01, "camera should sit %s above the eyes, was %s".formatted(height, camY - eyeY));
			check(pitch == CommanderView.PITCH, "camera should look straight down, pitch was " + pitch);

			double z0 = context.computeOnClient(mc -> CommanderView.centerZ());
			input.holdKeyFor(InputConstants.KEY_W, 20);
			context.waitTicks(2);
			double z1 = context.computeOnClient(mc -> CommanderView.centerZ());
			check(z1 < z0, "W should pan north (-Z): %s -> %s".formatted(z0, z1));

			double h0 = context.computeOnClient(mc -> CommanderView.height());
			input.scroll(-1.0);
			context.waitTicks(2);
			double h1 = context.computeOnClient(mc -> CommanderView.height());
			check(h1 > h0, "scrolling back should zoom out: %s -> %s".formatted(h0, h1));

			// Cursor pick: the centre of the screen is straight below the camera.
			int width = context.computeOnClient(mc -> mc.getWindow().getScreenWidth());
			int height2 = context.computeOnClient(mc -> mc.getWindow().getScreenHeight());
			input.setCursorPos(width / 2.0, height2 / 2.0);
			context.waitTicks(3);
			BlockHitResult centre = context.computeOnClient(mc -> CommanderView.cursorHit());
			double cx = context.computeOnClient(mc -> CommanderView.centerX());
			double cz = context.computeOnClient(mc -> CommanderView.centerZ());
			check(centre != null, "cursor at screen centre should hit a block");
			check(centre.getBlockPos().getX() == (int)Math.floor(cx) && centre.getBlockPos().getZ() == (int)Math.floor(cz),
				"centre pick should be the column under the camera (%s, %s), was %s".formatted(cx, cz, centre.getBlockPos()));
			check(centre.getDirection() == Direction.UP, "a straight-down pick should hit a top face, was " + centre.getDirection());
			boolean vanillaFollows = context.computeOnClient(mc -> mc.hitResult == CommanderView.cursorHit());
			check(vanillaFollows, "vanilla hitResult should follow the cursor pick so its outline is drawn there");
			context.takeScreenshot("commander-cursor-centre");

			// Moving the cursor right (east, +X) and down (south, +Z) moves the pick the same way.
			input.setCursorPos(width / 2.0 + 150.0, height2 / 2.0 + 100.0);
			context.waitTicks(3);
			BlockHitResult moved = context.computeOnClient(mc -> CommanderView.cursorHit());
			check(moved != null, "cursor offset from centre should hit a block");
			check(moved.getBlockPos().getX() > centre.getBlockPos().getX(), "cursor right should pick further east: %s -> %s".formatted(centre.getBlockPos(), moved.getBlockPos()));
			check(moved.getBlockPos().getZ() > centre.getBlockPos().getZ(), "cursor down should pick further south: %s -> %s".formatted(centre.getBlockPos(), moved.getBlockPos()));
			context.takeScreenshot("commander-cursor-offset");

			// End to end: a click sends the intent, the server validates it and places the marker above the picked block.
			input.setCursorPos(width / 2.0, height2 / 2.0);
			context.waitTicks(3);
			BlockHitResult target = context.computeOnClient(mc -> CommanderView.cursorHit());
			check(target != null, "cursor should hit a block before clicking");
			BlockPos markerPos = target.getBlockPos().above();
			world.getServer().runOnServer(server -> Coins.add(server.getPlayerList().getPlayers().get(0), 500));
			boolean airBefore = world.getServer().computeOnServer(server -> server.overworld().getBlockState(markerPos).isAir());
			check(airBefore, "target cell should be empty before the click, was " + markerPos);
			input.pressMouse(0);
			boolean placed = false;
			for (int tick = 0; tick < 40 && !placed; tick++) {
				context.waitTick();
				placed = world.getServer().computeOnServer(server -> server.overworld().getBlockState(markerPos).is(ModBlocks.ARROW_TOWER));
			}
			check(placed, "server should have placed an arrow tower at " + markerPos + " after the click");
			int coinsLeft = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
			check(coinsLeft == 500 - 50, "the arrow tower should have cost 50 coins, balance is " + coinsLeft);
			context.waitTicks(2);
			context.takeScreenshot("commander-marker-placed");

			// The tower list the palette uses is the server's, delivered by packet.
			for (var id : java.util.List.of(TowerDefs.ARROW, TowerDefs.CANNON, TowerDefs.FROST)) {
				boolean sameAsServer = context.computeOnClient(mc -> ClientTowers.get(id) != null && ClientTowers.get(id).equals(TowerDefs.get(id)));
				check(sameAsServer, "the client should hold the server's numbers for " + id);
			}

			// Right click selects the tower under the cursor; U upgrades, T changes the mode, X sells.
			input.pressMouse(1);
			context.waitTicks(3);
			BlockPos selected = context.computeOnClient(mc -> CommanderView.selectedTowerPos());
			check(markerPos.equals(selected), "right click should select the tower at " + markerPos + ", selected " + selected);

			input.pressKey(InputConstants.KEY_U);
			context.waitTicks(5);
			int serverTier = world.getServer().computeOnServer(server -> ((TowerBlockEntity) server.overworld().getBlockEntity(markerPos)).tier());
			int clientTier = context.computeOnClient(mc -> ((TowerBlockEntity) mc.level.getBlockEntity(markerPos)).tier());
			check(serverTier == 2, "U should upgrade the tower on the server, tier " + serverTier);
			check(clientTier == 2, "and the client should see the new tier, saw " + clientTier);
			int afterUpgrade = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
			check(afterUpgrade == 500 - 50 - 40, "the upgrade costs 40, balance " + afterUpgrade);
			context.takeScreenshot("commander-tower-selected");

			input.pressKey(InputConstants.KEY_T);
			context.waitTicks(5);
			String mode = world.getServer().computeOnServer(server -> ((TowerBlockEntity) server.overworld().getBlockEntity(markerPos)).targeting().name());
			check(mode.equals("STRONGEST"), "T should move the tower to the next mode, mode " + mode);

			input.pressKey(InputConstants.KEY_X);
			context.waitTicks(5);
			boolean gone = world.getServer().computeOnServer(server -> server.overworld().getBlockState(markerPos).isAir());
			int afterSale = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
			check(gone, "X should sell the tower and remove its block");
			int refund = TowerDefs.get(TowerDefs.ARROW).sellValue(2);
			check(afterSale == afterUpgrade + refund, "selling returns " + refund + ", balance " + afterSale);

			input.pressKey(InputConstants.KEY_G);
			context.waitFor(mc -> mc.screen == null);
			context.waitTicks(2);
			Camera camera = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera());
			double backY = context.computeOnClient(mc -> camera.position().y);
			check(Math.abs(backY - eyeY) < 1.0, "leaving commander view should return the camera to the player, was " + (backY - eyeY));
			context.takeScreenshot("commander-view-closed");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
