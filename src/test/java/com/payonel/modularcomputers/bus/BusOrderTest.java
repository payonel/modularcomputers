package com.payonel.modularcomputers.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BusOrderTest {
	private static final List<String> CASE = List.of("edp", "kbd", "sata:0");
	private final BusOrder order = new BusOrder(32);

	@Test
	void internalDevicesComeFirstInTheirFixedOrderAndDontMove() {
		order.sync(CASE, List.of("id:a"), 0);
		assertThat(order.keys()).containsExactly("edp", "kbd", "sata:0", "id:a");
		assertThat(order.move(1, false)).as("internal").isFalse();
		assertThat(order.move(3, true)).as("not past the internal ones").isFalse();
		order.sync(List.of("edp", "kbd"), List.of("id:a"), 1);
		assertThat(order.keys()).as("a part taken out drops out").containsExactly("edp", "kbd", "id:a");
	}

	@Test
	void externalDevicesKeepThePlayersOrderAndNewOnesGoLast() {
		order.sync(CASE, List.of("id:a", "id:b"), 0);
		assertThat(order.move(4, true)).isTrue(); // b above a
		order.sync(CASE, List.of("id:a", "id:b", "id:c"), 1);
		assertThat(order.keys()).containsExactly("edp", "kbd", "sata:0", "id:b", "id:a", "id:c");
	}

	@Test
	void aLateDeviceComesBackToItsOwnPlace() {
		order.sync(CASE, List.of("id:a", "id:b", "id:c"), 0);
		order.sync(CASE, List.of("id:a", "id:c"), 1); // b's chunk isn't loaded yet
		assertThat(order.keys()).as("listed, offline").containsExactly("edp", "kbd", "sata:0", "id:a", "id:b", "id:c");
		assertThat(order.isOnline("id:b")).isFalse();
		assertThat(order.connected()).containsExactly("edp", "kbd", "sata:0", "id:a", "id:c");
		order.sync(CASE, List.of("id:c", "id:a", "id:b"), 2);
		assertThat(order.connected()).containsExactly("edp", "kbd", "sata:0", "id:a", "id:b", "id:c");
	}

	@Test
	void aFullListBumpsTheDeviceMissingLongestElseTheNewOneWaits() {
		List<String> all = new ArrayList<>(IntStream.range(0, order.externalCapacity()).mapToObj(i -> "id:" + i).toList());
		order.sync(CASE, all, 0);
		order.sync(CASE, concat(all, "id:new"), 1);
		assertThat(order.keys()).doesNotContain("id:new");
		assertThat(order.waiting()).isEqualTo(1);
		assertThat(order.status()).isEqualTo(new BusStatus(32, 3 + 20, 20, 0, 1));
		assertThat(order.status().overflow()).isTrue();

		List<String> missingTwo = new ArrayList<>(all);
		missingTwo.remove("id:3");
		order.sync(CASE, missingTwo, 5); // 3 last seen at 1
		missingTwo.remove("id:7");
		order.sync(CASE, missingTwo, 9); // 7 last seen at 5
		order.sync(CASE, concat(missingTwo, "id:new"), 10);
		assertThat(order.keys()).as("3 was missing longest").contains("id:7", "id:new").doesNotContain("id:3");
		assertThat(order.keys().getLast()).isEqualTo("id:new");
		assertThat(order.waiting()).isZero();
		assertThat(order.status()).isEqualTo(new BusStatus(32, 3 + 20 - 1, 20, 1, 0));
	}

	@Test
	void theCapacityIsTheBoardsSettingAndShrinkingItDropsMissingDevicesFirst() {
		order.setCapacity(14); // room for 2 external devices
		order.sync(CASE, List.of("id:a", "id:b", "id:c"), 0);
		assertThat(order.keys()).containsExactly("edp", "kbd", "sata:0", "id:a", "id:b");
		assertThat(order.waiting()).isEqualTo(1);
		order.setCapacity(32);
		order.sync(CASE, List.of("id:a", "id:b", "id:c"), 1);
		assertThat(order.waiting()).isZero();
		order.sync(CASE, List.of("id:a", "id:c"), 2); // b missing
		order.setCapacity(14);
		order.sync(CASE, List.of("id:a", "id:c"), 3);
		assertThat(order.keys()).as("b, missing, made room").containsExactly("edp", "kbd", "sata:0", "id:a", "id:c");
	}

	@Test
	void anOfflineDeviceCanBeForgottenButNotAnOnlineOne() {
		order.sync(CASE, List.of("id:a", "id:b"), 0);
		order.setConnected("id:a", false);
		order.sync(CASE, List.of("id:b"), 1);
		assertThat(order.forget("id:b")).as("online").isFalse();
		assertThat(order.forget("edp")).as("internal").isFalse();
		assertThat(order.forget("id:a")).isTrue();
		assertThat(order.keys()).containsExactly("edp", "kbd", "sata:0", "id:b");
		assertThat(order.disconnected()).isEmpty();
	}

	@Test
	void disconnectedDevicesStayListedButOffTheBus() {
		order.sync(CASE, List.of("id:a", "id:b"), 0);
		assertThat(order.setConnected("edp", false)).as("internal").isFalse();
		assertThat(order.setConnected("id:a", false)).isTrue();
		assertThat(order.setConnected("id:a", false)).as("no change").isFalse();
		assertThat(order.keys()).contains("id:a");
		assertThat(order.connected()).containsExactly("edp", "kbd", "sata:0", "id:b");
		assertThat(order.isConnected("id:a")).isFalse();
	}

	@Test
	void restoresTheSavedListAndDisconnections() {
		order.restore(List.of(new BusOrder.Entry("id:b", 4), new BusOrder.Entry("id:a", 3)), List.of("id:a", "id:gone"));
		order.sync(CASE, List.of("id:a"), 5);
		assertThat(order.keys()).containsExactly("edp", "kbd", "sata:0", "id:b", "id:a");
		assertThat(order.disconnected()).containsExactly("id:a");
		assertThat(order.external()).containsExactly(new BusOrder.Entry("id:b", 4), new BusOrder.Entry("id:a", 5));
	}

	private static List<String> concat(List<String> keys, String key) {
		List<String> all = new ArrayList<>(keys);
		all.add(key);
		return all;
	}
}
