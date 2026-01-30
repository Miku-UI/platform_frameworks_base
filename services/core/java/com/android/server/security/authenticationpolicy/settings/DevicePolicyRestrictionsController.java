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

package com.android.server.security.authenticationpolicy.settings;

import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;
import static android.os.UserManager.DISALLOW_USB_FILE_TRANSFER;

import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Setting controller for applying and clearing device policy restrictions. */
public class DevicePolicyRestrictionsController implements SettingController<Set<String>> {
    public static final String TAG = "DevicePolicyController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    static final String DEVICE_POLICY_RESTRICTIONS_KEY = "device_policy_restrictions";
    static final String DEVICE_POLICY_ELEMENT_KEY = "restriction";
    private final Set<String> mSkipDuringTest = Set.of(DISALLOW_USB_FILE_TRANSFER,
            DISALLOW_DEBUGGING_FEATURES);
    private DevicePolicyManager mDevicePolicyManager;
    private boolean mSkipSecurityFeaturesForTest = false;

    @Override
    public void storeOriginalValue(@NonNull SettingState<Set<String>> state, int userId)
            throws Exception {
        if (mDevicePolicyManager == null) {
            Slog.w(TAG, "DevicePolicyManager is null, cannot retrieve original value for "
                    + "device policy restrictions.");
            return;
        }
        state.setOriginalValue(bundleToSet(mDevicePolicyManager.getUserRestrictionsGlobally()));
    }

    @Override
    public void applySecureLockDeviceValue(@NonNull SettingState<Set<String>> state, int userId)
            throws Exception {
        if (mDevicePolicyManager == null) {
            Slog.w(TAG, "DevicePolicyManager is null, cannot apply device policy restrictions.");
            return;
        }

        for (String restriction : state.getSecureLockDeviceValue()) {
            if (mSkipSecurityFeaturesForTest && mSkipDuringTest.contains(restriction)) {
                Slog.d(TAG, "Skipping applying restriction " + restriction + " for test.");
                continue;
            }

            mDevicePolicyManager.addUserRestrictionGlobally(TAG, restriction);
        }
    }

    @Override
    public void restoreFromOriginalValue(@NonNull SettingState<Set<String>> state, int userId)
            throws Exception {
        if (mDevicePolicyManager == null) {
            Slog.w(TAG, "DevicePolicyManager is null, cannot restore device policy "
                    + "restrictions.");
            return;
        } else if (state.getOriginalValue() == null) {
            Slog.w(TAG, "Original value for device policy restrictions is null, cannot restore "
                    + "device policy restrictions.");
            return;
        }

        Set<String> originalValue = state.getOriginalValue();
        for (String restriction : state.getSecureLockDeviceValue()) {
            if (mSkipSecurityFeaturesForTest && mSkipDuringTest.contains(restriction)) {
                Slog.d(TAG, "Skipping restoring restriction " + restriction + " for test.");
                continue;
            }

            if (originalValue.contains(restriction)) {
                Slog.i(TAG, "Restriction " + restriction + " was already set prior to "
                        + "secure lock device, leave unchanged.");
            } else {
                if (DEBUG) {
                    Slog.i(TAG, "Restriction " + restriction + " was not set prior to "
                            + "secure lock device, clear restriction.");
                }
                mDevicePolicyManager.clearUserRestrictionGlobally(TAG, restriction);
            }
        }
    }

    @Override
    public void serializeOriginalValue(@NonNull String settingKey,
            @NonNull Set<String> originalValue, @NonNull TypedXmlSerializer serializer)
            throws XmlPullParserException, IOException {
        XmlUtils.writeSetXml(originalValue, DEVICE_POLICY_ELEMENT_KEY, serializer);
    }

    @Override
    public Set<String> deserializeOriginalValue(@NonNull TypedXmlPullParser parser,
            @NonNull String settingKey) throws IOException, XmlPullParserException {
        HashSet<String> hashSet = XmlUtils.readThisSetXml(parser, "set", null);
        return hashSet;
    }

    /**
     * Sets whether to skip security features for test.
     *
     * @param skipSecurityFeaturesForTest Whether to skip security features for test.
     */
    void setSkipSecurityFeaturesForTest(boolean skipSecurityFeaturesForTest) {
        mSkipSecurityFeaturesForTest = skipSecurityFeaturesForTest;
    }

    /**
     * Sets the DevicePolicyManager.
     *
     * @param devicePolicyManager The DevicePolicyManager.
     */
    void setDevicePolicyManager(DevicePolicyManager devicePolicyManager) {
        mDevicePolicyManager = devicePolicyManager;
    }

    private Set<String> bundleToSet(Bundle restrictions) {
        Set<String> set = new HashSet<>();
        for (String key : restrictions.keySet()) {
            if (restrictions.getBoolean(key)) {
                set.add(key);
            }
        }
        return set;
    }
}
