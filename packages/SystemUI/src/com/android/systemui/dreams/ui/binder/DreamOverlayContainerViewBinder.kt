/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dreams.ui.binder

import android.view.View
import android.view.ViewStub
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dream.ui.composable.EdgeSwipeIndicator
import com.android.systemui.dreams.ui.viewmodel.DreamOverlayContainerViewModel
import com.android.systemui.lifecycle.WindowLifecycleState
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.setSnapshotBinding
import com.android.systemui.lifecycle.viewModel
import com.android.systemui.res.R
import java.util.function.Consumer

object DreamOverlayContainerViewBinder {

    fun bind(
        view: View,
        viewModel: DreamOverlayContainerViewModel,
        onDialogShowingChanged: Consumer<Boolean>,
    ) {
        view.repeatWhenAttached {
            view.viewModel(
                traceName = "DreamOverlayContainerViewBinder",
                minWindowLifecycleState = WindowLifecycleState.ATTACHED,
                /**
                 * The viewModel creation is hoisted to [DreamOverlayService], so just return the
                 * instance here
                 */
                factory = { viewModel },
            ) { _ ->
                createEdgeSwipeIndicatorView(view, viewModel)

                var currentActionId: Int? = null
                view.setSnapshotBinding {
                    val actionModel = viewModel.dreamSwitcherAction

                    currentActionId?.let {
                        ViewCompat.removeAccessibilityAction(view, it)
                        currentActionId = null
                    }

                    view.isFocusable = actionModel != null

                    if (actionModel != null) {
                        val actionLabel = view.resources.getString(actionModel.labelResId)
                        currentActionId =
                            ViewCompat.addAccessibilityAction(view, actionLabel) { _, _ ->
                                actionModel.action()
                                true
                            }
                    }

                    onDialogShowingChanged.accept(viewModel.dialogShowing)
                }
            }
        }
    }

    private fun createEdgeSwipeIndicatorView(
        view: View,
        viewModel: DreamOverlayContainerViewModel,
    ) {
        var composeView = view.findViewById<ComposeView>(R.id.edge_swipe_indicator_compose_view)
        if (composeView == null) {
            val stub = view.findViewById<ViewStub>(R.id.edge_swipe_indicator_stub)
            composeView = stub?.inflate() as? ComposeView
        }

        composeView?.setContent {
            PlatformTheme { EdgeSwipeIndicator(viewModel.edgeSwipeViewModel) }
        }
    }
}
