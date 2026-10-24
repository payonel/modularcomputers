package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.display.TextBuffer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The pictures on a screen (its cells' tiles: sixel), as one texture a screen: its cells'
 * {@link TextBuffer#TILE_WIDTH} by {@link TextBuffer#TILE_HEIGHT} pixels each, transparent where
 * there's no picture, drawn over the cells' backgrounds. Rebuilt only when its pictures change
 * ({@link TextBuffer#tileChanges}), not for every letter typed.
 */
public final class PixelLayers {
	private static final Map<TextBuffer, Layer> LAYERS = new WeakHashMap<>();
	private static int next;

	private PixelLayers() {
	}

	/** A screen's picture texture. */
	private static final class Layer {
		final Identifier id = ModularComputers.id("dynamic/pixels_" + next++);
		DynamicTexture texture;
		int version = -1;
		int width;
		int height;
	}

	/** The texture of a screen's pictures, up to date; null when it has none. Client thread. */
	public static Identifier of(TextBuffer buffer) {
		Layer layer = LAYERS.get(buffer);
		if (!buffer.hasTiles()) {
			if (layer != null) {
				release(layer);
				LAYERS.remove(buffer);
			}
			return null;
		}
		if (layer == null) {
			layer = new Layer();
			LAYERS.put(buffer, layer);
		}
		int columns = buffer.resolution().columns();
		int rows = buffer.resolution().rows();
		int width = columns * TextBuffer.TILE_WIDTH;
		int height = rows * TextBuffer.TILE_HEIGHT;
		if (layer.texture == null || layer.width != width || layer.height != height) {
			release(layer);
			Identifier id = layer.id;
			layer.texture = new DynamicTexture(() -> id.toString(), width, height, true);
			layer.width = width;
			layer.height = height;
			layer.version = -1;
			Minecraft.getInstance().getTextureManager().register(id, layer.texture);
		}
		if (layer.version != buffer.tileChanges()) {
			NativeImage image = layer.texture.getPixels();
			image.fillRect(0, 0, width, height, 0);
			for (int row = 0; row < rows; row++) {
				for (int column = 0; column < columns; column++) {
					int[] tile = buffer.tile(column, row);
					if (tile != null) {
						for (int y = 0; y < TextBuffer.TILE_HEIGHT; y++) {
							for (int x = 0; x < TextBuffer.TILE_WIDTH; x++) {
								image.setPixel(column * TextBuffer.TILE_WIDTH + x, row * TextBuffer.TILE_HEIGHT + y, tile[y * TextBuffer.TILE_WIDTH + x]);
							}
						}
					}
				}
			}
			layer.texture.upload();
			layer.version = buffer.tileChanges();
		}
		return layer.id;
	}

	private static void release(Layer layer) {
		if (layer.texture != null) {
			Minecraft.getInstance().getTextureManager().release(layer.id);
			layer.texture = null;
		}
	}

	/** The world went away: every screen's texture goes. */
	public static void clear() {
		LAYERS.values().forEach(PixelLayers::release);
		LAYERS.clear();
	}
}
