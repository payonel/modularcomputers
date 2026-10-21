package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.RedstoneTerminal;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.BatteryTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A redstone terminal's block entity: the terminal as a bus device, its sides in the world, its
 * cable ports, and its two slots: the radio (a Bluetooth card) and a battery, which pays for its
 * signal changes while it has the charge (the bus pays otherwise).
 */
public final class RedstoneTerminalBlockEntity extends CardHolderBlockEntity implements CablePorts.Holder {
	public static final int RADIO_SLOT = 0;
	public static final int BATTERY_SLOT = 1;
	/** Its sides as world directions, in the device's order ({@link RedstoneTerminal#SIDES}). */
	public static final List<Direction> DIRECTIONS = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
		Direction.UP, Direction.DOWN);
	private static final String DEVICE = "terminal";

	private final RedstoneTerminal device = new RedstoneTerminal();
	private final CablePorts ports = new CablePorts();

	public RedstoneTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.REDSTONE_TERMINAL_ENTITY, pos, state, 2);
		// Device calls come on the server thread: the world changes right then.
		device.world(new RedstoneTerminal.World() {
			@Override
			public boolean cable(int side) {
				return level != null && Cabling.carriesTo(level, worldPosition, DIRECTIONS.get(side));
			}

			@Override
			public int input(int side) {
				Direction direction = DIRECTIONS.get(side);
				return level == null ? 0 : level.getSignal(worldPosition.relative(direction), direction);
			}

			@Override
			public void port(int side, boolean open) {
				if (level != null && !level.isClientSide()) {
					CablePorts.set(level, worldPosition, DIRECTIONS.get(side), open);
				}
			}

			@Override
			public void outputChanged() {
				if (level != null && !level.isClientSide()) {
					level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
				}
			}
		});
		device.payWith(this::pay);
		// Minecraft writes only chunks marked changed: its events and outputs change as it's used.
		device.onChange(() -> {
			if (level != null && !level.isClientSide()) {
				level.blockEntityChanged(worldPosition);
			}
		});
	}

	/** The terminal as a bus device. */
	public RedstoneTerminal device() {
		return device;
	}

	@Override
	public CablePorts ports() {
		return ports;
	}

	@Override
	public int radioSlot() {
		return RADIO_SLOT;
	}

	/** The radio slot takes a Bluetooth card, the battery slot a battery. */
	@Override
	public boolean accepts(int slot, ItemStack stack) {
		return slot == BATTERY_SLOT ? stack.getItem() instanceof BatteryItem : super.accepts(slot, stack);
	}

	@Override
	protected boolean holdsCard(int slot) {
		return slot != BATTERY_SLOT && super.holdsCard(slot);
	}

	/** What it puts out on a world side: its signal there, where that side is a redstone side. */
	public int signal(Direction side) {
		int index = DIRECTIONS.indexOf(side);
		return device.redstone(index) ? device.output(index) : 0;
	}

	/** Server: something next to it changed: what comes in on each redstone side. */
	void sense() {
		for (int side = 0; side < DIRECTIONS.size(); side++) {
			if (device.redstone(side)) {
				Direction direction = DIRECTIONS.get(side);
				device.sense(side, level.getSignal(worldPosition.relative(direction), direction));
			}
		}
	}

	/** Pays for a change from its battery while it has the charge, otherwise from the bus. */
	private void pay(int cost) {
		ItemStack stack = cards().getItem(BATTERY_SLOT);
		if (level != null && stack.getItem() instanceof BatteryItem battery) {
			BatteryTier tier = battery.tier();
			long now = level.getGameTime();
			BatteryCharge charge = BatteryItem.charge(stack).rest(tier, now);
			if (tier.infinite() || charge.units() >= cost) {
				stack.set(ModComponents.CHARGE, charge.run(tier, cost, now));
				setChanged();
				return;
			}
		}
		device.power().charge(cost);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.redstoneTerminal(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	/** What it puts out and its events: a save isn't a power cut. And its ports. */
	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		DeviceSave.save(output, DEVICE, device);
		ports.save(output);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		DeviceSave.load(input, DEVICE, device);
		ports.load(input);
	}
}
