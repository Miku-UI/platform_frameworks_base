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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data object representing a document schema.
 *
 * <p>Warning: schema change must be backwards compatible. If an existing schema is modified in an
 * incompatible way (e.g. a path is removed or its data type is changed), the schema change itself
 * will be synced to older devices and may break them.
 */
public class DocumentSchemaInfo {
    private final String mDocId;
    private final int mVersion;
    private final List<PathSchemaInfo> mPathSchema;

    private DocumentSchemaInfo(String docId, int version, List<PathSchemaInfo> pathSchema) {
        mDocId = docId;
        mVersion = version;
        mPathSchema = pathSchema;
    }

    /** Get the doc id. */
    public String getDocId() {
        return mDocId;
    }

    /** Get the schema version. */
    public int getVersion() {
        return mVersion;
    }

    /** Get schema of all paths. */
    public List<PathSchemaInfo> getPathSchema() {
        return mPathSchema;
    }

    /** Creates a {@link Builder} for {@link DocumentSchemaInfo} */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link DocumentSchemaInfo}. */
    public static final class Builder {
        private String mDocId;
        private int mVersion;
        private final List<PathSchemaInfo> mPathSchema = new ArrayList<>();

        private Builder() {}

        /** Sets the document ID. */
        public Builder setDocId(String docId) {
            mDocId = docId;
            return this;
        }

        /** Sets the schema version. */
        public Builder setVersion(int version) {
            mVersion = version;
            return this;
        }

        /**
         * Adds or updates a path schema.
         *
         * @param path the data path.
         * @param type the type of the record. Must be one of {@link RecordType}.
         * @param version the schema version of the document when this path is firstly introduced.
         *     Must not change once introduced.
         */
        public Builder putPathSchema(String path, @RecordType int type, int version) {
            mPathSchema.add(new PathSchemaInfo(path, type, version));
            return this;
        }

        /** Builds the {@link DocumentSchemaInfo} object. */
        public DocumentSchemaInfo build() {
            // Validate schema.
            Set<String> pathSet = new HashSet<>(mPathSchema.size());
            for (PathSchemaInfo schemaInfo : mPathSchema) {
                if (!pathSet.add(schemaInfo.path())) {
                    throw new RuntimeException(
                            "Can't create schema: duplicate path - " + schemaInfo.path());
                }
                if (schemaInfo.version() > mVersion) {
                    throw new RuntimeException(
                            "Can't create schema: schema version "
                                    + schemaInfo.version()
                                    + " of path "
                                    + schemaInfo.path()
                                    + " is greater than document schema version "
                                    + mVersion);
                }
                switch (schemaInfo.type()) {
                    case RecordType.TYPE_REGISTER,
                            RecordType.TYPE_SET,
                            RecordType.TYPE_UNMERGED -> {
                        // Only these record types are valid.
                    }
                    default ->
                            throw new RuntimeException("Unknown record type: " + schemaInfo.type());
                }
            }
            return new DocumentSchemaInfo(requireNonNull(mDocId), mVersion, mPathSchema);
        }
    }
}
