package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Chassis;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** What the board tells a program of its case and devices (ACPI, SMBIOS, udev), and the EEPROM's contents. */
class BoardInfoTest {
	private final Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
	private final List<Object> seen = new ArrayList<>();

	@AfterEach
	void powerDown() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	private Eeprom run(com.payonel.modularcomputers.machine.Program program) {
		machine.board().setCpu(new Cpu(CpuTier.TIER_2));
		Eeprom eeprom = new Eeprom(program);
		machine.board().setEeprom(eeprom);
		machine.pressPower();
		return eeprom;
	}

	@Test
	void theCasesPowerAndHardwareAsItsFirmwareReportsThem() {
		Board.PowerInfo power = new Board.PowerInfo(new Board.Store(300, 600, false),
			List.of(new Board.BatteryInfo(2, 1, new Board.Store(90, 100, false)), new Board.BatteryInfo(0, 3, new Board.Store(0, 0, true))), 110);
		List<Board.MemoryInfo> sticks = List.of(new Board.MemoryInfo(0, 1, 256 << 10), new Board.MemoryInfo(3, 3, 1 << 20));
		machine.board().setChassis(new Chassis() {
			@Override
			public int tier() {
				return 2;
			}

			@Override
			public Board.PowerInfo power() {
				return power;
			}

			@Override
			public List<Board.MemoryInfo> memory() {
				return sticks;
			}
		});
		run(board -> {
			seen.add(board.power());
			seen.add(board.hardware());
			com.payonel.modularcomputers.machine.Program.park(board);
		});
		assertThat(seen.get(0)).isEqualTo(power);
		assertThat(((Board.PowerInfo) seen.get(0)).store().percent()).isEqualTo(50);
		assertThat(((Board.PowerInfo) seen.get(0)).batteries().get(1).charge().percent()).as("infinite: always full").isEqualTo(100);
		assertThat(seen.get(1)).isEqualTo(new Board.HardwareInfo(2, new Board.CpuInfo(2, 16), sticks, (256 << 10) + (1 << 20), 0));
	}

	@Test
	void aBoardOnABenchHasNoCaseAndEndlessPower() {
		run(board -> {
			seen.add(board.hardware().caseTier());
			seen.add(board.power().store().infinite());
			com.payonel.modularcomputers.machine.Program.park(board);
		});
		assertThat(seen).containsExactly(0, true);
	}

	@Test
	void eachDeviceSaysWhatItIs() {
		Disk disk = new Disk(DiskTier.TIER_2);
		disk.labelIn(new com.payonel.modularcomputers.bus.Label(() -> "backups", null));
		Terminal terminal = new Terminal(Resolution.DEFAULT, TerminalTier.TIER_2);
		machine.board().setCpu(new Cpu(CpuTier.TIER_2));
		TestBus bus = new TestBus(machine);
		String diskId = bus.disk(disk);
		bus.cable("id:screen", terminal);
		run(board -> {
			seen.add(board.info(diskId));
			seen.add(board.info("id:screen"));
			seen.add(board.info("console"));
			seen.add(board.info("sata:3"));
			com.payonel.modularcomputers.machine.Program.park(board);
		});
		assertThat(seen.get(0)).isEqualTo(Optional.of(Map.of("address", "sata:0", "id", diskId, "type", "storage", "label", "backups",
			"tier", 2L, "capacity", (long) DiskTier.TIER_2.bytes(), "mode", "hfs")));
		assertThat(seen.get(1)).as("no label unless named").isEqualTo(Optional.of(Map.of("address", "id:screen", "id", "id:screen",
			"type", "terminal", "tier", 2L, "width", 50L, "height", 16L, "colors", 16L)));
		assertThat(seen.get(2)).isEqualTo(Optional.of(Map.of("address", "console", "type", "console")));
		assertThat(seen.get(3)).isEqualTo(Optional.empty());
	}

	@Test
	void theEepromsContentsAreReadAndWrittenWithGetAndSet() {
		List<String> written = new ArrayList<>();
		Eeprom eeprom = new Eeprom(new Eeprom.Storage() { // where the contents are: each write lands here, each read comes from here
			@Override
			public String read() {
				return written.isEmpty() ? "" : written.getLast();
			}

			@Override
			public void write(String image) {
				written.add(image);
			}
		});
		eeprom.flash(board -> {
			seen.add(board.invoke("eeprom", "get").first());
			seen.add(board.invoke("eeprom", "set", "print('hi')").failed());
			seen.add(board.invoke("eeprom", "get").first());
			seen.add(board.info("eeprom").orElseThrow().get("used"));
			seen.add(board.invoke("eeprom", "set", "x".repeat(100_000)).failed()); // no size limit for now
			seen.add(board.info("eeprom").orElseThrow().get("used"));
			com.payonel.modularcomputers.machine.Program.park(board);
		});
		machine.board().setCpu(new Cpu(CpuTier.TIER_2));
		new TestBus(machine).eeprom(eeprom);
		machine.pressPower();
		assertThat(seen).containsExactly("", false, "print('hi')", 11L, false, 100_000L);
		assertThat(written).as("each write goes to its storage").containsExactly("print('hi')", "x".repeat(100_000));
	}
}
