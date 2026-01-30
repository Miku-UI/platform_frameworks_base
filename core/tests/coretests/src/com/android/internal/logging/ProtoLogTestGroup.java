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

package com.android.internal.logging;

import com.android.internal.protolog.common.IProtoLogGroup;

class ProtoLogTestGroup implements IProtoLogGroup {
    private final boolean mEnabled;
    private volatile boolean mLogToLogcat;
    private final String mTag;
    private final int mId;

    ProtoLogTestGroup(String tag, int id) {
        this(true, false, tag, id);
    }

    ProtoLogTestGroup(boolean enabled, boolean logToLogcat, String tag, int id) {
        this.mEnabled = enabled;
        this.mLogToLogcat = logToLogcat;
        this.mTag = tag;
        this.mId = id;
    }

    @Override
    public String name() {
        return mTag;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public boolean isLogToLogcat() {
        return mLogToLogcat;
    }

    @Override
    public String getTag() {
        return mTag;
    }

    @Override
    public void setLogToLogcat(boolean logToLogcat) {
        this.mLogToLogcat = logToLogcat;
    }

    @Override
    public int getId() {
        return mId;
    }
}
