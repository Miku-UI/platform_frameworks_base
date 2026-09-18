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

package com.android.systemui.motioncues

import android.app.motioncues.MotionCuesVisualStyle
import android.app.motioncues.MotionCuesSettings
import android.content.res.Configuration
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.view.View
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.motioncues.nano.MotionBubble
import com.android.systemui.motioncues.nano.MotionCueState
import com.android.systemui.statusbar.policy.ConfigurationController
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

@SmallTest
@RunWith(AndroidJUnit4::class)
class MotionCuesUiTest : SysuiTestCase() {

    private val PORTRAIT_BOUNDS = Rect(0, 0, 1080, 2340)
    private val LANDSCAPE_BOUNDS = Rect(0, 0, 2340, 1080)
    private val SETTINGS =
        MotionCuesSettings.Builder()
            .setHorizontalSpacingDp(50)
            .setVerticalSpacingDp(100)
            .setMarginSizeDp(10)
            .setRadiusDp(20)
            .build()

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var windowManager: WindowManager
    @Mock private lateinit var windowMetrics: WindowMetrics
    @Mock private lateinit var clientPackageContext: Context
    @Mock private lateinit var drawable: Drawable
    @Mock private lateinit var configurationController: ConfigurationController

    private lateinit var underTest: MotionCuesUi

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(windowManager.currentWindowMetrics).thenReturn(windowMetrics)
        whenever(windowMetrics.bounds).thenReturn(PORTRAIT_BOUNDS)

        val initialConfig = Configuration()
        initialConfig.orientation = Configuration.ORIENTATION_PORTRAIT
        initialConfig.screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL
        context.getOrCreateTestableResources().overrideConfiguration(initialConfig)

