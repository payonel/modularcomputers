package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.Card;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An expansion hub: a box of expansion card slots on the cable network, like an external
 * expansion chassis. Every computer wired to it has its cards on its bus. A card works where
 * it's installed: a redstone card in a hub will emit and detect redstone at the hub.
 */
public final class ExpansionHubBlockEntity extends CardHolderBlockEntity {
	public static final int SLOTS = 4;

	/** The devices made from the cards, by slot; null when empty. */
	private final Card[] devices = new Card[SLOTS];

	public ExpansionHubBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.EXPANSION_HUB_ENTITY, pos, state, SLOTS);
	}

	@Override
	public boolean accepts(ItemStack stack) {
		return ModItems.cardKind(stack).isPresent();
	}

	/** The card in a slot, as a bus device. */
	public Optional<Card> card(int slot) {
		return Optional.ofNullable(devices[slot]);
	}

	@Override
	protected void cardsChanged() {
		for (int slot = 0; slot < SLOTS; slot++) {
			CardKind kind = ModItems.cardKind(cards().getItem(slot)).orElse(null);
			devices[slot] = kind == null ? null : devices[slot] != null && devices[slot].kind() == kind ? devices[slot] : new Card(kind);
		}
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.hub(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}
}
