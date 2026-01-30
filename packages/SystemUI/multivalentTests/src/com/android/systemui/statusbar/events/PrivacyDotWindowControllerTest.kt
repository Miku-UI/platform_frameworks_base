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

package com.android.systemui.statusbar.events

import android.view.Gravity.BOTTOM
import android.view.Gravity.LEFT
import android.view.Gravity.RIGHT
import android.view.Gravity.TOP
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.fakeWindowManager
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class PrivacyDotWindowControllerTest : SysuiTestCase() {

    @get:Rule val expect: Expect = Expect.create()

    private val kosmos = testKosmos()
    private val underTest = kosmos.privacyDotWindowController
    private val viewController = kosmos.privacyDotViewController
    private val windowManager = kosmos.fakeWindowManager
    private val executor = kosmos.fakeExecutor

    @After
    fun cleanUpCustomDisplay() {
        context.display = null
    }

    @Test
    fun start_beforeUiThreadExecutes_doesNotAddWindows() {
        underTest.start()

        assertThat(windowManager.addedViews).isEmpty()
    }

    @Test
    fun start_beforeUiThreadExecutes_doesNotInitializeViewController() {
        underTest.start()

        assertThat(viewController.isInitialized).isFalse()
    }

    @Test
    fun start_afterUiThreadExecutes_doesNotAddWindowsInitially() {
        underTest.start()
        executor.runAllReady()

        // Windows are now added dynamically, so immediately after initialization,
        // no windows should be present until a dot is "shown".
        assertThat(windowManager.addedViews).isEmpty()
    }

    @Test
    fun start_afterUiThreadExecutes_initializesViewController() {
        underTest.start()

        executor.runAllReady()

        assertThat(viewController.isInitialized).isTrue()
    }

    @Test
    fun start_initializesTopLeft() {
        underTest.start()
        executor.runAllReady()

        // The ID should be on the dotView, not necessarily the container anymore.
        // Assuming R.id.privacy_dot_top_left_container is the ID of the inner dotView.
        assertThat(viewController.topLeft?.id).isEqualTo(R.id.privacy_dot_top_left_container)
    }

    @Test
    fun start_initializesTopRight() {
        underTest.start()
        executor.runAllReady()

        assertThat(viewController.topRight?.id).isEqualTo(R.id.privacy_dot_top_right_container)
    }

    @Test
    fun start_initializesTopBottomLeft() {
        underTest.start()
        executor.runAllReady()

        assertThat(viewController.bottomLeft?.id).isEqualTo(R.id.privacy_dot_bottom_left_container)
    }

    @Test
    fun start_initializesBottomRight() {
        underTest.start()
        executor.runAllReady()

        assertThat(viewController.bottomRight?.id)
            .isEqualTo(R.id.privacy_dot_bottom_right_container)
    }

    @Test
    fun onPrivacyDotShown_addsWindow() {
        underTest.start()
        executor.runAllReady()

        // Simulate the PrivacyDotViewController showing the top-left dot
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft!!)
        executor.runAllReady() // Ensure the addView call on UI thread is processed

        // Verify exactly one window was added
        assertThat(windowManager.addedViews).hasSize(1)

        // Get the FrameLayout that was added to the WindowManager
        val addedWindowRootView = windowManager.addedViews.keys.first()

        // Assert it's a FrameLayout (the expected container)
        expect.that(addedWindowRootView).isInstanceOf(FrameLayout::class.java)

        // Assert that this FrameLayout actually contains the specific dotView
        // (fakeViewController.topLeft)
        // The PrivacyDotWindowController's inflate method adds the dotView as a child of the
        // FrameLayout.
        expect
            .that((addedWindowRootView as FrameLayout).getChildAt(0))
            .isEqualTo(viewController.topLeft)
    }

    @Test
    fun onPrivacyDotHidden_removesWindow() {
        underTest.start()
        executor.runAllReady()

        // Show the top-left dot first
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        executor.runAllReady()
        assertThat(windowManager.addedViews).hasSize(1)

        // Now hide it
        viewController.showingListener?.onPrivacyDotHidden(viewController.topLeft)
        executor.runAllReady()

        assertThat(windowManager.addedViews).isEmpty()
    }

    @Test
    fun start_viewsAddedInRespectiveCorners() {
        context.display = mock { on { rotation } doReturn Surface.ROTATION_0 }
        underTest.start()
        executor.runAllReady()

        // Now, trigger the 'shown' event for each dot
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.topRight)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomRight)
        executor.runAllReady() // Process all addView calls

        expect.that(gravityForView(viewController.topLeft!!)).isEqualTo(TOP or LEFT)
        expect.that(gravityForView(viewController.topRight!!)).isEqualTo(TOP or RIGHT)
        expect.that(gravityForView(viewController.bottomLeft!!)).isEqualTo(BOTTOM or LEFT)
        expect.that(gravityForView(viewController.bottomRight!!)).isEqualTo(BOTTOM or RIGHT)
    }

    @Test
    fun start_rotation90_viewsPositionIsShifted90degrees() {
        context.display = mock { on { rotation } doReturn Surface.ROTATION_90 }
        underTest.start()
        executor.runAllReady()

        // Now, trigger the 'shown' event for each dot
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.topRight)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomRight)
        executor.runAllReady() // Process all addView calls

        expect.that(gravityForView(viewController.topLeft!!)).isEqualTo(BOTTOM or LEFT)
        expect.that(gravityForView(viewController.topRight!!)).isEqualTo(TOP or LEFT)
        expect.that(gravityForView(viewController.bottomLeft!!)).isEqualTo(BOTTOM or RIGHT)
        expect.that(gravityForView(viewController.bottomRight!!)).isEqualTo(TOP or RIGHT)
    }

    @Test
    fun start_rotation180_viewsPositionIsShifted180degrees() {
        context.display = mock { on { rotation } doReturn Surface.ROTATION_180 }
        underTest.start()
        executor.runAllReady()

        // Now, trigger the 'shown' event for each dot
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.topRight)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomRight)
        executor.runAllReady() // Process all addView calls

        expect.that(gravityForView(viewController.topLeft!!)).isEqualTo(BOTTOM or RIGHT)
        expect.that(gravityForView(viewController.topRight!!)).isEqualTo(BOTTOM or LEFT)
        expect.that(gravityForView(viewController.bottomLeft!!)).isEqualTo(TOP or RIGHT)
        expect.that(gravityForView(viewController.bottomRight!!)).isEqualTo(TOP or LEFT)
    }

    @Test
    fun start_rotation270_viewsPositionIsShifted270degrees() {
        context.display = mock { on { rotation } doReturn Surface.ROTATION_270 }
        underTest.start()
        executor.runAllReady()

        // Now, trigger the 'shown' event for each dot
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.topRight)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomRight)
        executor.runAllReady() // Process all addView calls

        expect.that(gravityForView(viewController.topLeft!!)).isEqualTo(TOP or RIGHT)
        expect.that(gravityForView(viewController.topRight!!)).isEqualTo(BOTTOM or RIGHT)
        expect.that(gravityForView(viewController.bottomLeft!!)).isEqualTo(TOP or LEFT)
        expect.that(gravityForView(viewController.bottomRight!!)).isEqualTo(BOTTOM or LEFT)
    }

    @Test
    fun onStop_removesAllCurrentlyAddedWindows() {
        underTest.start()
        executor.runAllReady()

        // Show all dots so their windows are added
        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.topRight)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomLeft)
        viewController.showingListener?.onPrivacyDotShown(viewController.bottomRight)
        executor.runAllReady()
        assertThat(windowManager.addedViews).hasSize(4)

        // Now call stop
        underTest.stop()
        executor.runAllReady()

        assertThat(windowManager.addedViews).isEmpty()
    }

    @Test
    fun onStop_removingWindowViewsThrows_codeDoesNotCrash() {
        underTest.start()
        executor.runAllReady()

        viewController.showingListener?.onPrivacyDotShown(viewController.topLeft)
        executor.runAllReady()

        // Simulate removing a view from window manager outside of our code
        windowManager.addedViews.clear()
        // Ensure no crash when stopping and trying to remove an already detached view
        underTest.stop()
        executor.runAllReady()
    }

    // Helper functions: Note that paramsForView needs to find the *root* view (FrameLayout)
    // that was added to the window manager, not the inner dotView.
    private fun paramsForView(dotView: View): WindowManager.LayoutParams {
        // We're looking for the FrameLayout that contains the dotView.
        // The dotView has an ID, and the FrameLayout is its parent.
        return windowManager.addedViews.entries
            .first { (rootView, _) ->
                rootView is FrameLayout && rootView.findViewById<View>(dotView.id) == dotView
            }
            .value
    }

    private fun gravityForView(view: View): Int {
        return paramsForView(view).gravity
    }
}
