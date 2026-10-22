package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

	private final Terminal display;

	public ScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SCREEN_ENTITY, pos, state, 1);
		// Its tier is its block's: tier 1 text only, tier 2 16 colors, tier 3 every color.
		this.display = new Terminal(state.getBlock() instanceof ScreenBlock screen ? screen.tier() : TerminalTier.TIER_3);
		display.setFlusher(this::serverTick); // right after a computer's turn changed it
		// Minecraft writes only chunks marked changed: what it shows and the keys waiting change as it's used.
		display.onChange(() -> {
			if (level != null && !level.isClientSide()) {
				level.blockEntityChanged(worldPosition);
			}
		});
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
		if (level instanceof ServerLevel server) {
			ScreenRows.send(server, this, display);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ScreenSave.save(output, DISPLAY, display);
		DeviceSave.save(output, DISPLAY + "_device", display); // keys waiting to be read
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ScreenSave.load(input, DISPLAY, display);
		DeviceSave.load(input, DISPLAY + "_device", display);
	}
}
