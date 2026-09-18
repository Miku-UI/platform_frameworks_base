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

/**
 * A functional interface for an event consumer that returns a result. Implementers define logic to
 * run when an event is processed and return a value.
 *
 * @param E The specific [CompatUIBaseEvent] type this consumer can process.
 * @param R The type of the result returned by this consumer.
 */
interface CompatUICallbackEventConsumer<E : CompatUIBaseEvent, R> : ExecutionContextOwner {
    /**
     * Processes the given event and returns a result. The thread this runs on is determined by the
     * [compatUIExecutionContext] provided at post time.
     *
     * @param event The event object to process.
     * @return The result of the processing.
     */
    fun process(event: E): R
}
