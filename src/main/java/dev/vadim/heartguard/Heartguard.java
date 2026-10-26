package dev.vadim.heartguard;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Heartguard implements ModInitializer {
	public static final String MOD_ID = "heartguard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Heartguard initialized (common)");
	}
}
