package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.ChargingPad;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.menu.ChargingPadMenu;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A charging pad (user design, 2026-10-24): a robot standing on it (a bot golem) charges, little
 * green glowing particles round it while it does. Its power is a device's: the bus's while a
 * running computer has it on its bus (by cable, or right against it), else its own buffer, which
 * its battery fills, as a case's does. It charges the robot's battery; a robot with none, its
 * board's buffer. Its device ({@link ChargingPad}) says what it's doing and when a robot comes and
 * goes. A battery bay and a radio slot (a Bluetooth card), as other blocks on the network have.
 */
public final class ChargingPadBlockEntity extends CardHolderBlockEntity {
	public static final int BATTERY = 0;
	public static final int RADIO = 1;
	public static final int SLOTS = 2;
	/** What it gives a robot a tick, in power units: ten running CPUs' worth, so a robot charges far faster than it runs down. */
	public static final int CHARGE_RATE = 10 * PowerUnits.CPU;
	/** How often its buffer takes from its battery, in ticks (a case's battery check). */
	private static final int BATTERY_TICKS = 20;
	/** How often it shows its particles while charging, in ticks, and how many. */
	private static final int PARTICLE_TICKS = 4;
	private static final int PARTICLES = 2;
	/** How far round the robot its particles go, in blocks. */
	private static final double PARTICLE_SPREAD = 0.35;
	/** How high above the pad a robot counts as on it, in blocks: standing there. */
	private static final double ON_IT = 0.5;
	/** How many numbers its menu syncs: its buffer's percent, its battery's, whether it's charging. */
	public static final int DATA = 3;
	private static final String BUFFER = "buffer";

	private final PowerBuffer buffer = new PowerBuffer();
	/** The robot on it (by its board's id), or null. */
	private String robot;
	private boolean charging;
	private int ticks;
	private final ChargingPad device = new ChargingPad(new ChargingPad.Pad() {
		@Override
		public String robot() {
			return robot;
		}

		@Override
		public boolean charging() {
			return charging;
		}

		@Override
		public int buffer() {
			return buffer.level();
		}

		@Override
		public int capacity() {
			return buffer.capacity();
		}
	});
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> buffer.percent();
				case 1 -> batteryPercent();
				default -> charging ? 1 : 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA;
		}
	};

	public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CHARGING_PAD_ENTITY, pos, state, SLOTS);
		device.onChange(() -> ChunkMarks.changed(level, worldPosition));
	}

	/** It, on the bus. */
	public ChargingPad device() {
		return device;
	}

	/** Whether it's charging a robot now. */
	public boolean charging() {
		return charging;
	}

	public PowerBuffer buffer() {
		return buffer;
	}

	@Override
	public int radioSlot() {
		return RADIO;
	}

	@Override
	public boolean accepts(int slot, ItemStack stack) {
		return takes(slot, stack);
	}

	/** What a slot takes: a battery in its bay, a Bluetooth card in its radio. */
	public static boolean takes(int slot, ItemStack stack) {
		return slot == BATTERY ? stack.getItem() instanceof BatteryItem : stack.is(ModItems.BLUETOOTH_CARD);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent sound(int slot, boolean inserted) {
		return slot == BATTERY ? (inserted ? ModSounds.BATTERY_INSERT : ModSounds.BATTERY_REMOVE) : super.sound(slot, inserted);
	}

	/** Server, every tick: its buffer from its battery now and then; the robot on it, charged. */
	void serverTick() {
		ServerLevel server = (ServerLevel) level;
		ticks++;
		if (ticks % BATTERY_TICKS == 0) {
			fillFromBattery();
		}
		BotGolem on = robotOnIt(server);
		String now = on == null ? null : on.board().boardId().toString();
		if (!java.util.Objects.equals(now, robot)) {
			if (robot != null) {
				device.undocked(robot);
			}
			robot = now;
			if (robot != null) {
				device.docked(robot);
			}
			setChanged();
		}
		boolean was = charging;
		charging = on != null && charge(on);
		if (charging && ticks % PARTICLE_TICKS == 0) {
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, on.getX(), on.getY() + on.getBbHeight() / 2, on.getZ(), PARTICLES,
				PARTICLE_SPREAD, on.getBbHeight() / 2, PARTICLE_SPREAD, 0);
		}
		if (charging != was) {
			setChanged();
		}
	}

	/** The robot standing on it, if one is. */
	private BotGolem robotOnIt(ServerLevel server) {
		AABB top = new AABB(worldPosition).setMinY(worldPosition.getY() + ChargingPadBlock.HEIGHT / 16.0).setMaxY(worldPosition.getY() + 1 + ON_IT);
		List<BotGolem> found = server.getEntitiesOfClass(BotGolem.class, top, golem -> golem.isAlive() && golem.blockPosition().equals(worldPosition));
		return found.isEmpty() ? null : found.get(0);
	}

	/**
	 * Gives {@code robot} a tick's charge: into its battery (its board's buffer when it has none),
	 * as much as it takes, paid from the bus or the buffer; whether it charged anything.
	 */
	private boolean charge(BotGolem robot) {
		ItemStack stack = robot.board().parts().getItem(CaseMenu.UPS_SLOT);
		long now = level.getGameTime();
		int room;
		if (stack.getItem() instanceof BatteryItem battery) {
			if (battery.tier().infinite()) {
				return false;
			}
			BatteryCharge charge = BatteryItem.charge(stack).rest(battery.tier(), now);
			room = battery.tier().capacity() - charge.units();
		} else {
			room = robot.board().power().capacity() - robot.board().power().level();
		}
		int amount = Math.min(CHARGE_RATE, room);
		if (amount <= 0 || !pay(amount)) {
			return false;
		}
		if (stack.getItem() instanceof BatteryItem battery) {
			stack.set(ModComponents.CHARGE, BatteryItem.charge(stack).rest(battery.tier(), now).charge(battery.tier(), amount)); // in place: not a part change
		} else {
			robot.board().power().fill(amount);
		}
		return true;
	}

	/** Pays {@code units}: the bus's power while a running computer supplies it, else its buffer's. */
	private boolean pay(int units) {
		if (device.payFromBus(units)) {
			return true;
		}
		if (buffer.level() < units) {
			fillFromBattery(); // (on demand: a battery just put in charges at once)
		}
		if (buffer.level() < units) {
			return false;
		}
		buffer.use(units);
		return true;
	}

	/** Its buffer takes what it's missing from its battery. */
	private void fillFromBattery() {
		ItemStack stack = cards().getItem(BATTERY);
		if (!(stack.getItem() instanceof BatteryItem battery)) {
			return;
		}
		if (battery.tier().infinite()) {
			buffer.fill(buffer.capacity());
			return;
		}
		long now = level.getGameTime();
		BatteryCharge charge = BatteryItem.charge(stack).rest(battery.tier(), now);
		stack.set(ModComponents.CHARGE, charge.run(battery.tier(), buffer.fill(charge.units()), now));
		setChanged();
	}

	private int batteryPercent() {
		ItemStack stack = cards().getItem(BATTERY);
		return stack.getItem() instanceof BatteryItem battery ? BatteryItem.charge(stack).percent(battery.tier()) : 0;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new ChargingPadMenu(id, inventory, cards(), data, worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt(BUFFER, buffer.level());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		buffer.set(input.getIntOr(BUFFER, 0));
	}
}
