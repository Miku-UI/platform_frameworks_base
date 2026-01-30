/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.panel.component.mediaoutput.domain.interactor

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.WindowManager
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.media.dialog.MediaOutputDialog
import com.android.systemui.media.dialog.MediaOutputDialogManager
import com.android.systemui.qs.panels.data.repository.QSPanelAppearanceRepository
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.volume.dialog.domain.interactor.DesktopAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.model.MediaOutputComponentModel
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import javax.inject.Inject

/** User actions interactor for Media Output Volume Panel component. */
@VolumePanelScope
class MediaOutputActionsInteractor
@Inject
constructor(
    @Application private val context: Context,
    private val mediaOutputDialogManager: MediaOutputDialogManager,
    private val qsPanelAppearanceRepository: QSPanelAppearanceRepository,
    private val desktopAudioTileDetailsFeatureInteractor: DesktopAudioTileDetailsFeatureInteractor,
) {
    private val mDesktopDialogWidth =
        context.getResources().getDimensionPixelSize(R.dimen.shade_panel_width)
    private val mDesktopDialogHeight = 650

    fun onBarClick(model: MediaOutputComponentModel?, expandable: Expandable?) {
        val onDialogEventListener =
            if (desktopAudioTileDetailsFeatureInteractor.isEnabled()) {
                object : MediaOutputDialog.OnDialogEventListener {
                    override fun onConfigurationChanged(dialog: Dialog, newConfig: Configuration) {
                        updateDialogBounds(dialog, qsPanelAppearanceRepository.qsPanelShape.value)
                    }

                    override fun onCreate(dialog: Dialog) {
                        updateDialogBounds(dialog, qsPanelAppearanceRepository.qsPanelShape.value)
                    }
                }
            } else {
                null
            }

        if (model is MediaOutputComponentModel.MediaSession) {
            mediaOutputDialogManager.createAndShowWithController(
                packageName = model.session.packageName,
                aboveStatusBar = false,
                controller = expandable?.dialogController(),
                onDialogEventListener = onDialogEventListener,
            )
        } else {
            mediaOutputDialogManager.createAndShowForSystemRouting(
                expandable?.dialogController(),
                onDialogEventListener,
            )
        }
    }

    private fun updateDialogBounds(dialog: Dialog, shape: ShadeScrimShape?) {
        if (shape == null) {
            return
        }
        val qsPanelBounds = shape.bounds
        val lp: WindowManager.LayoutParams = dialog.window!!.attributes
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.width = mDesktopDialogWidth
        lp.height = mDesktopDialogHeight
        // Position the dialog at the center of the qsPanelBounds
        lp.x = (qsPanelBounds.left + qsPanelBounds.right - mDesktopDialogWidth).toInt() / 2
        lp.y = (qsPanelBounds.top + qsPanelBounds.bottom - mDesktopDialogHeight).toInt() / 2
        dialog.window!!.attributes = lp
    }

    private fun Expandable.dialogController(): DialogTransitionAnimator.Controller? {
        if (desktopAudioTileDetailsFeatureInteractor.isEnabled()) {
            return null
        }
        return dialogTransitionController(
            cuj =
                DialogCuj(
                    InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                    MediaOutputDialogManager.INTERACTION_JANK_TAG,
                )
        )
    }
}
