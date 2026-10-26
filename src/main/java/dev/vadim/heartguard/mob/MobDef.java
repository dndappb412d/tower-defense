package dev.vadim.heartguard.mob;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.ai.HeartSeekGoal;
import dev.vadim.heartguard.data.DataRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * One kind of wave mob, parsed from {@code data/<namespace>/mobs/<name>.json}: a vanilla entity with its numbers
 * changed and a few behaviors switched on. Wave files name a mob by this id, or by a plain vanilla entity id.
 * <pre>
 * { "base": "minecraft:husk", "name": "Brute", "health": 60, "speed": 0.65, "armor": 8, "scale": 1.25,
 *   "knockback_resistance": 0.6, "heart_damage": 12, "traits": ["breaks_walls"],
 *   "equipment": [ { "slot": "head", "item": "minecraft:iron_helmet" } ] }
 * </pre>
 * {@code armor} is natural armor: worn {@code equipment} adds its own on top (the brute above has 4 plus 8 from its
 * iron). {@code speed} multiplies the base's walking speed (for a flier, its flying speed); a flier crosses the map
 * {@code cruise_height} blocks above the Heart (default 8), so it clears walls lower than that. Traits switch behavior on:
 * {@code flies} makes it ignore the maze and fly to the Heart, {@code boss} gives it a boss bar, and
 * {@code breaks_walls} and {@code explodes} are used by the pathing rules. A boss {@code ability} summons more mobs.
 */
public record MobDef(
	EntityType<?> base,
	String name,
	double health,
	double speed,
	double armor,
	double knockbackResistance,
	double scale,
	int heartDamage,
	double cruiseHeight,
	List<Trait> traits,
	List<Gear> equipment,
	Optional<Ability> ability
) {
	public enum Trait implements StringRepresentable {
		BREAKS_WALLS("breaks_walls"),
		EXPLODES("explodes"),
		FLIES("flies"),
		BOSS("boss");

		public static final Codec<Trait> CODEC = StringRepresentable.fromEnum(Trait::values);

		private final String serialized;

		Trait(String serialized) {
			this.serialized = serialized;
		}

		@Override
		public String getSerializedName() {
			return serialized;
		}
	}

	/** An item worn by the mob, for looks only: it never drops. */
	public record Gear(EquipmentSlot slot, Item item) {
		public static final Codec<Gear> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(EquipmentSlot::byName, EquipmentSlot::getName).fieldOf("slot").forGetter(Gear::slot),
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Gear::item)
		).apply(i, Gear::new));
	}

	/** Every {@code intervalTicks}, summon {@code count} of {@code summon}, never more than {@code maxAlive} at once. */
	public record Ability(Identifier summon, int count, int intervalTicks, int maxAlive) {
		public static final Codec<Ability> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("summon").forGetter(Ability::summon),
			ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Ability::count),
			Codec.intRange(20, 20 * 600).fieldOf("interval_ticks").forGetter(Ability::intervalTicks),
			ExtraCodecs.POSITIVE_INT.fieldOf("max_alive").forGetter(Ability::maxAlive)
		).apply(i, Ability::new));
	}

	public static final Codec<MobDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("base").forGetter(MobDef::base),
		Codec.STRING.optionalFieldOf("name", "Mob").forGetter(MobDef::name),
		Codec.doubleRange(1.0, 5000.0).fieldOf("health").forGetter(MobDef::health),
		Codec.doubleRange(0.1, 4.0).optionalFieldOf("speed", 1.0).forGetter(MobDef::speed),
		Codec.doubleRange(0.0, 30.0).optionalFieldOf("armor", 0.0).forGetter(MobDef::armor),
		Codec.doubleRange(0.0, 1.0).optionalFieldOf("knockback_resistance", 0.0).forGetter(MobDef::knockbackResistance),
		Codec.doubleRange(0.25, 4.0).optionalFieldOf("scale", 1.0).forGetter(MobDef::scale),
		ExtraCodecs.POSITIVE_INT.optionalFieldOf("heart_damage", HeartSeekGoal.HIT_DAMAGE).forGetter(MobDef::heartDamage),
		Codec.doubleRange(1.0, 64.0).optionalFieldOf("cruise_height", HeartSeekGoal.CRUISE_HEIGHT).forGetter(MobDef::cruiseHeight),
		Trait.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(MobDef::traits),
		Gear.CODEC.listOf().optionalFieldOf("equipment", List.of()).forGetter(MobDef::equipment),
		Ability.CODEC.optionalFieldOf("ability").forGetter(MobDef::ability)
	).apply(i, MobDef::new));

	private static final DataRegistry<MobDef> REGISTRY = new DataRegistry<>("mobs", CODEC);

	public MobDef {
		traits = List.copyOf(traits);
		equipment = List.copyOf(equipment);
	}

	public boolean has(Trait trait) {
		return traits.contains(trait);
	}

	public static @Nullable MobDef get(Identifier id) {
		return REGISTRY.get(id);
	}

	public static Map<Identifier, MobDef> all() {
		return REGISTRY.all();
	}

	public static void override(Identifier id, MobDef def) {
		REGISTRY.override(id, def);
	}

	public static void clearOverride(Identifier id) {
		REGISTRY.clearOverride(id);
	}

	public static void register() {
		REGISTRY.register();
	}

	/** Whether {@code id} names something that can spawn: a mob definition or a registered vanilla entity. */
	public static boolean isKnown(Identifier id) {
		return get(id) != null || BuiltInRegistries.ENTITY_TYPE.containsKey(id);
	}
}
