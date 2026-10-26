package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.client.ShopScreen;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.shop.Shop;
import dev.vadim.heartguard.shop.ShopDef;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** The shop from the player's side: the screen opens, shows every offer, and a mouse click buys an item. */
public class ShopClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();

			BlockPos shop = context.computeOnClient(mc -> BlockPos.containing(mc.player.position()).offset(0, 0, 2));
			world.getServer().runOnServer(server -> {
				server.overworld().setBlock(shop, ModBlocks.QUARTERMASTER.defaultBlockState(), Block.UPDATE_ALL);
				var player = server.getPlayerList().getPlayers().get(0);
				Coins.add(player, 400);
				Shop.open(player, shop);
			});

			context.waitForScreen(ShopScreen.class);
			context.waitTicks(3);
			int offers = context.computeOnClient(mc -> ((ShopScreen) mc.screen).offerCount());
			int expected = ShopDef.get(ShopDef.DEFAULT_ID).items().size();
			if (offers != expected) {
				throw new AssertionError("the screen should list all " + expected + " offers, listed " + offers);
			}
			context.takeScreenshot("shop-open");

			// Click the iron pickaxe like a player would.
			int index = -1;
			var items = ShopDef.get(ShopDef.DEFAULT_ID).items();
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i).item() == Items.IRON_PICKAXE) {
					index = i;
				}
			}
			int pickaxeIndex = index;
			int price = items.get(pickaxeIndex).price();
			double[] at = context.computeOnClient(mc -> ((ShopScreen) mc.screen).windowCentreOf(pickaxeIndex));
			context.getInput().setCursorPos(at[0], at[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(0);
			context.waitTicks(5);

			int balance = world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
			boolean holds = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory().contains(stack -> stack.is(Items.IRON_PICKAXE)));
			if (balance != 400 - price) {
				throw new AssertionError("clicking the iron pickaxe should cost " + price + ", balance is " + balance);
			}
			if (!holds) {
				throw new AssertionError("the iron pickaxe should be in the inventory after clicking it");
			}
			context.takeScreenshot("shop-after-buying");
		}
	}
}
