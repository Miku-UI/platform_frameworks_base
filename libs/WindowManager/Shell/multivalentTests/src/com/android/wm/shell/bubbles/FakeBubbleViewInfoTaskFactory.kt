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

package com.android.wm.shell.bubbles

import android.content.Context
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView
import com.android.wm.shell.bubbles.user.data.BubbleUserResolver
import java.util.concurrent.Executor

/** An implementation of [BubbleViewInfoTask.Factory] for testing. */
class FakeBubbleViewInfoTaskFactory(
    private val positioner: BubblePositioner,
    private val appInfoProvider: BubbleAppInfoProvider,
    private val mainExecutor: Executor,
    private val backgroundExecutor: Executor,
    private val userResolver: BubbleUserResolver,
) : BubbleViewInfoTask.Factory {

    override fun create(
        b: Bubble,
        context: Context,
        expandedViewManager: BubbleExpandedViewManager,
        taskViewFactory: BubbleTaskViewFactory,
        stackView: BubbleStackView?,
        layerView: BubbleBarLayerView?,
        iconFactory: BubbleIconFactory,
        skipInflation: Boolean,
        callback: BubbleViewInfoTask.Callback?,
    ): BubbleViewInfoTask =
        BubbleViewInfoTask(
            b,
            context,
            expandedViewManager,
            taskViewFactory,
            stackView,
            layerView,
            iconFactory,
            skipInflation,
            callback,
            positioner,
            appInfoProvider,
            mainExecutor,
            backgroundExecutor,
            userResolver,
        )
}
