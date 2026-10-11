package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.cable.CableWalk;
import com.payonel.modularcomputers.item.ModComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bluetooth. A card links to a <em>board</em>, not to another card: each block that can hold
 * Bluetooth cards (a computer, an expansion hub, a screen, a robotic piston, a paint gun) has its
 * own board id, and a card's link ({@link ModComponents#BLUETOOTH_LINK}) names one. Many cards
 * can link to the same board, and that board's own cards can link on, so links daisy-chain. A
 * link joins the two blocks like a cable.
 * <p>
 * A link only works while both blocks have a Bluetooth card in: the card is the radio. Moving
 * the card a computer was linked through to another computer doesn't take its links along;
 * they stay with the board, and work again once any Bluetooth card is back in.
 * <p>
 * Linking: right-click a block that has a Bluetooth card with a card in hand, and the card in
 * hand links to that block's board. Sneak + right-click clears the card in hand's link.
 * <p>
 * Each side keeps a registry of the loaded blocks with Bluetooth cards, which the cable walk
 * uses to cross links and clients use to highlight a held card's target.
 * <p>
 * <b>Pairing:</b> a device with a Bluetooth card in its pairing slot belongs to one computer,
 * found by following its card's link (on through the pairing cards of whatever board it links
 * to, like a hub's) until a computer. That's saved in the cards themselves, so it doesn't depend
 * on which chunk loads first. Unpaired devices are shared by every computer that reaches them.
 */
public final class Bluetooth {
	/** A block that holds Bluetooth cards. */
	public interface Holder {
		/** Its board's id: what cards link to. */
		UUID boardId();

		/** Its Bluetooth cards (the live stacks), in its order: bus order in a computer, slot order otherwise. */
		List<ItemStack> bluetoothCards();
	}

	/** A block with a radio: its board, the boards its cards link to, its pairing card's link, whether it's a computer. */
	private record Entry(UUID board, List<UUID> links, Optional<UUID> pairing, boolean computer) {
	}

	/** How many links {@link #owner} follows before giving up (a loop, or a very long chain). */
	private static final int MAX_CHAIN = 16;

	private static final Map<Level, Map<BlockPos, Entry>> HOLDERS = new WeakHashMap<>();

	private Bluetooth() {
	}

	private static Optional<UUID> link(ItemStack card) {
		return Optional.ofNullable(card.get(ModComponents.BLUETOOTH_LINK));
	}

	/**
	 * Records the Bluetooth cards the block at {@code pos} holds now: all of them, the one in its
	 * pairing slot (or null), and whether the block is a computer. Without a card it has no radio.
	 */
	public static void update(Level level, BlockPos pos, UUID board, List<ItemStack> cards, ItemStack pairing, boolean computer) {
		Map<BlockPos, Entry> holders = HOLDERS.computeIfAbsent(level, key -> new HashMap<>());
		if (cards.isEmpty()) {
			holders.remove(pos);
		} else {
			List<UUID> links = cards.stream().map(Bluetooth::link).flatMap(Optional::stream).toList();
			holders.put(pos.immutable(), new Entry(board, links, pairing == null ? Optional.empty() : link(pairing), computer));
		}
	}

	/** The loaded block with a radio whose board is {@code board}. */
	public static Optional<BlockPos> board(Level level, UUID board) {
		return HOLDERS.getOrDefault(level, Map.of()).entrySet().stream()
			.filter(holder -> holder.getValue().board().equals(board))
			.map(Map.Entry::getKey)
			.findFirst();
	}

	/**
	 * The computer a paired device belongs to: follow its pairing card's link to that board, and
	 * on through that board's pairing card, until a computer. Empty when the device isn't paired,
	 * or the chain ends (no link, or a board with no radio, or not loaded).
	 */
	public static Optional<BlockPos> owner(Level level, BlockPos device) {
		Map<BlockPos, Entry> holders = HOLDERS.getOrDefault(level, Map.of());
		Entry entry = holders.get(device);
		Optional<UUID> link = entry == null ? Optional.empty() : entry.pairing();
		for (int step = 0; step < MAX_CHAIN && link.isPresent(); step++) {
			Optional<BlockPos> next = board(level, link.get());
			if (next.isEmpty()) {
				return Optional.empty();
			}
			Entry board = holders.get(next.get());
			if (board.computer()) {
				return next;
			}
			link = board.pairing();
		}
		return Optional.empty();
	}

	/** The block at {@code pos} is gone, or its chunk unloaded. */
	public static void remove(Level level, BlockPos pos) {
		Map<BlockPos, Entry> holders = HOLDERS.get(level);
		if (holders != null) {
			holders.remove(pos);
		}
	}

	/**
	 * The blocks the one at {@code pos} is linked with, either way: its cards link to their
	 * board, or theirs link to its. Both need a radio. Distances are straight lines, in meters
	 * (at least 1).
	 */
	public static List<CableWalk.Link> links(Level level, BlockPos pos) {
		Map<BlockPos, Entry> holders = HOLDERS.getOrDefault(level, Map.of());
		Entry mine = holders.get(pos);
		if (mine == null) {
			return List.of();
		}
		List<CableWalk.Link> links = new ArrayList<>();
		for (var other : holders.entrySet()) {
			if (other.getKey().equals(pos)) {
				continue;
			}
			if (mine.links().contains(other.getValue().board()) || other.getValue().links().contains(mine.board())) {
				int meters = Math.max(1, (int) Math.round(Math.sqrt(pos.distSqr(other.getKey()))));
				links.add(new CableWalk.Link(other.getKey(), meters));
			}
		}
		return links;
	}

	/**
	 * A player right-clicked {@code pos} holding a Bluetooth card: link it to the block's board,
	 * if the block has a Bluetooth card to receive with. Without one, the click goes on to the
	 * block (to open its slots, say).
	 */
	public static InteractionResult link(ItemStack held, Level level, BlockPos pos, Player player) {
		if (player.isSecondaryUseActive()) {
			return InteractionResult.PASS; // sneaking: the card resets instead (the game skips the block)
		}
		if (!(level.getBlockEntity(pos) instanceof Holder holder) || holder.bluetoothCards().isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		held.set(ModComponents.BLUETOOTH_LINK, holder.boardId());
		player.sendOverlayMessage(Component.translatable("item.modularcomputers.bluetooth_card.linked", level.getBlockState(pos).getBlock().getName()));
		return InteractionResult.SUCCESS;
	}

	/** Sneak + right-click: the card links to nothing. */
	public static InteractionResult reset(ItemStack held, Level level, Player player) {
		if (!level.isClientSide()) {
			held.remove(ModComponents.BLUETOOTH_LINK);
			player.sendOverlayMessage(Component.translatable("item.modularcomputers.bluetooth_card.reset"));
		}
		return InteractionResult.SUCCESS;
	}
}
