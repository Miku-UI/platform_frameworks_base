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

package com.android.wm.shell.gamecontrols

import android.app.TaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.content.res.Resources
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Test class for [GameControlsHelper].
 *
 * Usage: atest WMShellUnitTests:GameControlsHelperTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_GAME_CONTROLS_HANDLE_MENU_ENTRY)
class GameControlsHelperTest : ShellTestCase() {

    private val mockContext = mock<Context>()
    private val mockPackageManager = mock<PackageManager>()
    private val mockResources = mock<Resources>()
    private val mockTaskInfo = mock<TaskInfo>()
    private val mockActivityInfo = mock<ActivityInfo>()
    private val mockApplicationInfo = mock<ApplicationInfo>()
    private val mockResolveInfo = mock<ResolveInfo>()

    @Before
    fun setUp() {
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(
                mockPackageManager.getApplicationInfoAsUser(
                    any<String>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(mockApplicationInfo)
        mockTaskInfo.topActivityInfo = mockActivityInfo
        mockTaskInfo.baseActivity = ComponentName("com.test.package", "com.test.activity")
        mockActivityInfo.applicationInfo = mockApplicationInfo

        mockApplicationInfo.category = ApplicationInfo.CATEGORY_GAME
        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature))
            .thenReturn("some.feature")
        whenever(mockPackageManager.hasSystemFeature("some.feature")).thenReturn(true)
        whenever(mockPackageManager.queryBroadcastReceivers(any<Intent>(), any<Int>()))
            .thenReturn(listOf(mockResolveInfo))
        whenever(mockResources.getString(R.string.config_gameControlsIntentReceiverPackage))
            .thenReturn("com.test.package")
        whenever(mockResources.getString(R.string.config_gameControlsIntentAction))
            .thenReturn("com.test.ACTION")
        whenever(mockResources.getString(R.string.config_gameControlsOptOutMetadataKey))
            .thenReturn("GameControlsOptOut")
    }

    @Test
    fun shouldShowGameControlsButton_returnsTrue_whenAllConditionsMet() {

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isTrue()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenNotGame() {
        mockApplicationInfo.category = ApplicationInfo.CATEGORY_UNDEFINED

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenNoSystemFeature() {
        whenever(mockPackageManager.hasSystemFeature("some.feature")).thenReturn(false)

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenSystemFeatureIsEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature)).thenReturn("")

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenAppInfoIsNull() {
        mockTaskInfo.topActivityInfo = null

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenNoBroadcastReceiver() {
        whenever(mockPackageManager.queryBroadcastReceivers(any<Intent>(), any<Int>()))
            .thenReturn(emptyList())

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenPackageEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentReceiverPackage))
            .thenReturn("")

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenActionEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentAction)).thenReturn("")

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsTrue_whenNoOptOutData() {
        val OPT_OUT_METADATA_KEY = ""
        whenever(mockResources.getString(R.string.config_gameControlsOptOutMetadataKey))
            .thenReturn(OPT_OUT_METADATA_KEY)
        // mockApplicationInfo.metaData is null by default, simulating no metadata.

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isTrue()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenOptOutTagIsTrue() {
        val OPT_OUT_METADATA_KEY = "GameControlsOptOut"
        whenever(mockResources.getString(R.string.config_gameControlsOptOutMetadataKey))
            .thenReturn(OPT_OUT_METADATA_KEY)

        mockApplicationInfo.metaData = Bundle()
        mockApplicationInfo.metaData.putBoolean(OPT_OUT_METADATA_KEY, true)

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun onLaunchGameControls_sendsBroadcast() {
        val intentCaptor = argumentCaptor<Intent>()
        val userCaptor = argumentCaptor<UserHandle>()
        mockTaskInfo.userId = 10
        mockTaskInfo.taskId = 123

        GameControlsHelper.onLaunchGameControls(mockContext, mockTaskInfo)

        verify(mockContext, times(1))
            .sendBroadcastAsUser(intentCaptor.capture(), userCaptor.capture())
        val intent = intentCaptor.firstValue
        assertThat(intent.action).isEqualTo("com.test.ACTION")
        assertThat(intent.`package`).isEqualTo("com.test.package")
        assertThat(intent.getIntExtra(Intent.EXTRA_TASK_ID, -1)).isEqualTo(mockTaskInfo.taskId)
        val user = userCaptor.firstValue
        assertThat(user.identifier).isEqualTo(mockTaskInfo.userId)
    }

    @Test
    fun onLaunchGameControls_noBroadcast_whenPackageEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentReceiverPackage))
            .thenReturn("")

        GameControlsHelper.onLaunchGameControls(mockContext, mockTaskInfo)

        verify(mockContext, never()).sendBroadcastAsUser(any(), any())
    }

    @Test
    fun onLaunchGameControls_noBroadcast_whenActionEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentAction)).thenReturn("")

        GameControlsHelper.onLaunchGameControls(mockContext, mockTaskInfo)

        verify(mockContext, never()).sendBroadcastAsUser(any(), any())
    }
}
