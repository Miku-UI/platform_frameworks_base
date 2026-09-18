/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.adb;

import android.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Optional;

class AdbdMessage {
    static final String REGISTER_SERVICE = "RS";
    static final String UNREGISTER_SERVICE = "US";

    private final byte[] mBuffer;
    private int mCursor;

    AdbdMessage(@NonNull byte[] buffer) {
        mBuffer = buffer;
        mCursor = 0;
    }

    Optional<String> readType() {
        final int typeSize = 2;
        if (mCursor + typeSize > mBuffer.length) {
            return Optional.empty();
        }
        String type = new String(Arrays.copyOfRange(mBuffer, mCursor, typeSize));
        mCursor += typeSize;
        return Optional.of(type);
    }

    Optional<String> readU8String() {
        if (mCursor >= mBuffer.length) {
            return Optional.empty();
        }
        int size = mBuffer[mCursor++] & 0xFF;

        if (mCursor + size > mBuffer.length) {
            return Optional.empty();
        }
        String str = new String(Arrays.copyOfRange(mBuffer, mCursor, mCursor + size));

        mCursor += size;
        return Optional.of(str);
    }

    Optional<Integer> readU16() {
        final int uint16Size = 2;
        if (mCursor + uint16Size > mBuffer.length) {
            return Optional.empty();
        }

        ByteBuffer bytes = ByteBuffer.wrap(mBuffer, mCursor, uint16Size);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        int u16 = bytes.getShort() & 0xFFFF;
        mCursor += uint16Size;
        return Optional.of(u16);
    }
}
