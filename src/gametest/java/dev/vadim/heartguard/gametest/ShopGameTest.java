package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.item.HeartRepairKitItem;
import dev.vadim.heartguard.item.ModItems;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.shop.BuyPayload;
import dev.vadim.heartguard.shop.OpenShopPayload;
import dev.vadim.heartguard.shop.Shop;
import dev.vadim.heartguard.shop.Shop.Result;
import dev.vadim.heartguard.shop.ShopDef;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** The Quartermaster: what it sells, how it opens, and every way a purchase can be refused. */
public class ShopGameTest {
	private static final BlockPos SHOP = new BlockPos(1, 1, 0);
	private static final BlockPos PLAYER_SPOT = new BlockPos(0, 1, 0);

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static DataResult<ShopDef> parse(String json) {
		return ShopDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	private static ShopDef shipped() {
		return ShopDef.get(ShopDef.DEFAULT_ID);
	}

	/** A player at the counter with coins, a Quartermaster block in place, and the shop open. */
	private static ServerPlayer shopper(GameTestHelper context, int coins) {
		context.setBlock(SHOP, ModBlocks.QUARTERMASTER);
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(PLAYER_SPOT).getCenter());
		Coins.add(player, coins);
		Shop.open(player, context.absolutePos(SHOP));
		return player;
	}

