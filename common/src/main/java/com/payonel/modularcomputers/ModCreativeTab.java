package com.payonel.modularcomputers;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** The mod's own creative inventory tab, listing everything it adds. */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModularComputers.id("main"));

	private ModCreativeTab() {
	}

	/** What it lists, in order. */
	static List<ItemStack> items() {
		List<ItemLike> things = new ArrayList<>();
		things.add(ModBlocks.ALL_IN_ONE);
		things.addAll(ModBlocks.CASES);
		things.addAll(ModBlocks.SCREENS);
		things.addAll(List.of(ModBlocks.CABLE, ModBlocks.EXPANSION_HUB, ModBlocks.ROBOTIC_PISTON, ModBlocks.ROBOTIC_STICKY_PISTON,
			ModBlocks.PAINT_GUN, ModBlocks.PHOTOMASK, ModBlocks.REDSTONE_TERMINAL, ModItems.SILICON, ModItems.CHIP));
		things.addAll(ModItems.CPUS);
		things.addAll(List.of(ModItems.JAVA_CPU, ModItems.EEPROM, ModItems.JAVA_EEPROM));
		things.addAll(ModItems.RAMS);
		things.addAll(ModItems.HARD_DISKS);
		things.addAll(ModItems.CARDS.keySet());
		List<ItemStack> items = new ArrayList<>(things.stream().map(ItemStack::new).toList());
		ModItems.BATTERIES.forEach(battery -> items.add(battery.charged()));
		return items;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, Platform.INSTANCE.creativeTab(ModCreativeTab::items)
			.title(Component.translatable("itemGroup.modularcomputers"))
			.icon(() -> new ItemStack(ModBlocks.ALL_IN_ONE))
			.build());
	}
}
