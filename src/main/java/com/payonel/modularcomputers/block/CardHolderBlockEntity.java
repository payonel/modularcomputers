package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.sound.ModSounds;
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
 * A cabled block with card slots inside (the expansion hub, the robotic piston). It has its
 * own id, so computers can tell which one it is (for the bus order) wherever it is. Its cards
 * are saved and synced to clients, drop when it's broken, and make the card sounds going in
 * and out. A change tells computers to look along their cables again.
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

	/** Whether a slot takes this card. */
	public abstract boolean accepts(ItemStack stack);

	/** The cards changed: rebuild whatever they make. Runs on both sides, and after loading. */
	protected void cardsChanged() {
	}

	@Override
	public List<ItemStack> bluetoothCards() {
		return Bluetooth.cardsAmong(cards.getItems());
	}

	@Override
	public void bluetoothChanged() {
		cards.setChanged();
	}

	private void updateBluetooth() {
		if (level != null) {
			Bluetooth.update(level, worldPosition, bluetoothCards());
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
			return accepts(stack);
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(CardHolderBlockEntity.this, player);
		}
	}
}
