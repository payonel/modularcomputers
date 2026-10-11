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
		order.restore(List.of("sata:0", "display", "gone", "keyboard"));
		order.sync(List.of("display", "keyboard", "sata:0"));
		assertThat(order.keys()).containsExactly("sata:0", "display", "keyboard");
	}
}
