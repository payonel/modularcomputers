package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DurationsTest {
	@ParameterizedTest
	@CsvSource({
		"0, 0s",
		"39, 1s",
		"800, 40s",
		"1200, 1m",
		"24000, 20m",      // one day
		"72000, 1h 0m",
		"240000, 3h 20m",  // a full tier 1
		"-5, 0s",
	})
	void showsHoursAndMinutesOrSeconds(long ticks, String text) {
		assertThat(Durations.realTime(ticks)).isEqualTo(text);
	}
}
