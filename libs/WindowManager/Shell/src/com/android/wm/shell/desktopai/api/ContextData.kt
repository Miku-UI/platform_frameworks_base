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

package com.android.wm.shell.desktopai.api

/**
 * Defines the contract for a specific unit of context information.
 *
 * Implementations represent specific data types (e.g., Recent Apps, Location) that can be
 * serialized and transported within an [AggregatedContext].
 *
 * All implementations must provide a stable, unique [id] to allow mapping across process boundaries
 * via Protobuf.
 */
interface ContextData {
    /**
     * A unique string identifier for this data type.
     *
     * This ID is used as the key for serialization (Proto) and retrieval logic. It should be
     * constant for the specific implementation class. Example: "context.recents",
     * "context.location".
     */
    val id: String
}
