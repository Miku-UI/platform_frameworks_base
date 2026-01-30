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

package com.android.systemui.communal.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.thenIf
import com.android.systemui.Flags
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.communal.smartspace.SmartspaceInteractionHandler
import com.android.systemui.communal.ui.compose.extensions.consumeHorizontalDragGestures
import com.android.systemui.communal.ui.compose.section.CommunalPopupSection
import com.android.systemui.communal.ui.compose.section.HubOnboardingSection
import com.android.systemui.communal.ui.view.layout.sections.CommunalAppWidgetSection
import com.android.systemui.communal.ui.viewmodel.CommunalViewModel
import com.android.systemui.keyguard.ui.composable.elements.IndicationAreaElementProvider
import com.android.systemui.keyguard.ui.composable.elements.LockIconElementProvider
import com.android.systemui.keyguard.ui.composable.layout.LockIconAlignmentLines
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import javax.inject.Inject

/** Renders the content of the glanceable hub. */
class CommunalContent
@Inject
constructor(
    private val viewModel: CommunalViewModel,
    private val interactionHandler: SmartspaceInteractionHandler,
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    private val dialogFactory: SystemUIDialogFactory,
    private val lockElement: LockIconElementProvider,
    private val indicationAreaElement: IndicationAreaElementProvider,
    private val communalPopupSection: CommunalPopupSection,
    private val widgetSection: CommunalAppWidgetSection,
    private val hubOnboardingSection: HubOnboardingSection,
) {

    @Composable
    fun ContentScope.Content(modifier: Modifier = Modifier) {
        val showLockIconAndChargingStatus = !communalSettingsInteractor.isV2FlagEnabled()

        CommunalTouchableSurface(viewModel = viewModel, modifier = modifier) {
            val orientation = LocalConfiguration.current.orientation
            var gridRegion by remember { mutableStateOf<Rect?>(null) }
            val showBackgroundForEditModeTransition by
                viewModel.showBackgroundForEditModeTransition.collectAsStateWithLifecycle(
                    initialValue = false
                )
            val empty by viewModel.isEmptyState.collectAsStateWithLifecycle(initialValue = false)

            // The animated background here matches the color scheme of the edit mode activity and
            // facilitates the transition to and from edit mode.
            AnimatedVisibility(
                visible = showBackgroundForEditModeTransition,
                enter = fadeIn(tween(TransitionDuration.EDIT_MODE_BACKGROUND_ANIM_DURATION_MS)),
                exit = fadeOut(tween(TransitionDuration.EDIT_MODE_BACKGROUND_ANIM_DURATION_MS)),
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim))
            }

            Layout(
                modifier =
                    Modifier.fillMaxSize().thenIf(
                        communalSettingsInteractor.isV2FlagEnabled() && !empty
                    ) {
                        Modifier.consumeHorizontalDragGestures(gridRegion)
                    },
                content = {
                    Box(
                        modifier =
                            Modifier.fillMaxSize().onGloballyPositioned {
                                gridRegion =
                                    Rect(offset = it.positionInWindow(), size = it.size.toSize())
                            }
                    ) {
                        with(communalPopupSection) { Popup() }
                        CommunalHub(
                            viewModel = viewModel,
                            interactionHandler = interactionHandler,
                            dialogFactory = dialogFactory,
                            widgetSection = widgetSection,
                            modifier = Modifier.element(Communal.Elements.Grid),
                            contentScope = this@Content,
                        )
                        with(hubOnboardingSection) { BottomSheet() }
                    }
                    if (showLockIconAndChargingStatus) {
                        with(lockElement) {
                            LockIcon(
                                overrideColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.element(Communal.Elements.LockIcon),
                            )
                        }

                        with(indicationAreaElement) {
                            IndicationArea(
                                Modifier.element(Communal.Elements.IndicationArea)
                                    .fillMaxWidth()
                                    .padding(
                                        bottom =
                                            dimensionResource(
                                                R.dimen.keyguard_indication_margin_bottom
                                            )
                                    )
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val communalGridMeasurable = measurables[0]
                val lockIconMeasurable = if (showLockIconAndChargingStatus) measurables[1] else null
                val bottomAreaMeasurable =
                    if (showLockIconAndChargingStatus) measurables[2] else null

                val noMinConstraints = constraints.copy(minWidth = 0, minHeight = 0)

                val lockIconPlaceable = lockIconMeasurable?.measure(noMinConstraints)
                val lockIconBounds =
                    if (lockIconPlaceable == null) {
                        null
                    } else {
                        IntRect(
                            left = lockIconPlaceable[LockIconAlignmentLines.Left],
                            top = lockIconPlaceable[LockIconAlignmentLines.Top],
                            right = lockIconPlaceable[LockIconAlignmentLines.Right],
                            bottom = lockIconPlaceable[LockIconAlignmentLines.Bottom],
                        )
                    }

                val bottomAreaPlaceable = bottomAreaMeasurable?.measure(noMinConstraints)

                val communalGridMaxHeight: Int
                val communalGridPositionY: Int
                if (Flags.communalResponsiveGrid()) {
                    val communalGridVerticalMargin =
                        if (lockIconBounds == null) {
                            0
                        } else {
                            constraints.maxHeight - lockIconBounds.top
                        }
                    // Use even top and bottom margin for grid to be centered in maxHeight (window)
                    communalGridMaxHeight = constraints.maxHeight - communalGridVerticalMargin * 2
                    communalGridPositionY = communalGridVerticalMargin
                } else {
                    communalGridMaxHeight = lockIconBounds?.top ?: constraints.maxHeight
                    communalGridPositionY = 0
                }
                val communalGridPlaceable =
                    communalGridMeasurable.measure(
                        noMinConstraints.copy(maxHeight = communalGridMaxHeight)
                    )

                layout(constraints.maxWidth, constraints.maxHeight) {
                    communalGridPlaceable.place(x = 0, y = communalGridPositionY)
                    if (lockIconBounds != null) {
                        lockIconPlaceable!!.place(x = lockIconBounds.left, y = lockIconBounds.top)
                    }

                    if (bottomAreaPlaceable != null) {
                        val bottomAreaTop = constraints.maxHeight - bottomAreaPlaceable.height
                        bottomAreaPlaceable.place(x = 0, y = bottomAreaTop)
                    }
                }
            }
        }
    }
}
