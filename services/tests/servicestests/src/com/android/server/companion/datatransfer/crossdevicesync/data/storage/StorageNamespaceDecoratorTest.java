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
package com.android.server.companion.datatransfer.crossdevicesync.data.storage;

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureSucceeded;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.data.storage.fake.FakeStorage;

import com.google.android.submerge.StorageInterface.StorageException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class StorageNamespaceDecoratorTest {
    private static final String NAMESPACE = "namespace";
    private static final String ANOTHER_NAMESPACE = "another_namespace";

    private StorageNamespaceDecorator mStorage;
    private StorageNamespaceDecorator mAnotherStorage;

    @Before
    public void setUp() {
        FakeStorage delegate = new FakeStorage();
        mStorage = new StorageNamespaceDecorator(NAMESPACE, delegate);
        mStorage.open();
        mAnotherStorage = new StorageNamespaceDecorator(ANOTHER_NAMESPACE, delegate);
        mAnotherStorage.open();
    }

    @Test
    public void testPersistAndGetDocument() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mStorage.persistDocument(id, doc);

        assertThat(Arrays.equals(doc, mStorage.getDocument(id))).isTrue();
    }

    @Test
    public void testPersistAndGetDocument_multipleDecorators() throws StorageException {
        String id = "id";
        byte[] doc1 = new byte[] {1, 2, 3, 4};
        byte[] doc2 = new byte[] {5, 6, 7, 8};
        mStorage.persistDocument(id, doc1);
        mAnotherStorage.persistDocument(id, doc2);

        assertThat(Arrays.equals(doc1, mStorage.getDocument(id))).isTrue();
        assertThat(Arrays.equals(doc2, mAnotherStorage.getDocument(id))).isTrue();
    }

    @Test
    public void testPersistAndGetMetadata() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        mStorage.persistMetadata(id, metadata);

        assertThat(Arrays.equals(metadata, mStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void testPersistAndGetMetadata_multipleDecorators() throws StorageException {
        String id = "id";
        byte[] metadata1 = new byte[] {1, 2, 3, 4};
        byte[] metadata2 = new byte[] {5, 6, 7, 8};
        mStorage.persistMetadata(id, metadata1);
        mAnotherStorage.persistMetadata(id, metadata2);

        assertThat(Arrays.equals(metadata1, mStorage.getMetadata(id))).isTrue();
        assertThat(Arrays.equals(metadata2, mAnotherStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void testPersistAndGetDocumentSchema() throws StorageException {
        String id = "id";
        byte[] schema = new byte[] {1, 2, 3, 4};
        mStorage.persistDocumentSchema(id, schema);

        assertThat(Arrays.equals(schema, mStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void testPersistAndGetDocumentSchema_multipleDecorators() throws StorageException {
        String id = "id";
        byte[] schema1 = new byte[] {1, 2, 3, 4};
        byte[] schema2 = new byte[] {5, 6, 7, 8};
        mStorage.persistDocumentSchema(id, schema1);
        mAnotherStorage.persistDocumentSchema(id, schema2);

        assertThat(Arrays.equals(schema1, mStorage.getDocumentSchema(id))).isTrue();
        assertThat(Arrays.equals(schema2, mAnotherStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void testDeleteDocuments() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);

        mStorage.deleteDocuments(List.of("id1", "id2"));

        assertThat(mStorage.getDocument("id1")).isNull();
        assertThat(mStorage.getDocumentSchema("id2")).isNull();
        assertThat(mStorage.getMetadata("id3")).isNotNull();
    }

    @Test
    public void testDeleteDocuments_multipleDecorators() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);
        mAnotherStorage.persistDocument("id1", new byte[0]);
        mAnotherStorage.persistDocumentSchema("id2", new byte[0]);
        mAnotherStorage.persistMetadata("id3", new byte[0]);

        mStorage.deleteDocuments(List.of("id1", "id2"));

        assertThat(mStorage.getDocument("id1")).isNull();
        assertThat(mStorage.getDocumentSchema("id2")).isNull();
        assertThat(mStorage.getMetadata("id3")).isNotNull();
        assertThat(mAnotherStorage.getDocument("id1")).isNotNull();
        assertThat(mAnotherStorage.getDocumentSchema("id2")).isNotNull();
        assertThat(mAnotherStorage.getMetadata("id3")).isNotNull();
    }

    @Test
    public void testGetAllDocumentIds() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);

        assertThat(mStorage.getAllDocumentIds()).containsExactly("id1", "id2", "id3");
    }

    @Test
    public void testGetAllDocumentIds_multipleDecorators() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);
        mAnotherStorage.persistDocument("id1", new byte[0]);

        assertThat(mStorage.getAllDocumentIds()).containsExactly("id1", "id2", "id3");
        assertThat(mAnotherStorage.getAllDocumentIds()).containsExactly("id1");
    }

    @Test
    public void testDeleteDatabase() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);

        mStorage.deleteDatabase();

        assertThat(mStorage.getDocument("id1")).isNull();
        assertThat(mStorage.getDocumentSchema("id2")).isNull();
        assertThat(mStorage.getMetadata("id3")).isNull();
    }

    @Test
    public void testDeleteDatabaseAfterClose() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);
        mStorage.close();

        mStorage.deleteDatabase();

        mStorage.open();
        assertThat(mStorage.getDocument("id1")).isNull();
        assertThat(mStorage.getDocumentSchema("id2")).isNull();
        assertThat(mStorage.getMetadata("id3")).isNull();
    }

    @Test
    public void testDeleteDatabase_multipleDecorators() throws StorageException {
        mStorage.persistDocument("id1", new byte[0]);
        mStorage.persistDocumentSchema("id2", new byte[0]);
        mStorage.persistMetadata("id3", new byte[0]);
        mAnotherStorage.persistDocument("id1", new byte[0]);

        mStorage.deleteDatabase();

        assertThat(mStorage.getDocument("id1")).isNull();
        assertThat(mStorage.getDocumentSchema("id2")).isNull();
        assertThat(mStorage.getMetadata("id3")).isNull();
        assertThat(mAnotherStorage.getDocument("id1")).isNotNull();
    }

    @Test
    public void testIoThread() {
        assertThat(isFutureSucceeded(mStorage.runInIoThread(() -> {}))).isTrue();
        assertThat(isFutureSucceeded(mAnotherStorage.runInIoThread(() -> {}))).isTrue();
    }

    @Test
    public void testShutdownIoThread() {
        mStorage.shutdownIoThread();

        assertThat(isFutureFailed(mStorage.runInIoThread(() -> {}))).isTrue();
        assertThat(isFutureSucceeded(mAnotherStorage.runInIoThread(() -> {}))).isTrue();
    }

    @Test
    public void testClose() throws StorageException {
        mStorage.close();

        assertThrows(StorageException.class, () -> mStorage.getDocument("id"));
        // Shall not affect another storage.
        mAnotherStorage.getDocument("id");
    }

    @Test
    public void testOpenAfterClose() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mStorage.persistDocument(id, doc);

        mStorage.close();
        mStorage.open();

        assertThat(Arrays.equals(doc, mStorage.getDocument(id))).isTrue();
    }

    @Test
    public void testOpenAfterClose_multipleDecorators() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mStorage.persistDocument(id, doc);
        mAnotherStorage.persistDocument(id, doc);

        mStorage.close();
        mStorage.open();

        assertThat(Arrays.equals(doc, mStorage.getDocument(id))).isTrue();
        assertThat(Arrays.equals(doc, mAnotherStorage.getDocument(id))).isTrue();
    }
}
