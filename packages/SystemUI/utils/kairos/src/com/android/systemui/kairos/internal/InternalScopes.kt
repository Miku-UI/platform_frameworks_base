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

import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.StateScope
import com.android.systemui.kairos.TransactionScope
import com.android.systemui.kairos.util.NameData
import kotlin.coroutines.ContinuationInterceptor

internal interface InitScope {
    val networkId: Any
}

internal interface EvalScope : NetworkScope, DeferScope, TransactionScope

internal interface InternalStateScope : EvalScope, StateScope {
    val deathSignal: Events<*>

    fun <A> truncateToScope(events: Events<A>, nameData: NameData): Events<A>
}

internal interface InternalBuildScope : InternalStateScope, BuildScope

internal interface NetworkScope : InitScope {

    val epoch: Long
    val network: Network

    val compactor: Scheduler
    val scheduler: Scheduler

    val transactionStore: TransactionStore

    fun scheduleOutput(output: Output<*>)

    fun scheduleDispatchedOutput(interceptor: ContinuationInterceptor?, block: () -> Unit)

    fun scheduleMuxMover(muxMover: MuxDeferredNode<*, *, *>)

    fun scheduleDeactivation(node: PushNode<*>)

    fun scheduleDeactivation(output: Output<*>)
}
