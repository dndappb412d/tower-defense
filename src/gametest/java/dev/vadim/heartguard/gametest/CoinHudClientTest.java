package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.economy.Coins;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** The balance changes on the server and shows up on the client: synced attachment and HUD. */
public class CoinHudClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();

			int before = context.computeOnClient(mc -> Coins.balance(mc.player));
			if (before != 0) {
				throw new AssertionError("a new player should start with 0 coins on the client, had " + before);
			}

			world.getServer().runOnServer(server -> Coins.add(server.getPlayerList().getPlayers().get(0), 123));

			int synced = 0;
			for (int tick = 0; tick < 40 && synced != 123; tick++) {
				context.waitTick();
				synced = context.computeOnClient(mc -> Coins.balance(mc.player));
			}
			if (synced != 123) {
				throw new AssertionError("the client should receive the synced balance 123, had " + synced);
			}
			context.waitTicks(2);
			context.takeScreenshot("coin-hud");
		}
	}
}
