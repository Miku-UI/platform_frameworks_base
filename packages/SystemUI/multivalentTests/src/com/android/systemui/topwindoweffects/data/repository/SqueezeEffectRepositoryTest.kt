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

package com.android.systemui.topwindoweffects.data.repository

import android.app.ActivityManager
import android.app.activityManager
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.assist.AssistManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.testKosmos
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_OUTWARD_EFFECT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.SET_INVOCATION_EFFECT_PARAMETERS_ACTION
import com.android.systemui.util.settings.FakeGlobalSettings
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class SqueezeEffectRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val globalSettings = FakeGlobalSettings(StandardTestDispatcher())
    private val mainExecutor = Executor(Runnable::run)
    private val am = kosmos.activityManager
    @Mock private lateinit var inputManager: InputManager
    @Mock private lateinit var taskStackChangeListenersMock: TaskStackChangeListeners

    private val keyGestureEventListenerCaptor =
        ArgumentCaptor.forClass(InputManager.KeyGestureEventListener::class.java)

    private val Kosmos.underTest by
        Kosmos.Fixture {
            SqueezeEffectRepositoryImpl(
                context = context,
                globalSettings = globalSettings,
                inputManager = inputManager,
                coroutineContext = testScope.testScheduler,
                executor = mainExecutor,
                preferences = fakeInvocationEffectPreferences,
                taskStackChangeListeners = taskStackChangeListenersMock,
                activityManager = am,
            )
        }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        kosmos.fakeInvocationEffectPreferences.apply {
            clear()
            activeAssistant = "a"
            activeUserId = 0
        }
        doNothing().`when`(taskStackChangeListenersMock).registerTaskStackListener(any())
        doNothing().`when`(taskStackChangeListenersMock).unregisterTaskStackListener(any())
        whenever(am.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE
    }

    @Test
    fun testSqueezeEffectEnabled_DisabledFromPhoneWindowManager() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(true)

            val isEffectEnabled by collectLastValue(underTest.isEffectEnabled)
            val isPowerButtonPressedAsSingleGesture by
                collectLastValue(underTest.isPowerButtonPressedAsSingleGesture)

            // no events sent from KeyGestureEvent to imitate it was disabled from PWM

            assertThat(isEffectEnabled).isTrue()
            assertThat(isPowerButtonPressedAsSingleGesture).isFalse()
        }

    @Test
    fun testSqueezeEffectDisabled_AssistantSettingDisabled() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(false)

            val isEffectEnabled by collectLastValue(underTest.isEffectEnabled)
            val isPowerButtonPressedAsSingleGesture by
                collectLastValue(underTest.isPowerButtonPressedAsSingleGesture)

            verify(inputManager)
                .registerKeyGestureEventListener(
                    eq(mainExecutor),
                    keyGestureEventListenerCaptor.capture(),
                )
            val event =
                KeyGestureEvent.Builder()
                    .setAction(KeyGestureEvent.ACTION_GESTURE_START)
                    .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT)
                    .build()
            keyGestureEventListenerCaptor.value.onKeyGestureEvent(event)

            assertThat(isEffectEnabled).isFalse()
            assertThat(isPowerButtonPressedAsSingleGesture).isTrue()
        }

    @Test
    fun testSqueezeEffectEnabled_AllSettingsEnabled() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(true)

            val isEffectEnabled by collectLastValue(underTest.isEffectEnabled)
            val isPowerButtonPressedAsSingleGesture by
                collectLastValue(underTest.isPowerButtonPressedAsSingleGesture)

            verify(inputManager)
                .registerKeyGestureEventListener(
                    eq(mainExecutor),
                    keyGestureEventListenerCaptor.capture(),
                )
            val event =
                KeyGestureEvent.Builder()
                    .setAction(KeyGestureEvent.ACTION_GESTURE_START)
                    .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT)
                    .build()
            keyGestureEventListenerCaptor.value.onKeyGestureEvent(event)

            assertThat(isEffectEnabled).isTrue()
            assertThat(isPowerButtonPressedAsSingleGesture).isTrue()
        }

    @Test
    fun testInvocationEffectInwardsAnimationDelay() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectConfig(
                InvocationEffectPreferences.Config(
                    isEnabled = true,
                    inwardsEffectDurationPadding = 450,
                    outwardsEffectDuration = 400,
                ),
                true,
            )

            assertThat(underTest.getLppInvocationEffectInAnimationDurationMillis()).isEqualTo(800)
        }

    @Test
    fun testInvocationEffectOutwardsAnimationDelay() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectConfig(
                InvocationEffectPreferences.Config(
                    isEnabled = true,
                    inwardsEffectDurationPadding = 450,
                    outwardsEffectDuration = 400,
                ),
                true,
            )

            assertThat(underTest.getInvocationEffectOutAnimationDurationMillis()).isEqualTo(400)
        }

    @Test
    fun testSetUiHints_whenSuppliedAllConfigs_allUpdatedInPreferences() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.activeUserId = 1
            fakeInvocationEffectPreferences.activeAssistant = "A"

            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isFalse()

            val hints =
                createAssistantSettingBundle(
                    enableAssistantSetting = false,
                    inwardsPaddingDuration = 0,
                    outwardsAnimationDuration = 1000,
                )
            underTest.tryHandleSetUiHints(hints)

            assertThat(fakeInvocationEffectPreferences.getInwardAnimationPaddingDurationMillis())
                .isEqualTo(0)
            assertThat(fakeInvocationEffectPreferences.getOutwardAnimationDurationMillis())
                .isEqualTo(1000)
            assertThat(fakeInvocationEffectPreferences.isInvocationEffectEnabledInPreferences())
                .isFalse()
            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isTrue()
        }

    @Test
    fun testSetUiHints_whenSuppliedPartialConfig() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.activeUserId = 1
            fakeInvocationEffectPreferences.activeAssistant = "A"

            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isFalse()

            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(
                    enableAssistantSetting = false,
                    inwardsPaddingDuration = 0,
                    outwardsAnimationDuration = 1000,
                )
            )

            assertThat(fakeInvocationEffectPreferences.getInwardAnimationPaddingDurationMillis())
                .isEqualTo(0)
            assertThat(fakeInvocationEffectPreferences.getOutwardAnimationDurationMillis())
                .isEqualTo(1000)
            assertThat(fakeInvocationEffectPreferences.isInvocationEffectEnabledInPreferences())
                .isFalse()
            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isTrue()

            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(enableAssistantSetting = true)
            )

            assertThat(fakeInvocationEffectPreferences.getInwardAnimationPaddingDurationMillis())
                .isEqualTo(0)
            assertThat(fakeInvocationEffectPreferences.getOutwardAnimationDurationMillis())
                .isEqualTo(1000)
            assertThat(fakeInvocationEffectPreferences.isInvocationEffectEnabledInPreferences())
                .isTrue()
        }

    @Test
    fun testSetUiHints_whenSuppliedNoConfig_shouldSetDefaults() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.activeUserId = 1
            fakeInvocationEffectPreferences.activeAssistant = "A"

            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isFalse()

            underTest.tryHandleSetUiHints(createAssistantSettingBundle())

            assertThat(fakeInvocationEffectPreferences.getInwardAnimationPaddingDurationMillis())
                .isEqualTo(DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS)
            assertThat(fakeInvocationEffectPreferences.getOutwardAnimationDurationMillis())
                .isEqualTo(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
            assertThat(fakeInvocationEffectPreferences.isInvocationEffectEnabledInPreferences())
                .isEqualTo(DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE)
            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isTrue()
        }

    @Test
    fun testSetUiHints_whenSuppliedWrongConfigType_setsDefault() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.activeUserId = 1
            fakeInvocationEffectPreferences.activeAssistant = "A"

            assertThat(fakeInvocationEffectPreferences.isCurrentUserAndAssistantPersisted())
                .isFalse()

            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(
                    enableAssistantSetting =
                        !DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE,
                    inwardsPaddingDuration = 501L,
                    outwardsAnimationDuration = 502L,
                )
            )

            underTest.tryHandleSetUiHints(
                Bundle().apply {
                    putString(AssistManager.ACTION_KEY, SET_INVOCATION_EFFECT_PARAMETERS_ACTION)
                    putInt(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT, 123)
                    putInt(INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS, 456)
                    putInt(INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS, 789)
                }
            )

            assertThat(fakeInvocationEffectPreferences.isInvocationEffectEnabledInPreferences())
                .isEqualTo(DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE)
            assertThat(fakeInvocationEffectPreferences.getInwardAnimationPaddingDurationMillis())
                .isEqualTo(DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS)
            assertThat(fakeInvocationEffectPreferences.getOutwardAnimationDurationMillis())
                .isEqualTo(DEFAULT_OUTWARD_EFFECT_DURATION_MS)
        }

    @Test
    fun testGestureEffectEnabled_preferenceEnabled() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(true)
            assertThat(underTest.isGestureEffectEnabled()).isTrue()
        }

    @Test
    fun testGestureEffectEnabled_preferenceDisabled() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(false)
            assertThat(underTest.isGestureEffectEnabled()).isFalse()
        }

    @Test
    fun onGestureProgress_updatesGestureProgressStateFlow() =
        kosmos.runTest {
            val gestureProgress by collectLastValue(underTest.gestureProgress)
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(true)

            underTest.onGestureProgress(0.5f)

            assertThat(gestureProgress?.status)
                .isEqualTo(SqueezeEffectRepository.GestureStatus.PARTIAL)
            assertThat(gestureProgress?.progress).isEqualTo(0.5f)
        }

    @Test
    fun onGestureCompletion_updatesGestureProgressStateFlow() =
        kosmos.runTest {
            val gestureProgress by collectLastValue(underTest.gestureProgress)
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(true)

            underTest.onGestureCompletion()

            assertThat(gestureProgress?.status)
                .isEqualTo(SqueezeEffectRepository.GestureStatus.COMPLETED)
        }

    @Test
    fun hideGestureEffect_updatesGestureProgressStateFlow() =
        kosmos.runTest {
            val gestureProgress by collectLastValue(underTest.gestureProgress)
            fakeInvocationEffectPreferences.setInvocationEffectEnabledByAssistant(true)

            underTest.hideGestureEffect()

            assertThat(gestureProgress?.status)
                .isEqualTo(SqueezeEffectRepository.GestureStatus.HIDDEN)
        }

    @Test
    fun testGetGestureInvocationEffectInAnimationDurationMillis() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectConfig(
                InvocationEffectPreferences.Config(
                    isEnabled = true,
                    inwardsEffectDurationPadding = 450,
                    outwardsEffectDuration = 400,
                ),
                true,
            )

            assertThat(underTest.getGestureInvocationEffectInAnimationDurationMillis())
                .isEqualTo(450)
        }

    @Test
    fun testSqueezeEffectEnabledWhenLockTaskModeNone() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectConfig(
                InvocationEffectPreferences.Config(
                    isEnabled = true,
                    inwardsEffectDurationPadding = 450,
                    outwardsEffectDuration = 400,
                ),
                true,
            )

            val isEffectEnabled by collectLastValue(underTest.isEffectEnabled)

            assertThat(isEffectEnabled).isTrue()
        }

    @Test
    fun testSqueezeEffectDisabledWhenLockTaskModeLocked() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectConfig(
                InvocationEffectPreferences.Config(
                    isEnabled = true,
                    inwardsEffectDurationPadding = 450,
                    outwardsEffectDuration = 400,
                ),
                true,
            )

            Mockito.reset(am)
            whenever(am.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_LOCKED

            val isEffectEnabled by collectLastValue(underTest.isEffectEnabled)

            assertThat(isEffectEnabled).isFalse()
        }

    @Test
    fun testSqueezeEffectDisabledWhenLockTaskModePinned() =
        kosmos.runTest {
            fakeInvocationEffectPreferences.setInvocationEffectConfig(
                InvocationEffectPreferences.Config(
                    isEnabled = true,
                    inwardsEffectDurationPadding = 450,
                    outwardsEffectDuration = 400,
                ),
                true,
            )

            Mockito.reset(am)
            whenever(am.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_PINNED

            val isEffectEnabled by collectLastValue(underTest.isEffectEnabled)

            assertThat(isEffectEnabled).isFalse()
        }

    private fun createAssistantSettingBundle(
        enableAssistantSetting: Boolean? = null,
        inwardsPaddingDuration: Long? = null,
        outwardsAnimationDuration: Long? = null,
    ) =
        Bundle().apply {
            putString(AssistManager.ACTION_KEY, SET_INVOCATION_EFFECT_PARAMETERS_ACTION)
            enableAssistantSetting?.let {
                putBoolean(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT, it)
            }
            inwardsPaddingDuration?.let {
                putLong(INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS, it)
            }
            outwardsAnimationDuration?.let {
                putLong(INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS, it)
            }
        }
}
