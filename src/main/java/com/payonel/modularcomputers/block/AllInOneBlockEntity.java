package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.HelloWorldProgram;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The all-in-one computer: a motherboard with soldered memory, a CPU socket and an EEPROM slot
 * (both filled from the case view's item slots), and its built-in display, keyboard and disk
 * wired to the physical bus. Power, display contents, the console state and the parts are
 * saved and synced to clients.
 */
public final class AllInOneBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private static final String POWERED = "powered";
	private static final String DISPLAY = "display";
	private static final String FOREGROUND = "display_foreground";
	private static final String BACKGROUND = "display_background";
	private static final String CONSOLE = "console";

	private final Display display = new Display(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(),
		new Bus(List.of(display, new Keyboard(), new Disk()))));
	private final Parts parts = new Parts();

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

	/** The CPU and EEPROM slots. */
	public Container parts() {
		return parts;
	}

	/** Server side: flips the power switch, updates the power button, clicks, and tells clients. */
	public void togglePower() {
		machine.togglePower();
		setChanged();
		if (level == null) {
			return;
		}
		// Changing the block state also sends this block entity's update to clients.
		level.setBlock(worldPosition, getBlockState().setValue(AllInOneBlock.POWERED, machine.isPowered()), Block.UPDATE_CLIENTS);
		level.playSound(null, worldPosition,
			machine.isPowered() ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
			SoundSource.BLOCKS, 0.3f, machine.isPowered() ? 0.6f : 0.5f);
	}

	/** Puts the parts in the slots onto the motherboard. */
	private void installParts() {
		Motherboard board = machine.board();
		board.setCpu(parts.getItem(CaseMenu.CPU_SLOT).is(ModItems.CPU) ? new Cpu() : null);
		// Every EEPROM holds the stand-in program until EEPROM contents are programmable.
		board.setEeprom(parts.getItem(CaseMenu.EEPROM_SLOT).is(ModItems.EEPROM) ? new Eeprom(new HelloWorldProgram()) : null);
	}

	private void onPartsChanged() {
		installParts();
		setChanged();
		if (level != null && !level.isClientSide()) {
			// The power button looks different without a CPU; the block state carries that to clients.
			boolean hasCpu = machine.board().cpu().isPresent();
			if (getBlockState().getValue(AllInOneBlock.CPU) != hasCpu) {
				level.setBlock(worldPosition, getBlockState().setValue(AllInOneBlock.CPU, hasCpu), Block.UPDATE_CLIENTS);
			}
		}
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new CaseMenu(id, inventory, parts, worldPosition, ContainerLevelAccess.create(level, worldPosition));
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
		ContainerHelper.saveAllItems(output, parts.getItems());
		output.store(DISPLAY, Codec.STRING.listOf(), display.buffer().rows());
		output.putIntArray(FOREGROUND, display.buffer().foregrounds());
		output.putIntArray(BACKGROUND, display.buffer().backgrounds());
		ConsoleSplitter console = machine.board().virtualBus().console();
		output.putIntArray(CONSOLE, new int[] {console.column(), console.row(), console.foreground(), console.background()});
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		machine.restorePowered(input.getBooleanOr(POWERED, false));
		parts.getItems().clear(); // not clearContent(): that would fire change handling mid-load
		ContainerHelper.loadAllItems(input, parts.getItems());
		installParts();
		input.read(DISPLAY, Codec.STRING.listOf()).ifPresent(display.buffer()::setRows);
		input.getIntArray(FOREGROUND).ifPresent(foreground ->
			input.getIntArray(BACKGROUND).ifPresent(background -> display.buffer().setColors(foreground, background)));
		input.getIntArray(CONSOLE).filter(state -> state.length == 4).ifPresent(state ->
			machine.board().virtualBus().console().restore(state[0], state[1], state[2], state[3]));
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
