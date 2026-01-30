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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.deviceentry.ui.view.UdfpsAccessibilityOverlayOverlappingView
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.binder.AccessibilityActionsViewBinder
import com.android.systemui.keyguard.ui.viewmodel.AccessibilityActionsViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.Utils
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle

/**
 * A placeholder section that provides shortcuts for navigating on the keyguard through
 * accessibility actions.
 */
class AccessibilityActionsSection
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    private val accessibilityActionsViewModel: AccessibilityActionsViewModel,
) : KeyguardSection() {
    private val viewId = R.id.accessibility_actions_view
    private var cachedConstraintLayout: ConstraintLayout? = null

    private var accessibilityActionsViewHandle: DisposableHandle? = null

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!communalSettingsInteractor.isCommunalFlagEnabled()) {
            return
        }
        cachedConstraintLayout = constraintLayout
        val view =
            UdfpsAccessibilityOverlayOverlappingView(constraintLayout.context).apply { id = viewId }
        constraintLayout.addView(view)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (!communalSettingsInteractor.isCommunalFlagEnabled()) {
            return
        }
        val view =
            constraintLayout.requireViewById<UdfpsAccessibilityOverlayOverlappingView>(viewId)
        accessibilityActionsViewHandle =
            AccessibilityActionsViewBinder.bind(view, accessibilityActionsViewModel)
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            // Starts from the bottom of the status bar.
            connect(
                viewId,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                Utils.getStatusBarHeaderHeightKeyguard(context),
            )
            connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            // Full width
            connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        accessibilityActionsViewHandle?.dispose()
        accessibilityActionsViewHandle = null
        constraintLayout.removeView(viewId)
    }
}
