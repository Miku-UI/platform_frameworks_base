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

package com.android.server.aiseal;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.text.format.DateUtils;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.aiseal.IAiSealInternalService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;
import com.android.server.SystemService.TargetUser;

import java.io.File;
import java.util.Objects;
import java.util.Set;

/** AiSeal system service. */
public class AiSealSystemService extends SystemService {

    private static final String TAG = "AiSealSystemService";
    private static final String AISEAL_PRIVATE_FOLDER = "AiSeal";
    private static final String KEK_FILENAME = "kek";

    private final Context mContext;

    // Synchronizes user state changes with the AiSeal internal service connection.
    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private IAiSealInternalService mAiSealInternalService;

    @GuardedBy("sLock")
    private final Set<Integer> mUnlockedUsers = new ArraySet<>();

    public AiSealSystemService(Context context) {
        super(context);
        mContext = Objects.requireNonNull(context);
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "AiSealSystemService has started, connecting to AiSeal internal service");
        connectAiSealInternalService();
        mContext.registerReceiverForAllUsers(
            new UserActionReceiver(),
            new IntentFilter(Intent.ACTION_USER_REMOVED),
            /* broadcastPermission= */ null,
            /* scheduler= */ null);
    }

    @Override
    public void onUserUnlocking(TargetUser user) {
        synchronized (sLock) {
            onUserUnlockingLocked(user.getUserIdentifier());
        }
    }

    @Override
    public void onUserStopped(TargetUser user) {
        synchronized (sLock) {
            onUserStoppedLocked(user.getUserIdentifier());
        }
    }

    private void connectAiSealInternalService() {
        synchronized (sLock) {
            connectAiSealInternalServiceLocked();
        }
    }

    @GuardedBy("sLock")
    private void connectAiSealInternalServiceLocked() {
        // Reset the service to null if we are currently connected to it.
        if (mAiSealInternalService != null) {
            Slog.w(TAG, "Connecting to AiSealInternalService twice");
            mAiSealInternalService = null;
        }

        Slog.d(TAG, "Connecting to AiSeal internal service");
        IBinder binder = ServiceManager.getService("aiseal_internal");
        if (binder != null) {
            try {
                binder.linkToDeath(
                        new DeathRecipient() {
                            @Override
                            public void binderDied() {
                                Slog.wtf(TAG, "AiSeal died; reconnecting");
                                connectAiSealInternalService();
                            }
                        },
                        0);
            } catch (RemoteException e) {
                binder = null;
            }
        }

        if (binder != null) {
            mAiSealInternalService = IAiSealInternalService.Stub.asInterface(binder);
        } else {
            Slog.d(TAG, "AiSeal internal service not yet available; trying again");
        }

        if (mAiSealInternalService == null) {
            BackgroundThread.getHandler()
                    .postDelayed(
                            () -> {
                                connectAiSealInternalService();
                            },
                            DateUtils.SECOND_IN_MILLIS);
        } else {
            onAiSealInternalServiceConnectedLocked();
        }
    }

    @GuardedBy("sLock")
    private void onAiSealInternalServiceConnectedLocked() {
        Slog.i(TAG, "Connected to AiSeal internal service");
        for (int userId : mUnlockedUsers) {
            notifyUserUnlockingLocked(userId);
        }
    }

    @GuardedBy("sLock")
    private void onUserUnlockingLocked(int userId) {
        mUnlockedUsers.add(userId);
        if (mAiSealInternalService != null) {
            notifyUserUnlockingLocked(userId);
        } else {
            // The user will be unlocked in onAiSealInternalServiceConnected().
            Slog.w(TAG, "Not yet connected to AiSeal to unlock user " + userId);
        }
    }

    @GuardedBy("sLock")
    private void onUserStoppedLocked(int userId) {
        mUnlockedUsers.remove(userId);
        if (mAiSealInternalService != null) {
            notifyUserStoppedLocked(userId);
        } else {
            Slog.w(TAG, "Not yet connected to AiSeal to stop user " + userId);
        }
    }

    /** AiSeal notifyUserUnlockingLocked */
    @GuardedBy("sLock")
    public void notifyUserUnlockingLocked(int userId) {
        Slog.i(TAG, "Unlocking user " + userId);
        try {
            File kekFile = getKekFile(userId);
            kekFile.getParentFile().mkdirs();
            SELinux.restorecon(kekFile.getParentFile());
            mAiSealInternalService.onUserUnlocking(userId, kekFile.getAbsolutePath());
        } catch (Exception e) {
            Slog.wtf(TAG, "Unable to unlock user " + userId, e);
        }
    }

    /** AiSeal notifyUserStoppedLocked */
    @GuardedBy("sLock")
    public void notifyUserStoppedLocked(int userId) {
        Slog.i(TAG, "Stopping user " + userId);
        try {
            mAiSealInternalService.onUserStopped(userId);
        } catch (Exception e) {
            Slog.wtf(TAG, "Unable to stop user " + userId, e);
        }
    }

    private class UserActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            Objects.requireNonNull(context);
            Objects.requireNonNull(intent);
            if (Intent.ACTION_USER_REMOVED.equals(intent.getAction())) {
                UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
                if (userHandle == null) {
                    Slog.e(TAG,
                            "Extra " + Intent.EXTRA_USER + " is missing in the intent: " + intent);
                    return;
                }
                onUserRemoved(userHandle);
            } else {
                Slog.e(TAG, "Received unknown intent: " + intent);
            }
        }
    }

    // Delete all AiSeal data to ensure the user data is properly destroyed when a user profile is
    // removed.
    private void onUserRemoved(@NonNull UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        try {
            synchronized (sLock) {
                mAiSealInternalService.onUserRemoved(userHandle.getIdentifier());
            }
        } catch (Exception e) {
            Slog.wtf(TAG, "Failed to remove AiSeal data for user " + userHandle);
        }
    }

    private File getKekFile(int userId) {
        return Environment.buildPath(Environment.getDataSystemCeDirectory(userId),
                AISEAL_PRIVATE_FOLDER, KEK_FILENAME);
    }
}
