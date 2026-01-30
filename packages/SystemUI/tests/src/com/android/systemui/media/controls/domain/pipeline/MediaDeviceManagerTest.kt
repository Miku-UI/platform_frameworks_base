/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.controls.domain.pipeline

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.TestStubDrawable
import android.media.MediaRoute2Info
import android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER
import android.media.MediaRouter2Manager
import android.media.RoutingSessionInfo
import android.media.SuggestedDeviceInfo
import android.media.session.MediaController
import android.media.session.MediaController.PlaybackInfo
import android.media.session.MediaSession
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager
import com.android.settingslib.flags.Flags
import com.android.settingslib.media.LocalMediaManager
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED
import com.android.settingslib.media.MediaDevice
import com.android.settingslib.media.SuggestedDeviceManager
import com.android.settingslib.media.SuggestedDeviceState
import com.android.systemui.Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI
import com.android.systemui.Flags.enableSuggestedDeviceUi
import com.android.systemui.SysuiTestCase
import com.android.systemui.media.controls.MediaTestUtils
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDeviceData
import com.android.systemui.media.controls.shared.model.SuggestionData
import com.android.systemui.media.controls.util.LocalMediaManagerFactory
import com.android.systemui.media.controls.util.MediaControllerFactory
import com.android.systemui.media.controls.util.SuggestedDeviceManagerFactory
import com.android.systemui.media.muteawait.MediaMuteAwaitConnectionManager
import com.android.systemui.media.muteawait.MediaMuteAwaitConnectionManagerFactory
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

private const val KEY = "TEST_KEY"
private const val KEY_OLD = "TEST_KEY_OLD"
private const val PACKAGE = "PKG"
private const val SESSION_KEY = "SESSION_KEY"
private const val DEVICE_ID = "DEVICE_ID"
private const val DEVICE_NAME = "DEVICE_NAME"
private const val REMOTE_DEVICE_NAME = "REMOTE_DEVICE_NAME"
private const val SUGGESTED_DEVICE_NAME = "SUGGESTED_DEVICE_NAME"
private const val SUGGESTED_DEVICE_CONNECTION_STATE_1 = STATE_DISCONNECTED
private const val SUGGESTED_DEVICE_CONNECTION_STATE_2 = STATE_CONNECTING

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper
public class MediaDeviceManagerTest(flags: FlagsParameterization) : SysuiTestCase() {

