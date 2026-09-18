/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.power.stats;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.BatteryConsumer;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BatteryStatsConfigTest {

    @Test
    public void testDefaults() {
        BatteryStatsConfig config = new BatteryStatsConfig.Builder().build();

        assertThat(config.shouldResetOnUnplugHighBatteryLevel()).isTrue();
        assertThat(config.getHighBatteryLevelAfterCharge()).isEqualTo(90);
        assertThat(config.shouldResetOnUnplugAfterSignificantCharge()).isTrue();
        assertThat(config.getMaxHistorySizeBytes()).isEqualTo(4 * 1024 * 1024);

        // Default throttle periods
        assertThat(config.getPowerStatsThrottlePeriod(BatteryConsumer.powerComponentIdToString(
                BatteryConsumer.POWER_COMPONENT_CPU))).isEqualTo(TimeUnit.MINUTES.toMillis(1));
        assertThat(config.getPowerStatsThrottlePeriod(BatteryConsumer.powerComponentIdToString(
                BatteryConsumer.POWER_COMPONENT_AUDIO))).isEqualTo(TimeUnit.HOURS.toMillis(1));
    }

    @Test
    public void testBuilderOverrides() {
        BatteryStatsConfig config = new BatteryStatsConfig.Builder()
                .setResetOnUnplugHighBatteryLevel(false)
                .setHighBatteryLevelAfterCharge(95)
                .setResetOnUnplugAfterSignificantCharge(false)
                .setMaxHistorySizeBytes(8 * 1024 * 1024)
                .setDefaultPowerStatsThrottlePeriodMillis(TimeUnit.MINUTES.toMillis(30))
                .setPowerStatsThrottlePeriodMillis(BatteryConsumer.powerComponentIdToString(
                        BatteryConsumer.POWER_COMPONENT_VIDEO), TimeUnit.SECONDS.toMillis(30))
                .build();

        assertThat(config.shouldResetOnUnplugHighBatteryLevel()).isFalse();
        assertThat(config.getHighBatteryLevelAfterCharge()).isEqualTo(95);
        assertThat(config.shouldResetOnUnplugAfterSignificantCharge()).isFalse();
        assertThat(config.getMaxHistorySizeBytes()).isEqualTo(8 * 1024 * 1024);

        // Overridden throttle periods
        // Still default
        assertThat(config.getPowerStatsThrottlePeriod(BatteryConsumer.powerComponentIdToString(
                BatteryConsumer.POWER_COMPONENT_CPU))).isEqualTo(TimeUnit.MINUTES.toMillis(1));
        assertThat(config.getPowerStatsThrottlePeriod(BatteryConsumer.powerComponentIdToString(
                BatteryConsumer.POWER_COMPONENT_VIDEO))).isEqualTo(TimeUnit.SECONDS.toMillis(30));
        assertThat(config.getPowerStatsThrottlePeriod(BatteryConsumer.powerComponentIdToString(
                BatteryConsumer.POWER_COMPONENT_AUDIO))).isEqualTo(TimeUnit.MINUTES.toMillis(30));
    }

    @Test
    public void testSetHighBatteryLevelAfterCharge_validValues() {
        // Arrange
        BatteryStatsConfig.Builder builder = new BatteryStatsConfig.Builder();

        // Act
        builder.setHighBatteryLevelAfterCharge(0);
        int value1 = builder.build().getHighBatteryLevelAfterCharge();
        builder.setHighBatteryLevelAfterCharge(100);
        int value2 = builder.build().getHighBatteryLevelAfterCharge();
        builder.setHighBatteryLevelAfterCharge(50);
        int value3 = builder.build().getHighBatteryLevelAfterCharge();

        // Assert
        assertThat(value1).isEqualTo(0);
        assertThat(value2).isEqualTo(100);
        assertThat(value3).isEqualTo(50);
    }

    @Test
    public void testSetHighBatteryLevelAfterCharge_invalidValues() {
        // Arrange
        BatteryStatsConfig.Builder builder = new BatteryStatsConfig.Builder();

        // Act
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class,
                () -> builder.setHighBatteryLevelAfterCharge(-1));
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class,
                () -> builder.setHighBatteryLevelAfterCharge(101));

        // Assert
        assertThat(exception1).hasMessageThat().contains(
                "highBatteryLevelAfterCharge must be between 0 and 100: -1");
        assertThat(exception2).hasMessageThat().contains(
                "highBatteryLevelAfterCharge must be between 0 and 100: 101");
    }

    @Test
    public void testSetDefaultPowerStatsThrottlePeriodMillis_validValues() {
        // Arrange
        BatteryStatsConfig.Builder builder = new BatteryStatsConfig.Builder();
        String otherComponent = BatteryConsumer.powerComponentIdToString(
                BatteryConsumer.POWER_COMPONENT_AUDIO);

        // Act
        builder.setDefaultPowerStatsThrottlePeriodMillis(0);
        long value1 = builder.build().getPowerStatsThrottlePeriod(otherComponent);
        builder.setDefaultPowerStatsThrottlePeriodMillis(1000);
        long value2 = builder.build().getPowerStatsThrottlePeriod(otherComponent);

        // Assert
        assertThat(value1).isEqualTo(0);
        assertThat(value2).isEqualTo(1000);
    }

    @Test
    public void testSetDefaultPowerStatsThrottlePeriodMillis_invalidValues() {
        // Arrange
        BatteryStatsConfig.Builder builder = new BatteryStatsConfig.Builder();

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> builder.setDefaultPowerStatsThrottlePeriodMillis(-1));

        // Assert
        assertThat(exception).hasMessageThat().contains("periodMs cannot be negative: -1");
    }

    @Test
    public void testSetPowerStatsThrottlePeriodMillis_validValues() {
        // Arrange
        BatteryStatsConfig.Builder builder = new BatteryStatsConfig.Builder();
        String component =
                BatteryConsumer.powerComponentIdToString(BatteryConsumer.POWER_COMPONENT_CPU);

        // Act
        builder.setPowerStatsThrottlePeriodMillis(component, 0);
        long value1 = builder.build().getPowerStatsThrottlePeriod(component);
        builder.setPowerStatsThrottlePeriodMillis(component, 1000);
        long value2 = builder.build().getPowerStatsThrottlePeriod(component);

        // Assert
        assertThat(value1).isEqualTo(0);
        assertThat(value2).isEqualTo(1000);
    }

    @Test
    public void testSetPowerStatsThrottlePeriodMillis_invalidValues() {
        // Arrange
        BatteryStatsConfig.Builder builder = new BatteryStatsConfig.Builder();
        String component =
                BatteryConsumer.powerComponentIdToString(BatteryConsumer.POWER_COMPONENT_CPU);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> builder.setPowerStatsThrottlePeriodMillis(component, -1));

        // Assert
        assertThat(exception).hasMessageThat().contains("periodMs cannot be negative: -1");
    }
}
