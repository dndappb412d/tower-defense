package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.placement.BuildRules;
import dev.vadim.heartguard.placement.PlacementService;
import dev.vadim.heartguard.tower.TowerActionPayload;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerBlockEntity.Targeting;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.tower.TowerListPayload;
import dev.vadim.heartguard.tower.TowerService;
import dev.vadim.heartguard.tower.TowerService.Result;
import dev.vadim.heartguard.tower.TowerSync;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Upgrading, selling and mode changes, the Command Post rule, and the packets that carry them. */
public class TowerManagementGameTest implements CustomTestMethodInvoker {
	private static final BlockPos GROUND = new BlockPos(0, 0, 0);
	private static final BlockPos TOWER = new BlockPos(0, 1, 0);
	private static final BlockPos POST = new BlockPos(1, 1, 0);

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static void expectResult(GameTestHelper context, Result expected, Result actual) {
		expect(context, actual == expected, "expected " + expected + " but was " + actual);
	}

	/** A player next to the tower with coins, who has built an arrow tower on stone. */
	private static ServerPlayer builder(GameTestHelper context) {
		context.setBlock(GROUND, Blocks.STONE);
		context.setBlock(TOWER, Blocks.AIR);
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(TOWER).getCenter());
		Coins.add(player, 1000);
		PlacementService.Result placed = PlacementService.tryPlace(player, TowerDefs.ARROW, context.absolutePos(TOWER));
		expect(context, placed == PlacementService.Result.PLACED, "setup: the tower should build, was " + placed);
		return player;
	}

	private static TowerBlockEntity tower(GameTestHelper context) {
		return (TowerBlockEntity) context.getLevel().getBlockEntity(context.absolutePos(TOWER));
	}

	// --- upgrade ---

	@GameTest
	public void upgradingCostsTheTierPriceAndRaisesTheTier(GameTestHelper context) {
		ServerPlayer player = builder(context);
		TowerDef def = TowerDefs.get(TowerDefs.ARROW);
		int before = Coins.balance(player);
		tower(context).setHp(1);

		expectResult(context, Result.DONE, TowerService.upgrade(player, context.absolutePos(TOWER)));
		expect(context, tower(context).tier() == 2, "the tower is now tier 2, was " + tower(context).tier());
		expect(context, before - Coins.balance(player) == def.tier(2).upgradeCost(), "tier 2 costs its upgrade price, paid " + (before - Coins.balance(player)));
		expect(context, tower(context).hp() == def.hp(), "an upgrade repairs the tower");

		expectResult(context, Result.DONE, TowerService.upgrade(player, context.absolutePos(TOWER)));
		expect(context, tower(context).tier() == 3, "and then tier 3");
		int atMax = Coins.balance(player);
		expectResult(context, Result.MAX_TIER, TowerService.upgrade(player, context.absolutePos(TOWER)));
		expect(context, tower(context).tier() == 3 && Coins.balance(player) == atMax, "the top tier cannot be passed and costs nothing");
		context.succeed();
	}

	@GameTest
	public void upgradeNeedsTheCoinsOwnershipAndAnActualTower(GameTestHelper context) {
		ServerPlayer owner = builder(context);
		ServerPlayer stranger = context.makeMockServerPlayerInLevel();
		stranger.setPos(owner.position());
		Coins.add(stranger, 1000);
		BlockPos pos = context.absolutePos(TOWER);

		expectResult(context, Result.NOT_YOURS, TowerService.upgrade(stranger, pos));
		expect(context, tower(context).tier() == 1 && Coins.balance(stranger) == 1000, "a stranger changes nothing and pays nothing");

		Coins.spend(owner, Coins.balance(owner));
		expectResult(context, Result.NOT_ENOUGH_COINS, TowerService.upgrade(owner, pos));
		expect(context, tower(context).tier() == 1, "a broke owner cannot upgrade");

		expectResult(context, Result.NOT_A_TOWER, TowerService.upgrade(owner, context.absolutePos(new BlockPos(5, 1, 0))));
		context.succeed();
	}

	@GameTest
	public void upgradeIsRefusedFromTooFarAway(GameTestHelper context) {
		ServerPlayer owner = builder(context);
		owner.setPos(owner.position().add(PlacementService.MAX_DISTANCE + 10.0, 0.0, 0.0));
		expectResult(context, Result.OUT_OF_RANGE, TowerService.upgrade(owner, context.absolutePos(TOWER)));
		context.succeed();
	}

	// --- sell ---

	@GameTest
	public void sellingRemovesTheTowerAndRefundsItsShare(GameTestHelper context) {
		ServerPlayer owner = builder(context);
		TowerDef def = TowerDefs.get(TowerDefs.ARROW);
		TowerService.upgrade(owner, context.absolutePos(TOWER));
		int before = Coins.balance(owner);

		expectResult(context, Result.DONE, TowerService.sell(owner, context.absolutePos(TOWER)));
		context.assertBlockNotPresent(ModBlocks.ARROW_TOWER, TOWER);
		expect(context, Coins.balance(owner) - before == def.sellValue(2), "selling a tier 2 tower returns " + def.sellValue(2) + ", got " + (Coins.balance(owner) - before));
		expectResult(context, Result.NOT_A_TOWER, TowerService.sell(owner, context.absolutePos(TOWER)));
		context.succeed();
	}

	@GameTest
	public void onlyTheOwnerCanSell(GameTestHelper context) {
		ServerPlayer owner = builder(context);
		ServerPlayer stranger = context.makeMockServerPlayerInLevel();
		stranger.setPos(owner.position());
		expectResult(context, Result.NOT_YOURS, TowerService.sell(stranger, context.absolutePos(TOWER)));
		context.assertBlockPresent(ModBlocks.ARROW_TOWER, TOWER);
		expect(context, Coins.balance(stranger) == 0, "a refused sale pays nothing");
		context.succeed();
	}

	// --- targeting mode ---

	@GameTest
	public void cyclingMovesThroughEveryModeAndBack(GameTestHelper context) {
		ServerPlayer owner = builder(context);
		BlockPos pos = context.absolutePos(TOWER);
		expect(context, tower(context).targeting() == Targeting.FIRST, "towers start on FIRST");
		TowerService.cycleTargeting(owner, pos);
		expect(context, tower(context).targeting() == Targeting.STRONGEST, "then STRONGEST");
		TowerService.cycleTargeting(owner, pos);
		expect(context, tower(context).targeting() == Targeting.CLOSEST, "then CLOSEST");
		TowerService.cycleTargeting(owner, pos);
		expect(context, tower(context).targeting() == Targeting.FIRST, "and back to FIRST");
		context.succeed();
	}

	// --- the Command Post rule ---

	@GameTest
	public void duringAWaveTowersNeedACommandPost(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerPlayer owner = builder(context);
		BlockPos tower = context.absolutePos(TOWER);
		BlockPos spot = context.absolutePos(new BlockPos(3, 1, 0));
		context.setBlock(new BlockPos(3, 0, 0), Blocks.STONE);
		try {
			Match.update(server, d -> MatchData.EMPTY.withHeart(tower.east(9)).withSpawn(tower.west(9)).started(1).advanced());
			expect(context, Match.get(server).phase() == MatchData.Phase.WAVE, "setup: a wave is on");
			expect(context, !BuildRules.canCommandNow(owner), "no Command Post nearby, so no command");

			expect(context, PlacementService.tryPlace(owner, TowerDefs.ARROW, spot) == PlacementService.Result.COMMAND_POST_REQUIRED, "building is refused");
			expectResult(context, Result.COMMAND_POST_REQUIRED, TowerService.upgrade(owner, tower));
			expectResult(context, Result.COMMAND_POST_REQUIRED, TowerService.sell(owner, tower));
			expectResult(context, Result.COMMAND_POST_REQUIRED, TowerService.cycleTargeting(owner, tower));
			expect(context, this_tierIs(context, 1), "nothing changed");

			context.setBlock(POST, ModBlocks.COMMAND_POST);
			expect(context, BuildRules.canCommandNow(owner), "standing at a Command Post, command works");
			expect(context, PlacementService.tryPlace(owner, TowerDefs.ARROW, spot) == PlacementService.Result.PLACED, "building works at a post");
			expectResult(context, Result.DONE, TowerService.upgrade(owner, tower));

			context.setBlock(POST, Blocks.AIR);
			owner.setPos(owner.position().add(BuildRules.COMMAND_POST_REACH + 3.0, 0.0, 0.0));
			context.setBlock(POST, ModBlocks.COMMAND_POST);
			expect(context, !BuildRules.canCommandNow(owner), "a post out of reach does not count");
		} finally {
			context.setBlock(POST, Blocks.AIR);
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	private static boolean this_tierIs(GameTestHelper context, int tier) {
		return tower(context).tier() == tier;
	}

	@GameTest
	public void outsideAWaveCommandWorksAnywhere(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerPlayer owner = builder(context);
		try {
			expect(context, BuildRules.canCommandNow(owner), "with no match running there is no restriction");
			BlockPos heart = context.absolutePos(TOWER).east(9);
			Match.update(server, d -> MatchData.EMPTY.withHeart(heart).withSpawn(heart.west(18)).started(1));
			expect(context, Match.get(server).phase() == MatchData.Phase.BUILD, "setup: build phase");
			expect(context, BuildRules.canCommandNow(owner), "the build phase is free command");
			expectResult(context, Result.DONE, TowerService.upgrade(owner, context.absolutePos(TOWER)));
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	// --- syncing ---

	@GameTest
	public void towerStateReachesTheClientsInTheUpdateTag(GameTestHelper context) {
		builder(context);
		TowerBlockEntity tower = tower(context);
		tower.setTier(3);
		tower.setTargeting(Targeting.CLOSEST);
		var tag = tower.getUpdateTag(context.getLevel().registryAccess());
		expect(context, tag.contains("tier") && tag.contains("targeting") && tag.contains("hp"), "the update tag carries tier, mode and HP: " + tag);
		expect(context, tower.getUpdatePacket() != null, "the tower has an update packet");
		context.succeed();
	}

	@GameTest
	public void towerListPacketRoundTrips(GameTestHelper context) {
		TowerListPayload sent = TowerSync.snapshot();
		expect(context, sent.towers().size() >= 3, "the snapshot has the three shipped towers, has " + sent.towers().keySet());

		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
		TowerListPayload.CODEC.encode(buffer, sent);
		TowerListPayload received = TowerListPayload.CODEC.decode(buffer);
		expect(context, received.equals(sent), "the list should arrive unchanged");
		TowerDef frost = received.towers().get(TowerDefs.FROST);
		expect(context, frost != null && frost.equals(TowerDefs.get(TowerDefs.FROST)), "including every number of the frost tower");
		context.succeed();
	}

	@GameTest
	public void towerActionPacketRoundTrips(GameTestHelper context) {
		for (TowerActionPayload.Action action : TowerActionPayload.Action.values()) {
			TowerActionPayload sent = new TowerActionPayload(new BlockPos(5, 64, -3), action);
			var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
			TowerActionPayload.CODEC.encode(buffer, sent);
			expect(context, TowerActionPayload.CODEC.decode(buffer).equals(sent), action + " should arrive unchanged");
		}
		context.succeed();
	}

	/** Remove any towers the test built before the next tick, so they cannot shoot other tests' mobs. */
	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		try {
			method.invoke(this, context);
		} finally {
			TowerCleanup.clear(context);
		}
	}
}