        underTest = MotionCuesUi(context, windowManager, configurationController)
    }

    @Test
    fun start_setsIsStartedTrue_andAddsView() {
        val userId = 0
        val clientPackageName = "com.example.app"

        underTest.start(SETTINGS, userId, clientPackageName)

        assertThat(underTest.isStarted).isTrue()
        verify(windowManager).addView(any(View::class.java), any(WindowManager.LayoutParams::class.java))
    }

    @Test
    fun stop_whenStarted_setsIsStartedFalse_andRemovesView() {
        // Start first to have a view to remove
        underTest.start(SETTINGS, 0, "com.example.app")
        assertThat(underTest.isStarted).isTrue()

        underTest.stop()

        assertThat(underTest.isStarted).isFalse()
        verify(windowManager).removeViewImmediate(any(View::class.java))
    }

    @Test
    fun stop_whenNotStarted_isNoOp() {
        assertThat(underTest.isStarted).isFalse()

        underTest.stop() // Should be a no-op

        assertThat(underTest.isStarted).isFalse()
        verify(windowManager, never()).removeViewImmediate(any(View::class.java))
    }

    @Test
    fun updateBubblePos_updatesPositions() {
        underTest.start(SETTINGS, 0, "com.example.app")
        val initialState: MotionCueState = underTest.getState()
        val initialBubble: MotionBubble = checkNotNull(initialState.motionBubbles.firstOrNull()) {
            "No bubbles found in initial state"
        }

        val dx = 10f
        val dy = 20f
        underTest.updateBubblePos(dx, dy)

        val newState: MotionCueState = underTest.getState()
        val newBubble: MotionBubble = checkNotNull(newState.motionBubbles.firstOrNull()) {
            "No bubbles found in new state"
        }
        assertThat(newBubble.x).isEqualTo(initialBubble.x + dx)
        assertThat(newBubble.y).isEqualTo(initialBubble.y + dy)
    }

    @Test
    fun updateMotionCuesVisualStyle_updatesColorAndShape() {
        whenever(mockContext.createPackageContextAsUser(anyString(), anyInt(), any(UserHandle::class.java)))
            .thenReturn(clientPackageContext)
        whenever(clientPackageContext.getDrawable(anyInt())).thenReturn(drawable)
        underTest = MotionCuesUi(mockContext, windowManager, configurationController)
        underTest.setIsStarted(true)
        underTest.setClientPackageName("com.example.app")

        val newColor = Color.RED
        val newShapeRes = 12345
        val data = MotionCuesVisualStyle(newColor, newShapeRes)
        underTest.updateMotionCuesVisualStyle(data)

        val state: MotionCueState = underTest.getState()
        assertThat(state.paintColor).isEqualTo(String.format("#%08X", newColor))
        assertThat(state.bubbleShapeResId).isEqualTo(newShapeRes)
    }

    @Test
    fun updateMotionCuesVisualStyle_withInvalidPackage_usesDefault() {
        whenever(mockContext.createPackageContextAsUser(anyString(), anyInt(), any(UserHandle::class.java)))
            .thenThrow(PackageManager.NameNotFoundException())
        underTest = MotionCuesUi(mockContext, windowManager, configurationController)
        underTest.setIsStarted(true)

        val newColor = Color.RED
        val newShapeRes = 12345
        val data = MotionCuesVisualStyle(newColor, newShapeRes)
        underTest.updateMotionCuesVisualStyle(data)

        val state: MotionCueState = underTest.getState()
        assertThat(state.paintColor).isEqualTo(String.format("#%08X", newColor))
        assertThat(state.bubbleShapeResId).isEqualTo(0) // Should fallback to default
    }

    @Test
    fun getState_returnsCorrectInitialState() {
        val settings =
            MotionCuesSettings.Builder()
                .setHorizontalSpacingDp(50)
                .setVerticalSpacingDp(100)
                .setMarginSizeDp(10)
                .setRadiusDp(20)
                .build()
        val userId = 1
        val clientPackageName = "com.test.app"

        underTest.start(settings, userId, clientPackageName)
        val state: MotionCueState = underTest.getState()

        assertThat(state.isStarted).isTrue()
        assertThat(state.clientPackageName).isEqualTo(clientPackageName)
        assertThat(state.horizontalSpacingDp).isEqualTo(settings.horizontalSpacingDp)
        assertThat(state.verticalSpacingDp).isEqualTo(settings.verticalSpacingDp)
        assertThat(state.marginSizeDp).isEqualTo(settings.marginSizeDp)
        assertThat(state.radiusDp).isEqualTo(settings.radiusDp)
        assertThat(state.motionBubbles).isNotEmpty()
    }

    @Test
    fun onConfigChanged_orientationChanged_whenStarted_recreatesOverlay() {
        // Start in Portrait
        underTest.start(SETTINGS, 0, "com.example.app")
        verify(windowManager, times(1)).addView(any(), any())

        // Simulate Landscape
        val newConfig = Configuration()
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE
        whenever(windowMetrics.bounds).thenReturn(LANDSCAPE_BOUNDS)
        underTest.onConfigChanged(newConfig)

        // Verify overlay was removed and re-added
        verify(windowManager, times(1)).removeViewImmediate(any())
        verify(windowManager, times(2)).addView(any(), any())
    }

    @Test
    fun onConfigChanged_screenLayoutChanged_whenStarted_recreatesOverlay() {
        // Start in normal screen layout
        underTest.start(SETTINGS, 0, "com.example.app")
        verify(windowManager, times(1)).addView(any(), any())

        // Simulate large screen layout
        val newConfig = Configuration()
        newConfig.screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE
        underTest.onConfigChanged(newConfig)

        // Verify overlay was removed and re-added
        verify(windowManager, times(1)).removeViewImmediate(any())
        verify(windowManager, times(2)).addView(any(), any())
    }

     @Test
     fun onConfigChanged_orientationChanged_whenStopped_doesNothing() {
        underTest.start(SETTINGS, 0, "com.example.app")
        underTest.stop()

        // Simulate Landscape
        val newConfig = Configuration()
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE
        whenever(windowMetrics.bounds).thenReturn(LANDSCAPE_BOUNDS)
        underTest.onConfigChanged(newConfig)

        // Verify no WindowManager interactions
        verify(windowManager, times(1)).removeViewImmediate(any())
        verify(windowManager, times(1)).addView(any(), any())
     }

     @Test
     fun onConfigChanged_orientationScreenLayoutNotChanged_doesNothing() {
        underTest.start(SETTINGS, 0, "com.example.app")
        verify(windowManager, times(1)).addView(any(), any())

        // Simulate config change with same orientation
        val newConfig = Configuration()
        newConfig.screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL
        newConfig.orientation = Configuration.ORIENTATION_PORTRAIT
        whenever(windowMetrics.bounds).thenReturn(PORTRAIT_BOUNDS)
        underTest.onConfigChanged(newConfig)

        // Verify no additional remove/add calls
        verify(windowManager, never()).removeViewImmediate(any())
        verify(windowManager, times(1)).addView(any(), any()) // Only the initial add
     }
}