    private companion object {
        val OTHER_DEVICE_ICON_STUB = TestStubDrawable()

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.progressionOf(FLAG_ENABLE_SUGGESTED_DEVICE_UI)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var manager: MediaDeviceManager
    @Mock private lateinit var controllerFactory: MediaControllerFactory
    @Mock private lateinit var lmmFactory: LocalMediaManagerFactory
    @Mock private lateinit var lmm: LocalMediaManager
    @Mock private lateinit var sdmFactory: SuggestedDeviceManagerFactory
    @Mock private lateinit var sdm: SuggestedDeviceManager
    @Mock private lateinit var mr2: MediaRouter2Manager
    @Mock private lateinit var muteAwaitFactory: MediaMuteAwaitConnectionManagerFactory
    @Mock private lateinit var muteAwaitManager: MediaMuteAwaitConnectionManager
    private lateinit var fakeFgExecutor: FakeExecutor
    private lateinit var fakeBgExecutor: FakeExecutor
    @Mock private lateinit var listener: MediaDeviceManager.Listener
    @Mock private lateinit var device: MediaDevice
    @Mock private lateinit var icon: Drawable
    @Mock private lateinit var routingSession: RoutingSessionInfo
    @Mock private lateinit var selectedRoute: MediaRoute2Info
    @Mock private lateinit var controller: MediaController
    @Mock private lateinit var playbackInfo: PlaybackInfo
    @Mock private lateinit var configurationController: ConfigurationController
    @Mock private lateinit var localBluetoothProfileManager: LocalBluetoothProfileManager
    @Mock private lateinit var localBluetoothLeBroadcast: LocalBluetoothLeBroadcast
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var applicationInfo: ApplicationInfo
    private lateinit var suggestedDeviceState1: SuggestedDeviceState
    private lateinit var suggestedDeviceState2: SuggestedDeviceState
    private lateinit var suggestedDeviceInfo: SuggestedDeviceInfo
    private lateinit var localBluetoothManager: LocalBluetoothManager
    private lateinit var session: MediaSession
    private lateinit var mediaData: MediaData
    @JvmField @Rule val mockito = MockitoJUnit.rule()

    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        fakeFgExecutor = FakeExecutor(FakeSystemClock())
        fakeBgExecutor = FakeExecutor(FakeSystemClock())
        localBluetoothManager = mDependency.injectMockDependency(LocalBluetoothManager::class.java)
        manager =
            MediaDeviceManager(
                context,
                controllerFactory,
                lmmFactory,
                sdmFactory,
                { mr2 },
                muteAwaitFactory,
                configurationController,
                { localBluetoothManager },
                fakeFgExecutor,
                fakeBgExecutor,
                kosmos.mediaDeviceLogger,
            )
        manager.addListener(listener)

        // Configure mocks.
        whenever(device.name).thenReturn(DEVICE_NAME)
        whenever(device.iconWithoutBackground).thenReturn(icon)
        whenever(lmmFactory.create(PACKAGE)).thenReturn(lmm)
        whenever(sdmFactory.create(lmm)).thenReturn(sdm)
        whenever(muteAwaitFactory.create(lmm)).thenReturn(muteAwaitManager)
        whenever(lmm.getCurrentConnectedDevice()).thenReturn(device)
        whenever(mr2.getRoutingSessionForMediaController(any())).thenReturn(routingSession)
        whenever(localBluetoothManager.profileManager).thenReturn(localBluetoothProfileManager)
        whenever(localBluetoothProfileManager.leAudioBroadcastProfile)
            .thenReturn(localBluetoothLeBroadcast)
        whenever(localBluetoothLeBroadcast.isEnabled(null)).thenReturn(false)
        suggestedDeviceInfo =
            SuggestedDeviceInfo.Builder(SUGGESTED_DEVICE_NAME, DEVICE_ID, TYPE_REMOTE_SPEAKER)
                .build()
        suggestedDeviceState1 =
            SuggestedDeviceState(
                suggestedDeviceInfo = suggestedDeviceInfo,
                connectionState = SUGGESTED_DEVICE_CONNECTION_STATE_1,
            )
        suggestedDeviceState2 =
            SuggestedDeviceState(
                suggestedDeviceInfo = suggestedDeviceInfo,
                connectionState = SUGGESTED_DEVICE_CONNECTION_STATE_2,
            )
        // A drawable for TYPE_REMOTE_SPEAKER, mocks return from SuggestedDeviceState#getIcon
        context.orCreateTestableResources.addOverride(
            com.android.settingslib.R.drawable.ic_media_speaker_device,
            icon,
        )
        whenever(sdm.getSuggestedDevice()).thenReturn(suggestedDeviceState1)

        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_LOCAL)
        whenever(controller.playbackInfo).thenReturn(playbackInfo)

        // Create a media sesssion and notification for testing.
        session = MediaSession(context, SESSION_KEY)

        mediaData =
            MediaTestUtils.emptyMediaData.copy(packageName = PACKAGE, token = session.sessionToken)
        whenever(controllerFactory.create(session.sessionToken)).thenReturn(controller)

