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
 *
 */

package com.android.systemui.user.data.repository

import android.annotation.UserIdInt
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap
import com.android.internal.util.UserIcons
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class UserIconProvider
@Inject
constructor(
    @Application private val appContext: Context,
    private val userManager: UserManager,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {

    private data class UserImageCacheKey(val userId: Int, val iconSize: Int)

    private val userImageCache = ConcurrentHashMap<UserImageCacheKey, android.graphics.Bitmap>()

    suspend fun getUserImage(@UserIdInt userId: Int, iconSize: Int): Drawable {
        val cacheKey = UserImageCacheKey(userId = userId, iconSize = iconSize)

        // Return cached image when possible. If there is no cache yet for the given user and the
        // given image size, we will cache newly created Bitmap below.
        userImageCache[cacheKey]?.let { cachedBitmap ->
            return BitmapDrawable(appContext.resources, cachedBitmap)
        }

        val userIcon =
            withContext(backgroundDispatcher) {
                userManager.getUserIcon(userId)?.let { bitmap ->
                    Icon.scaleDownIfNecessary(bitmap, iconSize, iconSize)
                }
                    ?: run {
                        val defaultIcon =
                            UserIcons.getDefaultUserIcon(
                                appContext.resources,
                                userId,
                                /* light= */ false,
                            )
                        defaultIcon.toBitmap(iconSize, iconSize)
                    }
            }

        userImageCache[cacheKey] = userIcon
        return BitmapDrawable(appContext.resources, userIcon)
    }

    fun clearCacheForUser(@UserIdInt userId: Int) {
        userImageCache.entries.removeIf { it.key.userId == userId }
    }
}
