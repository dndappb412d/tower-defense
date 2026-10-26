package dev.vadim.heartguard.gametest;

import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.vein.VeinDef;
import dev.vadim.heartguard.shop.ShopDef;
import dev.vadim.heartguard.wave.WaveSet;
import dev.vadim.heartguard.wave.WaveSets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Rules the numbers in the data files have to obey for the economy to make sense: every purchase has to be reachable,
 * every upgrade has to be worth buying, and nothing may print money. These do not say the game is fun; they catch the
 * data edits that quietly break it. How hard the waves feel needs a person playing, which the full-loop client test can
 * only report.
 */
public class BalanceGameTest {
	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static int shopPrice(Item item) {
		return ShopDef.get(ShopDef.DEFAULT_ID).items().stream().filter(e -> e.item() == item).findFirst().orElseThrow().price();
	}

	/** Coins a wave pays if everything in it is killed by the player's towers and the Heart is untouched. */
	private static int wavePayout(WaveSet.Wave wave) {
		int kills = wave.spawns().stream().mapToInt(e -> e.count() * e.reward()).sum();
		return kills + wave.clearBonus();
	}

	private static int cheapestTower() {
		return TowerDefs.all().values().stream().mapToInt(TowerDef::cost).min().orElseThrow();
	}

	@GameTest
	public void oneRichVeinBuysTheFirstPickaxe(GameTestHelper context) {
		int rich = VeinDef.get(VeinDef.RICH).payout();
		expect(context, rich >= shopPrice(Items.STONE_PICKAXE), "a rich vein pays " + rich + ", the stone pickaxe costs " + shopPrice(Items.STONE_PICKAXE));
		expect(context, VeinDef.get(VeinDef.POOR).payout() < VeinDef.get(VeinDef.RICH).payout(), "rich veins pay more than poor ones");
		context.succeed();
	}

	@GameTest
	public void pickaxesGetDearerAsTheyGetBetter(GameTestHelper context) {
		int stone = shopPrice(Items.STONE_PICKAXE);
		int iron = shopPrice(Items.IRON_PICKAXE);
		int diamond = shopPrice(Items.DIAMOND_PICKAXE);
		expect(context, stone < iron && iron < diamond, "pickaxe prices should rise with the tier: " + stone + ", " + iron + ", " + diamond);
		context.succeed();
	}

	@GameTest
	public void everyWavePaysForAnotherTower(GameTestHelper context) {
		WaveSet waves = WaveSets.get(WaveSets.DEFAULT_ID);
		int cheapest = cheapestTower();
		int previous = 0;
		for (int i = 0; i < waves.waves().size(); i++) {
			int payout = wavePayout(waves.waves().get(i));
			expect(context, payout >= cheapest, "wave " + (i + 1) + " pays " + payout + ", less than the cheapest tower (" + cheapest + ")");
			expect(context, payout >= previous, "wave " + (i + 1) + " pays " + payout + ", less than the wave before (" + previous + ")");
			previous = payout;
		}
		context.succeed();
	}

	@GameTest
	public void wavesNeverGetSmaller(GameTestHelper context) {
		WaveSet waves = WaveSets.get(WaveSets.DEFAULT_ID);
		int previous = 0;
		for (int i = 0; i < waves.waves().size(); i++) {
			int mobs = waves.waves().get(i).spawns().stream().mapToInt(WaveSet.SpawnEntry::count).sum();
			expect(context, mobs >= previous, "wave " + (i + 1) + " has " + mobs + " mobs, fewer than the wave before (" + previous + ")");
			previous = mobs;
		}
		context.succeed();
	}

	@GameTest
	public void upgradesAreDealsAndSalesLoseMoney(GameTestHelper context) {
		for (var entry : TowerDefs.all().entrySet()) {
			TowerDef def = entry.getValue();
			String name = entry.getKey().getPath();
			expect(context, def.sellRefundPercent() < 100, name + " must not refund its full price, or buying and selling is free money");
			expect(context, def.sellValue(def.maxTier()) < def.totalSpent(def.maxTier()), name + " loses money when sold at the top tier");
			for (int tier = 2; tier <= def.maxTier(); tier++) {
				expect(context, def.tier(tier).upgradeCost() > 0, name + " tier " + tier + " must cost something");
				expect(context, def.tier(tier).upgradeCost() < def.cost() * 2,
					name + " tier " + tier + " upgrade (" + def.tier(tier).upgradeCost() + ") should cost less than two new towers (" + def.cost() * 2 + ")");
			}
		}
		context.succeed();
	}

	@GameTest
	public void everyUpgradeMakesATowerBetter(GameTestHelper context) {
		for (var entry : TowerDefs.all().entrySet()) {
			TowerDef def = entry.getValue();
			String name = entry.getKey().getPath();
			for (int tier = 2; tier <= def.maxTier(); tier++) {
				TowerDef.Tier lower = def.tier(tier - 1);
				TowerDef.Tier higher = def.tier(tier);
				double lowerPower = def.kind() == TowerDef.Kind.SLOW ? lower.slowPercent() / (double) lower.fireIntervalTicks() : lower.damage() / lower.fireIntervalTicks();
				double higherPower = def.kind() == TowerDef.Kind.SLOW ? higher.slowPercent() / (double) higher.fireIntervalTicks() : higher.damage() / higher.fireIntervalTicks();
				expect(context, higherPower > lowerPower, name + " tier " + tier + " should be stronger than tier " + (tier - 1) + ": " + higherPower + " vs " + lowerPower);
				expect(context, higher.range() >= lower.range(), name + " tier " + tier + " should not lose range");
			}
		}
		context.succeed();
	}

	@GameTest
	public void dearerTowersDoSomethingTheCheapOnesCannot(GameTestHelper context) {
		TowerDef arrow = TowerDefs.get(TowerDefs.ARROW);
		TowerDef cannon = TowerDefs.get(TowerDefs.CANNON);
		expect(context, cannon.cost() > arrow.cost(), "the cannon costs more than the arrow tower");
		expect(context, cannon.tier(1).radius() > 0.0 && arrow.tier(1).radius() == 0.0, "what the cannon adds over an arrow tower is area damage");
		context.succeed();
	}
}
