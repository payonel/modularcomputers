package com.payonel.modularcomputers.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModCreativeTab;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.display.Viewport;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.IntPredicate;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
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
 * <li>Off, its face and display are black, and the display's power button is lit red.</li>
 * <li>Right-click opens the display at full size from its first frame.</li>
 * <li>Left- or right-clicking the display's power button toggles power: the button goes green and
 * "hello world" shows in the display and on the block face.</li>
 * <li>The glowing button on the back toggles power with a plain right-click.</li>
 * <li>Breaking the computer closes its display.</li>
 * </ul>
 */
public class AllInOneClientGameTest implements FabricClientGameTest {
	/** Near-white: the display's text color. Nothing else in the checked areas is this bright. */
	private static final int WHITE_THRESHOLD = 220;
	/** Ticks between right-clicks; the client ignores use clicks closer together than 4 ticks. */
	private static final int USE_COOLDOWN = 5;
	private static final IntPredicate RED = rgb -> red(rgb) > 150 && green(rgb) < 100 && blue(rgb) < 100;
	private static final IntPredicate GREEN = rgb -> green(rgb) > 150 && red(rgb) < 130 && blue(rgb) < 130;

	@Override
	public void runTest(ClientGameTestContext context) {
		// Consistent settings (the default, spelled out): superflat, fixed seed, no time or weather.
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true).create()) {
			singleplayer.getConnection().waitForChunksRender();
			checkCreativeInventory(context, singleplayer);

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

			// Off: right-click opens a black display at full size from the first frame, red button beside it.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			checkDisplayLayout(context);
			DisplayShot off = displayShot(context, "all-in-one-display-off");
			check(off.whitePixels(0, 16) == 0, "an off display should be all black");
			check(RED.test(off.buttonCenter()), "the power button should be lit red when off, got " + hex(off.buttonCenter()));

			// The display's power button toggles with right-click (on, off) and left-click (on).
			clickPowerButton(context, InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitFor(client -> machineRunning(client, computer));
			clickPowerButton(context, InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitFor(client -> !machineRunning(client, computer));
			clickPowerButton(context, InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(client -> machineRunning(client, computer));
			context.waitTicks(2);
			DisplayShot on = displayShot(context, "all-in-one-display-on");
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
			aimAtBackButton(context, computer);
			context.waitTicks(5);
			int[] back = buttonColors(context.takeScreenshot("all-in-one-back-on"));
			check(back[1] > 100 && back[0] == 0, "the back button should glow green when on, red/green pixels: " + back[0] + "/" + back[1]);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(client -> !machineRunning(client, computer));
			context.waitTicks(USE_COOLDOWN);
			check(context.computeOnClient(client -> client.gui.screen() == null), "the power button should not open the display");
			back = buttonColors(context.takeScreenshot("all-in-one-back-off"));
			check(back[0] > 100 && back[1] == 0, "the back button should glow red when off, red/green pixels: " + back[0] + "/" + back[1]);

			// Elsewhere on the back, right-click opens the display; breaking the computer closes it.
			context.getInput().lookAt(computer);
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

	/** The block is listed in the mod's own creative tab and in creative search. */
	private static void checkCreativeInventory(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("gamemode creative @a");
		context.waitFor(client -> client.player.isCreative());
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(CreativeModeInventoryScreen.class);
		context.runOnClient(client -> {
			ItemStack computer = new ItemStack(ModBlocks.ALL_IN_ONE);
			CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(ModCreativeTab.KEY);
			check(tab.contains(computer), "the all-in-one should be in the Modular Computers tab");
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

	private static boolean machineRunning(Minecraft client, BlockPos pos) {
		return client.level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer && computer.machine().isRunning();
	}

	/** Checked on the same tick the screen opened, so there's no placeholder size. */
	private static void checkDisplayLayout(ClientGameTestContext context) {
		context.runOnClient(client -> {
			DisplayScreen screen = (DisplayScreen) client.gui.screen();
			Viewport display = screen.viewport();
			Viewport button = screen.powerButton();
			boolean large = display.width() >= 0.85 * screen.width || display.height() >= 0.85 * screen.height;
			boolean notFull = display.width() < screen.width && display.height() < screen.height;
			check(large && notFull, "display should fill most but not all of the window: " + display
				+ " in " + screen.width + "x" + screen.height);
			check(button.x() >= display.right() && button.y() == display.y(), "power button should be right of the display at its top: " + button);
			check(button.right() <= screen.width, "power button should be inside the window: " + button);
		});
	}

	/** Moves the real mouse cursor onto the display's power button and clicks it ({@code InputConstants.MOUSE_BUTTON_*}). */
	private static void clickPowerButton(ClientGameTestContext context, int mouseButton) {
		double[] cursor = context.computeOnClient(client -> {
			Viewport button = ((DisplayScreen) client.gui.screen()).powerButton();
			int scale = client.getWindow().getGuiScale();
			return new double[] {(button.x() + button.width() / 2.0) * scale, (button.y() + button.height() / 2.0) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.getInput().pressMouse(mouseButton);
	}

	/** Turns the player to look at the middle of the power button on the back of a south-facing computer. */
	private static void aimAtBackButton(ClientGameTestContext context, BlockPos computer) {
		// From behind (looking south) right is west, so texel u counts from the east edge.
		double texel = 11.5 / 16;
		Vec3 target = new Vec3(computer.getX() + 1 - texel, computer.getY() + 1 - texel, computer.getZ());
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
			double rowHeight = (double) display.height() / rows;
			return count(image, display.x(), display.y() + (int) (fromRow * rowHeight),
				display.right(), display.y() + (int) (toRow * rowHeight), AllInOneClientGameTest::isWhite);
		}

		int buttonCenter() {
			return image.getRGB(button.x() + button.width() / 2, button.y() + button.height() / 2);
		}
	}

	/** Screenshot of the open display, with the display and button rectangles in screenshot pixels. */
	private static DisplayShot displayShot(ClientGameTestContext context, String name) {
		BufferedImage image = read(context.takeScreenshot(name));
		return context.computeOnClient(client -> {
			DisplayScreen screen = (DisplayScreen) client.gui.screen();
			double scale = (double) image.getWidth() / screen.width;
			Viewport display = scaled(screen.viewport(), scale);
			// The middle of the display is drawn black, not see-through.
			int middle = image.getRGB(display.x() + display.width() / 2, display.y() + display.height() * 3 / 4) & 0xFFFFFF;
			check(middle == 0, "display should be black, got " + hex(middle));
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
