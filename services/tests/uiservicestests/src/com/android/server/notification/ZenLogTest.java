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

package com.android.server.notification;

import static android.provider.Settings.Global.ZEN_MODE_ALARMS;
import static android.service.notification.NotificationListenerService.HINT_HOST_DISABLE_EFFECTS;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.UiServiceTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ZenLogTest extends UiServiceTestCase {

    @Before
    public void setUp() {
        ZenLog.clear();
    }

    @Test
    public void trace_splitsLogs() {
        ZenLog.traceMatchesCallFilter(true, "is starred", /* callingUid= */ 123);
        ZenLog.traceSetZenMode(ZEN_MODE_ALARMS, "only alarms plz");
        ZenLog.traceListenerHintsChanged(/* oldHints= */ 0,
                /* newHints= */ HINT_HOST_DISABLE_EFFECTS, /* listenerCount= */ 1);

        String log = getLog();
        assertThat(log).matches(Pattern.compile(
                "(.*)" // whitespace
                        + "Interception Events:"
                        + "(.*)" // whitespace + timestamp
                        + "matches_call_filter: result=true, reason=is starred, calling uid=123"
                        + "(.*)"
                        + "State Changes:"
                        + "(.*)"
                        + "set_zen_mode: alarms,only alarms plz"
                        + "(.*)"
                        + "Other Events:"
                        + "(.*)"
                        + "listener_hints_changed: none->disable_effects,listeners=1"
                        + "(.*)",
                Pattern.DOTALL));
    }

    private static String getLog() {
        StringWriter zenLogWriter = new StringWriter();
        ZenLog.dump(new PrintWriter(zenLogWriter), "");
        return zenLogWriter.toString();
    }
}
