package dev.vadim.heartguard.match;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.vadim.heartguard.map.MapDef;
import dev.vadim.heartguard.map.MapLoader;
import java.util.List;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
				.then(Commands.literal("play")
					.then(Commands.argument("map", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(MapDef.all().keySet().stream().map(Identifier::getPath), b))
						.executes(c -> play(c.getSource(), StringArgumentType.getString(c, "map"), "survival"))
						.then(Commands.argument("mode", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("survival", "sandbox"), b))
							.executes(c -> play(c.getSource(), StringArgumentType.getString(c, "map"), StringArgumentType.getString(c, "mode"))))))
				.then(Commands.literal("leave").executes(c -> leave(c.getSource())))
				.then(Commands.literal("shop").executes(c -> placeShop(c.getSource())))
				.then(Commands.literal("vein")
					.then(Commands.literal("poor").executes(c -> placeVein(c.getSource(), ModBlocks.POOR_VEIN)))
					.then(Commands.literal("rich").executes(c -> placeVein(c.getSource(), ModBlocks.RICH_VEIN))))
				.then(Commands.literal("start").executes(c -> start(c.getSource())))
				.then(Commands.literal("wave")
					.then(Commands.argument("number", IntegerArgumentType.integer(1)).executes(c -> wave(c.getSource(), IntegerArgumentType.getInteger(c, "number")))))
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
		ServerLevel level = source.getLevel();
		// Move the marker block: clear the old Heart, if it is still there, then place the new one.
		Match.get(source.getServer()).heart().ifPresent(old -> {
			if (level.getBlockState(old).is(ModBlocks.HEART)) {
				level.setBlock(old, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
		level.setBlock(pos, ModBlocks.HEART.defaultBlockState(), Block.UPDATE_ALL);
		Match.update(source.getServer(), d -> d.withHeart(pos).withDimension(level.dimension().identifier()));
		return reply(source, "Heart set at " + pos.toShortString());
	}

	private static int addSpawn(CommandSourceStack source) {
		BlockPos pos = here(source);
		MatchData data = Match.update(source.getServer(), d -> d.withSpawn(pos));
		return reply(source, "Spawn point added at " + pos.toShortString() + " (" + data.spawns().size() + " total)");
	}

	/** Starts a match of a map in the arena for the player who ran the command. */
	private static int play(CommandSourceStack source, String mapName, String mode) {
		if (!mode.equals("survival") && !mode.equals("sandbox")) {
			source.sendFailure(Component.literal("Unknown mode " + mode + " (available: survival, sandbox)"));
			return 0;
		}
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can play a map"));
			return 0;
		}
		Identifier id = mapName.contains(":") ? Identifier.parse(mapName) : Identifier.fromNamespaceAndPath("heartguard", mapName);
		MapLoader.PlayResult result = MapLoader.play(source.getServer(), player, id, mode.equals("sandbox"));
		if (result != MapLoader.PlayResult.STARTED) {
			source.sendFailure(Component.literal("Cannot play " + id + ": " + result));
			return 0;
		}
		return reply(source, "Playing " + id + " (" + mode + ")");
	}

	private static int leave(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can leave"));
			return 0;
		}
		MapLoader.leave(source.getServer(), player);
		return reply(source, "Left the arena");
	}

	private static int placeShop(CommandSourceStack source) {
		BlockPos pos = here(source);
		source.getLevel().setBlock(pos, ModBlocks.QUARTERMASTER.defaultBlockState(), Block.UPDATE_ALL);
		return reply(source, "Quartermaster placed at " + pos.toShortString());
	}

	private static int placeVein(CommandSourceStack source, Block vein) {
		BlockPos pos = here(source);
		source.getLevel().setBlock(pos, vein.defaultBlockState(), Block.UPDATE_ALL);
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
		if (!set.unknownMobs().isEmpty()) {
			source.sendFailure(Component.literal("Cannot start: wave set " + WaveSets.activeId() + " names unknown mobs " + set.unknownMobs()));
			return 0;
		}
		Match.update(source.getServer(), d -> d.started(set.waves().size()));
		return 1;
	}

	/** Sandbox: start wave {@code number} now. */
	private static int wave(CommandSourceStack source, int number) {
		MatchData before = Match.get(source.getServer());
		MatchData after = Match.update(source.getServer(), d -> d.jumpedTo(number));
		if (after.phase() != MatchData.Phase.WAVE || after.wave() != number || before.phase() == MatchData.Phase.WAVE) {
			source.sendFailure(Component.literal("Cannot start wave " + number + ": needs a sandbox match between waves, with " + before.totalWaves() + " waves"));
			return 0;
		}
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
