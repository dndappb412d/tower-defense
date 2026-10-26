package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.mob.MobFactory;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** The four wave mobs side by side in a real client, so a screenshot shows their size, gear and silhouette. */
public class MobsClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			BlockPos middle = context.computeOnClient(mc -> BlockPos.containing(mc.player.position()).offset(0, 0, 6));
			String[] names = {"runner", "brute", "flyer", "warlord"};
			int[] xs = {-6, -2, 2, 7};
			world.getServer().runOnServer(server -> {
				var level = server.overworld();
				var target = new FakeHeartTarget(level, middle.offset(0, 0, 40));
				target.active.set(false);
				for (int i = 0; i < names.length; i++) {
					Entity mob = MobFactory.spawnWaveMob(level, Identifier.fromNamespaceAndPath("heartguard", names[i]), middle.offset(xs[i], i == 2 ? 3 : 0, 0), 0, target);
					if (mob instanceof Mob m) {
						m.setNoAi(true);
					}
				}
			});
			context.getInput().lookAt(middle.offset(0, 1, 0));
			context.waitTicks(10);
			int count = context.computeOnClient(mc -> mc.level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(middle).inflate(12.0)).size());
			if (count < 4) {
				throw new AssertionError("the client should see all four mobs, saw " + count);
			}
			context.takeScreenshot("mob-roster");
		}
	}
}
