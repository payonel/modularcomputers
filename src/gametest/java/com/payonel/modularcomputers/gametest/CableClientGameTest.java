package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.CLICK_PAUSE;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.check;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.clickOnScreen;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.count;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.hex;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.isWhite;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.machinePowered;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.read;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.screenPixel;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CardHolderBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.client.Identify;
import com.payonel.modularcomputers.client.screen.CardHolderScreen;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PipeBlock;

/**
 * Client game test for screens and cables, with a real mouse: a computer wired to a screen
 * block shows hello world on it; the screen's own view has the computer's power button and no
 * case view; in the computer's bus order panel the plug disconnects and reconnects the screen,
 * and the bulb outlines it in the world.
 */
public class CableClientGameTest implements FabricClientGameTest {
	/** The bulb's outline color (DisplayRenderer). */
	private static final IntPredicate OUTLINE = rgb -> AllInOneClientGameTest.red(rgb) > 170 && AllInOneClientGameTest.green(rgb) > 140
		&& AllInOneClientGameTest.blue(rgb) < 110;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true)
			.adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			singleplayer.getConnection().waitForChunksRender();

			// A screen at eye level two blocks north of the player, facing them; a computer two
			// blocks west of it, with a cable between them.
			BlockPos feet = singleplayer.getServer().computeOnServer(server ->
				singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos screen = feet.north(2).above();
			BlockPos cable = screen.west();
			BlockPos computer = screen.west(2);
			setblock(singleplayer, computer, "all_in_one[facing=south]");
			setblock(singleplayer, screen, "screen[facing=south]");
			setblock(singleplayer, cable, "cable");
			singleplayer.getServer().runOnServer(server -> {
				var parts = ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)).parts();
				parts.setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.CPU));
				parts.setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
			});
			teleport(singleplayer, feet, 180);
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof ScreenBlockEntity);
			context.getInput().lookAt(screen);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle(); // no crosshair or hand over the screen
				}
			});
			context.waitFor(client -> client.level.getBlockState(cable).getValue(PipeBlock.WEST)
				&& client.level.getBlockState(cable).getValue(PipeBlock.EAST));
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);
			check(whiteOnScreen(read(context.takeScreenshot("screen-off"))) == 0, "an unconnected-to-power screen shows nothing");

			// The computer comes on: its console shows on the wired screen too.
			pressPower(singleplayer, computer);
			context.waitFor(client -> showsHello(client, screen));
			context.waitTicks(5);
			int lit = whiteOnScreen(read(context.takeScreenshot("screen-hello")));
			check(lit > 30, "the wired screen should show hello world on its face, found " + lit + " white pixels");

			// Its screen view: the computer's power button, no case view.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				DisplayScreen view = (DisplayScreen) client.gui.screen();
				check(view.powerButton().width() > 0, "a connected screen shows its computer's power button");
				check(view.viewToggle().width() == 0, "a screen block has no case view");
			});
			BufferedImage view = read(context.takeScreenshot("screen-view"));
			int viewText = context.computeOnClient(client -> {
				DisplayScreen shown = (DisplayScreen) client.gui.screen();
				double scale = (double) view.getWidth() / shown.width;
				var area = shown.viewport();
				return count(view, (int) (area.x() * scale), (int) (area.y() * scale), (int) (area.right() * scale),
					(int) ((area.y() + area.height() / 16.0) * scale), AllInOneClientGameTest::isWhite);
			});
			check(viewText > 50, "the screen view shows hello world, found " + viewText);
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((DisplayScreen) s).powerButton());
			context.waitFor(client -> !machinePowered(client, computer));
			context.waitFor(client -> !showsHello(client, screen));
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((DisplayScreen) s).powerButton());
			context.waitFor(client -> machinePowered(client, computer) && showsHello(client, screen));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);

			// The computer's case view lists the screen, with a bulb and a plug.
			singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
				.openMenu((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)));
			context.waitForScreen(CaseScreen.class);
			context.waitTicks(2);
			int row = context.computeOnClient(client -> {
				var entries = ((AllInOneBlockEntity) client.level.getBlockEntity(computer)).busEntries();
				for (int i = 0; i < entries.size(); i++) {
					if (entries.get(i).cabled()) {
						check(entries.get(i).name().getString().equals("Screen"), "the cabled row names the screen");
						check(entries.get(i).location().getString().equals("cable, 2m"), "and how far: " + entries.get(i).location().getString());
						return i;
					}
				}
				throw new AssertionError("the screen should be in the bus order panel");
			});
			BufferedImage plugged = read(context.takeScreenshot("case-screen-plugged"));
			int plugIn = screenPixel(context, plugged, s -> plugBody(((CaseScreen) s).busPlug(row), true));
			check((plugIn & 0xFFFFFF) == 0x3CB45A, "a connected screen's plug is in (green), got " + hex(plugIn));

			// The plug: out, and the screen goes blank; back in, and it shows the console again.
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((CaseScreen) s).busPlug(row));
			context.waitFor(client -> !showsHello(client, screen));
			context.waitTicks(2);
			BufferedImage unplugged = read(context.takeScreenshot("case-screen-unplugged"));
			int plugOut = screenPixel(context, unplugged, s -> plugBody(((CaseScreen) s).busPlug(row), false));
			check((plugOut & 0xFFFFFF) == 0x8C8C8C, "a disconnected screen's plug is out (grey), got " + hex(plugOut));
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((CaseScreen) s).busPlug(row));
			context.waitFor(client -> showsHello(client, screen));

			// The bulb: lit, and the screen is outlined in the world.
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((CaseScreen) s).busBulb(row));
			check(Identify.isOn(screen), "the bulb outlines the screen");
			BufferedImage bulbOn = read(context.takeScreenshot("case-bulb-on"));
			int bulb = screenPixel(context, bulbOn, s -> ((CaseScreen) s).busBulb(row));
			check((bulb & 0xFFFFFF) == 0xFFE066, "the bulb is lit, got " + hex(bulb));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.waitTicks(3);
			BufferedImage outlined = read(context.takeScreenshot("screen-identified"));
			int outline = count(outlined, 0, 0, outlined.getWidth(), outlined.getHeight(), OUTLINE);
			check(outline > 400, "the screen should be outlined in the world, found " + outline + " outline pixels");
			Identify.toggle(screen);
			context.waitTicks(3);
			int after = count(read(context.takeScreenshot("screen-not-identified")), 0, 0, outlined.getWidth(), outlined.getHeight(), OUTLINE);
			check(after < outline / 10, "the outline goes away when the bulb is turned off, found " + after);

			// An expansion hub's four card slots and a robotic piston's one.
			BlockPos hub = feet.east(3);
			BlockPos piston = feet.west(3);
			setblock(singleplayer, hub, "expansion_hub");
			setblock(singleplayer, piston, "robotic_piston");
			for (BlockPos holder : List.of(hub, piston)) {
				singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
					.openMenu((CardHolderBlockEntity) server.overworld().getBlockEntity(holder)));
				context.waitForScreen(CardHolderScreen.class);
				context.waitTicks(2);
				int slots = context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().cardSlots());
				check(slots == (holder == hub ? 4 : 1), "card slots: " + slots);
				context.takeScreenshot(holder == hub ? "expansion-hub" : "robotic-piston");
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitFor(client -> client.gui.screen() == null);
			}
		}
	}

	private static void setblock(TestSingleplayerContext singleplayer, BlockPos pos, String block) {
		singleplayer.getServer().runCommand("setblock %d %d %d modularcomputers:%s".formatted(pos.getX(), pos.getY(), pos.getZ(), block));
	}

	private static void pressPower(TestSingleplayerContext singleplayer, BlockPos computer) {
		singleplayer.getServer().runOnServer(server -> ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)).pressPower());
	}

	private static boolean showsHello(Minecraft client, BlockPos screen) {
		return client.level.getBlockEntity(screen) instanceof ScreenBlockEntity display && display.display().buffer().row(0).contains("hello");
	}

	/** A pixel of the plug's body: higher up when it's pulled out (CaseScreen.extractPlug). */
	private static com.payonel.modularcomputers.display.Viewport plugBody(com.payonel.modularcomputers.display.Viewport plug, boolean in) {
		return new com.payonel.modularcomputers.display.Viewport(plug.x() + 5, plug.y() + (in ? 4 : 2), 1, 1);
	}

	/** White text pixels on the screen block's glass, which is in the middle of the frame. */
	private static int whiteOnScreen(BufferedImage image) {
		int cx = image.getWidth() / 2;
		int cy = image.getHeight() / 2;
		return count(image, cx - 140, cy - 140, cx + 140, cy + 60, AllInOneClientGameTest::isWhite);
	}
}
