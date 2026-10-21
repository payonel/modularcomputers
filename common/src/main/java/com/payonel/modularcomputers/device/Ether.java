package com.payonel.modularcomputers.device;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;

/**
 * What carries network cards' frames (user design, 2026-10-21): the cables, for network cards
 * (every card the cables reach from its block, as the bus's walk finds devices: through hubs and
 * screens, across Bluetooth links, ending at computers), and the air, for WLAN cards (every one
 * within {@link NetworkCard#RANGE} blocks). A wired card hears only wired ones, a WLAN card only
 * WLAN ones, as Ethernet and Wi-Fi are separate networks until something routes between them.
 * <p>
 * Each card says where it is as its block ticks ({@link NetworkCard#place}); one that hasn't for
 * {@link #STALE_TICKS} (its chunk unloaded, its block broken, the card taken out) isn't there.
 * Nothing is saved: places are worked out afresh as blocks load.
 */
public final class Ether {
	/** A card that hasn't said where it is for this many ticks isn't on the network. */
	static final int STALE_TICKS = 2;
	private static final Set<NetworkCard> CARDS = Collections.newSetFromMap(new WeakHashMap<>());

	private Ether() {
	}

	/** Where a card is, as its block last said: its world (a level, any key), its block and when. */
	public record Place(Object world, BlockPos pos, long seen) {
	}

	static synchronized void join(NetworkCard card) {
		CARDS.add(card);
	}

	/**
	 * A frame from {@code from} to the card whose address is {@code to} (null: every card it
	 * reaches, a broadcast). Returns whether any card's radio or port took it (a unicast frame's
	 * acknowledgement, as Wi-Fi's link layer gives one).
	 */
	static boolean send(NetworkCard from, String to, String data) {
		Place here = from.place();
		String address = from.address();
		if (here == null || address == null) {
			return false;
		}
		List<NetworkCard> cards;
		synchronized (Ether.class) {
			cards = List.copyOf(CARDS);
		}
		boolean heard = false;
		for (NetworkCard card : cards) {
			Place there = card.place();
			if (card == from || card.kind() != from.kind() || there == null || there.world() != here.world()
				|| there.seen() < here.seen() - STALE_TICKS || to != null && !to.equals(card.address())) {
				continue;
			}
			Double distance = distance(from, here, there);
			if (distance != null) {
				heard |= card.hear(address, data, distance, to == null);
			}
		}
		return heard;
	}

	/** How far a frame goes from one card to another, in meters; null: it doesn't reach. */
	private static Double distance(NetworkCard from, Place here, Place there) {
		if (from.wireless()) {
			double meters = Math.sqrt(here.pos().distSqr(there.pos()));
			return meters <= NetworkCard.RANGE ? Math.round(meters * 10) / 10.0 : null;
		}
		if (here.pos().equals(there.pos())) {
			return 0.0; // two cards in one block: on the same wire
		}
		Integer meters = from.reach().get(there.pos());
		return meters == null ? null : (double) meters;
	}
}
