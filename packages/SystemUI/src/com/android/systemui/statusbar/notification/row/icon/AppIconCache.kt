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

package com.android.systemui.statusbar.notification.row.icon

import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.launcher3.icons.BitmapInfo
import com.android.systemui.Dumpable
import com.android.systemui.statusbar.notification.collection.NotifCollectionCache
import com.android.systemui.util.asIndenting
import com.android.systemui.util.printSection
import com.android.systemui.util.time.SystemClock
import java.io.PrintWriter

/**
 * A cache for app icons. This class exists to make two simultaneous optimizations:
 * * It allows callers to select which [Drawable] instance they require using a
 *   `drawableInstanceKey`. This allows the notification views to share an instance for each of
 *   their expansion states while allowing the bundle header code to have a separate instance for
 *   each bundle, which allows us to perform animations on the drawables.
 * * It allows every [Drawable] instance for the same app to be built from the same [BitmapInfo],
 *   which ensures that the bitmap data is shared across all the drawables, reducing the memory
 *   overhead of having multiple drawables.
 */
class AppIconCache(systemClock: SystemClock) : Dumpable {
    private val bitmapInfoCache = NotifCollectionCache<BitmapInfo>(systemClock = systemClock)
    private val drawableCache = NotifCollectionCache<Drawable>(systemClock = systemClock)

    fun purgeCache(wantedPackages: Collection<String>) {
        // We don't know from the packages if it's the work profile app or not, so let's just keep
        // both if they're present in the cache.
        bitmapInfoCache.purgeUnless { getPackageFromKey(it) in wantedPackages }
        drawableCache.purgeUnless { getPackageFromKey(it) in wantedPackages }
    }

    private fun createKey(
        packageName: String,
        userHandle: UserHandle?,
        instanceKey: String,
    ): String = "$packageName|${userHandle?.identifier}|$instanceKey"

    private fun getPackageFromKey(key: String): String = key.substringBefore('|')

    /**
     * Get value from cache, or fetch it and add it to cache if not found. This can be called from
     * any thread, but is usually expected to be called from the background.
     *
     * @param packageName package of the app
     * @param userHandle user id of the app, if you need to cache one copy per user
     * @param drawableInstanceKey a string for which a unique drawable instance is desired
     * @param createDrawable method to convert a [BitmapInfo] into a [Drawable]
     * @param fetchBitmapInfo method to fetch a [BitmapInfo] for the current key
     */
    fun getOrFetchAppIcon(
        packageName: String,
        userHandle: UserHandle?,
        drawableInstanceKey: String,
        createDrawable: (BitmapInfo) -> Drawable,
        fetchBitmapInfo: () -> BitmapInfo,
    ): Drawable {
        val drawableCacheKey = createKey(packageName, userHandle, drawableInstanceKey)
        val drawable =
            drawableCache.getOrFetch(drawableCacheKey) {
                val bitmapInfoCacheKey = createKey(packageName, userHandle, instanceKey = "SHARED")
                val bitmapInfo =
                    bitmapInfoCache.getOrFetch(bitmapInfoCacheKey) { fetchBitmapInfo() }
                createDrawable(bitmapInfo)
            }
        return drawable
    }

    override fun dump(pwOrig: PrintWriter, args: Array<out String>) {
        val pw = pwOrig.asIndenting()
        pw.printSection("BitmapInfo cache information") { bitmapInfoCache.dump(pw, args) }
        pw.printSection("Drawable cache information") { drawableCache.dump(pw, args) }
    }
}
