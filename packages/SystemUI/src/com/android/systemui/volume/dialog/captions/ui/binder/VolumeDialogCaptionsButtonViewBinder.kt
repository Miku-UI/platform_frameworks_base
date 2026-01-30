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

package com.android.systemui.volume.dialog.captions.ui.binder

import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.view.View
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.volume.CaptionsToggleImageButton
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.captions.ui.viewmodel.VolumeDialogCaptionsButtonViewModel
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.ui.binder.ViewBinder
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex

/** Binds the captions button view. */
@VolumeDialogScope
class VolumeDialogCaptionsButtonViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogCaptionsButtonViewModel,
    private val dialogViewModel: VolumeDialogViewModel,
    @Background private val bgHandler: Handler,
) : ViewBinder {
    override fun CoroutineScope.bind(view: View) {
        if (!Flags.captionsToggleInVolumeDialogV1()) {
            return
        }

        val captionsButton = view.requireViewById<CaptionsToggleImageButton>(R.id.odi_captions_icon)

        launchTraced("VDCBVB#addTouchableBounds") {
            dialogViewModel.addTouchableBounds(captionsButton)
        }

        viewModel.isVisible
            .onEach { isVisible ->
                captionsButton.visibility =
                    if (isVisible) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
            .launchInTraced("VDCBVB#isVisible", this)

        viewModel.isEnable
            .withIndex()
            .onEach { (index, isEnabled) ->
                captionsButton.apply {
                    setImageResource(
                        if (isEnabled) {
                            R.drawable.ic_volume_odi_captions
                        } else {
                            R.drawable.ic_volume_odi_captions_disabled
                        }
                    )

                    setColorFilter(
                        captionsButton.context.getColor(
                            if (isEnabled) {
                                com.android.internal.R.color.materialColorOnPrimary
                            } else {
                                com.android.internal.R.color.materialColorOnSurface
                            }
                        )
                    )

                    val transition = background as TransitionDrawable
                    transition.isCrossFadeEnabled = true
                    if (index == 0) {
                        if (isEnabled) {
                            transition.startTransition(0)
                        }
                    } else {
                        if (isEnabled) {
                            transition.startTransition(DURATION_MILLIS)
                        } else {
                            transition.reverseTransition(DURATION_MILLIS)
                        }
                    }

                    setCaptionsEnabled(isEnabled)
                }
            }
            .launchInTraced("VDCBVB#isEnabled", this)

        captionsButton.setOnConfirmedTapListener(
            {
                viewModel.onButtonClicked()
                Events.writeEvent(Events.EVENT_ODI_CAPTIONS_CLICK)
            },
            bgHandler,
        )
    }

    private companion object {
        const val DURATION_MILLIS = 500
    }
}
