/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.activity;

import static android.app.ActivityThread.shouldReportChange;
import static android.app.servertransaction.ActivityLifecycleItem.ON_CREATE;
import static android.app.servertransaction.ActivityLifecycleItem.ON_DESTROY;
import static android.app.servertransaction.ActivityLifecycleItem.ON_PAUSE;
import static android.app.servertransaction.ActivityLifecycleItem.ON_RESUME;
import static android.app.servertransaction.ActivityLifecycleItem.ON_START;
import static android.app.servertransaction.ActivityLifecycleItem.ON_STOP;
import static android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
import static android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.ActivityClient;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.ActivityThread.ActivityClientRecord;
import android.app.ClientTransactionHandler;
import android.app.ContentProviderHolder;
import android.app.IActivityManager;
import android.app.LoadedApk;
import android.app.servertransaction.DestroyActivityItem;
import android.app.servertransaction.PendingTransactionActions;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.PollingCheck;
import android.util.ArrayMap;
import android.view.WindowManagerGlobal;
import android.window.ActivityWindowInfo;
import android.window.SizeConfigurationBuckets;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.TimeUnit;

/**
 * Test for verifying {@link android.app.ActivityThread} class.
 *
 * <p>Build/Install/Run:
 *  atest FrameworksMockingCoreTests:android.app.activity.ActivityThreadClientTest
 *
 * <p>This test class is a part of Window Manager Service tests and specified in
 * {@link com.android.server.wm.test.filters.FrameworksTestsFilter}.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
@Presubmit
public class ActivityThreadClientTest {
    private static final long WAIT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @UiThreadTest
    public void testLifecycleAfterFinished_OnCreate() throws Exception {
        try (ClientMockSession clientSession = new ClientMockSession()) {
            ActivityClientRecord r = clientSession.stubActivityRecord();

            Activity activity = clientSession.launchActivity(r);
            activity.finish();
            assertEquals(ON_CREATE, r.getLifecycleState());

            clientSession.startActivity(r);
            assertEquals(ON_CREATE, r.getLifecycleState());

            clientSession.resumeActivity(r);
            assertEquals(ON_CREATE, r.getLifecycleState());

            clientSession.pauseActivity(r);
            assertEquals(ON_CREATE, r.getLifecycleState());

            clientSession.stopActivity(r);
            assertEquals(ON_CREATE, r.getLifecycleState());

            clientSession.destroyActivity(r);
            assertEquals(ON_DESTROY, r.getLifecycleState());
        }
    }

    @Test
    @UiThreadTest
    public void testLifecycleAfterFinished_OnStart() throws Exception {
        try (ClientMockSession clientSession = new ClientMockSession()) {
            ActivityClientRecord r = clientSession.stubActivityRecord();

            Activity activity = clientSession.launchActivity(r);
            clientSession.startActivity(r);
            activity.finish();
            assertEquals(ON_START, r.getLifecycleState());

            clientSession.resumeActivity(r);
            assertEquals(ON_START, r.getLifecycleState());

            clientSession.pauseActivity(r);
            assertEquals(ON_START, r.getLifecycleState());

            clientSession.stopActivity(r);
            assertEquals(ON_STOP, r.getLifecycleState());

            clientSession.destroyActivity(r);
            assertEquals(ON_DESTROY, r.getLifecycleState());
        }
    }

    @Test
    @UiThreadTest
    public void testLifecycleAfterFinished_OnResume() throws Exception {
        try (ClientMockSession clientSession = new ClientMockSession()) {
            ActivityClientRecord r = clientSession.stubActivityRecord();

            Activity activity = clientSession.launchActivity(r);
            clientSession.startActivity(r);
            clientSession.resumeActivity(r);
            activity.finish();
            assertEquals(ON_RESUME, r.getLifecycleState());

            clientSession.pauseActivity(r);
            assertEquals(ON_PAUSE, r.getLifecycleState());

            clientSession.stopActivity(r);
            assertEquals(ON_STOP, r.getLifecycleState());

            clientSession.destroyActivity(r);
            assertEquals(ON_DESTROY, r.getLifecycleState());
        }
    }

    @Test
    public void testLifecycleOfRelaunch() throws Exception {
        try (ClientMockSession clientSession = new ClientMockSession()) {
            ActivityThread activityThread = clientSession.mockThread();
            ActivityClientRecord r = clientSession.stubActivityRecord();
            final TestActivity[] activity = new TestActivity[1];

            // Verify for ON_CREATE state. Activity should not be relaunched.
            getInstrumentation().runOnMainSync(() -> {
                activity[0] = (TestActivity) clientSession.launchActivity(r);
            });
            recreateAndVerifyNoRelaunch(activityThread, activity[0]);

            // Verify for ON_START state. Activity should be relaunched.
            getInstrumentation().runOnMainSync(() -> clientSession.startActivity(r));
            recreateAndVerifyRelaunched(activityThread, activity[0], r, ON_PAUSE);

            // Verify for ON_RESUME state. Activity should be relaunched.
            getInstrumentation().runOnMainSync(() -> clientSession.resumeActivity(r));
            recreateAndVerifyRelaunched(activityThread, activity[0], r, ON_RESUME);

            // Verify for ON_PAUSE state. Activity should be relaunched.
            getInstrumentation().runOnMainSync(() -> clientSession.pauseActivity(r));
            recreateAndVerifyRelaunched(activityThread, activity[0], r, ON_PAUSE);

            // Verify for ON_STOP state. Activity should be relaunched.
            getInstrumentation().runOnMainSync(() -> clientSession.stopActivity(r));
            recreateAndVerifyRelaunched(activityThread, activity[0], r, ON_STOP);

            // Verify for ON_DESTROY state. Activity should not be relaunched.
            getInstrumentation().runOnMainSync(() -> clientSession.destroyActivity(r));
            recreateAndVerifyNoRelaunch(activityThread, activity[0]);
        }
    }

    @Test
    public void testShouldReportChange() {
        final Configuration newConfig = new Configuration();
        final Configuration currentConfig = new Configuration();

        assertFalse("Must not report change if no public diff",
                shouldReportChange(currentConfig, newConfig, null /* sizeBuckets */,
                        0 /* handledConfigChanges */, false /* alwaysReportChange */));

        final int[] verticalThresholds = {100, 400};
        final SizeConfigurationBuckets buckets = new SizeConfigurationBuckets(
                null /* horizontal */,
                verticalThresholds,
                null /* smallest */,
                null /* screenLayoutSize */,
                false /* screenLayoutLongSet */);
        currentConfig.screenHeightDp = 200;
        newConfig.screenHeightDp = 300;

        assertFalse("Must not report changes if the diff is small and not handled",
                shouldReportChange(currentConfig, newConfig, buckets,
                        CONFIG_FONT_SCALE /* handledConfigChanges */,
                        false /* alwaysReportChange */));

        assertTrue("Must report changes if the small diff is handled",
                shouldReportChange(currentConfig, newConfig, buckets,
                        CONFIG_SCREEN_SIZE /* handledConfigChanges */,
                        false /* alwaysReportChange */));

        assertTrue("Must report changes if it should, even it is small and not handled",
                shouldReportChange(currentConfig, newConfig, buckets,
                        CONFIG_FONT_SCALE /* handledConfigChanges */,
                        true /* alwaysReportChange */));

        currentConfig.fontScale = 0.8f;
        newConfig.fontScale = 1.2f;

        assertTrue("Must report handled changes regardless of small unhandled change",
                shouldReportChange(currentConfig, newConfig, buckets,
                        CONFIG_FONT_SCALE /* handledConfigChanges */,
                        false /* alwaysReportChange */));

        newConfig.screenHeightDp = 500;

        assertFalse("Must not report changes if there's unhandled big changes",
                shouldReportChange(currentConfig, newConfig, buckets,
                        CONFIG_FONT_SCALE /* handledConfigChanges */,
                        false /* alwaysReportChange */));
    }

    @Test
    public void testUpdateDeviceIdForNonUIContexts_nullProviderContext() {
        try (ClientMockSession clientSession = new ClientMockSession()) {
            ActivityThread activityThread = ActivityThread.currentActivityThread();
            final int testDeviceId = 1;

            // Create a mock ContentProvider that returns a null context
            ContentProvider mockProvider = mock(ContentProvider.class);
            doReturn(null).when(mockProvider).getContext();

            // Create a ProviderClientRecord with the mock provider
            ActivityThread.ProviderClientRecord providerClientRecord =
                    new ActivityThread.ProviderClientRecord(
                            new String[]{"com.android.test.provider"},
                            mock(IContentProvider.class),
                            mockProvider,
                            mock(ContentProviderHolder.class));

            final IBinder keyBinder = new Binder();
            activityThread.mLocalProviders.put(keyBinder, providerClientRecord);

            try {
                activityThread.updateDeviceIdForNonUIContexts(testDeviceId);
            } finally {
                activityThread.mLocalProviders.remove(keyBinder);
            }
        }
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_SINGLE_USER_PROVIDER_CACHE_FIX)
    public void testContentProvider_acquireSingleUserProvider() throws Exception {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        // Here we specify the received content provider is running under a
        // different user (user 0) than the one that was requested for (user 10).
        applicationInfo.uid = UserHandle.getUid(0 /* userId */, 23456 /* appId */);

        final ProviderInfo info = new ProviderInfo();
        info.applicationInfo = applicationInfo;
        info.authority = "fakecontentprovider";
        info.name = "com.example.fakecontentprovider";
        // Since this is a single-user provider, it should be cached regardless
        // of which user requested it.
        info.flags = ProviderInfo.FLAG_SINGLE_USER;

        final ContentProviderHolder holder = new ContentProviderHolder(info);

        holder.connection = mock(IBinder.class);
        holder.provider = mock(IContentProvider.class);

        final IBinder binderObj = mock(IBinder.class);
        when(holder.provider.asBinder()).thenReturn(binderObj);
        when(binderObj.isBinderAlive()).thenReturn(true);

        IActivityManager activityManager = ActivityManager.getService();
        spyOn(activityManager);

        doReturn(holder)
                .when(activityManager)
                .getContentProvider(any(), any(), eq("fakecontentprovider"),
                        eq(10) /* userId */, eq(false) /* stable */);
        doNothing()
                .when(activityManager)
                .removeContentProvider(any(), anyBoolean());

        final Context context = mock(Context.class);

        final ActivityThread activityThread = ActivityThread.currentActivityThread();

        // Acquire the provider first to make sure it is cached.
        IContentProvider resultProvider =
                activityThread.acquireProvider(context, "fakecontentprovider",
                        10 /* userId */, false /* stable */);

        assertSame(holder.provider, resultProvider);

        // The previously cached single-user provider should be returned, even
        // though it is being requested from a user different from the one it
        // is running under.
        resultProvider =
            activityThread.acquireExistingProvider(context, "fakecontentprovider",
                    10 /* userId */, false /* stable */);

        assertSame(holder.provider, resultProvider);

        // Need two release the provider twice since we acquired it twice.
        assertTrue(activityThread.releaseProvider(holder.provider, false /* stable */));
        assertTrue(activityThread.releaseProvider(holder.provider, false /* stable */));

        // Wait for the content provider to be released.
        verify(activityManager, timeout(3000))
                .removeContentProvider(eq(holder.connection), anyBoolean());
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_SINGLE_USER_PROVIDER_CACHE_FIX)
    public void testContentProvider_acquireRegularContentProvider_notVisibleAcrossUsers()
            throws Exception {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        // The provider is running under user 10.
        applicationInfo.uid = UserHandle.getUid(10 /* userId */, 23456 /* appId */);

        final ProviderInfo info = new ProviderInfo();
        info.applicationInfo = applicationInfo;
        info.authority = "fakecontentprovider";
        info.name = "com.example.fakecontentprovider";
        // This is a regular content provider, so FLAG_SINGLE_USER is not set.

        final ContentProviderHolder holder = new ContentProviderHolder(info);

        holder.connection = mock(IBinder.class);
        holder.provider = mock(IContentProvider.class);

        final IBinder binderObj = mock(IBinder.class);
        when(holder.provider.asBinder()).thenReturn(binderObj);
        when(binderObj.isBinderAlive()).thenReturn(true);

        IActivityManager activityManager = ActivityManager.getService();
        spyOn(activityManager);

        doReturn(holder)
                .when(activityManager)
                .getContentProvider(any(), any(), eq("fakecontentprovider"),
                        eq(10) /* userId */, eq(false) /* stable */);
        doNothing()
                .when(activityManager)
                .removeContentProvider(any(), anyBoolean());

        final Context context = mock(Context.class);

        final ActivityThread activityThread = ActivityThread.currentActivityThread();

        // This will cache it for user 10.
        IContentProvider resultProvider =
                activityThread.acquireProvider(context, "fakecontentprovider",
                        10 /* userId */, false /* stable */);

        assertSame(holder.provider, resultProvider);

        // Attempt to acquire the same provider from user 0.
        resultProvider =
                activityThread.acquireExistingProvider(context, "fakecontentprovider",
                        0 /* userId */, false /* stable */);

        // Verify that the provider was not found in the cache for user 0.
        assertEquals(null, resultProvider);

        // Attempt to acquire the same provider from the cache as USER_ALL.
        resultProvider =
                activityThread.acquireExistingProvider(context, "fakecontentprovider",
                        UserHandle.USER_ALL, false /* stable */);

        // Verify that the provider was not found in the cache for USER_ALL.
        assertEquals(null, resultProvider);

        assertTrue(activityThread.releaseProvider(holder.provider, false /* stable */));

        // Wait for the content provider to be released.
        verify(activityManager, timeout(3000))
                .removeContentProvider(eq(holder.connection), anyBoolean());
    }

    @Test
    public void testDestroyActivityItem_postExecute_notifyWMS() {
        try (ClientMockSession clientSession = new ClientMockSession()) {
            final IBinder activityToken = new Binder();
            final ClientTransactionHandler client = mock(ClientTransactionHandler.class);
            doReturn(new ArrayMap<IBinder, DestroyActivityItem>()).when(client)
                    .getActivitiesToBeDestroyed();
            final PendingTransactionActions pendingActions = mock(PendingTransactionActions.class);

            // No need to notify if not finishing.
            final DestroyActivityItem item0 =
                    new DestroyActivityItem(activityToken, false /* finished */);
            item0.postExecute(client, pendingActions);

            verify(clientSession.mActivityClient, never()).activityDestroyed(any());

            // Notify if finishing.
            final DestroyActivityItem item1 =
                    new DestroyActivityItem(activityToken, true /* finished */);
            item1.postExecute(client, pendingActions);

            verify(clientSession.mActivityClient).activityDestroyed(activityToken);
        }
    }

    private void recreateAndVerifyNoRelaunch(ActivityThread activityThread, TestActivity activity) {
        clearInvocations(activityThread);
        getInstrumentation().runOnMainSync(() -> activity.recreate());
        getInstrumentation().waitForIdleSync();

        verify(activityThread, never()).handleRelaunchActivity(any(), any());
    }

    private void recreateAndVerifyRelaunched(ActivityThread activityThread, TestActivity activity,
            ActivityClientRecord r, int expectedState) throws Exception {
        clearInvocations(activityThread);
        getInstrumentation().runOnMainSync(() -> activity.recreate());

        verify(activityThread, timeout(WAIT_TIMEOUT_MS)).handleRelaunchActivity(any(), any());

        // Wait for the relaunch to complete.
        PollingCheck.check("Waiting for the expected state " + expectedState + " timeout",
                WAIT_TIMEOUT_MS,
                () -> expectedState == r.getLifecycleState());
        assertEquals(expectedState, r.getLifecycleState());
    }

    private class ClientMockSession implements AutoCloseable {
        private MockitoSession mMockSession;
        private ActivityThread mThread;
        private ActivityClient mActivityClient;

        private ClientMockSession() {
            mThread = ActivityThread.currentActivityThread();
            mMockSession = mockitoSession()
                    .strictness(Strictness.LENIENT)
                    .spyStatic(ActivityClient.class)
                    .spyStatic(WindowManagerGlobal.class)
                    .startMocking();
            doReturn(Mockito.mock(WindowManagerGlobal.class))
                    .when(WindowManagerGlobal::getInstance);
            mActivityClient = Mockito.mock(ActivityClient.class);
            doReturn(mActivityClient).when(ActivityClient::getInstance);
            doReturn(true).when(mActivityClient).finishActivity(any() /* token */,
                    anyInt() /* resultCode */, any() /* resultData */, anyInt() /* finishTask */);
        }

        private Activity launchActivity(ActivityClientRecord r) {
            return mThread.handleLaunchActivity(r, null /* pendingActions */,
                    Context.DEVICE_ID_DEFAULT, null /* customIntent */);
        }

        private void startActivity(ActivityClientRecord r) {
            mThread.handleStartActivity(r, null /* pendingActions */, null /* activityOptions */);
        }

        private void resumeActivity(ActivityClientRecord r) {
            mThread.handleResumeActivity(r, true /* finalStateRequest */,
                    true /* isForward */, false /* shouldSendCompatFakeFocus */, "test");
        }

        private void pauseActivity(ActivityClientRecord r) {
            mThread.handlePauseActivity(r, false /* finished */,
                    false /* userLeaving */, false /* autoEnteringPip */,
                    null /* pendingActions */, "test");
        }

        private void stopActivity(ActivityClientRecord r) {
            mThread.handleStopActivity(r,
                    new PendingTransactionActions(), false /* finalStateRequest */, "test");
        }

        private void destroyActivity(ActivityClientRecord r) {
            mThread.handleDestroyActivity(r, true /* finishing */,
                    false /* getNonConfigInstance */, "test");
        }

        private ActivityThread mockThread() {
            spyOn(mThread);
            return mThread;
        }

        private ActivityClientRecord stubActivityRecord() {
            ComponentName component = new ComponentName(
                    InstrumentationRegistry.getInstrumentation().getContext(), TestActivity.class);
            ActivityInfo info = new ActivityInfo();
            info.packageName = component.getPackageName();
            info.name = component.getClassName();
            info.exported = true;
            info.applicationInfo = new ApplicationInfo();
            info.applicationInfo.packageName = info.packageName;
            info.applicationInfo.uid = UserHandle.myUserId();
            info.applicationInfo.sourceDir = "/test/sourceDir";

            // mock the function for preventing NPE in emulator environment. The purpose of the
            // test is for activity client state changes, we don't focus on the updating for the
            // ApplicationInfo.
            LoadedApk packageInfo = mThread.peekPackageInfo(info.packageName,
                    true /* includeCode */);
            spyOn(packageInfo);
            doNothing().when(packageInfo).updateApplicationInfo(any(), any());

            return new ActivityClientRecord(mock(IBinder.class), Intent.makeMainActivity(component),
                    0 /* ident */, info, new Configuration(), null /* referrer */,
                    null /* voiceInteractor */, null /* state */, null /* persistentState */,
                    null /* pendingResults */, null /* pendingNewIntents */,
                    null /* activityOptions */, true /* isForward */, null /* profilerInfo */,
                    mThread /* client */, null /* asssitToken */, null /* shareableActivityToken */,
                    false /* launchedFromBubble */, null /* taskfragmentToken */,
                    null /* initialCallerInfoAccessToken */, new ActivityWindowInfo(),
                    0 /* displayId */);
        }

        @Override
        public void close() {
            mMockSession.finishMocking();
        }
    }

    // Test activity
    public static class TestActivity extends Activity {
    }
}
