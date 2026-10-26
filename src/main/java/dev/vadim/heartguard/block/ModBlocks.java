package dev.vadim.heartguard.block;

import dev.vadim.heartguard.Heartguard;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	/**
	 * Marks where the Heart is. It is only a marker: the Heart HP lives in the match state, so this block is
	 * unbreakable survival-side and has no loot.
	 */
	public static final Block HEART = registerBlock(
		"heart",
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.strength(-1.0F, 3600000.0F)
			.lightLevel(state -> 10)
			.noLootTable()
	);

	private ModBlocks() {}

	/** Touches the class so the static registrations run. */
	public static void init() {}

	private static Block registerBlock(String name, BlockBehaviour.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(
			BuiltInRegistries.ITEM,
			itemKey,
			new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
		);
		return block;
	}
}
