package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/** Puts the Heart block in front of the player in a real client, so a screenshot shows its model and texture. */
public class HeartBlockClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();

			BlockPos target = context.computeOnClient(mc -> BlockPos.containing(mc.player.position()).offset(0, 1, 3));
			world.getServer().runOnServer(server ->
				server.overworld().setBlock(target, ModBlocks.HEART.defaultBlockState(), Block.UPDATE_ALL));
			context.getInput().lookAt(target);
			context.waitTicks(10);

			boolean clientSeesHeart = context.computeOnClient(mc -> mc.level.getBlockState(target).is(ModBlocks.HEART));
			if (!clientSeesHeart) {
				throw new AssertionError("the client should receive the Heart block at " + target);
			}
			context.takeScreenshot("heart-block");
		}
	}
}