	private static int indexOf(net.minecraft.world.item.Item item) {
		List<ShopDef.Entry> items = shipped().items();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).item() == item) {
				return i;
			}
		}
		throw new AssertionError(item + " is not in the shipped shop");
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// --- the shop file ---

	@GameTest
	public void theShippedShopSellsAtLeastTenItemsWithPickaxes(GameTestHelper context) {
		ShopDef shop = shipped();
		expect(context, shop != null, "the Quartermaster shop should be loaded from the datapack");
		expect(context, shop.items().size() >= 10, "the MVP shop has about ten items, has " + shop.items().size());
		indexOf(Items.STONE_PICKAXE);
		indexOf(Items.IRON_PICKAXE);
		indexOf(Items.DIAMOND_PICKAXE);
		indexOf(ModItems.HEART_REPAIR_KIT);
		int stone = shop.items().get(indexOf(Items.STONE_PICKAXE)).price();
		int iron = shop.items().get(indexOf(Items.IRON_PICKAXE)).price();
		int diamond = shop.items().get(indexOf(Items.DIAMOND_PICKAXE)).price();
		expect(context, stone < iron && iron < diamond, "better pickaxes cost more: " + stone + " < " + iron + " < " + diamond);
		expect(context, shop.items().stream().allMatch(e -> e.price() > 0 && e.count() > 0), "every price and count is positive");
		context.succeed();
	}

	@GameTest
	public void badShopFilesAreRejected(GameTestHelper context) {
		List<String> bad = List.of(
			"{ \"items\": [] }",
			"{ }",
			"{ \"items\": [ { \"item\": \"minecraft:no_such_item\", \"price\": 5 } ] }",
			"{ \"items\": [ { \"item\": \"minecraft:air\", \"price\": 5 } ] }",
			"{ \"items\": [ { \"item\": \"minecraft:bow\", \"price\": 0 } ] }",
			"{ \"items\": [ { \"item\": \"minecraft:bow\", \"price\": -5 } ] }",
			"{ \"items\": [ { \"item\": \"minecraft:bow\", \"price\": 5, \"count\": 0 } ] }",
			"{ \"items\": [ { \"item\": \"minecraft:bow\" } ] }"
		);
		for (String json : bad) {
			expect(context, parse(json).result().isEmpty(), "this shop should be rejected: " + json);
		}
		var ok = parse("{ \"items\": [ { \"item\": \"minecraft:bow\", \"price\": 5 } ] }").result().orElseThrow();
		expect(context, ok.items().get(0).count() == 1 && ok.items().get(0).category().equals("misc"), "count and category have defaults");
		context.succeed();
	}

	// --- opening ---

	@GameTest
	public void usingTheQuartermasterOpensTheShop(GameTestHelper context) {
		context.setBlock(SHOP, ModBlocks.QUARTERMASTER);
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(PLAYER_SPOT).getCenter());
		BlockHitResult hit = new BlockHitResult(context.absolutePos(SHOP).getCenter(), Direction.UP, context.absolutePos(SHOP), false);

		expect(context, !Shop.hasOpenShop(player), "no shop is open at first");
		InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, context.getLevel(), InteractionHand.MAIN_HAND, hit);
		expect(context, result == InteractionResult.SUCCESS, "using the block is consumed, so nothing gets placed against it, was " + result);
		expect(context, Shop.hasOpenShop(player), "using the Quartermaster opens the shop");

		context.setBlock(new BlockPos(3, 1, 0), Blocks.STONE);
		BlockHitResult other = new BlockHitResult(context.absolutePos(new BlockPos(3, 1, 0)).getCenter(), Direction.UP, context.absolutePos(new BlockPos(3, 1, 0)), false);
		expect(context, UseBlockCallback.EVENT.invoker().interact(player, context.getLevel(), InteractionHand.MAIN_HAND, other) == InteractionResult.PASS, "other blocks are left alone");
		context.succeed();
	}

	// --- buying ---

	@GameTest
	public void buyingChargesThePriceAndGivesTheItem(GameTestHelper context) {
		ServerPlayer player = shopper(context, 500);
		ShopDef.Entry pickaxe = shipped().items().get(indexOf(Items.IRON_PICKAXE));
		ShopDef.Entry arrows = shipped().items().get(indexOf(Items.ARROW));

		expect(context, Shop.buy(player, indexOf(Items.IRON_PICKAXE)) == Result.BOUGHT, "the pickaxe sells");
		expect(context, Coins.balance(player) == 500 - pickaxe.price(), "its price is charged, balance " + Coins.balance(player));
		expect(context, count(player, Items.IRON_PICKAXE) == 1, "and it lands in the inventory");

		expect(context, Shop.buy(player, indexOf(Items.ARROW)) == Result.BOUGHT, "arrows sell");
		expect(context, count(player, Items.ARROW) == arrows.count(), "a whole stack of " + arrows.count() + " arrows arrives, got " + count(player, Items.ARROW));
		expect(context, Coins.balance(player) == 500 - pickaxe.price() - arrows.price(), "one price per purchase, not per item");
		context.succeed();
	}

	@GameTest
	public void everyShopItemCanBeBought(GameTestHelper context) {
		ServerPlayer player = shopper(context, 100_000);
		for (int i = 0; i < shipped().items().size(); i++) {
			ShopDef.Entry entry = shipped().items().get(i);
			int before = Coins.balance(player);
			expect(context, Shop.buy(player, i) == Result.BOUGHT, "entry " + i + " (" + entry.item() + ") should be buyable");
			expect(context, before - Coins.balance(player) == entry.price(), "entry " + i + " charges exactly its price");
		}
		context.succeed();
	}

	@GameTest
	public void poorPlayersBuyNothing(GameTestHelper context) {
		ShopDef.Entry pickaxe = shipped().items().get(indexOf(Items.IRON_PICKAXE));
		ServerPlayer player = shopper(context, pickaxe.price() - 1);
		expect(context, Shop.buy(player, indexOf(Items.IRON_PICKAXE)) == Result.NOT_ENOUGH_COINS, "one coin short is refused");
		expect(context, Coins.balance(player) == pickaxe.price() - 1 && count(player, Items.IRON_PICKAXE) == 0, "nothing was charged or given");
		context.succeed();
	}

	@GameTest
	public void purchasesNeedAnOpenShopWithinReach(GameTestHelper context) {
		context.setBlock(SHOP, ModBlocks.QUARTERMASTER);
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		player.setPos(context.absolutePos(PLAYER_SPOT).getCenter());
		Coins.add(player, 1000);
		int index = indexOf(Items.IRON_PICKAXE);

		expect(context, Shop.buy(player, index) == Result.NO_SHOP_OPEN, "buying without opening a shop is refused");

		Shop.open(player, context.absolutePos(SHOP));
		player.setPos(player.position().add(Shop.BUY_RANGE + 2.0, 0.0, 0.0));
		expect(context, Shop.buy(player, index) == Result.TOO_FAR, "walking away from the counter ends the sale");

		player.setPos(context.absolutePos(PLAYER_SPOT).getCenter());
		expect(context, Shop.buy(player, index) == Result.BOUGHT, "coming back within reach works again");

		context.setBlock(SHOP, Blocks.AIR);
		expect(context, Shop.buy(player, index) == Result.NO_SHOP_OPEN, "a Quartermaster that is gone sells nothing");
		expect(context, count(player, Items.IRON_PICKAXE) == 1, "only the one purchase went through");
		context.succeed();
	}

	@GameTest
	public void forgedIndexesAreRefused(GameTestHelper context) {
		ServerPlayer player = shopper(context, 10_000);
		expect(context, Shop.buy(player, -1) == Result.NO_SUCH_ITEM, "a negative index is refused");
		expect(context, Shop.buy(player, shipped().items().size()) == Result.NO_SUCH_ITEM, "an index past the end is refused");
		expect(context, Shop.buy(player, Integer.MAX_VALUE) == Result.NO_SUCH_ITEM, "a huge index is refused");
		expect(context, Coins.balance(player) == 10_000, "no coins were taken");
		context.succeed();
	}

	@GameTest
	public void aFullInventoryDropsThePurchaseAtTheBuyersFeet(GameTestHelper context) {
		ServerPlayer player = shopper(context, 1000);
		for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
		}
		AABB around = new AABB(player.blockPosition()).inflate(4.0);
		int before = context.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.IRON_PICKAXE)).size();

		expect(context, Shop.buy(player, indexOf(Items.IRON_PICKAXE)) == Result.BOUGHT, "the sale still goes through");
		int after = context.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.IRON_PICKAXE)).size();
		expect(context, after == before + 1 || count(player, Items.IRON_PICKAXE) == 1, "the pickaxe is dropped or held, never lost");
		context.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
		context.succeed();
	}

	// --- the repair kit ---

	@GameTest
	public void theRepairKitMendsTheHeartAndIsSpent(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		BlockPos spot = context.absolutePos(PLAYER_SPOT);
		try {
			Match.update(server, d -> MatchData.EMPTY.withHeart(spot.east(9)).withSpawn(spot.west(9)).started(1));
			Match.damageHeart(server, 50);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HEART_REPAIR_KIT, 2));

			expect(context, ModItems.HEART_REPAIR_KIT.use(context.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "using the kit works");
			expect(context, Match.get(server).heartHp() == MatchData.MAX_HEART_HP - 50 + HeartRepairKitItem.REPAIR_AMOUNT, "the Heart gains " + HeartRepairKitItem.REPAIR_AMOUNT + " HP, has " + Match.get(server).heartHp());
			expect(context, player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "one kit is used up");

			Match.update(server, d -> d.repaired(1000));
			expect(context, ModItems.HEART_REPAIR_KIT.use(context.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "a healthy Heart refuses the kit");
			expect(context, player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "so the kit is kept");

			Match.update(server, d -> MatchData.EMPTY);
			expect(context, ModItems.HEART_REPAIR_KIT.use(context.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "with no match running the kit is kept too");
		} finally {
			Match.update(server, d -> MatchData.EMPTY);
		}
		context.succeed();
	}

	// --- packets ---

	@GameTest
	public void shopPacketsRoundTrip(GameTestHelper context) {
		var registry = context.getLevel().registryAccess();
		var offers = new OpenShopPayload(List.of(
			new OpenShopPayload.Offer(new ItemStack(Items.ARROW, 16), 18, "weapons"),
			new OpenShopPayload.Offer(new ItemStack(ModItems.HEART_REPAIR_KIT), 70, "consumables")
		));
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry);
		OpenShopPayload.CODEC.encode(buffer, offers);
		expect(context, OpenShopPayload.CODEC.decode(buffer).equals(offers), "the offers should arrive unchanged");

		var buy = new BuyPayload(7);
		var buffer2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry);
		BuyPayload.CODEC.encode(buffer2, buy);
		expect(context, BuyPayload.CODEC.decode(buffer2).equals(buy), "the buy index should arrive unchanged");
		context.succeed();
	}

	@GameTest
	public void theRepairKitIdIsStable(GameTestHelper context) {
		expect(context, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.HEART_REPAIR_KIT).equals(Identifier.fromNamespaceAndPath("heartguard", "heart_repair_kit")),
			"shop files refer to the kit as heartguard:heart_repair_kit");
		context.succeed();
	}
}
