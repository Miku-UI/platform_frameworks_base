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
package com.android.server.companion.datatransfer.crossdevicesync.data.storage;

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.submerge.StorageInterface.StorageException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class SqliteStorageTest {
    private SqliteStorage mSqliteStorage;

    @Before
    public void setUp() throws Exception {
        mSqliteStorage = new SqliteStorage(ApplicationProvider.getApplicationContext(), "test_db");
        mSqliteStorage.runInIoThread(mSqliteStorage::open).get();
    }

    @After
    public void tearDown() {
        try {
            mSqliteStorage.submitToIoThread(mSqliteStorage::deleteDatabase).get();
        } catch (Exception e) {
            // Ignore
        }
        mSqliteStorage.shutdownIoThread();
    }

    @Test
    public void persistDocument_docInserted() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] doc = new byte[] {1, 2, 3, 4};

                            mSqliteStorage.persistDocument(id, doc);
                            assertThat(Arrays.equals(doc, mSqliteStorage.getDocument(id))).isTrue();
                        })
                .get();
    }

    @Test
    public void persistDocument_docContentUpdated() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] doc = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistDocument(id, doc);

                            doc = new byte[] {5, 6, 7, 8};
                            mSqliteStorage.persistDocument(id, doc);

                            assertThat(Arrays.equals(doc, mSqliteStorage.getDocument(id))).isTrue();
                        })
                .get();
    }

    @Test
    public void persistDocument_dataPersistedAcrossClose() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] doc = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistDocument(id, doc);

                            mSqliteStorage.close();
                            mSqliteStorage.open();

                            assertThat(Arrays.equals(doc, mSqliteStorage.getDocument(id))).isTrue();
                        })
                .get();
    }

    @Test
    public void persistDocument_throwFromWrongThread() {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};

        assertThrows(
                IllegalThreadStateException.class, () -> mSqliteStorage.persistDocument(id, doc));
    }

    @Test
    public void persistMetadata_docInserted() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] metadata = new byte[] {1, 2, 3, 4};

                            mSqliteStorage.persistMetadata(id, metadata);

                            assertThat(Arrays.equals(metadata, mSqliteStorage.getMetadata(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistMetadata_docMetadataUpdated() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] metadata = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistMetadata(id, metadata);

                            metadata = new byte[] {5, 6, 7, 8};
                            mSqliteStorage.persistMetadata(id, metadata);

                            assertThat(Arrays.equals(metadata, mSqliteStorage.getMetadata(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistMetadata_dataPersistedAcrossClose() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] metadata = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistMetadata(id, metadata);

                            mSqliteStorage.close();
                            mSqliteStorage.open();

                            assertThat(Arrays.equals(metadata, mSqliteStorage.getMetadata(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistMetadata_throwFromWrongThread() {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};

        assertThrows(
                IllegalThreadStateException.class,
                () -> mSqliteStorage.persistMetadata(id, metadata));
    }

    @Test
    public void persistSchema_docInserted() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] schema = new byte[] {1, 2, 3, 4};

                            mSqliteStorage.persistDocumentSchema(id, schema);

                            assertThat(Arrays.equals(schema, mSqliteStorage.getDocumentSchema(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistSchema_schemaUpdated() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] schema = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistDocumentSchema(id, schema);

                            schema = new byte[] {5, 6, 7, 8};
                            mSqliteStorage.persistDocumentSchema(id, schema);

                            assertThat(Arrays.equals(schema, mSqliteStorage.getDocumentSchema(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistSchema_dataPersistedAcrossClose() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] schema = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistDocumentSchema(id, schema);

                            mSqliteStorage.close();
                            mSqliteStorage.open();

                            assertThat(Arrays.equals(schema, mSqliteStorage.getDocumentSchema(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistSchema_throwFromWrongThread() {
        String id = "id";
        byte[] schema = new byte[] {1, 2, 3, 4};

        assertThrows(
                IllegalThreadStateException.class,
                () -> mSqliteStorage.persistDocumentSchema(id, schema));
    }

    @Test
    public void persistDocument_metadataAndSchemaUnchanged() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] doc = new byte[] {1, 2, 3, 4};
                            byte[] metadata = new byte[] {5, 6, 7, 8};
                            byte[] schema = new byte[] {9, 10, 11, 12};
                            mSqliteStorage.persistMetadata(id, metadata);
                            mSqliteStorage.persistDocumentSchema(id, schema);

                            mSqliteStorage.persistDocument(id, doc);

                            assertThat(Arrays.equals(metadata, mSqliteStorage.getMetadata(id)))
                                    .isTrue();
                            assertThat(Arrays.equals(schema, mSqliteStorage.getDocumentSchema(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistMetadata_docContentAndSchemaUnchanged() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] doc = new byte[] {1, 2, 3, 4};
                            byte[] metadata = new byte[] {5, 6, 7, 8};
                            byte[] schema = new byte[] {9, 10, 11, 12};
                            mSqliteStorage.persistDocument(id, doc);
                            mSqliteStorage.persistDocumentSchema(id, schema);

                            mSqliteStorage.persistMetadata(id, metadata);

                            assertThat(Arrays.equals(doc, mSqliteStorage.getDocument(id))).isTrue();
                            assertThat(Arrays.equals(schema, mSqliteStorage.getDocumentSchema(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void persistSchema_docContentAndMetadataUnchanged() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] doc = new byte[] {1, 2, 3, 4};
                            byte[] metadata = new byte[] {5, 6, 7, 8};
                            byte[] schema = new byte[] {9, 10, 11, 12};
                            mSqliteStorage.persistDocument(id, doc);
                            mSqliteStorage.persistMetadata(id, metadata);

                            mSqliteStorage.persistDocumentSchema(id, schema);

                            assertThat(Arrays.equals(doc, mSqliteStorage.getDocument(id))).isTrue();
                            assertThat(Arrays.equals(metadata, mSqliteStorage.getMetadata(id)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void getDocument_multipleDocs_rightDocReturned() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id1 = "id1";
                            byte[] doc1 = new byte[] {1, 2, 3, 4};
                            String id2 = "id2";
                            byte[] doc2 = new byte[] {5, 6, 7, 8};
                            mSqliteStorage.persistDocument(id1, doc1);
                            mSqliteStorage.persistDocument(id2, doc2);

                            assertThat(Arrays.equals(doc1, mSqliteStorage.getDocument(id1)))
                                    .isTrue();
                            assertThat(Arrays.equals(doc2, mSqliteStorage.getDocument(id2)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void getDocument_throwFromWrongThread() {
        assertThrows(IllegalThreadStateException.class, () -> mSqliteStorage.getDocument("id"));
    }

    @Test
    public void getMetadata_multipleDocs_rightMetadataReturned() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id1 = "id1";
                            byte[] metadata1 = new byte[] {1, 2, 3, 4};
                            String id2 = "id2";
                            byte[] metadata2 = new byte[] {5, 6, 7, 8};
                            mSqliteStorage.persistMetadata(id1, metadata1);
                            mSqliteStorage.persistMetadata(id2, metadata2);

                            assertThat(Arrays.equals(metadata1, mSqliteStorage.getMetadata(id1)))
                                    .isTrue();
                            assertThat(Arrays.equals(metadata2, mSqliteStorage.getMetadata(id2)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void getMetadata_throwFromWrongThread() {
        assertThrows(IllegalThreadStateException.class, () -> mSqliteStorage.getMetadata("id"));
    }

    @Test
    public void getSchema_multipleDocs_rightMetadataReturned() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id1 = "id1";
                            byte[] schema1 = new byte[] {1, 2, 3, 4};
                            String id2 = "id2";
                            byte[] schema2 = new byte[] {5, 6, 7, 8};
                            mSqliteStorage.persistDocumentSchema(id1, schema1);
                            mSqliteStorage.persistDocumentSchema(id2, schema2);

                            assertThat(
                                            Arrays.equals(
                                                    schema1, mSqliteStorage.getDocumentSchema(id1)))
                                    .isTrue();
                            assertThat(
                                            Arrays.equals(
                                                    schema2, mSqliteStorage.getDocumentSchema(id2)))
                                    .isTrue();
                        })
                .get();
    }

    @Test
    public void getSchema_throwFromWrongThread() {
        assertThrows(
                IllegalThreadStateException.class, () -> mSqliteStorage.getDocumentSchema("id"));
    }

    @Test
    public void deleteDatabase_persistedDataDeleted() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] metadata = new byte[] {1, 2, 3, 4};
                            byte[] doc = new byte[] {1, 2, 3, 4};
                            byte[] schema = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistMetadata(id, metadata);
                            mSqliteStorage.persistDocument(id, doc);
                            mSqliteStorage.persistDocumentSchema(id, schema);

                            mSqliteStorage.deleteDatabase();
                            // Re-open the db.
                            mSqliteStorage.open();

                            assertThat(mSqliteStorage.getDocument("id")).isNull();
                            assertThat(mSqliteStorage.getMetadata("id")).isNull();
                            assertThat(mSqliteStorage.getDocumentSchema("id")).isNull();
                        })
                .get();
    }

    @Test
    public void deleteDatabase_throwFromWrongThread() {
        assertThrows(IllegalThreadStateException.class, () -> mSqliteStorage.deleteDatabase());
    }

    @Test
    public void close_notAccessibleAfterwards() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] metadata = new byte[] {1, 2, 3, 4};
                            mSqliteStorage.persistMetadata(id, metadata);

                            mSqliteStorage.close();

                            assertThrows(
                                    StorageException.class, () -> mSqliteStorage.getDocument("id"));
                            assertThrows(
                                    StorageException.class, () -> mSqliteStorage.getMetadata("id"));
                            assertThrows(
                                    StorageException.class,
                                    () -> mSqliteStorage.getDocumentSchema("id"));
                            assertThrows(
                                    StorageException.class,
                                    () -> mSqliteStorage.persistDocument("id", new byte[0]));
                            assertThrows(
                                    StorageException.class,
                                    () -> mSqliteStorage.persistMetadata("id", new byte[0]));
                            assertThrows(
                                    StorageException.class,
                                    () -> mSqliteStorage.persistDocumentSchema("id", new byte[0]));
                            assertThrows(
                                    StorageException.class,
                                    () -> mSqliteStorage.getAllDocumentIds());
                            assertThrows(
                                    StorageException.class,
                                    () -> mSqliteStorage.deleteDocuments(List.of("id")));
                        })
                .get();
    }

    @Test
    public void close_throwFromWrongThread() {
        assertThrows(IllegalThreadStateException.class, mSqliteStorage::close);
    }

    @Test
    public void transact_metadataRolledBackOnFailure() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] metadata = new byte[] {1, 2, 3, 4};

                            try {
                                mSqliteStorage.transact(
                                        storage -> {
                                            mSqliteStorage.persistMetadata(id, metadata);
                                            throw new RuntimeException();
                                        });
                            } catch (StorageException e) {
                                // Do nothing.
                            }

                            assertThat(mSqliteStorage.getMetadata(id)).isNull();
                        })
                .get();
    }

    @Test
    public void transact_contentRolledBackOnFailure() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] content = new byte[] {1, 2, 3, 4};

                            try {
                                mSqliteStorage.transact(
                                        storage -> {
                                            mSqliteStorage.persistDocument(id, content);
                                            throw new RuntimeException();
                                        });
                            } catch (StorageException e) {
                                // Do nothing.
                            }

                            assertThat(mSqliteStorage.getDocument(id)).isNull();
                        })
                .get();
    }

    @Test
    public void transact_schemaRolledBackOnFailure() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            String id = "id";
                            byte[] schema = new byte[] {1, 2, 3, 4};

                            try {
                                mSqliteStorage.transact(
                                        storage -> {
                                            mSqliteStorage.persistDocumentSchema(id, schema);
                                            throw new RuntimeException();
                                        });
                            } catch (StorageException e) {
                                // Do nothing.
                            }

                            assertThat(mSqliteStorage.getDocumentSchema(id)).isNull();
                        })
                .get();
    }

    @Test
    public void transact_throwFromWrongThread() {
        assertThrows(
                IllegalThreadStateException.class, () -> mSqliteStorage.transact(unused -> {}));
    }

    @Test
    public void open_throwFromWrongThread() {
        assertThrows(IllegalThreadStateException.class, mSqliteStorage::open);
    }

    @Test
    public void getAllDocumentIds_returnAllIds() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            mSqliteStorage.persistDocument("id1", new byte[0]);
                            mSqliteStorage.persistDocumentSchema("id2", new byte[0]);
                            mSqliteStorage.persistMetadata("id3", new byte[0]);

                            assertThat(mSqliteStorage.getAllDocumentIds())
                                    .containsExactly("id1", "id2", "id3");
                        })
                .get();
    }

    @Test
    public void getAllDocumentIds_throwFromWrongThread() {
        assertThrows(IllegalThreadStateException.class, mSqliteStorage::getAllDocumentIds);
    }

    @Test
    public void deleteDocuments_documentsDeleted() throws Exception {
        mSqliteStorage
                .runInIoThread(
                        () -> {
                            mSqliteStorage.persistDocument("id1", new byte[0]);
                            mSqliteStorage.persistDocumentSchema("id2", new byte[0]);
                            mSqliteStorage.persistMetadata("id3", new byte[0]);

                            mSqliteStorage.deleteDocuments(List.of("id1", "id2"));

                            assertThat(mSqliteStorage.getDocument("id1")).isNull();
                            assertThat(mSqliteStorage.getDocumentSchema("id2")).isNull();
                            assertThat(mSqliteStorage.getMetadata("id3")).isNotNull();
                        })
                .get();
    }

    @Test
    public void deleteDocuments_throwFromWrongThread() {
        assertThrows(
                IllegalThreadStateException.class,
                () -> mSqliteStorage.deleteDocuments(List.of("id")));
    }

    @Test
    public void shutdownIoThread_success() {
        mSqliteStorage.shutdownIoThread();

        assertThat(isFutureFailed(mSqliteStorage.runInIoThread(() -> {}))).isTrue();
        assertThat(isFutureFailed(mSqliteStorage.submitToIoThread(() -> true))).isTrue();
    }
}
