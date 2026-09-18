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

package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;
import android.provider.DocumentsContract;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ExternalStorageProviderPerfTest {
    private static final String AUTHORITY = DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY;
    private static final String ROOT_ID =
            DocumentsContract.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID;

    @Rule public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    private ContentResolver mResolver;
    private Uri mTestDirUri;

    private void setupDirectory(int fileCount) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mResolver = context.getContentResolver();
        Uri rootUri = DocumentsContract.buildDocumentUri(AUTHORITY, ROOT_ID + ":Download");
        mTestDirUri =
                DocumentsContract.createDocument(
                        mResolver,
                        rootUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        "perf_test_dir");

        for (int i = 0; i < fileCount; i++) {
            DocumentsContract.createDocument(
                    mResolver, mTestDirUri, "text/plain", "file_" + i + ".txt");
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mTestDirUri != null) {
            DocumentsContract.deleteDocument(mResolver, mTestDirUri);
        }
    }

    @Test
    public void testQueryChildDocuments_1k() throws Exception {
        runQueryBenchmark(1000);
    }

    @Test
    public void testQueryChildDocuments_5k() throws Exception {
        runQueryBenchmark(5000);
    }

    @Test
    public void testQueryChildDocuments_10k() throws Exception {
        runQueryBenchmark(10000);
    }

    private void runQueryBenchmark(int count) throws Exception {
        setupDirectory(count);
        String docId = DocumentsContract.getDocumentId(mTestDirUri);
        Uri uri = DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId);

        try (ContentProviderClient client = mResolver.acquireContentProviderClient(AUTHORITY)) {
            BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
            while (state.keepRunning()) {
                try (Cursor c = client.query(uri, null, null, null, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            // This block is empty as the enumeration is enough to test the
                            // performance without including unnecessary noise that is outside the
                            // scope of the ExternalStorageProvider.
                        }
                    }
                }
            }
        }
    }
}
