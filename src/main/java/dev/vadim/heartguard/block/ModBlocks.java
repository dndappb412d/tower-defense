package dev.vadim.heartguard.block;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.tower.TowerBlock;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDefs;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

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

	/** During a wave the commander view only works while the player stands next to one of these. */
	public static final Block COMMAND_POST = registerBlock(
		"command_post",
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE)
			.strength(-1.0F, 3600000.0F)
			.lightLevel(state -> 8)
			.noLootTable()
	);

	public static final TowerBlock ARROW_TOWER = registerTower("arrow_tower", TowerDefs.ARROW, MapColor.COLOR_GREEN);
	public static final TowerBlock CANNON_TOWER = registerTower("cannon_tower", TowerDefs.CANNON, MapColor.COLOR_GRAY);
	public static final TowerBlock FROST_TOWER = registerTower("frost_tower", TowerDefs.FROST, MapColor.ICE);

	private static final Map<Identifier, TowerBlock> TOWER_BLOCKS = Map.of(
		TowerDefs.ARROW, ARROW_TOWER,
		TowerDefs.CANNON, CANNON_TOWER,
		TowerDefs.FROST, FROST_TOWER
	);

	public static final BlockEntityType<TowerBlockEntity> TOWER_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "tower"),
		FabricBlockEntityTypeBuilder.create(TowerBlockEntity::new, ARROW_TOWER, CANNON_TOWER, FROST_TOWER).build()
	);

	private ModBlocks() {}

	/** Touches the class so the static registrations run. */
	public static void init() {}

	/** The block that holds towers of the given type, or null for an unknown type. */
	public static @Nullable TowerBlock towerBlock(Identifier towerId) {
		return TOWER_BLOCKS.get(towerId);
	}

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

	/** Towers have no item: they only come into the world by being bought from commander view. */
	private static TowerBlock registerTower(String name, Identifier towerId, MapColor color) {
		Identifier id = Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(color)
			.strength(-1.0F, 3600000.0F)
			.noLootTable()
			.setId(blockKey);
		return Registry.register(BuiltInRegistries.BLOCK, blockKey, new TowerBlock(towerId, properties));
	}
}
