package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The robotics device: its actions reach its body, and how they went comes back as events. */
class RoboticsDeviceTest {
	private final RoboticsDevice device = new RoboticsDevice();
	/** What the body was asked, in order. */
	private final List<String> asked = new ArrayList<>();
	/** Why the body refuses to walk (null: it walks). */
	private String refuse;

	@BeforeEach
	void body() {
		device.moveWith(new RoboticsDevice.Body() {
			@Override
			public String walk(double forward, double right, double up) {
				asked.add("walk " + forward + " " + right + " " + up);
				return refuse;
			}

			@Override
			public String turn(double degrees) {
				asked.add("turn " + degrees);
				return null;
			}

			@Override
			public void stop() {
				asked.add("stop");
			}
		});
		device.accepting(true);
		asked.clear();
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private Object state() {
		return ((Map<?, ?>) device.get(args()).first()).get("state");
	}

	@Test
	void aWalkGoesToItsBodyAndEndsInAnEvent() {
		assertThat(device.walk(args(3L, -1L)).first()).isEqualTo(true);
		assertThat(asked).containsExactly("walk 3.0 -1.0 0.0");
		assertThat(state()).isEqualTo("walking");
		assertThat(device.interruptPending()).isFalse();
		device.arrived();
		assertThat(device.interruptPending()).isTrue();
		assertThat(device.pull(args()).first()).as("its end: an event named after its method").isEqualTo(Map.of("event", "walk", "ok", true));
		assertThat(state()).isEqualTo("idle");
	}

	@Test
	void aWalkItsBodyCantStartFailsWithWhy() {
		refuse = "no way there";
		var result = device.walk(args(5L));
		assertThat(result.error()).isEqualTo("no way there");
		assertThat(state()).isEqualTo("idle");
	}

	@Test
	void aWalkTooFarIsRefusedBeforeTheBodyHearsOfIt() {
		assertThat(device.walk(args(RoboticsDevice.MAX_WALK + 1)).error()).startsWith("too far");
		assertThat(asked).isEmpty();
	}

	@Test
	void turnsRightAnglesOnly() {
		device.turn(args("left"));
		device.turn(args("around"));
		assertThat(device.turn(args(45L)).error()).as("no degrees: right angles only (the user's)").startsWith("not a way");
		assertThat(asked).containsExactly("turn -90.0", "turn 180.0");
		assertThat(device.turn(args("up")).error()).isEqualTo("not a way: up");
	}

	@Test
	void walksAreWholeBlocks() {
		assertThat(device.walk(args(1.5)).error()).isEqualTo(RoboticsDevice.NOT_WHOLE);
		assertThat(device.walk(args(2.0, -1L)).failed()).isFalse();
	}

	@Test
	void stoppingAWalkEndsItStoppedAndStoppingNothingDoesNothing() {
		device.stop(args());
		assertThat(device.interruptPending()).as("nothing was under way").isFalse();
		device.walk(args(2L));
		device.stop(args());
		assertThat(device.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", RoboticsDevice.STOPPED));
		assertThat(device.refusal("walk")).as("free again at once").isNull();
	}

	@Test
	void aCommandWhileOneRunsIsRefusedBusyButGetPullStopAndSetsStopAreFree() {
		device.walk(args(2L));
		assertThat(device.refusal("turn")).isEqualTo(Buffered.BUSY);
		assertThat(device.refusal("position")).as("a read waits its turn too").isEqualTo(Buffered.BUSY);
		assertThat(device.free()).contains("get", "pull", "stop", "set");
		assertThat(device.set(args(Map.of("action", "turn", "way", "right"))).error()).as("set refuses its own actions while busy").isEqualTo(Buffered.BUSY);
		assertThat(device.set(args(Map.of("action", "stop"))).failed()).as("but a stop goes through").isFalse();
		assertThat(device.refusal("turn")).isNull();
	}

	@Test
	void blockedSaysWhy() {
		device.walk(args(2L));
		device.blocked("stuck");
		assertThat(device.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", "stuck"));
	}

	@Test
	void setTakesAnActionTable() {
		device.set(args(Map.of("action", "walk", "forward", 4L)));
		device.arrived(); // (one command at a time: the walk ends before the turn)
		device.set(args(Map.of("action", "turn", "way", "right")));
		device.set(args(Map.of("action", "stop")));
		assertThat(asked).containsExactly("walk 4.0 0.0 0.0", "turn 90.0", "stop");
		assertThat(device.set(args(Map.of("action", "jump"))).error()).startsWith("not an action");
	}

	@Test
	void withNoProgramTakingDataItStopsAndItsEventsGo() {
		device.walk(args(2L));
		device.arrived();
		device.accepting(false);
		assertThat(asked).endsWith("stop");
		assertThat(device.interruptPending()).isFalse();
		assertThat(state()).isEqualTo("idle");
	}

	@Test
	void aWalkUnderWayAtASaveEndsBlockedAsTheProgramGoesOn() {
		device.walk(args(2L));
		var saved = device.saved();
		RoboticsDevice loaded = new RoboticsDevice();
		loaded.restore(saved);
		assertThat(loaded.interruptPending()).as("not until a program takes data").isFalse();
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", RoboticsDevice.INTERRUPTED));
	}

	@Test
	void walkingCostsPowerByTheBlock() {
		device.power().supply(this); // (a running board pays)
		device.walked(2.5);
		assertThat(device.power().collect(0)).isEqualTo((long) Math.ceil(2.5 * RoboticsDevice.WALK_COST)); // (by the distance, a part of a block too)
	}

	/** A body that can drift, fail its way back, fall, and says what it was asked to plan. */
	private static final class Drifting implements RoboticsDevice.Body {
		final List<String> asked = new ArrayList<>();
		Map<String, Object> drift;
		String noWayBack;
		boolean falling;
		boolean frozen;

		@Override
		public String walk(double forward, double right, double up) {
			asked.add("walk");
			return null;
		}

		@Override
		public String turn(double degrees) {
			asked.add("turn");
			return null;
		}

		@Override
		public void stop() {
			asked.add("stop");
		}

		@Override
		public Map<String, Object> drift() {
			return drift;
		}

		@Override
		public String returnToPlan() {
			asked.add("return");
			return noWayBack;
		}

		@Override
		public void holdHere() {
			asked.add("hold here");
		}

		@Override
		public void replan() {
			asked.add("replan");
		}

		@Override
		public void replanHere() {
			asked.add("replan here");
		}

		@Override
		public boolean falling() {
			return falling;
		}

		@Override
		public boolean frozen() {
			return frozen;
		}
	}

	private static RoboticsDevice with(Drifting body) {
		RoboticsDevice robot = new RoboticsDevice();
		robot.moveWith(body);
		robot.accepting(true);
		return robot;
	}

	/** A fresh boot (its devices reset first) isn't given an old program's interrupted end; a program going on after a load is. */
	@Test
	void anInterruptedEndGoesToTheProgramGoingOnNotToAFreshBoot() {
		RoboticsDevice loaded = new RoboticsDevice();
		loaded.moveWith(new Drifting());
		loaded.restore(Map.of("active", "walk"));
		loaded.accepting(false); // (a fresh boot's reset)
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).as("no old end at a fresh boot").isNull();
		RoboticsDevice resumed = new RoboticsDevice();
		resumed.moveWith(new Drifting());
		resumed.restore(Map.of("active", "walk"));
		resumed.accepting(true); // (its program goes on)
		assertThat(resumed.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", RoboticsDevice.INTERRUPTED));
	}

	/** Shoved where it can't get back from (no path), it says so once and holds where it is: no stream of shoves. */
	@Test
	void aWayBackItCantMakeIsSaidOnceAndItHoldsWhereItIs() {
		Drifting body = new Drifting();
		body.noWayBack = "no path";
		RoboticsDevice robot = with(body);
		robot.drifted(Map.of("forward", 1.5, "right", 0L, "up", 0L));
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "shoved", "drift", Map.of("forward", 1.5, "right", 0L, "up", 0L)));
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "return", "ok", false, "why", "no path"));
		assertThat(body.asked).containsExactly("return", "hold here");
		assertThat(robot.busy()).isFalse();
	}

	/** A positional command given off its plan, with no way back, is refused with why (it can't run where it should). */
	@Test
	void aPositionalCommandWithNoWayBackIsRefusedWithWhy() {
		Drifting body = new Drifting();
		body.drift = Map.of("forward", 0.5, "right", 0L, "up", 0L);
		body.noWayBack = "no path";
		RoboticsDevice robot = with(body);
		assertThat(robot.refusal("scan")).isEqualTo("no path");
		body.noWayBack = null;
		assertThat(robot.refusal("scan")).as("with a way back: it goes, the command waiting").isEqualTo(Buffered.BUSY);
		assertThat(robot.state()).isEqualTo(RoboticsDevice.RETURNING);
	}

	/** A stop's plan is where it stopped (the body plans it: on its legs the block it's nearest), not where the stopped command began. */
	@Test
	void aStopPlansWhereItStopped() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.walk(args(5L));
		robot.stop(args());
		assertThat(body.asked).containsExactly("walk", "stop", "replan here");
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", "stopped"));
	}

	/** Falling, it's busy; landed, its news (fell) ends the wait, said even for a short fall when a command waited. */
	@Test
	void fallingItsBusyAndItsLandingIsNewsWhenSomethingWaited() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.fell(0.01);
		assertThat(robot.pull(args()).first()).as("a settling: nothing to say").isNull();
		body.falling = true;
		assertThat(robot.refusal("walk")).isEqualTo(Buffered.BUSY);
		assertThat(robot.state()).isEqualTo(RoboticsDevice.FALLING);
		body.falling = false;
		robot.fell(0.01);
		assertThat(robot.pull(args()).first()).as("a command waited: its news").isEqualTo(Map.of("event", "fell", "by", 0.01));
		robot.fell(1.0);
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "fell", "by", 1L));
	}

	/**
	 * A field holds a robot's robotics only (the user's): a craft, the card's own work, goes on while
	 * its robot is held, and a new one starts; its hands' commands (moving items, its face actions)
	 * wait, held.
	 */
	@Test
	void aCraftGoesOnWhileAFieldHoldsItsRobotAndItsHandsCommandsWait() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		CraftingCard card = new CraftingCard();
		card.benchIn(new CraftingCard.Bench() {
			@Override
			public boolean robotics() {
				return true;
			}

			@Override
			public RoboticsDevice robot() {
				return robot;
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result list() {
				return com.payonel.modularcomputers.runtime.Result.ok(List.of());
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result craft() {
				return com.payonel.modularcomputers.runtime.Result.ok(Map.of("name", "minecraft:stick", "count", 4L));
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result move(int from, int to, int count) {
				return null;
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result equip(int slot) {
				return null;
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result pickup() {
				return null;
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result drop(String face, int slot, int count) {
				return null;
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result store(String face, int slot, int count, int to) {
				return null;
			}

			@Override
			public com.payonel.modularcomputers.runtime.Result take(String face, int slot, int count, int to) {
				return null;
			}
		});
		card.accepting(true);
		assertThat(card.craft(args(1L)).first()).isEqualTo(true);
		body.frozen = true;
		for (int tick = 0; tick < CraftingCard.CRAFT_TICKS; tick++) {
			card.tick();
		}
		assertThat(card.state()).as("held, its craft done").isEqualTo(CraftingCard.IDLE);
		assertThat(card.refusal("move")).as("its hands' commands wait for robotics").isEqualTo(RoboticsDevice.HELD);
		assertThat(card.refusal("take")).as("its face actions too").isEqualTo(RoboticsDevice.HELD);
		assertThat(card.craft(args(1L)).first()).as("a new craft starts, held").isEqualTo(true);
	}
}
