package dev.vadim.heartguard.wave;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.mob.MobDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What is coming in the next wave, worked out on the server from the wave file and the mob definitions and sent to
 * clients, which only draw it. An empty {@code entries} list means there is nothing to show (no match, or a wave is
 * running).
 */
public record WavePreview(int wave, int total, List<Entry> entries) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WavePreview> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "wave_preview"));

	public static final WavePreview NONE = new WavePreview(0, 0, List.of());

	/** One kind of mob in the wave: how many, and the traits a player plans around. */
	public record Entry(String name, int count, boolean boss, boolean flies) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Entry::name,
			ByteBufCodecs.VAR_INT, Entry::count,
			ByteBufCodecs.BOOL, Entry::boss,
			ByteBufCodecs.BOOL, Entry::flies,
			Entry::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, WavePreview> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, WavePreview::wave,
		ByteBufCodecs.VAR_INT, WavePreview::total,
		Entry.CODEC.apply(ByteBufCodecs.list()), WavePreview::entries,
		WavePreview::new);

	public WavePreview {
		entries = List.copyOf(entries);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** The preview for a match in its current state: the wave after this one, shown while the players are building. */
	public static WavePreview of(MatchData match, @Nullable WaveSet set) {
		if (set == null || (match.phase() != MatchData.Phase.BUILD && match.phase() != MatchData.Phase.PAYOUT)) {
			return NONE;
		}
		int next = match.wave() + 1;
		if (next > set.waves().size()) {
			if (!match.sandbox()) {
				return NONE;
			}
			next = 1;
		}
		return new WavePreview(next, set.waves().size(), describe(set.waves().get(next - 1)));
	}

	/** The wave's mobs by kind, in the order the file first names them. */
	public static List<Entry> describe(WaveSet.Wave wave) {
		Map<Identifier, Integer> counts = new LinkedHashMap<>();
		for (WaveSet.SpawnEntry spawn : wave.spawns()) {
			counts.merge(spawn.mob(), spawn.count(), Integer::sum);
		}
		List<Entry> entries = new ArrayList<>();
		counts.forEach((id, count) -> {
			MobDef def = MobDef.get(id);
			if (def != null) {
				entries.add(new Entry(def.name(), count, def.has(MobDef.Trait.BOSS), def.has(MobDef.Trait.FLIES)));
			} else {
				String name = BuiltInRegistries.ENTITY_TYPE.get(id).map(h -> h.value().getDescription().getString()).orElse(id.toString());
				entries.add(new Entry(name, count, false, false));
			}
		});
		return entries;
	}
}
