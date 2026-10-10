package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.HelloWorldProgram;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The all-in-one computer: a motherboard with soldered parts and a soldered EEPROM, and its
 * built-in display, keyboard and disk wired to the physical bus. Power state, display contents
 * and the console cursor are saved and synced to clients.
 */
public final class AllInOneBlockEntity extends BlockEntity {
	private static final String RUNNING = "running";
	private static final String DISPLAY = "display";
	private static final String CURSOR = "console_cursor";

	private final Display display = new Display(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(),
		new Bus(List.of(display, new Keyboard(), new Disk())),
		new Eeprom(new HelloWorldProgram())));

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

	/** Server side: flips the power, lights the power button, clicks, and tells clients. */
	public void togglePower() {
		machine.togglePower();
		setChanged();
		if (level == null) {
			return;
		}
		BlockState state = getBlockState().setValue(AllInOneBlock.RUNNING, machine.isRunning());
		// Changing the block state also sends this block entity's update to clients.
		level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
		level.playSound(null, worldPosition,
			machine.isRunning() ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
			SoundSource.BLOCKS, 0.3f, machine.isRunning() ? 0.6f : 0.5f);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean(RUNNING, machine.isRunning());
		output.store(DISPLAY, Codec.STRING.listOf(), display.buffer().rows());
		ConsoleSplitter console = machine.board().virtualBus().console();
		output.putIntArray(CURSOR, new int[] {console.column(), console.row()});
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		machine.restoreRunning(input.getBooleanOr(RUNNING, false));
		input.read(DISPLAY, Codec.STRING.listOf()).ifPresent(display.buffer()::setRows);
		input.getIntArray(CURSOR).filter(cursor -> cursor.length == 2)
			.ifPresent(cursor -> machine.board().virtualBus().console().restoreCursor(cursor[0], cursor[1]));
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
