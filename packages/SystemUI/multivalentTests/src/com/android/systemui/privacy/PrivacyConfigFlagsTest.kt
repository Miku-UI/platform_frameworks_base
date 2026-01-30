/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.location.flags.Flags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.FlagsParameterization.allCombinationsOf
import android.provider.DeviceConfig
import androidx.test.filters.SmallTest
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.util.DeviceConfigProxy
import com.android.systemui.util.DeviceConfigProxyFake
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class PrivacyConfigFlagsTest(flags: FlagsParameterization) : SysuiTestCase() {
    companion object {
        private const val MIC_CAMERA = SystemUiDeviceConfigFlags.PROPERTY_MIC_CAMERA_ENABLED
        private const val MEDIA_PROJECTION =
            SystemUiDeviceConfigFlags.PROPERTY_MEDIA_PROJECTION_INDICATORS_ENABLED

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return allCombinationsOf(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

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
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun testMicCameraListeningByDefault() {
        assertTrue(privacyConfig.micCameraAvailable)
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun testMicCameraChanged() {
        changeMicCamera(false) // default is true
        executor.runAllReady()

        verify(callback).onFlagMicCameraChanged(false)

        assertFalse(privacyConfig.micCameraAvailable)
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun testMediaProjectionChanged() {
        changeMediaProjection(false) // default is true
        executor.runAllReady()

        verify(callback).onFlagMediaProjectionChanged(false)

        assertFalse(privacyConfig.mediaProjectionAvailable)
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun testLocationChanged() {
        changeMicCamera(true)
        executor.runAllReady()

        verify(callback).onFlagLocationChanged(true)
        assertTrue(privacyConfig.locationAvailable)
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun testMicCamAndLocationChanged() {
        changeMicCamera(false)
        executor.runAllReady()

        verify(callback, atLeastOnce()).onFlagLocationChanged(true)
        verify(callback, atLeastOnce()).onFlagMicCameraChanged(false)

        assertTrue(privacyConfig.locationAvailable)
        assertFalse(privacyConfig.micCameraAvailable)
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun testMicDeleted_stillAvailable() {
        changeMicCamera(true)
        executor.runAllReady()
        changeMicCamera(null)
        executor.runAllReady()

        verify(callback, never()).onFlagMicCameraChanged(false)
        assertTrue(privacyConfig.micCameraAvailable)
    }

    private fun changeMicCamera(value: Boolean?) = changeProperty(MIC_CAMERA, value)

    private fun changeMediaProjection(value: Boolean?) = changeProperty(MEDIA_PROJECTION, value)

    private fun changeProperty(name: String, value: Boolean?) {
        deviceConfigProxy.setProperty(
            DeviceConfig.NAMESPACE_PRIVACY,
            name,
            value?.toString(),
            false,
        )
    }
}
