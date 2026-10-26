package dev.vadim.heartguard.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Structure templates, the form a map is stored in: one written by {@code tools/gen_structure.py} has to load from the
 * datapack and place exactly, and one saved from the world has to come back the same. These run in the GameTest world;
 * the dimension the maps live in is checked by {@code ArenaClientTest}, because the GameTest world has a fixed set of
 * dimensions.
 */
public class TemplateGameTest {
	private static final Identifier SPIKE_TEMPLATE = Identifier.fromNamespaceAndPath("heartguard", "spike");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static void forceLoad(ServerLevel level, BlockPos from, BlockPos to) {
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
	}

	@GameTest
	public void aDatapackTemplateLoadsAndPlacesExactly(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerLevel level = context.getLevel();
		BlockPos at = context.absolutePos(new BlockPos(0, 40, 0)).offset(300, 0, 300);
		forceLoad(level, at, at.offset(8, 0, 8));

		StructureTemplate template = server.getStructureManager().get(SPIKE_TEMPLATE).orElse(null);
		expect(context, template != null, "the template heartguard:spike should load from the datapack");
		expect(context, template.getSize().equals(new Vec3i(7, 3, 7)), "its size is 7x3x7, was " + template.getSize());

		boolean placed = template.placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
		expect(context, placed, "placeInWorld should report success");
		expect(context, level.getBlockState(at).is(Blocks.STONE) && level.getBlockState(at.offset(6, 0, 6)).is(Blocks.STONE), "the stone platform is there");
		expect(context, level.getBlockState(at.offset(3, 1, 3)).is(Blocks.GOLD_BLOCK), "and the gold block in the middle");
		expect(context, level.getBlockState(at.offset(0, 1, 0)).isAir(), "while the rest above the platform stays air");
		context.succeed();
	}

	@GameTest
	public void aTemplateSurvivesSavingAndLoading(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos build = context.absolutePos(new BlockPos(0, 40, 0)).offset(320, 0, 300);
		BlockPos copy = build.offset(40, 0, 0);
		forceLoad(level, build, copy.offset(8, 0, 8));
		level.setBlock(build, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(build.offset(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(build.offset(0, 1, 1), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);

		StructureTemplate original = new StructureTemplate();
		original.fillFromWorld(level, build, new Vec3i(2, 2, 2), false, List.of(Blocks.STRUCTURE_VOID));
		CompoundTag tag = original.save(new CompoundTag());

		StructureTemplate loaded = new StructureTemplate();
		loaded.load(level.holderLookup(Registries.BLOCK), tag);
		loaded.placeInWorld(level, copy, copy, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);

		expect(context, level.getBlockState(copy).is(Blocks.OAK_PLANKS), "planks copied");
		expect(context, level.getBlockState(copy.offset(1, 0, 0)).is(Blocks.GOLD_BLOCK), "gold copied");
		expect(context, level.getBlockState(copy.offset(0, 1, 1)).is(Blocks.COBBLESTONE), "cobblestone copied");
		expect(context, level.getBlockState(copy.offset(1, 1, 1)).isAir(), "and air stays air");
		context.succeed();
	}
}
