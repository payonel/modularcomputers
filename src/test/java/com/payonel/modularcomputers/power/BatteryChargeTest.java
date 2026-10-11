package com.payonel.modularcomputers.power;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatteryChargeTest {
	private static final int DAY = BatteryCharge.DAY_TICKS;

	@Test
	void aFullTier1BatteryRunsTenDaysLosingTenPercentADay() {
		BatteryCharge charge = BatteryCharge.full(BatteryTier.TIER_1);
		assertThat(charge.percent(BatteryTier.TIER_1)).isEqualTo(100);
		for (int day = 1; day <= 10; day++) {
			charge = charge.run(BatteryTier.TIER_1, DAY, (long) day * DAY);
			assertThat(charge.percent(BatteryTier.TIER_1)).isEqualTo(100 - 10 * day);
		}
		assertThat(charge.isEmpty(BatteryTier.TIER_1)).isTrue();
		assertThat(charge.run(BatteryTier.TIER_1, DAY, 11L * DAY).ticks()).as("never below empty").isZero();
	}

	@Test
	void tier2HoldsTwiceAsMuch() {
		assertThat(BatteryTier.TIER_2.capacity()).isEqualTo(2 * BatteryTier.TIER_1.capacity());
		BatteryCharge charge = BatteryCharge.full(BatteryTier.TIER_2).run(BatteryTier.TIER_2, DAY, DAY);
		assertThat(charge.percent(BatteryTier.TIER_2)).isEqualTo(95);
	}

	@Test
	void chargingAddsUpToFull() {
		BatteryCharge charge = BatteryCharge.EMPTY.charge(BatteryTier.TIER_1, 6 * DAY);
		assertThat(charge.percent(BatteryTier.TIER_1)).isEqualTo(60);
		assertThat(charge.isFull(BatteryTier.TIER_1)).isFalse();
		charge = charge.charge(BatteryTier.TIER_1, 6 * DAY);
		assertThat(charge.ticks()).isEqualTo(BatteryTier.TIER_1.capacity());
		assertThat(charge.isFull(BatteryTier.TIER_1)).isTrue();
		assertThat(BatteryCharge.EMPTY.charge(BatteryTier.TIER_2, 10 * DAY).percent(BatteryTier.TIER_2))
			.as("the same material fills half a tier 2").isEqualTo(50);
	}

	@Test
	void tier2RegainsOneTickForEveryTickItRests() {
		BatteryCharge charge = new BatteryCharge(1000, 500).rest(BatteryTier.TIER_2, 800);
		assertThat(charge).isEqualTo(new BatteryCharge(1300, 800));
		assertThat(charge.rest(BatteryTier.TIER_2, 800L + 100 * DAY).ticks()).as("up to full").isEqualTo(BatteryTier.TIER_2.capacity());
		// Run a day, rest a day: back where it started.
		BatteryCharge cycled = new BatteryCharge(5 * DAY, 0).run(BatteryTier.TIER_2, DAY, DAY).rest(BatteryTier.TIER_2, 2L * DAY);
		assertThat(cycled.ticks()).isEqualTo(5 * DAY);
	}

	@Test
	void tier1DoesNotRecharge() {
		assertThat(new BatteryCharge(1000, 500).rest(BatteryTier.TIER_1, 800)).isEqualTo(new BatteryCharge(1000, 800));
	}

	@Test
	void aNewBatteryStartsCountingAtTheFirstLook() {
		BatteryCharge charge = BatteryCharge.EMPTY.rest(BatteryTier.TIER_2, 1_000_000);
		assertThat(charge).isEqualTo(new BatteryCharge(0, 1_000_000));
	}

	@Test
	void tier3NeverRunsOut() {
		BatteryCharge charge = BatteryCharge.EMPTY.run(BatteryTier.TIER_3, 1000L * DAY, 1000L * DAY);
		assertThat(charge.isEmpty(BatteryTier.TIER_3)).isFalse();
		assertThat(charge.isFull(BatteryTier.TIER_3)).isTrue();
		assertThat(charge.percent(BatteryTier.TIER_3)).isEqualTo(100);
	}

	@Test
	void anyChargeLeftShowsAsAtLeastOnePercent() {
		assertThat(new BatteryCharge(1, 0).percent(BatteryTier.TIER_1)).isEqualTo(1);
		assertThat(BatteryCharge.EMPTY.percent(BatteryTier.TIER_1)).isZero();
	}
}
