/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

package com.android.systemui.user.ui.dialog

import android.app.Dialog
import android.content.Context
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.jank.InteractionJankMonitor
import com.android.settingslib.users.UserCreatingDialog
import com.android.systemui.CoreStartable
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.TransitionAnimator
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepository
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
import com.android.systemui.user.UserSwitcherFullscreenDialogDelegate
import com.android.systemui.user.domain.interactor.UserSwitcherInteractor
import com.android.systemui.user.domain.model.ShowDialogRequestModel
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull

/** Coordinates dialogs for user switcher logic. */
@SysUISingleton
class UserSwitcherDialogCoordinator
@Inject
constructor(
    @Application private val applicationScope: Lazy<CoroutineScope>,
    private val dialogTransitionAnimator: Lazy<DialogTransitionAnimator>,
    private val interactor: Lazy<UserSwitcherInteractor>,
    private val shadeDialogContextInteractor: Lazy<ShadeDialogContextInteractor>,
    private val displayPropertiesRepository: Lazy<DisplayWindowPropertiesRepository>,
    private val addUserDialogDelegateFactory: AddUserDialogDelegate.Factory,
    private val exitGuestDialogDelegateFactory: ExitGuestDialogDelegate.Factory,
    private val userSwitchDialogDelegateFactory: UserSwitchDialogDelegate.Factory,
    private val userSwitcherFullscreenDialogDelegateFactory:
        UserSwitcherFullscreenDialogDelegate.Factory,
) : CoreStartable {

    private var currentDialog: Dialog? = null

    override fun start() {
        startHandlingDialogShowRequests()
        startHandlingDialogDismissRequests()
    }

    private fun startHandlingDialogShowRequests() {
        applicationScope.get().launch {
            interactor.get().dialogShowRequests.filterNotNull().collect { request ->
                val context: Context =
                    request.context?.let {
                        displayPropertiesRepository
                            .get()
                            .get(it.displayId, TYPE_STATUS_BAR_SUB_PANEL)
                            ?.context
                    } ?: shadeDialogContextInteractor.get().context
                val (dialog, dialogCuj) =
                    when (request) {
                        is ShowDialogRequestModel.ShowAddUserDialog ->
                            Pair(
                                addUserDialogDelegateFactory
                                    .create(
                                        request.userHandle,
                                        request.isKeyguardShowing,
                                        request.showEphemeralMessage,
                                    )
                                    .createDialog(),
                                DialogCuj(
                                    InteractionJankMonitor.CUJ_USER_DIALOG_OPEN,
                                    INTERACTION_JANK_ADD_NEW_USER_TAG,
                                ),
                            )
                        is ShowDialogRequestModel.ShowUserCreationDialog ->
                            Pair(UserCreatingDialog(context, request.isGuest), null)
                        is ShowDialogRequestModel.ShowExitGuestDialog ->
                            Pair(
                                exitGuestDialogDelegateFactory
                                    .create(
                                        context,
                                        request.guestUserId,
                                        request.isGuestEphemeral,
                                        request.targetUserId,
                                        request.isKeyguardShowing,
                                        request.onExitGuestUser,
                                    )
                                    .createDialog(),
                                DialogCuj(
                                    InteractionJankMonitor.CUJ_USER_DIALOG_OPEN,
                                    INTERACTION_JANK_EXIT_GUEST_MODE_TAG,
                                ),
                            )
                        is ShowDialogRequestModel.ShowUserSwitcherDialog ->
                            Pair(
                                userSwitchDialogDelegateFactory.create(context).createDialog(),
                                DialogCuj(
                                    InteractionJankMonitor.CUJ_USER_DIALOG_OPEN,
                                    INTERACTION_JANK_EXIT_GUEST_MODE_TAG,
                                ),
                            )
                        is ShowDialogRequestModel.ShowUserSwitcherFullscreenDialog ->
                            Pair(
                                userSwitcherFullscreenDialogDelegateFactory
                                    .create(context)
                                    .createDialog(),
                                null, /* dialogCuj */
                            )
                    }
                currentDialog = dialog

                val controller = request.expandable?.dialogTransitionController(dialogCuj)
                if (controller != null) {
                    if (TransitionAnimator.dynamicTargetResolutionEnabled()) {
                        dialogTransitionAnimator
                            .get()
                            .show(
                                dialog,
                                request.expandable!!::dialogTransitionController,
                                controller.cuj,
                            )
                    } else {
                        dialogTransitionAnimator.get().show(dialog, controller)
                    }
                } else if (request.dialogShower != null && dialogCuj != null) {
                    request.dialogShower?.showDialog(dialog, dialogCuj)
                } else {
                    dialog.show()
                }

                interactor.get().onDialogShown()
            }
        }
    }

    private fun startHandlingDialogDismissRequests() {
        applicationScope.get().launch {
            interactor.get().dialogDismissRequests.filterNotNull().collect {
                currentDialog?.let {
                    if (it.isShowing) {
                        it.cancel()
                    }
                }

                interactor.get().onDialogDismissed()
            }
        }
    }

    companion object {
        private const val INTERACTION_JANK_ADD_NEW_USER_TAG = "add_new_user"
        private const val INTERACTION_JANK_EXIT_GUEST_MODE_TAG = "exit_guest_mode"
    }
}
