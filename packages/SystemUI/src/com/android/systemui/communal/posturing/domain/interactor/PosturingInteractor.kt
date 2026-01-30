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

package com.android.systemui.communal.posturing.domain.interactor

import android.hardware.Sensor
import com.android.systemui.communal.posturing.data.repository.PosturingRepository
import com.android.systemui.communal.posturing.domain.model.AggregatedConfidenceState
import com.android.systemui.communal.posturing.shared.model.ConfidenceLevel
import com.android.systemui.communal.posturing.shared.model.PosturedState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.CommunalLog
import com.android.systemui.log.dagger.CommunalTableLog
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.util.kotlin.observeTriggerSensor
import com.android.systemui.util.kotlin.pairwiseBy
import com.android.systemui.util.kotlin.slidingWindow
import com.android.systemui.util.sensors.AsyncSensorManager
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class PosturingInteractor
@Inject
constructor(
    repository: PosturingRepository,
    private val asyncSensorManager: AsyncSensorManager,
    @Application private val applicationScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @CommunalLog private val logBuffer: LogBuffer,
    @CommunalTableLog private val tableLogBuffer: TableLogBuffer,
    private val clock: SystemClock,
) {
    private val logger = Logger(logBuffer, TAG)

    private val debugPostured = MutableStateFlow<PosturedState>(PosturedState.Unknown)

    fun setValueForDebug(value: PosturedState) {
        debugPostured.value = value
    }

    /**
     * Detects whether or not the device is stationary, applying a sliding window smoothing
     * algorithm.
     */
    private val stationarySmoothed: Flow<AggregatedConfidenceState> =
        merge(
                observeTriggerSensor(Sensor.TYPE_PICK_UP_GESTURE)
                    // If pickup detected, avoid triggering posturing at all within the sliding
                    // window by emitting a negative confidence.
                    .map { ConfidenceLevel.Negative(1f) }
                    .onEach { logger.i("pickup gesture detected") },
                observeTriggerSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
                    // If motion detected, avoid triggering posturing at all within the sliding
                    // window by emitting a negative confidence.
                    .map { ConfidenceLevel.Negative(1f) }
                    .onEach { logger.i("significant motion detected") },
                repository.positionState.map { it.stationary },
            )
            .aggregateConfidences()

    /**
     * Detects whether or not the device is in an upright orientation, applying a sliding window
     * smoothing algorithm.
     */
    private val orientationSmoothed: Flow<AggregatedConfidenceState> =
        repository.positionState.map { it.orientation }.aggregateConfidences()

    /**
     * Posturing is composed of the device being stationary and in the correct orientation. If both
     * conditions are met, then consider it postured.
     */
    private val posturedSmoothed: StateFlow<PosturedState> =
        combine(stationarySmoothed, orientationSmoothed, ::Pair)
            // Add small debounce to batch the processing of stationary and orientation changes
            // which come in very close together.
            .debounce(BATCHING_DEBOUNCE_DURATION)
            .map { (stationaryConfidence, orientationConfidence) ->
                val isStationary = stationaryConfidence.isStationary()
                val isInOrientation = orientationConfidence.isInOrientation()

                logger.i({ "stationary ($bool1): $str1 | orientation ($bool2): $str2" }) {
                    bool1 = isStationary
                    str1 = stationaryConfidence.toString()
                    bool2 = isInOrientation
                    str2 = orientationConfidence.toString()
                }

                if (isStationary && isInOrientation) {
                    PosturedState.Postured
                } else if (
                    stationaryConfidence.latestConfidence >= ENTER_CONFIDENCE_THRESHOLD &&
                        orientationConfidence.latestConfidence >= ENTER_CONFIDENCE_THRESHOLD
                ) {
                    // We may be postured soon since the latest confidence is above the threshold.
                    // If  no new events come in, we will eventually transition to postured at the
                    // end of the sliding window.
                    PosturedState.MayBePostured(isStationary, isInOrientation)
                } else {
                    PosturedState.NotPostured(isStationary, isInOrientation)
                }
            }
            .logDiffsForTable(tableLogBuffer = tableLogBuffer, initialValue = PosturedState.Unknown)
            .flowOn(bgDispatcher)
            .stateIn(
                scope = applicationScope,
                // Avoid losing the smoothing history if the user plug/unplugs rapidly.
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = STOP_TIMEOUT_AFTER_UNSUBSCRIBE.inWholeMilliseconds,
                        replayExpirationMillis = 0,
                    ),
                initialValue = PosturedState.Unknown,
            )

    /**
     * Whether the device is postured.
     *
     * NOTE: Due to smoothing, this signal may be delayed to ensure we have a stable reading before
     * being considered postured.
     */
    val postured: Flow<Boolean> =
        combine(posturedSmoothed, debugPostured) { postured, debugValue ->
            debugValue.asBoolean() ?: postured.asBoolean() ?: false
        }

    /** Whether the device may become postured soon. */
    val mayBePostured: Flow<Boolean> = posturedSmoothed.map { it is PosturedState.MayBePostured }

    /** Helper for aggregating the confidence levels in the sliding window. */
    private fun Flow<ConfidenceLevel>.aggregateConfidences(): Flow<AggregatedConfidenceState> =
        filterNot { it is ConfidenceLevel.Unknown }
            .slidingWindow(SLIDING_WINDOW_DURATION, clock)
            .pairwiseBy(emptyList()) { old, new ->
                // If all elements have expired out of the window, then maintain only the last and
                // most recent element.
                if (old.isNotEmpty() && new.isEmpty()) {
                    old.subList(old.lastIndex, old.lastIndex + 1)
                } else {
                    new
                }
            }
            .distinctUntilChanged()
            .map { window -> window.toConfidenceState() }

    /**
     * Helper for observing a trigger sensor, which automatically unregisters itself after it
     * executes once.
     */
    private fun observeTriggerSensor(type: Int): Flow<Unit> {
        val sensor = asyncSensorManager.getDefaultSensor(type) ?: return emptyFlow()
        return asyncSensorManager.observeTriggerSensor(sensor)
    }

    private fun AggregatedConfidenceState.isStationary(): Boolean {
        return avgConfidence >= getThreshold(posturedSmoothed.value.isStationary)
    }

    private fun AggregatedConfidenceState.isInOrientation(): Boolean {
        return avgConfidence >= getThreshold(posturedSmoothed.value.inOrientation)
    }

    private fun getThreshold(currentlyMeetsThreshold: Boolean) =
        if (currentlyMeetsThreshold) {
            EXIT_CONFIDENCE_THRESHOLD
        } else {
            ENTER_CONFIDENCE_THRESHOLD
        }

    companion object {
        const val TAG = "PosturingInteractor"
        val SLIDING_WINDOW_DURATION = 10.seconds

        /**
         * The confidence threshold required to enter a stationary / orientation state. If the
         * confidence is greater than this, we may enter a postured state.
         */
        const val ENTER_CONFIDENCE_THRESHOLD = 0.8f

        /**
         * The confidence threshold required to exit a stationary / orientation state. If the
         * confidence is less than this, we may exit the postured state. This is smaller than
         * [ENTER_CONFIDENCE_THRESHOLD] to help ensure we don't exit the state due to small amounts
         * of motion, such as the user tapping on the screen.
         */
        const val EXIT_CONFIDENCE_THRESHOLD = 0.5f

        /**
         * Amount of time to keep the posturing algorithm running after the last subscriber
         * unsubscribes. This helps ensure that if the charging connection is flaky, we don't lose
         * the posturing state.
         */
        val STOP_TIMEOUT_AFTER_UNSUBSCRIBE = 5.seconds

        /** Debounce duration to batch the processing of events. */
        val BATCHING_DEBOUNCE_DURATION = 10.milliseconds
    }
}

fun PosturedState.asBoolean(): Boolean? {
    return when (this) {
        PosturedState.Unknown -> null
        else -> isStationary && inOrientation
    }
}

private fun List<ConfidenceLevel>.toConfidenceState(): AggregatedConfidenceState {
    return AggregatedConfidenceState(rawWindow = this.toList())
}
