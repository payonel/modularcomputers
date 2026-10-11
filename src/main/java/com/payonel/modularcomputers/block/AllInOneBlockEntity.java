package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.device.NetworkCard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.HelloWorldProgram;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The all-in-one computer: its built-in display and keyboard, and a motherboard whose part
 * slots (CPU, EEPROM, UPS battery, RAM, SATA drives, expansion cards) are filled from the case
 * view. Drives and cards join the physical bus after the built-ins, in an order the player
 * can change (the bus order panel). Power, the parts, the bus order, display contents and the
 * console state are saved and synced to clients. Only the CPU and EEPROM affect the machine so
 * far; RAM and the UPS battery are placeholders.
 */
public final class AllInOneBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private static final String POWERED = "powered";
	private static final String DISPLAY = "display";
	private static final String FOREGROUND = "display_foreground";
	private static final String BACKGROUND = "display_background";
	private static final String CONSOLE = "console";
	private static final String BUS_ORDER = "bus_order";
	private static final String DISPLAY_KEY = "display";
	private static final String KEYBOARD_KEY = "keyboard";
	private static final String SATA_KEY = "sata:";
	private static final String EXP_KEY = "exp:";
	/** Icons for the built-in devices in textures/gui/case_icons.png. */
	public static final int DISPLAY_ICON = 6;
	public static final int KEYBOARD_ICON = 7;
	private static final float ERROR_BEEP_VOLUME = 0.6f;

	private final Display display = new Display(Resolution.DEFAULT);
	private final Keyboard keyboard = new Keyboard();
	/** Devices made from the parts in the SATA and expansion slots, by lane; null when empty. */
	private final Disk[] disks = new Disk[CaseMenu.LANES];
	private final NetworkCard[] cards = new NetworkCard[CaseMenu.LANES];
	private final BusOrder busOrder = new BusOrder();
	private final Bus bus = new Bus(List.of(display, keyboard));
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), bus));
	private final Parts parts = new Parts();

	public AllInOneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALL_IN_ONE_ENTITY, pos, state);
	}

	public Machine machine() {
		return machine;
	}

	/** The built-in screen's display. */
	public Display display() {
		return display;
	}

	/** The part slots (see {@link CaseMenu} for which is which). */
	public Container parts() {
		return parts;
	}

	/** One line of the bus order panel. {@code item} is the part's item, or empty for a built-in device. */
	public record BusEntry(String key, Component name, Component location, ItemStack item, int builtInIcon) {
	}

	/** The devices on the bus, in order, as the bus order panel shows them. Works on clients too. */
	public List<BusEntry> busEntries() {
		List<BusEntry> entries = new ArrayList<>();
		Component builtIn = Component.translatable("gui.modularcomputers.bus.built_in");
		for (String key : busOrder.keys()) {
			if (key.equals(DISPLAY_KEY)) {
				entries.add(new BusEntry(key, Component.translatable("gui.modularcomputers.device.display"), builtIn, ItemStack.EMPTY, DISPLAY_ICON));
			} else if (key.equals(KEYBOARD_KEY)) {
				entries.add(new BusEntry(key, Component.translatable("gui.modularcomputers.device.keyboard"), builtIn, ItemStack.EMPTY, KEYBOARD_ICON));
			} else {
				boolean sata = key.startsWith(SATA_KEY);
				int lane = Integer.parseInt(key.substring(key.indexOf(':') + 1));
				ItemStack item = parts.getItem((sata ? CaseMenu.SATA_SLOTS : CaseMenu.EXP_SLOTS) + lane);
				Component location = Component.translatable(sata ? "gui.modularcomputers.bus.sata" : "gui.modularcomputers.bus.expansion", lane + 1);
				entries.add(new BusEntry(key, item.getHoverName(), location, item, -1));
			}
		}
		return entries;
	}

	/** Server side: the player moved the device at {@code index} up or down the bus order. */
	public boolean moveDevice(int index, boolean up) {
		if (!busOrder.move(index, up)) {
			return false;
		}
		rebuildBus();
		setChanged();
		syncToClients();
		return true;
	}

	/**
	 * Server side: someone pressed the power button. It clicks either way; without a CPU the
	 * machine stays off and beeps an error. Clients learn the new state from the block state.
	 */
	public Machine.PowerPress pressPower() {
		Machine.PowerPress result = machine.pressPower();
		setChanged();
		if (level == null) {
			return result;
		}
		boolean on = result == Machine.PowerPress.ON;
		level.playSound(null, worldPosition, on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
			SoundSource.BLOCKS, 0.3f, on ? 0.6f : 0.5f);
		if (result == Machine.PowerPress.NO_CPU) {
			errorBeep();
			// The light shows faded green for the moment the attempt lasts; the block's tick
			// sets it back.
			level.setBlock(worldPosition, getBlockState().setValue(AllInOneBlock.POWERED, true), Block.UPDATE_CLIENTS);
			level.scheduleTick(worldPosition, getBlockState().getBlock(), AllInOneBlock.ATTEMPT_TICKS);
		} else {
			// Changing the block state also sends this block entity's update to clients.
			level.setBlock(worldPosition, getBlockState().setValue(AllInOneBlock.POWERED, on), Block.UPDATE_CLIENTS);
		}
		return result;
	}

	/** One short, low buzz: an error, not a confirmation. */
	private void errorBeep() {
		level.playSound(null, worldPosition, ModSounds.ERROR_BEEP, SoundSource.BLOCKS, ERROR_BEEP_VOLUME, 1.0f);
	}

	/** Puts the parts in the slots onto the motherboard, and drives and cards onto the bus. */
	private void installParts() {
		Motherboard board = machine.board();
		board.setCpu(parts.getItem(CaseMenu.CPU_SLOT).is(ModItems.CPU) ? new Cpu() : null);
		// Every EEPROM holds the stand-in program until EEPROM contents are programmable.
		board.setEeprom(parts.getItem(CaseMenu.EEPROM_SLOT).is(ModItems.EEPROM) ? new Eeprom(new HelloWorldProgram()) : null);
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			boolean disk = parts.getItem(CaseMenu.SATA_SLOTS + lane).is(ModItems.HARD_DISK);
			disks[lane] = disk ? (disks[lane] != null ? disks[lane] : new Disk()) : null;
			boolean card = parts.getItem(CaseMenu.EXP_SLOTS + lane).is(ModItems.WLAN_CARD);
			cards[lane] = card ? (cards[lane] != null ? cards[lane] : new NetworkCard()) : null;
		}
		rebuildBus();
	}

	/** Puts the bus's devices in the player's order; newly attached ones go last. */
	private void rebuildBus() {
		Map<String, Device> attached = new LinkedHashMap<>();
		attached.put(DISPLAY_KEY, display);
		attached.put(KEYBOARD_KEY, keyboard);
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			if (disks[lane] != null) {
				attached.put(SATA_KEY + lane, disks[lane]);
			}
		}
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			if (cards[lane] != null) {
				attached.put(EXP_KEY + lane, cards[lane]);
			}
		}
		busOrder.sync(List.copyOf(attached.keySet()));
		bus.setDevices(busOrder.keys().stream().map(attached::get).toList());
	}

	/** Sends this block entity's data (parts, bus order, display) to clients. */
	private void syncToClients() {
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private void onPartsChanged() {
		installParts();
		setChanged();
		if (level == null || level.isClientSide()) {
			return;
		}
		if (machine.cpuRemoved()) {
			// Pulling the CPU out of a running machine kills it.
			errorBeep();
		}
		// The power button shows both; the block state carries them to clients.
		BlockState state = getBlockState()
			.setValue(AllInOneBlock.CPU, machine.board().cpu().isPresent())
			.setValue(AllInOneBlock.POWERED, machine.isPowered());
		if (state != getBlockState()) {
			level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
		} else {
			syncToClients(); // the bus order panel shows the parts
		}
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new CaseMenu(id, inventory, parts, worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, parts);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean(POWERED, machine.isPowered());
		ContainerHelper.saveAllItems(output, parts.getItems());
		output.store(BUS_ORDER, Codec.STRING.listOf(), busOrder.keys());
		output.store(DISPLAY, Codec.STRING.listOf(), display.buffer().rows());
		output.putIntArray(FOREGROUND, display.buffer().foregrounds());
		output.putIntArray(BACKGROUND, display.buffer().backgrounds());
		ConsoleSplitter console = machine.board().virtualBus().console();
		output.putIntArray(CONSOLE, new int[] {console.column(), console.row(), console.foreground(), console.background()});
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		machine.restorePowered(input.getBooleanOr(POWERED, false));
		parts.getItems().clear(); // not clearContent(): that would fire change handling mid-load
		ContainerHelper.loadAllItems(input, parts.getItems());
		input.read(BUS_ORDER, Codec.STRING.listOf()).ifPresent(busOrder::restore);
		installParts();
		input.read(DISPLAY, Codec.STRING.listOf()).ifPresent(display.buffer()::setRows);
		input.getIntArray(FOREGROUND).ifPresent(foreground ->
			input.getIntArray(BACKGROUND).ifPresent(background -> display.buffer().setColors(foreground, background)));
		input.getIntArray(CONSOLE).filter(state -> state.length == 4).ifPresent(state ->
			machine.board().virtualBus().console().restore(state[0], state[1], state[2], state[3]));
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	/** The part slots. Changes install the parts and update the power button. */
	private final class Parts extends SimpleContainer {
		Parts() {
			super(CaseMenu.PART_SLOTS);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			onPartsChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(AllInOneBlockEntity.this, player);
		}
	}
}
