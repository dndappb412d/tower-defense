package dev.vadim.heartguard.gametest;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class HeartguardGameTest implements CustomTestMethodInvoker {
	@GameTest
	public void trivialPlacedBlockIsPresent(GameTestHelper context) {
		context.assertBlockPresent(Blocks.STONE, 0, 0, 0);
		context.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		context.setBlock(0, 0, 0, Blocks.STONE);
		method.invoke(this, context);
	}
}
