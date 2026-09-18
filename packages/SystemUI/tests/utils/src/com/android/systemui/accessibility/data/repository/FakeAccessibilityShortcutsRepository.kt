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
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.input.KeyGestureEvent
import android.os.Build
import android.os.Handler
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.mockito.kotlin.mock

class FakeAccessibilityShortcutsRepository(
    @param:Application private val context: Context,
    @param:Main private val handler: Handler,
) : AccessibilityShortcutsRepository {
    companion object {
        const val FAKE_TALKBACK_TARGET_NAME = "com.android.test/.FakeTalkBack"
        const val FAKE_MAGNIFICATION_TARGET_NAME = "com.android.test/.FakeMagnification"
        const val FAKE_SELECT_TO_SPEAK_TARGET_NAME = "com.android.test/.FakeSelectToSpeak"
        const val FAKE_VOICE_ACCESS_TARGET_NAME = "com.android.test/.FakeVoiceAccess"
        const val FAKE_UNTRUSTED_SERVICE_TARGET_NAME = "com.android.test/.FakeUntrustedService"
        const val FAKE_HSU_EXCLUDED_TARGET_NAME = "com.android.test/.FakeHsuExcludedTarget"
        const val FAKE_KEYGUARD_EXCLUDED_TARGET_NAME =
            "com.android.test/.FakeKeyguardExcludedTarget"
    }

    private data class TargetInfo(
        val targetName: String,
        val featureName: String,
        val isToggleable: Boolean,
    )

    private val allTargets =
        listOf(
            TargetInfo(FAKE_TALKBACK_TARGET_NAME, "Screen Reader", true),
            TargetInfo(FAKE_MAGNIFICATION_TARGET_NAME, "Magnification", false),
            TargetInfo(FAKE_SELECT_TO_SPEAK_TARGET_NAME, "Select to Speak", false),
            TargetInfo(FAKE_VOICE_ACCESS_TARGET_NAME, "Voice Access", true),
            TargetInfo(FAKE_UNTRUSTED_SERVICE_TARGET_NAME, "Untrusted Service", true),
            TargetInfo(FAKE_HSU_EXCLUDED_TARGET_NAME, "HSU Excluded Service", true),
            TargetInfo(FAKE_KEYGUARD_EXCLUDED_TARGET_NAME, "KEYGUARD Excluded Service", true),
        )

    // Target names existing in the set means they are assigned to that shortcut type.
    private val assignedTargetNamesByShortcutType =
        MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    // Target names existing in the set means they are turned on.
    private val enabledTargetNames = MutableStateFlow<Set<String>>(emptySet())
    // Targets names that are not trusted.
    private val untrustedTargetNames = mutableSetOf(FAKE_UNTRUSTED_SERVICE_TARGET_NAME)

    private val featureNameTestMap =
        mapOf(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION to "Color Inversion",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION to "Magnification",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER to "Screen Reader",
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK to "Select to Speak",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS to "Voice Access",
        )

    override val hsuExcludedTargets = listOf(FAKE_HSU_EXCLUDED_TARGET_NAME)
    override val keyguardExcludedTargets = listOf(FAKE_KEYGUARD_EXCLUDED_TARGET_NAME)

    private val _accessibilityButtonTargetComponent = MutableStateFlow<String?>(null)
    override val accessibilityButtonTargetComponent =
        _accessibilityButtonTargetComponent.asStateFlow()

    override suspend fun setAccessibilityButtonTargetComponent(target: String) {
        _accessibilityButtonTargetComponent.value = target
    }

    var ttsPrompt: TtsPrompt? = null
    var ttsText: CharSequence = ""

    /** Returns true if the target is assigned for the given shortcut type. */
    fun isTargetAssigned(@UserShortcutType shortcutType: Int, targetName: String): Boolean =
        assignedTargetNamesByShortcutType.value.contains(shortcutType, targetName)

    /** Returns true if the target is turned on. */
    fun isTargetEnabled(targetName: String): Boolean = enabledTargetNames.value.contains(targetName)

    override suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo? =
        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER -> {
                val featureNameForTest = featureNameTestMap[keyGestureType] ?: ""
                val ttsText =
                    if (keyGestureType == KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER) {
                        "Press Action + Alt + T again to enable Screen Reader"
                    } else {
                        null
                    }
                // return a fake data
                KeyGestureConfirmInfo(
                    keyGestureType,
                    "$featureNameForTest fakeTitle",
                    "$featureNameForTest fakeContentText",
                    emptyList(),
                    targetName,
                    0,
                    displayId,
                    ttsText,
                )
            }

            else -> null
        }

    override fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetNames: Set<String>,
    ) {
        targetNames
            .filter { allTargets.map { it.targetName }.contains(it) }
            .let { validTargetNames ->
                assignedTargetNamesByShortcutType.update { currentMap ->
                    val currentSet = currentMap[shortcutType] ?: emptySet()
                    val newSet =
                        if (enable) {
                            currentSet.plus(validTargetNames)
                        } else {
                            currentSet.minus(validTargetNames)
                        }
                    currentMap + (shortcutType to newSet)
                }
            }
    }

    override fun enableMagnificationAndZoomIn(displayId: Int) =
        enabledTargetNames.update { it + FAKE_MAGNIFICATION_TARGET_NAME }

    override fun createTtsPromptForText(text: CharSequence): TtsPrompt =
        mock<TtsPrompt>().also {
            ttsPrompt = it
            ttsText = text
        }

    override fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        if (allTargets.map { it.targetName }.contains(targetName)) {
            toggleTarget(targetName)
        }
    }

    override fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> = allTargets.map { it.toTargetModel(shortcutType) }

    override fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        combine(assignedTargetNamesByShortcutType, enabledTargetNames) {
            getAllAccessibilityTargetsInfo(shortcutType)
        }

    override fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> =
        allTargets.map { it.toTargetModel(shortcutType) }.filter { it.isAssigned }

    override fun getSelectedAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        combine(assignedTargetNamesByShortcutType, enabledTargetNames) {
            getSelectedAccessibilityTargetsInfo(shortcutType)
        }

    override fun isServiceWarningRequired(target: AccessibilityTargetModel): Boolean =
        untrustedTargetNames.contains(target.targetName)

    override fun getAccessibilityServiceInfo(
        target: AccessibilityTargetModel
    ): AccessibilityServiceInfo? =
        allTargets
            .firstOrNull { it.targetName == target.targetName }
            ?.let {
                val componentName = ComponentName.unflattenFromString(it.targetName)!!
                val packageName = componentName.packageName
                val className = componentName.className
                val iconResId = 1
                AccessibilityServiceInfo().apply {
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
                            this.nonLocalizedLabel = it.featureName
                            this.icon = iconResId
                            this.iconResourceId = iconResId
                        }
                }
            }

    private fun TargetInfo.toTargetModel(
        @UserShortcutType shortcutType: Int
    ): AccessibilityTargetModel =
        AccessibilityTargetModel(
            shortcutType = shortcutType,
            targetName = targetName,
            featureName = featureName,
            icon = ColorDrawable(Color.RED),
            isAssigned = isTargetAssigned(shortcutType, targetName),
            isToggleable = isToggleable,
            isStateOn = isTargetEnabled(targetName),
        )

    /** Toggle the target on/off. */
    private fun toggleTarget(targetName: String) =
        enabledTargetNames.update {
            if (it.contains(targetName)) it - targetName else it + targetName
        }

    private fun Map<Int, Set<String>>.contains(
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = get(shortcutType)?.contains(targetName) ?: false
}
