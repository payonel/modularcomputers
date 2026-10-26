package com.payonel.modularcomputers.runtime;

import static com.payonel.modularcomputers.device.TerminalKeyboard.PRESS;
import static com.payonel.modularcomputers.device.TerminalKeyboard.RELEASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.NetworkCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.machine.Motherboard;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * openos: OpenComputers programs run on mcos, in a machine of their own (OpenComputers' component,
 * computer and unicode; OpenOS's libraries, ported) over mcos's terminal and devices. Each test's
 * program is an OpenOS program that writes what it saw to a file; the test checks the file (and
 * where the gpu drew, the terminal's cells: what the program drew, not what mcos says).
 */
class OpenOsTest extends McosHarness {
	/** Starts a program under openos (its errors in /err, its status in /status once it ends). */
	private void start(String program) {
		file("t.lua", program);
		boot();
		type("openos /t.lua 2> /err; echo $? > /status\r");
	}

	/** Runs a program to its end: its status. */
	private String run(String program) {
		start(program);
		return ended();
	}

	private String ended() {
		settle(() -> Files.exists(root().resolve("status")) && read(root().resolve("status")).endsWith("\n"));
		return read(root().resolve("status"));
	}

	private String out() {
		return read(root().resolve("out"));
	}

	private String errors() {
		return Files.exists(root().resolve("err")) ? read(root().resolve("err")) : "";
	}

	/** A key pressed and let go, as a screen in kitty's mode with its text sends it. */
	private void key(int code, int modifiers, String text) {
		screen.key(code, modifiers, PRESS, text);
		screen.key(code, modifiers, RELEASE, "");
	}

	private void typeKeys(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\r') {
				key(13, 0, "");
			} else {
				key(Character.toLowerCase(c), Character.isUpperCase(c) ? 1 : 0, String.valueOf(c));
			}
		}
	}

	@Test
	void aProgramHasOpenComputersGlobalsAndOpenOsLibraries() {
		String status = run("""
			local component = require("component")
			local term = require("term")
			local serialization = require("serialization")
			local sides = require("sides")
			local out = io.open("/out", "w")
			out:write(type(component.list), " ", type(computer.pullSignal), " ", unicode.len("h\\u{e9}llo"), " ",
			  tostring(component.isAvailable("gpu")), " ", tostring(term.isAvailable()), " ",
			  serialization.serialize({1, "a"}), " ", sides.front, " ", _OSVERSION:match("^OpenOS") or "?", "\\n")
			out:close()
			""");
		assertThat(status).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).isEqualTo("function function 5 true true {1,\"a\"} 3 OpenOS\n");
	}

	@Test
	void aLibraryOpenOsHadAndOpenOsDoesntIsAnErrorSayingSo() {
		String status = run("""
			require("process")
			""");
		assertThat(status).isEqualTo("1\n");
		assertThat(errors()).contains("openos: OpenOS's 'process' library is not supported");
	}

	@Test
	void osExitGivesTheStatus() {
		assertThat(run("os.exit(3)")).isEqualTo("3\n");
	}

	@Test
	void theGpuDrawsOnTheTerminalAndReadsItBack() {
		String status = run("""
			local gpu = require("component").gpu
			gpu.setForeground(0xFF0000)
			gpu.setBackground(0x0000FF)
			gpu.set(1, 1, "hello")
			gpu.fill(1, 2, 3, 1, "#")
			gpu.copy(1, 1, 2, 1, 5, 2) -- "he" to columns 6 and 7 of row 3
			local char, fg, bg = gpu.get(2, 1)
			local copied = gpu.get(7, 3)
			local w, h = gpu.getResolution()
			local out = io.open("/out", "w")
			out:write(char, " ", fg, " ", bg, " ", copied, " ", w, "x", h, " ", gpu.maxDepth(), "\\n")
			out:close()
			""");
		assertThat(status).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).isEqualTo("e 16711680 255 e 50x16 8\n");
		assertThat(screen.buffer().row(0)).startsWith("hello");
		assertThat(screen.buffer().row(1)).startsWith("###");
		assertThat(screen.buffer().row(2)).startsWith("     he");
	}

	@Test
	void keysAreKeyDownAndKeyUpSignalsWithTheirCharactersAndCodes() {
		start("""
			local event = require("event")
			local ready = io.open("/ready", "w") ready:close()
			local _, _, char, code = event.pull("key_down")
			local _, _, upChar, upCode = event.pull("key_up")
			local _, _, ctrlChar, ctrlCode = event.pull("key_down", nil, nil, 0x2E)
			local out = io.open("/out", "w")
			out:write(char, " ", code, " ", upChar, " ", upCode, " ", ctrlChar, " ", ctrlCode, "\\n")
			out:close()
			""");
		settle(() -> Files.exists(root().resolve("ready")));
		untilIdle();
		key('a', 1, "A");
		key('c', 4, "");
		assertThat(ended()).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).as("A on a's key (0x1E), then Ctrl+C: 3 on c's (0x2E)").isEqualTo("65 30 65 30 3 46\n");
	}

	@Test
	void ioReadIsOpenOsLineEditing() {
		start("""
			local ready = io.open("/ready", "w") ready:close()
			io.write("name? ")
			local line = io.read()
			local out = io.open("/out", "w")
			out:write("[", line, "]\\n")
			out:close()
			""");
		settle(() -> Files.exists(root().resolve("ready")));
		untilIdle();
		typeKeys("Ada");
		key(127, 0, ""); // (backspace)
		typeKeys("am\r");
		assertThat(ended()).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).isEqualTo("[Adam]\n");
	}

	@Test
	void sleepsAndTimersAreTheEventLibrarys() {
		String status = run("""
			local event = require("event")
			local ticks = 0
			event.timer(0.1, function() ticks = ticks + 1 end, 3)
			local start = computer.uptime()
			os.sleep(0.5)
			local out = io.open("/out", "w")
			out:write(ticks, " ", tostring(computer.uptime() - start >= 0.5), "\\n")
			out:close()
			""");
		assertThat(status).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).as("each due in time: a clock read is the firmware's, no server hop").isEqualTo("3 true\n");
	}

	@Test
	void aModemSendsAndHearsModemMessages() {
		devices.put(Motherboard.exp(0), new NetworkCard(CardKind.NETWORK));
		String status = run("""
			local component = require("component")
			local event = require("event")
			local modem = component.modem
			modem.open(7)
			modem.send(modem.address, 7, "ping", 42, true)
			local _, to, from, port, distance, a, b, c = event.pull(5, "modem_message")
			local out = io.open("/out", "w")
			out:write(tostring(to == modem.address), " ", tostring(from == modem.address), " ", port, " ", distance, " ",
			  a, " ", b, " ", tostring(c), " ", tostring(modem.isOpen(7)), " ", tostring(modem.isWireless()), "\\n")
			out:close()
			""");
		assertThat(status).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).isEqualTo("true true 7 0 ping 42 true true false\n");
	}

	@Test
	void redstoneIsSidesInputsOutputsAndChanges() {
		RedstoneCard card = new RedstoneCard();
		devices.put(Motherboard.exp(0), card);
		start("""
			local component = require("component")
			local event = require("event")
			local sides = require("sides")
			local rs = component.redstone
			local old = rs.setOutput(sides.east, 9)
			local ready = io.open("/ready", "w") ready:close()
			local _, address, side, from, to = event.pull(5, "redstone_changed")
			local out = io.open("/out", "w")
			out:write(old, " ", rs.getOutput(sides.east), " ", tostring(address == rs.address), " ", side, " ", from, " ", to, " ",
			  rs.getInput(sides.north), "\\n")
			out:close()
			""");
		settle(() -> Files.exists(root().resolve("ready")));
		untilIdle();
		server.add(() -> card.sense(0, 12)); // north: a signal comes in
		assertThat(ended()).as(() -> "its errors: " + errors()).isEqualTo("0\n");
		assertThat(out()).as("its own output no change; north (OpenComputers' 2, with no front) 0 to 12").isEqualTo("0 9 true 2 0 12 12\n");
	}
}
