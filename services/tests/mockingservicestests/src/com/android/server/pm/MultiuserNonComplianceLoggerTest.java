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

import static android.os.UserHandle.USER_ALL;
import static android.os.UserHandle.USER_SYSTEM;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.pm.MultiuserNonComplianceLogger.PROP_SHOW_HSU_NOTIFICATION_TITLE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.pm.MultiuserNonComplianceLogger.HsuNotification;
import com.android.server.testutils.TestHandler;

import com.google.common.testing.EqualsTester;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.StringWriter;

public final class MultiuserNonComplianceLoggerTest {

    private static final String TAG = MultiuserNonComplianceLoggerTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final ExtendedMockitoRule extendedMockito = new ExtendedMockitoRule.Builder(this)
            .mockStatic(SystemProperties.class)
            .build();

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;

    private final Context mTargetContext = getInstrumentation().getTargetContext();
    private final TestHandler mHandler = new TestHandler(/* callback= */ null);
    private MultiuserNonComplianceLogger mLogger;

    @Before
    public void setFixtures() {
        mLogger = new MultiuserNonComplianceLogger(mMockContext, mHandler);

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @Test
    public void testEmptyDump() {
        assertPendingMessagesAndFlushHandler(0);

        expect.withMessage("dump() with no logs")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           0 notifications blocked on HSU

                           0 notifications posted on HSU
                           """);
    }

    @Test
    public void testLogMainUserCalls() {
        // Simulate shared UIDs.
        mockPackagesForUid(1000, new String[]{"pkg1", "pkg2"});
        mockPackagesForUid(10001, new String[]{"pkg3"});

        // Simulate the case where the package isn't installed for user 0.
        mockPackagesForUid(10002, null);

        mLogger.logGetMainUserCall(1000);
        mLogger.logIsMainUserCall(1000);
        mLogger.logIsMainUserCall(10001);
        mLogger.logIsMainUserCall(10001);
        mLogger.logIsMainUserCall(10002);
        assertPendingMessagesAndFlushHandler(5);

        expect.withMessage("dump() after logging some main user calls")
                .that(dump(mLogger))
                .isEqualTo("""
                           1 apps called getMainUser()
                             UID 1000 (shared): 1 calls

                           3 apps called isMainUser()
                             UID 1000 (shared): 1 calls
                             UID 10001 (pkg3): 2 calls
                             UID 10002 (unknown): 1 calls

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           0 notifications blocked on HSU

                           0 notifications posted on HSU
                           """);
    }

    @Test
    public void testLogBlockedAndPostedHsuNotifications_nullTitleAndTag() {
        Notification notification = new Notification.Builder(mTargetContext, "TEST")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        StatusBarNotification sbn1 = newStatusBarNotification("test.pkg", /* id= */ 42,
                /* tag= */ null, notification, USER_ALL);
        StatusBarNotification sbn2 = newStatusBarNotification("test.pkg", /* id= */ 666,
                /* tag= */ null, notification, USER_ALL);

        mLogger.logPostedHsuNotification(sbn1);
        mLogger.logBlockedHsuNotification(sbn2);

        assertPendingMessagesAndFlushHandler(2);
        expect.withMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           1 notifications blocked on HSU
                             [pkg=test.pkg, tag=null, id=666, targetUserId=-1, \
                           title=null, vis=PUBLIC, category=sys, channel=TEST]: 1 times

                           1 notifications posted on HSU
                             [pkg=test.pkg, tag=null, id=42, targetUserId=-1, title=null, \
                           vis=PUBLIC, category=sys, channel=TEST]: 1 times
                           """);
    }

