package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DurationsTest {
	@ParameterizedTest
	@CsvSource({
		"0, 00:00:00",
		"39, 00:00:01",
		"800, 00:00:40",
		"1200, 00:01:00",
		"12000, 00:10:00",    // half a day
		"23999, 00:19:59",
		"24000, 01:00:00",    // one day: 20 real minutes
		"240000, 10:00:00",   // a full tier 1
		"2400000, 100:00:00",
		"-5, 00:00:00",
	})
	void showsDaysThenMinutesAndSecondsIntoTheDay(long ticks, String text) {
		assertThat(Durations.clock(ticks)).isEqualTo(text);
	}
}
