package dev.vadim.heartguard.match;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client intent from the end screen: play the same map again, or leave the arena. The server checks the match is over. */
public record EndActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		REPLAY,
		LEAVE
	}

	public static final CustomPacketPayload.Type<EndActionPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "end_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EndActionPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, payload -> payload.action().ordinal(),
			i -> new EndActionPayload(Action.values()[Math.floorMod(i, Action.values().length)]));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