    @Test
    public void testLogBlockedAndPostedHsuNotifications_titleRedacted() {
        mockSetPropertyShowTitle(false);
        Notification notification = new Notification.Builder(mTargetContext, "TEST")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        setTitle(notification, "Supreme Leader");
        StatusBarNotification sbn1 = newStatusBarNotification("test.pkg", /* id= */ 42, "TestTag",
                notification, USER_ALL);
        StatusBarNotification sbn2 = newStatusBarNotification("test.pkg", /* id= */ 666, "D'OH!",
                notification, USER_ALL);

        mLogger.logPostedHsuNotification(sbn1);
        mLogger.logBlockedHsuNotification(sbn2);

        assertPendingMessagesAndFlushHandler(2);
        assertWithMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           1 notifications blocked on HSU
                             [pkg=test.pkg, tag=D'OH!, id=666, targetUserId=-1, \
                           title=14_chars (redacted), vis=PUBLIC, category=sys, channel=TEST]: \
                           1 times

                           1 notifications posted on HSU
                             [pkg=test.pkg, tag=TestTag, id=42, targetUserId=-1, title=14_chars \
                           (redacted), vis=PUBLIC, category=sys, channel=TEST]: 1 times
                           """);
    }

    @Test
    public void testLogBlockedAndPostedHsuNotifications_titleNotRedacted() {
        mockSetPropertyShowTitle(true);

        Notification notification = new Notification.Builder(mTargetContext, "TEST")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        setTitle(notification, "Supreme Leader");
        StatusBarNotification sbn1 = newStatusBarNotification("test.pkg", /* id= */ 42, "TestTag",
                notification, USER_ALL);
        StatusBarNotification sbn2 = newStatusBarNotification("test.pkg", /* id= */ 666, "D'OH!",
                notification, USER_ALL);

        mLogger.logPostedHsuNotification(sbn1);
        mLogger.logBlockedHsuNotification(sbn2);

        assertPendingMessagesAndFlushHandler(2);
        assertWithMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           1 notifications blocked on HSU
                             [pkg=test.pkg, tag=D'OH!, id=666, targetUserId=-1, \
                           title=\"Supreme Leader\", vis=PUBLIC, category=sys, channel=TEST]: \
                           1 times

                           1 notifications posted on HSU
                             [pkg=test.pkg, tag=TestTag, id=42, targetUserId=-1, \
                           title=\"Supreme Leader\", vis=PUBLIC, category=sys, channel=TEST]: \
                           1 times
                           """);
    }

