package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.CaseBlock;
import com.payonel.modularcomputers.item.ScreenItem;
import com.payonel.modularcomputers.machine.BoardLayout;
import com.payonel.modularcomputers.machine.CaseTier;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.CpuItem;
import com.payonel.modularcomputers.item.DiskItem;
import com.payonel.modularcomputers.item.MemoryItem;
import com.payonel.modularcomputers.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.payonel.modularcomputers.device.CraftingCard;

/**
 * The case view: the motherboard's part slots above the player's inventory. Slot positions
 * match the sockets in textures/gui/case_board.png (drawn at panel offset {@link #BOARD_X},
 * {@link #BOARD_Y}); CaseArtTest checks that they line up.
 */
public final class CaseMenu extends AbstractContainerMenu {
	public static final int CPU_SLOT = 0;
	public static final int EEPROM_SLOT = 1;
	/** The first battery bay; the bays are {@link #UPS_BAYS} slots from here. */
	public static final int UPS_SLOT = 2;
	public static final int UPS_BAYS = 3;
	public static final int RAM_SLOTS = UPS_SLOT + UPS_BAYS;
	public static final int SATA_SLOTS = RAM_SLOTS + 4;
	public static final int EXP_SLOTS = SATA_SLOTS + 4;
	public static final int LANES = 4;
	public static final int PART_SLOTS = EXP_SLOTS + 4;
	/** Data slots for the power level and the recent draw: menu data travels as shorts, so the low and high 16 bits of each. */
	public static final int POWER_DATA = 4;

	/** Where the board texture is drawn inside the panel. */
	public static final int BOARD_X = 4;
	public static final int BOARD_Y = 4;
	public static final int INVENTORY_Y = 150;
	/** The player's inventory's slots: its main rows and its hotbar. */
	public static final int INVENTORY_SLOTS = 36;
	/** A robot's hand slot (its main hand), after the player's inventory: in the column right of the panel, under its buttons. */
	public static final int HAND_SLOT = PART_SLOTS + INVENTORY_SLOTS;
	public static final int HAND_X = 182;
	public static final int HAND_Y = 54;
	/** Its off hand's slot, under its hand's: where it catches what it breaks with nowhere else to put it. */
	public static final int OFF_HAND_SLOT = HAND_SLOT + 1;
	public static final int OFF_HAND_Y = HAND_Y + 18;
	/**
	 * Each expansion lane's crafting card's slots (its grid, its storage) and the grid's result,
	 * after the hand's: all lanes', always there, each shown while its card is in and its tab open.
	 */
	public static final int CRAFT_SLOTS = OFF_HAND_SLOT + 1;
	public static final int CRAFT_LANE_SLOTS = CraftingCard.SLOTS + 1;
	/** A lane's result slot, after its card's. */
	public static final int CRAFT_RESULT = CraftingCard.SLOTS;
	/** Where a crafting card's panel is: right of the buttons' column; its grid, result and storage in it. */
	public static final int CRAFT_X = 204;
	public static final int CRAFT_W = 116;
	public static final int CRAFT_H = 168;
	public static final int CRAFT_GRID_X = CRAFT_X + 12;
	public static final int CRAFT_GRID_Y = 22;
	public static final int CRAFT_RESULT_X = CRAFT_X + 90;
	public static final int CRAFT_RESULT_Y = 40;
	public static final int CRAFT_STORAGE_X = CRAFT_X + 22;
	public static final int CRAFT_STORAGE_Y = 88;
	private static final int CRAFT_COLUMNS = 3;
	private static final int STORAGE_COLUMNS = 4;
	private static final int SLOT_STEP = 18;

	/** Slot positions in the panel, in GUI units (the item's top-left), by slot index. */
	public static final int[][] SLOT_POSITIONS = {
		{18, 26}, {20, 68},
		{8, 103}, {28, 103}, {48, 103},
		{54, 40}, {74, 40}, {94, 40}, {114, 40},
		{138, 19}, {138, 39}, {138, 59}, {138, 79},
		{74, 114}, {98, 114}, {122, 114}, {146, 114},
	};

