package dev.vadim.heartguard.tower;

import dev.vadim.heartguard.block.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Per-tower state: who owns it, its tier, how it picks targets, and its HP. The numbers themselves live in {@link TowerDef}. */
public class TowerBlockEntity extends BlockEntity {
	public enum Targeting {
		/** Closest to the Heart, so the mob furthest along its way. */
		FIRST,
		/** Most current health. */
		STRONGEST,
		/** Closest to the tower. */
		CLOSEST
	}

	private @Nullable UUID owner;
	private int tier = 1;
	private Targeting targeting = Targeting.FIRST;
	private int hp;
	/** Ticks until the next shot. Not saved: a reloaded tower may fire at once, which costs nothing. */
	private int cooldown;

	public TowerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TOWER_ENTITY, pos, state);
	}

	/** Called once when the tower is placed. */
	public void initialize(UUID owner, TowerDef def) {
		this.owner = owner;
		this.tier = 1;
		this.targeting = Targeting.FIRST;
		this.hp = def.hp();
		sync();
	}

	public Optional<UUID> owner() {
		return Optional.ofNullable(owner);
	}

	public int tier() {
		return tier;
	}

	public void setTier(int tier) {
		this.tier = tier;
		sync();
	}

	public Targeting targeting() {
		return targeting;
	}

	public void setTargeting(Targeting targeting) {
		this.targeting = targeting;
		sync();
	}

	/** Saves the change and tells nearby clients, so the commander screen can show the real tier and mode. */
	private void sync() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	public int cooldown() {
		return cooldown;
	}

	public void setCooldown(int cooldown) {
		this.cooldown = cooldown;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TowerBlockEntity tower) {
		if (level instanceof ServerLevel serverLevel) {
			TowerCombat.tick(serverLevel, pos, tower);
		}
	}

	public int hp() {
		return hp;
	}

	public void setHp(int hp) {
		this.hp = hp;
		sync();
	}

	/** The definition this tower reads its numbers from, or null if its type is no longer loaded. */
	public @Nullable TowerDef def() {
		return getBlockState().getBlock() instanceof TowerBlock block ? TowerDefs.get(block.towerId()) : null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.putInt("tier", tier);
		output.putString("targeting", targeting.name());
		output.putInt("hp", hp);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		tier = input.getIntOr("tier", 1);
		String saved = input.getStringOr("targeting", Targeting.FIRST.name());
		try {
			targeting = Targeting.valueOf(saved);
		} catch (IllegalArgumentException e) {
			targeting = Targeting.FIRST;
		}
		hp = input.getIntOr("hp", 0);
	}
}