    @Test
    public void testLogBlockedAndLaunchedHsuActivities() {
        mLogger.logBlockedHsuActivity(ComponentName.createRelative("some.pkg", ".SomeActivity"));
        mLogger.logLaunchedHsuActivity(
                ComponentName.createRelative("some.pkg", ".AwesomeActivity"));

        assertPendingMessagesAndFlushHandler(2);
        expect.withMessage("dump() after logging blocked and launched activities on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           1 activities blocked on HSU
                             some.pkg/.SomeActivity: 1 times

                           1 activities launched on HSU
                             some.pkg/.AwesomeActivity: 1 times

                           0 notifications blocked on HSU

                           0 notifications posted on HSU
                           """);
    }

    @Test
    public void testLogEverythingMultipleTimes() {
        mockSetPropertyShowTitle(true);
        String app1 = "life.universe.and.everything";
        String app2 = "the.package.of.the.beast";

        mockPackagesForUid(42, new String[]{ app1 });
        mockPackagesForUid(666, new String[]{ app2 });

        mLogger.logGetMainUserCall(42);
        mLogger.logGetMainUserCall(42);
        mLogger.logGetMainUserCall(666);
        mLogger.logGetMainUserCall(666);
        mLogger.logGetMainUserCall(666);
        int getMainUserCalls = 5;

        mLogger.logIsMainUserCall(42);
        mLogger.logIsMainUserCall(42);
        mLogger.logIsMainUserCall(42);
        mLogger.logIsMainUserCall(666);
        mLogger.logIsMainUserCall(666);
        int isMainUserCalls = 5;

        ComponentName app1GoodActivity = ComponentName.createRelative(app1, ".you.re.good");
        ComponentName app1BadActivity = ComponentName.createRelative(app1, ".no.can.do");
        ComponentName app2GoodActivity = ComponentName.createRelative(app2, ".you.re.good");
        ComponentName app2BadActivity = ComponentName.createRelative(app2, ".no.can.do");

        // launched app2 first because it's package name (the..) is higher than app1 (life...)
        mLogger.logBlockedHsuActivity(app2BadActivity);
        mLogger.logLaunchedHsuActivity(app2GoodActivity);
        mLogger.logBlockedHsuActivity(app2BadActivity);
        mLogger.logLaunchedHsuActivity(app2GoodActivity);

        mLogger.logLaunchedHsuActivity(app1GoodActivity);
        mLogger.logLaunchedHsuActivity(app1GoodActivity);
        mLogger.logBlockedHsuActivity(app1BadActivity);
        mLogger.logBlockedHsuActivity(app1BadActivity);
        mLogger.logLaunchedHsuActivity(app1BadActivity);

        // this will be the 4th entry
        mLogger.logLaunchedHsuActivity(app2BadActivity);
        int activitiesCalls = 10;

        Notification notification = new Notification.Builder(mTargetContext, "English")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        setTitle(notification, "Supreme Leader");
        StatusBarNotification app1GoodNotif = newStatusBarNotification(app1, /* id= */ 42, "Taggo",
                notification, USER_SYSTEM);
        StatusBarNotification app1BadNotif = newStatusBarNotification(app1, /* id= */ 666, "D'OH!",
                notification, USER_ALL);
        StatusBarNotification app2GoodNotif = newStatusBarNotification(app2, /* id= */ 42, "Taggo",
                notification, USER_SYSTEM);
        StatusBarNotification app2BadNotif = newStatusBarNotification(app2, /* id= */ 666, "D'OH!",
                notification, USER_ALL);

        // post from app2 first because it's package name (the..) is higher than app1 (life...)
        mLogger.logBlockedHsuNotification(app2BadNotif);
        mLogger.logPostedHsuNotification(app2GoodNotif);
        mLogger.logBlockedHsuNotification(app2BadNotif);
        mLogger.logPostedHsuNotification(app2GoodNotif);

        mLogger.logPostedHsuNotification(app1GoodNotif);
        mLogger.logPostedHsuNotification(app1GoodNotif);
        mLogger.logBlockedHsuNotification(app1BadNotif);
        mLogger.logBlockedHsuNotification(app1BadNotif);
        mLogger.logPostedHsuNotification(app1BadNotif);

        // this will be the 4th entry
        mLogger.logPostedHsuNotification(app2BadNotif);
        int notificationsCalls = 10;

        assertPendingMessagesAndFlushHandler(
                getMainUserCalls + isMainUserCalls + activitiesCalls + notificationsCalls);
        assertWithMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           2 apps called getMainUser()
                             UID 42 (life.universe.and.everything): 2 calls
                             UID 666 (the.package.of.the.beast): 3 calls

                           2 apps called isMainUser()
                             UID 42 (life.universe.and.everything): 3 calls
                             UID 666 (the.package.of.the.beast): 2 calls

                           2 activities blocked on HSU
                             the.package.of.the.beast/.no.can.do: 2 times
                             life.universe.and.everything/.no.can.do: 2 times

                           4 activities launched on HSU
                             the.package.of.the.beast/.you.re.good: 2 times
                             life.universe.and.everything/.you.re.good: 2 times
                             life.universe.and.everything/.no.can.do: 1 times
                             the.package.of.the.beast/.no.can.do: 1 times

                           2 notifications blocked on HSU
                             [pkg=the.package.of.the.beast, tag=D'OH!, id=666, \
                           targetUserId=-1, title=\"Supreme Leader\", vis=PUBLIC, category=sys, \
                           channel=English]: 2 times
                             [pkg=life.universe.and.everything, tag=D'OH!, id=666, \
                           targetUserId=-1, title=\"Supreme Leader\", vis=PUBLIC, category=sys, \
                           channel=English]: 2 times

                           4 notifications posted on HSU
                             [pkg=the.package.of.the.beast, tag=Taggo, id=42, targetUserId=0, \
                           title=\"Supreme Leader\", vis=PUBLIC, category=sys, \
                           channel=English]: 2 times
                             [pkg=life.universe.and.everything, tag=Taggo, id=42, targetUserId=0, \
                           title=\"Supreme Leader\", vis=PUBLIC, category=sys, \
                           channel=English]: 2 times
                             [pkg=life.universe.and.everything, tag=D'OH!, id=666, \
                           targetUserId=-1, title=\"Supreme Leader\", vis=PUBLIC, category=sys, \
                           channel=English]: 1 times
                             [pkg=the.package.of.the.beast, tag=D'OH!, id=666, targetUserId=-1, \
                           title=\"Supreme Leader\", vis=PUBLIC, category=sys, \
                           channel=English]: 1 times
                           """);
    }

