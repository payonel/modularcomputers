package com.payonel.modularcomputers.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BusOrderTest {
	private final BusOrder order = new BusOrder();

	@Test
	void startsInTheDefaultOrder() {
		order.sync(List.of("display", "keyboard", "sata:0"));
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:0");
	}

	@Test
	void keepsThePlayersOrderAndAppendsNewDevices() {
		order.sync(List.of("display", "keyboard", "sata:0"));
		order.move(2, true); // sata:0 above keyboard
		order.sync(List.of("display", "keyboard", "sata:0", "exp:1"));
		assertThat(order.keys()).containsExactly("display", "sata:0", "keyboard", "exp:1");
	}

	@Test
	void removedDevicesDropOut() {
		order.sync(List.of("display", "keyboard", "sata:0", "sata:1"));
		order.sync(List.of("display", "keyboard", "sata:1"));
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:1");
	}

	@Test
	void movesStayInBounds() {
		order.sync(List.of("a", "b", "c"));
		assertThat(order.move(0, true)).isFalse();
		assertThat(order.move(2, false)).isFalse();
		assertThat(order.move(5, true)).isFalse();
		assertThat(order.move(0, false)).isTrue();
		assertThat(order.keys()).containsExactly("b", "a", "c");
	}

	@Test
	void restoreThenSyncDropsDevicesThatAreGone() {
		order.restore(List.of("sata:0", "display", "gone", "keyboard"), List.of());
		order.sync(List.of("display", "keyboard", "sata:0"));
		assertThat(order.keys()).containsExactly("sata:0", "display", "keyboard");
	}

	private static boolean cabled(String key) {
		return key.startsWith("cable:");
	}

	@Test
	void absentCabledDevicesKeepTheirPlace() {
		order.sync(List.of("display", "cable:a", "cable:b"), BusOrderTest::cabled);
		order.sync(List.of("display", "cable:b"), BusOrderTest::cabled); // a's chunk unloaded
		assertThat(order.keys()).containsExactly("display", "cable:b");
		order.sync(List.of("display", "cable:b", "cable:a"), BusOrderTest::cabled);
		assertThat(order.keys()).containsExactly("display", "cable:a", "cable:b");
	}

	@Test
	void movesSkipOverAbsentDevices() {
		order.sync(List.of("display", "cable:a", "cable:b"), BusOrderTest::cabled);
		order.sync(List.of("display", "cable:b"), BusOrderTest::cabled);
		assertThat(order.move(1, true)).isTrue(); // b above display, past the absent a
		assertThat(order.keys()).containsExactly("cable:b", "display");
		assertThat(order.all()).containsExactly("cable:b", "cable:a", "display");
	}

	@Test
	void onlyTheNewestAbsentDevicesAreRemembered() {
		List<String> many = new java.util.ArrayList<>();
		for (int i = 0; i < BusOrder.MAX_REMEMBERED + 3; i++) {
			many.add("cable:" + i);
		}
		order.sync(many, BusOrderTest::cabled);
		order.sync(List.of(), BusOrderTest::cabled);
		assertThat(order.all()).hasSize(BusOrder.MAX_REMEMBERED).doesNotContain("cable:0", "cable:1", "cable:2").contains("cable:3");
	}

	@Test
	void disconnectedDevicesStayListedButLeaveTheBus() {
		order.sync(List.of("display", "cable:a", "cable:b"), BusOrderTest::cabled);
		assertThat(order.setConnected("cable:a", false)).isTrue();
		assertThat(order.setConnected("cable:a", false)).isFalse();
		assertThat(order.keys()).containsExactly("display", "cable:a", "cable:b");
		assertThat(order.connected()).containsExactly("display", "cable:b");
		assertThat(order.isConnected("cable:a")).isFalse();
		assertThat(order.setConnected("cable:a", true)).isTrue();
		assertThat(order.connected()).containsExactly("display", "cable:a", "cable:b");
	}

	@Test
	void disconnectionsAreRememberedWhileTheDeviceIsAway() {
		order.sync(List.of("display", "cable:a"), BusOrderTest::cabled);
		order.setConnected("cable:a", false);
		order.sync(List.of("display"), BusOrderTest::cabled);
		order.sync(List.of("display", "cable:a"), BusOrderTest::cabled);
		assertThat(order.isConnected("cable:a")).isFalse();
	}

	@Test
	void restoresDisconnections() {
		order.restore(List.of("display", "cable:a"), List.of("cable:a", "unknown"));
		order.sync(List.of("display", "cable:a"), BusOrderTest::cabled);
		assertThat(order.connected()).containsExactly("display");
		assertThat(order.disconnected()).containsExactly("cable:a");
	}
}
