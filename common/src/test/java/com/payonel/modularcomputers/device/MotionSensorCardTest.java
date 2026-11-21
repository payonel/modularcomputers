package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** A motion sensor's looks: how often, and what counts as moved, by the settings' values passed in. */
class MotionSensorCardTest {
	/** It looks every interval ticks of the world's: once in each run of that many. */
	@Test
	void itLooksEveryIntervalTicks() {
		assertThat(LongStream.range(0, 20).filter(tick -> MotionSensorCard.looks(tick, 5))).containsExactly(0L, 5L, 10L, 15L);
		assertThat(LongStream.range(0, 20).filter(tick -> MotionSensorCard.looks(tick, 8))).containsExactly(0L, 8L, 16L);
		assertThat(LongStream.range(0, 4).filter(tick -> MotionSensorCard.looks(tick, 1))).as("1: every tick").hasSize(4);
	}

	/**
	 * A creature moved when it went the threshold or more along any one axis since the last look; one
	 * not seen then always has; a threshold of 0 counts every creature seen, moved or not.
	 */
	@Test
	void aCreatureMovedByTheThresholdAlongAnyAxis() {
		double[] was = {10, 64, 10};
		assertThat(MotionSensorCard.moved(was, new double[] {10.05, 64, 10}, 0.1)).as("short of it").isFalse();
		assertThat(MotionSensorCard.moved(was, new double[] {10, 64, 10.15}, 0.1)).as("past it").isTrue();
		assertThat(MotionSensorCard.moved(was, new double[] {10, 63.5, 10}, 0.1)).as("down").isTrue();
		assertThat(MotionSensorCard.moved(was, new double[] {10, 63.5, 10}, 1)).as("a threshold set higher").isFalse();
		assertThat(MotionSensorCard.moved(was, new double[] {10.05, 64, 10}, 0.01)).as("and lower").isTrue();
		assertThat(MotionSensorCard.moved(was, was.clone(), 0)).as("0: every creature, still or not").isTrue();
		assertThat(MotionSensorCard.moved(null, was, 0.1)).as("not seen at the last look").isTrue();
	}
}
