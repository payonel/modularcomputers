package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Network cards' frames, over the cables and through the air ({@link Ether}), and their wake (user design, 2026-10-21). */
class NetworkCardTest {
	/** This test's world: cards in another one never hear these. */
	private final Object world = new Object();
	private static final long NOW = 100;

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	/** A card at {@code pos}, with an id, powered and taking data (as on a running computer's bus). */
	private NetworkCard card(CardKind kind, BlockPos pos) {
		NetworkCard card = new NetworkCard(kind);
		UUID id = UUID.randomUUID();
		card.idFrom(() -> id);
		card.power().supply(this);
		card.accepting(true);
		card.place(world, pos, NOW);
		return card;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> received(NetworkCard card) {
		return (Map<String, Object>) card.receive(args()).first();
	}

	/** A card's traffic log's newest entry (the rest taken and dropped). */
	private static Object newest(NetworkCard card) {
		Object last = null;
		for (Object entry; (entry = card.pull(args()).first()) != null; ) {
			last = entry;
		}
		return last;
	}

	/**
	 * Its traffic log names each frame after what caused it (the user's): a send's and a broadcast's
	 * after their methods, a frame that came its own news, arrived; how it went is ok, a refusal its
	 * failure's own fields (send's answer), never a word in a string; a send that reached no card says
	 * its distance as -1, its shape kept.
	 */
	@Test
	void itsTrafficLogNamesEachFrameAfterWhatCausedItAndARefusalByItsFailure() {
		NetworkCard a = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.TIER2_WLAN, new BlockPos(3, 4, 0));
		a.send(args(b.address(), "hello"));
		assertThat(newest(a)).isEqualTo(Map.of("event", "send", "address", b.address(), "distance", 5.0, "bytes", 5L, "took", 1L, "ok", true));
		assertThat(newest(b)).isEqualTo(Map.of("event", "arrived", "address", a.address(), "broadcast", false, "distance", 5.0, "bytes", 5L, "ok", true));
		String nobody = UUID.randomUUID().toString();
		a.send(args(nobody, "x"));
		assertThat(newest(a)).isEqualTo(Map.of("event", "send", "address", nobody, "distance", -1.0, "bytes", 1L, "took", 0L, "ok", false,
			"why", "network", "reason", "unreachable", "message", "no card in reach"));
		a.broadcast(args("all"));
		assertThat(newest(a)).isEqualTo(Map.of("event", "broadcast", "bytes", 3L, "took", 1L, "ok", true));
		assertThat(newest(b)).isEqualTo(Map.of("event", "arrived", "address", a.address(), "broadcast", true, "distance", 5.0, "bytes", 3L, "ok", true));
		while (a.send(args(b.address(), "more")).first() != null) { // (till its ring is full)
		}
		Map<String, Object> full = Map.of("why", "limit", "of", "frames", "max", (long) RingDepths.of(com.payonel.modularcomputers.bus.DeviceType.NETWORK));
		Map<String, Object> sent = new java.util.HashMap<>(Map.of("event", "send", "address", b.address(), "distance", 5.0, "bytes", 4L, "took", 0L, "ok", false));
		sent.putAll(full);
		assertThat(newest(a)).isEqualTo(sent);
		Map<String, Object> came = new java.util.HashMap<>(Map.of("event", "arrived", "address", a.address(), "broadcast", false, "distance", 5.0, "bytes", 4L, "ok", false));
		came.putAll(full);
		assertThat(newest(b)).isEqualTo(came);
	}

	/**
	 * Its traffic log is kept as a device's events are (the user's): a window of the newest, each entry
	 * that pushed an older one out saying so as lost, never a silent drop.
	 */
	@Test
	void itsTrafficLogSaysHowManyEntriesWentWhenItWasFull() {
		NetworkCard a = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		String nobody = UUID.randomUUID().toString();
		int over = 5;
		for (int i = 0; i < RingDepths.of(com.payonel.modularcomputers.bus.DeviceType.NETWORK) + over; i++) {
			a.send(args(nobody, "x"));
		}
		int left = 0;
		long lost = 0;
		for (Object entry; (entry = a.pull(args()).first()) != null; left++) {
			lost += ((Map<?, ?>) entry).get("lost") instanceof Long n ? n : 0; // (each entry that pushed one out says so)
		}
		assertThat(left).as("a window of the newest").isEqualTo(RingDepths.of(com.payonel.modularcomputers.bus.DeviceType.NETWORK));
		assertThat(lost).as("every one that went, said").isEqualTo(over);
	}

