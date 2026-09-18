/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.flags.Flags as ViewFlags
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.clock.ui.viewmodel.clockViewModelFactory
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.scene.ui.view.mockShadeRootView
import com.android.systemui.statusbar.events.domain.interactor.systemStatusEventAnimationInteractor
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.connectedDisplaysStatusBarNotificationIconViewStoreFactory
import com.android.systemui.statusbar.phone.ui.DarkIconManager
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.phone.ui.statusBarIconController
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.defaultDisplayHomeStatusBarViewModelFactory
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.displayAwareHeadlineViewModelImplFactory
import com.android.systemui.statusbar.ui.viewmodel.statusBarRegionSamplingViewModelFactory
import com.android.systemui.testKosmosNew
import com.android.wm.shell.scrolltotop.fakeScrollToTop
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@DisableFlags(StatusBarMobileIconKairos.FLAG_NAME)
class StatusBarRootTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmosNew()
    private val testScope = kosmos.testScope

    private val darkIconManagerFactory = mock<DarkIconManager.Factory>()
    private val tintedIconManagerFactory = mock<TintedIconManager.Factory>()
    private val homeStatusBarViewBinder = mock<HomeStatusBarViewBinder>()

    @Before
    fun setUp() {
        whenever(darkIconManagerFactory.create(any(), any(), any())).thenReturn(mock())
        whenever(tintedIconManagerFactory.create(any(), any())).thenReturn(mock())
    }

    @Test
    @EnableFlags(
        ViewFlags.FLAG_SCROLL_TO_TOP,
        Flags.FLAG_STATUS_BAR_EVENT_FORWARDING_MODERNIZATION,
        Flags.FLAG_SCENE_CONTAINER,
    )
    fun tap_triggersScrollToTop() {
        setContent()
        composeTestRule.onNodeWithTag(STATUS_BAR_ROOT_TAG).performClick()
        assertThat(kosmos.fakeScrollToTop.lastScrollToTopDisplayId).isEqualTo(context.displayId)
    }

    @Test
    @EnableFlags(
        ViewFlags.FLAG_SCROLL_TO_TOP,
        Flags.FLAG_STATUS_BAR_EVENT_FORWARDING_MODERNIZATION,
        Flags.FLAG_SCENE_CONTAINER,
    )
    fun swipeDown_doesNotTriggerScrollToTop() {
        setContent()
        composeTestRule.onNodeWithTag(STATUS_BAR_ROOT_TAG).performTouchInput { swipeDown() }
        assertThat(kosmos.fakeScrollToTop.lastScrollToTopDisplayId).isNull()
    }

    @Test
    @EnableFlags(
        ViewFlags.FLAG_SCROLL_TO_TOP,
        Flags.FLAG_STATUS_BAR_EVENT_FORWARDING_MODERNIZATION,
        Flags.FLAG_SCENE_CONTAINER,
    )
    fun longClick_doesNotTriggerScrollToTop() {
        setContent()
        composeTestRule.onNodeWithTag(STATUS_BAR_ROOT_TAG).performTouchInput { longClick() }
        assertThat(kosmos.fakeScrollToTop.lastScrollToTopDisplayId).isNull()
    }

    private fun setContent() {
        composeTestRule.setContent {
            StatusBarRoot(
                parent = FrameLayout(context),
                shadeWindowRootView = kosmos.mockShadeRootView,
                statusBarViewModelFactory = kosmos.defaultDisplayHomeStatusBarViewModelFactory,
                statusBarViewBinder = homeStatusBarViewBinder,
                notificationIconsBinder = mock(),
                iconViewStoreFactory =
                    kosmos.connectedDisplaysStatusBarNotificationIconViewStoreFactory,
                clockViewModelFactory = kosmos.clockViewModelFactory,
                darkIconManagerFactory = darkIconManagerFactory,
                tintedIconManagerFactory = tintedIconManagerFactory,
                headlineViewModelFactory = kosmos.displayAwareHeadlineViewModelImplFactory,
                headlineComposer = mock(),
                iconController = kosmos.statusBarIconController,
                darkIconDispatcher = kosmos.fakeDarkIconDispatcher,
                eventAnimationInteractor = kosmos.systemStatusEventAnimationInteractor,
                statusBarRegionSamplingViewModelFactory =
                    kosmos.statusBarRegionSamplingViewModelFactory,
                onViewCreated = {},
                modifier = Modifier.testTag(STATUS_BAR_ROOT_TAG),
            )
        }
    }

    companion object {
        private const val STATUS_BAR_ROOT_TAG = "StatusBarRoot"
    }
}
