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

package com.android.systemui.kairos

import com.android.systemui.kairos.internal.IncrementalImpl
import com.android.systemui.kairos.internal.constInit
import com.android.systemui.kairos.internal.init
import com.android.systemui.kairos.internal.mapImpl
import com.android.systemui.kairos.internal.switchDeferredImplSingle
import com.android.systemui.kairos.internal.switchPromptImplSingle
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.mapPatchFromFullDiff
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData

/**
 * Returns an [Events] that switches to the [Events] contained within this [State] whenever it
 * changes.
 *
 * This switch does take effect until the *next* transaction after [State] changes. For a switch
 * that takes effect immediately, see [switchEventsPromptly].
 *
 * @sample com.android.systemui.kairos.KairosSamples.switchEvents
 */
@ExperimentalKairosApi
fun <A> State<Events<A>>.switchEvents(): Events<A> =
    switchEvents(nameTag("State.switchEvents").toNameData("State.switchEvents"))

internal fun <A> State<Events<A>>.switchEvents(nameData: NameData): Events<A> {
    val patches =
        mapImpl({ init.connect(this).changes }, nameData + "patches") { newEvents, _ ->
            newEvents.init.connect(this)
        }
    return EventsInit(
        constInit(
            nameData,
            switchDeferredImplSingle(
                nameData,
                getStorage = {
                    init.connect(this).getCurrentWithEpoch(this).first.init.connect(this)
                },
                getPatches = { patches },
            ),
        )
    )
}

/**
 * Returns an [Events] that switches to the [Events] contained within this [State] whenever it
 * changes.
 *
 * This switch takes effect immediately within the same transaction that [State] changes. If the
 * newly-switched-in [Events] is emitting a value within this transaction, then that value will be
 * emitted from this switch. If not, but the previously-switched-in [Events] *is* emitting, then
 * that value will be emitted from this switch instead. Otherwise, there will be no emission.
 *
 * In general, you should prefer [switchEvents] over this method. It is both safer and more
 * performant.
 *
 * @sample com.android.systemui.kairos.KairosSamples.switchEventsPromptly
 */
// TODO: parameter to handle coincidental emission from both old and new
@ExperimentalKairosApi
fun <A> State<Events<A>>.switchEventsPromptly(): Events<A> =
    switchEventsPromptly(
        nameTag("State.switchEventsPromptly").toNameData("State.switchEventsPromptly")
    )

internal fun <A> State<Events<A>>.switchEventsPromptly(nameData: NameData): Events<A> {
    val patches =
        mapImpl({ init.connect(this).changes }, nameData + "patches") { newEvents, _ ->
            newEvents.init.connect(this)
        }
    return EventsInit(
        constInit(
            nameData,
            switchPromptImplSingle(
                nameData,
                getStorage = {
                    init.connect(this).getCurrentWithEpoch(this).first.init.connect(this)
                },
                getPatches = { patches },
            ),
        )
    )
}

/** Returns an [Incremental] that behaves like current value of this [State]. */
fun <K, V> State<Incremental<K, V>>.switchIncremental(): Incremental<K, V> =
    switchIncremental(nameTag("State.switchIncremental").toNameData("State.switchIncremental"))

internal fun <K, V> State<Incremental<K, V>>.switchIncremental(
    nameData: NameData
): Incremental<K, V> {
    val stateChangePatches =
        transitions(nameData + "transitions").mapNotNull(nameData + "makePatchFromDiff") {
            (old, new) ->
            mapPatchFromFullDiff(old.sample(), new.sample()).takeIf { it.isNotEmpty() }
        }
    val innerChanges =
        map(nameData + "innerChangesPatch") { inner ->
                merge(
                    nameData + "mergeCoincidentalPatches",
                    stateChangePatches,
                    inner.updates(nameData + "innerUpdates"),
                ) { switchPatch, upcomingPatch ->
                    switchPatch + upcomingPatch
                }
            }
            .switchEventsPromptly(nameData + "innerChanges")
    val flattened = flatten(nameData)
    return IncrementalInit(
        init(nameData) {
            val upstream = flattened.init.connect(this)
            IncrementalImpl(
                nameData,
                upstream.changes,
                innerChanges.init.connect(this),
                upstream.store,
            )
        }
    )
}
