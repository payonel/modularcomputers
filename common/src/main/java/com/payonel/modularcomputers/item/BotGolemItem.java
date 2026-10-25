package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.hint.HintedItem;
import com.payonel.modularcomputers.machine.GolemLayout;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.menu.ItemContents;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.HostRef;
import com.payonel.modularcomputers.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * A bot golem, picked up: used on a block, it stands there, facing the player, with the parts and
 * board id it kept. Its torso alone (its legs detached) is one too, and is placed without legs.
 * Used sneaking (in the air or on a block), it opens its parts view where it is, in the hand (the
 * user's): its parts and its hands read and written on the item, their one home, its board off
 * (an item runs nothing).
 */
public final class BotGolemItem extends HintedItem {
	/** Whether it's a whole golem (or its torso alone). */
	private final boolean legs;

	public BotGolemItem(Properties properties) {
		this(properties, true);
	}

	public BotGolemItem(Properties properties, boolean legs) {
		super(properties);
		this.legs = legs;
	}

	@Override
	public InteractionResult use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			openParts(server, hand);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() != null && context.getPlayer().isSecondaryUseActive()) {
			if (context.getPlayer() instanceof ServerPlayer server) {
				openParts(server, context.getHand());
			}
			return InteractionResult.SUCCESS; // (sneaking: its view, not placed)
		}
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
		golem.legs(legs); // (its size, for the room it needs)
		golem.snapTo(at.x, at.y, at.z, facing, 0);
		golem.setYBodyRot(facing);
		golem.setYHeadRot(facing);
		if (!level.noCollision(golem)) {
			return InteractionResult.FAIL; // no room
		}
		golem.fromItem(stack);
		golem.legs(legs);
		level.addFreshEntity(golem);
		int before = stack.getCount();
		stack.consume(1, context.getPlayer());
		if (stack.getCount() == before) {
			ModComponents.placed(stack, context.getPlayer(), context.getHand()); // (creative left it: it moved all the same)
		}
		return InteractionResult.SUCCESS;
	}

	/** Its parts view, for the golem in the player's {@code hand}, open while it's still that stack there. */
	public static void openParts(ServerPlayer player, InteractionHand hand) {
		ItemStack golem = player.getItemInHand(hand);
		Supplier<ItemStack> held = () -> player.getItemInHand(hand) == golem ? golem : ItemStack.EMPTY;
		Platform.INSTANCE.openMenu(player, new SimpleMenuProvider((id, inventory, who) -> new CaseMenu(ModMenus.GOLEM, id, inventory,
			new Parts(held), new SimpleContainerData(CaseMenu.POWER_DATA), who.blockPosition(), HostRef.of(who),
			opener -> opener.getItemInHand(hand) == golem, GolemLayout.BOT_GOLEM, new ItemContents(held, ModComponents.HAND_SLOTS, ItemContents.in(ModComponents.HANDS), () -> {
			})), golem.getHoverName()), HostRef.of(player));
	}

	/** A held golem's parts, on its item; what a part holds changing (a crafting card's slots) is written back too. */
	private static final class Parts extends ItemContents implements com.payonel.modularcomputers.board.BoardAssembly.PartContents {
		Parts(Supplier<ItemStack> golem) {
			super(golem, CaseMenu.PART_SLOTS, CONTAINER, () -> {
			});
		}

		@Override
		public void partContentsChanged() {
			setChanged();
		}
	}

}
