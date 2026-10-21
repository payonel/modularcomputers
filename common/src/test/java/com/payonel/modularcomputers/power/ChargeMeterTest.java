package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChargeMeterTest {
	@ParameterizedTest
	@CsvSource({
		"0, 0, OFF",      // empty: all off
		"1, 1, LOW",      // any charge shows one red bar
		"10, 1, LOW",
		"17, 2, MEDIUM",
		"50, 3, MEDIUM",
		"51, 4, HIGH",
		"99, 6, HIGH",
		"100, 6, HIGH",
		"150, 6, HIGH",   // clamped
		"-5, 0, OFF",
	})
	void barsFillLeftToRightRedThenYellowThenGreen(int charge, int bars, ChargeMeter.Level level) {
		assertThat(ChargeMeter.litBars(charge)).isEqualTo(bars);
		assertThat(ChargeMeter.level(ChargeMeter.litBars(charge))).isEqualTo(level);
	}

	@ParameterizedTest
	@CsvSource({"0"})
	void noBarsMeansOff(int bars) {
		assertThat(ChargeMeter.level(bars)).isEqualTo(ChargeMeter.Level.OFF);
	}
}
