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

package com.android.systemui.util.lifecycle.kairos

import android.view.View
import android.view.ViewTreeObserver
import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.map
import com.android.systemui.lifecycle.WindowLifecycleState
import com.android.systemui.util.Assert
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Runs the given [block] in a new [BuildScope] when `this` [View]'s [Window][android.view.Window]'s
 * [WindowLifecycleState] is at least at [state] (or immediately after calling this function if the
 * window is already at least at [state]), automatically canceling the work when the window is no
 * longer at least at that state.
 *
 * [block] may be run multiple times, running once per every time the [WindowLifecycleState] becomes
 * at least at [state].
 */
@ExperimentalKairosApi
fun BuildScope.repeatOnWindowLifecycle(
    view: View,
    state: WindowLifecycleState,
    name: NameTag? = null,
    block: BuildScope.() -> Unit,
) {
    when (state) {
        WindowLifecycleState.ATTACHED -> repeatWhenAttachedToWindow(view, name, block)
        WindowLifecycleState.VISIBLE -> repeatWhenWindowIsVisible(view, name, block)
        WindowLifecycleState.FOCUSED -> repeatWhenWindowHasFocus(view, name, block)
    }
}

/**
 * Runs the given [block] every time the [View] becomes attached (or immediately after calling this
 * function, if the view was already attached), automatically canceling the work when the view
 * becomes detached.
 *
 * [block] may be run multiple times, running once per every time the view is attached.
 */
@ExperimentalKairosApi
fun BuildScope.repeatWhenAttachedToWindow(
    view: View,
    name: NameTag? = null,
    block: BuildScope.() -> Unit,
) {
    view.isAttached
        .flowOn(MAIN_DISPATCHER_SINGLETON)
        .toState(false, name?.map { "$it-attachedState" })
        .observeLatestBuild(name) {
            if (it) {
                block()
            }
        }
}

/**
 * Runs the given [block] every time the [Window][android.view.Window] this [View] is attached to
 * becomes visible (or immediately after calling this function, if the window is already visible),
 * automatically canceling the work when the window becomes invisible.
 *
 * [block] may be run multiple times, running once per every time the window becomes visible.
 */
@ExperimentalKairosApi
fun BuildScope.repeatWhenWindowIsVisible(
    view: View,
    name: NameTag? = null,
    block: BuildScope.() -> Unit,
) {
    view.isWindowVisible
        .flowOn(MAIN_DISPATCHER_SINGLETON)
        .toState(false, name?.map { "$it-visibleState" })
        .observeLatestBuild(name) {
            if (it) {
                block()
            }
        }
}

/**
 * Runs the given [block] every time the [Window][android.view.Window] this [View] is attached to
 * has focus (or immediately after calling this function, if the window is already focused),
 * automatically canceling the work when the window loses focus.
 *
 * [block] may be run multiple times, running once per every time the window is focused.
 */
@ExperimentalKairosApi
fun BuildScope.repeatWhenWindowHasFocus(
    view: View,
    name: NameTag? = null,
    block: BuildScope.() -> Unit,
) {
    view.isWindowFocused
        .flowOn(MAIN_DISPATCHER_SINGLETON)
        .toState(false, name?.map { "$it-focusState" })
        .observeLatestBuild(name) {
            if (it) {
                block()
            }
        }
}

private val View.isAttached
    get() = conflatedCallbackFlow {
        val onAttachListener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    Assert.isMainThread()
                    trySend(true)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    trySend(false)
                }
            }
        addOnAttachStateChangeListener(onAttachListener)
        trySend(isAttachedToWindow)
        awaitClose { removeOnAttachStateChangeListener(onAttachListener) }
    }

private val View.currentViewTreeObserver: Flow<ViewTreeObserver?>
    get() = isAttached.map { if (it) viewTreeObserver else null }

private val View.isWindowVisible
    get() =
        currentViewTreeObserver
            .flatMapLatestConflated { vto ->
                vto?.isWindowVisible?.onStart { emit(windowVisibility == View.VISIBLE) }
                    ?: emptyFlow()
            }
            .flowOn(MAIN_DISPATCHER_SINGLETON)

private val View.isWindowFocused
    get() =
        currentViewTreeObserver
            .flatMapLatestConflated { vto ->
                vto?.isWindowFocused?.onStart { emit(hasWindowFocus()) } ?: emptyFlow()
            }
            .flowOn(MAIN_DISPATCHER_SINGLETON)

private val ViewTreeObserver.isWindowFocused
    get() = conflatedCallbackFlow {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { trySend(it) }
        addOnWindowFocusChangeListener(listener)
        awaitClose { removeOnWindowFocusChangeListener(listener) }
    }

private val ViewTreeObserver.isWindowVisible
    get() = conflatedCallbackFlow {
        val listener =
            ViewTreeObserver.OnWindowVisibilityChangeListener { v -> trySend(v == View.VISIBLE) }
        addOnWindowVisibilityChangeListener(listener)
        awaitClose { removeOnWindowVisibilityChangeListener(listener) }
    }

/**
 * Cache dispatcher in a top-level property so that we do not unnecessarily create new
 * `CoroutineContext` objects for tracing on each call to `repeatWhen-`. It is okay to reuse a
 * single instance of the tracing context because it is copied for its children.
 *
 * Also, ideally, we would use the injected `@Main CoroutineDispatcher`, but `repeatWhen-` functions
 * are extension functions, and plumbing dagger-injected instances for static usage has little
 * benefit.
 */
private val MAIN_DISPATCHER_SINGLETON =
    Dispatchers.Main.immediate + newTracingContext("RepeatWhenAttachedKairos")
