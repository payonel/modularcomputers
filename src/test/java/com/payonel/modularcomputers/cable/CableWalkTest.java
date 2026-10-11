package com.payonel.modularcomputers.cable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class CableWalkTest {
	private final Map<BlockPos, CableWalk.Kind> blocks = new HashMap<>();
	/** Devices whose front (no port) faces this way. */
	private final Map<BlockPos, Direction> fronts = new HashMap<>();
	/** Bluetooth links, both ways. */
	private final Map<BlockPos, java.util.List<CableWalk.Link>> links = new HashMap<>();

	private void link(BlockPos a, BlockPos b, int meters) {
		links.computeIfAbsent(a, pos -> new java.util.ArrayList<>()).add(new CableWalk.Link(b, meters));
		links.computeIfAbsent(b, pos -> new java.util.ArrayList<>()).add(new CableWalk.Link(a, meters));
	}

	private final CableWalk.World world = new CableWalk.World() {
		@Override
		public CableWalk.Kind kind(BlockPos pos) {
			return blocks.getOrDefault(pos, CableWalk.Kind.NONE);
		}

		@Override
		public boolean hasPort(BlockPos pos, Direction side) {
			return fronts.get(pos) != side;
		}

		@Override
		public java.util.List<CableWalk.Link> wireless(BlockPos pos) {
			return links.getOrDefault(pos, java.util.List.of());
		}
	};

	private static final BlockPos COMPUTER = BlockPos.ZERO;

	private void cable(int x, int y, int z) {
		blocks.put(new BlockPos(x, y, z), CableWalk.Kind.CABLE);
	}

	private BlockPos device(int x, int y, int z, Direction front) {
		return block(x, y, z, front, CableWalk.Kind.DEVICE);
	}

	/** A device that carries the connection on, like a screen. */
	private BlockPos relay(int x, int y, int z, Direction front) {
		return block(x, y, z, front, CableWalk.Kind.RELAY);
	}

	private BlockPos block(int x, int y, int z, Direction front, CableWalk.Kind kind) {
		BlockPos pos = new BlockPos(x, y, z);
		blocks.put(pos, kind);
		fronts.put(pos, front);
		return pos;
	}

	{
		blocks.put(COMPUTER, CableWalk.Kind.DEVICE);
		fronts.put(COMPUTER, Direction.NORTH);
	}

	@Test
	void findsADeviceAtTheEndOfACable() {
		cable(1, 0, 0);
		cable(2, 0, 0);
		cable(3, 0, 0);
		BlockPos screen = device(4, 0, 0, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(screen, 4));
	}

	@Test
	void anAdjacentDeviceWithoutACableIsNotConnected() {
		device(1, 0, 0, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).isEmpty();
	}

	@Test
	void cablesDontPlugIntoAFront() {
		cable(0, 0, -1); // in front of the computer
		device(0, 0, -2, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).isEmpty();
		cable(1, 0, 0);
		device(2, 0, 0, Direction.WEST); // its front faces the cable
		assertThat(CableWalk.from(world, COMPUTER)).isEmpty();
	}

	@Test
	void devicesComeBackNearestFirstAndOnlyRelaysPassTheConnectionOn() {
		cable(1, 0, 0);
		cable(2, 0, 0);
		cable(3, 0, 0);
		BlockPos far = device(4, 0, 0, Direction.NORTH);
		BlockPos near = device(1, 1, 0, Direction.NORTH);
		cable(1, 2, 0); // behind the near device: not reachable through it
		device(1, 3, 0, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(near, 2), new CableWalk.Found(far, 4));
	}

	@Test
	void aDeviceOnTwoCablesIsFoundOnce() {
		cable(1, 0, 0);
		cable(1, 0, 1);
		cable(0, 0, 1);
		BlockPos screen = device(2, 0, 1, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(screen, 3));
	}

	@Test
	void relaysConnectLikeCablesAndAreFoundThemselves() {
		BlockPos first = relay(1, 0, 0, Direction.NORTH); // right next to the computer
		BlockPos second = relay(2, 0, 0, Direction.NORTH);
		cable(3, 0, 0);
		BlockPos third = relay(4, 0, 0, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(
			new CableWalk.Found(first, 1), new CableWalk.Found(second, 2), new CableWalk.Found(third, 4));
	}

	@Test
	void relaysDontConnectThroughTheirFront() {
		cable(1, 0, 0);
		relay(2, 0, 0, Direction.EAST); // its glass faces away from the cable: the back connects
		BlockPos behind = relay(3, 0, 0, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).hasSize(1).doesNotContain(new CableWalk.Found(behind, 3));
	}

	@Test
	void fromARelayAnAdjacentDeviceIsConnected() {
		BlockPos screen = relay(5, 0, 0, Direction.NORTH);
		blocks.put(new BlockPos(6, 0, 0), CableWalk.Kind.DEVICE);
		assertThat(CableWalk.from(world, screen)).containsExactly(new CableWalk.Found(new BlockPos(6, 0, 0), 1));
	}

	@Test
	void bluetoothReachesADeviceWithoutACable() {
		BlockPos piston = device(10, 0, 0, Direction.NORTH);
		link(COMPUTER, piston, 10);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(piston, 10, true));
	}

	@Test
	void devicesComeBackNearestFirstWhetherByCableOrBluetooth() {
		cable(1, 0, 0);
		cable(2, 0, 0);
		BlockPos wired = device(3, 0, 0, Direction.NORTH); // 3m by cable
		BlockPos linked = device(0, 2, 0, Direction.NORTH); // 2m by Bluetooth
		link(COMPUTER, linked, 2);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(linked, 2, true), new CableWalk.Found(wired, 3, false));
	}

	@Test
	void relaysPassBluetoothOnButDevicesDont() {
		cable(1, 0, 0);
		BlockPos hub = relay(2, 0, 0, Direction.NORTH);
		BlockPos far = relay(20, 0, 0, Direction.NORTH);
		link(hub, far, 18);
		BlockPos piston = device(20, 0, 5, Direction.NORTH);
		link(far, piston, 5); // daisy chain: hub -> far hub -> piston
		BlockPos beyond = device(40, 0, 0, Direction.NORTH);
		link(piston, beyond, 20); // a device doesn't pass it on
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(
			new CableWalk.Found(hub, 2, false), new CableWalk.Found(far, 20, true), new CableWalk.Found(piston, 25, true));
	}
}
