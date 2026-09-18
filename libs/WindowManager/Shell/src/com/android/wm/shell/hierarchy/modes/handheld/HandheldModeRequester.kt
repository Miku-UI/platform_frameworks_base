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
package com.android.wm.shell.hierarchy.modes.handheld

import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.experimental.AlwaysOnTopMode
import com.android.wm.shell.hierarchy.experimental.MultiContainerMode
import com.android.wm.shell.hierarchy.experimental.testsplit.PipMode
import com.android.wm.shell.hierarchy.experimental.testsplit.SplitMode
import com.android.wm.shell.hierarchy.modes.Mode.EnterRequestContext
import dagger.Lazy

/**
 * Handles requesting containers to enter handheld-specific modes.
 */
class HandheldModeRequester(
    private val alwaysOnTopModeLazy: Lazy<AlwaysOnTopMode>,
    private val multiContainerModeLazy: Lazy<MultiContainerMode>,
    private val splitModeLazy: Lazy<SplitMode>,
    private val pipModeLazy: Lazy<PipMode>,
) {

    /** @see HandheldModeRequester.requestEnterPipMode */
    fun requestEnterPipMode(
        task: Container,
        request: EnterRequestContext
    ): WindowContainerTransaction? {
        val wct = WindowContainerTransaction()
        return if (pipModeLazy.get().requestEnterMode(task, request, wct)) wct else null
    }

    /** @see HandheldModeRequester.requestEnterAlwaysOnTopMode */
    fun requestEnterAlwaysOnTopMode(
        task: Container,
        request: EnterRequestContext
    ): WindowContainerTransaction? {
        val wct = WindowContainerTransaction()
        return if (alwaysOnTopModeLazy.get().requestEnterMode(task, request, wct)) wct else null
    }

    /** @see HandheldModeRequester.requestEnterMultiContainerMode */
    fun requestEnterMultiContainerMode(
        task: Container,
        request: EnterRequestContext
    ): WindowContainerTransaction? {
        val wct = WindowContainerTransaction()
        return if (multiContainerModeLazy.get().requestEnterMode(task, request, wct)) wct else null
    }

    /** @see HandheldModeRequester.requestEnterSplitMode */
    fun requestEnterSplitMode(
        task: Container,
        request: EnterRequestContext
    ): WindowContainerTransaction? {
        val wct = WindowContainerTransaction()
        return if (splitModeLazy.get().requestEnterMode(task, request, wct)) wct else null
    }
}
