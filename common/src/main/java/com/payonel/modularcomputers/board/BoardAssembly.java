package com.payonel.modularcomputers.board;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payonel.modularcomputers.block.CardWorld;
import com.payonel.modularcomputers.block.DeviceSave;
import com.payonel.modularcomputers.block.OperatorCommands;
import com.payonel.modularcomputers.block.ScreenSave;
import com.payonel.modularcomputers.block.TerminalBell;
import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.BusStatus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Card;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.CpuItem;
import com.payonel.modularcomputers.item.DiskItem;
import com.payonel.modularcomputers.item.MemoryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.item.ScreenItem;
import com.payonel.modularcomputers.machine.BoardLayout;
import com.payonel.modularcomputers.machine.Chassis;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.Shutdown;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.BatteryTier;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.runtime.Board;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A board assembly (a PCBA: the motherboard with its parts in), whatever holds it ({@link BoardHost}:
 * a case, a golem): the board whose part
 * slots (CPU, EEPROM, battery bays, RAM, SATA drives, expansion cards, as {@link CaseMenu} numbers
 * them, those its {@link BoardLayout} has) are filled from its parts view, its terminal (a screen in
 * its gold slot, or one built in), its power buffer topped up from its batteries, and its bus: the
 * parts, then the external devices its host finds (a case's cables), in an order the player can
 * change, where external devices can also be disconnected. Its parts, power, bus order, program,
 * devices' data and screens are saved with its host.
 */
public final class BoardAssembly {
	private static final String POWERED = "powered";
	/** Why it last stopped (the board's record: {@link Shutdown}). */
	private static final String SHUTDOWN_CAUSE = "shutdown_cause";
	private static final String SHUTDOWN_MESSAGE = "shutdown_message";
	private static final String SHUTDOWN_TIME = "shutdown_time";
	/** The CPU stack installed: another in its slot is another CPU. */
	private ItemStack installedCpu;
	private static final String DISPLAY = "display";
	private static final String CONSOLE = "console";
	/** The saved external bus order. */
	private static final String BUS_EXTERNAL = "bus_external";
	private static final String KNOWN = "known";
	private static final Codec<BusOrder.Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("key").forGetter(BusOrder.Entry::key),
		Codec.LONG.fieldOf("last_seen").forGetter(BusOrder.Entry::lastSeen)
	).apply(instance, BusOrder.Entry::new));
	private static final String BUS_DISCONNECTED = "bus_disconnected";
	private static final String CABLED = "cabled";
	private static final String POWER_BUFFER = "power_buffer";
	/** A test's bus capacity in place of the CPU's, saved so clients see it too. */
	private static final String BUS_CAPACITY_OVERRIDE = "bus_capacity_override";
	public static final String BOARD_ID = "board_id";
	/** How often the battery is run down or recharged, in ticks. */
	private static final int BATTERY_TICKS = 20;
	private static final float ERROR_BEEP_VOLUME = 0.6f;
	/** The slots whose parts are bus devices: each gets a serial number, its id. */
	private static final int[] BUS_PART_SLOTS = {CaseMenu.EEPROM_SLOT,
		CaseMenu.SATA_SLOTS, CaseMenu.SATA_SLOTS + 1, CaseMenu.SATA_SLOTS + 2, CaseMenu.SATA_SLOTS + 3,
		CaseMenu.EXP_SLOTS, CaseMenu.EXP_SLOTS + 1, CaseMenu.EXP_SLOTS + 2, CaseMenu.EXP_SLOTS + 3};
	private static final String PROGRAM = "program";
	/** The machine's clock ({@link Machine#uptime}), saved so it goes on after a load. */
	private static final String UPTIME = "uptime";
	/** A card's saved state, by lane ({@link DeviceSave}). */
	private static final String CARD = "card_";
	/** A disk's reports of changes, by lane ({@link DeviceSave}). */
	private static final String DISK = "disk_";

	/** The bus panel's icon for a robotics device (case_icons.png's ninth). */
	public static final int ROBOTICS_ICON = 8;

	private final BoardHost host;
	private final BoardLayout layout;
	/**
	 * Its terminal: the screen in its gold expansion slot (a new one when a screen of another tier
	 * goes in). Without one it's off the bus.
	 */
	private Terminal display;
	/** Its terminal's bell, where it is. */
	private final TerminalBell bell = new TerminalBell();
	/** Devices made from the parts in the slots (by lane for drives and cards); null when empty. */
	private final Disk[] disks = new Disk[CaseMenu.LANES];
	/** The serials the disks were made for: another disk is another device, with its own data. */
	private final UUID[] diskSerials = new UUID[CaseMenu.LANES];
	private final Card[] cards = new Card[CaseMenu.LANES];
	/** Devices soldered onto connectors (a golem's robotics device): always there, never a part. */
	private final Map<String, Device> soldered = new LinkedHashMap<>();
	private Eeprom eeprom;
	/** The serial of the EEPROM {@link #eeprom} was made for: another EEPROM is another device. */
	private UUID eepromSerial;
	/** The EEPROM's storage: the item in the EEPROM slot, read and written in place (not a part change). */
	private final Eeprom.Storage eepromSlot = new Eeprom.Storage() {
		@Override
		public String read() {
			return parts.getItem(CaseMenu.EEPROM_SLOT).getOrDefault(ModComponents.EEPROM_IMAGE, "");
		}

		@Override
		public void write(String image) {
			parts.getItem(CaseMenu.EEPROM_SLOT).set(ModComponents.EEPROM_IMAGE, image);
			host.setChanged();
		}

		@Override
		public com.payonel.modularcomputers.machine.BootVariables nvram() {
			return parts.getItem(CaseMenu.EEPROM_SLOT).getOrDefault(ModComponents.NVRAM, com.payonel.modularcomputers.machine.BootVariables.EMPTY);
		}

		@Override
		public void nvram(com.payonel.modularcomputers.machine.BootVariables vars) {
			if (vars.equals(com.payonel.modularcomputers.machine.BootVariables.EMPTY)) {
				parts.getItem(CaseMenu.EEPROM_SLOT).remove(ModComponents.NVRAM); // (an empty chip stacks with a new one)
			} else {
				parts.getItem(CaseMenu.EEPROM_SLOT).set(ModComponents.NVRAM, vars);
			}
			host.setChanged();
		}

		@Override
		public String label() {
			return ModComponents.label(parts.getItem(CaseMenu.EEPROM_SLOT));
		}

		@Override
		public boolean relabel(String label) {
			return ModComponents.labelOn(() -> parts.getItem(CaseMenu.EEPROM_SLOT), host::setChanged).set(label);
		}
	};
	private final Bus bus = new Bus(); // empty until a CPU is in
	private final PowerBuffer power;
	/** The motherboard's id: what Bluetooth cards link to. Made when the computer is made, and saved. */
	private UUID boardId = UUID.randomUUID();
	/**
	 * The buffer's level and the computer's load (what it draws now) for the parts view, which sends
	 * them to the player as they change. Menu data goes as shorts, so each is two: the low 16 bits,
	 * then the high ({@link CaseMenu#powerLevel}, {@link CaseMenu#load}).
	 */
	private final ContainerData powerData = new ContainerData() {
		@Override
		public int get(int index) {
			int value = index < 2 ? power.level() : (int) Math.min(Integer.MAX_VALUE, machine.currentDraw());
			return index % 2 == 0 ? value & 0xFFFF : value >>> 16;
		}

		@Override
		public void set(int index, int value) {
			if (index < 2) { // the load is only ever read from the machine
				int old = power.level();
				power.set(index % 2 == 0 ? old & ~0xFFFF | value & 0xFFFF : (value & 0xFFFF) << 16 | old & 0xFFFF);
			}
		}

		@Override
		public int getCount() {
			return CaseMenu.POWER_DATA;
		}
	};
	/** The power clients last saw, in percent: the meter and its time left. */
	private int syncedPowerPercent;
	private final Machine machine;
	private final Parts parts = new Parts();
	/** The external devices found last time, nearest first. Saved and synced, for the bus order panel. */
	private List<Cabled> cabled = List.of();
	/**
	 * What was last seen of each external device in the saved bus order, online or not: an
	 * offline one's row still shows its name, icon and where it was.
	 */
	private final Map<String, Cabled> known = new LinkedHashMap<>();
	/** Server side: the external devices, by key. */
	private final Map<String, Device> cabledDevices = new LinkedHashMap<>();
	private int ticks;
	/** A running program's saved state, from a load, to go on with once the bus is found again. */
	private byte[] savedProgram;
	/** What each slot held last time, so a change can play the right insert or remove sound. */
	private final ItemStack[] lastParts = new ItemStack[CaseMenu.PART_SLOTS];

	/**
	 * A device wired to this computer: where it is, how far the connection runs to it in meters
	 * (blocks), whether the way crosses a Bluetooth link, whether it's paired with this computer,
	 * the item it is (for its name and icon), its slot when it's a card in an expansion hub
	 * (else -1), and the device it's behind (its host's key, a Disk Station's for its disks; else
	 * empty): on the bus only while its host is ({@link BusOrder}).
	 */
	public record Cabled(String key, BlockPos pos, int meters, boolean wireless, boolean paired, Item item, int hubSlot, String host) {
		/** A device behind nothing. */
		public Cabled(String key, BlockPos pos, int meters, boolean wireless, boolean paired, Item item, int hubSlot) {
			this(key, pos, meters, wireless, paired, item, hubSlot, "");
		}

		static final Codec<Cabled> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("key").forGetter(Cabled::key),
			BlockPos.CODEC.fieldOf("pos").forGetter(Cabled::pos),
			Codec.INT.fieldOf("meters").forGetter(Cabled::meters),
			Codec.BOOL.fieldOf("wireless").forGetter(Cabled::wireless),
			Codec.BOOL.fieldOf("paired").forGetter(Cabled::paired),
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Cabled::item),
			Codec.INT.fieldOf("hub_slot").forGetter(Cabled::hubSlot),
			Codec.STRING.optionalFieldOf("host", "").forGetter(Cabled::host)
		).apply(instance, Cabled::new));
	}

	/**
	 * One line of the bus order panel. {@code item} and {@code slot} are the part's item and
	 * slot, or empty and -1 for a built-in device. External devices have a {@code device}
	 * position (the bulb finds them in the world) and can be disconnected.
	 */
	public record BusEntry(String key, Component name, Component location, ItemStack item, int slot, int builtInIcon,
		Optional<BlockPos> device, boolean connected, boolean online, int position) {
		/** An external device: it can be reordered, identified, unplugged, and forgotten while offline. */
		public boolean cabled() {
			return device.isPresent();
		}
	}

	/** A board held by {@code host}, with {@code layout}'s slots; its terminal starts at {@code screen}'s tier, until a screen goes in. */
	public BoardAssembly(BoardHost host, BoardLayout layout, TerminalTier screen) {
		this.host = host;
		this.layout = layout;
		this.power = new PowerBuffer(layout.bufferCapacity());
		this.machine = new Machine(new Motherboard(Hardware.computer(), bus), this::hasPower);
		display = newDisplay(screen);
		machine.board().setChassis(new BoardChassis());
		machine.describedBy(host::describe);
		machine.wakeWith(this::pressPower); // a network card's wake presses its power button: it clicks and lights
		// Restore on AC power loss: its EEPROM's setting, read from the chip in its slot when power comes back.
		machine.restoreOnPowerLossWith(() -> ModItems.isEeprom(parts.getItem(CaseMenu.EEPROM_SLOT))
			&& parts.getItem(CaseMenu.EEPROM_SLOT).getOrDefault(ModComponents.NVRAM, com.payonel.modularcomputers.machine.BootVariables.EMPTY).restoreOnPowerLoss());
	}

	private Terminal newDisplay(TerminalTier tier) {
		Terminal made = new Terminal(tier);
		made.setFlusher(() -> host.syncDisplay(display));
		made.onChange(host::markChanged);
		made.onBell(() -> bell.ring(host.level(), host.pos()));
		return made;
	}

	/** What the board's firmware reads from its chassis (ACPI and SMBIOS): its tier, power and RAM. */
	private final class BoardChassis implements Chassis {
		@Override
		public int tier() {
			return layout.number();
		}

		@Override
		public Board.PowerInfo power() {
			List<Board.BatteryInfo> batteries = new ArrayList<>();
			for (int bay = 0; bay < CaseMenu.UPS_BAYS; bay++) {
				ItemStack stack = parts.getItem(CaseMenu.UPS_SLOT + bay);
				if (stack.getItem() instanceof BatteryItem battery) {
					BatteryTier batteryTier = battery.tier();
					Board.Store charge = batteryTier.infinite()
						? new Board.Store(0, 0, true)
						: new Board.Store(BatteryItem.charge(stack).units(), batteryTier.capacity(), false);
					batteries.add(new Board.BatteryInfo(bay, batteryTier.ordinal() + 1, charge));
				}
			}
			return new Board.PowerInfo(new Board.Store(power.level(), power.capacity(), false), batteries, machine.currentDraw());
		}

		@Override
		public long time() {
			return host.level() == null ? 0 : host.level().getGameTime();
		}

		@Override
		public List<Board.MemoryInfo> memory() {
			List<Board.MemoryInfo> memory = new ArrayList<>();
			for (int slot = 0; slot < CaseMenu.SATA_SLOTS - CaseMenu.RAM_SLOTS; slot++) {
				if (parts.getItem(CaseMenu.RAM_SLOTS + slot).getItem() instanceof MemoryItem stick) {
					memory.add(new Board.MemoryInfo(slot, stick.tier().number(), stick.tier().bytes()));
				}
			}
			return memory;
		}
	}

	public BoardLayout layout() {
		return layout;
	}

	/** Solders {@code device} onto a connector: always on the bus there, never a part (a golem's robotics device). */
	public void solder(String connector, Device device) {
		soldered.put(connector, device);
		installParts();
	}

	/** A screen is in the gold expansion slot. */
	public boolean screenInSlot() {
		return CaseMenu.isScreenSlot(layout, CaseMenu.EXP_SLOTS) && parts.getItem(CaseMenu.EXP_SLOTS).getItem() instanceof ScreenItem;
	}

	/** The motherboard's bus order. */
	public BusOrder busOrder() {
		return machine.board().order();
	}

	public Machine machine() {
		return machine;
	}

	/** Its terminal: the screen in its gold slot. */
	public Terminal terminal() {
		return display;
	}

	/** The part slots (see {@link CaseMenu} for which is which). */
	public Container parts() {
		return parts;
	}

	/** The power level and load, for its parts view. */
	public ContainerData powerData() {
		return powerData;
	}

	/** Its own store of power, which the charge meter shows. */
	public PowerBuffer power() {
		return power;
	}

	/** Its board's id, which Bluetooth cards link to. */
	public UUID boardId() {
		return boardId;
	}

	/** Its board's id, from where it was kept (its item). */
	public void restoreBoardId(UUID id) {
		boardId = id;
	}

	/** Its cards, by lane (null where there's none). */
	public List<Card> cards() {
		return java.util.Arrays.stream(cards).filter(java.util.Objects::nonNull).toList();
	}

	/** Its Bluetooth cards (in the expansion slots), in bus order. */
	public List<ItemStack> bluetoothCards() {
		List<ItemStack> found = new ArrayList<>();
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			ItemStack card = parts.getItem(CaseMenu.EXP_SLOTS + lane);
			if (card.is(ModItems.BLUETOOTH_CARD)) {
				found.add(card);
			}
		}
		return found;
	}

	/** The devices on the bus, in order, as the bus order panel shows them. Works on clients too. */
	public List<BusEntry> busEntries() {
		List<BusEntry> entries = new ArrayList<>();
		if (machine.board().cpu().isEmpty()) {
			return entries; // no CPU, no bus
		}
		BusOrder busOrder = busOrder();
		Set<String> onBus = Set.copyOf(busOrder.connected());
		for (String key : busOrder.keys()) {
			int position = machine.board().position(key);
			// What's there but left off because the bus is full isn't listed (the summary counts it);
			// offline and unplugged external devices are, to plug back in or forget.
			boolean waiting = !onBus.contains(key) && (busOrder.isInternal(key) || busOrder.isOnline(key) && busOrder.isConnected(key));
			if (waiting) {
				continue;
			}
			if (!busOrder.isInternal(key)) {
				Optional.ofNullable(known.get(key)).ifPresent(device -> {
					ItemStack item = new ItemStack(device.item());
					boolean online = busOrder.isOnline(key);
					Component location;
					if (!online) {
						location = Component.translatable("gui.modularcomputers.bus.offline");
					} else if (device.paired()) {
						location = device.hubSlot() >= 0 ? Component.translatable("gui.modularcomputers.bus.hub_paired", device.hubSlot() + 1)
							: Component.translatable("gui.modularcomputers.bus.paired");
					} else {
						location = device.hubSlot() >= 0
							? Component.translatable("gui.modularcomputers.bus.hub", device.hubSlot() + 1, device.meters())
							: Component.translatable(device.wireless() ? "gui.modularcomputers.bus.bluetooth" : "gui.modularcomputers.bus.cable", device.meters());
					}
					entries.add(new BusEntry(key, item.getHoverName(), location, item, -1, -1, Optional.of(device.pos()), busOrder.isConnected(key), online, position));
				});
			} else if (soldered.containsKey(key)) {
				// A soldered device is on the bus like any (as lspci lists a board's own), its place its connector's.
				entries.add(new BusEntry(key, Component.translatable("gui.modularcomputers.bus.robotics"),
					Component.translatable("gui.modularcomputers.bus.soldered", Motherboard.lane(key) + 1), ItemStack.EMPTY, -1, ROBOTICS_ICON,
					Optional.empty(), true, true, position));
			} else {
				int slot = partSlot(key);
				ItemStack item = parts.getItem(slot);
				entries.add(new BusEntry(key, item.getHoverName(), partLocation(key), item, slot, -1, Optional.empty(), true, true, position));
			}
		}
		return entries;
	}

	/** The slot holding the part on a motherboard connector. */
	private static int partSlot(String connector) {
		if (connector.equals(Motherboard.EEPROM)) {
			return CaseMenu.EEPROM_SLOT;
		}
		return (Motherboard.isSata(connector) ? CaseMenu.SATA_SLOTS : CaseMenu.EXP_SLOTS) + Motherboard.lane(connector);
	}

	/** Where a part sits, as the bus order panel says it: "ROM", "SATA 1", "EXP 2". */
	private static Component partLocation(String connector) {
		if (connector.equals(Motherboard.EEPROM)) {
			return Component.translatable("gui.modularcomputers.bus.rom");
		}
		return Component.translatable(Motherboard.isSata(connector) ? "gui.modularcomputers.bus.sata" : "gui.modularcomputers.bus.expansion",
			Motherboard.lane(connector) + 1);
	}

	/** How full the bus is, and whether devices are waiting for room (works on clients too). */
	public BusStatus busStatus() {
		return busOrder().status();
	}

	/** Server side: the player forgot an offline device in the bus order panel. */
	public boolean forgetDevice(String key) {
		if (!busOrder().forget(key)) {
			return false;
		}
		known.remove(key);
		rebuildBus();
		host.setChanged();
		syncToClients();
		return true;
	}

	/** Server side: the player moved device {@code key} up or down the bus order. */
	public boolean moveDevice(String key, boolean up) {
		int index = busOrder().keys().indexOf(key);
		if (index < 0 || !busOrder().move(index, up)) {
			return false;
		}
		rebuildBus();
		host.setChanged();
		syncToClients();
		return true;
	}

	/** Server side: the player plugged an external device in or out in the bus order panel. */
	public boolean setConnected(String key, boolean connected) {
		Set<String> before = externalOnBus();
		if (!busOrder().setConnected(key, connected)) { // external devices only
			return false;
		}
		rebuildBus();
		busSounds(before, busOrder().waiting());
		host.setChanged();
		syncToClients();
		return true;
	}

	/** It has a world now (a block loaded, an entity added): on a server, its calls go to the server thread, and its disks find their data. */
	public void placed() {
		if (host.level() instanceof ServerLevel server) {
			// A Lua CPU's calls are carried out on the server thread, as soon as it can: always queued for it.
			// (The server's own execute runs a task right there, on the CPU's thread, once the server is
			// stopping: the task then touched the world off its thread and waited on a chunk the stopping
			// server was saving, while the save waited for this computer: neither ever went on.)
			var minecraft = server.getServer();
			machine.setServer(task -> minecraft.schedule(minecraft.wrapRunnable(task)));
			// Loaded before it had a level: its disks find their data now.
			java.util.Arrays.fill(diskSerials, null);
			installParts();
		}
	}

	/** Destroyed (a block broken, a golem killed): a power cut. */
	public void removed() {
		machine.removed();
		PartHolds.releaseAll(this);
	}

	/** Unloaded (or destroyed): its program was saved, so it stops here, and goes on after a load. */
	public void unloaded() {
		machine.unload();
		PartHolds.releaseAll(this); // (its parts are where it is, unloaded: a copy may have them now; it waits for them when it's back)
		for (int lane = 0; lane < disks.length; lane++) {
			retireDisk(lane);
		}
	}

	/** The external devices on the bus now. */
	private Set<String> externalOnBus() {
		return busOrder().connected().stream().filter(key -> !busOrder().isInternal(key)).collect(Collectors.toSet());
	}

	/**
	 * The bus's sounds after a change: a soft hum when an external device joined it (wired up,
	 * plugged back in, or its chunk loaded), and a deeper, rougher one when one has to wait
	 * because the bus is full. Leaving is silent.
	 */
	private void busSounds(Set<String> before, int waitingBefore) {
		Level level = host.level();
		if (level == null || level.isClientSide()) {
			return;
		}
		if (busOrder().waiting() > waitingBefore) {
			level.playSound(null, host.pos(), ModSounds.BUS_OVERFLOW, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		if (!before.containsAll(externalOnBus())) {
			level.playSound(null, host.pos(), ModSounds.DEVICE_CONNECT, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
	}

	/**
	 * Server side, every tick: its battery now and then, its cards' work where it is, the
	 * motherboard's checks and the program's turn, a saved program resumed, and its display sent.
	 */
	public void tick() {
		ServerLevel level = (ServerLevel) host.level();
		ticks++;
		if (waiting && seenPartHolds != PartHolds.version()) {
			installParts(); // (a part one of its slots waited for was let go)
		}
		if (ticks % BATTERY_TICKS == 0) {
			updateBattery();
		}
		for (Card card : cards) {
			if (card != null) {
				CardWorld.tick(card, level, host.pos(), frame(), level.getGameTime()); // cards work where they're installed
			}
		}
		// Minecraft writes only what's marked changed (at a save, or an unload): an on machine changes
		// every tick (its clock, its program). Its devices mark it themselves as their data changes.
		if (machine.isPowered()) {
			host.markChanged();
		}
		// The motherboard checks its CPU and power every tick, and the program gets its turn.
		Machine.Supervision supervision = machine.tick();
		if (supervision != Machine.Supervision.OK) {
			play(dyingSound(supervision));
		}
		showPowerState(); // the CPU stopped (or started), or the program turned it off: the light changes
		if (savedProgram != null) {
			// Loaded running: the program goes on where it was saved, now its bus is found again.
			byte[] saved = savedProgram;
			savedProgram = null;
			machine.resumeProgram(saved);
		} else if (unsavedProgram) {
			unsavedProgram = false;
			machine.programUnsaved();
			showPowerState();
		}
		host.syncDisplay(display);
	}

	/** The computer runs on its power buffer. */
	private boolean hasPower() {
		return !power.isEmpty();
	}

	/**
	 * The low-power alarm, like a UPS on its last minutes: under half its buffer, with a load, it
	 * beeps at every battery check (each second). The batteries keep it full, so this means
	 * they're out or flat.
	 */
	public boolean lowPowerAlarm() {
		return power.isLow() && machine.currentDraw() > 0;
	}

	/**
	 * Server side, now and then: a running computer draws from its buffer, and the buffer tops
	 * itself up from the battery. A self-charging battery recharges while the computer is off.
	 * With the battery out or flat, the buffer runs down; when it's empty a running computer dies
	 * with an error beep. Time it wasn't loaded doesn't count.
	 */
	public void updateBattery() {
		// The CPU while it runs a program, and the bus devices it powers (see Machine.powerUse).
		long used = machine.powerUse(host.level().getGameTime());
		power.use(used);
		pullFromBattery();
		if (machine.powerLost()) {
			play(dyingSound(Machine.Supervision.LOST_POWER));
		} else if (lowPowerAlarm()) {
			errorBeep();
		}
		host.setChanged();
		if (!showPowerState() && power.percent() != syncedPowerPercent) {
			syncToClients();
		}
	}

	/**
	 * The buffer takes what it's missing from the batteries, up to a day's worth: from the last
	 * bay first, the first bay last (user's order). While the computer runs, its batteries are in
	 * use: no self-charging; while it's off, a self-charging one rests.
	 */
	private void pullFromBattery() {
		long now = host.level().getGameTime();
		for (int bay = CaseMenu.UPS_BAYS - 1; bay >= 0; bay--) {
			ItemStack stack = parts.getItem(CaseMenu.UPS_SLOT + bay);
			if (!(stack.getItem() instanceof BatteryItem battery)) {
				continue;
			}
			if (battery.tier().infinite()) {
				power.fill(power.capacity());
				continue;
			}
			BatteryCharge charge = BatteryItem.charge(stack);
			charge = machine.isPowered() ? charge.run(battery.tier(), 0, now) : charge.rest(battery.tier(), now);
			charge = charge.run(battery.tier(), power.fill(charge.units()), now);
			stack.set(ModComponents.CHARGE, charge); // in place: not a part change
		}
	}

	/** Whether it could run now: a CPU, and power. */
	public boolean ready() {
		return machine.board().cpu().isPresent() && hasPower();
	}

	/** Shows its state on its host (a case's power button); whether that changed. */
	public boolean showPowerState() {
		boolean changed = host.showPowerState(ready(), machine.isPowered(), machine.isAborted(), screenInSlot());
		if (changed) {
			syncedPowerPercent = power.percent();
		}
		return changed;
	}

	/** Server side: what its host found wired to it now (a case's cables), by key. */
	public void setExternal(List<Cabled> found, Map<String, Device> devices) {
		boolean same = found.equals(cabled) && devices.keySet().equals(cabledDevices.keySet())
			&& devices.entrySet().stream().allMatch(entry -> cabledDevices.get(entry.getKey()) == entry.getValue());
		if (same) {
			return;
		}
		cabled = List.copyOf(found);
		cabledDevices.clear();
		cabledDevices.putAll(devices);
		relayout(true); // (what was found changed: distances and places too)
	}

	/** Lays the bus out again, with the join and overflow sounds, and tells clients when something changed. */
	public void relayout(boolean changed) {
		Set<String> before = externalOnBus();
		int waitingBefore = busOrder().waiting();
		rebuildBus();
		if (changed || !before.equals(externalOnBus()) || waitingBefore != busOrder().waiting()) {
			busSounds(before, waitingBefore);
			host.setChanged();
			syncToClients();
		}
	}

	/**
	 * Server side: someone pressed the power button. It clicks either way. Without power nothing
	 * else happens; without a CPU the machine stays off and beeps an error.
	 */
	public Machine.PowerPress pressPower() {
		Machine.PowerPress result = machine.pressPower();
		showPowerState(); // what the press came to, an abort in it too (no EEPROM: off at once, blinking), for every holder
		host.setChanged();
		Level level = host.level();
		if (level == null) {
			return result;
		}
		boolean on = result == Machine.PowerPress.ON;
		level.playSound(null, host.pos(), on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
			SoundSource.BLOCKS, 0.3f, on ? 0.6f : 0.5f);
		if (result == Machine.PowerPress.NO_CPU) {
			errorBeep();
		}
		return result;
	}

	/** One short, low buzz: an error, not a confirmation. */
	private void errorBeep() {
		play(ModSounds.ERROR_BEEP);
	}

	private void play(SoundEvent sound) {
		host.level().playSound(null, host.pos(), sound, SoundSource.BLOCKS, ERROR_BEEP_VOLUME, 1.0f);
	}

	/**
	 * What a running computer sounds as it dies: running out of power, the beep sagging deeper and
	 * fading out (user, 2026-10-11); anything else (the CPU pulled), the error beep.
	 */
	public static SoundEvent dyingSound(Machine.Supervision cause) {
		return cause == Machine.Supervision.LOST_POWER ? ModSounds.POWER_DOWN : ModSounds.ERROR_BEEP;
	}

	/** A card that works here: a redstone card's output changes tell its host's neighbours. */
	/** Its computer's frame, where its cards say things are: a robot's own (its heading, its own right), else its block's. */
	private com.payonel.modularcomputers.entity.FieldProjector.Frame frame() {
		return host.robot() != null ? com.payonel.modularcomputers.entity.FieldProjector.Frame.of(host.robot())
			: com.payonel.modularcomputers.entity.FieldProjector.Frame.of(net.minecraft.world.phys.Vec3.atCenterOf(host.pos()), host.front());
	}

	private Card cardHere(CardKind kind) {
		Card card = com.payonel.modularcomputers.device.Cards.make(kind);
		if (card instanceof com.payonel.modularcomputers.device.OperatorCard operator) {
			operator.runWith(command -> host.level() instanceof ServerLevel server
				? OperatorCommands.run(server, host.pos(), command)
				: new com.payonel.modularcomputers.device.OperatorCard.Outcome(false, 0, "not in a world"));
		}
		card.onChange(host::markChanged);
		card.frontFrom(() -> com.payonel.modularcomputers.device.Face.of(host.front().getName()));
		if (card instanceof com.payonel.modularcomputers.device.FieldCard field) {
			field.projectorIn(new com.payonel.modularcomputers.entity.FieldProjector(field, () -> (ServerLevel) host.level(), this::frame, host::robot));
		}
		if (card instanceof com.payonel.modularcomputers.device.RedstoneCard redstone) {
			redstone.block(side -> CardWorld.redstone(cards(), side));
			redstone.onOutput(() -> {
				Level level = host.level();
				if (level != null && !level.isClientSide()) {
					host.redstoneChanged();
				}
				for (var other : cards) {
					if (other instanceof com.payonel.modularcomputers.device.RedstoneCard sibling && sibling != redstone) {
						sibling.report(); // what the block puts out changed: so may their levels
					}
				}
			});
		}
		return card;
	}

	/** The redstone its cards put out toward a neighbour ({@code direction}: from the neighbour to it). */
	public int redstoneOut(net.minecraft.core.Direction direction) {
		return CardWorld.redstone(cards(), direction);
	}

	private void retireDisk(int lane) {
		if (disks[lane] != null) {
			disks[lane].retire();
			disks[lane] = null;
		}
	}

	/**
	 * A disk that comes with files gets them the first time its folder is made (a computer's or a
	 * Disk Station's), made at the world's time {@code ticks}; then the mark goes. Whether it went.
	 */
	public static boolean imageDisk(ItemStack stack, java.nio.file.Path folder, long ticks) {
		String image = stack.get(ModComponents.DISK_IMAGE);
		if (image == null) {
			return false;
		}
		if (!java.nio.file.Files.exists(folder)) {
			try {
				com.payonel.modularcomputers.machine.LuaScripts.copyTree(image, folder);
				Disk.stampAll(folder, ticks); // (made now, in the world's time)
			} catch (RuntimeException e) {
				com.payonel.modularcomputers.ModularComputers.LOGGER.warn("A disk didn't get its {} files", image, e);
				return false;
			}
		}
		stack.remove(ModComponents.DISK_IMAGE);
		return true;
	}

	/**
	 * Where a disk's data lives: the world save's {@code modularcomputers/disks/<id>} (a folder
	 * for hfs, with ".img" for raw), so it's the disk's wherever it goes.
	 */
	public static java.nio.file.Path diskFolder(net.minecraft.server.MinecraftServer server, UUID id) {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
			.resolve("modularcomputers").resolve("disks").resolve(id.toString());
	}

	/**
	 * Takes the part {@code id} (server side; a client takes nothing): whether it's this board's, not
	 * one in another place ({@link PartHolds}: a creative copy waits, as a part that isn't there).
	 */
	private boolean mine(UUID id, Object server, Set<UUID> kept) {
		if (server == null || id == null) {
			return true;
		}
		if (PartHolds.claim(id, this, server)) {
			kept.add(id);
			return true;
		}
		waiting = true;
		return false;
	}

	/** A part in its slots is another's ({@link PartHolds}): it's looked at again when one is let go. */
	private boolean waiting;
	private long seenPartHolds = -1;

	/** Puts the parts in the slots onto the motherboard, and drives and cards onto the bus. */
	private void installParts() {
		Level level = host.level();
		Object server = level instanceof ServerLevel serverLevel ? serverLevel.getServer() : null;
		Set<UUID> kept = new java.util.HashSet<>();
		waiting = false;
		seenPartHolds = PartHolds.version();
		mine(boardId, server, kept); // (its board: what a copy of it would be placed as)
		Motherboard board = machine.board();
		ItemStack cpuStack = parts.getItem(CaseMenu.CPU_SLOT);
		CpuItem cpuItem = cpuStack.getItem() instanceof CpuItem cpu ? cpu : null;
		if (cpuItem == null) {
			board.setCpu(null);
		} else if (cpuStack != installedCpu || board.cpu().isEmpty()) {
			board.setCpu(new Cpu(cpuItem.tier())); // another CPU (even one like it): a running machine aborts
		}
		installedCpu = cpuItem == null ? null : cpuStack;
		// The EEPROM reads and writes the item in its slot directly (EepromSlot): no copy of its text.
		ItemStack stack = parts.getItem(CaseMenu.EEPROM_SLOT);
		UUID serial = ModItems.isEeprom(stack) ? stack.get(ModComponents.SERIAL) : null;
		boolean hasEeprom = ModItems.isEeprom(stack) && mine(serial, server, kept);
		if (!hasEeprom) {
			eeprom = null;
		} else if (eeprom == null || !java.util.Objects.equals(eepromSerial, serial)) {
			eeprom = new Eeprom(eepromSlot); // another EEPROM: another device
		}
		eepromSerial = serial;
		board.setEeprom(eeprom);
		// Each part answers to its serial number too: a card is found by it here or in a hub.
		board.plug(Motherboard.EEPROM, eeprom, serial(CaseMenu.EEPROM_SLOT));
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			UUID diskSerial = serial(CaseMenu.SATA_SLOTS + lane);
			DiskTier diskTier = parts.getItem(CaseMenu.SATA_SLOTS + lane).getItem() instanceof DiskItem disk && mine(diskSerial, server, kept) ? disk.tier() : null;
			ItemStack diskStack = parts.getItem(CaseMenu.SATA_SLOTS + lane);
			if (diskTier == null) {
				retireDisk(lane);
			} else if (disks[lane] == null || disks[lane].tier() != diskTier || !java.util.Objects.equals(diskSerials[lane], diskSerial)) {
				// Another disk: its own data, in the world save by its id (none on a client).
				retireDisk(lane);
				java.nio.file.Path folder = level instanceof ServerLevel world && diskSerial != null ? diskFolder(world.getServer(), diskSerial) : null;
				if (folder != null && imageDisk(diskStack, folder, level.getGameTime())) {
					host.markChanged(); // (from a load too, as its chunk loads: never setChanged, which waits for the chunk)
				}
				disks[lane] = new Disk(diskTier, folder);
				disks[lane].clockFrom(() -> host.level() == null ? 0 : host.level().getGameTime()); // (its files' times: the world's)
				disks[lane].onChange(host::markChanged); // its reports of changes, saved with the computer
			}
			diskSerials[lane] = diskSerial;
			if (disks[lane] != null) {
				int slot = CaseMenu.SATA_SLOTS + lane;
				disks[lane].labelIn(ModComponents.labelOn(() -> parts.getItem(slot), host::markChanged)); // the disk's own
				disks[lane].formatIn(() -> ModComponents.diskFormat(parts.getItem(slot)), format -> ModComponents.diskFormat(parts.getItem(slot), format));
			}
			board.plug(Motherboard.sata(lane), disks[lane], serial(CaseMenu.SATA_SLOTS + lane));
			String connector = Motherboard.exp(lane);
			if (soldered.containsKey(connector)) {
				cards[lane] = null;
				board.plug(connector, soldered.get(connector));
				continue;
			}
			ItemStack card = parts.getItem(CaseMenu.EXP_SLOTS + lane);
			boolean ours = mine(serial(CaseMenu.EXP_SLOTS + lane), server, kept);
			if (!ours) {
				cards[lane] = null;
				board.plug(connector, null, null);
				continue;
			}
			if (lane == 0 && screenInSlot()) {
				// A screen in the gold slot: its terminal, on the bus there.
				TerminalTier screenTier = ((ScreenItem) card.getItem()).screen().tier();
				if (display.tier() != screenTier) {
					display = newDisplay(screenTier);
				}
				cards[lane] = null;
				display.labelIn(ModComponents.labelOn(() -> parts.getItem(CaseMenu.EXP_SLOTS), host::markChanged)); // the screen's own
				board.plug(connector, display, serial(CaseMenu.EXP_SLOTS + lane));
				continue;
			}
			CardKind kind = ModItems.cardKind(card).orElse(null);
			cards[lane] = kind == null ? null : cards[lane] != null && cards[lane].kind() == kind ? cards[lane] : cardHere(kind);
			if (cards[lane] != null) {
				int slot = CaseMenu.EXP_SLOTS + lane;
				cards[lane].labelIn(ModComponents.labelOn(() -> parts.getItem(slot), host::markChanged)); // the card's own
				cards[lane].idFrom(() -> serial(slot));
				if (cards[lane] instanceof com.payonel.modularcomputers.device.BluetoothCard bluetooth) {
					// Its link lives on the card itself; a program's change is a link made, as by hand.
					bluetooth.linkIn(() -> parts.getItem(slot).get(ModComponents.BLUETOOTH_LINK), id -> {
						if (id == null) {
							parts.getItem(slot).remove(ModComponents.BLUETOOTH_LINK);
						} else {
							parts.getItem(slot).set(ModComponents.BLUETOOTH_LINK, id);
						}
						host.markChanged();
						host.bluetoothChanged();
					});
					bluetooth.boardFrom(this::boardId);
				}
				if (cards[lane] instanceof com.payonel.modularcomputers.device.CraftingCard crafting && level instanceof ServerLevel world) {
					// Its slots live on the card itself; a robot's robotics work them (none in a case).
					crafting.benchIn(new com.payonel.modularcomputers.entity.CraftingBench(
						new com.payonel.modularcomputers.menu.CraftingStorage(() -> parts.getItem(slot), host::markChanged), host::robot, () -> world));
				}
			}
			board.plug(connector, cards[lane], serial(CaseMenu.EXP_SLOTS + lane));
		}
		if (server != null) {
			PartHolds.keep(this, kept);
		}
		rebuildBus();
	}

	/**
	 * Puts the bus's devices in order (the motherboard's job: see {@link Motherboard#layout}).
	 * External devices that went away keep their place, offline, for when they come back, and
	 * disconnected ones stay off the bus.
	 */
	private void rebuildBus() {
		Level level = host.level();
		Map<String, Device> external = new LinkedHashMap<>();
		Map<String, String> hosts = new LinkedHashMap<>();
		for (Cabled device : cabled) {
			// Server side, a device saved as cabled is only on the bus once its host finds it again.
			external.put(device.key(), cabledDevices.get(device.key()));
			if (!device.host().isEmpty()) {
				hosts.put(device.key(), device.host());
			}
		}
		Motherboard board = machine.board();
		board.setExternal(external, hosts);
		long now = level != null ? level.getGameTime() : 0;
		if (level != null && level.isClientSide()) {
			board.rebuildBus(now); // clients show what the server sends; they don't run devices
		} else {
			machine.rebuildBus(now);
		}
		for (Cabled device : cabled) {
			known.put(device.key(), device);
		}
		known.keySet().retainAll(busOrder().external().stream().map(BusOrder.Entry::key).toList());
		host.bluetoothChanged();
	}

	private void playPartSounds() {
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			boolean had = lastParts[slot] != null && !lastParts[slot].isEmpty();
			boolean has = !parts.getItem(slot).isEmpty();
			if (has != had) {
				host.level().playSound(null, host.pos(), partSound(slot, has), SoundSource.BLOCKS, 0.8f, 1.0f);
			}
		}
		rememberParts();
	}

	private void rememberParts() {
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			lastParts[slot] = parts.getItem(slot).copy();
		}
	}

	private static SoundEvent partSound(int slot, boolean inserted) {
		if (slot == CaseMenu.CPU_SLOT) {
			return inserted ? ModSounds.CPU_INSERT : ModSounds.CPU_REMOVE;
		} else if (slot == CaseMenu.EEPROM_SLOT) {
			return inserted ? ModSounds.EEPROM_INSERT : ModSounds.EEPROM_REMOVE;
		} else if (CaseMenu.isBatteryBay(slot)) {
			return inserted ? ModSounds.BATTERY_INSERT : ModSounds.BATTERY_REMOVE;
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return inserted ? ModSounds.RAM_INSERT : ModSounds.RAM_REMOVE;
		} else if (slot < CaseMenu.EXP_SLOTS) {
			return inserted ? ModSounds.DISK_INSERT : ModSounds.DISK_REMOVE;
		}
		return inserted ? ModSounds.CARD_INSERT : ModSounds.CARD_REMOVE;
	}

	/** Sends its data (parts, bus order, display) to clients. */
	private void syncToClients() {
		Level level = host.level();
		if (level != null && !level.isClientSide()) {
			syncedPowerPercent = power.percent();
			host.syncToClients();
		}
	}

	/** The serial number of the part in a slot, if it has one. */
	private UUID serial(int slot) {
		return parts.getItem(slot).get(ModComponents.SERIAL);
	}

	private void onPartsChanged() {
		Level level = host.level();
		if (level != null && !level.isClientSide()) {
			for (int slot : BUS_PART_SLOTS) {
				ItemStack part = parts.getItem(slot);
				if (!part.isEmpty()) {
					ModComponents.serial(part); // its id, wherever it goes next
				}
			}
		}
		installParts();
		host.setChanged();
		if (level == null || level.isClientSide()) {
			return;
		}
		playPartSounds();
		host.bluetoothChanged(); // a Bluetooth card may have come or gone
		if (machine.cpuRemoved()) {
			// Pulling the CPU out of a running machine kills it.
			errorBeep();
		} else if (machine.memoryChanged()) {
			errorBeep(); // RAM in or out under a running program: the CPU aborts
		}
		pullFromBattery(); // a battery going in fills the buffer right away
		if (!showPowerState()) {
			syncToClients(); // the bus order panel shows the parts
		}
	}

	/** Its parts, from where they were kept (its item), installed without sounds (placing isn't inserting). */
	public void restoreParts(List<ItemStack> items) {
		parts.getItems().clear(); // not clearContent(): that would fire change handling
		for (int slot = 0; slot < Math.min(items.size(), CaseMenu.PART_SLOTS); slot++) {
			parts.getItems().set(slot, items.get(slot));
		}
		installParts();
		rememberParts();
		if (host.level() != null && !host.level().isClientSide()) {
			updateBattery(); // a battery that came along fills the buffer
		}
	}

	/**
	 * Saves it into its host's data. {@code syncing}: the clients' copy (no program, no devices'
	 * data: only what they show).
	 */
	public void save(ValueOutput output, boolean syncing) {
		output.putBoolean(POWERED, machine.isPowered());
		Shutdown last = machine.lastShutdown();
		output.putString(SHUTDOWN_CAUSE, last.cause().word());
		output.putString(SHUTDOWN_MESSAGE, last.message());
		output.putLong(SHUTDOWN_TIME, last.time());
		output.putLong(UPTIME, machine.uptime());
		output.putInt(POWER_BUFFER, power.level());
		if (machine.board().busCapacityOverride() != null) {
			output.putInt(BUS_CAPACITY_OVERRIDE, machine.board().busCapacityOverride());
		}
		output.store(BOARD_ID, UUIDUtil.CODEC, boardId);
		ContainerHelper.saveAllItems(output, parts.getItems());
		output.store(BUS_EXTERNAL, ENTRY_CODEC.listOf(), busOrder().external());
		output.store(BUS_DISCONNECTED, Codec.STRING.listOf(), busOrder().disconnected());
		output.store(CABLED, Cabled.CODEC.listOf(), cabled);
		output.store(KNOWN, Cabled.CODEC.listOf(), List.copyOf(known.values()));
		if (!syncing) {
			// A running Lua program is saved as it runs (its VM, by Styx, with its board's ring), at its
			// next safe point. First: the calls it makes meanwhile change the devices saved below.
			machine.persistProgram().or(() -> Optional.ofNullable(savedProgram))
				.ifPresent(saved -> output.store(PROGRAM, Codec.BYTE_BUFFER, java.nio.ByteBuffer.wrap(saved)));
			// What its devices hold for the program (keys, events) and settings it made: a save isn't a power cut.
			DeviceSave.save(output, DISPLAY + "_device", display);
			for (int lane = 0; lane < cards.length; lane++) {
				if (cards[lane] != null) {
					DeviceSave.save(output, CARD + lane, cards[lane]);
				}
			}
			for (int lane = 0; lane < disks.length; lane++) {
				if (disks[lane] != null) {
					DeviceSave.save(output, DISK + lane, disks[lane]);
				}
			}
			for (Map.Entry<String, Device> device : soldered.entrySet()) {
				DeviceSave.save(output, device.getKey(), device.getValue());
			}
		}
		ScreenSave.save(output, DISPLAY, display);
		ConsoleSplitter console = machine.board().virtualBus().console();
		ScreenSave.save(output, CONSOLE, console.screen(), console.decoder());
	}

	/** Loads it from its host's data. */
	public void load(ValueInput input) {
		machine.restorePowered(input.getBooleanOr(POWERED, false));
		machine.restoreShutdown(new Shutdown(Shutdown.Cause.of(input.getStringOr(SHUTDOWN_CAUSE, "none")),
			input.getStringOr(SHUTDOWN_MESSAGE, ""), input.getLongOr(SHUTDOWN_TIME, 0)));
		machine.restoreUptime(input.getLongOr(UPTIME, 0));
		power.set(input.getIntOr(POWER_BUFFER, 0));
		machine.board().overrideBusCapacity(input.getInt(BUS_CAPACITY_OVERRIDE).orElse(null));
		input.read(BOARD_ID, UUIDUtil.CODEC).ifPresent(saved -> boardId = saved);
		parts.getItems().clear(); // not clearContent(): that would fire change handling mid-load
		ContainerHelper.loadAllItems(input, parts.getItems());
		input.read(BUS_EXTERNAL, ENTRY_CODEC.listOf()).ifPresent(order ->
			busOrder().restore(order, input.read(BUS_DISCONNECTED, Codec.STRING.listOf()).orElse(List.of())));
		cabled = input.read(CABLED, Cabled.CODEC.listOf()).map(List::copyOf).orElse(List.of());
		known.clear();
		input.read(KNOWN, Cabled.CODEC.listOf()).orElse(List.of()).forEach(device -> known.put(device.key(), device));
		installParts();
		rememberParts(); // loading isn't inserting: no sounds
		ScreenSave.load(input, DISPLAY, display);
		DeviceSave.load(input, DISPLAY + "_device", display);
		for (int lane = 0; lane < cards.length; lane++) {
			if (cards[lane] != null) {
				DeviceSave.load(input, CARD + lane, cards[lane]);
			}
		}
		for (int lane = 0; lane < disks.length; lane++) {
			if (disks[lane] != null) {
				DeviceSave.load(input, DISK + lane, disks[lane]);
			}
		}
		for (Map.Entry<String, Device> device : soldered.entrySet()) {
			DeviceSave.load(input, device.getKey(), device.getValue());
		}
		ConsoleSplitter console = machine.board().virtualBus().console();
		ScreenSave.load(input, CONSOLE, console.screen(), console.decoder());
		console.restored();
		savedProgram = input.read(PROGRAM, Codec.BYTE_BUFFER).map(buffer -> {
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			return bytes;
		}).filter(bytes -> machine.isPowered()).orElse(null);
		unsavedProgram = machine.isPowered() && savedProgram == null;
	}

	/** Loaded on with no program saved (it couldn't be): it aborts at its first tick. */
	private boolean unsavedProgram;

	/** A board's part slots, whose parts hold slots of their own (a crafting card's, on the card). */
	public interface PartContents {
		/** What a part holds changed (not which parts are in): its holder is saved with it. */
		void partContentsChanged();
	}

	/** The part slots. Changes install the parts and update the power button. */
	private final class Parts extends SimpleContainer implements PartContents {
		Parts() {
			super(CaseMenu.PART_SLOTS);
		}

		@Override
		public void partContentsChanged() {
			host.markChanged();
		}

		@Override
		public void setChanged() {
			super.setChanged();
			onPartsChanged();
		}

		/** Each slot holds one part, however the items stack elsewhere. */
		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean stillValid(Player player) {
			return host.stillValid(player);
		}
	}
}
