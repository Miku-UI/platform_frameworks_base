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

package com.android.systemui.screencapture.common.data.repository

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.IconFactory
import com.android.launcher3.util.UserIconInfo
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.shared.system.PackageManagerWrapper
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/** Repository for app icons. */
interface ScreenCaptureIconRepository {
    /** Fetch app icon on background dispatcher. */
    suspend fun loadIcon(
        component: ComponentName,
        @UserIdInt userId: Int,
        badged: Boolean = true,
    ): Result<Bitmap>
}

/** Default implementation of [ScreenCaptureIconRepository]. */
@ScreenCaptureUiScope
class ScreenCaptureIconRepositoryImpl
@Inject
constructor(
    @Background private val bgContext: CoroutineContext,
    private val context: Context,
    private val userManager: UserManager,
    private val packageManagerWrapper: PackageManagerWrapper,
    private val packageManager: PackageManager,
    @ScreenCaptureUi private val iconFactoryProvider: Provider<IconFactory>,
) : ScreenCaptureIconRepository {

    override suspend fun loadIcon(
        component: ComponentName,
        @UserIdInt userId: Int,
        badged: Boolean,
    ): Result<Bitmap> =
        withContext(bgContext) {
            packageManagerWrapper
                .getActivityInfo(component, userId)
                ?.loadIcon(packageManager)
                ?.let {
                    Result.success(
                        if (badged) {
                                badgeIcon(it, userId)
                            } else {
                                it
                            }
                            .toBitmap()
                    )
                }
                ?: Result.failure(
                    IllegalStateException(
                        "Could not find icon for ${component.flattenToString()}, user $userId"
                    )
                )
        }

    private suspend fun badgeIcon(icon: Drawable, @UserIdInt userId: Int): Drawable {
        return withContext(bgContext) {
            val iconType = async { getIconTypeForUser(userId) }

            iconFactoryProvider.get().use { iconFactory ->
                val options =
                    BaseIconFactory.IconOptions().apply {
                        setUser(UserIconInfo(UserHandle(userId), iconType.await()))
                    }
                val badgedIcon = iconFactory.createBadgedIconBitmap(icon, options)
                badgedIcon.newIcon(context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @VisibleForTesting(otherwise = VisibleForTesting.Companion.PRIVATE)
    suspend fun getIconTypeForUser(@UserIdInt userId: Int): Int =
        withContext(bgContext) {
            val userInfo = userManager.getUserInfo(userId)

            when {
                userInfo.isCloneProfile -> UserIconInfo.TYPE_CLONED
                userInfo.isManagedProfile -> UserIconInfo.TYPE_WORK
                userInfo.isPrivateProfile -> UserIconInfo.TYPE_PRIVATE
                else -> UserIconInfo.TYPE_MAIN
            }
        }
}
