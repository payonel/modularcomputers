package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.ModularComputers;
import net.fabricmc.api.ClientModInitializer;

public final class ModularComputersClient implements ClientModInitializer {
	public static final String CLIENT_INIT_MARKER = "Modular Computers client initialized";

	private static volatile boolean initialized;

	public static boolean isInitialized() {
		return initialized;
	}

	@Override
	public void onInitializeClient() {
		initialized = true;
		ModularComputers.LOGGER.info(CLIENT_INIT_MARKER);
	}
}
