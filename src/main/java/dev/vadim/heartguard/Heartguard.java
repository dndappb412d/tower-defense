package dev.vadim.heartguard;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.item.ModItems;
import dev.vadim.heartguard.map.MapDef;
import dev.vadim.heartguard.mob.BossAbilityGoal;
import dev.vadim.heartguard.mob.MobAttachments;
import dev.vadim.heartguard.mob.MobDef;
import dev.vadim.heartguard.map.ReturnPoint;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchCommands;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.placement.Feedback;
import dev.vadim.heartguard.placement.PlaceTowerPayload;
import dev.vadim.heartguard.shop.Shop;
import dev.vadim.heartguard.tower.TowerActionPayload;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.tower.TowerRepair;
import dev.vadim.heartguard.tower.TowerService;
import dev.vadim.heartguard.tower.TowerSync;
import dev.vadim.heartguard.vein.Veins;
import dev.vadim.heartguard.wave.WaveSets;
import dev.vadim.heartguard.placement.PlacementService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Heartguard implements ModInitializer {
	public static final String MOD_ID = "heartguard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(PlaceTowerPayload.TYPE, PlaceTowerPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PlaceTowerPayload.TYPE, (payload, context) -> {
			PlacementService.Result result = PlacementService.tryPlace(context.player(), payload.tower(), payload.pos());
			Feedback.send(context.player(), result);
			LOGGER.debug("Place {} at {} for {}: {}", payload.tower(), payload.pos(), context.player().getName().getString(), result);
		});
		MatchData.init();
		Coins.init();
		Rewards.init();
		PayloadTypeRegistry.serverboundPlay().register(TowerActionPayload.TYPE, TowerActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TowerActionPayload.TYPE, (payload, context) -> {
			TowerService.Result result = switch (payload.action()) {
				case UPGRADE -> TowerService.upgrade(context.player(), payload.pos());
				case SELL -> TowerService.sell(context.player(), payload.pos());
				case CYCLE_TARGETING -> TowerService.cycleTargeting(context.player(), payload.pos());
			};
			Feedback.send(context.player(), result);
			LOGGER.debug("{} at {} for {}: {}", payload.action(), payload.pos(), context.player().getName().getString(), result);
		});
		TowerSync.init();
		TowerRepair.init();
		ModItems.init();
		Shop.init();
		MapDef.register();
		MobDef.register();
		MobAttachments.init();
		BossAbilityGoal.init();
		ReturnPoint.init();
		Veins.init();
		ModBlocks.init();
		WaveSets.init();
		TowerDefs.init();
		Match.init();
		MatchCommands.init();
		LOGGER.info("Heartguard initialized (common)");
	}
}
