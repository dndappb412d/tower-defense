package dev.vadim.heartguard.client;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.tower.TowerListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class HeartguardClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CommanderView.init();
		CoinHud.init();
		ClientPlayNetworking.registerGlobalReceiver(TowerListPayload.TYPE, (payload, context) -> ClientTowers.set(payload.towers()));
		Heartguard.LOGGER.info("Heartguard initialized (client)");
	}
}
