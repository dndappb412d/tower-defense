package dev.vadim.heartguard.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.vadim.heartguard.tower.TowerDef;
import dev.vadim.heartguard.tower.TowerDefs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/** Tower files: parsing, validation, the cost and refund arithmetic, and the shipped towers. */
public class TowerDefGameTest {
	private static final Identifier TEST_ID = Identifier.fromNamespaceAndPath("heartguard-test", "tester");

	private static void expect(GameTestHelper context, boolean condition, String message) {
		context.assertTrue(condition, message);
	}

	private static DataResult<TowerDef> parse(String json) {
		return TowerDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	private static TowerDef valid() {
		return parse("""
			{ "kind": "single_target", "cost": 50, "sell_refund_percent": 70, "hp": 40,
			  "tiers": [ { "damage": 4.0, "range": 12.0, "fire_interval_ticks": 20 },
			             { "upgrade_cost": 40, "damage": 6.0, "range": 14.0, "fire_interval_ticks": 16 },
			             { "upgrade_cost": 80, "damage": 9.0, "range": 16.0, "fire_interval_ticks": 12 } ] }
			""").result().orElseThrow();
	}

	@GameTest
	public void parsesAValidTower(GameTestHelper context) {
		TowerDef def = valid();
		expect(context, def.kind() == TowerDef.Kind.SINGLE_TARGET && def.cost() == 50 && def.hp() == 40, "base fields: " + def);
		expect(context, def.maxTier() == 3, "three tiers expected, was " + def.maxTier());
		expect(context, def.tier(2).damage() == 6.0 && def.tier(2).range() == 14.0 && def.tier(2).fireIntervalTicks() == 16, "tier 2 numbers: " + def.tier(2));
		expect(context, def.tier(1).radius() == 0.0 && def.tier(1).slowPercent() == 0, "optional numbers default to zero");
		context.succeed();
	}

	@GameTest
	public void costsAndRefundsAddUpAcrossTiers(GameTestHelper context) {
		TowerDef def = valid();
		expect(context, def.totalSpent(1) == 50, "tier 1 costs only the base, was " + def.totalSpent(1));
		expect(context, def.totalSpent(2) == 90, "tier 2 adds its upgrade, was " + def.totalSpent(2));
		expect(context, def.totalSpent(3) == 170, "tier 3 adds both upgrades, was " + def.totalSpent(3));
		expect(context, def.sellValue(1) == 35 && def.sellValue(3) == 119, "70% of what was spent, was " + def.sellValue(1) + "/" + def.sellValue(3));
		expect(context, def.totalSpent(9) == 170 && def.tier(9) == def.tier(3) && def.tier(0) == def.tier(1), "tiers outside the range are clamped");
		context.succeed();
	}

	@GameTest
	public void rejectsTowersThatCannotWork(GameTestHelper context) {
		String ok = "\"cost\": 50, \"hp\": 40, ";
		List<String> bad = List.of(
			"{ \"kind\": \"laser\", " + ok + "\"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"kind\": \"single_target\", " + ok + "\"tiers\": [] }",
			"{ \"kind\": \"single_target\", " + ok + "\"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 },"
				+ " { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 }, { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 },"
				+ " { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"kind\": \"single_target\", " + ok + "\"tiers\": [ { \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"kind\": \"area\", " + ok + "\"tiers\": [ { \"damage\": 5, \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"kind\": \"slow\", " + ok + "\"tiers\": [ { \"range\": 5, \"fire_interval_ticks\": 10, \"radius\": 3, \"slow_percent\": 0, \"slow_ticks\": 20 } ] }",
			"{ \"kind\": \"slow\", " + ok + "\"tiers\": [ { \"range\": 5, \"fire_interval_ticks\": 10, \"radius\": 3, \"slow_percent\": 101, \"slow_ticks\": 20 } ] }",
			"{ \"kind\": \"slow\", " + ok + "\"tiers\": [ { \"range\": 5, \"fire_interval_ticks\": 10, \"radius\": 3, \"slow_percent\": 30 } ] }",
			"{ \"kind\": \"single_target\", \"cost\": 0, \"hp\": 40, \"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"kind\": \"single_target\", \"cost\": 50, \"hp\": 0, \"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"kind\": \"single_target\", " + ok + "\"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 0 } ] }",
			"{ \"kind\": \"single_target\", " + ok + "\"sell_refund_percent\": 150, \"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }",
			"{ \"cost\": 50, \"hp\": 40, \"tiers\": [ { \"damage\": 1, \"range\": 5, \"fire_interval_ticks\": 10 } ] }"
		);
		for (String json : bad) {
			expect(context, parse(json).result().isEmpty(), "this tower should be rejected: " + json);
		}
		context.succeed();
	}

	@GameTest
	public void acceptsAreaAndSlowTowers(GameTestHelper context) {
		String area = "{ \"kind\": \"area\", \"cost\": 100, \"hp\": 60, \"tiers\": [ { \"damage\": 6, \"range\": 12, \"fire_interval_ticks\": 50, \"radius\": 2.5 } ] }";
		String slow = "{ \"kind\": \"slow\", \"cost\": 75, \"hp\": 40, \"tiers\": [ { \"range\": 10, \"fire_interval_ticks\": 30, \"radius\": 4,"
			+ " \"slow_percent\": 30, \"slow_ticks\": 40 } ] }";
		expect(context, parse(area).result().isPresent(), "a valid area tower should load: " + parse(area));
		expect(context, parse(slow).result().isPresent(), "a valid slow tower should load: " + parse(slow));
		context.succeed();
	}

	@GameTest
	public void theThreeShippedTowersLoadFromTheDatapack(GameTestHelper context) {
		TowerDef arrow = TowerDefs.get(TowerDefs.ARROW);
		TowerDef cannon = TowerDefs.get(TowerDefs.CANNON);
		TowerDef frost = TowerDefs.get(TowerDefs.FROST);
		expect(context, arrow != null && cannon != null && frost != null, "arrow, cannon and frost should all be loaded, have " + TowerDefs.all().keySet());
		expect(context, arrow.kind() == TowerDef.Kind.SINGLE_TARGET && cannon.kind() == TowerDef.Kind.AREA && frost.kind() == TowerDef.Kind.SLOW, "each tower has the kind its role needs");
		expect(context, arrow.maxTier() == 3 && cannon.maxTier() == 3 && frost.maxTier() == 3, "every shipped tower has three tiers");
		expect(context, arrow.cost() < frost.cost() && frost.cost() < cannon.cost(), "arrow is the cheapest and cannon the dearest");
		context.succeed();
	}

	@GameTest
	public void towersCanBeOverriddenInCode(GameTestHelper context) {
		TowerDef def = valid();
		try {
			expect(context, TowerDefs.get(TEST_ID) == null, "the test id starts out unknown");
			TowerDefs.override(TEST_ID, def);
			expect(context, TowerDefs.get(TEST_ID) == def && TowerDefs.all().containsKey(TEST_ID), "an override is found");
		} finally {
			TowerDefs.clearOverride(TEST_ID);
		}
		expect(context, TowerDefs.get(TEST_ID) == null && !TowerDefs.all().containsKey(TEST_ID), "clearing removes it again");
		context.succeed();
	}
}
