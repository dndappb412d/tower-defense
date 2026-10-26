package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/** The three tower blocks side by side in a real client, so a screenshot shows their models and textures. */
public class TowerBlocksClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();

			BlockPos middle = context.computeOnClient(mc -> BlockPos.containing(mc.player.position()).offset(0, 1, 4));
			world.getServer().runOnServer(server -> {
				server.overworld().setBlock(middle.west(2), ModBlocks.ARROW_TOWER.defaultBlockState(), Block.UPDATE_ALL);
				server.overworld().setBlock(middle, ModBlocks.CANNON_TOWER.defaultBlockState(), Block.UPDATE_ALL);
				server.overworld().setBlock(middle.east(2), ModBlocks.FROST_TOWER.defaultBlockState(), Block.UPDATE_ALL);
			});
			context.getInput().lookAt(middle);
			context.waitTicks(10);

			boolean allArrived = context.computeOnClient(mc ->
				mc.level.getBlockState(middle.west(2)).is(ModBlocks.ARROW_TOWER)
					&& mc.level.getBlockState(middle).is(ModBlocks.CANNON_TOWER)
					&& mc.level.getBlockState(middle.east(2)).is(ModBlocks.FROST_TOWER));
			if (!allArrived) {
				throw new AssertionError("the client should receive all three tower blocks");
			}
			context.takeScreenshot("tower-blocks");
		}
	}
}
