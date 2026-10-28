package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An EEPROM's NVRAM (BootVariables): a closed set of typed values, each change checked for its form
 * (never whether its disk is there), so its bootloader can always read what's kept.
 */
class BootVariablesTest {
	private static final String DISK = "id:0f4c2a10-7b3e-4d9a-9e1f-2c5b8a6d3e71";
	private static final String OTHER = "id:5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	@Test
	void anEntryNeedsAnIdAPathFromTheTopAndALabelThatCanBeOne() {
		BootVariables vars = BootVariables.EMPTY;
		assertThatThrownBy(() -> vars.withEntry(0, "sata:1", null, null)).hasMessage("not an id: sata:1");
		assertThatThrownBy(() -> vars.withEntry(0, "id:not-a-uuid", null, null)).hasMessageContaining("not an id");
		assertThatThrownBy(() -> vars.withEntry(0, DISK.toUpperCase().replace("ID:", "id:"), null, null)).hasMessageContaining("not an id: ");
		assertThatThrownBy(() -> vars.withEntry(0, DISK, "init.lua", null)).hasMessage("not absolute: init.lua");
		assertThatThrownBy(() -> vars.withEntry(0, DISK, "/my os.lua", null)).hasMessage("not printable");
		assertThatThrownBy(() -> vars.withEntry(0, DISK, "/" + "a".repeat(BootVariables.MAX_PATH), null)).hasMessage("too long: " + BootVariables.MAX_PATH);
		assertThatThrownBy(() -> vars.withEntry(0, DISK, null, "x".repeat(40))).hasMessage("too long: " + com.payonel.modularcomputers.bus.Label.MAX);
		assertThatThrownBy(() -> vars.withEntry(BootVariables.ENTRIES, DISK, null, null)).hasMessage("not an entry: " + BootVariables.ENTRIES);
		assertThatThrownBy(() -> vars.withEntry(-1, DISK, null, null)).hasMessage("not an entry: -1");
	}

	@Test
	void anEntryForADiskNotThereIsKeptAndItsFileIsInitLuaByDefault() throws Exception {
		BootVariables vars = BootVariables.EMPTY.withEntry(3, DISK, null, "tiny OS");
		assertThat(vars.entries()).isEqualTo(Map.of(3, new BootVariables.Entry(DISK, "/init.lua", "tiny OS")));
	}

	@Test
	void theOrderAndBootNextNameEntriesThatExistOnceEach() throws Exception {
		BootVariables vars = BootVariables.EMPTY.withEntry(0, DISK, null, null).withEntry(1, OTHER, "/boot/os.lua", null);
		assertThatThrownBy(() -> vars.withOrder(List.of(0L, 2L))).hasMessageContaining("no Boot0002");
		assertThatThrownBy(() -> vars.withOrder(List.of(0L, 0L))).hasMessage("twice: Boot0000");
		assertThatThrownBy(() -> vars.withNext(5)).hasMessageContaining("no Boot0005");
		BootVariables ordered = vars.withOrder(List.of(1L, 0L)).withNext(0);
		assertThat(ordered.toTry()).as("BootNext's first, then the order's, each once")
			.containsExactly(vars.entries().get(0), vars.entries().get(1));
		assertThat(ordered.withNext(BootVariables.NONE).toTry()).containsExactly(vars.entries().get(1), vars.entries().get(0));
	}

	@Test
	void removingAnEntryTakesItOutOfTheOrderAndBootNext() throws Exception {
		BootVariables vars = BootVariables.EMPTY.withEntry(0, DISK, null, null).withEntry(1, OTHER, null, null).withOrder(List.of(0L, 1L)).withNext(0);
		BootVariables left = vars.withoutEntry(0);
		assertThat(left.entries()).containsOnlyKeys(1);
		assertThat(left.order()).containsExactly(1);
		assertThat(left.next()).isEqualTo(BootVariables.NONE);
		assertThatThrownBy(() -> left.withoutEntry(0)).hasMessageContaining("no Boot0000");
	}

	@Test
	void theEepromRefusesABadChangeWithWhyAndKeepsWhatItHad() {
		Eeprom eeprom = new Eeprom("");
		assertThat(eeprom.setBootEntry(args(0L, DISK, null, "mcos")).failed()).isFalse();
		var refused = eeprom.setBootEntry(args(1L, "storage1"));
		assertThat(refused.failed()).isTrue();
		assertThat(refused.error()).startsWith("not an id: ");
		assertThat(eeprom.setBootOrder(args(Map.of(1L, 0L))).failed()).isFalse();
		assertThat(eeprom.setBootOrder(args(Map.of(1L, 4L))).failed()).as("no such entry").isTrue();
		assertThat(eeprom.setBootNext(args(0L)).failed()).isFalse();
		assertThat(eeprom.nvram().entries()).containsOnlyKeys(0);
		assertThat(eeprom.nvram().order()).containsExactly(0);
		assertThat(eeprom.nvram().next()).isZero();
		assertThat(eeprom.setBootNext(args()).failed()).isFalse();
		assertThat(eeprom.nvram().next()).as("cleared").isEqualTo(BootVariables.NONE);
	}
}
