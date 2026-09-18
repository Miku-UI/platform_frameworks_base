/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.stack;


import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import android.app.Notification;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.testing.TestableLooper.RunWithLooper;
import android.view.NotificationHeaderView;
import android.view.View;
import android.widget.RemoteViews;

import androidx.compose.ui.platform.ComposeView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.kosmos.KosmosJavaAdapter;
import com.android.systemui.statusbar.notification.SourceType;
import com.android.systemui.statusbar.notification.collection.BundleSpec;
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderViewModel;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@RunWithLooper
public class NotificationChildrenContainerTest extends SysuiTestCase {

    private ExpandableNotificationRow mGroup;
    private ExpandableNotificationRow mBundle;
    private NotificationChildrenContainer mChildrenContainer;

    private final KosmosJavaAdapter mKosmos = new KosmosJavaAdapter(this);

    @Before
    public void setUp() throws Exception {
        allowTestableLooperAsMainThread();
        mGroup = mKosmos.createRowGroup();
        mChildrenContainer = mGroup.getChildrenContainer();
        mBundle = mKosmos.createRowBundle(BundleSpec.Companion.getNEWS());
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_lowPriority() {
        mChildrenContainer.setIsMinimized(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_SYSTEM_EXPANDED);
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_headsUp() {
        mGroup.setHeadsUp(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_SYSTEM_EXPANDED);
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_lowPriority_expandedChildren() {
        mChildrenContainer.setIsMinimized(true);
        mChildrenContainer.setChildrenExpanded(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_SYSTEM_EXPANDED);
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_lowPriority_userSwipingToExpandRow() {
        mChildrenContainer.setIsMinimized(true);
        mChildrenContainer.setUserSwipingToExpandRow(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_SYSTEM_EXPANDED);
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_bundle_userSwipingToExpandRow() {
        ComposeView headerView = new ComposeView(mContext);
        mBundle.setBundleHeaderView(headerView);

        NotificationChildrenContainer childrenContainer = mBundle.getChildrenContainer();
        childrenContainer.setBundleHeaderViewModel(mock(BundleHeaderViewModel.class));
        mBundle.setUserSwipingToExpandRow(true);

        Assert.assertEquals(
                "During swipe open, bundle should show the expanded number of children",
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_BUNDLE_EXPANDED,
                childrenContainer.getMaxAllowedVisibleChildren());
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_likeCollapsed() {
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(true),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_COLLAPSED);
    }


    @Test
    public void testGetMaxAllowedVisibleChildren_expandedChildren() {
        mChildrenContainer.setChildrenExpanded(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_CHILDREN_EXPANDED);
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_userSwiping() {
        mGroup.setUserSwipingToExpandRow(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_CHILDREN_EXPANDED);
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_bundle_likeCollapsed() {
        ComposeView headerView = new ComposeView(mContext);
        // This will initialize the bundle's children container
        mBundle.setBundleHeaderView(headerView);

        NotificationChildrenContainer childrenContainer = mBundle.getChildrenContainer();
        childrenContainer.setBundleHeaderViewModel(mock(BundleHeaderViewModel.class));
        Assert.assertEquals(childrenContainer.getMaxAllowedVisibleChildren(true),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_BUNDLE_COLLAPSED);
    }


    @Test
    public void testGetMaxAllowedVisibleChildren_bundle_expandedChildren() {
        ComposeView headerView = new ComposeView(mContext);
        mBundle.setBundleHeaderView(headerView);

        NotificationChildrenContainer childrenContainer = mBundle.getChildrenContainer();
        childrenContainer.setBundleHeaderViewModel(mock(BundleHeaderViewModel.class));
        childrenContainer.getContainingNotification().expandNotification();
        childrenContainer.setChildrenExpanded(true);
        Assert.assertEquals(childrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_BUNDLE_EXPANDED);
    }

    @Test
    public void testExpandedClipRect_bundle_expandedChildren_requiresExtraClipping() {
        ComposeView headerView = new ComposeView(mContext);
        mBundle.setBundleHeaderView(headerView);

        NotificationChildrenContainer childrenContainer = mBundle.getChildrenContainer();
        childrenContainer.setBundleHeaderViewModel(mock(BundleHeaderViewModel.class));
        childrenContainer.getContainingNotification().expandNotification();
        childrenContainer.setChildrenExpanded(true);
        Assert.assertNotNull(childrenContainer.getExpandedClipRect(mGroup));
        Assert.assertTrue(childrenContainer.childNeedsExpandedClipPath(mGroup));
    }

    @Test
    public void testExpandedClipRect_bundle_notExpandedChildren_doesNotRequireExtraClipping() {
        ComposeView headerView = new ComposeView(mContext);
        mBundle.setBundleHeaderView(headerView);

        NotificationChildrenContainer childrenContainer = mBundle.getChildrenContainer();
        childrenContainer.setBundleHeaderViewModel(mock(BundleHeaderViewModel.class));
        childrenContainer.getContainingNotification().expandNotification();
        childrenContainer.setChildrenExpanded(false);
        Assert.assertFalse(childrenContainer.childNeedsExpandedClipPath(mGroup));
    }

    @Test
    public void testShowingAsLowPriority_lowPriority() {
        mChildrenContainer.setIsMinimized(true);
        Assert.assertTrue(mChildrenContainer.showingAsLowPriority());
    }

    @Test
    public void testShowingAsLowPriority_notLowPriority() {
        Assert.assertFalse(mChildrenContainer.showingAsLowPriority());
    }

    @Test
    public void testShowingAsLowPriority_lowPriority_expanded() {
        mChildrenContainer.setIsMinimized(true);
        mGroup.setExpandable(true);
        mGroup.setUserExpanded(true, false);
        Assert.assertFalse(mChildrenContainer.showingAsLowPriority());
    }

    @Test
    public void testGetMaxAllowedVisibleChildren_userSwiping_expandedChildren_lowPriority() {
        mGroup.setUserSwipingToExpandRow(true);
        mGroup.setExpandable(true);
        mGroup.setUserExpanded(true);
        mChildrenContainer.setIsMinimized(true);
        Assert.assertEquals(mChildrenContainer.getMaxAllowedVisibleChildren(),
                NotificationChildrenContainer.NUMBER_OF_CHILDREN_WHEN_CHILDREN_EXPANDED);
    }

    @Test
    public void testSetLowPriorityWithAsyncInflation_noHeaderReInflation() {
        mChildrenContainer.setLowPriorityGroupHeader(null, null);
        mChildrenContainer.setIsMinimized(true);

        // THEN
        assertNull("We don't inflate header from the main thread with Async "
                + "Inflation enabled", mChildrenContainer.getMinimizedNotificationHeader());
    }

    @Test
    public void setLowPriorityBeforeLowPriorityHeaderSet() {
        //Given: the children container does not have a low-priority header, and is not low-priority
        mChildrenContainer.setLowPriorityGroupHeader(null, null);
        mGroup.setIsMinimized(false);

        //When: set the children container to be low-priority and set the low-priority header
        mGroup.setIsMinimized(true);
        mGroup.setMinimizedGroupHeader(createHeaderView(/* lowPriorityHeader= */ true));

        //Then: the low-priority group header should be visible
        NotificationHeaderView lowPriorityHeaderView =
                mChildrenContainer.getMinimizedGroupHeaderWrapper().getNotificationHeader();
        Assert.assertEquals(View.VISIBLE, lowPriorityHeaderView.getVisibility());
        Assert.assertSame(mChildrenContainer, lowPriorityHeaderView.getParent());

        //When: set the children container to be not low-priority and set the normal header
        mGroup.setIsMinimized(false);
        mGroup.setGroupHeader(createHeaderView(/* lowPriorityHeader= */ false));

        //Then: the low-priority group header should not be visible , normal header should be
        // visible
        Assert.assertEquals(View.INVISIBLE, lowPriorityHeaderView.getVisibility());
        Assert.assertEquals(
                View.VISIBLE,
                mChildrenContainer.getNotificationHeaderWrapper().getNotificationHeader()
                        .getVisibility()
        );
    }

    @Test
    public void changeLowPriorityAfterHeaderSet() {

        //Given: the children container does not have headers, and is not low-priority
        mChildrenContainer.setLowPriorityGroupHeader(null, null);
        mChildrenContainer.setGroupHeader(null, null);
        mGroup.setIsMinimized(false);

        //When: set the set the normal header
        mGroup.setGroupHeader(createHeaderView(/* lowPriorityHeader= */ false));

        //Then: the group header should be visible
        NotificationHeaderView headerView =
                mChildrenContainer.getNotificationHeaderWrapper().getNotificationHeader();
        Assert.assertEquals(View.VISIBLE, headerView.getVisibility());
        Assert.assertSame(mChildrenContainer, headerView.getParent());

        //When: set the set the row to be low priority, and set the low-priority header
        mGroup.setIsMinimized(true);
        mGroup.setMinimizedGroupHeader(createHeaderView(/* lowPriorityHeader= */ true));

        //Then: the header view should not be visible, the low-priority group header should be
        // visible
        Assert.assertEquals(View.INVISIBLE, headerView.getVisibility());
        NotificationHeaderView lowPriorityHeaderView =
                mChildrenContainer.getMinimizedGroupHeaderWrapper().getNotificationHeader();
        Assert.assertEquals(View.VISIBLE, lowPriorityHeaderView.getVisibility());
    }

    @Test
    public void applyRoundnessAndInvalidate_should_be_immediately_applied_on_last_child() {
        List<ExpandableNotificationRow> children = mChildrenContainer.getAttachedChildren();
        ExpandableNotificationRow notificationRow = children.get(children.size() - 1);
        Assert.assertEquals(0f, mChildrenContainer.getBottomRoundness(), 0.001f);
        Assert.assertEquals(0f, notificationRow.getBottomRoundness(), 0.001f);

        mChildrenContainer.requestBottomRoundness(1f, SourceType.from(""), false);

        Assert.assertEquals(1f, mChildrenContainer.getBottomRoundness(), 0.001f);
        Assert.assertEquals(1f, notificationRow.getBottomRoundness(), 0.001f);
    }

    private NotificationHeaderView createHeaderView(boolean lowPriority) {
        Notification notification = mKosmos.buildNotificationEntry(NotificationEntryBuilder::done)
                .getSbn().getNotification();
        final Notification.Builder builder = Notification.Builder.recoverBuilder(getContext(),
                notification);
        RemoteViews headerRemoteViews;
        if (lowPriority) {
            headerRemoteViews = builder.makeLowPriorityContentView(true);
        } else {
            headerRemoteViews = builder.makeNotificationGroupHeader();
        }
        return (NotificationHeaderView) headerRemoteViews.apply(getContext(), mChildrenContainer);
    }
}
