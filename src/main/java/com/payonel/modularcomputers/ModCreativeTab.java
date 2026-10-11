package com.payonel.modularcomputers;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.item.ModItems;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** The mod's own creative inventory tab, listing everything it adds. */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModularComputers.id("main"));

	private ModCreativeTab() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.modularcomputers"))
			.icon(() -> new ItemStack(ModBlocks.ALL_IN_ONE))
			.displayItems((parameters, output) -> {
				output.accept(ModBlocks.ALL_IN_ONE);
				output.accept(ModItems.CPU);
				output.accept(ModItems.EEPROM);
				output.accept(ModItems.RAM);
				output.accept(ModItems.HARD_DISK);
				output.accept(ModItems.WLAN_CARD);
				output.accept(ModItems.UPS_BATTERY);
			})
			.build());
	}
}
