package dev.vadim.heartguard.match;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vadim.heartguard.Heartguard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Everything about one match, as an immutable value. All rules are pure functions on this record so they can be
 * tested without a world; {@link Match} stores it as a persistent attachment on the overworld.
 */
public record MatchData(
	Optional<BlockPos> heart,
	List<BlockPos> spawns,
	int heartHp,
	Phase phase,
	int wave,
	int ticksLeft
) {
	/** Build-phase length before a wave starts on its own. Fixed until it moves into data files. */
	public static final int BUILD_TICKS = 20 * 60;
	/** Pause between a cleared wave and the next build phase. */
	public static final int PAYOUT_TICKS = 20 * 5;
	public static final int MAX_HEART_HP = 100;

	public enum Phase {
		IDLE,
		BUILD,
		WAVE,
		PAYOUT,
		LOST
	}

	public enum StartCheck {
		OK,
		NO_HEART,
		NO_SPAWNS,
		ALREADY_RUNNING
	}

	private static final Codec<Phase> PHASE_CODEC = Codec.STRING.xmap(Phase::valueOf, Phase::name);

	public static final Codec<MatchData> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.optionalFieldOf("heart").forGetter(MatchData::heart),
		BlockPos.CODEC.listOf().fieldOf("spawns").forGetter(MatchData::spawns),
		Codec.INT.fieldOf("heart_hp").forGetter(MatchData::heartHp),
		PHASE_CODEC.fieldOf("phase").forGetter(MatchData::phase),
		Codec.INT.fieldOf("wave").forGetter(MatchData::wave),
		Codec.INT.fieldOf("ticks_left").forGetter(MatchData::ticksLeft)
	).apply(i, MatchData::new));

	public static final MatchData EMPTY = new MatchData(Optional.empty(), List.of(), MAX_HEART_HP, Phase.IDLE, 0, 0);

	public static final AttachmentType<MatchData> ATTACHMENT = AttachmentRegistry.<MatchData>builder()
		.persistent(CODEC)
		.initializer(() -> EMPTY)
		.buildAndRegister(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "match"));

	public MatchData {
		spawns = List.copyOf(spawns);
	}

	public boolean running() {
		return phase == Phase.BUILD || phase == Phase.WAVE || phase == Phase.PAYOUT;
	}

	public MatchData withHeart(BlockPos pos) {
		return new MatchData(Optional.of(pos), spawns, heartHp, phase, wave, ticksLeft);
	}

	public MatchData withSpawn(BlockPos pos) {
		List<BlockPos> next = new ArrayList<>(spawns);
		next.add(pos);
		return new MatchData(heart, next, heartHp, phase, wave, ticksLeft);
	}

	public MatchData withoutSpawns() {
		return new MatchData(heart, List.of(), heartHp, phase, wave, ticksLeft);
	}

	public StartCheck checkStart() {
		if (running()) {
			return StartCheck.ALREADY_RUNNING;
		}
		if (heart.isEmpty()) {
			return StartCheck.NO_HEART;
		}
		if (spawns.isEmpty()) {
			return StartCheck.NO_SPAWNS;
		}
		return StartCheck.OK;
	}

	/** A fresh match: full Heart, wave 0, in the first build phase. Callers check {@link #checkStart()} first. */
	public MatchData started() {
		return new MatchData(heart, spawns, MAX_HEART_HP, Phase.BUILD, 0, BUILD_TICKS);
	}

	/** Back to idle, keeping the Heart and spawn setup. */
	public MatchData stopped() {
		return new MatchData(heart, spawns, MAX_HEART_HP, Phase.IDLE, 0, 0);
	}

	/** Moves to the next phase: build to wave, wave to payout, payout to the next build. No effect when not running. */
	public MatchData advanced() {
		return switch (phase) {
			case BUILD -> new MatchData(heart, spawns, heartHp, Phase.WAVE, wave + 1, 0);
			case WAVE -> new MatchData(heart, spawns, heartHp, Phase.PAYOUT, wave, PAYOUT_TICKS);
			case PAYOUT -> new MatchData(heart, spawns, heartHp, Phase.BUILD, wave, BUILD_TICKS);
			case IDLE, LOST -> this;
		};
	}

	/** One server tick. Build and payout count down and then advance on their own; waves end elsewhere. */
	public MatchData ticked() {
		return switch (phase) {
			case BUILD, PAYOUT -> ticksLeft <= 1
				? advanced()
				: new MatchData(heart, spawns, heartHp, phase, wave, ticksLeft - 1);
			case WAVE, IDLE, LOST -> this;
		};
	}
}
