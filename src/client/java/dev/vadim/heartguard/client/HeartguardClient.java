package dev.vadim.heartguard.client;

import dev.vadim.heartguard.Heartguard;
import net.fabricmc.api.ClientModInitializer;

public class HeartguardClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Heartguard.LOGGER.info("Heartguard initialized (client)");
	}
}
