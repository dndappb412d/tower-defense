package dev.vadim.heartguard.ai;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.tower.TowerBlock;
import dev.vadim.heartguard.tower.TowerService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What wave mobs can wreck and how. Towers lose HP; only blocks in {@link #BREAKABLE_WALLS} can be broken, so the
 * map's own walls and terrain are safe and a player's cobblestone and planks are not.
 */
public final class StructureDamage {
	/** Blocks mobs may break: what the shop sells for walls. The map's stone brick walls are deliberately not in it. */
	public static final TagKey<Block> BREAKABLE_WALLS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "breakable_walls"));
	/** How far around itself a mob looks for something to attack, in blocks. */
	public static final int SEARCH_RADIUS = 8;

	private StructureDamage() {}

	public enum Kind {
		WALL,
		TOWER
	}

	public record Target(BlockPos pos, Kind kind) {}

	public static boolean isBreakableWall(BlockState state) {
		return state.is(BREAKABLE_WALLS);
	}

	/**
	 * The nearest tower, or breakable wall standing between the mob and the Heart, within {@link #SEARCH_RADIUS}.
	 * Towers count from any side; walls only when they lie toward the Heart, since a wall behind the mob is not in the way.
	 */
	public static @Nullable Target nearestTarget(Mob mob, Vec3 heart, boolean towersOnly) {
		ServerLevel level = (ServerLevel) mob.level();
		BlockPos here = mob.blockPosition();
		Vec3 toHeart = new Vec3(heart.x - mob.getX(), 0.0, heart.z - mob.getZ());
		Vec3 direction = toHeart.lengthSqr() < 1.0E-6 ? Vec3.ZERO : toHeart.normalize();
		Target best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(here.offset(-SEARCH_RADIUS, -1, -SEARCH_RADIUS), here.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			Kind kind;
			if (state.getBlock() instanceof TowerBlock) {
				kind = Kind.TOWER;
			} else if (!towersOnly && isBreakableWall(state) && pos.getY() >= here.getY() && inFront(mob, pos, direction)) {
				kind = Kind.WALL;
			} else {
				continue;
			}
			double distance = pos.getCenter().distanceToSqr(mob.position());
			if (distance < bestDistance) {
				bestDistance = distance;
				best = new Target(pos.immutable(), kind);
			}
		}
		return best;
	}

	private static boolean inFront(Mob mob, BlockPos pos, Vec3 direction) {
		Vec3 offset = new Vec3(pos.getX() + 0.5 - mob.getX(), 0.0, pos.getZ() + 0.5 - mob.getZ());
		return offset.lengthSqr() < 1.0 || offset.normalize().dot(direction) > 0.2;
	}

	/**
	 * A blast that hurts what it should and nothing else: towers in range lose {@code damage} HP, breakable walls
	 * within half the radius are destroyed, players nearby are hurt, and the terrain and everything else is left alone.
	 */
	public static void blast(ServerLevel level, Mob source, Vec3 centre, double radius, int damage) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y + 0.5, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.9F);
		BlockPos middle = BlockPos.containing(centre);
		int reach = (int) Math.ceil(radius);
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach), middle.offset(reach, reach, reach))) {
			double distance = pos.getCenter().distanceTo(centre);
			if (distance > radius) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof TowerBlock) {
				TowerService.damage(level, pos.immutable(), damage);
			} else if (distance <= radius * 0.6 && isBreakableWall(state)) {
				level.destroyBlock(pos, false, source);
			}
		}
		List<ServerPlayer> hit = level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(centre, centre).inflate(radius));
		for (ServerPlayer player : hit) {
			double falloff = 1.0 - Math.min(1.0, player.position().distanceTo(centre) / radius);
			player.hurtServer(level, level.damageSources().explosion(source, source), (float) (8.0 * falloff));
		}
	}

	/** The blast has gone off: the mob that caused it is spent. It pays nobody, as nothing killed it. */
	public static void spend(ServerLevel level, Mob mob) {
		mob.kill(level);
	}
}
