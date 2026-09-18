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

package com.android.extensions.computercontrol;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.RequiresPermission;
import android.app.role.RoleManager;
import android.companion.virtual.CompanionDeviceId;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Extensions for Computer Control features.
 *
 * <p>Internally relies on multiple system features that may be unavailable. Getting an instance via
 * {@link #getInstance(Context)} will enable the creation of new {@link ComputerControlSession}s
 * that enable inputs and outputs for computer control features.</p>
 */
public class ComputerControlExtensions {
    private final ArrayMap<AutomatedPackageListener,
            android.companion.virtual.computercontrol.AutomatedPackageListener> mListeners =
            new ArrayMap<>();

    private final int mTargetComputerControlVersion;

    private ComputerControlExtensions(int targetComputerControlVersion) {
        mTargetComputerControlVersion = targetComputerControlVersion;
    }

    /**
     * Retrieve the current version of the extensions.
     */
    public static int getVersion() {
        return VirtualDeviceManager.COMPUTER_CONTROL_VERSION;
    }

    /**
     * Gets an instance of the ComputerControlExtensions. These extensions can be unavailable on
     * devices. In such cases {@code null} is returned and the extensions won't be available on this
     * device.
     *
     * @param context Context to fetch system features
     * @return An instance of ComputerControlExtensions, or {@code null} if the extensions are
     * unavailable.
     *
     * @deprecated with ComputerControl v5. Use {@link #getInstance(Context, int)} instead.
     *
     */
    // TODO(b/489808412) force this method to getVersion() when finalizing v5
    @Deprecated
    @Nullable
    public static ComputerControlExtensions getInstance(@NonNull Context context) {
        if (!isAvailable(context)) {
            return null;
        }
        // Hardcode version 4 to prevent backwards incompatibilities
        return new ComputerControlExtensions(4);
    }

    /**
     * Gets an instance of the ComputerControlExtensions. These extensions can be unavailable on
     * devices. In such cases {@code null} is returned and the extensions won't be available on this
     * device.
     *
     * @param context Context to fetch system features
     * @param targetComputerControlVersion target version for the ComputerControl extensions
     * @return An instance of ComputerControlExtensions, or {@code null} if the extensions are
     * unavailable.
     */
    @Nullable
    public static ComputerControlExtensions getInstance(@NonNull Context context,
            int targetComputerControlVersion) {
        if (!isAvailable(context)) {
            return null;
        }
        return new ComputerControlExtensions(targetComputerControlVersion);
    }

    /**
     * Requests a new {@link ComputerControlSession} for the given parameters. When the session is
     * no longer used it should be closed by calling {@link ComputerControlSession#close()}.
     *
     * @param params parameters to use for this ComputerControlSession.
     * @param executor An executor to run the callback on.
     * @param callback A callback to get notified about the result of this operation.
     */
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_COMPUTER_CONTROL,
            Manifest.permission.POST_NOTIFICATIONS})
    public void requestSession(@NonNull ComputerControlSession.Params params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull ComputerControlSession.Callback callback) {
        Objects.requireNonNull(params, "Missing ComputerControlSession.Params");
        Objects.requireNonNull(executor, "Missing Executor");
        Objects.requireNonNull(callback, "Missing ComputerControlSession.Callback");

        CompanionDeviceId companionDeviceId = null;
        if (params.getCompanionDeviceId() != null) {
            companionDeviceId = new CompanionDeviceId(params.getCompanionDeviceId());
        }

        ComputerControlSession.NotificationParams notificationParams =
                params.getNotificationParams();
        ComputerControlSessionParams.NotificationParams systemNotificationParams =
                notificationParams == null ? null
                        : new ComputerControlSessionParams.NotificationParams.Builder(
                                notificationParams.getNotification(),
                                notificationParams.getNotificationId())
                                .setNotificationTag(notificationParams.getNotificationTag())
                                .build();
        ComputerControlSessionParams sessionParams =
                new ComputerControlSessionParams.Builder()
                        .setName(params.getName())
                        .setTargetComputerControlVersion(mTargetComputerControlVersion)
                        .setTargetPackageNames(params.getTargetPackageNames())
                        .setPreviewIntent(params.getPreviewIntent())
                        .setAppInteractionAttribution(params.getAppInteractionAttribution())
                        .setCompanionDeviceId(companionDeviceId)
                        .setNotificationParams(systemNotificationParams)
                        .build();

        var sessionCallback =
                new android.companion.virtual.computercontrol.ComputerControlSession.Callback() {

                    @Override
                    public void onSessionPending(@NonNull IntentSender intentSender) {
                        callback.onSessionPending(intentSender);
                    }
                    @Override
                    public void onSessionCreated(
                            @NonNull android.companion.virtual.computercontrol
                                    .ComputerControlSession session) {
                        callback.onSessionCreated(new ComputerControlSession(session));
                    }

                    @Override
                    public void onSessionCreationFailed(
                            @android.companion.virtual.computercontrol.ComputerControlSession
                                    .SessionCreationError int errorCode) {
                        callback.onSessionCreationFailed(errorCode);
                    }

                    @Override
                    public void onSessionClosed() {
                        callback.onSessionClosed();
                    }
                };

        VirtualDeviceManager vdm = params.getContext().getSystemService(VirtualDeviceManager.class);
        vdm.requestComputerControlSession(sessionParams, executor, sessionCallback);
    }

    /**
     * Registers a listener to receive notifications when the set of automated apps changes.
     *
     * @param context Context to fetch system features.
     * @param executor The executor where the listener is executed on.
     * @param listener The listener to add.
     * @throws SecurityException if the caller does not hold the {@link RoleManager#ROLE_HOME} role.
     * @see #unregisterAutomatedPackageListener
     * @hide
     */
    public void registerAutomatedPackageListener(
            @NonNull Context context,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull AutomatedPackageListener listener) {
        VirtualDeviceManager vdm = context.getSystemService(VirtualDeviceManager.class);

        var platformListener =
                new android.companion.virtual.computercontrol.AutomatedPackageListener() {
                    @Override
                    public void onAutomatedPackagesChanged(@NonNull String automatingPackage,
                            @NonNull List<String> automatedPackages, @NonNull UserHandle user) {
                        listener.onAutomatedPackagesChanged(
                                automatingPackage, automatedPackages, user);
                    }
                };
        vdm.registerAutomatedPackageListener(executor, platformListener);
        mListeners.put(listener, platformListener);
    }

    /**
     * Unregisters a listener previously registered with {@link #registerAutomatedPackageListener}.
     *
     * @param context Context to fetch system features.
     * @param listener The listener to unregister.
     * @throws SecurityException if the caller does not hold the {@link RoleManager#ROLE_HOME} role.
     * @see #registerAutomatedPackageListener
     * @hide
     */
    public void unregisterAutomatedPackageListener(
            @NonNull Context context, @NonNull AutomatedPackageListener listener) {
        var platformListener = mListeners.remove(listener);
        if (platformListener != null) {
            VirtualDeviceManager vdm = context.getSystemService(VirtualDeviceManager.class);
            vdm.unregisterAutomatedPackageListener(platformListener);
        }
    }

    /**
     * @return {@code true} if computer control is available and can be used. When the computer
     * control extensions are not available the current Android device is missing some configuration
     * that makes them unsupported. Computer control cannot be used on such devices.
     */
    private static boolean isAvailable(Context context) {
        if (!context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            return false;
        }

        return context.getSystemService(VirtualDeviceManager.class) != null;
    }

    /**
     * @return {@code true} if computer control functionality is available on the device and the
     * caller is allowed to access session creation functionality with the given {@link Context}.
     *
     * @deprecated with ComputerControl v5. Use {@link #isSessionCreationAvailable(Context, int)}
     * instead.
     */
    // TODO(b/489808412) remove this method to getVersion() when finalizing v5
    // This method was only added in v5 technically, but used in Trunkfood hence keeping it
    // to allow for migration.
    @Deprecated
    public static boolean isSessionCreationAvailable(@NonNull Context context) {
        // Hardcoded to 4 to ensure existing callers remain compatible
        return isSessionCreationAvailable(context, 4);
    }

    /**
     * @return {@code true} if computer control functionality is available on the device and the
     * caller is allowed to access session creation functionality with the given {@link Context}
     * and the given {@code targetComputerControlVersion}.
     */
    public static boolean isSessionCreationAvailable(@NonNull Context context,
            int targetComputerControlVersion) {
        if (!isAvailable(context)) {
            return false;
        }
        return context.getSystemService(VirtualDeviceManager.class).isComputerControlAvailable(
                targetComputerControlVersion);
    }
}
