package dev.vadim.heartguard.vein;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gold veins: mining one pays coins straight to the miner, with no item to carry, then leaves a depleted block that
 * grows back into the same vein at the next payout phase. Rich veins sit where it is dangerous to mine, so each wave
 * asks the player what the gold is worth.
 */
public final class Veins {
	private Veins() {}

	public static void init() {
		VeinData.init();
		VeinDef.init();
		// Cancel the vanilla break for veins: the block turns depleted instead of vanishing, and nothing drops.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer miner && ModBlocks.veinDefId(state.getBlock()) != null) {
				mine(miner, serverLevel, pos, state);
				return false;
			}
			return true;
		});
	}

	/** Pays the miner, empties the vein and remembers it for regrowth. Returns the coins paid. */
	public static int mine(ServerPlayer miner, ServerLevel level, BlockPos pos, BlockState state) {
		Identifier defId = ModBlocks.veinDefId(state.getBlock());
		VeinDef def = defId == null ? null : VeinDef.get(defId);
		if (def == null) {
			return 0;
		}
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		level.setBlock(pos, ModBlocks.DEPLETED_VEIN.defaultBlockState(), Block.UPDATE_ALL);
		level.setAttached(VeinData.ATTACHMENT, level.getAttachedOrCreate(VeinData.ATTACHMENT).with(new VeinData.Entry(pos.immutable(), blockId)));

		Coins.add(miner, def.payout());
		miner.sendSystemMessage(Component.literal("+" + def.payout() + " coins"), true);
		level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
		return def.payout();
	}

	/**
	 * Regrows every depleted vein whose block is still the depleted block. Entries in chunks that are not loaded wait for
	 * a later call, and entries whose block was replaced are dropped. Returns how many veins grew back.
	 */
	public static int regenerate(MinecraftServer server) {
		int regrown = 0;
		for (ServerLevel level : server.getAllLevels()) {
			regrown += regenerate(level);
		}
		return regrown;
	}

	/** Regrows the depleted veins of one level. */
	public static int regenerate(ServerLevel level) {
		VeinData data = level.getAttachedOrCreate(VeinData.ATTACHMENT);
		List<VeinData.Entry> finished = new ArrayList<>();
		int regrown = 0;
		for (VeinData.Entry entry : data.depleted()) {
			if (!level.isLoaded(entry.pos())) {
				continue;
			}
			finished.add(entry);
			if (level.getBlockState(entry.pos()).is(ModBlocks.DEPLETED_VEIN)) {
				level.setBlock(entry.pos(), BuiltInRegistries.BLOCK.getValue(entry.block()).defaultBlockState(), Block.UPDATE_ALL);
				regrown++;
			}
		}
		if (!finished.isEmpty()) {
			level.setAttached(VeinData.ATTACHMENT, data.without(finished));
		}
		return regrown;
	}
}
