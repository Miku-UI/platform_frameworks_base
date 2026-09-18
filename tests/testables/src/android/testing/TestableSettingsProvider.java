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

import static org.junit.Assert.assertEquals;

import android.content.ContentProviderClient;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

/**
 * Allows calls to android.provider.Settings to be tested easier.
 *
 * This provides a simple copy-on-write implementation of settings that gets cleared
 * at the end of each test.
 */
public class TestableSettingsProvider extends TestableSettingsProviderBase {

    private static final String TAG = "TestableSettingsProvider";
    private static final boolean DEBUG = false;
    private static final String MY_UNIQUE_KEY = "Key_" + TestableSettingsProvider.class.getName();
    private static TestableSettingsProvider sInstance;

    private final ContentProviderClient mSettings;

    private TestableSettingsProvider(ContentProviderClient settings) {
        mSettings = settings;
    }

    void clearValuesAndCheck(Context context) {
        // Ensure we swapped over to use TestableSettingsProvider
        clearSettingsProvider();

        // putString will eventually invoking the mocked call() method and update mValues
        Settings.Global.putString(context.getContentResolver(), MY_UNIQUE_KEY, MY_UNIQUE_KEY);
        Settings.Secure.putString(context.getContentResolver(), MY_UNIQUE_KEY, MY_UNIQUE_KEY);
        Settings.System.putString(context.getContentResolver(), MY_UNIQUE_KEY, MY_UNIQUE_KEY);
        // Verify that if any test is using TestableContext, they all have the correct settings
        // provider.
        assertEquals("Incorrect settings provider, test using incorrect Context?", MY_UNIQUE_KEY,
                Settings.Global.getString(context.getContentResolver(), MY_UNIQUE_KEY));
        assertEquals("Incorrect settings provider, test using incorrect Context?", MY_UNIQUE_KEY,
                Settings.Secure.getString(context.getContentResolver(), MY_UNIQUE_KEY));
        assertEquals("Incorrect settings provider, test using incorrect Context?", MY_UNIQUE_KEY,
                Settings.System.getString(context.getContentResolver(), MY_UNIQUE_KEY));

        mValues.clear();
    }

    void unregister() {
        clearSettingsProvider();
    }

    @Override
    protected Bundle onMissingValue(String method, String arg, Bundle extras) {
        // Fall through to real settings.
        try {
            if (DEBUG) Log.d(TAG, "Falling through to real settings " + method);
            // TODO: Add our own version of caching to handle this.
            Bundle call = mSettings.call(method, arg, extras);
            call.remove(Settings.CALL_METHOD_TRACK_GENERATION_KEY);
            return call;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Since the settings provider is cached inside android.provider.Settings, this must
     * be gotten statically to ensure there is only one instance referenced.
     */
    static TestableSettingsProvider getFakeSettingsProvider(ContentProviderClient settings) {
        if (sInstance == null) {
            sInstance = new TestableSettingsProvider(settings);
        }
        return sInstance;
    }
}
