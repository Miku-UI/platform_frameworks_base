/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.input.data;

import android.hardware.input.InputGestureData;
import android.os.Environment;
import android.util.AtomicFile;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages persistent state recorded by the input manager service as a set of XML files.
 * Caller must acquire lock on the data store before accessing it.
 */
public final class InputDataStore {
    private static final String TAG = "InputDataStore";

    private static final String INPUT_MANAGER_DIRECTORY = "input";

    private final Map<Class<?>, PersistedData<?>> mPersistedDataMap = new HashMap<>();
    private final FileInjector mFileInjector;

    public InputDataStore() {
        this(new FileInjector());
    }

    @VisibleForTesting
    InputDataStore(FileInjector fileInjector) {
        mFileInjector = fileInjector;
        mPersistedDataMap.put(InputGestureData.class, new InputGesturePersistedData());
    }

    /**
     * Reads from the local disk storage the list of {@link PersistedData}.
     *
     * @param userId The user id to fetch the data for.
     * @return List of {@link PersistedData}.
     */
    public <T> List<T> loadData(int userId, Class<T> dataType) {
        PersistedData<T> persistedData = getPersistedDataForType(dataType);
        if (persistedData == null) {
            return List.of();
        }
        final String fileName = persistedData.getFileName();
        List<T> dataList;
        try {
            final InputStream inputStream = mFileInjector.openRead(userId, fileName);
            dataList = persistedData.readData(inputStream, false);
            inputStream.close();
        } catch (FileNotFoundException exception) {
            // There are valid reasons for the file to be missing, such as shortcuts having not
            // been registered by the user.
            return List.of();
        } catch (IOException exception) {
            // In case we are unable to read from the file on disk or another IO operation error,
            // fail gracefully.
            Slog.e(TAG,
                    "Failed to read from " + mFileInjector.getAtomicFileForUserId(userId, fileName),
                    exception);
            return List.of();
        } catch (Exception exception) {
            // In the case of any other exception, we want it to bubble up as this would be due
            // to malformed trusted XML data.
            throw new RuntimeException(
                    "Failed to read from " + mFileInjector.getAtomicFileForUserId(userId, fileName),
                    exception);
        }
        return dataList;
    }

    /**
     * Writes to the local disk storage the list of {@link PersistedData} provided as a param.
     *
     * @param userId    The user id to store the data list under.
     * @param dataList  The list of {@link PersistedData} for the given {@code userId}.
     */
    public <T> void saveData(int userId, List<T> dataList, Class<T> dataType) {
        PersistedData<T> persistedData = getPersistedDataForType(dataType);
        if (persistedData == null) {
            return;
        }
        FileOutputStream outputStream = null;
        final String fileName = persistedData.getFileName();
        try {
            outputStream = mFileInjector.startWrite(userId, fileName);
            persistedData.writeData(outputStream, false, dataList);
            mFileInjector.finishWrite(userId, fileName, outputStream, true);
        } catch (IOException e) {
            Slog.e(TAG,
                    "Failed to write to file " + mFileInjector.getAtomicFileForUserId(
                            userId, persistedData.getFileName()), e);
            mFileInjector.finishWrite(userId, fileName, outputStream, false);
        }
    }

    /**
     * Reads from the provided input stream, the list of {@link PersistedData}.
     *
     * @param stream stream of the input payload of XML data
     * @param utf8Encoded whether or not the data is UTF-8 encoded
     * @return List of {@link PersistedData} pulled from the payload.
     * @throws XmlPullParserException If there is an issue parsing the XML.
     * @throws IOException            If there is an issue reading from the stream.
     */
    public <T> List<T> readData(InputStream stream, boolean utf8Encoded, Class<T> dataType)
            throws XmlPullParserException, IOException {
        PersistedData<T> persistedData = getPersistedDataForType(dataType);
        if (persistedData == null) {
            return List.of();
        }
        return persistedData.readData(stream, utf8Encoded);
    }

    /**
     * Serializes the given list of {@link PersistedData} objects to XML in the provided output
     * stream.
     *
     * @param stream        output stream to put serialized data.
     * @param utf8Encoded   whether or not to encode the serialized data in UTF-8 format.
     * @param dataList      the list of {@link PersistedData} objects to serialize.
     * @throws IOException  if there is an issue reading from the stream.
     */
    public <T> void writeData(OutputStream stream, boolean utf8Encoded, List<T> dataList,
            Class<T> dataType) throws IOException {
        PersistedData<T> persistedData = getPersistedDataForType(dataType);
        if (persistedData == null) {
            return;
        }
        persistedData.writeData(stream, utf8Encoded, dataList);
    }

    @SuppressWarnings("unchecked")
    private <T> PersistedData<T> getPersistedDataForType(Class<T> dataType) {
        return (PersistedData<T>) mPersistedDataMap.get(dataType);
    }

    @VisibleForTesting
    static class FileInjector {
        private final Map<String, AtomicFile> mAtomicFileMap = new HashMap<>();

        private InputStream openRead(int userId, String fileName) throws FileNotFoundException {
            return getAtomicFileForUserId(userId, fileName).openRead();
        }

        private FileOutputStream startWrite(int userId, String fileName) throws IOException {
            return getAtomicFileForUserId(userId, fileName).startWrite();
        }

        private void finishWrite(int userId, String fileName, FileOutputStream os,
                boolean success) {
            if (success) {
                getAtomicFileForUserId(userId, fileName).finishWrite(os);
            } else {
                getAtomicFileForUserId(userId, fileName).failWrite(os);
            }
        }

        AtomicFile getAtomicFileForUserId(int userId, String fileName) {
            String key = userId + "_" + fileName;
            if (!mAtomicFileMap.containsKey(key)) {
                mAtomicFileMap.put(key, new AtomicFile(new File(
                        Environment.buildPath(Environment.getDataSystemDeDirectory(userId),
                                INPUT_MANAGER_DIRECTORY), fileName + ".xml")));
            }
            return mAtomicFileMap.get(key);
        }
    }
}
