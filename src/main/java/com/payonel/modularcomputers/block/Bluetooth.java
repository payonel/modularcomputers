package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.cable.CableWalk;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
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
 * Bluetooth cards. Each has its own address ({@link ModComponents#BLUETOOTH_ID}) and can link
 * to one other card's address ({@link ModComponents#BLUETOOTH_LINK}). Many cards can link to
 * the same one, and a card linked to can itself link on, so cards can daisy-chain. A link joins
 * the blocks the two cards are in, like a cable.
 * <p>
 * Linking: right-click a block holding a Bluetooth card (a computer, an expansion hub, a robotic
 * piston) with a card in hand, and the card in hand links to the block's first one (in bus order,
 * in a computer). Sneak + right-click resets the card in hand: a new address, so nothing links
 * to it any more, and no link of its own.
 * <p>
 * Each side keeps a registry of the loaded blocks holding Bluetooth cards, which the cable walk
 * uses to cross links and clients use to highlight a held card's target.
 */
public final class Bluetooth {
	/** An installed card's address and link, either of which may not be set. */
	public record Card(Optional<UUID> id, Optional<UUID> link) {
		static Card of(ItemStack stack) {
			return new Card(Optional.ofNullable(stack.get(ModComponents.BLUETOOTH_ID)), Optional.ofNullable(stack.get(ModComponents.BLUETOOTH_LINK)));
		}
	}

	/** A block that holds Bluetooth cards. */
	public interface Holder {
		/** Its Bluetooth cards (the live stacks), in its order: bus order in a computer, slot order otherwise. */
		List<ItemStack> bluetoothCards();

		/** A card's components changed (it got an address): save it. */
		void bluetoothChanged();
	}

	private static final Map<Level, Map<BlockPos, List<Card>>> HOLDERS = new WeakHashMap<>();

	private Bluetooth() {
	}

	/** Records which Bluetooth cards the block at {@code pos} holds now. */
	public static void update(Level level, BlockPos pos, List<ItemStack> cards) {
		Map<BlockPos, List<Card>> holders = HOLDERS.computeIfAbsent(level, key -> new HashMap<>());
		if (cards.isEmpty()) {
			holders.remove(pos);
		} else {
			holders.put(pos.immutable(), cards.stream().map(Card::of).toList());
		}
	}

	/** The block at {@code pos} is gone, or its chunk unloaded. */
	public static void remove(Level level, BlockPos pos) {
		Map<BlockPos, List<Card>> holders = HOLDERS.get(level);
		if (holders != null) {
			holders.remove(pos);
		}
	}

	/**
	 * The blocks the one at {@code pos} is linked with, either way: its cards link to theirs, or
	 * theirs link to its. Distances are straight lines, in meters (at least 1).
	 */
	public static List<CableWalk.Link> links(Level level, BlockPos pos) {
		Map<BlockPos, List<Card>> holders = HOLDERS.getOrDefault(level, Map.of());
		List<Card> mine = holders.get(pos);
		if (mine == null) {
			return List.of();
		}
		List<CableWalk.Link> links = new ArrayList<>();
		for (var other : holders.entrySet()) {
			if (other.getKey().equals(pos)) {
				continue;
			}
			boolean linked = mine.stream().anyMatch(card -> other.getValue().stream().anyMatch(theirs ->
				card.link().isPresent() && card.link().equals(theirs.id()) || theirs.link().isPresent() && theirs.link().equals(card.id())));
			if (linked) {
				int meters = Math.max(1, (int) Math.round(Math.sqrt(pos.distSqr(other.getKey()))));
				links.add(new CableWalk.Link(other.getKey(), meters));
			}
		}
		return links;
	}

	/** Whether the block at {@code pos} holds the card with address {@code id}. */
	public static boolean holds(Level level, BlockPos pos, UUID id) {
		List<Card> cards = HOLDERS.getOrDefault(level, Map.of()).get(pos);
		return cards != null && cards.stream().anyMatch(card -> card.id().equals(Optional.of(id)));
	}

	/**
	 * A player right-clicked {@code pos} holding a Bluetooth card: link it to the block's first
	 * Bluetooth card. Without one, the click goes on to the block (to open its slots, say).
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
		ItemStack target = holder.bluetoothCards().getFirst();
		UUID address = target.get(ModComponents.BLUETOOTH_ID);
		if (address == null) {
			address = UUID.randomUUID();
			target.set(ModComponents.BLUETOOTH_ID, address);
			holder.bluetoothChanged();
		}
		held.set(ModComponents.BLUETOOTH_LINK, address);
		player.sendOverlayMessage(Component.translatable("item.modularcomputers.bluetooth_card.linked", level.getBlockState(pos).getBlock().getName()));
		return InteractionResult.SUCCESS;
	}

	/** Sneak + right-click: a new address, so nothing links to this card any more, and no link of its own. */
	public static InteractionResult reset(ItemStack held, Level level, Player player) {
		if (!level.isClientSide()) {
			held.set(ModComponents.BLUETOOTH_ID, UUID.randomUUID());
			held.remove(ModComponents.BLUETOOTH_LINK);
			player.sendOverlayMessage(Component.translatable("item.modularcomputers.bluetooth_card.reset"));
		}
		return InteractionResult.SUCCESS;
	}

	/** The Bluetooth cards among some stacks, in order. */
	public static List<ItemStack> cardsAmong(List<ItemStack> stacks) {
		return stacks.stream().filter(stack -> stack.is(ModItems.BLUETOOTH_CARD)).toList();
	}
}
