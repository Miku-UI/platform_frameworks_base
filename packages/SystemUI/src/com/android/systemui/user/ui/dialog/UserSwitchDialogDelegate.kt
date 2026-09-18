package com.android.systemui.user.ui.dialog

import android.content.Context
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import com.android.internal.logging.UiEventLogger
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.QSUserSwitcherEvent
import com.android.systemui.qs.tiles.UserDetailView
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Extracted from the old UserSwitchDialogController. This is the dialog version of the full-screen
 * user switcher. See config_enableFullscreenUserSwitcher
 */
class UserSwitchDialogDelegate
@AssistedInject
constructor(
    private val adapter: UserDetailView.Adapter,
    private val uiEventLogger: UiEventLogger,
    private val falsingManager: FalsingManager,
    private val activityStarter: ActivityStarter,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    @Assisted(USER_SWITCH_DIALOG_CONTEXT) private val context: Context,
) : SystemUIDialog.Delegate {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted(USER_SWITCH_DIALOG_CONTEXT) context: Context): UserSwitchDialogDelegate
    }

    override fun createDialog(): SystemUIDialog {
        val dialog = systemUIDialogFactory.create(this, context)
        with(dialog) {
            setShowForAllUsers(true)
            setCanceledOnTouchOutside(true)
            setTitle(R.string.qs_user_switch_dialog_title)
            setPositiveButton(R.string.quick_settings_done) { _, _ ->
                uiEventLogger.log(QSUserSwitcherEvent.QS_USER_DETAIL_CLOSE)
            }
            setNeutralButton(
                R.string.quick_settings_more_user_settings,
                { _, _ ->
                    if (!falsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
                        uiEventLogger.log(QSUserSwitcherEvent.QS_USER_MORE_SETTINGS)
                        val controller =
                            dialogTransitionAnimator.createActivityTransitionController(
                                getButton(BUTTON_NEUTRAL)
                            )

                        if (controller == null) {
                            dismiss()
                        }

                        activityStarter.postStartActivityDismissingKeyguard(
                            USER_SETTINGS_INTENT,
                            0,
                            controller,
                        )
                    }
                },
                false, /* dismissOnClick */
            )
            val gridFrame =
                LayoutInflater.from(this.context).inflate(R.layout.qs_user_dialog_content, null)
            setView(gridFrame)

            adapter.linkToViewGroup(gridFrame.findViewById(R.id.grid))
            adapter.injectDialogShower(DialogShowerImpl(this, dialogTransitionAnimator))
        }

        return dialog
    }

    companion object {
        private const val USER_SWITCH_DIALOG_CONTEXT = "context"
        private val USER_SETTINGS_INTENT = Intent(Settings.ACTION_USER_SETTINGS)
    }
}
