package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.match.EndActionPayload;
import dev.vadim.heartguard.match.EndScreenPayload;
import dev.vadim.heartguard.match.EndScreens;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The end of a match: what the screen is told, and that the buttons only work once the match is really over. */
public class EndScreensGameTest {
	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static MatchData running() {
		return MatchData.EMPTY.withHeart(new BlockPos(1, 64, 1)).withSpawn(new BlockPos(9, 64, 1)).started(3);
	}

	@GameTest
	public void summaryTellsWinFromLoss(GameTestHelper context) {
		MatchData lost = running().advanced().damaged(1000);
		EndScreenPayload loss = EndScreens.summary(lost);
		expect(context, !loss.won() && loss.wave() == 1 && loss.total() == 3 && loss.heartHp() == 0 && !loss.canReplay(), "a loss in wave 1, " + loss);
		MatchData won = running();
		for (int i = 0; i < 9; i++) {
			won = won.advanced();
		}
		EndScreenPayload win = EndScreens.summary(won.withMap(Identifier.fromNamespaceAndPath("heartguard", "meadow")));
		expect(context, win.won() && win.wave() == 3 && win.canReplay(), "a win with a known map, " + win);
		context.succeed();
	}

	@GameTest
	public void mapIsRememberedAndSaved(GameTestHelper context) {
		Identifier meadow = Identifier.fromNamespaceAndPath("heartguard", "meadow");
		MatchData data = running().withMap(meadow);
		MatchData loaded = MatchData.CODEC.parse(NbtOps.INSTANCE, MatchData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
		expect(context, loaded.equals(data) && loaded.map().equals(Optional.of(meadow)), "the map is saved with the match");
		expect(context, data.advanced().map().isPresent() && data.stopped().map().isPresent(), "and survives phase changes and a stop");
		context.succeed();
	}

	@GameTest
	public void buttonsDoNothingUntilTheMatchIsOver(GameTestHelper context) {
		MinecraftServer server = context.getLevel().getServer();
		ServerPlayer player = context.makeMockServerPlayerInLevel();
		MatchData saved = Match.get(server);
		try {
			Match.update(server, d -> running());
			for (EndActionPayload.Action action : EndActionPayload.Action.values()) {
				expect(context, EndScreens.act(server, player, action) != null, action + " is refused while the match runs");
			}
			expect(context, Match.get(server).phase() == Phase.BUILD, "and the match is untouched");
			Match.update(server, d -> running().damaged(1000));
			expect(context, Match.get(server).phase() == Phase.LOST, "sanity: the match is lost");
			expect(context, EndScreens.act(server, player, EndActionPayload.Action.REPLAY) != null, "replay is refused when no map is known");
			expect(context, Match.get(server).phase() == Phase.LOST, "and nothing changed");
		} finally {
			Match.update(server, d -> saved);
		}
		context.succeed();
	}

	@GameTest
	public void payloadsTravelOverTheWire(GameTestHelper context) {
		var access = context.getLevel().registryAccess();
		EndScreenPayload result = new EndScreenPayload(true, 3, 3, 80, true);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
		EndScreenPayload.CODEC.encode(buffer, result);
		expect(context, EndScreenPayload.CODEC.decode(buffer).equals(result), "the result survives the wire");
		for (EndActionPayload.Action action : EndActionPayload.Action.values()) {
			RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
			EndActionPayload.CODEC.encode(out, new EndActionPayload(action));
			expect(context, EndActionPayload.CODEC.decode(out).action() == action, action + " survives the wire");
		}
		context.succeed();
	}
}
