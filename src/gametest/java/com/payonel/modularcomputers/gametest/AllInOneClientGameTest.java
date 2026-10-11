package com.payonel.modularcomputers.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModCreativeTab;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The all-in-one computer in the real client, checked through screenshots pixel by pixel:
 * <ul>
 * <li>It's in the creative inventory.</li>
 * <li>Right-click opens the display at full size from its first frame.</li>
 * <li>A new computer has no CPU: its power button is faded red; pressing it is refused (faded
 * green only while held) and the computer stays off.</li>
 * <li>{@code /modularcomputers giveall}, sent from the player, gives every part.</li>
 * <li>The view toggle opens the case view, where the CPU and EEPROM go in with real mouse
 * clicks and a shift-click; the power button lights up.</li>
 * <li>The window into the case on the back opens the case view.</li>
 * <li>Powered on from the display, "hello world" shows in the display and on the block face.</li>
 * <li>The glowing button on the back toggles power with a plain right-click.</li>
 * <li>Without an EEPROM the CPU shows the soft gray abort screen, in the display and on the face.</li>
 * <li>Breaking the computer closes its display.</li>
 * </ul>
 */
public class AllInOneClientGameTest implements FabricClientGameTest {
	/** Near-white: the display's text color. Nothing else in the checked areas is this bright. */
	private static final int WHITE_THRESHOLD = 220;
	/** Ticks between right-clicks; the client ignores use clicks closer together than 4 ticks. */
	private static final int USE_COOLDOWN = 5;
	/** Ticks between mouse clicks: 300 ms, past the double-click window. */
	private static final int CLICK_PAUSE = 6;
	private static final IntPredicate RED = rgb -> red(rgb) > 150 && green(rgb) < 100 && blue(rgb) < 100;
	private static final IntPredicate GREEN = rgb -> green(rgb) > 150 && red(rgb) < 130 && blue(rgb) < 130;
	/** The power button without a CPU: the same hue, dim. */
	private static final IntPredicate FADED_RED = rgb -> red(rgb) > 100 && red(rgb) < 170 && red(rgb) - green(rgb) > 30;
	private static final IntPredicate FADED_GREEN = rgb -> green(rgb) > 100 && green(rgb) < 170 && green(rgb) - red(rgb) > 25;

	@Override
	public void runTest(ClientGameTestContext context) {
		// Consistent settings (the default, spelled out): superflat, fixed seed, no time or weather.
		// Cheats on, like a creative test world, so the player may use operator commands (giveall).
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true)
			.adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			singleplayer.getConnection().waitForChunksRender();
			checkCreativeInventory(context, singleplayer);
			// The error beep's sounds.json entry and .ogg loaded (a missing file would only log a warning).
			context.runOnClient(client -> {
				var beep = client.getSoundManager().getSoundEvent(ModularComputers.id("error_beep"));
				check(beep != null && beep.getWeight() > 0, "the error beep sound should be loaded");
			});

