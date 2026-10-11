package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A block on the network with card slots inside: the screen, the expansion hub, the robotic
 * piston, the paint gun. Each has a <em>pairing slot</em> that takes a Bluetooth card: with
 * one there, the block belongs to the one computer that card links to (see
 * {@link Bluetooth#owner}) and no other computer sees it, though it still passes the cable on.
 * <p>
 * It has its own id, so computers can tell which one it is (for the bus order) wherever it is.
 * Its cards are saved and synced to clients, drop when it's broken, and make the card sounds
 * going in and out. A change tells computers to look along their cables again.
 */
public abstract class CardHolderBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos>, Bluetooth.Holder {
	private static final String ID = "holder_id"; // not "id": that is the block entity type

	private final Slots cards;
	private final ItemStack[] last;
	private UUID id = UUID.randomUUID();

	protected CardHolderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
		super(type, pos, state);
		cards = new Slots(slots);
		last = new ItemStack[slots];
	}

	/** Which block this is, to the computers it's wired to. */
	public UUID id() {
		return id;
	}

	public Container cards() {
		return cards;
	}

	/** The slot for the Bluetooth card that pairs this block with one computer. */
	public abstract int pairingSlot();

	/** Whether a slot takes this item: the pairing slot only a Bluetooth card. */
	public boolean accepts(int slot, ItemStack stack) {
		return slot == pairingSlot() ? stack.is(ModItems.BLUETOOTH_CARD) : ModItems.cardKind(stack).isPresent();
	}

	/** The Bluetooth card in the pairing slot, or empty: unpaired, shared by every computer that reaches it. */
	public ItemStack pairingCard() {
		ItemStack card = cards.getItem(pairingSlot());
		return card.is(ModItems.BLUETOOTH_CARD) ? card : ItemStack.EMPTY;
	}

	public boolean isPaired() {
		return !pairingCard().isEmpty();
	}

	/** The cards changed: rebuild whatever they make. Runs on both sides, and after loading. */
	protected void cardsChanged() {
	}

	/** Its Bluetooth cards: the pairing card first (cards linking to this block link to it), then the rest by slot. */
	@Override
	public List<ItemStack> bluetoothCards() {
		List<ItemStack> all = new ArrayList<>();
		if (isPaired()) {
			all.add(pairingCard());
		}
		for (int slot = 0; slot < cards.getContainerSize(); slot++) {
			if (slot != pairingSlot() && cards.getItem(slot).is(ModItems.BLUETOOTH_CARD)) {
				all.add(cards.getItem(slot));
			}
		}
		return all;
	}

	@Override
	public void bluetoothChanged() {
		cards.setChanged();
	}

	private void updateBluetooth() {
		if (level != null) {
			Bluetooth.update(level, worldPosition, bluetoothCards(), isPaired() ? pairingCard() : null, false);
		}
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		updateBluetooth();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null) {
			Bluetooth.remove(level, worldPosition);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		updateBluetooth();
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	private void onCardsChanged() {
		cardsChanged();
		updateBluetooth();
		setChanged();
		if (level == null || level.isClientSide()) {
			return;
		}
		for (int slot = 0; slot < last.length; slot++) {
			boolean had = last[slot] != null && !last[slot].isEmpty();
			boolean has = !cards.getItem(slot).isEmpty();
			if (has != had) {
				level.playSound(null, worldPosition, has ? ModSounds.CARD_INSERT : ModSounds.CARD_REMOVE, SoundSource.BLOCKS, 0.8f, 1.0f);
			}
		}
		remember();
		Cabling.changed();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void remember() {
		for (int slot = 0; slot < last.length; slot++) {
			last[slot] = cards.getItem(slot).copy();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, cards);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(ID, UUIDUtil.CODEC, id);
		ContainerHelper.saveAllItems(output, cards.getItems());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(ID, UUIDUtil.CODEC).ifPresent(saved -> id = saved);
		cards.getItems().clear(); // not clearContent(): that would fire change handling mid-load
		ContainerHelper.loadAllItems(input, cards.getItems());
		cardsChanged();
		updateBluetooth(); // a client's copy after a sync; on the server the level isn't set yet
		remember(); // loading isn't inserting: no sounds
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	/** The card slots: one card each. */
	private final class Slots extends SimpleContainer {
		Slots(int size) {
			super(size);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			onCardsChanged();
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return accepts(slot, stack);
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(CardHolderBlockEntity.this, player);
		}
	}
}
