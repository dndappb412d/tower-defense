package dev.vadim.heartguard.shop;

import dev.vadim.heartguard.Heartguard;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Server to client: open the shop screen with these items. The client shows them; the server decides every purchase. */
public record OpenShopPayload(List<Offer> offers) implements CustomPacketPayload {
	/** One line of the shop as the client sees it: what you get, what it costs, and where it is listed. */
	public record Offer(ItemStack stack, int price, String category) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Offer::stack,
			ByteBufCodecs.VAR_INT, Offer::price,
			ByteBufCodecs.STRING_UTF8, Offer::category,
			Offer::new
		);

		@Override
		public boolean equals(Object other) {
			return other instanceof Offer o && ItemStack.matches(stack, o.stack) && price == o.price && category.equals(o.category);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(ItemStack.hashItemAndComponents(stack), price, category);
		}
	}

	public static final CustomPacketPayload.Type<OpenShopPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "open_shop"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> CODEC =
		Offer.CODEC.apply(ByteBufCodecs.list()).map(OpenShopPayload::new, OpenShopPayload::offers);

	public OpenShopPayload {
		offers = List.copyOf(offers);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
