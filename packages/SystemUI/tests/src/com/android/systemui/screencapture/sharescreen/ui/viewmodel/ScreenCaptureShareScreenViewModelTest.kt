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

package com.android.systemui.screencapture.sharescreen.ui.viewmodel

import android.app.WaitResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.packageManager
import android.content.pm.PackageManager
import android.content.testableContext
import android.media.projection.MediaProjectionAppContent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_APP_CONTENT
import android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_DISPLAY
import android.media.projection.ReviewGrantedConsentResult
import android.os.UserHandle
import android.view.Display
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureRecentTaskRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.repository.FakeAppContentProjectionCallback
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.ui.viewmodel.AppContentsViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DisplaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.displayViewModelFactory
import com.android.systemui.screencapture.common.ui.viewmodel.displaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.recentTaskViewModelFactory
import com.android.systemui.screencapture.common.ui.viewmodel.recentTasksViewModel
import com.android.systemui.screencapture.sharescreen.domain.interactor.ShareScreenUiInteractor
import com.android.systemui.screencapture.sharescreen.domain.interactor.fakeMediaProjectionServiceHelperWrapper
import com.android.systemui.screencapture.sharescreen.domain.interactor.mockAsyncActivityLauncher
import com.android.systemui.screencapture.sharescreen.domain.interactor.shareScreenUiInteractor
import com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor.shareScreenPrivacyIndicatorInteractor
import com.android.systemui.testKosmosNew
import com.android.systemui.util.mockito.whenever
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import java.lang.ref.WeakReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureShareScreenViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            testableContext.orCreateTestableResources.addOverride(
                R.bool.config_largeScreenPrivacyIndicator,
                true,
            )
        }

    private val Kosmos.fakeRecentTask by
        Kosmos.Fixture {
            RecentTask(
                taskId = 1,
                displayId = 2,
                userId = 3,
                topActivityComponent = ComponentName("FakeTopPackage", "FakeTopClass"),
                baseIntentComponent = ComponentName("FakeBasePackage", "FakeClass"),
                baseIntent = Intent(),
                colorBackground = 0x12345699,
                isForegroundTask = true,
                userType = UserType.MAIN,
                splitBounds = null,
            )
        }

    private val viewModel: ScreenCaptureShareScreenViewModel by lazy {
        kosmos.screenCaptureShareScreenViewModel
    }

    @Before
    fun setUp() {
        whenever(kosmos.packageManager.checkPermission(any<String>(), any<String>()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        runBlocking {
            kosmos.displayRepository.emit(
                setOf(
                    mock<Display> {
                        on { displayId } doReturn 0
                        on { name } doReturn "default display"
                    }
                )
            )
        }
    }

    private fun setupViewModel(config: MediaProjectionConfig) {
        kosmos.shareScreenUiInteractor.initialize(
            projection = null,
            reviewGrantedConsentRequired = true,
            hostUserHandle = UserHandle.CURRENT,
            uid = 100,
            packageName = context.packageName,
            initialDisplayId = 0,
            config = config,
        )
        viewModel.activateIn(kosmos.testScope)

        kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
            MediaProjectionState.NotProjecting
    }

    @Test
    fun initialState_initialSourceIsAppContent_showsAppContentsViewModel() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )

            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.currentTargetsModel).isInstanceOf(AppContentsViewModel::class.java)

            assertThat(viewModel.isUiVisible).isTrue()
        }

    @Test
    fun initialState_initialSourceIsEntireScreen_showsDisplaysViewModel() =
        kosmos.runTest {
            // Setup the interactor for this specific test case.
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_DISPLAY
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )

            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.currentTargetsModel).isInstanceOf(DisplaysViewModel::class.java)
            assertThat(viewModel.isUiVisible).isTrue()
        }

    @Test
    fun initialState_appContentSharingDisabled_showsAppSharing() =
        kosmos.runTest {
            // Setup the interactor for this specific test case.
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { isSourceEnabled(PROJECTION_SOURCE_APP_CONTENT) } doReturn false
                        on { isSourceEnabled(MediaProjectionConfig.PROJECTION_SOURCE_APP) } doReturn
                            true
                        on { isSourceEnabled(PROJECTION_SOURCE_DISPLAY) } doReturn true
                    }
            )

            assertThat(viewModel.isAppContentSharingEnabled).isFalse()
            assertThat(viewModel.currentTargetsModel).isInstanceOf(RecentTasksViewModel::class.java)
        }

    @Test
    fun initialState_appAndAppContentSharingDisabled_showsDisplaySharing() =
        kosmos.runTest {
            // Setup the interactor for this specific test case.
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { isSourceEnabled(PROJECTION_SOURCE_APP_CONTENT) } doReturn false
                        on { isSourceEnabled(MediaProjectionConfig.PROJECTION_SOURCE_APP) } doReturn
                            false
                        on { isSourceEnabled(PROJECTION_SOURCE_DISPLAY) } doReturn true
                    }
            )

            assertThat(viewModel.isAppContentSharingEnabled).isFalse()
            assertThat(viewModel.isAppSharingEnabled).isFalse()
            assertThat(viewModel.currentTargetsModel).isInstanceOf(DisplaysViewModel::class.java)
        }

    @Test
    fun onShareClicked_appTarget_sharingApproved() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            val isChipVisible by
                collectLastValue(kosmos.shareScreenPrivacyIndicatorInteractor.isChipVisible)
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            assertThat(isChipVisible).isFalse()
            val target = ScreenCaptureTarget.App(displayId = 1, taskId = 42)
            val fakeRecentTaskViewModel =
                kosmos.recentTaskViewModelFactory.create(
                    ScreenCaptureRecentTask(kosmos.fakeRecentTask)
                )

            // Setup the interactor's dependencies for this specific test.
            kosmos.fakeScreenCaptureRecentTaskRepository.setRecentTasks(kosmos.fakeRecentTask)
            whenever(
                    kosmos.mockAsyncActivityLauncher.startActivityAsUser(any(), any(), any(), any())
                )
                .thenAnswer {
                    // Immediately invoke the callback to simulate activity start.
                    it.getArgument<(WaitResult) -> Unit>(3).invoke(WaitResult())

                    kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                        MediaProjectionState.Projecting.SingleTask(
                            hostPackage = "FakeBasePackage",
                            hostDeviceName = null,
                            task = mock(),
                        )
                    true
                }
            kosmos.fakeMediaProjectionServiceHelperWrapper // Ensure the fake is initialized.

            kosmos.recentTasksViewModel.setSelectedTarget(fakeRecentTaskViewModel)
            viewModel.setTargetViewModel(target)

            viewModel.onShareClicked()

            // Verify UI is hidden
            assertThat(viewModel.isUiVisible).isFalse()
            // Verify chip is shown
            assertThat(isChipVisible).isTrue()
            // Verify the sharing state is updated to [Approved].
            assertThat(sharingState)
                .isInstanceOf(ShareScreenUiInteractor.SharingState.Approved::class.java)

            with(kosmos.fakeMediaProjectionServiceHelperWrapper) {
                assertThat(setReviewedConsentIfNeededCallCount).isEqualTo(1)
                assertThat(lastSetReviewedConsentResult)
                    .isEqualTo(ReviewGrantedConsentResult.RECORD_CONTENT_TASK)
            }
        }

    @Test
    fun onShareClicked_foregroundHostAppTarget_sharingApprovedWithoutLaunch() =
        kosmos.runTest {
            val projection = mock<android.media.projection.IMediaProjection>()
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            kosmos.shareScreenUiInteractor.initialize(
                projection = projection,
                reviewGrantedConsentRequired = true,
                hostUserHandle = UserHandle.of(100),
                uid = 100,
                packageName = context.packageName,
                initialDisplayId = 0,
                config = mock(),
            )
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            val target = ScreenCaptureTarget.App(displayId = 0, taskId = 42)

            // Task matches host package and is foreground
            val hostRecentTask =
                RecentTask(
                    taskId = 42,
                    displayId = 0,
                    userId = 100,
                    topActivityComponent = ComponentName(context.packageName, "HostClass"),
                    baseIntentComponent = ComponentName(context.packageName, "HostClass"),
                    baseIntent = Intent(),
                    colorBackground = 0,
                    isForegroundTask = true,
                    userType = UserType.MAIN,
                    splitBounds = null,
                )
            val fakeRecentTaskViewModel =
                kosmos.recentTaskViewModelFactory.create(ScreenCaptureRecentTask(hostRecentTask))

            kosmos.fakeScreenCaptureRecentTaskRepository.setRecentTasks(hostRecentTask)
            kosmos.fakeMediaProjectionServiceHelperWrapper // Ensure the fake is initialized.

            kosmos.recentTasksViewModel.setSelectedTarget(fakeRecentTaskViewModel)
            viewModel.setTargetViewModel(target)

            viewModel.onShareClicked()

            // Verify the sharing state is updated to [Approved].
            assertThat(sharingState)
                .isInstanceOf(ShareScreenUiInteractor.SharingState.Approved::class.java)

            // Verify setLaunchCookie and taskId were set on the projection binder
            org.mockito.kotlin.verify(projection).setLaunchCookie(any())
            org.mockito.kotlin.verify(projection).setTaskId(42)

            // Verify AsyncActivityLauncher was NEVER called
            org.mockito.kotlin
                .verify(kosmos.mockAsyncActivityLauncher, org.mockito.kotlin.never())
                .startActivityAsUser(any(), any(), any(), any())
        }

    @Test
    fun onShareClicked_foregroundOtherAppTarget_sharingApprovedWithLaunch() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            val target = ScreenCaptureTarget.App(displayId = 0, taskId = 42)

            // Task does NOT match host package but is foreground
            val otherRecentTask =
                RecentTask(
                    taskId = 42,
                    displayId = 0,
                    userId = 100,
                    topActivityComponent = ComponentName("OtherPackage", "OtherClass"),
                    baseIntentComponent = ComponentName("OtherPackage", "OtherClass"),
                    baseIntent = Intent(),
                    colorBackground = 0,
                    isForegroundTask = true,
                    userType = UserType.MAIN,
                    splitBounds = null,
                )
            val fakeRecentTaskViewModel =
                kosmos.recentTaskViewModelFactory.create(ScreenCaptureRecentTask(otherRecentTask))

            kosmos.fakeScreenCaptureRecentTaskRepository.setRecentTasks(otherRecentTask)
            whenever(
                    kosmos.mockAsyncActivityLauncher.startActivityAsUser(any(), any(), any(), any())
                )
                .thenAnswer {
                    // Immediately invoke the callback to simulate activity start.
                    it.getArgument<(WaitResult) -> Unit>(3).invoke(WaitResult())
                    true
                }
            kosmos.fakeMediaProjectionServiceHelperWrapper // Ensure the fake is initialized.

            kosmos.recentTasksViewModel.setSelectedTarget(fakeRecentTaskViewModel)
            viewModel.setTargetViewModel(target)

            viewModel.onShareClicked()

            // Verify the sharing state is updated to [Approved].
            assertThat(sharingState)
                .isInstanceOf(ShareScreenUiInteractor.SharingState.Approved::class.java)

            // Verify AsyncActivityLauncher WAS called
            org.mockito.kotlin
                .verify(kosmos.mockAsyncActivityLauncher, org.mockito.kotlin.atLeastOnce())
                .startActivityAsUser(any(), any(), any(), any())
        }

    @Test
    fun onShareClicked_nonForegroundHostAppTarget_sharingApprovedWithLaunch() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            val target = ScreenCaptureTarget.App(displayId = 0, taskId = 42)

            // Task matches host package but is NOT foreground
            val hostRecentTask =
                RecentTask(
                    taskId = 42,
                    displayId = 0,
                    userId = UserHandle.CURRENT.identifier,
                    topActivityComponent = ComponentName(context.packageName, "HostClass"),
                    baseIntentComponent = ComponentName(context.packageName, "HostClass"),
                    baseIntent = Intent(),
                    colorBackground = 0,
                    isForegroundTask = false,
                    userType = UserType.MAIN,
                    splitBounds = null,
                )
            val fakeRecentTaskViewModel =
                kosmos.recentTaskViewModelFactory.create(ScreenCaptureRecentTask(hostRecentTask))

            kosmos.fakeScreenCaptureRecentTaskRepository.setRecentTasks(hostRecentTask)

            val mockUserPackageManager = mock<PackageManager>()
            whenever(mockUserPackageManager.getLaunchIntentForPackage(any<String>()))
                .thenReturn(Intent())
            val mockUserContext = mock<Context>()
            whenever(mockUserContext.packageManager).thenReturn(mockUserPackageManager)
            val hostUserHandle = UserHandle.CURRENT
            context.prepareCreateContextAsUser(hostUserHandle, mockUserContext)

            whenever(
                    kosmos.mockAsyncActivityLauncher.startActivityAsUser(any(), any(), any(), any())
                )
                .thenAnswer {
                    // Immediately invoke the callback to simulate activity start.
                    it.getArgument<(WaitResult) -> Unit>(3).invoke(WaitResult())
                    true
                }
            kosmos.fakeMediaProjectionServiceHelperWrapper // Ensure the fake is initialized.

            kosmos.recentTasksViewModel.setSelectedTarget(fakeRecentTaskViewModel)
            viewModel.setTargetViewModel(target)

            viewModel.onShareClicked()

            // Verify the sharing state is updated to [Approved].
            assertThat(sharingState)
                .isInstanceOf(ShareScreenUiInteractor.SharingState.Approved::class.java)

            // Verify AsyncActivityLauncher WAS called despite being host app package
            org.mockito.kotlin
                .verify(kosmos.mockAsyncActivityLauncher, org.mockito.kotlin.atLeastOnce())
                .startActivityAsUser(any(), any(), any(), any())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onShareClicked_displayTarget_sharingApproved() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            val target = ScreenCaptureTarget.Fullscreen(displayId = 42)
            val fakeAndroidDisplay =
                mock<Display> {
                    on { displayId } doReturn 42
                    on { name } doReturn "test display"
                }
            kosmos.displayRepository.emit(setOf(fakeAndroidDisplay))
            viewModel.setTargetViewModel(target)

            val display = kosmos.displaysViewModel.targets.value!!.first()
            val fakeDisplayViewModel = displayViewModelFactory.create(display)
            kosmos.displaysViewModel.setSelectedTarget(fakeDisplayViewModel)
            kosmos.fakeMediaProjectionServiceHelperWrapper // Ensure the fake is initialized.

            viewModel.onShareClicked()

            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )

            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            assertThat(sharingState)
                .isInstanceOf(ShareScreenUiInteractor.SharingState.Approved::class.java)

            with(kosmos.fakeMediaProjectionServiceHelperWrapper) {
                assertThat(setReviewedConsentIfNeededCallCount).isEqualTo(1)
                assertThat(lastSetReviewedConsentResult)
                    .isEqualTo(ReviewGrantedConsentResult.RECORD_CONTENT_DISPLAY)
            }
        }

    @Test
    fun onCloseClicked_sharingDenied() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            viewModel.onCloseClicked()
            assertThat(sharingState).isEqualTo(ShareScreenUiInteractor.SharingState.Denied)
        }

    @Test
    fun onShareClicked_appContentTarget_sharingApproved() =
        kosmos.runTest {
            setupViewModel(
                config =
                    mock<MediaProjectionConfig> {
                        on { initiallySelectedSource } doReturn PROJECTION_SOURCE_APP_CONTENT
                        on { isSourceEnabled(any()) } doReturn true
                    }
            )
            val isChipVisible by
                collectLastValue(kosmos.shareScreenPrivacyIndicatorInteractor.isChipVisible)
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            assertThat(isChipVisible).isFalse()

            val target = ScreenCaptureTarget.AppContent(contentId = 123)

            // The view model starts in the App Contents tab.
            val appContentsViewModel = viewModel.currentTargetsModel as AppContentsViewModel

            // Create a fake app content target and its view model.
            val fakeMediaProjectionAppContent =
                MediaProjectionAppContent.Builder(123)
                    .setThumbnail(createBitmap(200, 100))
                    .setTitle("FakeLabel")
                    .build()
            val fakeCallback = FakeAppContentProjectionCallback(context)

            // Populate the view model with the fake target.
            kosmos.fakeScreenCaptureRecentTaskRepository.setRecentTasks(kosmos.fakeRecentTask)
            kosmos.fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = context.packageName,
                user = UserHandle.CURRENT,
                listOf(fakeMediaProjectionAppContent),
                WeakReference(fakeCallback),
            )

            // Select the fake target.
            val appContentTarget = appContentsViewModel.targets.value?.first()
            val appContentTargetViewModel =
                appContentTarget?.let { appContentsViewModel.createViewModelFor(it) }
            appContentsViewModel.setSelectedTarget(appContentTargetViewModel)
            viewModel.setTargetViewModel(target)
            kosmos.fakeMediaProjectionServiceHelperWrapper // Ensure the fake is initialized.

            // Act
            viewModel.onShareClicked()

            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "FakeBasePackage",
                    hostDeviceName = null,
                    task = mock(),
                )

            // Assert
            assertThat(viewModel.isUiVisible).isFalse()
            assertThat(isChipVisible).isTrue()
            assertThat(sharingState)
                .isInstanceOf(ShareScreenUiInteractor.SharingState.Approved::class.java)

            with(kosmos.fakeMediaProjectionServiceHelperWrapper) {
                assertThat(setReviewedConsentIfNeededCallCount).isEqualTo(1)
                assertThat(lastSetReviewedConsentResult)
                    .isEqualTo(ReviewGrantedConsentResult.RECORD_CONTENT_TASK)
            }
        }
}
