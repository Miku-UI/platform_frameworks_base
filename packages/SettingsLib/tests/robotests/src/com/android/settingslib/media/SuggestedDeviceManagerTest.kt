/*
 * Copyright 2025 The Android Open Source Project
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

import android.media.MediaRoute2Info
import android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER
import android.media.RoutingChangeInfo
import android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS
import android.media.SuggestedDeviceInfo
import android.os.Handler
import android.os.Looper
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.media.flags.Flags
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

private const val ROUTE_ID_1 = "ROUTE_ID_1"
private const val ROUTE_ID_2 = "ROUTE_ID_2"
private const val ROUTE_1_NAME = "ROUTE_1_NAME"
private const val ROUTE_2_NAME = "ROUTE_2_NAME"

@RunWith(RobolectricTestRunner::class)
class SuggestedDeviceManagerTest {
  @get:Rule val setFlagsRule = SetFlagsRule()

  private var localMediaManager: LocalMediaManager = mock<LocalMediaManager>()
  private val suggestedDeviceConnectionManager = mock<SuggestedDeviceConnectionManager>()
  private var listener = mock<SuggestedDeviceManager.Listener>()
  private var listener2 = mock<SuggestedDeviceManager.Listener>()
  private lateinit var mSuggestedDeviceManager: SuggestedDeviceManager

  private val routeInfo1 = mock<MediaRoute2Info> { on { id } doReturn ROUTE_ID_1 }
  private val mediaDevice1 =
    mock<MediaDevice> {
      on { routeInfo } doReturn routeInfo1
      on { state } doReturn STATE_DISCONNECTED
      on { isSelected } doReturn false
    }

  private val routeInfo2 = mock<MediaRoute2Info> { on { id } doReturn ROUTE_ID_2 }
  private val mediaDevice2 =
    mock<MediaDevice> {
      on { routeInfo } doReturn routeInfo2
      on { state } doReturn STATE_DISCONNECTED
      on { isSelected } doReturn false
    }

  private val suggestedDeviceInfo1 =
    SuggestedDeviceInfo.Builder(ROUTE_1_NAME, ROUTE_ID_1, TYPE_REMOTE_SPEAKER).build()

  private val suggestedDeviceInfo2 =
    SuggestedDeviceInfo.Builder(ROUTE_2_NAME, ROUTE_ID_2, TYPE_REMOTE_SPEAKER).build()

  private val routingChangeInfo =
    RoutingChangeInfo(ENTRY_POINT_SYSTEM_MEDIA_CONTROLS, /* isSuggested= */ true)

  @Before
  fun setUp() {
    val handler = Handler(Looper.getMainLooper())
    mSuggestedDeviceManager =
      SuggestedDeviceManager(localMediaManager, handler, suggestedDeviceConnectionManager)
  }

  @Test
  fun addListener_firstListener_registersCallback() {
    // Initially no listeners
    mSuggestedDeviceManager.addListener(listener)

    // Verify that the callback is registered with LocalMediaManager
    verify(localMediaManager).registerCallback(any())
    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions
    verifyNoMoreInteractions(localMediaManager)
  }

  @Test
  fun addListener_multipleListeners_registersCallbackOnce() {
    mSuggestedDeviceManager.addListener(listener)

    verify(localMediaManager).registerCallback(any())

    mSuggestedDeviceManager.addListener(listener2)

    verify(localMediaManager).registerCallback(any())
  }

  @Test
  fun removeListener_lastListener_unregistersCallback() {
    mSuggestedDeviceManager.addListener(listener)
    mSuggestedDeviceManager.addListener(listener2)
    mSuggestedDeviceManager.removeListener(listener)

    verify(localMediaManager, never()).unregisterCallback(any())

    mSuggestedDeviceManager.removeListener(listener2)

    verify(localMediaManager).unregisterCallback(any())
  }

  @Test
  fun requestDeviceSuggestion_callsLocalMediaManager() {
    mSuggestedDeviceManager.requestDeviceSuggestion()

    verify(localMediaManager).requestDeviceSuggestion()
  }

  @Test
  fun getSuggestedDevice_beforeListenersSet_callsLocalMediaManager() {
    localMediaManager.stub {
      on { mediaDevices } doReturn listOf(mediaDevice1)
      on { suggestions } doReturn listOf(suggestedDeviceInfo1)
    }

    assertThat(mSuggestedDeviceManager.getSuggestedDevice())
      .isEqualTo(SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state))
    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions
  }

  @Test
  fun getSuggestedDevice_addListener_callsLocalMediaManager() {
    localMediaManager.stub {
      on { mediaDevices } doReturn listOf(mediaDevice1)
      on { suggestions } doReturn listOf(suggestedDeviceInfo1)
    }

    mSuggestedDeviceManager.addListener(listener)

    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions

    assertThat(mSuggestedDeviceManager.getSuggestedDevice())
      .isEqualTo(SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state))
    // No additional calls are made after listeners are set
    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions
  }

  @Test
  fun onDeviceSuggestionsUpdated_noMatchedDevice_dispatchedDisconnectedState() {
    // No devices initially in localMediaManager
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    val expectedState =
      SuggestedDeviceState(
        suggestedDeviceInfo = suggestedDeviceInfo1,
        connectionState = STATE_DISCONNECTED,
      )
    verify(listener).onSuggestedDeviceStateUpdated(expectedState)
  }

  @Test
  fun onDeviceSuggestionsUpdated_matchedDeviceNotSelected_dispatchedMediaDeviceState() {
    mediaDevice1.stub { on { state } doReturn STATE_CONNECTING }

    // Set up initial devices
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    val expectedState =
      SuggestedDeviceState(
        suggestedDeviceInfo = suggestedDeviceInfo1,
        connectionState = STATE_CONNECTING,
      )
    verify(listener).onSuggestedDeviceStateUpdated(expectedState)
  }

  @Test
  fun onDeviceSuggestionsUpdated_matchedDeviceSelected_dispatchesNull() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(
          suggestedDeviceInfo = suggestedDeviceInfo1,
          connectionState = STATE_DISCONNECTED,
        )
      )

    mediaDevice1.stub {
      on { state } doReturn STATE_CONNECTED
      on { isSelected } doReturn true
    }
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    verify(listener).onSuggestedDeviceStateUpdated(null)
  }

  @Test
  fun onDeviceSuggestionsUpdated_newSuggestionDifferent_dispatchesUpdatedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1, mediaDevice2))

    // First suggestion update
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val expectedState1 = SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState1)

    // Second suggestion update with a different suggestion
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo2))
    val expectedState2 = SuggestedDeviceState(suggestedDeviceInfo2, mediaDevice2.state)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState2)
  }

  @Test
  fun onDeviceSuggestionsUpdated_hasStateOverrideAndNewSuggestionDifferent_keepsOverriddenState() {
    val initialSuggestedDeviceInfo = suggestedDeviceInfo1
    val updatedSuggestedDeviceInfo = suggestedDeviceInfo2

    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1, mediaDevice2))

    // Initial suggested device is set.
    deviceCallback.onDeviceSuggestionsUpdated(listOf(initialSuggestedDeviceInfo))
    val initialSuggestedDeviceState =
      SuggestedDeviceState(initialSuggestedDeviceInfo, mediaDevice1.state)
    verify(listener).onSuggestedDeviceStateUpdated(initialSuggestedDeviceState)

    // Emulate starting connection and subsequently setting the override.
    mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)
    val connectingSuggestedState =
      initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING)
    verify(listener).onSuggestedDeviceStateUpdated(connectingSuggestedState)
    clearInvocations(listener)

    // A different suggested device is set.
    deviceCallback.onDeviceSuggestionsUpdated(listOf(updatedSuggestedDeviceInfo))

    // The overridden state hasn't changed
    verify(listener, never()).onSuggestedDeviceStateUpdated(anyOrNull())
    assertThat(mSuggestedDeviceManager.getSuggestedDevice()).isEqualTo(connectingSuggestedState)

    // Emulate connection failure and subsequently setting the override.
    if (Flags.useSuggestedDeviceConnectionManager()) {
      val connectionFinishedCallback = captureConnectionFinishedCallback()
      connectionFinishedCallback.invoke(initialSuggestedDeviceState, false)
    }
    deviceCallback.onConnectSuggestedDeviceFinished(
      initialSuggestedDeviceState,
      false,
    )
    val failedSuggestedState =
      initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING_FAILED)
    verify(listener).onSuggestedDeviceStateUpdated(failedSuggestedState)
    clearInvocations(listener)

    // A different suggested device is set.
    deviceCallback.onDeviceSuggestionsUpdated(listOf(updatedSuggestedDeviceInfo))

    // The overridden state hasn't changed
    verify(listener, never()).onSuggestedDeviceStateUpdated(anyOrNull())
    assertThat(mSuggestedDeviceManager.getSuggestedDevice()).isEqualTo(failedSuggestedState)
  }

  @Test
  fun onDeviceSuggestionsUpdated_suggestionCleared_dispatchesNull() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1, mediaDevice2))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    // First suggestion update
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val expectedState1 = SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState1)

    // Second suggestion update with a different suggestion
    deviceCallback.onDeviceSuggestionsUpdated(listOf())
    verify(listener).onSuggestedDeviceStateUpdated(null)
  }

  @Test
  fun onDeviceListUpdate_fromNoMatchToMatchedDevice_dispatchesUpdatedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    // Initial suggestion
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val expectedState1 = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState1)

    mediaDevice1.stub { on { state } doReturn STATE_CONNECTING }

    // Device list update that now matches the suggestion
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    val expectedState2 = SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState2)
  }

  @Test
  fun onDeviceListUpdate_fromConnectingOverrideToSameStateOrDisconnected_noDispatch() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))

    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING))

    // If the matched device state is the same, override is not cleared.
    mediaDevice1.stub { on { state } doReturn STATE_CONNECTING }
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))

    // If the matched device state is STATE_DISCONNECTED, override is not cleared.
    mediaDevice1.stub { on { state } doReturn STATE_DISCONNECTED }
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))

    // Override was not cleared, therefore no callbacks are dispatched.
    verifyNoMoreInteractions(listener)
  }

  @Test
  fun onDeviceListUpdate_fromConnectingOverrideToConnected_dispatchesConnectedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))

    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    // This call sets the state to STATE_CONNECTING
    mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING))
    clearInvocations(listener)

    // Changing suggestion doesn't affect override.
    deviceCallback.onDeviceSuggestionsUpdated(listOf(null))
    verify(listener, never()).onSuggestedDeviceStateUpdated(anyOrNull())

    mediaDevice1.stub { on { state } doReturn STATE_CONNECTED }
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))

    // STATE_CONNECTED turns state to null
    verify(listener).onSuggestedDeviceStateUpdated(null)
  }

  @Test
  fun onTimeout_fromConnectingOverride_dispatchesDisconnectedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))

    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)

    // dispatches STATE_CONNECTING on connection attempt.
    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING))

    clearInvocations(listener)
    // Check the state one second before the timeout is reached.
    ShadowLooper.idleMainLooper(CONNECTING_TIMEOUT_MS - 1_000, TimeUnit.MILLISECONDS)
    verify(listener, never()).onSuggestedDeviceStateUpdated(any())

    clearInvocations(listener)
    // Check the state one second after the timeout is reached.
    ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))
  }

  @EnableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onDeviceListUpdate_fromConnectingFailedOverrideToDisconnected_sdcm_noDispatch() {
    onDeviceListUpdate_fromConnectingFailedOverrideToDisconnected_noDispatch()
  }

  @DisableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onDeviceListUpdate_fromConnectingFailedOverrideToDisconnected_lmm_noDispatch() {
    onDeviceListUpdate_fromConnectingFailedOverrideToDisconnected_noDispatch()
  }

  fun onDeviceListUpdate_fromConnectingFailedOverrideToDisconnected_noDispatch() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))

    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)

    if (Flags.useSuggestedDeviceConnectionManager()) {
      mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)
      val connectionFinishedCallback = captureConnectionFinishedCallback()
      connectionFinishedCallback.invoke(initialSuggestedDeviceState, false)
      verify(listener).onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING)
      )
    }
    deviceCallback.onConnectSuggestedDeviceFinished(initialSuggestedDeviceState, false)

    verify(listener)
      .onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING_FAILED)
      )

    mediaDevice1.stub { on { state } doReturn STATE_DISCONNECTED }
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))

    // STATE_DISCONNECTED is ignored.
    verifyNoMoreInteractions(listener)
  }

  @EnableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onTimeout_fromConnectingFailedOverride_sdcm_dispatchesNullState() {
    onTimeout_fromConnectingFailedOverride_dispatchesNullState()
  }

  @DisableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onTimeout_fromConnectingFailedOverride_lmm_dispatchesNullState() {
    onTimeout_fromConnectingFailedOverride_dispatchesNullState()
  }

  fun onTimeout_fromConnectingFailedOverride_dispatchesNullState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))

    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)

    if (Flags.useSuggestedDeviceConnectionManager()) {
      mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)
      val connectionFinishedCallback = captureConnectionFinishedCallback()
      connectionFinishedCallback.invoke(initialSuggestedDeviceState, false)
    }
    deviceCallback.onConnectSuggestedDeviceFinished(initialSuggestedDeviceState, false)

    // dispatches STATE_CONNECTING_FAILED on failed attempt.
    verify(listener)
      .onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING_FAILED)
      )

    clearInvocations(listener)
    // One second before the timeout is reached - no events are dispatched.
    ShadowLooper.idleMainLooper(CONNECTING_FAILED_TIMEOUT_MS - 1_000, TimeUnit.MILLISECONDS)
    verify(listener, never()).onSuggestedDeviceStateUpdated(any())

    clearInvocations(listener)
    // One second after the timeout is reached - clears the suggestedDeviceState.
    ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
    verify(listener).onSuggestedDeviceStateUpdated(null)

    clearInvocations(listener)
    // MediaDevice list updates don't cause the suggestedDeviceState to be restored.
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    verify(listener, never()).onSuggestedDeviceStateUpdated(any())

    // A new suggestion list doesn't cause the suggestedDeviceState to be restored.
    clearInvocations(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    verify(listener, never()).onSuggestedDeviceStateUpdated(any())

    // Requesting a new suggestion causes the suggestedDeviceState to be restored.
    clearInvocations(listener)
    mSuggestedDeviceManager.requestDeviceSuggestion()
    verify(listener)
      .onSuggestedDeviceStateUpdated(SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED))
  }

  @EnableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onConnectSuggestedDeviceFinished_failure_sdcm_dispatchesConnectingFailedState() {
    onConnectSuggestedDeviceFinished_failure_dispatchesConnectingFailedState()
  }

  @DisableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onConnectSuggestedDeviceFinished_failure_lmm_dispatchesConnectingFailedState() {
    onConnectSuggestedDeviceFinished_failure_dispatchesConnectingFailedState()
  }

  fun onConnectSuggestedDeviceFinished_failure_dispatchesConnectingFailedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    verify(listener).onSuggestedDeviceStateUpdated(initialSuggestedDeviceState)

    mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)
    val connectingState = initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING)
    verify(listener).onSuggestedDeviceStateUpdated(connectingState)

    deviceCallback.onConnectSuggestedDeviceFinished(initialSuggestedDeviceState, false)
    if (Flags.useSuggestedDeviceConnectionManager()) {
      val connectionFinishedCallback = captureConnectionFinishedCallback()
      connectionFinishedCallback.invoke(initialSuggestedDeviceState, false)
    }

    val failedState = initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING_FAILED)
    verify(listener).onSuggestedDeviceStateUpdated(failedState)
  }

  @EnableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onConnectionStarted_fromConnectingFailed_sdcm_changesStateToConnecting() {
    onConnectionStarted_fromConnectingFailed_changesStateToConnecting()
  }

  @DisableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun onConnectionStarted_fromConnectingFailed_lmm_changesStateToConnecting() {
    onConnectionStarted_fromConnectingFailed_changesStateToConnecting()
  }

  fun onConnectionStarted_fromConnectingFailed_changesStateToConnecting() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    // Simulate a failed connection first
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)

    if (Flags.useSuggestedDeviceConnectionManager()) {
      mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)
      clearInvocations(listener) // Clear a call with STATE_CONNECTING
      val connectionFinishedCallback = captureConnectionFinishedCallback()
      connectionFinishedCallback.invoke(initialSuggestedDeviceState, false)
    }
    deviceCallback.onConnectSuggestedDeviceFinished(
      initialSuggestedDeviceState,
      false,
    ) // Simulate failure

    val failedState = initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING_FAILED)
    verify(listener).onSuggestedDeviceStateUpdated(failedState)

    mSuggestedDeviceManager.connectSuggestedDevice(initialSuggestedDeviceState, routingChangeInfo)

    val expectedState = failedState.copy(connectionState = STATE_CONNECTING)
    verify(listener)
      .onSuggestedDeviceStateUpdated(expectedState) // Should be called again with connecting state
  }

  @EnableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun connectSuggestedDevice_stateMatches_sdcm_callsLocalMediaManagerConnect() {
    connectSuggestedDevice_stateMatches_callsLocalMediaManagerConnect()
  }

  @DisableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun connectSuggestedDevice_stateMatches_lmm_callsLocalMediaManagerConnect() {
    connectSuggestedDevice_stateMatches_callsLocalMediaManagerConnect()
  }

  fun connectSuggestedDevice_stateMatches_callsLocalMediaManagerConnect() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    val currentSuggestedState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    mSuggestedDeviceManager.connectSuggestedDevice(currentSuggestedState, routingChangeInfo)

    if (Flags.useSuggestedDeviceConnectionManager()) {
      verify(suggestedDeviceConnectionManager).connect(
        eq(currentSuggestedState), eq(routingChangeInfo), any()
      )
    } else {
      verify(localMediaManager).connectSuggestedDevice(currentSuggestedState, routingChangeInfo)
    }
    verify(listener)
      .onSuggestedDeviceStateUpdated(currentSuggestedState.copy(connectionState = STATE_CONNECTING))
  }

  @EnableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun connectSuggestedDevice_stateDoesNotMatch_sdcm_doesNotCallLocalMediaManagerConnect() {
    connectSuggestedDevice_stateDoesNotMatch_doesNotCallLocalMediaManagerConnect()
  }

  @DisableFlags(Flags.FLAG_USE_SUGGESTED_DEVICE_CONNECTION_MANAGER)
  @Test
  fun connectSuggestedDevice_stateDoesNotMatch_lmm_doesNotCallLocalMediaManagerConnect() {
    connectSuggestedDevice_stateDoesNotMatch_doesNotCallLocalMediaManagerConnect()
  }

  fun connectSuggestedDevice_stateDoesNotMatch_doesNotCallLocalMediaManagerConnect() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    verify(listener)
      .onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(
          suggestedDeviceInfo = suggestedDeviceInfo1,
          connectionState = STATE_DISCONNECTED,
        )
      )

    // Create a different suggested state than what's currently held by the repository
    val differentSuggestedState = SuggestedDeviceState(suggestedDeviceInfo2, STATE_DISCONNECTED)
    mSuggestedDeviceManager.connectSuggestedDevice(differentSuggestedState, routingChangeInfo)

    if (Flags.useSuggestedDeviceConnectionManager()) {
      verify(suggestedDeviceConnectionManager, never()).connect(any(), any(), any())
    } else {
      verify(localMediaManager, never()).connectSuggestedDevice(any(), any())
    }
    verifyNoMoreInteractions(listener)
  }

  /**
   * Helper to get the internal LocalMediaManager.DeviceCallback instance. This relies on the fact
   * that the callback is registered when the first listener is added.
   */
  private fun addListenerAndCaptureCallback(
    listener: SuggestedDeviceManager.Listener
  ): LocalMediaManager.DeviceCallback {
    val callbackCaptor = argumentCaptor<LocalMediaManager.DeviceCallback>()
    mSuggestedDeviceManager.addListener(listener)
    verify(localMediaManager).registerCallback(callbackCaptor.capture())
    return callbackCaptor.firstValue
  }

  private fun captureConnectionFinishedCallback(): ConnectionFinishedCallback {
    val callbackCaptor = argumentCaptor<ConnectionFinishedCallback>()
    verify(suggestedDeviceConnectionManager).connect(any(), any(), callbackCaptor.capture())
    return callbackCaptor.firstValue
  }
}
