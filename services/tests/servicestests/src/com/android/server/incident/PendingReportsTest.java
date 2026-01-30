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

package com.android.server.incident;

import static android.Manifest.permission.CAPTURE_CONSENTLESS_BUGREPORT_DELEGATED_CONSENT;
import static android.content.Intent.ACTION_PENDING_INCIDENT_REPORTS_CHANGED;
import static android.os.IncidentManager.FLAG_CONFIRMATION_DIALOG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.AttributionSourceState;
import android.content.Context;
import android.content.Intent;
import android.content.PermissionChecker;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IIncidentAuthListener;
import android.os.Message;
import android.os.TestLooperManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionCheckerManager;
import android.permission.PermissionManager;
import android.testing.TestableContext;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

/**
 * Build/Install/Run: atest FrameworksServicesTests:com.android.server.incident.PendingReportsTest
 */
@RunWith(AndroidJUnit4.class)
public class PendingReportsTest {
    private static final UserInfo ADMIN_USER_INFO =
            new UserInfo(/* id= */ 5678, "adminUser", UserInfo.FLAG_ADMIN);
    private static final UserInfo GUEST_USER_INFO = new UserInfo(/* id= */ 1234, "guestUser", 0);

    public @Rule MockitoRule mMockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    @Rule
    public TestableContext mContext =
            spy(new TestableContext(InstrumentationRegistry.getContext(), null));

    private final IIncidentAuthListener mIncidentAuthListener = mock(IIncidentAuthListener.class);

    private PendingReports mPendingReports;
    private TestInjector mTestInjector;
    private HandlerThread mUiThread;
    private TestLooperManager mTestLooperManager;
    @Mock private PackageManager mMockPackageManager;
    @Mock private UserManager mMockUserManager;
    @Mock private AppOpsManager mMockAppOpsManager;
    @Mock private PermissionCheckerManager mPermissionCheckerManager;
    @Mock private IBinder mListenerBinder;

    public class TestInjector extends PendingReports.Injector {

        private int mUserId = ADMIN_USER_INFO.id;

        TestInjector(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        UserManager getUserManager() {
            return mMockUserManager;
        }

        @Override
        AppOpsManager getAppOpsManager() {
            return mMockAppOpsManager;
        }

        @Override
        int getCurrentUserIfAdmin() {
            return mUserId;
        }

        void clearAdminUserId() {
            mUserId = UserHandle.USER_NULL;
        }
    }

    @Before
    public void setup() throws Exception {
        // generate new IBinder instance every time for test
        when(mIncidentAuthListener.asBinder()).thenReturn(mListenerBinder);

        ResolveInfo resolveInfo = new ResolveInfo();
        ActivityInfo mincidentActivityInfo = new ActivityInfo();
        mincidentActivityInfo.name = "PendingReportsTest";
        mincidentActivityInfo.packageName = mContext.getPackageName();
        resolveInfo.activityInfo = mincidentActivityInfo;

        List<ResolveInfo> intentReceivers = new ArrayList<>();
        intentReceivers.add(resolveInfo);
        ArgumentMatcher<Intent> filterIntent =
                intent -> intent.getAction().equals(Intent.ACTION_PENDING_INCIDENT_REPORTS_CHANGED);
        when(mMockPackageManager.queryBroadcastReceiversAsUser(
                        argThat(filterIntent),
                        /* flags= */ anyInt(),
                        /* userId= */ eq(ADMIN_USER_INFO.id)))
                .thenReturn(intentReceivers);
        mContext.setMockPackageManager(mMockPackageManager);

        when(mMockUserManager.isSameProfileGroup(anyInt(), eq(ADMIN_USER_INFO.id)))
                .thenReturn(true);

        mUiThread = new HandlerThread("MockUiThread");
        mUiThread.start();
        mTestLooperManager =
                InstrumentationRegistry.getInstrumentation()
                        .acquireLooperManager(mUiThread.getLooper());

        doReturn(Context.PERMISSION_CHECKER_SERVICE)
                .when(mContext)
                .getSystemServiceName(PermissionCheckerManager.class);
        mContext.addMockSystemService(PermissionCheckerManager.class, mPermissionCheckerManager);
        mContext.addMockSystemService(
                Context.PERMISSION_CHECKER_SERVICE, mPermissionCheckerManager);
        mContext.addMockSystemService(PermissionManager.class, new PermissionManager(mContext));

        Handler testHandler = new Handler(mUiThread.getLooper());
        mTestInjector = new TestInjector(mContext, testHandler);
        mPendingReports = new PendingReports(mTestInjector);
        mPendingReports.onBootCompleted();
    }

