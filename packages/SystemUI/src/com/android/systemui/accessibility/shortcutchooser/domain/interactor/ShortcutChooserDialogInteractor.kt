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

package com.android.systemui.accessibility.shortcutchooser.domain.interactor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.util.Log
import android.view.Display.INVALID_DISPLAY
import android.view.accessibility.Flags as AccessibilityFlags
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.AccessibilityUtils
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@SysUISingleton
class ShortcutChooserDialogInteractor
@Inject
constructor(
    @param:Application private val applicationContext: Context,
    private val repository: AccessibilityShortcutsRepository,
    private val displayRepository: DisplayRepository,
    private val userRepository: UserRepository,
    private val hsum: HeadlessSystemUserMode,
    private val keyguardInteractor: KeyguardInteractor,
    private val broadcastDispatcher: BroadcastDispatcher,
) {
    val dialogRequest: Flow<DialogRequestModel> =
        broadcastDispatcher
            .broadcastFlow(
                filter =
                    IntentFilter().apply {
                        addAction(
                            ShortcutChooserDialogConstants.LAUNCH_SHORTCUT_CHOOSER_DIALOG_ACTION
                        )
                    },
                user = UserHandle.ALL,
                flags = Context.RECEIVER_EXPORTED,
                permission = SHORTCUT_CHOOSER_PERMISSION,
            ) { intent, _ ->
                intent
            }
            .map { intent -> processShortcutChooserIntent(intent) }
            .filterNotNull()
            .filter { isValidDialogRequest(it) }

    fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        repository.getAllAccessibilityTargets(shortcutType).map { it.filterExcludedTargets() }

    fun getAssignedAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        repository.getSelectedAccessibilityTargets(shortcutType).map { it.filterExcludedTargets() }

    suspend fun getAssignedAccessibilityTargetsCount(@UserShortcutType shortcutType: Int): Int =
        repository.getSelectedAccessibilityTargetsInfo(shortcutType).filterExcludedTargets().size

    /**
     * Returns a [Context] for the given [dialogDisplayId]. If the requested display is not the
     * existing one, a new display-specific context is created. Falls back to the
     * [applicationContext] if the requested display does not exist.
     *
     * @param dialogDisplayId The display ID from the dialog request
     * @param applicationContext The existing application context
     * @return The context to create the dialog
     */
    fun getDialogContextByDisplayId(dialogDisplayId: Int, applicationContext: Context): Context =
        if (applicationContext.displayId == dialogDisplayId) {
            applicationContext
        } else {
            displayRepository.getDisplay(dialogDisplayId)?.let { display ->
                applicationContext.createDisplayContext(display)
            }
                ?: run {
                    Log.d(
                        TAG,
                        "Display $dialogDisplayId not found when creating display context." +
                            "Falling back to existing context.",
                    )
                    applicationContext
                }
        }

    fun enableShortcutForTarget(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = repository.enableShortcutsForTargets(enable, shortcutType, setOf(targetName))

    /**
     * Enables (assigns) shortcuts for all targets on the [shortcutType], except:
     * - Targets that need a consent warning
     * - Targets that are excluded for the System user if in HSUM
     *
     * @param shortcutType The shortcut type.
     */
    suspend fun enableShortcutForAllAllowedTargets(@UserShortcutType shortcutType: Int) =
        repository
            .getAllAccessibilityTargetsInfo(shortcutType)
            .filterExcludedTargets()
            .filter { !it.isAssigned && !isServiceWarningRequired(it) }
            .takeIf { it.isNotEmpty() }
            ?.map { it.targetName }
            ?.toSet()
            ?.let { repository.enableShortcutsForTargets(true, shortcutType, it) }

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        if (displayId != INVALID_DISPLAY) {
            repository.performAccessibilityShortcut(displayId, shortcutType, targetName)
        }
    }

    /** True for a full user (not HSU) that has completed OOBE setup. */
    suspend fun isCompletedFullUser() = !isHeadlessSystemUser() && !isOOBE()

    /** True if on login screen (HSU). */
    private suspend fun isHeadlessSystemUser(): Boolean =
        hsum.isHeadlessSystemUser(userRepository.getSelectedUserInfo().id)

    // TODO: https://issuetracker.google.com/461597843 - Use SecureSettings in interactor.
    private fun isOOBE() = !AccessibilityUtils.isUserSetupCompleted(applicationContext)

    fun isServiceWarningRequired(target: AccessibilityTargetModel) =
        repository.isServiceWarningRequired(target)

    fun getAccessibilityServiceInfo(target: AccessibilityTargetModel) =
        repository.getAccessibilityServiceInfo(target)

    val accessibilityButtonTargetComponent = repository.accessibilityButtonTargetComponent

    suspend fun setAccessibilityButtonTargetComponent(target: String) =
        repository.setAccessibilityButtonTargetComponent(target)

    suspend private fun processShortcutChooserIntent(intent: Intent): DialogRequestModel? {
        return DialogRequestModel(
                shortcutType =
                    intent.getIntExtra(
                        ShortcutChooserDialogConstants.SHORTCUT_TYPE,
                        UserShortcutType.DEFAULT,
                    ),
                displayId =
                    intent.getIntExtra(ShortcutChooserDialogConstants.DISPLAY_ID, INVALID_DISPLAY),
            )
            .run {
                if (shortcutType == UserShortcutType.TOP_ROW_KEY && !isCompletedFullUser()) {
                    // If the user is not logged in or hasn't completed OOBE setup, show the general
                    // purpose Quick Access dialog instead.
                    copy(shortcutType = UserShortcutType.QUICK_ACCESS)
                } else {
                    this
                }
            }
            .takeIf {
                when (it.shortcutType) {
                    UserShortcutType.QUICK_ACCESS ->
                        SystemUIFlags.launchAccessibilityQuickAccessDialogPermission()
                    else -> AccessibilityFlags.enableA11yTopRowShortcut()
                }
            }
    }

    private fun isValidDialogRequest(dialogRequest: DialogRequestModel) =
        dialogRequest.shortcutType > UserShortcutType.DEFAULT &&
            dialogRequest.shortcutType < UserShortcutType.ALL &&
            dialogRequest.displayId != INVALID_DISPLAY

    /**
     * Conditionally filters out excluded targets.
     * - Excluded targets when current user is Headless System User.
     * - Excluded targets when in OOBE.
     */
    private suspend fun List<AccessibilityTargetModel>.filterExcludedTargets() =
        when {
            !isCompletedFullUser() -> filterNot { it.targetName in repository.hsuExcludedTargets }
            keyguardInteractor.isKeyguardCurrentlyShowing() ->
                filterNot { it.targetName in repository.keyguardExcludedTargets }
            else -> this
        }

    companion object {
        private val TAG = ShortcutChooserDialogInteractor::class.simpleName

        private const val SHORTCUT_CHOOSER_PERMISSION =
            "com.android.systemui.permission.LAUNCH_ACCESSIBILITY_SHORTCUT_CHOOSER_DIALOG"
    }
}
