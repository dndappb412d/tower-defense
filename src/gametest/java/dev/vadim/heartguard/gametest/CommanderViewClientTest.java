package dev.vadim.heartguard.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.client.CommanderScreen;
import dev.vadim.heartguard.client.CommanderView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Camera;
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
