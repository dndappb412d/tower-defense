package dev.vadim.heartguard.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * One tower type, parsed from {@code data/<namespace>/towers/<name>.json}:
 * <pre>
 * { "kind": "single_target", "cost": 50, "sell_refund_percent": 70, "hp": 40,
 *   "tiers": [ { "damage": 4.0, "range": 12.0, "fire_interval_ticks": 20 },
 *              { "upgrade_cost": 40, "damage": 6.0, "range": 14.0, "fire_interval_ticks": 16 } ] }
 * </pre>
 * Tier 1 is the first entry and costs {@code cost}; every later tier costs its own {@code upgrade_cost}. A tower has
 * one to {@link #MAX_TIERS} tiers. {@code kind} decides what the numbers mean: single target hits one mob for
 * {@code damage}; area hits every mob within {@code radius} of the target for {@code damage}; slow hits nothing but
 * slows every mob within {@code radius} by {@code slow_percent} for {@code slow_ticks}.
 */
public record TowerDef(Kind kind, int cost, int sellRefundPercent, int hp, List<Tier> tiers) {
	public static final int MAX_TIERS = 3;

	public enum Kind implements StringRepresentable {
		SINGLE_TARGET("single_target"),
		AREA("area"),
		SLOW("slow");

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		private final String serialized;

		Kind(String serialized) {
			this.serialized = serialized;
		}

		@Override
		public String getSerializedName() {
			return serialized;
		}
	}

	public record Tier(
		int upgradeCost,
		double damage,
		double range,
		int fireIntervalTicks,
		double radius,
		int slowPercent,
		int slowTicks
	) {
		public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("upgrade_cost", 0).forGetter(Tier::upgradeCost),
			Codec.doubleRange(0.0, 1000.0).optionalFieldOf("damage", 0.0).forGetter(Tier::damage),
			Codec.doubleRange(1.0, 64.0).fieldOf("range").forGetter(Tier::range),
			ExtraCodecs.POSITIVE_INT.fieldOf("fire_interval_ticks").forGetter(Tier::fireIntervalTicks),
			Codec.doubleRange(0.0, 32.0).optionalFieldOf("radius", 0.0).forGetter(Tier::radius),
			Codec.intRange(0, 100).optionalFieldOf("slow_percent", 0).forGetter(Tier::slowPercent),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("slow_ticks", 0).forGetter(Tier::slowTicks)
		).apply(i, Tier::new));
	}

	public static final Codec<TowerDef> CODEC = RecordCodecBuilder.<TowerDef>create(i -> i.group(
		Kind.CODEC.fieldOf("kind").forGetter(TowerDef::kind),
		ExtraCodecs.POSITIVE_INT.fieldOf("cost").forGetter(TowerDef::cost),
		Codec.intRange(0, 100).optionalFieldOf("sell_refund_percent", 70).forGetter(TowerDef::sellRefundPercent),
		ExtraCodecs.POSITIVE_INT.fieldOf("hp").forGetter(TowerDef::hp),
		Tier.CODEC.listOf().fieldOf("tiers").forGetter(TowerDef::tiers)
	).apply(i, TowerDef::new)).validate(TowerDef::check);

	public TowerDef {
		tiers = List.copyOf(tiers);
	}

	/** Rejects numbers that cannot make the tower do anything for its kind, so a bad file fails at load. */
	private static DataResult<TowerDef> check(TowerDef def) {
		if (def.tiers.isEmpty() || def.tiers.size() > MAX_TIERS) {
			return DataResult.error(() -> "A tower needs 1 to " + MAX_TIERS + " tiers, found " + def.tiers.size());
		}
		for (int t = 0; t < def.tiers.size(); t++) {
			Tier tier = def.tiers.get(t);
			String where = "Tier " + (t + 1) + ": ";
			switch (def.kind) {
				case SINGLE_TARGET -> {
					if (tier.damage <= 0.0) {
						return DataResult.error(() -> where + "a single target tower needs damage above 0");
					}
				}
				case AREA -> {
					if (tier.damage <= 0.0 || tier.radius <= 0.0) {
						return DataResult.error(() -> where + "an area tower needs damage and radius above 0");
					}
				}
				case SLOW -> {
					if (tier.radius <= 0.0 || tier.slowPercent <= 0 || tier.slowTicks <= 0) {
						return DataResult.error(() -> where + "a slow tower needs radius, slow_percent and slow_ticks above 0");
					}
				}
			}
		}
		return DataResult.success(def);
	}

	public int maxTier() {
		return tiers.size();
	}

	/** Coins spent to own a tower of the given tier (1-based): the base cost plus every upgrade up to it. */
	public int totalSpent(int tier) {
		int total = cost;
		for (int t = 1; t < Math.min(tier, tiers.size()); t++) {
			total += tiers.get(t).upgradeCost;
		}
		return total;
	}

	/** Coins returned when selling a tower of the given tier. */
	public int sellValue(int tier) {
		return totalSpent(tier) * sellRefundPercent / 100;
	}

	/** The numbers for a tier (1-based), clamped to the tiers that exist. */
	public Tier tier(int tier) {
		return tiers.get(Math.clamp(tier, 1, tiers.size()) - 1);
	}
}
