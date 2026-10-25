package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.hint.HintedItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * A bot golem, picked up: used on a block, it stands there, facing the player, with the parts and
 * board id it kept.
 */
public final class BotGolemItem extends HintedItem {
	public BotGolemItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		if (ModComponents.copyOfOneInTheWorld(stack, level)) {
			if (context.getPlayer() != null) {
				context.getPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.modularcomputers.already_placed"));
			}
			return InteractionResult.FAIL;
		}
		BotGolem golem = ModEntities.BOT_GOLEM.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (golem == null) {
			return InteractionResult.FAIL;
		}
		Vec3 at = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
		float facing = context.getPlayer() != null ? context.getPlayer().getYRot() + 180 : 0; // (toward the player)
		golem.snapTo(at.x, at.y, at.z, facing, 0);
		golem.setYBodyRot(facing);
		golem.setYHeadRot(facing);
		if (!level.noCollision(golem)) {
			return InteractionResult.FAIL; // no room
		}
		golem.fromItem(stack);
		level.addFreshEntity(golem);
		int before = stack.getCount();
		stack.consume(1, context.getPlayer());
		if (stack.getCount() == before) {
			ModComponents.placed(stack, context.getPlayer(), context.getHand()); // (creative left it: it moved all the same)
		}
		return InteractionResult.SUCCESS;
	}
}
