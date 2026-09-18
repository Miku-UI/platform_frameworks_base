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
 * limitations under the License
 */

package com.android.server.backup.fullbackup;

import static com.android.server.backup.BackupManagerService.DEBUG;

import android.util.Slog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads from an input stream and writes to an output stream.
 */
public class FullBackupDataCopier implements Runnable {
    private static final String TAG = "FullBackupDataCopier";

    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private final AtomicBoolean mStopRunning;
    private final AtomicBoolean mIsIoSuccessful;
    private final int mBufferSize;
    private final boolean mCloseOutput;
    private final String mTag;

    public FullBackupDataCopier(
            InputStream inputStream,
            OutputStream outputStream,
            AtomicBoolean stopRunning,
            AtomicBoolean isIoSuccessful,
            int bufferSize,
            boolean closeOutput,
            String tag) {
        mInputStream = inputStream;
        mOutputStream = outputStream;
        mStopRunning = stopRunning;
        mIsIoSuccessful = isIoSuccessful;
        mBufferSize = bufferSize;
        mCloseOutput = closeOutput;
        mTag = tag;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[mBufferSize];
        int bytesRead = 0;
        try {
            while (!mStopRunning.get()) {
                bytesRead = mInputStream.read(buffer);

                if (DEBUG) {
                    Slog.i(TAG, "Read " + bytesRead + " bytes for " + mTag);
                }

                if (bytesRead > 0) {
                    if (mStopRunning.get()) {
                        break;
                    }
                    mOutputStream.write(buffer, 0, bytesRead);
                    mOutputStream.flush();
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            mIsIoSuccessful.set(false);
            Slog.e(TAG, "IOException while reading/writing data for " + mTag, e);
        } finally {
            if (mCloseOutput) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    mIsIoSuccessful.set(false);
                }
            }
        }
    }
}
