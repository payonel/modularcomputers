package com.payonel.modularcomputers.hint;

import java.util.function.Consumer;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.NonNullList;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ExpansionHubBlock;
import com.payonel.modularcomputers.block.AllInOneBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.payonel.modularcomputers.item.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a mod item's tooltip shows, below its name: a short summary (data, like a battery's
 * charge), then "Hold [Shift] for details". Holding Shift shows the details instead: how the
 * item works, and how to craft it. The client draws it ({@code client.hint.HintComponent}).
 */
public final class Hint {
	/** One part of a hint, stacked top to bottom. */
	public sealed interface Element {
	}

	/** A line of text. */
	public record Text(Component text) implements Element {
	}

	/** A row of item icons, each with a short label under it. */
	public record Icons(List<Icon> icons) implements Element {
	}

	public record Icon(ItemStack stack, Component label) {
	}

	/** The crafting grid that makes this item (empty if nothing does yet). */
	public record Crafting(Item item) implements Element {
	}

	private Hint() {
	}

	/** Gray, like vanilla's own tooltip lines under the name. */
	public static Text text(Component text) {
		return new Text(text.copy().withStyle(ChatFormatting.GRAY));
	}

	/** The hint for a stack: its summary, then either the Shift prompt or the details. */
	public static List<Element> of(ItemStack stack, boolean details) {
		List<Element> elements = new ArrayList<>();
		Hinted hinted = stack.getItem() instanceof Hinted item ? item : Hinted.NONE;
		hinted.summary(stack, elements::add);
		contents(stack, elements::add);
		UUID serial = stack.get(ModComponents.SERIAL);
		if (!details) {
			elements.add(new Text(Component.translatable("hint.modularcomputers.shift",
				Component.translatable("hint.modularcomputers.shift_key").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY)));
			return elements;
		}
		hinted.details(stack, elements::add);
		if (serial != null) {
			// Its id (programs address it as id:<uuid>), long, so only in the details (user); crafting it alone clears it.
			elements.add(text(Component.translatable("hint.modularcomputers.id", serial.toString())));
			if (stack.getMaxStackSize() > 1) { // things holding data keep their id (ClearIdRecipe)
				elements.add(text(Component.translatable("hint.modularcomputers.clear_id")));
			}
		}
		elements.add(text(Component.translatable("hint.modularcomputers.crafting")));
		elements.add(new Crafting(stack.getItem()));
		return elements;
	}

	/**
	 * What a computer or card holder carries inside (it keeps its parts when broken), one line a
	 * part: a computer's bus devices in bus order (ROM, SATA, EXP), then the rest (CPU, RAM,
	 * UPS); a holder's cards by slot, then its radio.
	 */
	private static void contents(ItemStack stack, Consumer<Element> hint) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null || !(stack.getItem() instanceof BlockItem item)) {
			return;
		}
		List<Integer> order = new ArrayList<>();
		java.util.function.IntFunction<Component> label;
		NonNullList<ItemStack> slots;
		if (item.getBlock() instanceof AllInOneBlock) {
			slots = NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
			order.add(CaseMenu.EEPROM_SLOT);
			for (int slot = CaseMenu.SATA_SLOTS; slot < CaseMenu.PART_SLOTS; slot++) {
				order.add(slot); // SATA 1-4, then EXP 1-4: bus places 1-9
			}
			order.add(CaseMenu.CPU_SLOT);
			for (int slot = CaseMenu.RAM_SLOTS; slot < CaseMenu.SATA_SLOTS; slot++) {
				order.add(slot);
			}
			for (int slot = CaseMenu.UPS_SLOT; slot < CaseMenu.UPS_SLOT + CaseMenu.UPS_BAYS; slot++) {
				order.add(slot);
			}
			label = Hint::caseSlot;
		} else {
			int radio = item.getBlock() instanceof ExpansionHubBlock ? ExpansionHubBlockEntity.RADIO_SLOT : 0;
			slots = NonNullList.withSize(radio + 1, ItemStack.EMPTY);
			for (int slot = 0; slot <= radio; slot++) {
				if (slot != radio) {
					order.add(slot);
				}
			}
			order.add(radio);
			label = slot -> slot == radio ? Component.translatable("gui.modularcomputers.radio")
				: Component.translatable("hint.modularcomputers.slot.card", slot + 1);
		}
		contents.copyInto(slots);
		for (int slot : order) {
			if (slot < slots.size() && !slots.get(slot).isEmpty()) {
				hint.accept(text(Component.translatable("hint.modularcomputers.holds", label.apply(slot), slots.get(slot).getHoverName())));
			}
		}
	}

	private static Component caseSlot(int slot) {
		if (slot == CaseMenu.EEPROM_SLOT) {
			return Component.translatable("gui.modularcomputers.bus.rom");
		} else if (slot == CaseMenu.CPU_SLOT) {
			return Component.translatable("hint.modularcomputers.slot.cpu");
		} else if (CaseMenu.isBatteryBay(slot)) {
			return Component.translatable("hint.modularcomputers.slot.ups", slot - CaseMenu.UPS_SLOT + 1);
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return Component.translatable("hint.modularcomputers.slot.ram", slot - CaseMenu.RAM_SLOTS + 1);
		} else if (slot < CaseMenu.EXP_SLOTS) {
			return Component.translatable("gui.modularcomputers.bus.sata", slot - CaseMenu.SATA_SLOTS + 1);
		}
		return Component.translatable("gui.modularcomputers.bus.expansion", slot - CaseMenu.EXP_SLOTS + 1);
	}

	/** The text lines of a hint, e.g. for tests. */
	public static List<String> lines(List<Element> elements) {
		return elements.stream().filter(Text.class::isInstance).map(element -> ((Text) element).text().getString()).toList();
	}
}