			// Computer at eye level two blocks north of the player, facing them.
			BlockPos feet = singleplayer.getServer().computeOnServer(server ->
				singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos computer = feet.north(2).above();
			singleplayer.getServer().runCommand("setblock %d %d %d modularcomputers:all_in_one[facing=south]"
				.formatted(computer.getX(), computer.getY(), computer.getZ()));
			teleport(singleplayer, feet, 180);
			context.waitFor(client -> client.level.getBlockEntity(computer) instanceof AllInOneBlockEntity);
			context.getInput().lookAt(computer);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle(); // no crosshair or hand over the screen
				}
			});
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);

			// Off: the face shows no text.
			check(whitePixelsOnFace(context.takeScreenshot("all-in-one-off")) == 0, "an off computer's face should show no text");

			// A new computer has no CPU: right-click opens a black display at full size from the
			// first frame, with a faded red power button. Pressing it is refused (an error beep):
			// faded green only while the mouse is held, then faded red again, and it stays off.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			checkDisplayLayout(context);
			DisplayShot empty = displayShot(context, "all-in-one-display-no-cpu", 0);
			check(FADED_RED.test(empty.buttonCenter()), "without a CPU the power button should be faded red, got " + hex(empty.buttonCenter()));
			moveCursorTo(context, screen -> ((DisplayScreen) screen).powerButton());
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(CLICK_PAUSE);
			DisplayShot held = displayShot(context, "all-in-one-display-no-cpu-held", 0);
			check(FADED_GREEN.test(held.buttonCenter()), "held without a CPU the button should be faded green, got " + hex(held.buttonCenter()));
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			// The refused press also flashes faded green briefly (the block state); wait it out.
			context.waitFor(client -> !client.level.getBlockState(computer).getValue(AllInOneBlock.POWERED));
			context.waitTicks(2);
			DisplayShot released = displayShot(context, "all-in-one-display-no-cpu-released", 0);
			check(FADED_RED.test(released.buttonCenter()), "released, the button should be faded red again, got " + hex(released.buttonCenter()));
			check(!context.computeOnClient(client -> machinePowered(client, computer)), "without a CPU the computer must stay off");
			check(released.whitePixels(0, 16) == 0, "nothing runs without a CPU");

			// The player gets every part with the cheat command, typed as they would.
			context.runOnClient(client -> client.player.connection.sendCommand("modularcomputers giveall"));
			context.waitFor(client -> client.player.getInventory().contains(new ItemStack(ModItems.CPU))
				&& client.player.getInventory().contains(new ItemStack(ModItems.EEPROM)));
			int cpuHotbar = hotbarSlotOf(context, ModItems.CPU);
			int eepromHotbar = hotbarSlotOf(context, ModItems.EEPROM);

			// The view toggle opens the case view; the parts go in with real mouse clicks.
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, screen -> ((DisplayScreen) screen).viewToggle());
			context.waitForScreen(CaseScreen.class);
			context.takeScreenshot("all-in-one-case-empty");
			int hotbar = CaseMenu.PART_SLOTS + 27; // the case menu's first hotbar slot
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, screen -> ((CaseScreen) screen).slotArea(hotbar + cpuHotbar)); // pick up the CPU
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, screen -> ((CaseScreen) screen).slotArea(CaseMenu.CPU_SLOT));
			shiftClickOnScreen(context, screen -> ((CaseScreen) screen).slotArea(hotbar + eepromHotbar)); // the EEPROM finds its slot
			singleplayer.getServer().waitFor(server -> server.overworld().getBlockEntity(computer) instanceof AllInOneBlockEntity be
				&& be.machine().board().cpu().isPresent() && be.machine().board().eeprom().isPresent());
			context.waitTicks(2);
			BufferedImage caseShot = read(context.takeScreenshot("all-in-one-case-parts"));
			int caseButton = pixelAt(context, caseShot, screen -> ((CaseScreen) screen).powerButton());
			check(RED.test(caseButton), "with a CPU the power button should be lit red, got " + hex(caseButton));

			// The board shows what's installed. Fill the rest of the case, then check each indicator.
			singleplayer.getServer().runOnServer(server -> {
				var parts = ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)).parts();
				for (int lane = 0; lane < 3; lane++) {
					parts.setItem(CaseMenu.RAM_SLOTS + lane, new ItemStack(ModItems.RAM));
				}
				parts.setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.HARD_DISK));
				parts.setItem(CaseMenu.SATA_SLOTS + 2, new ItemStack(ModItems.HARD_DISK));
				parts.setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.WLAN_CARD));
				parts.setItem(CaseMenu.UPS_SLOT, new ItemStack(ModItems.UPS_BATTERY));
			});
			context.waitFor(client -> ((CaseScreen) client.gui.screen()).getMenu().slots.get(CaseMenu.UPS_SLOT).hasItem());
			context.getInput().setCursorPos(0, 0); // no slot tooltip over the board
			context.waitTicks(2);
			BufferedImage full = read(context.takeScreenshot("all-in-one-case-full"));
			checkBoardPixel(context, full, 4, 20, 0xC8FFD2, "CPU dots lit");
			checkBoardPixel(context, full, 55, 26, 0x287842, "RAM lane 1 seated");
			checkBoardPixel(context, full, 115, 26, 0x1A1A1E, "RAM lane 4 empty");
			checkBoardPixel(context, full, 154, 23, 0xC4342C, "SATA port 1 plugged");
			checkBoardPixel(context, full, 154, 43, 0x1A1A1E, "SATA port 2 empty");
			checkBoardPixel(context, full, 73, 115, 0xB0B4BC, "expansion slot 2 engaged");
			checkBoardPixel(context, full, 46, 115, 0x1A1A1E, "expansion slot 1 empty");
			checkBoardPixel(context, full, 34, 121, 0x5AE66E, "full battery: sixth bar green");
			singleplayer.getServer().runOnServer(server -> {
				ItemStack drained = new ItemStack(ModItems.UPS_BATTERY);
				drained.set(ModComponents.CHARGE, 0);
				((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)).parts().setItem(CaseMenu.UPS_SLOT, drained);
			});
			context.waitFor(client -> ((CaseScreen) client.gui.screen()).getMenu().slots.get(CaseMenu.UPS_SLOT).getItem()
				.getOrDefault(ModComponents.CHARGE, 100) == 0);
			context.waitTicks(2);
			BufferedImage drainedShot = read(context.takeScreenshot("all-in-one-case-battery-empty"));
			checkBoardPixel(context, drainedShot, 14, 121, 0xDC3C32, "empty battery: one red bar");
			checkBoardPixel(context, drainedShot, 18, 121, 0x1E2621, "empty battery: second bar off");

			// The bus order panel lists the devices; its arrows reorder them on the server.
			check(context.computeOnClient(client -> ((CaseScreen) client.gui.screen()).busPanelShown()), "the bus order panel should show");
			check(serverBusKeys(singleplayer, computer).equals(List.of("display", "keyboard", "sata:0", "sata:2", "exp:1")),
				"default bus order, got " + serverBusKeys(singleplayer, computer));
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, screen -> ((CaseScreen) screen).busArrow(0, false));
			singleplayer.getServer().waitFor(server -> serverBusKeys(server, computer).getFirst().equals("keyboard"));
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, screen -> ((CaseScreen) screen).busArrow(4, true));
			singleplayer.getServer().waitFor(server -> serverBusKeys(server, computer).get(3).equals("exp:1"));
			context.waitTicks(2);
			context.takeScreenshot("all-in-one-case-reordered");
			check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), "clicking the panel must not pick anything up");

			// Back to the screen view; the power button turns the computer on.
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_LEFT, screen -> ((CaseScreen) screen).viewToggle());
			context.waitForScreen(DisplayScreen.class);
			checkDisplayLayout(context);
			clickOnScreen(context, InputConstants.MOUSE_BUTTON_RIGHT, screen -> ((DisplayScreen) screen).powerButton());
			context.waitFor(client -> machinePowered(client, computer));
			context.waitTicks(2);
			DisplayShot on = displayShot(context, "all-in-one-display-on", 0);
			check(GREEN.test(on.buttonCenter()), "the power button should be lit green when on, got " + hex(on.buttonCenter()));
			check(on.whitePixels(0, 1) >= 50, "the display's first row should show text, found " + on.whitePixels(0, 1));
			check(on.whitePixels(1, 16) == 0, "the rest of the display should be black");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);

			// On: the face shows "hello world".
			int onFace = whitePixelsOnFace(context.takeScreenshot("all-in-one-on"));
			check(onFace >= 20, "a running computer's face should show text, found " + onFace + " white pixels");

			// Behind it, the power button glows green; right-clicking it turns the computer off.
			BlockPos behind = computer.north(2).below();
			teleport(singleplayer, behind, 0);
			context.waitFor(client -> client.player.blockPosition().equals(behind));
			aimAtBack(context, computer, 11.5, 11.5); // the power button
			context.waitTicks(5);
			int[] back = buttonColors(context.takeScreenshot("all-in-one-back-on"));
			check(back[1] > 100 && back[0] == 0, "the back button should glow green when on, red/green pixels: " + back[0] + "/" + back[1]);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(client -> !machinePowered(client, computer));
			context.waitTicks(USE_COOLDOWN);
			check(context.computeOnClient(client -> client.gui.screen() == null), "the power button should not open the display");
			back = buttonColors(context.takeScreenshot("all-in-one-back-off"));
			check(back[0] > 100 && back[1] == 0, "the back button should glow red when off, red/green pixels: " + back[0] + "/" + back[1]);

			// The window into the case, across the top of the back, opens the case view.
			aimAtBack(context, computer, 7.5, 4.5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(CaseScreen.class);
			context.takeScreenshot("all-in-one-case-from-back");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			check(!context.computeOnClient(client -> machinePowered(client, computer)), "the case window should not toggle power");
			context.waitTicks(USE_COOLDOWN);

			// Without an EEPROM the CPU shows the abort screen: soft gray with dark text.
			singleplayer.getServer().runOnServer(server -> ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer))
				.parts().setItem(CaseMenu.EEPROM_SLOT, ItemStack.EMPTY));
			aimAtBack(context, computer, 11.5, 11.5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(client -> machinePowered(client, computer));
			context.waitTicks(USE_COOLDOWN);
			aimAtBack(context, computer, 5.5, 10.5); // elsewhere on the back: opens the display
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			context.waitTicks(2);
			DisplayShot abort = displayShot(context, "all-in-one-display-abort", Cpu.ABORT_BACKGROUND);
			int abortText = abort.pixels(0, 1, rgb -> (rgb & 0xFFFFFF) == Cpu.ABORT_FOREGROUND);
			check(abortText >= 30, "the abort message should show in dark text, found " + abortText);

			// The block face shows the abort screen too.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			teleport(singleplayer, feet, 180);
			context.waitFor(client -> client.player.blockPosition().equals(feet));
			context.getInput().lookAt(computer);
			context.waitTicks(5);
			BufferedImage abortFace = read(context.takeScreenshot("all-in-one-abort-face"));
			int cx = abortFace.getWidth() / 2;
			int cy = abortFace.getHeight() / 2;
			int gray = count(abortFace, cx - 100, cy - 105, cx + 100, cy, rgb -> near(rgb, Cpu.ABORT_BACKGROUND, 16));
			check(gray > 5000, "the face should show the gray abort screen, found " + gray + " gray pixels");

			// Breaking the computer closes its display.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			singleplayer.getServer().runCommand("setblock %d %d %d minecraft:air"
				.formatted(computer.getX(), computer.getY(), computer.getZ()));
			context.waitFor(client -> client.gui.screen() == null);
		}
	}

	/** Stands the player in the middle of {@code feet}, looking toward {@code yaw}. */
	private static void teleport(TestSingleplayerContext singleplayer, BlockPos feet, int yaw) {
		// Block centers are +0.5 even for negative coordinates (block -4 spans -4.0..-3.0).
		singleplayer.getServer().runCommand("tp @p %s %d %s %d 0".formatted(
			feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw));
	}

	/** The block and its parts are listed in the mod's own creative tab; the block is in creative search. */
	private static void checkCreativeInventory(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("gamemode creative @a");
		context.waitFor(client -> client.player.isCreative());
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(CreativeModeInventoryScreen.class);
		context.runOnClient(client -> {
			ItemStack computer = new ItemStack(ModBlocks.ALL_IN_ONE);
			CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(ModCreativeTab.KEY);
			check(tab.contains(computer), "the all-in-one should be in the Modular Computers tab");
			check(tab.contains(new ItemStack(ModItems.CPU)) && tab.contains(new ItemStack(ModItems.EEPROM)), "the CPU and EEPROM should be in the tab");
			check(CreativeModeTabs.tabs().contains(tab), "the Modular Computers tab should be shown");
			check(CreativeModeTabs.searchTab().contains(computer), "the all-in-one should be in creative search");
			var screen = (FabricCreativeModeInventoryScreen) client.gui.screen();
			check(screen.setSelectedTab(tab), "could not select the Modular Computers tab");
		});
		context.waitTicks(2);
		context.takeScreenshot("creative-tab");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(client -> client.gui.screen() == null);
	}

	/** The board pixel at (bx, by) in the case view's screenshot must be {@code rgb}. */
	private static void checkBoardPixel(ClientGameTestContext context, BufferedImage image, int bx, int by, int rgb, String what) {
		int actual = context.computeOnClient(client -> {
			CaseScreen screen = (CaseScreen) client.gui.screen();
			double scale = (double) image.getWidth() / screen.width;
			Viewport board = screen.board();
			return image.getRGB((int) ((board.x() + bx + 0.5) * scale), (int) ((board.y() + by + 0.5) * scale)) & 0xFFFFFF;
		});
		check(actual == rgb, what + ": expected " + hex(rgb) + ", got " + hex(actual));
	}

	private static List<String> serverBusKeys(TestSingleplayerContext singleplayer, BlockPos computer) {
		return singleplayer.getServer().computeOnServer(server -> serverBusKeys(server, computer));
	}

	private static List<String> serverBusKeys(net.minecraft.server.MinecraftServer server, BlockPos computer) {
		return ((AllInOneBlockEntity) server.overworld().getBlockEntity(computer)).busEntries().stream()
			.map(AllInOneBlockEntity.BusEntry::key).toList();
	}

	/** Which hotbar slot (0-8) holds the item. */
	private static int hotbarSlotOf(ClientGameTestContext context, net.minecraft.world.item.Item item) {
		int slot = context.computeOnClient(client -> {
			for (int i = 0; i < 9; i++) {
				if (client.player.getInventory().getItem(i).is(item)) {
					return i;
				}
			}
			return -1;
		});
		check(slot >= 0, item + " should be in the hotbar");
		return slot;
	}

	private static boolean machinePowered(Minecraft client, BlockPos pos) {
		return client.level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer && computer.machine().isPowered();
	}

	/** Checked on the same tick the screen opened, so there's no placeholder size. The bezel frames the display. */
	private static void checkDisplayLayout(ClientGameTestContext context) {
		context.runOnClient(client -> {
			DisplayScreen screen = (DisplayScreen) client.gui.screen();
			Viewport bezel = screen.bezel();
			Viewport display = screen.viewport();
			Viewport button = screen.powerButton();
			boolean large = bezel.width() >= 0.85 * screen.width || bezel.height() >= 0.85 * screen.height;
			boolean notFull = bezel.width() < screen.width && bezel.height() < screen.height;
			check(large && notFull, "the framed display should fill most but not all of the window: " + bezel
				+ " in " + screen.width + "x" + screen.height);
			check(display.x() > bezel.x() && display.right() < bezel.right() && display.y() > bezel.y() && display.bottom() < bezel.bottom(),
				"the bezel should frame the display: " + bezel + " around " + display);
			check(button.x() >= bezel.right() && button.y() == bezel.y(), "power button should be right of the bezel at its top: " + button);
			check(screen.viewToggle().bottom() <= screen.height, "view toggle should be inside the window: " + screen.viewToggle());
		});
	}

	/** Moves the real mouse cursor to the middle of an area of the open screen. */
	private static void moveCursorTo(ClientGameTestContext context, Function<Screen, Viewport> area) {
		double[] cursor = context.computeOnClient(client -> {
			Viewport target = area.apply(client.gui.screen());
			int scale = client.getWindow().getGuiScale();
			return new double[] {(target.x() + target.width() / 2.0) * scale, (target.y() + target.height() / 2.0) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
	}

	/** Moves the real mouse cursor to the middle of an area of the open screen and clicks ({@code InputConstants.MOUSE_BUTTON_*}). */
	private static void clickOnScreen(ClientGameTestContext context, int mouseButton, Function<Screen, Viewport> area) {
		moveCursorTo(context, area);
		context.getInput().pressMouse(mouseButton);
		// Pause like a person would; clicks closer together than 250 ms count as a double-click.
		context.waitTicks(CLICK_PAUSE);
	}

	/**
	 * Shift-clicks the middle of an area of the open screen. Fabric's {@code pressMouse} always
	 * sends no modifier flags, and 26.3 reads Shift from the click's flags, so this hands the
	 * screen the same event a real shift-click produces.
	 */
	private static void shiftClickOnScreen(ClientGameTestContext context, Function<Screen, Viewport> area) {
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			Viewport target = area.apply(screen);
			screen.mouseClicked(new MouseButtonEvent(target.x() + target.width() / 2.0, target.y() + target.height() / 2.0,
				new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT)), false);
		});
		context.waitTicks(CLICK_PAUSE);
	}

	/** The screenshot pixel in the middle of an area of the open screen. */
	private static int pixelAt(ClientGameTestContext context, BufferedImage image, Function<Screen, Viewport> area) {
		return context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			Viewport target = scaled(area.apply(screen), (double) image.getWidth() / screen.width);
			return image.getRGB(target.x() + target.width() / 2, target.y() + target.height() / 2);
		});
	}

	/**
	 * Turns the player to look at texel (u, v) on the back of a south-facing computer, as seen
	 * from behind (u to the right, v down).
	 */
	private static void aimAtBack(ClientGameTestContext context, BlockPos computer, double u, double v) {
		// From behind (looking south) right is west, so u counts from the east edge.
		Vec3 target = new Vec3(computer.getX() + 1 - u / 16, computer.getY() + 1 - v / 16, computer.getZ());
		Vec3 eye = context.computeOnClient(client -> client.player.getEyePosition());
		Vec3 d = target.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		context.getInput().lookAt(yaw, pitch);
	}

	/** Red and green pixel counts around the middle of the frame, where the aimed-at button is. */
	private static int[] buttonColors(Path screenshot) {
		BufferedImage image = read(screenshot);
		int cx = image.getWidth() / 2;
		int cy = image.getHeight() / 2;
		return new int[] {count(image, cx - 12, cy - 12, cx + 12, cy + 12, RED), count(image, cx - 12, cy - 12, cx + 12, cy + 12, GREEN)};
	}

	/** White pixels in the upper middle of the frame, where the computer's glass is. */
	private static int whitePixelsOnFace(Path screenshot) {
		BufferedImage image = read(screenshot);
		int cx = image.getWidth() / 2;
		int cy = image.getHeight() / 2;
		return count(image, cx - 100, cy - 105, cx + 100, cy, AllInOneClientGameTest::isWhite);
	}

	private record DisplayShot(BufferedImage image, Viewport display, Viewport button, int rows) {
		/** White pixels in display rows [from, to). */
		int whitePixels(int fromRow, int toRow) {
			return pixels(fromRow, toRow, AllInOneClientGameTest::isWhite);
		}

		/** Pixels of a color in display rows [from, to). */
		int pixels(int fromRow, int toRow, IntPredicate color) {
			double rowHeight = (double) display.height() / rows;
			return count(image, display.x(), display.y() + (int) (fromRow * rowHeight),
				display.right(), display.y() + (int) (toRow * rowHeight), color);
		}

		int buttonCenter() {
			return image.getRGB(button.x() + button.width() / 2, button.y() + button.height() / 2);
		}
	}

	/**
	 * Screenshot of the open display, with the display and button rectangles in screenshot
	 * pixels. The lower middle of the display must be {@code background} (0xRRGGBB): drawn, not
	 * see-through.
	 */
	private static DisplayShot displayShot(ClientGameTestContext context, String name, int background) {
		BufferedImage image = read(context.takeScreenshot(name));
		return context.computeOnClient(client -> {
			DisplayScreen screen = (DisplayScreen) client.gui.screen();
			double scale = (double) image.getWidth() / screen.width;
			Viewport display = scaled(screen.viewport(), scale);
			int middle = image.getRGB(display.x() + display.width() / 2, display.y() + display.height() * 3 / 4) & 0xFFFFFF;
			check(middle == background, "display background should be " + hex(background) + ", got " + hex(middle));
			return new DisplayShot(image, display, scaled(screen.powerButton(), scale), 16);
		});
	}

	private static Viewport scaled(Viewport v, double scale) {
		return new Viewport((int) (v.x() * scale), (int) (v.y() * scale), (int) (v.width() * scale), (int) (v.height() * scale));
	}

	private static int count(BufferedImage image, int x0, int y0, int x1, int y1, IntPredicate color) {
		int count = 0;
		for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
			for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
				if (color.test(image.getRGB(x, y))) {
					count++;
				}
			}
		}
		return count;
	}

	/** Each channel within {@code tolerance} of {@code expected} (0xRRGGBB). */
	private static boolean near(int rgb, int expected, int tolerance) {
		return Math.abs(red(rgb) - red(expected)) <= tolerance && Math.abs(green(rgb) - green(expected)) <= tolerance
			&& Math.abs(blue(rgb) - blue(expected)) <= tolerance;
	}

	private static boolean isWhite(int rgb) {
		return red(rgb) >= WHITE_THRESHOLD && green(rgb) >= WHITE_THRESHOLD && blue(rgb) >= WHITE_THRESHOLD;
	}

	private static int red(int rgb) {
		return rgb >> 16 & 0xFF;
	}

	private static int green(int rgb) {
		return rgb >> 8 & 0xFF;
	}

	private static int blue(int rgb) {
		return rgb & 0xFF;
	}

	private static String hex(int rgb) {
		return "#%06x".formatted(rgb & 0xFFFFFF);
	}

	private static BufferedImage read(Path screenshot) {
		try {
			return ImageIO.read(screenshot.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
