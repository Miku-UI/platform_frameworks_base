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
package com.android.server.companion.datatransfer.crossdevicesync.data;

import android.annotation.Nullable;

import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.Document;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;

import java.util.List;

/**
 * An interface responsible for providing schema related information
 *
 * @param <T> the type of data managed by data store.
 */
public interface SchemaProvider<T> {

    /** Gets all document schemas. */
    List<DocumentSchemaInfo> getAllDocumentSchema();

    /**
     * Perform migration of a document after a schema version change. This will be called on IO
     * thread after schema change has been applied. For example, this can be used to copy or
     * transfer data from an older path to new path.
     *
     * <p>Warning: implementation should not assume the new path is empty. It's possible that the
     * new path already contains a data synced from a remote device. Whatever change made in this
     * method will be considered a new change and synced to remote devices. You may risk overriding
     * remote device's current value unexpectedly if you update a path without caution.
     */
    void migrateDocument(MutableDocument<T> document);

    /** Get document schema from doc id. */
    @Nullable
    default DocumentSchemaInfo findSchema(String docId) {
        for (DocumentSchemaInfo schema : getAllDocumentSchema()) {
            if (docId.equals(schema.getDocId())) {
                return schema;
            }
        }
        return null;
    }

    /**
     * Perform a schema validation on a document. This will be called on IO thread before a
     * transaction is committed.
     *
     * @param doc The document to validate.
     * @throws SchemaValidationException if validation fails.
     */
    default void validateDocument(Document<T> doc) throws SchemaValidationException {
        DocumentSchemaInfo schema = findSchema(doc.getDocId());
        if (schema == null) {
            throw new SchemaValidationException(
                    "validateSchema: schema not found for doc " + doc.getDocId() + "!");
        }
        if (doc.getSchemaVersion() != schema.getVersion()) {
            throw new SchemaValidationException(
                    "validateSchema: schema version mismatch! Expects "
                            + schema.getVersion()
                            + " but got "
                            + doc.getSchemaVersion()
                            + ".");
        }
        for (PathSchemaInfo pathSchema : schema.getPathSchema()) {
            String path = pathSchema.path();
            int type = pathSchema.type();
            SharedDataStore.Record<T> r = doc.getRecord(path);
            switch (r) {
                case null ->
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " not found!");
                case SharedDataStore.SetRecord<T> setRecord -> {
                    if (type != RecordType.TYPE_SET) {
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " is not a set!");
                    }
                }
                case SharedDataStore.UnmergedRecord<T> unmergedRecord -> {
                    if (type != RecordType.TYPE_UNMERGED) {
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " is not an unmerged record!");
                    }
                }
                default -> {
                    if (type != RecordType.TYPE_REGISTER) {
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " is not a register record!");
                    }
                }
            }
        }
    }
}
