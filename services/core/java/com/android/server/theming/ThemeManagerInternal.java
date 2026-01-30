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

package com.android.server.theming;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeSettings;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

/**
 * Internal API implementation for {@link ThemeManagerService}.
 *
 * <p>Provides methods for other system services to interact with the theming
 * functionality.
 */
public class ThemeManagerInternal {
    private final Context mContext;
    private final ThemeSettingsManager mThemeSettingsManager;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeSettingsCallback>> mSettingsListeners =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final SparseArray<ThemeSettings> mCurrentSettings = new SparseArray<>();

    @NonNull
    private final ThemeSettings mDefaultSettings;

    ThemeManagerInternal(Context context, ThemeSettingsManager themeSettingsManager,
            @NonNull ThemeSettings defaultSettings) {
        mContext = context;
        mThemeSettingsManager = themeSettingsManager;
        mDefaultSettings = defaultSettings;
    }

    void notifySettingsChange(@UserIdInt int userId, ThemeSettings newSettings) {
        final RemoteCallbackList<IThemeSettingsCallback> userListeners;
        final ThemeSettings oldSettings;
        synchronized (mLock) {
            userListeners = mSettingsListeners.get(userId);
            oldSettings = mCurrentSettings.get(userId);
        }

        if (userListeners != null) {
            try {
                final int count = userListeners.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    userListeners.getBroadcastItem(i).onSettingsChanged(oldSettings, newSettings);
                }
            } catch (RemoteException e) {
                // This is not expected to happen for local services.
                throw new RuntimeException(e);
            } finally {
                userListeners.finishBroadcast();
            }
        }
    }


    /**
     * Registers a callback to receive notifications of theme settings changes.
     *
     * <p>This method allows clients to register an {@link IThemeSettingsCallback}
     * to be notified whenever the theme settings for the specified user are changed.
     *
     * @param userId The user ID to register the callback for.
     * @param cb     The {@link IThemeSettingsCallback} to register.
     * @return {@code true} if the callback was successfully registered, {@code false} otherwise.
     */
    public boolean registerThemeSettingsCallback(@UserIdInt int userId, IThemeSettingsCallback cb) {
        synchronized (mLock) {
            if (cb == null) return false;

            RemoteCallbackList<IThemeSettingsCallback> userListeners = mSettingsListeners.get(
                    userId);
            if (userListeners == null) {
                userListeners = new RemoteCallbackList<>();
                mSettingsListeners.put(userId, userListeners);
            }

            if (mCurrentSettings.get(userId) == null) {
                mCurrentSettings.put(userId, getThemeSettings(userId));
            }

            return userListeners.register(cb);
        }
    }

    /**
     * Unregisters a previously registered theme settings change callback.
     *
     * <p>This method allows clients to unregister an {@link IThemeSettingsCallback}
     * that was previously registered using
     * {@link #registerThemeSettingsCallback(int, IThemeSettingsCallback)}}.
     *
     * @param userId The user ID to unregister the callback from.
     * @param cb     The {@link IThemeSettingsCallback} to unregister.
     * @return {@code true} if the callback was successfully unregistered, {@code false} otherwise.
     */
    public boolean unregisterThemeSettingsCallback(@UserIdInt int userId,
            IThemeSettingsCallback cb) {
        synchronized (mLock) {
            RemoteCallbackList<IThemeSettingsCallback> userListeners = mSettingsListeners.get(
                    userId);

            if (cb == null || userListeners == null) return false;

            boolean didRemove = userListeners.unregister(cb);

            if (userListeners.getRegisteredCallbackCount() == 0) {
                mSettingsListeners.remove(userId);
                mCurrentSettings.remove(userId);
            }

            return didRemove;
        }
    }

    /**
     * Updates the theme settings for the current user.
     *
     * <p>This method allows clients to update the theme settings for the current user. The
     * provided {@link ThemeSettings} object should contain the new theme settings. Any
     * settings not explicitly set in the {@link ThemeSettings} object will remain unchanged.
     *
     * <p>Use the {@link ThemeSettings}'s builder() to construct {@link ThemeSettings} objects.
     *
     * @param userId      The user ID to update theme settings for.
     * @param newSettings The {@link ThemeSettings} object containing the new theme settings.
     */
    public boolean updateThemeSettings(@UserIdInt int userId, ThemeSettings newSettings) {
        try {
            Context userContext = mContext.createContextAsUser(UserHandle.of(userId),
                    Context.CONTEXT_IGNORE_SECURITY);
            mThemeSettingsManager.writeSettings(userId, userContext.getContentResolver(),
                    newSettings);
            synchronized (mLock) {
                mCurrentSettings.put(userId, newSettings);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieves the theme settings for the specified user.
     *
     * <p>This method allows clients to retrieve the current theme settings for a given user.
     *
     * @param userId The user ID to retrieve theme settings for.
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or {@code null} if an error occurs or no settings are found.
     */
    public ThemeSettings getThemeSettings(@UserIdInt int userId) {
        try {
            Context userContext = mContext.createContextAsUser(UserHandle.of(userId),
                    Context.CONTEXT_IGNORE_SECURITY);
            return mThemeSettingsManager.readSettings(userId, userContext.getContentResolver());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Retrieves the theme settings for the specified user, or the default settings if no
     * custom settings are found.
     *
     * @param userId The user ID to retrieve theme settings for.
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or the default settings if no custom settings are found.
     */
    public ThemeSettings getThemeSettingsOrDefault(int userId) {
        ThemeSettings storedSettings = getThemeSettings(userId);
        if (storedSettings != null) {
            return storedSettings;
        }
        return mDefaultSettings;
    }
}
