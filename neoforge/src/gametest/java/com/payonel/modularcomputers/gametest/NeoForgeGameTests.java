package com.payonel.modularcomputers.gametest;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The test mod on NeoForge: registers the server game tests (GameTests), their functions while the
 * game's registries take entries and their environment and instances when NeoForge asks for tests.
 */
@Mod(NeoForgeGameTests.MOD_ID)
public final class NeoForgeGameTests {
	public static final String MOD_ID = "modularcomputers_gametest";

	public NeoForgeGameTests(IEventBus modBus) {
		List<GameTests.Test> tests = GameTests.all();
		GameTests.mockConnection = player -> NetworkRegistry.configureMockConnection(player.connection.getConnection());
		modBus.addListener(RegisterEvent.class, event -> {
			if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
				tests.forEach(test -> Registry.register(BuiltInRegistries.TEST_FUNCTION, test.functionKey(), test.function()));
			}
		});
		modBus.addListener(RegisterGameTestsEvent.class, event -> {
			Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(GameTests.ENVIRONMENT,
				new TestEnvironmentDefinition.AllOf(List.of()));
			tests.forEach(test -> event.registerTest(test.name(), test.instance(environment)));
		});
	}
}
