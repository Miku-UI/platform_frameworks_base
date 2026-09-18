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

package com.android.systemui.accessibility

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.systemui.util.settings.SecureSettings
import java.util.StringJoiner
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class TestUtils {
    companion object {
        private fun getShortcutTargets(components: Set<ComponentName>): String {
            val stringJoiner = StringJoiner(ShortcutConstants.SERVICES_SEPARATOR.toString())
            for (target in components) {
                stringJoiner.add(target.flattenToString())
            }
            return stringJoiner.toString()
        }

        /**
         * Returns a mock secure settings configured to return information needed for tests.
         * Currently, this only includes button targets.
         */
        fun mockSecureSettings(context: Context): SecureSettings {
            val secureSettings = mock(SecureSettings::class.java)
            whenever(secureSettings.getRealUserHandle(UserHandle.USER_CURRENT))
                .thenReturn(context.getUserId())

            val targets = getShortcutTargets(setOf(TEST_COMPONENT_A, TEST_COMPONENT_B))
            whenever(
                    secureSettings.getStringForUser(
                        Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
                        UserHandle.USER_CURRENT,
                    )
                )
                .thenReturn(targets)

            return secureSettings
        }

        val TEST_COMPONENT_A: ComponentName = ComponentName("pkg", "A")
        val TEST_COMPONENT_B: ComponentName = ComponentName("pkg", "B")
    }
}
