/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.graphics.Point
import android.graphics.Rect
import android.testing.TestableLooper.RunWithLooper
import android.view.IRemoteAnimationFinishedCallback
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.View
import android.view.ViewRootImpl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.jank.InteractionJankMonitor
import com.android.keyguard.KeyguardViewController
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.keyguard.ui.viewmodel.DreamingToLockscreenTransitionViewModel
import com.android.systemui.power.domain.interactor.PowerInteractor
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class WindowManagerOcclusionManagerTest : SysuiTestCase() {

    @Mock private lateinit var keyguardOcclusionInteractor: KeyguardOcclusionInteractor
    @Mock private lateinit var activityTransitionAnimator: ActivityTransitionAnimator
    @Mock private lateinit var keyguardViewController: KeyguardViewController
    @Mock private lateinit var powerInteractor: PowerInteractor
    @Mock private lateinit var interactionJankMonitor: InteractionJankMonitor
    @Mock private lateinit var applicationScope: CoroutineScope
    @Mock
    private lateinit var dreamingToLockscreenTransitionViewModel:
        DreamingToLockscreenTransitionViewModel

    @Mock private lateinit var viewRootImpl: ViewRootImpl
    @Mock private lateinit var view: View

    private lateinit var occlusionManager: WindowManagerOcclusionManager
    private val directExecutor = Executor { it.run() }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        `when`(keyguardViewController.viewRootImpl).thenReturn(viewRootImpl)
        `when`(viewRootImpl.view).thenReturn(view)

        occlusionManager =
            WindowManagerOcclusionManager(
                keyguardOcclusionInteractor,
                activityTransitionAnimator,
                { keyguardViewController },
                powerInteractor,
                mContext,
                interactionJankMonitor,
                directExecutor,
                applicationScope,
                dreamingToLockscreenTransitionViewModel,
            )
    }

    private fun createMockTarget(isValid: Boolean): RemoteAnimationTarget {
        val mockSurfaceControl = mock(SurfaceControl::class.java)
        `when`(mockSurfaceControl.isValid).thenReturn(isValid)

        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD

        return RemoteAnimationTarget(
            0,
            0,
            mockSurfaceControl,
            false,
            Rect(),
            Rect(),
            0,
            Point(),
            Rect(),
            Rect(),
            WindowConfiguration(),
            false,
            mock(SurfaceControl::class.java),
            Rect(),
            taskInfo,
            false,
        )
    }

    @Test
    fun testUnoccludeAnimationRunner_skipsUpdateIfLeashInvalid() {
        val mockTarget = createMockTarget(isValid = false)
        val apps = arrayOf(mockTarget)
        val finishedCallback = mock(IRemoteAnimationFinishedCallback::class.java)

        occlusionManager.unoccludeAnimationRunner.onAnimationStart(
            0 /* transit */,
            apps,
            emptyArray(),
            emptyArray(),
            finishedCallback,
        )

        // The runner should call onAnimationFinished immediately when leash is invalid.
        verify(finishedCallback).onAnimationFinished()
        verify(interactionJankMonitor).end(InteractionJankMonitor.CUJ_LOCKSCREEN_OCCLUSION)
    }

    @Test
    fun testOccludeByDreamAnimationRunner_skipsUpdateIfLeashInvalid() {
        val mockTarget = createMockTarget(isValid = false)
        // Make it look like a dream task to pass the dream check
        mockTarget.taskInfo.topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM

        val apps = arrayOf(mockTarget)
        val finishedCallback = mock(IRemoteAnimationFinishedCallback::class.java)

        occlusionManager.occludeByDreamAnimationRunner.onAnimationStart(
            0 /* transit */,
            apps,
            emptyArray(),
            emptyArray(),
            finishedCallback,
        )

        // The runner should call onAnimationFinished immediately when leash is invalid.
        verify(finishedCallback).onAnimationFinished()
        verify(interactionJankMonitor).end(InteractionJankMonitor.CUJ_LOCKSCREEN_OCCLUSION)
    }
}
