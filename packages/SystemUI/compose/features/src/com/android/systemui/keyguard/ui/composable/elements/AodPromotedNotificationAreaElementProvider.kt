/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.promoted.AODPromotedNotification
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.ui.viewmodel.AODPromotedNotificationViewModel
import com.android.systemui.util.ui.isAnimating
import com.android.systemui.util.ui.stopAnimating
import com.android.systemui.util.ui.value
import javax.inject.Inject
import kotlin.collections.List

@SysUISingleton
class AodPromotedNotificationAreaElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val keyguardRootViewModel: KeyguardRootViewModel,
    private val aodPromotedNotificationViewModelFactory: AODPromotedNotificationViewModel.Factory,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(PromotedNotificationElement()) }

    private inner class PromotedNotificationElement : LockscreenElement {
        override val key = LockscreenElementKeys.Notifications.AOD.Promoted
        override val context = this@AodPromotedNotificationAreaElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            if (PromotedNotificationUi.isEnabled) {
                AodPromotedNotificationArea()
            }
        }
    }

    @Composable
    private fun LockscreenScope<ContentScope>.AodPromotedNotificationArea(
        modifier: Modifier = Modifier
    ) {
        val isVisible by
            keyguardRootViewModel.isAodPromotedNotifVisible.collectAsStateWithLifecycle()
        val transitionState = remember { MutableTransitionState(isVisible.value) }
        LaunchedEffect(key1 = isVisible, key2 = transitionState.isIdle) {
            transitionState.targetState = isVisible.value
            if (isVisible.isAnimating && transitionState.isIdle) {
                isVisible.stopAnimating()
            }
        }

        AnimatedVisibility(
            visibleState = transitionState,
            enter = if (isVisible.isAnimating) fadeIn() else EnterTransition.None,
            exit = if (isVisible.isAnimating) fadeOut() else ExitTransition.None,
            modifier = modifier.then(context.burnInModifier),
        ) {
            AODPromotedNotification(
                viewModelFactory = aodPromotedNotificationViewModelFactory,
                modifier = Modifier.sysuiResTag("aod_promoted_notification_frame"),
            )
        }
    }
}
