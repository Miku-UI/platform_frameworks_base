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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.annotation.SuppressLint
import android.content.ComponentName
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.animation.DelegateTransitionAnimatorController
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel.TransitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * A class that can help [OngoingActivityChipViewModel] instances with various transition states.
 *
 * For now, this class's only functionality is immediately hiding the chip if the user has tapped an
 * activity chip and then clicked "Stop" on the resulting dialog. There's a bit of a delay between
 * when the user clicks "Stop" and when the system services notify SysUI that the activity has
 * indeed stopped. We don't want the chip to briefly show for a few frames during that delay, so
 * this class helps us immediately hide the chip as soon as the user clicks "Stop" in the dialog.
 * See b/353249803#comment4.
 */
class ChipTransitionHelper(@Application private val scope: CoroutineScope) {
    /** A flow that emits each time the user has clicked "Stop" on the dialog. */
    @SuppressLint("SharedFlowCreation")
    private val activityStoppedFromDialogEvent = MutableSharedFlow<Unit>()

    /** True if the user recently stopped the activity from the dialog. */
    private val wasActivityRecentlyStoppedFromDialog: Flow<Boolean> =
        activityStoppedFromDialogEvent
            .transformLatest {
                // Give system services 500ms to stop the activity and notify SysUI. Once more than
                // 500ms has elapsed, we should go back to using the current system service
                // information as the source of truth.
                emit(true)
                delay(500)
                emit(false)
            }
            // Use stateIn so that the flow created in [createChipFlow] is guaranteed to
            // emit. (`combine`s require that all input flows have emitted.)
            .stateIn(scope, SharingStarted.Lazily, false)

    /**
     * Notifies this class that the user just clicked "Stop" on the stop dialog that's shown when
     * the chip is tapped.
     *
     * Call this method in order to immediately hide the chip.
     */
    fun onActivityStoppedFromDialog() {
        // Because this event causes UI changes, make sure it's launched on the main thread scope.
        scope.launch { activityStoppedFromDialogEvent.emit(Unit) }
    }

