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
 * within the sender's signal strength, {@link NetworkCard#range}). A wired card hears only wired ones, a WLAN card only
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

	/** The cards {@code from} reaches now, each with how far, in meters. */
	private static java.util.Map<NetworkCard, Double> reached(NetworkCard from) {
		java.util.Map<NetworkCard, Double> reached = new java.util.LinkedHashMap<>();
		Place here = from.place();
		if (here == null || from.address() == null) {
			return reached;
		}
		List<NetworkCard> cards;
		synchronized (Ether.class) {
			cards = List.copyOf(CARDS);
		}
		for (NetworkCard card : cards) {
			Place there = card.place();
			if (card == from || card.wireless() != from.wireless() || there == null || there.world() != here.world()
				|| there.seen() < here.seen() - STALE_TICKS) {
				continue;
			}
			Double distance = distance(from, here, there);
			if (distance != null) {
				reached.put(card, distance);
			}
		}
		return reached;
	}

	/**
	 * A frame from {@code from} to the card whose address is {@code to}: null when it took it, else
	 * why not ({@link NetworkCard#BUSY}, {@link NetworkCard#UNREACHABLE}). Nothing tries again.
	 */
	static String send(NetworkCard from, String to, String data) {
		for (var entry : reached(from).entrySet()) {
			if (to.equals(entry.getKey().address())) {
				String refused = entry.getKey().hear(from.address(), data, entry.getValue(), false);
				from.logged("out", to, entry.getValue(), data.length(), refused == null ? "taken" : refused);
				return refused;
			}
		}
		from.logged("out", to, null, data.length(), NetworkCard.UNREACHABLE);
		return NetworkCard.UNREACHABLE;
	}

	/** A frame from {@code from} to every card it reaches, as best it can: how many took it. */
	static int broadcast(NetworkCard from, String data) {
		int took = 0;
		for (var entry : reached(from).entrySet()) {
			if (entry.getKey().hear(from.address(), data, entry.getValue(), true) == null) {
				took++;
			}
		}
		return took;
	}

	/** How far a frame goes from one card to another, in meters; null: it doesn't reach. */
	private static Double distance(NetworkCard from, Place here, Place there) {
		if (from.wireless()) {
			double meters = Math.sqrt(here.pos().distSqr(there.pos()));
			return meters <= from.range() ? Math.round(meters * 10) / 10.0 : null; // (the sender's strength)
		}
		if (here.pos().equals(there.pos())) {
			return 0.0; // two cards in one block: on the same wire
		}
		Integer meters = from.reach().get(there.pos());
		return meters == null ? null : (double) meters;
	}
}
