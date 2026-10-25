package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.machine.BootVariables;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.LuaScripts;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The EEPROM's boot variables (user design, 2026-10-25): the factory bootloader trying BootNext once,
 * then BootOrder, skipping a disk not on the bus, then its fallback (the first disk holding
 * /init.lua); efibootmgr changing them, and /sys/firmware/efi/efivars showing them. Checked by which
 * disk booted (a file it writes on itself), the EEPROM's NVRAM, and files programs write.
 */
class BootOrderTest extends McosHarness {
	/** An OS that says it booted (a file on its own disk) and turns the computer off. */
	private static final String MARKS = """
		local disk = ...
		local ok, file = board.invoke(disk, "open", "/booted", "w")
		board.invoke(file, "write", "yes")
		board.invoke(file, "close")
		board.poweroff()
		""";
	/** One that halts instead: the power stays on (BootCurrent with it). */
	private static final String HALTS = MARKS.replace("board.poweroff()", "board.halt()");
	private static final UUID FIRST = UUID.fromString("0f4c2a10-7b3e-4d9a-9e1f-2c5b8a6d3e71");
	private static final UUID SECOND = UUID.fromString("5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
	private static final String ABSENT = "id:9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b";

	private final Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
	private Path first;
	private Path second;

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	/** Two disks, each holding an OS that marks it booted; the computer's EEPROM the factory's. */
	private void twoDisks() throws Exception {
		first = world.resolve("first");
		second = world.resolve("second");
		for (Path disk : new Path[] {first, second}) {
			Files.createDirectories(disk);
			Files.writeString(disk.resolve("init.lua"), MARKS);
		}
		devices.put("eeprom", eeprom);
		TestBus bus = TestBus.of(machine, devices);
		machine.board().setEeprom(eeprom);
		bus.plug(Motherboard.sata(0), new Disk(DiskTier.TIER_1, first), FIRST);
		bus.plug(Motherboard.sata(1), new Disk(DiskTier.TIER_1, second), SECOND);
	}

	/** Powers on, until the OS it booted (one that halts) has said so and halted, the power on. */
	private void bootToHalt(Path disk) throws Exception {
		Files.writeString(disk.resolve("init.lua"), HALTS);
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		settle(() -> booted(disk) && machine.runner().isEmpty());
		assertThat(machine.isPowered()).as("halted, the power on").isTrue();
	}

	/** Powers on, until the OS it booted turns the computer off. */
	private void bootOnce() {
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		settle(() -> !machine.isPowered());
	}

	private static boolean booted(Path disk) {
		return Files.exists(disk.resolve("booted"));
	}

	@Test
	void bootOrderBootsItsDiskThoughAnotherIsFirstOnTheBus() throws Exception {
		twoDisks();
		eeprom.setBootEntry(args(0L, Motherboard.id(SECOND)));
		eeprom.setBootOrder(args(Map.of(1L, 0L)));
		bootOnce();
		assertThat(booted(second)).as("the entry's disk").isTrue();
		assertThat(booted(first)).isFalse();
	}

	@Test
	void bootCurrentIsTheEntryBootedNoneForTheFallbackAndGoesWithThePower() throws Exception {
		twoDisks();
		eeprom.setBootEntry(args(3L, Motherboard.id(SECOND)));
		eeprom.setBootNext(args(3L));
		bootToHalt(second);
		assertThat(eeprom.bootCurrent()).as("BootNext's entry").isEqualTo(3);
		Map<String, Object> saved = eeprom.saved();
		eeprom.restore(Map.of());
		eeprom.restore(saved);
		assertThat(eeprom.bootCurrent()).as("kept through a save").isEqualTo(3);
		machine.pressPower(); // (off)
		settle(() -> !machine.isPowered());
		assertThat(eeprom.bootCurrent()).as("volatile: gone with the power").isEqualTo(BootVariables.NONE);
		bootToHalt(first); // (no BootNext now, no order: the fallback, the first disk)
		assertThat(eeprom.bootCurrent()).as("the fallback: none").isEqualTo(BootVariables.NONE);
		assertThat(eeprom.setBootCurrent(args(5L)).failed()).as("an entry that isn't there").isTrue();
	}

	@Test
	void bootNextBootsOnceAndIsClearedThenTheOrderAgain() throws Exception {
		twoDisks();
		eeprom.setBootEntry(args(0L, Motherboard.id(FIRST)));
		eeprom.setBootEntry(args(1L, Motherboard.id(SECOND)));
		eeprom.setBootOrder(args(Map.of(1L, 0L)));
		eeprom.setBootNext(args(1L));
		bootOnce();
		assertThat(booted(second)).as("BootNext's").isTrue();
		assertThat(booted(first)).isFalse();
		assertThat(eeprom.nvram().next()).as("cleared as it booted").isEqualTo(BootVariables.NONE);
		bootOnce();
		assertThat(booted(first)).as("then the order's").isTrue();
	}

	@Test
	void anEntryWhoseDiskIsntThereIsSkippedForTheNextAndThenTheFallback() throws Exception {
		twoDisks();
		eeprom.setBootEntry(args(0L, ABSENT));
		eeprom.setBootEntry(args(1L, Motherboard.id(SECOND), "/missing.lua"));
		eeprom.setBootOrder(args(Map.of(1L, 0L, 2L, 1L)));
		bootOnce();
		assertThat(booted(first)).as("neither entry boots: the first disk holding /init.lua").isTrue();
		assertThat(booted(second)).isFalse();
	}

	@Test
	void efibootmgrMakesAnEntryOrdersItAndSysShowsTheVariables() {
		dataDisk();
		boot();
		type("efibootmgr -q -c -d storage1 -L tiny; efibootmgr -q -n 0; efibootmgr -q -c -d " + ABSENT + "; echo $? > /made\r");
		untilFile(root().resolve("made"), "0\n");
		untilIdle();
		String efivars = "/sys/firmware/efi/efivars/";
		type("cd " + efivars + "; cat /sys/class/storage/storage1/id > /id; cat Boot0000 > /b0; cat Boot0001 > /b1; touch /read\r");
		untilFile(root().resolve("read"), "");
		untilIdle();
		type("cat BootOrder > /order; cat BootNext > /next; ls > /names; cd; touch /listed\r"); // (a line each: a terminal takes 256 keys at once)
		untilFile(root().resolve("listed"), "");
		String id = read(root().resolve("id")).strip();
		assertThat(id).startsWith("id:");
		assertThat(read(root().resolve("b0"))).isEqualTo(id + " /init.lua tiny\n");
		assertThat(read(root().resolve("b1"))).as("a disk not on the bus: kept, and said").isEqualTo(ABSENT + " /init.lua\nnot present\n");
		assertThat(read(root().resolve("order"))).as("a new entry goes first").isEqualTo("0001\n0000\n");
		assertThat(read(root().resolve("next"))).isEqualTo("0000\n");
		assertThat(read(root().resolve("names"))).isEqualTo("Boot0000\nBoot0001\nBootNext\nBootOrder\n");
		assertThat(eeprom().nvram().entries().get(0)).isEqualTo(new BootVariables.Entry(id, "/init.lua", "tiny"));
		untilIdle();
		// Refused: no such disk, an entry that isn't one, a bad usage; deleting one takes it out of the order too.
		type("efibootmgr -q -c -d nosuch; echo $? > /nodisk; efibootmgr -q -o 0,5; echo $? > /noentry; efibootmgr -x; echo $? > /usage; "
			+ "efibootmgr -q -b 1 -B; efibootmgr -q -N; echo $? > /deleted\r");
		untilFile(root().resolve("deleted"), "0\n");
		assertThat(read(root().resolve("nodisk"))).isEqualTo("1\n");
		assertThat(read(root().resolve("noentry"))).isEqualTo("1\n");
		assertThat(read(root().resolve("usage"))).isEqualTo("2\n");
		assertThat(eeprom().nvram().entries()).containsOnlyKeys(0);
		assertThat(eeprom().nvram().order()).containsExactly(0);
		assertThat(eeprom().nvram().next()).isEqualTo(BootVariables.NONE);
		assertThat(eeprom().bootCurrent()).as("booted by the fallback").isEqualTo(BootVariables.NONE);
		// Booted by an entry: BootCurrent says which, in /sys and efibootmgr.
		untilIdle();
		type("efibootmgr -q -c -d storage0 -L mcos; efibootmgr -q -n 1; reboot\r");
		settle(() -> eeprom().bootCurrent() == 1);
		untilIdle();
		type("cat /sys/firmware/efi/efivars/BootCurrent > /current; efibootmgr > /shown\r");
		untilFile(root().resolve("current"), "0001\n");
		settle(() -> Files.exists(root().resolve("shown")) && read(root().resolve("shown")).contains("Boot0001"));
		assertThat(read(root().resolve("shown"))).startsWith("BootCurrent: 0001\n");
		// -C: an entry left out of the order.
		var order = eeprom().nvram().order();
		type("efibootmgr -q -C -d storage1 -L only; touch /only\r");
		untilFile(root().resolve("only"), "");
		assertThat(eeprom().nvram().entries().get(2)).isEqualTo(new BootVariables.Entry(eeprom().nvram().entries().get(0).disk(), "/init.lua", "only"));
		assertThat(eeprom().nvram().order()).as("left as it was").isEqualTo(order);
	}

	private Eeprom eeprom() {
		return (Eeprom) devices.get("eeprom");
	}
}
