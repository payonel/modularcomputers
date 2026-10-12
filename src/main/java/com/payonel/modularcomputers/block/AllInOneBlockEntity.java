package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.BusStatus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.cable.CableWalk;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Card;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.CpuItem;
import com.payonel.modularcomputers.item.DiskItem;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CaseTier;
import com.payonel.modularcomputers.item.ScreenItem;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.HelloWorldProgram;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.power.RecentDraw;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The all-in-one computer: its built-in terminal (screen and keyboard), and a motherboard whose part
 * slots (CPU, EEPROM, UPS battery, RAM, SATA drives, expansion cards) are filled from the case
 * view. Drives and cards join the physical bus after the built-ins, then the screens wired to
 * it by cable, in an order the player can change (the bus order panel), where cabled devices
 * can also be disconnected. Power, the parts, the bus order, display contents and the console
 * state are saved and synced to clients. Only the CPU and EEPROM affect the machine so far;
 * RAM and the UPS battery are placeholders.
 */
public final class AllInOneBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos>, DisplayHost, Bluetooth.Holder {
	private static final String POWERED = "powered";
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
	private static final String BOARD_ID = "board_id";
	private static final String TERMINAL_ID = "terminal_id";
	/** Looks for cabled devices this often, in ticks, even when no cable changed (chunks load and unload). */
	private static final int RESCAN_TICKS = 20;
	/** How often the battery is run down or recharged. */
	private static final int BATTERY_TICKS = 20;
	/** Icons for the built-in devices in textures/gui/case_icons.png. */
	public static final int TERMINAL_ICON = 6;
	private static final float ERROR_BEEP_VOLUME = 0.6f;
	/** The slots whose parts are bus devices: each gets a serial number, its id. */
	private static final int[] BUS_PART_SLOTS = {CaseMenu.EEPROM_SLOT,
		CaseMenu.SATA_SLOTS, CaseMenu.SATA_SLOTS + 1, CaseMenu.SATA_SLOTS + 2, CaseMenu.SATA_SLOTS + 3,
		CaseMenu.EXP_SLOTS, CaseMenu.EXP_SLOTS + 1, CaseMenu.EXP_SLOTS + 2, CaseMenu.EXP_SLOTS + 3};

