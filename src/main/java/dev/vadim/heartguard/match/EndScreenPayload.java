package dev.vadim.heartguard.match;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the match is over. {@code canReplay} is true when the server knows which map to start again. */
public record EndScreenPayload(boolean won, int wave, int total, int heartHp, boolean canReplay) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EndScreenPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "end_screen"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EndScreenPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, EndScreenPayload::won,
		ByteBufCodecs.VAR_INT, EndScreenPayload::wave,
		ByteBufCodecs.VAR_INT, EndScreenPayload::total,
		ByteBufCodecs.VAR_INT, EndScreenPayload::heartHp,
		ByteBufCodecs.BOOL, EndScreenPayload::canReplay,
		EndScreenPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
