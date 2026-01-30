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

package com.android.server.power.stats;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import android.os.BatteryStats.HistoryItem;
import android.os.ConditionVariable;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.os.BackgroundThread;
import com.android.internal.os.BatteryStatsHistory;
import com.android.internal.os.BatteryStatsHistoryIterator;
import com.android.internal.os.MonotonicClock;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Test case for BatteryStatsHistory that reads from existing history files and writes to new
 * history files to compute the tagpool size.
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(reason = "BatteryStatsHistoryTests not supported under Ravenwood")
@Ignore("Skipping in presubmit")
public class BatteryStatsHistoryReadWriteHistoryTest {

    private static final String TAG = "BatteryStatsHistoryReadWriteHistoryTest";
    private static final int MAX_HISTORY_BUFFER_SIZE = 128 * 1024;
    // The INPUT_MONOTONIC_CLOCK_BASE and OUTPUT_MONOTONIC_CLOCK_BASE are arbitrary values
    // that are used to initialize the MonotonicClock objects. These values do not have any
    // specific relationship with the BH_Files.
    private static final long INPUT_MONOTONIC_CLOCK_BASE = 9852452119L;
    private static final int MAX_FILE_SIZE = 6194304;
    private static final long OUTPUT_MONOTONIC_CLOCK_BASE = 4852452119L;
    private static final long START_NEXT_FRAGMENT_TIME = 4940304651L;
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    private final MockClock mClock = new MockClock();
    private final MonotonicClock mInputMonotonicClock =
            new MonotonicClock(INPUT_MONOTONIC_CLOCK_BASE, mClock);
    private final MonotonicClock mOutputMonotonicClock =
            new MonotonicClock(OUTPUT_MONOTONIC_CLOCK_BASE, mClock);
    @Mock private BatteryStatsHistory.EventLogger mInputEventLogger;
    @Mock private BatteryStatsHistory.EventLogger mOutputEventLogger;
    private BatteryHistoryDirectory mInputDirectory;
    private BatteryHistoryDirectory mOutputDirectory;
    private File mTestInputHistoryDir;
    private File mTestOutputHistoryDir;
    private File mSystemDir;

    private static void awaitCompletion() {
        ConditionVariable done = new ConditionVariable();
        BackgroundThread.getHandler().post(done::open);
        done.block();
    }

    @Before
    public void setUp() throws Exception {
        mSystemDir = Files.createTempDirectory("BatteryStatsHistoryReadWriteHistoryTest").toFile();
        mTestInputHistoryDir = new File(mSystemDir, "battery-history-input");
        // Copy the actual existing history files from the external path to our writable test input
        // directory.
        copyExistingFilesToTestInput();
        mTestOutputHistoryDir = new File(mSystemDir, "battery-history-output");
    }

    private void copyExistingFilesToTestInput() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Resources resources = context.getResources();
        final String resourceName = "BH_Files";

        int zipResourceId = resources.getIdentifier(resourceName, "raw", context.getPackageName());

        if (context == null) {
            Log.e(TAG, "Invalid arguments: context is null ");
            return;
        }

        InputStream zipInputStreamRaw = null;
        ZipInputStream zipInputStream = null;

        try {
            zipInputStreamRaw = resources.openRawResource(zipResourceId);
            zipInputStream = new ZipInputStream(zipInputStreamRaw);
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory()) {
                    Log.e(TAG, "Skip directory entry: " + entryName);
                    continue;
                }
                File outputFile = new File(mTestInputHistoryDir, entryName);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                Files.copy(
                        zipInputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                zipInputStream.closeEntry();
            }
            return;
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "ZIP resource not found: " + zipResourceId, e);
            return;
        } catch (IOException e) {
            Log.e(TAG, "Error processing ZIP resource: " + e.getMessage(), e);
            return;
        } finally {
            try {
                if (zipInputStream != null) {
                    zipInputStream.close();
                } else if (zipInputStreamRaw != null) {
                    zipInputStreamRaw.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing ZIP streams: " + e.getMessage(), e);
            }
        }
    }

    @Test
    public void testReadExistingHistoryAndWriteToNewFiles() throws Exception {
        String[] initialFiles = mTestInputHistoryDir.list();
        assertNotNull("Test input history directory should not be null", initialFiles);
        assertTrue(
                "Test input history directory should contain copied battery history files",
                initialFiles.length > 0);

        BatteryHistoryDirectory.Compressor compressor = BatteryHistoryDirectory.DEFAULT_COMPRESSOR;

        mInputDirectory =
                new BatteryHistoryDirectory(mTestInputHistoryDir, MAX_FILE_SIZE, compressor);
        mOutputDirectory =
                new BatteryHistoryDirectory(mTestOutputHistoryDir, MAX_FILE_SIZE, compressor);

        mOutputDirectory.makeDirectoryLockUnconditional();

        BatteryStatsHistory readerBsh =
                new BatteryStatsHistory(
                        null,
                        MAX_HISTORY_BUFFER_SIZE,
                        mInputDirectory,
                        mClock,
                        mInputMonotonicClock,
                        null,
                        mInputEventLogger);
        awaitCompletion();

        BatteryStatsHistory writerBsh =
                new BatteryStatsHistory(
                        null,
                        MAX_HISTORY_BUFFER_SIZE,
                        mOutputDirectory,
                        mClock,
                        mOutputMonotonicClock,
                        null,
                        mOutputEventLogger);
        awaitCompletion();
        writerBsh.forceRecordAllHistory();

        try (BatteryStatsHistoryIterator iterate = readerBsh.iterate(0, -1)) {
            while (iterate.hasNext()) {
                HistoryItem next = iterate.next();
                writerBsh.writeAllHistoryItem(next.time, 0, next);
            }
        }
        writerBsh.startNextFragment(START_NEXT_FRAGMENT_TIME);

        File[] outputFiles = mTestOutputHistoryDir.listFiles();
        long inputDirSize = mInputDirectory.getSize();
        long outputDirSize = mOutputDirectory.getSize();
        assertNotNull("Output directory should not be null", outputFiles);
        assertTrue("Output directory should contain at least one file", outputFiles.length > 0);
        assertTrue("Input History size should be positive integer", inputDirSize > 0);
        assertTrue("Output History size should be positive integer", outputDirSize > 0);
    }
}
