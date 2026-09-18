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

package com.android.server.personalcontext.notifications;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.os.Bundle;
import android.service.notification.Adjustment;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.DisplayInsight;
import android.service.personalcontext.insight.InsightTraverser;
import android.service.personalcontext.insight.InsightVisitor;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.util.Log;
import android.util.Slog;

import com.android.server.notification.NotificationManagerInternal;
import com.android.server.personalcontext.component.Renderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link Renderer} that adds contextual {@link android.app.Notification.Action}s or text replies
 * to a {@link StatusBarNotification} based on a given {@link ContextInsight}.
 *
 * <p>This renderer inspects the provided {@link ContextInsight} to identify the target notification
 * and the type of action to be added. It then constructs the appropriate action or reply and
 * applies it to the notification using an {@link Adjustment}. This renderer can handle a single
 * {@link ActionableInsight} or an {@link InsightCollection} of them. When processing an {@link
 * InsightCollection}, it groups insights by notification and creates a single {@link Adjustment}
 * per notification.
 *
 * @hide
 */
public class NotificationActionRenderer implements Renderer {
    private static final String TAG = "NotifActionRenderer";

    static final int MAX_NOTIFICATION_ACTIONS = 4;
    static final int MAX_TEXT_REPLIES = 5;

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** Explanations are not used by the system, therefore we do not provide one. */
    private static final String NO_EXPLANATION = "";

    private final NotificationManagerInternal mNotificationManagerInternal;
    private final NotificationActionFactory mNotificationActionFactory;
    private final UUID mComponentId = UUID.randomUUID();

    public NotificationActionRenderer(
            NotificationManagerInternal nmi, NotificationActionFactory notificationActionFactory) {
        mNotificationManagerInternal = nmi;
        mNotificationActionFactory = notificationActionFactory;
    }

    @Override
    public int getProperties() {
        return Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS;
    }

    @Nullable
    private static StatusBarNotification getSbnFromInsight(ContextInsight insight) {
        for (ContextHint hint : PublishedContextHint.unwrapList(insight.getOriginHints())) {
            if (hint instanceof NotificationHint notificationHint
                    && notificationHint.getNotificationEvent()
                            instanceof NotificationEnqueuedEvent enqueuedEvent) {
                return enqueuedEvent.getStatusBarNotification();
            }
        }
        return null;
    }

    @Override
    public void render(@NonNull PublishedContextInsight publishedContextInsight,
            RenderToken renderToken) {
        if (mNotificationManagerInternal == null) {
            Slog.e(TAG, "NotificationManagerInternal not found.");
            return;
        }

        final ContextInsight insight = publishedContextInsight.getInsight();

        final Map<String, AdjustmentInfo> adjustmentsInfo = new LinkedHashMap<>();
        final InsightCollector collector =
                new InsightCollector(mNotificationActionFactory, adjustmentsInfo);
        InsightTraverser.traverse(insight, collector);

        if (adjustmentsInfo.isEmpty()) {
            if (DEBUG) {
                Slog.d(TAG, "No relevant insights to render from: " + insight);
            }
            return;
        }

        List<Adjustment> adjustments = createAdjustments(adjustmentsInfo);

        if (!adjustments.isEmpty()) {
            if (DEBUG) {
                Slog.d(TAG, "Creating adjustments: " + adjustments);
            }
            mNotificationManagerInternal.requestSystemAdjustments(adjustments);
        }
    }

    /**
     * Creates a list of {@link Adjustment}s from a map of grouped insights.
     *
     * @param insightsByNotificationKey A map of insights grouped by notification key.
     * @return A list of {@link Adjustment}s to be applied.
     */
    @NonNull
    private List<Adjustment> createAdjustments(
            @NonNull Map<String, AdjustmentInfo> adjustmentsInfo) {
        final List<Adjustment> adjustments = new ArrayList<>();
        for (final AdjustmentInfo adjustmentInfo : adjustmentsInfo.values()) {
            final Adjustment adjustment = createAdjustmentFromInfo(adjustmentInfo);
            if (adjustment != null) {
                adjustments.add(adjustment);
            }
        }
        return adjustments;
    }

    /**
     * Creates an {@link Adjustment} for a group of {@link ContextInsight}s that belong to the same
     * notification.
     *
     * @param insightGroup A group of {@link ContextInsight}s for a single notification.
     * @return An {@link Adjustment} containing all the generated actions and replies, or {@code
     *     null} if none could be created.
     */
    @Nullable
    private Adjustment createAdjustmentFromInfo(@NonNull AdjustmentInfo adjustmentInfo) {
        if (adjustmentInfo.mActions.isEmpty() && adjustmentInfo.mTextReplies.isEmpty()) {
            Slog.w(
                    TAG,
                    "Could not create any notification actions or replies for sbn: "
                            + adjustmentInfo.mSbn.getKey());
            return null;
        }

        return createAdjustment(
                adjustmentInfo.mSbn, adjustmentInfo.mActions, adjustmentInfo.mTextReplies);
    }

