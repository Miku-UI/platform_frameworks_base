/*
 * Copyright (c) 2022 The Android Open Source Project
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
 *
 */

package com.android.systemui.statusbar.notification.row

import android.net.Uri
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.testing.TestableLooper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.internal.statusbar.IStatusBarService
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.FeatureFlagsClassic
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.PluginManager
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.SmartReplyController
import com.android.systemui.statusbar.notification.BundleInteractionLogger
import com.android.systemui.statusbar.notification.ColorUpdateLogger
import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.collection.EntryAdapterFactory
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.provider.NotificationDismissibilityProvider
import com.android.systemui.statusbar.notification.collection.render.FakeNodeController
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManager
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRowController.BUBBLES_SETTING_URI
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.statusbar.notification.stack.NotificationChildrenContainerLogger
import com.android.systemui.statusbar.notification.stack.NotificationListContainer
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationRowStatsLogger
import com.android.systemui.statusbar.phone.KeyguardBypassController
import com.android.systemui.statusbar.policy.SmartReplyConstants
import com.android.systemui.statusbar.policy.dagger.RemoteInputViewSubcomponent
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.time.SystemClock
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import com.google.android.msdl.domain.MSDLPlayer
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.atLeastOnce

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class ExpandableNotificationRowControllerTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private lateinit var entry: NotificationEntry
    private lateinit var view: ExpandableNotificationRow
    private val activableNotificationViewController: ActivatableNotificationViewController = mock()
    private val rivSubComponentFactory: RemoteInputViewSubcomponent.Factory = mock()
    private val metricsLogger: MetricsLogger = mock()
    private val logBufferLogger = NotificationRowLogger(logcatLogBuffer(), logcatLogBuffer())
    private val colorUpdateLogger: ColorUpdateLogger = mock()
    private val listContainer: NotificationListContainer = mock()
    private val smartReplyConstants: SmartReplyConstants = mock()
    private val smartReplyController: SmartReplyController = mock()
    private val pluginManager: PluginManager = mock()
    private val systemClock: SystemClock = mock()
    private val keyguardBypassController: KeyguardBypassController = mock()
    private val groupMembershipManager: GroupMembershipManager = mock()
    private val groupExpansionManager: GroupExpansionManager = mock()
    private val rowContentBindStage: RowContentBindStage = mock()
    private val notifLogger: NotificationRowStatsLogger = mock()
    private val headsUpManager: HeadsUpManager = mock()
    private val onExpandClickListener: ExpandableNotificationRow.OnExpandClickListener = mock()
    private val statusBarStateController: StatusBarStateController = mock()
    private val gutsManager: NotificationGutsManager = mock()
    private val onUserInteractionCallback: OnUserInteractionCallback = mock()
    private val falsingManager: FalsingManager = mock()
    private val featureFlags: FeatureFlagsClassic = mock()
    private val peopleNotificationIdentifier: PeopleNotificationIdentifier = mock()
    private val settingsController: NotificationSettingsController = mock()
    private val dragController: ExpandableNotificationRowDragController = mock()
    private val dismissibilityProvider: NotificationDismissibilityProvider = mock()
    private val statusBarService: IStatusBarService = mock()
    private val uiEventLogger: UiEventLogger = mock()
    private val msdlPlayer: MSDLPlayer = mock()
    private val rebindingTracker: NotificationRebindingTracker = mock()
    private val entryAdapterFactory: EntryAdapterFactory = mock()
    private val bundleInteractionLogger: BundleInteractionLogger = mock()
    private lateinit var controller: ExpandableNotificationRowController
    private val notificationActivityStarter: NotificationActivityStarter = mock()

    @Before
    fun setUp() {
        entry = kosmos.buildNotificationEntry()
        view = spy(kosmos.createRowWithEntry(entry))

        allowTestableLooperAsMainThread()
        controller = initController(view)
    }

    private fun initController(
        row: ExpandableNotificationRow
    ): ExpandableNotificationRowController {
        return ExpandableNotificationRowController(
            row,
            activableNotificationViewController,
            rivSubComponentFactory,
            metricsLogger,
            colorUpdateLogger,
            logBufferLogger,
            NotificationChildrenContainerLogger(logcatLogBuffer()),
            listContainer,
            smartReplyConstants,
            smartReplyController,
            pluginManager,
            systemClock,
            context,
            keyguardBypassController,
            groupMembershipManager,
            groupExpansionManager,
            rowContentBindStage,
            notifLogger,
            headsUpManager,
            onExpandClickListener,
            statusBarStateController,
            gutsManager,
            /*allowLongPress=*/ false,
            onUserInteractionCallback,
            falsingManager,
            featureFlags,
            peopleNotificationIdentifier,
            settingsController,
            dragController,
            dismissibilityProvider,
            statusBarService,
            uiEventLogger,
            msdlPlayer,
            rebindingTracker,
            entryAdapterFactory,
            kosmos.windowRootViewBlurInteractor,
            bundleInteractionLogger,
            notificationActivityStarter,
        )
    }

    @After
    fun tearDown() {
        disallowTestableLooperAsMainThread()
    }

    @Test
    fun offerKeepInParent_parentDismissed() {
        entry.dismissState = NotificationEntry.DismissState.PARENT_DISMISSED

        assertThat(controller.offerToKeepInParentForAnimation()).isTrue()
        assertThat(view.keepInParentForDismissAnimation()).isTrue()
    }

    @Test
    fun offerKeepInParent_parentNotDismissed() {
        assertThat(controller.offerToKeepInParentForAnimation()).isFalse()
        assertThat(view.keepInParentForDismissAnimation()).isFalse()
    }

    @Test
    fun removeFromParent_keptForAnimation() {
        val parentView: ExpandableNotificationRow = mock()
        view.setIsChildInGroup(true, parentView)
        view.setKeepInParentForDismissAnimation(true)

        assertThat(controller.removeFromParentIfKeptForAnimation()).isTrue()
        verify(parentView).removeChildNotification(view)
    }

    @Test
    fun removeFromParent_notKeptForAnimation() {
        val parentView: ExpandableNotificationRow = mock()
        view.setIsChildInGroup(true, parentView)

        assertThat(controller.removeFromParentIfKeptForAnimation()).isFalse()
    }

    @Test
    fun removeChild_whenTransfer() {
        var rowGroup = kosmos.createRowGroup()
        val controllerGroup = initController(rowGroup)
        val firstChild = rowGroup.getChildNotificationAt(0)!!
        val childNodeController = FakeNodeController(firstChild)

        // GIVEN a child is removed for transfer
        controllerGroup.removeChild(childNodeController, /* isTransfer= */ true)

        // VERIFY the listContainer is not notified
        assertThat(firstChild.isChangingPosition).isTrue()
        assertThat(rowGroup.getChildNotificationAt(0)).isNotEqualTo(firstChild)
        verify(listContainer, never()).notifyGroupChildRemoved(any(), any())
    }

    @Test
    fun removeChild_whenNotTransfer() {
        var rowGroup = kosmos.createRowGroup()
        val controllerGroup = initController(rowGroup)
        val firstChild = rowGroup.getChildNotificationAt(0)!!
        val childNodeController = FakeNodeController(firstChild)

        // GIVEN a child is removed for real
        controllerGroup.removeChild(childNodeController, /* isTransfer= */ false)

        // VERIFY the listContainer is passed the childrenContainer for transient animations
        assertThat(firstChild.isChangingPosition).isFalse()
        assertThat(rowGroup.getChildNotificationAt(0)).isNotEqualTo(firstChild)
        verify(listContainer).notifyGroupChildRemoved(eq(firstChild), any())
    }

    @Test
    fun registerSettingsListener_forBubbles() {
        val row: ExpandableNotificationRow = mock()
        val entryLegacy: NotificationEntry = mock()
        controller = initController(row)
        controller.init(entryLegacy)
        val entryAdapter = mock(EntryAdapter::class.java)
        whenever(entryAdapter.sbn).thenReturn(mock(StatusBarNotification::class.java))
        whenever(row.entryAdapter).thenReturn(entryAdapter)
        whenever(row.entryLegacy).thenReturn(mock())
        val captor = ArgumentCaptor.forClass(View.OnAttachStateChangeListener::class.java)
        verify(row, atLeastOnce()).addOnAttachStateChangeListener(captor.capture())
        captor.allValues[0].onViewAttachedToWindow(view)

        verify(settingsController).addCallback(any(), any())
    }

    @Test
    fun unregisterSettingsListener_forBubbles() {
        val row: ExpandableNotificationRow = mock()
        val entryLegacy: NotificationEntry = mock()
        controller = initController(row)
        controller.init(entryLegacy)
        val entryAdapter = mock(EntryAdapter::class.java)
        whenever(entryAdapter.sbn).thenReturn(mock(StatusBarNotification::class.java))
        whenever(row.entryAdapter).thenReturn(entryAdapter)
        whenever(row.entryLegacy).thenReturn(entryLegacy)
        val captor = ArgumentCaptor.forClass(View.OnAttachStateChangeListener::class.java)
        verify(row, atLeastOnce()).addOnAttachStateChangeListener(captor.capture())
        captor.allValues[0].onViewDetachedFromWindow(view)

        verify(settingsController).removeCallback(any(), any())
    }

    @Test
    fun settingsListener_invalidUri() {
        controller.mSettingsListener.onSettingChanged(Uri.EMPTY, entry.sbn.userId, "1")
        assertThat(
                view.privateLayout.shouldShowBubbleButton(
                    if (NotificationBundleUi.isEnabled) null else entry
                )
            )
            .isFalse()
    }

    @Test
    fun settingsListener_invalidUserId() {
        controller.mSettingsListener.onSettingChanged(BUBBLES_SETTING_URI, -1000, "1")
        controller.mSettingsListener.onSettingChanged(BUBBLES_SETTING_URI, -1000, null)

        assertThat(
                view.privateLayout.shouldShowBubbleButton(
                    if (NotificationBundleUi.isEnabled) null else entry
                )
            )
            .isFalse()
    }

    @Test
    fun settingsListener_validUserId() {
        val childView: NotificationContentView = mock()
        view.privateLayout = childView

        controller.mSettingsListener.onSettingChanged(BUBBLES_SETTING_URI, entry.sbn.userId, "1")
        verify(childView).setBubblesEnabledForUser(true)

        controller.mSettingsListener.onSettingChanged(BUBBLES_SETTING_URI, entry.sbn.userId, "9")
        verify(childView).setBubblesEnabledForUser(false)
    }

    @Test
    fun settingsListener_userAll() {
        val entryAll =
            kosmos.buildNotificationEntry {
                setUser(UserHandle.ALL)
                setUid(UserHandle.ALL.getUid(1234))
            }
        val row = kosmos.createRowWithEntry(entryAll)
        val controllerUser = initController(row)

        val childView: NotificationContentView = mock()
        row.privateLayout = childView

        controllerUser.mSettingsListener.onSettingChanged(BUBBLES_SETTING_URI, 9, "1")
        verify(childView).setBubblesEnabledForUser(true)

        controllerUser.mSettingsListener.onSettingChanged(BUBBLES_SETTING_URI, 1, "0")
        verify(childView).setBubblesEnabledForUser(false)
    }
}
