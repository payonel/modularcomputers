package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.Face;
import com.payonel.modularcomputers.device.OperatorCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.device.SoundCard;
import com.payonel.modularcomputers.device.SoundFormat;
import com.payonel.modularcomputers.machine.Motherboard;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The manual's programs for the sound card, the terminal, the operator card and the robotic
 * piston (docs/examples/sound-*, terminal-*, operator-*, piston-*), each run on a computer with
 * the devices it uses, as a player would copy one. They check what each program does to its
 * devices (what the card played, the commands run, the arms moved, the screen drawn) and the files
 * it writes, never what mcos says.
 */
class DeviceExamplesTest extends McosHarness {
	/**
	 * Until the program has taken a redstone card's change and waits again: a change it hasn't
	 * read yet and the next one would be read as one (the level after both).
	 */
	private void heard(RedstoneCard card) {
		settle(() -> server.isEmpty() && !card.interruptPending());
		untilIdle();
	}

	// ---- the sound card ------------------------------------------------------------------------

	/** What a sound card played, tick by tick (its block's tick: a game tick each), and in which formats. */
	private static final class Speaker {
		final ByteArrayOutputStream played = new ByteArrayOutputStream();
		final List<SoundFormat> formats = Collections.synchronizedList(new ArrayList<>());
		private long time;

		Speaker(SoundCard card, List<Runnable> ticks) {
			ticks.add(() -> {
				byte[] out = card.play(time++);
				if (out != null) {
					played.writeBytes(out);
					if (formats.isEmpty() || !formats.getLast().equals(card.format())) {
						formats.add(card.format());
					}
				}
			});
		}

		int size() {
			return played.size();
		}
	}

