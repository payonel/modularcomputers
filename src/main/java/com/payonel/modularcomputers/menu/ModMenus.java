package com.payonel.modularcomputers.menu;

import static com.payonel.modularcomputers.ModularComputers.id;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Menu types. The case menu sends the computer's position to the client when it opens. */
public final class ModMenus {
	public static final ExtendedMenuType<CaseMenu, BlockPos> CASE = Registry.register(BuiltInRegistries.MENU, id("case"),
		new ExtendedMenuType<>(CaseMenu::new, BlockPos.STREAM_CODEC));

	public static final ExtendedMenuType<CardHolderMenu, BlockPos> EXPANSION_HUB = Registry.register(BuiltInRegistries.MENU, id("expansion_hub"),
		new ExtendedMenuType<>(CardHolderMenu::hub, BlockPos.STREAM_CODEC));

	public static final ExtendedMenuType<CardHolderMenu, BlockPos> ROBOTIC_PISTON = Registry.register(BuiltInRegistries.MENU, id("robotic_piston"),
		new ExtendedMenuType<>(CardHolderMenu::piston, BlockPos.STREAM_CODEC));

	private ModMenus() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}
}
