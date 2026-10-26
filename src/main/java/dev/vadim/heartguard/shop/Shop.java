package dev.vadim.heartguard.shop;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side authority over the Quartermaster. Using the block opens the shop and remembers which one; a purchase
 * is honoured only for a shop the player has open, is still standing next to, and can afford. Prices and items come
 * from the shop file, never from the client.
 */
public final class Shop {
	/** How close, in blocks, a player must stay to the Quartermaster to buy. */
	public static final double BUY_RANGE = 8.0;

	public enum Result {
		BOUGHT,
		NO_SHOP_OPEN,
		TOO_FAR,
		NO_SUCH_ITEM,
		NOT_ENOUGH_COINS
	}

	/** The Quartermaster each player has open. Not saved: a reconnecting player just uses the block again. */
	private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();

	private Shop() {}

	public static void init() {
		ShopDef.register();
		PayloadTypeRegistry.clientboundPlay().register(OpenShopPayload.TYPE, OpenShopPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuyPayload.TYPE, BuyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BuyPayload.TYPE, (payload, context) -> {
			Result result = buy(context.player(), payload.index());
			feedback(context.player(), result);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.getBlockState(hit.getBlockPos()).is(ModBlocks.QUARTERMASTER)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server && !player.isSpectator()) {
				open(server, hit.getBlockPos());
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.getPlayer().getUUID()));
	}

	/** Remembers the shop and tells the client to show it. */
	public static void open(ServerPlayer player, BlockPos quartermaster) {
		ShopDef def = ShopDef.get(ShopDef.DEFAULT_ID);
		if (def == null) {
			player.sendSystemMessage(Component.literal("The shop has nothing to sell"), true);
			return;
		}
		OPEN.put(player.getUUID(), quartermaster.immutable());
		List<OpenShopPayload.Offer> offers = new ArrayList<>();
		for (ShopDef.Entry entry : def.items()) {
			offers.add(new OpenShopPayload.Offer(entry.stack(), entry.price(), entry.category()));
		}
		ServerPlayNetworking.send(player, new OpenShopPayload(offers));
	}

	public static boolean hasOpenShop(ServerPlayer player) {
		return OPEN.containsKey(player.getUUID());
	}

	public static Result buy(ServerPlayer player, int index) {
		BlockPos shop = OPEN.get(player.getUUID());
		if (shop == null || !player.level().getBlockState(shop).is(ModBlocks.QUARTERMASTER)) {
			OPEN.remove(player.getUUID());
			return Result.NO_SHOP_OPEN;
		}
		if (shop.getCenter().distanceToSqr(player.position()) > BUY_RANGE * BUY_RANGE) {
			return Result.TOO_FAR;
		}
		ShopDef def = ShopDef.get(ShopDef.DEFAULT_ID);
		if (def == null || index < 0 || index >= def.items().size()) {
			return Result.NO_SUCH_ITEM;
		}
		ShopDef.Entry entry = def.items().get(index);
		if (!Coins.spend(player, entry.price())) {
			return Result.NOT_ENOUGH_COINS;
		}
		ItemStack bought = entry.stack();
		// Vanilla add() swallows the stack for creative players with a full inventory, so check for room first.
		if (hasRoomFor(player, bought)) {
			player.getInventory().add(bought);
		}
		if (!bought.isEmpty()) {
			player.drop(bought, false);
		}
		return Result.BOUGHT;
	}

	/** Whether the main inventory can take the whole stack, counting free slots and room on matching stacks. */
	static boolean hasRoomFor(ServerPlayer player, ItemStack stack) {
		int room = 0;
		for (ItemStack held : player.getInventory().getNonEquipmentItems()) {
			if (held.isEmpty()) {
				room += stack.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(held, stack)) {
				room += held.getMaxStackSize() - held.getCount();
			}
		}
		return room >= stack.getCount();
	}

	private static void feedback(ServerPlayer player, Result result) {
		String text = switch (result) {
			case BOUGHT -> "Purchased";
			case NO_SHOP_OPEN -> "Open a Quartermaster first";
			case TOO_FAR -> "Too far from the Quartermaster";
			case NO_SUCH_ITEM -> "The Quartermaster does not sell that";
			case NOT_ENOUGH_COINS -> "Not enough coins";
		};
		player.sendSystemMessage(Component.literal(text), true);
	}
}
