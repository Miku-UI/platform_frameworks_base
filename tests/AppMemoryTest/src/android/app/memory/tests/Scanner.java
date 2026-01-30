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

package android.app.memory.tests;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class scans a byte stream, picking off values in network byte order.  Read methods
 * throw IndexOutOfBoundsException if the stream comes up short.
 */
class Scanner {

    private final InputStream mStream;

    // If non-zero, IDs have this size.
    private final int mIdSize;

    // As a special case, if the scanner reads a type specifier, it remembers the
    // width needed by the type; that width is used in the next call to typedValue().
    private Type mLastType;

    // Create a scanner with a stream and a non-zero ID size.
    Scanner(InputStream stream, int idSize) {
        if (!(idSize == 1 || idSize == 2 || idSize == 4 || idSize == 8)) {
            throw new IllegalArgumentException("invalid idsize: " + idSize);
        }
        mStream = stream;
        mIdSize = idSize;
    }

    // Create a scanner with a zero ID size.
    Scanner(InputStream stream) {
        mStream = stream;
        mIdSize = 0;
    }

    boolean empty() {
        try {
            return mStream.available() == 0;
        } catch (IOException e) {
            throw new IndexOutOfBoundsException(e.toString());
        }
    }

    // Functions that read from the stream are given very short names that correspond to the
    // primitive type codes from the JNI specification.

    byte jB() {
        try {
            return (byte) mStream.read();
        } catch (IOException e) {
            throw new IndexOutOfBoundsException(e.toString());
        }
    }

    // Read bytes into the array and return a wrapped ByteBuffer.
    private ByteBuffer readPrimitive(byte[] a) {
        try {
            if (mStream.read(a, 0, a.length) != a.length) {
                throw new IndexOutOfBoundsException("short read");
            }
            return ByteBuffer.wrap(a).order(ByteOrder.BIG_ENDIAN);
        } catch (IOException e) {
            throw new IndexOutOfBoundsException(e.toString());
        }
    }

    private final byte[] mShort = new byte[2];
    short jS() {
        return readPrimitive(mShort).getShort();
    }

    private final byte[] mInt = new byte[4];
    int jI() {
        return readPrimitive(mInt).getInt();
    }

    private final byte[] mLong = new byte[8];
    long jJ() {
        return readPrimitive(mLong).getLong();
    }

    // Read an ID.  The length of the ID is based on sIdSize.  The return type is long, to
    // hold an id of any size.
    long id() {
        return switch (mIdSize) {
            case 2 -> jS();
            case 4 -> jI();
            case 8 -> jJ();
            default -> throw new RuntimeException("unknown ID size: " + mIdSize);
        };
    }

    // Read an object of the specified size. This always hold a long.
    long value(int width) {
        return switch (width) {
            case 0 -> id();
            case 1 -> jB();
            case 2 -> jS();
            case 4 -> jI();
            case 8 -> jJ();
            default -> throw new RuntimeException("unsupported object size: " + width);
        };
    }

    // Read a type.  This just reads a byte but it also saves the byte in mByte, for
    // use by the next typedValue() call.
    Type type() {
        byte b = jB();
        mLastType = switch (b) {
            case 2 -> Type.TypeObject;
            case 4 -> Type.TypeBoolean;
            case 5 -> Type.TypeChar;
            case 6 -> Type.TypeFloat;
            case 7 -> Type.TypeDouble;
            case 8 -> Type.TypeByte;
            case 9 -> Type.TypeShort;
            case 10 -> Type.TypeInt;
            case 11 -> Type.TypeLong;
            default -> null;
        };
        if (mLastType == null) throw new RuntimeException("unknown type " + b);
        return mLastType;
    }

    // Read a field whose width is determined by the last type that was read.
    long typedValue() {
        return value(mLastType.size());
    }

    // Read <n> bytes.
    byte[] jL(int len) {
        try {
            byte[] v = new byte[len];
            mStream.read(v, 0, v.length);
            return v;
        } catch (IOException e) {
            throw new IndexOutOfBoundsException(e.toString());
        }
    }

    private int available() {
        try {
            return mStream.available();
        } catch (IOException e) {
            throw new IndexOutOfBoundsException(e.toString());
        }
    }

    // Read the remaining stuff into a byte array.
    byte[] jL() {
        return jL(available());
    }

    // Read <n> IDs
    long[] id(int n) {
        long[] v = new long[n];
        for (int i = 0; i < n; i++) {
            v[i] = id();
        }
        return v;
    }
}
