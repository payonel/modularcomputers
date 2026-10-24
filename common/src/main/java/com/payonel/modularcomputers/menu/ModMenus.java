package com.payonel.modularcomputers.menu;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.payonel.modularcomputers.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Menu types. The case menu sends the computer's position to the client when it opens. */
public final class ModMenus {
	public static final MenuType<CaseMenu> CASE = Registry.register(BuiltInRegistries.MENU, id("case"),
		Platform.INSTANCE.menuType(CaseMenu::new));

	public static final MenuType<CardHolderMenu> EXPANSION_HUB = Registry.register(BuiltInRegistries.MENU, id("expansion_hub"),
		Platform.INSTANCE.menuType(CardHolderMenu::hub));

	public static final MenuType<CardHolderMenu> ROBOTIC_PISTON = Registry.register(BuiltInRegistries.MENU, id("robotic_piston"),
		Platform.INSTANCE.menuType(CardHolderMenu::piston));

	public static final MenuType<CardHolderMenu> SCREEN = Registry.register(BuiltInRegistries.MENU, id("screen"),
		Platform.INSTANCE.menuType(CardHolderMenu::screen));

	public static final MenuType<PhotomaskMenu> PHOTOMASK = Registry.register(BuiltInRegistries.MENU, id("photomask"),
		Platform.INSTANCE.menuType(PhotomaskMenu::new));

	public static final MenuType<DiskStationMenu> DISK_STATION = Registry.register(BuiltInRegistries.MENU, id("disk_station"),
		Platform.INSTANCE.menuType(DiskStationMenu::new));

	private ModMenus() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}

	/** Opens a block's menu (the block is its {@link MenuProvider}) for the player; nothing on a client. */
	public static void open(Player player, BlockEntity block) {
		Platform.INSTANCE.openMenu(player, (MenuProvider) block, block.getBlockPos());
	}
}
