package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskStation;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.DiskItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.DiskStationMenu;
import com.payonel.modularcomputers.runtime.Result;
import com.payonel.modularcomputers.sound.ModSounds;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Disk Station (user design): a dock holding a disk to work on (its target) and, optionally, a
 * source disk only read, for cloning; a battery bay; and a radio slot (a Bluetooth card), as other
 * blocks on the network have. Its device ({@link DiskStation}) and both disks are on the bus of
 * the computers wired to it. An action is paid from the bus's power while a running computer has it
 * on its bus, else from its battery. Disks go in and come out by hand. Broken, it keeps what's in it,
 * as other card holders do.
 */
public final class DiskStationBlockEntity extends CardHolderBlockEntity {
	public static final int TARGET = 0;
	public static final int SOURCE = 1;
	public static final int BATTERY = 2;
	public static final int RADIO = 3;
	public static final int SLOTS = 4;
	/** What the GUI's status shows: how the last action went. */
	public enum Outcome {
		NONE, DONE, NO_DISK, NO_SOURCE, NO_POWER, NO_FIT, READ_ONLY, FAILED;

		static Outcome of(Result result) {
			if (!result.failed()) {
				return DONE;
			}
			String error = String.valueOf(result.error());
			return switch (error) {
				case DiskStation.NO_DISK -> NO_DISK;
				case DiskStation.NO_SOURCE -> NO_SOURCE;
				case DiskStation.NO_POWER -> NO_POWER;
				case Disk.READ_ONLY -> READ_ONLY;
				default -> error.startsWith("doesn't fit") ? NO_FIT : FAILED;
			};
		}
	}

	/** The disks in its two disk slots, as devices; null when empty (or on a client). */
	private final Disk[] disks = new Disk[2];
	private final UUID[] serials = new UUID[2];
	private Outcome outcome = Outcome.NONE;
	private final DiskStation device = new DiskStation(new DiskStation.Bay() {
		@Override
		public Disk disk(String which) {
			return disks[slot(which)];
		}

		@Override
		public boolean pay(int cost) {
			return DiskStationBlockEntity.this.pay(cost);
		}

		@Override
		public List<Path> copyImage(String image, Path folder, boolean replace) {
			return com.payonel.modularcomputers.machine.LuaScripts.copyTree(image, folder, replace);
		}
	});
	/** The menu's view: the last action's {@link Outcome}, its battery's charge in percent. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return index == 0 ? outcome.ordinal() : batteryPercent();
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA;
		}
	};
	/** How many numbers its menu syncs ({@link #data}). */
	public static final int DATA = 2;

	public DiskStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.DISK_STATION_ENTITY, pos, state, SLOTS);
	}

	private static int slot(String which) {
		return which.equals(DiskStation.SOURCE) ? SOURCE : TARGET;
	}

	/** It, on the bus. */
	public DiskStation device() {
		return device;
	}

	/** The disk in a disk slot, as a device; null when empty. */
	public Disk disk(int slot) {
		return disks[slot];
	}

	@Override
	public int radioSlot() {
		return RADIO;
	}

	@Override
	public boolean accepts(int slot, ItemStack stack) {
		return takes(slot, stack);
	}

	/** What a slot takes: disks in its two disk slots, a battery in its bay, a Bluetooth card in its radio (the block's and its menu's rule). */
	public static boolean takes(int slot, ItemStack stack) {
		return switch (slot) {
			case TARGET, SOURCE -> stack.getItem() instanceof DiskItem;
			case BATTERY -> stack.getItem() instanceof BatteryItem;
			default -> stack.is(ModItems.BLUETOOTH_CARD);
		};
	}

	@Override
	protected boolean stamps(int slot) {
		return slot == TARGET || slot == SOURCE;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent sound(int slot, boolean inserted) {
		return switch (slot) {
			case TARGET, SOURCE -> inserted ? ModSounds.DISK_INSERT : ModSounds.DISK_REMOVE;
			case BATTERY -> inserted ? ModSounds.BATTERY_INSERT : ModSounds.BATTERY_REMOVE;
			default -> super.sound(slot, inserted);
		};
	}

	@Override
	protected void cardsChanged() {
		installDisks();
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		installDisks();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		for (int slot = 0; slot < disks.length; slot++) {
			retire(slot);
		}
		com.payonel.modularcomputers.board.PartHolds.releaseAll(this);
	}

	/** Server: a disk device for each disk in it, its data in the world save by its id; the source read only. */
	private void installDisks() {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		java.util.Set<UUID> kept = new java.util.HashSet<>();
		for (int slot = 0; slot < disks.length; slot++) {
			ItemStack stack = cards().getItem(slot);
			UUID claimed = stack.getItem() instanceof DiskItem ? ModComponents.serial(stack) : null;
			if (claimed != null && !com.payonel.modularcomputers.board.PartHolds.claim(claimed, this, server.getServer())) {
				retire(slot); // (a copy of a disk in another place: it waits, as no disk: PartHolds)
				serials[slot] = null;
				continue;
			}
			if (claimed != null) {
				kept.add(claimed);
			}
			if (!(stack.getItem() instanceof DiskItem item)) {
				retire(slot);
				serials[slot] = null;
				continue;
			}
			UUID serial = ModComponents.serial(stack);
			if (disks[slot] == null || disks[slot].tier() != item.tier() || !serial.equals(serials[slot])) {
				retire(slot);
				Path folder = AllInOneBlockEntity.diskFolder(server.getServer(), serial);
				AllInOneBlockEntity.imageDisk(stack, folder, level.getGameTime());
				Disk disk = new Disk(item.tier(), folder);
				disk.clockFrom(() -> level == null ? 0 : level.getGameTime());
				disk.onChange(() -> ChunkMarks.changed(level, worldPosition));
				disk.readOnly(slot == SOURCE);
				int at = slot;
				disk.labelIn(ModComponents.labelOn(() -> cards().getItem(at), () -> ChunkMarks.changed(level, worldPosition)));
				disk.formatIn(() -> ModComponents.diskFormat(cards().getItem(at)), format -> ModComponents.diskFormat(cards().getItem(at), format));
				disks[slot] = disk;
			}
			serials[slot] = serial;
		}
		com.payonel.modularcomputers.board.PartHolds.keep(this, kept);
	}

	private void retire(int slot) {
		if (disks[slot] != null) {
			disks[slot].retire();
			disks[slot] = null;
		}
	}

	/** Pays for an action: the bus's power while a running computer supplies it, else its battery's. */
	private boolean pay(int cost) {
		if (device.power().powered()) {
			device.power().charge(cost);
			return true;
		}
		ItemStack stack = cards().getItem(BATTERY);
		if (!(stack.getItem() instanceof BatteryItem battery) || level == null) {
			return false;
		}
		long now = level.getGameTime();
		var charge = BatteryItem.charge(stack).rest(battery.tier(), now);
		if (!battery.tier().infinite() && charge.units() < cost) {
			return false;
		}
		stack.set(ModComponents.CHARGE, charge.run(battery.tier(), cost, now));
		setChanged();
		return true;
	}

	private int batteryPercent() {
		ItemStack stack = cards().getItem(BATTERY);
		return stack.getItem() instanceof BatteryItem battery ? BatteryItem.charge(stack).percent(battery.tier()) : 0;
	}

	/** A button pressed in its GUI: the action, done as its device does it. */
	public void act(String action) {
		outcome = Outcome.of(device.act(action));
		setChanged();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new DiskStationMenu(id, inventory, cards(), data, worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}
}
