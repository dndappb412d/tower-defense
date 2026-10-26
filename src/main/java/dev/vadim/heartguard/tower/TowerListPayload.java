package dev.vadim.heartguard.tower;

import dev.vadim.heartguard.Heartguard;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: every tower type with its numbers, so the client palette never depends on data it cannot see. */
public record TowerListPayload(Map<Identifier, TowerDef> towers) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TowerListPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "tower_list"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TowerListPayload> CODEC = ByteBufCodecs
		.<RegistryFriendlyByteBuf, Identifier, TowerDef, Map<Identifier, TowerDef>>map(
			HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.fromCodec(TowerDef.CODEC))
		.map(TowerListPayload::new, TowerListPayload::towers);

	public TowerListPayload {
		towers = Map.copyOf(towers);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
