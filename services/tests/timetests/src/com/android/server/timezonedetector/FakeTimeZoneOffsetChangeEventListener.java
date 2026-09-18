/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.server.timezonedetector;

import android.util.IndentingPrintWriter;

import java.util.ArrayList;
import java.util.List;

/** A fake implementation of TimeZoneOffsetChangeListener for tests. */
public class FakeTimeZoneOffsetChangeEventListener implements TimeZoneOffsetChangeListener {
    private final List<TimeZoneOffsetChangeEvent> mEvents = new ArrayList<>();

    public void process(TimeZoneOffsetChangeEvent event) {
        mEvents.add(event);
    }

    public List<TimeZoneOffsetChangeEvent> getTimeZoneOffsetChangeEvents() {
        return mEvents;
    }

    @Override
    public void dump(IndentingPrintWriter ipw) {
        // No-op for tests
    }
}
