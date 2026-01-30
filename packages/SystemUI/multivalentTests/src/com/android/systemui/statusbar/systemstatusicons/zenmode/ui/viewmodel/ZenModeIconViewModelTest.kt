/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.zenmode.ui.viewmodel

import android.app.AutomaticZenRule
import android.content.testableContext
import android.graphics.drawable.TestStubDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.SysuiTestCase
import com.android.systemui.SysuiTestableContext
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.policy.data.repository.fakeZenModeRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ZenModeIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private var underTest: ZenModeIconViewModel =
        kosmos.zenModeIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Before
    fun setUp() {
        val customPackageContext = SysuiTestableContext(context)
        context.prepareCreatePackageContext(CUSTOM_PACKAGE_NAME, customPackageContext)
        customPackageContext.orCreateTestableResources.apply {
            addOverride(CUSTOM_ICON_RES_ID, CUSTOM_DRAWABLE)
        }
    }

    @Test
    fun icon_noActiveMode_isNull() =
        kosmos.runTest {
            fakeZenModeRepository.clearModes()

            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_oneActiveMode_showsCorrectIconAndDescription() =
        kosmos.runTest {
            fakeZenModeRepository.clearModes()
            val modeId = "test_mode_1"
            val modeName = "My Zen Mode"
            val mode =
                TestModeBuilder()
                    .setId(modeId)
                    .setName(modeName)
                    .setType(AutomaticZenRule.TYPE_DRIVING)
                    .setActive(false)
                    .build()
            fakeZenModeRepository.addMode(mode)
            fakeZenModeRepository.activateMode(modeId) // Activate it

            assertThat(underTest.icon).isInstanceOf(Icon.Loaded::class.java)

            val loadedIcon = underTest.icon as Icon.Loaded
            assertThat(loadedIcon.contentDescription).isEqualTo(ContentDescription.Loaded(modeName))
            assertThat(loadedIcon.resId).isEqualTo(R.drawable.ic_zen_mode_type_driving)
        }

    @Test
    fun icon_multipleActiveModes_showsHighestPriorityIcon() =
        kosmos.runTest {
            fakeZenModeRepository.clearModes()
            val highPriModeId = "bedtime"
            val highPriModeName = "Bedtime"
            val lowPriModeId = "other"
            val lowPriModeName = "Other Zen"

            val highPriMode =
                TestModeBuilder()
                    .setId(highPriModeId)
                    .setName(highPriModeName)
                    .setType(AutomaticZenRule.TYPE_BEDTIME)
                    .setActive(false)
                    .build()
            val lowPriMode =
                TestModeBuilder()
                    .setId(lowPriModeId)
                    .setName(lowPriModeName)
                    .setType(AutomaticZenRule.TYPE_OTHER) // Lower priority type
                    .setPackage(context.packageName)
                    .setIconResId(R.drawable.ic_zen_mode_type_driving)
                    .setActive(false)
                    .build()

            fakeZenModeRepository.addModes(listOf(highPriMode, lowPriMode))
            // Activate both (order shouldn't matter for the final state)
            fakeZenModeRepository.activateMode(lowPriModeId)
            fakeZenModeRepository.activateMode(highPriModeId)

            // THEN the icon shown corresponds to the highest priority mode (Bedtime)
            val actualIcon = underTest.icon
            assertThat(actualIcon).isInstanceOf(Icon.Loaded::class.java)

            val loadedIcon = actualIcon as Icon.Loaded

            assertThat(loadedIcon.resId).isEqualTo(R.drawable.ic_zen_mode_type_bedtime)
            assertThat(loadedIcon.contentDescription)
                .isEqualTo(ContentDescription.Loaded(highPriModeName))
        }

    @Test
    fun icon_activationChanges_updates() =
        kosmos.runTest {
            fakeZenModeRepository.clearModes()
            val modeId = "update_test"
            val modeName = "Dynamic Zen"
            val mode =
                TestModeBuilder()
                    .setId(modeId)
                    .setName(modeName)
                    .setType(AutomaticZenRule.TYPE_DRIVING)
                    .setActive(false)
                    .build()
            fakeZenModeRepository.addMode(mode)

            assertThat(underTest.icon).isNull()

            fakeZenModeRepository.activateMode(modeId)

            val actualIcon = underTest.icon
            assertThat(actualIcon).isInstanceOf(Icon.Loaded::class.java)
            val loadedIcon = actualIcon as Icon.Loaded
            assertThat(loadedIcon.resId).isEqualTo(R.drawable.ic_zen_mode_type_driving)
            assertThat(loadedIcon.contentDescription).isEqualTo(ContentDescription.Loaded(modeName))

            fakeZenModeRepository.deactivateMode(modeId)
            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_multipleActiveModes_updatesToNextPriorityOnDeactivation_customIcon() =
        kosmos.runTest {
            val highPriModeId = "high_pri_res"
            val highPriModeName = "High Priority Resource"

            val lowPriModeId = "low_pri_custom"
            val lowPriModeName = "Low Priority Custom"

            val highPriMode =
                TestModeBuilder()
                    .setId(highPriModeId)
                    .setName(highPriModeName)
                    .setType(AutomaticZenRule.TYPE_BEDTIME)
                    .setActive(false)
                    .build()
            val lowPriMode =
                TestModeBuilder()
                    .setId(lowPriModeId)
                    .setName(lowPriModeName)
                    .setType(AutomaticZenRule.TYPE_OTHER)
                    .setPackage(CUSTOM_PACKAGE_NAME)
                    .setIconResId(CUSTOM_ICON_RES_ID)
                    .setActive(false)
                    .build()

            kosmos.fakeZenModeRepository.addModes(listOf(highPriMode, lowPriMode))

            kosmos.fakeZenModeRepository.activateMode(lowPriModeId)
            kosmos.fakeZenModeRepository.activateMode(highPriModeId)

            var currentIcon = underTest.icon
            assertThat(currentIcon).isInstanceOf(Icon.Loaded::class.java)
            var loadedIcon = currentIcon as Icon.Loaded
            assertThat(loadedIcon.resId).isEqualTo(R.drawable.ic_zen_mode_type_bedtime)
            assertThat(loadedIcon.contentDescription)
                .isEqualTo(ContentDescription.Loaded(highPriModeName))

            // Deactivate the high priority mode, then the low priority (custom) icon should be
            // shown
            kosmos.fakeZenModeRepository.deactivateMode(highPriModeId)

            currentIcon = underTest.icon
            assertThat(currentIcon).isInstanceOf(Icon.Loaded::class.java)
            loadedIcon = currentIcon as Icon.Loaded

            assertThat(loadedIcon.contentDescription)
                .isEqualTo(ContentDescription.Loaded(lowPriModeName))
            assertThat(loadedIcon.resId).isEqualTo(CUSTOM_ICON_RES_ID)
            assertThat(loadedIcon.packageName).isEqualTo(CUSTOM_PACKAGE_NAME)
            assertThat(loadedIcon.drawable).isEqualTo(CUSTOM_DRAWABLE)
        }

    @Test
    fun visible_activationChanges_flips() =
        kosmos.runTest {
            kosmos.fakeZenModeRepository.clearModes()
            val modeId = "visibility_test_mode"
            val mode = TestModeBuilder().setId(modeId).build()
            kosmos.fakeZenModeRepository.addMode(mode)

            assertThat(underTest.visible).isFalse()

            kosmos.fakeZenModeRepository.activateMode(modeId)
            assertThat(underTest.visible).isTrue()

            kosmos.fakeZenModeRepository.deactivateMode(modeId)
            assertThat(underTest.visible).isFalse()
        }

    private companion object {
        const val CUSTOM_PACKAGE_NAME = "com.example.custom.zen.mode.provider"
        const val CUSTOM_ICON_RES_ID = 54321 // Arbitrary ID for the custom resource
        val CUSTOM_DRAWABLE = TestStubDrawable("custom_zen_icon")
    }
}
