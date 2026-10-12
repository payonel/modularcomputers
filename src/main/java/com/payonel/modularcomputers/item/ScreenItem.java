package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.block.ScreenBlock;
import com.payonel.modularcomputers.hint.Hint;
import com.payonel.modularcomputers.hint.HintedBlockItem;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A screen (a terminal: display and keyboard) as an item: its hint says what it shows. */
public final class ScreenItem extends HintedBlockItem {
	private final ScreenBlock screen;

	public ScreenItem(ScreenBlock screen, Properties properties) {
		super(screen, properties);
		this.screen = screen;
	}

	public ScreenBlock screen() {
		return screen;
	}

	@Override
	public void summary(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.screen." + screen.tier().name().toLowerCase(Locale.ROOT))));
	}
}
