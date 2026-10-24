package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.device.NetworkCard;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Network cards in the world (user design, 2026-10-21): wired ones talk over the cables (two cases
 * on one cable), WLAN cards through the air, and a frame holding a card's wake message turns its
 * computer on.
 */
public class NetworkGameTest {
	private static final BlockPos LEFT = new BlockPos(1, 1, 2);
	private static final BlockPos RIGHT = new BlockPos(5, 1, 2);
	/** How long the test computers' program waits at a time (it waits on, so its cards take data). */
	private static final double WAIT_SECONDS = 1000;

	private static Arguments args(Object... values) {
		return new Arguments(Arrays.asList(values));
	}

	/** A computer with {@code card}, its program waiting forever; switched on when {@code on}. */
	private static NetworkCard computer(GameTestHelper helper, BlockPos pos, Item card, boolean on) {
		helper.setBlock(pos, ModBlocks.TIER3_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(pos, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(card));
		TestPrograms.flash(computer, board -> {
			while (true) {
				board.waitForInterrupt(WAIT_SECONDS, "nothing");
			}
		});
		if (on) {
			computer.pressPower();
		}
		return computer.machine().board().bus().devices().stream().filter(NetworkCard.class::isInstance)
			.map(NetworkCard.class::cast).findFirst().orElseThrow();
	}

	private static void cable(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		helper.getLevel().setBlock(absolute, CableBlock.withConnections(helper.getLevel(), absolute, ModBlocks.CABLE.defaultBlockState()),
			Block.UPDATE_ALL);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> received(NetworkCard card) {
		return (Map<String, Object>) card.receive(args()).first();
	}

	@GameTest(maxTicks = 40)
	public void wiredCardsTalkOverTheCableBetweenTwoCasesAndNotWithoutIt(GameTestHelper helper) {
		NetworkCard left = computer(helper, LEFT, ModItems.NETWORK_CARD, true);
		NetworkCard right = computer(helper, RIGHT, ModItems.NETWORK_CARD, true);
		NetworkCard wireless = computer(helper, new BlockPos(3, 1, 5), ModItems.TIER2_WLAN_CARD, true);
		for (int x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
			cable(helper, new BlockPos(x, 1, 2));
		}
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(left.send(args(right.address(), "hello")).first(), true, "acknowledged over the cable"))
			.thenExecute(() -> {
				helper.assertValueEqual(received(right).get("data"), "hello", "the frame came");
				left.broadcast(args("all"));
				helper.assertValueEqual(received(right).get("distance"), 4.0, "four meters of cable away");
				helper.assertTrue(!wireless.interruptPending(), "a WLAN card isn't on the wire");
				helper.setBlock(new BlockPos(3, 1, 2), net.minecraft.world.level.block.Blocks.AIR);
			})
			.thenWaitUntil(() -> helper.assertValueEqual(left.send(args(right.address(), "x")).first(), false, "the cable cut: not acknowledged"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void wlanCardsTalkWithoutCables(GameTestHelper helper) {
		NetworkCard left = computer(helper, LEFT, ModItems.TIER2_WLAN_CARD, true);
		NetworkCard right = computer(helper, RIGHT, ModItems.TIER2_WLAN_CARD, true);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(left.send(args(right.address(), "hello")).first(), true, "acknowledged through the air"))
			.thenExecute(() -> {
				Map<String, Object> frame = received(right);
				helper.assertValueEqual(frame.get("from"), left.address(), "from its sender");
				helper.assertValueEqual(frame.get("distance"), 4.0, "four blocks away");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void aFrameHoldingTheWakeMessageTurnsAComputerOn(GameTestHelper helper) {
		NetworkCard left = computer(helper, LEFT, ModItems.TIER2_WLAN_CARD, true);
		NetworkCard right = computer(helper, RIGHT, ModItems.TIER2_WLAN_CARD, false);
		right.setWake(args("wake"));
		AllInOneBlockEntity sleeper = helper.getBlockEntity(RIGHT, AllInOneBlockEntity.class);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(left.send(args(right.address(), "please wake up")).first(), true,
				"its card listens while it's off"))
			.thenWaitUntil(() -> helper.assertBlockProperty(RIGHT, AllInOneBlock.POWERED, true))
			.thenExecute(() -> helper.assertTrue(sleeper.machine().isPowered(), "on"))
			.thenSucceed();
	}
}