	private final Container parts;
	private final ContainerData power;
	private final BlockPos pos;
	/** Whose parts these are: a block (a case) or an entity (a golem). */
	private final com.payonel.modularcomputers.network.HostRef host;
	/** Whether the player can still use it (in reach, still there). */
	private final java.util.function.Predicate<Player> valid;

	private final BoardLayout tier;

	/** Client side: the slots and the case's power are filled in by the server's sync; the tier is the block's. */
	public CaseMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(PART_SLOTS), new SimpleContainerData(POWER_DATA), pos, ContainerLevelAccess.NULL,
			inventory.player.level().getBlockState(pos).getBlock() instanceof CaseBlock block ? block.tier() : CaseTier.TIER_3);
	}

	/**
	 * {@code power} holds the case's power buffer level, in power units. The menu sends it to the
	 * player as it changes, like a furnace's progress; menu data goes as shorts, so it's two
	 * ({@link #POWER_DATA}).
	 */
	public CaseMenu(int id, Inventory inventory, Container parts, ContainerData power, BlockPos pos, ContainerLevelAccess access, CaseTier tier) {
		// Any case, still there and in reach.
		this(ModMenus.CASE, id, inventory, parts, power, pos, com.payonel.modularcomputers.network.HostRef.of(pos), player -> access.evaluate((level, at) -> level.getBlockState(at).getBlock() instanceof CaseBlock
			&& player.isWithinBlockInteractionRange(at, 4.0), true), tier);
	}

	/** A computer's parts view of any kind ({@code type}: whose art it's drawn in), on a board with {@code layout}'s slots. */
	public CaseMenu(net.minecraft.world.inventory.MenuType<CaseMenu> type, int id, Inventory inventory, Container parts, ContainerData power, BlockPos pos,
		com.payonel.modularcomputers.network.HostRef host, java.util.function.Predicate<Player> valid, BoardLayout tier) {
		this(type, id, inventory, parts, power, pos, host, valid, tier, null);
	}

	/** ...and a robot's: its hand ({@code hand}, one slot; null: it has none), after the player's inventory. */
	public CaseMenu(net.minecraft.world.inventory.MenuType<CaseMenu> type, int id, Inventory inventory, Container parts, ContainerData power, BlockPos pos,
		com.payonel.modularcomputers.network.HostRef host, java.util.function.Predicate<Player> valid, BoardLayout tier, Container hand) {
		super(type, id);
		this.hasHand = hand != null;
		this.player = inventory.player;
		this.tier = tier;
		checkContainerSize(parts, PART_SLOTS);
		this.parts = parts;
		this.power = power;
		this.pos = pos;
		this.host = host;
		this.valid = valid;
		addDataSlots(power);
		for (int slot = 0; slot < PART_SLOTS; slot++) {
			addSlot(new PartSlot(parts, slot, SLOT_POSITIONS[slot][0], SLOT_POSITIONS[slot][1], tier));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		Container hands = hand != null ? hand : new SimpleContainer(2);
		for (int which = 0; which < 2; which++) {
			addSlot(new Slot(hands, which, HAND_X, which == 0 ? HAND_Y : OFF_HAND_Y) {
				@Override
				public boolean isActive() {
					return hasHand;
				}

				@Override
				public boolean mayPlace(ItemStack stack) {
					return hasHand;
				}
			});
		}
		Runnable changed = parts instanceof com.payonel.modularcomputers.board.BoardAssembly.PartContents contents ? contents::partContentsChanged : () -> {
		};
		for (int lane = 0; lane < LANES; lane++) {
			int partSlot = EXP_SLOTS + lane;
			CraftingStorage storage = new CraftingStorage(() -> parts.getItem(partSlot), changed);
			craftStorage[lane] = storage;
			results[lane] = new net.minecraft.world.inventory.ResultContainer();
			int which = lane;
			for (int slot = 0; slot < CraftingCard.SLOTS; slot++) {
				int x = slot < CraftingCard.GRID ? CRAFT_GRID_X + slot % CRAFT_COLUMNS * SLOT_STEP
					: CRAFT_STORAGE_X + (slot - CraftingCard.GRID) % STORAGE_COLUMNS * SLOT_STEP;
				int y = slot < CraftingCard.GRID ? CRAFT_GRID_Y + slot / CRAFT_COLUMNS * SLOT_STEP
					: CRAFT_STORAGE_Y + (slot - CraftingCard.GRID) / STORAGE_COLUMNS * SLOT_STEP;
				addSlot(new Slot(storage, slot, x, y) {
					@Override
					public boolean isActive() {
						return showing(which);
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return storage.present();
					}

					@Override
					public boolean mayPickup(Player player) {
						return storage.present();
					}
				});
			}
			addSlot(new net.minecraft.world.inventory.ResultSlot(player, storage.grid(), results[lane], 0, CRAFT_RESULT_X, CRAFT_RESULT_Y) {
				@Override
				public boolean isActive() {
					return showing(which);
				}
			});
			storage.listen(() -> slotsChanged(storage));
			slotsChanged(storage); // (what its grid makes as it opens)
		}
	}

	private final boolean hasHand;
	private final Player player;
	/** Each lane's crafting card's slots (on the card: none while it holds no crafting card), and its grid's result. */
	private final CraftingStorage[] craftStorage = new CraftingStorage[LANES];
	private final net.minecraft.world.inventory.ResultContainer[] results = new net.minecraft.world.inventory.ResultContainer[LANES];
	/** The lane whose crafting card's tab is open (-1: none): the client's to choose. */
	private int openTab = -1;

	/** Whether this is a robot's, with a hand slot. */
	public boolean hasHand() {
		return hasHand;
	}

	/** Whether lane {@code lane} holds a crafting card. */
	public boolean craftingCard(int lane) {
		return craftStorage[lane].present();
	}

	/** The lane whose crafting card's tab is open, or -1. */
	public int openTab() {
		return openTab;
	}

	/** Opens a lane's crafting card's tab (-1: none). */
	public void openTab(int lane) {
		openTab = lane;
	}

	/** Whether a lane's crafting slots are shown: its card is in and its tab is open. */
	private boolean showing(int lane) {
		return openTab == lane && craftingCard(lane);
	}

	/** A crafting card's grid changed: what it makes, worked out on the server (the client is sent it). */
	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		for (int lane = 0; lane < LANES; lane++) {
			if (craftStorage[lane] == container) {
				ItemStack result = ItemStack.EMPTY;
				if (container instanceof CraftingStorage storage && storage.present()) {
					var input = storage.grid().asCraftInput();
					var recipe = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
					if (recipe.isPresent()) {
						ItemStack made = recipe.get().value().assemble(input);
						if (made.isItemEnabled(level.enabledFeatures())) {
							result = made;
							results[lane].setRecipeUsed(recipe.get());
						}
					}
				}
				results[lane].setItem(0, result);
			}
		}
	}

	public BoardLayout tier() {
		return tier;
	}

	/**
	 * Whether a case of {@code tier} has this slot: the CPU and ROM always, and the first of each
	 * kind up to the tier's count. The rest are greyed out.
	 */
	public static boolean available(BoardLayout tier, int slot) {
		if (slot == CPU_SLOT || slot == EEPROM_SLOT) {
			return true;
		} else if (isBatteryBay(slot)) {
			return slot - UPS_SLOT < tier.bays();
		} else if (slot < SATA_SLOTS) {
			return slot - RAM_SLOTS < tier.ram();
		} else if (slot < EXP_SLOTS) {
			return slot - SATA_SLOTS < tier.sata();
		}
		return slot - EXP_SLOTS < tier.expansion() && !tier.soldered(slot - EXP_SLOTS);
	}

	/** The gold-trimmed expansion slot, which also takes a screen: the first one, on tiers that have it. */
	public static boolean isScreenSlot(BoardLayout tier, int slot) {
		return tier.screenSlot() && slot == EXP_SLOTS;
	}

	/** Whether a case of {@code tier} takes this item in this slot. */
	public static boolean accepts(BoardLayout tier, int slot, ItemStack stack) {
		if (!available(tier, slot)) {
			return false;
		}
		if (isScreenSlot(tier, slot) && stack.getItem() instanceof ScreenItem screen) {
			return tier.takesScreen(screen.screen().tier());
		}
		return accepts(slot, stack);
	}

	/** Whether a slot takes this item, on a full board: each takes its own part, and expansion slots take any card. */
	public static boolean accepts(int slot, ItemStack stack) {
		if (slot == CPU_SLOT) {
			return stack.getItem() instanceof CpuItem;
		} else if (slot == EEPROM_SLOT) {
			return ModItems.isEeprom(stack);
		} else if (isBatteryBay(slot)) {
			return stack.getItem() instanceof BatteryItem;
		} else if (slot < SATA_SLOTS) {
			return stack.getItem() instanceof MemoryItem;
		} else if (slot < EXP_SLOTS) {
			return stack.getItem() instanceof DiskItem;
		}
		return ModItems.cardKind(stack).isPresent();
	}

	public static boolean isBatteryBay(int slot) {
		return slot >= UPS_SLOT && slot < UPS_SLOT + UPS_BAYS;
	}

	/** The case's power buffer level, in power units, put back together from its two shorts. */
	public int powerLevel() {
		return (power.get(1) & 0xFFFF) << 16 | power.get(0) & 0xFFFF;
	}

	/** What the computer draws now, in power units a tick (its load). */
	public int load() {
		return (power.get(3) & 0xFFFF) << 16 | power.get(2) & 0xFFFF;
	}

	/** Whose parts these are. */
	public com.payonel.modularcomputers.network.HostRef host() {
		return host;
	}

	public BlockPos pos() {
		return pos;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int inventoryEnd = PART_SLOTS + INVENTORY_SLOTS;
		boolean moved;
		if (index < PART_SLOTS || index >= inventoryEnd) {
			if (index >= CRAFT_SLOTS && (index - CRAFT_SLOTS) % CRAFT_LANE_SLOTS == CRAFT_RESULT) {
				stack.getItem().onCraftedBy(stack, player); // (a craft taken by shift-click: as a crafting table's)
			}
			moved = moveItemStackTo(stack, PART_SLOTS, inventoryEnd, true);
			if (moved && index >= CRAFT_SLOTS && (index - CRAFT_SLOTS) % CRAFT_LANE_SLOTS == CRAFT_RESULT) {
				slot.onQuickCraft(stack, original);
			}
		} else {
			// part slots only take their own item; then a robot's hand, then an open crafting card's storage
			moved = moveItemStackTo(stack, 0, PART_SLOTS, false) || hasHand() && moveItemStackTo(stack, HAND_SLOT, HAND_SLOT + 1, false);
			if (!moved && openTab >= 0 && craftingCard(openTab)) {
				int lane = CRAFT_SLOTS + openTab * CRAFT_LANE_SLOTS;
				moved = moveItemStackTo(stack, lane + CraftingCard.GRID, lane + CraftingCard.SLOTS, false);
			}
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		boolean result = index >= CRAFT_SLOTS && (index - CRAFT_SLOTS) % CRAFT_LANE_SLOTS == CRAFT_RESULT;
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (result) {
			// The craft is made (its grid used), as a crafting table's: what didn't fit is dropped.
			slot.onTake(player, stack);
			if (!stack.isEmpty()) {
				player.drop(stack, false, net.minecraft.util.Prediction.PREDICTED);
			}
		}
		return original;
	}

	/** A double click gathering a stack never takes from a crafting grid's result (as a crafting table's). */
	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return !(target instanceof net.minecraft.world.inventory.ResultSlot) && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public boolean stillValid(Player player) {
		return valid.test(player);
	}

	/** A slot for one kind of part, holding one item. */
	private static final class PartSlot extends Slot {
		private final BoardLayout tier;

		PartSlot(Container container, int index, int x, int y, BoardLayout tier) {
			super(container, index, x, y);
			this.tier = tier;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return accepts(tier, getContainerSlot(), stack);
		}

		/** A slot this tier leaves unprinted isn't a slot: no highlight, no clicks. */
		@Override
		public boolean isActive() {
			return available(tier, getContainerSlot());
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
