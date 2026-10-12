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
		AllInOneBlockEntity computer = helper.getBlockEntity(COMPUTER, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU)); // no CPU, no bus
		return computer;
	}

	private static RoboticPistonBlockEntity piston(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.ROBOTIC_PISTON.defaultBlockState().setValue(RoboticPistonBlock.FACING, Direction.DOWN));
		return helper.getBlockEntity(pos, RoboticPistonBlockEntity.class);
	}

	/** A Bluetooth card linking to a board, or (null) to nothing: then it's just a radio for others to link to. */
	private static ItemStack card(UUID link) {
		ItemStack card = new ItemStack(ModItems.BLUETOOTH_CARD);
		if (link != null) {
			card.set(ModComponents.BLUETOOTH_LINK, link);
		}
		return card;
	}

	private static List<String> locations(AllInOneBlockEntity computer) {
		return computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).map(entry -> entry.location().getString()).toList();
	}

	@GameTest
	public void rightClickLinksTheCardInHandToTheBoardAndSneakingResetsIt(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, new BlockPos(2, 1, 2));
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, card(null));
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.useBlock(new BlockPos(2, 1, 2), player);
		helper.assertTrue(held.get(ModComponents.BLUETOOTH_LINK) == null, "no Bluetooth card in the piston: nothing to link with");
		piston.cards().setItem(0, card(null)); // its radio
		helper.useBlock(new BlockPos(2, 1, 2), player);
		helper.assertValueEqual(held.get(ModComponents.BLUETOOTH_LINK), piston.boardId(), "the card in hand links to the piston's board");

		// Sneaking with an item in hand, the game skips the block and the item acts.
		player.setShiftKeyDown(true);
		var hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))), Direction.UP, helper.absolutePos(new BlockPos(2, 1, 2)), false);
		held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(held.get(ModComponents.BLUETOOTH_LINK) == null, "a reset card links to nothing");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void linksStayWithTheBoardNotTheCard(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity other = helper.getBlockEntity(new BlockPos(1, 1, 3), AllInOneBlockEntity.class);
		other.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(null));
		piston(helper, new BlockPos(1, 1, 6)).cards().setItem(0, card(computer.boardId()));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("paired"), "paired with the computer"))
			.thenExecute(() -> {
				ItemStack radio = computer.parts().removeItemNoUpdate(CaseMenu.EXP_SLOTS);
				computer.parts().setChanged();
				other.parts().setItem(CaseMenu.EXP_SLOTS, radio); // the card moves to the other computer
			})
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("offline"), "no radio: the piston drops off"))
			.thenIdle(25)
			.thenExecute(() -> helper.assertTrue(locations(other).isEmpty(), "the link didn't move with the card"))
			.thenExecute(() -> computer.parts().setItem(CaseMenu.EXP_SLOTS + 2, card(null))) // any card brings it back
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("paired"), "back with the computer"))
			.thenSucceed();
	}

	@GameTest
	public void aPairedPistonBelongsToTheComputerItsCardLinksTo(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(null));
		piston(helper, new BlockPos(1, 1, 6)).cards().setItem(0, card(computer.boardId())); // 5 blocks away, no cable
		helper.succeedWhen(() -> helper.assertValueEqual(locations(computer), List.of("paired"), "found over Bluetooth, paired"));
	}

	@GameTest
	public void aPairedScreenIsOnlyOnItsOwnersBusButStillCarriesTheCable(GameTestHelper helper) {
		// A - cable - screen - cable - B, and a second, unpaired screen on top of the first.
		AllInOneBlockEntity owner = computer(helper);
		owner.parts().setItem(CaseMenu.EXP_SLOTS, card(null));
		cable(helper, new BlockPos(2, 1, 1));
		ScreenBlockEntity paired = screen(helper, new BlockPos(3, 1, 1));
		paired.cards().setItem(0, card(owner.boardId()));
		ScreenBlockEntity shared = screen(helper, new BlockPos(3, 2, 1));
		cable(helper, new BlockPos(4, 1, 1));
		helper.setBlock(new BlockPos(5, 1, 1), ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity other = helper.getBlockEntity(new BlockPos(5, 1, 1), AllInOneBlockEntity.class);
		other.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		helper.succeedWhen(() -> {
			helper.assertTrue(onBus(owner, paired) && onBus(owner, shared), "the owner has both screens");
			helper.assertTrue(!onBus(other, paired), "the other computer doesn't get the paired screen");
			helper.assertTrue(onBus(other, shared), "but reaches the unpaired one through it: it still carries the cable");
			helper.assertTrue(locations(owner).contains("paired"), "the owner's row says paired");
		});
	}

	@GameTest
	public void pairingChainsThroughAHub(GameTestHelper helper) {
		// The screen's card links to the hub's board, and the hub's radio card to the computer's:
		// the computer owns both, with no cable anywhere.
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(null));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class);
		hub.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(computer.boardId()));
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		ScreenBlockEntity screen = screen(helper, new BlockPos(4, 1, 5));
		screen.cards().setItem(0, card(hub.boardId()));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(locations(computer), List.of("hub 1, paired", "paired"), "the hub's card and the screen, both paired");
			helper.assertTrue(onBus(computer, screen), "the screen is on the bus");
		});
	}

	@GameTest(maxTicks = 100)
	public void anUnlinkedRadioLeavesADeviceShared(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		cable(helper, new BlockPos(2, 1, 1));
		ScreenBlockEntity screen = screen(helper, new BlockPos(3, 1, 1));
		screen.cards().setItem(0, card(null)); // a radio, linked nowhere: not paired
		helper.succeedWhen(() -> {
			helper.assertTrue(onBus(computer, screen), "shared on the cable");
			helper.assertValueEqual(locations(computer), List.of("cable, 2m"), "not paired");
		});
	}

	@GameTest
	public void cardsDaisyChainThroughAHub(GameTestHelper helper) {
		// Unpaired: the computer's card links to the hub's board (the hub's radio receives); the
		// hub passes it on by cable.
		AllInOneBlockEntity computer = computer(helper);
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(hub.boardId()));
		hub.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(null));
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		cable(helper, new BlockPos(4, 1, 2));
		screen(helper, new BlockPos(4, 1, 3));
		helper.succeedWhen(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 3m", "bluetooth, 5m"),
			"the hub's card 3m away over Bluetooth, then the screen 2m past it by cable"));
	}

	@GameTest
	public void anUnlinkedCardJoinsNothing(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, card(UUID.randomUUID())); // a board that isn't there
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(4, 1, 1), ExpansionHubBlockEntity.class);
		hub.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(null));
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> helper.assertTrue(locations(computer).isEmpty(), "no link, no connection"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void hubsDaisyChain(GameTestHelper helper) {
		// The user's case: hub 1's radio links to hub 2, and a computer wired to hub 1 sees hub 2's
		// devices too, out past it by cable.
		AllInOneBlockEntity computer = computer(helper);
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub1 = helper.getBlockEntity(new BlockPos(2, 1, 1), ExpansionHubBlockEntity.class);
		helper.setBlock(new BlockPos(2, 1, 6), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub2 = helper.getBlockEntity(new BlockPos(2, 1, 6), ExpansionHubBlockEntity.class);
		hub2.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(null)); // hub 2's radio, to link to
		hub2.cards().setItem(0, new ItemStack(ModItems.WLAN_CARD));
		cable(helper, new BlockPos(3, 1, 6));
		ScreenBlockEntity screen = screen(helper, new BlockPos(4, 1, 6));
		hub1.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		hub1.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(hub2.boardId()));
		helper.succeedWhen(() -> {
			helper.assertTrue(onBus(computer, screen), "the screen past hub 2");
			helper.assertValueEqual(computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled)
				.map(entry -> entry.name().getString()).toList(), List.of("Audio Card", "WLAN Card", "Screen"), "hub 1's card, hub 2's, the screen");
		});
	}

		@GameTest(maxTicks = 100)
	public void aDevicePairedWithAHubGoesWithTheHub(GameTestHelper helper) {
		// Two computers share a hub by cable; a piston, far off, is paired with the hub.
		AllInOneBlockEntity first = computer(helper);
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.EXPANSION_HUB);
		ExpansionHubBlockEntity hub = helper.getBlockEntity(new BlockPos(2, 1, 1), ExpansionHubBlockEntity.class);
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity second = helper.getBlockEntity(new BlockPos(3, 1, 1), AllInOneBlockEntity.class);
		second.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		first.parts().setItem(CaseMenu.EXP_SLOTS, card(null)); // the first computer's radio, for later
		hub.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(null)); // a radio, linked nowhere: the hub is shared
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		piston(helper, new BlockPos(2, 1, 6)).cards().setItem(0, card(hub.boardId()));
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertValueEqual(locations(first), List.of("hub 1, 1m", "paired"), "the first computer has the hub and its piston");
				helper.assertValueEqual(locations(second), List.of("hub 1, 1m", "paired"), "and so does the second");
			})
			.thenExecute(() -> hub.cards().setItem(ExpansionHubBlockEntity.RADIO_SLOT, card(first.boardId()))) // the hub pairs with the first
			.thenWaitUntil(() -> {
				helper.assertValueEqual(locations(first), List.of("hub 1, paired", "paired"), "the first keeps both");
				helper.assertValueEqual(locations(second), List.of("offline", "offline"), "the second loses both");
			})
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
		return computer.machine().board().bus().devices().contains(screen.terminal());
	}
}
