package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.entity.GolemLegs;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.hint.HintedItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A bot golem's legs, picked up: used on a block, they stand there, facing whoever placed them (a golem places them too). */
public final class GolemLegsItem extends HintedItem {
	public GolemLegsItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		GolemLegs legs = ModEntities.GOLEM_LEGS.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (legs == null) {
			return InteractionResult.FAIL;
		}
		Vec3 at = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
		float facing = context.getPlayer() != null ? context.getPlayer().getYRot() + 180 : 0;
		legs.snapTo(at.x, at.y, at.z, facing, 0);
		if (!level.noCollision(legs)) {
			return InteractionResult.FAIL; // no room
		}
		level.addFreshEntity(legs);
		context.getItemInHand().consume(1, context.getPlayer());
		return InteractionResult.SUCCESS;
	}
}
