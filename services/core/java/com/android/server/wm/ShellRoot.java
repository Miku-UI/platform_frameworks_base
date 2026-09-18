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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.SHELL_ROOT_LAYER_DIVIDER;
import static android.view.WindowManager.SHELL_ROOT_LAYER_PIP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;
import android.view.IWindow;
import android.view.SurfaceControl;
import android.view.WindowManager;

/**
 * Represents a piece of the hierarchy under which a client Shell can manage sub-windows.
 */
public class ShellRoot {
    private static final String TAG = "ShellRoot";
    private final DisplayContent mDisplayContent;
    private IWindow mClient;
    private WindowToken mToken;
    private final IBinder.DeathRecipient mDeathRecipient;
    private SurfaceControl mSurfaceControl = null;
    private IWindow mAccessibilityWindow;
    private IBinder.DeathRecipient mAccessibilityWindowDeath;
    private int mWindowType;

    ShellRoot(@NonNull IWindow client, @NonNull DisplayContent dc,
            @WindowManager.ShellRootLayer final int shellRootLayer) {
        mDisplayContent = dc;
        mDeathRecipient = () -> mDisplayContent.removeShellRoot(shellRootLayer);
        try {
            client.asBinder().linkToDeath(mDeathRecipient, 0);
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to add shell root layer " + shellRootLayer + " on display "
                    + dc.getDisplayId(), e);
            return;
        }
        mClient = client;
        switch (shellRootLayer) {
            case SHELL_ROOT_LAYER_DIVIDER:
                mWindowType = TYPE_DOCK_DIVIDER;
                break;
            case SHELL_ROOT_LAYER_PIP:
                mWindowType = TYPE_APPLICATION_OVERLAY;
                break;
            default:
                throw new IllegalArgumentException(shellRootLayer
                        + " is not an acceptable shell root layer.");
        }
        mToken = new WindowToken.Builder(dc.mWmService, client.asBinder(), mWindowType)
                .setPersistOnEmpty(true)
                .setOwnerCanManageAppTokens(true)
                .build();
        dc.addWindowToken(mToken);
        mSurfaceControl = mToken.makeChildSurface(null)
                .setContainerLayer()
                .setName("Shell Root Leash " + dc.getDisplayId())
                .setCallsite("ShellRoot")
                .build();
        mToken.getPendingTransaction().show(mSurfaceControl);
    }

    int getWindowType() {
        return mWindowType;
    }

    void clear() {
        if (mClient != null) {
            mClient.asBinder().unlinkToDeath(mDeathRecipient, 0);
            mClient = null;
        }
        if (mToken != null) {
            mToken.removeImmediately();
            mToken = null;
        }
    }

    SurfaceControl getSurfaceControl() {
        return mSurfaceControl;
    }

    IWindow getClient() {
        return mClient;
    }

    @Nullable
    IBinder getAccessibilityWindowToken() {
        if (mAccessibilityWindow != null) {
            return mAccessibilityWindow.asBinder();
        }
        return null;
    }

    void setAccessibilityWindow(IWindow window) {
        if (mAccessibilityWindow != null) {
            mAccessibilityWindow.asBinder().unlinkToDeath(mAccessibilityWindowDeath, 0);
        }
        mAccessibilityWindow = window;
        if (mAccessibilityWindow != null) {
            try {
                mAccessibilityWindowDeath = () -> {
                    synchronized (mDisplayContent.mWmService.mGlobalLock) {
                        mAccessibilityWindow = null;
                        setAccessibilityWindow(null);
                    }
                };
                mAccessibilityWindow.asBinder().linkToDeath(mAccessibilityWindowDeath, 0);
            } catch (RemoteException e) {
                mAccessibilityWindow = null;
            }
        }
    }
}
