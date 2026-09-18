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

package com.android.wm.shell.apptoweb

import android.app.assist.AssistContent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.net.Uri
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.test.mock.MockContext
import android.testing.AndroidTestingRunner
import androidx.core.net.toUri
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.apptoweb.data.AppToWebDatastoreRepository
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.util.createTaskInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [AppToWebRepositoryImpl].
 *
 * Build/Install/Run: atest WMShellUnitTests:AppToWebRepositoryImplTests
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class AppToWebRepositoryImplTests : ShellTestCase() {

    @Mock private lateinit var mockAssistContentRequester: AssistContentRequester
    @Mock private lateinit var mockGenericLinksParser: AppToWebGenericLinksParser
    @Mock private lateinit var mockAssistContent: AssistContent
    @Mock private lateinit var mockContext: MockContext
    @Mock private lateinit var mockPackageManager: PackageManager
    @Mock private lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock private lateinit var mockResources: Resources
    @Mock private lateinit var appToWebDatastoreRepository: AppToWebDatastoreRepository
    @Mock private lateinit var launcherApps: LauncherApps
    @Mock private lateinit var shellController: ShellController
    @Mock private lateinit var shellCommandHandler: ShellCommandHandler
    private val testScope = TestScope()

    private lateinit var mockInit: AutoCloseable
    private val testExecutor = TestShellExecutor()
    private val shellInit = ShellInit(testExecutor)
    private lateinit var appToWebRepository: AppToWebRepositoryImpl
    private val taskInfo =
        createTaskInfo().apply {
            taskId = TEST_TASK_ID
            baseActivity = ComponentName("appToWeb", "testActivity")
            topActivity = baseActivity
        }
    private val resolveInfo =
        ResolveInfo().apply {
            activityInfo = ActivityInfo()
            activityInfo.packageName = "appToWeb"
            activityInfo.name = "testActivity"
        }

    private lateinit var launcherAppsCallback: LauncherApps.Callback

    @Before
    fun setUp() {
        mockInit = MockitoAnnotations.openMocks(this)
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.resolveActivityAsUser(any(), anyInt(), anyInt()))
            .thenReturn(resolveInfo)
        whenever(mockContext.resources).thenReturn(mockResources)
        setAppToWebActivePrompting(true)
        appToWebRepository =
            AppToWebRepositoryImpl(
                mockContext,
                mockAssistContentRequester,
                mockGenericLinksParser,
                appToWebDatastoreRepository,
                testScope,
                testScope.backgroundScope,
                shellTaskOrganizer,
                launcherApps,
                shellInit,
                shellController,
                shellCommandHandler,
            )
        shellInit.init()

        if (Flags.enableEnhancedAppToWebTransition()) {
            val callbackCaptor = argumentCaptor<LauncherApps.Callback>()
            verify(launcherApps).registerCallback(callbackCaptor.capture())
            launcherAppsCallback = callbackCaptor.firstValue
            whenever(
                    mockResources.getStringArray(
                        R.array.config_appToWebFirstRunPromptExemptPackages
                    )
                )
                .thenReturn(emptyArray<String>())
        }
    }

    @After
    fun tearDown() {
        testScope.cancel()
        mockInit.close()
    }

    @Test
    fun capturedLink_unavailableAfterUse() {
        appToWebRepository.setCapturedLink(
            taskId = taskInfo.taskId,
            link = TEST_CAPTURED_URI,
            timeStamp = 100,
        )
        appToWebRepository.onCapturedLinkUsed(taskInfo.taskId)
        assertFalse(appToWebRepository.isCapturedLinkAvailable(taskInfo.taskId))
    }

    @Test
    fun capturedLink_sameCapturedLinkNotSavedAgain() {
        val timeStamp = 100L
        appToWebRepository.setCapturedLink(taskInfo.taskId, TEST_CAPTURED_URI, timeStamp)
        appToWebRepository.onCapturedLinkUsed(taskInfo.taskId)
        // Set the same captured link (same timeStamp)
        appToWebRepository.setCapturedLink(taskInfo.taskId, TEST_CAPTURED_URI, timeStamp)
        assertFalse(appToWebRepository.isCapturedLinkAvailable(taskInfo.taskId))
    }

    @Test
    fun webUri_prioritizedOverCapturedAndGenericLinks() = runTest {
        // Set captured link
        appToWebRepository.setCapturedLink(
            taskInfo.taskId,
            link = TEST_CAPTURED_URI,
            timeStamp = 100,
        )
        // Set web Uri
        whenever(mockAssistContent.getSessionWebUri()).thenReturn(TEST_WEB_URI)
        mockAssistContent.webUri = TEST_WEB_URI
        // Set generic link
        whenever(mockGenericLinksParser.getGenericLink(any())).thenReturn(TEST_GENERIC_LINK)

        assertAppToWebIntent(TEST_WEB_URI)
    }

    @Test
    fun capturedLink_prioritizedOverGenericLink() = runTest {
        // Set captured link
        appToWebRepository.setCapturedLink(
            taskId = taskInfo.taskId,
            link = TEST_CAPTURED_URI,
            timeStamp = 100,
        )
        // Set generic link
        whenever(mockGenericLinksParser.getGenericLink(any())).thenReturn(TEST_GENERIC_LINK)

        assertAppToWebIntent(TEST_CAPTURED_URI)
    }

    @Test
    fun genericLink_utilizedWhenAllOtherLinksAreUnavailable() = runTest {
        // Set generic link
        whenever(mockGenericLinksParser.getGenericLink(any())).thenReturn(TEST_GENERIC_LINK)

        assertAppToWebIntent(TEST_GENERIC_LINK.toUri())
    }

    @Test
    fun webUri_usedWhenIsBrowserApp() = runTest {
        // Set web Uri
        whenever(mockAssistContent.getSessionWebUri()).thenReturn(TEST_WEB_URI)

        assertAppToWebIntent(expectedUri = TEST_WEB_URI, isBrowserApp = true)
    }

    @Test
    fun taskData_removedOnTaskVanished() {
        // Set captured link for task
        appToWebRepository.setCapturedLink(
            taskId = taskInfo.taskId,
            link = TEST_CAPTURED_URI,
            timeStamp = 100,
        )

        // Notify repo task is vanishing. Data should be cleared
        appToWebRepository.onTaskVanished(taskInfo)

        // Verify task data was cleared
        assertFalse(appToWebRepository.isCapturedLinkAvailable(taskInfo.taskId))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_isFirstRunPromptShown() {
        // TaskInfo with the same package as `taskInfo` but with different taskId.
        val taskInfo2 =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = taskInfo.baseActivity
                topActivity = taskInfo.topActivity
            }
        assertFalse(appToWebRepository.isFirstRunPromptShown(taskInfo))
        assertFalse(appToWebRepository.isFirstRunPromptShown(taskInfo2))

        // Show the first-run prompt for `taskInfo`.
        appToWebRepository.onFirstRunPromptShown(taskInfo)

        assertTrue(appToWebRepository.isFirstRunPromptShown(taskInfo))
        assertFalse(appToWebRepository.isFirstRunPromptShown(taskInfo2))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_removedOnTaskVanished() {
        // Show the first-run prompt for `taskInfo`.
        appToWebRepository.onFirstRunPromptShown(taskInfo)
        assertTrue(appToWebRepository.isFirstRunPromptShown(taskInfo))

        // Notify task is vanishing. Data should be cleared
        appToWebRepository.onTaskVanished(taskInfo)

        assertFalse(appToWebRepository.isFirstRunPromptShown(taskInfo))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_resourceFlag() {
        val taskInfoWithCapturedLink =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = taskInfo.baseActivity
                topActivity = taskInfo.topActivity
                capturedLink = TEST_CAPTURED_URI
            }

        setAppToWebActivePrompting(false)
        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))

        setAppToWebActivePrompting(true)
        assertTrue(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_packageRemoved() {
        val taskInfoWithCapturedLink =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = taskInfo.baseActivity
                topActivity = taskInfo.topActivity
                capturedLink = TEST_CAPTURED_URI
            }
        // Ack the first-run prompt for `taskInfo`.
        appToWebRepository.onFirstRunPromptAcked(taskInfo)
        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))

        // Package is removed.
        launcherAppsCallback.onPackageRemoved(
            taskInfo.baseActivity!!.packageName,
            UserHandle.of(taskInfo.userId),
        )

        assertTrue(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_packageUnavailable() {
        val taskInfoWithCapturedLink =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = taskInfo.baseActivity
                topActivity = taskInfo.topActivity
                capturedLink = TEST_CAPTURED_URI
            }
        // Ack the first-run prompt for `taskInfo`.
        appToWebRepository.onFirstRunPromptAcked(taskInfo)
        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))

        // Package becomes unavailable.
        launcherAppsCallback.onPackagesUnavailable(
            arrayOf(taskInfo.baseActivity!!.packageName),
            UserHandle.of(taskInfo.userId),
            false,
        )

        assertTrue(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_alreadyShown() {
        val taskInfoWithCapturedLink =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = taskInfo.baseActivity
                topActivity = taskInfo.topActivity
                capturedLink = TEST_CAPTURED_URI
            }
        assertTrue(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))

        appToWebRepository.onFirstRunPromptShown(taskInfoWithCapturedLink)

        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_browserApp() {
        val browserPackageName = "com.android.chrome"
        val nonBrowserPackageName = "not.browser"
        val browserResolveInfo =
            ResolveInfo().apply {
                activityInfo = ActivityInfo()
                handleAllWebDataURI = true
            }
        whenever(mockPackageManager.queryIntentActivitiesAsUser(any(), anyInt(), anyInt()))
            .thenAnswer {
                val intent = it.getArgument<Intent>(0)
                if (intent.getPackage() == browserPackageName) {
                    listOf(browserResolveInfo)
                } else {
                    emptyList()
                }
            }
        val taskInfoWithCapturedLink =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = ComponentName(nonBrowserPackageName, "")
                topActivity = ComponentName(nonBrowserPackageName, "")
                capturedLink = TEST_CAPTURED_URI
            }
        assertTrue(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))

        // Verify when the base activity is browser
        taskInfoWithCapturedLink.baseActivity = ComponentName(browserPackageName, "")
        taskInfoWithCapturedLink.topActivity = ComponentName(nonBrowserPackageName, "")
        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))

        // Verify when the top activity is browser
        taskInfoWithCapturedLink.baseActivity = ComponentName(nonBrowserPackageName, "")
        taskInfoWithCapturedLink.topActivity = ComponentName(browserPackageName, "")
        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithCapturedLink))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun firstRunPrompt_exemptPackage() {
        val exemptPackageName = "exempt.package"
        whenever(mockResources.getStringArray(R.array.config_appToWebFirstRunPromptExemptPackages))
            .thenReturn(arrayOf(exemptPackageName))

        val exemptTaskInfo =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = ComponentName(exemptPackageName, "")
                topActivity = baseActivity
                capturedLink = TEST_CAPTURED_URI
            }
        val nonExemptTaskInfo =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 2
                baseActivity = ComponentName("non.exempt.package", "")
                topActivity = baseActivity
                capturedLink = TEST_CAPTURED_URI
            }

        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(exemptTaskInfo))
        assertTrue(appToWebRepository.shouldShowFirstRunPrompt(nonExemptTaskInfo))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun shouldShowFirstRunPrompt_returnsFalse_withRequireNonBrowserFlag() {
        val taskInfoWithFlag =
            createTaskInfo().apply {
                taskId = taskInfo.taskId + 1
                baseActivity = taskInfo.baseActivity
                capturedLink = TEST_CAPTURED_URI
                baseIntent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER) }
            }
        assertFalse(appToWebRepository.shouldShowFirstRunPrompt(taskInfoWithFlag))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun onInit_registersCallback_whenPromptIsSupported() {
        // Reset launcherApps mock to clear invocations from setUp()
        reset(launcherApps)

        // Re-initialize with active prompting enabled
        setAppToWebActivePrompting(true)
        val newShellInit = ShellInit(testExecutor)
        AppToWebRepositoryImpl(
            mockContext,
            mockAssistContentRequester,
            mockGenericLinksParser,
            appToWebDatastoreRepository,
            testScope,
            testScope.backgroundScope,
            shellTaskOrganizer,
            launcherApps,
            newShellInit,
            shellController,
            shellCommandHandler,
        )
        newShellInit.init()

        // Verify that the callback is registered
        verify(launcherApps).registerCallback(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION)
    fun onInit_doesNotRegisterCallback_whenPromptNotSupported() {
        // Reset launcherApps mock to clear invocations from setUp()
        reset(launcherApps)

        // Re-initialize with active prompting disabled
        setAppToWebActivePrompting(false)
        val newShellInit = ShellInit(testExecutor)
        AppToWebRepositoryImpl(
            mockContext,
            mockAssistContentRequester,
            mockGenericLinksParser,
            appToWebDatastoreRepository,
            testScope,
            testScope.backgroundScope,
            shellTaskOrganizer,
            launcherApps,
            newShellInit,
            shellController,
            shellCommandHandler,
        )
        newShellInit.init()

        // Verify that the callback is not registered
        verify(launcherApps, never()).registerCallback(any())
    }

    private suspend fun assertAppToWebIntent(expectedUri: Uri, isBrowserApp: Boolean = false) {
        whenever(mockAssistContentRequester.requestAssistContent(anyInt(), any())).thenAnswer {
            invocation ->
            val callback = invocation.arguments[1] as AssistContentRequester.Callback
            callback.onAssistContentAvailable(mockAssistContent)
        }

        assertEquals(
            expectedUri,
            appToWebRepository
                .getAppToWebIntent(taskInfo = taskInfo, isBrowserApp = isBrowserApp)
                ?.data,
        )
    }

    private fun setAppToWebActivePrompting(enabled: Boolean) {
        whenever(mockResources.getBoolean(R.bool.config_appToWebActivePrompting))
            .thenReturn(enabled)
    }

    private companion object {
        private const val TEST_TASK_ID = 1
        private val TEST_WEB_URI = Uri.parse("http://www.youtube.com")
        private val TEST_CAPTURED_URI = Uri.parse("www.google.com")
        private const val TEST_GENERIC_LINK = "http://www.gmail.com"
    }
}
