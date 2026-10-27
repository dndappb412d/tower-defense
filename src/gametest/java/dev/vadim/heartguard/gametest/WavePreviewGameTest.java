package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.wave.WavePreview;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSet.SpawnEntry;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** The next-wave preview: worked out from the wave file on the server, and shown only while the players can build. */
public class WavePreviewGameTest {
	private static final Identifier RUNNER = Identifier.fromNamespaceAndPath("heartguard", "runner");
	private static final Identifier FLYER = Identifier.fromNamespaceAndPath("heartguard", "flyer");
	private static final Identifier WARLORD = Identifier.fromNamespaceAndPath("heartguard", "warlord");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static SpawnEntry entry(Identifier mob, int count) {
		return new SpawnEntry(0, mob, count, 20, 0, 5);
	}

	private static WaveSet set() {
		return new WaveSet(List.of(
			new WaveSet.Wave(List.of(entry(RUNNER, 3), entry(FLYER, 2), entry(RUNNER, 2))),
			new WaveSet.Wave(List.of(entry(WARLORD, 1), entry(Identifier.withDefaultNamespace("husk"), 4)))));
	}

	private static MatchData match() {
		return MatchData.EMPTY.withHeart(new BlockPos(1, 64, 1)).withSpawn(new BlockPos(9, 64, 1)).started(2);
	}

	@GameTest
	public void kindsAreCountedAndFlagged(GameTestHelper context) {
		WavePreview first = WavePreview.of(match(), set());
		expect(context, first.wave() == 1 && first.total() == 2, "the first build phase previews wave 1 of 2, was " + first.wave() + "/" + first.total());
		expect(context, first.entries().size() == 2, "runners listed once, was " + first.entries());
		WavePreview.Entry runners = first.entries().get(0);
		expect(context, runners.count() == 5 && !runners.boss() && !runners.flies(), "five runners, " + runners);
		WavePreview.Entry flyers = first.entries().get(1);
		expect(context, flyers.count() == 2 && flyers.flies(), "two flyers that fly, " + flyers);

		WavePreview second = WavePreview.of(match().advanced().advanced().advanced(), set());
		expect(context, second.wave() == 2, "after wave 1 the pause previews wave 2, was " + second.wave());
		expect(context, second.entries().get(0).boss() && second.entries().get(0).name().equals("Warlord"), "the boss is flagged, " + second.entries());
		expect(context, second.entries().get(1).name().equals("Husk") && second.entries().get(1).count() == 4, "plain vanilla mobs use their own name, " + second.entries());
		context.succeed();
	}

	@GameTest
	public void onlyShownWhileBuilding(GameTestHelper context) {
		expect(context, WavePreview.of(MatchData.EMPTY, set()).isEmpty(), "nothing when no match runs");
		expect(context, WavePreview.of(match().advanced(), set()).isEmpty(), "nothing while a wave is on");
		expect(context, WavePreview.of(match(), null).isEmpty(), "nothing without a wave set");
		MatchData afterLast = match().advanced().advanced().advanced().advanced().advanced();
		expect(context, afterLast.phase() == MatchData.Phase.PAYOUT && WavePreview.of(afterLast, set()).isEmpty(), "nothing after the last wave");
		MatchData sandboxAfterLast = match().withSandbox(true).advanced().advanced().advanced().advanced().advanced();
		expect(context, WavePreview.of(sandboxAfterLast, set()).wave() == 1, "a sandbox goes round to wave 1 again");
		context.succeed();
	}

	@GameTest
	public void theShippedSurvivalSetCanBePreviewed(GameTestHelper context) {
		WaveSet shipped = dev.vadim.heartguard.wave.WaveSets.get(dev.vadim.heartguard.wave.WaveSets.DEFAULT_ID);
		expect(context, shipped != null, "the survival set is loaded");
		for (int n = 0; n < shipped.waves().size(); n++) {
			List<WavePreview.Entry> entries = WavePreview.describe(shipped.waves().get(n));
			expect(context, !entries.isEmpty() && entries.stream().allMatch(e -> e.count() > 0 && !e.name().isBlank()), "wave " + (n + 1) + " lists real mobs, " + entries);
		}
		context.succeed();
	}

	@GameTest
	public void travelsOverTheWire(GameTestHelper context) {
		WavePreview preview = WavePreview.of(match(), set());
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
		WavePreview.CODEC.encode(buffer, preview);
		expect(context, WavePreview.CODEC.decode(buffer).equals(preview), "encode and decode agree");
		context.succeed();
	}
}
