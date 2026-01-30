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

package com.android.settingslib.devicestate

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_FOLDED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_HALF_FOLDED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_UNFOLDED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_UNKNOWN
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSettingListener
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import java.util.concurrent.Executor
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceStateAutoRotateSettingManagerImplTest {
    @get:Rule
    val rule = MockitoJUnit.rule()

    private val fakeSecureSettings = FakeSecureSettings()
    private val executor: Executor = Executor { it.run() }
    private val configPerDeviceStateRotationLockDefaults = arrayOf(
        "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:" +
                "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                "$DEVICE_STATE_ROTATION_KEY_UNFOLDED",
        "$DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY:" +
                "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                "$DEVICE_STATE_ROTATION_KEY_FOLDED",
        "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED",
        "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
    )

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockContentResolver: ContentResolver

    @Mock
    private lateinit var mMockPostureDeviceStateConverter: PostureDeviceStateConverter

    @Mock
    private lateinit var mockHandler: Handler

    @Mock
    private lateinit var mockDeviceStateManager: DeviceStateManager

    @Mock
    private lateinit var mockResources: Resources
    private lateinit var settingManager: DeviceStateAutoRotateSettingManagerImpl

    @Before
    fun setUp() {
        whenever(mockContext.contentResolver).thenReturn(mockContentResolver)
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(configPerDeviceStateRotationLockDefaults)
        whenever(mockHandler.post(any(Runnable::class.java))).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable
            runnable.run()
            null
        }
        whenever(mockContext.getSystemService(DeviceStateManager::class.java))
            .thenReturn(mockDeviceStateManager)
        setUpMockPostureHelper()

        settingManager =
            DeviceStateAutoRotateSettingManagerImpl(
                mockContext,
                executor,
                fakeSecureSettings,
                mockHandler,
                mMockPostureDeviceStateConverter,
            )
    }

    @After
    fun tearDown() {
        // Reset to default
        persistSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)
    }

    @Test
    fun registerListener_onSettingsChanged_listenerNotified() {
        val listener = mock(DeviceStateAutoRotateSettingListener::class.java)
        settingManager.registerListener(listener)

        persistSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        verify(listener).onSettingsChanged()
    }

    @Test
    fun registerMultipleListeners_onSettingsChanged_allListenersNotified() {
        val listener1 = mock(DeviceStateAutoRotateSettingListener::class.java)
        val listener2 = mock(DeviceStateAutoRotateSettingListener::class.java)
        settingManager.registerListener(listener1)
        settingManager.registerListener(listener2)

        persistSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        verify(listener1).onSettingsChanged()
        verify(listener2).onSettingsChanged()
    }

    @Test
    fun unregisterListener_onSettingsChanged_listenerNotNotified() {
        val listener = mock(DeviceStateAutoRotateSettingListener::class.java)
        settingManager.registerListener(listener)
        settingManager.unregisterListener(listener)

        persistSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        verify(listener, never()).onSettingsChanged()
    }

    @Test
    fun getAutoRotateSetting_offForUnfolded_returnsOff() {
        persistSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_UNFOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_LOCKED)
    }

    @Test
    fun getAutoRotateSetting_onForFolded_returnsOn() {
        persistSetting(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_UNLOCKED)
    }

    @Test
    fun getAutoRotateSetting_forInvalidPostureWithNoFallback_throwsException() {
        persistSetting(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            settingManager.getRotationLockSetting(DEVICE_STATE_INVALID)
        }

        assertThat(exception.message).contains(
            "Trying to get auto rotate value of invalid device posture: "
                    + "$DEVICE_STATE_ROTATION_KEY_UNKNOWN"
        )
    }

    @Test
    fun getAutoRotateSetting_forIgnoredPosture_returnsSettingForFallbackPosture() {
        persistSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_HALF_FOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_UNLOCKED)
    }

    @Test
    fun getAutoRotateSetting_invalidFormat_returnsNull() {
        persistSetting("invalid_format")

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)

        assertThat(autoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSetting_invalidNumberFormat_returnsNull() {
        persistSetting(INVALID_AUTO_ROTATE_VALUE_FOR_FOLDED_SETTING)

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)

        assertThat(autoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSetting_multipleSettings_returnsCorrectSetting() {
        persistSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        val foldedSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)
        val unfoldedSetting = settingManager.getRotationLockSetting(DEVICE_STATE_UNFOLDED)

        assertThat(foldedSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_UNLOCKED)
        assertThat(unfoldedSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_LOCKED)
    }

    @Test
    fun getAutoRotateSetting_missingDefaultPostureInPersistedSetting_returnsNull() {
        persistSetting(MISSING_FOLDED_KEY_AND_VALUE_SETTING)

        val unfoldedSetting = settingManager.getRotationLockSetting(
            DEVICE_STATE_ROTATION_KEY_UNFOLDED
        )

        assertThat(unfoldedSetting).isNull()
    }

    @Test
    fun getAutoRotateSetting_ignoredPostureHasNonZeroValue_returnsNull() {
        persistSetting(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_RESOLVED)

        val unfoldedSetting =
            settingManager.getRotationLockSetting(DEVICE_STATE_ROTATION_KEY_UNFOLDED)

        assertThat(unfoldedSetting).isNull()
    }

    @Test
    fun isAutoRotateOff_offForUnfolded_returnsTrue() {
        persistSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        val isAutoRotateOff = settingManager.isRotationLocked(DEVICE_STATE_UNFOLDED)

        assertThat(isAutoRotateOff).isTrue()
    }

    @Test
    fun isRotationLockedForAllStates_allStatesLocked_returnsTrue() {
        persistSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        val isRotationLockedForAllStates = settingManager.isRotationLockedForAllStates()

        assertThat(isRotationLockedForAllStates).isTrue()
    }

    @Test
    fun isRotationLockedForAllStates_someStatesLocked_returnsFalse() {
        persistSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)

        val isRotationLockedForAllStates = settingManager.isRotationLockedForAllStates()

        assertThat(isRotationLockedForAllStates).isFalse()
    }

    @Test
    fun isRotationLockedForAllStates_noStatesLocked_returnsFalse() {
        persistSetting(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)

        val isRotationLockedForAllStates = settingManager.isRotationLockedForAllStates()

        assertThat(isRotationLockedForAllStates).isFalse()
    }

    @Test
    fun getSettableDeviceStates_returnsExpectedValuesInOriginalOrder() {
        val settableDeviceStates = settingManager.getSettableDeviceStates()

        assertThat(settableDeviceStates)
            .containsExactly(
                SettableDeviceState(DEVICE_STATE_UNFOLDED, /* isSettable = */ true),
                SettableDeviceState(DEVICE_STATE_FOLDED, /* isSettable = */ true),
                SettableDeviceState(DEVICE_STATE_HALF_FOLDED, /* isSettable = */ false),
                SettableDeviceState(DEVICE_STATE_REAR_DISPLAY, /* isSettable = */ false),
            )
    }

    @Test
    fun getRotationLockSettingMap_multipleSettings_returnsCorrectMap() {
        persistSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)
        val expectedPairs = getDefaultSettingResolvedMap()
        expectedPairs[DEVICE_STATE_ROTATION_KEY_UNFOLDED] = false
        expectedPairs[DEVICE_STATE_ROTATION_KEY_FOLDED] = true
        expectedPairs[DEVICE_STATE_ROTATION_KEY_HALF_FOLDED] = false
        expectedPairs[DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY] = true

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNotNull()
        // Check if all expected pairs are present
        expectedPairs.forEach { (key, value) ->
            assertThat(value).isEqualTo(
                deviceStateAutoRotateSetting?.get(key)
            )
        }
    }

    @Test
    fun getAutoRotateSettingMap_missingDefaultPostureInPersistedSetting_returnsNull() {
        persistSetting(MISSING_FOLDED_KEY_AND_VALUE_SETTING)

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSettingMap_ignoredPostureHasNonZeroValue_returnsNull() {
        persistSetting(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_RESOLVED)

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSettingMap_invalidNumberOfElementsInPersistedSetting_returnsNull() {
        persistSetting(MISSING_VALUE_FOR_FOLDED_SETTING)

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSettingMap_nonIntegerCharacterInPersistedSetting_returnsNull() {
        persistSetting(NON_INTEGER_CHARACTER_IN_AUTO_ROTATE_VALUE_SETTING)

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSettingMap_emptyPersistedSetting_returnsNull() {
        persistSetting("")

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSettingMap_missingFoldedKeyValueInPersistedSetting_sizeOfPersistedSettingMatchDefault_returnsNull() {
        persistSetting(OPEN_UNLOCKED_INVALID_LOCKED_SETTING)

        val deviceStateAutoRotateSetting = settingManager.rotationLockSetting

        assertThat(deviceStateAutoRotateSetting).isNull()
    }

    @Test
    fun getAutoRotateSettingMapTwice_editOneOfThem_noChangesToOtherObject() {
        persistSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING_UNRESOLVED)
        val deviceStateAutoRotateSetting1 = settingManager.rotationLockSetting
        val deviceStateAutoRotateSetting2 = settingManager.rotationLockSetting
        assertThat(deviceStateAutoRotateSetting1).isNotNull()
        assertThat(deviceStateAutoRotateSetting2).isNotNull()

        deviceStateAutoRotateSetting1?.set(
            DEVICE_STATE_ROTATION_KEY_FOLDED,
            DEVICE_STATE_ROTATION_LOCK_LOCKED
        )
        val autoRotateValue = deviceStateAutoRotateSetting2?.get(DEVICE_STATE_ROTATION_KEY_FOLDED)

        assertThat(autoRotateValue).isTrue()
    }

    @Test
    fun getDefaultRotationLockSetting_returnsResolvedDefaultsFromConfig() {
        val expectedPairs = getDefaultSettingResolvedMap()

        val defaultDeviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting

        // Check if all expected pairs are present
        expectedPairs.forEach { (key, value) ->
            assertThat(value).isEqualTo(
                defaultDeviceStateAutoRotateSetting.get(key)
            )
        }
    }

    @Test
    fun settingSetGet_setValidDevicePosture_getsCorrespondingSettingValue() {
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_FOLDED,
            DEVICE_STATE_ROTATION_LOCK_UNLOCKED
        )

        val autoRotate = deviceStateAutoRotateSetting.get(DEVICE_STATE_ROTATION_KEY_FOLDED)

        assertThat(autoRotate).isTrue()
    }

    @Test
    fun settingSetGet_setValidDevicePosture_getResolvedValueForIgnoredDevicePosture() {
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_UNFOLDED,
            DEVICE_STATE_ROTATION_LOCK_LOCKED
        )

        val autoRotate = deviceStateAutoRotateSetting.get(DEVICE_STATE_ROTATION_KEY_HALF_FOLDED)

        assertThat(autoRotate).isFalse()
    }

    @Test
    fun settingSetGet_setValidDevicePostureTwice_getsLatestSettingValue() {
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_UNFOLDED,
            DEVICE_STATE_ROTATION_LOCK_LOCKED
        )
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_UNFOLDED,
            DEVICE_STATE_ROTATION_LOCK_UNLOCKED
        )

        val autoRotate = deviceStateAutoRotateSetting.get(DEVICE_STATE_ROTATION_KEY_UNFOLDED)

        assertThat(autoRotate).isTrue()
    }

    @Test
    fun settingGet_getInvalidDevicePosture_throwsException() {
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting

        val exception = assertThrows(IllegalArgumentException::class.java) {
            deviceStateAutoRotateSetting.get(DEVICE_STATE_ROTATION_KEY_UNKNOWN)
        }

        assertThat(exception.message).isEqualTo(
            "Trying to get auto rotate value of " +
                    "invalid device posture: $DEVICE_STATE_ROTATION_KEY_UNKNOWN"
        )
    }

    @Test
    fun settingSet_setInvalidDevicePosture_throwsException() {
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting

        val exception = assertThrows(IllegalArgumentException::class.java) {
            deviceStateAutoRotateSetting.set(
                DEVICE_STATE_ROTATION_KEY_UNKNOWN,
                DEVICE_STATE_ROTATION_LOCK_LOCKED
            )
        }

        assertThat(exception.message).isEqualTo(
            "Trying to set auto rotate value of " +
                    "invalid device posture: $DEVICE_STATE_ROTATION_KEY_UNKNOWN"
        )
    }

    @Test
    fun settingSet_setInvalidAutoRotateSettingValue_throwsException() {
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting

        val exception = assertThrows(IllegalArgumentException::class.java) {
            deviceStateAutoRotateSetting.set(DEVICE_STATE_ROTATION_KEY_FOLDED, /* autoRotate = */4)
        }

        assertThat(exception.message).isEqualTo("Trying to set invalid auto rotate value: 4")
    }

    @Test
    fun settingWrite_withOneUpdatedSetting_setsValueFromMap() {
        persistSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_FOLDED,
            DEVICE_STATE_ROTATION_LOCK_UNLOCKED
        )

        deviceStateAutoRotateSetting.write()

        val expectedPairs = FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED
        val persistedSetting = getPersistedDeviceStateAutoRotateSetting()
        assertThat(persistedSetting).isEqualTo(expectedPairs)
    }

    @Test
    fun settingWrite_withTwoUpdatedSettings_setsValueFromMap() {
        persistSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_FOLDED,
            DEVICE_STATE_ROTATION_LOCK_UNLOCKED
        )
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_UNFOLDED,
            DEVICE_STATE_ROTATION_LOCK_LOCKED
        )

        deviceStateAutoRotateSetting.write()

        val expectedPairs = FOLDED_UNLOCKED_OPEN_LOCKED_SETTING_UNRESOLVED
        val persistedSetting = getPersistedDeviceStateAutoRotateSetting()
        assertThat(persistedSetting).isEqualTo(expectedPairs)
    }

    @Test
    fun settingWrite_settingValueForIgnoredChanged_setUpdatedValueToAssociatedFallback() {
        persistSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED)
        val deviceStateAutoRotateSetting = settingManager.defaultRotationLockSetting
        deviceStateAutoRotateSetting.set(
            DEVICE_STATE_ROTATION_KEY_HALF_FOLDED,
            DEVICE_STATE_ROTATION_LOCK_LOCKED
        )

        deviceStateAutoRotateSetting.write()

        val expectedPairs = FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED
        val persistedSetting = getPersistedDeviceStateAutoRotateSetting()
        assertThat(persistedSetting).isEqualTo(expectedPairs)
    }

    @Test
    fun loadAutoRotateDeviceStates_missingDeviceStateForPosture_throwsException() {
        whenever(
            mMockPostureDeviceStateConverter.postureToDeviceState(
                eq(
                    DEVICE_STATE_ROTATION_KEY_UNFOLDED
                )
            )
        ).thenReturn(null)

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "No matching device state for posture: "
                    + "$DEVICE_STATE_ROTATION_KEY_UNFOLDED"
        )
    }

    @Test
    fun loadAutoRotateDeviceStates_missingFallbackPosture_throwsException() {
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(
                arrayOf(
                    "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:$DEVICE_STATE_ROTATION_LOCK_IGNORED",
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED"
                )
            )

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "Auto rotate setting is IGNORED for posture=" + DEVICE_STATE_ROTATION_KEY_HALF_FOLDED
                    + ", but no fallback-posture defined"
        )
    }

    @Test
    fun loadAutoRotateDeviceStates_invalidNumberOfElementsInEntry_throwsException() {
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(
                arrayOf(
                    "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED",
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
                    "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED"
                )
            )

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "Invalid number of values in entry: $DEVICE_STATE_ROTATION_KEY_HALF_FOLDED"
        )
    }

    @Test
    fun loadAutoRotateDeviceStates_invalidNumberFormatInEntry_throwsException() {
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(
                arrayOf(
                    "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:two",
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
                    "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED"
                )
            )

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "Invalid number format in '$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:two'"
        )
    }

    @Test
    fun loadAutoRotateDeviceStates_invalidDevicePosture_throwsException() {
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(
                arrayOf(
                    "$DEVICE_STATE_ROTATION_KEY_UNKNOWN:" +
                            "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                            "$DEVICE_STATE_ROTATION_KEY_UNFOLDED",
                    "$DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY:" +
                            "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                            "$DEVICE_STATE_ROTATION_KEY_FOLDED",
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED",
                    "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
                )
            )

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "Corrupted auto-rotate config. One or more values among " + "devicePosture="
                    + DEVICE_STATE_ROTATION_KEY_UNKNOWN + " autoRotateValue=" + DEVICE_STATE_ROTATION_LOCK_IGNORED + " are invalid"
        )
    }

    @Test
    fun loadAutoRotateDeviceStates_invalidAutoRotateValue_throwsException() {
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(
                arrayOf(
                    "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:" +
                            "3:" +
                            "$DEVICE_STATE_ROTATION_KEY_UNFOLDED",
                    "$DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY:" +
                            "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                            "$DEVICE_STATE_ROTATION_KEY_FOLDED",
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED",
                    "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
                )
            )

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "Corrupted auto-rotate config. One or more values among " + "devicePosture="
                    + DEVICE_STATE_ROTATION_KEY_HALF_FOLDED + " autoRotateValue=3 are invalid"
        )
    }

    @Test
    fun loadAutoRotateDeviceStates_invalidFallbackPosture_throwsException() {
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(
                arrayOf(
                    "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:" +
                            "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                            "$DEVICE_STATE_ROTATION_KEY_UNKNOWN",
                    "$DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY:" +
                            "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                            "$DEVICE_STATE_ROTATION_KEY_FOLDED",
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED",
                    "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
                )
            )

        val exception = assertThrows(IllegalStateException::class.java) {
            settingManager =
                DeviceStateAutoRotateSettingManagerImpl(
                    mockContext,
                    executor,
                    fakeSecureSettings,
                    mockHandler,
                    mMockPostureDeviceStateConverter,
                )
        }
        assertThat(exception.message).contains(
            "Corrupted auto-rotate config. Invalid" + " fallbackPosture="
                    + DEVICE_STATE_ROTATION_KEY_UNKNOWN + "for devicePosture="
                    + DEVICE_STATE_ROTATION_KEY_HALF_FOLDED
        )
    }

    private fun persistSetting(value: String) = fakeSecureSettings.putStringForUser(
        Settings.Secure.DEVICE_STATE_ROTATION_LOCK, value, UserHandle.USER_CURRENT
    )

    private fun getDefaultSettingResolvedMap() = mutableMapOf(
        DEVICE_STATE_ROTATION_KEY_HALF_FOLDED to true,
        DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY to false,
        DEVICE_STATE_ROTATION_KEY_UNFOLDED to true,
        DEVICE_STATE_ROTATION_KEY_FOLDED to false,
    )

    private fun getPersistedDeviceStateAutoRotateSetting() = fakeSecureSettings.getStringForUser(
        Settings.Secure.DEVICE_STATE_ROTATION_LOCK, UserHandle.USER_CURRENT
    )

    private fun setUpMockPostureHelper() {
        whenever(mMockPostureDeviceStateConverter.deviceStateToPosture(eq(DEVICE_STATE_UNFOLDED)))
            .thenReturn(DEVICE_STATE_ROTATION_KEY_UNFOLDED)
        whenever(mMockPostureDeviceStateConverter.deviceStateToPosture(eq(DEVICE_STATE_FOLDED)))
            .thenReturn(DEVICE_STATE_ROTATION_KEY_FOLDED)
        whenever(
            mMockPostureDeviceStateConverter
                .deviceStateToPosture(eq(DEVICE_STATE_HALF_FOLDED))
        ).thenReturn(DEVICE_STATE_ROTATION_KEY_HALF_FOLDED)
        whenever(
            mMockPostureDeviceStateConverter
                .deviceStateToPosture(eq(DEVICE_STATE_INVALID))
        ).thenReturn(DEVICE_STATE_ROTATION_KEY_UNKNOWN)
        whenever(
            mMockPostureDeviceStateConverter
                .deviceStateToPosture(eq(DEVICE_STATE_REAR_DISPLAY))
        ).thenReturn(DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY)

        whenever(
            mMockPostureDeviceStateConverter.postureToDeviceState(
                eq(
                    DEVICE_STATE_ROTATION_KEY_UNFOLDED
                )
            )
        ).thenReturn(DEVICE_STATE_UNFOLDED)
        whenever(
            mMockPostureDeviceStateConverter.postureToDeviceState(
                eq(
                    DEVICE_STATE_ROTATION_KEY_FOLDED
                )
            )
        ).thenReturn(DEVICE_STATE_FOLDED)
        whenever(
            mMockPostureDeviceStateConverter.postureToDeviceState(
                eq(
                    DEVICE_STATE_ROTATION_KEY_HALF_FOLDED
                )
            )
        ).thenReturn(DEVICE_STATE_HALF_FOLDED)
        whenever(
            mMockPostureDeviceStateConverter.postureToDeviceState(
                eq(
                    DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY
                )
            )
        ).thenReturn(DEVICE_STATE_REAR_DISPLAY)
    }

    private companion object {
        const val DEVICE_STATE_FOLDED = 0
        const val DEVICE_STATE_HALF_FOLDED = 1
        const val DEVICE_STATE_UNFOLDED = 2
        const val DEVICE_STATE_REAR_DISPLAY = 3
        const val DEVICE_STATE_INVALID = 4

        const val FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED: String = "0:2:1:0:2:2:3:0"
        const val FOLDED_LOCKED_OPEN_UNLOCKED_SETTING_UNRESOLVED: String = "0:1:1:0:2:2:3:0"
        const val FOLDED_LOCKED_OPEN_LOCKED_SETTING_UNRESOLVED: String = "0:1:1:0:2:1:3:0"
        const val FOLDED_UNLOCKED_OPEN_LOCKED_SETTING_UNRESOLVED: String = "0:2:1:0:2:1:3:0"
        const val FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING_RESOLVED: String = "0:2:1:2:2:2:3:2"
        const val INVALID_AUTO_ROTATE_VALUE_FOR_FOLDED_SETTING: String = "0:4:1:0:2:2:3:0"
        const val MISSING_FOLDED_KEY_AND_VALUE_SETTING: String = "1:0:2:2:3:0"
        const val MISSING_VALUE_FOR_FOLDED_SETTING: String = "0:1:0:2:2:3:0"
        const val NON_INTEGER_CHARACTER_IN_AUTO_ROTATE_VALUE_SETTING: String = "0:two:1:0:2:2:3:0"
        const val OPEN_UNLOCKED_INVALID_LOCKED_SETTING: String = "1:0:2:2:3:0:4:1"
    }
}
