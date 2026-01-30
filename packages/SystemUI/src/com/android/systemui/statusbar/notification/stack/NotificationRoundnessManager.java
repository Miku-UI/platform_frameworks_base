/*
 * Copyright (C) 2018 The Android Open Source Project
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

import androidx.annotation.NonNull;

import com.android.systemui.Dumpable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.statusbar.notification.Roundable;
import com.android.systemui.statusbar.notification.SourceType;
import com.android.systemui.statusbar.notification.TopBottomRoundness;
import com.android.systemui.statusbar.notification.row.ExpandableView;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

/**
 * A class that manages the roundness for notification views
 */
@SysUISingleton
public class NotificationRoundnessManager implements Dumpable {

    private static final String TAG = "NotificationRoundnessManager";
    private static final SourceType DISMISS_ANIMATION = SourceType.from("DismissAnimation");

    private final DumpManager mDumpManager;
    private HashSet<ExpandableView> mAnimatedChildren;
    private boolean mRoundForPulsingViews;
    private boolean mIsClearAllInProgress;

    private List<Roundable> mCurrentRoundables;

    @Inject
    NotificationRoundnessManager(DumpManager dumpManager) {
        mDumpManager = dumpManager;
        mDumpManager.registerDumpable(TAG, this);
        mCurrentRoundables = new ArrayList<>();
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("roundForPulsingViews=" + mRoundForPulsingViews);
        pw.println("isClearAllInProgress=" + mIsClearAllInProgress);
    }

    public boolean isViewAffectedBySwipe(ExpandableView expandableView) {
        return expandableView != null && mCurrentRoundables.contains(expandableView);
    }

    void setViewsAffectedBySwipe(List<Roundable> newViews) {
        // This method caches a new set of current View targets and reset the roundness of the old
        // View targets (if any) to 0f.

        // Make a copy of the current views
        List<Roundable> oldViews = new ArrayList<>(mCurrentRoundables);

        // From the old set, mark any view that is also contained in the new set as null. The old
        // set will be used to reset roundness but we don't want to modify views that are present on
        // both.
        for (int i = 0; i < oldViews.size(); i++) {
            if (newViews.contains(oldViews.get(i))) {
                oldViews.set(i, null);
            }
        }

        // Reset roundness of the remaining old views
        for (Roundable oldView : oldViews) {
            if (oldView != null) {
                oldView.requestRoundnessReset(DISMISS_ANIMATION);
            }
        }

        // Replace the current set of views
        mCurrentRoundables = newViews;
    }

    void setViewsAffectedBySwipe(
            Roundable viewBefore,
            ExpandableView viewSwiped,
            Roundable viewAfter) {
        List<Roundable> newViews = new ArrayList<>();
        newViews.add(viewBefore);
        newViews.add(viewSwiped);
        newViews.add(viewAfter);
        setViewsAffectedBySwipe(newViews);
    }

    void setRoundnessForAffectedViews(float roundness) {
        for (Roundable affected : mCurrentRoundables) {
            if (affected != null) {
                affected.requestRoundness(roundness, roundness, DISMISS_ANIMATION);
            }
        }
    }

    void setRoundnessForAffectedViews(List<TopBottomRoundness> roundnessSet, boolean animate) {
        if (roundnessSet.size() != mCurrentRoundables.size()) return;

        // In case there are fewer actual roundables than the full set/list allows for (for example,
        // if the notification is within a group and close to the group header), track which is the
        // first non-null element to avoid unnecessarily rounding the top corners.
        boolean seenFirst = false;
        for (int i = 0; i < roundnessSet.size(); i++) {
            Roundable roundable = mCurrentRoundables.get(i);
            TopBottomRoundness roundnessConfig = roundnessSet.get(i);
            if (roundable != null) {
                if (!seenFirst) {
                    seenFirst = true;
                    roundable.requestBottomRoundness(roundnessConfig.getBottomRoundness(),
                            DISMISS_ANIMATION, animate);
                } else {
                    roundable.requestRoundness(roundnessConfig.getTopRoundness(),
                            roundnessConfig.getBottomRoundness(), DISMISS_ANIMATION, animate);
                }
            }
        }
    }

    void setClearAllInProgress(boolean isClearingAll) {
        mIsClearAllInProgress = isClearingAll;
    }

    /**
     * Check if "Clear all" notifications is in progress.
     */
    public boolean isClearAllInProgress() {
        return mIsClearAllInProgress;
    }

    /**
     * Check if we can request the `Pulsing` roundness for notification.
     */
    public boolean shouldRoundNotificationPulsing() {
        return mRoundForPulsingViews;
    }

    public void setAnimatedChildren(HashSet<ExpandableView> animatedChildren) {
        mAnimatedChildren = animatedChildren;
    }

    /**
     * Check if the view should be animated
     * @param view target view
     * @return true, if is in the AnimatedChildren set
     */
    public boolean isAnimatedChild(ExpandableView view) {
        return mAnimatedChildren.contains(view);
    }

    public void setShouldRoundPulsingViews(boolean shouldRoundPulsingViews) {
        mRoundForPulsingViews = shouldRoundPulsingViews;
    }

    public boolean isSwipedViewSet() {
        return !mCurrentRoundables.isEmpty()
                && mCurrentRoundables.get(mCurrentRoundables.size() / 2) != null;
    }
}
