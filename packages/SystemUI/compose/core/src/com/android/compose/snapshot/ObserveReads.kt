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

package com.android.compose.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Run [block] in the Compose applying thread and observe snapshot state reads so that [block] is
 * re-run when any of those states have changed.
 *
 * This can be used to observe Snapshot state code and bind it with legacy (non-Compose) code
 * without using `snapshotFlow {}`.
 *
 * Example:
 * ```
 * // Before (without ObserveReads):
 * LaunchedEffect(viewModel, foo) {
 *     snapshotFlow { foo.value }.collect { value -> viewModel.onFooChanged(value) }
 * }
 *
 * // After (with ObserveReads):
 * ObserveReads { viewModel.onFooChanged(foo.value) }
 * ```
 *
 * @see ObserveReadsRoot
 */
@Composable
fun ObserveReads(block: () -> Unit) {
    val wrapper =
        LocalSnapshotStateObserverWrapper.current
            ?: error("ObserveReads must be used inside an ObserveReadsRoot")
    remember(wrapper, block) { ObserveScope(wrapper, block) }
}

/** Set up a root for [ObserveReads] so that it can be used inside [content]. */
@Composable
@OptIn(InternalComposeApi::class)
fun ObserveReadsRoot(content: @Composable () -> Unit) {
    if (LocalSnapshotStateObserverWrapper.current != null) {
        // There's already an observer in the hierarchy, so don't add a new one, just compose
        // the content
        content()
        return
    }

    val interceptor =
        currentComposer.applyCoroutineContext[ContinuationInterceptor]
            ?: error("Failed to get the composition ContinuationInterceptor")
    val wrapper = remember {
        val executor: (() -> Unit) -> Unit =
            if (interceptor is CoroutineDispatcher) {
                { callback -> interceptor.dispatch(EmptyCoroutineContext, callback) }
            } else {
                // This code should only be reached in tests.
                { callback ->
                    interceptor
                        .interceptContinuation(
                            object : Continuation<Unit> {
                                override val context: CoroutineContext
                                    get() = EmptyCoroutineContext

                                override fun resumeWith(result: Result<Unit>) {
                                    callback()
                                }
                            }
                        )
                        .resumeWith(Result.success(Unit))
                }
            }

        SnapshotStateObserverWrapper(executor)
    }

    CompositionLocalProvider(LocalSnapshotStateObserverWrapper provides wrapper, content = content)
}

private class ObserveScope(
    private val wrapper: SnapshotStateObserverWrapper,
    private val block: () -> Unit,
) : RememberObserver {
    override fun onRemembered() {
        wrapper.executor(::observeReads)
    }

    override fun onAbandoned() {}

    override fun onForgotten() {
        wrapper.observer.clear(this)
    }

    fun observeReads() {
        wrapper.observer.observeReads(
            scope = this,
            onValueChangedForScope = OnValueChangedForScope,
            block = block,
        )
    }
}

private val OnValueChangedForScope = { scope: ObserveScope -> scope.observeReads() }

private val LocalSnapshotStateObserverWrapper =
    staticCompositionLocalOf<SnapshotStateObserverWrapper?> { null }

private class SnapshotStateObserverWrapper(val executor: (() -> Unit) -> Unit) : RememberObserver {
    val observer = SnapshotStateObserver(executor)

    override fun onRemembered() {
        observer.start()
    }

    override fun onForgotten() {
        observer.stop()
    }

    override fun onAbandoned() {}
}
