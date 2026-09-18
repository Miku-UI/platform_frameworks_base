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

package com.android.wm.shell.compatui.api.events

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * A service that manages event dispatch and execution. Supports both fire-and-forget (1:1 Queue)
 * and request-response (Callback) patterns.
 */
@WMSingleton
class CompatUIEventService
@Inject
constructor(
    @param:ShellMainThread private val mainExecutor: ShellExecutor,
    @param:ShellBackgroundThread private val backgroundExecutor: ShellExecutor,
) {

    // Value can be CompatUIEventConsumer<*> OR CompatUICallbackEventConsumer<*, *>
    private val consumerMap: MutableMap<Class<*>, Any> = ConcurrentHashMap()

    /** Subscribes a Void consumer (fire-and-forget) to a specific event type. */
    fun <E : CompatUIBaseEvent> subscribe(eventType: Class<E>, consumer: CompatUIEventConsumer<E>) {
        consumerMap[eventType] = consumer
        ProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
            "$TAG: Subscribed void consumer for event: %s",
            eventType.simpleName,
        )
    }

    /** Subscribes a Callback consumer (request-response) to a specific event type. */
    fun <E : CompatUIBaseEvent, R> subscribe(
        eventType: Class<E>,
        consumer: CompatUICallbackEventConsumer<E, R>,
    ) {
        consumerMap[eventType] = consumer
        ProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
            "$TAG: Subscribed callback consumer for event: %s",
            eventType.simpleName,
        )
    }

    fun unsubscribe(eventType: Class<out CompatUIBaseEvent>) {
        val removed = consumerMap.remove(eventType)
        if (removed != null) {
            ProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
                "$TAG: Unsubscribed consumer for event: %s",
                eventType.simpleName,
            )
        }
    }

    /**
     * Posts an event (Fire-and-Forget). If the registered consumer is a Callback consumer, the
     * return value is ignored.
     */
    fun <E : CompatUIBaseEvent> postEvent(event: E) {
        val consumer = consumerMap[event.javaClass] as? ExecutionContextOwner
        if (consumer == null) {
            ProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
                "$TAG: No consumer for event: %s",
                event.javaClass.simpleName,
            )
            return
        }

        val runnable = Runnable {
            try {
                if (consumer is CompatUIEventConsumer<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (consumer as CompatUIEventConsumer<E>).process(event)
                } else if (consumer is CompatUICallbackEventConsumer<*, *>) {
                    // If a callback consumer is used here, we just run it and ignore the result
                    @Suppress("UNCHECKED_CAST")
                    (consumer as CompatUICallbackEventConsumer<E, *>).process(event)
                }
            } catch (e: Exception) {
                ProtoLog.v(
                    ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
                    "$TAG: Error processing event: %s %s",
                    event.javaClass.simpleName,
                    e,
                )
            }
        }

        getExecutor(consumer.compatUIExecutionContext).execute(runnable)
    }

    /**
     * Posts an event with a Callback. The registered consumer MUST be a
     * [CompatUICallbackEventConsumer]. The callback will be executed on the MAIN thread.
     *
     * @param callback A lambda that receives the result R on the Main Thread.
     */
    fun <E : CompatUIBaseEvent, R> postEvent(event: E, callback: (R) -> Unit) {
        val consumer = consumerMap[event.javaClass]
        if (consumer == null) {
            ProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
                "$TAG: No consumer for event: %s",
                event.javaClass.simpleName,
            )
            return
        }

        if (consumer !is CompatUICallbackEventConsumer<*, *>) {
            ProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
                "$TAG: Registered consumer for %s does not support callbacks.",
                event.javaClass.simpleName,
            )
            return
        }

        val runnable = Runnable {
            try {
                // 1. Execute the consumer logic on the requested context (BG/Main)
                @Suppress("UNCHECKED_CAST")
                val result = (consumer as CompatUICallbackEventConsumer<E, R>).process(event)

                // 2. Post the result back to the Main Thread
                mainExecutor.execute { callback(result) }
            } catch (e: Exception) {
                ProtoLog.v(
                    ShellProtoLogGroup.WM_SHELL_COMPAT_UI,
                    "$TAG: Error processing callback event: %s %s",
                    event.javaClass.simpleName,
                    e,
                )
            }
        }

        getExecutor(consumer.compatUIExecutionContext).execute(runnable)
    }

    private fun getExecutor(context: CompatUIExecutionContext): ShellExecutor {
        return when (context) {
            CompatUIExecutionContext.MAIN_THREAD -> mainExecutor
            CompatUIExecutionContext.BACKGROUND -> backgroundExecutor
        }
    }

    companion object {
        private const val TAG = "CompatUIEventService"
    }
}
