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

	/** A card's saved state, by slot ({@link DeviceSave}). */
	private static final String CARD = "card_";

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
		Object server = level instanceof net.minecraft.server.level.ServerLevel world ? world.getServer() : null;
		java.util.Set<java.util.UUID> kept = new java.util.HashSet<>();
		for (int slot = 0; slot < SLOTS; slot++) {
			CardKind kind = ModItems.cardKind(cards().getItem(slot)).orElse(null);
			java.util.UUID serial = cards().getItem(slot).get(ModComponents.SERIAL);
			if (kind != null && server != null && serial != null) {
				if (!com.payonel.modularcomputers.board.PartHolds.claim(serial, this, server)) {
					kind = null; // (a copy of a card in another place: it waits, as no card: PartHolds)
				} else {
					kept.add(serial);
				}
			}
			devices[slot] = kind == null ? null : devices[slot] != null && devices[slot].kind() == kind ? devices[slot] : cardHere(kind);
			if (devices[slot] != null) {
				int at = slot;
				devices[slot].labelIn(ModComponents.labelOn(() -> cards().getItem(at), () -> ChunkMarks.changed(level, worldPosition))); // the card's own
				devices[slot].idFrom(() -> cards().getItem(at).get(ModComponents.SERIAL));
			}
		}
		if (server != null) {
			com.payonel.modularcomputers.board.PartHolds.keep(this, kept);
		}
	}

	@Override
	public void setLevel(net.minecraft.world.level.Level level) {
		super.setLevel(level);
		cardsChanged(); // (loaded before it had a world: its cards claimed now)
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		com.payonel.modularcomputers.board.PartHolds.releaseAll(this);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.hub(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	/** A card that works here, at the hub: a redstone card's output changes update the hub's neighbours. */
	/** Its frame, where its cards say things are: its center, its front, its right as someone facing its front sees it. */
	private com.payonel.modularcomputers.entity.FieldProjector.Frame frame() {
		return com.payonel.modularcomputers.entity.FieldProjector.Frame.of(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition),
			getBlockState().getValue(ExpansionHubBlock.FACING));
	}

	private Card cardHere(com.payonel.modularcomputers.device.CardKind kind) {
		Card card = com.payonel.modularcomputers.device.Cards.make(kind);
		if (card instanceof com.payonel.modularcomputers.device.OperatorCard operator) {
			operator.runWith(command -> level instanceof net.minecraft.server.level.ServerLevel server
				? OperatorCommands.run(server, worldPosition, command)
				: new com.payonel.modularcomputers.device.OperatorCard.Outcome(false, 0, "not in a world"));
		}
		// Minecraft writes only chunks marked changed: its cards' events and settings change as they're used.
		card.onChange(() -> ChunkMarks.changed(level, worldPosition));
		card.frontFrom(() -> com.payonel.modularcomputers.device.Face.of(getBlockState().getValue(ExpansionHubBlock.FACING).getName()));
		if (card instanceof com.payonel.modularcomputers.device.FieldCard field) {
			field.projectorIn(new com.payonel.modularcomputers.entity.FieldProjector(field, () -> (net.minecraft.server.level.ServerLevel) level,
				this::frame, () -> null));
		}
		if (card instanceof com.payonel.modularcomputers.device.RedstoneCard redstone) {
			redstone.block(side -> CardWorld.redstone(java.util.Arrays.stream(devices).filter(java.util.Objects::nonNull).toList(), side));
			redstone.onOutput(() -> {
				if (level != null && !level.isClientSide()) {
					level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
				}
				for (var other : devices) {
					if (other instanceof com.payonel.modularcomputers.device.RedstoneCard sibling && sibling != redstone) {
						sibling.report(); // what the block puts out changed: so may their levels
					}
				}
			});
		}
		return card;
	}

	/** What its cards hold for programs (events) and the settings programs made: a save isn't a power cut. */
	@Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		for (int slot = 0; slot < SLOTS; slot++) {
			if (devices[slot] != null) {
				DeviceSave.save(output, CARD + slot, devices[slot]);
			}
		}
	}

	@Override
	protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input); // makes the cards
		for (int slot = 0; slot < SLOTS; slot++) {
			if (devices[slot] != null) {
				DeviceSave.load(input, CARD + slot, devices[slot]);
			}
		}
	}

	/** The redstone the hub's cards put out toward a neighbour ({@code direction}: from the neighbour to the hub). */
	public int redstoneOut(net.minecraft.core.Direction direction) {
		return CardWorld.redstone(java.util.Arrays.stream(devices).filter(java.util.Objects::nonNull).toList(), direction);
	}

	/** Server, every tick: its cards work where they are. */
	void serverTick() {
		for (Card card : devices) {
			if (card != null) {
				CardWorld.tick(card, (net.minecraft.server.level.ServerLevel) level, worldPosition, frame(), level.getGameTime());
			}
		}
	}
}
