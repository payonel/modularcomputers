package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecentDrawTest {
	@Test
	void followsTheDrawWithinAFewPeriods() {
		RecentDraw draw = new RecentDraw();
		assertThat(draw.perPeriod()).isZero();
		draw.record(2200);
		assertThat(draw.perPeriod()).as("a small start still shows").isEqualTo(2200);
		for (int i = 0; i < 30; i++) {
			draw.record(200);
		}
		assertThat(draw.perPeriod()).isBetween(200L, 210L);
		for (int i = 0; i < 40; i++) {
			draw.record(0);
		}
		assertThat(draw.perPeriod()).as("off: nothing").isZero();
	}

	@Test
	void unitsShowShortWithAnSiPrefix() {
		assertThat(PowerUnits.format(950)).isEqualTo("950");
		assertThat(PowerUnits.format(12_345)).isEqualTo("12.3k");
		assertThat(PowerUnits.format(264_000)).isEqualTo("264k");
		assertThat(PowerUnits.format(PowerUnits.DAY)).isEqualTo("2.64M");
		assertThat(PowerUnits.format(26_400_000)).isEqualTo("26.4M");
		assertThat(PowerUnits.format(999_999)).isEqualTo("1.00M");
		assertThat(PowerUnits.format(1_056_000_000)).isEqualTo("1.06G");
	}

	@Test
	void runtimeIsWhatsLeftAtTheRecentDraw() {
		// A running all-in-one draws 110 a tick: 2200 a period. A day of it lasts a day.
		assertThat(RecentDraw.runtime(PowerUnits.DAY, 20L * PowerUnits.ALL_IN_ONE)).isEqualTo(BatteryCharge.DAY_TICKS);
		assertThat(RecentDraw.runtime(PowerUnits.DAY, 20L * PowerUnits.TERMINAL)).as("just a terminal: 11 days").isEqualTo(11L * BatteryCharge.DAY_TICKS);
		assertThat(RecentDraw.runtime(1000, 0)).as("nothing drawn: no estimate").isNegative();
	}
}
