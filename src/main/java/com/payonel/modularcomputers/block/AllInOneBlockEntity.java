package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
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
 * Holds the all-in-one computer's machine. The power state is saved and synced to clients;
 * the display contents follow from it until there is a real machine to run.
 */
public final class AllInOneBlockEntity extends BlockEntity {
	private static final String RUNNING = "running";

	private final Machine machine = new Machine(Hardware.allInOne(), Resolution.DEFAULT);

	public AllInOneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALL_IN_ONE_ENTITY, pos, state);
	}

	public Machine machine() {
		return machine;
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
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		machine.setRunning(input.getBooleanOr(RUNNING, false));
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
