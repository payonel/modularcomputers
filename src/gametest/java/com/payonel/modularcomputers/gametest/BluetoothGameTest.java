package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RoboticPistonBlock;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlock;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
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
	public void aPairedPistonBelongsToTheComputerItsCardLinksTo(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		UUID computerCard = UUID.randomUUID();
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(computerCard, null));
		piston(helper, new BlockPos(1, 1, 6)).cards().setItem(0, card(UUID.randomUUID(), computerCard)); // 5 blocks away, no cable
		helper.succeedWhen(() -> helper.assertValueEqual(locations(computer), List.of("paired"), "found over Bluetooth, paired"));
	}

	@GameTest
	public void aPairedScreenIsOnlyOnItsOwnersBusButStillCarriesTheCable(GameTestHelper helper) {
		// A - cable - screen - cable - B, and a second, unpaired screen on top of the first.
		AllInOneBlockEntity owner = computer(helper);
		UUID ownerCard = UUID.randomUUID();
		owner.parts().setItem(CaseMenu.EXP_SLOTS, card(ownerCard, null));
		cable(helper, new BlockPos(2, 1, 1));
		ScreenBlockEntity paired = screen(helper, new BlockPos(3, 1, 1));
		paired.cards().setItem(0, card(UUID.randomUUID(), ownerCard));
		ScreenBlockEntity shared = screen(helper, new BlockPos(3, 2, 1));
		cable(helper, new BlockPos(4, 1, 1));
		helper.setBlock(new BlockPos(5, 1, 1), ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity other = helper.getBlockEntity(new BlockPos(5, 1, 1), AllInOneBlockEntity.class);
		helper.succeedWhen(() -> {
			helper.assertTrue(onBus(owner, paired) && onBus(owner, shared), "the owner has both screens");
			helper.assertTrue(!onBus(other, paired), "the other computer doesn't get the paired screen");
			helper.assertTrue(onBus(other, shared), "but reaches the unpaired one through it: it still carries the cable");
			helper.assertTrue(locations(owner).contains("paired"), "the owner's row says paired");
		});
	}

	@GameTest
	public void pairingFollowsTheChainThroughAHub(GameTestHelper helper) {
		// The screen's card links to the hub's pairing card, and the hub's to the computer's: the
		// computer owns both, with no cable anywhere.
		AllInOneBlockEntity computer = computer(helper);
		UUID computerCard = UUID.randomUUID();
		UUID hubCard = UUID.randomUUID();
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(computerCard, null));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class);
		hub.cards().setItem(ExpansionHubBlockEntity.PAIRING_SLOT, card(hubCard, computerCard));
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		ScreenBlockEntity screen = screen(helper, new BlockPos(4, 1, 5));
		screen.cards().setItem(0, card(UUID.randomUUID(), hubCard));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(locations(computer), List.of("hub 1, paired", "paired"), "the hub's card and the screen, both paired");
			helper.assertTrue(onBus(computer, screen), "the screen is on the bus");
		});
	}

	@GameTest(maxTicks = 100)
	public void aDevicePairedToNothingIsOnNoBus(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		cable(helper, new BlockPos(2, 1, 1));
		ScreenBlockEntity screen = screen(helper, new BlockPos(3, 1, 1));
		screen.cards().setItem(0, card(UUID.randomUUID(), null)); // a card, but it links to nothing
		helper.startSequence()
			.thenIdle(25)
			.thenExecute(() -> helper.assertTrue(!onBus(computer, screen) && locations(computer).isEmpty(), "paired to no one, on no bus"))
			.thenExecute(() -> screen.cards().setItem(0, ItemStack.EMPTY))
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, screen), "without the card it's shared on the cable again"))
			.thenSucceed();
	}

	@GameTest
	public void cardsDaisyChainThroughAHub(GameTestHelper helper) {
		// Unpaired: the computer's card links to a Bluetooth card in one of the hub's expansion
		// slots, which reaches the hub over Bluetooth; the hub passes it on by cable.
		AllInOneBlockEntity computer = computer(helper);
		UUID hubCard = UUID.randomUUID();
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(UUID.randomUUID(), hubCard));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class);
		hub.cards().setItem(0, card(hubCard, null));
		cable(helper, new BlockPos(4, 1, 2));
		screen(helper, new BlockPos(4, 1, 3));
		helper.succeedWhen(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 3m", "bluetooth, 5m"),
			"the hub's card 3m away over Bluetooth, then the screen 2m past it by cable"));
	}

	@GameTest
	public void anUnlinkedCardJoinsNothing(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(UUID.randomUUID(), UUID.randomUUID()));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class).cards().setItem(0, card(UUID.randomUUID(), null));
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> helper.assertTrue(locations(computer).isEmpty(), "no link, no connection"))
			.thenSucceed();
	}

	private static ScreenBlockEntity screen(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.SCREEN.defaultBlockState().setValue(ScreenBlock.FACING, Direction.SOUTH));
		return helper.getBlockEntity(pos, ScreenBlockEntity.class);
	}

	private static void cable(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		helper.getLevel().setBlock(absolute, CableBlock.withConnections(helper.getLevel(), absolute, ModBlocks.CABLE.defaultBlockState()),
			net.minecraft.world.level.block.Block.UPDATE_ALL);
	}

	private static boolean onBus(AllInOneBlockEntity computer, ScreenBlockEntity screen) {
		return computer.machine().board().bus().devices().contains(screen.display());
	}
}
