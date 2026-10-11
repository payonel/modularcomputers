package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.cable.CableWalk;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.device.Card;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.UpsBattery;
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
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * view. Drives and cards join the physical bus after the built-ins, then the screens wired to
 * it by cable, in an order the player can change (the bus order panel), where cabled devices
 * can also be disconnected. Power, the parts, the bus order, display contents and the console
 * state are saved and synced to clients. Only the CPU and EEPROM affect the machine so far;
 * RAM and the UPS battery are placeholders.
 */
public final class AllInOneBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos>, DisplayHost {
	private static final String POWERED = "powered";
	private static final String DISPLAY = "display";
	private static final String FOREGROUND = "display_foreground";
	private static final String BACKGROUND = "display_background";
	private static final String CONSOLE = "console";
	private static final String BUS_ORDER = "bus_order";
	private static final String BUS_DISCONNECTED = "bus_disconnected";
	private static final String CABLED = "cabled";
	private static final String CONSOLE_SCREEN = "console_screen";
	private static final String CONSOLE_FOREGROUND = "console_foreground";
	private static final String CONSOLE_BACKGROUND = "console_background";
	private static final String DISPLAY_KEY = "display";
	private static final String KEYBOARD_KEY = "keyboard";
	private static final String EEPROM_KEY = "eeprom";
	private static final String UPS_KEY = "ups";
	private static final String SATA_KEY = "sata:";
	private static final String EXP_KEY = "exp:";
	/** Cabled devices, by their own id: {@code cable:<uuid>}. */
	private static final String CABLE_KEY = "cable:";
	/** Looks for cabled devices this often, in ticks, even when no cable changed (chunks load and unload). */
	private static final int RESCAN_TICKS = 20;
	/** Icons for the built-in devices in textures/gui/case_icons.png. */
	public static final int DISPLAY_ICON = 6;
	public static final int KEYBOARD_ICON = 7;
	private static final float ERROR_BEEP_VOLUME = 0.6f;

