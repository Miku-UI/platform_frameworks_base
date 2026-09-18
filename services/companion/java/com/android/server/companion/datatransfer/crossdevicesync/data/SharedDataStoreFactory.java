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

package com.android.server.companion.datatransfer.crossdevicesync.data;

import android.annotation.NonNull;

/**
 * A testable interface for creating new {@link SharedDataStore} instances.
 *
 * @param <T> The type of the data stored in the data store.
 */
public interface SharedDataStoreFactory<T> {

    /** Create a new instance of {@link SharedDataStore} */
    @NonNull
    SharedDataStore<T> create(@NonNull SharedDataStoreHandle<T> handle);
}
