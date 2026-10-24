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

	@Test
	void aFrameGoesToTheCardAtItsAddressAcknowledgedAndABroadcastToEveryCardInRange() {
		NetworkCard a = card(CardKind.WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.WLAN, new BlockPos(3, 4, 0));
		NetworkCard c = card(CardKind.WLAN, new BlockPos(0, 0, 10));
		NetworkCard far = card(CardKind.WLAN, new BlockPos(NetworkCard.RANGE + 1, 0, 0));
		assertThat(a.send(args(b.address(), "hello")).first()).as("acknowledged").isEqualTo(true);
		assertThat(received(b)).containsEntry("from", a.address()).containsEntry("data", "hello").containsEntry("distance", 5.0)
			.doesNotContainKey("broadcast");
		assertThat(c.interruptPending()).as("not for it").isFalse();
		assertThat(a.send(args("id:" + far.address(), "x")).values()).as("out of range").containsExactly(false, NetworkCard.UNREACHABLE);
		assertThat(a.send(args(UUID.randomUUID().toString(), "x")).values()).as("nobody there").containsExactly(false, NetworkCard.UNREACHABLE);
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
		NetworkCard wireless = card(CardKind.WLAN, BlockPos.ZERO);
		a.reach(Map.of(new BlockPos(100, 0, 0), 120));
		a.broadcast(args("on the wire"));
		assertThat(received(b)).containsEntry("data", "on the wire").containsEntry("distance", 120.0);
		assertThat(received(sibling)).as("in the same block: on the same wire").containsEntry("distance", 0.0);
		assertThat(near.interruptPending()).as("near, but no cable").isFalse();
		assertThat(wireless.interruptPending()).as("Wi-Fi isn't Ethernet").isFalse();
	}

	@Test
	void aCardNotSeenLatelyOrInAnotherWorldIsntThere() {
		NetworkCard a = card(CardKind.WLAN, BlockPos.ZERO);
		NetworkCard gone = card(CardKind.WLAN, BlockPos.ZERO);
		gone.place(world, BlockPos.ZERO, NOW - Ether.STALE_TICKS - 1);
		NetworkCard elsewhere = card(CardKind.WLAN, BlockPos.ZERO);
		elsewhere.place(new Object(), BlockPos.ZERO, NOW);
		assertThat(a.send(args(gone.address(), "x")).first()).isEqualTo(false);
		assertThat(a.send(args(elsewhere.address(), "x")).first()).isEqualTo(false);
	}

	@Test
	void aFrameTooLongIsRefusedAndFramesWaitInOrderWhileAProgramRuns() {
		NetworkCard a = card(CardKind.WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.WLAN, BlockPos.ZERO);
		Result tooLong = com.payonel.modularcomputers.runtime.DeviceMethods.invoke(a, "send", args(b.address(), "x".repeat(NetworkCard.MTU + 1)));
		assertThat(tooLong.failed()).isTrue();
		a.send(args(b.address(), "1"));
		a.send(args(b.address(), "2"));
		assertThat(received(b)).containsEntry("data", "1");
		assertThat(received(b)).containsEntry("data", "2");
		assertThat(received(b)).isNull();
		b.accepting(false); // no program: it takes nothing, and says so
		assertThat(a.send(args(b.address(), "3")).values()).containsExactly(false, NetworkCard.BUSY);
		b.accepting(true);
		assertThat(received(b)).isNull();
	}

	@Test
	void aFrameHoldingTheWakeMessageTurnsItsComputerOnWhileItsOff() {
		NetworkCard sender = card(CardKind.WLAN, BlockPos.ZERO);
		NetworkCard card = new NetworkCard(CardKind.WLAN);
		UUID id = UUID.randomUUID();
		card.idFrom(() -> id);
		card.place(world, BlockPos.ZERO, NOW);
		Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus()));
		machine.board().setCpu(new Cpu(CpuTier.TIER_1));
		new com.payonel.modularcomputers.machine.TestBus(machine).card(card, id);
		assertThat(sender.send(args(card.address(), "wake up")).first()).as("off, and not listening: nobody hears").isEqualTo(false);
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
		NetworkCard a = card(CardKind.WLAN, BlockPos.ZERO);
		NetworkCard b = card(CardKind.WLAN, BlockPos.ZERO);
		int sent = 0;
		while (a.send(args(b.address(), "f" + sent)).first().equals(true)) {
			sent++;
		}
		assertThat(a.send(args(b.address(), "more")).values()).as("full: refused, not dropped").containsExactly(false, NetworkCard.BUSY);
		assertThat(a.broadcast(args("all")).first()).as("a broadcast it can't take: missed, counted").isEqualTo(0L);
		assertThat(received(b)).as("the oldest is still there").containsEntry("data", "f0");
		assertThat(a.send(args(b.address(), "more")).first()).as("room again").isEqualTo(true);
		for (int i = 1; i < sent; i++) {
			assertThat(received(b)).containsEntry("data", "f" + i);
		}
		assertThat(received(b)).containsEntry("data", "more");
	}
}
