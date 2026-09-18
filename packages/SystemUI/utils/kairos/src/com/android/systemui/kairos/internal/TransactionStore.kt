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

package com.android.systemui.kairos.internal

internal class TransactionStore private constructor(private val storage: ArrayList<Any?>) {

    constructor(capacity: Int) : this(ArrayList(capacity))

    constructor() : this(ArrayList())

    @Suppress("UNCHECKED_CAST")
    operator fun <A> get(key: Key<A>): A =
        storage.getOrElse(key.index) { error("no value for $key in this transaction") } as A

    fun <A> put(value: A): Key<A> {
        val index = storage.size
        storage.add(value)
        return Key(index)
    }

    fun clear() = storage.clear()

    @JvmInline value class Key<A>(val index: Int)
}

internal class TransactionCache<A> {
    private var key: TransactionStore.Key<A>? = null

    var epoch: Long = Long.MIN_VALUE
        private set

    fun getOrPut(evalScope: EvalScope, block: () -> A): A =
        if (epoch < evalScope.epoch) {
            epoch = evalScope.epoch
            block().also { key = evalScope.transactionStore.put(it) }
        } else {
            evalScope.transactionStore[key!!]
        }

    fun put(evalScope: EvalScope, value: A) {
        epoch = evalScope.epoch
        key = evalScope.transactionStore.put(value)
    }

    fun getCurrentValue(evalScope: EvalScope): A {
        check(epoch == evalScope.epoch) { "no value for $key in this transaction" }
        return evalScope.transactionStore[key!!]
    }
}
