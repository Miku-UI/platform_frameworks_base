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

import android.os.Binder;
import android.os.IBinder;
import android.util.Pair;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Setting controller for disabling status bar features. When secure lock device is enabled,
 * disables status bar expansion, notifications, home button, back gestures, search gestures, and
 * call chips.
 */
class DisableFlagsController implements SettingController<Pair<Integer, Integer>> {
    private static final String TAG = "DisableFlagsController";
    private final IBinder mToken = new LockTaskToken();
    private final String mPackageName;
    private final IStatusBarService mStatusBarService;

    DisableFlagsController(@NonNull String packageName,
            @Nullable IStatusBarService statusBarService) {
        mStatusBarService = statusBarService;
        mPackageName = packageName;
    }

    @Override
    public void storeOriginalValue(@NonNull SettingState<Pair<Integer, Integer>> state, int userId)
            throws Exception {
        if (mStatusBarService == null) {
            throw new Exception("IStatusBarService is null, cannot retrieve status bar state.");
        }

        int[] disableInfo = mStatusBarService.getDisableFlags(mToken, userId);
        state.setOriginalValue(new Pair<>(disableInfo[0], disableInfo[1]));
    }

    @Override
    public void applySecureLockDeviceValue(@NonNull SettingState<Pair<Integer, Integer>> state,
            int userId) throws Exception {
        if (mStatusBarService == null) {
            Slog.w(TAG, "IStatusBarService is null, cannot apply secure lock device value for "
                    + "status bar state.");
            return;
        }
        Pair<Integer, Integer> secureLockDeviceValue = state.getSecureLockDeviceValue();
        mStatusBarService.disable(secureLockDeviceValue.first, mToken, mPackageName);
        mStatusBarService.disable2(secureLockDeviceValue.second, mToken, mPackageName);
    }

    @Override
    public void restoreFromOriginalValue(@NonNull SettingState<Pair<Integer, Integer>> state,
            int userId) throws Exception {
        if (mStatusBarService == null) {
            Slog.w(TAG, "IStatusBarService is null, cannot restore original status bar state.");
            return;
        }

        Pair<Integer, Integer> originalValue = state.getOriginalValue();
        if (originalValue != null) {
            mStatusBarService.disable(originalValue.first, mToken, mPackageName);
            mStatusBarService.disable2(originalValue.second, mToken, mPackageName);
        }
    }

    @Override
    public void serializeOriginalValue(@NonNull String settingKey,
            @NonNull Pair<Integer, Integer> originalValue, @NonNull TypedXmlSerializer serializer)
            throws IOException {
        serializer.startTag(null, "disable1")
                .text(Integer.toString(originalValue.first))
                .endTag(null, "disable1");
        serializer.startTag(null, "disable2")
                .text(Integer.toString(originalValue.second))
                .endTag(null, "disable2");
    }

    @Override
    public Pair<Integer, Integer> deserializeOriginalValue(@NonNull TypedXmlPullParser parser,
            @NonNull String settingKey) throws IOException, XmlPullParserException {
        int disable1Val = 0;
        int disable2Val = 0;
        int disableFlagsDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, disableFlagsDepth)) {
            if ("disable1".equals(parser.getName())) {
                disable1Val = Integer.parseInt(parser.nextText());
            } else if ("disable2".equals(parser.getName())) {
                disable2Val = Integer.parseInt(parser.nextText());
            } else {
                XmlUtils.skipCurrentTag(parser);
            }
        }
        return new Pair<>(disable1Val, disable2Val);
    }

    /** Marker class for the token used to disable keyguard. */
    private static class LockTaskToken extends Binder {
    }
}
