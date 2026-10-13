package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.check;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.read;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.awt.image.BufferedImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Client game test for case tiers: a tier 2 case with a screen in its gold slot shows the screen
 * on its front and the console on it; its case view marks the gold slot; a tier 1 case's view
 * greys out what it leaves out of the board.
 */
public class CaseTierClientGameTest implements FabricClientGameTest {
	private static final int GOLD_TRIM = 0xD4AA28;
	private static final int CONNECTOR = 0x1A1A1E;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true)
			.adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			singleplayer.getConnection().waitForChunksRender();
			BlockPos feet = singleplayer.getServer().computeOnServer(server -> singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos tier2 = feet.north(2).above();
			BlockPos tier1 = tier2.east(2);
			singleplayer.getServer().runCommand("setblock %d %d %d modularcomputers:tier2_case[facing=south]".formatted(tier2.getX(), tier2.getY(), tier2.getZ()));
			singleplayer.getServer().runCommand("setblock %d %d %d modularcomputers:tier1_case[facing=south]".formatted(tier1.getX(), tier1.getY(), tier1.getZ()));
			singleplayer.getServer().runOnServer(server -> {
				var be = (AllInOneBlockEntity) server.overworld().getBlockEntity(tier2);
				be.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER2_CPU));
				be.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
				be.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
				be.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
				be.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModBlocks.TIER2_SCREEN));
				TestPrograms.flash(be, TestPrograms.HELLO);
				be.pressPower();
			});
			teleport(singleplayer, feet, 180);
			context.getInput().lookAt(tier2);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			context.waitFor(client -> client.level.getBlockEntity(tier2) instanceof AllInOneBlockEntity be
				&& client.level.getBlockState(tier2).getValue(AllInOneBlock.SCREEN) && be.terminal().buffer().row(0).contains("hello"));
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);
			context.takeScreenshot("case-tiers-front");

			// The tier 2 case view: the gold slot is trimmed.
			BufferedImage view2 = openCaseView(context, singleplayer, tier2, "case-tier2-view");
			int trim = pixel(context, view2, screen -> new int[] {screen.slotArea(CaseMenu.EXP_SLOTS).x() - 2, screen.slotArea(CaseMenu.EXP_SLOTS).y() + 4});
			check(trim == GOLD_TRIM, "the gold slot's trim, got " + Integer.toHexString(trim));
			closeScreen(context);

			// The tier 1 case view: its third and fourth expansion slots are greyed out.
			BufferedImage view1 = openCaseView(context, singleplayer, tier1, "case-tier1-view");
			int missing = pixel(context, view1, screen -> {
				var slot = screen.slotArea(CaseMenu.EXP_SLOTS + 3);
				return new int[] {slot.x() - 3, slot.y() + 8}; // on its connector
			});
			int present = pixel(context, view1, screen -> {
				var slot = screen.slotArea(CaseMenu.EXP_SLOTS + 1);
				return new int[] {slot.x() - 3, slot.y() + 8};
			});
			check(present == CONNECTOR, "a slot tier 1 has: its plain connector, got " + Integer.toHexString(present));
			check(missing != CONNECTOR, "a slot tier 1 leaves out is greyed over, got " + Integer.toHexString(missing));
			closeScreen(context);
		}
	}

	private static BufferedImage openCaseView(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos, String shot) {
		singleplayer.getServer().runOnServer(server ->
			singleplayer.getConnection().getServerPlayer().openMenu((AllInOneBlockEntity) server.overworld().getBlockEntity(pos)));
		context.waitForScreen(CaseScreen.class);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		return read(context.takeScreenshot(shot));
	}

	private static void closeScreen(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(client -> client.gui.screen() == null);
		context.waitTicks(6);
	}

	/** The screenshot's pixel at a GUI point of the case view. */
	private static int pixel(ClientGameTestContext context, BufferedImage image, java.util.function.Function<CaseScreen, int[]> at) {
		return context.computeOnClient(client -> {
			CaseScreen screen = (CaseScreen) client.gui.screen();
			double scale = (double) image.getWidth() / screen.width;
			int[] point = at.apply(screen);
			return image.getRGB((int) ((point[0] + 0.5) * scale), (int) ((point[1] + 0.5) * scale)) & 0xFFFFFF;
		});
	}
}
