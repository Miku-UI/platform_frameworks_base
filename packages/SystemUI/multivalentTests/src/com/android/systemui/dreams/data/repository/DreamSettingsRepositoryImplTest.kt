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

package com.android.systemui.dreams.data.repository

import android.content.pm.UserInfo
import android.content.res.mainResources
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.shared.model.WhenToDream
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamSettingsRepositoryImplTest : SysuiTestCase() {
    private val kosmos =
        testKosmos()
            .apply { mainResources = mContext.orCreateTestableResources.resources }
            .useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { dreamSettingsRepository }

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsEnabledByDefault,
            true,
        )

        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault,
            false,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault,
            false,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault,
            false,
        )
    }

    @After
    fun tearDown() {
        mContext.orCreateTestableResources.removeOverride(
            com.android.internal.R.bool.config_dreamsEnabledByDefault
        )
        mContext.orCreateTestableResources.removeOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault
        )
        mContext.orCreateTestableResources.removeOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault
        )
        mContext.orCreateTestableResources.removeOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault
        )
    }

    @Test
    fun whenToDream_charging() =
        kosmos.runTest {
            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())

            kosmos.userAwareSecureSettingsRepository.setInt(
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                1,
            )

            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_CHARGING)
        }

    @Test
    fun whenToDream_charging_defaultValue() =
        kosmos.runTest {
            mContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault,
                true,
            )

            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())
            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_CHARGING)
        }

    @Test
    fun whenToDream_docked() =
        kosmos.runTest {
            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())

            kosmos.userAwareSecureSettingsRepository.setInt(
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                1,
            )

            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_DOCKED)
        }

    @Test
    fun whenToDream_docked_defaultValue() =
        kosmos.runTest {
            mContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault,
                true,
            )

            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())
            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_DOCKED)
        }

    @Test
    fun whenToDream_postured() =
        kosmos.runTest {
            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())

            kosmos.userAwareSecureSettingsRepository.setInt(
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
                1,
            )

            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_POSTURED)
        }

    @Test
    fun whenToDream_postured_defaultValue() =
        kosmos.runTest {
            mContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault,
                true,
            )

            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())
            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_POSTURED)
        }

    @Test
    fun whenToDream_overriddenByEnabled() =
        kosmos.runTest {
            mContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault,
                true,
            )

            kosmos.userAwareSecureSettingsRepository.setBoolean(
                Settings.Secure.SCREENSAVER_ENABLED,
                false,
            )

            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())

            assertThat(whenToDreamState).isEqualTo(WhenToDream.NEVER)

            kosmos.userAwareSecureSettingsRepository.setBoolean(
                Settings.Secure.SCREENSAVER_ENABLED,
                true,
            )

            assertThat(whenToDreamState).isEqualTo(WhenToDream.WHILE_POSTURED)
        }

    @Test
    fun whenToDream_default() =
        kosmos.runTest {
            val whenToDreamState by collectLastValue(underTest.getWhenToDreamState())
            assertThat(whenToDreamState).isEqualTo(WhenToDream.NEVER)
        }

    private companion object {
        val PRIMARY_USER =
            UserInfo(/* id= */ 0, /* name= */ "primary user", /* flags= */ UserInfo.FLAG_MAIN)
    }
}
