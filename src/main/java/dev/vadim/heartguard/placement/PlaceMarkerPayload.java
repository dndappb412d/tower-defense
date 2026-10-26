package dev.vadim.heartguard.placement;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client intent: "put a marker at this block". The server decides whether that is allowed. */
public record PlaceMarkerPayload(BlockPos pos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlaceMarkerPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "place_marker"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceMarkerPayload> CODEC =
		StreamCodec.composite(BlockPos.STREAM_CODEC, PlaceMarkerPayload::pos, PlaceMarkerPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
