package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.CaseClientGameTest.CLICK_PAUSE;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.check;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.clickOnScreen;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.count;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.hex;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.isWhite;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.machinePowered;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.moveCursorTo;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.read;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.screenPixel;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.Bluetooth;
import com.payonel.modularcomputers.block.CardHolderBlockEntity;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.client.Identify;
import com.payonel.modularcomputers.client.screen.CardHolderScreen;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.menu.ModMenus;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PipeBlock;

/**
 * Client game test for screens and cables, with a real mouse: a computer wired to a screen
 * block shows hello world on it; the screen's own view has the computer's power button and no
 * case view; in the computer's bus order panel the plug disconnects and reconnects the screen,
 * and the bulb outlines it in the world.
 */
public class CableClientGameTest implements FabricClientGameTest {
	/** The Bluetooth outline's color (DisplayRenderer, OutlineRenderer). */
	private static final IntPredicate BLUE = rgb -> CaseClientGameTest.blue(rgb) > 200 && CaseClientGameTest.red(rgb) < 120
		&& CaseClientGameTest.green(rgb) > 100 && CaseClientGameTest.green(rgb) < 190;
	/** The bulb's outline color (DisplayRenderer). */
	/** The pulsing yellow outline, by hue: it blends with the frame under it, beige or graphite. */
	private static final IntPredicate OUTLINE = rgb -> CaseClientGameTest.red(rgb) > 110 && CaseClientGameTest.green(rgb) > 90
		&& CaseClientGameTest.red(rgb) >= CaseClientGameTest.green(rgb) && CaseClientGameTest.blue(rgb) < CaseClientGameTest.red(rgb) * 0.55;

