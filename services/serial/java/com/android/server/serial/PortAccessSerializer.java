/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.serial;

import android.os.Environment;
import android.util.ArrayMap;
import android.util.SparseBooleanArray;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.IntFunction;

class PortAccessSerializer implements PortAccessSerializerInterface {
    private static final String SERIAL_FOLDER_NAME = "serial";
    private static final String SERIAL_PORT_ACCESS_FILE_NAME = "serial_port_access.xml";

    private final Executor mIoExecutor;
    private final IntFunction<File> mSystemCeFolderSupplier;

    PortAccessSerializer(Executor ioExecutor) {
        this(ioExecutor, Environment::getDataSystemCeDirectory);
    }

    PortAccessSerializer(Executor ioExecutor, IntFunction<File> systemCeFolderSupplier) {
        mIoExecutor = ioExecutor;
        mSystemCeFolderSupplier = systemCeFolderSupplier;
    }

    private File getPortAccessFileForUser(int userId) {
        final File systemCeFolder = mSystemCeFolderSupplier.apply(userId);
        final File serialFolder = new File(systemCeFolder, SERIAL_FOLDER_NAME);
        return new File(serialFolder, SERIAL_PORT_ACCESS_FILE_NAME);
    }

    @Override
    public Future<ArrayMap<String, SparseBooleanArray>> loadPortAccessForUser(int userId) {
        final FutureTask<ArrayMap<String, SparseBooleanArray>> task =
                new FutureTask<>(new Reader(getPortAccessFileForUser(userId)));
        mIoExecutor.execute(task);
        return task;
    }

    @Override
    public Future<Void> savePortAccessForUser(
            int userId, ArrayMap<String, SparseBooleanArray> accessMap) {
        final FutureTask<Void> task =
                new FutureTask<>(new Writer(getPortAccessFileForUser(userId), accessMap));
        mIoExecutor.execute(task);
        return task;
    }

    private static class Reader implements Callable<ArrayMap<String, SparseBooleanArray>> {
        private final File mFile;

        private Reader(File file) {
            mFile = file;
        }

        @Override
        public ArrayMap<String, SparseBooleanArray> call() throws Exception {
            // TODO(b/474657010): Actually load the file content.
            return new ArrayMap<>();
        }
    }

    private static class Writer implements Callable<Void> {
        private final File mFile;
        private final ArrayMap<String, SparseBooleanArray> mMap;

        private Writer(File file, ArrayMap<String, SparseBooleanArray> map) {
            mFile = file;

            mMap = new ArrayMap<>();
            for (int i = 0; i < map.size(); ++i) {
                mMap.append(map.keyAt(i), map.valueAt(i).clone());
            }
        }

        @Override
        public Void call() throws Exception {
            // TODO(b/474657010): Actually write the file content.
            return null;
        }
    }
}
