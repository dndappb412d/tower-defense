package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.placement.PlacementService;
import dev.vadim.heartguard.placement.PlacementService.Result;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Server-side placement rules: the client only asks, these tests pin down what the server answers. */
public class PlacementGameTest {
	private static final BlockPos GROUND = new BlockPos(0, 0, 0);
	private static final BlockPos TARGET = new BlockPos(0, 1, 0);

	/** A mock player standing next to the target, in range. */
	private static ServerPlayer playerNear(GameTestHelper context) {
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(TARGET).getCenter());
		return player;
	}

	private static void expect(GameTestHelper context, Result expected, Result actual) {
		context.assertTrue(actual == expected, "expected " + expected + " but was " + actual);
	}

	@GameTest
	public void placesMarkerOnSolidGround(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		Result result = PlacementService.tryPlace(playerNear(context), context.absolutePos(TARGET));
		expect(context, Result.PLACED, result);
		context.assertBlockPresent(PlacementService.MARKER, TARGET);
		context.succeed();
	}

	@GameTest
	public void rejectsFloatingPosition(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.AIR);
		context.setBlock(TARGET, Blocks.AIR);
		Result result = PlacementService.tryPlace(playerNear(context), context.absolutePos(TARGET));
		expect(context, Result.NO_SUPPORT, result);
		context.assertBlockNotPresent(PlacementService.MARKER, TARGET);
		context.succeed();
	}

	@GameTest
	public void rejectsOccupiedPosition(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.DIRT);
		Result result = PlacementService.tryPlace(playerNear(context), context.absolutePos(TARGET));
		expect(context, Result.OBSTRUCTED, result);
		context.assertBlockPresent(Blocks.DIRT, TARGET);
		context.succeed();
	}

	@GameTest
	public void rejectsPositionFarFromPlayer(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TARGET, Blocks.AIR);
		ServerPlayer player = playerNear(context);
		player.setPos(player.position().add(PlacementService.MAX_DISTANCE + 10.0, 0.0, 0.0));
		Result result = PlacementService.tryPlace(player, context.absolutePos(TARGET));
		expect(context, Result.OUT_OF_RANGE, result);
		context.assertBlockNotPresent(PlacementService.MARKER, TARGET);
		context.succeed();
	}

	@GameTest
	public void rejectsPositionOutsideWorld(GameTestHelper context) {
		BlockPos belowWorld = context.absolutePos(new BlockPos(0, -1000, 0));
		Result result = PlacementService.tryPlace(playerNear(context), belowWorld);
		expect(context, Result.OUT_OF_WORLD, result);
		context.succeed();
	}
}
