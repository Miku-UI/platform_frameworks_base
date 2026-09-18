/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.stack;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.systemui.statusbar.notification.stack.NotificationPriorityBucketKt.BUCKET_FOREGROUND_SERVICE;
import static com.android.systemui.statusbar.notification.stack.NotificationPriorityBucketKt.BUCKET_HEADS_UP;
import static com.android.systemui.statusbar.notification.stack.NotificationPriorityBucketKt.BUCKET_SILENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.EnableFlags;
import android.testing.TestableLooper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.kosmos.KosmosJavaAdapter;
import com.android.systemui.media.controls.ui.controller.KeyguardMediaController;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.notification.collection.BundleSpec;
import com.android.systemui.statusbar.notification.collection.render.MediaContainerController;
import com.android.systemui.statusbar.notification.collection.render.SectionHeaderController;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.shared.NmContextualDisplay;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class NotificationSectionsManagerTest extends SysuiTestCase {

    @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock private NotificationStackScrollLayout mNssl;
    @Mock private StatusBarStateController mStatusBarStateController;
    @Mock private ConfigurationController mConfigurationController;
    @Mock private KeyguardMediaController mKeyguardMediaController;
    @Mock private MediaContainerController mMediaContainerController;
    @Mock private NotificationRoundnessManager mNotificationRoundnessManager;
    @Mock private SectionHeaderController mIncomingHeaderController;
    @Mock private SectionHeaderController mPeopleHeaderController;
    @Mock private SectionHeaderController mAlertingHeaderController;
    @Mock private SectionHeaderController mSilentHeaderController;
    @Mock private SectionHeaderController mHighlightsHeaderController;

    private NotificationSectionsManager mSectionsManager;

    private final KosmosJavaAdapter mKosmos = new KosmosJavaAdapter(this);

    @Before
    public void setUp() {
        mSectionsManager =
                new NotificationSectionsManager(
                        mConfigurationController,
                        mKeyguardMediaController,
                        mMediaContainerController,
                        mNotificationRoundnessManager,
                        mIncomingHeaderController,
                        mPeopleHeaderController,
                        mAlertingHeaderController,
                        mSilentHeaderController,
                        mHighlightsHeaderController
                );
        // Required in order for the header inflation to work properly
        when(mNssl.generateLayoutParams(any(AttributeSet.class)))
                .thenReturn(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        mSectionsManager.initialize(mNssl);
        when(mNssl.indexOfChild(any(View.class))).thenReturn(-1);
        when(mStatusBarStateController.getState()).thenReturn(StatusBarState.SHADE);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateInitializeThrows() {
        mSectionsManager.initialize(mNssl);
    }

    @Test
    public void hasIntrinsicBottomRoundness_no_only_neighbor_roundness() {
        ExpandableNotificationRow view = mKosmos.createRow();
        view.requestBottomRoundness(1f, NotificationSectionsManager.Companion.getFOLLOWING());
        assertThat(mSectionsManager.hasIntrinsicBottomRoundness(view)).isFalse();
    }

    @Test
    public void hasIntrinsicBottomRoundness_yes_neighbor_plus() {
        ExpandableNotificationRow view = mKosmos.createRow();
        view.requestBottomRoundness(1f, NotificationSectionsManager.Companion.getFOLLOWING());
        view.requestBottomRoundness(1f, NotificationSectionsManager.Companion.getBUNDLE());
        assertThat(mSectionsManager.hasIntrinsicBottomRoundness(view)).isTrue();
    }

    @Test
    public void hasIntrinsicTopRoundness_no_only_neighbor_roundness() {
        ExpandableNotificationRow view = mKosmos.createRow();
        view.requestTopRoundness(1f, NotificationSectionsManager.Companion.getPREVIOUS());
        assertThat(mSectionsManager.hasIntrinsicTopRoundness(view)).isFalse();
    }

    @Test
    public void hasIntrinsicTopRoundness_yes_neighbor_plus() {
        ExpandableNotificationRow view = mKosmos.createRow();
        view.requestTopRoundness(1f, NotificationSectionsManager.Companion.getPREVIOUS());
        view.requestTopRoundness(1f, NotificationSectionsManager.Companion.getBUNDLE());
        assertThat(mSectionsManager.hasIntrinsicTopRoundness(view)).isTrue();
    }

    @Test
    @EnableFlags(NmContextualDisplay.FLAG_NAME)
    public void testRoundAllBundles() {
        ExpandableNotificationRow be1 = mKosmos.createRowBundle(BundleSpec.Companion.getNEWS());
        ExpandableNotificationRow be2 = mKosmos.createRowBundle(
                BundleSpec.Companion.getRECOMMENDED());
        ExpandableNotificationRow silentSolo = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_SILENT);
                    return builder.done();
                }));
        ExpandableNotificationRow silentSolo2 = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_SILENT);
                    return builder.done();
                }));

        ExpandableNotificationRow silentSolo3 = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_SILENT);
                    return builder.done();
                }));

        List<ExpandableView> views = List.of(silentSolo, be1, be2, silentSolo2, silentSolo3);
        mSectionsManager.updateFirstAndLastViewsForAllSections(views);

        assertThat(silentSolo.hasRoundedTopCorners()).isTrue();
        assertThat(silentSolo.hasRoundedBottomCorners()).isTrue();
        assertThat(silentSolo.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());
        assertThat(silentSolo.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getFOLLOWING());

        assertThat(be1.hasRoundedTopCorners()).isTrue();
        assertThat(be1.hasRoundedBottomCorners()).isTrue();
        assertThat(be1.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getBUNDLE());
        assertThat(be1.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getBUNDLE(),
                NotificationSectionsManager.Companion.getFOLLOWING());

        assertThat(be2.hasRoundedTopCorners()).isTrue();
        assertThat(be2.hasRoundedBottomCorners()).isTrue();
        assertThat(be2.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getBUNDLE(),
                NotificationSectionsManager.Companion.getPREVIOUS());
        assertThat(be2.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getBUNDLE());

        assertThat(silentSolo2.hasRoundedTopCorners()).isTrue();
        assertThat(silentSolo2.hasRoundedBottomCorners()).isFalse();
        assertThat(silentSolo2.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getPREVIOUS());
        assertThat(silentSolo2.getBottomRoundnessSources()).isEmpty();

        assertThat(silentSolo3.hasRoundedTopCorners()).isFalse();
        assertThat(silentSolo3.hasRoundedBottomCorners()).isTrue();
        assertThat(silentSolo3.getTopRoundnessSources()).isEmpty();
        assertThat(silentSolo3.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());
    }

    @Test
    @EnableFlags(NmContextualDisplay.FLAG_NAME)
    public void testSilentSectionRounding_afterBundleRemoval() {
        ExpandableNotificationRow be1 = mKosmos.createRowBundle(BundleSpec.Companion.getNEWS());
        ExpandableNotificationRow silentSolo = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_SILENT);
                    return builder.done();
                }));
        ExpandableNotificationRow silentSolo2 = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_SILENT);
                    return builder.done();
                }));


        List<ExpandableView> views = List.of(silentSolo, be1, silentSolo2);
        mSectionsManager.updateFirstAndLastViewsForAllSections(views);

        assertThat(silentSolo.hasRoundedTopCorners()).isTrue();
        assertThat(silentSolo.hasRoundedBottomCorners()).isTrue();
        assertThat(silentSolo.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());
        assertThat(silentSolo.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getFOLLOWING());

        assertThat(be1.hasRoundedTopCorners()).isTrue();
        assertThat(be1.hasRoundedBottomCorners()).isTrue();
        assertThat(be1.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getBUNDLE());
        assertThat(be1.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getBUNDLE());

        assertThat(silentSolo2.hasRoundedTopCorners()).isTrue();
        assertThat(silentSolo2.hasRoundedBottomCorners()).isTrue();
        assertThat(silentSolo2.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getPREVIOUS());
        assertThat(silentSolo2.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());

        // remove bundle and check corners of remaining ENRs
        views = List.of(silentSolo, silentSolo2);
        mSectionsManager.updateFirstAndLastViewsForAllSections(views);

        assertThat(silentSolo.hasRoundedTopCorners()).isTrue();
        assertThat(silentSolo.hasRoundedBottomCorners()).isFalse();
        assertThat(silentSolo.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());
        assertThat(silentSolo.getBottomRoundnessSources()).isEmpty();

        assertThat(silentSolo2.hasRoundedTopCorners()).isFalse();
        assertThat(silentSolo2.hasRoundedBottomCorners()).isTrue();
        assertThat(silentSolo2.getTopRoundnessSources()).isEmpty();
        assertThat(silentSolo2.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());
    }

    @Test
    @EnableFlags(NmContextualDisplay.FLAG_NAME)
    public void testSiblingRoundingOnlyWithinSection() {
        ExpandableNotificationRow be1 = mKosmos.createRowBundle(BundleSpec.Companion.getNEWS());
        ExpandableNotificationRow silentSolo = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_HEADS_UP);
                    return builder.done();
                }));

        List<ExpandableView> views = List.of(silentSolo, be1);
        mSectionsManager.updateFirstAndLastViewsForAllSections(views);

        assertThat(silentSolo.hasRoundedTopCorners()).isTrue();
        assertThat(silentSolo.hasRoundedBottomCorners()).isTrue();
        assertThat(silentSolo.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());
        assertThat(silentSolo.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION());

        assertThat(be1.hasRoundedTopCorners()).isTrue();
        assertThat(be1.hasRoundedBottomCorners()).isTrue();
        assertThat(be1.getTopRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION(),
                NotificationSectionsManager.Companion.getBUNDLE());
        assertThat(be1.getBottomRoundnessSources()).containsExactly(
                NotificationSectionsManager.Companion.getSECTION(),
                NotificationSectionsManager.Companion.getBUNDLE());
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_RICH_ONGOING_IMPROVEMENTS)
    public void hasRoundedCorners_groupingDisabledChildInGroup_returnsFalse() {
        // GIVEN - Notification is in FGS section
        final ExpandableNotificationRow fgs = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_FOREGROUND_SERVICE);
                    return builder.done();
                }));

        // WHEN - NSSL updates sections rounding
        mSectionsManager.updateFirstAndLastViewsForAllSections(List.of(fgs));

        // THEN - FGS notification is rounded.
        assertThat(fgs.hasRoundedTopCorners()).isTrue();
        assertThat(fgs.hasRoundedBottomCorners()).isTrue();

        // GIVEN - FGS Notification is grouped.
        final ExpandableNotificationRow group = mKosmos.createRowGroup();
        group.addChildNotification(fgs, 2);

        // WHEN - NSSL updates sections rounding
        mSectionsManager.updateFirstAndLastViewsForAllSections(List.of(group));

        // THEN - FGS notification is not rounded.
        assertThat(fgs.hasRoundedTopCorners()).isFalse();
        assertThat(fgs.hasRoundedBottomCorners()).isFalse();
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_RICH_ONGOING_IMPROVEMENTS)
    public void isGroupingDisabled_for_fgs_returnsTrue() {
        ExpandableNotificationRow fgsRow = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setBucket(BUCKET_FOREGROUND_SERVICE);
                    return builder.done();
                }));
        assertThat(mSectionsManager.isGroupingDisabled(fgsRow)).isTrue();
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_RICH_ONGOING_IMPROVEMENTS)
    public void isGroupingDisabled_for_non_fgs_returnsFalse() {
        for (int bucket : PriorityBucket.Companion.getAllInOrder()) {
            if (bucket == BUCKET_FOREGROUND_SERVICE) {
                continue;
            }

            ExpandableNotificationRow nonFgsNotif = mKosmos.createRow(
                    mKosmos.buildNotificationEntry(builder -> {
                        builder.setBucket(bucket);
                        return builder.done();
                    }));

            assertThat(mSectionsManager.isGroupingDisabled(nonFgsNotif)).isFalse();
        }
    }
}
