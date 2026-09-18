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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.input.KeyGestureEvent
import android.os.Build
import android.os.fakeExecutorHandler
import android.provider.Settings
import android.text.Annotation
import android.text.Spanned
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.accessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.AccessibilityShortcutController.COLOR_INVERSION_COMPONENT_NAME
import com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.ShortcutUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceUntilIdle
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.settings.userTracker
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AccessibilityShortcutsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            AccessibilityShortcutsRepositoryImpl(
                context.apply {
                    addMockSystemService(AccessibilityManager::class.java, accessibilityManager)
                },
                accessibilityManager,
                packageManager,
                userTracker,
                secureSettingsRepository,
                mainResources,
                testDispatcher,
                fakeExecutorHandler,
            )
        }

    @Before
    fun setUp() {
        with(kosmos) {
            whenever(packageManager.getDrawable(anyString(), anyInt(), any()))
                .thenReturn(ColorDrawable(Color.RED))
        }
    }

    @Test
    fun getKeyGestureConfirmInfo_nonExistTypeReceived_isNull() =
        kosmos.runTest {
            // Just test a random non-accessibility service type
            val info =
                underTest.getKeyGestureConfirmInfo(
                    KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                    0,
                    0,
                    "empty",
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNull()
        }

    @Test
    fun getKeyGestureConfirmInfo_onColorInversionTypeReceived_getExpectedInfo() =
        kosmos.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val type = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION

            val info =
                underTest.getKeyGestureConfirmInfo(
                    type,
                    metaState,
                    KeyEvent.KEYCODE_I,
                    getTargetNameByType(type),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on Color inversion keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + I is the keyboard shortcut to use color inversion. Color" +
                        " inversion turns light screens dark. It also turns dark screens light."
                )
        }

    @Test
    fun getKeyGestureConfirmInfo_onMagnificationTypeReceived_getExpectedInfo() =
        kosmos.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION

            val info =
                underTest.getKeyGestureConfirmInfo(
                    keyGestureType,
                    metaState,
                    KeyEvent.KEYCODE_M,
                    getTargetNameByType(keyGestureType),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Magnification keyboard shortcut turned on")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + M is the keyboard shortcut to use Magnification, an" +
                        " accessibility feature. This allows you to quickly zoom in on the screen" +
                        " to make content larger. Once magnification is on, press Action icon +" +
                        " Alt and \"+\" or \"-\" to adjust zoom."
                )
        }

    @Test
    fun getKeyGestureConfirmInfo_onSelectToSpeakTypeReceived_getExpectedInfo() =
        kosmos.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("Select to Speak"))
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(keyGestureType))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    keyGestureType,
                    metaState,
                    KeyEvent.KEYCODE_S,
                    getTargetNameByType(keyGestureType),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on Select to Speak keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + S is the keyboard shortcut to use Select to Speak. " +
                        "This allows you to customize font and spacing and hear text read " +
                        "aloud right on your device."
                )
        }

    @Test
    fun getKeyGestureConfirmInfo_onVoiceAccessTypeReceived_getExpectedInfo() =
        kosmos.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("Voice Access"))
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(keyGestureType))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    keyGestureType,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(keyGestureType),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on Voice Access keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // The intro should be the string below instead of the intro from
            // AccessibilityServiceInfo.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Pressing Action icon + Alt + V turns on Voice Access, an accessibility" +
                        " feature. This lets you control your device hands-free."
                )
        }

    @Test
    fun getKeyGestureConfirmInfo_onScreenReaderTypeReceived_getExpectedInfo() =
        kosmos.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("TalkBack"))
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(keyGestureType))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    keyGestureType,
                    metaState,
                    KeyEvent.KEYCODE_T,
                    getTargetNameByType(keyGestureType),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on TalkBack keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + T is the keyboard shortcut to use TalkBack. TalkBack is a" +
                        " screen reader that allows you to hear items spoken aloud. It can be" +
                        " helpful for people who have difficulty seeing the screen. This may" +
                        " change how your device works."
                )
            assertThat(info.contentSections).hasSize(4)
            assertThat(info.contentSections[0].heading)
                .isEqualTo(
                    "By turning on the keyboard shortcut, you will allow TalkBack to have full control of your device."
                )
            assertThat(info.contentSections[0].message).isNull()
            assertThat(info.contentSections[1].heading).isNull()
            assertThat(info.contentSections[1].message)
                .isEqualTo(
                    "Full control is appropriate for apps that help you with accessibility needs," +
                        " but not for most apps."
                )
            assertThat(info.contentSections[2].heading).isEqualTo("View and control screen")
            assertThat(info.contentSections[2].message)
                .isEqualTo(
                    "It can read all content on the screen and display content over other apps."
                )
            assertThat(info.contentSections[3].heading).isEqualTo("View and perform actions")
            assertThat(info.contentSections[3].message)
                .isEqualTo(
                    "It can track your interactions with an app or a hardware sensor, and" +
                        " interact with apps on your behalf."
                )
        }

    @Test
    fun getKeyGestureConfirmInfo_serviceUninstalled_isNull() =
        kosmos.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            // If voice access isn't installed on device.
            whenever(accessibilityManager.getInstalledServiceInfoWithComponentName(anyOrNull()))
                .thenReturn(null)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNull()
        }

    @Test
    fun enableShortcutsForTargets_targetNameForMagnification_enabled() =
        kosmos.runTest {
            val targetNames =
                setOf(getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION))

            underTest.enableShortcutsForTargets(
                enable = true,
                UserShortcutType.KEY_GESTURE,
                targetNames,
            )

            verify(accessibilityManager)
                .enableShortcutsForTargets(
                    eq(true),
                    eq(UserShortcutType.KEY_GESTURE),
                    eq(targetNames),
                    anyInt(),
                )
        }

    @Test
    fun enableShortcutsForTargets_targetNameForS2S_enabled() =
        kosmos.runTest {
            val targetNames =
                setOf(
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK)
                )

            underTest.enableShortcutsForTargets(
                enable = true,
                UserShortcutType.KEY_GESTURE,
                targetNames,
            )

            verify(accessibilityManager)
                .enableShortcutsForTargets(
                    eq(true),
                    eq(UserShortcutType.KEY_GESTURE),
                    eq(targetNames),
                    anyInt(),
                )
        }

    @Test
    fun enableShortcutsForTargets_targetNameForVoiceAccess_enabled() =
        kosmos.runTest {
            val targetNames =
                setOf(getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS))

            underTest.enableShortcutsForTargets(
                enable = true,
                UserShortcutType.KEY_GESTURE,
                targetNames,
            )

            verify(accessibilityManager)
                .enableShortcutsForTargets(
                    eq(true),
                    eq(UserShortcutType.KEY_GESTURE),
                    eq(targetNames),
                    anyInt(),
                )
        }

    @Test
    fun enableShortcutsForTargets_targetNameForTalkBack_enabled() =
        kosmos.runTest {
            val targetNames =
                setOf(getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER))

            underTest.enableShortcutsForTargets(
                enable = true,
                UserShortcutType.KEY_GESTURE,
                targetNames,
            )

            verify(accessibilityManager)
                .enableShortcutsForTargets(
                    eq(true),
                    eq(UserShortcutType.KEY_GESTURE),
                    eq(targetNames),
                    anyInt(),
                )
        }

    @Test
    fun performAccessibilityShortcut_topRowKey_targetNameForTalkBack_performed() =
        kosmos.runTest {
            val targetName =
                getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)

            underTest.performAccessibilityShortcut(
                DEFAULT_DISPLAY,
                UserShortcutType.TOP_ROW_KEY,
                targetName,
            )

            verify(accessibilityManager)
                .performAccessibilityShortcut(
                    eq(DEFAULT_DISPLAY),
                    eq(UserShortcutType.TOP_ROW_KEY),
                    eq(targetName),
                )
        }

    @Test
    fun getAllAccessibilityTargets_returnsAccessibilityTargetModels() =
        kosmos.runTest {
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(listOf(getMockAccessibilityServiceInfo("Test Service")))

            val targets = underTest.getAllAccessibilityTargets(UserShortcutType.HARDWARE).first()

            assertThat(targets.any { it.featureName == "Test Service" }).isTrue()
            assertThat(targets.any { it.featureName == "Magnification" }).isTrue()
            assertThat(targets.any { it.featureName == "Mouse keys" }).isTrue()
        }

    @Test
    fun getAllAccessibilityTargets_whenAccessibilityServiceStateChanges_emitsUpdatedList() =
        kosmos.runTest {
            val serviceName = "Test Service"
            val a11yServices = listOf(getMockAccessibilityServiceInfo(serviceName))
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(a11yServices)

            val emissions = mutableListOf<List<AccessibilityTargetModel>>()
            val job =
                testScope.launch {
                    underTest.getAllAccessibilityTargets(UserShortcutType.HARDWARE).collect {
                        emissions.add(it)
                    }
                }
            advanceUntilIdle()

            val listenerCaptor =
                argumentCaptor<AccessibilityManager.AccessibilityServicesStateChangeListener>()
            verify(accessibilityManager)
                .addAccessibilityServicesStateChangeListener(listenerCaptor.capture())

            assertThat(emissions).isNotEmpty()
            assertThat(emissions.last().any { it.featureName == serviceName && !it.isStateOn })
                .isTrue()

            // Simulate a service state change.
            whenever(accessibilityManager.getEnabledAccessibilityServiceList(anyInt()))
                .thenReturn(a11yServices)
            listenerCaptor.firstValue.onAccessibilityServicesStateChanged(accessibilityManager)
            advanceUntilIdle()

            assertThat(emissions).isNotEmpty()
            assertThat(emissions.last().any { it.featureName == serviceName && it.isStateOn })
                .isTrue()

            job.cancel()

            verify(accessibilityManager)
                .removeAccessibilityServicesStateChangeListener(listenerCaptor.firstValue)
        }

    @Test
    fun getAllAccessibilityTargets_whenSystemFeatureStateChanges_emitsUpdatedList() =
        kosmos.runTest {
            val mouseKeysSettingsKey = Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ENABLED
            val mouseKeysFeatureName = "Mouse keys"

            val latestAllTargets by
                testScope.collectLastValue(
                    underTest.getAllAccessibilityTargets(UserShortcutType.HARDWARE)
                )
            advanceUntilIdle()

            assertThat(latestAllTargets).isNotNull()
            assertThat(
                    latestAllTargets!!.any {
                        it.featureName == mouseKeysFeatureName && !it.isStateOn
                    }
                )
                .isTrue()

            // Simulate a settings change.
            Settings.Secure.putInt(context.contentResolver, mouseKeysSettingsKey, 1)
            secureSettingsRepository.setBoolean(mouseKeysSettingsKey, true)
            advanceUntilIdle()

            assertThat(latestAllTargets).isNotNull()
            assertThat(
                    latestAllTargets!!.any {
                        it.featureName == mouseKeysFeatureName && it.isStateOn
                    }
                )
                .isTrue()
        }

    @Test
    fun getSelectedAccessibilityTargets_whenAssignedTargetsChange_emitsUpdatedList() =
        kosmos.runTest {
            val quickAccessTargetsSettingsKey =
                ShortcutUtils.convertToKey(UserShortcutType.QUICK_ACCESS)

            val latestSelectedTargets by
                testScope.collectLastValue(
                    underTest.getSelectedAccessibilityTargets(UserShortcutType.QUICK_ACCESS)
                )
            advanceUntilIdle()

            assertThat(latestSelectedTargets).isEmpty()

            // Simulate assigning a target to the shortcut type.
            whenever(
                    accessibilityManager.getAccessibilityShortcutTargets(
                        UserShortcutType.QUICK_ACCESS
                    )
                )
                .thenReturn(listOf(MAGNIFICATION_CONTROLLER_NAME))
            secureSettingsRepository.setString(
                quickAccessTargetsSettingsKey,
                MAGNIFICATION_CONTROLLER_NAME,
            )
            advanceUntilIdle()

            assertThat(latestSelectedTargets).hasSize(1)
            assertThat(latestSelectedTargets!!.get(0).targetName)
                .isEqualTo(MAGNIFICATION_CONTROLLER_NAME)
        }

    @Test
    fun isServiceWarningRequired_untrustedService_returnsTrue() =
        kosmos.runTest {
            val serviceInfo = getMockAccessibilityServiceInfo("Test Service")
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(listOf(serviceInfo))
            whenever(accessibilityManager.isAccessibilityServiceWarningRequired(serviceInfo))
                .thenReturn(true)
            val targetModel =
                createAccessibilityTargetModel(serviceInfo.componentName.flattenToString())

            assertThat(underTest.isServiceWarningRequired(targetModel)).isTrue()
        }

    @Test
    fun isServiceWarningRequired_trustedService_returnsFalse() =
        kosmos.runTest {
            val serviceInfo = getMockAccessibilityServiceInfo("Test Service")
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(listOf(serviceInfo))
            whenever(accessibilityManager.isAccessibilityServiceWarningRequired(serviceInfo))
                .thenReturn(false)
            val targetModel =
                createAccessibilityTargetModel(serviceInfo.componentName.flattenToString())

            assertThat(underTest.isServiceWarningRequired(targetModel)).isFalse()
        }

    @Test
    fun isServiceWarningRequired_nonServiceTarget_returnsFalse() =
        kosmos.runTest {
            val targetModel = createAccessibilityTargetModel(MAGNIFICATION_CONTROLLER_NAME)

            assertThat(underTest.isServiceWarningRequired(targetModel)).isFalse()
        }

    @Test
    fun getAccessibilityServiceInfo_serviceTarget_returnsServiceInfo() =
        kosmos.runTest {
            val serviceInfo = getMockAccessibilityServiceInfo("Test Service")
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(listOf(serviceInfo))
            val targetModel =
                createAccessibilityTargetModel(serviceInfo.componentName.flattenToString())

            val result = underTest.getAccessibilityServiceInfo(targetModel)

            assertThat(result).isEqualTo(serviceInfo)
        }

    @Test
    fun getAccessibilityServiceInfo_nonServiceTarget_returnsNull() =
        kosmos.runTest {
            val targetModel = createAccessibilityTargetModel(MAGNIFICATION_CONTROLLER_NAME)

            val result = underTest.getAccessibilityServiceInfo(targetModel)

            assertThat(result).isNull()
        }

    @Test
    fun hsuExcludedTargets_accessingValue_doesNotThrowException() =
        kosmos.runTest {
            val unused = underTest.hsuExcludedTargets
        }

    @Test
    fun accessibilityButtonTargetComponent_reflectsSecureSettings() =
        kosmos.runTest {
            val latestTarget by
                testScope.collectLastValue(underTest.accessibilityButtonTargetComponent)

            underTest.setAccessibilityButtonTargetComponent("TestService")
            assertThat(latestTarget).isEqualTo("TestService")

            underTest.setAccessibilityButtonTargetComponent("TestService2")
            assertThat(latestTarget).isEqualTo("TestService2")
        }

    @Test
    fun getKeyGestureConfirmInfo_turkishLocale_colorInversionUsesUnicodeI() =
        kosmos.runTest {
            val turkishLocale = Locale("tr")
            context.resources.configuration.setLocale(turkishLocale)

            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val type = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION

            val info =
                underTest.getKeyGestureConfirmInfo(
                    type,
                    metaState,
                    KeyEvent.KEYCODE_I,
                    getTargetNameByType(type),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            val contentText = info!!.contentText.toString()
            assertThat(contentText).contains("\u0130")
            assertThat(contentText).doesNotContain(" + I ")
        }

    private fun createAccessibilityTargetModel(targetName: String) =
        AccessibilityTargetModel(
            shortcutType = UserShortcutType.HARDWARE,
            targetName = targetName,
            featureName = "Fake Feature",
            icon = ColorDrawable(Color.RED),
            isAssigned = false,
            isToggleable = true,
            isStateOn = false,
        )

    private fun getMockAccessibilityServiceInfo(featureName: String): AccessibilityServiceInfo {
        val packageName = "com.android.test"
        val className = featureName.replace(" ", "")
        val componentName = ComponentName(packageName, "$packageName.$className")
        val iconResId = 1

        return AccessibilityServiceInfo().apply {
            this.componentName = componentName
            this.resolveInfo =
                ResolveInfo().apply {
                    this.serviceInfo =
                        ServiceInfo().apply {
                            this.applicationInfo =
                                ApplicationInfo().apply {
                                    this.packageName = packageName
                                    this.icon = iconResId
                                    this.targetSdkVersion = Build.VERSION_CODES.BAKLAVA
                                }
                            this.name = className
                            this.packageName = packageName
                            this.icon = iconResId
                        }
                    this.nonLocalizedLabel = featureName
                    this.icon = iconResId
                    this.iconResourceId = iconResId
                }
        }
    }

    private fun Kosmos.getTargetNameByType(keyGestureType: Int): String =
        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION ->
                COLOR_INVERSION_COMPONENT_NAME.flattenToString()
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION -> MAGNIFICATION_CONTROLLER_NAME
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK ->
                mainResources.getString(
                    com.android.internal.R.string.config_defaultSelectToSpeakService
                )

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS ->
                mainResources.getString(
                    com.android.internal.R.string.config_defaultVoiceAccessService
                )

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                mainResources.getString(
                    com.android.internal.R.string.config_defaultAccessibilityService
                )

            else -> ""
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
