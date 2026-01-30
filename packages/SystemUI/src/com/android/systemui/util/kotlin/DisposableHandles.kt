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

package com.android.systemui.util.kotlin

import kotlinx.coroutines.DisposableHandle

/** A mutable collection of [DisposableHandle] objects that is itself a [DisposableHandle] */
class DisposableHandles : DisposableHandle {
    private val handles = mutableListOf<DisposableHandle>()

    /** Add the provided handles to this collection. */
    fun add(vararg handles: DisposableHandle) {
        this.handles.addAll(handles)
    }

    /** Same as [add] */
    operator fun plusAssign(handle: DisposableHandle) {
        this.handles.add(handle)
    }

    /** Same as [add] */
    operator fun plusAssign(handles: Iterable<DisposableHandle>) {
        this.handles.addAll(handles)
    }

    /** [dispose] the current contents, then [add] the provided [handles] */
    fun replaceAll(vararg handles: DisposableHandle) {
        dispose()
        add(*handles)
    }

    /** Dispose of all added handles and empty this collection. */
    override fun dispose() {
        handles.forEach { it.dispose() }
        handles.clear()
    }
}

/**
 * A builder that allows setting up and collecting multiple disposable operations into a single
 * [DisposableHandle].
 */
class DisposableHandleBuilder {
    private val disposableHandles = DisposableHandles()

    /**
     * Invokes [setValue] with [value] immediately, then registers a [DisposableHandle] that will
     * invoke [setValue] again with `null` when disposed.
     */
    fun <T : Any> bind(value: T, setValue: (T?) -> Unit) {
        setValue(value)
        disposableHandles.add(DisposableHandle { setValue(null) })
    }

    /** Simply register a sub-[DisposableHandle] to be disposed with the built result. */
    fun register(handle: DisposableHandle) {
        disposableHandles.add(handle)
    }

    /** Get the underlying [DisposableHandle]. */
    fun build(): DisposableHandle = disposableHandles
}

/** Build a [DisposableHandle] that disposes of multiple other operations */
fun buildDisposableHandle(block: DisposableHandleBuilder.() -> Unit): DisposableHandle =
    DisposableHandleBuilder().let {
        it.block()
        it.build()
    }
