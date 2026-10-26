package dev.vadim.heartguard.placement;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client intent: "build this tower type at this block". The server decides whether that is allowed and affordable. */
public record PlaceTowerPayload(Identifier tower, BlockPos pos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlaceTowerPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "place_tower"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceTowerPayload> CODEC = StreamCodec.composite(
		Identifier.STREAM_CODEC, PlaceTowerPayload::tower,
		BlockPos.STREAM_CODEC, PlaceTowerPayload::pos,
		PlaceTowerPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
