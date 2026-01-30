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

package com.android.systemui.privacy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.res.R
import com.android.systemui.util.DeviceConfigProxy
import com.android.systemui.util.DeviceConfigProxyFake
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class PrivacyConfigTest : SysuiTestCase() {

    private lateinit var privacyConfig: PrivacyConfig

    @Mock private lateinit var callback: PrivacyConfig.Callback
    @Mock private lateinit var dumpManager: DumpManager

    private lateinit var executor: FakeExecutor
    private lateinit var deviceConfigProxy: DeviceConfigProxy

    fun createPrivacyConfig(): PrivacyConfig {
        return PrivacyConfig(executor, deviceConfigProxy, dumpManager)
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        executor = FakeExecutor(FakeSystemClock())
        deviceConfigProxy = DeviceConfigProxyFake()

        privacyConfig = createPrivacyConfig()
        privacyConfig.addCallback(callback)

        executor.runAllReady()
    }

    @Test
    fun getPrivacyColor_locationOnly_returnsLocationOnlyColor() {
        assertEquals(
            R.color.privacy_chip_location_only_background,
            PrivacyConfig.Companion.getPrivacyColor(locationOnly = true),
        )
    }

    @Test
    fun getPrivacyColor_multiplePrivacyItems_returnsDefaultPrivacyColor() {
        assertEquals(
            R.color.privacy_chip_background,
            PrivacyConfig.Companion.getPrivacyColor(locationOnly = false),
        )
    }

    @Test
    fun privacyItemsAreLocationOnly_locationOnly_returnsTrue() {
        assertTrue(
            PrivacyConfig.Companion.privacyItemsAreLocationOnly(
                listOf(PrivacyItem(PrivacyType.TYPE_LOCATION, PrivacyApplication("app", 1)))
            )
        )
    }

    @Test
    fun privacyItemsAreLocationOnly_multiplePrivacyItems_returnsFalse() {
        assertFalse(
            PrivacyConfig.Companion.privacyItemsAreLocationOnly(
                listOf(
                    PrivacyItem(PrivacyType.TYPE_CAMERA, PrivacyApplication("app", 1)),
                    PrivacyItem(PrivacyType.TYPE_LOCATION, PrivacyApplication("app", 1)),
                )
            )
        )
    }
}
