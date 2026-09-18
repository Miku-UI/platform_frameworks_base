/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.notification;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IUriGrantsManager;
import android.app.StatsManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.usage.UsageStatsManagerInternal;
import android.companion.ICompanionDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.permission.PermissionManager;
import android.service.notification.INotificationListener;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.util.AtomicFile;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.android.internal.config.sysui.TestableFlagResolver;
import com.android.internal.logging.InstanceIdSequenceFake;
import com.android.internal.logging.UiEventLogger;
import com.android.server.LocalServices;
import com.android.server.bitmapoffload.BitmapOffloadInternal;
import com.android.server.lights.LightsManager;
import com.android.server.notification.ManagedServices.ManagedServiceInfo;
import com.android.server.notification.NotificationRecordLogger.NotificationReportedEvent;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.utils.quota.MultiRateLimiter;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.mockito.Mock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TestableNotificationManagerService extends NotificationManagerService {
    int countSystemChecks = 0;
    boolean isSystemUid = true;
    boolean isSystemAppId = true;
    int countLogSmartSuggestionsVisible = 0;
    Set<Integer> mChannelToastsSent = new HashSet<>();
    INotificationListener mNas = mock(INotificationListener.class);

    AtomicFile mPolicyFile;
    File mFile;
    AtomicFile mRulesFile;
    File mFile2;

    String stringArrayResourceValue;
    @Nullable
    NotificationAssistantAccessGrantedCallback mNotificationAssistantAccessGrantedCallback;

    ComponentPermissionChecker permissionChecker;
    TestableLooper mTestableLooper;
    TestableContext mTestableContext;

    private static class SensitiveLog {
        public boolean hasPosted;
        public boolean hasSensitiveContent;
        public long lifetime;
    }
    public SensitiveLog lastSensitiveLog = null;

    private static class ClassificationChannelLog {
        public boolean hasPosted;
        public boolean isAlerting;
        public long classification;
        public long lifetime;
        public long eventId;
        public long instanceId;
        public long uid;
    }
    public ClassificationChannelLog  lastClassificationChannelLog = null;

    TestableNotificationManagerService(TestableContext context, TestableLooper looper) {
        super(context);
        mTestableContext = context;
        mTestableLooper = looper;
        mComputerControlHelper = new FakeComputerControlHelper();
    }
    void init() throws IOException {
        init("<notification-policy></notification-policy", null);
    }

    void init(String policyXml, String rulesXml) throws IOException {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                "android.permission.READ_CONTACTS");
        // write to a test file; the system file isn't readable from tests
        mFile = new File(getContext().getCacheDir(), "notification_policy.xml");
        mPolicyFile = new AtomicFile(mFile);
        if (policyXml != null) {
            FileOutputStream fos = mPolicyFile.startWrite();
            fos.write(policyXml.getBytes());
            mPolicyFile.finishWrite(fos);
        }
        mFile2 = new File(getContext().getCacheDir(), "notification_rules.xml");
        mRulesFile = new AtomicFile(mFile2);
        if (rulesXml != null) {
            FileOutputStream fos = mRulesFile.startWrite();
            fos.write(rulesXml.getBytes());
            mRulesFile.finishWrite(fos);
        } else {
            mRulesFile.delete();
        }

        // apps allowed as convos
        setStringArrayResourceValue("");

        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(WindowManagerInternal.class, mock(WindowManagerInternal.class));
        mTestableContext.addMockSystemService(AppOpsManager.class, mock(AppOpsManager.class));
        mTestableContext.addMockSystemService(Context.ALARM_SERVICE, mock(AlarmManager.class));

        NotificationAssistants assistants = spy(new NotificationAssistants(
                mTestableContext, mock(IPackageManager.class)));

        super.init(spy(new WorkerHandler(mTestableLooper.getLooper())),
                mock(RankingHandler.class), new Handler(mTestableLooper.getLooper()),
                mock(IPackageManager.class), mock(PackageManager.class),
                mock(LightsManager.class),
                new NotificationListeners(mTestableContext, new Object(),
                        mUserProfiles, mock(IPackageManager.class),
                        new NotificationManagerService.ConfigurableParameters()),
                assistants,
                new ConditionProviders(mTestableContext, mUserProfiles,
                        mock(IPackageManager.class)),
                mock(ICompanionDeviceManager.class),
                mock(SnoozeHelper.class), mock(NotificationUsageStats.class),
                mPolicyFile, mRulesFile, mock(ActivityManager.class),
                mock(GroupHelper.class), mock(IActivityManager.class),
                mock(ActivityTaskManagerInternal.class),
                mock(UsageStatsManagerInternal.class),
                mock(DevicePolicyManagerInternal.class), mock(IUriGrantsManager.class),
                mock(UriGrantsManagerInternal.class),
                mock(AppOpsManager.class),
                mock(NotificationHistoryManager.class), mock(StatsManager.class),
                mock(ActivityManagerInternal.class),
                mock(MultiRateLimiter.class), mock(PermissionHelper.class),
                mock(UsageStatsManagerInternal.class), mock(TelecomManager.class),
                mock(NotificationChannelLogger.class), new TestableFlagResolver(),
                mock(PermissionManager.class),
                mock(PowerManager.class),
                new NotificationManagerService.PostNotificationTrackerFactory() {},
                mock(UiEventLogger.class),
                mock(BitmapOffloadInternal.class), new NotificationListenerStats(),
                new NotificationRecordLoggerFake(), new InstanceIdSequenceFake(1 << 30),
                new TestPreferencesHelperFactory());

        when(mNas.asBinder()).thenReturn(mock(IBinder.class));
        mAssistants.registerSystemService(mNas, ComponentName.unflattenFromString("a/b"),
                ActivityManager.getCurrentUser(), 1000);
    }

    RankingHelper getRankingHelper() {
        return mRankingHelper;
    }

    FakeComputerControlHelper getFakeComputerControlHelper() {
        return (FakeComputerControlHelper) mComputerControlHelper;
    }

    /**
     * Sets {@link #isSystemUid} and {@link #isSystemAppId} to {@code false}, so that calls to NMS
     * methods don't succeed {@link #isCallingUidSystem()} and similar checks.
     */
    void setCallerIsNormalPackage() {
        isSystemUid = false;
        isSystemAppId = false;
    }

    @Override
    protected boolean isCallingUidSystem() {
        countSystemChecks++;
        return isSystemUid;
    }

    @Override
    protected boolean isCallingAppIdSystem() {
        countSystemChecks++;
        return isSystemUid || isSystemAppId;
    }

    @Override
    protected boolean isCallerSystemOrPhone() {
        countSystemChecks++;
        return isSystemUid || isSystemAppId;
    }

    @Override
    protected boolean isCallerSystemOrSystemUi() {
        countSystemChecks++;
        return isSystemUid || isSystemAppId;
    }

    @Override
    protected ICompanionDeviceManager getCompanionManager() {
        return null;
    }

    @Override
    protected void reportUserInteraction(NotificationRecord r) {
        return;
    }

    @Override
    protected void handleSavePolicyFile() {
        return;
    }

    @Override
    void logSmartSuggestionsVisible(NotificationRecord r, int notificationLocation) {
        super.logSmartSuggestionsVisible(r, notificationLocation);
        countLogSmartSuggestionsVisible++;
    }

    @Override
    protected void setNotificationAssistantAccessGrantedForUserInternal(
            ComponentName assistant, int userId, boolean granted, boolean userSet) {
        if (mNotificationAssistantAccessGrantedCallback != null) {
            mNotificationAssistantAccessGrantedCallback.onGranted(assistant, userId, granted,
                    userSet);
            return;
        }
        super.setNotificationAssistantAccessGrantedForUserInternal(assistant, userId, granted,
                userSet);
    }

    @Override
    protected String[] getStringArrayResource(int key) {
        return new String[] {stringArrayResourceValue};
    }

    protected void setStringArrayResourceValue(String value) {
        stringArrayResourceValue = value;
    }

    void setNotificationAssistantAccessGrantedCallback(
            @Nullable NotificationAssistantAccessGrantedCallback callback) {
        this.mNotificationAssistantAccessGrantedCallback = callback;
    }

    interface NotificationAssistantAccessGrantedCallback {
        void onGranted(ComponentName assistant, int userId, boolean granted, boolean userSet);
    }

    @Override
    protected void doChannelWarningToast(int uid, CharSequence toastText) {
        mChannelToastsSent.add(uid);
    }

    // Helper method for testing behavior when turning on/off the review permissions notification.
    protected void setShowReviewPermissionsNotification(boolean setting) {
        mShowReviewPermissionsNotification = setting;
    }

    @Override
    protected int checkComponentPermission(String permission, int uid, int owningUid,
            boolean exported) {
        return permissionChecker.check(permission, uid, owningUid, exported);
    }

    @Override
    protected void logSensitiveAdjustmentReceived(boolean hasPosted, boolean hasSensitiveContent,
            int lifetimeMs) {
        lastSensitiveLog = new SensitiveLog();
        lastSensitiveLog.hasPosted = hasPosted;
        lastSensitiveLog.hasSensitiveContent = hasSensitiveContent;
        lastSensitiveLog.lifetime = lifetimeMs;
    }

    public class StrongAuthTrackerFake extends NotificationManagerService.StrongAuthTracker {
        private int mGetStrongAuthForUserReturnValue = 0;
        StrongAuthTrackerFake(Context context) {
            super(context);
        }

        public void setGetStrongAuthForUserReturnValue(int val) {
            mGetStrongAuthForUserReturnValue = val;
        }

        @Override
        public int getStrongAuthForUser(int userId) {
            return mGetStrongAuthForUserReturnValue;
        }
    }

    public boolean checkLastSensitiveLog(boolean hasPosted, boolean hasSensitive, int lifetime) {
        if (lastSensitiveLog == null) {
            return false;
        }
        return hasPosted == lastSensitiveLog.hasPosted
                && hasSensitive == lastSensitiveLog.hasSensitiveContent
                && lifetime == lastSensitiveLog.lifetime;
    }

    public interface ComponentPermissionChecker {
        int check(String permission, int uid, int owningUid, boolean exported);
    }

    @Override
    protected void logClassificationChannelAdjustmentReceived(NotificationRecord r,
                                                              boolean hasPosted,
                                                              int classification) {

        boolean isAlerting = r.getChannel().getImportance() >= IMPORTANCE_DEFAULT;
        int instanceId = r.getSbn().getInstanceId() == null
                ? 0 : r.getSbn().getInstanceId().getId();
        int lifetimeMs = r.getLifespanMs(System.currentTimeMillis());
        int uid = r.getUid();

        lastClassificationChannelLog = new ClassificationChannelLog();
        lastClassificationChannelLog.hasPosted = hasPosted;
        lastClassificationChannelLog.isAlerting = isAlerting;
        lastClassificationChannelLog.classification = classification;
        lastClassificationChannelLog.lifetime = lifetimeMs;
        lastClassificationChannelLog.eventId =
                NotificationReportedEvent.NOTIFICATION_ADJUSTED.getId();
        lastClassificationChannelLog.instanceId = instanceId;
        lastClassificationChannelLog.uid = uid;
    }

    /**
     * Returns true if the last recorded classification channel log has all the values specified.
     */
    public boolean checkLastClassificationChannelLog(boolean hasPosted, boolean isAlerting,
                                                     int classification, int lifetime,
                                                     int eventId, int instanceId,
                                                     int uid) {
        if (lastClassificationChannelLog == null) {
            return false;
        }

        return hasPosted == lastClassificationChannelLog.hasPosted
                && isAlerting == lastClassificationChannelLog.isAlerting
                && classification == lastClassificationChannelLog.classification
                && lifetime == lastClassificationChannelLog.lifetime
                && eventId == lastClassificationChannelLog.eventId
                && instanceId == lastClassificationChannelLog.instanceId
                && uid == lastClassificationChannelLog.uid;
    }
}
