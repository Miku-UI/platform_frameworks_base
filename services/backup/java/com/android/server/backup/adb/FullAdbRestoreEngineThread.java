/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.backup.adb;

import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;

import libcore.io.IoUtils;

import java.io.InputStream;

class FullAdbRestoreEngineThread implements Runnable {

    FullAdbRestoreEngine mEngine;
    InputStream mEngineStream;

    FullAdbRestoreEngineThread(FullAdbRestoreEngine engine, InputStream inputStream) {
        mEngine = engine;
        engine.setRunning(true);
        mEngineStream = inputStream;
    }

    public boolean isRunning() {
        return mEngine.isRunning();
    }

    public int waitForResult() {
        return mEngine.waitForResult();
    }

    @Override
    public void run() {
        try {
            while (mEngine.isRunning()) {
                mEngine.restoreOneFile(mEngineStream);
            }
        } finally {
            // Because mEngineStream adopted its underlying FD, this also
            // closes this end of the pipe.
            IoUtils.closeQuietly(mEngineStream);
        }
    }

    public void handleTimeout() {
        IoUtils.closeQuietly(mEngineStream);
        mEngine.handleTimeout();
    }
}
