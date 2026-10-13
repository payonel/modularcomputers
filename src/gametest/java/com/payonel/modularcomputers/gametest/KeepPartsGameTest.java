package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * Blocks that hold parts or cards don't spill them when broken (user, 2026-10-12): like a shulker
 * box, the block drops as one item that keeps them, and its id, and placing it puts them back.
 */
public class KeepPartsGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	/** Breaks the block as a player would (its loot drops), and picks up what fell. */
	private static List<ItemStack> breakBlock(GameTestHelper helper, BlockPos pos) {
		BlockPos at = helper.absolutePos(pos);
		helper.getLevel().destroyBlock(at, true);
		List<ItemStack> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream()
			.map(entity -> {
				ItemStack stack = entity.getItem().copy();
				entity.discard();
				return stack;
			}).toList();
		return dropped;
	}

	private static Player placeBack(GameTestHelper helper, ItemStack stack, BlockPos pos) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack); // placing uses what's in hand
		helper.placeAt(player, stack, pos.below(), Direction.UP);
		return player;
	}

	@GameTest
	public void aBrokenComputerKeepsItsPartsIdAndBusOrder(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.TIER2_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(POS, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER2_CPU));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER1_RAM));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.WLAN_CARD));
		UUID board = computer.boardId();
		UUID card = computer.parts().getItem(CaseMenu.EXP_SLOTS + 1).get(ModComponents.SERIAL);
		computer.busOrder().restore(List.of(new com.payonel.modularcomputers.bus.BusOrder.Entry("id:somewhere", 5)), List.of());

		List<ItemStack> dropped = breakBlock(helper, POS);
		helper.assertValueEqual(dropped.size(), 1, "one item, not its parts spilled: " + dropped);
		ItemStack item = dropped.getFirst();
		helper.assertValueEqual(item.getItem(), ModBlocks.TIER2_CASE.asItem(), "the case");
		List<Item> inside = item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
			.map(ItemStack::getItem).toList();
		helper.assertValueEqual(inside, List.of(ModItems.TIER2_CPU, ModItems.EEPROM, ModItems.TIER1_BATTERY, ModItems.TIER1_RAM, ModItems.WLAN_CARD),
			"its parts are in the item");
		helper.assertValueEqual(item.get(ModComponents.SERIAL), board, "its board id is the item's id");
		// Its hint lists what's inside: the bus devices in bus order, then the rest.
		List<String> hint = com.payonel.modularcomputers.hint.Hint.lines(com.payonel.modularcomputers.hint.Hint.of(item, false));
		helper.assertValueEqual(hint.subList(0, 5), List.of("ROM: EEPROM", "EXP 2: WLAN Card", "CPU: Tier 2 CPU", "RAM 1: Tier 1 RAM",
			"UPS 1: Tier 1 Battery"), "the hint's list of parts");
		helper.assertTrue(!item.get(DataComponents.TOOLTIP_DISPLAY).shows(DataComponents.CONTAINER), "vanilla's own list is hidden");

		placeBack(helper, item, POS);
		AllInOneBlockEntity placed = helper.getBlockEntity(POS, AllInOneBlockEntity.class);
		helper.assertValueEqual(placed.boardId(), board, "the same board: Bluetooth links still find it");
		helper.assertValueEqual(placed.parts().getItem(CaseMenu.EXP_SLOTS + 1).get(ModComponents.SERIAL), card, "the card is the same device");
		helper.assertTrue(placed.machine().board().cpu().isPresent(), "its CPU is installed");
		helper.assertValueEqual(placed.busOrder().external().getFirst().key(), "id:somewhere", "its bus order came along");
		helper.assertTrue(!placed.machine().isPowered(), "placed, it's off");
		helper.succeed();
	}

	@GameTest
	public void anEmptyComputerStillStacksWithNewOnesOnlyWithoutItsId(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.TIER1_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		ItemStack item = breakBlock(helper, POS).getFirst();
		helper.assertTrue(!item.has(DataComponents.CONTAINER), "nothing inside: no contents");
		helper.assertTrue(item.has(ModComponents.SERIAL), "but it's that board");
		item.remove(ModComponents.SERIAL);
		helper.assertTrue(ItemStack.isSameItemSameComponents(item, new ItemStack(ModBlocks.TIER1_CASE)), "cleared, it stacks with new ones");
		helper.succeed();
	}

	@GameTest
	public void aBrokenScreenKeepsItsRadioAndId(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.TIER2_SCREEN.defaultBlockState());
		ScreenBlockEntity screen = helper.getBlockEntity(POS, ScreenBlockEntity.class);
		screen.cards().setItem(screen.radioSlot(), new ItemStack(ModItems.BLUETOOTH_CARD));
		UUID id = screen.id();

		List<ItemStack> dropped = breakBlock(helper, POS);
		helper.assertValueEqual(dropped.size(), 1, "one item: " + dropped);
		List<String> hint = com.payonel.modularcomputers.hint.Hint.lines(com.payonel.modularcomputers.hint.Hint.of(dropped.getFirst(), false));
		helper.assertTrue(hint.contains("Radio: Bluetooth Card"), "its hint lists its radio: " + hint);
		placeBack(helper, dropped.getFirst(), POS);
		ScreenBlockEntity placed = helper.getBlockEntity(POS, ScreenBlockEntity.class);
		helper.assertValueEqual(placed.id(), id, "the same screen (id:" + id + ")");
		helper.assertTrue(placed.radioCard().is(ModItems.BLUETOOTH_CARD), "its radio is back in");
		helper.succeed();
	}
}
