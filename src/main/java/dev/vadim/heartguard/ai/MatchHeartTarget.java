package dev.vadim.heartguard.ai;

import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** The real target: the Heart of the running match. Mobs march only while a wave is on. */
public record MatchHeartTarget(MinecraftServer server) implements HeartSeekGoal.Target {
	@Override
	public boolean active() {
		return Match.get(server).phase() == MatchData.Phase.WAVE;
	}

	@Override
	public Optional<BlockPos> heart() {
		return Match.get(server).heart();
	}

	@Override
	public void damage(int amount) {
		Match.damageHeart(server, amount);
	}
}
