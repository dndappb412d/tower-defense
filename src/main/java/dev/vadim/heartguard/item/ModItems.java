package dev.vadim.heartguard.item;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item HEART_REPAIR_KIT = register("heart_repair_kit", new Item.Properties().stacksTo(16));

	private ModItems() {}

	/** Touches the class so the static registrations run. */
	public static void init() {}

	private static Item register(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key, new HeartRepairKitItem(properties.setId(key)));
	}
}
