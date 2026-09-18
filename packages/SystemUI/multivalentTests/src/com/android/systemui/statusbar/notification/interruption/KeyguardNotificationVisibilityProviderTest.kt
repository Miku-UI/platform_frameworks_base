/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.statusbar.notification.interruption

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_MIN
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.security.Flags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambient.statusbar.shared.flag.OngoingActivityChipsOnDream
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.securelockdevice.domain.interactor.secureLockDeviceInteractor
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.modifyEntry
import com.android.systemui.statusbar.notification.collection.provider.HighPriorityProvider
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.withArgCaptor
import com.android.systemui.util.settings.FakeGlobalSettings
import com.android.systemui.util.settings.FakeSettings
import com.android.systemui.utils.os.FakeHandler
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor.forClass
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class KeyguardNotificationVisibilityProviderTest : SysuiTestCase() {
    @JvmField @Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var lockscreenUserManager: NotificationLockscreenUserManager

    @Mock private lateinit var highPriorityProvider: HighPriorityProvider

    @Mock private lateinit var statusBarStateController: SysuiStatusBarStateController

    @Mock private lateinit var userTracker: UserTracker
    private val secureSettings = FakeSettings()
    private val globalSettings = FakeGlobalSettings()

    private lateinit var keyguardNotificationVisibilityProvider:
        KeyguardNotificationVisibilityProviderImpl
    private lateinit var entry: NotificationEntry

    private val kosmos = testKosmos()
    private val keyguardStateController = kosmos.keyguardStateController
    private val keyguardUpdateMonitor = kosmos.keyguardUpdateMonitor

    @Before
    fun setup() {
        keyguardNotificationVisibilityProvider =
            KeyguardNotificationVisibilityProviderImpl(
                FakeHandler(TestableLooper.get(this).looper),
                keyguardStateController,
                lockscreenUserManager,
                keyguardUpdateMonitor,
                highPriorityProvider,
                statusBarStateController,
                userTracker,
                secureSettings,
                globalSettings,
            ) {
                kosmos.secureLockDeviceInteractor
            }
        keyguardNotificationVisibilityProvider.start()
        entry = NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID)).build()
    }

    @Test
    fun notifyListeners_onUnlockedChanged() =
        kosmos.testScope.runTest {
            val callbackCaptor = forClass(KeyguardStateController.Callback::class.java)
            verify(keyguardStateController).addCallback(callbackCaptor.capture())
            val callback = callbackCaptor.getValue()

            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            callback.onUnlockedChanged()

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onKeyguardShowingChanged() =
        kosmos.testScope.runTest {
            val callbackCaptor = forClass(KeyguardStateController.Callback::class.java)
            verify(keyguardStateController).addCallback(callbackCaptor.capture())
            val callback = callbackCaptor.getValue()

            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            callback.onKeyguardShowingChanged()

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onStrongAuthStateChanged() =
        kosmos.testScope.runTest {
            val callbackCaptor = forClass(KeyguardUpdateMonitorCallback::class.java)
            verify(keyguardUpdateMonitor).registerCallback(callbackCaptor.capture())
            val callback = callbackCaptor.getValue()

            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            callback.onStrongAuthStateChanged(0)

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onStatusBarUpcomingStateChanged() =
        kosmos.testScope.runTest {
            val callbackCaptor = forClass(StatusBarStateController.StateListener::class.java)
            verify(statusBarStateController).addCallback(callbackCaptor.capture())
            val callback = callbackCaptor.getValue()

            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            callback.onUpcomingStateChanged(0)

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onStatusBarStateChanged() =
        kosmos.testScope.runTest {
            val callbackCaptor = forClass(StatusBarStateController.StateListener::class.java)
            verify(statusBarStateController).addCallback(callbackCaptor.capture())
            val callback = callbackCaptor.getValue()

            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            callback.onStateChanged(0)

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onReceiveUserSwitchCallback() =
        kosmos.testScope.runTest {
            val callback =
                withArgCaptor<UserTracker.Callback> {
                    verify(userTracker).addCallback(capture(), any())
                }

            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            callback.onUserChanged(CURR_USER_ID, context)

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onSettingChange_lockScreenShowNotifs() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onSettingChange_lockScreenAllowPrivateNotifs() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, true)

            verify(listener).accept(anyString())
        }

    @Test
    fun hideSilentNotificationsPerUserSettingWithHighPriorityParent() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)
            val parent =
                GroupEntryBuilder()
                    .setKey("parent")
                    .addChild(entry)
                    .setSummary(
                        NotificationEntryBuilder()
                            .setUser(UserHandle(NOTIF_USER_ID))
                            .setImportance(NotificationManager.IMPORTANCE_LOW)
                            .build()
                    )
                    .build()
            entry =
                NotificationEntryBuilder()
                    .setUser(UserHandle(NOTIF_USER_ID))
                    .setImportance(NotificationManager.IMPORTANCE_LOW)
                    .setParent(parent)
                    .build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun keyguardShowing_hideSilentNotifications_perUserSetting() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)
            entry =
                NotificationEntryBuilder()
                    .setUser(UserHandle(NOTIF_USER_ID))
                    .setImportance(NotificationManager.IMPORTANCE_LOW)
                    .build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun keyguardShowing_hideSilentNotifications_perUserSetting_withHighPriorityParent() =
        kosmos.testScope.runTest {
            whenever(keyguardStateController.isShowing()).thenReturn(true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)
            val parent =
                GroupEntryBuilder()
                    .setKey("parent")
                    .addChild(entry)
                    .setSummary(
                        NotificationEntryBuilder()
                            .setUser(UserHandle(NOTIF_USER_ID))
                            .setImportance(NotificationManager.IMPORTANCE_LOW)
                            .build()
                    )
                    .build()
            entry =
                NotificationEntryBuilder()
                    .setUser(UserHandle(NOTIF_USER_ID))
                    .setImportance(NotificationManager.IMPORTANCE_LOW)
                    .setParent(parent)
                    .build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @EnableFlags(OngoingActivityChipsOnDream.FLAG_NAME)
    @Test
    fun dreamingWithOverlay_flagEnabled_showNotifications() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)
            entry =
                NotificationEntryBuilder()
                    .setUser(UserHandle(NOTIF_USER_ID))
                    .setImportance(NotificationManager.IMPORTANCE_LOW)
                    .build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)
            whenever(keyguardUpdateMonitor.isDreamingWithOverlay).thenReturn(true)
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @DisableFlags(OngoingActivityChipsOnDream.FLAG_NAME)
    @Test
    fun dreamingWithOverlay_flagDisabled_hideNotifications() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)
            entry =
                NotificationEntryBuilder()
                    .setUser(UserHandle(NOTIF_USER_ID))
                    .setImportance(NotificationManager.IMPORTANCE_LOW)
                    .build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)
            whenever(keyguardUpdateMonitor.isDreamingWithOverlay).thenReturn(true)
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun hideSilentOnLockscreenSetting() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state and notifications shown on lockscreen
            setupUnfilteredState(entry)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)

            // WHEN the show silent notifs on lockscreen setting is false
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)

            // WHEN the notification is not high priority and not ambient
            entry =
                NotificationEntryBuilder().setImportance(NotificationManager.IMPORTANCE_LOW).build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun showSilentOnLockscreenSetting() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state and notifications shown on lockscreen
            setupUnfilteredState(entry)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)

            // WHEN the show silent notifs on lockscreen setting is true
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, true)

            // WHEN the notification is not high priority and not ambient
            entry =
                NotificationEntryBuilder().setImportance(NotificationManager.IMPORTANCE_LOW).build()
            whenever(highPriorityProvider.isExplicitlyHighPriority(entry)).thenReturn(false)

            // THEN do not filter out the entry
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun defaultSilentOnLockscreenSettingIsHide() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state and notifications shown on lockscreen
            setupUnfilteredState(entry)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)

            // WHEN the notification is not high priority and not ambient
            entry =
                NotificationEntryBuilder()
                    .setUser(UserHandle(NOTIF_USER_ID))
                    .setImportance(NotificationManager.IMPORTANCE_LOW)
                    .build()
            whenever(
                    highPriorityProvider.isExplicitlyHighPriority(
                        // TODO("Cannot convert element")
                        any()
                    )
                )
                .thenReturn(false)

            // WhHEN the show silent notifs on lockscreen setting is unset
            assertNull(
                secureSettings.getString(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS)
            )
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun notifyListeners_onSettingChange_zenMode() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            globalSettings.putBool(Settings.Global.ZEN_MODE, true)

            verify(listener).accept(anyString())
        }

    @Test
    fun notifyListeners_onSettingChange_lockScreenShowSilentNotifs() =
        kosmos.testScope.runTest {
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.KEYGUARD)
            val listener: Consumer<String> = mock()
            keyguardNotificationVisibilityProvider.addOnStateChangedListener(listener)

            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, true)

            verify(listener).accept(anyString())
        }

    @Test
    fun unfilteredState() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // THEN don't filter out the entry
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun keyguardNotShowing() =
        kosmos.testScope.runTest {
            // GIVEN the lockscreen isn't showing
            setupUnfilteredState(entry)
            whenever(keyguardStateController.isShowing()).thenReturn(false)
            whenever(statusBarStateController.getCurrentOrUpcomingState())
                .thenReturn(StatusBarState.SHADE)

            // THEN don't filter out the entry
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun doNotShowLockscreenNotifications() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // WHEN we shouldn't show any lockscreen notifications
            whenever(lockscreenUserManager.shouldShowLockscreenNotifications()).thenReturn(false)

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun lockdown() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // WHEN the notification's user is in lockdown:
            whenever(keyguardUpdateMonitor.isUserInLockdown(NOTIF_USER_ID)).thenReturn(true)

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @EnableFlags(Flags.FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun secure_lock_device() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            val isSecureLockDeviceEnabled by
                collectLastValue(kosmos.secureLockDeviceInteractor.isSecureLockDeviceEnabled)

            // WHEN the notification's user is in secure lock device:
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            runCurrent()

            assertThat(isSecureLockDeviceEnabled).isTrue()

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun publicMode_settingsDisallow() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // WHEN the notification's user is in public mode and settings are configured to
            // disallow
            // notifications in public mode
            whenever(lockscreenUserManager.isLockscreenPublicMode(NOTIF_USER_ID)).thenReturn(true)
            whenever(lockscreenUserManager.userAllowsNotificationsInPublic(NOTIF_USER_ID))
                .thenReturn(false)

            entry.setRanking(
                RankingBuilder()
                    .setChannel(NotificationChannel("1", "1", NotificationManager.IMPORTANCE_HIGH))
                    .setVisibilityOverride(NotificationManager.VISIBILITY_NO_OVERRIDE)
                    .setKey(entry.key)
                    .build()
            )

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun publicMode_nullChannel_allowed() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // WHEN the notification's user is in public mode and settings are configured to
            // disallow
            // notifications in public mode
            whenever(lockscreenUserManager.isLockscreenPublicMode(CURR_USER_ID)).thenReturn(true)
            entry.setRanking(
                RankingBuilder()
                    .setKey(entry.key)
                    .setVisibilityOverride(Notification.VISIBILITY_SECRET)
                    .build()
            )

            // THEN allow the entry
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun publicMode_notifDisallowed() =
        kosmos.testScope.runTest {
            val channel = NotificationChannel("1", "1", NotificationManager.IMPORTANCE_HIGH)
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // WHEN the notification's user is in public mode and settings are configured to
            // disallow
            // notifications in public mode
            whenever(lockscreenUserManager.isLockscreenPublicMode(CURR_USER_ID)).thenReturn(true)
            entry.setRanking(
                RankingBuilder()
                    .setKey(entry.key)
                    .setChannel(channel)
                    .setVisibilityOverride(Notification.VISIBILITY_SECRET)
                    .build()
            )

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun doesNotExceedThresholdToShow() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // WHEN the notification doesn't exceed the threshold to show on the lockscreen
            entry.setRanking(
                RankingBuilder().setKey(entry.key).setImportance(IMPORTANCE_MIN).build()
            )
            whenever(highPriorityProvider.isExplicitlyHighPriority(entry)).thenReturn(false)

            // THEN filter out the entry
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun highPriorityCharacteristicsIgnored() =
        kosmos.testScope.runTest {
            // GIVEN an 'unfiltered-keyguard-showing' state with silent notifications hidden
            setupUnfilteredState(entry)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true)
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)

            // WHEN the notification doesn't exceed the threshold to show on the lockscreen, but
            // does
            // have the "high priority characteristics" that would promote it to high priority
            entry.setRanking(
                RankingBuilder().setKey(entry.key).setImportance(IMPORTANCE_MIN).build()
            )
            whenever(highPriorityProvider.isHighPriority(entry)).thenReturn(true)
            whenever(highPriorityProvider.isExplicitlyHighPriority(entry)).thenReturn(false)

            // THEN filter out the entry anyway, because the user explicitly asked us to hide it
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun notificationVisibilityPublic() =
        kosmos.testScope.runTest {
            // GIVEN a VISIBILITY_PUBLIC notification
            val entryBuilder = NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID))
            entryBuilder.modifyNotification(context).setVisibility(Notification.VISIBILITY_PUBLIC)
            entry = entryBuilder.build()

            // WHEN we're in an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // THEN don't hide the entry based on visibility.
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun notificationChannelVisibilityNoOverride() =
        kosmos.testScope.runTest {
            // GIVEN a VISIBILITY_PRIVATE notification
            val entryBuilder = NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID))
            entryBuilder.modifyNotification(context).setVisibility(Notification.VISIBILITY_PRIVATE)
            entry = entryBuilder.build()
            // ranking says secret because of DPC or Setting
            entry.setRanking(
                RankingBuilder()
                    .setKey(entry.key)
                    .setVisibilityOverride(Notification.VISIBILITY_SECRET)
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .build()
            )

            // WHEN we're in an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // THEN don't hide the entry based on visibility. (Redaction is handled elsewhere.)
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun notificationChannelVisibilitySecret() =
        kosmos.testScope.runTest {
            // GIVEN a VISIBILITY_PRIVATE notification
            val entryBuilder = NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID))
            entryBuilder.modifyNotification(context).setVisibility(Notification.VISIBILITY_PRIVATE)
            // And a VISIBILITY_SECRET NotificationChannel
            val channel = NotificationChannel("id", "name", NotificationManager.IMPORTANCE_HIGH)
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            entry = entryBuilder.build()
            // WHEN we're in an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)
            whenever(lockscreenUserManager.isLockscreenPublicMode(CURR_USER_ID)).thenReturn(true)
            whenever(lockscreenUserManager.isLockscreenPublicMode(NOTIF_USER_ID)).thenReturn(true)

            entry.setRanking(RankingBuilder(entry.ranking).setChannel(channel).build())

            // THEN hide the entry based on visibility.
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun notificationVisibilityPrivate() =
        kosmos.testScope.runTest {
            // GIVEN a VISIBILITY_PRIVATE notification
            val entryBuilder = NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID))
            entryBuilder.modifyNotification(context).setVisibility(Notification.VISIBILITY_PRIVATE)
            entry = entryBuilder.build()

            // WHEN we're in an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // THEN don't hide the entry based on visibility. (Redaction is handled elsewhere.)
            assertFalse(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun notificationVisibilitySecret() =
        kosmos.testScope.runTest {
            // GIVEN a VISIBILITY_SECRET notification
            val entryBuilder = NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID))
            entryBuilder.modifyNotification(context).setVisibility(Notification.VISIBILITY_SECRET)
            entry = entryBuilder.build()

            // WHEN we're in an 'unfiltered-keyguard-showing' state
            setupUnfilteredState(entry)

            // THEN hide the entry based on visibility.
            assertTrue(keyguardNotificationVisibilityProvider.shouldHideNotification(entry))
        }

    @Test
    fun summaryExceedsThresholdToShow() =
        kosmos.testScope.runTest {
            // GIVEN the notification doesn't exceed the threshold to show on the lockscreen
            // but it's part of a group (has a parent)
            val entryWithParent =
                NotificationEntryBuilder().setUser(UserHandle(NOTIF_USER_ID)).build()

            val parent =
                GroupEntryBuilder()
                    .setKey("test_group_key")
                    .setSummary(
                        NotificationEntryBuilder()
                            .setImportance(NotificationManager.IMPORTANCE_HIGH)
                            .build()
                    )
                    .addChild(entryWithParent)
                    .build()

            setupUnfilteredState(entryWithParent)
            entryWithParent.setRanking(
                RankingBuilder().setKey(entryWithParent.key).setImportance(IMPORTANCE_MIN).build()
            )

            // WHEN its parent does exceed threshold tot show on the lockscreen
            secureSettings.putBool(Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS, false)
            whenever(highPriorityProvider.isExplicitlyHighPriority(parent)).thenReturn(true)

            // THEN filter out the entry regardless of parent
            assertTrue(
                keyguardNotificationVisibilityProvider.shouldHideNotification(entryWithParent)
            )

            // WHEN its parent doesn't exceed threshold to show on lockscreen
            whenever(highPriorityProvider.isExplicitlyHighPriority(parent)).thenReturn(false)
            parent.summary?.modifyEntry { setImportance(IMPORTANCE_MIN).done() }

            // THEN filter out the entry
            assertTrue(
                keyguardNotificationVisibilityProvider.shouldHideNotification(entryWithParent)
            )
        }

    /**
     * setup a state where the notification will not be filtered by the
     * KeyguardNotificationCoordinator when the keyguard is showing.
     */
    private fun TestScope.setupUnfilteredState(entry: NotificationEntry) {
        // keyguard is showing
        whenever(keyguardStateController.isShowing()).thenReturn(true)
        whenever(statusBarStateController.getCurrentOrUpcomingState())
            .thenReturn(StatusBarState.KEYGUARD)

        // show notifications on the lockscreen
        whenever(lockscreenUserManager.shouldShowLockscreenNotifications()).thenReturn(true)

        // neither the current user nor the notification's user is in lockdown
        whenever(lockscreenUserManager.getCurrentUserId()).thenReturn(CURR_USER_ID)
        whenever(keyguardUpdateMonitor.isUserInLockdown(NOTIF_USER_ID)).thenReturn(false)
        whenever(keyguardUpdateMonitor.isUserInLockdown(CURR_USER_ID)).thenReturn(false)
        // neither the current user nor the notification's user is in secure lock device
        val isSecureLockDeviceEnabled by
            collectLastValue(kosmos.secureLockDeviceInteractor.isSecureLockDeviceEnabled)

        kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
        runCurrent()

        assertThat(isSecureLockDeviceEnabled).isFalse()

        // not in public mode
        whenever(lockscreenUserManager.isLockscreenPublicMode(CURR_USER_ID)).thenReturn(false)
        whenever(lockscreenUserManager.isLockscreenPublicMode(NOTIF_USER_ID)).thenReturn(false)

        // entry's ranking - should show on all lockscreens
        // + priority of the notification exceeds the threshold to be shown on the lockscreen
        entry.setRanking(
            RankingBuilder()
                .setKey(entry.key)
                .setVisibilityOverride(Notification.VISIBILITY_PUBLIC)
                .setImportance(NotificationManager.IMPORTANCE_HIGH)
                .build()
        )

        // settings allows notifications in public mode
        whenever(lockscreenUserManager.userAllowsNotificationsInPublic(CURR_USER_ID))
            .thenReturn(true)
        whenever(lockscreenUserManager.userAllowsNotificationsInPublic(NOTIF_USER_ID))
            .thenReturn(true)

        // notification doesn't have a summary

        // notification is high priority, so it shouldn't be filtered
        whenever(
                highPriorityProvider.isExplicitlyHighPriority(
                    this@KeyguardNotificationVisibilityProviderTest.entry
                )
            )
            .thenReturn(true)
    }

    companion object {
        private const val NOTIF_USER_ID = 0
        private const val CURR_USER_ID = 1
    }
}
