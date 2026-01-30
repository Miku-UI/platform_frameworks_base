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
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Extensions for Computer Control features.
 *
 * Internally relies on multiple system features that may be unavailable. Getting an instance via
 * {@link #getInstance(Context)} will enable the creation of new {@link ComputerControlSession}s
 * that enable inputs and outputs for computer control features.
 */
public class ComputerControlExtensions {
    @VisibleForTesting static final int EXTENSIONS_VERSION = 1;

    private final ArrayMap<AutomatedPackageListener,
            android.companion.virtual.computercontrol.AutomatedPackageListener> mListeners =
            new ArrayMap<>();

    private ComputerControlExtensions() {}

    /**
     * Retrieve the current version of the extensions.
     */
    public static int getVersion() {
        return EXTENSIONS_VERSION;
    }

    /**
     * Gets an instance of the ComputerControlExtensions. These extensions can be unavailable on
     * devices. In such cases {@code null} is returned and the extensions won't be available on this
     * device.
     *
     * @param context Context to fetch system features
     * @return An instance of ComputerControlExtensions, or {@code null} if the extensions are
     * unavailable.
     */
    @Nullable
    public static ComputerControlExtensions getInstance(Context context) {
        if (!isAvailable(context)) {
            return null;
        }
        return new ComputerControlExtensions();
    }

    /**
     * Requests a new {@link ComputerControlSession} for the given parameters. When the session is
     * no longer used it should be closed by calling {@link ComputerControlSession#close()}.
     *
     * @param params parameters to use for this ComputerControlSession.
     * @param executor An executor to run the callback on.
     * @param callback A callback to get notified about the result of this operation.
     */
    @RequiresPermission(Manifest.permission.ACCESS_COMPUTER_CONTROL)
    public void requestSession(@NonNull ComputerControlSession.Params params,
            @NonNull Executor executor, @NonNull ComputerControlSession.Callback callback) {
        Objects.requireNonNull(params, "Missing ComputerControlSession.Params");
        Objects.requireNonNull(executor, "Missing Executor");
        Objects.requireNonNull(callback, "Missing ComputerControlSession.Callback");

        ComputerControlSessionParams sessionParams =
                new ComputerControlSessionParams.Builder()
                        .setName(params.getName())
                        .setTargetPackageNames(params.getTargetPackageNames())
                        .setDisplayWidthPx(params.getDisplayWidthPx())
                        .setDisplayHeightPx(params.getDisplayHeightPx())
                        .setDisplayDpi(params.getDisplayDpi())
                        .setDisplaySurface(params.getDisplaySurface())
                        .setDisplayAlwaysUnlocked(params.isDisplayAlwaysUnlocked())
                        .build();

        var sessionCallback =
                new android.companion.virtual.computercontrol.ComputerControlSession.Callback() {

                    @Override
                    public void onSessionPending(@NonNull IntentSender intentSender) {
                        // TODO(b/437901655): Pass this to the caller.
                        try {
                            params.getContext().startIntentSender(intentSender, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            callback.onSessionCreationFailed(
                                    android.companion.virtual.computercontrol
                                            .ComputerControlSession.ERROR_PERMISSION_DENIED);
                        }
                    }

                    @Override
                    public void onSessionCreated(
                            @NonNull android.companion.virtual.computercontrol
                                    .ComputerControlSession session) {
                        AccessibilityManager accessibilityManager =
                                params.getContext().getSystemService(AccessibilityManager.class);
                        callback.onSessionCreated(
                                new ComputerControlSession(session, params, accessibilityManager));
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
}
