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

package com.android.systemui.util.state

import com.android.systemui.util.ListenerSet
import com.android.systemui.util.kotlin.DisposableHandles
import kotlinx.coroutines.DisposableHandle

/**
 * An object which allows accessing or observing a state.
 *
 * NOTE: This is designed for synchronous interoperability between the Notifications placeholder
 * composable inside the SceneContainer, and the NotificationStackScrollLayout, where we need to be
 * transmitting data synchronously to avoid 1-frame delays. Please do not use this for any other
 * purpose without contacting the Android System UI Notifications team, as they intend to delete it
 * when the NotificationStackScrollLayout is replaced with a composable.
 */
interface ObservableState<out T> {
    /** The current value of the state. */
    val value: T

    /**
     * Observe the value of the state, receiving updates with the provided [observer].
     *
     * @param observer the observer to be called when the value updates.
     * @param sendCurrentValue whether to send the current value to the observer immediately.
     * @return A [DisposableHandle] used to cancel the observation.
     */
    fun observe(sendCurrentValue: Boolean = true, observer: (value: T) -> Unit): DisposableHandle
}

private data class Subscription<T>(val observer: (value: T) -> Unit)

/**
 * An [ObservableState] that emits updates synchronously. This ensures that subscribers always
 * receive the updated state before the state setter returns.
 *
 * This is a useful alterative to [kotlinx.coroutines.flow.MutableStateFlow] in cases where the
 * design requires that the repository state is propagated to the UI layer without any possibility
 * of delay, as is the case when exposing flows.
 */
class SynchronouslyObservableState<T>(value: T) : ObservableState<T> {
    private val subscriptions = ListenerSet<Subscription<T>>()

    override var value: T = value
        set(value) {
            if (field != value) {
                field = value
                subscriptions.forEach { it.observer(value) }
            }
        }

    override fun observe(
        sendCurrentValue: Boolean,
        observer: (value: T) -> Unit,
    ): DisposableHandle {
        val subscription = Subscription(observer)
        subscriptions.addIfAbsent(subscription)
        if (sendCurrentValue) {
            // Send the current state to the observer immediately.
            observer(value)
        }
        return DisposableHandle { subscriptions.remove(subscription) }
    }

    override fun toString(): String = "SynchronouslyObservableState($value)"
}

/**
 * An [ObservableState] that allows observing a value derived from a map, with synchronous updates.
 *
 * This class wraps a [SynchronouslyObservableState] to manage a [MutableMap] and expose a
 * transformed value from that map. When the underlying map is modified via [set] or [remove], the
 * [selector] function is applied to the updated map, and the derived value is synchronously emitted
 * to all observers.
 *
 * @param K The type of the keys in the internal map.
 * @param V The type of the values in the internal map.
 * @param T The type handled by [value] and [observe] methods.
 * @param selector A function that transforms the internal map into the observable value of type
 *   [T]. This function is called whenever the map changes.
 */
class SynchronouslyObservableStateMap<K, V, T>(private val selector: (Map<K, V>) -> T) :
    ObservableState<T> {
    private val internalState = SynchronouslyObservableState(selector(emptyMap()))

    private val valuesMap = mutableMapOf<K, V>()

    override val value: T
        get() = internalState.value

    operator fun set(key: K, value: V) {
        valuesMap[key] = value
        internalState.value = selector(valuesMap)
    }

    /** Removes the specified key and its corresponding value from this map. */
    fun remove(key: K) {
        valuesMap.remove(key)
        internalState.value = selector(valuesMap)
    }

    override fun observe(
        sendCurrentValue: Boolean,
        observer: (value: T) -> Unit,
    ): DisposableHandle = internalState.observe(sendCurrentValue, observer)

    override fun toString(): String = "StateMap(value:$value storedValues:$valuesMap)"
}

abstract class DownstreamObservableState<R> : ObservableState<R> {
    private val subscriptions = ListenerSet<Subscription<R>>()

    data class SubscribedValue<R>(var value: R, val disposableHandle: DisposableHandle)

    private var subscribedValue: SubscribedValue<R>? = null

    /**
     * Poll the value of the state. This is called either when there are no subscribers to the
     * state, or when a subscriber is added, and the cached intermediate state needs to be updated.
     */
    protected abstract fun pollValue(): R

