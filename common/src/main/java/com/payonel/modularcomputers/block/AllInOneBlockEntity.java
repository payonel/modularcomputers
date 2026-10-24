package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.board.BoardAssembly;
import com.payonel.modularcomputers.board.BoardHost;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.BusStatus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.cable.CableWalk;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.machine.CaseTier;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.power.PowerBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A case (the all-in-one and the tiered ones): a block holding a {@link BoardAssembly}, its board
 * and parts, filled from the case view. What only a block does is here: the devices it finds along
 * its cables (screens, hubs' cards, robotic pistons, Disk Stations, photomasks) put on its bus, its
 * power button's light (the block state), what Bluetooth cards link to, and dropping as one item
 * that keeps its parts.
 */
public final class AllInOneBlockEntity extends BlockEntity implements MenuProvider, DisplayHost, Bluetooth.Holder {
	/** Looks for cabled devices this often, in ticks, even when no cable changed (chunks load and unload). */
	private static final int RESCAN_TICKS = 20;
	private static final String BUS_EXTERNAL = "bus_external";
	private static final String BUS_DISCONNECTED = "bus_disconnected";

	private final CaseTier tier;
	private final BoardAssembly board;
	private long seenCableChanges = -1;
	/** The holds version last seen ({@link com.payonel.modularcomputers.bus.BusHolds}). */
	private long seenHolds;
	private int ticks;
	/** Writing the client's copy (a sync), not a save: no program state in it. */
	private boolean syncing;

	public AllInOneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALL_IN_ONE_ENTITY, pos, state);
		AllInOneBlock block = state.getBlock() instanceof AllInOneBlock found ? found : (AllInOneBlock) ModBlocks.ALL_IN_ONE;
		this.tier = block.tier();
		this.board = new BoardAssembly(new CaseHost(), tier, TerminalTier.TIER_3);
	}

	/** What the board needs from the case: where it is, its front, its block state and its chunk. */
	private final class CaseHost implements BoardHost {
		@Override
		public Level level() {
			return level;
		}

		@Override
		public BlockPos pos() {
			return worldPosition;
		}

		@Override
		public Direction front() {
			return getBlockState().getValue(AllInOneBlock.FACING);
		}

		/**
		 * Marks its chunk changed, so the next save or unload writes it (Minecraft writes only chunks
		 * marked changed), at once when a device's data changes. Without {@link #setChanged}'s comparator
		 * update, which nothing here needs, so it's cheap to do often. Never waits for the chunk: its
		 * devices change as they go back on the bus while the chunk is still loading ({@link ChunkMarks}).
		 */
		@Override
		public void markChanged() {
			ChunkMarks.changed(level, worldPosition);
		}

		@Override
		public void setChanged() {
			AllInOneBlockEntity.this.setChanged();
		}

		@Override
		public void syncToClients() {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}

		/**
		 * The power button shows whether the machine could run and whether it is on, and the front a
		 * screen in the gold slot; the block state carries them to clients.
		 */
		@Override
		public boolean showPowerState(boolean ready, boolean powered, boolean halted, boolean screen) {
			if (level == null || level.isClientSide()) {
				return false;
			}
			BlockState state = getBlockState()
				.setValue(AllInOneBlock.READY, ready)
				.setValue(AllInOneBlock.POWERED, powered)
				.setValue(AllInOneBlock.HALTED, halted)
				.setValue(AllInOneBlock.SCREEN, screen);
			if (state == getBlockState()) {
				return false;
			}
			level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS); // sends this block entity too
			return true;
		}

		/**
		 * Sends the screen's changed rows to the players who have it loaded: each tick, and right
		 * after a program's turn. Only what changed goes, not the whole block.
		 */
		@Override
		public void syncDisplay(Terminal display) {
			if (level instanceof ServerLevel server) {
				ScreenRows.send(server, AllInOneBlockEntity.this, display);
			}
		}

		@Override
		public void redstoneChanged() {
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}

		@Override
		public void bluetoothChanged() {
			updateBluetooth();
			Cabling.changed(); // (what's linked, so what's reached, changed)
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(AllInOneBlockEntity.this, player);
		}

		@Override
		public String describe() {
			return "board " + board.boardId() + " at " + worldPosition.toShortString()
				+ (level != null ? " in " + level.dimension().identifier() : "") + ", chunk " + net.minecraft.core.SectionPos.blockToSectionCoord(worldPosition.getX())
				+ "," + net.minecraft.core.SectionPos.blockToSectionCoord(worldPosition.getZ());
		}
	}

	public CaseTier tier() {
		return tier;
	}

	/** Its board, with its parts. */
	public BoardAssembly board() {
		return board;
	}

	@Override
	public boolean showsScreen() {
		return board.screenInSlot();
	}

	/** The motherboard's bus order. */
	public BusOrder busOrder() {
		return board.busOrder();
	}

	public Machine machine() {
		return board.machine();
	}

	/** Its terminal: the screen in its gold slot. */
	@Override
	public Terminal terminal() {
		return board.terminal();
	}

	/** The part slots (see {@link CaseMenu} for which is which). */
	public Container parts() {
		return board.parts();
	}

	/** The case's own store of power, which the charge meter shows. */
	public PowerBuffer power() {
		return board.power();
	}

	/** What the server sends to open this computer's screen view. */
	public OpenDisplayPayload openDisplayPayload() {
		return new OpenDisplayPayload(worldPosition, terminal().buffer().resolution(), Optional.of(worldPosition), true);
	}

	/** The devices on the bus, in order, as the bus order panel shows them. Works on clients too. */
	public List<BoardAssembly.BusEntry> busEntries() {
		return board.busEntries();
	}

	public BusStatus busStatus() {
		return board.busStatus();
	}

	public boolean forgetDevice(String key) {
		return board.forgetDevice(key);
	}

	public boolean moveDevice(String key, boolean up) {
		return board.moveDevice(key, up);
	}

	public boolean setConnected(String key, boolean connected) {
		return board.setConnected(key, connected);
	}

	public boolean lowPowerAlarm() {
		return board.lowPowerAlarm();
	}

	public int redstoneOut(Direction direction) {
		return board.redstoneOut(direction);
	}

	public static SoundEvent dyingSound(Machine.Supervision cause) {
		return BoardAssembly.dyingSound(cause);
	}

	public static boolean imageDisk(ItemStack stack, java.nio.file.Path folder, long ticks) {
		return BoardAssembly.imageDisk(stack, folder, ticks);
	}

	public static java.nio.file.Path diskFolder(net.minecraft.server.MinecraftServer server, UUID id) {
		return BoardAssembly.diskFolder(server, id);
	}

	/** Its Bluetooth cards (in the expansion slots), in bus order. */
	@Override
	public List<ItemStack> bluetoothCards() {
		return board.bluetoothCards();
	}

	/** Its board's id, which Bluetooth cards link to. */
	@Override
	public UUID boardId() {
		return board.boardId();
	}

	private void updateBluetooth() {
		if (level != null) {
			Bluetooth.update(level, worldPosition, board.boardId(), bluetoothCards(), null);
		}
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		board.placed();
		updateBluetooth();
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		board.removed(); // broken: a power cut (an unload only freezes it)
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		board.unloaded();
		if (level != null) {
			Bluetooth.remove(level, worldPosition);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		updateBluetooth();
	}

	/**
	 * Server side, every tick: the board's turn, and the cables looked along again when one changed
	 * (or now and then, for chunks loading and unloading).
	 */
	void serverTick() {
		ticks++;
		board.tick();
		if (seenCableChanges != Cabling.changes() || ticks % RESCAN_TICKS == 0) {
			seenCableChanges = Cabling.changes();
			scanCables();
		}
		if (seenHolds != com.payonel.modularcomputers.bus.BusHolds.version()) {
			// Another board let go of a device: if it's one of ours, it may join this bus now.
			seenHolds = com.payonel.modularcomputers.bus.BusHolds.version();
			board.relayout(false);
		}
	}

	/**
	 * Finds the devices wired to this computer (screens, the cards in expansion hubs, robotic
	 * pistons, Disk Stations, photomasks) and puts them on the bus.
	 */
	private void scanCables() {
		List<BoardAssembly.Cabled> found = new ArrayList<>();
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
				addCabled(found, devices, new BoardAssembly.Cabled(Motherboard.id(screen.id()), at.pos(), at.meters(), at.wireless(), paired, screen.getBlockState().getBlock().asItem(), -1), screen.terminal());
			} else if (block instanceof ExpansionHubBlockEntity hub) {
				for (int slot = 0; slot < ExpansionHubBlockEntity.SLOTS; slot++) {
					int hubSlot = slot;
					// A hub's card is known by its serial number, so it keeps its place in the bus
					// order when it moves to another slot (the hub stamps one on as it goes in).
					UUID serial = hub.cards().getItem(hubSlot).get(ModComponents.SERIAL);
					if (serial != null) {
						hub.card(slot).ifPresent(card -> addCabled(found, devices, new BoardAssembly.Cabled(Motherboard.id(serial), at.pos(), at.meters(),
							at.wireless(), paired, hub.cards().getItem(hubSlot).getItem(), hubSlot), card));
					}
				}
			} else if (block instanceof RoboticPistonBlockEntity piston) {
				addCabled(found, devices, new BoardAssembly.Cabled(Motherboard.id(piston.id()), at.pos(), at.meters(), at.wireless(), paired, piston.getBlockState().getBlock().asItem(), -1), piston.device());
			} else if (block instanceof DiskStationBlockEntity station) {
				String host = Motherboard.id(station.id());
				addCabled(found, devices, new BoardAssembly.Cabled(host, at.pos(), at.meters(), at.wireless(), paired, ModBlocks.DISK_STATION.asItem(), -1), station.device());
				// Its disks, each by its serial number (the source only read), behind it.
				for (int slot : new int[] {DiskStationBlockEntity.TARGET, DiskStationBlockEntity.SOURCE}) {
					UUID serial = station.cards().getItem(slot).get(ModComponents.SERIAL);
					if (serial != null && station.disk(slot) != null) {
						addCabled(found, devices, new BoardAssembly.Cabled(Motherboard.id(serial), at.pos(), at.meters(), at.wireless(), paired,
							station.cards().getItem(slot).getItem(), -1, host), station.disk(slot));
					}
				}
			} else if (block instanceof PhotomaskBlockEntity mask) {
				addCabled(found, devices, new BoardAssembly.Cabled(Motherboard.id(mask.id()), at.pos(), at.meters(), at.wireless(), paired, ModBlocks.PHOTOMASK.asItem(), -1), mask.device());
			}
		}
		board.setExternal(found, devices);
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
				Optional<BlockPos> pairedTo = paired ? Bluetooth.pairedTo(level, at.pos()) : Optional.empty();
				// Paired with this computer, or with a hub it sees; another computer's pairings are its own.
				boolean mine = pairedTo.filter(pos -> pos.equals(worldPosition)
					|| visible.contains(pos) && level.getBlockEntity(pos) instanceof ExpansionHubBlockEntity).isPresent();
				if (!paired || mine) {
					grew |= visible.add(at.pos());
				}
			}
		}
		return visible;
	}

	private static void addCabled(List<BoardAssembly.Cabled> found, Map<String, Device> devices, BoardAssembly.Cabled cabled, Device device) {
		if (devices.putIfAbsent(cabled.key(), device) == null) {
			found.add(cabled);
		}
	}

	/**
	 * Server side: someone pressed the power button. It clicks either way. Without power nothing
	 * else happens; without a CPU the machine stays off and beeps an error, the light faded green
	 * for the moment the attempt lasts. Clients learn the new state from the block state.
	 */
	public Machine.PowerPress pressPower() {
		Machine.PowerPress result = board.pressPower();
		if (level == null) {
			return result;
		}
		if (result == Machine.PowerPress.NO_CPU) {
			// The block's tick sets the light back.
			level.setBlock(worldPosition, getBlockState().setValue(AllInOneBlock.POWERED, true), Block.UPDATE_CLIENTS);
			level.scheduleTick(worldPosition, getBlockState().getBlock(), AllInOneBlock.ATTEMPT_TICKS);
		} else if (result != Machine.PowerPress.NO_POWER) {
			// Changing the block state also sends this block entity's update to clients.
			level.setBlock(worldPosition, getBlockState().setValue(AllInOneBlock.POWERED, result == Machine.PowerPress.ON), Block.UPDATE_CLIENTS);
		}
		return result;
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new CaseMenu(id, inventory, board.parts(), board.powerData(), worldPosition, ContainerLevelAccess.create(level, worldPosition), tier);
	}

	/*
	 * Broken, a computer drops as one item that keeps its parts, its board id (what Bluetooth
	 * cards link to) and its bus order (user, 2026-10-12), like a shulker box: the loot table
	 * copies these onto the item, and placing it puts them back. The parts don't spill. What a
	 * running machine holds goes, as unplugging one does: it's off, its screens dark, and its
	 * power buffer empty until a battery fills it.
	 */
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		List<ItemStack> items = new ArrayList<>();
		for (int slot = 0; slot < parts().getContainerSize(); slot++) {
			items.add(parts().getItem(slot));
		}
		if (items.stream().anyMatch(stack -> !stack.isEmpty())) {
			components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		}
		components.set(ModComponents.SERIAL, board.boardId());
		if (!busOrder().external().isEmpty() || !busOrder().disconnected().isEmpty()) {
			components.set(ModComponents.BUS_ORDER, new ModComponents.SavedBusOrder(busOrder().external(), busOrder().disconnected()));
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		UUID serial = components.get(ModComponents.SERIAL);
		if (serial != null) {
			board.restoreBoardId(serial);
		}
		ModComponents.SavedBusOrder order = components.get(ModComponents.BUS_ORDER);
		if (order != null) {
			busOrder().restore(order.external(), order.disconnected());
		}
		net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
		components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		board.restoreParts(items);
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		super.removeComponentsFromTag(output);
		output.discard(BoardAssembly.BOARD_ID);
		output.discard("Items");
		output.discard(BUS_EXTERNAL);
		output.discard(BUS_DISCONNECTED);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		board.save(output, syncing);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		board.load(input);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		syncing = true;
		try {
			return saveWithoutMetadata(registries);
		} finally {
			syncing = false;
		}
	}
}