    /**
     * Creates a {@link Notification.Action} from the provided {@link ActionableInsight}.
     *
     * <p>This method extracts the action intent, title, and icon from the insight. If the title or
     * icon are not available in the insight, it falls back to using the application's label and
     * icon.
     *
     * @param insight The insight containing the details for the action.
     * @return A {@link Notification.Action} if it can be created, or {@code null} otherwise.
     */
    @Nullable
    private Adjustment createAdjustment(
            StatusBarNotification sbn,
            List<Notification.Action> actions,
            List<CharSequence> textReplies) {
        final Bundle signals = new Bundle();

        if (!actions.isEmpty()) {
            signals.putParcelableArrayList(
                    Adjustment.KEY_CONTEXTUAL_ACTIONS, new ArrayList<>(actions));
        }

        if (!textReplies.isEmpty()) {
            signals.putCharSequenceArrayList(
                    Adjustment.KEY_TEXT_REPLIES, new ArrayList<>(textReplies));
        }

        return new Adjustment(
                sbn.getPackageName(), sbn.getKey(), signals, NO_EXPLANATION, sbn.getUser());
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    @Override
    public boolean isInterestedInInsight(PublishedContextInsight insight) {
        // Notifications should be rendered due to a RenderToken, which bypasses this filter.
        // We don't want any other random insights.
        return false;
    }

    /**
     * Holds state for a specific notification adjustment. Encapsulates the logic for enforcing
     * system limits on actions and replies.
     */
    private static class AdjustmentInfo {
        final StatusBarNotification mSbn;
        final List<Notification.Action> mActions = new ArrayList<>();
        final List<CharSequence> mTextReplies = new ArrayList<>();

        AdjustmentInfo(StatusBarNotification sbn) {
            mSbn = sbn;
        }

        /**
         * Adds a contextual action to this adjustment.
         *
         * @return {@code true} if the action was added, or {@code false} if the limit has been
         *     reached.
         */
        boolean addAction(@NonNull Notification.Action action) {
            if (isActionLimitReached()) {
                Slog.w(TAG, "Max number of actions reached. Dropping action.");
                return false;
            }
            mActions.add(action);
            return true;
        }

        /**
         * Adds a text reply suggestion to this adjustment.
         *
         * @return {@code true} if the reply was added, or {@code false} if the limit has been
         *     reached.
         */
        boolean addReply(@NonNull CharSequence reply) {
            if (isReplyLimitReached()) {
                Slog.w(TAG, "Max number of replies reached. Dropping reply.");
                return false;
            }
            mTextReplies.add(reply);
            return true;
        }

        /** Returns {@code true} if the number of actions has reached the system limit. */
        boolean isActionLimitReached() {
            return mActions.size() >= MAX_NOTIFICATION_ACTIONS;
        }

        /** Returns {@code true} if the number of text replies has reached the system limit. */
        boolean isReplyLimitReached() {
            return mTextReplies.size() >= MAX_TEXT_REPLIES;
        }
    }

    /**
     * An {@link InsightVisitor} that collects {@link ActionableInsight}s and {@link
     * DisplayInsight}s, groups them by notification, and prepares them for adjustment.
     */
    private static class InsightCollector implements InsightVisitor {
        private final NotificationActionFactory mNotificationActionFactory;
        private final Map<String, AdjustmentInfo> mAdjustments;

        InsightCollector(
                @NonNull NotificationActionFactory notificationActionFactory,
                @NonNull Map<String, AdjustmentInfo> adjustments) {
            mNotificationActionFactory = notificationActionFactory;
            mAdjustments = adjustments;
        }

        @Nullable
        private AdjustmentInfo getAdjustmentInfo(@NonNull ContextInsight insight) {
            final StatusBarNotification sbn = getSbnFromInsight(insight);
            if (sbn == null) {
                return null;
            }
            return mAdjustments.computeIfAbsent(sbn.getKey(), k -> new AdjustmentInfo(sbn));
        }

        @Override
        public void visit(@NonNull ActionableInsight insight, int index) {
            final AdjustmentInfo info = getAdjustmentInfo(insight);
            if (info == null || info.isActionLimitReached()) {
                return;
            }

            final Notification.Action action =
                    mNotificationActionFactory.createNotificationAction(insight);

            if (action != null) {
                info.addAction(action);
            }
        }

        @Override
        public void visit(@NonNull DisplayInsight insight, int index) {
            final AdjustmentInfo info = getAdjustmentInfo(insight);
            if (info == null || info.isReplyLimitReached()) {
                return;
            }

            final CharSequence reply = insight.getDetails().getTitle();
            if (reply != null) {
                info.addReply(reply);
            }
        }
    }
}
