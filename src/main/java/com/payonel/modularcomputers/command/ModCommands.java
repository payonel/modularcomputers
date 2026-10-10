package com.payonel.modularcomputers.command;

import com.payonel.modularcomputers.ModularComputers;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /modularcomputers giveall}: a cheat for testing that gives the player one of every
 * item this mod adds (it finds them in the registry, so new items are included). Needs the
 * same permission as {@code /give}.
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
			Commands.literal(ModularComputers.MOD_ID)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("giveall").executes(command -> {
					ServerPlayer player = command.getSource().getPlayerOrException();
					int count = giveAll(player);
					command.getSource().sendSuccess(() -> Component.translatable("commands.modularcomputers.giveall", count), true);
					return count;
				}))));
	}

	/** Every item registered under this mod's namespace, in registration order. */
	public static List<Item> modItems() {
		return BuiltInRegistries.ITEM.stream()
			.filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ModularComputers.MOD_ID))
			.toList();
	}

	/** Gives one of each mod item; what doesn't fit drops at the player's feet. Returns how many. */
	public static int giveAll(ServerPlayer player) {
		List<Item> items = modItems();
		for (Item item : items) {
			player.getInventory().placeItemBackInInventory(new ItemStack(item), Prediction.SERVER_ONLY);
		}
		return items.size();
	}
}
