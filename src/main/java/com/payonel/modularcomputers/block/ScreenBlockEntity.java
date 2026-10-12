package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A screen block's display. Computers it's wired to write to it; it saves what it shows and
 * sends changes to clients. Its one slot (opened from the back) is the radio slot: a
 * Bluetooth card there gives the screen to one computer.
 */
public final class ScreenBlockEntity extends CardHolderBlockEntity implements DisplayHost {
	private static final String DISPLAY = "display";
	private static final String FOREGROUND = "display_foreground";
	private static final String BACKGROUND = "display_background";
	/** The terminal's cursor, colors and modes. */
	private static final String TERMINAL_STATE = "display_state";

	private final Terminal display = new Terminal(Resolution.DEFAULT);
	/** The display's change count last sent to clients. */
	private int synced = -1;

	public ScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SCREEN_ENTITY, pos, state, 1);
		display.setFlusher(this::serverTick); // right after a computer's turn changed it
	}

	@Override
	public Terminal terminal() {
		return display;
	}

	@Override
	public int radioSlot() {
		return 0;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.screen(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}

	/** Sends what the screen shows to clients when a computer changed it. */
	void serverTick() {
		int changes = display.buffer().changes();
		if (changes != synced && level != null && !level.isClientSide()) {
			synced = changes;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(DISPLAY, Codec.STRING.listOf(), display.buffer().rows());
		output.putIntArray(FOREGROUND, display.buffer().foregrounds());
		output.putIntArray(BACKGROUND, display.buffer().backgrounds());
		output.putIntArray(TERMINAL_STATE, display.decoder().state());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(DISPLAY, Codec.STRING.listOf()).ifPresent(display.buffer()::setRows);
		input.getIntArray(FOREGROUND).ifPresent(foreground ->
			input.getIntArray(BACKGROUND).ifPresent(background -> display.buffer().setColors(foreground, background)));
		input.getIntArray(TERMINAL_STATE).ifPresent(display.decoder()::restore);
	}
}