        context.orCreateTestableResources.addOverride(
            R.drawable.ic_media_home_devices,
            OTHER_DEVICE_ICON_STUB,
        )
    }

    @After
    fun tearDown() {
        session.release()
    }

    @Test
    fun removeUnknown() {
        manager.onMediaDataRemoved("unknown", false)
        verify(listener, never()).onKeyRemoved(eq(KEY), any())
    }

    @Test
    fun loadMediaData() {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        verify(lmmFactory).create(PACKAGE)
    }

    @Test
    fun loadAndRemoveMediaData() {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        manager.onMediaDataRemoved(KEY, false)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        verify(lmm).unregisterCallback(any())
        verify(sdm).removeListener(any())
        verify(muteAwaitManager).stopListening()
    }

    @Test
    fun loadMediaDataWithNullToken() {
        manager.onMediaDataLoaded(KEY, null, mediaData.copy(token = null))
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        val data = captureDeviceDataFromCombinedCallback(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
    }

    @Test
    fun loadWithNewKey() {
        // GIVEN that media data has been loaded with an old key
        manager.onMediaDataLoaded(KEY_OLD, null, mediaData)
        reset(listener)
        // WHEN data is loaded with a new key
        manager.onMediaDataLoaded(KEY, KEY_OLD, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        // THEN the listener for the old key should removed.
        verify(lmm).unregisterCallback(any())
        verify(sdm).removeListener(any())
        verify(muteAwaitManager).stopListening()
        // AND a new device event emitted
        val data = captureDeviceDataFromCombinedCallback(KEY, KEY_OLD)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
    }

    @Test
    fun newKeySameAsOldKey() {
        // GIVEN that media data has been loaded
        manager.onMediaDataLoaded(KEY, null, mediaData)
        reset(listener)
        // WHEN the new key is the same as the old key
        manager.onMediaDataLoaded(KEY, KEY, mediaData)
        // THEN no event should be emitted
        verify(listener, never()).onMediaDeviceChanged(eq(KEY), eq(null), any())
    }

    @Test
    fun unknownOldKey() {
        val oldKey = "unknown"
        manager.onMediaDataLoaded(KEY, oldKey, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        if (enableSuggestedDeviceUi()) {
            verify(listener)
                .onMediaDeviceAndSuggestionDataChanged(eq(KEY), eq(oldKey), any(), any())
        } else {
            verify(listener).onMediaDeviceChanged(eq(KEY), eq(oldKey), any())
        }
    }

    @Test
    fun updateToSessionTokenWithNullRoute() {
        // GIVEN that media data has been loaded with a null token
        manager.onMediaDataLoaded(KEY, null, mediaData.copy(token = null))
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        reset(listener)
        // WHEN media data is loaded with a different token
        // AND that token results in a null route
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        whenever(mr2.getRoutingSessionForMediaController(any())).thenReturn(null)
        val data = loadMediaAndCaptureDeviceData()

        // THEN the device should be disabled
        assertThat(data.enabled).isFalse()
    }

    @Test
    fun deviceEventOnAddNotification() {
        // WHEN a notification is added
        // THEN the update is dispatched to the listener
        val data = loadMediaAndCaptureDeviceData()
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
        assertThat(data.icon).isEqualTo(icon)
    }

    @Test
    fun removeListener() {
        // WHEN a listener is removed
        manager.removeListener(listener)
        // THEN it doesn't receive device events
        manager.onMediaDataLoaded(KEY, null, mediaData)
        verify(listener, never()).onMediaDeviceChanged(eq(KEY), eq(null), any())
    }

    @Test
    fun deviceListUpdate() {
        whenever(lmm.getCurrentConnectedDevice()).thenReturn(null)
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        reset(listener)
        val deviceCallback = captureCallback()
        verify(muteAwaitManager).startListening()
        // WHEN the device list changes
        whenever(lmm.getCurrentConnectedDevice()).thenReturn(device)
        deviceCallback.onDeviceListUpdate(mutableListOf(device))
        assertThat(fakeBgExecutor.runAllReady()).isEqualTo(1)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)
        // THEN the update is dispatched to the listener
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
        assertThat(data.icon).isEqualTo(icon)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    fun onMediaDataLoaded() {
        // Verify that on media data loaded, fgExecutor only runs once and listener notified
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)

        val mediaDeviceCaptor = argumentCaptor<MediaDeviceData>()
        val suggestionCaptor = argumentCaptor<SuggestionData>()
        verify(listener)
            .onMediaDeviceAndSuggestionDataChanged(
                eq(KEY),
                eq(null),
                mediaDeviceCaptor.capture(),
                suggestionCaptor.capture(),
            )
        val deviceData = mediaDeviceCaptor.firstValue
        assertThat(deviceData.enabled).isTrue()
        assertThat(deviceData.name).isEqualTo(DEVICE_NAME)
        assertThat(deviceData.icon).isEqualTo(icon)
        val suggestionData = suggestionCaptor.firstValue.suggestedMediaDeviceData!!
        assertThat(suggestionData.name).isEqualTo(SUGGESTED_DEVICE_NAME)
        assertThat(suggestionData.connectionState).isEqualTo(SUGGESTED_DEVICE_CONNECTION_STATE_1)
        assertThat(suggestionData.icon).isEqualTo(icon)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    fun onMediaDataLoaded_resumption_doesNotLoadSuggestionData() {
        val resumptionMediaData = mediaData.copy(resumption = true)

        manager.onMediaDataLoaded(KEY, null, resumptionMediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()

        val mediaDeviceCaptor = argumentCaptor<MediaDeviceData>()
        val suggestionCaptor = argumentCaptor<SuggestionData>()
        verify(listener)
            .onMediaDeviceAndSuggestionDataChanged(
                eq(KEY),
                eq(null),
                mediaDeviceCaptor.capture(),
                suggestionCaptor.capture(),
            )
        val deviceData = mediaDeviceCaptor.firstValue
        assertThat(deviceData.enabled).isTrue()
        assertThat(deviceData.name).isEqualTo(DEVICE_NAME)
        assertThat(deviceData.icon).isEqualTo(icon)
        val suggestionData = suggestionCaptor.firstValue
        assertThat(suggestionData.suggestedMediaDeviceData).isNull()
        // The onSuggestionSpaceVisible should be set.
        assertThat(suggestionData.onSuggestionSpaceVisible).isNotNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    fun suggestedDeviceUpdate() {
        // Need to load media data to load LocalMediaManager the first time
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        clearInvocations(listener)
        val suggestedDeviceListener = captureSuggestedDeviceListener()
        // WHEN the device list changes
        suggestedDeviceListener.onSuggestedDeviceStateUpdated(suggestedDeviceState2)
        assertThat(fakeBgExecutor.runAllReady()).isEqualTo(1)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)
        // THEN the update is dispatched to the listener
        val data = captureSuggestionData(KEY).suggestedMediaDeviceData!!
        assertThat(data.name).isEqualTo(SUGGESTED_DEVICE_NAME)
        assertThat(data.connectionState).isEqualTo(SUGGESTED_DEVICE_CONNECTION_STATE_2)
        assertThat(data.icon).isEqualTo(icon)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    fun suggestedDeviceUpdateWithRepeatedValue() {
        // Need to load media data to load LocalMediaManager the first time
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        clearInvocations(listener)
        val suggestedDeviceListener = captureSuggestedDeviceListener()
        // Load initial suggestion data
        suggestedDeviceListener.onSuggestedDeviceStateUpdated(suggestedDeviceState2)
        assertThat(fakeBgExecutor.runAllReady()).isEqualTo(1)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)
        clearInvocations(listener)
        // Reload device suggestion and verify no work on foreground
        suggestedDeviceListener.onSuggestedDeviceStateUpdated(suggestedDeviceState2)
        assertThat(fakeBgExecutor.runAllReady()).isEqualTo(1)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(0)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    fun suggestedDeviceInitialization_resumption_doesNotAddListener() {
        val resumptionMediaData = mediaData.copy(resumption = true)
        manager.onMediaDataLoaded(KEY, null, resumptionMediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        verify(sdm, never()).addListener(any())
    }

    @Test
    fun selectedDeviceStateChanged() {
        whenever(lmm.getCurrentConnectedDevice()).thenReturn(null)
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        reset(listener)
        val deviceCallback = captureCallback()
        // WHEN the selected device changes state
        whenever(lmm.getCurrentConnectedDevice()).thenReturn(device)
        deviceCallback.onSelectedDeviceStateChanged(device, STATE_CONNECTING)
        assertThat(fakeBgExecutor.runAllReady()).isEqualTo(1)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)
        // THEN the update is dispatched to the listener
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
        assertThat(data.icon).isEqualTo(icon)
    }

    @Test
    fun onAboutToConnectDeviceAdded_findsDeviceInfoFromAddress() {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        // Run and reset the executors and listeners so we only focus on new events.
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        reset(listener)

        // Ensure we'll get device info when using the address
        val fullMediaDevice = mock<MediaDevice>()
        val address = "fakeAddress"
        val nameFromDevice = "nameFromDevice"
        val iconFromDevice = mock<Drawable>()
        whenever(lmm.getMediaDeviceById(eq(address))).thenReturn(fullMediaDevice)
        whenever(fullMediaDevice.name).thenReturn(nameFromDevice)
        whenever(fullMediaDevice.iconWithoutBackground).thenReturn(iconFromDevice)

        // WHEN the about-to-connect device changes to non-null
        val deviceCallback = captureCallback()
        val nameFromParam = "nameFromParam"
        val iconFromParam = mock<Drawable>()
        deviceCallback.onAboutToConnectDeviceAdded(address, nameFromParam, iconFromParam)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)

        // THEN the about-to-connect device based on the address is returned
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(nameFromDevice)
        assertThat(data.name).isNotEqualTo(nameFromParam)
        assertThat(data.icon).isEqualTo(iconFromDevice)
        assertThat(data.icon).isNotEqualTo(iconFromParam)
    }

    @Test
    fun onAboutToConnectDeviceAdded_cantFindDeviceInfoFromAddress() {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        // Run and reset the executors and listeners so we only focus on new events.
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        reset(listener)

        // Ensure we can't get device info based on the address
        val address = "fakeAddress"
        whenever(lmm.getMediaDeviceById(eq(address))).thenReturn(null)

        // WHEN the about-to-connect device changes to non-null
        val deviceCallback = captureCallback()
        val name = "AboutToConnectDeviceName"
        val mockIcon = mock<Drawable>()
        deviceCallback.onAboutToConnectDeviceAdded(address, name, mockIcon)
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)

        // THEN the about-to-connect device based on the parameters is returned
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(name)
        assertThat(data.icon).isEqualTo(mockIcon)
    }

    @Test
    fun onAboutToConnectDeviceAddedThenRemoved_usesNormalDevice() {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        val deviceCallback = captureCallback()
        // First set a non-null about-to-connect device
        deviceCallback.onAboutToConnectDeviceAdded(
            "fakeAddress",
            "AboutToConnectDeviceName",
            mock<Drawable>(),
        )
        // Run and reset the executors and listeners so we only focus on new events.
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        reset(listener)

        // WHEN hasDevice switches to false
        deviceCallback.onAboutToConnectDeviceRemoved()
        assertThat(fakeFgExecutor.runAllReady()).isEqualTo(1)
        // THEN the normal device is returned
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
        assertThat(data.icon).isEqualTo(icon)
    }

    @Test
    fun listenerReceivesKeyRemoved() {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        // WHEN the notification is removed
        manager.onMediaDataRemoved(KEY, true)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        // THEN the listener receives key removed event
        verify(listener).onKeyRemoved(eq(KEY), eq(true))
    }

    @Test
    fun onMediaDataLoaded_withRemotePlaybackType_usesNonNullRoutingSessionName() {
        // GIVEN that MR2Manager returns a valid routing session
        whenever(routingSession.name).thenReturn(REMOTE_DEVICE_NAME)
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        // WHEN a notification is added
        // THEN it uses the route name (instead of device name)
        val data = loadMediaAndCaptureDeviceData()
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(REMOTE_DEVICE_NAME)
    }

    @Test
    fun onMediaDataLoaded_withRemotePlaybackType_usesNonNullRoutingSessionName_drawableReused() {
        whenever(routingSession.name).thenReturn(REMOTE_DEVICE_NAME)
        whenever(routingSession.selectedRoutes).thenReturn(listOf("selectedRoute", "selectedRoute"))
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)

        val firstData = loadMediaAndCaptureDeviceData()
        reset(listener)
        val secondData = loadMediaAndCaptureDeviceData()

        assertThat(secondData.icon).isEqualTo(firstData.icon)
    }

    @Test
    fun onMediaDataLoaded_withRemotePlaybackInfo_noMatchingRoutingSession_returnsOtherDevice() {
        // GIVEN that MR2Manager returns null for routing session
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        whenever(mr2.getRoutingSessionForMediaController(any())).thenReturn(null)
        // WHEN a notification is added
        // THEN the device is disabled and name and icon are set to "OTHER DEVICE".
        val data = loadMediaAndCaptureDeviceData()
        assertThat(data.enabled).isFalse()
        assertThat(data.name).isEqualTo(context.getString(R.string.media_seamless_other_device))
    }

    @Test
    fun onMediaDataLoaded_withRemotePlaybackInfo_noMatchingRoutingSession() {
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        whenever(mr2.getRoutingSessionForMediaController(any())).thenReturn(null)
        context.orCreateTestableResources.removeOverride(R.drawable.ic_media_home_devices)

        val firstData = loadMediaAndCaptureDeviceData()
        reset(listener)
        val secondData = loadMediaAndCaptureDeviceData()

        assertThat(secondData.icon).isEqualTo(firstData.icon)
    }

    @Test
    fun onSelectedDeviceStateChanged_withRemotePlaybackInfo_noMatchingRoutingSession_returnOtherDevice() {
        // GIVEN a notif is added
        loadMediaAndCaptureDeviceData()
        reset(listener)
        // AND MR2Manager returns null for routing session
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        whenever(mr2.getRoutingSessionForMediaController(any())).thenReturn(null)
        // WHEN the selected device changes state
        val deviceCallback = captureCallback()
        deviceCallback.onSelectedDeviceStateChanged(device, STATE_CONNECTING)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        // THEN the device is disabled and name and icon are set to "OTHER DEVICE".
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isFalse()
        assertThat(data.name).isEqualTo(context.getString(R.string.media_seamless_other_device))
        assertThat(data.icon).isEqualTo(OTHER_DEVICE_ICON_STUB)
    }

    @Test
    fun onDeviceListUpdate_withRemotePlaybackInfo_noMatchingRoutingSession_returnsOtherDevice() {
        // GIVEN a notif is added
        loadMediaAndCaptureDeviceData()
        reset(listener)
        // GIVEN that MR2Manager returns null for routing session
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        whenever(mr2.getRoutingSessionForMediaController(any())).thenReturn(null)
        // WHEN the selected device changes state
        val deviceCallback = captureCallback()
        deviceCallback.onDeviceListUpdate(mutableListOf(device))
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        // THEN device is disabled and name and icon are set to "OTHER DEVICE".
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isFalse()
        assertThat(data.name).isEqualTo(context.getString(R.string.media_seamless_other_device))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING)
    fun onDeviceListUpdate_withBroadcastOnAndRemotePlaybackType_usesNonNullRoutingSessionName() {
        // GIVEN a notif is added
        loadMediaAndCaptureDeviceData()
        reset(listener)
        // GIVEN that MR2Manager returns a valid routing session
        whenever(routingSession.name).thenReturn(REMOTE_DEVICE_NAME)
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        // GIVEN that broadcast is on
        whenever(localBluetoothLeBroadcast.isEnabled(null)).thenReturn(true)
        // WHEN the selected device changes state
        val deviceCallback = captureCallback()
        deviceCallback.onDeviceListUpdate(mutableListOf(device))
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        // THEN device is enabled and name and icon are set to remote device name.
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(REMOTE_DEVICE_NAME)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_LE_AUDIO_SHARING,
        com.android.media.flags.Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING,
    )
    fun onDeviceListUpdate_withBroadcastOn_returnsBroadcastDevice() {
        // GIVEN a notif is added
        loadMediaAndCaptureDeviceData()
        reset(listener)
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_LOCAL)
        // GIVEN that broadcast is on
        whenever(localBluetoothLeBroadcast.isEnabled(null)).thenReturn(true)
        // WHEN the selected device changes state
        val deviceCallback = captureCallback()
        deviceCallback.onDeviceListUpdate(mutableListOf(device))
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        // THEN device is enabled and name and icon are set to "Sharing audio".
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isTrue()
        assertThat(data.name).isEqualTo(context.getString(R.string.audio_sharing_description))
    }

    @Test
    fun mr2ReturnsNonSystemRouteWithNullName_useLocalDeviceName() {
        // GIVEN that MR2Manager returns a routing session that does not have a name
        whenever(routingSession.name).thenReturn(null)
        whenever(routingSession.isSystemSession).thenReturn(false)
        // WHEN a notification is added
        // THEN the device is enabled and uses the current connected device name
        val data = loadMediaAndCaptureDeviceData()
        assertThat(data.name).isEqualTo(DEVICE_NAME)
        assertThat(data.enabled).isTrue()
    }

    @Test
    fun audioInfoPlaybackTypeChanged() {
        whenever(playbackInfo.getPlaybackType()).thenReturn(PlaybackInfo.PLAYBACK_TYPE_LOCAL)
        whenever(controller.getPlaybackInfo()).thenReturn(playbackInfo)
        // GIVEN a controller with local playback type
        loadMediaAndCaptureDeviceData()
        reset(mr2)
        // WHEN onAudioInfoChanged fires with remote playback type
        whenever(playbackInfo.getPlaybackType()).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        val captor = argumentCaptor<MediaController.Callback>()
        verify(controller).registerCallback(captor.capture())
        captor.firstValue.onAudioInfoChanged(playbackInfo)
        // THEN the route is checked
        verify(mr2).getRoutingSessionForMediaController(eq(controller))
    }

    @Test
    fun onAudioInfoChanged_withRemotePlaybackInfo_queriesRoutingSession() {
        whenever(playbackInfo.getPlaybackType()).thenReturn(PlaybackInfo.PLAYBACK_TYPE_LOCAL)
        whenever(playbackInfo.getVolumeControlId()).thenReturn(null)
        whenever(controller.getPlaybackInfo()).thenReturn(playbackInfo)
        // GIVEN a controller with local playback type
        loadMediaAndCaptureDeviceData()
        reset(mr2)
        // WHEN onAudioInfoChanged fires with a volume control id change
        whenever(playbackInfo.getVolumeControlId()).thenReturn("placeholder id")
        whenever(playbackInfo.playbackType).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        val captor = argumentCaptor<MediaController.Callback>()
        verify(controller).registerCallback(captor.capture())
        captor.firstValue.onAudioInfoChanged(playbackInfo)
        // THEN the routing session is checked
        verify(mr2).getRoutingSessionForMediaController(eq(controller))
    }

    @Test
    fun audioInfoHasntChanged() {
        whenever(playbackInfo.getPlaybackType()).thenReturn(PlaybackInfo.PLAYBACK_TYPE_REMOTE)
        whenever(controller.getPlaybackInfo()).thenReturn(playbackInfo)
        // GIVEN a controller with remote playback type
        loadMediaAndCaptureDeviceData()
        reset(mr2)
        // WHEN onAudioInfoChanged fires with remote playback type
        val captor = argumentCaptor<MediaController.Callback>()
        verify(controller).registerCallback(captor.capture())
        captor.firstValue.onAudioInfoChanged(playbackInfo)
        // THEN the route is not checked
        verify(mr2, never()).getRoutingSessionForMediaController(eq(controller))
    }

    @Test
    fun deviceIdChanged_informListener() {
        // GIVEN a notification is added, with a particular device connected
        whenever(device.id).thenReturn(DEVICE_ID)
        loadMediaAndCaptureDeviceData()

        // and later the manager gets a new device ID
        val deviceCallback = captureCallback()
        val updatedId = DEVICE_ID + "_new"
        whenever(device.id).thenReturn(updatedId)
        deviceCallback.onDeviceListUpdate(mutableListOf(device))

        // THEN the listener gets the updated info
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()

        val dataCaptor = argumentCaptor<MediaDeviceData>()
        val firstDevice =
            if (enableSuggestedDeviceUi()) {
                verify(listener)
                    .onMediaDeviceAndSuggestionDataChanged(
                        eq(KEY),
                        anyOrNull(),
                        dataCaptor.capture(),
                        any(),
                    )
                dataCaptor.firstValue
            } else {
                verify(listener, times(2))
                    .onMediaDeviceChanged(eq(KEY), anyOrNull(), dataCaptor.capture())
                dataCaptor.allValues.get(0)
            }

        val secondDevice =
            if (enableSuggestedDeviceUi()) {
                captureDeviceData(KEY)
            } else {
                dataCaptor.allValues.get(1)
            }

        assertThat(firstDevice.id).isEqualTo(DEVICE_ID)
        assertThat(secondDevice.id).isEqualTo(updatedId)
    }

    @Test
    fun deviceNameChanged_informListener() {
        // GIVEN a notification is added, with a particular device connected
        whenever(device.id).thenReturn(DEVICE_ID)
        whenever(device.name).thenReturn(DEVICE_NAME)
        loadMediaAndCaptureDeviceData()

        // and later the manager gets a new device name
        val deviceCallback = captureCallback()
        val updatedName = DEVICE_NAME + "_new"
        whenever(device.name).thenReturn(updatedName)
        deviceCallback.onDeviceListUpdate(mutableListOf(device))

        // THEN the listener gets the updated info
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()

        val dataCaptor = argumentCaptor<MediaDeviceData>()
        val firstDevice =
            if (enableSuggestedDeviceUi()) {
                verify(listener)
                    .onMediaDeviceAndSuggestionDataChanged(
                        eq(KEY),
                        anyOrNull(),
                        dataCaptor.capture(),
                        any(),
                    )
                dataCaptor.firstValue
            } else {
                verify(listener, times(2))
                    .onMediaDeviceChanged(eq(KEY), anyOrNull(), dataCaptor.capture())
                dataCaptor.allValues.get(0)
            }

        val secondDevice =
            if (enableSuggestedDeviceUi()) {
                captureDeviceData(KEY)
            } else {
                dataCaptor.allValues.get(1)
            }

        assertThat(firstDevice.name).isEqualTo(DEVICE_NAME)
        assertThat(secondDevice.name).isEqualTo(updatedName)
    }

    @Test
    fun deviceIconChanged_doesNotCallListener() {
        // GIVEN a notification is added, with a particular device connected
        whenever(device.id).thenReturn(DEVICE_ID)
        whenever(device.name).thenReturn(DEVICE_NAME)
        val firstIcon = mock<Drawable>()
        whenever(device.icon).thenReturn(firstIcon)

        loadMediaAndCaptureDeviceData()
        // Run anything in progress to not conflate with later interactions
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        clearInvocations(listener)

        // and later the manager gets a callback with only the icon changed
        val deviceCallback = captureCallback()
        val secondIcon = mock<Drawable>()
        whenever(device.icon).thenReturn(secondIcon)
        deviceCallback.onDeviceListUpdate(mutableListOf(device))

        // THEN the listener is not called again
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun testRemotePlaybackDeviceOverride() {
        whenever(routingSession.name).thenReturn(DEVICE_NAME)
        val deviceData = MediaDeviceData(false, null, REMOTE_DEVICE_NAME, null)
        val mediaDataWithDevice = mediaData.copy(device = deviceData)

        // GIVEN media data that already has a device set
        manager.onMediaDataLoaded(KEY, null, mediaDataWithDevice)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()

        // THEN we keep the device info, and don't register a listener
        val data = captureDeviceData(KEY)
        assertThat(data.enabled).isFalse()
        assertThat(data.name).isEqualTo(REMOTE_DEVICE_NAME)
        verify(lmm, never()).registerCallback(any())
    }

    private fun captureCallback(): LocalMediaManager.DeviceCallback {
        val captor = argumentCaptor<LocalMediaManager.DeviceCallback>()
        verify(lmm).registerCallback(captor.capture())
        return captor.firstValue
    }

    private fun captureSuggestedDeviceListener(): SuggestedDeviceManager.Listener {
        val captor = argumentCaptor<SuggestedDeviceManager.Listener>()
        verify(sdm).addListener(captor.capture())
        return captor.firstValue
    }

    private fun captureDeviceDataFromCombinedCallback(
        key: String,
        oldKey: String? = null,
    ): MediaDeviceData {
        if (!enableSuggestedDeviceUi()) {
            return captureDeviceData(key, oldKey)
        }

        val captor = argumentCaptor<MediaDeviceData>()
        verify(listener)
            .onMediaDeviceAndSuggestionDataChanged(eq(key), eq(oldKey), captor.capture(), any())
        verify(listener, never()).onMediaDeviceChanged(eq(key), eq(oldKey), any())
        verify(listener, never()).onSuggestionDataChanged(eq(key), eq(oldKey), any())
        return captor.firstValue
    }

    private fun captureDeviceData(key: String, oldKey: String? = null): MediaDeviceData {
        val captor = argumentCaptor<MediaDeviceData>()
        verify(listener).onMediaDeviceChanged(eq(key), eq(oldKey), captor.capture())
        return captor.firstValue
    }

    private fun captureSuggestionData(key: String, oldKey: String? = null): SuggestionData {
        val captor = argumentCaptor<SuggestionData>()
        verify(listener).onSuggestionDataChanged(eq(key), eq(oldKey), captor.capture())
        return captor.firstValue
    }

    private fun loadMediaAndCaptureDeviceData(): MediaDeviceData {
        manager.onMediaDataLoaded(KEY, null, mediaData)
        fakeBgExecutor.runAllReady()
        fakeFgExecutor.runAllReady()

        return captureDeviceDataFromCombinedCallback(KEY)
    }
}
