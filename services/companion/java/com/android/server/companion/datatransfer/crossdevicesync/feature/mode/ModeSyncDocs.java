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
package com.android.server.companion.datatransfer.crossdevicesync.feature.mode;

import static com.android.server.companion.datatransfer.crossdevicesync.data.RecordType.TYPE_REGISTER;

import com.android.server.companion.datatransfer.crossdevicesync.data.Docs;
import com.android.server.companion.datatransfer.crossdevicesync.data.DocumentSchemaInfo;
import com.android.server.companion.datatransfer.crossdevicesync.data.SchemaProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.SchemaValidationException;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.Document;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;

import java.util.List;
import java.util.function.Consumer;

/** Utility class for interacting with mode sync doc. */
public final class ModeSyncDocs {
    /** Mode sync document id. */
    public static final String CONTEXTUAL_MODES_DOC_ID = "contextual_modes";

    /** Path for manual DND mode. */
    public static final String MANUAL_DO_NOT_DISTURB_PATH = "/manual_do_not_disturb";

    /** Attribute key for mode state. */
    public static final String ATTRIBUTE_STATE = "state";

    /** Value of ATTRIBUTE_STATE indicating the mode is inactive. */
    public static final int STATE_INACTIVE = 0;

    /** Value of ATTRIBUTE_STATE indicating the mode is active. */
    public static final int STATE_ACTIVE = 1;

    /** Current schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final DocumentSchemaInfo MODE_SYNC_DOC_SCHEMA =
            DocumentSchemaInfo.builder()
                    .setDocId(CONTEXTUAL_MODES_DOC_ID)
                    .setVersion(CURRENT_SCHEMA_VERSION)
                    .putPathSchema(
                            getManualDoNotDisturbStatePath(), TYPE_REGISTER, /* version= */ 1)
                    .build();

    private ModeSyncDocs() {}

    /** Get a schema provider for the mode sync doc. */
    public static SchemaProvider<String> getSchemaProvider(
            Consumer<MutableDocument<String>> migrator) {
        return new ModeSyncSchemaProvider(migrator);
    }

    /** Get the path for manual DND state. */
    public static String getManualDoNotDisturbStatePath() {
        return MANUAL_DO_NOT_DISTURB_PATH + "/" + ATTRIBUTE_STATE;
    }

    /** Check if the mode in the given path is active. */
    public static boolean isModeStateActive(Document<String> doc, String path) {
        return Docs.getInt(doc, path) == STATE_ACTIVE;
    }

    /** Put active state of the mode in the given path. */
    public static void putModeStateActive(
            MutableDocument<String> doc, String path, boolean active) {
        Docs.putInt(doc, path, active ? STATE_ACTIVE : STATE_INACTIVE);
    }

    /** Schema provider for mode sync doc. */
    private static class ModeSyncSchemaProvider implements SchemaProvider<String> {
        private final Consumer<MutableDocument<String>> mMigrator;

        ModeSyncSchemaProvider(Consumer<MutableDocument<String>> migrator) {
            mMigrator = migrator;
        }

        @Override
        public List<DocumentSchemaInfo> getAllDocumentSchema() {
            return List.of(MODE_SYNC_DOC_SCHEMA);
        }

        @Override
        public void migrateDocument(MutableDocument<String> document) {
            mMigrator.accept(document);
        }

        @Override
        public void validateDocument(Document<String> doc) throws SchemaValidationException {
            SchemaProvider.super.validateDocument(doc);
            try {
                int manualDndState = Docs.getInt(doc, getManualDoNotDisturbStatePath());
                if (manualDndState != STATE_ACTIVE && manualDndState != STATE_INACTIVE) {
                    throw new SchemaValidationException(
                            "Illegal manual do not disturb state: " + manualDndState);
                }
            } catch (SchemaValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new SchemaValidationException(e);
            }
        }
    }
}
