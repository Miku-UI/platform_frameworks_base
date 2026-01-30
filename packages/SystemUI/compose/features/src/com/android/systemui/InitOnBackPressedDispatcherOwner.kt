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

package com.android.systemui

import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import com.android.systemui.scene.shared.flag.SceneContainerFlag

/**
 * Attaches a [OnBackPressedDispatcherOwner] to a [View] to prevent a [IllegalStateException].
 *
 * When [SceneContainerFlag] is enabled this is usually not needed for child [ComposeView]s because
 * it is sufficient to attach it to the root container. However, when [SceneContainerFlag] is
 * disabled, this function needs to be called for each [ComposeView] or one of its ancestors. Needs
 * to be called within [repeatOnLifeCycle] as follows:
 * ```
 * ComposeView(context).apply {
 *     repeatWhenAttached {
 *         repeatOnLifecycle(Lifecycle.State.CREATED) {
 *             initOnBackPressedDispatcherOwner(this@repeatWhenAttached.lifecycle)
 *             setContent {
 *                 PlatformTheme { YourComposable() }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Make sure to call [setContent] within the same [repeatOnLifeCycle] block and after this init
 * otherwise a composition might happen before and cause a crash.
 *
 * @param lifecycle The lifecycle of the view.
 * @param force Whether the dispatcher should be set up regardless of any flag checks.
 */
fun View.initOnBackPressedDispatcherOwner(lifecycle: Lifecycle, force: Boolean = false) {
    if (!SceneContainerFlag.isEnabled || force) {
        setViewTreeOnBackPressedDispatcherOwner(
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher =
                    OnBackPressedDispatcher().apply {
                        setOnBackInvokedDispatcher(viewRootImpl.onBackInvokedDispatcher)
                    }

                override val lifecycle: Lifecycle = lifecycle
            }
        )
    }
}
