package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A screen block's display. Computers it's wired to write to it; it saves what it shows and
 * sends changes to clients. Its id tells computers which screen is which (for the bus order),
 * wherever it is.
 */
public final class ScreenBlockEntity extends BlockEntity implements DisplayHost {
	private static final String ID = "screen_id"; // not "id": that is the block entity type
	private static final String DISPLAY = "display";
	private static final String FOREGROUND = "display_foreground";
	private static final String BACKGROUND = "display_background";

	private final Display display = new Display(Resolution.DEFAULT);
	private UUID id = UUID.randomUUID();
	/** The display's change count last sent to clients. */
	private int synced = -1;

	public ScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SCREEN_ENTITY, pos, state);
	}

	@Override
	public Display display() {
		return display;
	}

	/** Which screen this is, to the computers it's wired to. */
	public UUID id() {
		return id;
	}

	/** Sends what the screen shows to clients when a computer changed it. */
	void serverTick() {
		int changes = display.buffer().changes();
		if (changes != synced && level != null) {
			synced = changes;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(ID, UUIDUtil.CODEC, id);
		output.store(DISPLAY, Codec.STRING.listOf(), display.buffer().rows());
		output.putIntArray(FOREGROUND, display.buffer().foregrounds());
		output.putIntArray(BACKGROUND, display.buffer().backgrounds());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(ID, UUIDUtil.CODEC).ifPresent(saved -> id = saved);
		input.read(DISPLAY, Codec.STRING.listOf()).ifPresent(display.buffer()::setRows);
		input.getIntArray(FOREGROUND).ifPresent(foreground ->
			input.getIntArray(BACKGROUND).ifPresent(background -> display.buffer().setColors(foreground, background)));
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
