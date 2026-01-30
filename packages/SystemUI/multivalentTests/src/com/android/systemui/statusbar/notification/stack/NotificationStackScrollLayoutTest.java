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

import static android.view.WindowInsets.Type.ime;

import static com.android.systemui.flags.SceneContainerFlagParameterizationKt.parameterizeSceneContainerFlag;
import static com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.ROWS_ALL;
import static com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.ROWS_GENTLE;
import static com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.RUBBER_BAND_FACTOR_NORMAL;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.DimenRes;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.MathUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.keyguard.BouncerPanelExpansionCalculator;
import com.android.systemui.ExpandHelper;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.BrokenWithSceneContainer;
import com.android.systemui.flags.DisableSceneContainer;
import com.android.systemui.flags.EnableSceneContainer;
import com.android.systemui.flags.FakeFeatureFlags;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.Flags;
import com.android.systemui.kosmos.KosmosJavaAdapter;
import com.android.systemui.qs.flags.QSComposeFragment;
import com.android.systemui.res.R;
import com.android.systemui.shade.QSHeaderBoundsProvider;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.shade.transition.LargeScreenShadeInterpolator;
import com.android.systemui.statusbar.NotificationShelf;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.notification.collection.EntryAdapter;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManager;
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager;
import com.android.systemui.statusbar.notification.data.repository.HeadsUpRepository;
import com.android.systemui.statusbar.notification.emptyshade.ui.view.EmptyShadeView;
import com.android.systemui.statusbar.notification.footer.ui.view.FooterView;
import com.android.systemui.statusbar.notification.headsup.AvalancheController;
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi;
import com.android.systemui.statusbar.notification.shared.NotificationThrottleHun;
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds;
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.ScreenOffAnimationController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.policy.ResourcesSplitShadeStateController;

