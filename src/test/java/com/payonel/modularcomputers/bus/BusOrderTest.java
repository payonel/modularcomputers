package com.payonel.modularcomputers.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BusOrderTest {
	private static final List<String> CASE = List.of("display", "keyboard", "sata:0");
	private final BusOrder order = new BusOrder(32);

	@Test
	void internalDevicesComeFirstInTheirFixedOrderAndDontMove() {
		order.sync(CASE, List.of("cable:a"), 0);
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:0", "cable:a");
		assertThat(order.move(1, false)).as("internal").isFalse();
		assertThat(order.move(3, true)).as("not past the internal ones").isFalse();
		order.sync(List.of("display", "keyboard"), List.of("cable:a"), 1);
		assertThat(order.keys()).as("a part taken out drops out").containsExactly("display", "keyboard", "cable:a");
	}

	@Test
	void externalDevicesKeepThePlayersOrderAndNewOnesGoLast() {
		order.sync(CASE, List.of("cable:a", "cable:b"), 0);
		assertThat(order.move(4, true)).isTrue(); // b above a
		order.sync(CASE, List.of("cable:a", "cable:b", "cable:c"), 1);
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:0", "cable:b", "cable:a", "cable:c");
	}

	@Test
	void aLateDeviceComesBackToItsOwnPlace() {
		order.sync(CASE, List.of("cable:a", "cable:b", "cable:c"), 0);
		order.sync(CASE, List.of("cable:a", "cable:c"), 1); // b's chunk isn't loaded yet
		assertThat(order.keys()).as("listed, offline").containsExactly("display", "keyboard", "sata:0", "cable:a", "cable:b", "cable:c");
		assertThat(order.isOnline("cable:b")).isFalse();
		assertThat(order.connected()).containsExactly("display", "keyboard", "sata:0", "cable:a", "cable:c");
		order.sync(CASE, List.of("cable:c", "cable:a", "cable:b"), 2);
		assertThat(order.connected()).containsExactly("display", "keyboard", "sata:0", "cable:a", "cable:b", "cable:c");
	}

	@Test
	void aFullListBumpsTheDeviceMissingLongestElseTheNewOneWaits() {
		List<String> all = new ArrayList<>(IntStream.range(0, order.externalCapacity()).mapToObj(i -> "cable:" + i).toList());
		order.sync(CASE, all, 0);
		order.sync(CASE, concat(all, "cable:new"), 1);
		assertThat(order.keys()).doesNotContain("cable:new");
		assertThat(order.waiting()).isEqualTo(1);
		assertThat(order.status()).isEqualTo(new BusStatus(32, 3 + 20, 20, 0, 1));
		assertThat(order.status().overflow()).isTrue();

		List<String> missingTwo = new ArrayList<>(all);
		missingTwo.remove("cable:3");
		order.sync(CASE, missingTwo, 5); // 3 last seen at 1
		missingTwo.remove("cable:7");
		order.sync(CASE, missingTwo, 9); // 7 last seen at 5
		order.sync(CASE, concat(missingTwo, "cable:new"), 10);
		assertThat(order.keys()).as("3 was missing longest").contains("cable:7", "cable:new").doesNotContain("cable:3");
		assertThat(order.keys().getLast()).isEqualTo("cable:new");
		assertThat(order.waiting()).isZero();
		assertThat(order.status()).isEqualTo(new BusStatus(32, 3 + 20 - 1, 20, 1, 0));
	}

	@Test
	void theCapacityIsTheBoardsSettingAndShrinkingItDropsMissingDevicesFirst() {
		order.setCapacity(14); // room for 2 external devices
		order.sync(CASE, List.of("cable:a", "cable:b", "cable:c"), 0);
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:0", "cable:a", "cable:b");
		assertThat(order.waiting()).isEqualTo(1);
		order.setCapacity(32);
		order.sync(CASE, List.of("cable:a", "cable:b", "cable:c"), 1);
		assertThat(order.waiting()).isZero();
		order.sync(CASE, List.of("cable:a", "cable:c"), 2); // b missing
		order.setCapacity(14);
		order.sync(CASE, List.of("cable:a", "cable:c"), 3);
		assertThat(order.keys()).as("b, missing, made room").containsExactly("display", "keyboard", "sata:0", "cable:a", "cable:c");
	}

	@Test
	void anOfflineDeviceCanBeForgottenButNotAnOnlineOne() {
		order.sync(CASE, List.of("cable:a", "cable:b"), 0);
		order.setConnected("cable:a", false);
		order.sync(CASE, List.of("cable:b"), 1);
		assertThat(order.forget("cable:b")).as("online").isFalse();
		assertThat(order.forget("display")).as("internal").isFalse();
		assertThat(order.forget("cable:a")).isTrue();
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:0", "cable:b");
		assertThat(order.disconnected()).isEmpty();
	}

	@Test
	void disconnectedDevicesStayListedButOffTheBus() {
		order.sync(CASE, List.of("cable:a", "cable:b"), 0);
		assertThat(order.setConnected("display", false)).as("internal").isFalse();
		assertThat(order.setConnected("cable:a", false)).isTrue();
		assertThat(order.setConnected("cable:a", false)).as("no change").isFalse();
		assertThat(order.keys()).contains("cable:a");
		assertThat(order.connected()).containsExactly("display", "keyboard", "sata:0", "cable:b");
		assertThat(order.isConnected("cable:a")).isFalse();
	}

	@Test
	void restoresTheSavedListAndDisconnections() {
		order.restore(List.of(new BusOrder.Entry("cable:b", 4), new BusOrder.Entry("cable:a", 3)), List.of("cable:a", "cable:gone"));
		order.sync(CASE, List.of("cable:a"), 5);
		assertThat(order.keys()).containsExactly("display", "keyboard", "sata:0", "cable:b", "cable:a");
		assertThat(order.disconnected()).containsExactly("cable:a");
		assertThat(order.external()).containsExactly(new BusOrder.Entry("cable:b", 4), new BusOrder.Entry("cable:a", 5));
	}

	private static List<String> concat(List<String> keys, String key) {
		List<String> all = new ArrayList<>(keys);
		all.add(key);
		return all;
	}
}
