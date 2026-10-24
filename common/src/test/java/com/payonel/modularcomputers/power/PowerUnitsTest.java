package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PowerUnitsTest {
	@Test
	void ratesAndAmountsShowShortWithAnSiPrefixAndTheirUnit() {
		assertThat(PowerUnits.rate(8)).isEqualTo("8 " + PowerUnits.RATE_SYMBOL);
		assertThat(PowerUnits.rate(950)).isEqualTo("950 " + PowerUnits.RATE_SYMBOL);
		assertThat(PowerUnits.rate(12_345)).isEqualTo("12.3 k" + PowerUnits.RATE_SYMBOL);
		assertThat(PowerUnits.rate(999_999)).isEqualTo("1.00 M" + PowerUnits.RATE_SYMBOL);
		assertThat(PowerUnits.rate(0)).isEqualTo("0 " + PowerUnits.RATE_SYMBOL);
		long hundred = (long) (100 * PowerUnits.UNITS_PER_AMOUNT);
		assertThat(PowerUnits.amount(hundred, false)).isEqualTo("100 " + PowerUnits.AMOUNT_SYMBOL);
		assertThat(PowerUnits.amount(hundred / 1000, false)).as("a tenth").isEqualTo("100 m" + PowerUnits.AMOUNT_SYMBOL);
		assertThat(PowerUnits.amount(hundred, true)).isEqualTo(PowerUnits.INFINITE);
	}

	@Test
	void runtimeIsWhatsStoredAtTheLoad() {
		assertThat(PowerUnits.runtime(PowerUnits.DAY, PowerUnits.ALL_IN_ONE)).as("a running all-in-one: a day").isEqualTo(BatteryCharge.DAY_TICKS);
		assertThat(PowerUnits.runtime(PowerUnits.DAY, PowerUnits.TERMINAL)).as("just a terminal: 11 days").isEqualTo(11L * BatteryCharge.DAY_TICKS);
		assertThat(PowerUnits.runtime(1000, 0)).as("no load: no estimate").isNegative();
	}
}
