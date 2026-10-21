package com.payonel.modularcomputers.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * What the common code needs from the loader it runs on, where the plain game has no way to do it.
 * Each loader's project implements it (fabric/, neoforge/), found as a service
 * ({@code META-INF/services}). Everything else the loader does (events, packet registration) its
 * project wires up itself, calling into the common code.
 */
public interface Platform {
	Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
		.orElseThrow(() -> new IllegalStateException("no " + Platform.class.getName() + " for this loader"));

	/** The mod's version, as the loader read it from its metadata. */
	String version();

	/** The game's config folder. */
	Path configDir();

	/**
	 * Every file under a folder of the mod's resources ({@code /data/...}), as paths relative to it
	 * ({@code bin/sh}); none when there's no such folder. (The class path can't list a folder: only
	 * the loader knows the mod's files.)
	 */
	List<String> files(String folder);

	/** Whether this player's client takes this packet (has the mod, and is a real client). */
	boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type);

	/**
	 * A builder for a creative tab of the mod's own, listing {@code items} (the loader places it among
	 * the others; the plain game's tab contents type isn't public).
	 */
	CreativeModeTab.Builder creativeTab(Supplier<List<ItemStack>> items);

	/** A menu type whose client side is made knowing the block it's for (its position, sent on opening). */
	<M extends AbstractContainerMenu> MenuType<M> menuType(BlockMenu<M> factory);

	/** Opens a menu of a type from {@link #menuType} for the player (nothing on a client), sending it {@code pos}. */
	void openMenu(Player player, MenuProvider provider, BlockPos pos);

	/** Makes the client's side of a menu for the block at {@code pos}. */
	interface BlockMenu<M extends AbstractContainerMenu> {
		M create(int id, Inventory inventory, BlockPos pos);
	}
}
