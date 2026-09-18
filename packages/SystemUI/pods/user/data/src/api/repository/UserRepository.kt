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

package com.android.systemui.user.data.repository

import android.annotation.UserIdInt
import android.content.pm.UserInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.systemui.user.data.model.SelectedUserModel
import com.android.systemui.user.data.model.UserSwitcherSettingsModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Acts as source of truth for user related data.
 *
 * Abstracts-away data sources and their schemas so the rest of the app doesn't need to worry about
 * upstream changes.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface UserRepository {
    /** User switcher related settings. */
    public val userSwitcherSettings: Flow<UserSwitcherSettingsModel>

    /** List of all users on the device. */
    public val userInfos: Flow<List<UserInfo>>

    /** Information about the currently-selected user, including [UserInfo] and other details. */
    public val selectedUser: StateFlow<SelectedUserModel>

    /** [UserInfo] of the currently-selected user. */
    @get:JvmName("selectedUserInfo") public val selectedUserInfo: Flow<UserInfo>

    /** Tracks whether the main user is unlocked. */
    public fun isUserUnlocked(userHandle: UserHandle?): Flow<Boolean>

    /** User ID of the main user. */
    public val mainUserId: Int

    /** User ID of the last non-guest selected user. */
    public val lastSelectedNonGuestUserId: Int

    /** Whether the device is configured to always have a guest user available. */
    public val isGuestUserAutoCreated: Boolean

    /** Whether there is more than one full user */
    public val hasMultipleFullUsers: Flow<Boolean>
        get() =
            userInfos.map { it.count { userInfo -> userInfo.isFull } > 1 }.distinctUntilChanged()

    /** Whether the guest user is currently being reset. */
    public var isGuestUserResetting: Boolean

    /** Whether we've scheduled the creation of a guest user. */
    public val isGuestUserCreationScheduled: AtomicBoolean

    /** Whether to enable the status bar user chip (which launches the user switcher) */
    public val isStatusBarUserChipEnabled: Boolean

    /** The user of the secondary service. */
    public var secondaryUserId: Int

    /** Whether refresh users should be paused. */
    public var isRefreshUsersPaused: Boolean

    /**
     * Whether logout back to primary user can be performed by the device Policy Manager for the
     * secondary account. See
     * https://developer.android.com/work/dpc/dedicated-devices/multiple-users#logout for more
     * details.
     */
    public val isPolicyManagerLogoutEnabled: StateFlow<Boolean>

    /**
     * Whether logout can be performed by the UserManager, to support desktop-style logout into the
     * neutral login space.
     */
    public val isUserManagerLogoutEnabled: StateFlow<Boolean>

    /** Whether the current user is the headless system user. */
    public val isCurrentUserHeadlessSystemUser: StateFlow<Boolean>

    /** Asynchronously refresh the list of users. This will cause [userInfos] to be updated. */
    public fun refreshUsers()

    public fun getSelectedUserInfo(): UserInfo

    public fun isSimpleUserSwitcher(): Boolean

    public fun isUserSwitcherEnabled(): Boolean

    /** Performs logout from secondary user into primary user via DevicePolicyManager. */
    public suspend fun logOutWithPolicyManager()

    /** Performs logout into the system user via UserManager. */
    public suspend fun logOutWithUserManager()

    /**
     * Returns the user ID of the "main user" of the device. This user may have access to certain
     * features which are limited to at most one user. There will never be more than one main user
     * on a device.
     *
     * <p>Currently, on most form factors the first human user on the device will be the main user;
     * in the future, the concept may be transferable, so a different user (or even no user at all)
     * may be designated the main user instead. On other form factors there might not be a main
     * user.
     *
     * <p> When the device doesn't have a main user, this will return {@code null}.
     *
     * @see [UserManager.getMainUser]
     */
    @UserIdInt public suspend fun getMainUserId(): Int?

    /** Returns the user image, potentially from a cache. */
    public suspend fun getUserImage(@UserIdInt userId: Int, iconSize: Int): Drawable

    /** Clears all cached user images for specified user. */
    public fun clearUserImageCacheForUser(@UserIdInt userId: Int)
}
