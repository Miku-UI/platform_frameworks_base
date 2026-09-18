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

package com.android.server.privatecompute;

import android.annotation.NonNull;
import android.os.PersistableBundle;
import com.android.internal.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Data class for an audit log entry. */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditLogEntry {
    final long mRealTimeNanos;
    final long mCurrentTimeMillis;
    final String mCallingPackage;
    final int mCallingUid;
    final PersistableBundle mData;

    AuditLogEntry(
            PersistableBundle data,
            long realTimeNanos,
            long currentTimeMillis,
            @NonNull String callingPackage,
            int callingUid) {
        mData = data;
        mRealTimeNanos = realTimeNanos;
        mCurrentTimeMillis = currentTimeMillis;
        mCallingPackage = callingPackage;
        mCallingUid = callingUid;
    }

    PersistableBundle getBundle() {
        return mData;
    }

    /**
     * Serializes this {@link AuditLogEntry} to a byte array.
     *
     * <p>Variable-length fields are written prepended by their size, as an int, to allow for
     * parsing back. The format is as follows:
     *
     * <ul>
     *   <li>The format version as an int. Only version 0 is supported at the moment.
     *   <li>For each AuditLogEntry:
     *       <ul>
     *         <li>The elapsed nanoseconds since boot when the audit log entry was submitted, as a
     *             long.
     *         <li>The current time in milliseconds when the audit log entry was submitted, as a
     *             long.
     *         <li>The calling UID as an int.
     *         <li>The calling package as a byte array, prepended by its size.
     *         <li>The PersistableBundle to a byte array, prepended by its size.
     *       </ul>
     * </ul>
     */
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeLong(mRealTimeNanos);
        stream.writeLong(mCurrentTimeMillis);
        stream.writeInt(mCallingUid);

        // Write the calling package to a byte array to know its length.
        byte[] callingPackageBytes = mCallingPackage.getBytes(StandardCharsets.UTF_8);
        stream.writeInt(callingPackageBytes.length);
        stream.write(callingPackageBytes);

        // Write the PersistableBundle to a byte array to know its length.
        ByteArrayOutputStream stream2 = new ByteArrayOutputStream();
        mData.writeToStream(stream2);
        byte[] bundleBytes = stream2.toByteArray();

        // Write the length of the bundle, then the bundle itself.
        stream.writeInt(bundleBytes.length);
        stream.write(bundleBytes);

        return output.toByteArray();
    }

    static AuditLogEntry readFromStream(DataInputStream input) throws IOException {
        long realTimeNanos = input.readLong();
        long currentTimeMillis = input.readLong();
        int uid = input.readInt();
        int pkgLen = input.readInt();
        byte[] pkgBytes = new byte[pkgLen];
        input.readFully(pkgBytes);
        String pkg = new String(pkgBytes, StandardCharsets.UTF_8);

        int bundleLen = input.readInt();
        byte[] bundleBytes = new byte[bundleLen];
        input.readFully(bundleBytes);
        PersistableBundle bundle =
                PersistableBundle.readFromStream(new ByteArrayInputStream(bundleBytes));

        return new AuditLogEntry(bundle, realTimeNanos, currentTimeMillis, pkg, uid);
    }
}
