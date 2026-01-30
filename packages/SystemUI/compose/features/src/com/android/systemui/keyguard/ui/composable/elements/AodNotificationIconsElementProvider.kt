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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.AlwaysOnDisplayNotificationIconViewStore
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.StatusBarIconViewBindingFailureTracker
import com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerAlwaysOnDisplayViewModel
import com.android.systemui.statusbar.phone.NotificationIconContainer
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import com.android.systemui.util.ui.isAnimating
import com.android.systemui.util.ui.stopAnimating
import com.android.systemui.util.ui.value
import javax.inject.Inject
import kotlin.collections.List
import kotlinx.coroutines.launch

@SysUISingleton
class AodNotificationIconsElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val keyguardRootViewModel: KeyguardRootViewModel,
    @ShadeDisplayAware private val configurationState: ConfigurationState,
    private val iconBindingFailureTracker: StatusBarIconViewBindingFailureTracker,
    private val nicAodViewModel: NotificationIconContainerAlwaysOnDisplayViewModel,
    private val nicAodIconViewStore: AlwaysOnDisplayNotificationIconViewStore,
    @ShadeDisplayAware private val systemBarUtilsState: SystemBarUtilsState,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(AodNotificationElement()) }

    private inner class AodNotificationElement : LockscreenElement {
        override val key = LockscreenElementKeys.Notifications.AOD.IconShelf
        override val context = this@AodNotificationIconsElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            AodNotificationIcons()
        }
    }

    @Composable
    private fun LockscreenScope<ContentScope>.AodNotificationIcons(modifier: Modifier = Modifier) {
        val isVisible by
            keyguardRootViewModel.isNotifIconContainerVisible.collectAsStateWithLifecycle()
        val transitionState = remember { MutableTransitionState(isVisible.value) }
        LaunchedEffect(key1 = isVisible, key2 = transitionState.isIdle) {
            transitionState.targetState = isVisible.value
            if (isVisible.isAnimating && transitionState.isIdle) {
                isVisible.stopAnimating()
            }
        }

        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                modifier
                    .height(dimensionResource(R.dimen.notification_shelf_height))
                    .padding(
                        start = dimensionResource(R.dimen.below_clock_padding_start_icons),
                        end = dimensionResource(R.dimen.shelf_icon_container_padding),
                    )
                    .then(context.burnInModifier),
        ) {
            val scope = rememberCoroutineScope()
            AndroidView(
                factory = { context ->
                    NotificationIconContainer(context, null).also { nic ->
                        scope.launch {
                            NotificationIconContainerViewBinder.bind(
                                nic,
                                nicAodViewModel,
                                configurationState,
                                systemBarUtilsState,
                                iconBindingFailureTracker,
                                nicAodIconViewStore,
                            )
                        }
                    }
                }
            )
        }
    }
}
