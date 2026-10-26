package dev.vadim.heartguard.shop;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client intent: buy the {@code index}th item of the shop I have open. The server checks the rest. */
public record BuyPayload(int index) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BuyPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "buy"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BuyPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, BuyPayload::index, BuyPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
