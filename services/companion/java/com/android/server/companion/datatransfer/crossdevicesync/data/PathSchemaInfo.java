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

/**
 * Data record for a path schema.
 *
 * @param path the data path.
 * @param type the type of the record. Must be one of {@code RecordType}.
 * @param version the schema version of the document when this path is firstly introduced. Must not
 *     change once introduced.
 */
public record PathSchemaInfo(String path, @RecordType int type, int version) {}
