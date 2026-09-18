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

package com.android.server.am;

import static android.os.Process.myUid;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.DropBoxManagerInternal;
import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerService.Injector;
import com.android.server.am.ApplicationExitInfoTest.ServiceThreadRule;
import com.android.server.appop.AppOpsService;
import com.android.server.firewall.IntentFirewall;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.WindowProcessController;

import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public abstract class BaseServiceTest {
    private static final String TAG = BaseServiceTest.class.getSimpleName();

    protected static final String CALLING_PACKAGE_NAME = "com.example.foo";

    @Rule
    public final ServiceThreadRule mServiceThreadRule = new ServiceThreadRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private HandlerThread mHandlerThread;

    @Mock
    private AppOpsService mAppOpsService;
    @Mock
    private DropBoxManagerInternal mDropBoxManagerInt;
    @Mock
    private PackageManagerInternal mPackageManagerInt;
    @Mock
    private UsageStatsManagerInternal mUsageStatsManagerInt;
    @Mock
    private UserController mUserControllerInt;
    @Mock
    private UriGrantsManagerInternal mUriGrantsManagerInternalInt;
    @Mock
    private AppErrors mAppErrors;
    @Mock
    private IntentFirewall mIntentFirewall;

    protected ActivityManagerService mAms;
    protected ProcessList mProcessList;
    private ActiveServices mActiveServices;

    protected int mCurrentCallingUid;
    protected int mCurrentCallingPid;

    /**
     * Setup system server objects with just enough mocking to test service related behavior.
     */
    @SuppressWarnings("GuardedBy")
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        final ProcessList realProcessList = new ProcessList();
        mProcessList = spy(realProcessList);

        LocalServices.removeServiceForTest(DropBoxManagerInternal.class);
        LocalServices.addService(DropBoxManagerInternal.class, mDropBoxManagerInt);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInt);
        doReturn(new ComponentName("", "")).when(mPackageManagerInt).getSystemUiServiceComponent();

        final ActivityManagerService realAms = new ActivityManagerService(
                new TestInjector(mContext), mServiceThreadRule.getThread(), mUserControllerInt);
        final ActivityTaskManagerService realAtm = new ActivityTaskManagerService(mContext);
        realAtm.initialize(null, null, realAms.mProcessStateController, mContext.getMainLooper());
        realAms.mActivityTaskManager = spy(realAtm);
        realAms.mAtmInternal = spy(realAms.mActivityTaskManager.getAtmInternal());

        final CachedAppOptimizer cachedAppOptimizer = spy(realAms.getCachedAppOptimizer());
        doReturn(true).when(cachedAppOptimizer).useFreezer();
        doNothing().when(cachedAppOptimizer).freezeAppAsyncAtEarliestLSP(any());
        doNothing().when(cachedAppOptimizer).freezeAppAsyncLSP(any());
        realAms.setCachedAppOptimizer(cachedAppOptimizer);
        realAms.mProcessStateController = spy(realAms.mProcessStateController);
        realAms.mOomAdjuster = spy(realAms.mOomAdjuster);

        realAms.mPackageManagerInt = mPackageManagerInt;
        realAms.mUsageStatsService = mUsageStatsManagerInt;
        realAms.mUgmInternal = mUriGrantsManagerInternalInt;
        realAms.mAppProfiler = spy(realAms.mAppProfiler);
        realAms.mProcessesReady = true;
        mAms = spy(realAms);
        realProcessList.mService = mAms;

        doReturn(false).when(mPackageManagerInt).filterAppAccess(anyString(), anyInt(), anyInt());
        // Necessary for calling package to match caller uid
        doReturn(myUid()).when(mPackageManagerInt).getPackageUid(
                eq(CALLING_PACKAGE_NAME), anyLong(), anyInt());
        doReturn(true).when(mPackageManagerInt).isSameApp(
                eq(CALLING_PACKAGE_NAME), anyLong(), eq(myUid()), anyInt());
        doReturn(true).when(mIntentFirewall).checkService(any(), any(), anyInt(), anyInt(), any(),
                any());
        doReturn(false).when(mAms.mAtmInternal).hasSystemAlertWindowPermission(anyInt(), anyInt(),
                any());
        doNothing().when(mAms.mAppProfiler).updateLowMemStateLSP(anyInt(), anyInt(), anyLong());
        doReturn(true).when(mUserControllerInt).exists(anyInt());
        doReturn(true).when(mUserControllerInt).hasStartedUserState(anyInt());
    }

    /** Clean up anything that can affect subsequent tests. */
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(DropBoxManagerInternal.class);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        mHandlerThread.quit();
    }

    protected Intent createServiceIntent(String packageName, String serviceName, int serviceUid) {
        final Intent serviceIntent = new Intent().setClassName(packageName, serviceName);
        final ResolveInfo rInfo = new ResolveInfo();
        rInfo.serviceInfo = makeServiceInfo(serviceName, packageName, serviceUid);
        doReturn(rInfo).when(mPackageManagerInt).resolveService(eq(serviceIntent), any(), anyLong(),
                anyInt(), anyInt(), anyInt());

        return serviceIntent;
    }

    private ServiceInfo makeServiceInfo(String serviceName, String packageName, int packageUid) {
        final ServiceInfo sInfo = new ServiceInfo();
        sInfo.name = serviceName;
        sInfo.processName = packageName;
        sInfo.packageName = packageName;
        sInfo.applicationInfo = new ApplicationInfo();
        sInfo.applicationInfo.uid = packageUid;
        sInfo.applicationInfo.packageName = packageName;
        sInfo.exported = true;
        return sInfo;
    }

    private class TestInjector extends Injector {
        TestInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mHandlerThread.getThreadHandler();
        }

        @Override
        public ProcessList getProcessList(ActivityManagerService service) {
            return mProcessList;
        }

        @Override
        public ActiveServices getActiveServices(ActivityManagerService service) {
            if (mActiveServices == null) {
                mActiveServices = spy(new ActiveServices(service));
            }
            return mActiveServices;
        }

        @Override
        public int getCallingUid() {
            return mCurrentCallingUid;
        }

        @Override
        public int getCallingPid() {
            return mCurrentCallingPid;
        }

        @Override
        public AppErrors getAppErrors() {
            return mAppErrors;
        }

        @Override
        public IntentFirewall getIntentFirewall() {
            return mIntentFirewall;
        }
    }

    public static class TestProcessBuilder {
        ActivityManagerService mAms = mock(ActivityManagerService.class);
        String mPackageName = CALLING_PACKAGE_NAME;
        String mProcessName = null;
        ApplicationThreadDeferred mAppThread = mock(ApplicationThreadDeferred.class);
        int mUid = 10123;
        int mPid = 12345;
        boolean mIsNativeService = false;

        TestProcessBuilder setActivityManagerService(ActivityManagerService ams) {
            mAms = ams;
            return this;
        }

        TestProcessBuilder setPackageName(String packageName) {
            mPackageName = packageName;
            return this;
        }

        TestProcessBuilder setProcessName(String processName) {
            mProcessName = processName;
            return this;
        }

        TestProcessBuilder setAppThread(ApplicationThreadDeferred appThread) {
            mAppThread = appThread;
            return this;
        }

        TestProcessBuilder setPid(int pid) {
            mPid = pid;
            return this;
        }

        TestProcessBuilder setUid(int uid) {
            mUid = uid;
            return this;
        }

        ProcessRecord build() {
            if (mProcessName == null) {
                mProcessName = mPackageName;
            }

            // Create the ApplicationInfo
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = mPackageName;
            ai.uid = mUid;

            // Create and setup the Process
            final ProcessRecord proc = new ProcessRecord(mAms, ai, mProcessName, mUid);
            proc.setPid(mPid);
            proc.makeActive(mAppThread, mAms.mProcessStats);
            doReturn(mock(IBinder.class)).when(mAppThread).asBinder();

            // Make ActivityManagerService aware of the process.
            mAms.mProcessList.addProcessNameLocked(proc);
            mAms.mProcessList.updateLruProcessLocked(proc, false, null);

            setFieldValue(ProcessRecord.class, proc, "mWindowProcessController",
                    mock(WindowProcessController.class));

            return proc;
        }
    }

    private static <T> void setFieldValue(Class clazz, Object obj, String fieldName, T val) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Field mfield = Field.class.getDeclaredField("accessFlags");
            mfield.setAccessible(true);
            mfield.setInt(field, mfield.getInt(field) & ~(Modifier.FINAL | Modifier.PRIVATE));
            field.set(obj, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }
}
