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

package com.android.systemui.statusbar.chips.ui.compose

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder

/**
 * Composable for all ongoing activity chips shown in the status bar.
 *
 * @param onChipBoundsChanged should be invoked each time any chip has their on-screen bounds
 *   changed.
 */
@Composable
fun OngoingActivityChips(
    chips: MultipleOngoingActivityChipsModel,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    onChipBoundsChanged: (String, RectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (StatusBarChipsReturnAnimations.isEnabled) {
        SideEffect {
            // Active chips must always be capable of animating to/from activities, even when they
            // are hidden. Therefore we always register their transitions.
            for (chip in chips.active) chip.transitionManager?.registerTransition?.invoke()
            // Inactive chips and chips in the overflow are never shown, so they must not have any
            // registered transition.
            for (chip in chips.overflow) chip.transitionManager?.unregisterTransition?.invoke()
            for (chip in chips.inactive) chip.transitionManager?.unregisterTransition?.invoke()
        }
    }

    val activeChips = chips.active
    if (activeChips.isNotEmpty()) {
        // For performance reasons, only create the Row if we have chips. See b/401241197.
        Row(
            // The status bar clock will have some end padding so we don't need as much padding
            // at the beginning of the row (but we need more padding between chips)
            modifier = modifier.fillMaxHeight().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            activeChips.forEach {
                key(it.key) {
                    val chipModifier =
                        Modifier.sysuiResTag(it.key).onGloballyPositioned { coordinates ->
                            onChipBoundsChanged.invoke(
                                it.key,
                                coordinates.boundsInWindow().toAndroidRectF(),
                            )
                        }
                    if (activeChips.size == 1) {
                        // AnimatedVisibility works well if we have just 1 active chip, but it
                        // causes some problems if there's 2 chips and then one chip becomes hidden.
                        // For now, use AnimatedVisibility only if we only have 1 active chip. See
                        // b/393581408.
                        AnimatedVisibility(
                            visible = !it.isHidden,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            OngoingActivityChip(
                                model = it,
                                iconViewStore = iconViewStore,
                                modifier = chipModifier,
                            )
                        }
                    } else {
                        if (!it.isHidden) {
                            OngoingActivityChip(
                                model = it,
                                iconViewStore = iconViewStore,
                                modifier = chipModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
