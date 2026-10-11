package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RoboticPistonBlock;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server game tests for Bluetooth cards: linking, resetting, and the links joining blocks like cables. */
public class BluetoothGameTest {
	private static final BlockPos COMPUTER = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(COMPUTER, ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		return helper.getBlockEntity(COMPUTER, AllInOneBlockEntity.class);
	}

	private static RoboticPistonBlockEntity piston(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.ROBOTIC_PISTON.defaultBlockState().setValue(RoboticPistonBlock.FACING, Direction.DOWN));
		return helper.getBlockEntity(pos, RoboticPistonBlockEntity.class);
	}

	/** A Bluetooth card with an address and, optionally, a link. */
	private static ItemStack card(UUID id, UUID link) {
		ItemStack card = new ItemStack(ModItems.BLUETOOTH_CARD);
		if (id != null) {
			card.set(ModComponents.BLUETOOTH_ID, id);
		}
		if (link != null) {
			card.set(ModComponents.BLUETOOTH_LINK, link);
		}
		return card;
	}

	private static List<String> locations(AllInOneBlockEntity computer) {
		return computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).map(entry -> entry.location().getString()).toList();
	}

	@GameTest
	public void rightClickLinksTheCardInHandAndSneakingResetsIt(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, new BlockPos(2, 1, 2));
		piston.cards().setItem(0, card(null, null)); // a fresh card: no address yet
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, card(null, null));
		helper.useBlock(new BlockPos(2, 1, 2), player);
		UUID address = piston.cards().getItem(0).get(ModComponents.BLUETOOTH_ID);
		helper.assertTrue(address != null, "linking gives the piston's card an address");
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertValueEqual(held.get(ModComponents.BLUETOOTH_LINK), address, "the card in hand links to it");

		// Sneaking with an item in hand, the game skips the block and the item acts.
		player.setShiftKeyDown(true);
		held.set(ModComponents.BLUETOOTH_ID, address); // pretend another card linked to this one
		var hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))), Direction.UP, helper.absolutePos(new BlockPos(2, 1, 2)), false);
		held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(held.get(ModComponents.BLUETOOTH_LINK) == null, "a reset card links to nothing");
		helper.assertTrue(!address.equals(held.get(ModComponents.BLUETOOTH_ID)), "and has a new address, so nothing links to it");
		helper.succeed();
	}

	@GameTest
	public void aComputerReachesALinkedPistonWithoutACable(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		UUID pistonCard = UUID.randomUUID();
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(UUID.randomUUID(), pistonCard));
		piston(helper, new BlockPos(1, 1, 6)).cards().setItem(0, card(pistonCard, null)); // 5 blocks away
		helper.succeedWhen(() -> helper.assertValueEqual(locations(computer), List.of("bluetooth, 5m"), "found over Bluetooth"));
	}

	@GameTest
	public void cardsDaisyChainThroughAHub(GameTestHelper helper) {
		// The computer's card links to the hub's; the piston's card links to the hub's too (many
		// to one). The hub passes the connection on, so the computer reaches the piston.
		AllInOneBlockEntity computer = computer(helper);
		UUID hubCard = UUID.randomUUID();
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(UUID.randomUUID(), hubCard));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class);
		hub.cards().setItem(0, card(hubCard, null));
		piston(helper, new BlockPos(4, 1, 5)).cards().setItem(0, card(UUID.randomUUID(), hubCard));
		helper.succeedWhen(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 3m", "bluetooth, 7m"),
			"the hub's card 3m away, then the piston 4m past it"));
	}

	@GameTest
	public void anUnlinkedCardJoinsNothing(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(UUID.randomUUID(), UUID.randomUUID()));
		piston(helper, new BlockPos(1, 1, 6)).cards().setItem(0, card(UUID.randomUUID(), null));
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> helper.assertTrue(locations(computer).isEmpty(), "no link, no connection"))
			.thenSucceed();
	}
}
