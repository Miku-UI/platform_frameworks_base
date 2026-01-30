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

package com.android.systemui.securelockdevice.ui.composable

import android.platform.test.annotations.EnableFlags
import android.security.Flags
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.SysuiTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_SECURE_LOCK_DEVICE)
class SecureLockDeviceBiometricAuthContentTest : SysuiTestCase() {
    private val interactionJankMonitor: InteractionJankMonitor = mock()
    private val view: View = mock()
    private val onDisappearAnimationFinished: () -> Unit = mock()

    @Test
    fun handleJankMonitoring_tracksAppear() {
        // Simulate start of transition when target state becomes visible
        handleJankMonitoring(
            currentState = false,
            isCurrentStateIdle = false,
            targetState = true,
            isReadyToDismissBiometricAuth = false,
            interactionJankMonitor = interactionJankMonitor,
            view = view,
            onDisappearAnimationFinished = onDisappearAnimationFinished,
        )

        // Verify jank monitoring begins for appear
        verify(interactionJankMonitor)
            .begin(view, Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_APPEAR)

        // Simulate end of transition when state becomes idle at visible
        handleJankMonitoring(
            currentState = true,
            isCurrentStateIdle = true,
            targetState = true,
            isReadyToDismissBiometricAuth = false,
            interactionJankMonitor = interactionJankMonitor,
            view = view,
            onDisappearAnimationFinished = onDisappearAnimationFinished,
        )

        // THEN jank monitoring ends for appear
        verify(interactionJankMonitor).end(Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_APPEAR)
    }

    @Test
    fun handleJankMonitoring_tracksDisappear() {
        // Simulate start of transition when target state becomes not visible
        handleJankMonitoring(
            currentState = true,
            isCurrentStateIdle = false,
            targetState = false,
            isReadyToDismissBiometricAuth = true,
            interactionJankMonitor = interactionJankMonitor,
            view = view,
            onDisappearAnimationFinished = onDisappearAnimationFinished,
        )

        // Verify jank monitoring begins for disappear
        verify(interactionJankMonitor)
            .begin(view, Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR)

        // Simulate end of transition when not visible state becomes idle
        handleJankMonitoring(
            currentState = false,
            isCurrentStateIdle = true,
            targetState = false,
            isReadyToDismissBiometricAuth = true,
            interactionJankMonitor = interactionJankMonitor,
            view = view,
            onDisappearAnimationFinished = onDisappearAnimationFinished,
        )

        // Verify jank monitoring ends for disappear and the callback is invoked
        verify(interactionJankMonitor)
            .end(Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR)
        verify(onDisappearAnimationFinished).invoke()
    }

    @Test
    fun handleJankMonitoring_doesNotTrackDisappear_whenNotReadyToDismiss() {
        // Simulate start of transition when target state becomes not visible, but not ready to
        // dismiss (animations haven't finished playing)
        handleJankMonitoring(
            currentState = true,
            isCurrentStateIdle = false,
            targetState = false,
            isReadyToDismissBiometricAuth = false,
            interactionJankMonitor = interactionJankMonitor,
            view = view,
            onDisappearAnimationFinished = onDisappearAnimationFinished,
        )

        // Verify jank monitoring does NOT begin
        verify(interactionJankMonitor, never())
            .begin(view, Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR)
    }
}
