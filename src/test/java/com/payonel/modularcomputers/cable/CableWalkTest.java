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

	private final CableWalk.World world = new CableWalk.World() {
		@Override
		public CableWalk.Kind kind(BlockPos pos) {
			return blocks.getOrDefault(pos, CableWalk.Kind.NONE);
		}

		@Override
		public boolean hasPort(BlockPos pos, Direction side) {
			return fronts.get(pos) != side;
		}
	};

	private static final BlockPos COMPUTER = BlockPos.ZERO;

	private void cable(int x, int y, int z) {
		blocks.put(new BlockPos(x, y, z), CableWalk.Kind.CABLE);
	}

	private BlockPos device(int x, int y, int z, Direction front) {
		BlockPos pos = new BlockPos(x, y, z);
		blocks.put(pos, CableWalk.Kind.DEVICE);
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
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(screen, 3));
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
	void devicesComeBackNearestFirstAndDontPassTheCableOn() {
		cable(1, 0, 0);
		cable(2, 0, 0);
		cable(3, 0, 0);
		BlockPos far = device(4, 0, 0, Direction.NORTH);
		BlockPos near = device(1, 1, 0, Direction.NORTH);
		cable(1, 2, 0); // behind the near device: not reachable through it
		device(1, 3, 0, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(near, 1), new CableWalk.Found(far, 3));
	}

	@Test
	void aDeviceOnTwoCablesIsFoundOnce() {
		cable(1, 0, 0);
		cable(1, 0, 1);
		cable(0, 0, 1);
		BlockPos screen = device(2, 0, 1, Direction.NORTH);
		assertThat(CableWalk.from(world, COMPUTER)).containsExactly(new CableWalk.Found(screen, 2));
	}
}
