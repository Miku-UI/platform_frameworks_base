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

package com.android.server.dreams;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.service.dreams.DreamItem;

import java.util.Optional;

/** Repository for accessing dream configuration and metadata. */
interface DreamRepository {
    /** Returns the configured dream components for the user. */
    ComponentName[] getDreamComponentsForUser(int userId);

    /** Returns the default dream component for the user. */
    ComponentName getDefaultDreamComponentForUser(int userId);

    /** Returns the active dream component for the user. */
    ComponentName getActiveDreamComponentForUser(int userId);

    /** Returns the DreamItem for the given component, if available. */
    Optional<DreamItem> getDreamItem(ComponentName component);
}
