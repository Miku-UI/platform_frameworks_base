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

package com.android.settingslib.media

import android.media.RoutingChangeInfo
import android.media.SuggestedDeviceInfo
import android.os.Handler
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.android.media.flags.Flags.useSuggestedDeviceConnectionManager
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_SELECTED
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "SuggestedDeviceManager"

@VisibleForTesting
const val CONNECTING_TIMEOUT_MS = 30_000L
@VisibleForTesting
const val CONNECTING_FAILED_TIMEOUT_MS = 10_000L

/**
 * Provides data to render and handles user interactions for the suggested device chip within the
 * Android Media Controls.
 *
 * This class exposes the [SuggestedDeviceState] which is calculated based on:
 * - Lists of device suggestions and media routes (media devices) provided by the Media Router.
 * - User interactions with the suggested device chip.
 * - The results of user-initiated connection attempts to these devices.
 *
 * @param localMediaManager an instance of [LocalMediaManager]
 * @param handler a MainHandler to run timeout events on.
 */
class SuggestedDeviceManager(
  private val localMediaManager: LocalMediaManager,
  private val handler: Handler,
  private val suggestedDeviceConnectionManager: SuggestedDeviceConnectionManager,
) {
  private val lock: Any = Object()
  private val listeners = CopyOnWriteArraySet<Listener>()
  @GuardedBy("lock") private var mediaDevices: List<MediaDevice> = listOf()
  @GuardedBy("lock") private var topSuggestion: SuggestedDeviceInfo? = null
  @GuardedBy("lock") private var suggestedDeviceState: SuggestedDeviceState? = null
  // Overrides the suggested device state obtained from the [MediaDevice] that matches the
  // [topSuggestion]. This is necessary to prevent hiding or changing the title of the suggested
  // device chip during connection attempts or when displaying error messages.
  @GuardedBy("lock") private var suggestedStateOverride: SuggestedDeviceState? = null
  @GuardedBy("lock") private var hideSuggestedDeviceState: Boolean = false

  private val onSuggestedStateOverrideExpiredRunnable = Runnable {
    synchronized(lock) {
      if (suggestedStateOverride?.connectionState == STATE_CONNECTING_FAILED) {
        // After the connection error, hide the suggestion chip until the new suggestion is
        // requested.
        hideSuggestedDeviceState = true
      }
      suggestedStateOverride = null
      updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
    }
    dispatchOnSuggestedDeviceUpdated()
  }

  private val localMediaManagerDeviceCallback =
    object : LocalMediaManager.DeviceCallback {
      override fun onDeviceListUpdate(newDevices: List<MediaDevice>?) {
        val stateChanged =
          synchronized(lock) {
            mediaDevices = newDevices?.toList() ?: listOf()
            updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
          }
        if (stateChanged) {
          dispatchOnSuggestedDeviceUpdated()
        }
      }

      override fun onDeviceSuggestionsUpdated(newSuggestions: List<SuggestedDeviceInfo>) {
        val stateChanged =
          synchronized(lock) {
            topSuggestion = newSuggestions.firstOrNull()
            updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
          }
        if (stateChanged) {
          dispatchOnSuggestedDeviceUpdated()
        }
      }

      override fun onConnectSuggestedDeviceFinished(
        newSuggestedDeviceState: SuggestedDeviceState,
        success: Boolean,
      ) {
        if (!useSuggestedDeviceConnectionManager()) {
          onSuggestedDeviceConnectionFinished(newSuggestedDeviceState, success)
        }
      }
    }

  fun addListener(listener: Listener) {
    val shouldRegisterCallback =
      synchronized(lock) {
        val wasSetEmpty = listeners.isEmpty()
        listeners.add(listener)
        wasSetEmpty
      }

    if (shouldRegisterCallback) {
      eagerlyUpdateState()
      localMediaManager.registerCallback(localMediaManagerDeviceCallback)
    }
  }

  fun removeListener(listener: Listener) {
    val shouldUnregisterCallback =
      synchronized(lock) {
        listeners.remove(listener)
        listeners.isEmpty()
      }

    if (shouldUnregisterCallback) {
      localMediaManager.unregisterCallback(localMediaManagerDeviceCallback)
    }
  }

  fun cancelAllRequests() {
    if (useSuggestedDeviceConnectionManager()) suggestedDeviceConnectionManager.cancel()
  }

  fun requestDeviceSuggestion() {
    localMediaManager.requestDeviceSuggestion()
    stopHidingSuggestedDeviceState()
  }

  private fun stopHidingSuggestedDeviceState() {
    var stateChanged = false
    synchronized(lock) {
      if (hideSuggestedDeviceState) {
        hideSuggestedDeviceState = false
        stateChanged = updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
      }
    }
    if (stateChanged) {
      dispatchOnSuggestedDeviceUpdated()
    }
  }

  fun getSuggestedDevice(): SuggestedDeviceState? {
    if (listeners.isEmpty()) {
      // If there were no callbacks set, recalculate the state before returning the result.
      eagerlyUpdateState()
    }
    return suggestedDeviceState
  }

  fun connectSuggestedDevice(
    newSuggestedDeviceState: SuggestedDeviceState,
    routingChangeInfo: RoutingChangeInfo,
  ) {
    if (!isCurrentSuggestion(newSuggestedDeviceState.suggestedDeviceInfo)) {
      Log.w(TAG, "Suggestion got changed, aborting connection.")
      return
    }
    if (useSuggestedDeviceConnectionManager()) {
      try {
        suggestedDeviceConnectionManager.connect(
          newSuggestedDeviceState,
          routingChangeInfo,
        ) { suggestedDeviceState, success ->
          onSuggestedDeviceConnectionFinished(suggestedDeviceState, success)
        }
        overrideSuggestedStateWithExpiration(
          connectionState = STATE_CONNECTING,
          timeoutMs = CONNECTING_TIMEOUT_MS,
        )
      } catch (e: IllegalStateException) {
        Log.e(TAG, "Connection already in progress", e)
      }
    } else {
      overrideSuggestedStateWithExpiration(
        connectionState = STATE_CONNECTING,
        timeoutMs = CONNECTING_TIMEOUT_MS,
      )
      localMediaManager.connectSuggestedDevice(newSuggestedDeviceState, routingChangeInfo)
    }
  }

  private fun onSuggestedDeviceConnectionFinished(
    newSuggestedDeviceState: SuggestedDeviceState,
    success: Boolean,
  ) {
    if (!isCurrentSuggestion(newSuggestedDeviceState.suggestedDeviceInfo)) {
      Log.w(TAG, "onSuggestedDeviceConnectionFinished. Suggestion got changed.")
      return
    }
    if (!success) {
      overrideSuggestedStateWithExpiration(
        connectionState = STATE_CONNECTING_FAILED,
        timeoutMs = CONNECTING_FAILED_TIMEOUT_MS,
      )
    } // On success, the state should automatically be updated by the MediaDevice state.
  }

  private fun eagerlyUpdateState() {
    synchronized(lock) {
      mediaDevices = localMediaManager.mediaDevices
      topSuggestion = localMediaManager.suggestions.firstOrNull()
      updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
    }
  }

  @GuardedBy("lock")
  private fun updateSuggestedDeviceStateLocked(
    newTopSuggestion: SuggestedDeviceInfo?,
    newMediaDevices: List<MediaDevice>,
  ): Boolean {
    tryClearSuggestedStateOverrideLocked(newMediaDevices)
    val newSuggestedDeviceState = suggestedStateOverride ?:
      calculateNewSuggestedDeviceStateLocked(newTopSuggestion, newMediaDevices)
    if (newSuggestedDeviceState != suggestedDeviceState) {
      suggestedDeviceState = newSuggestedDeviceState
      return true
    }
    return false
  }

  @GuardedBy("lock")
  private fun calculateNewSuggestedDeviceStateLocked(
    newTopSuggestion: SuggestedDeviceInfo?,
    newMediaDevices: List<MediaDevice>,
  ): SuggestedDeviceState? {
    if (hideSuggestedDeviceState) {
      return null
    }

    if (newTopSuggestion == null) {
      return null
    }

    val newConnectionState =
      getConnectionStateFromMatchedDeviceLocked(newTopSuggestion, newMediaDevices)
    return if (isConnectedState(newConnectionState)) {
      // Don't display a suggestion if the MediaDevice that matches the suggestion is connected.
      null
    } else {
      SuggestedDeviceState(newTopSuggestion, newConnectionState)
    }
  }

  @GuardedBy("lock")
  @MediaDeviceState
  private fun getConnectionStateFromMatchedDeviceLocked(
    newTopSuggestion: SuggestedDeviceInfo,
    newMediaDevices: List<MediaDevice>,
  ): Int {
    val matchedDevice = getDeviceByRouteId(newMediaDevices, newTopSuggestion.routeId)
    if (matchedDevice?.isSelected == true) {
      return STATE_SELECTED
    }
    return matchedDevice?.state ?: STATE_DISCONNECTED
  }

  private fun isConnectedState(@MediaDeviceState state: Int): Boolean =
    state == STATE_CONNECTED || state == STATE_SELECTED

  private fun getDeviceByRouteId(mediaDevices: List<MediaDevice>, routeId: String): MediaDevice? =
    mediaDevices.find { it.routeInfo?.id == routeId }

  private fun isCurrentSuggestion(suggestedDeviceInfo: SuggestedDeviceInfo) =
    synchronized(lock) {
      suggestedDeviceState?.suggestedDeviceInfo?.routeId == suggestedDeviceInfo.routeId
    }

  private fun overrideSuggestedStateWithExpiration(connectionState: Int, timeoutMs: Long) {
    synchronized(lock) {
      suggestedStateOverride = suggestedDeviceState?.copy(connectionState = connectionState)
      suggestedDeviceState = suggestedStateOverride
      handler.removeCallbacks(onSuggestedStateOverrideExpiredRunnable)
      handler.postDelayed(onSuggestedStateOverrideExpiredRunnable, timeoutMs)
    }
    dispatchOnSuggestedDeviceUpdated()
  }

  @GuardedBy("lock")
  private fun tryClearSuggestedStateOverrideLocked(newMediaDevices: List<MediaDevice>) {
    suggestedStateOverride?.let { override ->
      val newConnectionState =
          getConnectionStateFromMatchedDeviceLocked(override.suggestedDeviceInfo, newMediaDevices)
      // Clear the state override unless the matched device state is:
      // - STATE_DISCONNECTED: This state might be reported during the connection process,
      //   potentially causing UI flicker.
      // - Same connection state as in the override: Getting an event with the same device state
      //   should not affect the override timeout.
      if (newConnectionState !in setOf(STATE_DISCONNECTED, override.connectionState)) {
        suggestedStateOverride = null
        handler.removeCallbacks(onSuggestedStateOverrideExpiredRunnable)
      }
    }
  }

  private fun dispatchOnSuggestedDeviceUpdated() {
    val state = synchronized(lock) { suggestedDeviceState }
    Log.i(TAG, "dispatchOnSuggestedDeviceUpdated(), state: $state")
    listeners.forEach { it.onSuggestedDeviceStateUpdated(state) }
  }

  interface Listener {
    fun onSuggestedDeviceStateUpdated(state: SuggestedDeviceState?)
  }
}
