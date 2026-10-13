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
				ModBlocks.CASES.forEach(output::accept);
				ModBlocks.SCREENS.forEach(output::accept);
				output.accept(ModBlocks.CABLE);
				output.accept(ModBlocks.EXPANSION_HUB);
				output.accept(ModBlocks.ROBOTIC_PISTON);
				output.accept(ModBlocks.PAINT_GUN);
				output.accept(ModBlocks.PHOTOMASK);
				output.accept(ModItems.SILICON);
				output.accept(ModItems.CHIP);
				ModItems.CPUS.forEach(output::accept);
				output.accept(ModItems.EEPROM);
				ModItems.RAMS.forEach(output::accept);
				ModItems.HARD_DISKS.forEach(output::accept);
				ModItems.CARDS.keySet().forEach(output::accept);
				ModItems.BATTERIES.forEach(battery -> output.accept(battery.charged()));
			})
			.build());
	}
}