    /**
     * Subscribe to the upstream state. This is called when the first subscriber is added, and will
     * be disposed when the last subscriber is removed.
     *
     * @param onUpstreamChange the callback to be called when the upstream state changes.
     * @return A [DisposableHandle] used to cancel the subscription.
     */
    protected abstract fun subscribeUpstream(onUpstreamChange: (R) -> Unit): DisposableHandle

    final override val value: R
        get() {
            subscribedValue?.let {
                return it.value
            }
            return pollValue()
        }

    final override fun observe(
        sendCurrentValue: Boolean,
        observer: (value: R) -> Unit,
    ): DisposableHandle {
        if (subscribedValue == null) {
            subscribedValue =
                SubscribedValue(
                    pollValue(),
                    subscribeUpstream { newValue ->
                        val sv = subscribedValue ?: return@subscribeUpstream
                        val oldValue = sv.value
                        if (oldValue != newValue) {
                            sv.value = newValue
                            subscriptions.forEach { it.observer(value) }
                        }
                    },
                )
        }
        val subscription = Subscription(observer)
        subscriptions.addIfAbsent(subscription)
        if (sendCurrentValue) {
            // Send the current state to the observer immediately.
            observer(value)
        }
        return DisposableHandle {
            subscriptions.remove(subscription)
            if (subscriptions.isEmpty()) {
                subscribedValue?.disposableHandle?.dispose()
                subscribedValue = null
            }
        }
    }

    override fun toString(): String = "DownstreamObservableState($value)"
}

/** Creates an [ObservableState] that is a pure transformation of [this] based on [transform]. */
fun <T, R> ObservableState<T>.map(transform: (T) -> R): ObservableState<R> {
    val upstream = this
    return object : DownstreamObservableState<R>() {
        override fun pollValue(): R {
            return transform(upstream.value)
        }

        override fun subscribeUpstream(onUpstreamChange: (R) -> Unit): DisposableHandle {
            return upstream.observe(sendCurrentValue = false) { onUpstreamChange(transform(it)) }
        }
    }
}

/**
 * Creates an [ObservableState] that is a pure transformation from the [u1] and [u2] based on
 * [transform].
 */
fun <U1, U2, R> combine(
    u1: ObservableState<U1>,
    u2: ObservableState<U2>,
    transform: (U1, U2) -> R,
): ObservableState<R> {
    return object : DownstreamObservableState<R>() {
        override fun pollValue(): R {
            return transform(u1.value, u2.value)
        }

        override fun subscribeUpstream(onUpstreamChange: (R) -> Unit): DisposableHandle {
            return DisposableHandles().apply {
                add(
                    u1.observe(sendCurrentValue = false) {
                        onUpstreamChange(transform(it, u2.value))
                    }
                )
                add(
                    u2.observe(sendCurrentValue = false) {
                        onUpstreamChange(transform(u1.value, it))
                    }
                )
            }
        }
    }
}

/**
 * Creates an [ObservableState] that is a pure transformation from the [u1] and [u2] based on
 * [transform].
 */
fun <U1, U2, U3, R> combine(
    u1: ObservableState<U1>,
    u2: ObservableState<U2>,
    u3: ObservableState<U3>,
    transform: (U1, U2, U3) -> R,
): ObservableState<R> {
    return object : DownstreamObservableState<R>() {
        override fun pollValue(): R {
            return transform(u1.value, u2.value, u3.value)
        }

        override fun subscribeUpstream(onUpstreamChange: (R) -> Unit): DisposableHandle {
            return DisposableHandles().apply {
                add(
                    u1.observe(sendCurrentValue = false) {
                        onUpstreamChange(transform(it, u2.value, u3.value))
                    }
                )
                add(
                    u2.observe(sendCurrentValue = false) {
                        onUpstreamChange(transform(u1.value, it, u3.value))
                    }
                )
                add(
                    u3.observe(sendCurrentValue = false) {
                        onUpstreamChange(transform(u1.value, u2.value, it))
                    }
                )
            }
        }
    }
}

/** Create an immutable [ObservableState]. */
fun <T> observableStateOf(value: T) =
    object : ObservableState<T> {
        override val value: T = value

        override fun observe(
            sendCurrentValue: Boolean,
            observer: (value: T) -> Unit,
        ): DisposableHandle {
            if (sendCurrentValue) {
                observer(value)
            }
            return DisposableHandle {}
        }

        override fun toString(): String = "observableStateOf($value)"
    }
