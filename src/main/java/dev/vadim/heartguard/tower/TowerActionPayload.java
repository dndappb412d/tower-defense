package dev.vadim.heartguard.tower;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client intent: do this to the tower at this block. The server decides whether it is allowed. */
public record TowerActionPayload(BlockPos pos, Action action) implements CustomPacketPayload {
	public enum Action {
		UPGRADE,
		SELL,
		CYCLE_TARGETING
	}

	public static final CustomPacketPayload.Type<TowerActionPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "tower_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TowerActionPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TowerActionPayload::pos,
		ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), TowerActionPayload::action,
		TowerActionPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
