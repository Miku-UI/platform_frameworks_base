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
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.personalcontext.insight.ActionableInsight;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Locale;

/**
 * Helper class to resolve action intents and remote actions into {@link PendingIntent}s and {@link
 * ResolveInfo} for notification actions.
 */
public class ContextActionResolver {
    private static final String TAG = "ContextActionResolver";

    private static final int PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final PendingIntentFactory mPendingIntentFactory;

    @VisibleForTesting
    public ContextActionResolver(
            @NonNull Context context,
            @NonNull PackageManager packageManager,
            @NonNull PendingIntentFactory pendingIntentFactory) {
        mContext = context;
        mPackageManager = packageManager;
        mPendingIntentFactory = pendingIntentFactory;
    }

    public ContextActionResolver(@NonNull Context context) {
        this(context, context.getPackageManager(), new PendingIntentFactoryImpl());
    }

    /** Represents the type of action being performed. */
    public enum ActionType {
        ACTIVITY,
        SERVICE,
        BROADCAST,
        UNKNOWN
    }

    /** Encapsulates the result of resolving an action intent. */
    public static class ResolutionResult {
        @Nullable public final PendingIntent pendingIntent;
        @Nullable public final ResolveInfo resolveInfo;
        @NonNull public final ActionType actionType;

        ResolutionResult(
                @Nullable PendingIntent pendingIntent,
                @Nullable ResolveInfo resolveInfo,
                @NonNull ActionType actionType) {
            this.pendingIntent = pendingIntent;
            this.resolveInfo = resolveInfo;
            this.actionType = actionType;
        }
    }

    /**
     * Resolves the given action intent or remote action to a PendingIntent and ResolveInfo.
     *
     * @param insight The insight containing the details for the action.
     * @param needsComponentInfo If true, the {@link ResolveInfo} will be resolved.
     * @return A {@link ResolutionResult} if successful, or {@code null} otherwise.
     */
    @Nullable
    public ResolutionResult resolveActionIntent(
            @NonNull ActionableInsight insight, boolean needsComponentInfo) {
        final PendingIntent actionIntent = insight.getActionDetails().getPendingIntent();
        final RemoteAction remoteAction = insight.getActionDetails().getRemoteAction();

        // Strategy 1: RemoteAction
        ResolutionResult result = resolveFromRemoteAction(remoteAction, needsComponentInfo);
        if (result != null) {
            return result;
        }

        // Strategy 2: Pending Intent
        result = resolveFromPendingIntent(actionIntent);
        if (result != null) {
            return result;
        }

        Slog.w(TAG, "Could not resolve action for insight: " + insight);
        return null;
    }

    @Nullable
    private ResolutionResult resolveFromRemoteAction(
            @Nullable RemoteAction remoteAction, boolean needsComponentInfo) {
        if (remoteAction == null) {
            return null;
        }

        final PendingIntent pendingIntent = remoteAction.getActionIntent();
        if (pendingIntent == null) {
            return null;
        }

        final ActionType actionType = getActionTypeFromPendingIntent(pendingIntent);

        if (actionType == ActionType.UNKNOWN) {
            Slog.w(TAG, "Unknown action type for remote action: " + remoteAction);
            return null;
        }

        if (!needsComponentInfo) {
            return new ResolutionResult(pendingIntent, null, actionType);
        }

        final Intent resolvedIntent = pendingIntent.getIntent();
        if (resolvedIntent == null) {
            Slog.w(TAG, "RemoteAction's PendingIntent.getIntent() returned null.");
            return null;
        }

        final ResolutionResult resolution = resolveComponent(resolvedIntent, actionType);
        if (resolution == null || resolution.resolveInfo == null) {
            Slog.w(TAG, "Could not resolve remote action: " + remoteAction);
            return null;
        }

        return new ResolutionResult(pendingIntent, resolution.resolveInfo, actionType);
    }

    private static ActionType getActionTypeFromPendingIntent(@NonNull PendingIntent pendingIntent) {
        if (pendingIntent.isActivity()) {
            return ActionType.ACTIVITY;
        }
        if (pendingIntent.isService()) {
            return ActionType.SERVICE;
        }
        if (pendingIntent.isBroadcast()) {
            return ActionType.BROADCAST;
        }
        return ActionType.UNKNOWN;
    }

    @Nullable
    private ResolutionResult resolveFromPendingIntent(@Nullable PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            return null;
        }

        final ResolutionResult resolution = resolveComponent(
                pendingIntent.getIntent(), getActionTypeFromPendingIntent(pendingIntent));

        if (resolution != null
                && resolution.resolveInfo != null
                && resolution.actionType != ActionType.UNKNOWN) {
            ResolveInfo resolveInfo = resolution.resolveInfo;
            ActionType actionType = resolution.actionType;

            return new ResolutionResult(pendingIntent, resolveInfo, actionType);
        }
        return null;
    }

    /**
     * Helper method to resolve an Intent to a {@link ResolveInfo} and {@link ActionType}.
     *
     * <p>The resolution order prioritizes Activities > Services > Broadcasts.
     *
     * @param intent The intent to resolve.
     * @param hint An optional hint for the expected ActionType, to optimize resolution.
     * @return A {@link ResolutionResult} containing the resolved {@link ResolveInfo} and {@link
     *     ActionType}, or {@code null} if no component could be resolved.
     */
    @Nullable
    private ResolutionResult resolveComponent(@NonNull Intent intent, @Nullable ActionType hint) {
        final int flags = PackageManager.MATCH_ALL;

        // 1. Try Hint
        if (hint != null) {
            final ResolutionResult result = resolveSpecificType(intent, hint, flags);
            if (result != null) return result;
        }

        // 2. Try Activity
        if (hint != ActionType.ACTIVITY) {
            final ResolutionResult result = resolveSpecificType(intent, ActionType.ACTIVITY, flags);
            if (result != null) return result;
        }

        // 3. Try Service
        if (hint != ActionType.SERVICE) {
            final ResolutionResult result = resolveSpecificType(intent, ActionType.SERVICE, flags);
            if (result != null) return result;
        }

        // 4. Try Broadcast
        if (hint != ActionType.BROADCAST) {
            final ResolutionResult result =
                    resolveSpecificType(intent, ActionType.BROADCAST, flags);
            if (result != null) return result;
        }

        Slog.w(TAG, "Could not resolve intent: " + intent);
        return null;
    }

    @Nullable
    private ResolutionResult resolveSpecificType(
            @NonNull Intent intent, @NonNull ActionType type, int flags) {
        List<ResolveInfo> results;
        switch (type) {
            case ACTIVITY -> results = mPackageManager.queryIntentActivities(intent, flags);
            case SERVICE -> results = mPackageManager.queryIntentServices(intent, flags);
            case BROADCAST -> results = mPackageManager.queryBroadcastReceivers(intent, flags);
            default -> {
                Slog.w(TAG, "Unexpected ActionType for resolution: " + type);
                return null;
            }
        }

        if (results != null && !results.isEmpty()) {
            if (results.size() > 1) {
                Slog.w(
                        TAG,
                        "Multiple "
                                + type.name().toLowerCase(Locale.ROOT)
                                + "s resolved for intent: "
                                + intent);
                if (type == ActionType.SERVICE || type == ActionType.BROADCAST) {
                    Slog.w(TAG, "Failing safe for Service/Broadcast, returning null.");
                    return null;
                }
                Slog.w(TAG, "Picking the first activity.");
            }
            return new ResolutionResult(null, results.get(0), type);
        }
        return null;
    }
}
