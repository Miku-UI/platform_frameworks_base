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

package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager
import android.view.SurfaceControl
import android.view.View
import android.window.WindowContainerTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder
import kotlinx.coroutines.CoroutineScope
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Implementation of [CaptionController] for testing. */
class TestCaptionController(
    override val captionType: CaptionType,
    taskInfo: ActivityManager.RunningTaskInfo,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost> = mock(),
    taskOrganizer: ShellTaskOrganizer = mock(),
    testScope: CoroutineScope = mock(),
) :
    CaptionController<WindowDecorLinearLayout>(
        taskInfo,
        windowDecorViewHostSupplier,
        taskOrganizer,
        testScope,
    ) {

    override val occludingElements = listOf<OccludingElement>()
    var closed = false

    override fun createCaptionView(): TestWindowDecorationViewHolder {
        return TestWindowDecorationViewHolder()
    }

    override fun getCaptionHeight(): Int {
        return TEST_CAPTION_HEIGHT
    }

    override fun getCaptionWidth(): Int {
        return TEST_CAPTION_WIDTH
    }

    override fun close(wct: WindowContainerTransaction, t: SurfaceControl.Transaction): Boolean {
        closed = true
        return super.close(wct, t)
    }

    /** Implementation of [WindowDecorationViewHolder] for testing. */
    inner class TestWindowDecorationViewHolder : WindowDecorationViewHolder<TestData>() {
        override val rootView = mock<View>().apply { whenever(this.parent).thenReturn(mock()) }

        override fun onHandleMenuOpened() {}

        override fun onHandleMenuClosed() {}

        override fun close() {}

        override fun setTaskFocusState(taskFocusState: Boolean) {}

        override fun bindData(data: TestData) {}
    }

    /** Implementation of [WindowDecorationViewHolder.Data] for testing. */
    inner class TestData : WindowDecorationViewHolder.Data()

    private companion object {
        /** Width of the [TestCaptionController]. */
        const val TEST_CAPTION_WIDTH = 32
        /** Height of the [TestCaptionController]. */
        const val TEST_CAPTION_HEIGHT = 32
    }
}
