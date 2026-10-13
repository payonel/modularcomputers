package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.Card;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An expansion hub: a box of expansion card slots on the cable network, like an external
 * expansion chassis. Every computer wired to it has its cards on its bus, unless a Bluetooth
 * card in its radio slot pairs the whole hub with one computer. A card works where it's
 * installed: a redstone card in a hub will emit and detect redstone at the hub.
 */
public final class ExpansionHubBlockEntity extends CardHolderBlockEntity {
	/** Expansion card slots; the radio slot comes after them. */
	public static final int SLOTS = 4;
	public static final int RADIO_SLOT = SLOTS;

	/** The devices made from the cards, by slot; null when empty. */
	private final Card[] devices = new Card[SLOTS];

	public ExpansionHubBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.EXPANSION_HUB_ENTITY, pos, state, SLOTS + 1);
	}

	@Override
	public int radioSlot() {
		return RADIO_SLOT;
	}

	/** The card in a slot, as a bus device. */
	public Optional<Card> card(int slot) {
		return Optional.ofNullable(devices[slot]);
	}

	@Override
	protected void cardsChanged() {
		for (int slot = 0; slot < SLOTS; slot++) {
			CardKind kind = ModItems.cardKind(cards().getItem(slot)).orElse(null);
			devices[slot] = kind == null ? null : devices[slot] != null && devices[slot].kind() == kind ? devices[slot] : cardHere(kind);
			if (devices[slot] != null) {
				int at = slot;
				devices[slot].labelFrom(() -> ModComponents.label(cards().getItem(at))); // read from the card itself
			}
		}
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.hub(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	/** A card that works here, at the hub: a redstone card's output changes update the hub's neighbours. */
	private Card cardHere(com.payonel.modularcomputers.device.CardKind kind) {
		Card card = com.payonel.modularcomputers.device.Cards.make(kind);
		if (card instanceof com.payonel.modularcomputers.device.RedstoneCard redstone) {
			redstone.onOutput(() -> {
				if (level != null && !level.isClientSide()) {
					level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
				}
			});
		}
		return card;
	}

	/** The redstone the hub's cards put out toward a neighbour ({@code direction}: from the neighbour to the hub). */
	public int redstoneOut(net.minecraft.core.Direction direction) {
		return CardWorld.redstone(java.util.Arrays.stream(devices).filter(java.util.Objects::nonNull).toList(), direction);
	}

	/** Server, every tick: its cards work where they are. */
	void serverTick() {
		for (Card card : devices) {
			if (card != null) {
				CardWorld.tick(card, (net.minecraft.server.level.ServerLevel) level, worldPosition, level.getGameTime());
			}
		}
	}
}
