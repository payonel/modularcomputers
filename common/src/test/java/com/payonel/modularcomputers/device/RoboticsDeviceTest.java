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
		assertThat(withoutCall(device.pull(args()).first())).isEqualTo(Map.of("event", "walk", "ok", false, "why", RoboticsDevice.STOPPED));
		assertThat(device.refusal("walk")).as("free again at once").isNull();
	}

	@Test
	void aCommandWhileOneRunsIsRefusedBusyButGetPullAndStopAreFree() {
		device.walk(args(2L));
		assertThat(device.refusal("turn")).isEqualTo(Buffered.BUSY);
		assertThat(device.refusal("position")).as("a read waits its turn too").isEqualTo(Buffered.BUSY);
		assertThat(device.free()).containsExactlyInAnyOrder("get", "pull", "stop");
		assertThat(device.stop(args()).failed()).as("a stop goes through").isFalse();
		assertThat(device.refusal("turn")).isNull();
	}

	@Test
	void aFailureSaysWhy() {
		device.walk(args(2L));
		device.failed("no path");
		assertThat(device.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", "no path"));
	}

	/** Blocked is one word, its block and face named fields beside it (the user's: one shape), the block "" when none is named. */
	@Test
	void blockedIsTheWordWithTheBlockAndItsFace() {
		device.walk(args(2L));
		device.blocked("minecraft:stone", "front");
		assertThat(withoutCall(device.pull(args()).first())).isEqualTo(Map.of("event", "walk", "ok", false, "why", "blocked", "block", "minecraft:stone", "face", "front"));
		device.walk(args(2L));
		device.blocked(null, "up");
		assertThat(withoutCall(device.pull(args()).first())).as("no block named: \"\", never nil").isEqualTo(Map.of("event", "walk", "ok", false, "why", "blocked", "block", "", "face", "up"));
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

	/** A walk under way at a save goes on after the load (the user's): given again as it was, from where its last command left it. */
	@Test
	void aWalkUnderWayAtASaveGoesOnAfterTheLoad() {
		device.called("walk", List.of(2L));
		device.walk(args(2L));
		var saved = device.saved();
		Drifting body = new Drifting();
		RoboticsDevice loaded = new RoboticsDevice();
		loaded.moveWith(body);
		loaded.restore(saved);
		loaded.accepting(true);
		assertThat(body.asked).as("it walks on").containsExactly("walk");
		assertThat(loaded.busy()).isTrue();
		assertThat(loaded.pull(args()).first()).as("no end: it goes on").isNull();
	}

	/** One that can't go on (its body won't walk now) ends interrupted, the fallback, its call as given. */
	@Test
	void aCommandThatCantGoOnAfterALoadEndsInterruptedWithItsCall() {
		RoboticsDevice loaded = new RoboticsDevice();
		loaded.moveWith(new Drifting() {
			@Override
			public String walk(double forward, double right, double up) {
				return "no legs";
			}
		});
		loaded.restore(Map.of("active", "walk", "args", List.of(2L)));
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", "interrupted",
			"call", Map.of("args", List.of(2L), "progress", 0L)));
		assertThat(loaded.busy()).isFalse();
	}

	@Test
	void walkingCostsPowerByTheBlock() {
		device.power().supply(this); // (a running board pays)
		device.walked(2.5);
		assertThat(device.power().collect(0)).isEqualTo((long) Math.ceil(2.5 * RoboticsDevice.WALK_COST)); // (by the distance, a part of a block too)
	}

	/** A body that can drift, be off its spot, fall, and says what it was asked to plan. */
	private static class Drifting implements RoboticsDevice.Body {
		final List<String> asked = new ArrayList<>();
		Map<String, Object> drift;
		boolean off;
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
		public boolean offSpot() {
			return off;
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

	/** An event without its call (a test about something else). */
	static Object withoutCall(Object event) {
		if (!(event instanceof Map<?, ?> map)) {
			return event;
		}
		Map<Object, Object> rest = new java.util.LinkedHashMap<>(map);
		rest.remove("call");
		return rest;
	}

	/** A shove is said, and nothing more (the user's: no way back of its own; the program corrects it). */
	@Test
	void aShoveIsSaidAndNothingMore() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.drifted(Map.of("forward", 1.5, "right", 0L, "up", 0L));
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "notice", "why", "shove", "drift", Map.of("forward", 1.5, "right", 0L, "up", 0L)));
		assertThat(robot.pull(args()).first()).as("nothing more").isNull();
		assertThat(body.asked).as("no way back").isEmpty();
		assertThat(robot.busy()).isFalse();
	}

	/**
	 * Positional, always (the user's): off its spot, a command answering at once fails drifted, doing
	 * nothing; one over time without moving it (turn, swing) starts and ends at once, drifted, its
	 * drift and its call; a move goes on from its spot as ever.
	 */
	@Test
	void aPositionalCommandOffItsSpotFailsDrifted() {
		Drifting body = new Drifting();
		body.off = true;
		body.drift = Map.of("forward", 0.6, "right", 0L, "up", 0L);
		RoboticsDevice robot = with(body);
		assertThat(robot.refusal("scan")).isEqualTo(Buffered.DRIFTED);
		assertThat(robot.refusal("take")).isEqualTo(Buffered.DRIFTED);
		assertThat(robot.refusal("walk")).as("a move: from its spot, as ever").isNull();
		assertThat(robot.refusal("land")).as("a landing is a move").isNull();
		robot.called("turn", List.of("left"));
		assertThat(robot.turn(args("left")).first()).isEqualTo(true);
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "turn", "ok", false, "why", "drifted",
			"drift", Map.of("forward", 0.6, "right", 0L, "up", 0L), "call", Map.of("args", List.of("left"), "progress", 0L)));
		assertThat(body.asked).as("it didn't turn").isEmpty();
		assertThat(robot.busy()).isFalse();
		CraftingCard card = cardIn(robot);
		assertThat(card.refusal("take")).as("its crafting card's too").isEqualTo(Buffered.DRIFTED);
		assertThat(card.refusal("craft")).as("a craft isn't where the robot is").isNull();
	}

	/** stop() always makes where it is where its last command left it, a command running or not, falling too (the user's). */
	@Test
	void aStopAlwaysTakesWhereItIs() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.stop(args());
		assertThat(body.asked).as("nothing running: still where it is").containsExactly("stop", "replan here");
		assertThat(robot.pull(args()).first()).as("and no event").isNull();
		body.asked.clear();
		body.falling = true;
		robot.stop(args());
		assertThat(body.asked).as("falling: its fall goes on, and where it is is taken").containsExactly("replan here");
	}

	/** A stopped command's end says its call (the user's): its arguments as the board gave them, and how far it got. */
	@Test
	void aStoppedCommandSaysItsCall() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.called("walk", java.util.Arrays.asList(20L, null));
		robot.walk(args(20L));
		robot.stop(args());
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "walk", "ok", false, "why", "stopped",
			"call", Map.of("args", List.of(20L), "progress", 0L)));
	}

	private static RoboticsDevice with(Drifting body) {
		RoboticsDevice robot = new RoboticsDevice();
		robot.moveWith(body);
		robot.accepting(true);
		return robot;
	}

	/** A fresh boot (its devices reset first) doesn't go on with an old program's command; a program going on after a load does. */
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
		assertThat(resumed.busy()).as("its walk goes on").isTrue();
	}

	/** A stop's plan is where it stopped (the body plans it: on its legs the block it's nearest), not where the stopped command began. */
	@Test
	void aStopPlansWhereItStopped() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.walk(args(5L));
		robot.stop(args());
		assertThat(body.asked).containsExactly("walk", "stop", "replan here", "replan here"); // (its end's, and the stop's own: always)
		assertThat(withoutCall(robot.pull(args()).first())).isEqualTo(Map.of("event", "walk", "ok", false, "why", "stopped"));
	}

	/**
	 * A fall is one notice, said as it begins and as it lands (the user's): why fall, its drift and
	 * the block below; falling, it's busy but for its flights.
	 */
	@Test
	void aFallIsSaidAsItBeginsAndAsItLandsAndWhileItFallsItsBusy() {
		Drifting body = new Drifting();
		RoboticsDevice robot = with(body);
		robot.fallEnds(Map.of("forward", 0L, "right", 0L, "up", -1L), "minecraft:stone");
		assertThat(robot.pull(args()).first()).as("no landing for a fall whose start wasn't said").isNull();
		robot.fallBegins(Map.of("forward", 0L, "right", 0L, "up", 0L), "minecraft:air");
		assertThat(robot.pull(args()).first()).isEqualTo(Map.of("event", "notice", "why", "fall",
			"drift", Map.of("forward", 0L, "right", 0L, "up", 0L), "below", "minecraft:air"));
		body.falling = true;
		assertThat(robot.refusal("walk")).isEqualTo(Buffered.BUSY);
		assertThat(robot.refusal("fly")).as("a tier 2 catches itself on its thrusters (the user's)").isNull();
		assertThat(robot.refusal("flyTo")).isNull();
		assertThat(robot.state()).isEqualTo(RoboticsDevice.FALLING);
		body.falling = false;
		robot.fallEnds(Map.of("forward", 0L, "right", 0L, "up", -5L), "minecraft:stone");
		assertThat(robot.pull(args()).first()).as("landed: its drift the drop").isEqualTo(Map.of("event", "notice", "why", "fall",
			"drift", Map.of("forward", 0L, "right", 0L, "up", -5L), "below", "minecraft:stone"));
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
