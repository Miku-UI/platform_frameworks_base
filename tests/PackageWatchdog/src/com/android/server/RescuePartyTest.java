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

package com.android.server;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.PackageWatchdog.MITIGATION_RESULT_SKIPPED;
import static com.android.server.PackageWatchdog.MITIGATION_RESULT_SUCCESS;
import static com.android.server.RescueParty.DEFAULT_FACTORY_RESET_THROTTLE_DURATION_MIN;
import static com.android.server.RescueParty.LEVEL_FACTORY_RESET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.os.TestLooperManager;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.OnPropertiesChangedListener;
import android.provider.DeviceConfig.Properties;
import android.util.Slog;

import com.android.crashrecovery.flags.Flags;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.PackageWatchdog.PackageHealthObserverImpact;
import com.android.server.RescueParty.RescuePartyObserver;
import com.android.server.crashrecovery.CrashRecoveryUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Test RescueParty.
 */
@RunWith(ParameterizedAndroidJunit4.class)
public class RescuePartyTest {

    @Rule public final SetFlagsRule mSetFlagsRule;
    private HandlerThread mTestHandlerThread;
    private TestLooperManager mTestLooperManager;
    private Handler mTestHandler;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(Flags.FLAG_FLAG_RESET_DISABLED,
                Flags.FLAG_FLAG_RESET_ENABLED);
    }

    private static final long CURRENT_NETWORK_TIME_MILLIS = 0L;

    private static VersionedPackage sFailingPackage = new VersionedPackage("com.package.name", 1);
    private static final String PROP_DISABLE_RESCUE = "persist.sys.disable_rescue";
    private static final String PERSISTENT_PACKAGE = "com.persistent.package";
    private static final String NON_PERSISTENT_PACKAGE = "com.nonpersistent.package";
    private static final String PROP_DEVICE_CONFIG_DISABLE_FLAG =
            "persist.device_config.configuration.disable_rescue_party";
    private static final String PROP_DISABLE_FACTORY_RESET_FLAG =
            "persist.device_config.configuration.disable_rescue_party_factory_reset";
    private static final String NAMESPACE_TO_RESET1 = "foo_ns";
    private static final String NAMESPACE_TO_RESET2 = "bar_ns";
    private static final String NETWORK_STACK_PACKAGE_NAME = "com.android.networkstack";

    private MockitoSession mSession;
    private HashMap<String, String> mSystemSettingsMap;
    private HashMap<String, String> mCrashRecoveryPropertiesMap;
    //Records the namespaces wiped by setProperties().
    private HashSet<String> mNamespacesWiped;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mMockContext;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PackageWatchdog mMockPackageWatchdog;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OnPropertiesChangedListener mMockOnPropertiesChangedListener;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ContentResolver mMockContentResolver;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PackageManager mPackageManager;

    public RescuePartyTest(FlagsParameterization flags) {
        mSetFlagsRule = new SetFlagsRule(flags);
    }

    // Mock only sysprop apis
    private PackageWatchdog.BootThreshold mSpyBootThreshold;

    @Before
    public void setUp() throws Exception {
        mSession =
                ExtendedMockito.mockitoSession().initMocks(
                        this)
                        .strictness(Strictness.LENIENT)
                        .spyStatic(DeviceConfig.class)
                        .spyStatic(SystemProperties.class)
                        .spyStatic(RecoverySystem.class)
                        .spyStatic(RescueParty.class)
                        .spyStatic(PackageWatchdog.class)
                        .spyStatic(CrashRecoveryUtils.class)
                        .spyStatic(Slog.class)
                        .startMocking();
        mSystemSettingsMap = new HashMap<>();
        mNamespacesWiped = new HashSet<>();
        mCrashRecoveryPropertiesMap = new HashMap<>();

        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        ApplicationInfo persistentApplicationInfo = new ApplicationInfo();
        persistentApplicationInfo.flags |=
                ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_PERSISTENT;

        // If the package name is PERSISTENT_PACKAGE, then set the flags to be persistent and
        // system. Don't set any flags otherwise.
        when(mPackageManager.getApplicationInfo(eq(PERSISTENT_PACKAGE),
                anyInt())).thenReturn(persistentApplicationInfo);
        when(mPackageManager.getApplicationInfo(eq(NON_PERSISTENT_PACKAGE),
                anyInt())).thenReturn(new ApplicationInfo());
        // Reset observer instance to get new mock context on every run
        RescuePartyObserver.reset();

        // Mock SystemProperties setter and various getters
        doAnswer((Answer<Void>) invocationOnMock -> {
                    String key = invocationOnMock.getArgument(0);
                    String value = invocationOnMock.getArgument(1);

                    mSystemSettingsMap.put(key, value);
                    return null;
                }
        ).when(() -> SystemProperties.set(anyString(), anyString()));

        doAnswer((Answer<Boolean>) invocationOnMock -> {
                    String key = invocationOnMock.getArgument(0);
                    boolean defaultValue = invocationOnMock.getArgument(1);

                    String storedValue = mSystemSettingsMap.get(key);
                    return storedValue == null ? defaultValue : Boolean.parseBoolean(storedValue);
                }
        ).when(() -> SystemProperties.getBoolean(anyString(), anyBoolean()));

        doAnswer((Answer<Integer>) invocationOnMock -> {
                    String key = invocationOnMock.getArgument(0);
                    int defaultValue = invocationOnMock.getArgument(1);

                    String storedValue = mSystemSettingsMap.get(key);
                    return storedValue == null ? defaultValue : Integer.parseInt(storedValue);
                }
        ).when(() -> SystemProperties.getInt(anyString(), anyInt()));

        doAnswer((Answer<Long>) invocationOnMock -> {
                    String key = invocationOnMock.getArgument(0);
                    long defaultValue = invocationOnMock.getArgument(1);

                    String storedValue = mSystemSettingsMap.get(key);
                    return storedValue == null ? defaultValue : Long.parseLong(storedValue);
                }
        ).when(() -> SystemProperties.getLong(anyString(), anyLong()));

        // Mock DeviceConfig
        doAnswer((Answer<Void>) invocationOnMock -> {
            String namespace = invocationOnMock.getArgument(1);
            mNamespacesWiped.add(namespace);
            return null;
        }).when(() -> DeviceConfig.resetToDefaults(anyInt(), anyString()));
        doAnswer((Answer<Void>) invocationOnMock -> {
            mMockOnPropertiesChangedListener = invocationOnMock.getArgument(2);
            return null;
        }).when(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(),
                        any(Executor.class), any(OnPropertiesChangedListener.class)));

        // Mock PackageWatchdog
        doAnswer((Answer<PackageWatchdog>) invocationOnMock -> mMockPackageWatchdog)
                .when(() -> PackageWatchdog.getInstance(mMockContext));
        mockCrashRecoveryProperties(mMockPackageWatchdog);

        // Mock CrashRecoveryUtils
        doAnswer((Answer<Set<String>>) invocationOnMock ->
                Set.of(NAMESPACE_TO_RESET1, NAMESPACE_TO_RESET2))
                .when(CrashRecoveryUtils::getFlagNamespacesInModules);
        doAnswer((Answer<String>) invocationOnMock -> NETWORK_STACK_PACKAGE_NAME)
                .when(() -> CrashRecoveryUtils.getNetworkStackPackageName(mMockContext));

        // Mock Slog
        doAnswer((Answer<Integer>) invocationOnMock -> 0)
                .when(() -> android.util.Slog.wtf(anyString(), anyString()));

        doReturn(CURRENT_NETWORK_TIME_MILLIS).when(() -> RescueParty.getElapsedRealtime());

        setCrashRecoveryPropRescueBootCount(0);
        SystemProperties.set(RescueParty.PROP_ENABLE_RESCUE, Boolean.toString(true));
        SystemProperties.set(PROP_DEVICE_CONFIG_DISABLE_FLAG, Boolean.toString(false));

        mTestHandlerThread = new HandlerThread("TestHandlerThread");
        mTestHandlerThread.start();
        mTestLooperManager = new TestLooperManager(mTestHandlerThread.getLooper());
        mTestHandler =  new Handler(mTestHandlerThread.getLooper());

    }

    @After
    public void tearDown() throws Exception {
        mSession.finishMocking();
        if (mTestLooperManager != null) {
            mTestLooperManager.release();
        }
        if (mTestHandlerThread != null) {
            mTestHandlerThread.quitSafely();
        }
        mSystemSettingsMap.clear();
        mNamespacesWiped.clear();
        mCrashRecoveryPropertiesMap.clear();
    }

    @Test
    public void testBootLoopExecution_flagResetEnabled() {
        assumeTrue(RescueParty.isFlagResetEnabled());
        noteBoot(1);
        verify(() -> DeviceConfig.resetToDefaults(anyInt(), eq(NAMESPACE_TO_RESET1)));
        verify(() -> DeviceConfig.resetToDefaults(anyInt(), eq(NAMESPACE_TO_RESET2)));
        assertFalse(RescueParty.isRecoveryTriggeredReboot());

        noteBoot(2);
        assertTrue(RescueParty.isRebootPropertySet());

        setCrashRecoveryPropAttemptingReboot(false);
        noteBoot(3);
        assertTrue(RescueParty.isFactoryResetPropertySet());
    }

    @Test
    public void testBootLoopExecution_flagResetDisabled() {
        assumeFalse(RescueParty.isFlagResetEnabled());
        noteBoot(1);
        assertTrue(RescueParty.isRebootPropertySet());

        setCrashRecoveryPropAttemptingReboot(false);
        noteBoot(2);
        assertTrue(RescueParty.isFactoryResetPropertySet());
    }

    @Test
    public void testPersistentAppCrashNoFlags() {
        // this is old test where the flag needs to be disabled
        noteAppCrash(1, true);
        assertTrue(RescueParty.isRebootPropertySet());

        setCrashRecoveryPropAttemptingReboot(false);
        noteAppCrash(2, true);
        assertTrue(RescueParty.isFactoryResetPropertySet());
    }

    @Test
    public void testIsRecoveryTriggeredReboot() {
        for (int i = 0; i < LEVEL_FACTORY_RESET; i++) {
            noteBoot(i + 1);
        }
        assertFalse(RescueParty.isFactoryResetPropertySet());
        setCrashRecoveryPropAttemptingReboot(false);
        noteBoot(LEVEL_FACTORY_RESET + 1);
        assertTrue(RescueParty.isRecoveryTriggeredReboot());
        assertTrue(RescueParty.isFactoryResetPropertySet());
    }

    @Test
    public void testIsRecoveryTriggeredRebootOnlyAfterRebootCompleted() {
        for (int i = 0; i < LEVEL_FACTORY_RESET; i++) {
            noteBoot(i + 1);
        }
        int mitigationCount = LEVEL_FACTORY_RESET + 1;
        assertFalse(RescueParty.isFactoryResetPropertySet());
        noteBoot(mitigationCount++);
        assertFalse(RescueParty.isFactoryResetPropertySet());
        noteBoot(mitigationCount++);
        assertFalse(RescueParty.isFactoryResetPropertySet());
        noteBoot(mitigationCount++);
        setCrashRecoveryPropAttemptingReboot(false);
        noteBoot(mitigationCount + 1);
        assertTrue(RescueParty.isRecoveryTriggeredReboot());
        assertTrue(RescueParty.isFactoryResetPropertySet());
    }

    @Test
    public void testThrottlingOnBootFailures() {
        setCrashRecoveryPropAttemptingReboot(false);
        long now = System.currentTimeMillis();
        long beforeTimeout = now - TimeUnit.MINUTES.toMillis(
                DEFAULT_FACTORY_RESET_THROTTLE_DURATION_MIN - 1);
        setCrashRecoveryPropLastFactoryReset(beforeTimeout);
        for (int i = 1; i <= LEVEL_FACTORY_RESET; i++) {
            noteBoot(i);
        }
        assertFalse(RescueParty.isRecoveryTriggeredReboot());
    }

    @Test
    public void testThrottlingOnAppCrash() {
        setCrashRecoveryPropAttemptingReboot(false);
        long now = System.currentTimeMillis();
        long beforeTimeout = now - TimeUnit.MINUTES.toMillis(
                DEFAULT_FACTORY_RESET_THROTTLE_DURATION_MIN - 1);
        setCrashRecoveryPropLastFactoryReset(beforeTimeout);
        for (int i = 0; i <= LEVEL_FACTORY_RESET; i++) {
            noteAppCrash(i + 1, true);
        }
        assertFalse(RescueParty.isRecoveryTriggeredReboot());
    }

    @Test
    public void testNotThrottlingAfterTimeoutOnBootFailures() {
        setCrashRecoveryPropAttemptingReboot(false);
        long now = System.currentTimeMillis();
        long afterTimeout = now - TimeUnit.MINUTES.toMillis(
                DEFAULT_FACTORY_RESET_THROTTLE_DURATION_MIN + 1);
        setCrashRecoveryPropLastFactoryReset(afterTimeout);
        for (int i = 1; i <= LEVEL_FACTORY_RESET; i++) {
            noteBoot(i);
        }
        assertTrue(RescueParty.isRecoveryTriggeredReboot());
    }

    @Test
    public void testNotThrottlingAfterTimeoutOnAppCrash() {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        setCrashRecoveryPropAttemptingReboot(false);
        long now = System.currentTimeMillis();
        long afterTimeout = now - TimeUnit.MINUTES.toMillis(
                DEFAULT_FACTORY_RESET_THROTTLE_DURATION_MIN + 1);
        setCrashRecoveryPropLastFactoryReset(afterTimeout);
        for (int i = 0; i <= LEVEL_FACTORY_RESET; i++) {
            noteAppCrash(i + 1, true);
        }
        assertTrue(RescueParty.isRecoveryTriggeredReboot());
    }

    @Test
    public void testExplicitlyEnablingAndDisablingRescue() {
        SystemProperties.set(RescueParty.PROP_ENABLE_RESCUE, Boolean.toString(false));
        SystemProperties.set(PROP_DISABLE_RESCUE, Boolean.toString(true));
        assertEquals(RescuePartyObserver.getInstance(mMockContext).onExecuteHealthCheckMitigation(
                sFailingPackage, PackageWatchdog.FAILURE_REASON_APP_NOT_RESPONDING, 1),
                MITIGATION_RESULT_SKIPPED);

        SystemProperties.set(RescueParty.PROP_ENABLE_RESCUE, Boolean.toString(true));
        assertEquals(RescuePartyObserver.getInstance(mMockContext).onExecuteHealthCheckMitigation(
                sFailingPackage, PackageWatchdog.FAILURE_REASON_APP_NOT_RESPONDING, 1),
                MITIGATION_RESULT_SUCCESS);
    }

    @Test
    public void testDisablingRescueByDeviceConfigFlag() {
        SystemProperties.set(RescueParty.PROP_ENABLE_RESCUE, Boolean.toString(false));
        SystemProperties.set(PROP_DEVICE_CONFIG_DISABLE_FLAG, Boolean.toString(true));

        assertEquals(RescuePartyObserver.getInstance(mMockContext).onExecuteHealthCheckMitigation(
                sFailingPackage, PackageWatchdog.FAILURE_REASON_APP_NOT_RESPONDING, 1),
                MITIGATION_RESULT_SKIPPED);

        // Restore the property value initialized in SetUp()
        SystemProperties.set(RescueParty.PROP_ENABLE_RESCUE, Boolean.toString(true));
        SystemProperties.set(PROP_DEVICE_CONFIG_DISABLE_FLAG, Boolean.toString(false));
    }

    @Test
    public void testDisablingFactoryResetByDeviceConfigFlag() {
        SystemProperties.set(PROP_DISABLE_FACTORY_RESET_FLAG, Boolean.toString(true));

        for (int i = 0; i < LEVEL_FACTORY_RESET; i++) {
            noteBoot(i + 1);
        }
        assertFalse(RescueParty.isFactoryResetPropertySet());

        // Restore the property value initialized in SetUp()
        SystemProperties.set(PROP_DISABLE_FACTORY_RESET_FLAG, "");
    }

    @Test
    public void testHealthCheckLevelsNoFlags() {
        // this is old test where the flag needs to be disabled
        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);

        // Ensure that no action is taken for cases where the failure reason is unknown
        assertEquals(observer.onHealthCheckFailed(null, PackageWatchdog.FAILURE_REASON_UNKNOWN, 1),
                PackageHealthObserverImpact.USER_IMPACT_LEVEL_0);

        // Ensure the correct user impact is returned for each mitigation count.
        assertEquals(observer.onHealthCheckFailed(null,
                        PackageWatchdog.FAILURE_REASON_APP_NOT_RESPONDING, 1),
                PackageHealthObserverImpact.USER_IMPACT_LEVEL_50);

        assertEquals(observer.onHealthCheckFailed(null,
                        PackageWatchdog.FAILURE_REASON_APP_NOT_RESPONDING, 2),
                PackageHealthObserverImpact.USER_IMPACT_LEVEL_100);
    }

    @Test
    public void testBootLoopLevels_flagResetEnabled() {
        assumeTrue(RescueParty.isFlagResetEnabled());
        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);

        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_40, observer.onBootLoop(1));
        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_50, observer.onBootLoop(2));
        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_100, observer.onBootLoop(3));
        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_100, observer.onBootLoop(4));
    }

    @Test
    public void testBootLoopLevels_flagResetDisabled() {
        assumeFalse(RescueParty.isFlagResetEnabled());
        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);

        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_50, observer.onBootLoop(1));
        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_100, observer.onBootLoop(2));
        assertEquals(PackageHealthObserverImpact.USER_IMPACT_LEVEL_100, observer.onBootLoop(3));
    }

    @Test
    public void testExplicitHealthCheckTriggersAfterFlagUpdate() throws Exception {
        assumeTrue(RescueParty.isFlagResetEnabled());

        Properties properties1 = new Properties.Builder(NAMESPACE_TO_RESET1)
                .setString("KEY1", "VALUE1").build();
        Properties properties2 = new Properties.Builder(NAMESPACE_TO_RESET2)
                .setString("KEY1", "VALUE1").build();
        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);
        injectTestHandler(observer);

        verify(CrashRecoveryUtils::getFlagNamespacesInModules);
        verify(() -> CrashRecoveryUtils.getNetworkStackPackageName(mMockContext));
        observer.initializeDeviceConfigMonitoringIfRequiredAsync();
        mTestLooperManager.execute(mTestLooperManager.next());

        verify(() -> DeviceConfig.addOnPropertiesChangedListener(eq(NAMESPACE_TO_RESET1),
                any(Executor.class), any(OnPropertiesChangedListener.class)));
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(eq(NAMESPACE_TO_RESET2),
                any(Executor.class), any(OnPropertiesChangedListener.class)));

        // verify that startExplicitHealthCheck was not triggered immediately
        mMockOnPropertiesChangedListener.onPropertiesChanged(properties1);
        mMockOnPropertiesChangedListener.onPropertiesChanged(properties2);
        verify(mMockPackageWatchdog, times(0)).startExplicitHealthCheck(any(List.class),
                any(Long.class), eq(observer));

        // Reset the delay to 1 sec and update property
        Field mDefaultHealthCheckTriggerDelayField = observer.getClass()
                .getDeclaredField("DEFAULT_HEALTH_CHECK_TRIGGER_DELAY_MS");
        mDefaultHealthCheckTriggerDelayField.setAccessible(true);
        mDefaultHealthCheckTriggerDelayField.set(observer, 1000);
        mMockOnPropertiesChangedListener.onPropertiesChanged(properties1);
        mMockOnPropertiesChangedListener.onPropertiesChanged(properties2);
        mTestLooperManager.execute(mTestLooperManager.next());

        // verify that explicit health check was triggered exactly once.
        verify(mMockPackageWatchdog).startExplicitHealthCheck(any(List.class),
                eq(observer.DEFAULT_OBSERVING_DURATION_MS),
                eq(observer));
    }

    @Test
    public void testObserverCreation_emptyNamespaces() throws Exception {
        assumeTrue(RescueParty.isFlagResetEnabled());
        doAnswer((Answer<Set<String>>) invocationOnMock -> Set.of())
                .when(CrashRecoveryUtils::getFlagNamespacesInModules);

        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);
        injectTestHandler(observer);

        observer.initializeDeviceConfigMonitoringIfRequiredAsync();

        // Verify no listeners are added
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(),
                any(Executor.class), any(OnPropertiesChangedListener.class)), times(0));
    }

    @Test
    public void testObserverCreation_nullNetworkPackage() throws Exception {
        assumeTrue(RescueParty.isFlagResetEnabled());
        doAnswer((Answer<String>) invocationOnMock -> null)
                .when(() -> CrashRecoveryUtils.getNetworkStackPackageName(mMockContext));

        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);
        injectTestHandler(observer);

        verify(() -> android.util.Slog.wtf(eq(RescueParty.TAG),
                eq("Unable to find NetworkPackage for monitoring network health")));

        observer.initializeDeviceConfigMonitoringIfRequiredAsync();

        // Verify no listeners are added
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(),
                any(Executor.class), any(OnPropertiesChangedListener.class)), times(0));
    }

    @Test
    public void testObserverCreation_emptyNamespaces_nullNetworkPackage() throws Exception {
        assumeTrue(RescueParty.isFlagResetEnabled());
        doAnswer((Answer<Set<String>>) invocationOnMock -> Set.of())
                .when(CrashRecoveryUtils::getFlagNamespacesInModules);
        doAnswer((Answer<String>) invocationOnMock -> null)
                .when(() -> CrashRecoveryUtils.getNetworkStackPackageName(mMockContext));

        RescuePartyObserver observer = RescuePartyObserver.getInstance(mMockContext);
        injectTestHandler(observer);

        verify(() -> android.util.Slog.wtf(eq(RescueParty.TAG),
                eq("Unable to find NetworkPackage for monitoring network health")));

        observer.initializeDeviceConfigMonitoringIfRequiredAsync();

        // Verify no listeners are added
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(),
                any(Executor.class), any(OnPropertiesChangedListener.class)), times(0));
    }

    private void noteBoot(int mitigationCount) {
        RescuePartyObserver.getInstance(mMockContext).onExecuteBootLoopMitigation(mitigationCount);
    }

    private void noteAppCrash(int mitigationCount, boolean isPersistent) {
        String packageName = isPersistent ? PERSISTENT_PACKAGE : NON_PERSISTENT_PACKAGE;
        RescuePartyObserver.getInstance(mMockContext).onExecuteHealthCheckMitigation(
                new VersionedPackage(packageName, 1), PackageWatchdog.FAILURE_REASON_APP_CRASH,
                mitigationCount);
    }

    // Mock CrashRecoveryProperties as they cannot be accessed due to SEPolicy restrictions
    private void mockCrashRecoveryProperties(PackageWatchdog watchdog) {
        // mock properties in RescueParty
        try {

            doAnswer((Answer<Boolean>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.attempting_factory_reset", "false");
                return Boolean.parseBoolean(storedValue);
            }).when(() -> RescueParty.isFactoryResetPropertySet());
            doAnswer((Answer<Void>) invocationOnMock -> {
                boolean value = invocationOnMock.getArgument(0);
                mCrashRecoveryPropertiesMap.put("crashrecovery.attempting_factory_reset",
                        Boolean.toString(value));
                return null;
            }).when(() -> RescueParty.setFactoryResetProperty(anyBoolean()));

            doAnswer((Answer<Boolean>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.attempting_reboot", "false");
                return Boolean.parseBoolean(storedValue);
            }).when(() -> RescueParty.isRebootPropertySet());
            doAnswer((Answer<Void>) invocationOnMock -> {
                boolean value = invocationOnMock.getArgument(0);
                setCrashRecoveryPropAttemptingReboot(value);
                return null;
            }).when(() -> RescueParty.setRebootProperty(anyBoolean()));

            doAnswer((Answer<Long>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("persist.crashrecovery.last_factory_reset", "0");
                return Long.parseLong(storedValue);
            }).when(() -> RescueParty.getLastFactoryResetTimeMs());
            doAnswer((Answer<Void>) invocationOnMock -> {
                long value = invocationOnMock.getArgument(0);
                setCrashRecoveryPropLastFactoryReset(value);
                return null;
            }).when(() -> RescueParty.setLastFactoryResetTimeMs(anyLong()));

            doAnswer((Answer<Integer>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.max_rescue_level_attempted", "0");
                return Integer.parseInt(storedValue);
            }).when(() -> RescueParty.getMaxRescueLevelAttempted());
            doAnswer((Answer<Void>) invocationOnMock -> {
                int value = invocationOnMock.getArgument(0);
                mCrashRecoveryPropertiesMap.put("crashrecovery.max_rescue_level_attempted",
                        Integer.toString(value));
                return null;
            }).when(() -> RescueParty.setMaxRescueLevelAttempted(anyInt()));

        } catch (Exception e) {
            // tests will fail, just printing the error
            System.out.println("Error while mocking crashrecovery properties " + e.getMessage());
        }

        // mock properties in BootThreshold
        try {
            mSpyBootThreshold = spy(watchdog.new BootThreshold(
                    PackageWatchdog.DEFAULT_BOOT_LOOP_TRIGGER_COUNT,
                    PackageWatchdog.DEFAULT_BOOT_LOOP_TRIGGER_WINDOW_MS));

            doAnswer((Answer<Integer>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.rescue_boot_count", "0");
                return Integer.parseInt(storedValue);
            }).when(mSpyBootThreshold).getCount();
            doAnswer((Answer<Void>) invocationOnMock -> {
                int count = invocationOnMock.getArgument(0);
                setCrashRecoveryPropRescueBootCount(count);
                return null;
            }).when(mSpyBootThreshold).setCount(anyInt());

            doAnswer((Answer<Integer>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.boot_mitigation_count", "0");
                return Integer.parseInt(storedValue);
            }).when(mSpyBootThreshold).getMitigationCount();
            doAnswer((Answer<Void>) invocationOnMock -> {
                int count = invocationOnMock.getArgument(0);
                mCrashRecoveryPropertiesMap.put("crashrecovery.boot_mitigation_count",
                        Integer.toString(count));
                return null;
            }).when(mSpyBootThreshold).setMitigationCount(anyInt());

            doAnswer((Answer<Long>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.rescue_boot_start", "0");
                return Long.parseLong(storedValue);
            }).when(mSpyBootThreshold).getStart();
            doAnswer((Answer<Void>) invocationOnMock -> {
                long count = invocationOnMock.getArgument(0);
                mCrashRecoveryPropertiesMap.put("crashrecovery.rescue_boot_start",
                        Long.toString(count));
                return null;
            }).when(mSpyBootThreshold).setStart(anyLong());

            doAnswer((Answer<Long>) invocationOnMock -> {
                String storedValue = mCrashRecoveryPropertiesMap
                        .getOrDefault("crashrecovery.boot_mitigation_start", "0");
                return Long.parseLong(storedValue);
            }).when(mSpyBootThreshold).getMitigationStart();
            doAnswer((Answer<Void>) invocationOnMock -> {
                long count = invocationOnMock.getArgument(0);
                mCrashRecoveryPropertiesMap.put("crashrecovery.boot_mitigation_start",
                        Long.toString(count));
                return null;
            }).when(mSpyBootThreshold).setMitigationStart(anyLong());

            Field mBootThresholdField = watchdog.getClass().getDeclaredField("mBootThreshold");
            mBootThresholdField.setAccessible(true);
            mBootThresholdField.set(watchdog, mSpyBootThreshold);
        } catch (Exception e) {
            // tests will fail, just printing the error
            System.out.println("Error while spying BootThreshold " + e.getMessage());
        }
    }

    private void setCrashRecoveryPropRescueBootCount(int count) {
        mCrashRecoveryPropertiesMap.put("crashrecovery.rescue_boot_count",
                Integer.toString(count));
    }

    private void setCrashRecoveryPropAttemptingReboot(boolean value) {
        mCrashRecoveryPropertiesMap.put("crashrecovery.attempting_reboot",
                Boolean.toString(value));
    }

    private void setCrashRecoveryPropLastFactoryReset(long value) {
        mCrashRecoveryPropertiesMap.put("persist.crashrecovery.last_factory_reset",
                Long.toString(value));
    }

    private void injectTestHandler(RescuePartyObserver observer) throws Exception {
        Field mHandlerField = observer.getClass().getDeclaredField("mHandler");
        mHandlerField.setAccessible(true);
        mHandlerField.set(observer, mTestHandler);
    }
}
