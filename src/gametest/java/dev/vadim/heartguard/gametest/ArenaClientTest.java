package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The arena in a real new world, which is where it has to work: the datapack dimension is there, a template places into
 * it, a player can be moved in and sees it, and towers and mobs tick inside it.
 */
public class ArenaClientTest implements FabricClientGameTest {
	public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("heartguard", "arena"));
	private static final Identifier SPIKE_TEMPLATE = Identifier.fromNamespaceAndPath("heartguard", "spike");
	private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();

			boolean exists = world.getServer().computeOnServer(server -> server.getLevel(ARENA) != null);
			Heartguard.LOGGER.info("[arena spike] heartguard:arena exists in a new singleplayer world: {}", exists);
			check(exists, "the heartguard:arena dimension should exist in a new world");

			// A template from the datapack places into the arena.
			boolean placed = world.getServer().computeOnServer(server -> {
				ServerLevel arena = server.getLevel(ARENA);
				for (int cx = -1; cx <= 1; cx++) {
					for (int cz = -1; cz <= 1; cz++) {
						arena.setChunkForced(cx, cz, true);
					}
				}
				StructureTemplate template = server.getStructureManager().get(SPIKE_TEMPLATE).orElseThrow();
				return template.placeInWorld(arena, ORIGIN, ORIGIN, new StructurePlaceSettings(), arena.getRandom(), Block.UPDATE_ALL);
			});
			check(placed, "the template should place into the arena");
			boolean shapeOk = world.getServer().computeOnServer(server -> {
				ServerLevel arena = server.getLevel(ARENA);
				return arena.getBlockState(ORIGIN).is(Blocks.STONE) && arena.getBlockState(ORIGIN.offset(3, 1, 3)).is(Blocks.GOLD_BLOCK)
					&& arena.getBlockState(ORIGIN.offset(0, 1, 0)).isAir() && arena.getBlockState(ORIGIN.offset(20, 0, 20)).isAir();
			});
			check(shapeOk, "the platform has the shape in the template and nothing else is built around it");

			// The player moves in and the client follows.
			UUID playerId = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.teleport(new TeleportTransition(server.getLevel(ARENA), Vec3.atBottomCenterOf(ORIGIN.offset(1, 1, 1)), Vec3.ZERO, 0.0F, 20.0F, TeleportTransition.DO_NOTHING));
				return player.getUUID();
			});
			boolean clientThere = false;
			for (int tick = 0; tick < 100 && !clientThere; tick++) {
				context.waitTick();
				clientThere = context.computeOnClient(mc -> mc.level != null && mc.level.dimension().equals(ARENA) && mc.player != null);
			}
			check(clientThere, "the client should switch to the arena dimension after the player is teleported");
			world.getClientLevel().waitForChunksRender();
			context.takeScreenshot("arena-platform");

			// Towers and mobs run in the arena.
			BlockPos towerPos = ORIGIN.offset(1, 1, 3);
			Mob target = world.getServer().computeOnServer(server -> {
				ServerLevel arena = server.getLevel(ARENA);
				arena.setBlock(towerPos, ModBlocks.ARROW_TOWER.defaultBlockState(), Block.UPDATE_ALL);
				((TowerBlockEntity) arena.getBlockEntity(towerPos)).initialize(playerId, TowerDefs.get(TowerDefs.ARROW));
				Mob husk = EntityType.HUSK.create(arena, EntitySpawnReason.EVENT);
				husk.setPos(Vec3.atBottomCenterOf(ORIGIN.offset(5, 1, 3)));
				husk.setNoAi(true);
				husk.addTag(WaveSpawner.WAVE_MOB_TAG);
				arena.addFreshEntity(husk);
				Rewards.setKillReward(husk, 3);
				return husk;
			});
			boolean dead = false;
			for (int tick = 0; tick < 300 && !dead; tick++) {
				context.waitTick();
				dead = !target.isAlive();
			}
			context.takeScreenshot("arena-tower-fired");
			check(dead, "a tower in the arena should kill a husk there by itself, health " + target.getHealth());
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
