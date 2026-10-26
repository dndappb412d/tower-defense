package dev.vadim.heartguard.tower;

import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** How a tower picks one mob out of those in range. Pure numbers in, one choice out, so it is easy to test. */
public final class TowerTargeting {
	/** A mob in range, reduced to the numbers targeting cares about. {@code distanceToHeart} is infinite when there is no Heart. */
	public record Candidate<T>(T mob, double distanceToTower, double distanceToHeart, double health) {}

	private TowerTargeting() {}

	/**
	 * Picks the target for a mode, or null when nothing is in range. Ties fall back to the mob closest to the tower,
	 * so the choice never depends on iteration order.
	 */
	public static <T> @Nullable Candidate<T> choose(TowerBlockEntity.Targeting mode, List<Candidate<T>> candidates) {
		Comparator<Candidate<T>> byTower = Comparator.comparingDouble(Candidate::distanceToTower);
		Comparator<Candidate<T>> order = switch (mode) {
			case FIRST -> Comparator.<Candidate<T>>comparingDouble(Candidate::distanceToHeart).thenComparing(byTower);
			case STRONGEST -> Comparator.<Candidate<T>>comparingDouble(c -> -c.health()).thenComparing(byTower);
			case CLOSEST -> byTower;
		};
		return candidates.stream().min(order).orElse(null);
	}
}
