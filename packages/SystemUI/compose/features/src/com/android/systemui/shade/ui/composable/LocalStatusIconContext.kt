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

package com.android.systemui.shade.ui.composable

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.compose.animation.scene.ContentKey
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.systemstatusicons.ui.compose.MovableSystemStatusIconLegacy
import com.android.systemui.statusbar.systemstatusicons.ui.compose.movableSystemStatusIconsLegacyAndroidView

/**
 * Defines interface for classes that can provide a context for UI that renders the status bar
 * icons.
 */
interface StatusIconContext {

    /**
     * Returns a [StatusIconContainer] for the given [contentKey]. Note that this container will be
     * cached in the [StatusIconContext].
     */
    fun iconContainer(contentKey: ContentKey): StatusIconContainer

    /**
     * Returns a [TintedIconManager] for the given [contentKey]. Note that this manager will be
     * cached in the [StatusIconContext].
     */
    fun iconManager(contentKey: ContentKey): TintedIconManager

    /**
     * Returns a [MovableSystemStatusIconLegacy] movable content for the given [TintedIconManager].
     * This movable content will be cached in the [StatusIconContext].
     */
    fun movableContent(tintedIconManager: TintedIconManager): MovableSystemStatusIconLegacy
}

/** This is only set inside the SceneContainer STL. */
val LocalStatusIconContext =
    compositionLocalOf<StatusIconContext> { error("LocalStatusIconContext not set!") }

@Composable
fun WithStatusIconContext(
    tintedIconManagerFactory: TintedIconManager.Factory,
    block: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusIconContext provides rememberStatusIconContext(tintedIconManagerFactory),
        content = block,
    )
}

@Composable
fun rememberStatusIconContext(
    tintedIconManagerFactory: TintedIconManager.Factory
): StatusIconContext {
    val context = LocalContext.current

    return remember(context, tintedIconManagerFactory) {
        object : StatusIconContext {
            private val iconContainerByContentKey = mutableMapOf<ContentKey, StatusIconContainer>()
            private val iconManagerByContentKey = mutableMapOf<ContentKey, TintedIconManager>()
            private val movableContentByIconManager =
                mutableMapOf<TintedIconManager, MovableSystemStatusIconLegacy>()

            override fun iconContainer(contentKey: ContentKey): StatusIconContainer {
                return iconContainerByContentKey.getOrPut(contentKey) {
                    StatusIconContainer(
                        ContextThemeWrapper(context, R.style.Theme_SystemUI_QuickSettings_Header),
                        null,
                    )
                }
            }

            override fun iconManager(contentKey: ContentKey): TintedIconManager {
                return iconManagerByContentKey.getOrPut(contentKey) {
                    tintedIconManagerFactory.create(iconContainer(contentKey), StatusBarLocation.QS)
                }
            }

            override fun movableContent(
                tintedIconManager: TintedIconManager
            ): MovableSystemStatusIconLegacy {
                return movableContentByIconManager.getOrPut(tintedIconManager) {
                    movableSystemStatusIconsLegacyAndroidView(tintedIconManager)
                }
            }
        }
    }
}
