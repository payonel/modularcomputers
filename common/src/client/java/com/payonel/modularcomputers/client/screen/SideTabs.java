package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The panels left of a computer's board in its parts view, and the cards' tabs that open them, down
 * its views' button column under the view buttons ({@link ComputerButtons}; the user's: one
 * mechanism, not a case for each). The bus order is the parts view's own panel (its button opens it);
 * each card in an expansion slot whose kind registered a tab ({@link #register}: the crafting card's
 * grid and storage) has one, in slot order, the same in the screen view (each opening the parts view
 * with its panel). A card brings its tab by registering, with nothing else to change.
 */
public final class SideTabs {
	/** The bus order panel's lane: not a card's. */
	public static final int BUS = -1;
	/** The tabs' buttons (scripts/art/case_gui_textures.py): one a kind, a row each. */
	private static final Identifier TEXTURE = ModularComputers.id("textures/gui/side_tabs.png");
	private static final int CRAFTING_ROW = 0;
	private static final int ROWS = 1;

	/** A panel left of the board, and (a card's) the tab opening it. */
	public interface Tab {
		/** Its card's expansion lane, or {@link #BUS}. */
		int lane();

		/** Its panel's width, in GUI units. */
		int width();

		/** Whether its panel is left out of a window too narrow for it (the view works without it). */
		default boolean optional() {
			return false;
		}

		/** Its button (the open one's ring is the column's). */
		void extractButton(GuiGraphicsExtractor graphics, Viewport at);

		/** Its panel, at {@code at} (left of the board). */
		void extractPanel(CaseScreen screen, GuiGraphicsExtractor graphics, Viewport at, int mouseX, int mouseY);
	}

	/** Makes the tab of a card of a kind in an expansion lane. */
	public interface CardTab {
		Tab make(int lane);
	}

	private static final Map<CardKind, CardTab> CARDS = new EnumMap<>(CardKind.class);

	private SideTabs() {
	}

	/** Gives a kind of card a tab: each card of it gets one, made by {@code tab}. */
	public static void register(CardKind kind, CardTab tab) {
		CARDS.put(kind, tab);
	}

	/** The bus order's panel: the parts view's own. */
	public static Tab bus() {
		return BUS_TAB;
	}

	/** The cards' tabs a computer's parts give ({@code card}: what's in an expansion lane), in slot order. */
	public static List<Tab> of(IntFunction<ItemStack> card) {
		List<Tab> tabs = new ArrayList<>();
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			CardTab maker = ModItems.cardKind(card.apply(lane)).map(CARDS::get).orElse(null);
			if (maker != null) {
				tabs.add(maker.make(lane));
			}
		}
		return tabs;
	}

	/** Where a computer's tabs go: down from {@code top}, right of its view at {@code x}, a button each. */
	public static Viewport button(int x, int top, int index) {
		return ComputerButtons.row(x, top, index);
	}

	private static void extractButton(GuiGraphicsExtractor graphics, Viewport at, int row) {
		int size = ComputerButtons.SIZE;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, at.x(), at.y(), 0, row * size, at.width(), at.height(), size, ROWS * size);
	}

	/** The bus order's: every device on the bus, in order (the parts view's own panel; left out when there's no room). */
	private static final Tab BUS_TAB = new Tab() {
		@Override
		public int lane() {
			return BUS;
		}

		@Override
		public int width() {
			return CaseScreen.BUS_W;
		}

		@Override
		public boolean optional() {
			return true;
		}

		@Override
		public void extractButton(GuiGraphicsExtractor graphics, Viewport at) {
			// (no button of its own: the parts view's opens it)
		}

		@Override
		public void extractPanel(CaseScreen screen, GuiGraphicsExtractor graphics, Viewport at, int mouseX, int mouseY) {
			screen.extractBusPanel(graphics);
		}
	};

	/** A crafting card's: its grid, its result and its storage (their slots the menu's: CaseMenu.CRAFT_*). */
	public static Tab crafting(int lane) {
		return new Tab() {
			/** Its panel's art (scripts/art/case_gui_textures.py) and where its title goes. */
			private static final Identifier PANEL = ModularComputers.id("textures/gui/crafting_panel.png");
			private static final int TITLE_X = 10;
			private static final int TITLE_Y = 6;
			private static final int TITLE = 0xFFEAEADE;

			@Override
			public int lane() {
				return lane;
			}

			@Override
			public int width() {
				return CaseMenu.CRAFT_W;
			}

			@Override
			public void extractButton(GuiGraphicsExtractor graphics, Viewport at) {
				SideTabs.extractButton(graphics, at, CRAFTING_ROW);
			}

			@Override
			public void extractPanel(CaseScreen screen, GuiGraphicsExtractor graphics, Viewport at, int mouseX, int mouseY) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, at.x(), at.y(), 0, 0, at.width(), CaseMenu.CRAFT_H, at.width(), CaseMenu.CRAFT_H);
				graphics.text(Minecraft.getInstance().font, Component.translatable("gui.modularcomputers.crafting.title", lane + 1), at.x() + TITLE_X,
					at.y() + TITLE_Y, TITLE, false);
			}
		};
	}
}
