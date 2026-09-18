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
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.input.KeyGestureEvent
import android.os.Handler
import android.provider.Settings
import android.text.BidiFormatter
import android.text.TextUtils
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import com.android.internal.R as RI
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.dialog.AccessibilityServiceTarget
import com.android.internal.accessibility.dialog.AccessibilityTarget
import com.android.internal.accessibility.dialog.AccessibilityTargetHelper
import com.android.internal.accessibility.util.FrameworkObjectProvider
import com.android.internal.accessibility.util.ShortcutUtils
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.accessibility.keygesture.shared.model.DialogContentSection
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyboard.shortcut.data.repository.ShortcutHelperKeys
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Provides data for enabling and triggering accessibility feature shortcuts. */
interface AccessibilityShortcutsRepository {
    suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo?

    fun createTtsPromptForText(text: CharSequence): TtsPrompt

    fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetNames: Set<String>,
    )

    fun enableMagnificationAndZoomIn(displayId: Int)

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    )

    /**
     * Returns list of [AccessibilityTargetModel] of the installed accessibility service,
     * accessibility activity, and allowlisting feature including accessibility feature's package
     * name, component id, etc.
     *
     * @param shortcutType The shortcut type.
     * @return The list of [AccessibilityTargetModel].
     */
    fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel>

    /**
     * Get a flow of all accessibility targets that emits updates when service state or settings
     * change.
     *
     * @param shortcutType The shortcut type.
     * @return The flow of list of [AccessibilityTargetModel].
     */
    fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>>

    /**
     * Returns list of [AccessibilityTargetModel] of assigned accessibility shortcuts from
     * [AccessibilityTargetHelper.getTargets] including accessibility feature's package name,
     * component id, etc.
     *
     * @param shortcutType The shortcut type.
     * @return The list of [AccessibilityTargetModel].
     */
    fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel>

    /**
     * Get a flow of selected/assigned accessibility targets that emits updates when service state
     * or settings change.
     *
     * @param shortcutType The shortcut type.
     * @return The flow of list of [AccessibilityTargetModel].
     */
    fun getSelectedAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>>

    /**
     * Returns true if the accessibility service warning dialog should be shown for the given
     * accessibility target.
     *
     * @param target The [AccessibilityTargetModel].
     * @return True if the accessibility service warning dialog should be shown, false otherwise.
     *   Also returns false if the target is not an accessibility service.
     */
    fun isServiceWarningRequired(target: AccessibilityTargetModel): Boolean

    /**
     * Returns the [AccessibilityServiceInfo] for the given accessibility target.
     *
     * @param target The [AccessibilityTargetModel].
     * @return The [AccessibilityServiceInfo] if the target is an accessibility service, null
     *   otherwise.
     */
    fun getAccessibilityServiceInfo(target: AccessibilityTargetModel): AccessibilityServiceInfo?

    /**
     * The list of accessibility targets that are excluded from showing up in the accessibility
     * shortcut chooser dialog when the current user is the Headless System User.
     */
    val hsuExcludedTargets: List<String>

    /**
     * The list of accessibility targets that are excluded from showing up in the accessibility
     * shortcut chooser dialog when the current user is on the Lock screen.
     */
    val keyguardExcludedTargets: List<String>

    /**
     * Setting specifying the accessibility service or feature to be toggled via the accessibility
     * button in the navigation bar. This is either a flattened [ComponentName] or the class name of
     * a system class implementing a supported accessibility feature.
     *
     * See: [Settings.Secure.ACCESSIBILITY_BUTTON_TARGET_COMPONENT]
     *
     * Value is null if nothing is selected.
     */
    val accessibilityButtonTargetComponent: Flow<String?>

    /**
     * Sets the accessibility service or feature to be toggled via the accessibility button in the
     * navigation bar.
     *
     * @param target The flattened [ComponentName] of the target.
     */
    suspend fun setAccessibilityButtonTargetComponent(target: String)
}