	private final Display display = new Display(Resolution.DEFAULT);
	private final Keyboard keyboard = new Keyboard();
	/** Devices made from the parts in the slots (by lane for drives and cards); null when empty. */
	private final Disk[] disks = new Disk[CaseMenu.LANES];
	private final Card[] cards = new Card[CaseMenu.LANES];
	private Eeprom eeprom;
	private UpsBattery ups;
	private final BusOrder busOrder = new BusOrder();
	private final Bus bus = new Bus(List.of(display, keyboard));
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), bus));
	private final Parts parts = new Parts();
	/** The cabled devices found last time, nearest first. Saved and synced, for the bus order panel. */
	private List<Cabled> cabled = List.of();
	/** Server side: the cabled devices' displays, by key. */
	private final Map<String, Display> cabledDisplays = new LinkedHashMap<>();
	private long seenCableChanges = -1;
	private int ticks;
	private int syncedDisplay = -1;

	/** A device wired to this computer: where it is, and how far the connection runs to it, in meters (blocks). */
	public record Cabled(String key, BlockPos pos, int meters) {
		static final Codec<Cabled> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("key").forGetter(Cabled::key),
			BlockPos.CODEC.fieldOf("pos").forGetter(Cabled::pos),
			Codec.INT.fieldOf("meters").forGetter(Cabled::meters)
		).apply(instance, Cabled::new));
	}

	public AllInOneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALL_IN_ONE_ENTITY, pos, state);
	}

	public Machine machine() {
		return machine;
	}

	/** The built-in screen's display. */
	@Override
	public Display display() {
		return display;
	}

	/** The part slots (see {@link CaseMenu} for which is which). */
	public Container parts() {
		return parts;
	}

	/** What the server sends to open this computer's screen view. */
	public OpenDisplayPayload openDisplayPayload() {
		return new OpenDisplayPayload(worldPosition, display.buffer().resolution(), Optional.of(worldPosition), true);
	}

	/**
	 * One line of the bus order panel. {@code item} and {@code slot} are the part's item and
	 * case slot, or empty and -1 for a built-in device. Cabled devices have a {@code device}
	 * position (the bulb finds them in the world) and can be disconnected.
	 */
	public record BusEntry(String key, Component name, Component location, ItemStack item, int slot, int builtInIcon,
		Optional<BlockPos> device, boolean connected) {
		public boolean cabled() {
			return device.isPresent();
		}
	}

	/** Server side: whether this computer has the screen on its bus (wired and connected). */
	public boolean drives(ScreenBlockEntity screen) {
		return cabledDisplays.get(CABLE_KEY + screen.id()) == screen.display() && busOrder.connected().contains(CABLE_KEY + screen.id());
	}

	/** The devices on the bus, in order, as the bus order panel shows them. Works on clients too. */
	public List<BusEntry> busEntries() {
		List<BusEntry> entries = new ArrayList<>();
		Component builtIn = Component.translatable("gui.modularcomputers.bus.built_in");
		for (String key : busOrder.keys()) {
			if (key.equals(DISPLAY_KEY)) {
				entries.add(new BusEntry(key, Component.translatable("gui.modularcomputers.device.display"), builtIn, ItemStack.EMPTY, -1, DISPLAY_ICON,
					Optional.empty(), true));
			} else if (key.equals(KEYBOARD_KEY)) {
				entries.add(new BusEntry(key, Component.translatable("gui.modularcomputers.device.keyboard"), builtIn, ItemStack.EMPTY, -1, KEYBOARD_ICON,
					Optional.empty(), true));
			} else if (key.startsWith(CABLE_KEY)) {
				cabled.stream().filter(device -> device.key().equals(key)).findFirst().ifPresent(device -> {
					ItemStack item = new ItemStack(ModBlocks.SCREEN);
					Component location = Component.translatable("gui.modularcomputers.bus.cable", device.meters());
					entries.add(new BusEntry(key, item.getHoverName(), location, item, -1, -1, Optional.of(device.pos()), busOrder.isConnected(key)));
				});
			} else {
				int slot = partSlot(key);
				ItemStack item = parts.getItem(slot);
				entries.add(new BusEntry(key, item.getHoverName(), partLocation(key), item, slot, -1, Optional.empty(), true));
			}
		}
		return entries;
	}

	/** The case slot a part's bus key stands for. */
	private static int partSlot(String key) {
		if (key.equals(EEPROM_KEY)) {
			return CaseMenu.EEPROM_SLOT;
		} else if (key.equals(UPS_KEY)) {
			return CaseMenu.UPS_SLOT;
		}
		int lane = Integer.parseInt(key.substring(key.indexOf(':') + 1));
		return (key.startsWith(SATA_KEY) ? CaseMenu.SATA_SLOTS : CaseMenu.EXP_SLOTS) + lane;
	}

	/** Where a part sits, as the bus order panel says it: "ROM", "UPS", "SATA 1", "EXP 2". */
	private static Component partLocation(String key) {
		if (key.equals(EEPROM_KEY)) {
			return Component.translatable("gui.modularcomputers.bus.rom");
		} else if (key.equals(UPS_KEY)) {
			return Component.translatable("gui.modularcomputers.bus.ups");
		}
		int lane = Integer.parseInt(key.substring(key.indexOf(':') + 1));
		return Component.translatable(key.startsWith(SATA_KEY) ? "gui.modularcomputers.bus.sata" : "gui.modularcomputers.bus.expansion", lane + 1);
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

	/** Server side: the player plugged a cabled device in or out in the bus order panel. */
	public boolean setConnected(String key, boolean connected) {
		if (!key.startsWith(CABLE_KEY) || !busOrder.setConnected(key, connected)) {
			return false;
		}
		rebuildBus();
		setChanged();
		syncToClients();
		return true;
	}

	/**
	 * Server side, every tick: looks along the cables again when one changed (or now and then,
	 * for chunks loading and unloading), and sends the display to clients when it changed.
	 */
	void serverTick() {
		ticks++;
		if (seenCableChanges != Cabling.changes() || ticks % RESCAN_TICKS == 0) {
			seenCableChanges = Cabling.changes();
			scanCables();
		}
		if (display.buffer().changes() != syncedDisplay) {
			syncedDisplay = display.buffer().changes();
			setChanged();
			syncToClients();
		}
	}

	/** Finds the screens wired to this computer and puts them on the bus. */
	private void scanCables() {
		List<Cabled> found = new ArrayList<>();
		Map<String, Display> displays = new LinkedHashMap<>();
		for (CableWalk.Found device : Cabling.walk(level, worldPosition)) {
			if (level.getBlockEntity(device.pos()) instanceof ScreenBlockEntity screen) {
				String key = CABLE_KEY + screen.id();
				if (!displays.containsKey(key)) {
					found.add(new Cabled(key, device.pos(), device.meters()));
					displays.put(key, screen.display());
				}
			}
		}
		boolean sameDevices = found.equals(cabled) && displays.keySet().equals(cabledDisplays.keySet())
			&& displays.entrySet().stream().allMatch(entry -> cabledDisplays.get(entry.getKey()) == entry.getValue());
		if (sameDevices) {
			return;
		}
		cabled = List.copyOf(found);
		cabledDisplays.clear();
		cabledDisplays.putAll(displays);
		rebuildBus();
		setChanged();
		syncToClients();
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
		boolean hasEeprom = parts.getItem(CaseMenu.EEPROM_SLOT).is(ModItems.EEPROM);
		eeprom = hasEeprom ? (eeprom != null ? eeprom : new Eeprom(new HelloWorldProgram())) : null;
		board.setEeprom(eeprom);
		boolean hasUps = parts.getItem(CaseMenu.UPS_SLOT).is(ModItems.UPS_BATTERY);
		ups = hasUps ? (ups != null ? ups : new UpsBattery()) : null;
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			boolean disk = parts.getItem(CaseMenu.SATA_SLOTS + lane).is(ModItems.HARD_DISK);
			disks[lane] = disk ? (disks[lane] != null ? disks[lane] : new Disk()) : null;
			CardKind kind = ModItems.cardKind(parts.getItem(CaseMenu.EXP_SLOTS + lane)).orElse(null);
			cards[lane] = kind == null ? null : cards[lane] != null && cards[lane].kind() == kind ? cards[lane] : new Card(kind);
		}
		rebuildBus();
	}

	/**
	 * Puts the bus's devices in the player's order; newly attached ones go last. Cabled devices
	 * that went away keep their place for when they come back, and disconnected ones stay off
	 * the bus.
	 */
	private void rebuildBus() {
		Map<String, Device> attached = new LinkedHashMap<>();
		attached.put(DISPLAY_KEY, display);
		attached.put(KEYBOARD_KEY, keyboard);
		if (eeprom != null) {
			attached.put(EEPROM_KEY, eeprom);
		}
		if (ups != null) {
			attached.put(UPS_KEY, ups);
		}
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
		for (Cabled device : cabled) {
			// Server side, a device saved as cabled is only on the bus once a scan finds it again.
			attached.put(device.key(), cabledDisplays.get(device.key()));
		}
		busOrder.sync(List.copyOf(attached.keySet()), key -> key.startsWith(CABLE_KEY));
		List<Device> devices = busOrder.connected().stream().map(attached::get).filter(Objects::nonNull).toList();
		if (level != null && level.isClientSide()) {
			bus.setDevices(devices); // clients show what the server sends; they don't run devices
		} else {
			machine.setDevices(devices);
		}
	}

	/** What each slot held last time, so a change can play the right insert or remove sound. */
	private final ItemStack[] lastParts = new ItemStack[CaseMenu.PART_SLOTS];

	private void playPartSounds() {
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			boolean had = lastParts[slot] != null && !lastParts[slot].isEmpty();
			boolean has = !parts.getItem(slot).isEmpty();
			if (has != had) {
				level.playSound(null, worldPosition, partSound(slot, has), SoundSource.BLOCKS, 0.8f, 1.0f);
			}
		}
		rememberParts();
	}

	private void rememberParts() {
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			lastParts[slot] = parts.getItem(slot).copy();
		}
	}

	private static net.minecraft.sounds.SoundEvent partSound(int slot, boolean inserted) {
		if (slot == CaseMenu.CPU_SLOT) {
			return inserted ? ModSounds.CPU_INSERT : ModSounds.CPU_REMOVE;
		} else if (slot == CaseMenu.EEPROM_SLOT) {
			return inserted ? ModSounds.EEPROM_INSERT : ModSounds.EEPROM_REMOVE;
		} else if (slot == CaseMenu.UPS_SLOT) {
			return inserted ? ModSounds.BATTERY_INSERT : ModSounds.BATTERY_REMOVE;
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return inserted ? ModSounds.RAM_INSERT : ModSounds.RAM_REMOVE;
		} else if (slot < CaseMenu.EXP_SLOTS) {
			return inserted ? ModSounds.DISK_INSERT : ModSounds.DISK_REMOVE;
		}
		return inserted ? ModSounds.CARD_INSERT : ModSounds.CARD_REMOVE;
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
		playPartSounds();
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
		output.store(BUS_ORDER, Codec.STRING.listOf(), busOrder.all());
		output.store(BUS_DISCONNECTED, Codec.STRING.listOf(), busOrder.disconnected());
		output.store(CABLED, Cabled.CODEC.listOf(), cabled);
		output.store(DISPLAY, Codec.STRING.listOf(), display.buffer().rows());
		output.putIntArray(FOREGROUND, display.buffer().foregrounds());
		output.putIntArray(BACKGROUND, display.buffer().backgrounds());
		ConsoleSplitter console = machine.board().virtualBus().console();
		output.putIntArray(CONSOLE, new int[] {console.column(), console.row(), console.foreground(), console.background()});
		output.store(CONSOLE_SCREEN, Codec.STRING.listOf(), console.screen().rows());
		output.putIntArray(CONSOLE_FOREGROUND, console.screen().foregrounds());
		output.putIntArray(CONSOLE_BACKGROUND, console.screen().backgrounds());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		machine.restorePowered(input.getBooleanOr(POWERED, false));
		parts.getItems().clear(); // not clearContent(): that would fire change handling mid-load
		ContainerHelper.loadAllItems(input, parts.getItems());
		input.read(BUS_ORDER, Codec.STRING.listOf()).ifPresent(order ->
			busOrder.restore(order, input.read(BUS_DISCONNECTED, Codec.STRING.listOf()).orElse(List.of())));
		cabled = input.read(CABLED, Cabled.CODEC.listOf()).map(List::copyOf).orElse(List.of());
		installParts();
		rememberParts(); // loading isn't inserting: no sounds
		input.read(DISPLAY, Codec.STRING.listOf()).ifPresent(display.buffer()::setRows);
		input.getIntArray(FOREGROUND).ifPresent(foreground ->
			input.getIntArray(BACKGROUND).ifPresent(background -> display.buffer().setColors(foreground, background)));
		ConsoleSplitter console = machine.board().virtualBus().console();
		input.getIntArray(CONSOLE).filter(state -> state.length == 4).ifPresent(state ->
			console.restore(state[0], state[1], state[2], state[3]));
		input.read(CONSOLE_SCREEN, Codec.STRING.listOf()).ifPresent(console.screen()::setRows);
		input.getIntArray(CONSOLE_FOREGROUND).ifPresent(foreground ->
			input.getIntArray(CONSOLE_BACKGROUND).ifPresent(background -> console.screen().setColors(foreground, background)));
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
