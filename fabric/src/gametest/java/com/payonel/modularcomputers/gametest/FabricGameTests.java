package com.payonel.modularcomputers.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;

/**
 * Registers the server game tests (GameTests) on Fabric: their functions at start, and their
 * environment and instances whenever a world's registries are set up (Fabric's own test API finds
 * only its own annotation).
 */
public final class FabricGameTests implements ModInitializer {
	@Override
	public void onInitialize() {
		var tests = GameTests.all();
		tests.forEach(test -> Registry.register(BuiltInRegistries.TEST_FUNCTION, test.functionKey(), test.function()));
		DynamicRegistrySetupCallback.EVENT.register(view -> view.getOptional(Registries.TEST_INSTANCE).ifPresent(instances -> {
			var environments = view.getOptional(Registries.TEST_ENVIRONMENT).orElseThrow();
			Holder<TestEnvironmentDefinition<?>> environment = Registry.registerForHolder(environments, GameTests.ENVIRONMENT,
				new TestEnvironmentDefinition.AllOf(java.util.List.of()));
			tests.forEach(test -> Registry.register(instances, test.name(), test.instance(environment)));
		}));
	}
}
