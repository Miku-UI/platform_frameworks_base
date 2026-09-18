/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.os;

import static android.os.UserHandle.ERR_GID;
import static android.os.UserHandle.getAppId;
import static android.os.UserHandle.getCacheAppGid;
import static android.os.UserHandle.getSharedAppGid;
import static android.os.UserHandle.getUid;
import static android.os.UserHandle.getUserId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class UserHandleTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // NOTE: keep logic in sync with system/core/libcutils/tests/multiuser_test.cpp

    @Test
    public void testMerge() throws Exception {
        EXPECT_EQ(0, multiuser_get_uid(0, 0));
        EXPECT_EQ(1000, multiuser_get_uid(0, 1000));
        EXPECT_EQ(10000, multiuser_get_uid(0, 10000));
        EXPECT_EQ(50000, multiuser_get_uid(0, 50000));
        EXPECT_EQ(1000000, multiuser_get_uid(10, 0));
        EXPECT_EQ(1001000, multiuser_get_uid(10, 1000));
        EXPECT_EQ(1010000, multiuser_get_uid(10, 10000));
        EXPECT_EQ(1050000, multiuser_get_uid(10, 50000));
    }

    @Test
    public void testSplitUser() throws Exception {
        EXPECT_EQ(0, multiuser_get_user_id(0));
        EXPECT_EQ(0, multiuser_get_user_id(1000));
        EXPECT_EQ(0, multiuser_get_user_id(10000));
        EXPECT_EQ(0, multiuser_get_user_id(50000));
        EXPECT_EQ(10, multiuser_get_user_id(1000000));
        EXPECT_EQ(10, multiuser_get_user_id(1001000));
        EXPECT_EQ(10, multiuser_get_user_id(1010000));
        EXPECT_EQ(10, multiuser_get_user_id(1050000));
    }

    @Test
    public void testSplitApp() throws Exception {
        EXPECT_EQ(0, multiuser_get_app_id(0));
        EXPECT_EQ(1000, multiuser_get_app_id(1000));
        EXPECT_EQ(10000, multiuser_get_app_id(10000));
        EXPECT_EQ(50000, multiuser_get_app_id(50000));
        EXPECT_EQ(0, multiuser_get_app_id(1000000));
        EXPECT_EQ(1000, multiuser_get_app_id(1001000));
        EXPECT_EQ(10000, multiuser_get_app_id(1010000));
        EXPECT_EQ(50000, multiuser_get_app_id(1050000));
    }

    @Test
    public void testCache() throws Exception {
        EXPECT_EQ(ERR_GID, multiuser_get_cache_gid(0, 0));
        EXPECT_EQ(ERR_GID, multiuser_get_cache_gid(0, 1000));
        EXPECT_EQ(20000, multiuser_get_cache_gid(0, 10000));
        EXPECT_EQ(ERR_GID, multiuser_get_cache_gid(0, 50000));
        EXPECT_EQ(ERR_GID, multiuser_get_cache_gid(10, 0));
        EXPECT_EQ(ERR_GID, multiuser_get_cache_gid(10, 1000));
        EXPECT_EQ(1020000, multiuser_get_cache_gid(10, 10000));
        EXPECT_EQ(ERR_GID, multiuser_get_cache_gid(10, 50000));

        // PCC UIDs
        EXPECT_EQ(60000, multiuser_get_cache_gid(0, 30000));
        EXPECT_EQ(1060000, multiuser_get_cache_gid(10, 30000));
        EXPECT_EQ(69999, multiuser_get_cache_gid(0, 39999));
        EXPECT_EQ(1069999, multiuser_get_cache_gid(10, 39999));
    }

    @Test
    public void testPccCache() throws Exception {
        assertEquals(60000, UserHandle.getCacheAppGid(0, 30000));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsSameAppIdWithPcc_identicalAppIds() {
        int appUid = Process.FIRST_APPLICATION_UID + 123;
        int pccUid = Process.FIRST_PCC_UID + 123;

        assertTrue(UserHandle.isSameAppIdWithPcc(appUid, appUid));
        assertTrue(UserHandle.isSameApp(appUid, appUid));
        assertTrue(UserHandle.isSameAppIdWithPcc(pccUid, pccUid));
        assertTrue(UserHandle.isSameApp(pccUid, pccUid));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsSameAppIdWithPcc_pccSupportCrossChecks() {
        int appUid = Process.FIRST_APPLICATION_UID + 123;
        int pccUid = Process.FIRST_PCC_UID + 123;

        // Cross-checks without PCC support
        assertFalse(UserHandle.isSameApp(appUid, pccUid));
        assertFalse(UserHandle.isSameApp(pccUid, appUid));

        // Cross-checks with PCC support
        assertTrue(UserHandle.isSameAppIdWithPcc(appUid, pccUid));
        assertTrue(UserHandle.isSameAppIdWithPcc(pccUid, appUid));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsSameAppIdWithPcc_differentAppIds() {
        int appUid1 = Process.FIRST_APPLICATION_UID + 123;
        int pccUid1 = Process.FIRST_PCC_UID + 123;
        int appUid2 = Process.FIRST_APPLICATION_UID + 456;
        int pccUid2 = Process.FIRST_PCC_UID + 456;

        assertFalse(UserHandle.isSameAppIdWithPcc(appUid1, appUid2));
        assertFalse(UserHandle.isSameAppIdWithPcc(appUid1, pccUid2));
        assertFalse(UserHandle.isSameAppIdWithPcc(pccUid1, appUid2));
        assertFalse(UserHandle.isSameAppIdWithPcc(pccUid1, pccUid2));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsSameAppIdWithPcc_differentUsersSameApp() {
        int appUid1 = Process.FIRST_APPLICATION_UID + 123;
        int pccUid1 = Process.FIRST_PCC_UID + 123;
        int u10App1 = UserHandle.getUid(10, appUid1);
        int u10Pcc1 = UserHandle.getUid(10, pccUid1);

        assertTrue(UserHandle.isSameAppIdWithPcc(appUid1, u10App1));
        assertTrue(UserHandle.isSameAppIdWithPcc(appUid1, u10Pcc1));
        assertTrue(UserHandle.isSameAppIdWithPcc(pccUid1, u10App1));
        assertTrue(UserHandle.isSameAppIdWithPcc(pccUid1, u10Pcc1));
    }

    @Test
    @RequiresFlagsDisabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testIsSameAppIdWithPcc_flagDisabled() {
        int appUid1 = Process.FIRST_APPLICATION_UID + 123;
        int pccUid1 = Process.FIRST_PCC_UID + 123;

        // Cross-checks with PCC support but flag is disabled
        assertFalse(UserHandle.isSameAppIdWithPcc(appUid1, pccUid1));
        assertFalse(UserHandle.isSameAppIdWithPcc(pccUid1, appUid1));
    }

    @Test
    public void testShared() throws Exception {
        EXPECT_EQ(0, multiuser_get_shared_gid(0, 0));
        EXPECT_EQ(1000, multiuser_get_shared_gid(0, 1000));
        EXPECT_EQ(50000, multiuser_get_shared_gid(0, 10000));
        EXPECT_EQ(ERR_GID, multiuser_get_shared_gid(0, 50000));
        EXPECT_EQ(0, multiuser_get_shared_gid(10, 0));
        EXPECT_EQ(1000, multiuser_get_shared_gid(10, 1000));
        EXPECT_EQ(50000, multiuser_get_shared_gid(10, 10000));
        EXPECT_EQ(ERR_GID, multiuser_get_shared_gid(10, 50000));
    }

    private static void EXPECT_EQ(int expected, int actual) {
        assertEquals(expected, actual);
    }

    private static int multiuser_get_uid(int userId, int appId) {
        return getUid(userId, appId);
    }

    private static int multiuser_get_cache_gid(int userId, int appId) {
        return getCacheAppGid(userId, appId);
    }

    private static int multiuser_get_shared_gid(int userId, int appId) {
        return getSharedAppGid(userId, appId);
    }

    private static int multiuser_get_user_id(int uid) {
        return getUserId(uid);
    }

    private static int multiuser_get_app_id(int uid) {
        return getAppId(uid);
    }

    @Test
    public void testExtraCache() {
        assertEquals(0, UserHandle.sExtraUserHandleCache.size());

        // This shouldn't hit the extra cache.
        assertEquals(10, UserHandle.of(10).getIdentifier());

        assertEquals(0, UserHandle.sExtraUserHandleCache.size());

        // This should hit the cache
        assertEquals(20, UserHandle.of(20).getIdentifier());

        assertEquals(1, UserHandle.sExtraUserHandleCache.size());

        // Make sure the cache works.
        final Random rnd = new Random();
        for (int i = 0; i < 10000; i++) {
            final int userId = rnd.nextInt(100);
            assertEquals(userId, UserHandle.of(userId).getIdentifier());
            assertSame(UserHandle.of(userId), UserHandle.of(userId));
        }

        // Make sure the cache size doesn't exceed the max size.
        assertEquals(UserHandle.MAX_EXTRA_USER_HANDLE_CACHE_SIZE,
                UserHandle.sExtraUserHandleCache.size());
    }
}
