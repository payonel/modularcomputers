package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
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
	/** Where its last command left it, from its origin (null: no position, its origin then). */
	private Map<String, Object> left;

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

			@Override
			public String fly(double forward, double right, double up) {
				asked.add("fly " + forward + " " + right + " " + up);
				return null;
			}

			@Override
			public String walkTo(double forward, double right, double up) {
				asked.add("walkTo " + forward + " " + right + " " + up);
				return null;
			}

			@Override
			public Result planned() {
				return left == null ? Result.fail("no position") : Result.ok(left);
			}

			@Override
			public double toward(Face way) {
				return switch (way) { // (facing north)
					case EAST -> 90;
					case SOUTH -> 180;
					case WEST -> -90;
					default -> 0;
				};
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

	/** A turn can face the world's way (the user's: a golem may know north): the body says how far, from its heading. */
	@Test
	void aTurnFacesTheWorldsWay() {
		device.turn(args("east"));
		device.turn(args("north"));
		device.turn(args("west"));
		assertThat(asked).as("facing north: a right, none, a left").containsExactly("turn 90.0", "turn 0.0", "turn -90.0");
	}

	/** Every move must end squared (the user's): whole blocks from its origin, within a 4096th, made exact; else it's refused, saying where it would end. */
	@Test
	void aMoveMustEndSquared() {
		assertThat(device.walk(args(4.4)).error()).as("from a block's middle").isEqualTo("not an integer: forward 4.4");
		assertThat(device.walk(args(2.0, 0L, 0.5)).error()).as("an elevation's change is whole").isEqualTo("not an integer: up 0.5");
		assertThat(device.walk(args(2.0, -1L)).failed()).isFalse();
		device.stop(args());
		left = Map.of("forward", 0.6, "right", 0L, "up", 0L, "heading", 0L); // (stopped between blocks)
		assertThat(device.walk(args(4.3)).error()).as("where it would end").isEqualTo("not an integer: forward 4.9");
		assertThat(device.walk(args(4.4)).failed()).as("the rest of the way: squared").isFalse();
		device.stop(args());
		left = Map.of("forward", 0L, "right", 0.6, "up", 0L, "heading", 90L); // (facing the origin's right)
		assertThat(device.walk(args(4.4)).failed()).as("its frame turned: forward is the origin's right").isFalse();
		device.stop(args());
		left = Map.of("forward", 0L, "right", 0L, "up", 0.5, "heading", 0L); // (on a slab)
		assertThat(device.fly(args(0L, 0L, 1L)).error()).as("a flight's height is its feet's").isEqualTo("not an integer: up 1.5");
		assertThat(device.fly(args(0L, 0L, 1.5 + 1.0 / 8192)).failed()).as("within a 4096th").isFalse();
		device.stop(args());
		assertThat(device.walkTo(args(2.5)).error()).isEqualTo("not an integer: forward 2.5");
		assertThat(device.walkTo(args(2L, 1L)).failed()).isFalse();
		assertThat(asked).containsExactly("walk 2.0 -1.0 0.0", "stop", "walk 4.4 0.0 0.0", "stop", "walk 4.4 0.0 0.0", "stop",
			"fly 0.0 0.0 1.5", "stop", "walkTo 2.0 1.0 0.0");
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
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "notice", "what", "shoved", "drift", Map.of("forward", 1.5, "right", 0L, "up", 0L)));
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "notice", "what", "stuck", "why", "no path"));
		assertThat(body.asked).containsExactly("return", "hold here");
		assertThat(robot.busy()).isFalse();
	}

	/**
	 * A way back blocked partway says so (stuck, why) and holds where it is, where it goes on from left
	 * the last good point: no program asked for the way back, so its end moves nothing (a blocked walk's
	 * does). A walk() then takes it back.
	 */
	@Test
	void aWayBackBlockedPartwayHoldsAndKeepsItsLastGoodPoint() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.drifted(Map.of("forward", 0.9, "right", 0L, "up", 0L));
		assertThat(robot.state()).isEqualTo(RoboticsDevice.RETURNING);
		robot.blocked("blocked: minecraft:piston_head back");
		robot.pull(args());
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "notice", "what", "stuck", "why", "blocked: minecraft:piston_head back"));
		assertThat(body.asked).as("held there, nothing replanned").containsExactly("return", "hold here");
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
		assertThat(robot.pull(args()).first()).as("a command waited: its news").isEqualTo(Map.of("event", "notice", "what", "fell", "by", 0.01));
		robot.fell(1.0);
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "notice", "what", "fell", "by", 1L));
	}

	/**
	 * A field holds a robot's robotics only (the user's): a craft under way, the card's own work, goes
	 * on while its robot is held, but every command the card is given waits, held: the robot is what
	 * works the card (the actor), a craft's start and a move between its own slots too.
	 */
	/** A crafting card in {@code robot}, taking data: its bench makes 4 sticks a craft and does nothing else. */
	private static CraftingCard cardIn(RoboticsDevice robot) {
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
		return card;
	}

	/**
	 * The card's commands are its robot's to wait for (the robot decides, the card asks it): a take
	 * off the robot's plan waits for it to go back (the robot's return, its news the wait's end), and
	 * a craft waits for that move too, without starting one.
	 */
	@Test
	void aCardsTakeOffItsRobotsPlanWaitsForItToGoBackFirst() {
		Drifting body = new Drifting();
		body.drift = Map.of("forward", 0.5, "right", 0L, "up", 0L);
		RoboticsDevice robot = with(body);
		CraftingCard card = cardIn(robot);
		assertThat(card.refusal("craft")).as("a craft isn't where the robot is: no way back for it").isNull();
		assertThat(card.refusal("take")).as("a take is: it waits").isEqualTo(Buffered.BUSY);
		assertThat(robot.state()).as("the robot goes back").isEqualTo(RoboticsDevice.RETURNING);
		assertThat(card.blocker("take")).as("the robot's news ends the wait").isSameAs(robot);
		assertThat(card.refusal("craft")).as("a craft waits for that move too").isEqualTo(Buffered.BUSY);
		body.drift = null;
		robot.arrived(); // (back on its plan)
		assertThat(card.refusal("take")).as("in position: it takes").isNull();
		assertThat(body.asked).containsExactly("return", "replan");
	}

	@Test
	void aCraftUnderWayGoesOnWhileAFieldHoldsItsRobotAndTheCardsCommandsWait() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		CraftingCard card = cardIn(robot);
		assertThat(card.craft(args(1L)).first()).isEqualTo(true);
		body.frozen = true;
		for (int tick = 0; tick < CraftingCard.CRAFT_TICKS; tick++) {
			card.tick();
		}
		assertThat(card.state()).as("held, its craft done").isEqualTo(CraftingCard.IDLE);
		assertThat(card.refusal("craft")).as("a new craft waits: the robot starts it").isEqualTo(RoboticsDevice.HELD);
		assertThat(card.refusal("move")).as("a move between its own slots too: the robot moves them").isEqualTo(RoboticsDevice.HELD);
		assertThat(card.refusal("take")).as("and its face actions").isEqualTo(RoboticsDevice.HELD);
	}
}