import kotlin.Unit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests for {@link NotificationStackScrollLayout}.
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper
public class NotificationStackScrollLayoutTest extends SysuiTestCase {

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return parameterizeSceneContainerFlag();
    }

    private final FakeFeatureFlags mFeatureFlags = new FakeFeatureFlags();
    private NotificationStackScrollLayout mStackScroller;  // Normally test this
    private NotificationStackScrollLayout mStackScrollerInternal;  // See explanation below
    private AmbientState mAmbientState;
    private TestableResources mTestableResources;
    @Rule public MockitoRule mockito = MockitoJUnit.rule();
    @Mock private SysuiStatusBarStateController mBarState;
    @Mock private GroupMembershipManager mGroupMembershipManger;
    @Mock private GroupExpansionManager mGroupExpansionManager;
    @Mock private DumpManager mDumpManager;
    @Mock private ExpandHelper mExpandHelper;
    @Mock private EmptyShadeView mEmptyShadeView;
    @Mock private NotificationRoundnessManager mNotificationRoundnessManager;
    @Mock private KeyguardBypassController mBypassController;
    @Mock private NotificationSectionsManager mNotificationSectionsManager;
    @Mock private NotificationSection mNotificationSection;
    @Mock private NotificationSwipeHelper mNotificationSwipeHelper;
    @Mock private NotificationStackScrollLayoutController mStackScrollLayoutController;
    @Mock private ScreenOffAnimationController mScreenOffAnimationController;
    @Mock private NotificationShelf mNotificationShelf;
    @Mock private NotificationStackSizeCalculator mStackSizeCalculator;
    @Mock private StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    @Mock private LargeScreenShadeInterpolator mLargeScreenShadeInterpolator;
    @Mock private AvalancheController mAvalancheController;
    @Mock private HeadsUpRepository mHeadsUpRepository;
    private final KosmosJavaAdapter mKosmos = new KosmosJavaAdapter(this);

    public NotificationStackScrollLayoutTest(FlagsParameterization flags) {
        super();
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() throws Exception {
        allowTestableLooperAsMainThread();
        mTestableResources = mContext.getOrCreateTestableResources();

        // Interact with real instance of AmbientState.
        mAmbientState = spy(new AmbientState(
                mContext,
                mDumpManager,
                mNotificationSectionsManager,
                mBypassController,
                mStatusBarKeyguardViewManager,
                mLargeScreenShadeInterpolator,
                mHeadsUpRepository,
                mAvalancheController
        ));

        // Register the debug flags we use
        assertFalse(Flags.NSSL_DEBUG_LINES.getDefault());
        assertFalse(Flags.NSSL_DEBUG_REMOVE_ANIMATION.getDefault());
        mFeatureFlags.set(Flags.NSSL_DEBUG_LINES, false);
        mFeatureFlags.set(Flags.NSSL_DEBUG_REMOVE_ANIMATION, false);
        mFeatureFlags.set(Flags.LOCKSCREEN_ENABLE_LANDSCAPE, false);

        // Inject dependencies before initializing the layout
        mDependency.injectTestDependency(FeatureFlags.class, mFeatureFlags);
        mDependency.injectTestDependency(SysuiStatusBarStateController.class, mBarState);
        mDependency.injectMockDependency(ShadeController.class);
        mDependency.injectTestDependency(
                NotificationSectionsManager.class, mNotificationSectionsManager);
        mDependency.injectTestDependency(GroupMembershipManager.class, mGroupMembershipManger);
        mDependency.injectTestDependency(GroupExpansionManager.class, mGroupExpansionManager);
        mDependency.injectTestDependency(AmbientState.class, mAmbientState);
        mDependency.injectTestDependency(NotificationShelf.class, mNotificationShelf);
        mDependency.injectTestDependency(
                ScreenOffAnimationController.class, mScreenOffAnimationController);

        when(mNotificationSectionsManager.createSectionsForBuckets()).thenReturn(
                new NotificationSection[]{
                        mNotificationSection
                });

        // The actual class under test.  You may need to work with this class directly when
        // testing anonymous class members of mStackScroller, like mMenuEventListener,
        // which refer to members of NotificationStackScrollLayout. The spy
        // holds a copy of the CUT's instances of these KeyguardBypassController, so they still
        // refer to the CUT's member variables, not the spy's member variables.
        mStackScrollerInternal = new NotificationStackScrollLayout(getContext(), null);
        mStackScrollerInternal.initView(getContext(), mNotificationSwipeHelper,
                mStackSizeCalculator);
        mStackScroller = spy(mStackScrollerInternal);
        mStackScroller.setResetUserExpandedStatesRunnable(() -> {});
        mStackScroller.setEmptyShadeView(mEmptyShadeView);
        when(mStackScrollLayoutController.isHistoryEnabled()).thenReturn(true);
        when(mStackScrollLayoutController.getNotificationRoundnessManager())
                .thenReturn(mNotificationRoundnessManager);
        mStackScroller.setController(mStackScrollLayoutController);
        mStackScroller.setShelf(mNotificationShelf);
        when(mStackScroller.getExpandHelper()).thenReturn(mExpandHelper);

        doNothing().when(mGroupExpansionManager).collapseGroups();
        doNothing().when(mExpandHelper).cancelImmediately();
        doNothing().when(mNotificationShelf).setAnimationsEnabled(anyBoolean());
    }

    @Test
    @DisableSceneContainer // TODO(b/332574413) cover stack bounds integration with tests
    public void testUpdateStackHeight_qsExpansionGreaterThanZero() {
        final float expansionFraction = 0.2f;
        final float overExpansion = 50f;

        mStackScroller.setQsExpansionFraction(1f);
        mAmbientState.setExpansionFraction(expansionFraction);
        mAmbientState.setOverExpansion(overExpansion);
        when(mAmbientState.isBouncerInTransit()).thenReturn(true);


        mStackScroller.setExpandedHeight(100f);

        float expected = MathUtils.lerp(0, overExpansion,
                BouncerPanelExpansionCalculator.aboutToShowBouncerProgress(expansionFraction));
        assertThat(mAmbientState.getStackY()).isEqualTo(expected);
    }

    @Test
    @EnableSceneContainer
    public void updateInterpolatedStackHeight_qsExpanded_shouldSkipUpdateStackEndHeight() {
        // Before: QS is fully expanded
        final float expansionFraction = 0.2f;
        final float endHeight = 200f;

        mStackScroller.setQsExpandFraction(1f);
        mStackScroller.suppressHeightUpdates(true);
        mAmbientState.setExpansionFraction(expansionFraction);
        mAmbientState.setStackEndHeight(endHeight);

        // When: update StackEndHeightAndStackHeight
        mStackScroller.updateStackEndHeightAndStackHeight(expansionFraction);

        // Then: stackEndHeight is not updated
        float expected = mStackScroller
                .calculateInterpolatedStackHeight(endHeight, expansionFraction);

        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(expected);
    }

    @Test
    @EnableSceneContainer
    public void updateInterpolatedStackHeight_onLockScreenStack_shouldUpdateStackEndHeight() {
        // Before: QS is fully collapsed, on lockscreen
        final float expansionFraction = 0.2f;
        final float endHeight = 200f;

        mStackScroller.setQsExpandFraction(0f);
        mStackScroller.suppressHeightUpdates(false);
        mStackScroller.setMaxDisplayedNotifications(2);
        mAmbientState.setExpansionFraction(expansionFraction);
        mAmbientState.setStackEndHeight(endHeight);


        // When: update StackEndHeightAndStackHeight
        mStackScroller.updateStackEndHeightAndStackHeight(expansionFraction);

        // Then: updatedEndHeight should be mStackScroller.getIntrinsicStackHeight()
        float updatedEndHeight = mStackScroller.getIntrinsicStackHeight();
        float expected = mStackScroller.calculateInterpolatedStackHeight(
                updatedEndHeight, expansionFraction);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(expected);
    }

    @Test
    @EnableSceneContainer
    public void updateInterpolatedStackHeight_onExpandedShade_shouldUpdateStackEndHeight() {
        // Before: QS is fully collapsed, on expanded Shade
        final float expansionFraction = 0.2f;
        final float endHeight = 200f;

        mStackScroller.setQsExpandFraction(0f);
        mStackScroller.suppressHeightUpdates(false);
        mStackScroller.setMaxDisplayedNotifications(-1);
        mAmbientState.setExpansionFraction(expansionFraction);
        mAmbientState.setStackEndHeight(endHeight);


        // When: update StackEndHeightAndStackHeight
        mStackScroller.updateStackEndHeightAndStackHeight(expansionFraction);

        // Then: updatedEndHeight should be determined by the stack cutoffs
        float updatedEndHeight = Math.max(
                0f, mAmbientState.getStackCutoff() - mAmbientState.getStackTop());
        float expected = mStackScroller.calculateInterpolatedStackHeight(
                updatedEndHeight, expansionFraction);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(expected);
    }


    @Test
    @EnableSceneContainer
    public void testIntrinsicStackHeight() {
        int stackHeight = 300;
        when(mStackSizeCalculator.computeHeight(eq(mStackScroller), anyInt(), anyFloat(), any()))
                .thenReturn((float) stackHeight);

        mStackScroller.updateIntrinsicStackHeight();

        assertThat(mStackScroller.getIntrinsicStackHeight()).isEqualTo(stackHeight);
    }

    @Test
    @DisableSceneContainer // TODO(b/312473478): address disabled test
    public void testUpdateStackHeight_qsExpansionZero() {
        final float expansionFraction = 0.2f;
        final float overExpansion = 50f;

        mStackScroller.setQsExpansionFraction(0f);
        mAmbientState.setExpansionFraction(expansionFraction);
        mAmbientState.setOverExpansion(overExpansion);
        when(mAmbientState.isBouncerInTransit()).thenReturn(true);

        mStackScroller.setExpandedHeight(100f);

        float expected = MathUtils.lerp(0, overExpansion, expansionFraction);
        assertThat(mAmbientState.getStackY()).isEqualTo(expected);
    }

    @Test
    public void testUpdateStackHeight_withExpansionAmount_whenDozeNotChanging() {
        final float endHeight = 8f;
        final float expansionFraction = 0.5f;
        final float expected = mStackScroller.calculateInterpolatedStackHeight(
                endHeight, expansionFraction);

        mStackScroller.updateInterpolatedStackHeight(endHeight, expansionFraction);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(expected);
    }

    @Test
    @EnableSceneContainer
    public void updateStackEndHeightAndStackHeight_shadeFullyExpanded_withSceneContainer() {
        final float stackTop = 200f;
        final float stackBottom = 1000f;
        final float stackWidth = 400f;
        final float stackEndHeight = stackBottom - stackTop;
        mAmbientState.setStackTop(stackTop);
        mAmbientState.setDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));
        mAmbientState.setStatusBarState(StatusBarState.KEYGUARD);
        clearInvocations(mAmbientState);

        // WHEN shade is fully expanded
        mStackScroller.updateStackEndHeightAndStackHeight(/* fraction = */ 1.0f);

        // THEN stackHeight and stackEndHeight are the same
        verify(mAmbientState).setStackEndHeight(stackEndHeight);
        verify(mAmbientState).setInterpolatedStackHeight(stackEndHeight);
    }

    @Test
    @EnableSceneContainer
    public void updateStackEndHeightAndStackHeight_shadeExpanding_withSceneContainer() {
        final float stackTop = 200f;
        final float stackBottom = 1000f;
        final float stackWidth = 400f;
        final float stackEndHeight = stackBottom - stackTop;
        mAmbientState.setStackTop(stackTop);
        mAmbientState.setDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));
        mAmbientState.setStatusBarState(StatusBarState.KEYGUARD);
        clearInvocations(mAmbientState);

        // WHEN shade is expanding
        final float expansionFraction = 0.5f;
        mStackScroller.updateStackEndHeightAndStackHeight(expansionFraction);

        // THEN stackHeight is changed by the expansion frac
        verify(mAmbientState).setStackEndHeight(stackEndHeight);
        verify(mAmbientState).setInterpolatedStackHeight(stackEndHeight * 0.75f);
    }

    @Test
    @EnableSceneContainer
    public void updateStackEndHeightAndStackHeight_shadeOverscrolledToTop_withSceneContainer() {
        // GIVEN stack scrolled over the top, stack top is negative
        final float stackTop = -2000f;
        final float stackBottom = 1000f;
        final float stackWidth = 400f;
        final float stackEndHeight = stackBottom - stackTop;
        mAmbientState.setStackTop(stackTop);
        mAmbientState.setDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));
        mAmbientState.setStatusBarState(StatusBarState.KEYGUARD);
        clearInvocations(mAmbientState);

        // WHEN stack is updated
        mStackScroller.updateStackEndHeightAndStackHeight(/* fraction = */ 1.0f);

        // THEN stackHeight is measured from the stack top
        verify(mAmbientState).setStackEndHeight(stackEndHeight);
        verify(mAmbientState).setInterpolatedStackHeight(stackEndHeight);
    }

    @Test
    @EnableSceneContainer
    public void updateDrawBounds_updatesStackEndHeight() {
        // GIVEN shade is fully open
        final float stackTop = 200f;
        final float stackBottom = 1000f;
        final float stackWidth = 400f;
        final float stackHeight = stackBottom - stackTop;
        mAmbientState.setStackTop(stackTop);
        mAmbientState.setDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));
        mAmbientState.setStatusBarState(StatusBarState.SHADE);
        mStackScroller.setMaxDisplayedNotifications(-1); // no limit on the shade
        mStackScroller.setExpandFraction(1f); // shade is fully expanded
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackHeight);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(stackHeight);

        // WHEN stackBottom changes
        final float newStackBottom = 800;
        mStackScroller.updateDrawBounds(new RectF(0, stackTop, 400, newStackBottom));

        // THEN stackEndHeight is updated
        final float newStackHeight = newStackBottom - stackTop;
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(newStackHeight);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(newStackHeight);
    }

    @Test
    @EnableSceneContainer
    public void updateStackEndHeightAndStackHeight_maxNotificationsSet_withSceneContainer() {
        float stackHeight = 300f;
        when(mStackSizeCalculator.computeHeight(eq(mStackScroller), anyInt(), anyFloat(), any()))
                .thenReturn(stackHeight);
        mStackScroller.setMaxDisplayedNotifications(3); // any non-zero amount

        clearInvocations(mAmbientState);
        mStackScroller.updateStackEndHeightAndStackHeight(1f);

        verify(mAmbientState).setInterpolatedStackHeight(eq(300f));
    }

    @Test
    @DisableSceneContainer
    public void updateStackEndHeightAndStackHeight_onlyUpdatesStackHeightDuringSwipeUp() {
        final float expansionFraction = 0.5f;
        mAmbientState.setStatusBarState(StatusBarState.KEYGUARD);
        mAmbientState.setSwipingUp(true);

        // Validate that when the gesture is in progress, we update only the stackHeight
        clearInvocations(mAmbientState);
        mStackScroller.updateStackEndHeightAndStackHeight(expansionFraction);
        verify(mAmbientState, never()).setStackEndHeight(anyFloat());
        verify(mAmbientState).setInterpolatedStackHeight(anyFloat());
    }

    @Test
    @DisableSceneContainer
    public void setPanelFlinging_updatesStackEndHeightOnlyOnFinish() {
        final float expansionFraction = 0.5f;
        mAmbientState.setStatusBarState(StatusBarState.KEYGUARD);
        mAmbientState.setSwipingUp(true);
        mStackScroller.setPanelFlinging(true);
        mAmbientState.setSwipingUp(false);

        // Validate that when the animation is running, we update only the stackHeight
        clearInvocations(mAmbientState);
        mStackScroller.updateStackEndHeightAndStackHeight(expansionFraction);
        verify(mAmbientState, never()).setStackEndHeight(anyFloat());
        verify(mAmbientState).setInterpolatedStackHeight(anyFloat());

        // Validate that when the animation ends the stackEndHeight is recalculated immediately
        clearInvocations(mAmbientState);
        mStackScroller.setPanelFlinging(false);
        verify(mAmbientState).setFlinging(eq(false));
        verify(mAmbientState).setStackEndHeight(anyFloat());
        verify(mAmbientState).setInterpolatedStackHeight(anyFloat());
    }

    @Test
    @EnableSceneContainer
    public void setExpandFraction_fullyCollapsed() {
        // Given: NSSL has a height
        when(mStackScroller.getHeight()).thenReturn(1200);
        // And: stack bounds are set
        float expandFraction = 0.0f;
        float stackTop = 100;
        float stackBottom = 1100;
        float stackHeight = stackBottom - stackTop;
        float stackWidth = 400;
        mStackScroller.setStackTop(stackTop);
        mAmbientState.setDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));

        // When: panel is fully collapsed
        mStackScroller.setExpandFraction(expandFraction);

        // Then
        assertThat(mAmbientState.getExpansionFraction()).isEqualTo(expandFraction);
        assertThat(mAmbientState.isExpansionChanging()).isFalse();
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackHeight);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(
                stackHeight * StackScrollAlgorithm.START_FRACTION);
        assertThat(mAmbientState.isShadeExpanded()).isFalse();
        assertThat(mStackScroller.getExpandedHeight()).isZero();
    }

    @Test
    @EnableSceneContainer
    public void setExpandFraction_expanding() {
        // Given: NSSL has a height
        when(mStackScroller.getHeight()).thenReturn(1200);
        // And: stack bounds are set
        float expandFraction = 0.6f;
        float stackTop = 100;
        float stackBottom = 1100;
        float stackHeight = stackBottom - stackTop;
        float stackWidth = 400;
        mStackScroller.setStackTop(stackTop);
        mStackScroller.updateDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));

        // When: panel is expanding
        mStackScroller.setExpandFraction(expandFraction);

        // Then
        assertThat(mAmbientState.getExpansionFraction()).isEqualTo(expandFraction);
        assertThat(mAmbientState.isExpansionChanging()).isTrue();
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackHeight);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isGreaterThan(
                stackHeight * StackScrollAlgorithm.START_FRACTION);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isLessThan(stackHeight);
        assertThat(mStackScroller.getExpandedHeight()).isGreaterThan(0f);
        assertThat(mAmbientState.isShadeExpanded()).isTrue();
    }

    @Test
    @EnableSceneContainer
    public void setExpandFraction_fullyExpanded() {
        // Given: NSSL has a height
        int viewHeight = 1200;
        when(mStackScroller.getHeight()).thenReturn(viewHeight);
        // And: stack bounds are set
        float expandFraction = 1.0f;
        float stackTop = 100;
        float stackBottom = 1100;
        float stackHeight = stackBottom - stackTop;
        float stackWidth = 400;
        mStackScroller.setStackTop(stackTop);
        mStackScroller.updateDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));

        // When: panel is fully expanded
        mStackScroller.setExpandFraction(expandFraction);

        // Then
        assertThat(mAmbientState.getExpansionFraction()).isEqualTo(expandFraction);
        assertThat(mAmbientState.isExpansionChanging()).isFalse();
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackHeight);
        assertThat(mAmbientState.getInterpolatedStackHeight()).isEqualTo(stackHeight);
        assertThat(mStackScroller.getExpandedHeight()).isEqualTo(viewHeight);
        assertThat(mAmbientState.isShadeExpanded()).isTrue();
    }

    @Test
    @DisableSceneContainer
    public void testSetExpandedHeight_listenerReceivedCallbacks() {
        final float expectedHeight = 0f;

        mStackScroller.addOnExpandedHeightChangedListener((height, appear) -> {
            Assert.assertEquals(expectedHeight, height, 0);
        });
        mStackScroller.setExpandedHeight(expectedHeight);
    }

    @Test
    public void testAppearFractionCalculationIsNotNegativeWhenShelfBecomesSmaller() {
        // this situation might occur if status bar height is defined in pixels while shelf height
        // in dp and screen density changes - appear start position
        // (calculated in NSSL#getMinExpansionHeight) that is adjusting for status bar might
        // increase and become bigger that end position, which should be prevented

        // appear start position
        when(mNotificationShelf.getIntrinsicHeight()).thenReturn(80);
        mStackScroller.mStatusBarHeight = 100;
        // appear end position
        when(mEmptyShadeView.getHeight()).thenReturn(90);

        assertThat(mStackScroller.calculateAppearFraction(100)).isAtLeast(0);
    }

    @Test
    @DisableSceneContainer
    public void testSetExpandedHeight_withSplitShade_doesntInterpolateStackHeight() {
        mTestableResources
                .addOverride(R.bool.config_use_split_notification_shade, /* value= */ true);
        final int[] expectedStackHeight = {0};

        mStackScroller.addOnExpandedHeightChangedListener((expandedHeight, appear) -> {
            assertWithMessage("Given shade enabled: %s",
                    true)
                    .that(mStackScroller.getHeight())
                    .isEqualTo(expectedStackHeight[0]);
        });

        mTestableResources
                .addOverride(R.bool.config_use_split_notification_shade, /* value= */ false);
        expectedStackHeight[0] = 0;
        mStackScroller.setExpandedHeight(100f);

        mTestableResources
                .addOverride(R.bool.config_use_split_notification_shade, /* value= */ true);
        expectedStackHeight[0] = 100;
        mStackScroller.setExpandedHeight(100f);
    }

    @Test
    public void testFooterPosition_atEnd() {
        // add footer
        FooterView view = mock(FooterView.class);
        mStackScroller.setFooterView(view);

        // add notification
        ExpandableNotificationRow row = createClearableRow();
        mStackScroller.addContainerView(row);

        mStackScroller.onUpdateRowStates();

        // Expecting the footer to be the last child
        int expected = mStackScroller.getChildCount() - 1;
        verify(mStackScroller).changeViewPosition(any(FooterView.class), eq(expected));
    }

    @Test
    public void testSetIsBeingDraggedResetsExposedMenu() {
        mStackScroller.setIsBeingDragged(true);
        verify(mNotificationSwipeHelper).resetExposedMenuView(true, true);
    }

    @Test
    public void testPanelTrackingStartResetsExposedMenu() {
        mStackScroller.onPanelTrackingStarted();
        verify(mNotificationSwipeHelper).resetExposedMenuView(true, true);
    }

    @Test
    public void testDarkModeResetsExposedMenu() {
        mStackScroller.setHideAmount(0.1f, 0.1f);
        verify(mNotificationSwipeHelper).resetExposedMenuView(true, true);
    }

    @Test
    public void testClearNotifications_All() {
        final int[] numCalls = {0};
        final int[] selected = {-1};
        mStackScroller.setClearAllListener(selectedRows -> {
            numCalls[0]++;
            selected[0] = selectedRows;
        });

        mStackScroller.clearNotifications(ROWS_ALL,
                /* closeShade = */ true,
                /* hideSilentSection = */ true);
        assertEquals(1, numCalls[0]);
        assertEquals(ROWS_ALL, selected[0]);
    }

    @Test
    public void testClearNotifications_Gentle() {
        final int[] numCalls = {0};
        final int[] selected = {-1};
        mStackScroller.setClearAllListener(selectedRows -> {
            numCalls[0]++;
            selected[0] = selectedRows;
        });

        mStackScroller.clearNotifications(NotificationStackScrollLayout.ROWS_GENTLE,
                /* closeShade = */ false,
                /* hideSilentSection = */ true);
        assertEquals(1, numCalls[0]);
        assertEquals(ROWS_GENTLE, selected[0]);
    }

    @Test
    public void testClearNotifications_clearAllInProgress() {
        ExpandableNotificationRow row = createClearableRow();
        when(row.hasFinishedInitialization()).thenReturn(true);
        doReturn(true).when(mStackScroller).isVisible(row);
        mStackScroller.addContainerView(row);

        mStackScroller.clearNotifications(ROWS_ALL,
                /* closeShade = */ false,
                /* hideSilentSection = */ false);

        assertClearAllInProgress(true);
        verify(mNotificationRoundnessManager).setClearAllInProgress(true);
    }

    @Test
    public void testOnChildAnimationFinished_resetsClearAllInProgress() {
        mStackScroller.setClearAllInProgress(true);

        mStackScroller.onChildAnimationFinished();

        assertClearAllInProgress(false);
        verify(mNotificationRoundnessManager).setClearAllInProgress(false);
    }

    @Test
    public void testShadeCollapsed_resetsClearAllInProgress() {
        mStackScroller.setClearAllInProgress(true);

        mStackScroller.setIsExpanded(false);

        assertClearAllInProgress(false);
        verify(mNotificationRoundnessManager).setClearAllInProgress(false);
    }

    @Test
    public void testShadeExpanded_doesntChangeClearAllInProgress() {
        mStackScroller.setClearAllInProgress(true);
        clearInvocations(mNotificationRoundnessManager);

        mStackScroller.setIsExpanded(true);

        assertClearAllInProgress(true);
        verify(mNotificationRoundnessManager, never()).setClearAllInProgress(anyBoolean());
    }

    @Test
    public void testAddNotificationUpdatesSpeedBumpIndex() {
        // initial state calculated == 0
        assertEquals(0, mStackScroller.getSpeedBumpIndex());

        // add notification that's before the speed bump
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        if (NotificationBundleUi.isEnabled()) {
            EntryAdapter entryAdapter = mock(EntryAdapter.class);
            when(entryAdapter.isAmbient()).thenReturn(false);
            when(row.getEntryAdapter()).thenReturn(entryAdapter);
        } else {
            NotificationEntry entry = mock(NotificationEntry.class);
            when(row.getEntryLegacy()).thenReturn(entry);
            when(entry.isAmbient()).thenReturn(false);
        }

        mStackScroller.addContainerView(row);

        // speed bump = 1
        assertEquals(1, mStackScroller.getSpeedBumpIndex());
    }

    @Test
    public void testAddAmbientNotificationNoSpeedBumpUpdate() {
        // initial state calculated  == 0
        assertEquals(0, mStackScroller.getSpeedBumpIndex());

        // add notification that's after the speed bump
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        if (NotificationBundleUi.isEnabled()) {
            EntryAdapter entryAdapter = mock(EntryAdapter.class);
            when(entryAdapter.isAmbient()).thenReturn(true);
            when(row.getEntryAdapter()).thenReturn(entryAdapter);
        } else {
            NotificationEntry entry = mock(NotificationEntry.class);
            when(row.getEntryLegacy()).thenReturn(entry);
            when(entry.isAmbient()).thenReturn(true);
        }
        mStackScroller.addContainerView(row);

        // speed bump is set to 0
        assertEquals(0, mStackScroller.getSpeedBumpIndex());
    }

    @Test
    public void testRemoveNotificationUpdatesSpeedBump() {
        // initial state calculated == 0
        assertEquals(0, mStackScroller.getSpeedBumpIndex());

        // add 3 notification that are after the speed bump
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        if (NotificationBundleUi.isEnabled()) {
            EntryAdapter entryAdapter = mock(EntryAdapter.class);
            when(entryAdapter.isAmbient()).thenReturn(false);
            when(row.getEntryAdapter()).thenReturn(entryAdapter);
        } else {
            NotificationEntry entry = mock(NotificationEntry.class);
            when(row.getEntryLegacy()).thenReturn(entry);
            when(entry.isAmbient()).thenReturn(false);
        }
        mStackScroller.addContainerView(row);

        // speed bump is 1
        assertEquals(1, mStackScroller.getSpeedBumpIndex());

        // remove the notification that was before the speed bump
        mStackScroller.removeContainerView(row);

        // speed bump is now 0
        assertEquals(0, mStackScroller.getSpeedBumpIndex());
    }

    @Test
    @DisableFlags(QSComposeFragment.FLAG_NAME)
    @DisableSceneContainer
    public void testInsideQSHeader_noOffset() {
        ViewGroup qsHeader = mock(ViewGroup.class);
        Rect boundsOnScreen = new Rect(0, 0, 1000, 1000);
        mockBoundsOnScreen(qsHeader, boundsOnScreen);

        mStackScroller.setQsHeader(qsHeader);
        mStackScroller.setLeftTopRightBottom(0, 0, 2000, 2000);

        MotionEvent event1 = transformEventForView(createMotionEvent(100f, 100f), mStackScroller);
        assertTrue(mStackScroller.isInsideQsHeader(event1));

        MotionEvent event2 = transformEventForView(createMotionEvent(1100f, 100f), mStackScroller);
        assertFalse(mStackScroller.isInsideQsHeader(event2));
    }

    @Test
    @DisableFlags(QSComposeFragment.FLAG_NAME)
    @DisableSceneContainer
    public void testInsideQSHeader_Offset() {
        ViewGroup qsHeader = mock(ViewGroup.class);
        Rect boundsOnScreen = new Rect(100, 100, 1000, 1000);
        mockBoundsOnScreen(qsHeader, boundsOnScreen);

        mStackScroller.setQsHeader(qsHeader);
        mStackScroller.setLeftTopRightBottom(200, 200, 2000, 2000);

        MotionEvent event1 = transformEventForView(createMotionEvent(50f, 50f), mStackScroller);
        assertFalse(mStackScroller.isInsideQsHeader(event1));

        MotionEvent event2 = transformEventForView(createMotionEvent(150f, 150f), mStackScroller);
        assertFalse(mStackScroller.isInsideQsHeader(event2));

        MotionEvent event3 = transformEventForView(createMotionEvent(250f, 250f), mStackScroller);
        assertTrue(mStackScroller.isInsideQsHeader(event3));
    }

    @Test
    @EnableFlags(QSComposeFragment.FLAG_NAME)
    @DisableSceneContainer
    public void testInsideQSHeader_noOffset_qsCompose() {
        ViewGroup qsHeader = mock(ViewGroup.class);
        Rect boundsOnScreen = new Rect(0, 0, 1000, 1000);
        mockBoundsOnScreen(qsHeader, boundsOnScreen);

        QSHeaderBoundsProvider provider = new QSHeaderBoundsProvider(
                () -> 0,
                boundsOnScreen::height,
                rect -> {
                    qsHeader.getBoundsOnScreen(rect);
                    return Unit.INSTANCE;
                }
        );

        mStackScroller.setQsHeaderBoundsProvider(provider);
        mStackScroller.setLeftTopRightBottom(0, 0, 2000, 2000);

        MotionEvent event1 = transformEventForView(createMotionEvent(100f, 100f), mStackScroller);
        assertTrue(mStackScroller.isInsideQsHeader(event1));

        MotionEvent event2 = transformEventForView(createMotionEvent(1100f, 100f), mStackScroller);
        assertFalse(mStackScroller.isInsideQsHeader(event2));
    }

    @Test
    @EnableFlags(QSComposeFragment.FLAG_NAME)
    @DisableSceneContainer
    public void testInsideQSHeader_Offset_qsCompose() {
        ViewGroup qsHeader = mock(ViewGroup.class);
        Rect boundsOnScreen = new Rect(100, 100, 1000, 1000);
        mockBoundsOnScreen(qsHeader, boundsOnScreen);

        QSHeaderBoundsProvider provider = new QSHeaderBoundsProvider(
                () -> 0,
                boundsOnScreen::height,
                rect -> {
                    qsHeader.getBoundsOnScreen(rect);
                    return Unit.INSTANCE;
                }
        );

        mStackScroller.setQsHeaderBoundsProvider(provider);
        mStackScroller.setLeftTopRightBottom(200, 200, 2000, 2000);

        MotionEvent event1 = transformEventForView(createMotionEvent(50f, 50f), mStackScroller);
        assertFalse(mStackScroller.isInsideQsHeader(event1));

        MotionEvent event2 = transformEventForView(createMotionEvent(150f, 150f), mStackScroller);
        assertFalse(mStackScroller.isInsideQsHeader(event2));

        MotionEvent event3 = transformEventForView(createMotionEvent(250f, 250f), mStackScroller);
        assertTrue(mStackScroller.isInsideQsHeader(event3));
    }

    @Test
    @EnableSceneContainer
    public void testIsInsideScrollableRegion_noScrim() {
        mStackScroller.setLeftTopRightBottom(0, 0, 2000, 2000);

        MotionEvent event = transformEventForView(createMotionEvent(250f, 250f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event)).isTrue();
    }

    @Test
    @EnableSceneContainer
    public void testIsInsideScrollableRegion_noOffset() {
        mStackScroller.setLeftTopRightBottom(0, 0, 1000, 2000);
        mStackScroller.setClippingShape(createScrimShape(100, 500, 900, 2000));

        MotionEvent event1 = transformEventForView(createMotionEvent(500f, 400f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event1)).isFalse();

        MotionEvent event2 = transformEventForView(createMotionEvent(50, 1000f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event2)).isFalse();

        MotionEvent event3 = transformEventForView(createMotionEvent(950f, 1000f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event3)).isFalse();

        MotionEvent event4 = transformEventForView(createMotionEvent(500f, 1000f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event4)).isTrue();
    }

    @Test
    @EnableSceneContainer
    public void testIsInsideScrollableRegion_offset() {
        mStackScroller.setLeftTopRightBottom(1000, 0, 2000, 2000);
        mStackScroller.setClippingShape(createScrimShape(100, 500, 900, 2000));

        MotionEvent event1 = transformEventForView(createMotionEvent(1500f, 400f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event1)).isFalse();

        MotionEvent event2 = transformEventForView(createMotionEvent(1050, 1000f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event2)).isFalse();

        MotionEvent event3 = transformEventForView(createMotionEvent(1950f, 1000f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event3)).isFalse();

        MotionEvent event4 = transformEventForView(createMotionEvent(1500f, 1000f), mStackScroller);
        assertThat(mStackScroller.isInScrollableRegion(event4)).isTrue();
    }

    @Test
    @DisableSceneContainer // TODO(b/312473478): address disabled test
    public void setFractionToShade_recomputesStackHeight() {
        mStackScroller.setFractionToShade(1f);
        verify(mStackSizeCalculator).computeHeight(
                any(), anyInt(), anyFloat(), eq("updateContentHeight"));
    }

    @Test
    @DisableSceneContainer // TODO(b/312473478): address disabled test
    public void testSetOwnScrollY_shadeNotClosing_scrollYChanges() {
        // Given: shade is not closing, scrollY is 0
        mAmbientState.setScrollY(0);
        assertEquals(0, mAmbientState.getScrollY());
        mAmbientState.setIsClosing(false);

        // When: call NotificationStackScrollLayout.setOwnScrollY to set scrollY to 1
        mStackScroller.setOwnScrollY(1);

        // Then: scrollY should be set to 1
        assertEquals(1, mAmbientState.getScrollY());

        // Reset scrollY back to 0 to avoid interfering with other tests
        mStackScroller.setOwnScrollY(0);
        assertEquals(0, mAmbientState.getScrollY());
    }

    @Test
    public void testSetOwnScrollY_shadeClosing_scrollYDoesNotChange() {
        // Given: shade is closing, scrollY is 0
        mAmbientState.setScrollY(0);
        assertEquals(0, mAmbientState.getScrollY());
        mAmbientState.setIsClosing(true);

        // When: call NotificationStackScrollLayout.setOwnScrollY to set scrollY to 1
        mStackScroller.setOwnScrollY(1);

        // Then: scrollY should not change, it should still be 0
        assertEquals(0, mAmbientState.getScrollY());

        // Reset scrollY and mAmbientState.mIsClosing to avoid interfering with other tests
        mAmbientState.setIsClosing(false);
        mStackScroller.setOwnScrollY(0);
        assertEquals(0, mAmbientState.getScrollY());
    }

    @Test
    public void testSetOwnScrollY_clearAllInProgress_scrollYDoesNotChange() {
        // Given: clear all is in progress, scrollY is 0
        mAmbientState.setScrollY(0);
        assertEquals(0, mAmbientState.getScrollY());
        mAmbientState.setClearAllInProgress(true);

        // When: call NotificationStackScrollLayout.setOwnScrollY to set scrollY to 1
        mStackScroller.setOwnScrollY(1);

        // Then: scrollY should not change, it should still be 0
        assertEquals(0, mAmbientState.getScrollY());

        // Reset scrollY and mAmbientState.mIsClosing to avoid interfering with other tests
        mAmbientState.setClearAllInProgress(false);
        mStackScroller.setOwnScrollY(0);
        assertEquals(0, mAmbientState.getScrollY());
    }

    @Test
    public void onShadeFlingClosingEnd_scrollYShouldBeSetToZero() {
        // Given: mAmbientState.mIsClosing is set to be true
        // mIsExpanded is set to be false
        mAmbientState.setIsClosing(true);
        mStackScroller.setIsExpanded(false);

        // When: onExpansionStopped is called
        mStackScroller.onExpansionStopped();

        // Then: mAmbientState.scrollY should be set to be 0
        assertEquals(mAmbientState.getScrollY(), 0);
    }

    @Test
    public void onShadeClosesWithAnimationWillResetTouchState() {
        // GIVEN shade is expanded
        mStackScroller.setIsExpanded(true);
        clearInvocations(mNotificationSwipeHelper);

        // WHEN closing the shade with the animations
        mStackScroller.onExpansionStarted();
        mStackScroller.setIsExpanded(false);
        mStackScroller.onExpansionStopped();

        // VERIFY touch is reset
        verify(mNotificationSwipeHelper).resetTouchState();
    }

    @Test
    public void onShadeClosesWithoutAnimationWillResetTouchState() {
        // GIVEN shade is expanded
        mStackScroller.setIsExpanded(true);
        clearInvocations(mNotificationSwipeHelper);

        // WHEN closing the shade without the animation
        mStackScroller.setIsExpanded(false);

        // VERIFY touch is reset
        verify(mNotificationSwipeHelper).resetTouchState();
    }

    @Test
    @DisableSceneContainer // TODO(b/312473478): address disabled test
    public void testSplitShade_hasTopOverscroll() {
        mTestableResources
                .addOverride(R.bool.config_use_split_notification_shade, /* value= */ true);
        mStackScroller.passSplitShadeStateController(new ResourcesSplitShadeStateController());
        mStackScroller.updateSplitNotificationShade();
        mAmbientState.setExpansionFraction(1f);

        int topOverscrollPixels = 100;
        mStackScroller.setOverScrolledPixels(topOverscrollPixels, true, false);

        float expectedTopOverscrollAmount = topOverscrollPixels * RUBBER_BAND_FACTOR_NORMAL;
        assertEquals(expectedTopOverscrollAmount, mStackScroller.getCurrentOverScrollAmount(true));
        assertEquals(expectedTopOverscrollAmount, mAmbientState.getStackY());
    }

    @Test
    @DisableSceneContainer // NSSL has no more scroll logic when SceneContainer is on
    public void testNormalShade_hasNoTopOverscroll() {
        mTestableResources
                .addOverride(R.bool.config_use_split_notification_shade, /* value= */ false);
        mStackScroller.passSplitShadeStateController(new ResourcesSplitShadeStateController());
        mStackScroller.updateSplitNotificationShade();
        mAmbientState.setExpansionFraction(1f);

        int topOverscrollPixels = 100;
        mStackScroller.setOverScrolledPixels(topOverscrollPixels, true, false);

        float expectedTopOverscrollAmount = topOverscrollPixels * RUBBER_BAND_FACTOR_NORMAL;
        assertEquals(expectedTopOverscrollAmount, mStackScroller.getCurrentOverScrollAmount(true));
        // When not in split shade mode, then the overscroll effect is handled in
        // NotificationPanelViewController and not in NotificationStackScrollLayout. Therefore
        // mAmbientState must have stackY set to 0
        assertEquals(0f, mAmbientState.getStackY());
    }

    @Test
    @DisableSceneContainer
    public void testWindowInsetAnimationProgress_updatesBottomInset() {
        int imeInset = 100;
        WindowInsets windowInsets = new WindowInsets.Builder()
                .setInsets(ime(), Insets.of(0, 0, 0, imeInset)).build();
        ArrayList<WindowInsetsAnimation> windowInsetsAnimations = new ArrayList<>();
        mStackScrollerInternal
                .dispatchWindowInsetsAnimationProgress(windowInsets, windowInsetsAnimations);

        assertEquals(imeInset, mStackScrollerInternal.mImeInset);
    }

    @Test
    @EnableSceneContainer
    public void testSetMaxDisplayedNotifications_updatesStackHeight() {
        int fullStackHeight = 300;
        int limitedStackHeight = 100;
        int maxNotifs = 2; // any non-zero limit
        float stackTop = 100;
        float stackBottom = 1100;
        float stackWidth = 400;
        float stackViewPortHeight = stackBottom - stackTop;
        mStackScroller.setStackTop(stackTop);
        mStackScroller.updateDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));
        when(mStackSizeCalculator.computeHeight(
                eq(mStackScroller),
                eq(-1),
                anyFloat(),
                any())
        ).thenReturn((float) fullStackHeight);
        when(mStackSizeCalculator.computeHeight(
                eq(mStackScroller),
                eq(maxNotifs),
                anyFloat(),
                any())
        ).thenReturn((float) limitedStackHeight);

        // When we set a limit on max displayed notifications
        mStackScroller.setMaxDisplayedNotifications(maxNotifs);

        // Then
        assertThat(mStackScroller.getIntrinsicStackHeight()).isEqualTo(limitedStackHeight);
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(limitedStackHeight);

        // When there is no limit on max displayed notifications
        mStackScroller.setMaxDisplayedNotifications(-1);

        // Then
        assertThat(mStackScroller.getIntrinsicStackHeight()).isEqualTo(fullStackHeight);
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackViewPortHeight);
    }

    @Test
    @EnableSceneContainer
    public void testChildHeightUpdated_whenMaxDisplayedNotificationsSet_updatesStackHeight() {
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        int maxNotifs = 1; // any non-zero limit
        float stackTop = 100;
        float stackBottom = 1100;
        float stackWidth = 400;
        mStackScroller.setStackTop(stackTop);
        mStackScroller.updateDrawBounds(new RectF(0, stackTop, stackWidth, stackBottom));

        // Given we have a limit on max displayed notifications
        int stackHeightBeforeUpdate = 100;
        when(mStackSizeCalculator.computeHeight(
                eq(mStackScroller),
                eq(maxNotifs),
                anyFloat(),
                any())
        ).thenReturn((float) stackHeightBeforeUpdate);
        mStackScroller.setMaxDisplayedNotifications(maxNotifs);

        // And the stack heights are set
        assertThat(mStackScroller.getIntrinsicStackHeight()).isEqualTo(stackHeightBeforeUpdate);
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackHeightBeforeUpdate);

        // When a child changes its height
        int stackHeightAfterUpdate = 300;
        when(mStackSizeCalculator.computeHeight(
                eq(mStackScroller),
                eq(maxNotifs),
                anyFloat(),
                any())
        ).thenReturn((float) stackHeightAfterUpdate);
        mStackScroller.onChildHeightChanged(row, /* needsAnimation = */ false);

        // Then the stack heights are updated
        assertThat(mStackScroller.getIntrinsicStackHeight()).isEqualTo(stackHeightAfterUpdate);
        assertThat(mAmbientState.getStackEndHeight()).isEqualTo(stackHeightAfterUpdate);
    }

    @Test
    @DisableSceneContainer
    public void testSetMaxDisplayedNotifications_notifiesListeners() {
        ExpandableView.OnHeightChangedListener listener =
                mock(ExpandableView.OnHeightChangedListener.class);
        Runnable runnable = mock(Runnable.class);
        mStackScroller.setOnHeightChangedListener(listener);
        mStackScroller.setOnHeightChangedRunnable(runnable);

        mStackScroller.setMaxDisplayedNotifications(50);

        verify(listener).onHeightChanged(mNotificationShelf, false);
        verify(runnable).run();
    }

    @Test
    @DisableSceneContainer
    public void testDispatchTouchEvent_sceneContainerDisabled() {
        MotionEvent event = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                0,
                0,
                0
        );

        mStackScroller.dispatchTouchEvent(event);

        verify(mStackScrollLayoutController, never()).sendTouchToSceneFramework(any());
    }

    @Test
    @EnableSceneContainer
    public void testDispatchTouchEvent_sceneContainerEnabled() {
        mStackScroller.setIsBeingDragged(true);

        long downTime = SystemClock.uptimeMillis() - 100;
        MotionEvent moveEvent1 = MotionEvent.obtain(
                /* downTime= */ downTime,
                /* eventTime= */ SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                101,
                201,
                0
        );
        MotionEvent syntheticDownEvent = moveEvent1.copy();
        syntheticDownEvent.setAction(MotionEvent.ACTION_DOWN);
        mStackScroller.dispatchTouchEvent(moveEvent1);

        assertThatMotionEvent(captureTouchSentToSceneFramework()).matches(syntheticDownEvent);
        assertTrue(mStackScroller.getIsBeingDragged());
        clearInvocations(mStackScrollLayoutController);

        MotionEvent moveEvent2 = MotionEvent.obtain(
                /* downTime= */ downTime,
                /* eventTime= */ SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                102,
                202,
                0
        );

        mStackScroller.dispatchTouchEvent(moveEvent2);

        assertThatMotionEvent(captureTouchSentToSceneFramework()).matches(moveEvent2);
        assertTrue(mStackScroller.getIsBeingDragged());
        clearInvocations(mStackScrollLayoutController);

        MotionEvent upEvent = MotionEvent.obtain(
                /* downTime= */ downTime,
                /* eventTime= */ SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                103,
                203,
                0
        );

        mStackScroller.dispatchTouchEvent(upEvent);

        assertThatMotionEvent(captureTouchSentToSceneFramework()).matches(upEvent);
        assertFalse(mStackScroller.getIsBeingDragged());
    }

    @Test
    @EnableSceneContainer
    public void testDispatchTouchEvent_sceneContainerEnabled_ignoresInitialActionUp() {
        mStackScroller.setIsBeingDragged(true);

        MotionEvent upEvent = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                0,
                0,
                0
        );

        mStackScroller.dispatchTouchEvent(upEvent);
        verify(mStackScrollLayoutController, never()).sendTouchToSceneFramework(any());
        assertFalse(mStackScroller.getIsBeingDragged());
    }


    @Test
    @EnableSceneContainer
    public void testGenerateHeadsUpDisappearEvent_setsHeadsUpAnimatingAway() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        // WHEN we generate a disappear event
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false, /* statusBarChipBounds= */ null);

        // THEN headsUpAnimatingAway is true
        verify(headsUpAnimatingAwayListener).accept(true);
        assertTrue(mStackScroller.mHeadsUpAnimatingAway);
    }

    @Test
    @EnableSceneContainer
    @DisableFlags(PromotedNotificationUi.FLAG_NAME)
    public void testGenerateHeadsUpDisappearEvent_notifChipsFlagOff_statusBarChipNotSet() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false, /* statusBarChipBounds= */ new RectF(0f, 0f, 1f, 1f));

        verify(row, never()).setHasStatusBarChipDuringHeadsUpAnimation(anyBoolean());
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    public void testGenerateHeadsUpDisappearEvent_notifChipsFlagOn_statusBarChipSetToFalse() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false, /* statusBarChipBounds= */ null);

        verify(row).setHasStatusBarChipDuringHeadsUpAnimation(false);
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    public void testGenerateHeadsUpDisappearEvent_notifChipsFlagOn_statusBarChipSetToTrue() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false, /* statusBarChipBounds= */ new RectF(0f, 0f, 1f, 1f));

        verify(row).setHasStatusBarChipDuringHeadsUpAnimation(true);
    }

    @Test
    @EnableSceneContainer
    public void testGenerateHeadsUpDisappearEvent_stackExpanded_headsUpAnimatingAwayNotSet() {
        // GIVEN NSSL would be ready for HUN animations, BUT it is expanded
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        assertTrue("Should be expanded by default.", mStackScroller.isExpanded());
        mStackScroller.setHeadsUpAnimatingAwayListener(headsUpAnimatingAwayListener);
        mStackScroller.setAnimationsEnabled(true);
        mStackScroller.setHeadsUpGoingAwayAnimationsAllowed(true);

        // WHEN we generate a disappear event
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false, /* statusBarChipBounds= */ null);

        // THEN nothing happens
        verify(headsUpAnimatingAwayListener, never()).accept(anyBoolean());
        assertFalse(mStackScroller.mHeadsUpAnimatingAway);
    }

    @Test
    @EnableSceneContainer
    public void testGenerateHeadsUpDisappearEvent_pendingAppearEvent_headsUpAnimatingAwayNotSet() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);
        // BUT there is a pending appear event
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ true, /* statusBarChipBounds= */ null);

        // WHEN we generate a disappear event
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false, /* statusBarChipBounds= */ null);

        // THEN nothing happens
        verify(headsUpAnimatingAwayListener, never()).accept(anyBoolean());
        assertFalse(mStackScroller.mHeadsUpAnimatingAway);
    }

    @Test
    @EnableSceneContainer
    public void testGenerateHeadsUpAppearEvent_headsUpAnimatingAwayNotSet() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        // WHEN we generate a disappear event
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ true, /* statusBarChipBounds= */ null);

        // THEN headsUpAnimatingWay is not set
        verify(headsUpAnimatingAwayListener, never()).accept(anyBoolean());
        assertFalse(mStackScroller.mHeadsUpAnimatingAway);
    }

    @Test
    @EnableFlags(NotificationThrottleHun.FLAG_NAME)
    @BrokenWithSceneContainer(bugId = 332732878) // because NSSL#mAnimationsEnabled is always true
    public void testGenerateHeadsUpAnimation_isSeenInShade_noAnimation() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        // Entry was seen in shade
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        if (NotificationBundleUi.isEnabled()) {
            EntryAdapter entryAdapter = mock(EntryAdapter.class);
            when(entryAdapter.isSeenInShade()).thenReturn(true);
            when(row.getEntryAdapter()).thenReturn(entryAdapter);
        } else {
            NotificationEntry entry = mock(NotificationEntry.class);
            when(entry.isSeenInShade()).thenReturn(true);
            when(row.getEntryLegacy()).thenReturn(entry);
        }

        // WHEN we generate an add event
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ true, /* statusBarChipBounds= */ null);

        // THEN nothing happens
        assertThat(mStackScroller.isAddOrRemoveAnimationPending()).isFalse();
    }

    @Test
    @EnableSceneContainer
    public void testOnChildAnimationsFinished_resetsheadsUpAnimatingAway() {
        // GIVEN NSSL is ready for HUN animations
        Consumer<Boolean> headsUpAnimatingAwayListener = mock(BooleanConsumer.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        prepareStackScrollerForHunAnimations(headsUpAnimatingAwayListener);

        // AND there is a HUN animating away
        mStackScroller.generateHeadsUpAnimation(
                row, /* isHeadsUp = */ false,  /* statusBarChipBounds= */ null);
        assertTrue("a HUN should be animating away", mStackScroller.mHeadsUpAnimatingAway);

        // WHEN the child animations are finished
        mStackScroller.onChildAnimationFinished();

        // THEN headsUpAnimatingAway is false
        verify(headsUpAnimatingAwayListener).accept(false);
        assertFalse(mStackScroller.mHeadsUpAnimatingAway);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapOnTop() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.applyToView(firstRow);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(50f);
        viewState.height = 100;
        viewState.notGoneIndex = 1;
        viewState.applyToView(secondRow);
        mStackScroller.avoidNotificationOverlaps();
        // bigger than because of padding
        assertTrue("TopOverlap not calculated accurately", secondRow.getTopOverlap() >= 50);
        assertTrue("BottomOverlap not calculated accurately", secondRow.getBottomOverlap() == 0);
        assertTrue("TopOverlap not calculated accurately", firstRow.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately", firstRow.getBottomOverlap() == 0);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapOnTop_groupCollapsed() {
        ExpandableNotificationRow firstRow = createRowGroup();
        mStackScroller.addContainerView(firstRow);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.applyToView(firstRow);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(50f);
        viewState.height = 100;
        viewState.notGoneIndex = 1;
        viewState.applyToView(secondRow);
        mStackScroller.avoidNotificationOverlaps();
        // bigger than because of padding
        assertTrue("TopOverlap not calculated accurately", secondRow.getTopOverlap() >= 50);
        assertTrue("BottomOverlap not calculated accurately", secondRow.getBottomOverlap() == 0);
        assertTrue("TopOverlap not calculated accurately", firstRow.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately", firstRow.getBottomOverlap() == 0);
    }

    @Test
    @EnableFlags({com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            com.android.systemui.Flags.FLAG_NOTIFICATION_BUNDLE_UI})
    public void testOverlapOnTop_groupExpanded() {
        ExpandableNotificationRow parent = createRowGroup();
        mStackScroller.addContainerView(parent);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = parent.getViewState();
        viewState.initFrom(parent);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 0;
        viewState.applyToView(parent);

        ExpandableNotificationRow child = parent.getAttachedChildren().getLast();
        viewState = child.getViewState();
        viewState.initFrom(child);
        viewState.setYTranslation(400f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 4;
        viewState.applyToView(child);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(430f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 5;
        viewState.applyToView(secondRow);
        mKosmos.getGroupExpansionManager().setGroupExpanded(parent.getEntryAdapter(), true);
        mStackScroller.avoidNotificationOverlaps();
        // bigger than because of padding
        assertTrue("TopOverlap not calculated accurately", secondRow.getTopOverlap() >= 70);
        assertTrue("BottomOverlap not calculated accurately", secondRow.getBottomOverlap() == 0);
        assertTrue("TopOverlap not calculated accurately", child.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately", child.getBottomOverlap() == 0);
    }

    @Test
    @EnableFlags({com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            com.android.systemui.Flags.FLAG_NOTIFICATION_BUNDLE_UI})
    public void testOverlapOnBottom_groupExpanded_Transient() {
        ExpandableNotificationRow parent = createRowGroup();
        mStackScroller.addContainerView(parent);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = parent.getViewState();
        viewState.initFrom(parent);
        viewState.setYTranslation(0f);
        viewState.height = 200;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(1.0f);
        viewState.applyToView(parent);

        // Inset more to reflect header inset
        ExpandableNotificationRow child = parent.getAttachedChildren().getLast();
        viewState = child.getViewState();
        viewState.initFrom(child);
        viewState.setYTranslation(400f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 4;
        viewState.applyToView(child);
        parent.removeChildNotification(child);
        child.setTransientContainer(parent.getChildrenContainer());
        parent.getChildrenContainer().addTransientView(child, 0);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(430f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 5;
        viewState.applyToView(secondRow);
        mKosmos.getGroupExpansionManager().setGroupExpanded(parent.getEntryAdapter(), true);
        mStackScroller.avoidNotificationOverlaps();
        // bigger than because of padding
        assertTrue("TopOverlap of transient child not calculated accurately",
                child.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately", child.getBottomOverlap() >= 70);
        assertTrue("TopOverlap of child after group not calculated accurately",
                secondRow.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately", secondRow.getBottomOverlap() == 0);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapOnBottom_whenTransient() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addTransientView(firstRow, 0);
        firstRow.setTransientContainer(mStackScroller);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.applyToView(firstRow);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(50f);
        viewState.height = 100;
        viewState.notGoneIndex = 1;
        viewState.applyToView(secondRow);
        mStackScroller.avoidNotificationOverlaps();
        // bigger than because of padding
        assertTrue("TopOverlap not calculated accurately for first view",
                firstRow.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately for first view",
                firstRow.getBottomOverlap() >= 50);
        assertTrue("TopOverlap not calculated accurately for second view",
                secondRow.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately for second view",
                secondRow.getBottomOverlap() == 0);
    }

    @NonNull
    private ExpandableNotificationRow createRow() {
        ExpandableNotificationRow row = mKosmos.createRow();
        row.setIsBlurSupported(true);
        return row;
    }

    @NonNull
    private ExpandableNotificationRow createRowGroup() {
        ExpandableNotificationRow rowGroup = mKosmos.createRowGroup();
        rowGroup.setIsBlurSupported(true);
        List<ExpandableNotificationRow> children = rowGroup.getAttachedChildren();
        children.forEach((it) -> it.setIsBlurSupported(true));
        return rowGroup;
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_baseline() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(1);
        viewState.hidden = false;
        viewState.applyToView(firstRow);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(50f);
        viewState.height = 100;
        viewState.notGoneIndex = 1;
        viewState.hidden = false;
        viewState.setAlpha(1);
        viewState.applyToView(secondRow);
        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("First row wasn't returned as the first element",
                !overlapList.isEmpty() && overlapList.get(0) == firstRow);
        assertTrue("Second row wasn't returned as the first element",
                overlapList.size() >= 2 && overlapList.get(1) == secondRow);
        assertTrue("The first view should not be non-overlapping",
                !nonOverlapList.contains(firstRow));
        assertTrue("The second view should not be non-overlapping",
                !nonOverlapList.contains(secondRow));
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_sorted() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);
        ExpandableNotificationRow thirdRow = createRow();
        mStackScroller.addContainerView(thirdRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.height = 100;
        viewState.notGoneIndex = 2;
        viewState.setAlpha(1);
        viewState.hidden = false;
        viewState.applyToView(firstRow);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.height = 100;
        viewState.notGoneIndex = 1;
        viewState.hidden = false;
        viewState.setAlpha(1);
        viewState.applyToView(secondRow);

        viewState = thirdRow.getViewState();
        viewState.initFrom(thirdRow);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.hidden = false;
        viewState.setAlpha(1);
        viewState.applyToView(thirdRow);


        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("Third row wasn't returned as the first element",
                !overlapList.isEmpty() && overlapList.get(0) == thirdRow);
        assertTrue("Second row wasn't returned as the second element",
                overlapList.size() > 1 &&overlapList.get(1) == secondRow);
        assertTrue("First row wasn't returned as the last element",
                overlapList.size() > 2 &&overlapList.get(2) == firstRow);
        assertTrue("The first view should not be non-overlapping",
                !nonOverlapList.contains(firstRow));
        assertTrue("The second view should not be non-overlapping",
                !nonOverlapList.contains(secondRow));
        assertTrue("The third view should not be non-overlapping",
                !nonOverlapList.contains(thirdRow));
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_transient() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addTransientView(firstRow, 0);
        firstRow.setTransientContainer(mStackScroller);
        ExpandableNotificationRow secondRow = createRow();
        mStackScroller.addContainerView(secondRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(1);
        viewState.hidden = false;
        viewState.applyToView(firstRow);

        viewState = secondRow.getViewState();
        viewState.initFrom(secondRow);
        viewState.setYTranslation(0);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.hidden = false;
        viewState.setAlpha(1);
        viewState.applyToView(secondRow);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("First row wasn't returned as the first element",
                !overlapList.isEmpty() &&overlapList.get(0) == firstRow);
        assertTrue("Second row wasn't returned as the second element",
                overlapList.size() > 1 &&overlapList.get(1) == secondRow);
        assertTrue("The first view should not be non-overlapping",
                !nonOverlapList.contains(firstRow));
        assertTrue("The second view should not be non-overlapping",
                !nonOverlapList.contains(secondRow));
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_alpha() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(0);
        viewState.hidden = false;
        viewState.applyToView(firstRow);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("A hidden view wasn't returned as non-overlapping",
                nonOverlapList.contains(firstRow));
        assertTrue("There was an unexpected overlapping view", overlapList.isEmpty());
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_gone() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(1);
        viewState.hidden = false;
        viewState.applyToView(firstRow);
        firstRow.setVisibility(View.GONE);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("A gone view wasn't returned as non-overlapping",
                nonOverlapList.contains(firstRow));
        assertTrue("There was an unexpected overlapping view", overlapList.isEmpty());
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_opaque() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(1);
        viewState.hidden = false;
        viewState.applyToView(firstRow);
        // make it opaque
        firstRow.setIsBlurSupported(false);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("A opaque view was returned as foverlapping",
                nonOverlapList.contains(firstRow));
        assertTrue("There was an unexpected overlapping view", overlapList.isEmpty());
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_invisible() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(0f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.setAlpha(1);
        viewState.applyToView(firstRow);
        firstRow.setVisibility(View.INVISIBLE);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("A hidden view wasn't returned as non-overlapping",
                nonOverlapList.contains(firstRow));
        assertTrue("There was an unexpected overlapping view", overlapList.isEmpty());
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT)
    public void testOverlapListCreation_collapsed_group() {
        ExpandableNotificationRow parent = createRowGroup();
        mStackScroller.addContainerView(parent);

        ExpandableViewState viewState = parent.getViewState();
        viewState.initFrom(parent);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 0;
        viewState.applyToView(parent);

        ExpandableNotificationRow child = parent.getAttachedChildren().getLast();
        viewState = child.getViewState();
        viewState.initFrom(child);
        viewState.setYTranslation(200f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 4;
        viewState.applyToView(child);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("The parent wasn't added to the overlapping list",
                !overlapList.isEmpty() && overlapList.get(0) == parent);
        assertTrue("Children should only be added when expanded", !overlapList.contains(child));
        assertTrue("Children of collapsed group wasn't added non-overlapping",
                nonOverlapList.contains(child));
    }

    @Test
    @EnableFlags({com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            com.android.systemui.Flags.FLAG_NOTIFICATION_BUNDLE_UI})
    public void testOverlapListCreation_expanded_group() {
        ExpandableNotificationRow parent = createRowGroup();
        mStackScroller.addContainerView(parent);

        ExpandableViewState viewState = parent.getViewState();
        viewState.initFrom(parent);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 0;
        viewState.applyToView(parent);

        ExpandableNotificationRow child = parent.getAttachedChildren().getLast();
        viewState = child.getViewState();
        viewState.initFrom(child);
        viewState.setYTranslation(200f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 4;
        viewState.applyToView(child);

        mKosmos.getGroupExpansionManager().setGroupExpanded(parent.getEntryAdapter(), true);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("The parent wasn't added to the overlapping list",
                !overlapList.isEmpty() &&overlapList.get(0) == parent);
        assertTrue("Children should be added when expanded", overlapList.contains(child));
        assertTrue("Children of expanded group was added non-overlapping",
                !nonOverlapList.contains(child));
    }

    @Test
    @EnableFlags({com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            com.android.systemui.Flags.FLAG_NOTIFICATION_BUNDLE_UI})
    public void testOverlapListCreation_expanded_group_alpha() {
        ExpandableNotificationRow parent = createRowGroup();
        mStackScroller.addContainerView(parent);

        ExpandableViewState viewState = parent.getViewState();
        viewState.initFrom(parent);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 0;
        viewState.applyToView(parent);

        ExpandableNotificationRow child = parent.getAttachedChildren().getLast();
        viewState = child.getViewState();
        viewState.initFrom(child);
        viewState.setYTranslation(200f);
        viewState.height = 100;
        viewState.setAlpha(0.0f);
        viewState.notGoneIndex = 4;
        viewState.applyToView(child);

        mKosmos.getGroupExpansionManager().setGroupExpanded(parent.getEntryAdapter(), true);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("The parent wasn't added to the overlapping list",
                !overlapList.isEmpty() &&overlapList.get(0) == parent);
        assertTrue("Children only should be added when expanded and visible",
                !overlapList.contains(child));
        assertTrue("Children of expanded group was added non-overlapping",
                nonOverlapList.contains(child));
    }

    @Test
    @EnableFlags({com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            com.android.systemui.Flags.FLAG_NOTIFICATION_BUNDLE_UI})
    public void testOverlapListCreation_expanded_group_transient() {
        ExpandableNotificationRow parent = createRowGroup();
        mStackScroller.addContainerView(parent);

        ExpandableViewState viewState = parent.getViewState();
        viewState.initFrom(parent);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 0;
        viewState.applyToView(parent);

        ExpandableNotificationRow child = parent.getAttachedChildren().getLast();
        viewState = child.getViewState();
        viewState.initFrom(child);
        viewState.setYTranslation(200f);
        viewState.height = 100;
        viewState.setAlpha(1.0f);
        viewState.notGoneIndex = 4;
        viewState.applyToView(child);
        parent.removeChildNotification(child);
        child.setTransientContainer(parent.getChildrenContainer());
        parent.getChildrenContainer().addTransientView(child, 0);

        mKosmos.getGroupExpansionManager().setGroupExpanded(parent.getEntryAdapter(), true);

        ArrayList<ExpandableView> overlapList = new ArrayList<>();
        ArrayList<ExpandableView> nonOverlapList = new ArrayList<>();
        mStackScroller.createSortedNotificationLists(overlapList, nonOverlapList);

        assertTrue("The parent wasn't added to the overlapping list",
                !overlapList.isEmpty() && overlapList.get(0) == parent);
        assertTrue("Transient children should be added overlapping when expanded",
                overlapList.contains(child));
        assertTrue("Transient child of expanded group was added non-overlapping",
                !nonOverlapList.contains(child));
    }

    @Test
    @EnableFlags({com.android.systemui.Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT})
    public void testOverlapWhenOutOfBounds() {
        ExpandableNotificationRow firstRow = createRow();
        mStackScroller.addContainerView(firstRow);

        ExpandableViewState viewState = firstRow.getViewState();
        viewState.initFrom(firstRow);
        viewState.setYTranslation(-100f);
        viewState.height = 100;
        viewState.notGoneIndex = 0;
        viewState.applyToView(firstRow);

        mStackScroller.avoidNotificationOverlaps();
        // bigger than because of padding
        assertTrue("TopOverlap not calculated accurately", firstRow.getTopOverlap() == 0);
        assertTrue("BottomOverlap not calculated accurately", firstRow.getBottomOverlap() == 0);
    }

    /**
     * This test validates the legacy behavior when the NotificationBundleUi flag is OFF.
     * It confirms that when a partially visible child is removed, the scroll position
     * incorrectly jumps to an absolute position based on the removed child's location.
     */
    @Test
    @DisableFlags(NotificationBundleUi.FLAG_NAME)
    @DisableSceneContainer
    public void updateScrollStateForRemovedChild_partiallyVisible_bundleUiFlagOff_jumpsToTop() {
        // GIVEN: A scrollable list with two rows, where we can control the height of the first row
        final int rowHeight = 200;
        ExpandableNotificationRow row1 = spy(mKosmos.createRow());
        doReturn(rowHeight).when(row1).getIntrinsicHeight();
        ExpandableNotificationRow row2 = mKosmos.createRow();
        mStackScroller.addContainerView(row1);
        mStackScroller.addContainerView(row2);

        // GIVEN: The NSSL is scrolled down by 100px, making row1 partially visible at the top.
        mStackScroller.setOwnScrollY(100);
        assertThat(mStackScroller.getOwnScrollY()).isEqualTo(100);

        // WHEN: The partially visible row1 is removed (simulating auto-grouping).
        mStackScroller.removeContainerView(row1);

        // THEN: The scroll position should jump to a value LESS THAN its original position.
        assertThat(mStackScroller.getOwnScrollY()).isLessThan(100);
    }

    /**
     * This test validates the new behavior when the NotificationBundleUi flag is ON.
     * It confirms that when a partially visible child is removed, the scroll position is
     * adjusted relatively to maintain visual stability, preventing any jump
     */
    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    @DisableSceneContainer
    public void updateScrollStateForRemovedChild_partiallyVisible_bundleUiFlagOn_adjustsRelative() {
        // GIVEN: A scrollable list with two rows, where we can control the height of the first.
        final int rowHeight = 200;
        final int padding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.notification_divider_height);
        final int childHeight = rowHeight + padding;

        ExpandableNotificationRow row1 = spy(mKosmos.createRow());
        doReturn(rowHeight).when(row1).getIntrinsicHeight();
        ExpandableNotificationRow row2 = mKosmos.createRow();
        mStackScroller.addContainerView(row1);
        mStackScroller.addContainerView(row2);

        // GIVEN: The NSSL is scrolled down by 100px, making row1 partially visible at the top.
        mStackScroller.setOwnScrollY(100);
        assertThat(mStackScroller.getOwnScrollY()).isEqualTo(100);

        // WHEN: The partially visible row1 is removed (simulating auto-grouping).
        mStackScroller.removeContainerView(row1);

        // THEN: The scroll position should be adjusted by the height of the removed child to
        // maintain stability. The new scrollY will be (currentScrollY - childHeight).
        assertThat(mStackScroller.getOwnScrollY()).isEqualTo(100 - childHeight);
    }

    @Test
    @EnableSceneContainer
    public void testRequestScrollToRemoteInput_singleRow() {
        final Consumer<Float> scroller = mock(FloatConsumer.class);
        mStackScroller.setRemoteInputRowBottomBoundConsumer(scroller);

        // GIVEN a non-grouped notification row with specific dimensions and offsets
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        when(row.getTranslationY()).thenReturn(100f);
        when(row.getActualHeight()).thenReturn(200);
        when(row.getRemoteInputActionsContainerExpandedOffset()).thenReturn(50f);
        mTestableResources.addOverride(
                com.android.internal.R.dimen.notification_content_margin, 10);

        // WHEN requestScrollToRemoteInput is called
        mStackScroller.requestScrollToRemoteInput(row);

        // THEN the correct bottom bound is sent
        // expected = translationY + height + remoteInputOffset + contentMargin
        // expected = 100 + 200 + 50 + 10 = 360
        verify(scroller).accept(360f);

        // WHEN requestScrollToRemoteInput is called with null
        mStackScroller.requestScrollToRemoteInput(null);

        // THEN null is sent
        verify(scroller).accept(null);
    }

    @Test
    @EnableSceneContainer
    public void testRequestScrollToRemoteInput_childInGroup() {
        final Consumer<Float> scroller = mock(FloatConsumer.class);
        mStackScroller.setRemoteInputRowBottomBoundConsumer(scroller);

        // GIVEN a grouped notification row
        ExpandableNotificationRow parent = mock(ExpandableNotificationRow.class);
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);
        when(row.getEntryAdapter()).thenReturn(mock(EntryAdapter.class));
        when(mGroupMembershipManger.isChildInGroup(row.getEntryAdapter())).thenReturn(true);
        when(row.getTranslationY()).thenReturn(100f);
        when(row.getActualHeight()).thenReturn(200);
        when(row.getRemoteInputActionsContainerExpandedOffset()).thenReturn(50f);
        when(parent.getTranslationY()).thenReturn(50f);
        when(row.getNotificationParent()).thenReturn(parent);
        mTestableResources.addOverride(
                com.android.internal.R.dimen.notification_content_margin, 10);

        // WHEN requestScrollToRemoteInput is called for the grouped row
        mStackScroller.requestScrollToRemoteInput(row);

        // THEN the correct bottom bound is sent, including the parent's translation
        // expected = parentTranslationY + translationY + height + remoteInputOffset + contentMargin
        // expected = 50 + 100 + 200 + 50 + 10 = 410
        verify(scroller).accept(410f);

        // WHEN requestScrollToRemoteInput is called with null
        mStackScroller.requestScrollToRemoteInput(null);

        // THEN null is sent
        verify(scroller).accept(null);
    }

    private MotionEvent captureTouchSentToSceneFramework() {
        ArgumentCaptor<MotionEvent> captor = ArgumentCaptor.forClass(MotionEvent.class);
        verify(mStackScrollLayoutController).sendTouchToSceneFramework(captor.capture());
        return captor.getValue();
    }

    private void setBarStateForTest(int state) {
        // Can't inject this through the listener or we end up on the actual implementation
        // rather than the mock because the spy just coppied the anonymous inner /shruggie.
        mStackScroller.setStatusBarState(state);
    }

    private void prepareStackScrollerForHunAnimations(
            Consumer<Boolean> headsUpAnimatingAwayListener) {
        mStackScroller.setHeadsUpAnimatingAwayListener(headsUpAnimatingAwayListener);
        mStackScroller.setIsExpanded(false);
        mStackScroller.setAnimationsEnabled(true);
        mStackScroller.setHeadsUpGoingAwayAnimationsAllowed(true);
    }

    private ExpandableNotificationRow createClearableRow() {
        ExpandableNotificationRow row = mock(ExpandableNotificationRow.class);

        when(row.canViewBeCleared()).thenReturn(true);
        if (NotificationBundleUi.isEnabled()) {
            EntryAdapter entryAdapter = mock(EntryAdapter.class);
            when(entryAdapter.isClearable()).thenReturn(true);
            when(row.getEntryAdapter()).thenReturn(entryAdapter);
        } else {
            NotificationEntry entry = mock(NotificationEntry.class);
            when(row.getEntryLegacy()).thenReturn(entry);
            when(entry.isClearable()).thenReturn(true);
        }

        return row;
    }

    private void assertClearAllInProgress(boolean expected) {
        assertEquals(expected, mStackScroller.getClearAllInProgress());
        assertEquals(expected, mAmbientState.isClearAllInProgress());
    }

    private int px(@DimenRes int id) {
        return mTestableResources.getResources().getDimensionPixelSize(id);
    }

    private static void mockBoundsOnScreen(View view, Rect bounds) {
        doAnswer(invocation -> {
            Rect out = invocation.getArgument(0);
            out.set(bounds);
            return null;
        }).when(view).getBoundsOnScreen(any());
    }

    private static MotionEvent transformEventForView(MotionEvent event, View view) {
        // From `ViewGroup#dispatchTransformedTouchEvent`
        MotionEvent transformed = event.copy();
        transformed.offsetLocation(/* deltaX = */-view.getLeft(), /* deltaY = */ -view.getTop());
        return transformed;
    }

    private static MotionEvent createMotionEvent(float x, float y) {
        return MotionEvent.obtain(
                /* downTime= */0,
                /* eventTime= */0,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                /* metaState= */0
        );
    }

    private MotionEventSubject assertThatMotionEvent(MotionEvent actual) {
        return new MotionEventSubject(actual);
    }

    private static class MotionEventSubject {
        private final MotionEvent mActual;

        MotionEventSubject(MotionEvent actual) {
            mActual = actual;
        }

        public void matches(MotionEvent expected) {
            assertThat(mActual.getActionMasked()).isEqualTo(expected.getActionMasked());
            assertThat(mActual.getDownTime()).isEqualTo(expected.getDownTime());
            assertThat(mActual.getEventTime()).isEqualTo(expected.getEventTime());
            assertThat(mActual.getX()).isEqualTo(expected.getX());
            assertThat(mActual.getY()).isEqualTo(expected.getY());
        }
    }

    private abstract static class BooleanConsumer implements Consumer<Boolean> { }

    private abstract static class FloatConsumer implements Consumer<Float> { }

    private ShadeScrimShape createScrimShape(int left, int top, int right, int bottom) {
        ShadeScrimBounds bounds = new ShadeScrimBounds(left, top, right, bottom);
        return new ShadeScrimShape(bounds, 0, 0);
    }
}
