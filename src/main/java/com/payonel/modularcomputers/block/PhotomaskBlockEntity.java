package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.PhotomaskMenu;
import com.payonel.modularcomputers.device.Photomask;
import com.payonel.modularcomputers.power.PowerUnits;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.UUID;

/**
 * A photomask's frame: silicon in, chips out, and a bay for a battery. Each wafer of silicon takes
 * {@link #WORK} turns of the crank and gives {@link #CHIPS} chips, as a wafer is printed and diced
 * into dies. Powered, it turns every {@link #TICKS_PER_TURN} ticks, for {@link #TURN_COST} units a
 * turn: from the bus, when a running computer has it on its bus (it's a device, wired by cable),
 * or else from a charged battery in its bay. Broken, it keeps what's in it and its id (like a
 * shulker box).
 */
public final class PhotomaskBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	public static final int INPUT = 0;
	public static final int OUTPUT = 1;
	public static final int BATTERY = 2;
	public static final int SLOTS = 3;
	/** Turns of the crank a wafer takes. */
	public static final int WORK = 8;
	/** Chips a wafer gives. */
	public static final int CHIPS = 4;
	/** On a battery, it turns once this many ticks. */
	public static final int TICKS_PER_TURN = 5;
	/** Power units a battery-driven turn takes: a running CPU's tick. */
	public static final int TURN_COST = PowerUnits.CPU;
	/** The block event that tells clients the crank was turned (the gear turns an eighth). */
	public static final int SPIN_EVENT = 1;
	private static final float TURN_DEGREES = 45;
	private static final String PROGRESS = "progress";
	private static final String ID = "photomask_id";

	private final SimpleContainer items = new SimpleContainer(SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			PhotomaskBlockEntity.this.setChanged();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return accepts(slot, stack);
		}
	};
	private int progress;
	/** Its id, its bus address ({@code id:<uuid>}); kept on its item when broken. */
	private UUID id = UUID.randomUUID();
	private final Photomask device = new Photomask();
	/** The menu's view of the progress, in turns. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return progress;
		}

		@Override
		public void set(int index, int value) {
			progress = value;
		}

		@Override
		public int getCount() {
			return 1;
		}
	};
	// Client side: the gear's turn, in degrees, and how much of a crank's turn it still has to make.
	private float angle;
	private float previousAngle;
	private float pending;

	public PhotomaskBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.PHOTOMASK_ENTITY, pos, state);
	}

	public static boolean accepts(int slot, ItemStack stack) {
		return switch (slot) {
			case INPUT -> stack.is(ModItems.SILICON);
			case BATTERY -> stack.getItem() instanceof BatteryItem;
			default -> false; // chips only come out
		};
	}

	public UUID id() {
		return id;
	}

	/** It on the bus. */
	public Photomask device() {
		return device;
	}

	public SimpleContainer items() {
		return items;
	}

	public int progress() {
		return progress;
	}

	/** Silicon to print, and room for its chips. */
	public boolean canWork() {
		ItemStack out = items.getItem(OUTPUT);
		return items.getItem(INPUT).is(ModItems.SILICON)
			&& (out.isEmpty() || out.is(ModItems.CHIP) && out.getCount() + CHIPS <= out.getMaxStackSize());
	}

	/** One turn of the work; a wafer's last turn gives its chips. Returns whether there was work. */
	public boolean turn() {
		if (!canWork()) {
			return false;
		}
		if (++progress >= WORK) {
			progress = 0;
			items.getItem(INPUT).shrink(1);
			ItemStack out = items.getItem(OUTPUT);
			if (out.isEmpty()) {
				items.setItem(OUTPUT, new ItemStack(ModItems.CHIP, CHIPS));
			} else {
				out.grow(CHIPS);
			}
		}
		items.setChanged();
		return true;
	}

	/** A player turned the crank: the gear turns (for everyone watching), and the work goes on. */
	public void crank() {
		turn();
		level.blockEvent(worldPosition, getBlockState().getBlock(), SPIN_EVENT, 0);
		level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.6f);
	}

	/** Right-click with silicon or a battery: in it goes, as much as fits. */
	public void insert(ItemStack held) {
		int slot = held.is(ModItems.SILICON) ? INPUT : BATTERY;
		ItemStack there = items.getItem(slot);
		if (there.isEmpty()) {
			int count = slot == BATTERY ? 1 : held.getCount();
			items.setItem(slot, held.split(count));
		} else if (slot == INPUT && ItemStack.isSameItemSameComponents(there, held)) {
			int moved = Math.min(held.getCount(), there.getMaxStackSize() - there.getCount());
			there.grow(moved);
			held.shrink(moved);
			items.setChanged();
		}
	}

	private BatteryItem battery() {
		return items.getItem(BATTERY).getItem() instanceof BatteryItem battery ? battery : null;
	}

	/** Server, every tick: power from the bus, or else a charged battery, turns it while there's work. */
	void serverTick() {
		BatteryItem battery = battery();
		ItemStack stack = items.getItem(BATTERY);
		long now = level.getGameTime();
		boolean bus = device.power().powered();
		boolean charged = battery != null && !BatteryItem.charge(stack).isEmpty(battery.tier());
		boolean powered = (bus || charged) && canWork();
		if (powered && now % TICKS_PER_TURN == 0) {
			if (bus) {
				device.power().charge(TURN_COST); // the computers supplying it pay
			} else {
				stack.set(ModComponents.CHARGE, BatteryItem.charge(stack).run(battery.tier(), TURN_COST, now));
			}
			turn();
		}
		if (getBlockState().getValue(PhotomaskBlock.WORKING) != powered) {
			level.setBlock(worldPosition, getBlockState().setValue(PhotomaskBlock.WORKING, powered), Block.UPDATE_CLIENTS);
		}
	}

	/** Client, every tick: the gear turns on by itself while working, or finishes a crank's turn. */
	void clientTick() {
		previousAngle = angle;
		if (getBlockState().getValue(PhotomaskBlock.WORKING)) {
			angle += TURN_DEGREES / TICKS_PER_TURN;
		} else if (pending > 0) {
			float step = Math.min(pending, 15);
			angle += step;
			pending -= step;
		}
	}

	/** The gear's turn, in degrees, between ticks. */
	public float gearAngle(float partialTick) {
		return Mth.lerp(partialTick, previousAngle, angle);
	}

	@Override
	public boolean triggerEvent(int id, int param) {
		if (id == SPIN_EVENT) {
			pending += TURN_DEGREES;
			return true;
		}
		return super.triggerEvent(id, param);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.modularcomputers.photomask");
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new PhotomaskMenu(id, inventory, items, data, worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// Its contents stay in its item (collectImplicitComponents): nothing spills.
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(ModComponents.SERIAL, id);
		if (!items.isEmpty()) {
			components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items.getItems()));
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		UUID serial = components.get(ModComponents.SERIAL);
		if (serial != null) {
			id = serial;
		}
		items.getItems().clear();
		components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items.getItems());
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		super.removeComponentsFromTag(output);
		output.discard("Items");
		output.discard(ID);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt(PROGRESS, progress);
		output.store(ID, net.minecraft.core.UUIDUtil.CODEC, id);
		ContainerHelper.saveAllItems(output, items.getItems());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		progress = input.getIntOr(PROGRESS, 0);
		input.read(ID, net.minecraft.core.UUIDUtil.CODEC).ifPresent(saved -> id = saved);
		items.getItems().clear();
		ContainerHelper.loadAllItems(input, items.getItems());
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
