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

package com.android.wm.shell.util

import android.app.ActivityManager.RunningTaskInfo
import com.android.testing.wm.util.RunningTaskInfoTestInputBuilder
import com.android.testing.wm.util.TestInputBuilder
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.compatui.api.CompatUIEvent
import com.android.wm.shell.compatui.api.CompatUIHandler
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUIRequest
import java.util.function.Consumer

@DslMarker annotation class CompatUIHandlerTestTagMarker

// Base class for the CompatUIHandler interfaces Test Context.
@CompatUIHandlerTestTagMarker
open class CompatUIHandlerTestContext(private val testSubjectFactory: () -> CompatUIHandler) {

    protected lateinit var compatUIInfo: CompatUIInfo
    protected lateinit var compatUIRequest: CompatUIRequest
    protected var compatUIEventConsumer: Consumer<CompatUIEvent>? = null

    fun compatUIInfo(builder: CompatUIInfoInputBuilder.() -> Unit): CompatUIInfo {
        val compatUIInfoObj = CompatUIInfoInputBuilder()
        compatUIInfoObj.builder()
        return compatUIInfoObj.build().apply { compatUIInfo = this }
    }

    fun compatUIRequest(
        builder: CompatUIRequestTestInputBuilder.() -> CompatUIRequest
    ): CompatUIRequest {
        val binderObj = CompatUIRequestTestInputBuilder()
        return binderObj.builder().apply { compatUIRequest = this }
    }

    fun compatUIEventConsumer(
        builder: CompatUIEventConsumerTestInputBuilder.() -> Consumer<CompatUIEvent>?
    ): Consumer<CompatUIEvent>? {
        val binderObj = CompatUIEventConsumerTestInputBuilder()
        return binderObj.builder().apply { compatUIEventConsumer = this }
    }

    fun validateOnCompatInfoChanged(verifier: () -> Unit) {
        // We execute the test subject using the input
        testSubjectFactory().onCompatInfoChanged(compatUIInfo)
        verifier()
    }

    fun validateSendCompatUIRequest(verifier: () -> Unit) {
        // We execute the test subject using the input
        testSubjectFactory().sendCompatUIRequest(compatUIRequest)
        verifier()
    }

    fun validateSetCallback(verifier: () -> Unit) {
        // We execute the test subject using the input
        testSubjectFactory().setCallback(compatUIEventConsumer)
        verifier()
    }
}

/** Function to run tests for the different [CompatUIHandler] implementations. */
fun testCompatUIHandler(
    testSubjectFactory: () -> CompatUIHandler,
    init: CompatUIHandlerTestContext.() -> Unit,
): CompatUIHandlerTestContext {
    val testContext = CompatUIHandlerTestContext(testSubjectFactory)
    testContext.init()
    return testContext
}

/** [InputBuilder] that helps in the creation of a [CompatUIInfo] object for testing. */
class CompatUIInfoInputBuilder : TestInputBuilder<CompatUIInfo> {

    private val inputParams = InputParams()

    data class InputParams(
        var taskInfo: RunningTaskInfo = RunningTaskInfo(),
        val taskListener: ShellTaskOrganizer.TaskListener? = null,
    )

    fun runningTaskInfo(
        builder: RunningTaskInfoTestInputBuilder.(RunningTaskInfo) -> Unit
    ): RunningTaskInfo {
        val runningTaskInfoObj = RunningTaskInfoTestInputBuilder()
        return RunningTaskInfo()
            .also { runningTaskInfoObj.builder(it) }
            .apply { inputParams.taskInfo = this }
    }

    override fun build(): CompatUIInfo =
        CompatUIInfo(inputParams.taskInfo, inputParams.taskListener)
}

// [TestInputBuilder] for a [CompatUIRequest]
class CompatUIRequestTestInputBuilder

// [TestInputBuilder] for a [Consumer<CompatUIEvent>?]
class CompatUIEventConsumerTestInputBuilder
