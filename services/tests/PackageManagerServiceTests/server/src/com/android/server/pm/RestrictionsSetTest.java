/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.server.devicepolicy.DpmTestUtils.assertRestrictions;
import static com.android.server.devicepolicy.DpmTestUtils.newRestrictions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** Test for {@link RestrictionsSet}. */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class RestrictionsSetTest {

    private RestrictionsSet mRestrictionsSet = new RestrictionsSet();
    private final int originatingUserId = 0;

    @Test
    public void testUpdateRestrictions_addRestrictions() {
        Bundle restrictions = newRestrictions(UserManager.ENSURE_VERIFY_APPS);

        assertTrue(mRestrictionsSet.updateRestrictions(originatingUserId, restrictions));

        assertRestrictions(restrictions, mRestrictionsSet.getRestrictions(originatingUserId));
    }

    @Test
    public void testUpdateRestrictions_removeRestrictions() {
        Bundle restrictions = newRestrictions(UserManager.ENSURE_VERIFY_APPS);
        mRestrictionsSet.updateRestrictions(originatingUserId, restrictions);

        assertTrue(mRestrictionsSet.updateRestrictions(originatingUserId, new Bundle()));

        assertNull(mRestrictionsSet.getRestrictions(originatingUserId));
    }

    @Test
    public void testUpdateRestrictions_noChange() {
        Bundle restrictions = newRestrictions(UserManager.ENSURE_VERIFY_APPS);
        mRestrictionsSet.updateRestrictions(originatingUserId, restrictions);

        assertFalse(mRestrictionsSet.updateRestrictions(originatingUserId, restrictions));
    }

    @Test
    public void testRemoveRestrictionsForAllUsers() {
        // Verifies that a specific restriction is removed from all users.
        final int userId1 = 0;
        final int userId2 = 10;
        final String restrictionToRemove = UserManager.ENSURE_VERIFY_APPS;
        final String restrictionToKeep = UserManager.DISALLOW_CONFIG_DATE_TIME;

        mRestrictionsSet.updateRestrictions(userId1,
                newRestrictions(restrictionToRemove, restrictionToKeep));
        mRestrictionsSet.updateRestrictions(userId2, newRestrictions(restrictionToRemove));

        // Remove the restriction for all users
        assertTrue(mRestrictionsSet.removeRestrictionsForAllUsers(restrictionToRemove));

        // Verify restriction is removed for userId1, but other restrictions remain
        Bundle user1Restrictions = mRestrictionsSet.getRestrictions(userId1);
        assertNotNull(user1Restrictions);
        assertFalse(user1Restrictions.containsKey(restrictionToRemove));
        assertTrue(user1Restrictions.containsKey(restrictionToKeep));

        // Verify all restrictions are removed for userId2, leaving an empty bundle.
        // The implementation does not remove the user entry if its bundle becomes empty.
        Bundle user2Restrictions = mRestrictionsSet.getRestrictions(userId2);
        assertNotNull(user2Restrictions);
        assertTrue(user2Restrictions.isEmpty());
    }

    @Test
    public void testRemoveRestrictionsForAllUsers_restrictionNotPresent() {
        // Verifies that no changes are made if the restriction to be removed is not present.
        final int userId1 = 0;
        final String restriction = UserManager.DISALLOW_CONFIG_DATE_TIME;
        mRestrictionsSet.updateRestrictions(userId1, newRestrictions(restriction));

        // Attempt to remove a different restriction
        assertFalse(mRestrictionsSet.removeRestrictionsForAllUsers(UserManager.ENSURE_VERIFY_APPS));

        // Verify original restrictions are untouched
        assertRestrictions(newRestrictions(restriction), mRestrictionsSet.getRestrictions(userId1));
    }

    @Test
    public void testMoveRestriction_containsRestriction() {
        RestrictionsSet destRestrictionsSet = new RestrictionsSet();

        String restriction = UserManager.DISALLOW_CONFIG_DATE_TIME;
        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(restriction));

        mRestrictionsSet.moveRestriction(destRestrictionsSet, restriction);

        assertNull(mRestrictionsSet.getRestrictions(originatingUserId));
        assertNotNull(destRestrictionsSet.getRestrictions(originatingUserId));
        assertRestrictions(newRestrictions(restriction),
                destRestrictionsSet.getRestrictions(originatingUserId));
    }

    @Test
    public void testMoveRestriction_doesNotContainRestriction() {
        RestrictionsSet destRestrictionsSet = new RestrictionsSet();

        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(UserManager.ENSURE_VERIFY_APPS));

        mRestrictionsSet.moveRestriction(destRestrictionsSet,
                UserManager.DISALLOW_CONFIG_DATE_TIME);

        assertRestrictions(newRestrictions(UserManager.ENSURE_VERIFY_APPS),
                mRestrictionsSet.getRestrictions(originatingUserId));
        assertNull(destRestrictionsSet.getRestrictions(originatingUserId));
    }

    @Test
    public void testIsEmpty_noRestrictions() {
        assertTrue(mRestrictionsSet.isEmpty());
    }

    @Test
    public void testIsEmpty_hasRestrictions() {
        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(UserManager.ENSURE_VERIFY_APPS,
                        UserManager.DISALLOW_CONFIG_DATE_TIME));

        assertFalse(mRestrictionsSet.isEmpty());
    }

    @Test
    public void testMergeAll_noRestrictions() {
        assertTrue(mRestrictionsSet.mergeAll().isEmpty());
    }

    @Test
    public void testMergeAll_hasRestrictions() {
        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(UserManager.ENSURE_VERIFY_APPS,
                        UserManager.DISALLOW_CONFIG_DATE_TIME));
        mRestrictionsSet.updateRestrictions(10,
                newRestrictions(UserManager.DISALLOW_ADD_USER,
                        UserManager.DISALLOW_AIRPLANE_MODE));

        Bundle actual = mRestrictionsSet.mergeAll();
        assertRestrictions(newRestrictions(UserManager.ENSURE_VERIFY_APPS,
                UserManager.DISALLOW_CONFIG_DATE_TIME, UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_AIRPLANE_MODE), actual);
    }

    @Test
    @Ignore("b/268334580")
    public void testGetEnforcingUsers_hasEnforcingUser() {
        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(UserManager.ENSURE_VERIFY_APPS));
        mRestrictionsSet.updateRestrictions(10,
                newRestrictions(UserManager.DISALLOW_ADD_USER));

        List<UserManager.EnforcingUser> enforcingUsers = mRestrictionsSet.getEnforcingUsers(
                UserManager.ENSURE_VERIFY_APPS, originatingUserId);

        UserManager.EnforcingUser enforcingUser1 = enforcingUsers.get(0);
        assertEquals(UserHandle.of(originatingUserId), enforcingUser1.getUserHandle());
        assertEquals(UserManager.RESTRICTION_SOURCE_DEVICE_OWNER,
                enforcingUser1.getUserRestrictionSource());
    }

    @Test
    @Ignore("b/268334580")
    public void testGetEnforcingUsers_hasMultipleEnforcingUsers() {
        int originatingUserId2 = 10;
        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(UserManager.ENSURE_VERIFY_APPS));
        mRestrictionsSet.updateRestrictions(originatingUserId2,
                newRestrictions(UserManager.ENSURE_VERIFY_APPS));

        List<UserManager.EnforcingUser> enforcingUsers = mRestrictionsSet.getEnforcingUsers(
                UserManager.ENSURE_VERIFY_APPS, originatingUserId);

        assertEquals(2, enforcingUsers.size());
        for (UserManager.EnforcingUser enforcingUser : enforcingUsers) {
            int userId = enforcingUser.getUserHandle().getIdentifier();
            assertTrue((userId == originatingUserId) || (userId == originatingUserId2));
            if (userId == originatingUserId) {
                assertEquals(UserManager.RESTRICTION_SOURCE_DEVICE_OWNER,
                        enforcingUser.getUserRestrictionSource());
            }
            if (userId == originatingUserId2) {
                assertEquals(UserManager.RESTRICTION_SOURCE_PROFILE_OWNER,
                        enforcingUser.getUserRestrictionSource());
            }
        }
    }

    @Test
    public void testGetEnforcingUsers_noEnforcingUsers() {
        mRestrictionsSet.updateRestrictions(originatingUserId,
                newRestrictions(UserManager.DISALLOW_USER_SWITCH));

        List<UserManager.EnforcingUser> enforcingUsers = mRestrictionsSet.getEnforcingUsers(
                UserManager.ENSURE_VERIFY_APPS, originatingUserId);

        assertTrue(enforcingUsers.isEmpty());
    }

    @Test
    public void testGetRestrictionsNonNull() {
        // Verifies getRestrictionsNonNull returns an empty bundle for a user with no restrictions.
        Bundle restrictions = mRestrictionsSet.getRestrictionsNonNull(originatingUserId);
        assertNotNull(restrictions);
        assertTrue(restrictions.isEmpty());

        // Verifies it returns the correct bundle for a user with restrictions.
        Bundle expectedRestrictions = newRestrictions(UserManager.ENSURE_VERIFY_APPS);
        mRestrictionsSet.updateRestrictions(originatingUserId, expectedRestrictions);
        restrictions = mRestrictionsSet.getRestrictionsNonNull(originatingUserId);
        assertRestrictions(expectedRestrictions, restrictions);
    }

    @Test
    public void testRemove() {
        // Verifies that restrictions for a specific user are removed.
        final int userId1 = 0;
        final int userId2 = 10;
        mRestrictionsSet.updateRestrictions(
                userId1, newRestrictions(UserManager.ENSURE_VERIFY_APPS));
        mRestrictionsSet.updateRestrictions(
                userId2, newRestrictions(UserManager.DISALLOW_ADD_USER));

        // Remove user1
        assertTrue(mRestrictionsSet.remove(userId1));

        // Verify user1 is removed and user2 remains
        assertNull(mRestrictionsSet.getRestrictions(userId1));
        assertNotNull(mRestrictionsSet.getRestrictions(userId2));
        assertEquals(1, mRestrictionsSet.size());
    }

    @Test
    public void testRemove_userNotPresent() {
        // Verifies that remove returns false for a user that doesn't exist.
        assertFalse(mRestrictionsSet.remove(originatingUserId));
    }

    @Test
    public void testRemoveAllRestrictions() {
        // Verifies that all restrictions for all users are removed.
        final int userId1 = 0;
        final int userId2 = 10;
        mRestrictionsSet.updateRestrictions(
                userId1, newRestrictions(UserManager.ENSURE_VERIFY_APPS));
        mRestrictionsSet.updateRestrictions(
                userId2, newRestrictions(UserManager.DISALLOW_ADD_USER));

        mRestrictionsSet.removeAllRestrictions();

        assertTrue(mRestrictionsSet.isEmpty());
        assertNull(mRestrictionsSet.getRestrictions(userId1));
        assertNull(mRestrictionsSet.getRestrictions(userId2));
    }

    @Test
    public void testGetUserIds() {
        // Verifies getUserIds returns the correct list of user IDs with restrictions.
        final int userId1 = 0;
        final int userId2 = 10;
        mRestrictionsSet.updateRestrictions(
                userId1, newRestrictions(UserManager.ENSURE_VERIFY_APPS));
        mRestrictionsSet.updateRestrictions(
                userId2, newRestrictions(UserManager.DISALLOW_ADD_USER));

        int[] userIds = mRestrictionsSet.getUserIds().toArray();
        // SparseArray keys are not guaranteed to be ordered.
        Arrays.sort(userIds);
        assertEquals(2, userIds.length);
        assertEquals(userId1, userIds[0]);
        assertEquals(userId2, userIds[1]);
    }
}
