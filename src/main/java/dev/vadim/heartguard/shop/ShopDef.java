package dev.vadim.heartguard.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.data.DataRegistry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What a Quartermaster sells, parsed from {@code data/<namespace>/shop/<name>.json}:
 * <pre>
 * { "items": [ { "item": "minecraft:iron_pickaxe", "count": 1, "price": 90, "category": "tools" } ] }
 * </pre>
 * {@code count} defaults to 1 and {@code category} to "misc". Prices are in coins and always above zero.
 */
public record ShopDef(List<Entry> items) {
	public static final int MAX_ENTRIES = 40;

	public record Entry(Item item, int count, int price, String category) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			BuiltInRegistries.ITEM.byNameCodec().validate(item -> item == Items.AIR
				? DataResult.<Item>error(() -> "A shop cannot sell air")
				: DataResult.success(item)).fieldOf("item").forGetter(Entry::item),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Entry::count),
			ExtraCodecs.POSITIVE_INT.fieldOf("price").forGetter(Entry::price),
			Codec.STRING.optionalFieldOf("category", "misc").forGetter(Entry::category)
		).apply(i, Entry::new));

		public ItemStack stack() {
			return new ItemStack(item, count);
		}
	}

	public static final Codec<ShopDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Entry.CODEC.listOf().validate(list -> list.isEmpty() || list.size() > MAX_ENTRIES
			? DataResult.<List<Entry>>error(() -> "A shop sells 1 to " + MAX_ENTRIES + " items, found " + list.size())
			: DataResult.success(list)).fieldOf("items").forGetter(ShopDef::items)
	).apply(i, ShopDef::new));

	public static final Identifier DEFAULT_ID = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "quartermaster");

	private static final DataRegistry<ShopDef> REGISTRY = new DataRegistry<>("shop", CODEC);

	public ShopDef {
		items = List.copyOf(items);
	}

	public static @Nullable ShopDef get(Identifier id) {
		return REGISTRY.get(id);
	}

	public static Map<Identifier, ShopDef> all() {
		return REGISTRY.all();
	}

	public static void override(Identifier id, ShopDef def) {
		REGISTRY.override(id, def);
	}

	public static void clearOverride(Identifier id) {
		REGISTRY.clearOverride(id);
	}

	public static void register() {
		REGISTRY.register();
	}
}
