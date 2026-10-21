package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PowerUnitsTest {
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
	void runtimeIsWhatsStoredAtTheLoad() {
		assertThat(PowerUnits.runtime(PowerUnits.DAY, PowerUnits.ALL_IN_ONE)).as("a running all-in-one: a day").isEqualTo(BatteryCharge.DAY_TICKS);
		assertThat(PowerUnits.runtime(PowerUnits.DAY, PowerUnits.TERMINAL)).as("just a terminal: 11 days").isEqualTo(11L * BatteryCharge.DAY_TICKS);
		assertThat(PowerUnits.runtime(1000, 0)).as("no load: no estimate").isNegative();
	}
}
