package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.LuaScripts;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * The manual's own-OS tutorials (docs/examples/os-*): each OS on a disk with nothing else, booted
 * by the factory EEPROM's bootloader, as a player would put it there. They check what each does
 * (the computer off, files on its disk, a redstone face), with keys typed as a player types them.
 */
class OwnOsExamplesTest extends McosHarness {
	/** Boots a disk holding only this /init.lua (and the files a test puts there first). */
	private void bootOwn(String init) {
		file("init.lua", init);
		devices.put("sata:0", new Disk(DiskTier.TIER_1, root()));
		Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
		devices.put("eeprom", eeprom);
		TestBus.of(machine, devices);
		machine.board().setEeprom(eeprom);
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
	}

	/** Until the OS waits for keys (still running). */
	private void untilWaiting() {
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(() -> machine.runner().isEmpty() || (!screen.hasKeys() && runner.state() == Runner.State.WAITING));
		assertThat(machine.runner()).as(() -> "still running; the screen:\n" + screen()).isPresent();
	}

	private void untilOff() {
		settle(() -> !machine.isPowered());
	}

	@Test
	void theHelloOsSaysHelloAndTurnsTheComputerOff() {
		bootOwn(example("os-hello"));
		untilOff();
	}

	@Test
	void theEchoOsReadsLinesWithBackspaceAndArrowsAndTurnsOffAtOff() {
		bootOwn(example("os-echo"));
		untilWaiting();
		type("hello\r");
		untilWaiting();
		type("ofx\u007Ff"); // (a typo, rubbed out)
		type("\u001B[A\u001B[D"); // (arrow keys: skipped)
		type("\r");
		untilOff();
	}

	@Test
	void theShellOsSavesFilesCallsMethodsRunsProgramsAndTurnsOff() throws Exception {
		RedstoneCard card = new RedstoneCard();
		devices.put(Motherboard.exp(0), card);
		file("blink.lua", example("os-blink"));
		bootOwn(example("os-shell"));
		untilWaiting();
		type("sv\u007Fave /note.txt hello  world\r"); // (the text as typed, its spaces kept)
		untilFile(root().resolve("note.txt"), "hello  world\n");
		untilWaiting();
		type("call redstone set {north = {powered = 7}}\r");
		settle(() -> card.output(0) == 7);
		untilWaiting();
		// A program from the disk: it blinks the face twice, then saves a file through the shell's own commands.
		type("call redstone set {north = {powered = 0}}\r");
		settle(() -> card.output(0) == 0);
		untilWaiting();
		type("run /blink.lua 2\r");
		settle(() -> card.output(0) == 15);
		settle(() -> card.output(0) == 0);
		untilWaiting();
		type("nosuch\r"); // (no such command: the shell goes on)
		untilWaiting();
		type("cat /missing\r");
		untilWaiting();
		type("methods redstone\r");
		untilWaiting();
		type("devices\r");
		untilWaiting();
		type("ls /\r");
		untilWaiting();
		type("call redstone set {north = {powered = 3}}\r"); // (a change: its event waits in the card)
		settle(() -> card.output(0) == 3);
		untilWaiting();
		type("wait 0.1\r");
		untilWaiting();
		type("call redstone pull\r"); // (its oldest change, taken)
		untilWaiting();
		type("uptime\r");
		untilWaiting();
		type("help\r");
		untilWaiting();
		assertThat(Files.readString(root().resolve("note.txt"))).isEqualTo("hello  world\n");
		type("off\r");
		untilOff();
	}
}
