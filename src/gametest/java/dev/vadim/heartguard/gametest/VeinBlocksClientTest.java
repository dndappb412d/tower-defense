package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.vein.VeinDef;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/** Mines a rich vein as a real player would, with the pickaxe in hand, and shows poor, rich and depleted veins together. */
public class VeinBlocksClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();

			BlockPos middle = context.computeOnClient(mc -> BlockPos.containing(mc.player.position()).offset(0, 1, 3));
			world.getServer().runOnServer(server -> {
				server.overworld().setBlock(middle.west(2), ModBlocks.POOR_VEIN.defaultBlockState(), Block.UPDATE_ALL);
				server.overworld().setBlock(middle, ModBlocks.RICH_VEIN.defaultBlockState(), Block.UPDATE_ALL);
				server.overworld().setBlock(middle.east(2), ModBlocks.DEPLETED_VEIN.defaultBlockState(), Block.UPDATE_ALL);
			});
			context.getInput().lookAt(middle);
			context.waitTicks(10);
			context.takeScreenshot("veins-before");

			// Hold the left button on the rich vein until it breaks; creative survival rules do not apply to this test player.
			int before = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
			context.getInput().holdMouse(0);
			boolean mined = false;
			for (int tick = 0; tick < 200 && !mined; tick++) {
				context.waitTick();
				mined = context.computeOnClient(mc -> mc.level.getBlockState(middle).is(ModBlocks.DEPLETED_VEIN));
			}
			context.getInput().releaseMouse(0);
			if (!mined) {
				throw new AssertionError("holding attack on a vein should deplete it");
			}
			context.waitTicks(5);
			int paid = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0))) - before;
			if (paid != VeinDef.get(VeinDef.RICH).payout()) {
				throw new AssertionError("the rich vein should pay " + VeinDef.get(VeinDef.RICH).payout() + ", paid " + paid);
			}
			context.takeScreenshot("veins-after");
		}
	}
}
