package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.CaseClientGameTest.check;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.count;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.read;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.teleport;

import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.awt.image.BufferedImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * A tier 1 screen shows a VT102's attributes on its one phosphor: plain text, bold brighter, and
 * reverse video lighting its cells with the text dark on them (the user's report: they looked the
 * same).
 */
public class TerminalTierClientGameTest implements FabricClientGameTest {
	/** What's written: plain, bold, and reverse video, each a word, many times over so they're big on screen. */
	private static final String ATTRIBUTES = "\u001B[2J\u001B[H" + ("plain " + "\u001B[1mbold\u001B[0m " + "\u001B[7mreverse\u001B[0m\r\n").repeat(6);
	/** A lit phosphor cell's green, at least: reverse video's background. */
	private static final int LIT = 120;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true)
			.adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			singleplayer.getConnection().waitForChunksRender();
			BlockPos feet = singleplayer.getServer().computeOnServer(server -> singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos screen = feet.north(2).above();
			BlockPos computer = screen.west(2);
			setblock(singleplayer, computer, "modularcomputers:tier3_case[facing=south]");
			setblock(singleplayer, screen, "modularcomputers:tier1_screen[facing=south]");
			setblock(singleplayer, screen.west(), "modularcomputers:cable");
			singleplayer.getServer().runOnServer(server -> {
				var be = (CaseBlockEntity) server.overworld().getBlockEntity(computer);
				be.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
				be.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
				be.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
				be.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
				TestPrograms.flash(be, TestPrograms.HELLO);
				be.pressPower();
			});
			teleport(singleplayer, feet, 180);
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof ScreenBlockEntity shown && !shown.terminal().buffer().isBlank());
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).machine().board()
				.virtualBus().console().write(ATTRIBUTES));
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof ScreenBlockEntity shown && shown.terminal().buffer().row(0).startsWith("plain"));
			context.getInput().lookAt(screen);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);
			BufferedImage shot = read(context.takeScreenshot("tier1-attributes"));
			int lit = count(shot, 0, 0, shot.getWidth(), shot.getHeight(),
				rgb -> CaseClientGameTest.green(rgb) > LIT && CaseClientGameTest.red(rgb) < CaseClientGameTest.green(rgb) / 2);
			check(lit > 200, "reverse video lights its cells green on a tier 1 screen, found " + lit + " lit pixels");
			// Its view, close up: the text in tier 1's font (Unifont).
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(com.payonel.modularcomputers.client.screen.DisplayScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("tier1-view");
		}
	}

	private static void setblock(TestSingleplayerContext singleplayer, BlockPos pos, String block) {
		singleplayer.getServer().runCommand("setblock %d %d %d %s".formatted(pos.getX(), pos.getY(), pos.getZ(), block));
	}
}
