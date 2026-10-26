package dev.vadim.heartguard.tower;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A placed tower. One block per tower type, all sharing this class; the type id says which {@link TowerDef} the
 * block entity reads its numbers from. Towers are unbreakable by hand: they leave the world by being sold or destroyed,
 * never by mining, so nobody can pick one up for free.
 */
public class TowerBlock extends BaseEntityBlock {
	public static final MapCodec<TowerBlock> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(Identifier.CODEC.fieldOf("tower").forGetter(TowerBlock::towerId), propertiesCodec()).apply(i, TowerBlock::new)
	);

	private final Identifier towerId;

	public TowerBlock(Identifier towerId, BlockBehaviour.Properties properties) {
		super(properties);
		this.towerId = towerId;
	}

	public Identifier towerId() {
		return towerId;
	}

	@Override
	protected MapCodec<TowerBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TowerBlockEntity(pos, state);
	}

	/** The block that holds towers of the given type, or null for an unknown type. */
	public static TowerBlock forTower(Identifier towerId) {
		return ModBlocks.towerBlock(towerId);
	}
}
