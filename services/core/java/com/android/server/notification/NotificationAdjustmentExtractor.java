/**
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
* limitations under the License.
*/
package com.android.server.notification;

import static android.app.Flags.nmContextualDisplayLaunch;
import static android.app.Notification.FLAG_PROMOTED_ONGOING;
import static android.service.notification.Adjustment.KEY_DYNAMIC_BUNDLE;
import static android.service.notification.Adjustment.KEY_TYPE;
import static android.service.notification.Adjustment.KEY_UNCLASSIFY;

import android.annotation.NonNull;
import android.app.NotificationChannel;
import android.app.NotificationRule;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.Adjustment;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Applies adjustments from the group helper and notification assistant
 */
public class NotificationAdjustmentExtractor implements NotificationSignalExtractor {
    private static final String TAG = "AdjustmentExtractor";
    private static final boolean DBG = false;
    private GroupHelper mGroupHelper;
    private NotificationRuleManager mRuleManager;
    private RankingConfig mRankingConfig;

    /** Length of time (in milliseconds) that a noisy notification will stay in its non-bundled
     * classification.
     */
    @VisibleForTesting
    static final long HANG_TIME_MS = 30000;

    @VisibleForTesting
    InjectedTime mInjectedTimeMs = null;

    public void initialize(Context ctx, NotificationUsageStats usageStats) {
        if (DBG) Slog.d(TAG, "Initializing  " + getClass().getSimpleName() + ".");
    }

    public RankingReconsideration process(NotificationRecord record) {
        if (record == null || record.getNotification() == null) {
            if (DBG) Slog.d(TAG, "skipping empty notification");
            return null;
        }

        if (nmContextualDisplayLaunch()) {
            record.resetRuleBehaviors();
            Adjustment matchingRules = record.getMatchingRulesAdjustment();
            if (matchingRules != null) {
                for (Adjustment adjustment : mRuleManager.getAdjustmentsForRules(matchingRules)) {
                    Bundle signals = adjustment.getSignals();
                    NotificationRule.DynamicBundle dynamicBundle =
                            signals.getParcelable(KEY_DYNAMIC_BUNDLE,
                                    NotificationRule.DynamicBundle.class);
                    if (dynamicBundle != null) {
                        final NotificationChannel newChannel =
                                getDynamicBundleChannelLocked(record, dynamicBundle);
                        if (newChannel.getId().equals(record.getChannel().getId())) {
                            signals.remove(KEY_DYNAMIC_BUNDLE);
                        } else if (record.getNotification().hasFlag(FLAG_PROMOTED_ONGOING)) {
                            // Don't bundle any promoted ongoing notifications
                            signals.remove(KEY_DYNAMIC_BUNDLE);
                        } else {
                            // swap app provided type with the real thing
                            signals.putParcelable(KEY_DYNAMIC_BUNDLE, newChannel);
                        }
                    }
                    record.addAdjustment(adjustment);
                }
            }
        }

        final boolean hasAdjustedClassification = record.hasAdjustment(KEY_TYPE)
                || (nmContextualDisplayLaunch() && record.hasAdjustment(KEY_DYNAMIC_BUNDLE));
        final boolean removedClassification = record.hasAdjustment(KEY_UNCLASSIFY);

        if (hasAdjustedClassification && record.getLastAudiblyAlertedMs() > 0) {
            record.applyAdjustments(new ArraySet<>(new String[] {KEY_TYPE, KEY_DYNAMIC_BUNDLE}));
            return getClassificationReconsideration(record);
        }

        record.applyAdjustments();

        // Classification adjustments trigger regrouping
        if (mGroupHelper != null && (hasAdjustedClassification || removedClassification)) {
            return getRegroupReconsideration(
                    record, hasAdjustedClassification, removedClassification);
        }

        return null;
    }

    @Override
    public void setConfig(RankingConfig config) {
        mRankingConfig = config;
    }

    @Override
    public void setRuleManager(NotificationRuleManager ruleManager) {
        mRuleManager = ruleManager;
    }

    @Override
    public void setGroupHelper(GroupHelper groupHelper) {
        mGroupHelper = groupHelper;
    }

    private long getCurrentTime() {
        if (mInjectedTimeMs != null) {
            return mInjectedTimeMs.getCurrentTimeMillis();
        }
        return System.currentTimeMillis();
    }

    private RankingReconsideration getClassificationReconsideration(NotificationRecord record) {
        return new RankingReconsideration(record.getKey(), HANG_TIME_MS) {
            @Override
            public void work() {
                // pass
            }

            @Override
            public void applyChangesLocked(NotificationRecord record) {
                if ((getCurrentTime() - record.getLastAudiblyAlertedMs()) >= HANG_TIME_MS) {
                    record.applyAdjustments();
                    getRegroupReconsideration(record, true, false).applyChangesLocked(record);
                }
            }
        };
    }

    // The notification channel of the record has changed such that it's now moving to a new
    // UI section. We need to change the record's grouping to make sure it's not in a group
    // for the wrong section
    private RankingReconsideration getRegroupReconsideration(NotificationRecord record,
            boolean hasAdjustedClassification, boolean removedClassification) {
        return new RankingReconsideration(record.getKey(), 0) {
            @Override
            public void work() {
            }

            @Override
            public void applyChangesLocked(NotificationRecord record) {
                if (hasAdjustedClassification) {
                    mGroupHelper.onChannelUpdated(record);
                }
                if (removedClassification) {
                    mGroupHelper.onNotificationUnbundled(record,
                            record.hadGroupSummaryWhenUnclassified());

                    // clear this bit now that we're done reading it
                    record.setHadGroupSummaryWhenUnclassified(false);
                }
            }
        };
    }

    @NonNull
    private NotificationChannel getDynamicBundleChannelLocked(NotificationRecord r,
            NotificationRule.DynamicBundle dynamicBundle) {
        NotificationChannel channel = mRankingConfig.getNotificationChannel(
                r.getSbn().getPackageName(), r.getUid(), dynamicBundle.getChannelId(), false);
        if (channel == null) {
            channel = mRankingConfig.createReservedChannel(r.getSbn().getPackageName(), r.getUid(),
                    dynamicBundle.getChannelId(), dynamicBundle.getBundleName(),
                    dynamicBundle.getEmojiIcon());
        }
        return channel;
    }


    static class InjectedTime {
        private final long mCurrentTimeMillis;

        InjectedTime(long time) {
            mCurrentTimeMillis = time;
        }

        long getCurrentTimeMillis() {
            return mCurrentTimeMillis;
        }
    }
}
