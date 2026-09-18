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
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.InsightDisplayDetails;
import android.util.Slog;

import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;
import com.android.server.personalcontext.notifications.ContextActionResolver.ResolutionResult;

import java.util.Objects;

/**
 * A factory for creating {@link android.app.Notification.Action} instances from {@link
 * ActionableInsight}s.
 *
 * @hide
 */
public class NotificationActionFactory {
    private static final String TAG = "NotifActionFactory";

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final ContextActionResolver mActionResolver;

    public NotificationActionFactory(
            @NonNull Context context,
            @NonNull PackageManager packageManager,
            @NonNull ContextActionResolver actionResolver) {
        mContext = Objects.requireNonNull(context);
        mPackageManager = Objects.requireNonNull(packageManager);
        mActionResolver = Objects.requireNonNull(actionResolver);
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
    Notification.Action createNotificationAction(ActionableInsight insight) {
        final InsightDisplayDetails displayDetails = insight.getDisplayDetails();
        final boolean needsComponentInfo =
                displayDetails.getTitle() == null || displayDetails.getIcon() == null;

        final ResolutionResult resolutionResult =
                mActionResolver.resolveActionIntent(insight, needsComponentInfo);

        if (resolutionResult == null || resolutionResult.pendingIntent == null) {
            return null;
        }

        if (needsComponentInfo
                && (resolutionResult.resolveInfo == null
                        || resolutionResult.actionType == ActionType.UNKNOWN)) {
            Slog.w(TAG, "Needed component info but could not resolve it.");
            return null;
        }

        final PendingIntent pendingIntent = resolutionResult.pendingIntent;
        final ResolveInfo resolveInfo = resolutionResult.resolveInfo;
        final ActionType actionType = resolutionResult.actionType;
        final ComponentInfo componentInfo =
                resolveInfo != null ? resolveInfo.getComponentInfo() : null;

        if (needsComponentInfo && componentInfo == null) {
            Slog.w(TAG, "Missing title/icon, and component info is null.");
            return null;
        }

        final Icon icon = getIconOrDefault(displayDetails.getIcon(), componentInfo);

        if (icon == null) {
            Slog.w(
                    TAG,
                    "Could not get icon to create notification action for "
                            + (componentInfo != null ? componentInfo.packageName : "unknown"));
            return null;
        }

        final CharSequence title =
                getTitleOrDefault(displayDetails.getTitle(), componentInfo, actionType);

        final Bundle extras = new Bundle();
        final CharSequence contentDescription = displayDetails.getContentDescription();
        if (contentDescription != null) {
            extras.putCharSequence(
                    Notification.Action.EXTRA_CONTENT_DESCRIPTION, contentDescription);
        }
        extras.putBoolean(Notification.Action.EXTRA_IS_ANIMATED, true);

        return new Notification.Action.Builder(icon, title, pendingIntent)
                .setContextual(true)
                .addExtras(extras)
                .build();
    }

    @NonNull
    private CharSequence getTitleOrDefault(
            @Nullable CharSequence title,
            @Nullable ComponentInfo componentInfo,
            @NonNull ActionType actionType) {
        if (title != null) {
            return title;
        }
        if (componentInfo == null) {
            return "";
        }
        final CharSequence appLabel =
                mPackageManager.getApplicationLabel(componentInfo.applicationInfo);
        if (actionType == ActionType.ACTIVITY) {
            return mContext.getString(com.android.internal.R.string.open_app_name, appLabel);
        }
        return appLabel;
    }

    @Nullable
    private Icon getIconOrDefault(@Nullable Icon icon, @Nullable ComponentInfo componentInfo) {
        if (icon != null) {
            return icon;
        }
        if (componentInfo == null) {
            return null;
        }
        int iconRes = componentInfo.getIconResource();
        if (iconRes == 0) {
            iconRes = componentInfo.applicationInfo.icon;
        }
        if (iconRes != 0) {
            return Icon.createWithResource(componentInfo.packageName, iconRes);
        }
        return null;
    }
}
