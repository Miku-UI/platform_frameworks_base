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
package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Trace
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.View
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecoration2
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.AppPinnedViewHolder
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder
import kotlinx.coroutines.CoroutineScope

/**
 * Controller for the Pinned Caption, used for exclusive floating windows, interactive PiP, or
 * whatever this feature ends up being called. Names are TBD and this comment will get updated.
 */
class AppPinnedController(
    taskInfo: RunningTaskInfo,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val decorWindowContext: Context,
    private val displayController: DisplayController,
    private val onTouchListener: View.OnTouchListener,
    private val onGenericMotionEventListener: View.OnGenericMotionListener,
    private val windowDecorationActions: WindowDecorationActions,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    taskOrganizer: ShellTaskOrganizer,
    @ShellBackgroundThread bgScope: CoroutineScope,
) :
    CaptionController<WindowDecorLinearLayout>(
        taskInfo,
        windowDecorViewHostSupplier,
        taskOrganizer,
        bgScope,
    ) {

    companion object {
        private const val TAG = "AppPinnedController"
    }

    private lateinit var viewHolder: AppPinnedViewHolder

    override fun createCaptionView(): WindowDecorationViewHolder<*> {
        val rootView =
            LayoutInflater.from(decorWindowContext).inflate(R.layout.desktop_mode_app_pinned, null)
        return AppPinnedViewHolder(
                rootView = rootView,
                onTouchListener = onTouchListener,
                onGenericMotionEventListener = onGenericMotionEventListener,
                onOpenSettings = {
                    getSettingsIntent()?.let {
                        windowDecorationActions.onOpenIntent(taskInfo.taskId, it)
                    }
                },
                onCloseWindow = { windowDecorationActions.onClose(taskInfo) },
            )
            .also {
                viewHolder = it
                taskResourceLoader.getNameAndHeaderIcon(taskInfo) { name, _ ->
                    viewHolder.setAppName(name)
                }
            }
    }

    override val captionType = CaptionType.APP_PINNED

    override fun relayout(
        params: WindowDecoration2.RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult =
        traceSection(
            traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
            name = "AppPinnedController#relayout",
        ) {
            val relayoutParams =
                super.relayout(
                    params,
                    parentContainer,
                    display,
                    decorWindowContext,
                    startT,
                    finishT,
                    wct,
                )

            viewHolder.bindData(AppPinnedViewHolder.AppPinnedData(taskInfo, params.hasGlobalFocus))
            viewHolder.setTaskFocusState(params.hasGlobalFocus)
            return relayoutParams
        }

    override val occludingElements: List<OccludingElement> =
        arrayListOf(
            OccludingElement(
                width =
                    decorWindowContext.resources.getDimensionPixelSize(
                        R.dimen.desktop_mode_pinned_header_margin_start
                    ),
                alignment = OccludingElement.Alignment.START,
            ),
            OccludingElement(
                width =
                    decorWindowContext.resources.getDimensionPixelSize(
                        R.dimen.desktop_mode_pinned_header_margin_end
                    ),
                alignment = OccludingElement.Alignment.END,
            ),
        )

    override fun getCaptionHeight(): Int =
        decorWindowContext.resources.getDimensionPixelSize(
            R.dimen.desktop_mode_pinned_header_height
        ) + getCaptionTopPadding()

    override fun getCaptionWidth(): Int =
        taskInfo.getConfiguration().windowConfiguration.bounds.width()

    override fun calculateValidDragArea(): Rect? {
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val displayLayout = displayController.getDisplayLayout(taskInfo.displayId) ?: return Rect()
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }

        return Rect(
            0,
            stableBounds.top,
            stableBounds.right - taskBounds.width(),
            stableBounds.bottom - taskBounds.height(),
        )
    }

    private fun getSettingsIntent(): Intent? {
        val packageName = taskInfo.baseActivity?.packageName
        if (packageName == null) {
            Log.e(TAG, "Unable to find package name for the current window!")
            return null
        }
        return Intent(Settings.ACTION_PICTURE_IN_PICTURE_SETTINGS).apply {
            setData(android.net.Uri.fromParts("package", packageName, null))
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
