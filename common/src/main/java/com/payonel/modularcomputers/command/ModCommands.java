package com.payonel.modularcomputers.command;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.config.ModConfig;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.network.SettingsPayload;
import java.util.ArrayList;
import java.util.List;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /modularcomputers giveall}: a cheat for testing that gives the player one of every
 * item this mod adds (it finds them in the registry, so new items are included).
 * {@code /modularcomputers reload}: rereads the settings file and sends it to the players.
 * Both need the same permission as {@code /give}.
 */
public final class ModCommands {
	private ModCommands() {
	}

	/** Adds the commands (each loader calls this when the server builds its commands). */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal(ModularComputers.MOD_ID)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("giveall").executes(command -> {
					ServerPlayer player = command.getSource().getPlayerOrException();
					int count = giveAll(player);
					command.getSource().sendSuccess(() -> Component.translatable("commands.modularcomputers.giveall", count), true);
					return count;
				}))
				.then(Commands.literal("reload").executes(command -> {
					ModConfig.load();
					SettingsPayload.sendToAll(command.getSource().getServer());
					command.getSource().sendSuccess(() -> Component.translatable("commands.modularcomputers.reload", ModConfig.FILE_NAME), true);
					return 1;
				})));
	}

	/** Every item registered under this mod's namespace, in registration order. */
	public static List<Item> modItems() {
		return BuiltInRegistries.ITEM.stream()
			.filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ModularComputers.MOD_ID))
			.toList();
	}

	/** What it takes to build a working computer and wire a screen to it: these go in the hotbar first, in this order. */
	public static List<Item> hotbarFirst() {
		return List.of(ModBlocks.TIER3_CASE.asItem(), ModItems.TIER3_CPU, ModItems.TIER3_RAM, ModItems.EEPROM, ModItems.TIER1_BATTERY,
			ModBlocks.TIER3_SCREEN.asItem(), ModBlocks.CABLE.asItem());
	}

	/**
	 * Gives one of each mod item (batteries fully charged). The computer's essentials go into
	 * free hotbar slots first ({@link #hotbarFirst}); the rest wherever there's room, and what
	 * doesn't fit drops at the player's feet. Returns how many.
	 */
	public static int giveAll(ServerPlayer player) {
		List<Item> items = new ArrayList<>(hotbarFirst());
		modItems().stream().filter(item -> !items.contains(item)).forEach(items::add);
		Inventory inventory = player.getInventory();
		for (Item item : items) {
			ItemStack stack = item instanceof BatteryItem battery ? battery.charged() : new ItemStack(item);
			int hotbar = hotbarFirst().contains(item) ? freeHotbarSlot(inventory) : -1;
			if (hotbar >= 0) {
				inventory.setItem(hotbar, stack);
			} else {
				inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
		return items.size();
	}

	private static int freeHotbarSlot(Inventory inventory) {
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				return slot;
			}
		}
		return -1;
	}
}
