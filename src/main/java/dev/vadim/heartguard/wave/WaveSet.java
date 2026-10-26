package dev.vadim.heartguard.wave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;

/**
 * One loadable set of waves, parsed from {@code data/<namespace>/waves/<name>.json}:
 * <pre>
 * { "waves": [ { "spawns": [ { "spawn": 0, "mob": "minecraft:husk", "count": 5,
 *                              "interval_ticks": 40, "delay_ticks": 0 } ] } ] }
 * </pre>
 * {@code spawn} indexes the match spawn points; {@code interval_ticks} and {@code delay_ticks} default to 20 and 0.
 */
public record WaveSet(List<Wave> waves) {
	public static final Codec<WaveSet> CODEC = RecordCodecBuilder.create(i -> i.group(
		Wave.CODEC.listOf().validate(WaveSet::nonEmpty).fieldOf("waves").forGetter(WaveSet::waves)
	).apply(i, WaveSet::new));

	private static <T> com.mojang.serialization.DataResult<List<T>> nonEmpty(List<T> list) {
		return list.isEmpty()
			? com.mojang.serialization.DataResult.error(() -> "List must not be empty")
			: com.mojang.serialization.DataResult.success(list);
	}

	public WaveSet {
		waves = List.copyOf(waves);
	}

	/** One mob spawn: the {@code index}th mob of its entry, due {@code tick} ticks after the wave starts. */
	public record Scheduled(int tick, SpawnEntry entry, int index) {}

	public record SpawnEntry(int spawn, EntityType<?> mob, int count, int intervalTicks, int delayTicks) {
		public static final Codec<SpawnEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("spawn").forGetter(SpawnEntry::spawn),
			BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("mob").forGetter(SpawnEntry::mob),
			ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(SpawnEntry::count),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("interval_ticks", 20).forGetter(SpawnEntry::intervalTicks),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("delay_ticks", 0).forGetter(SpawnEntry::delayTicks)
		).apply(i, SpawnEntry::new));

		public int lastSpawnTick() {
			return delayTicks + (count - 1) * intervalTicks;
		}
	}

	public record Wave(List<SpawnEntry> spawns) {
		public static final Codec<Wave> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpawnEntry.CODEC.listOf().validate(WaveSet::nonEmpty).fieldOf("spawns").forGetter(Wave::spawns)
		).apply(i, Wave::new));

		public Wave {
			spawns = List.copyOf(spawns);
		}

		/** Every spawn of the wave in time order. */
		public List<Scheduled> schedule() {
			List<Scheduled> all = new ArrayList<>();
			for (SpawnEntry entry : spawns) {
				for (int n = 0; n < entry.count(); n++) {
					all.add(new Scheduled(entry.delayTicks() + n * entry.intervalTicks(), entry, n));
				}
			}
			all.sort(Comparator.comparingInt(Scheduled::tick));
			return all;
		}

		public int lastSpawnTick() {
			return spawns.stream().mapToInt(SpawnEntry::lastSpawnTick).max().orElse(0);
		}
	}
}
