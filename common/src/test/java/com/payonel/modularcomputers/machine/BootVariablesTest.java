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
		assertThat(refused(() -> vars.withEntry(0, "sata:1", null, null))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 2L, "value", "sata:1", "expected", "disk_id"));
		assertThat(refused(() -> vars.withEntry(0, "id:not-a-uuid", null, null))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 2L, "value", "id:not-a-uuid", "expected", "disk_id"));
		assertThat(refused(() -> vars.withEntry(0, DISK.toUpperCase().replace("ID:", "id:"), null, null)).get("expected")).isEqualTo("disk_id");
		assertThat(refused(() -> vars.withEntry(0, DISK, "init.lua", null))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 3L, "value", "init.lua", "expected", "absolute_path"));
		assertThat(refused(() -> vars.withEntry(0, DISK, "/my os.lua", null))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 3L, "value", "/my os.lua", "expected", "printable"));
		assertThat(refused(() -> vars.withEntry(0, DISK, "/" + "a".repeat(BootVariables.MAX_PATH), null)))
			.isEqualTo(java.util.Map.of("why", "limit", "of", "path", "max", (long) BootVariables.MAX_PATH));
		assertThat(refused(() -> vars.withEntry(0, DISK, null, "x".repeat(40))))
			.isEqualTo(java.util.Map.of("why", "limit", "of", "label", "max", (long) com.payonel.modularcomputers.bus.Label.MAX));
		assertThat(refused(() -> vars.withEntry(BootVariables.ENTRIES, DISK, null, null))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 1L, "value", (long) BootVariables.ENTRIES, "expected", "entry"));
		assertThat(refused(() -> vars.withEntry(-1, DISK, null, null))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 1L, "value", -1L, "expected", "entry"));
	}

	@Test
	void anEntryForADiskNotThereIsKeptAndItsFileIsInitLuaByDefault() throws Exception {
		BootVariables vars = BootVariables.EMPTY.withEntry(3, DISK, null, "tiny OS");
		assertThat(vars.entries()).isEqualTo(Map.of(3, new BootVariables.Entry(DISK, "/init.lua", "tiny OS")));
	}

	@Test
	void theOrderAndBootNextNameEntriesThatExistOnceEach() throws Exception {
		BootVariables vars = BootVariables.EMPTY.withEntry(0, DISK, null, null).withEntry(1, OTHER, "/boot/os.lua", null);
		assertThat(refused(() -> vars.withOrder(List.of(0L, 2L)))).isEqualTo(java.util.Map.of("why", "not_found", "kind", "boot_entry", "value", "0002"));
		assertThat(refused(() -> vars.withOrder(List.of(0L, 0L)))).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 1L, "value", 0L, "expected", "distinct_entries"));
		assertThat(refused(() -> vars.withNext(5))).isEqualTo(java.util.Map.of("why", "not_found", "kind", "boot_entry", "value", "0005"));
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
		assertThat(refused(() -> left.withoutEntry(0))).isEqualTo(java.util.Map.of("why", "not_found", "kind", "boot_entry", "value", "0000"));
	}

	@Test
	void theEepromRefusesABadChangeWithWhyAndKeepsWhatItHad() {
		Eeprom eeprom = new Eeprom("");
		assertThat(eeprom.setBootEntry(args(0L, DISK, null, "mcos")).failed()).isFalse();
		var refused = eeprom.setBootEntry(args(1L, "storage1"));
		assertThat(refused.failed()).isTrue();
		assertThat(refused.failure().table()).isEqualTo(java.util.Map.of("why", "bad_argument", "argument", 2L, "value", "storage1", "expected", "disk_id"));
		assertThat(eeprom.setBootOrder(args(Map.of(1L, 0L))).failed()).isFalse();
		assertThat(eeprom.setBootOrder(args(Map.of(1L, 4L))).failed()).as("no such entry").isTrue();
		assertThat(eeprom.setBootNext(args(0L)).failed()).isFalse();
		assertThat(eeprom.nvram().entries()).containsOnlyKeys(0);
		assertThat(eeprom.nvram().order()).containsExactly(0);
		assertThat(eeprom.nvram().next()).isZero();
		assertThat(eeprom.setBootNext(args()).failed()).isFalse();
		assertThat(eeprom.nvram().next()).as("cleared").isEqualTo(BootVariables.NONE);
	}

	/** A change the variables refuse, as its failure's table. */
	private interface Change {
		Object apply() throws BootVariables.Invalid;
	}

	private static java.util.Map<String, Object> refused(Change change) {
		try {
			change.apply();
		} catch (BootVariables.Invalid e) {
			return e.failure().table();
		}
		throw new AssertionError("not refused");
	}
}
