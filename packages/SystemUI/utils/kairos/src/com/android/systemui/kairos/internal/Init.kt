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

package com.android.systemui.kairos.internal

import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.maybeOf

/** Performs actions once, when the reactive component is first connected to the network. */
internal class Init<out A>(val nameData: NameData, initBlock: InitScope.() -> A) {

    init {
        nameData.forceInit()
    }

    private var block: (InitScope.() -> A)? = initBlock

    /**
     * Stores the result after initialization, as well as the id of the [Network] it's been
     * initialized with.
     */
    private val cache = CompletableLazy<Initialized<A>>()

    fun connect(evalScope: InitScope): A {
        val block = block
        if (block == null) {
            // Read from cache
            val (networkId, result) = cache.value
            check(networkId == evalScope.networkId) { "Network mismatch" }
            return result
        } else {
            // Write to cache
            return block(evalScope).also {
                cache.setValue(Initialized(evalScope.networkId, it))
                this.block = null
            }
        }
    }

    fun getUnsafe(): Maybe<A> =
        if (cache.isInitialized()) {
            maybeOf(cache.value.value)
        } else {
            maybeOf()
        }

    override fun toString(): String = "${super.toString()}[$nameData]"

    private data class Initialized<A>(val networkId: Any, val value: A)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <A> init(nameData: NameData, noinline block: InitScope.() -> A): Init<A> =
    Init(nameData, block)

@Suppress("NOTHING_TO_INLINE")
internal inline fun <A> constInit(nameData: NameData, value: A): Init<A> = init(nameData) { value }
