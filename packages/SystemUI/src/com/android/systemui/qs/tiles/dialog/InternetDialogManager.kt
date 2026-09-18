/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.qs.tiles.dialog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.Flags
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.TransitionAnimator
import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.res.R
import com.android.systemui.retail.domain.interactor.RetailModeInteractor
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.phone.SystemUIDialog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

private const val TAG = "InternetDialogFactory"
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

/** Factory to create Internet Dialog objects. */
@SysUISingleton
class InternetDialogManager
@Inject
constructor(
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val contentManagerFactory: InternetDetailsContentManager.Factory,
    private val internetDialogDelegateLegacyFactory: InternetDialogDelegateLegacy.Factory,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    private val shadeDialogContextInteractor: ShadeDialogContextInteractor,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val retailModeInteractor: RetailModeInteractor,
) {
    private lateinit var coroutineScope: CoroutineScope

    companion object {
        private const val INTERACTION_JANK_TAG = "internet"
        var dialog: SystemUIDialog? = null
    }

    private inner class InternetDialogDelegate(
        private val contentManager: InternetDetailsContentManager,
        private val aboveStatusBar: Boolean,
    ) : SystemUIDialog.Delegate {
        override fun createDialog(): SystemUIDialog {
            val dialog = systemUIDialogFactory.create(this, shadeDialogContextInteractor.context)
            if (!aboveStatusBar) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            return dialog
        }

        override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
            val view =
                LayoutInflater.from(dialog.context)
                    .inflate(R.layout.internet_connectivity_dialog, null)
            dialog.window?.setContentView(view)
            dialog.window?.setWindowAnimations(R.style.Animation_InternetDialog)
            contentManager.bind(view, dialog, coroutineScope)
        }

        override fun onStart(dialog: SystemUIDialog) {
            contentManager.initializeAndConfigure()
        }

        override fun onStop(dialog: SystemUIDialog) {
            contentManager.unBind()
            destroyDialog()
        }
    }

    /**
     * Creates an internet dialog. The dialog will be animated from [expandable] if it is not null.
     */
    fun create(
        aboveStatusBar: Boolean,
        canConfigMobileData: Boolean,
        canConfigWifi: Boolean,
        expandable: Expandable?,
    ) {
        if (shadeModeInteractor.isDualShade && !retailModeInteractor.isInRetailMode) {
            // If `QsDetailedView` is enabled, it should show the details view.
            // Will still show the dialog if it's not dual shade mode, or it's in retail mode.
            QsDetailedView.assertInLegacyMode()
        }
        if (dialog != null) {
            if (DEBUG) {
                Log.d(TAG, "InternetDialog is showing, do not create it twice.")
            }
            return
        }
        coroutineScope = CoroutineScope(bgDispatcher + newTracingContext("InternetDialogScope"))

        val delegate: SystemUIDialog.Delegate
        if (Flags.internetDialogDelegateLegacyDeprecation()) {
            val contentManager =
                contentManagerFactory.create(
                    canConfigMobileData = canConfigMobileData,
                    canConfigWifi = canConfigWifi,
                    isInDialog = true,
                )
            delegate = InternetDialogDelegate(contentManager, aboveStatusBar)
        } else {
            delegate =
                internetDialogDelegateLegacyFactory.create(
                    aboveStatusBar,
                    canConfigMobileData,
                    canConfigWifi,
                    coroutineScope,
                )
        }

        dialog = delegate.createDialog()
        val controller =
            expandable?.dialogTransitionController(
                DialogCuj(InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN, INTERACTION_JANK_TAG)
            )
        controller?.let {
            if (TransitionAnimator.dynamicTargetResolutionEnabled()) {
                dialogTransitionAnimator.show(
                    dialog!!,
                    expandable::dialogTransitionController,
                    it.cuj,
                    animateBackgroundBoundsChange = true,
                )
            } else {
                dialogTransitionAnimator.show(dialog!!, it, animateBackgroundBoundsChange = true)
            }
        } ?: dialog?.show()
    }

    fun destroyDialog() {
        if (DEBUG) {
            Log.d(TAG, "destroyDialog")
        }
        if (dialog != null) {
            coroutineScope.cancel()
        }
        dialog = null
    }
}
