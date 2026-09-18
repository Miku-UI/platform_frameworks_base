/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.display.config

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableContext
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.android.server.display.feature.flags.Flags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WorkDurationsConfigLoaderTest {
    @get:Rule
    val mContext: TestableContext = TestableContext(
        InstrumentationRegistry.getInstrumentation().getContext()
    )

    @get:Rule
    val setFlagRule = SetFlagsRule()

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun displayConfiguration_workDurationsPresentEnabled_parsesWorkDurations() {
        val config: DisplayConfiguration = createDisplayConfiguration {

            thermalThrottling {
                workDurationsThrottlingMap {
                    workDurationsThrottlingPair {
                        thermalStatus(ThermalStatusEnum.NONE)
                        thermalThrottlingWorkDurations("11500000", "17600000", "18600000")
                    }
                }
            }

            refreshRateConfigs {
                defaultWorkDurations("10500000", "16600000", "16600000")
                lowPowerWorkDurations("9500000", "15600000", "15600000")
            }
        }
        val thermalThrottlingData = ThermalThrottlingData()
        thermalThrottlingData.loadThermalThrottlingConfig(config)
        val thermalDurationsMap = thermalThrottlingData.thermalThrottlingWorkDurations

        assertThat(thermalDurationsMap).isNotNull()
        assertThat(thermalDurationsMap).hasSize(1)
        val thermalDurationsPairs = thermalDurationsMap["default"] // default id

        assertThat(thermalDurationsPairs).isNotNull()
        val thermalDurations = thermalDurationsPairs?.get(ThermalStatusEnum.NONE.toPowerManagerConstant())
        val lowPowerDurations = WorkDurationsConfigLoader.loadLowPowerWorkDurations(config.refreshRate)
        val defaultDurations = WorkDurationsConfigLoader.loadDefaultWorkDurations(config.refreshRate)

        // Assert Default Work Durations
        assertThat(defaultDurations).isNotNull()
        assertThat(defaultDurations?.minSfDurationNanos).isEqualTo(10500000)
        assertThat(defaultDurations?.maxSfDurationNanos).isEqualTo(16600000)
        assertThat(defaultDurations?.appDurationNanos).isEqualTo(16600000)

        // Assert Thermal Throttling Work Durations
        assertThat(thermalDurations).isNotNull()
        assertThat(thermalDurations?.minSfDurationNanos).isEqualTo(11500000)
        assertThat(thermalDurations?.maxSfDurationNanos).isEqualTo(17600000)
        assertThat(thermalDurations?.appDurationNanos).isEqualTo(18600000)

        // Assert Low Power Work Durations
        assertThat(lowPowerDurations).isNotNull()
        assertThat(lowPowerDurations?.minSfDurationNanos).isEqualTo(9500000)
        assertThat(lowPowerDurations?.maxSfDurationNanos).isEqualTo(15600000)
        assertThat(lowPowerDurations?.appDurationNanos).isEqualTo(15600000)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun displayConfiguration_handlesMissingWorkDurations() {
        val config: DisplayConfiguration = createDisplayConfiguration {
            thermalThrottling { /* No work durations */ }

            refreshRateConfigs { /* No work durations */ }
        }

        val thermalThrottlingData = ThermalThrottlingData()
        thermalThrottlingData.loadThermalThrottlingConfig(config)
        val thermalDurationsMap = thermalThrottlingData.thermalThrottlingWorkDurations
        val lowPowerDurations = WorkDurationsConfigLoader.loadLowPowerWorkDurations(config.refreshRate)
        val defaultDurations = WorkDurationsConfigLoader.loadDefaultWorkDurations(config.refreshRate)

        assertThat(thermalDurationsMap).isEmpty()
        assertThat(lowPowerDurations).isNull()
        assertThat(defaultDurations).isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun displayConfiguration_workDurationsDisabled() {
        val config: DisplayConfiguration = createDisplayConfiguration {

            thermalThrottling {
                workDurationsThrottlingMap {
                    workDurationsThrottlingPair {
                        thermalStatus(ThermalStatusEnum.NONE)
                        thermalThrottlingWorkDurations("11500000", "17600000", "18600000")
                    }
                }
            }

            refreshRateConfigs {
                defaultWorkDurations("10500000", "16600000", "16600000")
                lowPowerWorkDurations("9500000", "15600000", "15600000")
            }
        }

        val thermalThrottlingData = ThermalThrottlingData()
        thermalThrottlingData.loadThermalThrottlingConfig(config)
        val thermalDurationsMap = thermalThrottlingData.thermalThrottlingWorkDurations
        val defaultDurations = WorkDurationsConfigLoader.loadDefaultWorkDurations(config.refreshRate)
        val lowPowerDurations = WorkDurationsConfigLoader.loadLowPowerWorkDurations(config.refreshRate)

        assertThat(thermalDurationsMap).isEmpty()
        assertThat(lowPowerDurations).isNull()
        assertThat(defaultDurations).isNull()
    }
}
