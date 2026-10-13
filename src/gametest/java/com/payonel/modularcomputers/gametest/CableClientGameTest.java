package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.CLICK_PAUSE;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.check;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.clickOnScreen;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.count;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.hex;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.isWhite;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.machinePowered;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.moveCursorTo;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.read;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.screenPixel;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
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
	private static final IntPredicate BLUE = rgb -> AllInOneClientGameTest.blue(rgb) > 200 && AllInOneClientGameTest.red(rgb) < 120
		&& AllInOneClientGameTest.green(rgb) > 100 && AllInOneClientGameTest.green(rgb) < 190;
	/** The bulb's outline color (DisplayRenderer). */
	/** The pulsing yellow outline, by hue: it blends with the frame under it, beige or graphite. */
	private static final IntPredicate OUTLINE = rgb -> AllInOneClientGameTest.red(rgb) > 110 && AllInOneClientGameTest.green(rgb) > 90
		&& AllInOneClientGameTest.red(rgb) >= AllInOneClientGameTest.green(rgb) && AllInOneClientGameTest.blue(rgb) < AllInOneClientGameTest.red(rgb) * 0.55;

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
			setblock(singleplayer, screen, "tier3_screen[facing=south]");
			setblock(singleplayer, cable, "cable");
			singleplayer.getServer().runOnServer(server -> {
				var be = (AllInOneBlockEntity) server.overworld().getBlockEntity(computer);
				var parts = be.parts();
				parts.setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
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

			// With the battery out the case's own power runs down, and clients see it drop.
			singleplayer.getServer().runOnServer(server -> {
				AllInOneBlockEntity be = (AllInOneBlockEntity) server.overworld().getBlockEntity(computer);
				be.power().set(PowerBuffer.CAPACITY / 2 + 10); // just over 50%: the next draw crosses it
				be.parts().setItem(CaseMenu.UPS_SLOT, ItemStack.EMPTY);
			});
			context.waitFor(client -> casePercent(client, computer) == 51);
			context.waitFor(client -> casePercent(client, computer) == 50);

			// Its screen view: the display and the toggle to its slot. The power button is the
			// computer case's.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				DisplayScreen view = (DisplayScreen) client.gui.screen();
				check(view.powerButton().width() == 0, "a screen block has no power button");
				check(view.viewToggle().width() > 0, "a screen block has a toggle to its slot");
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
			pressPower(singleplayer, computer);
			context.waitFor(client -> !machinePowered(client, computer) && !showsHello(client, screen));
			pressPower(singleplayer, computer);
			context.waitFor(client -> machinePowered(client, computer) && showsHello(client, screen));

			// The toggle opens the screen's radio slot, and back, straight from view to view.
			int grabs = context.computeOnClient(client -> MouseGrabs.count);
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((DisplayScreen) s).viewToggle());
			context.waitForScreen(CardHolderScreen.class);
			context.waitTicks(2);
			check(context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().cardSlots()) == 1, "the screen's one slot");
			context.takeScreenshot("screen-slot");
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, s -> ((CardHolderScreen) s).viewToggle());
			context.waitForScreen(DisplayScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				check(MouseGrabs.count == grabs, "switching views should not grab the mouse (it recenters the cursor)");
				check(client.player.containerMenu == client.player.inventoryMenu, "the slot's menu should be closed");
			});
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
			singleplayer.getServer().runOnServer(server -> ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer))
				.machine().board().overrideBusCapacity(2)); // its ROM, and room for one
			BlockPos second = screen.east();
			setblock(singleplayer, second, "tier3_screen[facing=south]");
			context.waitFor(client -> client.level.getBlockEntity(computer) instanceof AllInOneBlockEntity be && be.busStatus().overflow());
			context.waitTicks(2);
			BufferedImage full = read(context.takeScreenshot("case-bus-full"));
			int ledOn = screenPixel(context, full, s -> ((CaseScreen) s).busOverflowLed());
			check((ledOn & 0xFFFFFF) == 0xF0A020, "the overflow light is lit, got " + hex(ledOn));
			check(context.computeOnClient(client -> ((AllInOneBlockEntity) client.level.getBlockEntity(computer)).busStatus().waiting()) == 1,
				"one screen waits");
			check(summary(context, computer).equals("bus: 2 of 2 (1 internal, 1 external), overflow 1"), "the summary says so, got " + summary(context, computer));
			setblock(singleplayer, second, "minecraft:air");
			singleplayer.getServer().runOnServer(server -> ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer))
				.machine().board().overrideBusCapacity(null));
			context.waitFor(client -> !((AllInOneBlockEntity) client.level.getBlockEntity(computer)).busStatus().overflow());

			// Cut the cable: the screen stays listed, offline, its row's display gray.
			setblock(singleplayer, cable, "minecraft:air");
			context.waitFor(client -> ((AllInOneBlockEntity) client.level.getBlockEntity(computer)).busEntries().get(row).online() == false);
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
			context.waitFor(client -> ((AllInOneBlockEntity) client.level.getBlockEntity(computer)).busEntries().get(row).online());
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
				singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
					.openMenu((CardHolderBlockEntity) server.overworld().getBlockEntity(holder)));
				context.waitForScreen(CardHolderScreen.class);
				context.waitTicks(2);
				int slots = context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().cardSlots());
				check(slots == (holder == hub ? 5 : 1), "card slots: " + slots);
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
		}
	}

	/** The gear's brass. */
	private static final IntPredicate BRASS = rgb -> AllInOneClientGameTest.red(rgb) > 60
		&& AllInOneClientGameTest.red(rgb) > AllInOneClientGameTest.green(rgb) + 12 && AllInOneClientGameTest.green(rgb) > AllInOneClientGameTest.blue(rgb) + 15;

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
		return context.computeOnClient(client -> CaseScreen.busSummary(((AllInOneBlockEntity) client.level.getBlockEntity(computer)).busStatus()).getString());
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
		return client.level.getBlockEntity(computer) instanceof AllInOneBlockEntity be ? be.power().percent() : -1;
	}

		private static void pressPower(TestSingleplayerContext singleplayer, BlockPos computer) {
		singleplayer.getServer().runOnServer(server -> ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)).pressPower());
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
		return count(image, cx - 140, cy - 140, cx + 140, cy + 60, AllInOneClientGameTest::isWhite);
	}
}
