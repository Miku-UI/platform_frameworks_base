/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.util.test;

import android.annotation.NonNull;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.mock.MockContentProvider;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import java.util.Map;
import java.util.Objects;

/**
 * Fake for system settings.
 *
 * To use, ensure that the Context used by the test code returns a ContentResolver that uses this
 * provider for the Settings authority:
 *
 *   class MyTestContext extends MockContext {
 *       ...
 *       private final MockContentResolver mContentResolver;
 *       public MyTestContext(...) {
 *           ...
 *           mContentResolver = new MockContentResolver();
 *           mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
 *       }
 *       ...
 *       @Override
 *       public ContentResolver getContentResolver() {
 *           return mContentResolver;
 *       }
 *
 * As long as the code under test is using the test Context, the actual code under test does not
 * need to be modified, and can access Settings using the normal static methods:
 *
 *   Settings.Global.getInt(cr, "my_setting", 0);  // Returns 0.
 *   Settings.Global.putInt(cr, "my_setting", 5);
 *   Settings.Global.getInt(cr, "my_setting", 0);  // Returns 5.
 *
 * Note that this class cannot be used in the same process as real settings. This is because it
 * works by passing an alternate ContentResolver to Settings operations. Unfortunately, the Settings
 * class only fetches the content provider from the passed-in ContentResolver the first time it's
 * used, and after that stores it in a per-process static. If this needs to be used in this case,
 * then call {@link #clearSettingsProvider()} before and after using this.
 *
 * This class is thread-safe.
 */
public class FakeSettingsProvider extends MockContentProvider {

    private static final String TAG = FakeSettingsProvider.class.getSimpleName();
    private static final boolean DBG = false;
    private static final String[] TABLES = { "system", "secure", "global" };
    private static final Map<String, String> EMPTY_MAP = Map.of();

    /**
     * Stores settings values. Keyed by:
     * - Table name (e.g., "system")
     * - User ID (e.g., USER_SYSTEM)
     * - Setting name (e.g., "screen_brightness")
     */
    @GuardedBy("mDb")
    private final Map<String, Map<Integer, Map<String, String>>> mDb = new ArrayMap<>();

    public interface Callback {
        /** Called whenever a setting's value changes. */
        void onUriChanged(int userId, Uri uri);
    }

    private final Callback mCallback;

    public FakeSettingsProvider(@NonNull Callback callback) {
        Objects.requireNonNull(callback);
        synchronized (mDb) {
            for (int i = 0; i < TABLES.length; i++) {
                mDb.put(TABLES[i], new ArrayMap<>());
            }
        }
        mCallback = callback;
    }

    public FakeSettingsProvider() {
        this((user, uri) -> {});
    }

    private static Uri getUriFor(String table, String key) {
        switch (table) {
            case "system":
                return Settings.System.getUriFor(key);
            case "secure":
                return Settings.Secure.getUriFor(key);
            case "global":
                return Settings.Global.getUriFor(key);
            default:
                throw new UnsupportedOperationException("Unknown settings table " + table);
        }
    }

    /**
     * Creates a {@link org.junit.rules.TestRule} that makes sure {@link #clearSettingsProvider()}
     * is triggered before and after each test.
     */
    public static FakeSettingsProviderRule rule() {
        return new FakeSettingsProviderRule();
    }

    /**
     * This needs to be called before and after using the FakeSettingsProvider class.
     */
    public static void clearSettingsProvider() {
        Settings.Secure.clearProviderForTest();
        Settings.Global.clearProviderForTest();
        Settings.System.clearProviderForTest();
    }

    private static int getUserId(String table, Bundle extras) {
        // Global settings always use USER_SYSTEM, see SettingsProvider#getGlobalSetting.
        if ("global".equals(table)) {
            return UserHandle.USER_SYSTEM;
        }
        // When running in process, Settings does not set the userId. Default to current userId.
        final int myUserId = UserHandle.getCallingUserId();
        int userId = extras.getInt(Settings.CALL_METHOD_USER_KEY, myUserId);
        // Many tests write settings before initializing users, so treat USER_NULL as current user.
        if (userId == UserHandle.USER_CURRENT || userId == UserHandle.USER_NULL) {
            return myUserId;
        }
        if (userId < 0) {
            throw new UnsupportedOperationException("userId " + userId + " is not a real user");
        }
        return userId;
    }

    public Bundle call(String method, String arg, Bundle extras) {
        // Methods are "GET_system", "GET_global", "PUT_secure", etc.
        String[] commands = method.split("_", 2);
        String op = commands[0];
        String table = commands[1];

        Bundle out = new Bundle();
        String value;
        final int userId = getUserId(table, extras);

        switch (op) {
            case "GET":
                synchronized (mDb) {
                    final Map<String, String> perUserTable = mDb.get(table)
                            .getOrDefault(userId, EMPTY_MAP);
                    value = perUserTable.get(arg);
                }
                if (value != null) {
                    out.putString(Settings.NameValueTable.VALUE, value);
                }
                if (DBG) {
                    Log.d(TAG, String.format("Returning fake setting %d:%s.%s = %s",
                            userId, table, arg, (value == null) ? "null" : "\"" + value + "\""));
                }
                break;
            case "PUT":
                value = extras.getString(Settings.NameValueTable.VALUE, null);
                final boolean changed;
                synchronized (mDb) {
                    if (value != null) {
                        changed = !value.equals(
                                mDb.get(table)
                                .computeIfAbsent(userId, (u) -> new ArrayMap<>())
                                .put(arg, value));
                        if (DBG) {
                            Log.d(TAG, String.format("Inserting fake setting %d:%s.%s = \"%s\"",
                                    userId, table, arg, value));
                        }
                    } else {
                        final Map<String, String> perUserTable = mDb.get(table).get(userId);
                        changed = perUserTable != null && perUserTable.remove(arg) != null;
                        if (DBG) {
                            Log.d(TAG, String.format("Removing fake setting %d:%s.%s", userId,
                                table, arg));
                        }
                    }
                }
                if (changed) mCallback.onUriChanged(userId, getUriFor(table, arg));
                break;
            default:
                throw new UnsupportedOperationException("Unknown command " + method);
        }

        return out;
    }
}
