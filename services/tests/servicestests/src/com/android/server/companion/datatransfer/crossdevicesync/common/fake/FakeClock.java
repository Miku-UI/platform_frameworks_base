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
package com.android.server.companion.datatransfer.crossdevicesync.common.fake;

import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;

import java.util.ArrayList;
import java.util.List;

public class FakeClock implements Clock {
    private static final List<Runnable> sListeners = new ArrayList<>();
    private static long sCurrentTime = 0;

    public FakeClock() {}

    @Override
    public long elapsedRealtime() {
        return sCurrentTime;
    }

    @Override
    public long currentTimeMillis() {
        return sCurrentTime;
    }

    public void advanceTime(long millis) {
        setCurrentTime(elapsedRealtime() + millis);
    }

    public void setCurrentTime(long millis) {
        sCurrentTime = millis;
        sListeners.forEach(Runnable::run);
    }

    public void addListener(Runnable runnable) {
        sListeners.add(runnable);
    }
}
