package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.LuaScripts;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * mcos (Lua), booted from a disk through the bootloader, as a player would: commands typed at its
 * shell. Tests bring their own programs and check what they do (files on the disks, the computer
 * halting or running), never what mcos says.
 */
class McosTest {
	private final Terminal screen = new Terminal(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus()));
	private final ConcurrentLinkedQueue<Runnable> server = new ConcurrentLinkedQueue<>();
	private final Map<String, Device> devices = new LinkedHashMap<>();

	@TempDir
	Path world;

	McosTest() {
		devices.put("id:screen", screen);
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.setServer(server::add);
	}

	@AfterEach
	void powerOff() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	/** The boot disk's folder (mcos's files, and the test's). */
	private Path root() {
		return world.resolve("mcos-disk");
	}

	/** Puts a file on the boot disk before it boots. */
	private void file(String path, String text) {
		try {
			Path at = root().resolve(path);
			Files.createDirectories(at.getParent());
			Files.writeString(at, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A second disk, at sata:1, with its folder. */
	private Path dataDisk() {
		Path folder = world.resolve("data-disk");
		devices.put("sata:1", new Disk(DiskTier.TIER_1, folder));
		return folder;
	}

	private void boot() {
		boot(() -> {
		});
	}

	/** Boots, with {@code configure} changing mcos's own files on the disk first (its /etc). */
	private void boot(Runnable configure) {
		LuaScripts.copyTree("mcos", root());
		configure.run();
		devices.put("sata:0", new Disk(DiskTier.TIER_1, root()));
		Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
		devices.put("eeprom", eeprom); // (on the bus at its slot, as a board puts it)
		TestBus.of(machine, devices);
		machine.board().setEeprom(eeprom);
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		untilIdle();
	}

	/** Plays the server thread until {@code done}: its asked-for tasks, and a tick a millisecond. */
	private void settle(BooleanSupplier done) {
		long end = System.nanoTime() + 20_000_000_000L;
		while (!done.getAsBoolean()) {
			assertThat(System.nanoTime()).as(() -> "settled in time; the screen:\n" + screen()).isLessThan(end);
			Runnable task;
			while ((task = server.poll()) != null) {
				task.run();
			}
			machine.tick();
			for (Runnable tick : ticks) {
				tick.run(); // (another computer's, and what a test does each tick)
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** What else happens each tick: other computers' ticks, a test's own doings. */
	private final java.util.List<Runnable> ticks = new java.util.ArrayList<>();

	/** Another computer running mcos from a disk of its own, on this test's server thread, with a network card in {@code world}. */
	private final class Other {
		final Terminal screen = new Terminal(Resolution.DEFAULT);
		final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus()));
		final Path root;
		final com.payonel.modularcomputers.device.NetworkCard card;

		Other(String name, Object world, java.util.UUID id) {
			root = McosTest.this.world.resolve(name);
			LuaScripts.copyTree("mcos", root);
			card = networkCard(world, id);
			Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
			Map<String, Device> devices = new LinkedHashMap<>();
			devices.put("id:screen", screen);
			devices.put("sata:0", new Disk(DiskTier.TIER_1, root));
			devices.put("eeprom", eeprom);
			devices.put(Motherboard.exp(0), card);
			machine.board().setCpu(new Cpu(CpuTier.TIER_3));
			machine.setServer(server::add);
			TestBus.of(machine, devices);
			machine.board().setEeprom(eeprom);
			assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
			ticks.add(machine::tick);
		}

		void type(String keys) {
			for (int i = 0; i < keys.length(); i++) {
				screen.press(keys.substring(i, i + 1));
			}
		}
	}

	/** What the console shows (to explain a failure: tests never assert on it). */
	private String screen() {
		StringBuilder text = new StringBuilder();
		var console = machine.board().virtualBus().console().screen();
		for (int row = 0; row < Resolution.DEFAULT.rows(); row++) {
			text.append(console.row(row).stripTrailing()).append('\n');
		}
		return text.toString();
	}

	/** Until mcos has read every key and idles, waiting for more (still running). */
	private void untilIdle() {
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(() -> machine.runner().isEmpty() || (!screen.hasKeys() && runner.state() == Runner.State.WAITING));
		assertThat(machine.runner()).as(() -> "still running; the screen:\n" + screen()).isPresent();
	}

	private void untilHalted() {
		settle(() -> machine.runner().isEmpty());
		assertThat(machine.board().virtualBus().console().screen().row(0)).doesNotStartWith("ABRT");
	}

	private void type(String keys) {
		for (int i = 0; i < keys.length(); i++) {
			screen.press(keys.substring(i, i + 1));
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Until a file is there with this text (a file appears when it's opened, its text when it's closed). */
	private void untilFile(Path file, String text) {
		settle(() -> Files.isRegularFile(file) && read(file).equals(text));
	}

	@Test
	void keysTypedWhileItBootsShowAfterThePromptAndTheLineRuns() {
		LuaScripts.copyTree("mcos", root());
		devices.put("sata:0", new Disk(DiskTier.TIER_1, root()));
		Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
		devices.put("eeprom", eeprom);
		TestBus.of(machine, devices);
		machine.pressPower();
		machine.tick();
		type("echo"); // (before the shell reads: the tty echoes them where the cursor is)
		settle(() -> !screen.hasKeys() && machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false)
			&& screen().lines().anyMatch(row -> row.contains("$")));
		untilIdle();
		String row = screen().lines().filter(r -> r.contains("$")).reduce((a, b) -> b).orElseThrow().stripTrailing();
		assertThat(row).as("the line, after the prompt, where readline drew it: not over the prompt").endsWith(" echo");
		type(" ahead > /ahead\r");
		untilFile(root().resolve("ahead"), "ahead\n");
	}

	@Test
	void haltHalts() {
		boot();
		type("halt\r");
		untilHalted();
	}

	@Test
	void ctrlCDropsTheLineAndTheShellReadsOn() {
		boot();
		type("hal\u0003"); // what was typed is dropped with the ^C
		untilIdle();
		type("t\r"); // not "halt": the line was flushed
		untilIdle();
		type("halt\r");
		untilHalted();
	}

	@Test
	void backspaceErasesBackAcrossAWrappedLine() {
		boot();
		type("x".repeat(60)); // past the screen's 50 columns: the line wraps
		untilIdle();
		type("\u007f".repeat(15)); // back across the wrap
		untilIdle();
		long shown = screen().chars().filter(c -> c == 'x').count();
		assertThat(shown).as("what's left of the line, and nothing erased still showing").isEqualTo(45);
		type("\u007f".repeat(45) + "y\r");
		untilIdle();
		assertThat(screen().chars().filter(c -> c == 'x').count()).as("all of it erased").isZero();
	}

	@Test
	void theShellEditsItsLineAndKeepsAHistory() {
		boot();
		type("echo bc > /h1\r");
		untilFile(root().resolve("h1"), "bc\n");
		untilIdle(); // (the shell back at its prompt: keys typed sooner are the tty's to echo, as bash's are)
		type("\u001B[A"); // up: the last line again
		type("\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D"); // left, onto "c > /h1"... back to "bc"
		type("a"); // inserted mid-line: echo abc > /h1 ... with the file name left to fix
		type("\u0005\u007f2\r"); // ^E, erase the 1, a 2: echo abc > /h2
		untilFile(root().resolve("h2"), "abc\n");
		type("stty -echo; stty > /flags; stty sane\r");
		settle(() -> Files.isRegularFile(root().resolve("flags")) && read(root().resolve("flags")).contains("-echo"));
		assertThat(read(root().resolve("flags"))).as("while the shell isn't reading, the tty is as programs set it").contains("icanon");
	}

	@Test
	void aMultiLinePasteGoesIntoTheLineAndRunsOnlyAtEnter() {
		boot();
		untilIdle();
		screen.paste("touch /p1\ntouch /p2\n");
		untilIdle();
		assertThat(root().resolve("p1")).as("pasted: nothing runs yet").doesNotExist();
		type("\r");
		untilFile(root().resolve("p2"), "");
		assertThat(root().resolve("p1")).as("then all of it").exists();
	}

	@Test
	void ctrlArrowsMoveAWordAsBashs() {
		boot();
		type("echo two > /w");
		type("\u001B[1;5D\u001B[1;5D"); // a word back (to w's start), and another (to two's start)
		type("one ");
		type("\u001B[1;5C"); // a word on: to two's end
		type("s\r");
		untilFile(root().resolve("w"), "one twos\n");
	}

	@Test
	void whatsTypedAheadGoesToTheCommand() {
		boot();
		type("cat > /typed\rhello\r\u0004"); // at once: the shell's line, then cat's input and its end
		untilFile(root().resolve("typed"), "hello\n");
	}

	@Test
	void theShellEndingIsStartedAgain() {
		boot();
		type("exit\r");
		untilIdle();
		type("\u0004"); // end of file ends it too
		untilIdle();
		type("halx\u007ft\r"); // Backspace erases
		untilHalted();
	}

	@Test
	void poweroffTurnsTheComputerOff() {
		boot();
		type("poweroff\r");
		settle(() -> !machine.isPowered());
	}

	@Test
	void rebootStartsItAgain() {
		boot();
		Runner first = machine.runner().orElseThrow();
		type("touch /before; reboot\r");
		settle(() -> machine.runner().isPresent() && machine.runner().get() != first);
		assertThat(machine.isPowered()).isTrue();
		untilIdle(); // booted again, at a new shell
		assertThat(root().resolve("before")).as("written before it went down").isRegularFile();
	}

	@Test
	void devHoldsOnlyDataTheEepromIsReadAndWrittenThroughItAndLsdevAsksTheKernel() {
		dataDisk();
		boot();
		String image = machine.board().eeprom().orElseThrow().image();
		type("ls /dev > /ld; cat /dev/eeprom > /e; lsdev -i > /devs\r");
		untilFile(root().resolve("e"), image);
		settle(() -> Files.isRegularFile(root().resolve("devs")) && read(root().resolve("devs")).contains("terminal0"));
		assertThat(read(root().resolve("ld"))).as("only data: disks' bytes, no terminal files")
			.contains("console\n", "null\n", "eeprom\n", "storage0\n", "storage1\n").doesNotContain("terminal");
		assertThat(read(root().resolve("devs"))).contains("storage0", "storage1", "sata:1", "eeprom ", "terminal0", "id:");
		// written: one set at close, the whole of it (cp writes it in pieces)
		file("new.lua", "-- " + "x".repeat(9000) + "\nboard.halt()\n");
		type("cp /new.lua /dev/eeprom; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(machine.board().eeprom().orElseThrow().image()).isEqualTo(read(root().resolve("new.lua")));
	}

	@Test
	void aRedstoneCardsFileIsTextALineAFace() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		boot();
		file("set.txt", "north 11\n");
		file("bad.txt", "not a face\n");
		type("cp /set.txt /dev/redstone0; cat /dev/redstone0 > /r; cp /bad.txt /dev/redstone0 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(card.output(0)).as("north, as written").isEqualTo(11);
		assertThat(read(root().resolve("r"))).as("a line a face: face level powered")
			.isEqualTo("north 11 11\nsouth 0 0\neast 0 0\nwest 0 0\nup 0 0\ndown 0 0\n");
		assertThat(read(root().resolve("err"))).as("a line that isn't \"face powered\": the write fails").isNotEmpty();
		assertThat(card.output(0)).isEqualTo(11);
	}

	/** A WAV file: its format chunk (tag, channels, rate, bits, block align) and its data. */
	private static byte[] wav(int tag, int channels, int rate, int bits, int block, byte[] data) {
		java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(44 + data.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		out.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes());
		out.put("fmt ".getBytes()).putInt(16).putShort((short) tag).putShort((short) channels).putInt(rate).putInt(rate * block)
			.putShort((short) block).putShort((short) bits);
		out.put("data".getBytes()).putInt(data.length).put(data);
		return out.array();
	}

	private static byte[] noise(int length, long seed) {
		byte[] data = new byte[length];
		new java.util.Random(seed).nextBytes(data);
		return data;
	}

	@Test
	void aplayPlaysWavFilesOnTheSoundCardInTheirFormatAndCatARawOneAtOssDefaults() throws IOException {
		com.payonel.modularcomputers.device.SoundCard card = new com.payonel.modularcomputers.device.SoundCard();
		devices.put(Motherboard.exp(0), card);
		java.io.ByteArrayOutputStream played = new java.io.ByteArrayOutputStream();
		java.util.List<com.payonel.modularcomputers.device.SoundFormat> formats = new java.util.ArrayList<>();
		long[] time = {0};
		ticks.add(() -> {
			byte[] out = card.play(time[0]++); // (its block's tick: a game tick each)
			if (out != null) {
				played.writeBytes(out);
				if (formats.isEmpty() || !formats.getLast().equals(card.format())) {
					formats.add(card.format());
				}
			}
		});
		boot();
		byte[] stereo = noise(22050 * 4 * 3 / 2, 1); // a second and a half of 16-bit stereo: more than its buffer holds
		byte[] adpcm = noise(256 * 6, 2); // six blocks of mono ADPCM
		byte[] raw = noise(4000, 3);
		Files.write(root().resolve("s.wav"), wav(1, 2, 22050, 16, 4, stereo));
		Files.write(root().resolve("a.wav"), wav(0x11, 1, 8000, 4, 256, adpcm));
		Files.write(root().resolve("r.raw"), raw);
		type("aplay /s.wav /a.wav 2> /err; echo $? > /status; cat /r.raw > /dev/dsp; touch /done\r");
		untilFile(root().resolve("done"), "");
		settle(() -> card.format().equals(com.payonel.modularcomputers.device.SoundFormat.DEFAULT) && !card.playing() && played.size() > stereo.length + adpcm.length);
		assertThat(read(root().resolve("status"))).as("played: " + read(root().resolve("err"))).isEqualTo("0\n");
		java.io.ByteArrayOutputStream expected = new java.io.ByteArrayOutputStream();
		expected.writeBytes(stereo);
		expected.writeBytes(adpcm);
		expected.writeBytes(raw);
		assertThat(played.toByteArray()).as("every byte, in order").isEqualTo(expected.toByteArray());
		assertThat(formats).as("each in its own format").containsExactly(
			new com.payonel.modularcomputers.device.SoundFormat(22050, "s16le", 2, 0),
			new com.payonel.modularcomputers.device.SoundFormat(8000, "ima_adpcm", 1, 256),
			com.payonel.modularcomputers.device.SoundFormat.DEFAULT);
	}

	@Test
	void theConsolesShellIsALoginShellRunningTheProfileGoingHomeThenTheShrc() {
		boot(() -> file("home/.shrc", "touch /shrc-ran; alias mine='echo mine'\n"));
		type("echo $HOME > /h; pwd > /p; alias ll > /a; mine > /m; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("h"))).as("the profile's HOME").isEqualTo("/home\n");
		assertThat(read(root().resolve("p"))).as("and there it starts").isEqualTo("/home\n");
		assertThat(read(root().resolve("a"))).as("its aliases").contains("ls -alF");
		assertThat(root().resolve("shrc-ran")).as("then the file ENV names").exists();
		assertThat(read(root().resolve("m"))).as("in the shell itself").isEqualTo("mine\n");
	}

	@Test
	void anUpdateLeavesTheUsersHomeAsItIs() throws IOException {
		file("home/.shrc", "mine\n");
		LuaScripts.copyTree("mcos", root(), true);
		assertThat(read(root().resolve("home/.shrc"))).isEqualTo("mine\n");
		assertThat(root().resolve("etc/profile")).as("the rest written").exists();
	}

	@Test
	void labelSetsADevicesLabelOnItsHomeAndShowsIt() {
		String[] home = {null};
		dataDisk();
		((Disk) devices.get("sata:1")).labelIn(new com.payonel.modularcomputers.bus.Label(() -> home[0], label -> home[0] = label));
		boot();
		type("label storage1 backups; label /dev/storage1 > /l; lsdev > /d; label storage1 " + "x".repeat(40) + " 2> /dev/null; echo $? > /st; "
			+ "label storage0 boot 2> /dev/null; echo $? > /st0; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(home[0]).as("on its home: its item").isEqualTo("backups");
		assertThat(read(root().resolve("l"))).isEqualTo("backups\n");
		assertThat(read(root().resolve("d"))).as("lsdev shows it").contains("backups");
		assertThat(read(root().resolve("st"))).as("too long").isEqualTo("1\n");
		assertThat(read(root().resolve("st0"))).as("a device with no home for one").isEqualTo("1\n");
	}

	@Test
	void dmesgShowsDevicesJoiningAndTheirEventsAndMountsAndClears() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put(Motherboard.exp(0), motion);
		boot();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		type("head -n 1 /dev/motion0 > /dev/null; dmesg > /d; dmesg -C; dmesg > /e; touch /done\r");
		untilFile(root().resolve("done"), "");
		String log = read(root().resolve("d"));
		assertThat(log).as("a device joining").containsPattern("motion0 +" + "[0-9a-f-]+ +attached: motion at exp:0");
		assertThat(log).as("its event").contains("mob=minecraft:pig x=1.5 y=0.0 z=-3.0");
		assertThat(log).as("a mount").contains("mount");
		assertThat(log).as("plain into a file: no colors").doesNotContain("\u001b[");
		assertThat(log.lines().findFirst().orElse("")).as("[time] first").matches("\\[\\d\\d:\\d\\d:\\d\\d\\] .*");
		assertThat(read(root().resolve("e"))).as("cleared").isEmpty();
	}

	@Test
	void lsDashSSortsBySizeLargestFirst() {
		file("s/small", "a");
		file("s/big", "abcdef");
		file("s/mid", "abc");
		file("s/also", "abc");
		boot();
		type("ls -S /s > /o; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("o"))).as("largest first; ties by name").isEqualTo("big\nalso\nmid\nsmall\n");
	}

	@Test
	void aWlanInterfacesTransmitPowerIsItsCardsStrength() {
		var card = new com.payonel.modularcomputers.device.NetworkCard(com.payonel.modularcomputers.device.CardKind.TIER1_WLAN);
		card.idFrom(java.util.UUID::randomUUID);
		devices.put(Motherboard.exp(0), card);
		file("tx.lua", """
			local sys = require("sys")
			local s = assert(sys.socket())
			local before = sys.ioctl(s, "SIOCGIWTXPOW", "wlan0")
			local set = sys.ioctl(s, "SIOCSIWTXPOW", {name = "wlan0", txpower = 999})
			local lowered = sys.ioctl(s, "SIOCSIWTXPOW", {name = "wlan0", txpower = 4})
			local out = io.open("/tx", "w")
			out:write(before, " ", set, " ", lowered)
			out:close()
			""");
		boot();
		type("lua /tx.lua 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("tx"))).as("its tier's at first; at most its tier's; turned down").isEqualTo("16 16 4");
		assertThat(card.range()).isEqualTo(4);
	}

	@Test
	void curlAndWgetMakeRequestsThroughTheInternetCardAndTheServersOwnMachineIsRefused() throws IOException {
		com.sun.net.httpserver.HttpServer web = com.sun.net.httpserver.HttpServer.create(
			new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
		web.createContext("/", exchange -> {
			byte[] body = switch (exchange.getRequestURI().getPath()) {
				case "/moved" -> {
					exchange.getResponseHeaders().add("Location", "/file.txt");
					exchange.sendResponseHeaders(302, -1);
					yield null;
				}
				case "/file.txt" -> "x".repeat(10_000).getBytes();
				case "/missing" -> {
					exchange.sendResponseHeaders(404, -1);
					yield null;
				}
				default -> (exchange.getRequestMethod() + " " + new String(exchange.getRequestBody().readAllBytes())).getBytes();
			};
			if (body != null) {
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		web.start();
		String url = "http://127.0.0.1:" + web.getAddress().getPort();
		com.payonel.modularcomputers.device.InternetCard.policyFrom(
			() -> new com.payonel.modularcomputers.internet.InternetPolicy(true, java.util.List.of("127.0.0.1"), java.util.List.of()));
		try {
			var card = new com.payonel.modularcomputers.device.InternetCard();
			devices.put(Motherboard.exp(0), card);
			ticks.add(card::tick); // (its block's tick)
			boot();
			type("curl -d hi " + url + "/post > /c1; wget -q " + url + "/moved; curl -sf " + url + "/missing > /c2; echo $? > /c3; "
				+ "curl -s http://127.0.0.2:1/ > /dev/null; echo $? > /c4; touch /done\r");
			untilFile(root().resolve("done"), "");
			assertThat(read(root().resolve("c1"))).as("a POST and its body").isEqualTo("POST hi");
			assertThat(read(root().resolve("home/file.txt"))).as("followed, named by its URL, every byte, where the shell is (home)").isEqualTo("x".repeat(10_000));
			assertThat(read(root().resolve("c3"))).as("-f: an HTTP error, no output").isEqualTo("22\n");
			assertThat(read(root().resolve("c2"))).isEmpty();
			assertThat(read(root().resolve("c4"))).as("not allowed: refused").isEqualTo("7\n");
		} finally {
			com.payonel.modularcomputers.device.InternetCard.policyFrom(() -> com.payonel.modularcomputers.internet.InternetPolicy.DEFAULT);
			web.stop(0);
		}
	}

	@Test
	void vtdemoRunsFromUsrBinAndDrawsItsPictureOnATier3Terminal() {
		boot();
		type("vtdemo.lua > /dev/tty; echo $? > /st\r");
		untilFile(root().resolve("st"), "0\n");
		assertThat(screen.buffer().hasTiles()).as("its sixel picture").isTrue();
	}

	@Test
	void anOperatorCardsFileRunsALineAsACommandAndReadsWhatItSaid() {
		var card = new com.payonel.modularcomputers.device.OperatorCard();
		java.util.List<String> ran = new java.util.ArrayList<>();
		card.runWith(command -> {
			ran.add(command);
			return command.startsWith("time")
				? new com.payonel.modularcomputers.device.OperatorCard.Outcome(true, 1000, "Set the time to 1000\n")
				: new com.payonel.modularcomputers.device.OperatorCard.Outcome(false, 0, "Unknown command\n");
		});
		devices.put(Motherboard.exp(0), card);
		boot();
		file("bogus.txt", "bogus\n");
		type("echo /time set day > /dev/operator0; cat /dev/operator0 > /o; cp /bogus.txt /dev/operator0 2> /e; cat /dev/operator0 > /why; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(ran).as("each line a command, its / dropped").containsExactly("time set day", "bogus");
		assertThat(read(root().resolve("o"))).isEqualTo("Set the time to 1000\n");
		assertThat(read(root().resolve("e"))).as("a command that failed fails its write").isNotEmpty();
		assertThat(read(root().resolve("why"))).as("and its file says why").isEqualTo("Unknown command\n");
	}

	@Test
	void dateIsTheWorldsClockOnItsCalendar() {
		long noonOfTheThirdDay = 2 * 24000 + 6000; // (game time: 0 is the first sunrise, 6000 its noon)
		machine.board().setChassis(new com.payonel.modularcomputers.machine.Chassis() {
			@Override
			public int tier() {
				return 3;
			}

			@Override
			public Board.PowerInfo power() {
				return com.payonel.modularcomputers.machine.Chassis.BENCH.power();
			}

			@Override
			public java.util.List<Board.MemoryInfo> memory() {
				return com.payonel.modularcomputers.machine.Chassis.BENCH.memory();
			}

			@Override
			public long time() {
				return noonOfTheThirdDay;
			}
		});
		boot();
		type("date > /d1; date +%s > /d2; date +%H:%M > /d3; date +%Q 2> /dev/null; echo $? > /done\r");
		untilFile(root().resolve("done"), "1\n");
		assertThat(read(root().resolve("d1"))).isEqualTo("Day 3 12:00:00\n");
		assertThat(read(root().resolve("d2"))).as("seconds since the epoch: game time's").isEqualTo(noonOfTheThirdDay / 20 + "\n");
		assertThat(read(root().resolve("d3"))).isEqualTo("12:00\n");
	}

	@Test
	void anAudioSensorsFileGivesASoundALine() {
		com.payonel.modularcomputers.device.AudioSensorCard audio = new com.payonel.modularcomputers.device.AudioSensorCard();
		devices.put(Motherboard.exp(0), audio); // (a card in a slot, as in a case)
		boot();
		type("head -n 1 /dev/hearing0 > /h; touch /done\r");
		untilIdle(); // head waits: nothing was heard
		server.add(() -> audio.hear(Map.of("sound", "minecraft:entity.pig.ambient", "x", 1.5, "y", 0.0, "z", -3.0)));
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("h"))).isEqualTo("minecraft:entity.pig.ambient 1.5 0.0 -3.0\n");
	}

	@Test
	void aBluetoothCardsFileSaysItsLinkAndBoardAndALineRelinksIt() {
		com.payonel.modularcomputers.device.BluetoothCard card = new com.payonel.modularcomputers.device.BluetoothCard();
		java.util.UUID[] link = {null};
		java.util.UUID board = java.util.UUID.randomUUID();
		java.util.UUID other = java.util.UUID.randomUUID();
		card.linkIn(() -> link[0], id -> link[0] = id);
		card.boardFrom(() -> board);
		devices.put(Motherboard.exp(0), card);
		boot();
		file("bad.txt", "nonsense\n");
		type("cat /dev/bluetooth0 > /b1; echo link " + other + " > /dev/bluetooth0; cat /dev/bluetooth0 > /b2; echo link - > /dev/bluetooth0; "
			+ "cp /bad.txt /dev/bluetooth0 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).as("a line that isn't a link: the write fails").isNotEmpty();
		assertThat(read(root().resolve("b1"))).isEqualTo("link -\nboard " + board + "\n");
		assertThat(read(root().resolve("b2"))).isEqualTo("link " + other + "\nboard " + board + "\n");
		assertThat(link[0]).as("unlinked").isNull();
	}

	@Test
	void aSensorsFileGivesAnEventALineAndAReadWaitsForOne() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put(Motherboard.exp(0), motion); // (a card in a slot, as in a case)
		boot();
		type("head -n 2 /dev/motion0 > /m; touch /done\r");
		untilIdle(); // head waits: nothing has moved
		assertThat(root().resolve("done")).doesNotExist();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		server.add(() -> motion.sense(Map.of("mob", "minecraft:zombie", "x", 0.0, "y", 0.0, "z", 2.0)));
		untilFile(root().resolve("done"), "");
		String[] lines = read(root().resolve("m")).split("\n");
		assertThat(lines).hasSize(2);
		assertThat(lines).containsExactly("minecraft:pig 1.5 0.0 -3.0", "minecraft:zombie 0.0 0.0 2.0");
	}

	@Test
	void catPassesASensorsEventsOnAsTheyComeNotWhenItsReadFills() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put(Motherboard.exp(0), motion); // (a card in a slot, as in a case)
		boot();
		type("cat /dev/motion0 | head -n 1 > /m\r"); // (head ends at its line; cat goes on until it writes again)
		untilIdle();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		untilFile(root().resolve("m"), "minecraft:pig 1.5 0.0 -3.0\n");
	}

	@Test
	void tailFollowsASensorsFileShowingWhatWaitsThenEachEventAsItComes() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put(Motherboard.exp(0), motion); // (a card in a slot, as in a case)
		boot();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0))); // (before tail starts)
		untilIdle();
		type("tail -f /dev/motion0 | head -n 2 > /m\r"); // (head ends at its second line; tail goes on, as GNU's, until it writes again)
		untilIdle(); // tail shows the one waiting and follows
		server.add(() -> motion.sense(Map.of("mob", "minecraft:zombie", "x", 0.0, "y", 0.0, "z", 2.0)));
		untilFile(root().resolve("m"), "minecraft:pig 1.5 0.0 -3.0\nminecraft:zombie 0.0 0.0 2.0\n");
	}

	@Test
	void aDiskStationsFileSaysWhatsInItAndALineWrittenIsAnAction() {
		Disk target = new Disk(DiskTier.TIER_2, world.resolve("station-disk"));
		var station = new com.payonel.modularcomputers.device.DiskStation(new com.payonel.modularcomputers.device.DiskStation.Bay() {
			@Override
			public Disk disk(String which) {
				return which.equals(com.payonel.modularcomputers.device.DiskStation.TARGET) ? target : null;
			}

			@Override
			public boolean pay(int cost) {
				return true;
			}

			@Override
			public java.util.List<Path> copyImage(String image, Path folder, boolean replace) {
				return java.util.List.of();
			}
		});
		devices.put("id:station", station); // (cabled, as a station is)
		boot();
		type("cat /dev/diskstation0 > /s; echo wipe > /dev/diskstation0; echo $? > /done\r");
		untilFile(root().resolve("done"), "0\n");
		assertThat(read(root().resolve("s"))).as("a line a slot")
			.isEqualTo("target 2 hfs 0 " + DiskTier.TIER_2.bytes() + "\nsource -\n");
		assertThat(target.getMode(new Arguments(java.util.List.of())).first()).as("wiped").isEqualTo(Disk.RAW);
		target.retire();
	}

	@Test
	void aDisksFileIsItsBytesByOffsetAndAFilesystemsDiskRefusesIt() {
		Path folder = world.resolve("raw-disk");
		Disk raw = new Disk(DiskTier.TIER_1, folder);
		raw.format(Disk.RAW);
		devices.put("sata:1", raw);
		boot();
		file("d.lua", """
			local sys = require("sys")
			local function put(name, text)
			  local out = io.open(name, "w")
			  out:write(tostring(text))
			  out:close()
			end
			local disk = io.open("/dev/storage1", "r+")
			disk:seek("set", 100)
			disk:write("bytes")
			disk:flush()
			disk:seek("set", 98)
			put("/raw", disk:read(9))
			put("/end", disk:seek("end"))
			disk:close()
			local fs = io.open("/dev/storage0", "r")
			local data, why, errno = fs:read(4)
			put("/hfs", tostring(data) .. " " .. tostring(errno))
			put("/done", "")
			""");
		type("lua /d.lua 2> /err; touch /done2\r");
		untilFile(root().resolve("done2"), "");
		assertThat(read(root().resolve("raw"))).as("zeros, then what it wrote").isEqualTo("\0\0bytes\0\0");
		assertThat(raw.read(new Arguments(java.util.List.of(100L, 5L))).first()).as("on the disk").isEqualTo("bytes");
		assertThat(read(root().resolve("end"))).isEqualTo(String.valueOf(DiskTier.TIER_1.bytes()));
		assertThat(read(root().resolve("hfs"))).as("a filesystem's disk refuses").endsWith("EMEDIUMTYPE");
		raw.retire();
	}

	@Test
	void pollWaitsForARedstoneChangeOrAPipesDataOrItsTime() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		boot();
		file("p.lua", """
			local sys = require("sys")
			local function put(name, text)
			  local out = io.open(name, "w")
			  out:write(text)
			  out:close()
			end
			local r, w = sys.pipe()
			local IN = {IN = true}
			local empty = sys.poll({[r] = IN}, 0.1)
			sys.write(w, "x")
			local data = sys.poll({[r] = IN}, 0.1)
			sys.read(r)
			sys.close(w)
			local hup = sys.poll({[r] = IN}, 0.1)
			put("/pipe", tostring(next(empty) == nil) .. " " .. tostring(data[r].IN) .. " " .. tostring(hup[r].HUP))
			local f = io.open("/dev/redstone0")
			local fd = sys.fileno(f)
			local function west()
			  f:seek("set", 0)
			  return f:read("a"):match("west (%d+)")
			end
			local before = west()
			local always = sys.poll({[fd] = IN}, 0) -- an attribute: always readable
			local quiet = sys.poll({[fd] = {PRI = true}}, 0.1) -- nothing changed
			put("/started", "")
			local changed = sys.poll({[fd] = {PRI = true}})
			put("/polled", before .. " " .. west() .. " " .. tostring(always[fd].IN) .. " " .. tostring(next(quiet) == nil) .. " " .. tostring(changed[fd].PRI))
			""");
		type("lua /p.lua; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("started")));
		server.add(() -> card.sense(3, 9)); // west: 9 comes in
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("pipe"))).as("nothing, then the pipe's data, then its writer gone").isEqualTo("true true true");
		assertThat(read(root().resolve("polled"))).as("readable at once; PRI waited for the change, which a read from the start shows")
			.isEqualTo("0 9 true true true");
	}

	@Test
	void aLineWrittenToARedstoneFileTakesEffectAtOnceSoAPulseWorks() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		boot();
		file("pulse.lua", """
			local sys = require("sys")
			local f = io.open("/dev/redstone0", "w")
			f:write("north 15\\n")
			f:flush()
			local started = io.open("/started", "w")
			started:close()
			sys.sleep(0.5)
			f:write("north 0\\n")
			f:close()
			""");
		type("lua /pulse.lua; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("started")));
		assertThat(card.output(0)).as("the first line, set with the file still open").isEqualTo(15);
		untilFile(root().resolve("done"), "");
		assertThat(card.output(0)).isZero();
	}

	@Test
	void pollWaitsOnASensorAndTheKeyboardAtOnce() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put(Motherboard.exp(0), motion); // (a card in a slot, as in a case)
		boot();
		file("m.lua", """
			local sys = require("sys")
			local f = io.open("/dev/motion0")
			local fd = sys.fileno(f)
			local started = io.open("/started", "w")
			started:close()
			local ready = sys.poll({[0] = {IN = true}, [fd] = {IN = true}})
			local out = io.open("/m", "w")
			out:write(tostring(ready[fd] ~= nil and ready[0] == nil), " ", f:read("l"))
			out:close()
			""");
		type("lua /m.lua; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("started")));
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("m"))).isEqualTo("true minecraft:pig 1.5 0.0 -3.0");
	}

	@Test
	void tailFollowsARedstoneFileShowingItAgainWhenItChanges() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		boot();
		// (into /tmp: a file on a disk shows its writes at close, and tail -f keeps it open)
		type("tail -f -n 100 /dev/redstone0 > /tmp/out 2> /tmp/err &\r");
		untilIdle();
		server.add(() -> card.sense(3, 9)); // west: 9 comes in
		settle(() -> server.isEmpty());
		untilIdle(); // (no timer: tail has shown it by the time mcos is idle again)
		type("cp /tmp/out /o; cp /tmp/err /e\r");
		settle(() -> Files.isRegularFile(root().resolve("e")) && read(root().resolve("e")).contains("truncated"));
		String out = read(root().resolve("o"));
		assertThat(out.split("west", -1)).as("the state, then again, changed").hasSize(3);
		assertThat(out.substring(out.lastIndexOf("west"))).startsWith("west 9 0");
		assertThat(read(root().resolve("e"))).isEqualTo("tail: /dev/redstone0: file truncated\n");
		type("kill $!\r");
		untilIdle();
	}

	@Test
	void inotifyTellsOfChangesOnADiskAndInTmp() {
		boot();
		file("n.lua", """
			local sys = require("sys")
			local fd = sys.inotify()
			sys.watch(fd, "/data.txt")
			sys.watch(fd, "/tmp")
			local function write(name, text)
			  local f = io.open(name, "w")
			  f:write(text)
			  f:close()
			end
			write("/data.txt", "one")
			write("/other.txt", "unwatched")
			write("/tmp/t", "x")
			os.rename("/tmp/t", "/tmp/u")
			local seen = {}
			while sys.poll({[fd] = {IN = true}}, 0.5)[fd] do
			  seen[#seen + 1] = sys.read(fd)
			end
			write("/seen", table.concat(seen))
			""");
		type("lua /n.lua; touch /done\r");
		untilFile(root().resolve("done"), "");
		String seen = read(root().resolve("seen"));
		assertThat(seen.lines()).contains("CREATE /tmp/t", "MODIFY /tmp/t", "MOVED_FROM /tmp/t", "MOVED_TO /tmp/u", "MODIFY /data.txt")
			.noneMatch(line -> line.contains("other.txt"));
	}

	@Test
	void theKernelsViewOfTheBusCatchesUpWithAHotPlug() {
		boot();
		type("lsdev > /before\r");
		settle(() -> Files.isRegularFile(root().resolve("before")) && read(root().resolve("before")).contains("storage0"));
		assertThat(read(root().resolve("before"))).doesNotContain("storage1");
		devices.put("sata:1", new Disk(DiskTier.TIER_1, world.resolve("late")));
		TestBus.of(machine, devices);
		untilIdle();
		type("lsdev > /after\r");
		settle(() -> Files.isRegularFile(root().resolve("after")) && read(root().resolve("after")).contains("storage1"));
	}

	@Test
	void lsIntoAFileIsOneNameALineAndFreeSaysTheMemory() {
		boot();
		type("ls / > /listing; free > /mem\r");
		settle(() -> Files.isRegularFile(root().resolve("mem")) && read(root().resolve("mem")).contains("Mem:"));
		assertThat(read(root().resolve("listing"))).contains("bin\n", "dev\n", "lib\n");
		assertThat(read(root().resolve("mem")).lines().toList()).hasSize(2);
	}

	@Test
	void theEditorWritesWhatsTyped() {
		boot();
		type("edit /notes.txt\r");
		untilIdle();
		type("first\rsecond\rthird"); // three lines
		type("\u001B[A\u000B"); // up, ^K: "second" cut
		type("\u001B[B\u0015"); // down (past the end: the last line), ^U: pasted above it
		type("\u000F\r"); // ^O, Enter: written under its name
		type("\u0018"); // ^X
		untilFile(root().resolve("notes.txt"), "first\nsecond\nthird\n");
		untilIdle();
		type("touch /back\r"); // the shell has the terminal again
		untilFile(root().resolve("back"), "");
	}

	@Test
	void aProgramMakesDirectoriesAndWritesReadsAndRenamesFiles() {
		file("t.lua", """
			local fs = require("fs")
			assert(fs.mkdir("/out/deep", true))
			local f = assert(io.open("/out/deep/a.txt", "w"))
			f:write("hello ", 42, "\\n")
			f:close()
			local g = assert(io.open("/out/deep/a.txt"))
			local line = g:read("l")
			g:close()
			assert(os.rename("/out/deep/a.txt", "/out/b.txt"))
			local missing, why, errno = io.open("/out/deep/a.txt")
			local h = assert(io.open("/out/result", "w"))
			h:write(line, "|", tostring(fs.isdir("/out")), "|", #fs.list("/out/deep"), "|", errno)
			h:close()
			""");
		boot();
		type("/t.lua\r");
		untilFile(root().resolve("out/result"), "hello 42|true|0|ENOENT");
		assertThat(read(root().resolve("out/b.txt"))).isEqualTo("hello 42\n");
	}

	@Test
	void theShellPipesAndRedirects() {
		boot();
		type("echo hello  world | cat | cat > /piped; ls / > /listing; cat /nothing 2> /err; echo $? >> /piped\r");
		untilFile(root().resolve("piped"), "hello world\n1\n");
		assertThat(read(root().resolve("listing"))).contains("bin\n").contains("dev\n").contains("tmp\n");
		assertThat(read(root().resolve("err"))).contains("No such file or directory");
	}

	@Test
	void anotherDiskIsMountedUnderMnt() throws Exception {
		Path data = dataDisk();
		Files.createDirectories(data);
		Files.writeString(data.resolve("hello.txt"), "from the other disk");
		boot();
		type("cp /mnt/sata/1/hello.txt /copied; echo x > /mnt/sata/1/new; mv /copied /mnt/sata/1/moved\r");
		untilFile(data.resolve("moved"), "from the other disk");
		settle(() -> !Files.exists(root().resolve("copied"))); // moved across disks: copied, then removed
		assertThat(read(data.resolve("new"))).isEqualTo("x\n");
	}

	@Test
	void aDiskAddedWhileItRunsIsMounted() {
		boot();
		Path data = dataDisk();
		TestBus.of(machine, devices); // plugged in: the bus's interrupt
		untilIdle();
		type("echo hot > /mnt/sata/1/plugged\r");
		untilFile(data.resolve("plugged"), "hot\n");
	}

	@Test
	void ctrlCEndsABusyCommandAndTheKernelClosesWhatItHad() {
		file("busy.lua", """
			local f = assert(io.open("/kept", "w"))
			f:write("kept")
			f:flush() -- in the kernel's hands now (a buffer in the process is lost with it)
			io.open("/ready", "w"):close()
			while true do end
			""");
		boot();
		type("/busy.lua\r");
		untilFile(root().resolve("ready"), "");
		type("\u0003");
		untilFile(root().resolve("kept"), "kept"); // closed as the process ended: its writes are on the disk
		type("touch /alive\r"); // the shell lived on
		untilFile(root().resolve("alive"), "");
	}

	@Test
	void aBusyBackgroundProgramDoesntStopTheShell() {
		file("spin.lua", """
			io.open("/spinning", "w"):close()
			while true do end
			""");
		boot();
		type("/spin.lua &\r");
		untilFile(root().resolve("spinning"), "");
		type("touch /meanwhile\r"); // preempted: the shell and touch run while it spins
		untilFile(root().resolve("meanwhile"), "");
		type("\u0003touch /still\r"); // ^C doesn't reach it (a background job ignores it); halt ends it
		untilFile(root().resolve("still"), "");
		type("halt\r");
		untilHalted();
	}

	@Test
	void requireFindsModulesInLibAndTmpIsInMemory() {
		file("lib/greeting.lua", """
			return {text = "hi from a module"}
			""");
		file("t.lua", """
			local greeting = require("greeting")
			local f = assert(io.open("/tmp/note", "w"))
			f:write(greeting.text)
			f:close()
			local text = io.open("/tmp/note"):read("a")
			local out = assert(io.open("/result", "w"))
			out:write(text, "|", tostring(require("greeting") == greeting))
			out:close()
			""");
		boot();
		type("/t.lua; sleep 0.1; touch /slept\r");
		untilFile(root().resolve("result"), "hi from a module|true");
		untilFile(root().resolve("slept"), "");
		assertThat(root().resolve("tmp")).as("/tmp is the kernel's memory, not the disk").doesNotExist();
	}

	/** One tier 1 stick (1 MiB): mcos with its shell and a pipeline. */
	@Test
	void aPipelineRunsOnOneTier1Stick() {
		machine.board().setChassis(new com.payonel.modularcomputers.machine.Chassis() {
			@Override
			public int tier() {
				return 3;
			}

			@Override
			public Board.PowerInfo power() {
				return com.payonel.modularcomputers.machine.Chassis.BENCH.power();
			}

			@Override
			public java.util.List<Board.MemoryInfo> memory() {
				return java.util.List.of(new Board.MemoryInfo(0, 1, com.payonel.modularcomputers.machine.MemoryTier.TIER_1.bytes()));
			}
		});
		file("count.lua", """
			collectgarbage()
			print(math.floor(collectgarbage("count")) .. " KiB")
			""");
		boot();
		type("echo a | cat | cat > /x; /count.lua | cat > /used\r");
		untilFile(root().resolve("x"), "a\n");
		settle(() -> Files.isRegularFile(root().resolve("used")) && read(root().resolve("used")).endsWith("KiB\n"));
		System.out.println("mcos, a shell and a pipeline of two: " + read(root().resolve("used")).strip() + " in the VM");
	}

	@Test
	void theShellExpandsAndSubstitutesAndKeepsStatus() {
		file("t.sh", """
			X=hello
			echo "$X world" > /o1
			echo $(echo inner $(echo nested)) `echo back` > /o2
			Y="a   b"
			echo $Y > /o3
			echo "$Y" >> /o3
			echo ${UNSET:-dflt} ${#X} ${X:+set} "${UNSET}" > /o4
			false; echo $? > /o5
			true && echo and >> /o5
			false || echo or >> /o5
			false && echo no >> /o5
			false && echo no || echo yes >> /o5
			! false && echo negated >> /o5
			nosuchcmd 2> /dev/null; echo $? >> /o5
			/bin 2> /dev/null; echo $? >> /o5
			cat /missing 2> /dev/null; echo $? >> /o5
			cd /tmp; pwd > /o6; cd / ; pwd >> /o6
			A=1 sh -c 'echo $A' > /o7
			echo "[$A]" >> /o7
			sh -c 'echo $0 $# $1 "$2"' name one "two words" > /o8
			echo one | cat | wc 2> /dev/null; echo $? > /o9
			""");
		boot();
		type("sh /t.sh; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("o1"))).isEqualTo("hello world\n");
		assertThat(read(root().resolve("o2"))).as(() -> screen()).isEqualTo("inner nested back\n");
		assertThat(read(root().resolve("o3"))).as("unquoted: split into fields; quoted: as it is").isEqualTo("a b\na   b\n");
		assertThat(read(root().resolve("o4"))).isEqualTo("dflt 5 set \n");
		assertThat(read(root().resolve("o5"))).as(() -> screen()).isEqualTo("1\nand\nor\nyes\nnegated\n127\n126\n1\n");
		assertThat(read(root().resolve("o6"))).isEqualTo("/tmp\n/\n");
		assertThat(read(root().resolve("o7"))).as("A=1 cmd: that command's only").isEqualTo("1\n[]\n");
		assertThat(read(root().resolve("o8"))).isEqualTo("name 2 one two words\n");
		assertThat(read(root().resolve("o9"))).as("a pipeline's status is its last command's").isEqualTo("127\n");
	}

	@Test
	void redirectionsAreMadeBeforeTheCommandIsLookedFor() {
		boot();
		type("nosuch > /made 2> /err; echo $? > /st\r");
		untilFile(root().resolve("st"), "127\n");
		assertThat(root().resolve("made")).as("made (empty), though the command never ran").isRegularFile();
		assertThat(read(root().resolve("made"))).isEmpty();
		assertThat(read(root().resolve("err"))).as("not found: said on the command's own stderr").isNotEmpty();
	}

	@Test
	void theShellGlobs() throws Exception {
		for (String name : new String[] {"a.txt", "b.txt", "c.lua", ".hidden"}) {
			file("g/" + name, "");
		}
		boot();
		type("cd /g; echo * > /r1; echo *.txt ?.lua > /r2; echo [!a]* [[:alpha:]].lua > /r3;"
			+ " echo \"*.txt\" '*' \\*.txt none* .h* > /r4; echo /g/*.lua > /r5\r");
		untilFile(root().resolve("r5"), "/g/c.lua\n");
		assertThat(read(root().resolve("r1"))).as("no dot files").isEqualTo("a.txt b.txt c.lua\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("a.txt b.txt c.lua\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("b.txt c.lua c.lua\n");
		assertThat(read(root().resolve("r4"))).as("quoted: no glob; no match: the word").isEqualTo("*.txt * *.txt none* .hidden\n");
	}

	@Test
	void grepHeadAndTail() {
		file("f", "alpha\nbeta\nGamma\ndelta\nepsilon\nbbc\n");
		boot();
		type("grep -n a /f > /r1; grep -c b /f > /r2; grep -v a /f > /r3; cat /f | grep -i GAMMA > /r4;"
			+ " grep -E '^b+c$' /f > /r5; grep zzz /f; echo $? > /r6; head -n 2 /f > /r7; tail -n 2 /f > /r8;"
			+ " tail -n +5 /f > /r9; grep -l eta /f /f > /r10\r");
		untilFile(root().resolve("r10"), "/f\n/f\n");
		assertThat(read(root().resolve("r1"))).isEqualTo("1:alpha\n2:beta\n3:Gamma\n4:delta\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("2\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("epsilon\nbbc\n");
		assertThat(read(root().resolve("r4"))).isEqualTo("Gamma\n");
		assertThat(read(root().resolve("r5"))).isEqualTo("bbc\n");
		assertThat(read(root().resolve("r6"))).as("no line selected").isEqualTo("1\n");
		assertThat(read(root().resolve("r7"))).isEqualTo("alpha\nbeta\n");
		assertThat(read(root().resolve("r8"))).isEqualTo("epsilon\nbbc\n");
		assertThat(read(root().resolve("r9"))).isEqualTo("epsilon\nbbc\n");
	}

	@Test
	void aProgramsErrorIsReportedWithItsStackTraceback() {
		file("crash.lua", """
			local function inner()
			  error("boom")
			end
			inner()
			""");
		file("refuse.lua", """
			return false, "nope"
			""");
		boot();
		type("/crash.lua 2> /err; echo $? > /st; /refuse.lua 2> /err2; echo $? >> /st\r");
		untilFile(root().resolve("st"), "1\n1\n");
		assertThat(read(root().resolve("err")))
			.contains("/crash.lua:2: boom", "stack traceback:", "/crash.lua:2: in", "/crash.lua:4: in")
			.as("the loader's own frames are cut").doesNotContain("/lib/ld.lua");
		assertThat(read(root().resolve("err2"))).isEqualTo("nope\n");
	}

	@Test
	void symbolicLinksAreKeptByTheDiskAndFollowedByTheKernel() {
		file("real/a.txt", "through\n");
		file("t.lua", """
			local sys = require("sys")
			local out = assert(io.open("/r", "w"))
			out:write(tostring(sys.readlink("/rel")), "|", sys.lstat("/rel").type, "|", sys.stat("/rel").type, "|",
			  tostring(select(3, sys.stat("/loop"))), "|", sys.realpath("/rel/a.txt"), "|", tostring(select(3, sys.link("/real/a.txt", "/hard"))))
			out:close()
			""");
		boot();
		type("ln -s real /rel; ln -s /tmp /tmplink; ln -s loop /loop; echo new > /rel/b.txt\r");
		untilIdle();
		type("echo over /tmplink > /tmplink/c; cat /tmp/c > /r2; cat /rel/a.txt > /r3; ln -s gone /dangle\r");
		untilIdle();
		type("echo made > /dangle; ls -l / > /r4; /t.lua; cd /rel; pwd > /r5; cd /\r");
		untilIdle();
		type("mkdir /d; ln -s /real /d/in; rm -r /d; rm /rel; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("real/b.txt"))).as("written through a link").isEqualTo("new\n");
		assertThat(read(root().resolve("r2"))).as("a link to another filesystem").isEqualTo("over /tmplink\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("through\n");
		assertThat(read(root().resolve("gone"))).as("a link to nothing makes its target").isEqualTo("made\n");
		assertThat(read(root().resolve("r4"))).contains("dangle -> gone", "tmplink -> /tmp");
		assertThat(read(root().resolve("r"))).isEqualTo("real|link|directory|ELOOP|/real/a.txt|EPERM");
		assertThat(read(root().resolve("r5"))).as("cd through a link: the real path").isEqualTo("/real\n");
		assertThat(root().resolve("real/a.txt")).as("rm -r never follows a link").exists();
		assertThat(root().resolve("rel")).as("rm takes the link, not what it points to").doesNotExist();
		assertThat(root().resolve("real")).exists();
	}

	@Test
	void findDuTreeYesTimeUptimeMktempAndLsdev() {
		file("w/a.txt", "x".repeat(1500));
		file("w/s/b.lua", "y");
		file("w/s/.h", "");
		boot();
		type("find /w -name '*.lua' > /r1; find /w -type d > /r2; find /w -mindepth 1 -maxdepth 1 -not -name s > /r3\r");
		untilIdle();
		type("du /w > /r4; du -s -b /w > /r5; tree /w > /r6; yes hi | head -n 3 > /r7\r");
		untilIdle();
		type("time true 2> /r8; uptime > /r9; mktemp > /r10; mktemp -d -p /w dXXX > /r11; lsdev > /r12; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("r1"))).isEqualTo("/w/s/b.lua\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("/w\n/w/s\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("/w/a.txt\n");
		assertThat(read(root().resolve("r4"))).as("1 KiB blocks").isEqualTo("1\t/w/s\n3\t/w\n");
		assertThat(read(root().resolve("r5"))).isEqualTo("1501\t/w\n");
		assertThat(read(root().resolve("r6"))).isEqualTo("/w\n├── a.txt\n└── s\n    └── b.lua\n\n1 directory, 2 files\n");
		assertThat(read(root().resolve("r7"))).isEqualTo("hi\nhi\nhi\n");
		assertThat(read(root().resolve("r8"))).matches("\nreal\t0m\\d\\.\\d{3}s\n");
		assertThat(read(root().resolve("r9"))).matches(" up \\d+ min\n");
		assertThat(read(root().resolve("r10"))).matches("/tmp/tmp\\.[A-Za-z0-9]{10}\n");
		String dir = read(root().resolve("r11")).strip();
		assertThat(dir).matches("/w/d[A-Za-z0-9]{3}");
		assertThat(root().resolve(dir.substring(1))).isDirectory();
		assertThat(read(root().resolve("r12"))).startsWith("NAME").contains("storage0", "sata:0");
	}

	@Test
	void commandRunsPastAliasesAndSaysHowNamesWouldRun() {
		file("w/f", "");
		boot();
		type("alias ls='echo aliased'\r"); // (an alias counts from the next line read, as POSIX has it)
		untilIdle();
		type("ls > /r1; command ls /w > /r2; command -v ls cd sh nosuch > /r3; echo $? >> /r3\r");
		untilFile(root().resolve("r3"), "alias ls='echo aliased'\ncd\n/bin/sh\n1\n");
		assertThat(read(root().resolve("r1"))).isEqualTo("aliased\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("f\n");
	}

	@Test
	void tabCompletesCommandsAndFileNamesInTheShellAndLuaNamesInTheRepl() {
		file("w/long name.txt", "spaced\n");
		file("w/other", "");
		boot();
		type("ec\t hi > /r1; cat /w/lo\t > /r2\r");
		untilFile(root().resolve("r2"), "spaced\n");
		assertThat(read(root().resolve("r1"))).as("the command completed").isEqualTo("hi\n");
		type("lua\r");
		untilIdle();
		type("t = {3, 1, k = \"v\"}\r");
		untilIdle();
		type("io.op\t'/r3', 'w'):write(require('serialization').serialize(t)):close()\r");
		untilIdle();
		type("\4");
		untilIdle();
		assertThat(read(root().resolve("r3"))).as("io.op completed to io.open(; tables serialize").isEqualTo("{3, 1, k = \"v\"}");
	}

	@Test
	void lessPagesAndQuitsAndIntoAFileIsCat() {
		file("f", "one\ntwo\n");
		boot();
		type("less /f > /r1; cat /f | less > /r2\r");
		untilFile(root().resolve("r2"), "one\ntwo\n");
		assertThat(read(root().resolve("r1"))).isEqualTo("one\ntwo\n");
		type("less /f\r");
		untilIdle();
		type("G/two\rq");
		untilIdle();
		type("touch /done\r");
		untilFile(root().resolve("done"), "");
	}

	@Test
	void manFormatsAManPage() {
		file("usr/share/man/man1/t.1", """
			.TH T 1
			.SH NAME
			t \\- a test
			.SH OPTIONS
			.TP
			.B \\-x
			does x
			.PP
			Some words that are filled into one line.
			.nf
			kept   as is
			.fi
			""");
		boot();
		type("man t > /r; man nosuch 2> /dev/null; echo $? > /st\r");
		untilFile(root().resolve("st"), "16\n");
		assertThat(read(root().resolve("r"))).contains("NAME\n       t - a test\n", "OPTIONS\n       -x     does x\n",
			"       Some words that are filled into one line.\n", "       kept   as is\n", "T(1)");
	}

	@Test
	void anAssignmentsStatusIsItsSubstitutions() {
		boot();
		type("false; x=$(false); echo $? > /r; true; y=1; echo $? >> /r\r");
		untilFile(root().resolve("r"), "1\n0\n");
	}

	@Test
	void backgroundListsAliasesBuiltinAssignmentsReadAndDollarStarAsPosixHasThem() {
		file("in1", "a:b:c\n");
		file("in2", "  one   two  three  \n");
		file("in3", "a\\ b c\n");
		boot();
		// a whole && || list in the background; y runs (x failed)
		type("true && echo a > /b1 &\r");
		untilIdle();
		type("false || echo y > /b2 &\r");
		untilFile(root().resolve("b2"), "y\n");
		untilFile(root().resolve("b1"), "a\n");
		// an alias's operators; a quoted name isn't one; a blank at its end makes the next word a name
		type("alias c='echo hi | cat > /a1'; alias e='echo '; alias w=world\r");
		untilIdle();
		type("c; 'c' 2> /dev/null; echo $? > /a2; e w > /a3\r");
		untilFile(root().resolve("a3"), "world\n");
		assertThat(read(root().resolve("a1"))).isEqualTo("hi\n");
		assertThat(read(root().resolve("a2"))).isEqualTo("127\n");
		// assignments before a builtin: only while it runs, but for a special builtin
		type("X=old; X=new cd /; echo $X > /c1; X=b export Z=1; echo $X > /c2\r");
		untilFile(root().resolve("c2"), "b\n");
		assertThat(read(root().resolve("c1"))).isEqualTo("old\n");
		// read splits on IFS (IFS=: just for it), trims blanks, the last takes the rest; \ quotes
		type("IFS=: read x y < /in1; echo \"$x|$y\" > /d1; read p q < /in2; echo \"[$p][$q]\" > /d2\r");
		untilFile(root().resolve("d2"), "[one][two  three]\n");
		assertThat(read(root().resolve("d1"))).isEqualTo("a|b:c\n");
		type("read u v < /in3; echo \"[$u][$v]\" > /d3; read -r u v < /in3; echo \"[$u][$v]\" > /d4\r");
		untilFile(root().resolve("d4"), "[a\\][b c]\n");
		assertThat(read(root().resolve("d3"))).isEqualTo("[a b][c]\n");
		// "$*" joins with IFS's first character
		type("sh -c 'IFS=:; echo \"$*\" > /f1' x a b c\r");
		untilFile(root().resolve("f1"), "a:b:c\n");
	}

	@Test
	void anUnsetParameterWithColonQuestionEndsAScriptButNotAnInteractiveShell() {
		boot();
		type("sh -c 'echo ${NOPE:?gone}; touch /e1' 2> /dev/null; echo $? > /e2\r");
		untilFile(root().resolve("e2"), "1\n");
		assertThat(root().resolve("e1")).doesNotExist();
		type("echo ${NOPE:?x} 2> /dev/null; touch /e3\r");
		untilFile(root().resolve("e3"), "");
	}

	@Test
	void grepCaseClassesWithIAndTailFollows() {
		file("log", "first\n");
		boot();
		type("echo Hello | grep -i '[[:upper:]]ello' > /g1; echo a1 | grep '[[:digit:]]' >> /g1; tail -x 2> /dev/null; echo $? > /h1\r");
		untilFile(root().resolve("h1"), "1\n");
		assertThat(read(root().resolve("g1"))).isEqualTo("Hello\na1\n");
		// (into /tmp: a file on a disk shows its writes at close, and tail -f keeps it open)
		type("tail -f /log > /tmp/out &\r");
		untilIdle();
		type("echo more >> /log\r");
		untilIdle(); // (no timer: tail has shown it by the time mcos is idle again)
		type("cp /tmp/out /h2\r");
		untilFile(root().resolve("h2"), "first\nmore\n");
		type("kill $!\r");
		untilIdle();
	}

	@Test
	void fixesFoundByTheFootprintAudit() {
		file("t.lua", """
			local sys, fs = require("sys"), require("fs")
			local out = {}
			local function say(...) out[#out + 1] = table.concat({...}, " ") end
			-- a file under a directory that isn't there: ENOENT, and nothing made (tmpfs)
			local _, _, e1 = sys.open("/tmp/nodir/file", "w")
			say("nodir", tostring(e1), tostring(sys.stat("/tmp/nodir") ~= nil))
			-- the open's mode is enforced for every file, devices too
			local r = sys.open("/dev/null", "r")
			local w = sys.open("/dev/null", "w")
			say("modes", tostring(select(3, sys.write(r, "x"))), tostring(select(3, sys.read(w, 1))))
			-- a glob's \\d is the letter d
			say("glob", tostring(string.find("d", fs.pattern([[\\d*]])) ~= nil), tostring(string.find("1", fs.pattern([[\\d*]])) ~= nil))
			-- os.remove takes a link, never what it points to
			sys.symlink("/tmp", "/lk")
			say("remove", tostring(os.remove("/lk")), tostring(sys.stat("/tmp") ~= nil))
			-- io.lines with several formats gives them all
			io.open("/two", "w"):write("a\\nb\\n"):close()
			for x, y in io.lines("/two", "l", "l") do say("lines", x, y) end
			-- an orphan that had ended is reaped (no zombie left)
			local zombies = 0
			for _, p in ipairs(sys.processes()) do
			  if p.state == "zombie" then zombies = zombies + 1 end
			end
			say("zombies", zombies)
			io.open("/r", "w"):write(table.concat(out, "\\n")):close()
			""");
		file("orphan.lua", """
			require("sys").spawn("/bin/true", {})
			require("sys").sleep(0.3) -- (the child ends: a zombie, never waited for)
			""");
		file("fd1.lua", """
			io.stderr:write(tostring(select(3, require("sys").write(1, "x"))))
			""");
		file("crash.lua", "local function f() error('boom') end f()\n");
		boot();
		type("/orphan.lua; /t.lua 2> /terr; /fd1.lua >&- 2> /r2; pwd >&- 2> /r3; echo $? >> /r3\r");
		settle(() -> Files.isRegularFile(root().resolve("r3")) && read(root().resolve("r3")).endsWith("1\n"));
		type("lua /crash.lua 2> /r4; echo \"a\\\r");
		untilIdle();
		type("b\" > /r5\r");
		untilFile(root().resolve("r5"), "ab\n");
		assertThat(root().resolve("r")).as(() -> read(root().resolve("terr"))).exists();
		assertThat(read(root().resolve("r"))).isEqualTo(String.join("\n", "nodir ENOENT false", "modes EBADF EBADF",
			"glob true false", "remove true true", "lines a b", "zombies 0"));
		assertThat(read(root().resolve("r2"))).as("n>&- closes it in the child").isEqualTo("EBADF");
		assertThat(read(root().resolve("r3"))).as("a builtin's closed stdout").contains("write error: Bad file descriptor").endsWith("1\n");
		assertThat(read(root().resolve("r4"))).as("lua's own frames are cut").contains("/crash.lua:1").doesNotContain("/bin/lua:");
	}

	@Test
	void fieldSplittingIsPosixs() {
		file("args.lua", """
			local out = assert(io.open("/r", "a"))
			out:write(select("#", ...), ":", table.concat({...}, "|"), "\\n")
			out:close()
			""");
		boot();
		type("a='a::b'; b=':a'; c='a:'; d='  x  y  '; IFS=:; /args.lua $a; /args.lua $b; /args.lua $c\r");
		untilIdle();
		type("unset IFS; /args.lua $d; /args.lua x${d}y; touch /done\r");
		untilFile(root().resolve("done"), "");
		// two IFS characters that aren't white space make an empty field; one at the start does too, one
		// at the end doesn't; white space runs separate, and at an expansion's ends end the word's field
		assertThat(read(root().resolve("r"))).isEqualTo("3:a||b\n2:|a\n1:a\n2:x|y\n4:x|x|y|y\n");
	}

	@Test
	void everyCommandHasAManPageThatRenders() {
		file("t.lua", """
			local fs = require("fs")
			local out = assert(io.open("/r", "w"))
			for _, entry in ipairs(assert(fs.list("/bin"))) do
			  local pipe = assert(io.popen("man " .. entry.name))
			  local text = pipe:read("a")
			  local ok = pipe:close()
			  if not ok or not string.find(text, "NAME\\n       " .. entry.name, 1, true) then
			    out:write(entry.name, " ")
			  end
			end
			out:write("done")
			out:close()
			""");
		boot();
		type("/t.lua\r");
		untilFile(root().resolve("r"), "done");
	}

	@Test
	void binLuaIsLuasStandaloneInterpreter() {
		file("s.lua", """
			local out = assert(io.open("/r1", "w"))
			out:write(arg[0], " ", arg[-1], " ", table.concat({...}, ","), " ", tostring(greeting))
			out:close()
			return 7
			""");
		file("lib/greet.lua", """
			return "hi"
			""");
		file("in.lua", """
			io.open("/r3", "w"):write("from stdin"):close()
			""");
		boot();
		type("lua -l greeting=greet /s.lua a b; echo $? > /st;"
			+ " lua -e 'io.open(\"/r2\", \"w\"):write(1 + 1):close()'; lua - < /in.lua;"
			+ " lua -e 'error(\"x\")' 2> /dev/null; echo $? >> /st\r");
		untilFile(root().resolve("st"), "0\n1\n");
		assertThat(read(root().resolve("r1"))).as("arg, ... and -l; what it returns is ignored")
			.isEqualTo("/s.lua greeting=greet a,b hi");
		assertThat(read(root().resolve("r2"))).isEqualTo("2");
		assertThat(read(root().resolve("r3"))).isEqualTo("from stdin");
	}

	@Test
	void aLineThatGoesOnIsReadOnAndThePromptShowsTheDirectory() {
		boot();
		type("echo 'a\rb' |\r cat > /q\r");
		untilFile(root().resolve("q"), "a\nb\n");
		type("export PS1='[\\w]# '; cd /tmp\r");
		untilIdle();
		assertThat(screen()).contains("[/tmp]#");
	}

	@Test
	void ioPopenReadsACommandsOutput() {
		file("t.lua", """
			local p = assert(io.popen("echo piped through"))
			local text = p:read("a")
			local ok, how, status = p:close()
			local out = assert(io.open("/result", "w"))
			out:write(text, "|", tostring(ok), " ", how, " ", status)
			out:close()
			""");
		boot();
		type("/t.lua\r");
		untilFile(root().resolve("result"), "piped through\n|true exit 0");
	}
	/** A network card beside the test computer's (it and the peer: one world, one block), with its id. */
	private com.payonel.modularcomputers.device.NetworkCard networkCard(Object world, java.util.UUID id) {
		var card = new com.payonel.modularcomputers.device.NetworkCard(com.payonel.modularcomputers.device.CardKind.TIER2_WLAN);
		card.idFrom(() -> id);
		card.place(world, net.minecraft.core.BlockPos.ZERO, 0);
		return card;
	}

	/** A UDP datagram, as a card's frame carries it: its protocol (IP's number for UDP, 17), source and destination port, then the data. */
	private static String datagram(int from, int to, String data) {
		return "" + (char) 17 + (char) (from >> 8) + (char) (from & 0xff) + (char) (to >> 8) + (char) (to & 0xff) + data;
	}

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	@Test
	void aSocketTakesDatagramsOnItsPortAndAnswersTheirSenders() {
		Object world = new Object();
		java.util.UUID mine = java.util.UUID.randomUUID();
		var card = networkCard(world, mine);
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		var peer = networkCard(world, java.util.UUID.randomUUID());
		peer.power().supply(this); // (a card on a running computer's bus elsewhere)
		peer.accepting(true);
		file("u.lua", """
			local sys = require("sys")
			local function put(name, text)
			  local out = io.open(name, "w")
			  out:write(text)
			  out:close()
			end
			local s = assert(sys.socket())
			assert(sys.bind(s, 7))
			local _, _, inuse = sys.bind(assert(sys.socket()), 7)
			local quiet = sys.poll({[s] = {IN = true}}, 0)
			put("/ready", "")
			local ready = sys.poll({[s] = {IN = true}})
			local data, from, port = sys.recvfrom(s)
			assert(sys.sendto(s, "pong", from, port))
			local _, _, unreachable = sys.sendto(s, "x", "nobody", 1)
			local _, _, toolong = sys.sendto(s, string.rep("x", 9000), from, port)
			local back = assert(sys.socket())
			assert(sys.bind(back, 9))
			assert(sys.sendto(s, "all", "broadcast", 9))
			local mine = sys.ioctl(back, "SIOCGIFCONF")[1]
			assert(sys.sendto(s, "self", mine.address, 9))
			local all = sys.recvfrom(back)
			local looped, _, loopport = sys.recvfrom(back)
			looped = all .. "+" .. looped
			put("/got", table.concat({tostring(next(quiet) == nil), tostring(ready[s].IN), data, from, port, inuse,
			  unreachable, toolong, looped, loopport, mine.name}, " "))
			""");
		boot();
		type("lua /u.lua 2> /err; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("ready")));
		server.add(() -> peer.send(args(mine.toString(), datagram(5000, 7, "ping"))));
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(read(root().resolve("got"))).as("nothing waited, then the ping, from its card and port; the errors; its own broadcast and datagram looped back")
			.isEqualTo("true true ping " + peer.address() + " 5000 EADDRINUSE EHOSTUNREACH EMSGSIZE all+self 7 wlan0");
		@SuppressWarnings("unchecked") var pong = (Map<String, Object>) peer.receive(args()).first();
		assertThat(pong).containsEntry("from", mine.toString()).containsEntry("data", datagram(7, 5000, "pong"));
		@SuppressWarnings("unchecked") var all = (Map<String, Object>) peer.receive(args()).first();
		assertThat(all).containsEntry("data", datagram(7, 9, "all")).containsEntry("broadcast", true);
	}

	@Test
	void ncListensAndSendsAndASocketsIoctlSetsTheWakeMessage() {
		Object world = new Object();
		java.util.UUID mine = java.util.UUID.randomUUID();
		var card = networkCard(world, mine);
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		var peer = networkCard(world, java.util.UUID.randomUUID());
		peer.power().supply(this);
		peer.accepting(true);
		file("w.lua", """
			local sys = require("sys")
			assert(sys.ioctl(assert(sys.socket()), "ETHTOOL_SWOL", {name = "wlan0", wake = "secret"}))
			""");
		boot();
		type("lua /w.lua; touch /set\r");
		untilFile(root().resolve("set"), "");
		assertThat(card.getWake(args()).first()).isEqualTo("secret");
		type("nc -ul 7 > /tmp/heard &\r");
		untilIdle();
		server.add(() -> peer.send(args(mine.toString(), datagram(5000, 7, "hello\n"))));
		untilIdle();
		type("cp /tmp/heard /heard; echo hi | nc -u " + peer.address() + " 6 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("heard"))).isEqualTo("hello\n");
		assertThat(read(root().resolve("err"))).isEmpty();
		var hi = (Map<?, ?>) peer.receive(args()).first();
		assertThat(hi).isNotNull();
		assertThat((String) hi.get("data")).endsWith("hi\n");
	}
	@Test
	void anEnabledServiceStartsAtBootOutOfTheConsolesReachAndRcStopsIt() {
		file("etc/rc.d/testd", """
			-- a test's service: settings, and an action of its own
			description = "waits"
			command = "/testd.lua"
			command_args = {"one", "two"}
			function hello()
			  local out = io.open("/hello", "w")
			  out:write(name)
			  out:close()
			  return 4
			end
			""");
		file("testd.lua", """
			local sys = require("sys")
			local function put(name, text)
			  local out = io.open(name, "w")
			  out:write(text)
			  out:close()
			end
			sys.signal("SIGTERM", function()
			  put("/stopped", "")
			  sys.exit(0)
			end)
			put("/started", table.concat(arg, " ") .. " " .. tostring(sys.isatty(0)))
			while true do
			  sys.sleep(1000)
			end
			""");
		boot(() -> file("etc/rc.conf", "testd_enable=YES\n"));
		untilFile(root().resolve("started"), "one two false");
		type("\u0003rc testd status > /dev/null; echo $? > /s1; rc testd stop; rc testd status > /dev/null; echo $? > /s2\r");
		untilFile(root().resolve("s2"), "3\n");
		assertThat(read(root().resolve("s1"))).as("running, though ^C was typed at the console").isEqualTo("0\n");
		assertThat(root().resolve("stopped")).as("stopped by SIGTERM").exists();
		type("rc testd hello; echo $? > /h; rc testd disable; rc other enable 2> /dev/null; echo $? > /none\r");
		untilFile(root().resolve("none"), "1\n"); // no such service
		assertThat(read(root().resolve("hello"))).as("its own action, with its name").isEqualTo("testd");
		assertThat(read(root().resolve("h"))).as("its status").isEqualTo("4\n");
		assertThat(read(root().resolve("etc/rc.conf"))).isEqualTo("testd_enable=NO\n");
	}

	@Test
	void aShellOnAPtyTakesWhatItsMasterTypesAndHangsUpWhenItCloses() {
		file("winsize.lua", """
			local sys = require("sys")
			local out = io.open("/size", "w")
			out:write(table.concat({sys.ioctl(0, "TIOCGWINSZ")}, " "))
			out:close()
			""");
		file("pty.lua", """
			local sys = require("sys")
			local function put(name, text)
			  local out = io.open(name, "w")
			  out:write(text)
			  out:close()
			end
			local function exists(name)
			  return sys.stat(name) ~= nil
			end
			local master, terminal = assert(sys.openpty())
			assert(sys.ioctl(master, "TIOCSWINSZ", {30, 10}))
			local pid = assert(sys.spawn("/bin/sh", {}, {setsid = true, fds = {[0] = terminal, [1] = terminal, [2] = terminal}}))
			sys.close(terminal)
			local shown = ""
			-- reads what the terminal shows until 'name' exists (or a while passes)
			local function until_(name)
			  for _ = 1, 400 do
			    if exists(name) then
			      return true
			    end
			    local ready = sys.poll({[master] = {IN = true}}, 0.05)
			    if ready[master] then
			      shown = shown .. (sys.read(master) or "")
			    end
			  end
			  return false
			end
			sys.write(master, "echo typed > /p1\\r")
			local typed = until_("/p1")
			sys.write(master, "lua /winsize.lua\\r")
			until_("/size")
			sys.write(master, "sleep 100\\r")
			local function sleeping()
			  for _, q in ipairs(sys.processes()) do
			    if q.path == "/bin/sleep" then
			      return true
			    end
			  end
			  return false
			end
			for _ = 1, 400 do
			  if sleeping() then
			    break
			  end
			  sys.sleep(0.05)
			end
			sys.write(master, "\\3") -- ^C: the sleep ends
			sys.write(master, "echo after > /p2\\r") -- at once, not in 100 seconds
			local interrupted = until_("/p2")
			sys.close(master) -- the line hangs up: the shell gets SIGHUP
			local got, how, value = sys.waitpid(pid)
			put("/result", table.concat({tostring(typed), tostring(interrupted), tostring(how), tostring(value),
			  tostring(shown:find("typed", 1, true) ~= nil)}, " "))
			""");
		boot();
		type("lua /pty.lua 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(read(root().resolve("p1"))).isEqualTo("typed\n");
		assertThat(read(root().resolve("size"))).as("the size its master set").isEqualTo("30 10");
		assertThat(read(root().resolve("result"))).as("typed, interrupted by ^C, ended by the hangup; its echo shown to the master")
			.isEqualTo("true true killed SIGHUP true");
	}

	/**
	 * A ^D waiting at the start of a line (an end of file, in canonical mode) when a program turns
	 * the tty raw is the ^D typed, and an end of file again back in canonical mode (found as a
	 * kernel error when the shell's line editor went raw after a ^D was typed under a command).
	 */
	@Test
	void anEndOfFileWaitingWhenTheTtyGoesRawIsTheCtrlDTyped() {
		file("eof.lua", """
			local sys = require("sys")
			local cooked = sys.ioctl(0, "TCGETS")
			local raw = {}
			for k, v in pairs(cooked) do raw[k] = v end
			raw.icanon, raw.echo = false, false
			assert(sys.ioctl(0, "TIOCSTI", "\\4")) -- ^D at the start of a line: an end of file
			assert(sys.ioctl(0, "TCSETS", raw))
			local rawread = sys.read(0)
			assert(sys.ioctl(0, "TIOCSTI", "\\4"))
			assert(sys.ioctl(0, "TCSETS", cooked))
			local cookedread = sys.read(0)
			local out = io.open("/eof", "w")
			out:write(string.format("%q %q", rawread, cookedread))
			out:close()
			""");
		boot();
		type("lua /eof.lua 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(read(root().resolve("eof"))).as("raw: the ^D byte; cooked again: an end of file").isEqualTo("\"\\4\" \"\"");
	}
	@Test
	void resizeMakesTheTerminalAnotherSizeAndTheProgramInFrontHearsOfIt() {
		file("w.lua", """
			local sys = require("sys")
			local heard = 0
			sys.signal("SIGWINCH", function() heard = heard + 1 end)
			local pid = sys.spawn("/bin/resize", {"-s", "10", "30"}, {fds = {[1] = sys.open("/dev/null", "w")}})
			sys.waitpid(pid)
			sys.sleep(0.1) -- (a signal is handled on the way back from a syscall)
			local out = io.open("/w", "w")
			out:write(heard, " ", table.concat({sys.ioctl(0, "TIOCGWINSZ")}, " "))
			out:close()
			""");
		boot();
		type("lua /w.lua 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(screen.buffer().resolution()).isEqualTo(new Resolution(30, 10));
		assertThat(read(root().resolve("w"))).as("SIGWINCH, and the size the kernel gives now").isEqualTo("1 30 10");
		type("resize -s 0 0 > /dev/null; touch /done2\r");
		untilFile(root().resolve("done2"), "");
		assertThat(screen.buffer().resolution()).as("0: as large as it can be").isEqualTo(Resolution.DEFAULT);
	}
	/** man's bold and underline reach the terminal as styles, not as ^[ (less -R, which man puts in LESS, as man-db does). */
	@Test
	void aManPageShowsItsStylesNotTheirEscapeSequences() {
		file("usr/share/man/man1/t.1", """
			.TH T 1
			.SH NAME
			t \\- a test
			.SH DESCRIPTION
			.B emphatic
			words
			""");
		boot();
		type("man t\r");
		settle(() -> screen().contains("emphatic"));
		String shown = screen();
		type("q");
		untilIdle();
		assertThat(shown).as("no escape sequence shown as text").doesNotContain("^[");
		type("less -Z /dev/null 2> /err; echo $? > /st\r");
		untilFile(root().resolve("st"), "1\n");
	}

	@Test
	void aStreamCarriesEveryByteInOrderAndEndsAtItsShutdown() {
		java.util.UUID mine = java.util.UUID.randomUUID();
		devices.put(Motherboard.exp(0), networkCard(new Object(), mine)); // (a card in a slot, as in a case)
		file("client.lua", """
			local sys = require("sys")
			local s = assert(sys.socket("stream"))
			assert(sys.connect(s, arg[1], 7))
			local data = string.rep("0123456789", 2000)
			assert(sys.write(s, data) == #data)
			assert(sys.shutdown(s))
			local reply = ""
			while true do
			  local d = sys.read(s)
			  if not d or d == "" then break end
			  reply = reply .. d
			end
			local out = io.open("/client", "w")
			out:write(reply)
			out:close()
			""");
		file("server.lua", """
			local sys = require("sys")
			local me = sys.ioctl(assert(sys.socket()), "SIOCGIFCONF")[1].address
			local l = assert(sys.socket("stream"))
			assert(sys.bind(l, 7))
			assert(sys.listen(l))
			local _, _, refused = sys.connect(assert(sys.socket("stream")), me, 9)
			local pid = assert(sys.spawn("/client.lua", {me}))
			local c, from = assert(sys.accept(l))
			local got, ok = "", true
			while true do
			  local d = sys.read(c, 4096)
			  if not d or d == "" then break end
			  got = got .. d
			end
			ok = got == string.rep("0123456789", 2000)
			sys.write(c, "got " .. #got)
			sys.close(c)
			sys.waitpid(pid)
			local out = io.open("/server", "w")
			out:write(table.concat({tostring(refused), #got, tostring(ok), tostring(from == me)}, " "))
			out:close()
			""");
		boot();
		type("lua /server.lua 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(read(root().resolve("server"))).as("refused where nothing listens; every byte, in order, from its own address").isEqualTo("ECONNREFUSED 20000 true true");
		assertThat(read(root().resolve("client"))).isEqualTo("got 20000");
	}

	@Test
	void theHostsNameIsItsCardsAddressUntilSetAndRshFindsItByName() {
		java.util.UUID mine = java.util.UUID.randomUUID();
		devices.put(Motherboard.exp(0), networkCard(new Object(), mine));
		boot();
		type("hostname > /h1; hostname lab; hostname > /h2; hostname 'bad name' 2> /dev/null; echo $? > /st; "
			+ "rc rshd start; sleep 0.5; rsh lab 'hostname' > /h3; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("h1"))).as("unset: the first card's address").isEqualTo(mine + "\n");
		assertThat(read(root().resolve("h2"))).isEqualTo("lab\n");
		assertThat(read(root().resolve("etc/hostname"))).as("kept for the next boot").isEqualTo("lab\n");
		assertThat(read(root().resolve("st"))).as("not a name").isEqualTo("1\n");
		assertThat(read(root().resolve("h3"))).as("rsh finds it by name").isEqualTo("lab\n");
	}

	@Test
	void theBootSetsTheHostsNameFromEtcHostname() {
		boot(() -> file("etc/hostname", "kept\n"));
		type("hostname > /h; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("h"))).isEqualTo("kept\n");
	}

	@Test
	void rshRunsCommandsAndLoginsAndRcpCopiesThroughRshd() {
		java.util.UUID mine = java.util.UUID.randomUUID();
		devices.put(Motherboard.exp(0), networkCard(new Object(), mine)); // (a card in a slot, as in a case)
		String host = mine.toString().substring(0, 6); // (a prefix: found by asking every rshd in reach)
		file("f", "a file\n");
		boot();
		type("rc rshd start; sleep 0.5; rsh " + host + " 'echo out; echo err >&2; exit 3' > /o 2> /e; echo $? > /st\r");
		untilFile(root().resolve("st"), "3\n");
		assertThat(read(root().resolve("o"))).as("its output").isEqualTo("out\n");
		assertThat(read(root().resolve("e"))).as("its errors, apart").isEqualTo("err\n");
		type("rcp /f " + host + ":/copied; rcp " + host + ":/copied /back; echo $? > /st2\r");
		untilFile(root().resolve("st2"), "0\n");
		assertThat(read(root().resolve("copied"))).isEqualTo("a file\n");
		assertThat(read(root().resolve("back"))).isEqualTo("a file\n");
		// (each rsh, then a local touch: what's typed next waits for rsh to be gone, or rsh would take it)
		type("rsh " + host + "; touch /back1\r");
		untilIdle();
		type("echo remote > /p; exit\r");
		untilFile(root().resolve("p"), "remote\n");
		untilFile(root().resolve("back1"), "");
		type("rsh " + host + "; touch /back2\r");
		untilIdle();
		type("\r~.");
		untilFile(root().resolve("back2"), "");
	}
	@Test
	void rcpCopiesSeveralFilesAndWithRDirectoriesBothWays() {
		java.util.UUID mine = java.util.UUID.randomUUID();
		devices.put(Motherboard.exp(0), networkCard(new Object(), mine)); // (a card in a slot, as in a case)
		String host = mine.toString().substring(0, 6);
		file("a/one.txt", "one\n");
		file("a/sub/two.txt", "two\n");
		file("f1", "f1\n");
		file("f2", "f2\n");
		boot();
		type("rc rshd start; sleep 0.5; mkdir /many; rcp /f1 /f2 " + host + ":/many; rcp /a " + host + ":/no 2> /refused; echo $? > /st1\r");
		untilFile(root().resolve("st1"), "1\n");
		assertThat(read(root().resolve("many/f1")) + read(root().resolve("many/f2"))).isEqualTo("f1\nf2\n");
		assertThat(root().resolve("no")).as("a directory, without -r: refused").doesNotExist();
		type("rcp -r /a " + host + ":/copy; mkdir /back; rcp -r " + host + ":/copy /back; echo $? > /st2\r");
		untilFile(root().resolve("st2"), "0\n");
		assertThat(read(root().resolve("copy/one.txt")) + read(root().resolve("copy/sub/two.txt"))).as("there").isEqualTo("one\ntwo\n");
		assertThat(read(root().resolve("back/copy/one.txt")) + read(root().resolve("back/copy/sub/two.txt"))).as("and back, into the directory")
			.isEqualTo("one\ntwo\n");
	}

	@Test
	void twoComputersTalkThroughTheirCardsAndTcpLosesNothingWhenFramesAre() {
		Object world = new Object();
		java.util.UUID mine = java.util.UUID.randomUUID();
		java.util.UUID theirs = java.util.UUID.randomUUID();
		var card = networkCard(world, mine);
		devices.put(Motherboard.exp(0), card); // (a card in a slot, as in a case)
		file("big.lua", """
			local out = io.open("/big", "w")
			for i = 1, 3000 do
			  out:write(string.format("line %05d of the file that goes there and back\\n", i))
			end
			out:close()
			""");
		boot();
		Other other = new Other("other", world, theirs);
		settle(() -> other.machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		other.type("rc rshd start; touch /started\r");
		// Their card goes out of reach half a second in every two: frames to it are lost then, and
		// TCP sends them again.
		int[] tick = {0};
		ticks.add(() -> {
			tick[0]++;
			other.card.place(world, net.minecraft.core.BlockPos.ZERO, tick[0] % 40 < 10 ? -100 : 0);
		});
		settle(() -> Files.exists(other.root.resolve("started"))); // (its rshd is up)
		type("lua /big.lua; rcp /big " + theirs + ":/big; rsh " + theirs + " cat /big > /back; echo $? > /st\r");
		untilFile(root().resolve("st"), "0\n");
		String sent = read(root().resolve("big"));
		assertThat(sent).hasSize(3000 * 48);
		assertThat(read(other.root.resolve("big"))).as("there, whole").isEqualTo(sent);
		assertThat(read(root().resolve("back"))).as("and back, whole").isEqualTo(sent);
	}
	@Test
	void rshFindsAnotherComputerByAPrefixEveryTime() {
		Object world = new Object();
		java.util.UUID mine = java.util.UUID.randomUUID();
		java.util.UUID theirs = java.util.UUID.randomUUID();
		var card = networkCard(world, mine);
		devices.put(Motherboard.exp(0), card);
		file("tries.lua", """
			local sys = require("sys")
			-- First, until it answers (its rshd up and warm: asking sooner is the asker's mistake).
			repeat
			  local pid = assert(sys.spawn("/bin/rsh", {arg[1], "true"}, {fds = {[2] = assert(sys.open("/dev/null", "w"))}}))
			  local _, how, value = sys.waitpid(pid)
			until how == "exited" and value == 0
			local out = io.open("/tries", "w")
			for i = 1, 20 do
			  local pid = assert(sys.spawn("/bin/rsh", {arg[1], "true"}, {}))
			  local _, how, value = sys.waitpid(pid)
			  out:write(tostring(how), " ", tostring(value), "\\n")
			end
			out:close()
			""");
		boot();
		Other other = new Other("other", world, theirs);
		settle(() -> other.machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		other.type("rc rshd start; touch /started\r");
		long[] tick = {0};
		ticks.add(() -> { // (each card says where it is every tick, as its block does)
			tick[0]++;
			card.place(world, net.minecraft.core.BlockPos.ZERO, tick[0]);
			other.card.place(world, new net.minecraft.core.BlockPos(3, 0, 0), tick[0]);
		});
		settle(() -> Files.exists(other.root.resolve("started"))); // (its rshd is up: not a race with a sleep)
		type("sleep 0.2; lua /tries.lua " + theirs.toString().substring(0, 4) + " 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("tries"))).as(() -> "errors: " + read(root().resolve("err"))).isEqualTo("exited 0\n".repeat(20));
	}

	@Test
	void sendtoSaysWhenTheOtherCardIsBusy() {
		Object world = new Object();
		java.util.UUID mine = java.util.UUID.randomUUID();
		devices.put(Motherboard.exp(0), networkCard(world, mine)); // (a card in a slot, as in a case)
		var peer = networkCard(world, java.util.UUID.randomUUID());
		peer.power().supply(this); // on a computer whose program takes no data: it takes nothing
		file("b.lua", """
			local sys = require("sys")
			local s = assert(sys.socket())
			local _, _, busy = sys.sendto(s, "x", arg[1], 7)
			local out = io.open("/b", "w")
			out:write(tostring(busy))
			out:close()
			""");
		boot();
		type("lua /b.lua " + peer.address() + " 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(read(root().resolve("b"))).isEqualTo("ENOBUFS");
	}

	/** A program of the manual (docs/examples), as a player reads it. */
	private static String example(String name) {
		try {
			Path dir = Path.of("").toAbsolutePath();
			while (!Files.isDirectory(dir.resolve("docs/examples"))) {
				dir = dir.getParent();
			}
			return Files.readString(dir.resolve("docs/examples/" + name + ".lua"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void theManualsBlinkProgramTurnsAFaceOnAndOffForever() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card);
		boot();
		file("blink.lua", example("redstone-blink"));
		type("lua /blink.lua &\r");
		settle(() -> card.output(0) == 15);
		settle(() -> card.output(0) == 0);
		settle(() -> card.output(0) == 15); // (and again: it never ends)
	}

	@Test
	void theManualsMirrorProgramCopiesTheWestInputToTheEastOutputAsItChanges() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card);
		boot();
		file("mirror.lua", example("redstone-mirror"));
		type("lua /mirror.lua &\r");
		untilIdle();
		server.add(() -> card.sense(3, 9)); // west: 9 comes in
		settle(() -> card.output(2) == 9); // east: 9 goes out
		server.add(() -> card.sense(3, 4));
		settle(() -> card.output(2) == 4);
	}

	@Test
	void theManualsMotionAlarmProgramSoundsWhenSomethingMoves() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), motion);
		devices.put(Motherboard.exp(1), card);
		boot();
		// (a long pulse, so the alarm is still on when the test looks)
		file("alarm.lua", example("motion-alarm").replace("local PULSE = 1 ", "local PULSE = 600 "));
		type("lua /alarm.lua > /tmp/out &\r");
		untilIdle();
		assertThat(card.output(0)).as("quiet until something moves").isZero();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		settle(() -> card.output(0) == 15);
	}
}
