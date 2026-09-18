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
import android.os.UserHandle
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.android.internal.jank.InteractionJankMonitor
import com.android.media.flags.Flags.fixOutputSwitcherMultiuserSupport
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.dialog.MediaOutputDialogDelegate
import com.android.systemui.media.dialog.MediaOutputDialogManager
import com.android.systemui.media.dialog.MediaSwitchingType
import com.android.systemui.qs.panels.data.repository.QSPanelAppearanceRepository
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.model.MediaOutputComponentModel
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val AUDIO_TILE_DETAILS_DIALOG_TOP_OFFSET = 48
private const val AUDIO_TILE_DETAILS_DIALOG_VERTICAL_PADDING = 46

/** User actions interactor for Media Output Volume Panel component. */
@VolumePanelScope
class MediaOutputActionsInteractor
@Inject
constructor(
    @VolumePanelScope private val coroutineScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
    private val userTracker: UserTracker,
    private val mediaOutputDialogManager: MediaOutputDialogManager,
    private val qsPanelAppearanceRepository: QSPanelAppearanceRepository,
    private val expandedAudioTileDetailsFeatureInteractor: ExpandedAudioTileDetailsFeatureInteractor,
) {
    fun onBarClick(
        model: MediaOutputComponentModel?,
        expandable: Expandable?,
        mediaSwitchingType: MediaSwitchingType?,
    ) {
        val onDialogEventListener =
            if (expandedAudioTileDetailsFeatureInteractor.isEnabled()) {
                object : MediaOutputDialogDelegate.OnDialogEventListener {
                    private var job: Job? = null
                    private var onGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? =
                        null

                    override fun onCreate(dialog: Dialog) {
                        job?.cancel()
                        job =
                            coroutineScope.launch {
                                onGlobalLayoutListener =
                                    updateDialogBounds(
                                        dialog,
                                        qsPanelAppearanceRepository.qsPanelShape.value,
                                        onGlobalLayoutListener,
                                    )
                                // Update the dialog bounds when the QS panel shape changes.
                                qsPanelAppearanceRepository.qsPanelShape.collect { shape ->
                                    onGlobalLayoutListener =
                                        updateDialogBounds(dialog, shape, onGlobalLayoutListener)
                                }
                            }
                    }

                    override fun onStop(dialog: Dialog) {
                        dialog.window!!
                            .decorView
                            .viewTreeObserver
                            ?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
                        job?.cancel()
                    }
                }
            } else {
                null
            }

        coroutineScope.launch {
            if (model is MediaOutputComponentModel.MediaSession) {
                suspendCancellableCoroutine { continuation ->
                    if (fixOutputSwitcherMultiuserSupport()) {
                        mediaOutputDialogManager.createAndShowWithController(
                            packageName = model.session.packageName,
                            aboveStatusBar = false,
                            controller = expandable?.dialogController(),
                            userHandle =
                                UserHandle.getUserHandleForUid(model.session.sessionToken.uid),
                            token = model.session.sessionToken,
                            onDialogEventListener = onDialogEventListener,
                            mediaSwitchingType = mediaSwitchingType,
                            useSystemColors = true,
                        )
                    } else {
                        mediaOutputDialogManager.createAndShowWithController(
                            packageName = model.session.packageName,
                            aboveStatusBar = false,
                            controller = expandable?.dialogController(),
                            onDialogEventListener = onDialogEventListener,
                            mediaSwitchingType = mediaSwitchingType,
                            useSystemColors = true,
                        )
                    }
                    continuation.invokeOnCancellation { mediaOutputDialogManager.dismiss() }
                }
            } else {
                suspendCancellableCoroutine { continuation ->
                    if (fixOutputSwitcherMultiuserSupport()) {
                        mediaOutputDialogManager.createAndShowForSystemRouting(
                            expandable?.dialogController(),
                            onDialogEventListener,
                            mediaSwitchingType,
                            userHandle = userTracker.userHandle,
                        )
                    } else {
                        mediaOutputDialogManager.createAndShowForSystemRouting(
                            expandable?.dialogController(),
                            onDialogEventListener,
                            mediaSwitchingType,
                        )
                    }
                    continuation.invokeOnCancellation { mediaOutputDialogManager.dismiss() }
                }
            }
        }
    }

    private suspend fun updateDialogBounds(
        dialog: Dialog,
        shape: ShadeScrimShape?,
        listener: ViewTreeObserver.OnGlobalLayoutListener?,
    ): ViewTreeObserver.OnGlobalLayoutListener? {
        // Update the dialog UI on main thread.
        return withContext(mainDispatcher) {
            if (shape == null) {
                return@withContext null
            }
            val qsPanelBounds = shape.bounds
            val dialogWidth = (qsPanelBounds.right - qsPanelBounds.left).toInt()
            val lp: WindowManager.LayoutParams = dialog.window!!.attributes
            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.width = dialogWidth
            lp.x = (qsPanelBounds.left + qsPanelBounds.right - dialogWidth).toInt() / 2
            lp.y = (qsPanelBounds.top + AUDIO_TILE_DETAILS_DIALOG_TOP_OFFSET).toInt()
            dialog.window!!.attributes = lp

            val dialogDecorView = dialog.window!!.decorView
            if (listener != null) {
                dialogDecorView.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            }
            val maxHeight =
                (qsPanelBounds.bottom -
                        qsPanelBounds.top -
                        AUDIO_TILE_DETAILS_DIALOG_VERTICAL_PADDING)
                    .toInt()
            val newListener: ViewTreeObserver.OnGlobalLayoutListener =
                ViewTreeObserver.OnGlobalLayoutListener {
                    if (dialogDecorView.height > maxHeight) {
                        lp.height = maxHeight
                        dialog.window!!.attributes = lp
                    }
                }
            dialogDecorView.viewTreeObserver?.addOnGlobalLayoutListener(newListener)
            newListener
        }
    }

    private fun Expandable.dialogController(): DialogTransitionAnimator.Controller? {
        if (expandedAudioTileDetailsFeatureInteractor.isEnabled()) {
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
