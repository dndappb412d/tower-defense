package dev.vadim.heartguard.wave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import dev.vadim.heartguard.mob.MobDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;

/**
 * One loadable set of waves, parsed from {@code data/<namespace>/waves/<name>.json}:
 * <pre>
 * { "waves": [ { "clear_bonus": 50,
 *                  "spawns": [ { "spawn": 0, "mob": "minecraft:husk", "count": 5,
 *                                "interval_ticks": 40, "delay_ticks": 0, "reward": 5 } ] } ] }
 * </pre>
 * {@code spawn} indexes the match spawn points; {@code interval_ticks}, {@code delay_ticks}, {@code reward} (coins per
 * kill) and {@code clear_bonus} default to 20, 0, 5 and 50.
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

	/** Mob ids in this set that name neither a mob definition nor a vanilla entity, so a match can refuse to start on them. */
	public List<Identifier> unknownMobs() {
		return waves.stream()
			.flatMap(wave -> wave.spawns().stream())
			.map(SpawnEntry::mob)
			.filter(id -> !MobDef.isKnown(id))
			.distinct()
			.toList();
	}

	/** One mob spawn: the {@code index}th mob of its entry, due {@code tick} ticks after the wave starts. */
	public record Scheduled(int tick, SpawnEntry entry, int index) {}

	public record SpawnEntry(int spawn, Identifier mob, int count, int intervalTicks, int delayTicks, int reward) {
		/** Coins per kill when the file does not say. */
		public static final int DEFAULT_REWARD = 5;

		public static final Codec<SpawnEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("spawn").forGetter(SpawnEntry::spawn),
			Identifier.CODEC.validate(SpawnEntry::checkMobId).fieldOf("mob").forGetter(SpawnEntry::mob),
			ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(SpawnEntry::count),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("interval_ticks", 20).forGetter(SpawnEntry::intervalTicks),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("delay_ticks", 0).forGetter(SpawnEntry::delayTicks),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("reward", DEFAULT_REWARD).forGetter(SpawnEntry::reward)
		).apply(i, SpawnEntry::new));

		public SpawnEntry(int spawn, EntityType<?> mob, int count, int intervalTicks, int delayTicks) {
			this(spawn, mob, count, intervalTicks, delayTicks, DEFAULT_REWARD);
		}

		public SpawnEntry(int spawn, EntityType<?> mob, int count, int intervalTicks, int delayTicks, int reward) {
			this(spawn, BuiltInRegistries.ENTITY_TYPE.getKey(mob), count, intervalTicks, delayTicks, reward);
		}

		/**
		 * Vanilla entity ids are checked when the file is read. Other ids name a mob definition, which may load after
		 * the wave file, so those are checked by {@link WaveSet#unknownMobs()} when a match starts.
		 */
		private static DataResult<Identifier> checkMobId(Identifier id) {
			if (id.getNamespace().equals("minecraft") && !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
				return DataResult.error(() -> "Unknown entity type " + id);
			}
			return DataResult.success(id);
		}

		public int lastSpawnTick() {
			return delayTicks + (count - 1) * intervalTicks;
		}
	}

	public record Wave(List<SpawnEntry> spawns, int clearBonus) {
		/** Coins for clearing the wave with an untouched Heart, when the file does not say. */
		public static final int DEFAULT_CLEAR_BONUS = 50;

		public static final Codec<Wave> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpawnEntry.CODEC.listOf().validate(WaveSet::nonEmpty).fieldOf("spawns").forGetter(Wave::spawns),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("clear_bonus", DEFAULT_CLEAR_BONUS).forGetter(Wave::clearBonus)
		).apply(i, Wave::new));

		public Wave(List<SpawnEntry> spawns) {
			this(spawns, DEFAULT_CLEAR_BONUS);
		}

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
