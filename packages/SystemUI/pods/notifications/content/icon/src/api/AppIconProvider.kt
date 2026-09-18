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

package com.android.systemui.notifications.content.icon

import android.annotation.WorkerThread
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.UserHandle

/** A provider used to cache and fetch app icons used by notifications. */
public interface AppIconProvider {
    /**
     * Loads the icon corresponding to [packageName] into cache, or fetches it from there if already
     * present. This should only be called from the background.
     */
    @Throws(NameNotFoundException::class)
    @WorkerThread
    public fun getOrFetchAppIcon(
        packageName: String,
        userHandle: UserHandle,
        instanceKey: String,
    ): Drawable

    /**
     * Loads the skeleton (black and white)-themed icon corresponding to [packageName] into cache,
     * or fetches it from there if already present. This should only be called from the background.
     */
    @Throws(NameNotFoundException::class)
    @WorkerThread
    public fun getOrFetchSkeletonAppIcon(packageName: String, userHandle: UserHandle): Drawable

    /**
     * Mark all the entries in the cache that are NOT in [wantedPackages] to be cleared. If they're
     * still not needed on the next call of this method (made after a timeout of 1s, in case they
     * happen more frequently than that), they will be purged. This can be done from any thread.
     */
    public fun purgeCache(wantedPackages: Collection<String>)
}
