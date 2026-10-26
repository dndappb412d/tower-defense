package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.vein.VeinData;
import dev.vadim.heartguard.vein.VeinDef;
import dev.vadim.heartguard.vein.Veins;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Gold veins: what they pay, how they empty, how they grow back, and that none of it needs an item. */
public class VeinGameTest {
	private static final BlockPos SPOT = new BlockPos(0, 1, 0);
	private static final BlockPos SECOND = new BlockPos(2, 1, 0);

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static ServerPlayer miner(GameTestHelper context) {
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(SPOT).getCenter());
		return player;
	}

	private static VeinData veins(ServerLevel level) {
		return level.getAttachedOrCreate(VeinData.ATTACHMENT);
	}

	@GameTest
	public void veinKindsLoadAndRichPaysMore(GameTestHelper context) {
		VeinDef poor = VeinDef.get(VeinDef.POOR);
		VeinDef rich = VeinDef.get(VeinDef.RICH);
		expect(context, poor != null && rich != null, "poor and rich veins should be loaded, have " + VeinDef.all().keySet());
		expect(context, rich.payout() > poor.payout(), "a rich vein pays more: " + rich.payout() + " vs " + poor.payout());
		expect(context, VeinDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{ \"payout\": 0 }")).result().isEmpty(), "a zero payout is rejected");
		expect(context, VeinDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{ }")).result().isEmpty(), "a missing payout is rejected");
		expect(context, ModBlocks.veinDefId(ModBlocks.POOR_VEIN).equals(VeinDef.POOR) && ModBlocks.veinDefId(ModBlocks.RICH_VEIN).equals(VeinDef.RICH), "each block maps to its kind");
		expect(context, ModBlocks.veinDefId(Blocks.STONE) == null && ModBlocks.veinDefId(ModBlocks.DEPLETED_VEIN) == null, "other blocks, depleted veins included, pay nothing");
		context.succeed();
	}

	@GameTest
	public void miningAVeinPaysTheMinerAndLeavesItDepleted(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		ServerPlayer player = miner(context);
		BlockPos pos = context.absolutePos(SPOT);
		context.setBlock(SPOT, ModBlocks.POOR_VEIN);
		int payout = VeinDef.get(VeinDef.POOR).payout();

		player.gameMode.destroyBlock(pos);
		expect(context, Coins.balance(player) == payout, "the miner is paid " + payout + ", had " + Coins.balance(player));
		context.assertBlockPresent(ModBlocks.DEPLETED_VEIN, SPOT);
		expect(context, veins(level).depleted().stream().anyMatch(e -> e.pos().equals(pos)), "the empty vein is remembered for regrowth");
		expect(context, level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0)).isEmpty(), "mining drops no items, the gold is coins");

		player.gameMode.destroyBlock(pos);
		expect(context, Coins.balance(player) == payout, "a depleted vein pays nothing more");
		veinCleanup(level, pos);
		context.succeed();
	}

	@GameTest
	public void richVeinsPayTheirOwnAmount(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		ServerPlayer player = miner(context);
		context.setBlock(SPOT, ModBlocks.RICH_VEIN);
		player.gameMode.destroyBlock(context.absolutePos(SPOT));
		expect(context, Coins.balance(player) == VeinDef.get(VeinDef.RICH).payout(), "a rich vein pays the rich amount, had " + Coins.balance(player));
		veinCleanup(level, context.absolutePos(SPOT));
		context.succeed();
	}

	@GameTest
	public void regenerationRestoresEveryVeinAsItWas(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel level = context.getLevel();
		ServerPlayer player = miner(context);
		BlockPos poorPos = context.absolutePos(SPOT);
		BlockPos richPos = context.absolutePos(SECOND);
		context.setBlock(SPOT, ModBlocks.POOR_VEIN);
		context.setBlock(SECOND, ModBlocks.RICH_VEIN);
		player.gameMode.destroyBlock(poorPos);
		player.gameMode.destroyBlock(richPos);
		context.assertBlockPresent(ModBlocks.DEPLETED_VEIN, SPOT);
		context.assertBlockPresent(ModBlocks.DEPLETED_VEIN, SECOND);

		int regrown = Veins.regenerate(server);
		expect(context, regrown >= 2, "at least both of our veins grow back, regrown " + regrown);
		context.assertBlockPresent(ModBlocks.POOR_VEIN, SPOT);
		context.assertBlockPresent(ModBlocks.RICH_VEIN, SECOND);
		expect(context, veins(level).depleted().stream().noneMatch(e -> e.pos().equals(poorPos) || e.pos().equals(richPos)), "regrown veins leave the list");

		int before = Coins.balance(player);
		player.gameMode.destroyBlock(poorPos);
		expect(context, Coins.balance(player) - before == VeinDef.get(VeinDef.POOR).payout(), "a regrown vein pays again");
		veinCleanup(level, poorPos, richPos);
		context.succeed();
	}

	@GameTest
	public void regenerationLeavesReplacedAndUnloadedEntriesAlone(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel level = context.getLevel();
		ServerPlayer player = miner(context);
		BlockPos pos = context.absolutePos(SPOT);
		context.setBlock(SPOT, ModBlocks.POOR_VEIN);
		player.gameMode.destroyBlock(pos);
		level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);

		BlockPos faraway = new BlockPos(30_000_000, 70, 30_000_000);
		level.setAttached(VeinData.ATTACHMENT, veins(level).with(new VeinData.Entry(faraway, Identifier.fromNamespaceAndPath("heartguard", "poor_vein"))));

		Veins.regenerate(server);
		context.assertBlockPresent(Blocks.DIRT, SPOT);
		expect(context, veins(level).depleted().stream().noneMatch(e -> e.pos().equals(pos)), "an entry whose block was replaced is dropped");
		expect(context, veins(level).depleted().stream().anyMatch(e -> e.pos().equals(faraway)), "an entry in an unloaded chunk waits for later");
		veinCleanup(level, pos, faraway);
		context.succeed();
	}

	@GameTest
	public void veinsGrowBackWhenAWaveIsCleared(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel level = context.getLevel();
		ServerPlayer player = miner(context);
		BlockPos pos = context.absolutePos(SPOT);
		context.setBlock(SPOT, ModBlocks.RICH_VEIN);
		try {
			Match.update(server, d -> MatchData.EMPTY.withHeart(pos.east(9)).withSpawn(pos.west(9)).started(2).advanced());
			expect(context, Match.get(server).phase() == MatchData.Phase.WAVE, "setup: a wave is on");
			player.gameMode.destroyBlock(pos);
			context.assertBlockPresent(ModBlocks.DEPLETED_VEIN, SPOT);

			Match.update(server, MatchData::advanced);
			expect(context, Match.get(server).phase() == MatchData.Phase.PAYOUT, "the wave ended");
			expect(context, level.getBlockState(pos).is(ModBlocks.RICH_VEIN), "the vein should have regrown; block " + level.getBlockState(pos)
				+ ", list " + veins(level).depleted() + ", levelIsOverworld " + (level == server.overworld()) + ", loaded " + level.isLoaded(pos));
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
			veinCleanup(level, pos);
		}
		context.succeed();
	}

	@GameTest
	public void veinsAreMinedWithPickaxesAndDepletedOnesNeverAre(GameTestHelper context) {
		ServerPlayer player = miner(context);
		var vein = ModBlocks.POOR_VEIN.defaultBlockState();
		expect(context, vein.is(BlockTags.MINEABLE_WITH_PICKAXE), "veins are pickaxe blocks");
		float byHand = player.getDestroySpeed(vein);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		float withPickaxe = player.getDestroySpeed(vein);
		expect(context, withPickaxe > byHand, "a pickaxe digs faster than a hand: " + withPickaxe + " vs " + byHand);
		expect(context, ModBlocks.DEPLETED_VEIN.defaultBlockState().getDestroySpeed(context.getLevel(), context.absolutePos(SPOT)) < 0.0F, "a depleted vein is unbreakable");
		context.succeed();
	}

	@GameTest
	public void depletedVeinListSurvivesEncoding(GameTestHelper context) {
		VeinData original = VeinData.EMPTY
			.with(new VeinData.Entry(new BlockPos(1, 64, 2), Identifier.fromNamespaceAndPath("heartguard", "poor_vein")))
			.with(new VeinData.Entry(new BlockPos(-5, 70, 9), Identifier.fromNamespaceAndPath("heartguard", "rich_vein")));
		var encoded = VeinData.CODEC.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
		VeinData decoded = VeinData.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
		expect(context, decoded.equals(original), "decoded list should equal the original: " + decoded);
		expect(context, original.with(new VeinData.Entry(new BlockPos(1, 64, 2), Identifier.fromNamespaceAndPath("heartguard", "rich_vein"))).depleted().size() == 2, "one entry per position");
		context.succeed();
	}

	/** The depleted list is world state shared by every test, so each test takes its own entries back out. */
	private static void veinCleanup(ServerLevel level, BlockPos... positions) {
		List<BlockPos> mine = List.of(positions);
		VeinData data = veins(level);
		level.setAttached(VeinData.ATTACHMENT, data.without(data.depleted().stream().filter(e -> mine.contains(e.pos())).toList()));
	}
}
