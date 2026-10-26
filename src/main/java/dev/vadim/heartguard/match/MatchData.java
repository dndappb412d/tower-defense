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
 *
 * <p>{@code ticksLeft} counts down in the build and payout phases. {@code waveTick} counts up during a wave and
 * drives the spawn schedule, so a wave resumes correctly after a save and reload.
 */
public record MatchData(
	Optional<BlockPos> heart,
	List<BlockPos> spawns,
	int heartHp,
	Phase phase,
	int wave,
	int totalWaves,
	int ticksLeft,
	int waveTick
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
		WON,
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
		Codec.INT.optionalFieldOf("total_waves", 0).forGetter(MatchData::totalWaves),
		Codec.INT.fieldOf("ticks_left").forGetter(MatchData::ticksLeft),
		Codec.INT.optionalFieldOf("wave_tick", 0).forGetter(MatchData::waveTick)
	).apply(i, MatchData::new));

	public static final MatchData EMPTY = new MatchData(Optional.empty(), List.of(), MAX_HEART_HP, Phase.IDLE, 0, 0, 0, 0);

	public static final AttachmentType<MatchData> ATTACHMENT = AttachmentRegistry.<MatchData>builder()
		.persistent(CODEC)
		.initializer(() -> EMPTY)
		.buildAndRegister(Identifier.fromNamespaceAndPath(Heartguard.MOD_ID, "match"));

	public MatchData {
		spawns = List.copyOf(spawns);
	}

	/** Loads the class so the attachment type is registered before any world is read; otherwise saved data is skipped. */
	public static void init() {}

	public boolean running() {
		return phase == Phase.BUILD || phase == Phase.WAVE || phase == Phase.PAYOUT;
	}

	private MatchData with(int heartHp, Phase phase, int wave, int ticksLeft, int waveTick) {
		return new MatchData(heart, spawns, heartHp, phase, wave, totalWaves, ticksLeft, waveTick);
	}

	public MatchData withHeart(BlockPos pos) {
		return new MatchData(Optional.of(pos), spawns, heartHp, phase, wave, totalWaves, ticksLeft, waveTick);
	}

	public MatchData withSpawn(BlockPos pos) {
		List<BlockPos> next = new ArrayList<>(spawns);
		next.add(pos);
		return new MatchData(heart, next, heartHp, phase, wave, totalWaves, ticksLeft, waveTick);
	}

	public MatchData withoutSpawns() {
		return new MatchData(heart, List.of(), heartHp, phase, wave, totalWaves, ticksLeft, waveTick);
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

	/** A fresh match of the given length: full Heart, wave 0, first build phase. Callers check {@link #checkStart()} first. */
	public MatchData started(int totalWaves) {
		return new MatchData(heart, spawns, MAX_HEART_HP, Phase.BUILD, 0, totalWaves, BUILD_TICKS, 0);
	}

	/** Back to idle, keeping the Heart and spawn setup. */
	public MatchData stopped() {
		return new MatchData(heart, spawns, MAX_HEART_HP, Phase.IDLE, 0, 0, 0, 0);
	}

	/** The Heart takes damage while a match runs; at 0 HP the match is lost. Damage outside a running match is ignored. */
	public MatchData damaged(int amount) {
		if (!running() || amount <= 0) {
			return this;
		}
		int next = Math.max(0, heartHp - amount);
		return next == 0 ? with(0, Phase.LOST, wave, 0, 0) : with(next, phase, wave, ticksLeft, waveTick);
	}

	/** Heals the Heart up to its maximum while a match runs. */
	public MatchData repaired(int amount) {
		if (!running() || amount <= 0) {
			return this;
		}
		return with(Math.min(MAX_HEART_HP, heartHp + amount), phase, wave, ticksLeft, waveTick);
	}

	/**
	 * Moves to the next phase: build to wave, wave to payout, payout to the next build or, after the last wave,
	 * to a win. No effect when not running.
	 */
	public MatchData advanced() {
		return switch (phase) {
			case BUILD -> with(heartHp, Phase.WAVE, wave + 1, 0, 0);
			case WAVE -> with(heartHp, Phase.PAYOUT, wave, PAYOUT_TICKS, 0);
			case PAYOUT -> wave >= totalWaves
				? with(heartHp, Phase.WON, wave, 0, 0)
				: with(heartHp, Phase.BUILD, wave, BUILD_TICKS, 0);
			case IDLE, WON, LOST -> this;
		};
	}

	/** One server tick. Build and payout count down and then advance; a wave counts up and ends elsewhere. */
	public MatchData ticked() {
		return switch (phase) {
			case BUILD, PAYOUT -> ticksLeft <= 1 ? advanced() : with(heartHp, phase, wave, ticksLeft - 1, waveTick);
			case WAVE -> with(heartHp, phase, wave, ticksLeft, waveTick + 1);
			case IDLE, WON, LOST -> this;
		};
	}
}