	/** The test picture's side, in pixels. */
	private static final int PICTURE_SIZE = 36;
	/** Ticks a card put in a running computer may take to be on its bus. */
	private static final int SOUND_CARD_TICKS = 40;

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
			setblock(singleplayer, computer, "tier3_case[facing=south]");
			setblock(singleplayer, screen, "tier3_screen[facing=south]");
			setblock(singleplayer, cable, "cable");
			singleplayer.getServer().runOnServer(server -> {
				var be = (CaseBlockEntity) server.overworld().getBlockEntity(computer);
				var parts = be.parts();
				parts.setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
				parts.setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
				parts.setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
				parts.setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
				TestPrograms.flash(be, TestPrograms.HELLO);
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

			// A tier 3 screen draws pixels: a sixel picture, a magenta square under the greeting.
			int[] picture = new int[PICTURE_SIZE * PICTURE_SIZE];
			java.util.Arrays.fill(picture, 0xFFFF00FF);
			String sixel = "\u001B[6;3H" + com.payonel.modularcomputers.display.Sixel.encode(picture, PICTURE_SIZE);
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).machine().board()
				.virtualBus().console().write(sixel)); // (as a program's output goes: the console, so its repaints keep it)
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof ScreenBlockEntity shown && shown.terminal().buffer().hasTiles());
			context.waitTicks(5);
			BufferedImage drawn = read(context.takeScreenshot("screen-sixel"));
			int cx = drawn.getWidth() / 2;
			int cy = drawn.getHeight() / 2;
			int magenta = count(drawn, cx - 140, cy - 140, cx + 140, cy + 60, rgb -> CaseClientGameTest.red(rgb) > 200
				&& CaseClientGameTest.green(rgb) < 60 && CaseClientGameTest.blue(rgb) > 200);
			check(magenta > 30, "the screen's face shows its picture, found " + magenta + " magenta pixels");

			// A Sound Card in it plays stereo: two speakers, its left and right as seen facing its
			// front (it faces south: its left is west), half a block either side.
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).parts()
				.setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.SOUND_CARD)));
			for (int tick = 0; !singleplayer.getServer().computeOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer))
				.machine().board().bus().devices().stream().anyMatch(com.payonel.modularcomputers.device.SoundCard.class::isInstance)); tick++) {
				check(tick < SOUND_CARD_TICKS, "the sound card on the bus");
				context.waitTicks(1);
			}
			singleplayer.getServer().runOnServer(server -> {
				var card = (com.payonel.modularcomputers.device.SoundCard) ((CaseBlockEntity) server.overworld().getBlockEntity(computer))
					.machine().board().bus().devices().stream().filter(com.payonel.modularcomputers.device.SoundCard.class::isInstance).findFirst().orElseThrow();
				card.setFormat(new com.payonel.modularcomputers.runtime.Arguments(java.util.List.of(java.util.Map.of("rate", 22050L, "encoding", "s16le", "channels", 2L))));
				card.write(new com.payonel.modularcomputers.runtime.Arguments(java.util.List.of("\0".repeat(4 * 22050 / 2)))); // half a second
			});
			context.waitFor(client -> com.payonel.modularcomputers.client.sound.SoundCardSpeakers.playing().size() == 2);
			context.runOnClient(client -> {
				var speakers = com.payonel.modularcomputers.client.sound.SoundCardSpeakers.playing();
				double center = computer.getX() + 0.5;
				check(Math.abs(speakers.get(0).x - (center - 0.5)) < 1e-6 && Math.abs(speakers.get(1).x - (center + 0.5)) < 1e-6,
					"the left channel's speaker west of it, the right's east: " + speakers);
				check(speakers.stream().allMatch(at -> Math.abs(at.z - (computer.getZ() + 0.5)) < 1e-6), "in line with its center: " + speakers);
			});
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).parts()
				.setItem(CaseMenu.EXP_SLOTS + 1, ItemStack.EMPTY)); // (what follows counts the bus without it)

			// With the battery out the case's own power runs down, and clients see it drop.
			singleplayer.getServer().runOnServer(server -> {
				CaseBlockEntity be = (CaseBlockEntity) server.overworld().getBlockEntity(computer);
				be.power().set(PowerBuffer.CAPACITY / 2 + 10); // just over 50%: the next draw crosses it
				be.parts().setItem(CaseMenu.UPS_SLOT, ItemStack.EMPTY);
			});
			context.waitFor(client -> casePercent(client, computer) == 51);
			context.waitFor(client -> casePercent(client, computer) == 50);

			// Its screen view: the display, and the column's view buttons (this view's, its slot view's).
			// The power button is the computer case's.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				DisplayScreen view = (DisplayScreen) client.gui.screen();
				check(view.powerButton().width() == 0, "a screen block has no power button");
				check(view.screenButton().width() > 0 && view.partsButton().y() > view.screenButton().y(), "a screen block has its view's button, its slot view's under it");
			});
			BufferedImage view = read(context.takeScreenshot("screen-view"));
			int viewMagenta = count(view, 0, 0, view.getWidth(), view.getHeight(), rgb -> CaseClientGameTest.red(rgb) > 200
				&& CaseClientGameTest.green(rgb) < 60 && CaseClientGameTest.blue(rgb) > 200);
			check(viewMagenta > 100, "the screen view shows its picture, found " + viewMagenta);
			int viewText = context.computeOnClient(client -> {
				DisplayScreen shown = (DisplayScreen) client.gui.screen();
				double scale = (double) view.getWidth() / shown.width;
				var area = shown.viewport();
				return count(view, (int) (area.x() * scale), (int) (area.y() * scale), (int) (area.right() * scale),
					(int) ((area.y() + area.height() / 16.0) * scale), CaseClientGameTest::isWhite);
			});
			check(viewText > 50, "the screen view shows hello world, found " + viewText);
			// At tier 3's own size (160x50, as it powers on), its view's text: Unifont, crisp, where the
			// cells are drawn big (the user's report: the default font, magnified, was blocky).
			String page = "\u001B[8;50;160t\u001B[2J\u001B[H" + ("The quick brown fox jumps over the lazy dog. 0123456789 ~!@#$%^&*()_+ "
				+ "Modular Computers, tier 3: 160 by 50.   ").repeat(2).substring(0, 158).concat("\r\n").repeat(12);
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).machine().board()
				.virtualBus().console().write(page));
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof com.payonel.modularcomputers.block.ScreenBlockEntity shown
				&& shown.terminal().buffer().resolution().columns() == 160 && shown.terminal().buffer().row(0).startsWith("The quick"));
			context.waitTicks(2);
			context.takeScreenshot("screen-view-160x50");
			// And on a 1080p window, as players' are: the cells drawn big enough for Unifont, whole pixels a texel.
			context.runOnClient(client -> client.getWindow().setWindowed(1920, 1080));
			context.waitTicks(5);
			context.takeScreenshot("screen-view-160x50-1080p");
			context.runOnClient(client -> {
				DisplayScreen shown = (DisplayScreen) client.gui.screen();
				var area = shown.viewport();
				double scale = client.getWindow().getGuiScale();
				var text = com.payonel.modularcomputers.client.render.TerminalFonts.forView(com.payonel.modularcomputers.device.TerminalTier.TIER_3,
					area.width(), area.height(), new com.payonel.modularcomputers.display.Resolution(160, 50), client.font, scale);
				double pixels = text.grid().fontScale() * 0.5 * scale; // (Unifont's texel: half a font unit)
				check(!text.style().equals(net.minecraft.network.chat.Style.EMPTY), "160x50 on a 1080p window: Unifont, its detail on screen");
				check(Math.abs(pixels - Math.rint(pixels)) < 1e-6 && pixels >= 1, "whole pixels a texel (crisp), was " + pixels);
			});
			context.runOnClient(client -> client.getWindow().setWindowed(854, 480));
			context.waitTicks(5);
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).machine().board()
				.virtualBus().console().write("\u001B[8;16;50t\u001B[2J\u001B[Hhello world\r\n")); // (back as the test had it)
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof com.payonel.modularcomputers.block.ScreenBlockEntity shown
				&& shown.terminal().buffer().resolution().columns() == 50);
			// UTF-8 as a program writes it (bytes): box drawing, CJK and emoji two cells wide, an accent
			// combined, DEC's line drawing.
			String unicode = "\u001B[2J\u001B[H\u250F\u2501\u2501\u2501\u2513 \u4E2D\u6587 \uD83D\uDE00 caf\u00E9 cafe\u0301\r\n"
				+ "\u2503ok \u2503 \u65E5\u672C\u8A9E \u2500\u2502\u253C \u03C0\u2248\u221E\r\n\u2517\u2501\u2501\u2501\u251B "
				+ "\u001B(0lqqk\u001B(B\r\n";
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).machine().board()
				.virtualBus().console().write(com.payonel.modularcomputers.device.Terminal.utf8(unicode)));
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof com.payonel.modularcomputers.block.ScreenBlockEntity shown
				&& shown.terminal().buffer().get(6, 0) == 0x4E2D);
			context.runOnClient(client -> {
				var cells = ((com.payonel.modularcomputers.block.ScreenBlockEntity) client.level.getBlockEntity(screen)).terminal().buffer();
				check(cells.get(7, 0) == com.payonel.modularcomputers.display.TextBuffer.WIDE && cells.get(8, 0) == 0x6587,
					"the client's copy: 中 two cells, then 文");
				check(cells.get(0, 2) == 0x2517 && cells.get(6, 2) == 0x250C, "box drawing, and DEC's line drawing as box drawing");
			});
			context.waitTicks(2);
			context.takeScreenshot("screen-view-utf8");
			// The shapes it draws itself: blocks and shades, quadrants, braille, double lines, dashes, diagonals, arcs.
			String shapes = "\u001B[2J\u001B[H\u2580\u2584\u2588\u258C\u2590 \u2591\u2592\u2593 \u2596\u2597\u2598\u259D \u28FF\u283F\u2847\u2880\r\n"
				+ "\u2554\u2550\u2566\u2550\u2557 \u250C\u2504\u2510 \u2571\u2572\u2573 \u256D\u2500\u256E\r\n"
				+ "\u2560\u2550\u256C\u2550\u2563 \u2506 \u2506 \u2588\u2588\u2588 \u2502 \u2502\r\n"
				+ "\u255A\u2550\u2569\u2550\u255D \u2514\u2504\u2518     \u2570\u2500\u256F\r\n";
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).machine().board()
				.virtualBus().console().write(com.payonel.modularcomputers.device.Terminal.utf8(shapes)));
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof com.payonel.modularcomputers.block.ScreenBlockEntity shown
				&& shown.terminal().buffer().get(0, 1) == 0x2554);
			context.waitTicks(2);
			context.takeScreenshot("screen-view-shapes");
			pressPower(singleplayer, computer);
			context.waitFor(client -> !machinePowered(client, computer) && !showsHello(client, screen));
			pressPower(singleplayer, computer);
			context.waitFor(client -> machinePowered(client, computer) && showsHello(client, screen));

			// Its parts view's button opens the screen's radio slot, and the screen view's back, straight from view to view.
			int grabs = context.computeOnClient(client -> MouseGrabs.count);
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((DisplayScreen) s).partsButton());
			context.waitForScreen(CardHolderScreen.class);
			context.waitTicks(2);
			check(context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().cardSlots()) == 1, "the screen's one slot");
			context.takeScreenshot("screen-slot");
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((CardHolderScreen) s).screenButton());
			context.waitForScreen(DisplayScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				check(MouseGrabs.count == grabs, "switching views should not grab the mouse (it recenters the cursor)");
				check(client.player.containerMenu == client.player.inventoryMenu, "the slot's menu should be closed");
			});
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);

			// The computer's case view lists the screen, with a bulb and a plug.
			singleplayer.getServer().runOnServer(server -> ModMenus.open(server.getPlayerList().getPlayers().getFirst(),
				server.overworld().getBlockEntity(computer)));
			context.waitForScreen(CaseScreen.class);
			context.waitTicks(2);
			int row = context.computeOnClient(client -> {
				var entries = ((CaseBlockEntity) client.level.getBlockEntity(computer)).busEntries();
				for (int i = 0; i < entries.size(); i++) {
					if (entries.get(i).cabled()) {
						check(entries.get(i).name().getString().equals("Tier 3 Screen"), "the cabled row names the screen");
						check(entries.get(i).location().getString().equals("cable, 2m"), "and how far: " + entries.get(i).location().getString());
						return i;
					}
				}
				throw new AssertionError("the screen should be in the bus order panel");
			});
			// Each button names itself in one word when hovered.
			check(tipAt(context, s -> ((CaseScreen) s).busBulb(row)).equals("View"), "the bulb's tip");
			check(tipAt(context, s -> ((CaseScreen) s).busPlug(row)).equals("Disconnect"), "the plug's tip");
			check(tipAt(context, s -> ((CaseScreen) s).busArrow(row, true)).isEmpty(), "no tip on an arrow that can't move");
			check(tipAt(context, s -> ((CaseScreen) s).busArrow(0, true)).isEmpty(), "nor on the case's rows");
			moveCursorTo(context, s -> ((CaseScreen) s).busPlug(row));
			context.waitTicks(2);
			context.takeScreenshot("case-plug-tip");
			context.getInput().setCursorPos(0, 0);
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

			// A full bus: with room for just one external device, a second screen has to wait.
			// The warning light comes on.
			int ledOff = screenPixel(context, bulbOn, s -> ((CaseScreen) s).busOverflowLed());
			check(summary(context, computer).equals("bus: 2 of 64 (1 internal, 1 external)"), "the summary line, got " + summary(context, computer));
			check((ledOff & 0xFFFFFF) == 0x3A2E1A, "the overflow light is dark, got " + hex(ledOff));
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer))
				.machine().board().overrideBusCapacity(2)); // its ROM, and room for one
			BlockPos second = screen.east();
			setblock(singleplayer, second, "tier3_screen[facing=south]");
			context.waitFor(client -> client.level.getBlockEntity(computer) instanceof CaseBlockEntity be && be.busStatus().overflow());
			context.waitTicks(2);
			BufferedImage full = read(context.takeScreenshot("case-bus-full"));
			int ledOn = screenPixel(context, full, s -> ((CaseScreen) s).busOverflowLed());
			check((ledOn & 0xFFFFFF) == 0xF0A020, "the overflow light is lit, got " + hex(ledOn));
			check(context.computeOnClient(client -> ((CaseBlockEntity) client.level.getBlockEntity(computer)).busStatus().waiting()) == 1,
				"one screen waits");
			check(summary(context, computer).equals("bus: 2 of 2 (1 internal, 1 external), overflow 1"), "the summary says so, got " + summary(context, computer));
			setblock(singleplayer, second, "minecraft:air");
			singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer))
				.machine().board().overrideBusCapacity(null));
			context.waitFor(client -> !((CaseBlockEntity) client.level.getBlockEntity(computer)).busStatus().overflow());

			// Cut the cable: the screen stays listed, offline, its row's display gray.
			setblock(singleplayer, cable, "minecraft:air");
			context.waitFor(client -> ((CaseBlockEntity) client.level.getBlockEntity(computer)).busEntries().get(row).online() == false);
			context.waitTicks(2);
			BufferedImage offline = read(context.takeScreenshot("case-screen-offline"));
			int gray = screenPixel(context, offline, s -> {
				Viewport display = ((CaseScreen) s).busRowDisplay(row);
				return new Viewport(display.x() + 50, display.y() + 4, 1, 1);
			});
			check((gray & 0xFFFFFF) == 0x4E4E52, "an offline row's display is gray, got " + hex(gray));
			check(tipAt(context, s -> ((CaseScreen) s).busPlug(row)).equals("Forget"), "and its plug is a forget button");
			check(summary(context, computer).equals("bus: 1 of 64 (1 internal, 0 external), missing 1"), "missing in the summary, got " + summary(context, computer));
			setblock(singleplayer, cable, "cable");
			context.waitFor(client -> ((CaseBlockEntity) client.level.getBlockEntity(computer)).busEntries().get(row).online());
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

			// Holding a linked Bluetooth card outlines the block whose board it links to, in blue.
			BlockPos linked = screen.east(2);
			setblock(singleplayer, linked, "robotic_piston");
			UUID address = singleplayer.getServer().computeOnServer(server -> {
				RoboticPistonBlockEntity piston = (RoboticPistonBlockEntity) server.overworld().getBlockEntity(linked);
				piston.cards().setItem(0, new ItemStack(ModItems.BLUETOOTH_CARD)); // its radio
				ItemStack held = new ItemStack(ModItems.BLUETOOTH_CARD);
				held.set(ModComponents.BLUETOOTH_LINK, piston.boardId());
				server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, held);
				return piston.boardId();
			});
			context.waitFor(client -> client.player.getMainHandItem().has(ModComponents.BLUETOOTH_LINK)
				&& Bluetooth.board(client.level, address).filter(linked::equals).isPresent());
			context.waitTicks(3);
			BufferedImage holding = read(context.takeScreenshot("bluetooth-target"));
			int blue = count(holding, 0, 0, holding.getWidth(), holding.getHeight(), BLUE);
			check(blue > 300, "the linked piston should be outlined in blue, found " + blue);
			singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
				.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
			context.waitFor(client -> client.player.getMainHandItem().isEmpty());
			context.waitTicks(3);
			BufferedImage notHolding = read(context.takeScreenshot("bluetooth-no-card"));
			int none = count(notHolding, 0, 0, notHolding.getWidth(), notHolding.getHeight(), BLUE);
			check(none < blue / 10, "no card in hand, no outline, found " + none);

			// An expansion hub's four card slots and a robotic piston's one.
			BlockPos hub = feet.east(3);
			BlockPos piston = feet.west(3);
			setblock(singleplayer, hub, "expansion_hub");
			setblock(singleplayer, piston, "robotic_piston");
			for (BlockPos holder : List.of(hub, piston)) {
				singleplayer.getServer().runOnServer(server -> ModMenus.open(server.getPlayerList().getPlayers().getFirst(),
					server.overworld().getBlockEntity(holder)));
				context.waitForScreen(CardHolderScreen.class);
				context.waitTicks(2);
				int slots = context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().cardSlots());
				check(slots == (holder == hub ? 5 : 1), "card slots: " + slots);
				// Carrying a Bluetooth card, only the radio slot lights up as where it goes.
				List<Integer> radio = context.computeOnClient(client -> {
					var menu = ((CardHolderScreen) client.gui.screen()).getMenu();
					menu.setCarried(new ItemStack(ModItems.BLUETOOTH_CARD));
					List<Integer> fit = com.payonel.modularcomputers.client.screen.SlotHints.fitting(menu, null, menu.cardSlots()).stream()
						.map(slot -> slot.index).toList();
					menu.setCarried(ItemStack.EMPTY);
					return fit;
				});
				int radioSlot = context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().radioSlot());
				check(radio.equals(List.of(radioSlot)), "a Bluetooth card fits the radio slot: " + radio);
				context.takeScreenshot(holder == hub ? "expansion-hub" : "robotic-piston");
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitFor(client -> client.gui.screen() == null);
			}

			// A photomask where the screen was: its brass gear shows on its front, and turns when cranked.
			setblock(singleplayer, screen, "photomask[facing=south]");
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof com.payonel.modularcomputers.block.PhotomaskBlockEntity);
			context.waitTicks(3);
			BufferedImage still = read(context.takeScreenshot("photomask"));
			int brass = count(still, 0, 0, still.getWidth(), still.getHeight(), BRASS);
			check(brass > 200, "the photomask's gear should show, found " + brass + " brass pixels");
			singleplayer.getServer().runOnServer(server ->
				((com.payonel.modularcomputers.block.PhotomaskBlockEntity) server.overworld().getBlockEntity(screen)).crank());
			context.waitTicks(1);
			BufferedImage turning = read(context.takeScreenshot("photomask-turning"));
			context.waitTicks(4);
			BufferedImage turned = read(context.takeScreenshot("photomask-turned"));
			check(differ(still, turned) > 50, "cranked, the gear turns: " + differ(still, turned) + " pixels changed");
			check(turning.getWidth() == turned.getWidth(), "frames");
			// Its slots: silicon and batteries light up where they go; nothing goes in the chips' slot. Turned by
			// hand, its buffer charges, and a photomask item to hover for its hint.
			singleplayer.getServer().runOnServer(server -> {
				var mask = (com.payonel.modularcomputers.block.PhotomaskBlockEntity) server.overworld().getBlockEntity(screen);
				for (int i = 0; i < 4; i++) {
					mask.crank();
				}
				var player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().add(new ItemStack(com.payonel.modularcomputers.block.ModBlocks.PHOTOMASK));
				ModMenus.open(player, mask);
			});
			context.waitForScreen(com.payonel.modularcomputers.client.screen.PhotomaskScreen.class);
			List<List<Integer>> fits = context.computeOnClient(client -> {
				var menu = ((com.payonel.modularcomputers.client.screen.PhotomaskScreen) client.gui.screen()).getMenu();
				List<List<Integer>> out = new java.util.ArrayList<>();
				for (ItemStack carried : List.of(new ItemStack(ModItems.SILICON), ModItems.TIER1_BATTERY.charged(), new ItemStack(ModItems.CHIP))) {
					menu.setCarried(carried);
					out.add(com.payonel.modularcomputers.client.screen.SlotHints.fitting(menu, null,
						com.payonel.modularcomputers.block.PhotomaskBlockEntity.SLOTS).stream().map(slot -> slot.index).toList());
				}
				menu.setCarried(ItemStack.EMPTY);
				return out;
			});
			check(fits.equals(List.of(List.of(com.payonel.modularcomputers.block.PhotomaskBlockEntity.INPUT),
				List.of(com.payonel.modularcomputers.block.PhotomaskBlockEntity.BATTERY), List.of())), "where things go in a photomask: " + fits);
			context.takeScreenshot("photomask-slots");
			context.waitFor(client -> ((com.payonel.modularcomputers.client.screen.PhotomaskScreen) client.gui.screen()).getMenu().charge() > 0);
			moveCursorTo(context, s2 -> ((com.payonel.modularcomputers.client.screen.PhotomaskScreen) s2).meter());
			context.waitTicks(2);
			context.takeScreenshot("photomask-charge");
			int maskSlot = context.computeOnClient(client -> {
				var menu = ((com.payonel.modularcomputers.client.screen.PhotomaskScreen) client.gui.screen()).getMenu();
				for (var slot : menu.slots) {
					if (slot.getItem().is(com.payonel.modularcomputers.block.ModBlocks.PHOTOMASK.asItem())) {
						return slot.index;
					}
				}
				throw new AssertionError("no photomask in the inventory");
			});
			moveCursorTo(context, s2 -> ((com.payonel.modularcomputers.client.screen.PhotomaskScreen) s2).slotArea(maskSlot));
			com.payonel.modularcomputers.client.hint.HintComponent.detailsOverride = true;
			context.waitTicks(2);
			context.takeScreenshot("photomask-hint");
			context.waitTicks(20);
			context.takeScreenshot("photomask-hint-later");
			com.payonel.modularcomputers.client.hint.HintComponent.detailsOverride = null;
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);

			// A Disk Station where the screen was: iron and emerald, its two bays on its front.
			setblock(singleplayer, screen, "disk_station[facing=south]");
			context.waitFor(client -> client.level.getBlockEntity(screen) instanceof com.payonel.modularcomputers.block.DiskStationBlockEntity);
			context.waitTicks(3);
			BufferedImage station = read(context.takeScreenshot("disk-station"));
			int emerald = count(station, 0, 0, station.getWidth(), station.getHeight(), EMERALD);
			check(emerald > 40, "the station's emerald should show, found " + emerald);
			// Its GUI: a disk and a battery in it, the switches down the left, the source bay faint and locked.
			singleplayer.getServer().runOnServer(server -> {
				var bay = (com.payonel.modularcomputers.block.DiskStationBlockEntity) server.overworld().getBlockEntity(screen);
				bay.cards().setItem(com.payonel.modularcomputers.block.DiskStationBlockEntity.TARGET, new ItemStack(ModItems.TIER2_HARD_DISK));
				bay.cards().setItem(com.payonel.modularcomputers.block.DiskStationBlockEntity.BATTERY, ModItems.TIER1_BATTERY.charged());
				ModMenus.open(server.getPlayerList().getPlayers().getFirst(), bay);
			});
			context.waitForScreen(com.payonel.modularcomputers.client.screen.DiskStationScreen.class);
			context.waitTicks(2);
			BufferedImage gui = read(context.takeScreenshot("disk-station-gui"));
			int inlay = count(gui, 0, 0, gui.getWidth(), gui.getHeight(), EMERALD);
			check(inlay > 60, "the station's panel shows its emerald inlay, found " + inlay);
			moveCursorTo(context, s2 -> ((com.payonel.modularcomputers.client.screen.DiskStationScreen) s2).switchArea(
				com.payonel.modularcomputers.menu.DiskStationMenu.ACTIONS.indexOf("install")));
			context.waitTicks(2);
			context.takeScreenshot("disk-station-gui-tip");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);

			// A broken computer from the ancient sites: cracked open, its board showing.
			setblock(singleplayer, screen, "broken_case[facing=south]");
			context.waitTicks(3);
			BufferedImage broken = read(context.takeScreenshot("broken-case"));
			int board = count(broken, 0, 0, broken.getWidth(), broken.getHeight(),
				rgb -> CaseClientGameTest.green(rgb) > CaseClientGameTest.red(rgb) + 25 && CaseClientGameTest.green(rgb) > CaseClientGameTest.blue(rgb) + 15
					&& CaseClientGameTest.green(rgb) < 140);
			check(board > 40, "the broken computer's board shows through, found " + board);

			// A robotic sticky piston, its cable away from us, pushing to the side: its arm comes out, and goes back.
			setblock(singleplayer, screen, "robotic_sticky_piston[facing=north]");
			context.waitTicks(3);
			BufferedImage before = read(context.takeScreenshot("robotic-piston-in"));
			String pushed = singleplayer.getServer().computeOnServer(server -> String.valueOf(((com.payonel.modularcomputers.block.RoboticPistonBlockEntity) server.overworld().getBlockEntity(screen))
				.device().push(new com.payonel.modularcomputers.runtime.Arguments(List.of("east")))));
			check(pushed.contains("true"), "pushed: " + pushed);
			context.waitFor(client -> client.level.getBlockState(screen.east()).is(com.payonel.modularcomputers.block.ModBlocks.ROBOTIC_PISTON_ARM));
			context.waitTicks(2);
			BufferedImage out = read(context.takeScreenshot("robotic-piston-out"));
			check(differ(before, out) > 500, "its arm comes out: " + differ(before, out) + " pixels changed");
			context.waitFor(client -> client.level.getBlockState(screen.east()).isAir());
		}
	}

	/** The gear's brass. */
	/** The Disk Station's emerald: strongly green, as its trim and its GUI's inlay are. */
	private static final IntPredicate EMERALD = rgb -> CaseClientGameTest.green(rgb) > 110 && CaseClientGameTest.green(rgb) > CaseClientGameTest.red(rgb) + 60
		&& CaseClientGameTest.green(rgb) > CaseClientGameTest.blue(rgb) + 30;
	private static final IntPredicate BRASS = rgb -> CaseClientGameTest.red(rgb) > 60
		&& CaseClientGameTest.red(rgb) > CaseClientGameTest.green(rgb) + 12 && CaseClientGameTest.green(rgb) > CaseClientGameTest.blue(rgb) + 15;

	/** How many pixels differ between two screenshots of the same view. */
	private static int differ(BufferedImage a, BufferedImage b) {
		int changed = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					changed++;
				}
			}
		}
		return changed;
	}

	/** Sets a block by command: a mod block by its path ("tier3_screen[facing=south]"), anything else by its full id. */
	private static void setblock(TestSingleplayerContext singleplayer, BlockPos pos, String block) {
		String id = block.contains(":") ? block : "modularcomputers:" + block;
		singleplayer.getServer().runCommand("setblock %d %d %d %s".formatted(pos.getX(), pos.getY(), pos.getZ(), id));
	}

	/** The bus order panel's summary line, as the client would draw it now. */
	private static String summary(ClientGameTestContext context, BlockPos computer) {
		return context.computeOnClient(client -> CaseScreen.busSummary(((CaseBlockEntity) client.level.getBlockEntity(computer)).busStatus()).getString());
	}

	/** The bus order panel's tip for the middle of an area, or "" for none. */
	private static String tipAt(ClientGameTestContext context, java.util.function.Function<net.minecraft.client.gui.screens.Screen, Viewport> area) {
		return context.computeOnClient(client -> {
			Viewport target = area.apply(client.gui.screen());
			return ((CaseScreen) client.gui.screen()).busButtonTip(target.x() + target.width() / 2.0, target.y() + target.height() / 2.0)
				.map(tip -> tip.getString()).orElse("");
		});
	}

		private static int casePercent(Minecraft client, BlockPos computer) {
		return client.level.getBlockEntity(computer) instanceof CaseBlockEntity be ? be.power().percent() : -1;
	}

		private static void pressPower(TestSingleplayerContext singleplayer, BlockPos computer) {
		singleplayer.getServer().runOnServer(server -> ((CaseBlockEntity) server.overworld().getBlockEntity(computer)).pressPower());
	}

	private static boolean showsHello(Minecraft client, BlockPos screen) {
		return client.level.getBlockEntity(screen) instanceof ScreenBlockEntity display && display.terminal().buffer().row(0).contains("hello");
	}

	/** A pixel of the plug's body: higher up when it's pulled out (CaseScreen.extractPlug). */
	private static com.payonel.modularcomputers.display.Viewport plugBody(com.payonel.modularcomputers.display.Viewport plug, boolean in) {
		return new com.payonel.modularcomputers.display.Viewport(plug.x() + 5, plug.y() + (in ? 4 : 2), 1, 1);
	}

	/** White text pixels on the screen block's glass, which is in the middle of the frame. */
	private static int whiteOnScreen(BufferedImage image) {
		int cx = image.getWidth() / 2;
		int cy = image.getHeight() / 2;
		return count(image, cx - 140, cy - 140, cx + 140, cy + 60, CaseClientGameTest::isWhite);
	}
}
