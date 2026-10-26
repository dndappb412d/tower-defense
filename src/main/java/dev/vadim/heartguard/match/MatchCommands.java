package dev.vadim.heartguard.match;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import dev.vadim.heartguard.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Dev and test controls for the match, standing in for the lobby and map metadata that come later. */
public final class MatchCommands {
	private MatchCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("heartguard")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("setheart").executes(c -> setHeart(c.getSource())))
				.then(Commands.literal("addspawn").executes(c -> addSpawn(c.getSource())))
				.then(Commands.literal("clearspawns").executes(c -> clearSpawns(c.getSource())))
				.then(Commands.literal("vein")
					.then(Commands.literal("poor").executes(c -> placeVein(c.getSource(), ModBlocks.POOR_VEIN)))
					.then(Commands.literal("rich").executes(c -> placeVein(c.getSource(), ModBlocks.RICH_VEIN))))
				.then(Commands.literal("start").executes(c -> start(c.getSource())))
				.then(Commands.literal("skip").executes(c -> skip(c.getSource())))
				.then(Commands.literal("stop").executes(c -> stop(c.getSource())))
				.then(Commands.literal("status").executes(c -> status(c.getSource())))
		);
	}

	private static BlockPos here(CommandSourceStack source) {
		return BlockPos.containing(source.getPosition());
	}

	private static int reply(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int setHeart(CommandSourceStack source) {
		BlockPos pos = here(source);
		ServerLevel level = source.getServer().overworld();
		// Move the marker block: clear the old Heart, if it is still there, then place the new one.
		Match.get(source.getServer()).heart().ifPresent(old -> {
			if (level.getBlockState(old).is(ModBlocks.HEART)) {
				level.setBlock(old, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
		level.setBlock(pos, ModBlocks.HEART.defaultBlockState(), Block.UPDATE_ALL);
		Match.update(source.getServer(), d -> d.withHeart(pos));
		return reply(source, "Heart set at " + pos.toShortString());
	}

	private static int addSpawn(CommandSourceStack source) {
		BlockPos pos = here(source);
		MatchData data = Match.update(source.getServer(), d -> d.withSpawn(pos));
		return reply(source, "Spawn point added at " + pos.toShortString() + " (" + data.spawns().size() + " total)");
	}

	private static int placeVein(CommandSourceStack source, Block vein) {
		BlockPos pos = here(source);
		source.getServer().overworld().setBlock(pos, vein.defaultBlockState(), Block.UPDATE_ALL);
		return reply(source, "Vein placed at " + pos.toShortString());
	}

	private static int clearSpawns(CommandSourceStack source) {
		Match.update(source.getServer(), MatchData::withoutSpawns);
		return reply(source, "Spawn points cleared");
	}

	private static int start(CommandSourceStack source) {
		MatchData.StartCheck check = Match.get(source.getServer()).checkStart();
		if (check != MatchData.StartCheck.OK) {
			source.sendFailure(Component.literal("Cannot start: " + check));
			return 0;
		}
		WaveSet set = WaveSets.active();
		if (set == null) {
			source.sendFailure(Component.literal("Cannot start: wave set " + WaveSets.activeId() + " is not loaded"));
			return 0;
		}
		Match.update(source.getServer(), d -> d.started(set.waves().size()));
		return 1;
	}

	private static int skip(CommandSourceStack source) {
		if (!Match.get(source.getServer()).running()) {
			source.sendFailure(Component.literal("No match is running"));
			return 0;
		}
		Match.update(source.getServer(), MatchData::advanced);
		return 1;
	}

	private static int stop(CommandSourceStack source) {
		Match.update(source.getServer(), MatchData::stopped);
		return 1;
	}

	private static int status(CommandSourceStack source) {
		MatchData d = Match.get(source.getServer());
		return reply(source, "phase=" + d.phase() + " wave=" + d.wave() + " heartHp=" + d.heartHp()
			+ " waves=" + d.wave() + "/" + d.totalWaves() + " ticksLeft=" + d.ticksLeft() + " heart=" + d.heart().map(BlockPos::toShortString).orElse("unset")
			+ " spawns=" + d.spawns().size());
	}
}
