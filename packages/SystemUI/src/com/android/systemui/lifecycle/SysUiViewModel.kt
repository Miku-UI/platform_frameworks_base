/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.lifecycle

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.android.app.tracing.TraceUtils
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.app.tracing.coroutines.traceCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope

/**
 * Returns a remembered view-model of the type [T]. If the returned instance is also an
 * [Activatable], it's automatically kept active until this composable leaves the composition; if
 * the [key] changes, the old view-model is deactivated and a new one will be instantiated,
 * activated, and returned.
 *
 * The [traceName] is used for coroutine performance tracing purposes. Please try to use a label
 * that's unique enough and easy enough to find in code search; this should help correlate
 * performance findings with actual code. One recommendation: prefer whole string literals instead
 * of some complex concatenation or templating scheme.
 *
 * Note that, by default, `rememberViewModel` will activate its view-model in the [CoroutineContext]
 * from which it was called. To configure this, either pass a [coroutineContext] to this method or
 * use [WithConfiguredRememberViewModels] to bulk-configure all usages of `rememberViewModel`s
 * within the composable hierarchy. If you do both, the provided [coroutineContext] takes precedence
 * over the [WithConfiguredRememberViewModels] one.
 */
@Composable
fun <T> rememberViewModel(
    traceName: String,
    key: Any = Unit,
    coroutineContext: CoroutineContext = LocalCoroutineContext.current,
    factory: () -> T,
): T {
    val instance = remember(key) { factory() }
    if (instance is Activatable) {
        // TODO(b/436984081): Pass the coroutineContext once we use LaunchedEffectWithLifecycle
        // again.
        LaunchedEffect(instance) {
            TraceUtils.traceAsync("SystemUI.rememberViewModel", traceName) {
                traceCoroutine(traceName) { instance.activate() }
            }
        }
    }
    return instance
}

/**
 * Configures all usages of [rememberViewModel] in this composition to use the provided
 * [coroutineContext] to run their activations. Individual calls to [rememberViewModel] can still
 * override this behavior by passing a different [CoroutineContext].
 */
@Composable
fun WithConfiguredRememberViewModels(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCoroutineContext provides coroutineContext, block)
}

/**
 * Invokes [block] in a new coroutine with a new view-model that is automatically activated whenever
 * `this` [View]'s Window's [WindowLifecycleState] is at least at [minWindowLifecycleState], and is
 * automatically canceled once that is no longer the case.
 *
 * The [traceName] is used for coroutine performance tracing purposes. Please try to use a label
 * that's unique enough and easy enough to find in code search; this should help correlate
 * performance findings with actual code. One recommendation: prefer whole string literals instead
 * of some complex concatenation or templating scheme.
 */
suspend fun <T> View.viewModel(
    traceName: String,
    minWindowLifecycleState: WindowLifecycleState,
    factory: () -> T,
    block: suspend CoroutineScope.(T) -> Unit,
): Nothing =
    repeatOnWindowLifecycle(minWindowLifecycleState) {
        val instance = factory()
        if (instance is Activatable) {
            launch { traceCoroutine(traceName) { instance.activate() } }
        }
        block(instance)
    }

private val LocalCoroutineContext =
    staticCompositionLocalOf<CoroutineContext> { EmptyCoroutineContext }
