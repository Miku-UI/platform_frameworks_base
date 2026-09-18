/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.testing;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.mock.MockContentProvider;

import java.util.HashMap;

/**
 * Allows calls to android.provider.Settings to be tested easier.
 *
 * This provides a simple copy-on-write implementation of settings that gets cleared
 * at the end of each test.
 */
public abstract class TestableSettingsProviderBase extends MockContentProvider {

    protected final HashMap<String, String> mValues = new HashMap<>();

    public static void clearSettingsProvider() {
        Settings.Global.clearProviderForTest();
        Settings.Secure.clearProviderForTest();
        Settings.System.clearProviderForTest();
    }

    protected abstract Bundle onMissingValue(String method, String arg, Bundle extras);

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // Methods are "GET_system", "GET_global", "PUT_secure", etc.
        int userId = extras.getInt(Settings.CALL_METHOD_USER_KEY, UserHandle.USER_CURRENT);
        if (userId == UserHandle.USER_CURRENT || userId == UserHandle.USER_CURRENT_OR_SELF) {
            userId = UserHandle.myUserId();
        }

        final String[] commands = method.split("_", 2);
        final String op = commands[0];
        final String table = commands[1];

        String k = key(table, arg, userId);
        String value;
        Bundle out = new Bundle();
        switch (op) {
            case "GET":
                if (mValues.containsKey(k)) {
                    value = mValues.get(k);
                    if (value != null) {
                        out.putString(Settings.NameValueTable.VALUE, value);
                    }
                } else {
                    return onMissingValue(method, arg, extras);
                }
                break;
            case "PUT":
                value = extras.getString(Settings.NameValueTable.VALUE, null);
                mValues.put(k, value);
                break;
            case "RESET":
                switch (table) {
                    case "global" -> Settings.Global.clearProviderForTest();
                    case "secure" -> Settings.Secure.clearProviderForTest();
                    case "system" -> Settings.System.clearProviderForTest();
                }
                break;
            default:
                throw new UnsupportedOperationException("Unknown command " + method);
        }
        return out;
    }

    private static String key(String table, String key, int userId) {
        if ("global".equals(table)) {
            return table + "_" + key;
        } else {
            return table + "_" + userId + "_" + key;
        }
    }
}