    /**
     * Creates a flow that will forcibly hide the chip if the user recently stopped the activity
     * (see [onActivityStoppedFromDialog]). In general, this flow just uses value in [chip].
     */
    fun createChipFlow(chip: Flow<OngoingActivityChipModel>): StateFlow<OngoingActivityChipModel> {
        return combine(chip, wasActivityRecentlyStoppedFromDialog) {
                chipModel,
                activityRecentlyStopped ->
                if (activityRecentlyStopped) {
                    // There's a bit of a delay between when the user stops an activity via
                    // SysUI and when the system services notify SysUI that the activity has
                    // indeed stopped. Prevent the chip from showing during this delay by
                    // immediately hiding it without any animation.
                    OngoingActivityChipModel.Inactive(shouldAnimate = false)
                } else {
                    chipModel
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(), OngoingActivityChipModel.Inactive())
    }

    companion object {
        /** Define the current state of the chip's transition animation. */
        sealed interface TransitionState {
            /** Idle. */
            data object NoTransition : TransitionState

            /** Launch animation has been requested but hasn't started yet. */
            data object LaunchRequested : TransitionState

            /** Launch animation in progress. */
            data object Launching : TransitionState

            /** Return animation has been requested but hasn't started yet. */
            data object ReturnRequested : TransitionState

            /** Return animation in progress. */
            data object Returning : TransitionState
        }

        /** Model that contains a chip's information relevant to launch and return transitions. */
        sealed interface TransitionAwareChipModel {
            val cookie: ActivityTransitionAnimator.TransitionCookie

            /** The chip is not currently in use. */
            data class Inactive(override val cookie: ActivityTransitionAnimator.TransitionCookie) :
                TransitionAwareChipModel

            /** The chip is in use, though it may or may not currently be hidden. */
            data class Active(
                override val cookie: ActivityTransitionAnimator.TransitionCookie,
                val component: ComponentName?,
                val isAppVisible: Boolean,
                val launchCujType: Int? = null,
                val returnCujType: Int? = null,
            ) : TransitionAwareChipModel
        }

        /**
         * Builds a [TransitionManager] for a transition-aware chip, using its current state and
         * [transitionState].
         *
         * This manager will register and unregister [factory] when the chip is active and inactive,
         * or create a new factory to register/unregister if [factory] is null. Before, during, and
         * after each transition, [updateTransitionState] will be used to update chip's latest
         * transition state.
         */
        fun buildTransitionManager(
            chip: TransitionAwareChipModel?,
            transitionState: TransitionState,
            updateTransitionState: (TransitionState) -> Unit,
            scope: CoroutineScope,
            factory: ComposableControllerFactory?,
            activityStarter: ActivityStarter,
        ): TransitionManager? {
            if (!StatusBarChipsReturnAnimations.isEnabled || chip == null) return null
            return if (chip !is TransitionAwareChipModel.Active) {
                // If the chip is inactive, we don't want its transitions to be registered. This
                // manager only knows how to unregister (a no-op if no registration exists).
                TransitionManager(
                    unregisterTransition = { activityStarter.unregisterTransition(chip.cookie) }
                )
            } else {
                if (chip.component != null) {
                    val factory =
                        if (factory?.component == chip.component) {
                            factory
                        } else {
                            buildTransitionControllerFactory(
                                chip.cookie,
                                chip.component,
                                chip.launchCujType,
                                chip.returnCujType,
                                updateTransitionState,
                            )
                        }
                    TransitionManager(
                        factory,
                        registerTransition = {
                            activityStarter.registerTransition(chip.cookie, factory, scope)
                        },
                        // Make the chip invisible at the beginning of the return transition to
                        // avoid it flickering.
                        hideChipForTransition = transitionState is TransitionState.ReturnRequested,
                    )
                } else {
                    // Without a component we can't instantiate a controller factory, and without a
                    // factory registering an animation is impossible. In this case, the transition
                    // manager is empty and inert.
                    TransitionManager()
                }
            }
        }

        /** Creates a controller factory for launch and return transitions. */
        private fun buildTransitionControllerFactory(
            cookie: ActivityTransitionAnimator.TransitionCookie,
            component: ComponentName,
            launchCujType: Int?,
            returnCujType: Int?,
            updateTransitionState: (TransitionState) -> Unit,
        ): ComposableControllerFactory {
            return object :
                ComposableControllerFactory(cookie, component, launchCujType, returnCujType) {
                override suspend fun createController(
                    forLaunch: Boolean
                ): ActivityTransitionAnimator.Controller {
                    // Update the transition state first, so that Compose can react and add the
                    // chip if necessary.
                    updateTransitionState(
                        if (forLaunch) {
                            TransitionState.LaunchRequested
                        } else {
                            TransitionState.ReturnRequested
                        }
                    )

                    // This is the suspend part, as we might need to wait for the chip to be
                    // added to the composition. Once this is done, [expandable] will be
                    // non-null and usable.
                    val controller =
                        expandable
                            .mapNotNull {
                                it?.activityTransitionController(
                                    launchCujType,
                                    cookie,
                                    component,
                                    returnCujType,
                                    isEphemeral = false,
                                )
                            }
                            .first()

                    return object : DelegateTransitionAnimatorController(controller) {
                        override val isLaunching: Boolean
                            get() = forLaunch

                        override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
                            delegate.onTransitionAnimationStart(isExpandingFullyAbove)
                            updateTransitionState(
                                if (isLaunching) {
                                    TransitionState.Launching
                                } else {
                                    TransitionState.Returning
                                }
                            )
                        }

                        override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                            delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
                            updateTransitionState(TransitionState.NoTransition)
                        }

                        override fun onTransitionAnimationCancelled(
                            newKeyguardOccludedState: Boolean?
                        ) {
                            delegate.onTransitionAnimationCancelled(newKeyguardOccludedState)
                            updateTransitionState(TransitionState.NoTransition)
                        }
                    }
                }
            }
        }

        /**
         * Determines whether or not a chip should be hidden based on its previous and current
         * state, as well as its previous and current transition state.
         */
        fun shouldChipBeHidden(
            oldState: TransitionAwareChipModel?,
            newState: TransitionAwareChipModel.Active,
            oldTransitionState: TransitionState,
            newTransitionState: TransitionState,
        ): Boolean {
            // The app is in the background and no transitions are ongoing (during transitions,
            // [isAppVisible] must always be true). Show the chip.
            if (!newState.isAppVisible) return false

            // The chip has just appeared with the app visible. Hide the chip.
            if (oldState !is TransitionAwareChipModel.Active) return true

            // The transition state has changed. A launch or return animation has been requested or
            // is ongoing. Show the chip.
            if (
                newTransitionState is TransitionState.LaunchRequested ||
                    newTransitionState is TransitionState.Launching ||
                    newTransitionState is TransitionState.ReturnRequested ||
                    newTransitionState is TransitionState.Returning
            ) {
                return false
            }

            // The state went from the app not being visible to visible with no transition requested
            // or ongoing. This can happen when the app is opened by means other than tapping the
            // chip. Hide the chip.
            if (!oldState.isAppVisible) return true

            // The app was and remains visible, so we generally want to hide the chip. The only
            // exception is if a return transition has just ended. In this case, the transition
            // state changes shortly before the app visibility does. If we hide the chip between
            // these two updates, this results in a flicker. We bridge the gap by keeping the chip
            // showing.
            return oldTransitionState != TransitionState.Returning
        }
    }
}