	/**
	 * Its terminal: the all-in-one's built-in one, or the screen in a case's gold expansion slot
	 * (a new one when a screen of another tier goes in). In a case without one it's off the bus.
	 */
	private Terminal display = new Terminal(Resolution.DEFAULT);
	/** The built-in terminal's id, its address on the bus. Made when the computer is placed, and saved. */
	private UUID terminalId = UUID.randomUUID();
	/** Devices made from the parts in the slots (by lane for drives and cards); null when empty. */
	private final Disk[] disks = new Disk[CaseMenu.LANES];
	private final Card[] cards = new Card[CaseMenu.LANES];
	private Eeprom eeprom;
	private final Bus bus = new Bus(List.of()); // empty until a CPU is in
	private final PowerBuffer power;
	private final CaseTier tier;
	/** It's the all-in-one: a built-in terminal on its front. */
	private final boolean builtInTerminal;
	/** The motherboard's id: what Bluetooth cards link to. Made when the computer is placed, and saved. */
	private UUID boardId = UUID.randomUUID();
	/** What the computer has drawn lately, for the case view's runtime estimate. */
	private final RecentDraw recentDraw = new RecentDraw();
	/**
	 * The buffer's level and the recent draw for the case view, which sends them to the player as
	 * they change. Menu data goes as shorts, so each is two: the low 16 bits, then the high
	 * ({@link CaseMenu#powerLevel}, {@link CaseMenu#recentDraw}).
	 */
	private final ContainerData powerData = new ContainerData() {
		@Override
		public int get(int index) {
			int value = index < 2 ? power.level() : (int) Math.min(Integer.MAX_VALUE, recentDraw.perPeriod());
			return index % 2 == 0 ? value & 0xFFFF : value >>> 16;
		}

		@Override
		public void set(int index, int value) {
			int old = index < 2 ? power.level() : (int) recentDraw.perPeriod();
			int merged = index % 2 == 0 ? old & ~0xFFFF | value & 0xFFFF : (value & 0xFFFF) << 16 | old & 0xFFFF;
			if (index < 2) {
				power.set(merged);
			} else {
				recentDraw.set(merged);
			}
		}

		@Override
		public int getCount() {
			return CaseMenu.POWER_DATA;
		}
	};
	/** The case power clients last saw, in percent: the meter and its time left. */
	private int syncedPowerPercent;
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), bus), this::hasPower);
	private final Parts parts = new Parts();
	/** The cabled devices found last time, nearest first. Saved and synced, for the bus order panel. */
	private List<Cabled> cabled = List.of();
	/**
	 * What was last seen of each external device in the saved bus order, online or not: an
	 * offline one's row still shows its name, icon and where it was.
	 */
	private final Map<String, Cabled> known = new LinkedHashMap<>();
	/** Server side: the cabled devices, by key. */
	private final Map<String, Device> cabledDevices = new LinkedHashMap<>();
	private long seenCableChanges = -1;
	private int ticks;

	/**
	 * A device wired to this computer: where it is, how far the connection runs to it in meters
	 * (blocks), whether the way crosses a Bluetooth link, whether it's paired with this computer,
	 * the item it is (for its name and icon), and its slot when it's a card in an expansion hub
	 * (else -1).
	 */
	public record Cabled(String key, BlockPos pos, int meters, boolean wireless, boolean paired, Item item, int hubSlot) {
		static final Codec<Cabled> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("key").forGetter(Cabled::key),
			BlockPos.CODEC.fieldOf("pos").forGetter(Cabled::pos),
			Codec.INT.fieldOf("meters").forGetter(Cabled::meters),
			Codec.BOOL.fieldOf("wireless").forGetter(Cabled::wireless),
			Codec.BOOL.fieldOf("paired").forGetter(Cabled::paired),
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Cabled::item),
			Codec.INT.fieldOf("hub_slot").forGetter(Cabled::hubSlot)
		).apply(instance, Cabled::new));
	}

	public AllInOneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALL_IN_ONE_ENTITY, pos, state);
		AllInOneBlock block = state.getBlock() instanceof AllInOneBlock found ? found : (AllInOneBlock) ModBlocks.ALL_IN_ONE;
		this.tier = block.tier();
		this.builtInTerminal = block.builtInTerminal();
		this.power = new PowerBuffer(tier.bufferCapacity());
		display.setFlusher(this::syncDisplay);
		if (builtInTerminal) {
			// The all-in-one's own terminal, wired to its board.
			if (builtInTerminal) {
			machine.board().plug(Motherboard.TERMINAL, display, terminalId);
		}
		}
	}

	public CaseTier tier() {
		return tier;
	}

	@Override
	public boolean showsScreen() {
		return builtInTerminal || screenInSlot();
	}

	/** A screen is in the gold expansion slot (tiers 2 and 3). */
	private boolean screenInSlot() {
		return !builtInTerminal && CaseMenu.isScreenSlot(tier, CaseMenu.EXP_SLOTS) && parts.getItem(CaseMenu.EXP_SLOTS).getItem() instanceof ScreenItem;
	}

	/** The motherboard's bus order. */
	private BusOrder busOrder() {
		return machine.board().order();
	}

	public Machine machine() {
		return machine;
	}

	/** Its terminal: built in on the all-in-one, or the screen in a case's gold slot. */
	@Override
	public Terminal terminal() {
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
		Optional<BlockPos> device, boolean connected, boolean online, int position) {
		/** An external device: it can be reordered, identified, unplugged, and forgotten while offline. */
		public boolean cabled() {
			return device.isPresent();
		}
	}

	/** The devices on the bus, in order, as the bus order panel shows them. Works on clients too. */
	public List<BusEntry> busEntries() {
		List<BusEntry> entries = new ArrayList<>();
		if (machine.board().cpu().isEmpty()) {
			return entries; // no CPU, no bus
		}
		Component builtIn = Component.translatable("gui.modularcomputers.bus.built_in");
		BusOrder busOrder = busOrder();
		for (String key : busOrder.keys()) {
			int position = machine.board().position(key);
			if (key.equals(machine.board().address(Motherboard.TERMINAL))) {
				entries.add(new BusEntry(key, Component.translatable("gui.modularcomputers.device.terminal"), builtIn, ItemStack.EMPTY, -1, TERMINAL_ICON,
					Optional.empty(), true, true, position));
			} else if (!busOrder.isInternal(key)) {
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
			} else {
				int slot = partSlot(key);
				ItemStack item = parts.getItem(slot);
				entries.add(new BusEntry(key, item.getHoverName(), partLocation(key), item, slot, -1, Optional.empty(), true, true, position));
			}
		}
		return entries;
	}

	/** The case slot holding the part on a motherboard connector. */
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
		setChanged();
		syncToClients();
		return true;
	}

	/** Server side: the player moved the device at {@code index} up or down the bus order. */
	public boolean moveDevice(int index, boolean up) {
		if (!busOrder().move(index, up)) {
			return false;
		}
		rebuildBus();
		setChanged();
		syncToClients();
		return true;
	}

	/** Its Bluetooth cards (in the expansion slots), in bus order. */
	@Override
	public List<ItemStack> bluetoothCards() {
		List<ItemStack> cards = new ArrayList<>();
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			ItemStack card = parts.getItem(CaseMenu.EXP_SLOTS + lane);
			if (card.is(ModItems.BLUETOOTH_CARD)) {
				cards.add(card);
			}
		}
		return cards;
	}

	/** Its board's id, which Bluetooth cards link to. */
	@Override
	public UUID boardId() {
		return boardId;
	}

	private void updateBluetooth() {
		if (level != null) {
			Bluetooth.update(level, worldPosition, boardId, bluetoothCards(), null);
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
		machine.unload(); // a running program isn't saved: it stops
		if (level != null) {
			Bluetooth.remove(level, worldPosition);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		updateBluetooth();
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
		if (level == null || level.isClientSide()) {
			return;
		}
		if (busOrder().waiting() > waitingBefore) {
			level.playSound(null, worldPosition, ModSounds.BUS_OVERFLOW, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		if (!before.containsAll(externalOnBus())) {
			level.playSound(null, worldPosition, ModSounds.DEVICE_CONNECT, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
	}

	/** Server side: the player plugged a cabled device in or out in the bus order panel. */
	public boolean setConnected(String key, boolean connected) {
		Set<String> before = externalOnBus();
		if (!busOrder().setConnected(key, connected)) { // external devices only
			return false;
		}
		rebuildBus();
		busSounds(before, busOrder().waiting());
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
		if (ticks % BATTERY_TICKS == 0) {
			updateBattery();
		}
		// The motherboard checks its CPU and power every tick, and the program gets its turn.
		Machine.Supervision supervision = machine.tick();
		if (supervision != Machine.Supervision.OK) {
			errorBeep();
			setPowerState();
		}
		if (seenCableChanges != Cabling.changes() || ticks % RESCAN_TICKS == 0) {
			seenCableChanges = Cabling.changes();
			scanCables();
		}
		syncDisplay();
	}

	/**
	 * Sends the built-in screen's changed rows to the players who have it loaded: each tick, and
	 * right after a program's turn. Only what changed goes, not the whole block.
	 */
	private void syncDisplay() {
		if (level instanceof ServerLevel server) {
			ScreenRows.send(server, this, display.buffer());
		}
	}

	/** The computer runs on the case's power buffer. */
	private boolean hasPower() {
		return !power.isEmpty();
	}

	/** The case's own store of power, which the charge meter shows. */
	public PowerBuffer power() {
		return power;
	}

	/**
	 * Server side, now and then: a running computer draws from the case's buffer, and the buffer
	 * tops itself up from the battery. A self-charging battery recharges while the computer is
	 * off. With the battery out or flat, the buffer runs down; when it's empty a running computer
	 * dies with an error beep. Time this chunk wasn't loaded doesn't count.
	 */
	private void updateBattery() {
		// The CPU while it runs a program, and the bus devices it powers (see Machine.powerUse).
		long used = machine.powerUse(level.getGameTime());
		power.use(used);
		recentDraw.record(used);
		pullFromBattery();
		if (machine.powerLost()) {
			errorBeep();
		}
		setChanged();
		if (!setPowerState() && power.percent() != syncedPowerPercent) {
			syncToClients();
		}
	}

	/**
	 * The buffer takes what it's missing from the batteries, up to a day's worth: from the last
	 * bay first, the first bay last (user's order). While the computer runs, its batteries are in
	 * use: no self-charging; while it's off, a self-charging one rests.
	 */
	private void pullFromBattery() {
		long now = level.getGameTime();
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

	/**
	 * The power button shows whether the machine could run and whether it is on; the block
	 * state carries both to clients. Returns whether it changed.
	 */
	private boolean setPowerState() {
		BlockState state = getBlockState()
			.setValue(AllInOneBlock.READY, machine.board().cpu().isPresent() && hasPower())
			.setValue(AllInOneBlock.POWERED, machine.isPowered())
			.setValue(AllInOneBlock.SCREEN, screenInSlot()); // the front shows a screen in the gold slot
		if (state == getBlockState()) {
			return false;
		}
		level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS); // sends this block entity too
		syncedPowerPercent = power.percent();
		return true;
	}

	/**
	 * Finds the devices wired to this computer (screens, the cards in expansion hubs, robotic
	 * pistons, paint guns) and puts them on the bus.
	 */
	private void scanCables() {
		List<Cabled> found = new ArrayList<>();
		Map<String, Device> devices = new LinkedHashMap<>();
		List<CableWalk.Found> reached = Cabling.walk(level, worldPosition);
		Set<BlockPos> visible = visibleDevices(reached);
		for (CableWalk.Found at : reached) {
			BlockEntity block = level.getBlockEntity(at.pos());
			boolean paired = block instanceof CardHolderBlockEntity holder && holder.isPaired();
			if (!visible.contains(at.pos())) {
				continue;
			}
			if (block instanceof ScreenBlockEntity screen) {
				addCabled(found, devices, new Cabled(Motherboard.id(screen.id()), at.pos(), at.meters(), at.wireless(), paired, screen.getBlockState().getBlock().asItem(), -1), screen.terminal());
			} else if (block instanceof ExpansionHubBlockEntity hub) {
				for (int slot = 0; slot < ExpansionHubBlockEntity.SLOTS; slot++) {
					int hubSlot = slot;
					// A hub's card is known by its serial number, so it keeps its place in the bus
					// order when it moves to another slot (the hub stamps one on as it goes in).
					UUID serial = hub.cards().getItem(hubSlot).get(ModComponents.SERIAL);
					if (serial != null) {
						hub.card(slot).ifPresent(card -> addCabled(found, devices, new Cabled(Motherboard.id(serial), at.pos(), at.meters(),
							at.wireless(), paired, hub.cards().getItem(hubSlot).getItem(), hubSlot), card));
					}
				}
			} else if (block instanceof RoboticPistonBlockEntity piston) {
				addCabled(found, devices, new Cabled(Motherboard.id(piston.id()), at.pos(), at.meters(), at.wireless(), paired, ModBlocks.ROBOTIC_PISTON.asItem(), -1), piston.device());
			} else if (block instanceof PaintGunBlockEntity gun) {
				addCabled(found, devices, new Cabled(Motherboard.id(gun.id()), at.pos(), at.meters(), at.wireless(), paired, ModBlocks.PAINT_GUN.asItem(), -1), gun.device());
			}
		}
		boolean sameDevices = found.equals(cabled) && devices.keySet().equals(cabledDevices.keySet())
			&& devices.entrySet().stream().allMatch(entry -> cabledDevices.get(entry.getKey()) == entry.getValue());
		if (sameDevices) {
			return;
		}
		cabled = List.copyOf(found);
		cabledDevices.clear();
		cabledDevices.putAll(devices);
		Set<String> before = externalOnBus();
		int waitingBefore = busOrder().waiting();
		rebuildBus();
		busSounds(before, waitingBefore);
		setChanged();
		syncToClients();
	}

	/**
	 * Which of the blocks the walk reached this computer sees: the unpaired ones (shared), those
	 * paired with this computer, and those paired with a hub this computer sees, and so on
	 * through hubs paired with hubs.
	 */
	private Set<BlockPos> visibleDevices(List<CableWalk.Found> reached) {
		Set<BlockPos> visible = new HashSet<>();
		boolean grew = true;
		while (grew) {
			grew = false;
			for (CableWalk.Found at : reached) {
				if (visible.contains(at.pos())) {
					continue;
				}
				boolean paired = level.getBlockEntity(at.pos()) instanceof CardHolderBlockEntity holder && holder.isPaired();
				Optional<BlockPos> board = paired ? Bluetooth.pairedTo(level, at.pos()) : Optional.empty();
				// Paired with this computer, or with a hub it sees; another computer's pairings are its own.
				boolean mine = board.filter(pos -> pos.equals(worldPosition)
					|| visible.contains(pos) && level.getBlockEntity(pos) instanceof ExpansionHubBlockEntity).isPresent();
				if (!paired || mine) {
					grew |= visible.add(at.pos());
				}
			}
		}
		return visible;
	}

		private static void addCabled(List<Cabled> found, Map<String, Device> devices, Cabled cabled, Device device) {
		if (devices.putIfAbsent(cabled.key(), device) == null) {
			found.add(cabled);
		}
	}

	/**
	 * Server side: someone pressed the power button. It clicks either way. Without power nothing
	 * else happens; without a CPU the machine stays off and beeps an error. Clients learn the
	 * new state from the block state.
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
		} else if (result != Machine.PowerPress.NO_POWER) {
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
		CpuTier cpuTier = parts.getItem(CaseMenu.CPU_SLOT).getItem() instanceof CpuItem cpu ? cpu.tier() : null;
		if (cpuTier == null) {
			board.setCpu(null);
		} else if (board.cpu().map(Cpu::tier).orElse(null) != cpuTier) {
			board.setCpu(new Cpu(cpuTier));
		}
		// Every EEPROM holds the stand-in program until EEPROM contents are programmable.
		boolean hasEeprom = parts.getItem(CaseMenu.EEPROM_SLOT).is(ModItems.EEPROM);
		eeprom = hasEeprom ? (eeprom != null ? eeprom : new Eeprom(new HelloWorldProgram())) : null;
		board.setEeprom(eeprom);
		// Each part answers to its serial number too: a card is found by it here or in a hub.
		board.plug(Motherboard.EEPROM, eeprom, serial(CaseMenu.EEPROM_SLOT));
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			DiskTier diskTier = parts.getItem(CaseMenu.SATA_SLOTS + lane).getItem() instanceof DiskItem disk ? disk.tier() : null;
			disks[lane] = diskTier == null ? null : disks[lane] != null && disks[lane].tier() == diskTier ? disks[lane] : new Disk(diskTier);
			board.plug(Motherboard.sata(lane), disks[lane], serial(CaseMenu.SATA_SLOTS + lane));
			ItemStack card = parts.getItem(CaseMenu.EXP_SLOTS + lane);
			if (lane == 0 && screenInSlot()) {
				// A screen in the gold slot: the case's terminal, on the bus there.
				TerminalTier screenTier = ((ScreenItem) card.getItem()).screen().tier();
				if (display.tier() != screenTier) {
					display = new Terminal(Resolution.DEFAULT, screenTier);
					display.setFlusher(this::syncDisplay);
				}
				cards[lane] = null;
				board.plug(Motherboard.exp(lane), display, serial(CaseMenu.EXP_SLOTS + lane));
				continue;
			}
			CardKind kind = ModItems.cardKind(card).orElse(null);
			cards[lane] = kind == null ? null : cards[lane] != null && cards[lane].kind() == kind ? cards[lane] : new Card(kind);
			board.plug(Motherboard.exp(lane), cards[lane], serial(CaseMenu.EXP_SLOTS + lane));
		}
		rebuildBus();
	}

	/**
	 * Puts the bus's devices in order (the motherboard's job: see {@link Motherboard#layout}).
	 * External devices that went away keep their place, offline, for when they come back, and
	 * disconnected ones stay off the bus.
	 */
	private void rebuildBus() {
		Map<String, Device> external = new LinkedHashMap<>();
		for (Cabled device : cabled) {
			// Server side, a device saved as cabled is only on the bus once a scan finds it again.
			external.put(device.key(), cabledDevices.get(device.key()));
		}
		Motherboard board = machine.board();
		board.setExternal(external);
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
		updateBluetooth();
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
		} else if (CaseMenu.isBatteryBay(slot)) {
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
			syncedPowerPercent = power.percent();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** The serial number of the part in a slot, if it has one. */
	private UUID serial(int slot) {
		return parts.getItem(slot).get(ModComponents.SERIAL);
	}

	private void onPartsChanged() {
		if (level != null && !level.isClientSide()) {
			for (int slot : BUS_PART_SLOTS) {
				ItemStack part = parts.getItem(slot);
				if (!part.isEmpty()) {
					ModComponents.serial(part); // its id, wherever it goes next
				}
			}
		}
		installParts();
		setChanged();
		if (level == null || level.isClientSide()) {
			return;
		}
		playPartSounds();
		Cabling.changed(); // a Bluetooth card may have come or gone
		if (machine.cpuRemoved()) {
			// Pulling the CPU out of a running machine kills it.
			errorBeep();
		}
		pullFromBattery(); // a battery going in fills the buffer right away
		if (!setPowerState()) {
			syncToClients(); // the bus order panel shows the parts
		}
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new CaseMenu(id, inventory, parts, powerData, worldPosition, ContainerLevelAccess.create(level, worldPosition), tier);
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
		output.putInt(POWER_BUFFER, power.level());
		if (machine.board().busCapacityOverride() != null) {
			output.putInt(BUS_CAPACITY_OVERRIDE, machine.board().busCapacityOverride());
		}
		output.store(BOARD_ID, UUIDUtil.CODEC, boardId);
		output.store(TERMINAL_ID, UUIDUtil.CODEC, terminalId);
		ContainerHelper.saveAllItems(output, parts.getItems());
		output.store(BUS_EXTERNAL, ENTRY_CODEC.listOf(), busOrder().external());
		output.store(BUS_DISCONNECTED, Codec.STRING.listOf(), busOrder().disconnected());
		output.store(CABLED, Cabled.CODEC.listOf(), cabled);
		output.store(KNOWN, Cabled.CODEC.listOf(), List.copyOf(known.values()));
		ScreenSave.save(output, DISPLAY, display.buffer(), display.decoder());
		ConsoleSplitter console = machine.board().virtualBus().console();
		ScreenSave.save(output, CONSOLE, console.screen(), console.decoder());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		machine.restorePowered(input.getBooleanOr(POWERED, false));
		power.set(input.getIntOr(POWER_BUFFER, 0));
		machine.board().overrideBusCapacity(input.getInt(BUS_CAPACITY_OVERRIDE).orElse(null));
		input.read(BOARD_ID, UUIDUtil.CODEC).ifPresent(saved -> boardId = saved);
		input.read(TERMINAL_ID, UUIDUtil.CODEC).ifPresent(saved -> terminalId = saved);
		if (builtInTerminal) {
			machine.board().plug(Motherboard.TERMINAL, display, terminalId);
		}
		parts.getItems().clear(); // not clearContent(): that would fire change handling mid-load
		ContainerHelper.loadAllItems(input, parts.getItems());
		input.read(BUS_EXTERNAL, ENTRY_CODEC.listOf()).ifPresent(order ->
			busOrder().restore(order, input.read(BUS_DISCONNECTED, Codec.STRING.listOf()).orElse(List.of())));
		cabled = input.read(CABLED, Cabled.CODEC.listOf()).map(List::copyOf).orElse(List.of());
		known.clear();
		input.read(KNOWN, Cabled.CODEC.listOf()).orElse(List.of()).forEach(device -> known.put(device.key(), device));
		installParts();
		rememberParts(); // loading isn't inserting: no sounds
		ScreenSave.load(input, DISPLAY, display.buffer(), display.decoder());
		ConsoleSplitter console = machine.board().virtualBus().console();
		ScreenSave.load(input, CONSOLE, console.screen(), console.decoder());
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
