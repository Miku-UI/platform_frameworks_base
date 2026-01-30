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

package com.android.server.am.psc;

import android.content.ComponentName;

/** Internal representation of a content provider record. */
public abstract class ContentProviderRecordInternal {
    /** The component name of this content provider. */
    public final ComponentName name;

    public ContentProviderRecordInternal(ComponentName name) {
        this.name = name;
    }

    /** Returns the process record that hosts this content provider. */
    public abstract ProcessRecordInternal getHostProcess();

    /** Checks if this content provider has any active external process handles or connections. */
    public abstract boolean hasExternalProcessHandles();

    /** Returns the total number of active connections to this content provider. */
    public abstract int numberOfConnections();

    /** Returns the content provider connection at the specified index. */
    public abstract ContentProviderConnectionInternal getConnectionsAt(int index);
}
