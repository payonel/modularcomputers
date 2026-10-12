package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PowerBufferTest {
	@Test
	void holdsItsCapacityAndTakesOnlyWhatItsMissing() {
		PowerBuffer buffer = new PowerBuffer();
		assertThat(buffer.isEmpty()).isTrue();
		assertThat(buffer.fill(10L * PowerUnits.DAY)).as("a hundredth of a day of a running all-in-one").isEqualTo(PowerUnits.DAY / 100);
		assertThat(buffer.percent()).isEqualTo(100);
		int quarter = PowerBuffer.CAPACITY / 4;
		buffer.use(quarter);
		assertThat(buffer.percent()).isEqualTo(75);
		assertThat(buffer.fill(PowerBuffer.CAPACITY)).as("tops up").isEqualTo(quarter);
		assertThat(buffer.fill(PowerBuffer.CAPACITY)).as("full").isZero();
	}

	@Test
	void runsDownToEmptyAndNoFurther() {
		PowerBuffer buffer = new PowerBuffer();
		assertThat(buffer.fill(100)).isEqualTo(100);
		buffer.use(150);
		assertThat(buffer.level()).isZero();
		assertThat(buffer.isEmpty()).isTrue();
		assertThat(buffer.fill(-5)).isZero();
	}

	@Test
	void anyPowerLeftShowsAsAtLeastOnePercent() {
		PowerBuffer buffer = new PowerBuffer();
		buffer.set(1);
		assertThat(buffer.percent()).isEqualTo(1);
		buffer.set(Integer.MAX_VALUE);
		assertThat(buffer.level()).isEqualTo(PowerBuffer.CAPACITY);
	}
}
