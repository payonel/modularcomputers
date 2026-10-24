package com.payonel.modularcomputers.bus;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A device is on one bus at a time: the first board to have it holds it; the others find it missing until it's let go. */
class BusHoldsTest {
	private final Terminal screen = new Terminal(Resolution.DEFAULT);

	private Machine machine() {
		Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus()));
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.board().setExternal(Map.of("id:screen", screen));
		return machine;
	}

	@Test
	void theFirstBoardHoldsItAndTheOtherGetsItWhenLetGo() {
		Machine first = machine();
		Machine second = machine();
		first.rebuildBus(0);
		second.rebuildBus(0);
		assertThat(first.board().bus().devices()).containsExactly(screen);
		assertThat(second.board().bus().devices()).as("held by the first: missing here").isEmpty();

		long before = BusHolds.version();
		first.unload();
		assertThat(BusHolds.version()).as("a release tells the others").isGreaterThan(before);
		second.rebuildBus(1);
		assertThat(second.board().bus().devices()).containsExactly(screen);
		first.rebuildBus(2);
		assertThat(first.board().bus().devices()).as("now the second's").isEmpty();
	}

	@Test
	void aBoardWithoutACpuHoldsNothing() {
		Machine first = machine();
		Machine second = machine();
		first.rebuildBus(0);
		first.board().setCpu(null);
		first.rebuildBus(1); // no CPU, no bus: it lets go
		second.rebuildBus(1);
		assertThat(second.board().bus().devices()).containsExactly(screen);
	}
}
