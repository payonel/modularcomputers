package com.payonel.modularcomputers;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModularComputersTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		// fabric-loader-junit puts Minecraft on the test classpath; bootstrapping fills the
		// vanilla registries so tests can use items, blocks, etc. without starting a game.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void idUsesModNamespace() {
		var id = ModularComputers.id("computer_case");
		assertThat(id.getNamespace()).isEqualTo("modularcomputers");
		assertThat(id.getPath()).isEqualTo("computer_case");
		assertThat(id.toString()).isEqualTo("modularcomputers:computer_case");
	}

	@Test
	void vanillaRegistriesAreAvailableInUnitTests() {
		// Note: as of 26.x item data components are bound later than Bootstrap, so constructing
		// an ItemStack needs more setup; tests that need stacks belong in game tests for now.
		assertThat(BuiltInRegistries.ITEM.getKey(Items.REDSTONE).getPath()).isEqualTo("redstone");
		assertThat(BuiltInRegistries.BLOCK.containsKey(Identifier.withDefaultNamespace("redstone_block"))).isTrue();
	}
}
