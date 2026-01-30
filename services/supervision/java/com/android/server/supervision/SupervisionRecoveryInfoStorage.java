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

package com.android.server.supervision;

import android.app.supervision.SupervisionRecoveryInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.PersistableBundle;
import android.util.Log;

import java.io.File;

/**
 * Provides storage and retrieval of device supervision recovery information.
 *
 * <p>This class uses {@link SharedPreferences} as a temporary solution for persistent storage of
 * the recovery email and ID associated with device supervision.
 *
 * <p>The storage is managed as a singleton, ensuring a single point of access for recovery
 * information. Access to the shared preferences is synchronized to ensure thread safety.
 *
 * <p>TODO(b/406054267): need to figure out better solutions(binary xml) for persistent storage.
 */
public class SupervisionRecoveryInfoStorage {
    private static final String LOG_TAG = "RecoveryInfoStorage";
    private static final String PREF_NAME = "supervision_recovery_info";
    private static final String KEY_ACCOUNT_TYPE = "account_type";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_DATA = "account_data";
    private static final String KEY_STATE = "state";

    private final SharedPreferences mSharedPreferences;
    private static SupervisionRecoveryInfoStorage sInstance;
    private static final Object sLock = new Object();

    private SupervisionRecoveryInfoStorage(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        File sharedPrefs = new File(Environment.getDataSystemDirectory(), PREF_NAME);
        mSharedPreferences = deviceContext.getSharedPreferences(sharedPrefs, Context.MODE_PRIVATE);
    }

    /**
     * Gets the singleton instance of {@link SupervisionRecoveryInfoStorage}.
     *
     * @param context The application context.
     * @return The singleton instance.
     */
    public static SupervisionRecoveryInfoStorage getInstance(Context context) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new SupervisionRecoveryInfoStorage(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    /**
     * Loads the device supervision recovery information from persistent storage.
     *
     * @return The {@link SupervisionRecoveryInfo} if found, otherwise {@code null}.
     */
    public SupervisionRecoveryInfo loadRecoveryInfo() {
        synchronized (sLock) {
            String accountType = mSharedPreferences.getString(KEY_ACCOUNT_TYPE, null);
            String accountName = mSharedPreferences.getString(KEY_ACCOUNT_NAME, null);
            String accountDataString = mSharedPreferences.getString(KEY_ACCOUNT_DATA, null);
            int state = mSharedPreferences.getInt(KEY_STATE, SupervisionRecoveryInfo.STATE_PENDING);

            if (accountType != null && accountName != null) {
                PersistableBundle accountData = null;
                if (accountDataString != null) {
                    try {
                        accountData = PersistableBundleUtils.fromString(accountDataString);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to load account data from SharedPreferences", e);
                        // If failed to load accountData, just return other info.
                    }
                }
                return new SupervisionRecoveryInfo(accountName, accountType, state, accountData);
            }
        }
        return null;
    }

    /**
     * Saves the device supervision recovery information to persistent storage.
     *
     * @param recoveryInfo The {@link SupervisionRecoveryInfo} to save or {@code null} to clear the
     *     stored information.
     */
    public void saveRecoveryInfo(SupervisionRecoveryInfo recoveryInfo) {
        synchronized (sLock) {
            SharedPreferences.Editor editor = mSharedPreferences.edit();

            if (recoveryInfo == null) {
                editor.remove(KEY_ACCOUNT_TYPE);
                editor.remove(KEY_ACCOUNT_NAME);
                editor.remove(KEY_ACCOUNT_DATA);
                editor.remove(KEY_STATE);
            } else {
                editor.putString(KEY_ACCOUNT_TYPE, recoveryInfo.getAccountType());
                editor.putString(KEY_ACCOUNT_NAME, recoveryInfo.getAccountName());
                PersistableBundle accountData = recoveryInfo.getAccountData();
                String accountDataString =
                        accountData != null ? PersistableBundleUtils.toString(accountData) : null;
                editor.putString(KEY_ACCOUNT_DATA, accountDataString);
                editor.putInt(KEY_STATE, recoveryInfo.getState());
            }
            editor.apply();
            if (!editor.commit()) {
                Log.e(LOG_TAG, "Failed to save recovery info to SharedPreferences");
            }
        }
    }

    private static class PersistableBundleUtils {
        private static final String SEPARATOR = ";";
        private static final String KEY_VALUE_SEPARATOR = ":";

        public static String toString(PersistableBundle bundle) {
            if (bundle == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (String key : bundle.keySet()) {
                String value = String.valueOf(bundle.get(key));
                sb.append(key).append(KEY_VALUE_SEPARATOR).append(value).append(SEPARATOR);
            }
            return sb.toString();
        }

        public static PersistableBundle fromString(String str) {
            if (str == null || str.isEmpty()) {
                return null;
            }
            PersistableBundle bundle = new PersistableBundle();
            String[] pairs = str.split(SEPARATOR);
            for (String pair : pairs) {
                if (pair.isEmpty()) {
                    continue;
                }
                String[] keyValue = pair.split(KEY_VALUE_SEPARATOR);
                if (keyValue.length == 2) {
                    bundle.putString(keyValue[0], keyValue[1]);
                }
            }
            return bundle;
        }
    }
}