	@Test
	void theMelodyPlaysEveryBeatOfItsTuneAtTheDspDefaults() {
		SoundCard card = new SoundCard();
		devices.put(Motherboard.exp(0), card);
		Speaker speaker = new Speaker(card, ticks);
		boot();
		file("melody.lua", example("sound-melody"));
		type("lua /melody.lua 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		int beats = 16; // (its tune: 16 notes and rests)
		int beat = 2000; // (a quarter second at 8000 a second, a byte each)
		settle(() -> speaker.size() == beats * beat && !card.playing());
		assertThat(speaker.formats).as("OSS's defaults, as every open starts").containsExactly(SoundFormat.DEFAULT);
	}

	@Test
	void theDoorbellChimesInSixteenBitOnceAPressAndWaitsForTheChimeToEnd() {
		RedstoneCard button = new RedstoneCard();
		SoundCard card = new SoundCard();
		devices.put(Motherboard.exp(0), button);
		devices.put(Motherboard.exp(1), card);
		Speaker speaker = new Speaker(card, ticks);
		boot();
		file("bell.lua", example("sound-doorbell"));
		type("lua /bell.lua 2> /err &\r");
		untilIdle();
		SoundFormat chimeFormat = new SoundFormat(11025, "s16le", 1, 0);
		settle(() -> card.format().equals(chimeFormat)); // (set by OSS's ioctls)
		int chime = 2 * 4410 * 2; // two tones of 0.4 s at 11025 a second, two bytes a sample
		assertThat(speaker.size()).as("quiet until pressed").isZero();
		server.add(() -> button.sense(0, 15)); // north: pressed
		settle(() -> speaker.size() == chime && !card.playing());
		server.add(() -> button.sense(0, 0));
		heard(button);
		server.add(() -> button.sense(0, 15)); // and again
		settle(() -> speaker.size() == 2 * chime && !card.playing());
		assertThat(speaker.formats).containsOnly(chimeFormat);
		assertThat(read(root().resolve("err"))).isEmpty();
	}

	@Test
	void theSirenPlaysStereoForItsTimeAndStopsAtOnceWhenTold() {
		SoundCard card = new SoundCard();
		devices.put(Motherboard.exp(0), card);
		Speaker speaker = new Speaker(card, ticks);
		boot();
		file("siren.lua", example("sound-siren"));
		type("lua /siren.lua 2 > /out 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		int full = 2 * 8000 * 2; // two seconds of 8000 frames, two bytes a frame
		assertThat(speaker.size()).as("all of it played before it ended (sync)").isEqualTo(full);
		assertThat(speaker.formats).containsExactly(new SoundFormat(8000, "u8", 2, 0));
		byte[] frames = speaker.played.toByteArray();
		boolean leftOnly = false;
		boolean rightOnly = false;
		for (int i = 0; i < frames.length; i += 2) {
			int left = frames[i] & 0xFF;
			int right = frames[i + 1] & 0xFF;
			leftOnly |= left != 128 && right == 128;
			rightOnly |= right != 128 && left == 128;
		}
		assertThat(leftOnly && rightOnly).as("it swings between the speakers").isTrue();

		// Again, for its default ten seconds, stopped once it's sounding.
		file("run.sh", "lua /siren.lua > /out2 2> /err2\ntouch /done2\n");
		type("sh /run.sh &\r");
		settle(() -> speaker.size() > full + 8000);
		type("touch /tmp/siren.off\r");
		untilFile(root().resolve("done2"), "");
		assertThat(read(root().resolve("err2"))).isEmpty();
		assertThat(card.playing()).as("what was queued is dropped (reset), not played on").isFalse();
		assertThat(speaker.size()).isLessThan(full + 10 * 8000 * 2);
	}

	// ---- the terminal --------------------------------------------------------------------------

	@Test
	void theClockKeepsTheWorldsTimeInTheTerminalsTopRightCorner() {
		boot();
		file("clock.lua", example("terminal-clock"));
		type("lua /clock.lua 2> /err &\r");
		settle(() -> screen.buffer().row(0).matches(".* Day \\d+ \\d\\d:\\d\\d $"));
		assertThat(screen.buffer().row(0)).hasSize(50);
	}

	@Test
	void theListGivesEachTerminalsSizeFromItsMethods() {
		boot();
		file("list.lua", example("terminal-list"));
		type("lua /list.lua > /out 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(read(root().resolve("out"))).containsPattern("(?m)^terminal0 +50x16 +50x16 +-$");
	}

	@Test
	void theSignDrawsAFramedMessageInTheMiddleOfTheScreenThroughItsWriteMethod() {
		boot();
		file("sign.lua", example("terminal-sign"));
		type("lua /sign.lua terminal0 'Back soon' 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		// 16 rows: the box's three are rows 7 to 9 (6 to 8 counted from 0), centered in 50 columns.
		String edge = "+" + "-".repeat(13) + "+";
		assertThat(screen.buffer().row(6)).isEqualTo(" ".repeat(17) + edge + " ".repeat(18));
		assertThat(screen.buffer().row(7)).isEqualTo(" ".repeat(17) + "|  Back soon  |" + " ".repeat(18));
	}

	@Test
	void theMouseProgramReportsEachClickWhereItWasUntilQ() {
		boot();
		file("mouse.lua", example("terminal-mouse"));
		type("lua /mouse.lua 2> /err; touch /done\r");
		settle(() -> screen.decoder().mouseTracking() == com.payonel.modularcomputers.display.VtDecoder.MOUSE_NORMAL && screen.decoder().mouseSgr());
		untilIdle(); // (reading: the program waits for the mouse)
		server.add(() -> {
			screen.mouse(com.payonel.modularcomputers.device.Terminal.MouseAction.PRESS, com.payonel.modularcomputers.device.Terminal.MOUSE_LEFT, 4, 2, 0);
			screen.mouse(com.payonel.modularcomputers.device.Terminal.MouseAction.RELEASE, com.payonel.modularcomputers.device.Terminal.MOUSE_LEFT, 4, 2, 0);
		});
		settle(() -> screenText().contains("left released at column 5, row 3"));
		assertThat(screenText()).contains("left at column 5, row 3");
		type("q");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(screen.decoder().mouseTracking()).as("its mouse given back").isEqualTo(com.payonel.modularcomputers.display.VtDecoder.MOUSE_OFF);
	}

	/** What the terminal shows, a line a row. */
	private String screenText() {
		StringBuilder text = new StringBuilder();
		for (int row = 0; row < screen.buffer().resolution().rows(); row++) {
			text.append(screen.buffer().row(row).stripTrailing()).append('\n');
		}
		return text.toString();
	}

	// ---- the operator card ---------------------------------------------------------------------

	/** An operator card whose block runs only time and weather commands, keeping each one run. */
	private static OperatorCard operator(List<String> ran) {
		OperatorCard card = new OperatorCard();
		card.runWith(command -> {
			ran.add(command);
			if (command.startsWith("time")) {
				return new OperatorCard.Outcome(true, 1000, "Set the time to 1000\n");
			} else if (command.startsWith("weather")) {
				return new OperatorCard.Outcome(true, 1, "Set the weather to clear\n");
			} else if (command.startsWith("title")) {
				return new OperatorCard.Outcome(true, 1, "Showing new title for payonel\n");
			}
			return new OperatorCard.Outcome(false, 0, "Unknown or incomplete command\n");
		});
		return card;
	}

	@Test
	void daylightSetsTheTimeAndClearsTheWeatherAtDusk() {
		List<String> ran = Collections.synchronizedList(new ArrayList<>());
		devices.put(Motherboard.exp(0), operator(ran));
		boot();
		// (dusk at once, and a look every second: the world's clock stands still in a test)
		file("day.lua", example("operator-daylight").replace("local DUSK = 18 ", "local DUSK = 0 ").replace("local CHECK = 50 ", "local CHECK = 1 "));
		type("lua /day.lua 2> /tmp/err &\r");
		settle(() -> ran.size() >= 4);
		assertThat(ran.subList(0, 4)).containsExactly("time set day", "weather clear", "time set day", "weather clear");
	}

	@Test
	void theScriptRunsEachCommandLogsItAndStopsAtTheFirstFailure() {
		List<String> ran = Collections.synchronizedList(new ArrayList<>());
		devices.put(Motherboard.exp(0), operator(ran));
		boot();
		file("op.lua", example("operator-script"));
		file("setup.txt", "# the spawn's settings\ntime set day\n\n  weather clear\nbogus\ntime set night\n");
		type("lua /op.lua /setup.txt /op.log 2> /err; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(ran).containsExactly("time set day", "weather clear", "bogus");
		assertThat(read(root().resolve("op.log"))).isEqualTo(
			"2: time set day: ok 1000 Set the time to 1000\n"
				+ "4: weather clear: ok 1 Set the weather to clear\n"
				+ "5: bogus: FAILED 0 Unknown or incomplete command\n");
	}

	@Test
	void theGreeterSaysWelcomeAtEachStepAndLogsWhatTheServerSaid() {
		List<String> ran = Collections.synchronizedList(new ArrayList<>());
		RedstoneCard plate = new RedstoneCard();
		devices.put(Motherboard.exp(0), plate);
		devices.put(Motherboard.exp(1), operator(ran));
		boot();
		file("greet.lua", example("operator-greeter"));
		type("lua /greet.lua 2> /tmp/err &\r");
		untilIdle();
		server.add(() -> plate.sense(1, 15)); // south: stepped on
		settle(() -> Files.isRegularFile(root().resolve("home/visitors.log"))
			&& read(root().resolve("home/visitors.log")).contains("visitor: Showing new title for payonel\n"));
		assertThat(ran).containsExactly("title @a[distance=..5] title \"Welcome home!\"");
		server.add(() -> plate.sense(1, 0)); // off, and on again: a second visitor
		heard(plate);
		server.add(() -> plate.sense(1, 15));
		settle(() -> ran.size() == 2);
		settle(() -> read(root().resolve("home/visitors.log")).lines().count() == 2);
	}

	// ---- the robotic piston --------------------------------------------------------------------

	/** A move a piston was asked for: which piston, where, how. */
	private record Move(String piston, String side, RoboticPiston.Motion motion) {
	}

	/**
	 * A piston whose front faces {@code front}, keeping each move it's asked for: its arms move as a
	 * block's do (an arm out stays out until retracted; extending one out is free).
	 */
	private RoboticPiston piston(String name, Face front, List<Move> moves) {
		RoboticPiston piston = new RoboticPiston();
		piston.frontFrom(() -> front);
		Set<String> out = new HashSet<>();
		piston.moveWith((side, motion) -> {
			moves.add(new Move(name, side, motion));
			return switch (motion) {
				case PUSH -> RoboticPiston.Moved.DONE;
				case EXTEND -> out.add(side) ? RoboticPiston.Moved.DONE : RoboticPiston.Moved.ALREADY_OUT;
				case RETRACT -> out.remove(side) ? RoboticPiston.Moved.DONE : RoboticPiston.Moved.NOT_EXTENDED;
			};
		});
		devices.put("id:" + name, piston);
		return piston;
	}

	@Test
	void theDoorClosesWhileTheLeverIsOffAndOpensWhileItIsOn() {
		List<Move> moves = Collections.synchronizedList(new ArrayList<>());
		RedstoneCard lever = new RedstoneCard();
		devices.put(Motherboard.exp(0), lever);
		piston("door", Face.NORTH, moves);
		boot();
		file("door.lua", example("piston-door"));
		type("lua /door.lua 2> /tmp/err &\r");
		settle(() -> moves.size() == 1);
		untilIdle();
		assertThat(moves).as("closed at the start: its front is north").containsExactly(new Move("door", "north", RoboticPiston.Motion.EXTEND));
		server.add(() -> lever.sense(3, 15)); // west: the lever on
		settle(() -> moves.size() == 2);
		assertThat(moves.get(1)).isEqualTo(new Move("door", "north", RoboticPiston.Motion.RETRACT));
		untilIdle();
		server.add(() -> lever.sense(3, 0));
		settle(() -> moves.size() == 3);
		assertThat(moves.get(2)).isEqualTo(new Move("door", "north", RoboticPiston.Motion.EXTEND));
	}

	@Test
	void thePusherPushesEveryPeriodAndLogsWhenItIsBlockedAndMovesAgain() {
		List<Move> moves = Collections.synchronizedList(new ArrayList<>());
		RoboticPiston pusher = piston("pusher", Face.EAST, moves);
		boolean[] blocked = {false};
		pusher.moveWith((side, motion) -> {
			moves.add(new Move("pusher", side, motion));
			return blocked[0] ? RoboticPiston.Moved.CANNOT_PUSH : RoboticPiston.Moved.DONE;
		});
		boot();
		file("push.lua", example("piston-pusher").replace("local PERIOD = 2 ", "local PERIOD = 0.1 ").replace("local BLOCKED = 10 ", "local BLOCKED = 0.2 "));
		type("lua /push.lua 2> /tmp/err &\r");
		settle(() -> moves.size() >= 3);
		assertThat(moves.getFirst()).as("its front is east").isEqualTo(new Move("pusher", "east", RoboticPiston.Motion.PUSH));
		server.add(() -> blocked[0] = true);
		java.nio.file.Path log = root().resolve("home/pusher.log");
		settle(() -> Files.isRegularFile(log) && read(log).contains("blocked: east\n"));
		int now = moves.size();
		settle(() -> moves.size() >= now + 2); // (it tries again)
		server.add(() -> blocked[0] = false);
		settle(() -> read(log).contains("moving again\n"));
		assertThat(read(log).lines().filter(line -> line.contains("blocked")).count()).as("said once").isEqualTo(1);
	}

	@Test
	void theWavePushesEveryPistonInTurnForEachRound() {
		List<Move> moves = Collections.synchronizedList(new ArrayList<>());
		piston("first", Face.NORTH, moves);
		piston("second", Face.EAST, moves);
		boot();
		file("wave.lua", example("piston-wave"));
		type("lua /wave.lua front 2 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(moves).hasSize(4);
		assertThat(moves.get(0).piston()).isNotEqualTo(moves.get(1).piston());
		assertThat(moves.subList(2, 4)).as("the same order each round").isEqualTo(moves.subList(0, 2));
		assertThat(moves).allMatch(move -> move.motion() == RoboticPiston.Motion.PUSH)
			.allMatch(move -> move.side().equals(move.piston().equals("first") ? "north" : "east"));
	}
}
