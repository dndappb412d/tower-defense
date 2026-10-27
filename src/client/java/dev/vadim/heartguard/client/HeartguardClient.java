package dev.vadim.heartguard.client;

import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.match.EndScreenPayload;
import dev.vadim.heartguard.shop.OpenShopPayload;
import dev.vadim.heartguard.tower.TowerListPayload;
import dev.vadim.heartguard.wave.WavePreview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class HeartguardClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CommanderView.init();
		CoinHud.init();
		WavePreviewHud.init();
		ClientPlayNetworking.registerGlobalReceiver(EndScreenPayload.TYPE, (payload, context) -> context.client().setScreen(new EndScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(WavePreview.TYPE, (payload, context) -> WavePreviewHud.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenShopPayload.TYPE,
			(payload, context) -> context.client().setScreen(new ShopScreen(payload.offers())));
		ClientPlayNetworking.registerGlobalReceiver(TowerListPayload.TYPE, (payload, context) -> ClientTowers.set(payload.towers()));
		Heartguard.LOGGER.info("Heartguard initialized (client)");
	}
}
