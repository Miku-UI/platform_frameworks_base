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

package com.android.systemui.accessibility.data.repository

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.res.mainResources
import android.hardware.input.KeyGestureEvent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.text.Annotation
import android.text.Spanned
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags
import com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AccessibilityShortcutsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val packageManager = kosmos.packageManager
    private val userTracker = kosmos.userTracker
    private val resources = kosmos.mainResources
    private val testScope = kosmos.testScope

    @get:Rule val setFlagsRule = SetFlagsRule()

    // mocks
    private val accessibilityManager: AccessibilityManager = mock(AccessibilityManager::class.java)

    private lateinit var underTest: AccessibilityShortcutsRepositoryImpl

    @Before
    fun setUp() {
        underTest =
            AccessibilityShortcutsRepositoryImpl(
                context,
                accessibilityManager,
                packageManager,
                userTracker,
                resources,
                kosmos.testDispatcher,
            )
    }

    @Test
    fun getTitleToContentForKeyGestureDialog_nonExistTypeReceived_isNull() {
        testScope.runTest {
            // Just test a random non-accessibility service type
            val titleToContent =
                underTest.getTitleToContentForKeyGestureDialog(
                    KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                    0,
                    0,
                    "empty",
                )

            assertThat(titleToContent).isNull()
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    @Test
    fun getTitleToContentForKeyGestureDialog_onMagnificationTypeReceived_doNotEnableShortcut_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON

            val titleToContent =
                underTest.getTitleToContentForKeyGestureDialog(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    metaState,
                    KeyEvent.KEYCODE_M,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION),
                )

            assertThat(titleToContent).isNotNull()
            assertThat(titleToContent?.first).isEqualTo("Magnification keyboard shortcut turned on")
            val contentText = titleToContent?.second
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText?.toString())
                .isEqualTo(
                    "Action icon + Alt + M is the keyboard shortcut to use Magnification, an" +
                        " accessibility feature. This allows you to quickly zoom in on the screen" +
                        " to make content larger. Once magnification is on, press Action icon +" +
                        " Alt and \"+\" or \"-\" to adjust zoom."
                )
        }
    }

    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    @Test
    fun getTitleToContentForKeyGestureDialog_onMagnificationTypeReceived_enableShortcut_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON

            val titleToContent =
                underTest.getTitleToContentForKeyGestureDialog(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    metaState,
                    KeyEvent.KEYCODE_M,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION),
                )

            assertThat(titleToContent).isNotNull()
            assertThat(titleToContent?.first).isEqualTo("Turn on Magnification keyboard shortcut?")
            val contentText = titleToContent?.second
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText?.toString())
                .isEqualTo(
                    "Action icon + Alt + M is the keyboard shortcut to use Magnification, an" +
                        " accessibility feature. This allows you to quickly zoom in on the screen" +
                        " to make content larger. Once magnification is on, press Action icon +" +
                        " Alt and \"+\" or \"-\" to adjust zoom."
                )
        }
    }

    @Test
    fun getTitleToContentForKeyGestureDialog_serviceUninstalled_isNull() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            // If voice access isn't installed on device.
            whenever(accessibilityManager.getInstalledServiceInfoWithComponentName(anyOrNull()))
                .thenReturn(null)

            val titleToContent =
                underTest.getTitleToContentForKeyGestureDialog(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS),
                )

            assertThat(titleToContent).isNull()
        }
    }

    @Test
    fun getTitleToContentForKeyGestureDialog_onVoiceAccessTypeReceived_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val type = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("Voice Access"))
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(type))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val titleToContent =
                underTest.getTitleToContentForKeyGestureDialog(
                    type,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(type),
                )

            assertThat(titleToContent).isNotNull()
            assertThat(titleToContent?.first).isEqualTo("Turn on Voice Access keyboard shortcut?")
            val contentText = titleToContent?.second
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // The intro should be the string below instead of the intro from
            // AccessibilityServiceInfo.
            assertThat(contentText?.toString())
                .isEqualTo(
                    "Pressing Action icon + Alt + V turns on Voice Access, an accessibility" +
                        " feature. This lets you control your device hands-free."
                )
        }
    }

    @Test
    fun enableShortcutsForTargets_targetNameForMagnification_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)

        underTest.enableShortcutsForTargets(enable = true, targetName)

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun enableShortcutsForTargets_targetNameForS2S_enabled() {
        val targetName =
            getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK)

        underTest.enableShortcutsForTargets(enable = true, targetName)

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun enableShortcutsForTargets_targetNameForVoiceAccess_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS)

        underTest.enableShortcutsForTargets(enable = true, targetName)

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun enableShortcutsForTargets_targetNameForTalkBack_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)

        underTest.enableShortcutsForTargets(enable = true, targetName)

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    private fun getMockAccessibilityServiceInfo(featureName: String): AccessibilityServiceInfo {
        val packageName = "com.android.test"
        val componentName = ComponentName(packageName, "$packageName.test_a11y_service")

        val applicationInfo = mock(ApplicationInfo::class.java)
        applicationInfo.packageName = componentName.packageName

        val serviceInfo = spy(ServiceInfo())
        serviceInfo.packageName = componentName.packageName
        serviceInfo.name = componentName.className
        serviceInfo.applicationInfo = applicationInfo

        val resolveInfo = mock(ResolveInfo::class.java)
        resolveInfo.serviceInfo = serviceInfo
        whenever(resolveInfo.loadLabel(any())).thenReturn(featureName)

        val a11yServiceInfo = AccessibilityServiceInfo(resolveInfo, context)
        a11yServiceInfo.componentName = componentName
        return a11yServiceInfo
    }

    private fun getTargetNameByType(keyGestureType: Int): String {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION -> MAGNIFICATION_CONTROLLER_NAME
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK ->
                resources.getString(
                    com.android.internal.R.string.config_defaultSelectToSpeakService
                )

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS ->
                resources.getString(com.android.internal.R.string.config_defaultVoiceAccessService)

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                resources.getString(
                    com.android.internal.R.string.config_defaultAccessibilityService
                )

            else -> ""
        }
    }

    // Return true if the text contains the expected annotation.
    private fun hasExpectedAnnotation(text: CharSequence?): Boolean {
        if (text == null || text !is Spanned) {
            return false
        }

        val annotations = text.getSpans(0, text.length, Annotation::class.java)
        for (annotation in annotations) {
            if (annotation.key == "id" && annotation.value == "action_key_icon") {
                return true
            }
        }
        return false
    }
}
