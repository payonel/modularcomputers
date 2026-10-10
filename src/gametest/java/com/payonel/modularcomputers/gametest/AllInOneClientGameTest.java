package com.payonel.modularcomputers.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.display.Viewport;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The all-in-one computer in the real client: an off computer's face and display are black;
 * sneak + right-click turns it on and "hello world" renders on the block face; right-click
 * opens the display, which fills most of the window from its first frame and shows the
 * same text. Screenshots are checked pixel by pixel.
 */
public class AllInOneClientGameTest implements FabricClientGameTest {
	/** Near-white: the display's text color. Nothing else in the checked areas is this bright. */
	private static final int WHITE_THRESHOLD = 220;
	/** Ticks between right-clicks; the client ignores use clicks closer together than 4 ticks. */
	private static final int USE_COOLDOWN = 5;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			// Computer at eye level two blocks north of the player, facing them.
			BlockPos feet = singleplayer.getServer().computeOnServer(server ->
				singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos computer = feet.north(2).above();
			singleplayer.getServer().runCommand("setblock %d %d %d modularcomputers:all_in_one[facing=south]"
				.formatted(computer.getX(), computer.getY(), computer.getZ()));
			singleplayer.getServer().runCommand("tp @p %d.5 %d %d.5 180 0".formatted(feet.getX(), feet.getY(), feet.getZ()));
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

			// Off: right-click opens a black display at full size from the first frame.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			checkDisplayIsLarge(context);
			DisplayShot off = displayShot(context, "all-in-one-display-off");
			check(off.whitePixels(0, 16) == 0, "an off display should be all black");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.waitTicks(USE_COOLDOWN);

			// Sneak + right-click turns it on; the face shows "hello world".
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(client -> machineRunning(client, computer));
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(USE_COOLDOWN);
			int onFace = whitePixelsOnFace(context.takeScreenshot("all-in-one-on"));
			check(onFace >= 20, "a running computer's face should show text, found " + onFace + " white pixels");

			// Right-click shows the same text in the display: on the first row only.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DisplayScreen.class);
			checkDisplayIsLarge(context);
			context.waitTicks(2);
			DisplayShot on = displayShot(context, "all-in-one-display-on");
			check(on.whitePixels(0, 1) >= 50, "the display's first row should show text, found " + on.whitePixels(0, 1));
			check(on.whitePixels(1, 16) == 0, "the rest of the display should be black");

			// Breaking the computer closes its display.
			singleplayer.getServer().runCommand("setblock %d %d %d minecraft:air"
				.formatted(computer.getX(), computer.getY(), computer.getZ()));
			context.waitFor(client -> client.gui.screen() == null);
		}
	}

	private static boolean machineRunning(Minecraft client, BlockPos pos) {
		return client.level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer && computer.machine().isRunning();
	}

	private static void checkDisplayIsLarge(ClientGameTestContext context) {
		// Checked on the same tick the screen opened: no placeholder size.
		context.runOnClient(client -> {
			DisplayScreen screen = (DisplayScreen) client.gui.screen();
			Viewport viewport = screen.viewport();
			boolean large = viewport.width() >= 0.85 * screen.width || viewport.height() >= 0.85 * screen.height;
			boolean notFull = viewport.width() < screen.width && viewport.height() < screen.height;
			check(large && notFull, "display should fill most but not all of the window: " + viewport
				+ " in " + screen.width + "x" + screen.height);
		});
	}

	/** White pixels in the upper middle of the frame, where the computer's glass is. */
	private static int whitePixelsOnFace(Path screenshot) {
		BufferedImage image = read(screenshot);
		int cx = image.getWidth() / 2;
		int cy = image.getHeight() / 2;
		return countWhite(image, cx - 100, cy - 105, cx + 100, cy);
	}

	private record DisplayShot(BufferedImage image, Viewport pixels, int rows) {
		/** White pixels in display rows [from, to). */
		int whitePixels(int fromRow, int toRow) {
			double rowHeight = (double) pixels.height() / rows;
			return countWhite(image, pixels.x(), pixels.y() + (int) (fromRow * rowHeight),
				pixels.right(), pixels.y() + (int) (toRow * rowHeight));
		}
	}

	private static DisplayShot displayShot(ClientGameTestContext context, String name) {
		BufferedImage image = read(context.takeScreenshot(name));
		return context.computeOnClient(client -> {
			DisplayScreen screen = (DisplayScreen) client.gui.screen();
			Viewport viewport = screen.viewport();
			double scale = (double) image.getWidth() / screen.width;
			Viewport pixels = new Viewport((int) (viewport.x() * scale), (int) (viewport.y() * scale),
				(int) (viewport.width() * scale), (int) (viewport.height() * scale));
			// The middle of the display is drawn black, not see-through.
			int middle = image.getRGB(pixels.x() + pixels.width() / 2, pixels.y() + pixels.height() * 3 / 4) & 0xFFFFFF;
			check(middle == 0, "display should be black, got #%06x".formatted(middle));
			return new DisplayShot(image, pixels, 16);
		});
	}

	private static int countWhite(BufferedImage image, int x0, int y0, int x1, int y1) {
		int count = 0;
		for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
			for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
				int rgb = image.getRGB(x, y);
				if ((rgb >> 16 & 0xFF) >= WHITE_THRESHOLD && (rgb >> 8 & 0xFF) >= WHITE_THRESHOLD && (rgb & 0xFF) >= WHITE_THRESHOLD) {
					count++;
				}
			}
		}
		return count;
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
