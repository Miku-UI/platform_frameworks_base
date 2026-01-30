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
package com.android.systemui.dreams

import android.service.dreams.DreamService
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.annotations.WeaklyReferencedCallback
import com.android.systemui.complication.Complication
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.dagger.DreamLog
import com.android.systemui.statusbar.policy.CallbackController
import com.android.systemui.util.reference.WeakReferenceFactory
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.Objects
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * [DreamOverlayStateController] is the source of truth for Dream overlay configurations and state.
 * Clients can register as listeners for changes to the overlay composition and can query for the
 * complications on-demand.
 */
@SysUISingleton
class DreamOverlayStateController
@VisibleForTesting
@Inject
constructor(
    @Main private val executor: Executor,
    // TODO(b/408036961): Remove deprecated usage.
    featureFlags: FeatureFlags,
    @DreamLog logBuffer: LogBuffer,
    private val weakReferenceFactory: WeakReferenceFactory,
) : CallbackController<DreamOverlayStateController.Callback?> {
    private var state = 0

    /**
     * Callback for dream overlay events. NOTE: Caller should maintain a strong reference to this
     * themselves so the callback does not get garbage collected.
     */
    @WeaklyReferencedCallback
    interface Callback {
        /** Called when the composition of complications changes. */
        fun onComplicationsChanged() {}

        /** Called when the dream overlay state changes. */
        fun onStateChanged() {}

        /** Called when the available complication types changes. */
        fun onAvailableComplicationTypesChanged() {}

        /** Called when the low light dream is exiting and transitioning back to the user dream. */
        fun onExitLowLight() {}
    }

    private val callbacks = ArrayList<WeakReference<Callback>>()

    @Complication.ComplicationType
    private var _availableComplicationTypes = Complication.COMPLICATION_TYPE_NONE

    private var _shouldShowComplications = DreamService.DEFAULT_SHOW_COMPLICATIONS

    private val _complications: MutableCollection<Complication> = hashSetOf()

    private var supportedTypes = 0

    private val logger: DreamLogger = DreamLogger(logBuffer, TAG)

    init {
        supportedTypes =
            if (featureFlags.isEnabled(Flags.ALWAYS_SHOW_HOME_CONTROLS_ON_DREAMS)) {
                (Complication.COMPLICATION_TYPE_NONE or
                    Complication.COMPLICATION_TYPE_HOME_CONTROLS)
            } else {
                Complication.COMPLICATION_TYPE_NONE
            }
    }

    /** Adds a complication to be included on the dream overlay. */
    fun addComplication(complication: Complication) {
        executor.execute {
            if (_complications.add(complication)) {
                logger.logAddComplication(complication.toString())
                notifyCallbacksLocked { obj: Callback -> obj.onComplicationsChanged() }
            }
        }
    }

    /** Removes a complication from inclusion on the dream overlay. */
    fun removeComplication(complication: Complication) {
        executor.execute {
            if (_complications.remove(complication)) {
                logger.logRemoveComplication(complication.toString())
                notifyCallbacksLocked { obj: Callback -> obj.onComplicationsChanged() }
            }
        }
    }

    val complications: Collection<Complication>
        /** Returns collection of present [Complication]. */
        get() = getComplications(true)

    /** Returns collection of present [Complication]. */
    fun getComplications(filterByAvailability: Boolean): Collection<Complication> {
        if (isLowLightActive || containsState(STATE_HOME_CONTROL_ACTIVE)) {
            // Don't show complications on low light.
            return emptyList()
        }
        return Collections.unmodifiableCollection(
            if (filterByAvailability)
                _complications
                    .stream()
                    .filter { complication: Complication ->
                        @Complication.ComplicationType
                        val requiredTypes = complication.requiredTypeAvailability
                        // If it should show complications, show ones whose required types are
                        // available. Otherwise, only show ones that don't require types.
                        if (_shouldShowComplications) {
                            return@filter (requiredTypes and availableComplicationTypes) ==
                                requiredTypes
                        }
                        val typesToAlwaysShow = supportedTypes and availableComplicationTypes
                        (requiredTypes and typesToAlwaysShow) == requiredTypes
                    }
                    .collect(Collectors.toSet())
            else _complications
        )
    }

    private fun notifyCallbacks(callbackConsumer: Consumer<Callback>) {
        executor.execute { notifyCallbacksLocked(callbackConsumer) }
    }

    private fun notifyCallbacksLocked(callbackConsumer: Consumer<Callback>) {
        val iterator = callbacks.iterator()
        while (iterator.hasNext()) {
            val callback = iterator.next().get()
            // Remove any callbacks which have been GC'd
            if (callback == null) {
                iterator.remove()
            } else {
                callbackConsumer.accept(callback)
            }
        }
    }

    override fun addCallback(callback: Callback) {
        executor.execute {
            Objects.requireNonNull(callback, "Callback must not be null. b/128895449")
            val containsCallback =
                callbacks.stream().anyMatch { reference: WeakReference<Callback> ->
                    reference.get() === callback
                }
            if (containsCallback) {
                return@execute
            }

            callbacks.add(weakReferenceFactory.create(callback))

            if (_complications.isEmpty()) {
                return@execute
            }
            callback.onComplicationsChanged()
        }
    }

    override fun removeCallback(callback: Callback) {
        executor.execute {
            Objects.requireNonNull(callback, "Callback must not be null. b/128895449")
            val iterator = callbacks.iterator()
            while (iterator.hasNext()) {
                val cb = iterator.next().get()
                if (cb == null || cb === callback) {
                    iterator.remove()
                }
            }
        }
    }

    var isOverlayActive: Boolean
        /**
         * Returns whether the overlay is active.
         *
         * @return `true` if overlay is active, `false` otherwise.
         */
        get() = containsState(STATE_DREAM_OVERLAY_ACTIVE)
        /**
         * Sets whether the overlay is active.
         *
         * @param active `true` if overlay is active, `false` otherwise.
         */
        set(active) {
            logger.logOverlayActive(active)
            modifyState(if (active) OP_SET_STATE else OP_CLEAR_STATE, STATE_DREAM_OVERLAY_ACTIVE)
        }

    var isLowLightActive: Boolean
        /**
         * Returns whether low light mode is active.
         *
         * @return `true` if in low light mode, `false` otherwise.
         */
        get() = containsState(STATE_LOW_LIGHT_ACTIVE)
        /**
         * Sets whether low light mode is active.
         *
         * @param active `true` if low light mode is active, `false` otherwise.
         */
        set(active) {
            logger.logLowLightActive(active)

            if (isLowLightActive && !active) {
                // Notify that we're exiting low light only on the transition from active to not
                // active.
                notifyCallbacks { obj: Callback -> obj.onExitLowLight() }
            }
            modifyState(if (active) OP_SET_STATE else OP_CLEAR_STATE, STATE_LOW_LIGHT_ACTIVE)
        }

    /**
     * Returns whether the dream content and dream overlay entry animations are finished.
     *
     * @return `true` if animations are finished, `false` otherwise.
     */
    fun areEntryAnimationsFinished(): Boolean {
        return containsState(STATE_DREAM_ENTRY_ANIMATIONS_FINISHED)
    }

    /**
     * Returns whether the dream content and dream overlay exit animations are running.
     *
     * @return `true` if animations are running, `false` otherwise.
     */
    fun areExitAnimationsRunning(): Boolean {
        return containsState(STATE_DREAM_EXIT_ANIMATIONS_RUNNING)
    }

    /**
     * Returns whether assistant currently has the user's attention.
     *
     * @return `true` if assistant has the user's attention, `false` otherwise.
     */
    fun hasAssistantAttention(): Boolean {
        return containsState(STATE_HAS_ASSISTANT_ATTENTION)
    }

    var isDreamOverlayStatusBarVisible: Boolean
        /**
         * Returns whether the dream overlay status bar is currently visible.
         *
         * @return `true` if the status bar is visible, `false` otherwise.
         */
        get() = containsState(STATE_DREAM_OVERLAY_STATUS_BAR_VISIBLE)
        /**
         * Sets whether the dream overlay status bar is visible.
         *
         * @param visible `true` if the status bar is visible, `false` otherwise.
         */
        set(visible) {
            logger.logStatusBarVisible(visible)
            modifyState(
                if (visible) OP_SET_STATE else OP_CLEAR_STATE,
                STATE_DREAM_OVERLAY_STATUS_BAR_VISIBLE,
            )
        }

    private fun containsState(state: Int): Boolean {
        return (this.state and state) != 0
    }

    private fun modifyState(op: Int, state: Int) {
        val existingState = this.state
        when (op) {
            OP_CLEAR_STATE -> this.state = this.state and state.inv()
            OP_SET_STATE -> this.state = this.state or state
        }

        if (existingState != this.state) {
            notifyCallbacks { obj: Callback -> obj.onStateChanged() }
        }
    }

    /**
     * Sets whether home control panel is active.
     *
     * @param active `true` if home control panel is active, `false` otherwise.
     */
    fun setHomeControlPanelActive(active: Boolean) {
        modifyState(if (active) OP_SET_STATE else OP_CLEAR_STATE, STATE_HOME_CONTROL_ACTIVE)
    }

    /**
     * Sets whether dream content and dream overlay entry animations are finished.
     *
     * @param finished `true` if entry animations are finished, `false` otherwise.
     */
    fun setEntryAnimationsFinished(finished: Boolean) {
        modifyState(
            if (finished) OP_SET_STATE else OP_CLEAR_STATE,
            STATE_DREAM_ENTRY_ANIMATIONS_FINISHED,
        )
    }

    /**
     * Sets whether dream content and dream overlay exit animations are running.
     *
     * @param running `true` if exit animations are running, `false` otherwise.
     */
    fun setExitAnimationsRunning(running: Boolean) {
        modifyState(
            if (running) OP_SET_STATE else OP_CLEAR_STATE,
            STATE_DREAM_EXIT_ANIMATIONS_RUNNING,
        )
    }

    /**
     * Sets whether assistant currently has the user's attention.
     *
     * @param hasAttention `true` if has the user's attention, `false` otherwise.
     */
    fun setHasAssistantAttention(hasAttention: Boolean) {
        logger.logHasAssistantAttention(hasAttention)
        modifyState(
            if (hasAttention) OP_SET_STATE else OP_CLEAR_STATE,
            STATE_HAS_ASSISTANT_ATTENTION,
        )
    }

    @get:Complication.ComplicationType
    var availableComplicationTypes: Int
        /** Returns the available complication types. */
        get() = _availableComplicationTypes
        /** Sets the available complication types for the dream overlay. */
        set(types) {
            executor.execute {
                logger.logAvailableComplicationTypes(types)
                _availableComplicationTypes = types
                notifyCallbacksLocked { obj: Callback -> obj.onAvailableComplicationTypesChanged() }
            }
        }

    var shouldShowComplications: Boolean
        /** Returns whether the dream overlay should show complications. */
        get() = _shouldShowComplications
        /** Sets whether the dream overlay should show complications. */
        set(shouldShowComplications) {
            executor.execute {
                logger.logShouldShowComplications(shouldShowComplications)
                _shouldShowComplications = shouldShowComplications
                notifyCallbacksLocked { obj: Callback -> obj.onAvailableComplicationTypesChanged() }
            }
        }

    companion object {
        private const val TAG = "DreamOverlayStateCtlr"

        const val STATE_DREAM_OVERLAY_ACTIVE: Int = 1 shl 0
        const val STATE_LOW_LIGHT_ACTIVE: Int = 1 shl 1
        const val STATE_DREAM_ENTRY_ANIMATIONS_FINISHED: Int = 1 shl 2
        const val STATE_DREAM_EXIT_ANIMATIONS_RUNNING: Int = 1 shl 3
        const val STATE_HAS_ASSISTANT_ATTENTION: Int = 1 shl 4
        const val STATE_DREAM_OVERLAY_STATUS_BAR_VISIBLE: Int = 1 shl 5
        private const val STATE_HOME_CONTROL_ACTIVE = 1 shl 6
        private const val OP_CLEAR_STATE = 1
        private const val OP_SET_STATE = 2
    }
}
