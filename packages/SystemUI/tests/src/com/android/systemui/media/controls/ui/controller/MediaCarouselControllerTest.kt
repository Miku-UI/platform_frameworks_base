/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.media.controls.ui.controller

import android.app.PendingIntent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.LocaleList
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import android.testing.TestableLooper
import android.util.MathUtils.abs
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.media.controls.MediaTestUtils
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QS
import com.android.systemui.media.controls.ui.view.MediaHostState
import com.android.systemui.media.controls.ui.view.MediaScrollView
import com.android.systemui.media.controls.util.MediaUiEventLogger
import com.android.systemui.media.remedia.shared.flag.MediaControlsInComposeFlag
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.PageIndicator
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.statusbar.featurepods.media.domain.interactor.mediaControlChipInteractor
import com.android.systemui.statusbar.notification.collection.provider.OnReorderingAllowedListener
import com.android.systemui.statusbar.notification.collection.provider.VisualStabilityProvider
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.fakeSettings
import com.android.systemui.util.time.FakeSystemClock
import java.util.Locale
import javax.inject.Provider
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.floatThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq

private val DATA = MediaTestUtils.emptyMediaData

private const val PAUSED_LOCAL = "paused local"
private const val PLAYING_LOCAL = "playing local"

@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
@DisableSceneContainer
@DisableFlags(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE)
class MediaCarouselControllerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testDispatcher = kosmos.testDispatcher
    private val secureSettings = kosmos.fakeSettings

    @Mock lateinit var mediaControlPanelFactory: Provider<MediaControlPanel>
    @Mock lateinit var panel: MediaControlPanel
    @Mock lateinit var visualStabilityProvider: VisualStabilityProvider
    @Mock lateinit var mediaHostStatesManager: MediaHostStatesManager
    @Mock lateinit var mediaHostState: MediaHostState
    @Mock lateinit var activityStarter: ActivityStarter
    @Mock lateinit var mediaDataManager: MediaDataManager
    @Mock lateinit var configurationController: ConfigurationController
    @Mock lateinit var falsingManager: FalsingManager
    @Mock lateinit var dumpManager: DumpManager
    @Mock lateinit var logger: MediaUiEventLogger
    @Mock lateinit var debugLogger: MediaCarouselControllerLogger
    @Mock lateinit var mediaViewController: MediaViewController
    @Mock lateinit var mediaCarousel: MediaScrollView
    @Mock lateinit var pageIndicator: PageIndicator
    @Mock lateinit var keyguardUpdateMonitor: KeyguardUpdateMonitor
    @Mock lateinit var globalSettings: GlobalSettings
    private val transitionRepository = kosmos.fakeKeyguardTransitionRepository
    @Captor lateinit var listener: ArgumentCaptor<MediaDataManager.Listener>
    @Captor
    lateinit var configListener: ArgumentCaptor<ConfigurationController.ConfigurationListener>
    @Captor lateinit var visualStabilityCallback: ArgumentCaptor<OnReorderingAllowedListener>
    @Captor lateinit var keyguardCallback: ArgumentCaptor<KeyguardUpdateMonitorCallback>
    @Captor lateinit var hostStateCallback: ArgumentCaptor<MediaHostStatesManager.Callback>
    @Captor lateinit var settingsObserverCaptor: ArgumentCaptor<ContentObserver>

    private val clock = FakeSystemClock()
    private lateinit var bgExecutor: FakeExecutor
    private lateinit var uiExecutor: FakeExecutor
    private lateinit var mediaCarouselController: MediaCarouselController

    private var originalResumeSetting =
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME, 1)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context.resources.configuration.setLocales(LocaleList(Locale.US, Locale.UK))
        bgExecutor = FakeExecutor(clock)
        uiExecutor = FakeExecutor(clock)

        mediaCarouselController =
            MediaCarouselController(
                applicationScope = kosmos.applicationCoroutineScope,
                context = context,
                mediaControlPanelFactory = mediaControlPanelFactory,
                visualStabilityProvider = visualStabilityProvider,
                mediaHostStatesManager = mediaHostStatesManager,
                activityStarter = activityStarter,
                systemClock = clock,
                uiExecutor = uiExecutor,
                bgExecutor = bgExecutor,
                backgroundDispatcher = testDispatcher,
                mediaManager = mediaDataManager,
                configurationController = configurationController,
                falsingManager = falsingManager,
                dumpManager = dumpManager,
                logger = logger,
                debugLogger = debugLogger,
                keyguardUpdateMonitor = keyguardUpdateMonitor,
                keyguardTransitionInteractor = kosmos.keyguardTransitionInteractor,
                globalSettings = globalSettings,
                secureSettings = secureSettings,
                mediaControlChipInteractor = kosmos.mediaControlChipInteractor,
                secureLockDeviceInteractor = { kosmos.secureLockDeviceInteractor },
            )
        verify(configurationController).addCallback(capture(configListener))
        if (!MediaControlsInComposeFlag.isEnabled) {
            verify(visualStabilityProvider)
                .addPersistentReorderingAllowedListener(capture(visualStabilityCallback))
        }
        verify(keyguardUpdateMonitor).registerCallback(capture(keyguardCallback))
        verify(mediaHostStatesManager).addCallback(capture(hostStateCallback))
        whenever(mediaControlPanelFactory.get()).thenReturn(panel)
        whenever(panel.mediaViewController).thenReturn(mediaViewController)
        MediaPlayerData.clear()
        FakeExecutor.exhaustExecutors(bgExecutor)
        FakeExecutor.exhaustExecutors(uiExecutor)
        verify(globalSettings)
            .registerContentObserverSync(
                eq(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)),
                capture(settingsObserverCaptor),
            )
    }

    @After
    fun tearDown() {
        Settings.Secure.putInt(
            context.contentResolver,
            Settings.Secure.MEDIA_CONTROLS_RESUME,
            originalResumeSetting,
        )
    }

    @Test
    fun testPlayerOrdering() {
        // Test values: key, data, last active time
        val playingLocal =
            Triple(
                PLAYING_LOCAL,
                DATA.copy(
                    active = true,
                    isPlaying = true,
                    playbackLocation = MediaData.PLAYBACK_LOCAL,
                    resumption = false,
                ),
                4500L,
            )

        val playingCast =
            Triple(
                "playing cast",
                DATA.copy(
                    active = true,
                    isPlaying = true,
                    playbackLocation = MediaData.PLAYBACK_CAST_LOCAL,
                    resumption = false,
                ),
                5000L,
            )

        val pausedLocal =
            Triple(
                PAUSED_LOCAL,
                DATA.copy(
                    active = true,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_LOCAL,
                    resumption = false,
                ),
                1000L,
            )

        val pausedCast =
            Triple(
                "paused cast",
                DATA.copy(
                    active = true,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_CAST_LOCAL,
                    resumption = false,
                ),
                2000L,
            )

        val playingRcn =
            Triple(
                "playing RCN",
                DATA.copy(
                    active = true,
                    isPlaying = true,
                    playbackLocation = MediaData.PLAYBACK_CAST_REMOTE,
                    resumption = false,
                ),
                5000L,
            )

        val pausedRcn =
            Triple(
                "paused RCN",
                DATA.copy(
                    active = true,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_CAST_REMOTE,
                    resumption = false,
                ),
                5000L,
            )

        val active =
            Triple(
                "active",
                DATA.copy(
                    active = true,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_LOCAL,
                    resumption = true,
                ),
                250L,
            )

        val resume1 =
            Triple(
                "resume 1",
                DATA.copy(
                    active = false,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_LOCAL,
                    resumption = true,
                ),
                500L,
            )

        val resume2 =
            Triple(
                "resume 2",
                DATA.copy(
                    active = false,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_LOCAL,
                    resumption = true,
                ),
                1000L,
            )

        // Expected ordering for media players:
        // Actively playing local sessions
        // Actively playing cast sessions
        // Paused local and cast sessions, by last active
        // RCNs
        // Resume controls, by last active

        val expected =
            listOf(
                playingLocal,
                playingCast,
                pausedCast,
                pausedLocal,
                playingRcn,
                pausedRcn,
                active,
                resume2,
                resume1,
            )

        expected.forEach {
            clock.setCurrentTimeMillis(it.third)
            MediaPlayerData.addMediaPlayer(
                it.first,
                it.second.copy(notificationKey = it.first),
                panel,
                clock,
            )
        }

        for ((index, key) in MediaPlayerData.playerKeys().withIndex()) {
            assertEquals(expected[index].first, key.data.notificationKey)
        }

        for ((index, key) in MediaPlayerData.visiblePlayerKeys().withIndex()) {
            assertEquals(expected[index].first, key.data.notificationKey)
        }
    }

    @Test
    fun testPlayingExistingMediaPlayerFromCarousel_visibleMediaPlayersNotUpdated() {
        verify(mediaDataManager).addListener(capture(listener))

        testPlayerOrdering()
        // playing paused player
        listener.value.onMediaDataLoaded(
            PAUSED_LOCAL,
            PAUSED_LOCAL,
            DATA.copy(
                active = true,
                isPlaying = true,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        listener.value.onMediaDataLoaded(
            PLAYING_LOCAL,
            PLAYING_LOCAL,
            DATA.copy(
                active = true,
                isPlaying = false,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = true,
            ),
        )
        runAllReady()

        assertEquals(
            MediaPlayerData.getMediaPlayerIndex(PAUSED_LOCAL),
            mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex,
        )
        // paused player order should stays the same in visibleMediaPLayer map.
        // paused player order should be first in mediaPlayer map.
        assertEquals(
            MediaPlayerData.visiblePlayerKeys().elementAt(3),
            MediaPlayerData.playerKeys().elementAt(0),
        )
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    @Test
    fun testChangingPlayerKeys_visibleMediaPlayersUpdated() {
        verify(mediaDataManager).addListener(capture(listener))

        val key1 = "key1"
        val key2 = "key2"
        val key3 = "key3"
        val newKey = "newKey"

        MediaPlayerData.addMediaPlayer(
            key1,
            DATA.copy(
                active = true,
                isPlaying = true,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
                notificationKey = key1,
            ),
            panel,
            clock,
        )

        MediaPlayerData.addMediaPlayer(
            key2,
            DATA.copy(
                active = true,
                isPlaying = false,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = true,
                notificationKey = key2,
            ),
            panel,
            clock,
        )

        MediaPlayerData.addMediaPlayer(
            key3,
            DATA.copy(
                active = true,
                isPlaying = false,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = true,
                notificationKey = key1,
            ),
            panel,
            clock,
        )

        assertEquals(listOf(key1, key2, key3), MediaPlayerData.visiblePlayerKeys().map { it.key })

        // Replacing key2 with newKey.
        listener.value.onMediaDataLoaded(
            key = newKey,
            oldKey = key2,
            data =
                DATA.copy(
                    active = true,
                    isPlaying = false,
                    playbackLocation = MediaData.PLAYBACK_LOCAL,
                    resumption = false,
                ),
        )
        runAllReady()

        // newKey has the same position as key2 used to have.
        assertEquals(listOf(key1, newKey, key3), MediaPlayerData.visiblePlayerKeys().map { it.key })
    }

    @Test
    fun testSwipeDismiss_logged() {
        mediaCarouselController.mediaCarouselScrollHandler.dismissCallback.invoke()

        verify(logger).logSwipeDismiss()
    }

    @Test
    fun testSettingsButton_logged() {
        mediaCarouselController.settingsButton.callOnClick()

        verify(logger).logCarouselSettings()
    }

    @Test
    fun testLocationChangeQs_logged() {
        mediaCarouselController.onDesiredLocationChanged(
            LOCATION_QS,
            mediaHostState,
            animate = false,
        )
        bgExecutor.runAllReady()
        verify(logger).logCarouselPosition(LOCATION_QS)
    }

    @Test
    fun testLocationChangeQqs_logged() {
        mediaCarouselController.onDesiredLocationChanged(
            MediaHierarchyManager.LOCATION_QQS,
            mediaHostState,
            animate = false,
        )
        bgExecutor.runAllReady()
        verify(logger).logCarouselPosition(MediaHierarchyManager.LOCATION_QQS)
    }

    @Test
    fun testLocationChangeLockscreen_logged() {
        mediaCarouselController.onDesiredLocationChanged(
            MediaHierarchyManager.LOCATION_LOCKSCREEN,
            mediaHostState,
            animate = false,
        )
        bgExecutor.runAllReady()
        verify(logger).logCarouselPosition(MediaHierarchyManager.LOCATION_LOCKSCREEN)
    }

    @Test
    fun testLocationChangeDream_logged() {
        mediaCarouselController.onDesiredLocationChanged(
            MediaHierarchyManager.LOCATION_DREAM_OVERLAY,
            mediaHostState,
            animate = false,
        )
        bgExecutor.runAllReady()
        verify(logger).logCarouselPosition(MediaHierarchyManager.LOCATION_DREAM_OVERLAY)
    }

    @Test
    fun testGetCurrentVisibleMediaContentIntent() {
        val clickIntent1 = mock(PendingIntent::class.java)
        val player1 = Triple("player1", DATA.copy(clickIntent = clickIntent1), 1000L)
        clock.setCurrentTimeMillis(player1.third)
        MediaPlayerData.addMediaPlayer(
            player1.first,
            player1.second.copy(notificationKey = player1.first),
            panel,
            clock,
        )

        assertEquals(mediaCarouselController.getCurrentVisibleMediaContentIntent(), clickIntent1)

        val clickIntent2 = mock(PendingIntent::class.java)
        val player2 = Triple("player2", DATA.copy(clickIntent = clickIntent2), 2000L)
        clock.setCurrentTimeMillis(player2.third)
        MediaPlayerData.addMediaPlayer(
            player2.first,
            player2.second.copy(notificationKey = player2.first),
            panel,
            clock,
        )

        // mediaCarouselScrollHandler.visibleMediaIndex is unchanged (= 0), and the new player is
        // added to the front because it was active more recently.
        assertEquals(mediaCarouselController.getCurrentVisibleMediaContentIntent(), clickIntent2)

        val clickIntent3 = mock(PendingIntent::class.java)
        val player3 = Triple("player3", DATA.copy(clickIntent = clickIntent3), 500L)
        clock.setCurrentTimeMillis(player3.third)
        MediaPlayerData.addMediaPlayer(
            player3.first,
            player3.second.copy(notificationKey = player3.first),
            panel,
            clock,
        )

        // mediaCarouselScrollHandler.visibleMediaIndex is unchanged (= 0), and the new player is
        // added to the end because it was active less recently.
        assertEquals(mediaCarouselController.getCurrentVisibleMediaContentIntent(), clickIntent2)
    }

    @Test
    fun testSetCurrentState_UpdatePageIndicatorAlphaWhenSquish() {
        val delta = 0.0001F
        mediaCarouselController.mediaCarousel = mediaCarousel
        mediaCarouselController.pageIndicator = pageIndicator
        whenever(mediaCarousel.measuredHeight).thenReturn(100)
        whenever(pageIndicator.translationY).thenReturn(80F)
        whenever(pageIndicator.height).thenReturn(10)
        whenever(mediaHostStatesManager.mediaHostStates)
            .thenReturn(mutableMapOf(LOCATION_QS to mediaHostState))
        whenever(mediaHostState.visible).thenReturn(true)
        mediaCarouselController.currentEndLocation = LOCATION_QS
        whenever(mediaHostState.squishFraction).thenReturn(0.938F)
        mediaCarouselController.updatePageIndicatorAlpha()
        verify(pageIndicator).alpha = floatThat { abs(it - 0.5F) < delta }

        whenever(mediaHostState.squishFraction).thenReturn(1.0F)
        mediaCarouselController.updatePageIndicatorAlpha()
        verify(pageIndicator).alpha = floatThat { abs(it - 1.0F) < delta }
    }

    @Test
    fun testOnConfigChanged_playersAreAddedBack() {
        testConfigurationChange { configListener.value.onConfigChanged(Configuration()) }
    }

    @Test
    fun testOnUiModeChanged_playersAreAddedBack() {
        testConfigurationChange(configListener.value::onUiModeChanged)

        verify(pageIndicator).tintList =
            ColorStateList.valueOf(context.getColor(R.color.media_paging_indicator))
        verify(pageIndicator, times(2)).setNumPages(any())
    }

    @Test
    fun testOnDensityOrFontScaleChanged_playersAreAddedBack() {
        testConfigurationChange(configListener.value::onDensityOrFontScaleChanged)

        verify(pageIndicator).tintList =
            ColorStateList.valueOf(context.getColor(R.color.media_paging_indicator))
        // when recreateMedia is set to true, page indicator is updated on removal and addition.
        verify(pageIndicator, times(4)).setNumPages(any())
    }

    @Test
    fun testOnThemeChanged_playersAreAddedBack() {
        testConfigurationChange(configListener.value::onThemeChanged)

        verify(pageIndicator).tintList =
            ColorStateList.valueOf(context.getColor(R.color.media_paging_indicator))
        verify(pageIndicator, times(2)).setNumPages(any())
    }

    @Test
    fun testOnLocaleListChanged_playersAreAddedBack() {
        context.resources.configuration.setLocales(LocaleList(Locale.US, Locale.UK, Locale.CANADA))
        testConfigurationChange(configListener.value::onLocaleListChanged)

        verify(pageIndicator, never()).tintList =
            ColorStateList.valueOf(context.getColor(R.color.media_paging_indicator))

        context.resources.configuration.setLocales(LocaleList(Locale.UK, Locale.US, Locale.CANADA))
        testConfigurationChange(configListener.value::onLocaleListChanged)

        verify(pageIndicator).tintList =
            ColorStateList.valueOf(context.getColor(R.color.media_paging_indicator))
        // When recreateMedia is set to true, page indicator is updated on removal and addition.
        verify(pageIndicator, times(4)).setNumPages(any())
    }

    @Test
    fun testOnLockDownMode_hideMediaCarousel() {
        whenever(keyguardUpdateMonitor.isUserInLockdown(context.userId)).thenReturn(true)
        mediaCarouselController.mediaCarousel = mediaCarousel

        keyguardCallback.value.onStrongAuthStateChanged(context.userId)

        verify(mediaCarousel).visibility = View.GONE
    }

    @Test
    fun testLockDownModeOff_showMediaCarousel() {
        whenever(keyguardUpdateMonitor.isUserInLockdown(context.userId)).thenReturn(false)
        whenever(keyguardUpdateMonitor.isUserUnlocked(context.userId)).thenReturn(true)
        mediaCarouselController.mediaCarousel = mediaCarousel

        keyguardCallback.value.onStrongAuthStateChanged(context.userId)

        verify(mediaCarousel).visibility = View.VISIBLE
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun testOnSecureLockDeviceMode_hideMediaCarousel() {
        kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
        mediaCarouselController.mediaCarousel = mediaCarousel

        keyguardCallback.value.onStrongAuthStateChanged(context.userId)

        verify(mediaCarousel).visibility = View.GONE
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun testOnSecureLockDeviceModeOff_showMediaCarousel() {
        kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
        whenever(keyguardUpdateMonitor.isUserUnlocked(context.userId)).thenReturn(true)
        mediaCarouselController.mediaCarousel = mediaCarousel

        keyguardCallback.value.onStrongAuthStateChanged(context.userId)

        verify(mediaCarousel).visibility = View.VISIBLE
    }

    @Test
    fun testKeyguardGone_showMediaCarousel() =
        kosmos.testScope.runTest {
            var updatedVisibility = false
            mediaCarouselController.updateHostVisibility = { updatedVisibility = true }
            mediaCarouselController.mediaCarousel = mediaCarousel

            val job = mediaCarouselController.listenForAnyStateToGoneKeyguardTransition(this)
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                this,
            )

            verify(mediaCarousel, atLeast(1)).visibility = View.VISIBLE
            assertEquals(true, updatedVisibility)
            assertEquals(false, mediaCarouselController.isLockedAndHidden())

            job.cancel()
        }

    @Test
    fun keyguardShowing_notAllowedOnLockscreen_updateVisibility() {
        kosmos.testScope.runTest {
            var updatedVisibility = false
            mediaCarouselController.updateHostVisibility = { updatedVisibility = true }
            mediaCarouselController.mediaCarousel = mediaCarousel

            val settingsJob =
                mediaCarouselController.listenForLockscreenSettingChanges(
                    kosmos.applicationCoroutineScope
                )
            secureSettings.putBool(Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN, false)

            val keyguardJob = mediaCarouselController.listenForAnyStateToLockscreenTransition(this)
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.LOCKSCREEN,
                this,
            )

            assertEquals(true, updatedVisibility)
            assertEquals(true, mediaCarouselController.isLockedAndHidden())

            settingsJob.cancel()
            keyguardJob.cancel()
        }
    }

    @Test
    fun keyguardShowing_allowedOnLockscreen_updateVisibility() {
        kosmos.testScope.runTest {
            var updatedVisibility = false
            mediaCarouselController.updateHostVisibility = { updatedVisibility = true }
            mediaCarouselController.mediaCarousel = mediaCarousel

            val settingsJob =
                mediaCarouselController.listenForLockscreenSettingChanges(
                    kosmos.applicationCoroutineScope
                )
            secureSettings.putBool(Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN, true)

            val keyguardJob = mediaCarouselController.listenForAnyStateToLockscreenTransition(this)
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.LOCKSCREEN,
                this,
            )

            assertEquals(true, updatedVisibility)
            assertEquals(false, mediaCarouselController.isLockedAndHidden())

            settingsJob.cancel()
            keyguardJob.cancel()
        }
    }

    @Test
    fun goingToDozing_notAllowedOnLockscreen_updateVisibility() {
        kosmos.testScope.runTest {
            var updatedVisibility = false
            mediaCarouselController.updateHostVisibility = { updatedVisibility = true }
            mediaCarouselController.mediaCarousel = mediaCarousel

            val settingsJob =
                mediaCarouselController.listenForLockscreenSettingChanges(
                    kosmos.applicationCoroutineScope
                )
            secureSettings.putBool(Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN, false)

            val keyguardJob = mediaCarouselController.listenForAnyStateToDozingTransition(this)
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.DOZING,
                this,
            )

            assertEquals(true, updatedVisibility)
            assertEquals(true, mediaCarouselController.isLockedAndHidden())

            settingsJob.cancel()
            keyguardJob.cancel()
        }
    }

    @Test
    fun testInvisibleToUserAndExpanded_playersNotListening() {
        // Add players to carousel.
        testPlayerOrdering()

        // Make the carousel visible to user in expanded layout.
        mediaCarouselController.currentlyExpanded = true
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true

        // panel is the player for each MediaPlayerData.
        // Verify that seekbar listening attribute in media control panel is set to true.
        verify(panel, times(MediaPlayerData.players().size)).listening = true

        // Make the carousel invisible to user.
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = false

        // panel is the player for each MediaPlayerData.
        // Verify that seekbar listening attribute in media control panel is set to false.
        verify(panel, times(MediaPlayerData.players().size)).listening = false
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    @Test
    fun testOnCarouselBecomesVisible_requestsSuggestion() {
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true
        addPlayer(playerId = "player1")
        addPlayer(playerId = "player2")

        mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex = 0
        mediaCarouselController.onCarouselVisibleToUser()

        verify(panel).onPanelFullyVisible()
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    @Test
    fun testUpdateVisibility_samePlayerVisible_doNothing() {
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true
        addPlayer(playerId = "player1")
        addPlayer(playerId = "player2")

        mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex = 0
        mediaCarouselController.onVisibleCardChanged()

        verify(panel).onPanelFullyVisible()
        clearInvocations(panel)

        mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex = 0
        mediaCarouselController.onVisibleCardChanged()

        verify(panel, never()).onPanelFullyVisible()
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    @Test
    fun testUpdateVisibility_noPlayerAvailable_doNothing() {
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true
        mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex = 0
        mediaCarouselController.onVisibleCardChanged()

        verify(panel, never()).onPanelFullyVisible()
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI)
    @Test
    fun testUpdateVisibility_activePlayerChanged_requestsSuggestion() {
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true
        addPlayer(playerId = "player1")
        addPlayer(playerId = "player2")

        mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex = 0
        mediaCarouselController.onVisibleCardChanged()

        verify(panel).onPanelFullyVisible()
        clearInvocations(panel)

        mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex = 1
        mediaCarouselController.onVisibleCardChanged()

        verify(panel).onPanelFullyVisible()
    }

    @Test
    fun testVisibleToUserAndExpanded_playersListening() {
        // Add players to carousel.
        testPlayerOrdering()

        // Make the carousel visible to user in expanded layout.
        mediaCarouselController.currentlyExpanded = true
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true

        // panel is the player for each MediaPlayerData.
        // Verify that seekbar listening attribute in media control panel is set to true.
        verify(panel, times(MediaPlayerData.players().size)).listening = true
    }

    @Test
    fun testUMOCollapsed_playersNotListening() {
        // Add players to carousel.
        testPlayerOrdering()

        // Make the carousel in collapsed layout.
        mediaCarouselController.currentlyExpanded = false

        // panel is the player for each MediaPlayerData.
        // Verify that seekbar listening attribute in media control panel is set to false.
        verify(panel, times(MediaPlayerData.players().size)).listening = false

        // Make the carousel visible to user.
        reset(panel)
        mediaCarouselController.mediaCarouselScrollHandler.visibleToUser = true

        // Verify that seekbar listening attribute in media control panel is set to false.
        verify(panel, times(MediaPlayerData.players().size)).listening = false
    }

    @Test
    fun testOnHostStateChanged_updateVisibility() {
        var stateUpdated = false
        mediaCarouselController.updateUserVisibility = { stateUpdated = true }

        // When the host state updates
        hostStateCallback.value!!.onHostStateChanged(LOCATION_QS, mediaHostState)

        // Then the carousel visibility is updated
        assertTrue(stateUpdated)
    }

    @Test
    fun testAnimationScaleChanged_mediaControlPanelsNotified() {
        MediaPlayerData.addMediaPlayer("key", DATA, panel, clock)

        globalSettings.putFloat(Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        settingsObserverCaptor.value!!.onChange(false)
        verify(panel).updateAnimatorDurationScale()
    }

    @Test
    fun swipeToDismiss_pausedAndResumeOff_userInitiated() =
        kosmos.testScope.runTest {
            verify(mediaDataManager).addListener(capture(listener))
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                this,
            )

            // When resumption is disabled, paused media should be dismissed after being swiped away
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME,
                0,
            )
            val pausedMedia = DATA.copy(isPlaying = false)
            listener.value.onMediaDataLoaded(PAUSED_LOCAL, PAUSED_LOCAL, pausedMedia)
            runAllReady()
            mediaCarouselController.onSwipeToDismiss()

            // When it can be removed immediately on update
            whenever(visualStabilityProvider.isReorderingAllowed).thenReturn(true)
            val inactiveMedia = pausedMedia.copy(active = false)
            listener.value.onMediaDataLoaded(PAUSED_LOCAL, PAUSED_LOCAL, inactiveMedia)
            runAllReady()

            // This is processed as a user-initiated dismissal
            verify(debugLogger).logMediaRemoved(eq(PAUSED_LOCAL), eq(true))
            verify(mediaDataManager).dismissMediaData(eq(PAUSED_LOCAL), anyLong(), eq(true))
        }

    @Test
    fun swipeToDismiss_pausedAndResumeOff_delayed_userInitiated() {
        verify(mediaDataManager).addListener(capture(listener))

        // When resumption is disabled, paused media should be dismissed after being swiped away
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME, 0)
        mediaCarouselController.updateHostVisibility = {}

        val pausedMedia = DATA.copy(isPlaying = false)
        listener.value.onMediaDataLoaded(PAUSED_LOCAL, PAUSED_LOCAL, pausedMedia)
        runAllReady()
        mediaCarouselController.onSwipeToDismiss()

        // When it can't be removed immediately on update
        whenever(visualStabilityProvider.isReorderingAllowed).thenReturn(false)
        val inactiveMedia = pausedMedia.copy(active = false)
        listener.value.onMediaDataLoaded(PAUSED_LOCAL, PAUSED_LOCAL, inactiveMedia)
        runAllReady()
        visualStabilityCallback.value.onReorderingAllowed()

        // This is processed as a user-initiated dismissal
        verify(mediaDataManager).dismissMediaData(eq(PAUSED_LOCAL), anyLong(), eq(true))
    }

    @EnableFlags(Flags.FLAG_MEDIA_CAROUSEL_ARROWS)
    @Test
    fun singleMediaPlayer_disablePageArrows() {
        verify(mediaDataManager).addListener(capture(listener))
        listener.value.onMediaDataLoaded(
            PLAYING_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = true,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        runAllReady()

        val player = MediaPlayerData.players().first()
        verify(player).setPageArrowsVisible(eq(false))
    }

    @EnableFlags(Flags.FLAG_MEDIA_CAROUSEL_ARROWS)
    @Test
    fun multipleMediaPlayers_enablePageArrows() {
        verify(mediaDataManager).addListener(capture(listener))
        listener.value.onMediaDataLoaded(
            PLAYING_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = true,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        listener.value.onMediaDataLoaded(
            PAUSED_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = false,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        runAllReady()

        assertEquals(2, MediaPlayerData.players().size)
        MediaPlayerData.players().forEachIndexed { index, mediaPlayer ->
            verify(mediaPlayer, atLeast(1)).setPageArrowsVisible(eq(true))
            if (index == 0) {
                verify(mediaPlayer).setPageLeftEnabled(eq(false))
                verify(mediaPlayer).setPageRightEnabled(eq(true))
            } else if (index == 1) {
                verify(mediaPlayer).setPageLeftEnabled(eq(true))
                verify(mediaPlayer).setPageRightEnabled(eq(false))
            }
        }
    }

    @EnableFlags(Flags.FLAG_MEDIA_CAROUSEL_ARROWS)
    @DisableSceneContainer
    @Test
    fun multipleMediaPlayers_disableScrolling_noPageArrows() {
        verify(mediaDataManager).addListener(capture(listener))

        // Set carousel host to disable scrolling
        whenever(mediaHostStatesManager.mediaHostStates)
            .thenReturn(mutableMapOf(LOCATION_QS to mediaHostState))
        whenever(mediaHostState.disableScrolling).thenReturn(true)
        mediaCarouselController.currentEndLocation = LOCATION_QS
        mediaCarouselController.setCurrentState(LOCATION_QS, LOCATION_QS, 1.0f, true)

        listener.value.onMediaDataLoaded(
            PLAYING_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = true,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        listener.value.onMediaDataLoaded(
            PAUSED_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = false,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        runAllReady()

        assertEquals(2, MediaPlayerData.players().size)
        MediaPlayerData.players().forEachIndexed { index, mediaPlayer ->
            verify(mediaPlayer, atLeast(1)).setPageArrowsVisible(eq(false))
            verify(mediaPlayer, never()).setPageArrowsVisible(eq(true))
        }
    }

    private fun addPlayer(playerId: String) {
        clock.setCurrentTimeMillis(1000L)
        MediaPlayerData.addMediaPlayer(
            key = playerId,
            data =
                DATA.copy(
                    clickIntent = mock(PendingIntent::class.java),
                    notificationKey = playerId,
                ),
            player = panel,
            clock = clock,
        )
    }

    /**
     * Helper method when a configuration change occurs.
     *
     * @param function called when a certain configuration change occurs.
     */
    private fun testConfigurationChange(function: () -> Unit) {
        verify(mediaDataManager).addListener(capture(listener))
        mediaCarouselController.pageIndicator = pageIndicator
        listener.value.onMediaDataLoaded(
            PLAYING_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = true,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        listener.value.onMediaDataLoaded(
            PAUSED_LOCAL,
            null,
            DATA.copy(
                active = true,
                isPlaying = false,
                playbackLocation = MediaData.PLAYBACK_LOCAL,
                resumption = false,
            ),
        )
        runAllReady()

        val playersSize = MediaPlayerData.players().size
        reset(pageIndicator)
        function()
        runAllReady()

        assertEquals(playersSize, MediaPlayerData.players().size)
        assertEquals(
            MediaPlayerData.getMediaPlayerIndex(PLAYING_LOCAL),
            mediaCarouselController.mediaCarouselScrollHandler.visibleMediaIndex,
        )
    }

    private fun runAllReady() {
        bgExecutor.runAllReady()
        uiExecutor.runAllReady()
    }
}
