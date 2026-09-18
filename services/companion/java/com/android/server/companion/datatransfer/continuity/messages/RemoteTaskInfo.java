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

package com.android.server.companion.datatransfer.continuity.messages;

import android.annotation.NonNull;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.Objects;

public record RemoteTaskInfo(
        int id,
        @NonNull String packageName,
        boolean isInForeground,
        long lastUsedTimeMillis,
        @NonNull HandoffOptions handoffOptions)
        implements Proto {

    public RemoteTaskInfo {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(handoffOptions);
    }

    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        Objects.requireNonNull(pos).writeInt32(android.companion.RemoteTaskInfo.ID, id());
        pos.writeString(android.companion.RemoteTaskInfo.PACKAGE_NAME, packageName());
        pos.writeBool(android.companion.RemoteTaskInfo.IS_IN_FOREGROUND, isInForeground());
        pos.writeInt64(
                android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS, lastUsedTimeMillis());
        Proto.writeField(pos, android.companion.RemoteTaskInfo.HANDOFF_OPTIONS, handoffOptions());
    }

    public static final class Builder extends Proto.Builder<RemoteTaskInfo> {
        private int mId = 0;
        private String mPackageName = "";
        private boolean mIsInForeground = false;
        private long mLastUsedTimeMillis = 0;
        private HandoffOptions mHandoffOptions = new HandoffOptions(false, false);

        public Builder setId(int id) {
            mId = id;
            return this;
        }

        public Builder setPackageName(@NonNull String packageName) {
            mPackageName = Objects.requireNonNull(packageName);
            return this;
        }

        public Builder setIsInForeground(boolean isInForeground) {
            mIsInForeground = isInForeground;
            return this;
        }

        public Builder setLastUsedTimeMillis(long lastUsedTimeMillis) {
            mLastUsedTimeMillis = lastUsedTimeMillis;
            return this;
        }

        public Builder setHandoffOptions(@NonNull HandoffOptions handoffOptions) {
            mHandoffOptions = Objects.requireNonNull(handoffOptions);
            return this;
        }

        @Override
        protected void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException {
            switch (fieldNumber) {
                case (int) android.companion.RemoteTaskInfo.ID ->
                        setId(pis.readInt(android.companion.RemoteTaskInfo.ID));
                case (int) android.companion.RemoteTaskInfo.PACKAGE_NAME ->
                        setPackageName(
                                pis.readString(android.companion.RemoteTaskInfo.PACKAGE_NAME));
                case (int) android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS ->
                        setLastUsedTimeMillis(
                                pis.readLong(
                                        android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS));
                case (int) android.companion.RemoteTaskInfo.HANDOFF_OPTIONS ->
                        setHandoffOptions(
                                new HandoffOptions.Builder()
                                        .readFromField(
                                                pis,
                                                android.companion.RemoteTaskInfo.HANDOFF_OPTIONS)
                                        .build());
                case (int) android.companion.RemoteTaskInfo.IS_IN_FOREGROUND ->
                        setIsInForeground(
                                pis.readBoolean(android.companion.RemoteTaskInfo.IS_IN_FOREGROUND));
            }
        }

        @Override
        public RemoteTaskInfo build() {
            return new RemoteTaskInfo(
                    mId, mPackageName, mIsInForeground, mLastUsedTimeMillis, mHandoffOptions);
        }
    }
}
