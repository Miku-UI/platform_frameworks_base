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

package com.android.systemui.statusbar.notification.row

import android.annotation.DimenRes
import android.content.res.Resources
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.service.notification.StatusBarNotification
import android.testing.TestableLooper
import android.testing.ViewUtils
import android.view.NotificationHeaderView
import android.view.NotificationTopLineView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.widget.NotificationActionListLayout
import com.android.internal.widget.NotificationExpandButton
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.collection.EntryAdapterFactory
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.makeEntryOfPeopleType
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier
import com.android.systemui.statusbar.notification.people.peopleNotificationIdentifier
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class NotificationContentViewTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val factory: EntryAdapterFactory = kosmos.entryAdapterFactory

    private lateinit var row: ExpandableNotificationRow
    private lateinit var fakeParent: ViewGroup

    private val testableResources = mContext.getOrCreateTestableResources()
    private val contractedHeight =
        px(com.android.systemui.res.R.dimen.min_notification_layout_height)
    private val expandedHeight = px(com.android.systemui.res.R.dimen.notification_max_height)

    @Before
    fun setup() {
        initMocks(this)
        fakeParent =
            spy(FrameLayout(mContext, /* attrs= */ null)).also { it.visibility = View.GONE }

        val entry = kosmos.makeEntryOfPeopleType()
        val entryAdapter = factory.create(entry)

        row =
            spy(
                ExpandableNotificationRow(mContext, /* attrs= */ null, UserHandle.CURRENT).apply {
                    this.entryAdapter = entryAdapter
                }
            )
        ViewUtils.attachView(fakeParent)
    }

    @After
    fun teardown() {
        fakeParent.removeAllViews()
        ViewUtils.detachView(fakeParent)
    }

    @Test
    fun contractedWrapperSelected_whenShadeIsClosed_wrapperNotNotified() {
        // GIVEN the shade is closed
        fakeParent.visibility = View.GONE

        // WHEN a collapsed content is created
        val view = createContentView(isSystemExpanded = false)

        // THEN the contractedWrapper is set
        assertEquals(view.contractedWrapper, view.visibleWrapper)
        // AND the contractedWrapper is visible, but NOT shown
        verify(view.contractedWrapper).setVisible(true)
        verify(view.contractedWrapper, never()).onContentShown(any())
    }

    @Test
    fun contractedWrapperSelected_whenShadeIsOpen_wrapperNotified() {
        // GIVEN the shade is open
        fakeParent.visibility = View.VISIBLE

        // WHEN a collapsed content is created
        val view = createContentView(isSystemExpanded = false)

        // THEN the contractedWrapper is set
        assertEquals(view.contractedWrapper, view.visibleWrapper)
        // AND the contractedWrapper is visible and shown
        verify(view.contractedWrapper, atLeastOnce()).setVisible(true)
        verify(view.contractedWrapper, times(1)).onContentShown(true)
    }

    @Test
    fun shadeOpens_collapsedWrapperIsSelected_wrapperNotified() {
        // GIVEN the shade is closed
        fakeParent.visibility = View.GONE
        // AND a collapsed content is created
        val view = createContentView(isSystemExpanded = false).apply { clearInvocations() }

        // WHEN the shade opens
        fakeParent.visibility = View.VISIBLE
        view.onVisibilityAggregated(true)

        // THEN the contractedWrapper is set
        assertEquals(view.contractedWrapper, view.visibleWrapper)
        // AND the contractedWrapper is shown
        verify(view.contractedWrapper, times(1)).onContentShown(true)
    }

    @Test
    fun shadeCloses_collapsedWrapperIsShown_wrapperNotified() {
        // GIVEN the shade is closed
        fakeParent.visibility = View.VISIBLE
        // AND a collapsed content is created
        val view = createContentView(isSystemExpanded = false).apply { clearInvocations() }

        // WHEN the shade opens
        fakeParent.visibility = View.GONE
        view.onVisibilityAggregated(false)

        // THEN the contractedWrapper is set
        assertEquals(view.contractedWrapper, view.visibleWrapper)
        // AND the contractedWrapper is NOT shown
        verify(view.contractedWrapper, times(1)).onContentShown(false)
    }

    @Test
    fun expandedWrapperSelected_whenShadeIsClosed_wrapperNotNotified() {
        // GIVEN the shade is closed
        fakeParent.visibility = View.GONE

        // WHEN a system-expanded content is created
        val view = createContentView(isSystemExpanded = true)

        // THEN the contractedWrapper is set
        assertEquals(view.expandedWrapper, view.visibleWrapper)
        // AND the contractedWrapper is visible, but NOT shown
        verify(view.expandedWrapper, atLeastOnce()).setVisible(true)
        verify(view.expandedWrapper, never()).onContentShown(any())
    }

    @Test
    fun expandedWrapperSelected_whenShadeIsOpen_wrapperNotified() {
        // GIVEN the shade is open
        fakeParent.visibility = View.VISIBLE

        // WHEN an system-expanded content is created
        val view = createContentView(isSystemExpanded = true)

        // THEN the expandedWrapper is set
        assertEquals(view.expandedWrapper, view.visibleWrapper)
        // AND the expandedWrapper is visible and shown
        verify(view.expandedWrapper, atLeastOnce()).setVisible(true)
        verify(view.expandedWrapper, times(1)).onContentShown(true)
    }

    @Test
    fun shadeOpens_expandedWrapperIsSelected_wrapperNotified() {
        // GIVEN the shade is closed
        fakeParent.visibility = View.GONE
        // AND a system-expanded content is created
        val view = createContentView(isSystemExpanded = true).apply { clearInvocations() }

        // WHEN the shade opens
        fakeParent.visibility = View.VISIBLE
        view.onVisibilityAggregated(true)

        // THEN the expandedWrapper is set
        assertEquals(view.expandedWrapper, view.visibleWrapper)
        // AND the expandedWrapper is shown
        verify(view.expandedWrapper, times(1)).onContentShown(true)
    }

    @Test
    fun shadeCloses_expandedWrapperIsShown_wrapperNotified() {
        // GIVEN the shade is open
        fakeParent.visibility = View.VISIBLE
        // AND a system-expanded content is created
        val view = createContentView(isSystemExpanded = true).apply { clearInvocations() }

        // WHEN the shade opens
        fakeParent.visibility = View.GONE
        view.onVisibilityAggregated(false)

        // THEN the expandedWrapper is set
        assertEquals(view.expandedWrapper, view.visibleWrapper)
        // AND the expandedWrapper is NOT shown
        verify(view.expandedWrapper, times(1)).onContentShown(false)
    }

    @Test
    fun expandCollapsedNotification_expandedWrapperShown() {
        // GIVEN the shade is open
        fakeParent.visibility = View.VISIBLE
        // AND a collapsed content is created
        val view = createContentView(isSystemExpanded = false).apply { clearInvocations() }

        // WHEN we collapse the notification
        whenever(row.intrinsicHeight).thenReturn(expandedHeight)
        view.contentHeight = expandedHeight

        // THEN the wrappers are updated
        assertEquals(view.expandedWrapper, view.visibleWrapper)
        verify(view.contractedWrapper, times(1)).onContentShown(false)
        verify(view.contractedWrapper).setVisible(false)
        verify(view.expandedWrapper, times(1)).onContentShown(true)
        verify(view.expandedWrapper).setVisible(true)
    }

    @Test
    fun collapseExpandedNotification_expandedWrapperShown() {
        // GIVEN the shade is open
        fakeParent.visibility = View.VISIBLE
        // AND a system-expanded content is created
        val view = createContentView(isSystemExpanded = true).apply { clearInvocations() }

        // WHEN we collapse the notification
        whenever(row.intrinsicHeight).thenReturn(contractedHeight)
        view.contentHeight = contractedHeight

        // THEN the wrappers are updated
        assertEquals(view.contractedWrapper, view.visibleWrapper)
        verify(view.expandedWrapper, times(1)).onContentShown(false)
        verify(view.expandedWrapper).setVisible(false)
        verify(view.contractedWrapper, times(1)).onContentShown(true)
        verify(view.contractedWrapper).setVisible(true)
    }

    @Test
    fun testExpandButtonFocusIsCalled() {
        val mockContractedEB = mock<NotificationExpandButton>()
        val mockContracted = createMockNotificationHeaderView(contractedHeight, mockContractedEB)

        val mockExpandedEB = mock<NotificationExpandButton>()
        val mockExpanded = createMockNotificationHeaderView(expandedHeight, mockExpandedEB)

        val mockHeadsUpEB = mock<NotificationExpandButton>()
        val mockHeadsUp = createMockNotificationHeaderView(contractedHeight, mockHeadsUpEB)

        val view = createContentView(isSystemExpanded = false)

        // Update all 3 child forms
        view.apply {
            contractedChild = mockContracted
            expandedChild = mockExpanded
            headsUpChild = mockHeadsUp

            expandedWrapper = spy(expandedWrapper)
        }

        // This is required to call requestAccessibilityFocus()
        view.setFocusOnVisibilityChange()

        // The following will initialize the view and switch from not visible to expanded.
        // (heads-up is actually an alternate form of contracted, hence this enters expanded state)
        view.setHeadsUp(true)
        assertEquals(view.expandedWrapper, view.visibleWrapper)
        verify(mockContractedEB, never()).requestAccessibilityFocus()
        verify(mockExpandedEB).requestAccessibilityFocus()
        verify(mockHeadsUpEB, never()).requestAccessibilityFocus()
    }

    private fun createMockNotificationHeaderView(
        height: Int,
        mockExpandedEB: NotificationExpandButton,
    ) =
        spy(NotificationHeaderView(mContext, /* attrs= */ null).apply { minimumHeight = height })
            .apply {
                whenever(this.animate()).thenReturn(mock())
                whenever(this.findViewById<View>(R.id.expand_button)).thenReturn(mockExpandedEB)
                whenever(this.findViewById<View>(R.id.notification_top_line))
                    .thenReturn(mock<NotificationTopLineView>())
            }

    @Test
    fun testRemoteInputVisibleSetsActionsUnimportantHideDescendantsForAccessibility() {
        val mockContracted = spy(createViewWithHeight(contractedHeight))

        val mockExpandedActions = mock<NotificationActionListLayout>()
        val mockExpanded = spy(createViewWithHeight(expandedHeight))
        whenever(mockExpanded.findViewById<View>(R.id.actions)).thenReturn(mockExpandedActions)

        val mockHeadsUpActions = mock<NotificationActionListLayout>()
        val mockHeadsUp = spy(createViewWithHeight(contractedHeight))
        whenever(mockHeadsUp.findViewById<View>(R.id.actions)).thenReturn(mockHeadsUpActions)

        val view =
            createContentView(
                isSystemExpanded = false,
                contractedView = mockContracted,
                expandedView = mockExpanded,
                headsUpView = mockHeadsUp,
            )

        view.setRemoteInputVisible(true)

        verify(mockContracted, never()).findViewById<View>(0)
        verify(mockExpandedActions).importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        verify(mockHeadsUpActions).importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    @Test
    fun testRemoteInputInvisibleSetsActionsAutoImportantForAccessibility() {
        val mockContracted = spy(createViewWithHeight(contractedHeight))

        val mockExpandedActions = mock<NotificationActionListLayout>()
        val mockExpanded = spy(createViewWithHeight(expandedHeight))
        whenever(mockExpanded.findViewById<View>(R.id.actions)).thenReturn(mockExpandedActions)

        val mockHeadsUpActions = mock<NotificationActionListLayout>()
        val mockHeadsUp = spy(createViewWithHeight(contractedHeight))
        whenever(mockHeadsUp.findViewById<View>(R.id.actions)).thenReturn(mockHeadsUpActions)

        val view =
            createContentView(
                isSystemExpanded = false,
                contractedView = mockContracted,
                expandedView = mockExpanded,
                headsUpView = mockHeadsUp,
            )

        view.setRemoteInputVisible(false)

        verify(mockContracted, never()).findViewById<View>(0)
        verify(mockExpandedActions).importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        verify(mockHeadsUpActions).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    @Test
    fun onSetAnimationRunning() {
        // Given: contractedWrapper, enpandedWrapper, and headsUpWrapper being set
        val view = createContentView(isSystemExpanded = false)

        // When: we set content animation running.
        assertTrue(view.setContentAnimationRunning(true))

        // Then: contractedChild, expandedChild, and headsUpChild should have setAnimationsRunning
        // called on them.
        verify(view.contractedWrapper, times(1)).setAnimationsRunning(true)
        verify(view.expandedWrapper, times(1)).setAnimationsRunning(true)
        verify(view.headsUpWrapper, times(1)).setAnimationsRunning(true)

        // When: we set content animation running true _again_.
        assertFalse(view.setContentAnimationRunning(true))

        // Then: the children should not have setAnimationRunning called on them again.
        // Verify counts number of calls so far on the object, so these still register as 1.
        verify(view.contractedWrapper, times(1)).setAnimationsRunning(true)
        verify(view.expandedWrapper, times(1)).setAnimationsRunning(true)
        verify(view.headsUpWrapper, times(1)).setAnimationsRunning(true)
    }

    @Test
    fun onSetAnimationStopped() {
        // Given: contractedWrapper, expandedWrapper, and headsUpWrapper being set
        val view = createContentView(isSystemExpanded = false)

        // When: we set content animation running.
        assertTrue(view.setContentAnimationRunning(true))

        // Then: contractedChild, expandedChild, and headsUpChild should have setAnimationsRunning
        // called on them.
        verify(view.contractedWrapper).setAnimationsRunning(true)
        verify(view.expandedWrapper).setAnimationsRunning(true)
        verify(view.headsUpWrapper).setAnimationsRunning(true)

        // When: we set content animation running false, the state changes, so the function
        // returns true.
        assertTrue(view.setContentAnimationRunning(false))

        // Then: the children have their animations stopped.
        verify(view.contractedWrapper).setAnimationsRunning(false)
        verify(view.expandedWrapper).setAnimationsRunning(false)
        verify(view.headsUpWrapper).setAnimationsRunning(false)
    }

    @Test
    fun onSetAnimationInitStopped() {
        // Given: contractedWrapper, expandedWrapper, and headsUpWrapper being set
        val view = createContentView(isSystemExpanded = false)

        // When: we try to stop the animations before they've been started.
        assertFalse(view.setContentAnimationRunning(false))

        // Then: the children should not have setAnimationRunning called on them again.
        verify(view.contractedWrapper, never()).setAnimationsRunning(false)
        verify(view.expandedWrapper, never()).setAnimationsRunning(false)
        verify(view.headsUpWrapper, never()).setAnimationsRunning(false)
    }

    @Test
    fun notifySubtreeAccessibilityStateChanged_notifiesParent() {
        // Given: a contentView is created
        val view = createContentView()
        clearInvocations(fakeParent)

        // When: the contentView is notified for an A11y change
        view.notifySubtreeAccessibilityStateChanged(view.contractedChild, view.contractedChild, 0)

        // Then: the contentView propagates the event to its parent
        verify(fakeParent).notifySubtreeAccessibilityStateChanged(any(), any(), any())
    }

    @Test
    fun notifySubtreeAccessibilityStateChanged_animatingContentView_dontNotifyParent() {
        // Given: a collapsed contentView is created
        val view = createContentView()
        clearInvocations(fakeParent)

        // And: it is animating to expanded
        view.setAnimationStartVisibleType(NotificationContentView.VISIBLE_TYPE_EXPANDED)

        // When: the contentView is notified for an A11y change
        view.notifySubtreeAccessibilityStateChanged(view.contractedChild, view.contractedChild, 0)

        // Then: the contentView DOESN'T propagates the event to its parent
        verify(fakeParent, never()).notifySubtreeAccessibilityStateChanged(any(), any(), any())
    }

    @Test
    fun testGetVisualTypeForHeight_headsUp() {
        val view =
            createContentView(
                headsUpView = createViewWithSpecificHeight(100),
                contractedView = createViewWithSpecificHeight(100),
                expandedView = createViewWithSpecificHeight(200),
            )
        view.setHeadsUp(true)
        whenever(row.canShowHeadsUp()).thenReturn(true)

        // Height less than heads up height
        var result = view.getVisualTypeForHeight(50f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_HEADSUP)

        // Height equal to heads up height
        result = view.getVisualTypeForHeight(100f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_HEADSUP)
    }

    @Test
    fun testGetVisualTypeForHeight_headsUpAnimatingAway() {
        val view =
            createContentView(
                headsUpView = createViewWithSpecificHeight(100),
                contractedView = createViewWithSpecificHeight(100),
                expandedView = createViewWithSpecificHeight(200),
            )
        view.setHeadsUp(false)
        view.setHeadsUpAnimatingAway(true)

        // Height less than heads up height
        var result = view.getVisualTypeForHeight(50f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_HEADSUP)

        // Height equal to heads up height
        result = view.getVisualTypeForHeight(100f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_HEADSUP)
    }

    @Test
    fun testGetVisualTypeForHeight_expanded() {
        val view =
            createContentView(
                contractedView = createViewWithSpecificHeight(100),
                expandedView = createViewWithSpecificHeight(200),
            )

        // Height greater than contracted height
        var result = view.getVisualTypeForHeight(150f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_EXPANDED)

        // Height equal to expanded height
        result = view.getVisualTypeForHeight(200f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_EXPANDED)
    }

    @Test
    fun testGetVisualTypeForHeight_singleLine() {
        val view = createContentView()
        view.singleLineView = spy(HybridNotificationView(mContext, null))
        view.setIsChildInGroup(true)
        whenever(row.isParentGroupExpanded).thenReturn(false)

        val result = view.getVisualTypeForHeight(50f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_SINGLELINE)
    }

    @Test
    fun testGetVisualTypeForHeight_contracted() {
        val view = createContentView(contractedView = createViewWithSpecificHeight(100))

        // Height less than contracted height
        var result = view.getVisualTypeForHeight(50f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)

        // Height equal to contracted height
        result = view.getVisualTypeForHeight(100f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)
    }

    @Test
    fun testGetVisualTypeForHeight_headsUpButTaller() {
        val view =
            createContentView(
                headsUpView = createViewWithSpecificHeight(100),
                contractedView = createViewWithSpecificHeight(100),
                expandedView = createViewWithSpecificHeight(200),
            )
        view.setHeadsUp(true)
        whenever(row.canShowHeadsUp()).thenReturn(true)

        // Height greater than heads up height
        val result = view.getVisualTypeForHeight(150f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_EXPANDED)
    }

    @Test
    fun testGetVisualTypeForHeight_contracted_childInGroup_groupExpanded() {
        val view = createContentView(contractedView = createViewWithSpecificHeight(100))
        val mockNotificationEntry = kosmos.makeEntryOfPeopleType()
        val row = createMockContainingNotification(mockNotificationEntry)
        view.setContainingNotification(row)
        view.setIsChildInGroup(true)
        whenever(row.isGroupExpanded).thenReturn(true)

        val result = view.getVisualTypeForHeight(100f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)
    }

    @Test
    fun testGetVisualTypeForHeight_contracted_childInGroup_notifNotExpanded() {
        val view = createContentView(contractedView = createViewWithSpecificHeight(100))
        val mockNotificationEntry = kosmos.makeEntryOfPeopleType()
        val row = createMockContainingNotification(mockNotificationEntry)
        view.setContainingNotification(row)
        whenever(row.isGroupExpanded()).thenReturn(false)

        whenever(row.isExpanded(true)).thenReturn(false)
        view.setIsChildInGroup(true)

        val result = view.getVisualTypeForHeight(100f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)
    }

    @Test
    fun testGetVisualTypeForHeight_headsUp_noExpanded() {
        val view =
            createContentView(
                headsUpView = createViewWithSpecificHeight(100),
                contractedView = createViewWithSpecificHeight(100),
            )
        view.expandedChild = null // No expanded child
        view.mockRequestLayout()

        view.setHeadsUp(true)
        whenever(row.canShowHeadsUp()).thenReturn(true)

        // Height less than heads up height
        var result = view.getVisualTypeForHeight(50f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_HEADSUP)

        // Height greater than heads up height
        result = view.getVisualTypeForHeight(150f)
        // Stays HeadsUp
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_HEADSUP)
    }

    @Test
    fun testGetVisualTypeForHeight_noExpanded_alwaysContractedUnlessSpecial() {
        val view = createContentView()
        view.contractedChild = createViewWithSpecificHeight(100)
        view.expandedChild = null // No expanded child
        view.headsUpChild = null // No heads up
        view.singleLineView = null // No single line
        view.mockRequestLayout()

        // Height less than contracted
        var result = view.getVisualTypeForHeight(50f)
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)

        // Height greater than contracted
        result = view.getVisualTypeForHeight(150f)
        // Defaults to Contracted
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)
    }

    @EnableFlags(android.app.Flags.FLAG_RICH_ONGOING_IMPROVEMENTS)
    @Test
    fun getVisualTypeForHeight_promoted_canShowExpanded_returnsExpanded() {
        // GIVEN: is promoted, can show expanded, and has expanded child
        val view =
            createContentView(
                contractedView = createViewWithSpecificHeight(100),
                expandedView = createViewWithSpecificHeight(200),
            )
        whenever(row.isPromotedOngoing).thenReturn(true)
        whenever(row.canPromotedNotificationShowExpanded(false)).thenReturn(true)

        // WHEN height would normally be contracted
        val result = view.getVisualTypeForHeight(100f)

        // THEN returns EXPANDED due to rich ongoing
        assertEquals(NotificationContentView.VISIBLE_TYPE_EXPANDED, result)
    }

    @EnableFlags(android.app.Flags.FLAG_RICH_ONGOING_IMPROVEMENTS)
    @Test
    fun getVisualTypeForHeight_promoted_cannotShowExpanded_returnsContracted() {
        // GIVEN: cannot show promoted notification expanded
        val view =
            createContentView(
                contractedView = createViewWithSpecificHeight(100),
                expandedView = createViewWithSpecificHeight(200),
            )
        whenever(row.isPromotedOngoing).thenReturn(true)
        whenever(row.canPromotedNotificationShowExpanded(false)).thenReturn(false)

        val result = view.getVisualTypeForHeight(100f)

        // THEN returns CONTRACTED
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)
    }

    @EnableFlags(android.app.Flags.FLAG_RICH_ONGOING_IMPROVEMENTS)
    @Test
    fun getVisualTypeForHeight_promoted_noExpandedChild_returnsContracted() {
        // GIVEN: no expanded child
        val view = createContentView(contractedView = createViewWithSpecificHeight(100))
        view.expandedChild = null
        view.mockRequestLayout()
        whenever(row.isPromotedOngoing).thenReturn(true)
        whenever(row.canPromotedNotificationShowExpanded(false)).thenReturn(true)

        val result = view.getVisualTypeForHeight(100f)

        // THEN returns CONTRACTED
        assertThat(result).isEqualTo(NotificationContentView.VISIBLE_TYPE_CONTRACTED)
    }

    private fun createMockContainingNotification(notificationEntry: NotificationEntry) =
        mock<ExpandableNotificationRow>().apply {
            whenever(this.context).thenReturn(mContext)
            whenever(this.bubbleClickListener).thenReturn(View.OnClickListener {})
            whenever(this.entryAdapter).thenReturn(factory.create(notificationEntry))
        }

    private fun createMockNotificationEntry() =
        mock<NotificationEntry>().apply {
            whenever(kosmos.peopleNotificationIdentifier.getPeopleNotificationType(this))
                .thenReturn(PeopleNotificationIdentifier.TYPE_FULL_PERSON)
            whenever(this.bubbleMetadata).thenReturn(mock())
            val sbnMock: StatusBarNotification = mock()
            val userMock: UserHandle = mock()
            whenever(this.sbn).thenReturn(sbnMock)
            whenever(sbnMock.user).thenReturn(userMock)
        }

    private fun createMockNotificationEntryAdapter() = mock<EntryAdapter>()

    private fun createLinearLayoutWithBottomMargin(bottomMargin: Int): LinearLayout {
        val outerLayout = LinearLayout(mContext)
        val innerLayout = LinearLayout(mContext)
        outerLayout.addView(innerLayout)
        val mlp = innerLayout.layoutParams as ViewGroup.MarginLayoutParams
        mlp.setMargins(0, 0, 0, bottomMargin)
        return innerLayout
    }

    private fun createMockExpandedChild() =
        spy(createViewWithHeight(expandedHeight)).apply {
            whenever(this.findViewById<ImageView>(R.id.bubble_button)).thenReturn(mock())
            val value =
                whenever(this.findViewById<FrameLayout>(R.id.actions_container)).thenReturn(mock())
            whenever(this.findViewById<LinearLayout>(R.id.actions_container_layout))
                .thenReturn(mock())
            whenever(this.context).thenReturn(mContext)

            val resourcesMock: Resources = mock()
            whenever(resourcesMock.configuration).thenReturn(mock())
            whenever(this.resources).thenReturn(resourcesMock)
        }

    private fun createContentView(
        isSystemExpanded: Boolean = false,
        contractedView: View = createViewWithHeight(contractedHeight),
        expandedView: View = createViewWithHeight(expandedHeight),
        headsUpView: View = createViewWithHeight(contractedHeight),
        row: ExpandableNotificationRow = this.row,
    ): NotificationContentView {
        val height = if (isSystemExpanded) expandedHeight else contractedHeight
        doReturn(height).whenever(row).intrinsicHeight

        return NotificationContentView(mContext, /* attrs= */ null)
            .apply {
                initialize(
                    kosmos.peopleNotificationIdentifier,
                    mock(),
                    mock(),
                    mock(),
                    mock(),
                    mock(),
                )
                setContainingNotification(row)
                setHeights(
                    /* smallHeight= */ contractedHeight,
                    /* headsUpMaxHeight= */ contractedHeight,
                    /* maxHeight= */ expandedHeight,
                )
                contractedChild = contractedView
                expandedChild = expandedView
                headsUpChild = headsUpView
                contractedWrapper = spy(contractedWrapper)
                expandedWrapper = spy(expandedWrapper)
                headsUpWrapper = spy(headsUpWrapper)

                if (isSystemExpanded) {
                    contentHeight = expandedHeight
                }
            }
            .also { contentView ->
                fakeParent.addView(contentView)
                contentView.mockRequestLayout()
                contentView.onNotificationUpdated()
            }
    }

    private fun createViewWithSpecificHeight(height: Int) =
        spy(View(mContext, /* attrs= */ null)).apply {
            minimumHeight = height
            whenever(this.height).thenReturn(height)
        }

    private fun createViewWithHeight(height: Int) =
        View(mContext, /* attrs= */ null).apply { minimumHeight = height }

    private fun getMarginBottom(layout: LinearLayout): Int =
        (layout.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

    private fun px(@DimenRes id: Int): Int = testableResources.resources.getDimensionPixelSize(id)
}

private fun NotificationContentView.mockRequestLayout() {
    measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    layout(0, 0, measuredWidth, measuredHeight)
}

private fun NotificationContentView.clearInvocations() {
    clearInvocations(contractedWrapper, expandedWrapper, headsUpWrapper)
}
