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

package com.android.protolog.tool;

import com.android.internal.protolog.common.IProtoLogGroup;

import java.util.UUID;

public enum TestProtoLogGroups implements IProtoLogGroup {

    TEST_GROUP_1(true, true, false, "TEST_TAG_1"),
    TEST_GROUP_2(true, false, true, "TEST_TAG_2");

    private final boolean mEnabled;
    private volatile boolean mLogToProto;
    private volatile boolean mLogToLogcat;
    private final String mTag;

    TestProtoLogGroups(boolean enabled, boolean logToProto, boolean logToLogcat, String tag) {
        this.mEnabled = enabled;
        this.mLogToProto = logToProto;
        this.mLogToLogcat = logToLogcat;
        this.mTag = tag;
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
        return Consts.START_ID + this.ordinal();
    }

    private static class Consts {
        private static final int START_ID = (int) (
                UUID.nameUUIDFromBytes(TestProtoLogGroups.class.getName().getBytes())
                        .getMostSignificantBits() % Integer.MAX_VALUE);
    }
}
