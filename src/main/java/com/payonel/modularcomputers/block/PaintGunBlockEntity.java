package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.PaintGun;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A paint gun's id (for the bus order of the computers wired to it) and its bus device. */
public final class PaintGunBlockEntity extends BlockEntity {
	private static final String ID = "device_id"; // not "id": that is the block entity type

	private final PaintGun device = new PaintGun();
	private UUID id = UUID.randomUUID();

	public PaintGunBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.PAINT_GUN_ENTITY, pos, state);
	}

	public UUID id() {
		return id;
	}

	public PaintGun device() {
		return device;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(ID, UUIDUtil.CODEC, id);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(ID, UUIDUtil.CODEC).ifPresent(saved -> id = saved);
	}
}
