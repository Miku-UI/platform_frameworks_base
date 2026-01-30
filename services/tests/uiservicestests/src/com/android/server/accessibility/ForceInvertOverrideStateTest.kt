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
 * limitations under the License
 */

package com.android.server.accessibility

import android.app.UiModeManager
import android.provider.Settings
import android.testing.TestableContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compatibility.common.util.ShellIdentityUtils
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val BLOCKED_PACKAGE = "blocked.package"
private const val ENABLED_PACKAGE = "enabled.package"
private const val DISABLED_PACKAGE = "disabled.package"
private const val OTHER_PACKAGE = "other.package"

@SmallTest
@RunWith(AndroidJUnit4::class)
class ForceInvertOverrideStateTest {

    private val context = TestableContext(ApplicationProvider.getApplicationContext())

    @Before
    fun setup() {
        context.getOrCreateTestableResources().addOverride(
            com.android.internal.R.array.config_forceInvertPackageBlocklist,
            arrayOf(BLOCKED_PACKAGE)
        )
    }

    @After
    fun teardown() {
        Settings.System.resetToDefaults(context.contentResolver, /* tag= */ null)
    }

    @Test
    fun normalPackage_allowed() {
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(OTHER_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED)
    }

    @Test
    fun disabledPackage_returnsDisable() {
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE,
            DISABLED_PACKAGE
        )
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(DISABLED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE)
    }

    @Test
    fun enabledPackage_returnsEnable() {
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
            ENABLED_PACKAGE
        )
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(ENABLED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE)
    }

    @Test
    fun differentUser_notAffected() {
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
            ENABLED_PACKAGE,
            context.userId + 1
        )
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(ENABLED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED)
    }

    @Test
    fun blockListedPackage_returnsDisable() {
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(BLOCKED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE)
    }

    @Test
    fun blockListedPackage_overrideEnabled_returnsEnable() {
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
            BLOCKED_PACKAGE
        )
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(BLOCKED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE)
    }

    @Test
    fun bothLists_disableTakesPrecedence() {
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
            ENABLED_PACKAGE
        )
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE,
            ENABLED_PACKAGE
        )
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(ENABLED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE)
    }

    @Test
    fun multiplePackages_parsesCsv() {
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
            "$ENABLED_PACKAGE,$OTHER_PACKAGE"
        )
        putSystemSetting(
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE,
            ",$DISABLED_PACKAGE,"
        )
        val state = ForceInvertOverrideState.loadFrom(context, context.userId)

        assertThat(state.getStateForPackage(ENABLED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE)
        assertThat(state.getStateForPackage(OTHER_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE)
        assertThat(state.getStateForPackage(DISABLED_PACKAGE))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE)
        assertThat(state.getStateForPackage("random.package"))
            .isEqualTo(UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED)
    }

    private fun putSystemSetting(name: String, value: String, userId: Int = context.userId) {
        ShellIdentityUtils.invokeWithShellPermissions {
            Settings.System.putStringForUser(
                context.contentResolver,
                name,
                value,
                userId
            )
        }
    }
}
