/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.audio

import android.content.AttributionSource
import android.media.AudioDeviceAttributes.ROLE_OUTPUT
import android.media.AudioDeviceInfo
import android.media.AudioDevicePort
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioManager.MODE_RINGTONE
import android.media.AudioModeSession.ROUTING_RESULT_PREEMPTED
import android.media.AudioModeSession.ROUTING_RESULT_SUCCESSFUL
import android.media.AudioSystem
import android.media.audio.AudioModeSessionRequest
import android.media.audio.DeviceIdentity
import android.media.audio.IAudioModeSession
import android.media.audio.IAudioModeSessionCallback
import android.os.IBinder
import android.platform.test.annotations.Presubmit
import android.platform.test.ravenwood.RavenwoodRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@Presubmit
@RunWith(AndroidJUnit4::class)
class AudioModeSessionTest {

    @get:Rule val mRavenwood = RavenwoodRule()

    // Standard mock devices
    private val mSpeaker: AudioDeviceInfo =
        AudioDeviceInfo(AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_SPEAKER, "", ""))
    private val mEarpiece: AudioDeviceInfo =
        AudioDeviceInfo(AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_EARPIECE, "", ""))
    private val mWiredHeadset: AudioDeviceInfo =
        AudioDeviceInfo(
            AudioDevicePort.createForTesting(
                AudioSystem.DEVICE_OUT_WIRED_HEADSET,
                "",
                "headset_address",
            )
        )
    private val mScoHeadset: AudioDeviceInfo =
        AudioDeviceInfo(
            AudioDevicePort.createForTesting(
                AudioSystem.DEVICE_OUT_BLUETOOTH_SCO,
                "",
                "sco_address",
            )
        )

    private val mUsbHeadset: AudioDeviceInfo =
        AudioDeviceInfo(
            AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_USB_HEADSET, "", "usb_address")
        )

    private val mBleHeadset: AudioDeviceInfo =
        AudioDeviceInfo(
            AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_BLE_HEADSET, "", "ble_address")
        )

    private val mHearingAid: AudioDeviceInfo =
        AudioDeviceInfo(
            AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_HEARING_AID, "", "ha_address")
        )

    private val mAttributionSource = AttributionSource(1000, 1234, "com.test", null)
    private val mAudioService: AudioService = mock {
        on { requestAudioFocusForModeSession(any(), any(), any(), anyInt(), any()) } doReturn
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private val mCallbackBinder: IBinder = mock()
    private val mCallback: IAudioModeSessionCallback = mock {
        on { asBinder() } doReturn mCallbackBinder
    }
    private val mDeviceBroker: AudioDeviceBroker = mock()

    private fun createDefaultRequest() =
        AudioModeSessionRequest().apply {
            attributionSource = mAttributionSource.asState()
            mode = MODE_IN_COMMUNICATION
            isDisplayActiveUseCase = false
            noFocusModes = intArrayOf()
        }

    private fun createSession(
        request: AudioModeSessionRequest = createDefaultRequest()
    ): AudioModeSession {
        return AudioModeSession(mAudioService, mDeviceBroker, request, mCallback, { it.run() })
    }

    private fun createRoute(device: AudioDeviceInfo) =
        IAudioModeSession.Route().apply {
            output =
                DeviceIdentity().apply {
                    type = device.type
                    role = ROLE_OUTPUT
                    address = device.address
                }
        }

    @Test
    fun createSession_initializesState() {
        createSession()
        val inOrderService = inOrder(mAudioService)
        inOrderService
            .verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
        inOrderService.verify(mAudioService).setMode(eq(MODE_IN_COMMUNICATION), any(), any())
    }

    @Test
    fun createSession_noFocusMode_neverGainsFocus() {
        val request =
            createDefaultRequest().apply { noFocusModes = intArrayOf(MODE_IN_COMMUNICATION) }

        createSession(request)

        verify(mAudioService, never())
            .requestAudioFocusForModeSession(any(), any(), any(), any(), any())
    }

    @Test
    fun close_cleansUp() {
        val session = createSession()
        session.close()
        verify(mDeviceBroker).removeAudioModeSession(session)
        verify(mCallback).onClosed()
        val inOrderService = inOrder(mAudioService)
        inOrderService.verify(mAudioService).setMode(eq(MODE_NORMAL), any(), any())
        inOrderService
            .verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
    }

    @Test
    fun setMode_updatesService() {
        val session = createSession()
        session.setMode(MODE_RINGTONE)
        verify(mAudioService).setMode(eq(MODE_RINGTONE), any(), any())
    }

    @Test
    fun onAvailableDevicesChanged_updatesCallback() {
        val session = createSession()
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        val captor = argumentCaptor<List<IAudioModeSession.Route>>()
        verify(mCallback).onAvailableRoutesChanged(captor.capture())
        val routes = captor.firstValue
        assertThat(routes).isNotEmpty()
        val outputDevice = routes[0].output
        assertThat(outputDevice.type).isEqualTo(mSpeaker.type)
        assertThat(outputDevice.address).isEqualTo(mSpeaker.address)
    }

    @Test
    fun setRequestedRoute_success_async() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = false }
        val session = createSession(request)
        val devices = listOf(mSpeaker, mEarpiece)
        session.onAvailableDevicesChanged(devices)
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mEarpiece), eq(true), eq(true), any())
        session.onCommunicationDeviceChanged(mEarpiece)

        val route = createRoute(mSpeaker)
        val requestId = session.setRequestedRoute(route)
        assertThat(requestId).isNotEqualTo(0)

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mSpeaker)

        verify(mCallback).onRoutingResult(requestId, route, ROUTING_RESULT_SUCCESSFUL)
    }

    @Test
    fun setRequestedRoute_preempts_pending() {
        val session = createSession()
        val devices = listOf(mSpeaker, mWiredHeadset, mScoHeadset)
        session.onAvailableDevicesChanged(devices)

        val speakerRoute = createRoute(mSpeaker)
        val speakerRequestId = session.setRequestedRoute(speakerRoute)
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        val headsetRoute = createRoute(mWiredHeadset)
        val headsetRequestId = session.setRequestedRoute(headsetRoute)

        verify(mCallback).onRoutingResult(speakerRequestId, speakerRoute, ROUTING_RESULT_PREEMPTED)

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mWiredHeadset), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mWiredHeadset)
        verify(mCallback).onRoutingResult(headsetRequestId, headsetRoute, ROUTING_RESULT_SUCCESSFUL)
    }

    @Test
    fun preemptClientRoute_notifiesAndUpdates() {
        val session = createSession()
        val devices = listOf(mSpeaker, mEarpiece, mWiredHeadset)
        session.onAvailableDevicesChanged(devices)
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mWiredHeadset), eq(true), eq(true), any())

        val speakerRoute = createRoute(mSpeaker)
        session.preemptClientRoute(speakerRoute)

        val captor = argumentCaptor<Int>()
        verify(mCallback).onExternalRequestedRouteChanged(eq(speakerRoute), captor.capture())
        val requestId = captor.firstValue

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())
        session.onCommunicationDeviceChanged(mSpeaker)
        verify(mCallback).onRoutingResult(requestId, speakerRoute, ROUTING_RESULT_SUCCESSFUL)
    }

    @Test
    fun onAvailableDevicesChanged_removesUnavailableRequestedRoute() {
        val session = createSession()
        val devices = listOf(mSpeaker, mWiredHeadset, mScoHeadset)
        session.onAvailableDevicesChanged(devices)

        // Request Headset
        val wiredRoute = createRoute(mWiredHeadset)
        val requestId = session.setRequestedRoute(wiredRoute)
        assertThat(requestId).isNotEqualTo(0)

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mWiredHeadset), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mWiredHeadset)
        verify(mCallback).onRoutingResult(requestId, wiredRoute, ROUTING_RESULT_SUCCESSFUL)

        clearInvocations(mDeviceBroker)
        session.onAvailableDevicesChanged(listOf(mSpeaker, mScoHeadset))

        val captor = argumentCaptor<Int>()
        verify(mCallback).onExternalRequestedRouteChanged(eq(null), captor.capture())
        val externalRequestId = captor.lastValue

        // Verify that the requested route is cleared (set to null), so we fall to default
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mScoHeadset), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mScoHeadset)
        val scoRoute = createRoute(mScoHeadset)
        verify(mCallback).onRoutingResult(externalRequestId, scoRoute, ROUTING_RESULT_SUCCESSFUL)
    }

    @Test
    fun setClientPaused_true_pauses() {
        val session = createSession()
        val inOrderService = inOrder(mAudioService)
        session.setClientPaused(true)
        inOrderService.verify(mAudioService).setMode(eq(MODE_NORMAL), any(), any())
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(null), eq(true), eq(true), any())
        inOrderService
            .verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
        verify(mCallback).onPaused()
    }

    @Test
    fun setClientPaused_false_resumes() {
        val session = createSession()
        val devices = listOf(mSpeaker, mEarpiece)
        session.onAvailableDevicesChanged(devices)

        val speakerRoute = createRoute(mSpeaker)
        session.setRequestedRoute(speakerRoute)
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())
        clearInvocations(mDeviceBroker)
        val inOrderService = inOrder(mAudioService)

        session.setClientPaused(true)
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(null), eq(true), eq(true), any())

        session.setClientPaused(false)
        inOrderService
            .verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
        inOrderService
            .verify(mAudioService)
            .setMode(eq(MODE_IN_COMMUNICATION), any(), eq(mAttributionSource.packageName))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())
        verify(mCallback).onResumed(any())
    }

    @Test
    fun pause_releasesControl() {
        val session = createSession()
        val inOrderService = inOrder(mAudioService)
        // Pause from server side (e.g. higher priority session active)
        session.pause()

        inOrderService.verify(mAudioService).setMode(eq(MODE_NORMAL), any(), any())
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(null), eq(true), eq(true), any())
        inOrderService
            .verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
        verify(mCallback).onPaused()
    }

    @Test
    fun resume_restoresControl() {
        val session = createSession()
        // Setup initial state
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)
        clearInvocations(mDeviceBroker, mCallback)

        val route = createRoute(mSpeaker)
        session.setRequestedRoute(route)
        val inOrderService = inOrder(mAudioService)

        // Server pause
        session.pause()
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(null), eq(true), eq(true), any())
        clearInvocations(mDeviceBroker, mAudioService)

        // Server resume
        session.resume()

        inOrderService
            .verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
        inOrderService
            .verify(mAudioService)
            .setMode(eq(MODE_IN_COMMUNICATION), any(), eq(mAttributionSource.packageName))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())
        verify(mCallback).onResumed(any())
    }

    @Test
    fun onCommunicationDeviceChanged_ignores_mismatch() {
        val session = createSession()
        val devices = listOf(mSpeaker, mWiredHeadset, mScoHeadset, mEarpiece)
        session.onAvailableDevicesChanged(devices)

        val speakerRoute = createRoute(mSpeaker)
        val speakerRequestId = session.setRequestedRoute(speakerRoute)

        // Report a different device change (e.g. system switched to earpiece unexpectedly or for
        // another reason)
        session.onCommunicationDeviceChanged(mEarpiece)

        verify(mCallback, never()).onRoutingResult(eq(speakerRequestId), any(), any())

        // Now report the correct device
        session.onCommunicationDeviceChanged(mSpeaker)
        verify(mCallback).onRoutingResult(speakerRequestId, speakerRoute, ROUTING_RESULT_SUCCESSFUL)
    }

    @Test
    fun setRequestedRoute_unavailable_doesNotSetDevice() {
        val session = createSession()
        // Available devices: Speaker
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        // Request: Wired Headset (Not available)
        val route = createRoute(mWiredHeadset)
        val requestId = session.setRequestedRoute(route)
        assertThat(requestId).isNotEqualTo(0)

        // Should not apply communication device since it's unavailable
        verify(mDeviceBroker, never())
            .setCommunicationDevice(any(), any(), eq(mWiredHeadset), eq(true), any())
        // TODO: callback
    }

    @Test
    fun setMode_focusMode_requestFocus() {
        // Use a session that starts in a NO_FOCUS mode
        val request =
            createDefaultRequest().apply {
                mode = MODE_NORMAL
                noFocusModes = intArrayOf(MODE_NORMAL)
            }
        val session = createSession(request)

        // Should NOT have requested focus initially
        verify(mAudioService, never())
            .requestAudioFocusForModeSession(any(), any(), any(), any(), any())

        // Switch to IN_COMMUNICATION (should acquire focus)
        session.setMode(MODE_IN_COMMUNICATION)
        verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
    }

    @Test
    fun setMode_noFocusMode_abandonsFocus() {
        // Start in IN_COMMUNICATION (default, gains focus)
        val request = createDefaultRequest().apply { noFocusModes = intArrayOf(MODE_NORMAL) }
        val session = createSession(request)
        val inOrderService = inOrder(mAudioService)
        inOrderService
            .verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )

        // Switch to NORMAL (configured as no-focus)
        session.setMode(MODE_NORMAL)
        inOrderService
            .verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
    }

    @Test
    fun defaultRouting_prioritization_Sco() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = true }
        val session = createSession(request)

        session.onAvailableDevicesChanged(
            listOf(mSpeaker, mEarpiece, mWiredHeadset, mBleHeadset, mScoHeadset)
        )

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mScoHeadset), eq(true), eq(true), any())
    }

    @Test
    fun defaultRouting_prioritization_Le() {
        val session = createSession()

        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece, mWiredHeadset, mBleHeadset))

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mBleHeadset), eq(true), eq(true), any())
    }

    @Test
    fun defaultRouting_prioritization_Wired() {
        val session = createSession()

        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece, mWiredHeadset))

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mWiredHeadset), eq(true), eq(true), any())
    }

    @Test
    fun defaultRouting_prioritization_HearingAid() {
        val session = createSession()

        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece, mHearingAid))

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mHearingAid), eq(true), eq(true), any())
    }

    @Test
    fun defaultRouting_preferredDeviceOverride() {
        val customDeviceBroker: AudioDeviceBroker = mock {
            on { preferredCommunicationDevice } doReturn mSpeaker
        }
        val session =
            AudioModeSession(
                mAudioService,
                customDeviceBroker,
                createDefaultRequest(),
                mCallback,
                { it.run() },
            )

        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece, mHearingAid))

        // speaker is prioritized by the external pref
        verify(customDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        // Add SCO headset (priority 30). It should beat the external preference
        session.onAvailableDevicesChanged(listOf(mScoHeadset, mHearingAid, mWiredHeadset))
        verify(customDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mScoHeadset), eq(true), eq(true), any())
    }

    @Test
    fun createSession_displayActive_defaultsToSpeaker() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = true }
        val session = createSession(request)

        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mSpeaker)

        val speakerRoute = createRoute(mSpeaker)
        verify(mCallback).onRoutingResult(any(), eq(speakerRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun createSession_displayInactive_defaultsToEarpiece() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = false }
        val session = createSession(request)

        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mEarpiece), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mEarpiece)

        val earpieceRoute = createRoute(mEarpiece)
        verify(mCallback).onRoutingResult(any(), eq(earpieceRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun createSession_displayInactive_noEarpiece_defaultsToSpeaker() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = false }
        val session = createSession(request)

        // Display inactive prefers earpiece, but we only have speaker available.
        session.onAvailableDevicesChanged(listOf(mSpeaker))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())
    }

    @Test
    fun setDisplayActiveUseCase_updatesDefaultRouteAndDispatchesResult() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = false }
        val session = createSession(request)

        // Default is Earpiece
        session.onAvailableDevicesChanged(listOf(mSpeaker, mEarpiece))
        session.onCommunicationDeviceChanged(mEarpiece)
        val earpieceRoute = createRoute(mEarpiece)
        verify(mCallback).onRoutingResult(any(), eq(earpieceRoute), eq(ROUTING_RESULT_SUCCESSFUL))

        // Set display active to true, should switch to Speaker
        session.setDisplayActiveUseCase(true)

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        // Simulate broker confirming the device
        session.onCommunicationDeviceChanged(mSpeaker)

        val speakerRoute = createRoute(mSpeaker)
        verify(mCallback).onRoutingResult(any(), eq(speakerRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun preemptClientRoute_null_appliesDefaultRouting() {
        val session = createSession()
        val devices = listOf(mScoHeadset, mBleHeadset, mSpeaker, mEarpiece)
        session.onAvailableDevicesChanged(devices)

        val requestId = session.setRequestedRoute(createRoute(mBleHeadset))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mBleHeadset), eq(true), eq(true), any())
        session.onCommunicationDeviceChanged(mBleHeadset)

        val bleRoute = createRoute(mBleHeadset)
        verify(mCallback).onRoutingResult(any(), eq(bleRoute), eq(ROUTING_RESULT_SUCCESSFUL))

        clearInvocations(mDeviceBroker)

        session.preemptClientRoute(null)

        val defaultRoute = createRoute(mScoHeadset)

        val captor = argumentCaptor<Int>()
        verify(mCallback).onExternalRequestedRouteChanged(eq(null), captor.capture())
        val externalRequestId = captor.lastValue

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mScoHeadset), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mScoHeadset)

        // the routing callback result is the *actual* route
        verify(mCallback)
            .onRoutingResult(externalRequestId, defaultRoute, ROUTING_RESULT_SUCCESSFUL)
    }

    @Test
    fun defaultRoutePending_preemptedBySetRequestedRoute() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = false }
        val session = createSession(request)

        // Start the default routing
        session.onAvailableDevicesChanged(listOf(mEarpiece, mSpeaker))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mEarpiece), eq(true), eq(true), any())

        val earpieceRoute = createRoute(mEarpiece)

        // Before onCommunicationDeviceChanged, client requests speaker
        val speakerRoute = createRoute(mSpeaker)
        val requestId = session.setRequestedRoute(speakerRoute)

        verify(mCallback).onRoutingResult(any(), eq(earpieceRoute), eq(ROUTING_RESULT_PREEMPTED))

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        // Success callback for the explicit request
        session.onCommunicationDeviceChanged(mSpeaker)
        verify(mCallback)
            .onRoutingResult(eq(requestId), eq(speakerRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun onAvailableDevicesChanged_higherPriorityDeviceAvailable_updatesDefaultRoute() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = true }
        val session = createSession(request)

        // Start with speaker
        session.onAvailableDevicesChanged(listOf(mSpeaker))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())
        session.onCommunicationDeviceChanged(mSpeaker)

        val speakerRoute = createRoute(mSpeaker)
        verify(mCallback).onRoutingResult(any(), eq(speakerRoute), eq(ROUTING_RESULT_SUCCESSFUL))

        // Connect SCO
        session.onAvailableDevicesChanged(listOf(mSpeaker, mScoHeadset))

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mScoHeadset), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mScoHeadset)
        val scoRoute = createRoute(mScoHeadset)
        verify(mCallback).onRoutingResult(any(), eq(scoRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun onAvailableDevicesChanged_activeDeviceUnavailable_updatesDefaultRoute() {
        val request = createDefaultRequest().apply { isDisplayActiveUseCase = true }
        val session = createSession(request)

        // Start with speaker and SCO
        session.onAvailableDevicesChanged(listOf(mSpeaker, mScoHeadset))
        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mScoHeadset), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mScoHeadset)
        val scoRoute = createRoute(mScoHeadset)
        verify(mCallback).onRoutingResult(any(), eq(scoRoute), eq(ROUTING_RESULT_SUCCESSFUL))

        // Disconnect SCO
        session.onAvailableDevicesChanged(listOf(mSpeaker))

        verify(mDeviceBroker)
            .setCommunicationDevice(any(), any(), eq(mSpeaker), eq(true), eq(true), any())

        session.onCommunicationDeviceChanged(mSpeaker)
        val speakerRoute = createRoute(mSpeaker)
        verify(mCallback).onRoutingResult(any(), eq(speakerRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }
}