	@Test
	void aFrameGoesToTheCardAtItsAddressAcknowledgedAndABroadcastToEveryCardInRange() {
		NetworkCard a = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.TIER2_WLAN, new BlockPos(3, 4, 0));
		NetworkCard c = card(CardKind.TIER2_WLAN, new BlockPos(0, 0, 10));
		NetworkCard far = card(CardKind.TIER2_WLAN, new BlockPos(NetworkCard.TIER2_RANGE + 1, 0, 0));
		assertThat(a.send(args(b.address(), "hello")).first()).as("acknowledged").isEqualTo(true);
		assertThat(received(b)).containsEntry("from", a.address()).containsEntry("data", "hello").containsEntry("distance", 5.0)
			.doesNotContainKey("broadcast");
		assertThat(c.interruptPending()).as("not for it").isFalse();
		assertThat(a.send(args("id:" + far.address(), "x")).failure().table()).as("out of range").isEqualTo(Map.of("why", "network", "reason", "unreachable", "message", "no card in reach"));
		assertThat(a.send(args(UUID.randomUUID().toString(), "x")).failure().table()).as("nobody there").isEqualTo(Map.of("why", "network", "reason", "unreachable", "message", "no card in reach"));
		assertThat(a.broadcast(args("all")).first()).as("how many took it").isEqualTo(2L);
		assertThat(received(b)).containsEntry("data", "all").containsEntry("broadcast", true);
		assertThat(received(c)).containsEntry("data", "all");
		assertThat(far.interruptPending()).isFalse();
		assertThat(a.interruptPending()).as("not its own").isFalse();
	}

	@Test
	void aWiredCardReachesWhatItsCablesReachAndOnlyWiredCards() {
		NetworkCard a = card(CardKind.NETWORK, BlockPos.ZERO);
		NetworkCard b = card(CardKind.NETWORK, new BlockPos(100, 0, 0));
		NetworkCard near = card(CardKind.NETWORK, new BlockPos(1, 0, 0));
		NetworkCard sibling = card(CardKind.NETWORK, BlockPos.ZERO);
		NetworkCard wireless = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		a.reach(Map.of(new BlockPos(100, 0, 0), 120));
		a.broadcast(args("on the wire"));
		assertThat(received(b)).containsEntry("data", "on the wire").containsEntry("distance", 120.0);
		assertThat(received(sibling)).as("in the same block: on the same wire").containsEntry("distance", 0.0);
		assertThat(near.interruptPending()).as("near, but no cable").isFalse();
		assertThat(wireless.interruptPending()).as("Wi-Fi isn't Ethernet").isFalse();
	}

	@Test
	void aCardNotSeenLatelyOrInAnotherWorldIsntThere() {
		NetworkCard a = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard gone = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		gone.place(world, BlockPos.ZERO, NOW - Ether.STALE_TICKS - 1);
		NetworkCard elsewhere = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		elsewhere.place(new Object(), BlockPos.ZERO, NOW);
		assertThat(a.send(args(gone.address(), "x")).error()).isEqualTo("network");
		assertThat(a.send(args(elsewhere.address(), "x")).error()).isEqualTo("network");
	}

	@Test
	void aFrameTooLongIsRefusedAndFramesWaitInOrderWhileAProgramRuns() {
		NetworkCard a = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		Result tooLong = com.payonel.modularcomputers.runtime.DeviceMethods.invoke(a, "send", args(b.address(), "x".repeat(NetworkCard.MTU + 1)));
		assertThat(tooLong.failed()).isTrue();
		a.send(args(b.address(), "1"));
		a.send(args(b.address(), "2"));
		assertThat(received(b)).containsEntry("data", "1");
		assertThat(received(b)).containsEntry("data", "2");
		assertThat(received(b)).isNull();
		b.accepting(false); // no program: it takes nothing, and says so
		assertThat(a.send(args(b.address(), "3")).failure().table()).as("not busy: a limit (no news of the sender's ends it)").isEqualTo(Map.of("why", "limit", "of", "frames", "max", (long) RingDepths.of(com.payonel.modularcomputers.bus.DeviceType.NETWORK)));
		b.accepting(true);
		assertThat(received(b)).isNull();
	}

	@Test
	void aFrameHoldingTheWakeMessageTurnsItsComputerOnWhileItsOff() {
		NetworkCard sender = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard card = new NetworkCard(CardKind.TIER2_WLAN);
		UUID id = UUID.randomUUID();
		card.idFrom(() -> id);
		card.place(world, BlockPos.ZERO, NOW);
		Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
		machine.board().setCpu(new Cpu(CpuTier.TIER_1));
		machine.board().setEeprom(new com.payonel.modularcomputers.machine.Eeprom(com.payonel.modularcomputers.machine.Program::park));
		new com.payonel.modularcomputers.machine.TestBus(machine).card(card, id);
		assertThat(sender.send(args(card.address(), "wake up")).error()).as("off, and not listening: nobody hears").isEqualTo("network");
		assertThat(card.setWake(args("wake")).first()).isNull();
		assertThat(sender.send(args(card.address(), "no")).first()).as("listening for its wake").isEqualTo(true);
		machine.tick();
		assertThat(machine.isPowered()).isFalse();
		sender.broadcast(args("please wake up"));
		machine.tick();
		assertThat(machine.isPowered()).as("woken, as its power button would").isTrue();
		assertThat(card.getWake(args()).first()).as("the setting outlasts power").isEqualTo("wake");
		assertThat(card.saved()).containsEntry("wake", "wake");
	}

	@Test
	void aFullCardRefusesAFrameAndTheSenderHearsItsBusyNothingDropped() {
		NetworkCard a = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.TIER2_WLAN, BlockPos.ZERO);
		int sent = 0;
		while (!a.send(args(b.address(), "f" + sent)).failed()) {
			sent++;
		}
		assertThat(a.send(args(b.address(), "more")).failure().table()).as("full: refused, not dropped").isEqualTo(Map.of("why", "limit", "of", "frames", "max", (long) RingDepths.of(com.payonel.modularcomputers.bus.DeviceType.NETWORK)));
		assertThat(a.broadcast(args("all")).first()).as("a broadcast it can't take: missed, counted").isEqualTo(0L);
		assertThat(received(b)).as("the oldest is still there").containsEntry("data", "f0");
		assertThat(a.send(args(b.address(), "more")).first()).as("room again").isEqualTo(true);
		for (int i = 1; i < sent; i++) {
			assertThat(received(b)).containsEntry("data", "f" + i);
		}
		assertThat(received(b)).containsEntry("data", "more");
	}

	@Test
	void aWlanCardsTierSetsItsReachAndItsStrengthTurnsItDown() {
		NetworkCard small = card(CardKind.TIER1_WLAN, BlockPos.ZERO);
		NetworkCard near = card(CardKind.TIER1_WLAN, new BlockPos(NetworkCard.TIER1_RANGE, 0, 0));
		NetworkCard beyond = card(CardKind.TIER2_WLAN, new BlockPos(NetworkCard.TIER1_RANGE + 1, 0, 0));
		assertThat(small.range()).as("OpenComputers' tier 1").isEqualTo(16);
		assertThat(new NetworkCard(CardKind.TIER2_WLAN).range()).as("and tier 2").isEqualTo(400);
		assertThat(small.broadcast(new Arguments(List.of("hi"))).first()).as("16 blocks: only the near one").isEqualTo(1L);
		assertThat(small.setStrength(new Arguments(List.of(1000L))).first()).as("up to its tier's").isEqualTo(16L);
		assertThat(small.setStrength(new Arguments(List.of(5L))).first()).isEqualTo(5L);
		assertThat(small.broadcast(new Arguments(List.of("hi"))).first()).as("turned down: none").isEqualTo(0L);
		assertThat(beyond.broadcast(new Arguments(List.of("hi"))).first()).as("a tier 2 reaches both").isEqualTo(2L);
		assertThat(card(CardKind.NETWORK, BlockPos.ZERO).getStrength(args()).failed()).as("wired: none").isTrue();
		assertThat(near.wireless()).isTrue();
	}
}
