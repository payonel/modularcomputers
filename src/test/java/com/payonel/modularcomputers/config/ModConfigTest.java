package com.payonel.modularcomputers.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.BatteryTier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
	@TempDir
	Path dir;

	@AfterEach
	void restoreDefaults() {
		for (BatteryTier tier : BatteryTier.values()) {
			tier.setDays(tier.defaultDays());
		}
	}

	@Test
	void writesTheDefaultsTheFirstTime() throws IOException {
		Path file = dir.resolve("config").resolve(ModConfig.FILE_NAME);
		ModConfig.load(file);
		assertThat(Files.readString(file)).contains("battery_tier1_duration: 10", "battery_tier2_duration: 20", "battery_tier3_duration: 0");
		assertThat(BatteryTier.TIER_1.days()).isEqualTo(10);
		assertThat(BatteryTier.TIER_3.infinite()).isTrue();
	}

	@Test
	void readsKeyColonValueLinesWithFractionsAndZeroForever() throws IOException {
		Path file = dir.resolve(ModConfig.FILE_NAME);
		Files.writeString(file, "battery_tier1_duration: 0.5\nbattery_tier2_duration: 1\nbattery_tier3_duration: 0\n");
		ModConfig.load(file);
		assertThat(BatteryTier.TIER_1.capacity()).isEqualTo(BatteryCharge.DAY_TICKS / 2);
		assertThat(BatteryTier.TIER_2.capacity()).isEqualTo(BatteryCharge.DAY_TICKS);
		assertThat(BatteryTier.TIER_3.infinite()).isTrue();
		Files.writeString(file, "battery_tier1_duration: 0\nbattery_tier3_duration: 3\n");
		ModConfig.load(file);
		assertThat(BatteryTier.TIER_1.infinite()).as("any tier can be set to last forever").isTrue();
		assertThat(BatteryTier.TIER_2.days()).as("missing: back to the default").isEqualTo(20);
		assertThat(BatteryTier.TIER_3.infinite()).isFalse();
		assertThat(BatteryTier.TIER_3.capacity()).isEqualTo(3 * BatteryCharge.DAY_TICKS);
	}

	@Test
	void badValuesFallBackToTheDefaults() throws IOException {
		Path file = dir.resolve(ModConfig.FILE_NAME);
		Files.writeString(file, "battery_tier1_duration: lots\nbattery_tier2_duration: -4\n");
		ModConfig.load(file);
		assertThat(BatteryTier.TIER_1.days()).isEqualTo(10);
		assertThat(BatteryTier.TIER_2.days()).isEqualTo(20);
	}
}
