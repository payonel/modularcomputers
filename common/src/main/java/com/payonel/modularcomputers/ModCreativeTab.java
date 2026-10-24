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

/**
 * The mod's own creative inventory tab, listing everything it adds: its usual order first, then
 * every other item in the mod's namespace (found in the registry, so a new item is never missing).
 * Its icon is a tier 3 CPU (the user's pick).
 */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModularComputers.id("main"));

	private ModCreativeTab() {
	}

	/** What it lists, in order. */
	public static List<ItemStack> items() {
		List<ItemLike> things = new ArrayList<>();
		things.add(ModBlocks.ALL_IN_ONE);
		things.addAll(ModBlocks.CASES);
		things.addAll(ModBlocks.SCREENS);
		things.addAll(List.of(ModBlocks.CABLE, ModBlocks.EXPANSION_HUB, ModBlocks.ROBOTIC_PISTON, ModBlocks.ROBOTIC_STICKY_PISTON,
			ModBlocks.PHOTOMASK, ModBlocks.DISK_STATION, ModItems.SILICON, ModItems.CHIP));
		things.addAll(ModItems.CPUS);
		things.addAll(List.of(ModItems.JAVA_CPU, ModItems.EEPROM, ModItems.JAVA_EEPROM));
		things.addAll(ModItems.RAMS);
		things.addAll(ModItems.HARD_DISKS);
		things.addAll(ModItems.CARDS.keySet());
		List<ItemStack> items = new ArrayList<>(things.stream().map(ItemStack::new).toList());
		ModItems.BATTERIES.forEach(battery -> items.add(battery.charged()));
		for (var item : com.payonel.modularcomputers.command.ModCommands.modItems()) {
			if (items.stream().noneMatch(stack -> stack.is(item))) {
				items.add(new ItemStack(item)); // (one not placed in the order above)
			}
		}
		return items;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, Platform.INSTANCE.creativeTab(ModCreativeTab::items)
			.title(Component.translatable("itemGroup.modularcomputers"))
			.icon(() -> new ItemStack(ModItems.TIER3_CPU))
			.build());
	}
}
