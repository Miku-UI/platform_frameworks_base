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
package com.android.server.wm;

import static android.app.ActivityManager.START_CLASS_NOT_FOUND;
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.app.ActivityManager.START_SUCCESS;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.server.pm.GenericAllowlist.AllowlistStatus;
import static com.android.server.pm.UserActivitiesAllowlist.ALLOWLIST_MODE_ENABLED;
import static com.android.server.pm.UserActivitiesAllowlist.ALLOWLIST_MODE_DISABLED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.pm.UserActivitiesAllowlist;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityStarter.Request;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class UserHelperTest {

    private static final @UserIdInt int USER_ID = 007;
    private static final ComponentName COMP_NAME =
            ComponentName.createRelative("The.name.is.Bond", "James.Bond");

    private static final boolean STARTED = true;
    private static final boolean NOT_STARTED = false;

    private static final int INVALID_ALLOWLIST_MODE = 666;

    // The actual value "doesn't matter", as long as it's allowed or disallowed.
    private static final int STATUS_ALLOWED =
            UserActivitiesAllowlist.STATUS_ALLOWED_BY_PERMANENT_LIST;
    private static final int STATUS_DISALLOWED =
            UserActivitiesAllowlist.STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST;

    @Rule
    public final Expect expect = Expect.create();

    // TODO(b/456300837): switch back to MockitoRule once UserHelper caches the current user
    @Rule
    public final ExtendedMockitoRule extendedMockito =
            new ExtendedMockitoRule.Builder(this)
            .mockStatic(ActivityManager.class)
            .build();

    private final Request mRequest = new Request();

    @Mock
    private UserManagerInternal mMockUmi;

    @Mock
    private ActivityRecord mMockActivityRecord;

    @Mock
    private UserActivitiesAllowlist mMockHsuAllowlist;

    private UserHelper mUserHelper;

    @Before
    public void setFixtures() {
        mRequest.intent = new Intent().setComponent(COMP_NAME);
        mRequest.activityInfo = new ActivityInfo();
        mRequest.activityInfo.applicationInfo = new ApplicationInfo();

        // Currently, it only checks for if the current user is the HSU, so we're setting a
        // different userId in the request to make sure it's ignored
        setUserIdOnRequest(USER_ID);

        when(mMockHsuAllowlist.toString()).thenReturn("Schindler's"); // used on dump() tests

        // Sets default properties before setting the common fixture
        mockIsHsum(true);
        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_ENABLED);
        mockHsuActivityAllowlist(mMockHsuAllowlist);
        mockDefaultActivityAllowlistStatus(STATUS_DISALLOWED);
        mUserHelper = createUserHelper();
    }

    @Test
    public void testCheckRequest_nullInfo_failure() {
        mRequest.activityInfo = null;

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_CLASS_NOT_FOUND);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_nullComponentName_success() {
        mRequest.intent = new Intent();

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_allowListIsNull_success() {
        mockNoHsuActivitiesAllowlist();
        var userHelper = createUserHelper();

        expect.withMessage("checkRequest()")
                .that(userHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_notAllowlisted_failure() {
        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_NOT_ALLOWED_FOR_USER);

        verifyUmiLogDefaultActivityLaunchStatusForHsu(STATUS_DISALLOWED);
    }

    @Test
    public void testCheckRequest_notAllowlisted_successWhenDeviceIsNotHsum() {
        mockIsHsum(false);
        var userHelper = createUserHelper();

        expect.withMessage("checkRequest()")
                .that(userHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_notAllowlisted_successWhenCurrentUserIsNotHsu() {
        setCurrentUserId(USER_ID);

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_notAllowlisted_successWhenExplicitlyDisabled() {
        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_DISABLED);
        var userHelper = createUserHelper();

        expect.withMessage("checkRequest()")
                .that(userHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_notAllowlisted_successWhenInvalidMode() {
        mockHsuActivityAllowlistMode(INVALID_ALLOWLIST_MODE);
        var userHelper = createUserHelper();

        expect.withMessage("checkRequest()")
                .that(userHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testCheckRequest_whenAllowlistModeChangedFromDisabledToEnabled() {
        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_ENABLED);
        expect.withMessage("checkRequest() after mode set to enabled")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_NOT_ALLOWED_FOR_USER);

        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_DISABLED);
        expect.withMessage("checkRequest() after mode set to disabled")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);
    }

    @Test
    public void testCheckRequest_whenAllowlistModeChangedFromEnabledToDisabled() {
        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_DISABLED);
        expect.withMessage("checkRequest() after mode set to disabled")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_ENABLED);
        expect.withMessage("checkRequest() after mode set to enabled")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_NOT_ALLOWED_FOR_USER);
    }

    @Test
    public void testCheckRequest_allowlisted_success() {
        mockDefaultActivityAllowlisted();

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testLogStarted_notStarted() {
        mUserHelper.logActivityStarted(mMockActivityRecord, NOT_STARTED, STATUS_ALLOWED);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testLogStarted_started_dontLogWhenNotHsum() {
        mockIsHsum(false);
        var userHelper = createUserHelper();

        userHelper.logActivityStarted(mMockActivityRecord, STARTED, STATUS_ALLOWED);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testLogStarted_started_dontLogWhenNotHsu() {
        mUserHelper.logActivityStarted(USER_ID, COMP_NAME, STATUS_ALLOWED);

        verifyUmiLogActivityLaunchStatusNeverCalled();
    }

    @Test
    public void testLogStarted_started_logWhenNotHsu() {
        mUserHelper.logActivityStarted(USER_SYSTEM, COMP_NAME, STATUS_ALLOWED);

        verifyUmiLogDefaultActivityLaunchStatusForHsu(STATUS_ALLOWED);
    }

    @Test
    public void testGetUserId_nullActivityInfo() {
        expect.withMessage("getUserId()").that(UserHelper.getUserId(null)).isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetUserId_nullApplicationInfo() {
        ActivityInfo info = new ActivityInfo();

        expect.withMessage("getUserId()").that(UserHelper.getUserId(info)).isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetUserId() {
        var aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        setUserId(aInfo.applicationInfo, USER_ID);

        expect.withMessage("getUserId()").that(UserHelper.getUserId(aInfo)).isEqualTo(USER_ID);
    }

    @Test
    @android.platform.test.annotations.RequiresFlagsEnabled(
            android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testGetUserId_usesGetUid() {
        int pccUid = android.os.Process.FIRST_PCC_UID + 5;
        // Use user 10 to ensure different user ID from PCC (user 0)
        int appUid = UserHandle.getUid(10, android.os.Process.FIRST_APPLICATION_UID + 123);
        int pccUserId = UserHandle.getUserId(pccUid);
        int appUserId = UserHandle.getUserId(appUid);

        ActivityInfo aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.applicationInfo.uid = appUid;
        aInfo.applicationInfo.pccUid = pccUid;
        // Mark as PCC component so getUid() returns pccUid
        aInfo.flags |= ActivityInfo.FLAG_RUN_IN_PCC_SANDBOX;

        // Verify getUserId uses getUid() (PCC UID) instead of applicationInfo.uid
        expect.withMessage("getUserId() should return user ID from PCC UID")
                .that(UserHelper.getUserId(aInfo)).isEqualTo(pccUserId);
        expect.withMessage("getUserId() should NOT return user ID from App UID")
                .that(UserHelper.getUserId(aInfo)).isNotEqualTo(appUserId);
    }

    @Test
    public void testDump_hsum() throws Exception {
        String dump = dump(mUserHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=true
                      ...  activityLaunchIntegrationStatus=1 (ENABLED)
                      ...  mHsuActivitiesAllowlist=Schindler's
                      """);
    }

    @Test
    public void testDump_nonHsum() throws Exception {
        mockIsHsum(false);
        var userHelper = createUserHelper();

        String dump = dump(userHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=false
                      ...  activityLaunchIntegrationStatus=-1 (DISABLED_NOT_HSUM)
                      ...  mHsuActivitiesAllowlist=null
                      """);
    }

    @Test
    public void testDump_noAllowlist() throws Exception {
        mockNoHsuActivitiesAllowlist();
        var userHelper = createUserHelper();

        String dump = dump(userHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=true
                      ...  activityLaunchIntegrationStatus=-2 (DISABLED_NO_ALLOWLIST)
                      ...  mHsuActivitiesAllowlist=null
                      """);
    }

    @Test
    public void testDump_disabled() throws Exception {
        mockHsuActivityAllowlistMode(ALLOWLIST_MODE_DISABLED);
        var userHelper = createUserHelper();

        String dump = dump(userHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=true
                      ...  activityLaunchIntegrationStatus=-3 (DISABLED_EXPLICITLY)
                      ...  mHsuActivitiesAllowlist=Schindler's
                      """);
    }

    @Test
    public void testDump_invalidMode() throws Exception {
        mockHsuActivityAllowlistMode(INVALID_ALLOWLIST_MODE);
        var userHelper = createUserHelper();

        String dump = dump(userHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=true
                      ...  activityLaunchIntegrationStatus=-4 (DISABLED_INVALID_MODE)
                      ...  mHsuActivitiesAllowlist=Schindler's
                      """);
    }

    private void setUserIdOnRequest(@UserIdInt int userId) {
        setUserId(mRequest.activityInfo.applicationInfo, userId);
    }

    private void setUserId(ApplicationInfo applicationInfo, @UserIdInt int userId) {
        applicationInfo.uid = userId * UserHandle.PER_USER_RANGE + 666;
    }

    private UserHelper createUserHelper() {
        return new UserHelper(mMockUmi);
    }

    private void mockIsHsum(boolean hsum) {
        when(mMockUmi.isHeadlessSystemUserMode()).thenReturn(hsum);
    }

    private void mockHsuActivityAllowlist(UserActivitiesAllowlist allowlist) {
        when(mMockUmi.getActivitiesAllowlist(USER_SYSTEM)).thenReturn(allowlist);
    }

    private void mockNoHsuActivitiesAllowlist() {
        mockHsuActivityAllowlist(null);
    }

    private void mockHsuActivityAllowlistMode(int mode) {
        when(mMockHsuAllowlist.getMode()).thenReturn(mode);
    }

    private void mockDefaultActivityAllowlisted() {
        when(mMockHsuAllowlist.isAllowed(COMP_NAME)).thenReturn(true);
        mockDefaultActivityAllowlistStatus(STATUS_ALLOWED);
    }

    private void mockDefaultActivityAllowlistStatus(@AllowlistStatus int status) {
        when(mMockHsuAllowlist.getAllowlistStatus(COMP_NAME)).thenReturn(status);
    }

    private void setCurrentUserId(@UserIdInt int userId) {
        doReturn(userId).when(ActivityManager::getCurrentUser);
    }

    private void verifyUmiLogDefaultActivityLaunchStatusForHsu(@AllowlistStatus int status) {
        verify(mMockUmi).logActivityLaunchStatus(COMP_NAME, USER_SYSTEM, status);
    }

    private void verifyUmiLogActivityLaunchStatusNeverCalled() {
        verify(mMockUmi, never()).logActivityLaunchStatus(any(), anyInt(), anyInt());
    }

    private static String dump(UserHelper userHelper, String prefix) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            userHelper.dump(new PrintWriter(sw), prefix);
            return sw.toString();
        }
    }
}
