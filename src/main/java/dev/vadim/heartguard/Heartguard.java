package dev.vadim.heartguard;

import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.economy.Rewards;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchCommands;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.placement.PlaceMarkerPayload;
import dev.vadim.heartguard.tower.TowerDefs;
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
		PayloadTypeRegistry.serverboundPlay().register(PlaceMarkerPayload.TYPE, PlaceMarkerPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PlaceMarkerPayload.TYPE, (payload, context) -> {
			PlacementService.Result result = PlacementService.tryPlace(context.player(), payload.pos());
			LOGGER.debug("Place marker at {} for {}: {}", payload.pos(), context.player().getName().getString(), result);
		});
		MatchData.init();
		Coins.init();
		Rewards.init();
		ModBlocks.init();
		WaveSets.init();
		TowerDefs.init();
		Match.init();
		MatchCommands.init();
		LOGGER.info("Heartguard initialized (common)");
	}
}