    @Test
    public void testReset() {
        mLogger.logGetMainUserCall(1000);
        mLogger.logIsMainUserCall(1000);

        ComponentName activity = ComponentName.createRelative("some.pkg", ".SomeActivity");
        mLogger.logBlockedHsuActivity(activity);
        mLogger.logLaunchedHsuActivity(activity);

        Notification notification = new Notification.Builder(mTargetContext, "TEST").build();
        StatusBarNotification sbn = newStatusBarNotification("pkg", 42, "Tag", notification, 666);
        mLogger.logBlockedHsuNotification(sbn);
        mLogger.logPostedHsuNotification(sbn);

        assertPendingMessagesAndFlushHandler(6);

        mLogger.reset();

        assertPendingMessagesAndFlushHandler(0);
        expect.withMessage("dump() after reset()")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           0 notifications blocked on HSU

                           0 notifications posted on HSU
                           """);
    }

    // TODO(b/414326600): move to its own test class
    @Test
    public void testHsuNotifications_equalsHashcode() {
        mockSetPropertyShowTitle(true);
        var withoutTitle1 = new HsuNotification("pkg", "tag", 42, 666, 108, null, true, "category",
                "channel");
        var withoutTitle2 = new HsuNotification("pkg", "tag", 42, 666, 108, null, true, "category",
                "channel");
        var withTitle1 = new HsuNotification("pkg", "tag", 42, 666, 108, "title", true, "category",
                "channel");
        var withTitle2 = new HsuNotification("pkg", "tag", 42, 666, 108, "title", true, "category",
                "channel");
        // Redacted should be instantiated exactly as "withTitle" - the title will be null, though.
        mockSetPropertyShowTitle(false);
        var redacted1 = new HsuNotification("pkg", "tag", 42, 666, 108, "title", true, "category",
                "channel");
        var redacted2 = new HsuNotification("pkg", "tag", 42, 666, 108, "title", true, "category",
                "channel");

        new EqualsTester()
                .addEqualityGroup(withTitle1, withTitle2)
                .addEqualityGroup(withoutTitle1, withoutTitle2)
                .addEqualityGroup(redacted1, redacted2)
                .testEquals();
    }

    private String dump(MultiuserNonComplianceLogger logger) {
        try {
            try (StringWriter sw = new StringWriter()) {
                logger.dump(new IndentingPrintWriter(sw));
                String dump = sw.toString();
                if (DEBUG) {
                    Log.v(TAG, "dump():\n" + dump);
                }
                return dump;
            }
        } catch (IOException e) {
            fail("Failed to call dump() on logger: " + e);
            return "";
        }
    }

    private void mockPackagesForUid(int uid, @Nullable String[] packages) {
        when(mMockPackageManager.getPackagesForUid(uid)).thenReturn(packages);
    }

    private static void setTitle(Notification notification, CharSequence title) {
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, title);
    }

    private static StatusBarNotification newStatusBarNotification(String pkg, int id, String tag,
            Notification notification, @UserIdInt int userId) {
        String opPkg = null;
        int uid = Process.myUid();
        int pid = Process.myPid();
        UserHandle user = UserHandle.of(userId);
        String overrideGroupKey = null;
        long postTime = System.currentTimeMillis();

        return new StatusBarNotification(pkg, opPkg, id, tag, uid, pid, notification, user,
                overrideGroupKey, postTime);
    }

    private static void mockGetSystemProperty(String name, boolean defaultValue,
            boolean mockedValue) {
        doReturn(mockedValue).when(() -> SystemProperties.getBoolean(name, defaultValue));
    }

    private static void mockSetPropertyShowTitle(boolean value) {
        // NOTE: it's explicitly passing the default value as false - instead of anyBoolean() - to
        // make sure it's refacting by default.
        mockGetSystemProperty(PROP_SHOW_HSU_NOTIFICATION_TITLE, /* def= */ false, value);
    }

    private void assertPendingMessagesAndFlushHandler(int expectedNumberOfMessages) {
        expect.withMessage("number of pending messages on handler")
                .that(mHandler.getPendingMessages())
                .hasSize(expectedNumberOfMessages);
        mHandler.flush();
    }
}
