package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.wave.WaveSpawner;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** A tower left alone for many ticks. Kept apart from the single-tick tests, whose cleanup would remove it too early. */
public class TowerTickGameTest {
	@GameTest(maxTicks = 400)
	public void aPlacedTowerShootsOnItsOwn(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		TestArena.forceLoad(context, new BlockPos(-6, 0, -6), new BlockPos(12, 4, 6));
		for (int x = -4; x <= 10; x++) {
			for (int z = -4; z <= 4; z++) {
				context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		ServerPlayer owner = context.makeMockServerPlayerInLevel();
		BlockPos pos = context.absolutePos(new BlockPos(0, 1, 0));
		level.setBlock(pos, ModBlocks.ARROW_TOWER.defaultBlockState(), Block.UPDATE_ALL);
		((TowerBlockEntity) level.getBlockEntity(pos)).initialize(owner.getUUID(), TowerDefs.get(TowerDefs.ARROW));

		Mob target = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
		target.setPos(pos.getCenter().add(4.0, -0.5, 0.0));
		target.setNoAi(true);
		target.addTag(WaveSpawner.WAVE_MOB_TAG);
		level.addFreshEntity(target);
		Rewards.setKillReward(target, 9);

		context.succeedWhen(() -> {
			context.assertTrue(!target.isAlive(), "the tower should kill the husk by itself, health " + target.getHealth());
			context.assertTrue(Coins.balance(owner) == 9, "and pay its owner, had " + Coins.balance(owner));
		});
	}
}
