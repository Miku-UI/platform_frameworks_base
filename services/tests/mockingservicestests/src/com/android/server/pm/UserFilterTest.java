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
package com.android.server.pm;

import static android.content.pm.UserInfo.FLAG_ADMIN;
import static android.content.pm.UserInfo.FLAG_FULL;
import static android.content.pm.UserInfo.FLAG_MAIN;
import static android.content.pm.UserInfo.FLAG_SYSTEM;
import static android.os.UserHandle.USER_SYSTEM;
import static android.os.UserManager.USER_TYPE_FULL_SYSTEM;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;

import static org.junit.Assert.assertThrows;

import android.annotation.UserIdInt;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;

import com.android.server.pm.UserFilter.Builder;
import com.android.server.pm.UserFilter.DeathPredictor;

import com.google.common.testing.EqualsTester;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class UserFilterTest {

    private static final @UserIdInt int ADMIN_USER_ID = 4;
    private static final @UserIdInt int NON_ADMIN_USER_ID = 8;
    private static final @UserIdInt int FLAGLESS_USER_ID = 15;
    private static final @UserIdInt int PARTIAL_USER_ID = 16;
    private static final @UserIdInt int DYING_USER_ID = 23;
    private static final @UserIdInt int PRE_CREATED_USER_ID = 42;

    private static final DeathPredictor NOSTRADAMUS = user -> user.id == DYING_USER_ID;

    private final UserInfo mNullUser = null;

    // Note: not setting FLAG_PRIMARY on system user as it's deprecated (and not really used
    private final UserInfo mFullSystemUser =
            createUser(USER_SYSTEM, "full system user",
            FLAG_ADMIN | FLAG_FULL | FLAG_SYSTEM, USER_TYPE_FULL_SYSTEM);
    private final UserInfo mHeadlessSystemUser =
            createUser(USER_SYSTEM, "headless system user",
            FLAG_ADMIN | FLAG_SYSTEM, USER_TYPE_SYSTEM_HEADLESS);

    private final UserInfo mAdminUser =
            createUser(ADMIN_USER_ID, "admin user",
            FLAG_ADMIN | FLAG_FULL , USER_TYPE_FULL_SECONDARY);
    private final UserInfo mNonAdminUser =
            createSecondaryUser(NON_ADMIN_USER_ID, "non-admin user");
    private final UserInfo mFlagLessUser =
            createUser(FLAGLESS_USER_ID, "flagless user", /* flags= */ 0, USER_TYPE_FULL_SECONDARY);
    private final UserInfo mDyingUser = createSecondaryUser(DYING_USER_ID, "dying user");

    // Users below are defined in the constructor because we need to set some properties after
    // they're instantiated
    private final UserInfo mPartialUser;
    // NOTE: pre-created users are not supported anymore, so they shouldn't be included on any
    // result
    private final UserInfo mPreCreatedUser;

    @Rule
    public final Expect expect = Expect.create();

    public UserFilterTest() {
        mPartialUser = createSecondaryUser(PARTIAL_USER_ID, "partial user");
        mPartialUser.partial = true;

        mPreCreatedUser = createSecondaryUser(PRE_CREATED_USER_ID, "pre-created user");
        mPreCreatedUser.preCreated = true;
    }

    @Test
    public void testEquals() {
        var defaultBuilder = createBuilder();
        UserFilter default1 = defaultBuilder.build();
        UserFilter default2 = defaultBuilder.build();

        var builderWithEverything = createBuilder()
                .withPartialUsers()
                .withDyingUsers()
                .setRequiredFlags(FLAG_MAIN);
        UserFilter withEverything1 = builderWithEverything.build();
        UserFilter withEverything2 = builderWithEverything.build();

        new EqualsTester()
                .addEqualityGroup(default1, default2)
                .addEqualityGroup(withEverything1, withEverything2)
                .testEquals();
    }

    @Test
    public void testNullDeathPredictor() {
        UserFilter filter = createBuilder().build();

        assertThrows(NullPointerException.class, ()-> filter.matches(null, mFullSystemUser));
        assertThrows(NullPointerException.class, ()-> filter.matches(null, null));
    }

    @Test
    public void testDefaultFilter() {
        UserFilter filter = createBuilder().build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, true);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, true);
        expectMatches(filter, mFlagLessUser, true);
        expectMatches(filter, mPartialUser, false);
        expectMatches(filter, mDyingUser, false);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testIncludePartial() {
        UserFilter filter = createBuilder()
                .withPartialUsers()
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, true);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, true);
        expectMatches(filter, mFlagLessUser, true);
        expectMatches(filter, mPartialUser, true);
        expectMatches(filter, mDyingUser, false);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testIncludeDying() {
        UserFilter filter = createBuilder()
                .withDyingUsers()
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, true);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, true);
        expectMatches(filter, mFlagLessUser, true);
        expectMatches(filter, mPartialUser, false);
        expectMatches(filter, mDyingUser, true);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testIncludePreCreated() {
        UserFilter filter = createBuilder()
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, true);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, true);
        expectMatches(filter, mFlagLessUser, true);
        expectMatches(filter, mPartialUser, false);
        expectMatches(filter, mDyingUser, false);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testIncludeAllDefectiveUsers() {
        UserFilter filter = createBuilder()
                .withPartialUsers()
                .withDyingUsers()
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, true);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, true);
        expectMatches(filter, mFlagLessUser, true);
        expectMatches(filter, mPartialUser, true);
        expectMatches(filter, mDyingUser, true);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testAdminsOnly() {
        UserFilter filter = createBuilder()
                .setRequiredFlags(FLAG_ADMIN)
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, true);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, false);
        expectMatches(filter, mFlagLessUser, false);
        expectMatches(filter, mPartialUser, false);
        expectMatches(filter, mDyingUser, false);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testFullUsersOnly() {
        UserFilter filter = createBuilder()
                .setRequiredFlags(FLAG_FULL)
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, false);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, true);
        expectMatches(filter, mFlagLessUser, false);
        expectMatches(filter, mPartialUser, false);
        expectMatches(filter, mDyingUser, false);
        expectMatches(filter, mPreCreatedUser, false);
    }

    @Test
    public void testFullAdminsOnly() {
        UserFilter filter = createBuilder()
                .setRequiredFlags(FLAG_FULL | FLAG_ADMIN)
                .build();

        expectMatches(filter, mNullUser, false);
        expectMatches(filter, mFullSystemUser, true);
        expectMatches(filter, mHeadlessSystemUser, false);
        expectMatches(filter, mAdminUser, true);
        expectMatches(filter, mNonAdminUser, false);
        expectMatches(filter, mFlagLessUser, false);
        expectMatches(filter, mPartialUser, false);
        expectMatches(filter, mDyingUser, false);
        expectMatches(filter, mPreCreatedUser, false);
    }

    private void expectMatches(UserFilter filter, UserInfo user, boolean value) {
        expect.withMessage("matches %s", user).that(filter.matches(NOSTRADAMUS, user))
                .isEqualTo(value);
    }

    private static UserInfo createUser(@UserIdInt int userId, String name, @UserInfoFlag int flags,
            String userType) {
        return new UserInfo(userId, name, /* iconPath= */ null, flags, userType);
    }

    private static UserInfo createSecondaryUser(@UserIdInt int userId, String name) {
        return createUser(userId, name, FLAG_FULL, USER_TYPE_FULL_SECONDARY);
    }

    private Builder createBuilder() {
        return UserFilter.builder();
    }
}
