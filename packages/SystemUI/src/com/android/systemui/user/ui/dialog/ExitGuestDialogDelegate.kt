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

import android.annotation.UserIdInt
import android.content.Context
import android.content.DialogInterface
import com.android.settingslib.R
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Dialog for exiting the guest user. */
class ExitGuestDialogDelegate
@AssistedInject
constructor(
    private val falsingManager: FalsingManager,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    @Assisted(EXIT_GUEST_CONTEXT) private val context: Context,
    @Assisted(GUEST_USER_ID) private val guestUserId: Int,
    @Assisted(GUEST_EPHEMERAL) private val isGuestEphemeral: Boolean,
    @Assisted(TARGET_ID) private val targetUserId: Int,
    @Assisted(KEYGUARD_SHOWING) private val isKeyguardShowing: Boolean,
    @Assisted(EXIT_GUEST_USER_LISTENER) private val onExitGuestUserListener: OnExitGuestUserListener,
) : SystemUIDialog.Delegate {

    companion object {
        private const val EXIT_GUEST_CONTEXT = "context"
        private const val GUEST_USER_ID = "guest_user_id"
        private const val GUEST_EPHEMERAL = "is_guest_ephemeral"
        private const val TARGET_ID = "target_id"
        private const val KEYGUARD_SHOWING = "is_keyguard_showing"
        private const val EXIT_GUEST_USER_LISTENER = "on_exit_guest_user_listener"
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted(EXIT_GUEST_CONTEXT) context: Context,
            @Assisted(GUEST_USER_ID) guestUserId: Int,
            @Assisted(GUEST_EPHEMERAL) isGuestEphemeral: Boolean,
            @Assisted(TARGET_ID) targetId: Int,
            @Assisted(KEYGUARD_SHOWING) isKeyguardShowing: Boolean,
            @Assisted(EXIT_GUEST_USER_LISTENER) onExitGuestUserListener: OnExitGuestUserListener,
        ): ExitGuestDialogDelegate
    }

    fun interface OnExitGuestUserListener {
        fun onExitGuestUser(
            @UserIdInt guestId: Int,
            @UserIdInt targetId: Int,
            forceRemoveGuest: Boolean,
        )
    }

    private val onClickListener =
        object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                val penalty =
                    if (which == DialogInterface.BUTTON_NEGATIVE) {
                        FalsingManager.NO_PENALTY
                    } else {
                        FalsingManager.MODERATE_PENALTY
                    }
                if (falsingManager.isFalseTap(penalty)) {
                    return
                }

                if (isGuestEphemeral) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        dialogTransitionAnimator.dismissStack(dialog)
                        // Ephemeral guest: exit guest, guest is removed by the system
                        // on exit, since its marked ephemeral
                        onExitGuestUserListener.onExitGuestUser(guestUserId, targetUserId, false)
                    } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                        // Cancel clicked, do nothing
                        dialog.cancel()
                    }
                } else {
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            dialogTransitionAnimator.dismissStack(dialog)
                            // Non-ephemeral guest: exit guest, guest is not removed by the system
                            // on exit, since its marked non-ephemeral
                            onExitGuestUserListener.onExitGuestUser(
                                guestUserId,
                                targetUserId,
                                false,
                            )
                        }
                        DialogInterface.BUTTON_NEGATIVE -> {
                            dialogTransitionAnimator.dismissStack(dialog)
                            // Non-ephemeral guest: remove guest and then exit
                            onExitGuestUserListener.onExitGuestUser(guestUserId, targetUserId, true)
                        }
                        DialogInterface.BUTTON_NEUTRAL -> {
                            // Cancel clicked, do nothing
                            dialog.cancel()
                        }
                    }
                }
            }
        }

    override fun createDialog(): SystemUIDialog {
        val dialog = systemUIDialogFactory.create(context)
        with(dialog) {
            if (isGuestEphemeral) {
                setTitle(context.getString(R.string.guest_exit_dialog_title))
                setMessage(context.getString(R.string.guest_exit_dialog_message))
                setButton(
                    DialogInterface.BUTTON_NEUTRAL,
                    context.getString(android.R.string.cancel),
                    onClickListener,
                )
                setButton(
                    DialogInterface.BUTTON_POSITIVE,
                    context.getString(R.string.guest_exit_dialog_button),
                    onClickListener,
                )
            } else {
                setTitle(context.getString(R.string.guest_exit_dialog_title_non_ephemeral))
                setMessage(context.getString(R.string.guest_exit_dialog_message_non_ephemeral))
                setButton(
                    DialogInterface.BUTTON_NEUTRAL,
                    context.getString(android.R.string.cancel),
                    onClickListener,
                )
                setButton(
                    DialogInterface.BUTTON_NEGATIVE,
                    context.getString(R.string.guest_exit_clear_data_button),
                    onClickListener,
                )
                setButton(
                    DialogInterface.BUTTON_POSITIVE,
                    context.getString(R.string.guest_exit_save_data_button),
                    onClickListener,
                )
            }
            SystemUIDialog.setWindowOnTop(this, isKeyguardShowing)
            setCanceledOnTouchOutside(false)
        }

        return dialog
    }
}