    @After
    public void tearDown() {
        mTestLooperManager.release();
        mUiThread.quit();
    }

    @Test
    public void testAuthorizeReport_sendsIncidentBroadcast() throws Exception {
        mockDelegatePermissionStatus(false);
        mPendingReports.authorizeReport(
                ADMIN_USER_INFO.id,
                mContext.getPackageName(),
                "receiverClass",
                "report_id",
                FLAG_CONFIRMATION_DIALOG,
                mIncidentAuthListener);
        drainRequestQueue();

        assertThat(mPendingReports.getPendingReports()).hasSize(1);
        assertThat(mPendingReports.getPendingReports().get(0))
                .matches(
                        "content://android\\.os\\.IncidentManager/pending\\?id=1"
                                + "&pkg=com\\.android\\.frameworks\\.servicestests"
                                + "&flags=1"
                                + "&t=(\\d+)"
                                + "&receiver=receiverClass&r=report_id");
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext)
                .sendBroadcastAsUser(
                        intentArgumentCaptor.capture(),
                        /* user= */ any(),
                        /* receiverPermission= */ eq(
                                android.Manifest.permission.APPROVE_INCIDENT_REPORTS),
                        /* options= */ any());
        assertBroadcastHasExpectedValue(intentArgumentCaptor.getValue());
    }

    private void assertBroadcastHasExpectedValue(Intent intent) {
        assertThat(intent.getAction()).isEqualTo(ACTION_PENDING_INCIDENT_REPORTS_CHANGED);
        assertThat(intent.getComponent().getClassName()).isEqualTo("PendingReportsTest");
        assertThat(intent.getComponent().getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void testAuthorizeReport_nonAdmin_getsApprovedIfHaveConsentlessPermission()
            throws Exception {
        mockDelegatePermissionStatus(true);
        mTestInjector.clearAdminUserId();

        mPendingReports.authorizeReport(
                GUEST_USER_INFO.id,
                mContext.getPackageName(),
                "receiverClass",
                "report_id",
                FLAG_CONFIRMATION_DIALOG,
                mIncidentAuthListener);
        drainRequestQueue();

        verify(mIncidentAuthListener).onReportApproved();
    }

    @Test
    public void testAuthorizeReport_nonAdmin_denysByDefault() throws Exception {
        mockDelegatePermissionStatus(false);
        mTestInjector.clearAdminUserId();

        mPendingReports.authorizeReport(
                GUEST_USER_INFO.id,
                mContext.getPackageName(),
                "receiverClass",
                "report_id",
                FLAG_CONFIRMATION_DIALOG,
                mIncidentAuthListener);
        drainRequestQueue();

        verify(mIncidentAuthListener).onReportDenied();
    }

    @Test
    public void testCancelAuthorization_sendsIncidentBroadcast() throws Exception {
        mockDelegatePermissionStatus(false);
        mPendingReports.authorizeReport(
                ADMIN_USER_INFO.id,
                mContext.getPackageName(),
                "receiverClass",
                "report_id",
                FLAG_CONFIRMATION_DIALOG,
                mIncidentAuthListener);
        drainRequestQueue();
        mPendingReports.cancelAuthorization(mIncidentAuthListener);
        drainRequestQueue();

        assertThat(mPendingReports.getPendingReports()).isEmpty();
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2))
                .sendBroadcastAsUser(
                        /* intent= */ intentArgumentCaptor.capture(),
                        /* user= */ any(),
                        /* receiverPermission= */ eq(
                                android.Manifest.permission.APPROVE_INCIDENT_REPORTS),
                        /* options= */ any());
        List<Intent> intents = intentArgumentCaptor.getAllValues();
        assertThat(intents).hasSize(2);
        // authorize and cancel sends same intent
        assertBroadcastHasExpectedValue(intents.get(0));
        assertBroadcastHasExpectedValue(intents.get(1));
    }

    private void mockDelegatePermissionStatus(boolean granted) {
        int permissionCode =
                granted
                        ? PermissionChecker.PERMISSION_GRANTED
                        : PermissionChecker.PERMISSION_HARD_DENIED;
        doReturn(permissionCode)
                .when(mPermissionCheckerManager)
                .checkPermission(
                        eq(CAPTURE_CONSENTLESS_BUGREPORT_DELEGATED_CONSENT),
                        any(AttributionSourceState.class),
                        isNull(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt());
    }

    private void drainRequestQueue() {
        while (true) {
            Message m = mTestLooperManager.poll();
            if (m == null) {
                break;
            }
            mTestLooperManager.execute(m);
            mTestLooperManager.recycle(m);
        }
    }
}
