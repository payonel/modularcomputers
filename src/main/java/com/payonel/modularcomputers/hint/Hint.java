package com.payonel.modularcomputers.hint;

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
		UUID serial = stack.get(ModComponents.SERIAL);
		if (serial != null) {
			// Its id, as programs address it; crafting it alone clears it.
			elements.add(new Text(Component.translatable("hint.modularcomputers.id", "id:" + serial).withStyle(ChatFormatting.GRAY)));
		}
		if (!details) {
			elements.add(new Text(Component.translatable("hint.modularcomputers.shift",
				Component.translatable("hint.modularcomputers.shift_key").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY)));
			return elements;
		}
		hinted.details(stack, elements::add);
		if (serial != null) {
			elements.add(text(Component.translatable("hint.modularcomputers.clear_id")));
		}
		elements.add(text(Component.translatable("hint.modularcomputers.crafting")));
		elements.add(new Crafting(stack.getItem()));
		return elements;
	}

	/** The text lines of a hint, e.g. for tests. */
	public static List<String> lines(List<Element> elements) {
		return elements.stream().filter(Text.class::isInstance).map(element -> ((Text) element).text().getString()).toList();
	}
}