@SysUISingleton
class AccessibilityShortcutsRepositoryImpl
@Inject
constructor(
    @param:Application private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    private val packageManager: PackageManager,
    private val userTracker: UserTracker,
    private val secureSettingsRepository: SecureSettingsRepository,
    @param:Main private val resources: Resources,
    @param:Background private val backgroundDispatcher: CoroutineDispatcher,
    @param:Main private val handler: Handler,
) : AccessibilityShortcutsRepository {
    // Action key
    private val MODIFIER_KEY = KeyEvent.META_META_ON

    override suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo? {
        // TODO: b/419026315 - Update the secondary modifier key label.
        val secondaryModifierLabel =
            ShortcutHelperKeys.modifierLabels[MODIFIER_KEY xor metaState] ?: return null
        val keyCodeLabel = ShortcutUtils.getLabelFromKeyCode(context, keyCode) ?: return null

        val actionKeyLabel = resources.getText(R.string.shortcut_helper_customizer_action_key_text)
        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER -> {
                val featureName = getFeatureName(keyGestureType, targetName) ?: return null
                val title = getDialogTitle(keyGestureType, featureName) ?: return null
                val content =
                    getDialogContent(
                        keyGestureType,
                        actionKeyLabel,
                        secondaryModifierLabel.invoke(context),
                        keyCodeLabel,
                        featureName,
                    ) ?: return null

                val sectionsData =
                    listOf(
                        R.string.accessibility_key_gesture_screen_reader_dialog_warning_heading to
                            null,
                        null to
                            R.string.accessibility_key_gesture_screen_reader_dialog_warning_message,
                        R.string
                            .accessibility_key_gesture_screen_reader_dialog_warning_view_control_screen_heading to
                            R.string
                                .accessibility_key_gesture_screen_reader_dialog_warning_view_control_screen_message,
                        R.string
                            .accessibility_key_gesture_screen_reader_dialog_warning_perform_actions_heading to
                            R.string
                                .accessibility_key_gesture_screen_reader_dialog_warning_perform_actions_message,
                    )

                val contentSections =
                    sectionsData.map { (headingResId, messageResId) ->
                        DialogContentSection(
                            heading = headingResId?.let { resources.getString(it) },
                            message = messageResId?.let { resources.getString(it) },
                        )
                    }

                val ttsText =
                    resources.getString(
                        R.string.accessibility_key_gesture_dialog_screen_reader_tts,
                        actionKeyLabel,
                        secondaryModifierLabel.invoke(context),
                        keyCodeLabel,
                        featureName,
                    )

                return KeyGestureConfirmInfo(
                    keyGestureType,
                    title,
                    content,
                    contentSections,
                    targetName,
                    getActionKeyIconResId(),
                    displayId,
                    ttsText,
                )
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS -> {
                val featureName = getFeatureName(keyGestureType, targetName) ?: return null
                val title = getDialogTitle(keyGestureType, featureName) ?: return null
                val content =
                    getDialogContent(
                        keyGestureType,
                        actionKeyLabel,
                        secondaryModifierLabel.invoke(context),
                        keyCodeLabel,
                        featureName,
                    ) ?: return null

                return KeyGestureConfirmInfo(
                    keyGestureType,
                    title,
                    content,
                    emptyList(),
                    targetName,
                    getActionKeyIconResId(),
                    displayId,
                    null,
                )
            }
            else -> {
                val featureNameToIntro = getFeatureNameToIntro(targetName) ?: return null
                val title =
                    resources.getString(
                        R.string.accessibility_key_gesture_shortcut_not_yet_enabled_dialog_title,
                        featureNameToIntro.first,
                    )
                val content =
                    TextUtils.expandTemplate(
                        resources.getText(R.string.accessibility_key_gesture_dialog_content),
                        actionKeyLabel,
                        secondaryModifierLabel.invoke(context),
                        keyCodeLabel,
                        featureNameToIntro.first,
                        featureNameToIntro.second,
                    )

                return KeyGestureConfirmInfo(
                    keyGestureType,
                    title,
                    content,
                    emptyList(),
                    targetName,
                    getActionKeyIconResId(),
                    displayId,
                    null,
                )
            }
        }
    }

    override fun createTtsPromptForText(text: CharSequence): TtsPrompt {
        return TtsPrompt(context, handler, FrameworkObjectProvider(), text)
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetNames: Set<String>,
    ) {
        accessibilityManager.enableShortcutsForTargets(
            enable,
            shortcutType,
            targetNames,
            userTracker.userId,
        )
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun enableMagnificationAndZoomIn(displayId: Int) {
        accessibilityManager.enableMagnificationAndZoomIn(displayId)
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        accessibilityManager.performAccessibilityShortcut(displayId, shortcutType, targetName)
    }

    override fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> =
        AccessibilityTargetHelper.getInstalledTargets(context, shortcutType).map {
            it.toAccessibilityTargetModel()
        }

    override fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        getTargetsAsFlow(shortcutType, ::getAllAccessibilityTargetsInfo)

    override fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> =
        AccessibilityTargetHelper.getTargets(context, shortcutType).map {
            it.toAccessibilityTargetModel()
        }

    override fun getSelectedAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        getTargetsAsFlow(shortcutType, ::getSelectedAccessibilityTargetsInfo)

    private fun getTargetsAsFlow(
        @UserShortcutType shortcutType: Int,
        getTargetsBlock: (Int) -> List<AccessibilityTargetModel>,
    ): Flow<List<AccessibilityTargetModel>> {
        val servicesStateChanged = conflatedCallbackFlow {
            val listener =
                AccessibilityManager.AccessibilityServicesStateChangeListener { trySend(Unit) }
            accessibilityManager.addAccessibilityServicesStateChangeListener(listener)

            trySend(Unit)

            awaitClose {
                accessibilityManager.removeAccessibilityServicesStateChangeListener(listener)
            }
        }
        val secureSettingsChanged =
            AccessibilityTargetHelper.getInstalledTargets(context, shortcutType)
                .filter { it.isToggleable && it.key != null }
                .asFlow()
                .flatMapMerge {
                    secureSettingsRepository.boolSetting(
                        it.key,
                        defaultValue = secureSettingsRepository.getInt(it.key) != 0,
                    )
                }
                .conflate()
        val assignedTargetsChanged =
            secureSettingsRepository
                .stringSetting(ShortcutUtils.convertToKey(shortcutType))
                .conflate()
        return combineTransform(
                servicesStateChanged,
                secureSettingsChanged,
                assignedTargetsChanged,
            ) {
                emit(getTargetsBlock(shortcutType))
            }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
    }

    override fun isServiceWarningRequired(target: AccessibilityTargetModel) =
        getAccessibilityServiceInfo(target)?.let {
            accessibilityManager.isAccessibilityServiceWarningRequired(it)
        } ?: false

    override fun getAccessibilityServiceInfo(
        target: AccessibilityTargetModel
    ): AccessibilityServiceInfo? =
        AccessibilityTargetHelper.getInstalledTargets(context, target.shortcutType)
            .find { it.id == target.targetName }
            ?.let { (it as? AccessibilityServiceTarget)?.accessibilityServiceInfo }

    override val hsuExcludedTargets: List<String> by lazy {
        resources.getStringArray(RI.array.hsu_accessibility_targets_blocklist).toList()
    }

    override val keyguardExcludedTargets: List<String> by lazy {
        resources.getStringArray(RI.array.keyguard_accessibility_targets_blocklist).toList()
    }

    override val accessibilityButtonTargetComponent =
        secureSettingsRepository.stringSetting(
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGET_COMPONENT,
            defaultValue = null,
        )

    override suspend fun setAccessibilityButtonTargetComponent(target: String) {
        secureSettingsRepository.setString(
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGET_COMPONENT,
            target,
        )
    }

    private fun AccessibilityTarget.toAccessibilityTargetModel() =
        AccessibilityTargetModel(
            shortcutType = shortcutType,
            targetName = id,
            featureName = label.toString(),
            icon = icon,
            isAssigned = isShortcutEnabled,
            isToggleable = isToggleable,
            isStateOn = isStateOn,
        )

    private suspend fun getFeatureName(keyGestureType: Int, targetName: String): CharSequence? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION ->
                resources.getString(R.string.quick_settings_inversion_label)
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION ->
                resources.getString(
                    com.android.settingslib.R.string.accessibility_screen_magnification_title
                )
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS -> {
                val componentName = ComponentName.unflattenFromString(targetName)
                withContext(backgroundDispatcher) {
                    accessibilityManager
                        .getInstalledServiceInfoWithComponentName(componentName)
                        ?.resolveInfo
                        ?.loadLabel(packageManager)
                        ?.let { formatFeatureName(it) }
                }
            }
            else -> null
        }
    }

    private suspend fun getDialogTitle(keyGestureType: Int, featureName: CharSequence): String? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION -> {
                resources.getString(
                    R.string.accessibility_key_gesture_magnification_dialog_title,
                    featureName,
                )
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS -> {
                resources.getString(
                    R.string.accessibility_key_gesture_shortcut_not_yet_enabled_dialog_title,
                    featureName,
                )
            }
            else -> null
        }
    }

    private fun getDialogContent(
        keyGestureType: Int,
        actionKeyLabel: CharSequence,
        secondaryModifierLabel: String,
        keyCodeLabel: String,
        featureName: CharSequence,
    ): CharSequence? {
        val contentTemplateResId: Int? =
            when (keyGestureType) {
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION ->
                    R.string.accessibility_key_gesture_color_inversion_dialog_content
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION ->
                    R.string.accessibility_key_gesture_magnification_dialog_content
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                    R.string.accessibility_key_gesture_screen_reader_dialog_content
                KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK ->
                    R.string.accessibility_key_gesture_select_to_speak_dialog_content
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS ->
                    R.string.accessibility_key_gesture_voice_access_dialog_content
                else -> null
            }

        return contentTemplateResId?.let { resId ->
            val contentTemplate = resources.getText(resId)
            TextUtils.expandTemplate(
                contentTemplate,
                actionKeyLabel,
                secondaryModifierLabel,
                keyCodeLabel,
                featureName,
            )
        }
    }

    private suspend fun getFeatureNameToIntro(
        targetName: String
    ): Pair<CharSequence, CharSequence>? {
        val accessibilityServiceInfo =
            withContext(backgroundDispatcher) {
                accessibilityManager.getInstalledServiceInfoWithComponentName(
                    ComponentName.unflattenFromString(targetName)
                )
            } ?: return null

        val featureName =
            formatFeatureName(accessibilityServiceInfo.resolveInfo.loadLabel(packageManager))

        val intro = accessibilityServiceInfo.loadIntro(packageManager) ?: ""

        return Pair(featureName, intro)
    }

    // Get the service name and bidi wrap it to protect from bidi side effects.
    private fun formatFeatureName(label: CharSequence): CharSequence {
        val locale = context.resources.configuration.getLocales().get(0)
        return BidiFormatter.getInstance(locale).unicodeWrap(label)
    }

    private fun getActionKeyIconResId(): Int {
        // TODO: b/419026315 - Update the modifier key icon res id based on keyboard device.
        return ShortcutHelperKeys.metaModifierIconResId
    }
}
