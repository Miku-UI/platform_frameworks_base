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

package com.android.server.display;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CAPTURE_VIDEO_OUTPUT;
import static android.Manifest.permission.CONTROL_DISPLAY_BRIGHTNESS;
import static android.Manifest.permission.MANAGE_DISPLAYS;
import static android.Manifest.permission.MODIFY_USER_PREFERRED_DISPLAY_MODE;
import static android.Manifest.permission.WRITE_SETTINGS;
import static android.app.ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND;
import static android.hardware.display.DisplayManager.BRIGHTNESS_UNIT_NITS;
import static android.hardware.display.DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE;
import static android.hardware.display.DisplayManager.SWITCHING_TYPE_NONE;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
import static android.hardware.display.DisplayManagerGlobal.INTERNAL_EVENT_FLAG_TOPOLOGY_UPDATED;
import static android.hardware.display.HdrConversionMode.HDR_CONVERSION_SYSTEM;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS;
import static android.provider.Settings.Secure.INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY;
import static android.provider.Settings.Secure.MIRROR_BUILT_IN_DISPLAY;
import static android.view.ContentRecordingSession.RECORD_CONTENT_DISPLAY;
import static android.view.ContentRecordingSession.RECORD_CONTENT_TASK;
import static android.view.Display.HdrCapabilities.HDR_TYPE_INVALID;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.display.ExternalDisplayPolicy.ENABLE_ON_CONNECT;
import static com.android.server.display.TestUtilsKt.createInMemoryPersistentDataStore;
import static com.android.server.display.TestUtilsKt.createSensor;
import static com.android.server.display.TestUtilsKt.createTestDisplayAddress;
import static com.android.server.display.TestUtilsKt.TEST_SENSOR_TYPE;
import static com.android.server.display.VirtualDisplayAdapter.UNIQUE_ID_PREFIX;
import static com.android.server.display.config.DisplayDeviceConfigTestUtilsKt.createSensorData;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityOptions.LaunchCookie;
import android.app.PropertyInvalidatedCache;
import android.app.TaskStackListener;
import android.app.job.JobScheduler;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.devicestate.DeviceState;
import android.hardware.display.BrightnessConfiguration;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.Curve;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.DisplayManagerInternal.DisplayOffloader;
import android.hardware.display.DisplayTopology;
import android.hardware.display.DisplayTopologyGraph;
import android.hardware.display.DisplayViewport;
import android.hardware.display.DisplayedContentSample;
import android.hardware.display.DisplayedContentSamplingAttributes;
import android.hardware.display.HdrConversionMode;
import android.hardware.display.IDisplayManagerCallback;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.display.WifiDisplay;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PermissionEnforcer;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.TestLooperManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.FakePermissionEnforcer;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.test.mock.MockContentResolver;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Spline;
import android.view.ContentRecordingSession;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayCutout;
import android.view.DisplayEventReceiver;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.DisplayWindowPolicyController;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.DisplayThread;
import com.android.server.SystemService;
import com.android.server.am.BatteryStatsService;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.display.DisplayManagerService.DeviceStateListener;
import com.android.server.display.DisplayManagerService.SyncRoot;
import com.android.server.display.config.HdrBrightnessData;
import com.android.server.display.config.SensorData;
import com.android.server.display.feature.DisplayManagerFlags;
import com.android.server.display.layout.Layout;
import com.android.server.display.notifications.DisplayNotificationManager;
import com.android.server.display.plugin.PluginManager;
import com.android.server.input.InputManagerInternal;
import com.android.server.lights.LightsManager;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.sensors.SensorManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import com.google.common.truth.Expect;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

// TODO(b/297170420) Parameterize the test.
@SmallTest
@RunWith(JUnitParamsRunner.class)
public class DisplayManagerServiceTest {
    private static final int MSG_REGISTER_DEFAULT_DISPLAY_ADAPTERS = 1;
    private static final int MSG_REGISTER_ADDITIONAL_DISPLAY_ADAPTERS = 2;
    private static final long SHORT_DEFAULT_DISPLAY_TIMEOUT_MILLIS = 10;

    private static final float FLOAT_TOLERANCE = 0.01f;

    private static final String VIRTUAL_DISPLAY_NAME = "Test Virtual Display";
    private static final String PACKAGE_NAME = "com.android.frameworks.displayservicetests";
    private static final long STANDARD_DISPLAY_EVENTS =
            DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED
            | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BASIC_CHANGED
            | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE
            | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED;
    private static final long STANDARD_AND_CONNECTION_DISPLAY_EVENTS =
            STANDARD_DISPLAY_EVENTS
                    | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_CONNECTION_CHANGED;

    private static final String EVENT_DISPLAY_ADDED = "EVENT_DISPLAY_ADDED";
    private static final String EVENT_DISPLAY_REMOVED = "EVENT_DISPLAY_REMOVED";
    private static final String EVENT_DISPLAY_BASIC_CHANGED = "EVENT_DISPLAY_BASIC_CHANGED";
    private static final String EVENT_DISPLAY_BRIGHTNESS_CHANGED =
            "EVENT_DISPLAY_BRIGHTNESS_CHANGED";
    private static final String EVENT_DISPLAY_HDR_SDR_RATIO_CHANGED =
            "EVENT_DISPLAY_HDR_SDR_RATIO_CHANGED";
    private static final String EVENT_DISPLAY_CONNECTED = "EVENT_DISPLAY_CONNECTED";
    private static final String EVENT_DISPLAY_DISCONNECTED = "EVENT_DISPLAY_DISCONNECTED";
    private static final String EVENT_DISPLAY_REFRESH_RATE_CHANGED =
            "EVENT_DISPLAY_REFRESH_RATE_CHANGED";
    private static final String EVENT_DISPLAY_STATE_CHANGED = "EVENT_DISPLAY_STATE_CHANGED";
    private static final String DISPLAY_GROUP_EVENT_ADDED = "DISPLAY_GROUP_EVENT_ADDED";
    private static final String DISPLAY_GROUP_EVENT_REMOVED = "DISPLAY_GROUP_EVENT_REMOVED";
    private static final String DISPLAY_GROUP_EVENT_CHANGED = "DISPLAY_GROUP_EVENT_CHANGED";
    private static final String TOPOLOGY_CHANGED_EVENT = "TOPOLOGY_CHANGED_EVENT";

    @Rule(order = 0)
    public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Rule(order = 1)
    public Expect expect = Expect.create();
    @Rule
    public SetFlagsRule mSetFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);
    @Rule
    public FakeSettingsProviderRule mSettingsProviderRule = FakeSettingsProvider.rule();

    @Rule(order = 2)
    public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    private Context mContext;

    private Resources mResources;

    private int mHdrConversionMode;

    private int mPreferredHdrOutputType;
    private TestLooperManager mPowerLooperManager;
    private TestLooperManager mDisplayLooperManager;
    private TestLooperManager mBackgroundLooperManager;
    private UserManager mUserManager;

    private int[] mAllowedHdrOutputTypes;

    private final FakePermissionEnforcer mPermissionEnforcer = new FakePermissionEnforcer();

    private final DisplayManagerService.Injector mShortMockedInjector =
            new DisplayManagerService.Injector() {
                @Override
                VirtualDisplayAdapter getVirtualDisplayAdapter(SyncRoot syncRoot,
                        Context context, Handler handler, DisplayAdapter.Listener listener,
                        DisplayManagerFlags flags) {
                    return mMockVirtualDisplayAdapter;
                }

                @Override
                LocalDisplayAdapter getLocalDisplayAdapter(SyncRoot syncRoot, Context context,
                        Handler handler, DisplayAdapter.Listener displayAdapterListener,
                        DisplayManagerFlags flags,
                        DisplayNotificationManager displayNotificationManager) {
                    return new LocalDisplayAdapter(syncRoot, context, handler,
                            displayAdapterListener, flags,
                            mMockedDisplayNotificationManager,
                            new LocalDisplayAdapter.Injector() {
                                @Override
                                public LocalDisplayAdapter.SurfaceControlProxy
                                        getSurfaceControlProxy() {
                                    return mSurfaceControlProxy;
                                }
                            });
                }

                @Override
                long getDefaultDisplayDelayTimeout() {
                    return SHORT_DEFAULT_DISPLAY_TIMEOUT_MILLIS;
                }
            };

    class BasicInjector extends DisplayManagerService.Injector {
        @Override
        IMediaProjectionManager getProjectionService() {
            return mMockProjectionService;
        }

        @Override
        DisplayManagerFlags getFlags() {
            return mMockFlags;
        }

        @Override
        VirtualDisplayAdapter getVirtualDisplayAdapter(SyncRoot syncRoot, Context context,
                Handler handler, DisplayAdapter.Listener displayAdapterListener,
                DisplayManagerFlags flags) {
            return new VirtualDisplayAdapter(syncRoot, context, handler, displayAdapterListener,
                    new VirtualDisplayAdapter.SurfaceControlDisplayFactory() {
                        @Override
                        public IBinder createDisplay(String name, boolean secure,
                                boolean optimizeForPower, String uniqueId,
                                float requestedRefreshRate) {
                            return mMockDisplayToken;
                        }

                        @Override
                        public void destroyDisplay(IBinder displayToken) {
                        }

                        @Override
                        public void setDisplayPowerMode(IBinder displayToken, int mode) {
                        }
                    }, flags);
        }

        @Override
        LocalDisplayAdapter getLocalDisplayAdapter(SyncRoot syncRoot, Context context,
                Handler handler, DisplayAdapter.Listener displayAdapterListener,
                DisplayManagerFlags flags,
                DisplayNotificationManager displayNotificationManager) {
            return new LocalDisplayAdapter(
                    syncRoot,
                    context,
                    handler,
                    displayAdapterListener,
                    flags,
                    mMockedDisplayNotificationManager,
                    new LocalDisplayAdapter.Injector() {
                        @Override
                        public LocalDisplayAdapter.SurfaceControlProxy getSurfaceControlProxy() {
                            return mSurfaceControlProxy;
                        }
                    });
        }

        @Override
        int setHdrConversionMode(int conversionMode, int preferredHdrOutputType,
                int[] allowedHdrOutputTypes) {
            mHdrConversionMode = conversionMode;
            mPreferredHdrOutputType = preferredHdrOutputType;
            return Display.HdrCapabilities.HDR_TYPE_INVALID;
        }

        @Override
        int[] getSupportedHdrOutputTypes() {
            return new int[]{};
        }

        @Override
        int[] getHdrOutputTypesWithLatency() {
            return new int[]{Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION};
        }

        boolean getHdrOutputConversionSupport() {
            return true;
        }

        @Override
        boolean isDesktopModeSupportedOnInternalDisplay(Context context) {
            return false;
        }

        @Override
        PersistentDataStore getPersistentDataStore() {
            return createInMemoryPersistentDataStore();
        }
    }

    private final DisplayManagerService.Injector mBasicInjector = new BasicInjector();
    private final FakeSettingsProvider mFakeSettingsProvider = new FakeSettingsProvider();

    @Mock DisplayNotificationManager mMockedDisplayNotificationManager;
    @Mock IMediaProjectionManager mMockProjectionService;
    @Mock IVirtualDeviceManager mIVirtualDeviceManager;
    @Mock InputManagerInternal mMockInputManagerInternal;
    @Mock VirtualDeviceManagerInternal mMockVirtualDeviceManagerInternal;
    @Mock IVirtualDisplayCallback.Stub mMockAppToken;
    @Mock IVirtualDisplayCallback.Stub mMockAppToken2;

    @Mock IVirtualDisplayCallback.Stub mMockAppToken3;
    @Mock WindowManagerInternal mMockWindowManagerInternal;
    @Mock LightsManager mMockLightsManager;
    @Mock VirtualDisplayAdapter mMockVirtualDisplayAdapter;
    @Mock LocalDisplayAdapter.SurfaceControlProxy mSurfaceControlProxy;
    @Mock IBinder mMockDisplayToken;
    @Mock SensorManagerInternal mMockSensorManagerInternal;
    @Mock SensorManager mSensorManager;
    @Mock DisplayDeviceConfig mMockDisplayDeviceConfig;
    @Mock PackageManagerInternal mMockPackageManagerInternal;
    @Mock DisplayManagerInternal mMockDisplayManagerInternal;
    @Mock ActivityManagerInternal mMockActivityManagerInternal;
    @Mock
    ActivityTaskManagerInternal mMockActivityTaskManagerInternal;
    @Mock DisplayAdapter mMockDisplayAdapter;
    @Mock DisplayTopologyCoordinator mMockDisplayTopologyCoordinator;

    @Captor ArgumentCaptor<ContentRecordingSession> mContentRecordingSessionCaptor;
    @Mock DisplayManagerFlags mMockFlags;

    @Mock WindowManagerPolicy mMockedWindowManagerPolicy;

    @Mock IBatteryStats mMockedBatteryStats;
    @Mock WifiP2pManager mMockedWifiP2pManager;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .setStrictness(Strictness.LENIENT)
                    .spyStatic(SystemProperties.class)
                    .spyStatic(BatteryStatsService.class)
                    .build();

    private int mUniqueIdCount = 0;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLocalServiceKeeperRule.overrideLocalService(
                InputManagerInternal.class, mMockInputManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                WindowManagerInternal.class, mMockWindowManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                LightsManager.class, mMockLightsManager);
        mLocalServiceKeeperRule.overrideLocalService(
                SensorManagerInternal.class, mMockSensorManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                VirtualDeviceManagerInternal.class, mMockVirtualDeviceManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                PackageManagerInternal.class, mMockPackageManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                DisplayManagerInternal.class, mMockDisplayManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                ActivityManagerInternal.class, mMockActivityManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                ActivityTaskManagerInternal.class, mMockActivityTaskManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                WindowManagerPolicy.class, mMockedWindowManagerPolicy);
        when(BatteryStatsService.getService()).thenReturn(null);
        Display display = mock(Display.class);
        when(display.getDisplayAdjustments()).thenReturn(new DisplayAdjustments());
        when(display.getBrightnessInfo()).thenReturn(mock(BrightnessInfo.class));
        mContext = spy(new ContextWrapper(
                ApplicationProvider.getApplicationContext().createDisplayContext(display)));
        final MockContentResolver resolver = mSettingsProviderRule.mockContentResolver(mContext);
        when(mContext.getContentResolver()).thenReturn(resolver);
        resolver.addProvider(Settings.AUTHORITY, mFakeSettingsProvider);
        mResources = Mockito.spy(mContext.getResources());
        mPowerLooperManager = InstrumentationRegistry.getInstrumentation().acquireLooperManager(
                Looper.getMainLooper());
        mDisplayLooperManager = InstrumentationRegistry.getInstrumentation().acquireLooperManager(
                DisplayThread.get().getLooper());
        mBackgroundLooperManager =
                InstrumentationRegistry.getInstrumentation().acquireLooperManager(
                        BackgroundThread.getHandler().getLooper());
        manageDisplaysPermission(/* granted= */ false);
        when(mContext.getResources()).thenReturn(mResources);
        mUserManager = Mockito.spy(mContext.getSystemService(UserManager.class));
        when(mMockDisplayDeviceConfig.getTempSensor()).thenReturn(
                SensorData.loadTempSensorUnspecifiedConfig());

        when(mMockActivityTaskManagerInternal.getLockTaskModeState()).thenReturn(
                ActivityManager.LOCK_TASK_MODE_NONE);

        doReturn(Context.PERMISSION_ENFORCER_SERVICE).when(mContext).getSystemServiceName(
                eq(PermissionEnforcer.class));
        doReturn(mPermissionEnforcer).when(mContext).getSystemService(
                eq(Context.PERMISSION_ENFORCER_SERVICE));
        doReturn(mock(JobScheduler.class)).when(mContext).getSystemService(JobScheduler.class);

        VirtualDeviceManager vdm = new VirtualDeviceManager(mIVirtualDeviceManager, mContext);
        when(mContext.getSystemService(VirtualDeviceManager.class)).thenReturn(vdm);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        // Disable binder caches in this process.
        PropertyInvalidatedCache.disableForTestMode();
        setUpDisplay();
    }

    @After
    public void tearDown() {
        flushHandlers();
        mPowerLooperManager.release();
        mDisplayLooperManager.release();
        mBackgroundLooperManager.release();
    }

    private void setUpDisplay() {
        long[] ids = new long[] {100};
        when(mSurfaceControlProxy.getPhysicalDisplayIds()).thenReturn(ids);
        when(mSurfaceControlProxy.getPhysicalDisplayToken(anyLong()))
                .thenReturn(mMockDisplayToken);
        SurfaceControl.StaticDisplayInfo staticDisplayInfo = new SurfaceControl.StaticDisplayInfo();
        staticDisplayInfo.isInternal = true;
        when(mSurfaceControlProxy.getStaticDisplayInfo(anyLong()))
                .thenReturn(staticDisplayInfo);
        SurfaceControl.DynamicDisplayInfo dynamicDisplayMode =
                new SurfaceControl.DynamicDisplayInfo();
        SurfaceControl.DisplayMode displayMode = new SurfaceControl.DisplayMode();
        displayMode.width = 100;
        displayMode.height = 200;
        displayMode.supportedHdrTypes = new int[]{1, 2};
        dynamicDisplayMode.supportedDisplayModes = new SurfaceControl.DisplayMode[] {displayMode};
        when(mSurfaceControlProxy.getDynamicDisplayInfo(anyLong()))
                .thenReturn(dynamicDisplayMode);
        when(mSurfaceControlProxy.getDesiredDisplayModeSpecs(mMockDisplayToken))
                .thenReturn(new SurfaceControl.DesiredDisplayModeSpecs());
    }

    @Test
    public void testCreateVirtualDisplay_sentToInputManager() throws RemoteException {
        // This is to update the display device config such that DisplayManagerService can ignore
        // the usage of SensorManager, which is available only after the PowerManagerService
        // is ready.
        resetConfigToIgnoreSensorManager();
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.systemReady(false /* safeMode */);
        displayManager.windowManagerAndInputReady();

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Test";
        String uniqueIdPrefix = UNIQUE_ID_PREFIX + mContext.getPackageName() + ":";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setUniqueId(uniqueId);
        builder.setFlags(flags);
        int displayId = bs.createVirtualDisplay(builder.build(), mMockAppToken /* callback */,
                null /* projection */, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        ArgumentCaptor<List<DisplayViewport>> viewportCaptor = ArgumentCaptor.forClass(List.class);
        verify(mMockInputManagerInternal).setDisplayViewports(viewportCaptor.capture());
        List<DisplayViewport> viewports = viewportCaptor.getValue();

        // Expect to receive at least 2 viewports: at least 1 internal, and 1 virtual
        assertTrue(viewports.size() >= 2);

        DisplayViewport virtualViewport = null;
        DisplayViewport internalViewport = null;
        for (int i = 0; i < viewports.size(); i++) {
            DisplayViewport v = viewports.get(i);
            switch (v.type) {
                case DisplayViewport.VIEWPORT_INTERNAL: {
                    // If more than one internal viewport, this will get overwritten several times,
                    // which for the purposes of this test is fine.
                    internalViewport = v;
                    assertTrue(internalViewport.valid);
                    break;
                }
                case DisplayViewport.VIEWPORT_EXTERNAL: {
                    // External view port is present for auto devices in the form of instrument
                    // cluster.
                    break;
                }
                case DisplayViewport.VIEWPORT_VIRTUAL: {
                    virtualViewport = v;
                    break;
                }
            }
        }
        // INTERNAL viewport gets created upon access.
        assertNotNull(internalViewport);
        assertNotNull(virtualViewport);

        // VIRTUAL
        assertEquals(height, virtualViewport.deviceHeight);
        assertEquals(width, virtualViewport.deviceWidth);
        assertEquals(uniqueIdPrefix + uniqueId, virtualViewport.uniqueId);
        assertEquals(displayId, virtualViewport.displayId);
    }

    @Test
    public void testPhysicalViewports() {
        // This is to update the display device config such that DisplayManagerService can ignore
        // the usage of SensorManager, which is available only after the PowerManagerService
        // is ready.
        resetConfigToIgnoreSensorManager();
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.systemReady(false /* safeMode */);
        displayManager.windowManagerAndInputReady();

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        final int[] displayIds = bs.getDisplayIds(/* includeDisabled= */ true);
        final int size = displayIds.length;
        assertTrue(size > 0);

        Map<Integer, Integer> expectedDisplayTypeToViewPortTypeMapping = Map.of(
                Display.TYPE_INTERNAL, DisplayViewport.VIEWPORT_INTERNAL,
                Display.TYPE_EXTERNAL, DisplayViewport.VIEWPORT_EXTERNAL
        );
        for (int i = 0; i < size; i++) {
            DisplayInfo info = bs.getDisplayInfo(displayIds[i]);
            assertTrue(expectedDisplayTypeToViewPortTypeMapping.keySet().contains(info.type));
        }

        performTraversalInternal(displayManager);

        flushHandlers();

        ArgumentCaptor<List<DisplayViewport>> viewportCaptor = ArgumentCaptor.forClass(List.class);
        verify(mMockInputManagerInternal).setDisplayViewports(viewportCaptor.capture());
        List<DisplayViewport> viewports = viewportCaptor.getValue();

        // Due to the nature of foldables, we may have a different number of viewports than
        // displays, just verify there's at least one.
        final int viewportSize = viewports.size();
        assertTrue(viewportSize > 0);

        // Now verify that each viewport's displayId is valid.
        Arrays.sort(displayIds);
        for (int i = 0; i < viewportSize; i++) {
            DisplayViewport viewport = viewports.get(i);
            assertNotNull(viewport);
            DisplayInfo displayInfo = bs.getDisplayInfo(viewport.displayId);
            assertTrue(expectedDisplayTypeToViewPortTypeMapping.get(displayInfo.type)
                    == viewport.type);
            assertTrue(viewport.valid);
            assertTrue(Arrays.binarySearch(displayIds, viewport.displayId) >= 0);
        }
    }

    @Test
    public void testCreateVirtualDisplayRotatesWithContent() throws Exception {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Rotates With Content Test";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT;

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        int displayId = bs.createVirtualDisplay(builder.build(), mMockAppToken /* callback */,
                null /* projection */, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT) != 0);
    }

    @Test
    public void testCreateVirtualRotatesWithContent() throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Rotates with Content Test";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT;

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        int displayId = bs.createVirtualDisplay(builder.build(), /* callback= */ mMockAppToken,
                /* projection= */ null, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT) != 0);
    }

    @Test
    public void testCreateVirtualDisplayOwnFocus() throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Own Focus Test";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        int displayId = bs.createVirtualDisplay(builder.build(), /* callback= */ mMockAppToken,
                /* projection= */ null, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_OWN_FOCUS) != 0);
    }

    @Test
    public void testCreateVirtualDisplayOwnFocus_nonTrustedDisplay() throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Own Focus Test -- nonTrustedDisplay";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS;

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        int displayId = bs.createVirtualDisplay(builder.build(), /* callback= */ mMockAppToken,
                /* projection= */ null, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_OWN_FOCUS) == 0);
    }

    @Test
    public void testCreateVirtualDisplayStealTopFocusDisabled() throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Steal Top Focus Test";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        int displayId = bs.createVirtualDisplay(builder.build(), /* callback= */ mMockAppToken,
                /* projection= */ null, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_STEAL_TOP_FOCUS_DISABLED) != 0);
    }

    @Test
    public void testCreateVirtualDisplayOwnFocus_nonOwnFocusDisplay() throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        String uniqueId = "uniqueId --- Steal Top Focus Test -- nonOwnFocusDisplay";
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        int displayId = bs.createVirtualDisplay(builder.build(), /* callback= */ mMockAppToken,
                /* projection= */ null, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_STEAL_TOP_FOCUS_DISABLED) == 0);
    }

    @Test
    public void testCreateVirtualDisplayOwnFocus_checkDisplayDeviceInfo() throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        final String uniqueId = "uniqueId --- Own Focus Test -- checkDisplayDeviceInfo";
        float refreshRate = 60.0f;
        int width = 600;
        int height = 800;
        int dpi = 320;
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS;

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setFlags(flags);
        builder.setUniqueId(uniqueId);
        builder.setRequestedRefreshRate(refreshRate);

        // Create a virtual display in its own display group.
        final VirtualDisplayConfig ownerDisplayConfig = builder.build();
        int displayId = bs.createVirtualDisplay(ownerDisplayConfig, /* callback= */ mMockAppToken,
                /* projection= */ null, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        DisplayInfo displayInfo = bs.getDisplayInfo(displayId);
        assertNotNull(displayInfo);
        assertTrue((displayInfo.flags & DisplayDeviceInfo.FLAG_OWN_FOCUS) == 0);
        final String displayUniqueId = VirtualDisplayAdapter.generateDisplayUniqueId(
                PACKAGE_NAME, Process.myUid(), ownerDisplayConfig);
        assertEquals(displayInfo.uniqueId, displayUniqueId);
        assertEquals(displayInfo.name, VIRTUAL_DISPLAY_NAME);
        assertEquals(displayInfo.ownerPackageName, PACKAGE_NAME);
        assertEquals(displayInfo.getRefreshRate(), refreshRate, 0.1f);

        performTraversalInternal(displayManager);

        flushHandlers();

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertTrue((ddi.flags & DisplayDeviceInfo.FLAG_OWN_FOCUS) == 0);
        assertEquals(ddi.width, width);
        assertEquals(ddi.height, height);
        assertEquals(ddi.name, displayInfo.name);
        assertEquals(ddi.ownerPackageName, displayInfo.ownerPackageName);
        assertEquals(ddi.uniqueId, displayInfo.uniqueId);
        assertEquals(ddi.renderFrameRate, displayInfo.getRefreshRate(), 0.1f);
    }

    /**
     * Tests that the virtual display is created along-side the default display.
     */
    @Test
    public void testStartVirtualDisplayWithDefaultDisplay_Succeeds() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);
    }

    /**
     * Tests that we send the device state to window manager
     */
    @Test
    public void testOnStateChanged_sendsStateChangedEventToWm() throws Exception {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DeviceStateListener listener = displayManager.new DeviceStateListener();
        IDisplayManagerCallback displayChangesCallback = registerDisplayChangeCallback(
                displayManager);

        listener.onDeviceStateChanged(new DeviceState(
                new DeviceState.Configuration.Builder(123 /* identifier */,
                        "TEST" /* name */).build()));
        flushHandlers();

        InOrder inOrder = inOrder(mMockWindowManagerInternal, displayChangesCallback);
        // Verify there are no display events before WM call
        inOrder.verify(displayChangesCallback, never()).onDisplayEvent(anyInt(), anyInt());
        inOrder.verify(mMockWindowManagerInternal).onDisplayManagerReceivedDeviceState(123);
    }

    /**
     * Tests that there should be a display change notification to WindowManager to update its own
     * internal state for things like display cutout when nonOverrideDisplayInfo is changed.
     */
    @Test
    public void testShouldNotifyChangeWhenNonOverrideDisplayInfoChanged() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        // Add the FakeDisplayDevice
        FakeDisplayDevice displayDevice = new FakeDisplayDevice();
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        displayDeviceInfo.width = 100;
        displayDeviceInfo.height = 200;
        displayDeviceInfo.supportedModes = new Display.Mode[1];
        displayDeviceInfo.supportedModes[0] = new Display.Mode(1, 100, 200, 60f);
        displayDeviceInfo.modeId = 1;
        final Rect zeroRect = new Rect();
        displayDeviceInfo.displayCutout = new DisplayCutout(
                Insets.of(0, 10, 0, 0),
                zeroRect, new Rect(0, 0, 10, 10), zeroRect, zeroRect);
        displayDeviceInfo.flags = DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY;
        displayDeviceInfo.address = createTestDisplayAddress();
        displayDevice.setDisplayDeviceInfo(displayDeviceInfo);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
        flushHandlers();

        // Find the display id of the added FakeDisplayDevice
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        int displayId = getDisplayIdForDisplayDevice(displayManager, bs, displayDevice);
        // Setup override DisplayInfo
        DisplayInfo overrideInfo = bs.getDisplayInfo(displayId);
        displayManager.setDisplayInfoOverrideFromWindowManagerInternal(displayId, overrideInfo);

        FakeDisplayManagerCallback callback = registerDisplayListenerCallback(
                displayManager, bs, displayDevice);
        // Simulate DisplayDevice change
        DisplayDeviceInfo displayDeviceInfo2 = new DisplayDeviceInfo();
        displayDeviceInfo2.copyFrom(displayDeviceInfo);
        displayDeviceInfo2.displayCutout = null;
        displayDevice.setDisplayDeviceInfo(displayDeviceInfo2);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_CHANGED);

        flushHandlers();
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_BASIC_CHANGED);
    }

    /**
     * Tests that we get a Runtime exception when we cannot initialize the default display.
     */
    @Test
    public void testStartVirtualDisplayWithDefDisplay_NoDefaultDisplay() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        flushHandlers();

        try {
            displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);
        } catch (RuntimeException e) {
            return;
        }
        fail("Expected DisplayManager to throw RuntimeException when it cannot initialize the"
                + " default display");
    }

    /**
     * Tests that we get a Runtime exception when we cannot initialize the virtual display.
     */
    @Test
    public void testStartVirtualDisplayWithDefDisplay_NoVirtualDisplayAdapter() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext,
                new DisplayManagerService.Injector() {
                    @Override
                    VirtualDisplayAdapter getVirtualDisplayAdapter(SyncRoot syncRoot,
                            Context context, Handler handler, DisplayAdapter.Listener listener,
                            DisplayManagerFlags flags) {
                        return null;  // return null for the adapter.  This should cause a failure.
                    }

                    @Override
                    long getDefaultDisplayDelayTimeout() {
                        return SHORT_DEFAULT_DISPLAY_TIMEOUT_MILLIS;
                    }
                });
        try {
            displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);
        } catch (RuntimeException e) {
            return;
        }
        fail("Expected DisplayManager to throw RuntimeException when it cannot initialize the"
                + " virtual display adapter");
    }

    /**
     * Tests that an exception is raised for too dark a brightness configuration.
     */
    @Test
    public void testTooDarkBrightnessConfigurationThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        Curve minimumBrightnessCurve = displayManager.getMinimumBrightnessCurveInternal();
        float[] lux = minimumBrightnessCurve.getX();
        float[] minimumNits = minimumBrightnessCurve.getY();
        float[] nits = new float[minimumNits.length];
        // For every control point, assert that making it slightly lower than the minimum throws an
        // exception.
        for (int i = 0; i < nits.length; i++) {
            for (int j = 0; j < nits.length; j++) {
                nits[j] = minimumNits[j];
                if (j == i) {
                    nits[j] -= 0.1f;
                }
                if (nits[j] < 0) {
                    nits[j] = 0;
                }
            }
            BrightnessConfiguration config =
                    new BrightnessConfiguration.Builder(lux, nits).build();
            Exception thrown = null;
            try {
                displayManager.validateBrightnessConfiguration(config);
            } catch (IllegalArgumentException e) {
                thrown = e;
            }
            assertNotNull("Building too dark a brightness configuration must throw an exception");
        }
    }

    /**
     * Tests that no exception is raised for not too dark a brightness configuration.
     */
    @Test
    public void testBrightEnoughBrightnessConfigurationDoesNotThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        Curve minimumBrightnessCurve = displayManager.getMinimumBrightnessCurveInternal();
        float[] lux = minimumBrightnessCurve.getX();
        float[] nits = minimumBrightnessCurve.getY();
        BrightnessConfiguration config = new BrightnessConfiguration.Builder(lux, nits).build();
        displayManager.validateBrightnessConfiguration(config);
    }

    /**
     * Tests that null brightness configurations are alright.
     */
    @Test
    public void testNullBrightnessConfiguration() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        displayManager.validateBrightnessConfiguration(null);
    }

    /**
     * Tests that collection of display color sampling results are sensible.
     */
    @Test
    public void testDisplayedContentSampling() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        registerDefaultDisplays(displayManager);

        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(0);
        assertNotNull(ddi);

        DisplayedContentSamplingAttributes attr =
                displayManager.getDisplayedContentSamplingAttributesInternal(0);
        if (attr == null) return; //sampling not supported on device, skip remainder of test.

        boolean enabled = displayManager.setDisplayedContentSamplingEnabledInternal(0, true, 0, 0);
        assertTrue(enabled);

        displayManager.setDisplayedContentSamplingEnabledInternal(0, false, 0, 0);
        DisplayedContentSample sample = displayManager.getDisplayedContentSampleInternal(0, 0, 0);
        assertNotNull(sample);

        long numPixels = ddi.width * ddi.height * sample.getNumFrames();
        long[] samples = sample.getSampleComponent(DisplayedContentSample.ColorComponent.CHANNEL0);
        assertTrue(samples.length == 0 || LongStream.of(samples).sum() == numPixels);

        samples = sample.getSampleComponent(DisplayedContentSample.ColorComponent.CHANNEL1);
        assertTrue(samples.length == 0 || LongStream.of(samples).sum() == numPixels);

        samples = sample.getSampleComponent(DisplayedContentSample.ColorComponent.CHANNEL2);
        assertTrue(samples.length == 0 || LongStream.of(samples).sum() == numPixels);

        samples = sample.getSampleComponent(DisplayedContentSample.ColorComponent.CHANNEL3);
        assertTrue(samples.length == 0 || LongStream.of(samples).sum() == numPixels);
    }

    /**
     * Tests that the virtual display is created with
     * {@link VirtualDisplayConfig.Builder#setDisplayIdToMirror(int)}
     */
    @Test
    @FlakyTest(bugId = 127687569)
    public void testCreateVirtualDisplay_displayIdToMirror() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        DisplayManagerService.LocalService localDisplayManager = displayManager.new LocalService();

        final String uniqueId = "uniqueId --- displayIdToMirrorTest";
        final int width = 600;
        final int height = 800;
        final int dpi = 320;

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setUniqueId(uniqueId);
        builder.setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        when(mContext.checkCallingPermission(CAPTURE_VIDEO_OUTPUT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        final int firstDisplayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, null /* projection */, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        // The second virtual display requests to mirror the first virtual display.
        final String uniqueId2 = "uniqueId --- displayIdToMirrorTest #2";
        when(mMockAppToken2.asBinder()).thenReturn(mMockAppToken2);
        final VirtualDisplayConfig.Builder builder2 = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi).setUniqueId(uniqueId2);
        builder2.setUniqueId(uniqueId2);
        builder2.setDisplayIdToMirror(firstDisplayId);
        builder2.setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        final int secondDisplayId = binderService.createVirtualDisplay(builder2.build(),
                mMockAppToken2 /* callback */, null /* projection */,
                PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        performTraversalInternal(displayManager);

        flushHandlers();

        // The displayId to mirror should be a default display if there is none initially.
        assertEquals(localDisplayManager.getDisplayIdToMirror(firstDisplayId),
                Display.DEFAULT_DISPLAY);
        assertEquals(localDisplayManager.getDisplayIdToMirror(secondDisplayId),
                firstDisplayId);
    }

    /** Tests that a trusted virtual display is created in a device display group. */
    @Test
    public void createVirtualDisplay_addsTrustedDisplaysToDeviceDisplayGroups() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();

        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        when(mMockAppToken2.asBinder()).thenReturn(mMockAppToken2);

        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        // Create a first virtual display. A display group should be created for this display on the
        // virtual device.
        final VirtualDisplayConfig.Builder builder1 =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- device display group")
                        .setFlags(VIRTUAL_DISPLAY_FLAG_TRUSTED);
        int displayId1 =
                localService.createVirtualDisplay(
                        builder1.build(),
                        mMockAppToken2 /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        int displayGroupId1 = localService.getDisplayInfo(displayId1).displayGroupId;
        assertNotEquals(displayGroupId1, Display.DEFAULT_DISPLAY_GROUP);

        // Create a second virtual display. This should be added to the previously created display
        // group.
        final VirtualDisplayConfig.Builder builder2 =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- device display group")
                        .setFlags(VIRTUAL_DISPLAY_FLAG_TRUSTED);

        int displayId2 =
                localService.createVirtualDisplay(
                        builder2.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        int displayGroupId2 = localService.getDisplayInfo(displayId2).displayGroupId;

        assertEquals(
                "Both displays should be added to the same displayGroup.",
                displayGroupId1,
                displayGroupId2);
    }

    /** Tests that an untrusted virtual display is created in the default display group. */
    @Test
    public void createVirtualDisplay_addsUntrustedDisplayToDefaultDisplayGroups() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();

        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);
        // Create the virtual display. It is untrusted, so it should go into the default group.
        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- device display group");

        int displayId =
                localService.createVirtualDisplay(
                        builder.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        int displayGroupId = localService.getDisplayInfo(displayId).displayGroupId;
        assertEquals(displayGroupId, Display.DEFAULT_DISPLAY_GROUP);
    }

    /**
     * Tests that the virtual display is not added to the device display group when
     * VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP is set.
     */
    @Test
    public void createVirtualDisplay_addsDisplaysToOwnDisplayGroups() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();

        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        when(mMockAppToken2.asBinder()).thenReturn(mMockAppToken2);

        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        // Create a first virtual display. A display group should be created for this display on the
        // virtual device.
        final VirtualDisplayConfig.Builder builder1 =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- device display group")
                        .setFlags(VIRTUAL_DISPLAY_FLAG_TRUSTED);

        int displayId1 =
                localService.createVirtualDisplay(
                        builder1.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        int displayGroupId1 = localService.getDisplayInfo(displayId1).displayGroupId;
        assertNotEquals(displayGroupId1, Display.DEFAULT_DISPLAY_GROUP);

        // Create a second virtual display. With the flag VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP,
        // the display should not be added to the previously created display group.
        final VirtualDisplayConfig.Builder builder2 =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                                | VIRTUAL_DISPLAY_FLAG_TRUSTED)
                        .setUniqueId("uniqueId --- own display group");

        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        int displayId2 =
                localService.createVirtualDisplay(
                        builder2.build(),
                        mMockAppToken2 /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        int displayGroupId2 = localService.getDisplayInfo(displayId2).displayGroupId;
        assertNotEquals(displayGroupId2, Display.DEFAULT_DISPLAY_GROUP);

        assertNotEquals(
                "Display 1 should be in the device display group and display 2 in its own display"
                        + " group.",
                displayGroupId1,
                displayGroupId2);
    }

    @Test
    public void displaysInDeviceOrOwnDisplayGroupShouldPreserveAlwaysUnlockedFlag()
            throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();

        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        when(mMockAppToken2.asBinder()).thenReturn(mMockAppToken2);
        when(mMockAppToken3.asBinder()).thenReturn(mMockAppToken3);

        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        // Allow an ALWAYS_UNLOCKED display to be created.
        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        when(mContext.checkCallingPermission(ADD_ALWAYS_UNLOCKED_DISPLAY))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        // Create a virtual display in a device display group.
        final VirtualDisplayConfig deviceDisplayGroupDisplayConfig =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- device display group 1")
                        .setFlags(VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                                | VIRTUAL_DISPLAY_FLAG_TRUSTED)
                        .build();

        int deviceDisplayGroupDisplayId =
                localService.createVirtualDisplay(
                        deviceDisplayGroupDisplayConfig,
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        // Check that FLAG_ALWAYS_UNLOCKED is set.
        assertNotEquals(
                "FLAG_ALWAYS_UNLOCKED should be set for displays created in a device display"
                        + " group.",
                (displayManager.getDisplayDeviceInfoInternal(deviceDisplayGroupDisplayId).flags
                        & DisplayDeviceInfo.FLAG_ALWAYS_UNLOCKED),
                0);

        // Create a virtual display in its own display group.
        final VirtualDisplayConfig ownDisplayGroupConfig =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- own display group 1")
                        .setFlags(
                                VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                                        | VIRTUAL_DISPLAY_FLAG_TRUSTED
                                        | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP)
                        .build();

        int ownDisplayGroupDisplayId =
                localService.createVirtualDisplay(
                        ownDisplayGroupConfig,
                        mMockAppToken2 /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        // Check that FLAG_ALWAYS_UNLOCKED is set.
        assertNotEquals(
                "FLAG_ALWAYS_UNLOCKED should be set for displays created in their own display"
                        + " group.",
                (displayManager.getDisplayDeviceInfoInternal(ownDisplayGroupDisplayId).flags
                        & DisplayDeviceInfo.FLAG_ALWAYS_UNLOCKED),
                0);

        // Create a virtual display in a device display group.
        final VirtualDisplayConfig defaultDisplayGroupConfig =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setUniqueId("uniqueId --- default display group 1")
                        .setFlags(VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED)
                        .build();

        int defaultDisplayGroupDisplayId =
                localService.createVirtualDisplay(
                        defaultDisplayGroupConfig,
                        mMockAppToken3 /* callback */,
                        null /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        // Check that FLAG_ALWAYS_UNLOCKED is not set.
        assertEquals(
                "FLAG_ALWAYS_UNLOCKED should not be set for displays created in the default"
                        + " display group.",
                (displayManager.getDisplayDeviceInfoInternal(defaultDisplayGroupDisplayId).flags
                        & DisplayDeviceInfo.FLAG_ALWAYS_UNLOCKED),
                0);
    }

    /**
     * Tests that it's not allowed to create an auto-mirror virtual display without
     * CAPTURE_VIDEO_OUTPUT permission or a virtual device.
     */
    @Test
    public void createAutoMirrorDisplay_withoutPermission_withoutVirtualDevice_throwsException() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        when(mContext.checkCallingPermission(CAPTURE_VIDEO_OUTPUT)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                        .setUniqueId("uniqueId --- mirror display");
        assertThrows(SecurityException.class, () -> {
            localService.createVirtualDisplay(
                    builder.build(),
                    mMockAppToken /* callback */,
                    null /* virtualDeviceToken */,
                    mock(DisplayWindowPolicyController.class),
                    PACKAGE_NAME,
                    Process.myUid());
        });
    }

    /**
     * Tests that it is not allowed to create an auto-mirror virtual display for a virtual device
     * without mirror display creation capability.
     */
    @Test
    public void createAutoMirrorDisplay_withoutPermission_throwsException() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(virtualDevice.canCreateMirrorDisplays()).thenReturn(false);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);
        when(mContext.checkCallingPermission(CAPTURE_VIDEO_OUTPUT)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                        .setUniqueId("uniqueId --- mirror display");
        assertThrows(SecurityException.class, () -> {
            localService.createVirtualDisplay(
                    builder.build(),
                    mMockAppToken /* callback */,
                    virtualDevice /* virtualDeviceToken */,
                    mock(DisplayWindowPolicyController.class),
                    PACKAGE_NAME,
                    Process.myUid());
        });
    }

    /**
     * Tests that the virtual display is added to the default display group when created with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR using a virtual device.
     */
    @Test
    public void createAutoMirrorVirtualDisplay_addsDisplayToDefaultDisplayGroup() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(virtualDevice.canCreateMirrorDisplays()).thenReturn(true);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        // Create an auto-mirror virtual display using a virtual device.
        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                        .setUniqueId("uniqueId --- default display group");
        int displayId =
                localService.createVirtualDisplay(
                        builder.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());

        // The virtual display should be in the default display group.
        assertEquals(Display.DEFAULT_DISPLAY_GROUP,
                localService.getDisplayInfo(displayId).displayGroupId);
    }

    /**
     * Tests that the virtual display mirrors the default display when created with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR using a virtual device.
     */
    @Test
    public void createAutoMirrorVirtualDisplay_mirrorsDefaultDisplay() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(virtualDevice.canCreateMirrorDisplays()).thenReturn(true);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        // Create an auto-mirror virtual display using a virtual device.
        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                        .setUniqueId("uniqueId --- mirror display");
        int displayId =
                localService.createVirtualDisplay(
                        builder.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());

        // The virtual display should mirror the default display.
        assertEquals(Display.DEFAULT_DISPLAY, localService.getDisplayIdToMirror(displayId));
    }

    /**
     * Tests that the virtual display does not mirror any other display when created with
     * VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY using a virtual device.
     */
    @Test
    public void createOwnContentOnlyVirtualDisplay_doesNotMirrorAnyDisplay() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        // Create an auto-mirror virtual display using a virtual device.
        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                        .setUniqueId("uniqueId --- own content only display");
        int displayId =
                localService.createVirtualDisplay(
                        builder.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());

        // The virtual display should not mirror any display.
        assertEquals(Display.INVALID_DISPLAY, localService.getDisplayIdToMirror(displayId));
        // The virtual display should have FLAG_OWN_CONTENT_ONLY set.
        assertEquals(DisplayDeviceInfo.FLAG_OWN_CONTENT_ONLY,
                (displayManager.getDisplayDeviceInfoInternal(displayId).flags
                        & DisplayDeviceInfo.FLAG_OWN_CONTENT_ONLY));
    }

    /**
     * Tests that the virtual display should not be always unlocked when created with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR using a virtual device.
     */
    @Test
    public void createAutoMirrorVirtualDisplay_flagAlwaysUnlockedNotSet() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(virtualDevice.canCreateMirrorDisplays()).thenReturn(true);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);
        when(mContext.checkCallingPermission(ADD_ALWAYS_UNLOCKED_DISPLAY))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        // Create an auto-mirror virtual display using a virtual device.
        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                                | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED)
                        .setUniqueId("uniqueId --- mirror display");
        int displayId =
                localService.createVirtualDisplay(
                        builder.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());

        // The virtual display should not have FLAG_ALWAYS_UNLOCKED set.
        assertEquals(0, (displayManager.getDisplayDeviceInfoInternal(displayId).flags
                & DisplayDeviceInfo.FLAG_ALWAYS_UNLOCKED));
    }

    /**
     * Tests that the virtual display should not allow presentation when created with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR using a virtual device.
     */
    @Test
    public void createAutoMirrorVirtualDisplay_flagPresentationNotSet() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(virtualDevice.canCreateMirrorDisplays()).thenReturn(true);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        // Create an auto-mirror virtual display using a virtual device.
        final VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(VIRTUAL_DISPLAY_NAME, 600, 800, 320)
                        .setFlags(VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                                | VIRTUAL_DISPLAY_FLAG_PRESENTATION)
                        .setUniqueId("uniqueId --- mirror display");
        int displayId =
                localService.createVirtualDisplay(
                        builder.build(),
                        mMockAppToken /* callback */,
                        virtualDevice /* virtualDeviceToken */,
                        mock(DisplayWindowPolicyController.class),
                        PACKAGE_NAME,
                        Process.myUid());

        // The virtual display should not have FLAG_PRESENTATION set.
        assertEquals(0, (displayManager.getDisplayDeviceInfoInternal(displayId).flags
                & DisplayDeviceInfo.FLAG_PRESENTATION));
    }

    @Test
    public void testGetDisplayIdToMirror() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        DisplayManagerService.LocalService localDisplayManager = displayManager.new LocalService();

        final String uniqueId = "uniqueId --- displayIdToMirrorTest";
        final int width = 600;
        final int height = 800;
        final int dpi = 320;

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi)
                .setUniqueId(uniqueId)
                .setFlags(VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        final int firstDisplayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, null /* projection */, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        // The second virtual display requests to mirror the first virtual display.
        final String uniqueId2 = "uniqueId --- displayIdToMirrorTest #2";
        when(mMockAppToken2.asBinder()).thenReturn(mMockAppToken2);
        final VirtualDisplayConfig.Builder builder2 = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi)
                .setUniqueId(uniqueId2)
                .setWindowManagerMirroringEnabled(true);
        final int secondDisplayId = binderService.createVirtualDisplay(builder2.build(),
                mMockAppToken2 /* callback */, null /* projection */,
                PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        performTraversalInternal(displayManager);

        flushHandlers();

        // The displayId to mirror should be a invalid since the display had flag OWN_CONTENT_ONLY
        assertEquals(localDisplayManager.getDisplayIdToMirror(firstDisplayId),
                Display.INVALID_DISPLAY);
        // The second display has mirroring managed by WindowManager so the mirror displayId should
        // be invalid.
        assertEquals(localDisplayManager.getDisplayIdToMirror(secondDisplayId),
                Display.INVALID_DISPLAY);
    }

    @Test
    public void testGetDisplayIdsForGroup() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerInternal localService = displayManager.new LocalService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        // Create display 1
        FakeDisplayDevice displayDevice1 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        LogicalDisplay display1 = logicalDisplayMapper.getDisplayLocked(displayDevice1);
        final int groupId1 = display1.getDisplayInfoLocked().displayGroupId;
        // Create display 2
        FakeDisplayDevice displayDevice2 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        LogicalDisplay display2 = logicalDisplayMapper.getDisplayLocked(displayDevice2);
        final int groupId2 = display2.getDisplayInfoLocked().displayGroupId;
        // Both displays should be in the same display group
        assertEquals(groupId1, groupId2);
        final int[] expectedDisplayIds = new int[]{
                display1.getDisplayIdLocked(), display2.getDisplayIdLocked()};

        final int[] displayIds = localService.getDisplayIdsForGroup(groupId1);

        assertArrayEquals(expectedDisplayIds, displayIds);
    }

    @Test
    public void testGetDisplayIdsForUnknownGroup() throws Exception {
        final int unknownDisplayGroupId = 999;
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerInternal localService = displayManager.new LocalService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        // Verify that display manager does not have display group
        assertNull(logicalDisplayMapper.getDisplayGroupLocked(unknownDisplayGroupId));

        final int[] displayIds = localService.getDisplayIdsForGroup(unknownDisplayGroupId);

        assertEquals(0, displayIds.length);
    }

    @Test
    public void testGetDisplayIdsByGroupsIds() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerInternal localService = displayManager.new LocalService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        // Create display 1
        FakeDisplayDevice displayDevice1 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        LogicalDisplay display1 = logicalDisplayMapper.getDisplayLocked(displayDevice1);
        final int groupId1 = display1.getDisplayInfoLocked().displayGroupId;
        // Create display 2
        FakeDisplayDevice displayDevice2 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        LogicalDisplay display2 = logicalDisplayMapper.getDisplayLocked(displayDevice2);
        final int groupId2 = display2.getDisplayInfoLocked().displayGroupId;
        // Both displays should be in the same display group
        assertEquals(groupId1, groupId2);
        final int[] displayIds = new int[]{
                display1.getDisplayIdLocked(), display2.getDisplayIdLocked()};
        final SparseArray<int[]> expectedDisplayGroups = new SparseArray<>();
        expectedDisplayGroups.put(groupId1, displayIds);

        final SparseArray<int[]> displayGroups = localService.getDisplayIdsByGroupsIds();

        for (int i = 0; i < expectedDisplayGroups.size(); i++) {
            final int groupId = expectedDisplayGroups.keyAt(i);
            assertTrue(displayGroups.contains(groupId));
            assertArrayEquals(expectedDisplayGroups.get(groupId), displayGroups.get(groupId));
        }
    }

    @Test
    public void testGetDisplayIdsByGroupsIds_multipleDisplayGroups() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerInternal localService = displayManager.new LocalService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        // Create display 1
        FakeDisplayDevice displayDevice1 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        LogicalDisplay display1 = logicalDisplayMapper.getDisplayLocked(displayDevice1);
        final int groupId1 = display1.getDisplayInfoLocked().displayGroupId;
        // Create display 2
        FakeDisplayDevice displayDevice2 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        LogicalDisplay display2 = logicalDisplayMapper.getDisplayLocked(displayDevice2);
        final int groupId2 = display2.getDisplayInfoLocked().displayGroupId;
        // Both displays should be in different display groups
        assertNotEquals(groupId1, groupId2);
        final SparseArray<int[]> expectedDisplayGroups = new SparseArray<>();
        expectedDisplayGroups.put(groupId1, new int[]{display1.getDisplayIdLocked()});
        expectedDisplayGroups.put(groupId2, new int[]{display2.getDisplayIdLocked()});

        final SparseArray<int[]> displayGroups = localService.getDisplayIdsByGroupsIds();

        assertEquals(expectedDisplayGroups.size(), displayGroups.size());
        for (int i = 0; i < expectedDisplayGroups.size(); i++) {
            final int groupId = expectedDisplayGroups.keyAt(i);
            assertTrue(displayGroups.contains(groupId));
            assertArrayEquals(expectedDisplayGroups.get(groupId), displayGroups.get(groupId));
        }
    }

    @Test
    public void testCreateVirtualDisplay_isValidProjection_notValid()
            throws RemoteException {
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IMediaProjection projection = mock(IMediaProjection.class);
        doReturn(false).when(projection).isValid();
        when(mMockProjectionService
                .setContentRecordingSession(any(ContentRecordingSession.class), eq(projection)))
                .thenReturn(true);
        doReturn(true).when(mMockProjectionService).isCurrentProjection(eq(projection));

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- isValid false");

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        // Pass in a non-null projection.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, projection, PACKAGE_NAME);

        // VirtualDisplay is created for mirroring.
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        verify(mMockProjectionService, atLeastOnce()).setContentRecordingSession(
                any(ContentRecordingSession.class), nullable(IMediaProjection.class));
        // But mirroring doesn't begin.
        verify(mMockProjectionService, atLeastOnce()).setContentRecordingSession(
                mContentRecordingSessionCaptor.capture(), nullable(IMediaProjection.class));
        ContentRecordingSession session = mContentRecordingSessionCaptor.getValue();
        assertThat(session.isWaitingForConsent()).isTrue();
    }

    @Test
    public void testCreateVirtualDisplay_setContentRecordingSessionSuccess()
            throws RemoteException {
        final int displayToRecord = 50;
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IMediaProjection projection = mock(IMediaProjection.class);
        doReturn(true).when(projection).isValid();
        when(mMockProjectionService
                .setContentRecordingSession(any(ContentRecordingSession.class), eq(projection)))
                .thenReturn(true);
        doReturn(true).when(mMockProjectionService).isCurrentProjection(eq(projection));

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- setContentRecordingSession true");
        builder.setDisplayIdToMirror(displayToRecord);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, projection, PACKAGE_NAME);

        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        verify(mMockProjectionService, atLeastOnce()).setContentRecordingSession(
                mContentRecordingSessionCaptor.capture(), nullable(IMediaProjection.class));
        ContentRecordingSession session = mContentRecordingSessionCaptor.getValue();
        assertThat(session.getContentToRecord()).isEqualTo(RECORD_CONTENT_DISPLAY);
        assertThat(session.getVirtualDisplayId()).isEqualTo(displayId);
        assertThat(session.getDisplayToRecord()).isEqualTo(displayToRecord);
        assertThat(session.isWaitingForConsent()).isFalse();
    }

    @Test
    public void testCreateVirtualDisplay_setContentRecordingSessionFail() throws RemoteException {
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IMediaProjection projection = mock(IMediaProjection.class);
        doReturn(true).when(projection).isValid();
        when(mMockProjectionService
                .setContentRecordingSession(any(ContentRecordingSession.class), eq(projection)))
                .thenReturn(false);
        doReturn(true).when(mMockProjectionService).isCurrentProjection(eq(projection));

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- setContentRecordingSession false");

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, projection, PACKAGE_NAME);

        assertThat(displayId).isEqualTo(Display.INVALID_DISPLAY);
    }

    @Test
    public void testCreateVirtualDisplay_setContentRecordingSession_taskSession()
            throws RemoteException {
        final int displayToRecord = 50;
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IMediaProjection projection = mock(IMediaProjection.class);
        doReturn(true).when(projection).isValid();
        when(mMockProjectionService
                .setContentRecordingSession(any(ContentRecordingSession.class), eq(projection)))
                .thenReturn(true);
        doReturn(new LaunchCookie()).when(projection).getLaunchCookie();
        doReturn(true).when(mMockProjectionService).isCurrentProjection(eq(projection));

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- setContentRecordingSession false");
        builder.setDisplayIdToMirror(displayToRecord);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, projection, PACKAGE_NAME);

        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        verify(mMockProjectionService, atLeastOnce()).setContentRecordingSession(
                mContentRecordingSessionCaptor.capture(), nullable(IMediaProjection.class));
        ContentRecordingSession session = mContentRecordingSessionCaptor.getValue();
        assertThat(session.getContentToRecord()).isEqualTo(RECORD_CONTENT_TASK);
        assertThat(session.getVirtualDisplayId()).isEqualTo(displayId);
        assertThat(session.getTokenToRecord()).isNotNull();
    }

    @Test
    public void testCreateVirtualDisplay_setContentRecordingSession_noProjection_noFlags()
            throws RemoteException {
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        // Set no flags for the VirtualDisplay.
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- setContentRecordingSession false");

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        // Pass in a null projection.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, null /* projection */, PACKAGE_NAME);

        // VirtualDisplay is created but not for mirroring.
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        verify(mMockProjectionService, never()).setContentRecordingSession(
                any(ContentRecordingSession.class), nullable(IMediaProjection.class));
    }

    @Test
    public void testCreateVirtualDisplay_setContentRecordingSession_noProjection_noMirroringFlag()
            throws RemoteException {
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        // Set a non-mirroring flag for the VirtualDisplay.
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- setContentRecordingSession false");
        builder.setFlags(VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        // Pass in a null projection.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, null /* projection */, PACKAGE_NAME);

        // VirtualDisplay is created but not for mirroring.
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        verify(mMockProjectionService, never()).setContentRecordingSession(
                any(ContentRecordingSession.class), nullable(IMediaProjection.class));
    }

    @Test
    public void testCreateVirtualDisplay_setContentRecordingSession_projection_noMirroringFlag()
            throws RemoteException {
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        IMediaProjection projection = mock(IMediaProjection.class);
        doReturn(true).when(projection).isValid();
        when(mMockProjectionService
                .setContentRecordingSession(any(ContentRecordingSession.class), eq(projection)))
                .thenReturn(true);
        doReturn(true).when(mMockProjectionService).isCurrentProjection(eq(projection));

        // Set no flags for the VirtualDisplay.
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setUniqueId("uniqueId --- setContentRecordingSession false");

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);
        displayManager.windowManagerAndInputReady();

        // Pass in a non-null projection.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, projection, PACKAGE_NAME);

        // VirtualDisplay is created for mirroring.
        assertThat(displayId).isNotEqualTo(Display.INVALID_DISPLAY);
        verify(mMockProjectionService, atLeastOnce()).setContentRecordingSession(
                any(ContentRecordingSession.class), nullable(IMediaProjection.class));
    }

    /**
     * Tests that the virtual display is created with
     * {@link VirtualDisplayConfig.Builder#setSurface(Surface)}
     */
    @Test
    public void testCreateVirtualDisplay_setSurface() throws Exception {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        // This is effectively the DisplayManager service published to ServiceManager.
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();

        final String uniqueId = "uniqueId --- setSurface";
        final int width = 600;
        final int height = 800;
        final int dpi = 320;
        final Surface surface = new Surface();

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);
        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, width, height, dpi);
        builder.setSurface(surface);
        builder.setUniqueId(uniqueId);
        final int displayId = binderService.createVirtualDisplay(builder.build(),
                mMockAppToken /* callback */, null /* projection */, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));

        performTraversalInternal(displayManager);

        flushHandlers();

        assertEquals(displayManager.getVirtualDisplaySurfaceInternal(mMockAppToken), surface);
    }

    /**
     * Tests that specifying VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP is allowed when the permission
     * ADD_TRUSTED_DISPLAY is granted and that display is not in the default display group.
     */
    @Test
    public void testOwnDisplayGroup_allowCreationWithAddTrustedDisplayPermission()
            throws RemoteException {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP);
        builder.setUniqueId("uniqueId --- OWN_DISPLAY_GROUP");

        int displayId = bs.createVirtualDisplay(builder.build(), mMockAppToken /* callback */,
                null /* projection */, PACKAGE_NAME);
        verify(mMockProjectionService, never()).setContentRecordingSession(any(),
                nullable(IMediaProjection.class));
        performTraversalInternal(displayManager);
        flushHandlers();
        DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayId);
        assertNotNull(ddi);
        assertNotEquals(0, ddi.flags & DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);

        int displayGroupId = bs.getDisplayInfo(displayId).displayGroupId;
        assertNotEquals(displayGroupId, Display.DEFAULT_DISPLAY_GROUP);
    }

    /**
     * Tests that specifying VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP is blocked when the permission
     * ADD_TRUSTED_DISPLAY is denied.
     */
    @Test
    public void testOwnDisplayGroup_withoutAddTrustedDisplayPermission_throwsSecurityException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        registerDefaultDisplays(displayManager);

        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP);
        builder.setUniqueId("uniqueId --- OWN_DISPLAY_GROUP");

        try {
            bs.createVirtualDisplay(builder.build(), mMockAppToken /* callback */,
                    null /* projection */, PACKAGE_NAME);
            fail("Creating virtual display with VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP without "
                    + "ADD_TRUSTED_DISPLAY permission should throw SecurityException.");
        } catch (SecurityException e) {
            // SecurityException is expected
        }
    }

    /**
     * Tests that specifying VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP is not allowed when called with
     * a virtual device, if ADD_TRUSTED_DISPLAY is not granted.
     */
    @Test
    public void testOwnDisplayGroup_disallowCreationWithVirtualDevice() throws Exception {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();

        registerDefaultDisplays(displayManager);

        when(mMockAppToken.asBinder()).thenReturn(mMockAppToken);

        when(mContext.checkCallingPermission(ADD_TRUSTED_DISPLAY)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        final VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                VIRTUAL_DISPLAY_NAME, 600, 800, 320);
        builder.setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP);
        builder.setUniqueId("uniqueId --- OWN_DISPLAY_GROUP");

        IVirtualDevice virtualDevice = mock(IVirtualDevice.class);
        when(virtualDevice.getDeviceId()).thenReturn(1);
        when(mIVirtualDeviceManager.isValidVirtualDeviceId(1)).thenReturn(true);

        try {
            localService.createVirtualDisplay(builder.build(),
                    mMockAppToken /* callback */, virtualDevice /* virtualDeviceToken */,
                    mock(DisplayWindowPolicyController.class), PACKAGE_NAME, Process.myUid());
            fail("Creating virtual display with VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP without "
                    + "ADD_TRUSTED_DISPLAY permission should throw SecurityException even if "
                    + "called with a virtual device.");
        } catch (SecurityException e) {
            // SecurityException is expected
        }
    }

    @Test
    public void test_displayChangedNotified_displayInfoFramerateOverridden() {
        when(mMockFlags.isFramerateOverrideTriggersRrCallbacksEnabled()).thenReturn(false);

        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();

        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        FakeDisplayManagerCallback callback = registerDisplayListenerCallback(displayManager,
                displayManagerBinderService, displayDevice);

        int myUid = Process.myUid();
        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(myUid, 30f),
                });
        flushHandlers();
        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_BASIC_CHANGED);
        callback.clear();
    }

    /**
     * Tests that there is a display change notification if the frame rate override
     * list is updated.
     */
    @Test
    public void test_refreshRateChangedNotified_displayInfoFramerateOverridden() {
        when(mMockFlags.isFramerateOverrideTriggersRrCallbacksEnabled()).thenReturn(true);

        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        FakeDisplayManagerCallback callback = registerDisplayListenerCallback(displayManager,
                displayManagerBinderService, displayDevice);

        int myUid = Process.myUid();
        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(myUid, 30f),
                });
        flushHandlers();
        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_REFRESH_RATE_CHANGED);
        callback.clear();

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(myUid, 30f),
                        new DisplayEventReceiver.FrameRateOverride(1234, 30f),
                });
        flushHandlers();
        assertThat(callback.receivedEvents()).doesNotContain(EVENT_DISPLAY_REFRESH_RATE_CHANGED);

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(myUid, 20f),
                        new DisplayEventReceiver.FrameRateOverride(1234, 30f),
                        new DisplayEventReceiver.FrameRateOverride(5678, 30f),
                });
        flushHandlers();
        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_REFRESH_RATE_CHANGED);
        callback.clear();

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(1234, 30f),
                        new DisplayEventReceiver.FrameRateOverride(5678, 30f),
                });
        flushHandlers();
        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_REFRESH_RATE_CHANGED);
        callback.clear();

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(5678, 30f),
                });
        flushHandlers();
        assertThat(callback.receivedEvents()).doesNotContain(EVENT_DISPLAY_REFRESH_RATE_CHANGED);
    }

    /**
     * Tests that the DisplayInfo is updated correctly with a frame rate override
     */
    @Test
    public void testDisplayInfoFrameRateOverride() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f, 30f, 20f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(60f, displayInfo.getRefreshRate(), 0.01f);

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(
                                Process.myUid(), 20f),
                        new DisplayEventReceiver.FrameRateOverride(
                                Process.myUid() + 1, 30f)
                });
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(20f, displayInfo.getRefreshRate(), 0.01f);

        // Changing the mode to 30Hz should not override the refresh rate to 20Hz anymore
        // as 20 is not a divider of 30.
        updateModeId(displayManager, displayDevice, 2);
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(30f, displayInfo.getRefreshRate(), 0.01f);
    }

    /**
     * Tests that the frame rate override is returning the correct value from
     * DisplayInfo#getRefreshRate
     */
    @Test
    public void testDisplayInfoNonNativeFrameRateOverride() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(60f, displayInfo.getRefreshRate(), 0.01f);

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(
                                Process.myUid(), 20f)
                });
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(20f, displayInfo.getRefreshRate(), 0.01f);
    }

    /**
     * Tests that the mode reflects the frame rate override is in compat mode
     */
    @Test
    @DisableCompatChanges({DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE})
    public  void testDisplayInfoFrameRateOverrideModeCompat() {
        testDisplayInfoFrameRateOverrideModeCompat(/*compatChangeEnabled*/ false);
    }

    /**
     * Tests that the mode reflects the physical display refresh rate when not in compat mode.
     */
    @Test
    @EnableCompatChanges({DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE})
    public  void testDisplayInfoFrameRateOverrideMode() {
        testDisplayInfoFrameRateOverrideModeCompat(/*compatChangeEnabled*/ true);
    }

    /**
     * Tests that the mode reflects the frame rate override is in compat mode and accordingly to the
     * allowNonNativeRefreshRateOverride policy.
     */
    @Test
    @DisableCompatChanges({DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE})
    public void testDisplayInfoNonNativeFrameRateOverrideModeCompat() {
        testDisplayInfoNonNativeFrameRateOverrideMode(/*compatChangeEnabled*/ false);
    }

    /**
     * Tests that the mode reflects the physical display refresh rate when not in compat mode.
     */
    @Test
    @EnableCompatChanges({DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE})
    public void testDisplayInfoNonNativeFrameRateOverrideMode() {
        testDisplayInfoNonNativeFrameRateOverrideMode(/*compatChangeEnabled*/ true);
    }

    /**
     * Tests that there is a display change notification if the render frame rate is updated
     */
    @Test
    public void testShouldNotifyChangeWhenDisplayInfoRenderFrameRateChanged() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        FakeDisplayManagerCallback callback = registerDisplayListenerCallback(displayManager,
                displayManagerBinderService, displayDevice);

        updateRenderFrameRate(displayManager, displayDevice, 30f);
        flushHandlers();
        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_REFRESH_RATE_CHANGED);
        callback.clear();

        updateRenderFrameRate(displayManager, displayDevice, 30f);
        flushHandlers();
        assertThat(callback.receivedEvents()).doesNotContain(EVENT_DISPLAY_REFRESH_RATE_CHANGED);

        updateRenderFrameRate(displayManager, displayDevice, 20f);
        flushHandlers();
        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_REFRESH_RATE_CHANGED);
        callback.clear();
    }

    @Test
    public void test_doesNotNotifyRefreshRateChanged_whenAppInBackground() {
        when(mMockFlags.isRefreshRateEventForForegroundAppsEnabled()).thenReturn(true);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        displayManager.windowManagerAndInputReady();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        FakeDisplayManagerCallback callback = registerDisplayListenerCallback(displayManager,
                displayManagerBinderService, displayDevice);

        when(mMockActivityManagerInternal.getUidProcessState(Process.myUid()))
                .thenReturn(PROCESS_STATE_TRANSIENT_BACKGROUND);
        updateRenderFrameRate(displayManager, displayDevice, 30f);
        flushHandlers();
        assertEquals(0, callback.receivedEvents().size());
        callback.clear();
    }

    /**
     * Tests that the DisplayInfo is updated correctly with a render frame rate
     */
    @Test
    public void testDisplayInfoRenderFrameRate() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f, 30f, 20f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(60f, displayInfo.getRefreshRate(), 0.01f);

        updateRenderFrameRate(displayManager, displayDevice, 20f);
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(20f, displayInfo.getRefreshRate(), 0.01f);
    }

    /**
     * Tests that the DisplayInfo is updated correctly with a render frame rate even if it not
     * a divisor of the peak refresh rate.
     */
    @Test
    public void testDisplayInfoRenderFrameRateNonPeakDivisor() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{120f}, new float[]{240f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(120f, displayInfo.getRefreshRate(), 0.01f);

        updateRenderFrameRate(displayManager, displayDevice, 80f);
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(80f, displayInfo.getRefreshRate(), 0.01f);
    }

    /**
     * Tests that the mode reflects the render frame rate is in compat mode
     */
    @Test
    @DisableCompatChanges({DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE})
    public  void testDisplayInfoRenderFrameRateModeCompat() {
        testDisplayInfoRenderFrameRateModeCompat(/*compatChangeEnabled*/ false);
    }

    /**
     * Tests that the mode reflects the physical display refresh rate when not in compat mode.
     */
    @Test
    @EnableCompatChanges({DisplayManagerService.DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE})
    public  void testDisplayInfoRenderFrameRateMode() {
        testDisplayInfoRenderFrameRateModeCompat(/*compatChangeEnabled*/ true);
    }

    /**
     * Tests that EVENT_DISPLAY_ADDED is sent when a display is added.
     */
    @Test
    public void testShouldNotifyDisplayAdded_WhenNewDisplayDeviceIsAdded() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();

        flushHandlers();

        // register display listener callback
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        displayManagerBinderService.registerCallbackWithEventMask(
                callback, STANDARD_DISPLAY_EVENTS);

        flushHandlers();

        createFakeDisplayDevice(displayManager, new float[]{60f});

        flushHandlers();

        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_ADDED);
    }

    /**
     * Tests that EVENT_DISPLAY_ADDED is not sent when a display is added and the
     * client has a callback which is not subscribed to this event type.
     */
    @Test
    public void testShouldNotNotifyDisplayAdded_WhenClientIsNotSubscribed() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();

        flushHandlers();

        // register display listener callback
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        long allEventsExceptDisplayAdded = STANDARD_DISPLAY_EVENTS
                & ~DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED;
        displayManagerBinderService.registerCallbackWithEventMask(callback,
                allEventsExceptDisplayAdded);

        flushHandlers();

        createFakeDisplayDevice(displayManager, new float[]{60f});

        flushHandlers();

        assertThat(callback.receivedEvents()).isEmpty();
    }

    /**
     * Tests that EVENT_DISPLAY_REMOVED is sent when a display is removed.
     */
    @Test
    public void testShouldNotifyDisplayRemoved_WhenDisplayDeviceIsRemoved() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();

        flushHandlers();

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f});
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);

        flushHandlers();

        FakeDisplayManagerCallback callback = registerDisplayListenerCallback(
                displayManager, displayManagerBinderService, displayDevice);

        flushHandlers();

        display.setPrimaryDisplayDeviceLocked(null);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
        flushHandlers();

        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_REMOVED);
    }

    /**
     * Tests that EVENT_DISPLAY_REMOVED is not sent when a display is added and the
     * client has a callback which is not subscribed to this event type.
     */
    @Test
    public void testShouldNotNotifyDisplayRemoved_WhenClientIsNotSubscribed() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();

        flushHandlers();

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f});
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);

        flushHandlers();

        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        long allEventsExceptDisplayRemoved = STANDARD_DISPLAY_EVENTS
                & ~DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED;
        displayManagerBinderService.registerCallbackWithEventMask(callback,
                allEventsExceptDisplayRemoved);

        flushHandlers();

        display.setPrimaryDisplayDeviceLocked(null);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
        flushHandlers();

        assertThat(callback.receivedEvents()).isEmpty();
    }



    @Test
    public void testSettingTwoBrightnessConfigurationsOnMultiDisplay() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);

        // get the first two internal displays
        Display[] displays = displayManager.getDisplays();
        Display internalDisplayOne = null;
        Display internalDisplayTwo = null;
        for (Display display : displays) {
            if (display.getType() == Display.TYPE_INTERNAL) {
                if (internalDisplayOne == null) {
                    internalDisplayOne = display;
                } else {
                    internalDisplayTwo = display;
                    break;
                }
            }
        }

        // return if there are fewer than 2 displays on this device
        if (internalDisplayOne == null || internalDisplayTwo == null) {
            return;
        }

        final String uniqueDisplayIdOne = internalDisplayOne.getUniqueId();
        final String uniqueDisplayIdTwo = internalDisplayTwo.getUniqueId();

        BrightnessConfiguration configOne =
                new BrightnessConfiguration.Builder(
                        new float[]{0.0f, 12345.0f}, new float[]{15.0f, 400.0f})
                        .setDescription("model:1").build();
        BrightnessConfiguration configTwo =
                new BrightnessConfiguration.Builder(
                        new float[]{0.0f, 6789.0f}, new float[]{12.0f, 300.0f})
                        .setDescription("model:2").build();

        displayManager.setBrightnessConfigurationForDisplay(configOne,
                uniqueDisplayIdOne);
        displayManager.setBrightnessConfigurationForDisplay(configTwo,
                uniqueDisplayIdTwo);

        BrightnessConfiguration configFromOne =
                displayManager.getBrightnessConfigurationForDisplay(uniqueDisplayIdOne);
        BrightnessConfiguration configFromTwo =
                displayManager.getBrightnessConfigurationForDisplay(uniqueDisplayIdTwo);

        assertNotNull(configFromOne);
        assertEquals(configOne, configFromOne);
        assertEquals(configTwo, configFromTwo);

    }

    @Test
    public void testHdrConversionModeEquals() {
        assertEquals(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE, 2),
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE, 2));
        assertNotEquals(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE, 2),
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE, 3));
        assertEquals(
                new HdrConversionMode(HDR_CONVERSION_SYSTEM),
                new HdrConversionMode(HDR_CONVERSION_SYSTEM));
        assertNotEquals(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE, 2),
                new HdrConversionMode(HDR_CONVERSION_SYSTEM));
    }

    @Test
    public void testCreateHdrConversionMode_withInvalidArguments_throwsException() {
        assertThrows(
                "preferredHdrOutputType must not be set if the conversion mode is "
                        + "HDR_CONVERSION_PASSTHROUGH",
                IllegalArgumentException.class,
                () -> new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH,
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION));
    }

    @Test
    public void testSetHdrConversionModeInternal_withInvalidArguments_throwsException() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        assertThrows("Expected DisplayManager to throw IllegalArgumentException when "
                        + "preferredHdrOutputType is set and the conversion mode is "
                        + "HDR_CONVERSION_SYSTEM",
                IllegalArgumentException.class,
                () -> displayManager.setHdrConversionModeInternal(new HdrConversionMode(
                        HDR_CONVERSION_SYSTEM,
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)));
    }

    @Test
    public void testSetAndGetHdrConversionModeInternal() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        final HdrConversionMode mode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_FORCE,
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION);
        displayManager.setHdrConversionModeInternal(mode);

        assertEquals(mode, displayManager.getHdrConversionModeSettingInternal());
        assertEquals(mode.getConversionMode(), mHdrConversionMode);
        assertEquals(mode.getPreferredHdrOutputType(), mPreferredHdrOutputType);
    }

    @Test
    public void testHdrConversionMode_withMinimalPostProcessing() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f, 30f, 20f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);

        final HdrConversionMode mode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_FORCE,
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION);
        displayManager.setHdrConversionModeInternal(mode);
        assertEquals(mode, displayManager.getHdrConversionModeSettingInternal());

        displayManager.setMinimalPostProcessingAllowed(true);
        displayManager.setDisplayPropertiesInternal(displayId, false /* hasContent */,
                30.0f /* requestedRefreshRate */,
                displayDevice.getDisplayDeviceInfoLocked().modeId /* requestedModeId */,
                30.0f /* requestedMinRefreshRate */, 120.0f /* requestedMaxRefreshRate */,
                true /* preferMinimalPostProcessing */, false /* disableHdrConversion */,
                true /* inTraversal */);

        assertEquals(new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH),
                displayManager.getHdrConversionModeInternal());

        displayManager.setDisplayPropertiesInternal(displayId, false /* hasContent */,
                30.0f /* requestedRefreshRate */,
                displayDevice.getDisplayDeviceInfoLocked().modeId /* requestedModeId */,
                30.0f /* requestedMinRefreshRate */, 120.0f /* requestedMaxRefreshRate */,
                false /* preferMinimalPostProcessing */, false /* disableHdrConversion */,
                true /* inTraversal */);
        assertEquals(mode, displayManager.getHdrConversionModeInternal());
    }

    @Test
    public void testSetAreUserDisabledHdrTypesAllowed_withFalse_whenHdrDisabled_stripsHdrType() {
        DisplayManagerService displayManager = new DisplayManagerService(
                mContext, new BasicInjector() {
                    @Override
                    int setHdrConversionMode(int conversionMode, int preferredHdrOutputType,
                            int[] allowedTypes) {
                        mAllowedHdrOutputTypes = allowedTypes;
                        return Display.HdrCapabilities.HDR_TYPE_INVALID;
                    }

                    // Overriding this method to capture the allowed HDR type
                    @Override
                    int[] getSupportedHdrOutputTypes() {
                        return new int[]{Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION};
                    }
                });

        // Setup: no HDR types disabled, userDisabledTypes allowed, system conversion
        displayManager.setUserDisabledHdrTypesInternal(new int [0]);
        displayManager.setAreUserDisabledHdrTypesAllowedInternal(true);
        displayManager.setHdrConversionModeInternal(
                new HdrConversionMode(HDR_CONVERSION_SYSTEM));

        assertEquals(1, mAllowedHdrOutputTypes.length);
        assertTrue(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION == mAllowedHdrOutputTypes[0]);

        // Action: disable Dolby Vision, set userDisabledTypes not allowed
        displayManager.setUserDisabledHdrTypesInternal(
                new int [] {Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION});
        displayManager.setAreUserDisabledHdrTypesAllowedInternal(false);

        assertEquals(0, mAllowedHdrOutputTypes.length);
    }

    @Test
    public void testGetEnabledHdrTypesLocked_whenTypesDisabled_stripsDisabledTypes() {
        DisplayManagerService displayManager = new DisplayManagerService(
                mContext, new BasicInjector() {
                    @Override
                    int[] getSupportedHdrOutputTypes() {
                        return new int[]{Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION};
                    }
                });

        displayManager.setUserDisabledHdrTypesInternal(new int [0]);
        displayManager.setAreUserDisabledHdrTypesAllowedInternal(true);
        int [] enabledHdrOutputTypes = displayManager.getEnabledHdrOutputTypes();
        assertEquals(1, enabledHdrOutputTypes.length);
        assertTrue(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION == enabledHdrOutputTypes[0]);

        displayManager.setAreUserDisabledHdrTypesAllowedInternal(false);
        enabledHdrOutputTypes = displayManager.getEnabledHdrOutputTypes();
        assertEquals(1, enabledHdrOutputTypes.length);
        assertTrue(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION == enabledHdrOutputTypes[0]);

        displayManager.setUserDisabledHdrTypesInternal(
                new int [] {Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION});
        enabledHdrOutputTypes = displayManager.getEnabledHdrOutputTypes();
        assertEquals(0, enabledHdrOutputTypes.length);
    }

    @Test
    public void testSetHdrConversionModeInternal_isForceSdrIsUpdated() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        LogicalDisplay logicalDisplay =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);

        displayManager.setHdrConversionModeInternal(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE, HDR_TYPE_INVALID));
        assertTrue(logicalDisplay.getDisplayInfoLocked().isForceSdr);

        displayManager.setHdrConversionModeInternal(
                new HdrConversionMode(HDR_CONVERSION_SYSTEM));
        assertFalse(logicalDisplay.getDisplayInfoLocked().isForceSdr);
    }

    @Test
    public void testReturnsRefreshRateForDisplayAndSensor_proximitySensorSet() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        displayManager.overrideSensorManager(mSensorManager);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        displayDevice.mDisplayDeviceConfig = mMockDisplayDeviceConfig;
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);

        String testSensorName = "testName";
        String testSensorType = "testType";
        Sensor testSensor = createSensor(TEST_SENSOR_TYPE, testSensorType, testSensorName);

        SensorData sensorData = createSensorData(testSensorType, testSensorName,
                /* minRefreshRate= */ 10f, /* maxRefreshRate= */ 100f);

        when(mMockDisplayDeviceConfig.getProximitySensor()).thenReturn(sensorData);
        when(mSensorManager.getSensorList(Sensor.TYPE_ALL)).thenReturn(Collections.singletonList(
                testSensor));

        SurfaceControl.RefreshRateRange result = localService.getRefreshRateForDisplayAndSensor(
                displayId, testSensorName, testSensorType);

        assertNotNull(result);
        assertEquals(result.min, sensorData.minRefreshRate, FLOAT_TOLERANCE);
        assertEquals(result.max, sensorData.maxRefreshRate, FLOAT_TOLERANCE);
    }

    @Test
    public void testReturnsRefreshRateForDisplayAndSensor_proximitySensorNotSet() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        displayManager.overrideSensorManager(mSensorManager);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        displayDevice.mDisplayDeviceConfig = mMockDisplayDeviceConfig;
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);

        String testSensorName = "testName";
        String testSensorType = "testType";
        Sensor testSensor = createSensor(TEST_SENSOR_TYPE, testSensorType, testSensorName);

        when(mMockDisplayDeviceConfig.getProximitySensor()).thenReturn(null);
        when(mSensorManager.getSensorList(Sensor.TYPE_ALL)).thenReturn(Collections.singletonList(
                testSensor));

        SurfaceControl.RefreshRateRange result = localService.getRefreshRateForDisplayAndSensor(
                displayId, testSensorName, testSensorType);

        assertNull(result);
    }

    @Test
    public void testConnectExternalDisplay_shouldDisableDisplay() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        localService.registerDisplayGroupListener(callback);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        // Create default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_CONNECTED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.waitForExpectedEvent();

        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        assertThat(display.isEnabledLocked()).isFalse();
        assertThat(callback.receivedEvents()).containsExactly(DISPLAY_GROUP_EVENT_ADDED,
                EVENT_DISPLAY_CONNECTED).inOrder();
    }

    @Test
    public void testConnectExternalDisplay_withSysprop_shouldEnableDisplay() {
        Assume.assumeTrue(Build.IS_ENG || Build.IS_USERDEBUG);
        doAnswer((Answer<Boolean>) invocationOnMock -> true)
                .when(() -> SystemProperties.getBoolean(ENABLE_ON_CONNECT, false));
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        localService.registerDisplayGroupListener(callback);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);

        // Create default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.waitForExpectedEvent();

        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ false);
        assertThat(display.isEnabledLocked()).isTrue();
        assertThat(callback.receivedEvents()).containsExactly(DISPLAY_GROUP_EVENT_ADDED,
                EVENT_DISPLAY_CONNECTED, EVENT_DISPLAY_ADDED).inOrder();
    }

    @Test
    public void testConnectExternalDisplay_allowsEnableAndDisableDisplay() {
        manageDisplaysPermission(/* granted= */ true);
        BatteryStatsService.overrideService(mMockedBatteryStats);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        localService.registerDisplayGroupListener(callback);

        // Create default display device
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice defaultDisplayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        LogicalDisplay defaultDisplay =
                logicalDisplayMapper.getDisplayLocked(defaultDisplayDevice, false);
        callback.clear();

        // Create external display device
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, new float[]{60f},
                        Display.TYPE_EXTERNAL, callback);
        callback.waitForExpectedEvent();
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        assertThat(display.isEnabledLocked()).isTrue();
        callback.clear();

        callback.expectsEvent(FakeDisplayDevice.COMMITTED_DISPLAY_STATE_CHANGED);
        initDisplayPowerController(localService);
        // Initial power request, should have happened from PowerManagerService.
        localService.requestPowerState(defaultDisplay.getDisplayInfoLocked().displayGroupId,
                new DisplayManagerInternal.DisplayPowerRequest(),
                /*waitForNegativeProximity=*/ false);
        localService.requestPowerState(display.getDisplayInfoLocked().displayGroupId,
                new DisplayManagerInternal.DisplayPowerRequest(),
                /*waitForNegativeProximity=*/ false);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(displayDevice.getDisplayDeviceInfoLocked().committedState)
                .isEqualTo(Display.STATE_OFF);
        assertThat(display.isEnabledLocked()).isFalse();
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_CONNECTED,
                EVENT_DISPLAY_REMOVED).inOrder();

        int displayId = display.getDisplayIdLocked();
        boolean enabled = display.isEnabledLocked();
        assertThat(enabled).isFalse();

        for (int i = 0; i < 9; i++) {
            callback.expectsEvent(FakeDisplayDevice.COMMITTED_DISPLAY_STATE_CHANGED);
            enabled = !enabled;
            Slog.d("DisplayManagerServiceTest", "enabled=" + enabled);
            displayManager.enableConnectedDisplay(displayId, enabled);
            flushHandlers();
            callback.waitForExpectedEvent();
            assertThat(displayDevice.getDisplayDeviceInfoLocked().committedState)
                    .isEqualTo(enabled ? Display.STATE_ON : Display.STATE_OFF);
            assertThat(defaultDisplayDevice.getDisplayDeviceInfoLocked().committedState)
                    .isEqualTo(Display.STATE_ON);
        }
        callback.expectsEvent(FakeDisplayDevice.COMMITTED_DISPLAY_STATE_CHANGED);
        callback.waitForNonExpectedEvent();
    }

    @Test
    public void testConnectInternalDisplay_shouldConnectAndAddDisplay() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();

        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_CONNECTED,
                EVENT_DISPLAY_ADDED).inOrder();
    }

    @Test
    public void testPowerOnAndOffInternalDisplay() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();

        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        displayManager.setDisplayState(display.getDisplayIdLocked(), Display.STATE_ON);

        assertThat(displayDevice.getDisplayDeviceInfoLocked().committedState)
                .isEqualTo(Display.STATE_UNKNOWN);

        assertThat(displayManager.requestDisplayPower(display.getDisplayIdLocked(),
                Display.STATE_OFF)).isTrue();

        assertThat(displayDevice.getDisplayDeviceInfoLocked().committedState)
                .isEqualTo(Display.STATE_OFF);

        assertThat(displayManager.requestDisplayPower(display.getDisplayIdLocked(),
                Display.STATE_UNKNOWN)).isTrue();

        assertThat(displayDevice.getDisplayDeviceInfoLocked().committedState)
                .isEqualTo(Display.STATE_ON);
    }

    @Test
    public void testPowerOnAndOffInternalDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();

        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        var displayId = display.getDisplayIdLocked();

        assertThat(displayDevice.getDisplayDeviceInfoLocked().committedState)
                .isEqualTo(Display.STATE_UNKNOWN);

        assertThrows(SecurityException.class,
                () -> bs.requestDisplayPower(displayId, Display.STATE_UNKNOWN));
        assertThrows(SecurityException.class,
                () -> bs.requestDisplayPower(displayId, Display.STATE_OFF));
    }

    @Test
    public void testEnableExternalDisplay_shouldSignalDisplayAdded() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        // Create default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        callback.expectsEvent(EVENT_DISPLAY_CONNECTED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        displayManager.enableConnectedDisplay(display.getDisplayIdLocked(), /* enabled= */ true);
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(display.isEnabledLocked()).isTrue();
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_ADDED).inOrder();
    }

    @Test
    public void testEnableExternalDisplay_shouldThrowException() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        // Create default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        // Withouts permission, we cannot get the CONNECTED event.
        flushHandlers();
        callback.clear();
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        int displayId = display.getDisplayIdLocked();

        assertThrows(SecurityException.class, () -> bs.enableConnectedDisplay(displayId));
    }

    @Test
    public void testEnableInternalDisplay_shouldSignalAdded() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        callback.expectsEvent(EVENT_DISPLAY_REMOVED);
        logicalDisplayMapper.setEnabledLocked(display, /* isEnabled= */ false);
        logicalDisplayMapper.updateLogicalDisplays();
        flushHandlers();
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        logicalDisplayMapper.setEnabledLocked(display, /* isEnabled= */ true);
        logicalDisplayMapper.updateLogicalDisplays();
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_ADDED);
    }

    @Test
    public void testDisableInternalDisplay_shouldSignalRemove() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        callback.clear();
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);

        callback.expectsEvent(EVENT_DISPLAY_REMOVED);
        logicalDisplayMapper.setEnabledLocked(display, /* isEnabled= */ false);
        logicalDisplayMapper.updateLogicalDisplays();
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_REMOVED);
    }

    @Test
    public void testDisableExternalDisplay_shouldSignalDisplayRemoved() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        DisplayManagerInternal localService = displayManager.new LocalService();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);
        localService.registerDisplayGroupListener(callback);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        // Create default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.waitForExpectedEvent();

        callback.expectsEvent(EVENT_DISPLAY_REMOVED);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        flushHandlers();
        callback.waitForExpectedEvent();

        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        logicalDisplayMapper.setEnabledLocked(display, /* isEnabled= */ true);
        logicalDisplayMapper.updateLogicalDisplays();
        flushHandlers();
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_REMOVED);
        logicalDisplayMapper.setEnabledLocked(display, /* isEnabled= */ false);
        logicalDisplayMapper.updateLogicalDisplays();
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(display.isEnabledLocked()).isFalse();
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_REMOVED);
    }

    @Test
    public void testDisableExternalDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_DISPLAY_EVENTS);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        // Create default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        int displayId = display.getDisplayIdLocked();
        logicalDisplayMapper.setEnabledLocked(display, /* isEnabled= */ true);
        logicalDisplayMapper.updateLogicalDisplays();
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThrows(SecurityException.class, () -> bs.disableConnectedDisplay(displayId));
    }

    @Test
    public void testRemoveExternalDisplay_whenDisabled_shouldSignalDisconnected() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        DisplayManagerInternal localService = displayManager.new LocalService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        localService.registerDisplayGroupListener(callback);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        // Create default display device'
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        callback.waitForExpectedEvent();
        callback.expectsEvent(EVENT_DISPLAY_CONNECTED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.waitForExpectedEvent();
        callback.clear();
        LogicalDisplay display = logicalDisplayMapper.getDisplayLocked(displayDevice);
        int groupId = display.getDisplayInfoLocked().displayGroupId;
        DisplayGroup group = logicalDisplayMapper.getDisplayGroupLocked(groupId);
        assertThat(group.getSizeLocked()).isEqualTo(1);

        callback.expectsEvent(DISPLAY_GROUP_EVENT_REMOVED);
        display.setPrimaryDisplayDeviceLocked(null);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(group.getSizeLocked()).isEqualTo(0);
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_DISCONNECTED,
                DISPLAY_GROUP_EVENT_REMOVED);
    }

    @Test
    public void testRegisterCallback_withoutPermission_shouldThrow() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();

        assertThrows(SecurityException.class, () -> bs.registerCallbackWithEventMask(callback,
                STANDARD_AND_CONNECTION_DISPLAY_EVENTS));
    }

    @Test
    public void testRemoveExternalDisplay_whenEnabled_shouldSignalRemovedAndDisconnected() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        callback.expectsEvent(EVENT_DISPLAY_CONNECTED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        callback.waitForExpectedEvent();
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        int displayId = display.getDisplayIdLocked();
        displayManager.enableConnectedDisplay(displayId, /* enabled= */ true);
        flushHandlers();
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_DISCONNECTED);
        display.setPrimaryDisplayDeviceLocked(null);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(logicalDisplayMapper.getDisplayLocked(displayId, true)).isNull();
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_REMOVED,
                EVENT_DISPLAY_DISCONNECTED).inOrder();
    }

    @Test
    public void testRemoveInternalDisplay_whenEnabled_shouldSignalRemovedAndDisconnected() {
        manageDisplaysPermission(/* granted= */ true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        bs.registerCallbackWithEventMask(callback, STANDARD_AND_CONNECTION_DISPLAY_EVENTS);
        callback.expectsEvent(EVENT_DISPLAY_ADDED);
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        callback.waitForExpectedEvent();
        callback.clear();

        callback.expectsEvent(EVENT_DISPLAY_DISCONNECTED);
        display.setPrimaryDisplayDeviceLocked(null);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
        flushHandlers();
        callback.waitForExpectedEvent();

        assertThat(logicalDisplayMapper.getDisplayLocked(displayDevice,
                /* includeDisabled= */ true)).isNull();
        assertThat(callback.receivedEvents()).containsExactly(EVENT_DISPLAY_REMOVED,
                EVENT_DISPLAY_DISCONNECTED);
    }

    @Test
    public void testRegisterDisplayOffloader_whenEnabled_DisplayHasDisplayOffloadSession() {
        when(mMockFlags.isDisplayOffloadEnabled()).thenReturn(true);
        // set up DisplayManager
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        // set up display
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.DEFAULT_DISPLAY);
        initDisplayPowerController(localService);
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        int displayId = display.getDisplayIdLocked();

        // Register DisplayOffloader.
        DisplayOffloader mockDisplayOffloader = mock(DisplayOffloader.class);
        localService.registerDisplayOffloader(displayId, mockDisplayOffloader);

        assertThat(display.getDisplayOffloadSessionLocked()).isNotNull();
    }

    @Test
    public void testRegisterDisplayOffloader_whenDisabled_DisplayHasNoDisplayOffloadSession() {
        when(mMockFlags.isDisplayOffloadEnabled()).thenReturn(false);
        // set up DisplayManager
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        // set up display
        FakeDisplayDevice displayDevice =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.DEFAULT_DISPLAY);
        initDisplayPowerController(localService);
        LogicalDisplayMapper logicalDisplayMapper = displayManager.getLogicalDisplayMapper();
        LogicalDisplay display =
                logicalDisplayMapper.getDisplayLocked(displayDevice, /* includeDisabled= */ true);
        int displayId = display.getDisplayIdLocked();

        // Register DisplayOffloader.
        DisplayOffloader mockDisplayOffloader = mock(DisplayOffloader.class);
        localService.registerDisplayOffloader(displayId, mockDisplayOffloader);

        assertThat(display.getDisplayOffloadSessionLocked()).isNull();
    }

    @Test
    public void testOnUserSwitching_UpdatesBrightness() {
        mPermissionEnforcer.grant(CONTROL_DISPLAY_BRIGHTNESS);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        displayManager.windowManagerAndInputReady();
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        float brightness1 = 0.3f;
        float brightness2 = 0.45f;
        flushHandlers();

        int userId1 = 123;
        int userId2 = 456;
        UserInfo userInfo1 = new UserInfo();
        userInfo1.id = userId1;
        UserInfo userInfo2 = new UserInfo();
        userInfo2.id = userId2;
        when(mUserManager.getUserSerialNumber(userId1)).thenReturn(12345);
        when(mUserManager.getUserSerialNumber(userId2)).thenReturn(45678);
        final SystemService.TargetUser user1 = new SystemService.TargetUser(userInfo1);
        final SystemService.TargetUser user2 = new SystemService.TargetUser(userInfo2);

        // The same brightness will be restored for a user only if auto-brightness is off,
        // otherwise the current lux will be used to determine the brightness.
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        displayManager.onUserSwitching(/* from= */ user2, /* to= */ user1);
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, brightness1);
        displayManager.onUserSwitching(/* from= */ user1, /* to= */ user2);
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, brightness2);

        displayManager.onUserSwitching(/* from= */ user2, /* to= */ user1);
        flushHandlers();
        assertEquals(brightness1,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);

        displayManager.onUserSwitching(/* from= */ user1, /* to= */ user2);
        flushHandlers();
        assertEquals(brightness2,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);
    }

    @Test
    public void testOnUserSwitching_brightnessForNewUserIsDefault() {
        mPermissionEnforcer.grant(CONTROL_DISPLAY_BRIGHTNESS);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        displayManager.windowManagerAndInputReady();
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        int userId1 = 123;
        int userId2 = 456;
        UserInfo userInfo1 = new UserInfo();
        userInfo1.id = userId1;
        UserInfo userInfo2 = new UserInfo();
        userInfo2.id = userId2;
        when(mUserManager.getUserSerialNumber(userId1)).thenReturn(12345);
        when(mUserManager.getUserSerialNumber(userId2)).thenReturn(45678);
        final SystemService.TargetUser from = new SystemService.TargetUser(userInfo1);
        final SystemService.TargetUser to = new SystemService.TargetUser(userInfo2);

        displayManager.onUserSwitching(from, to);
        flushHandlers();
        assertEquals(displayManagerBinderService.getDisplayInfo(Display.DEFAULT_DISPLAY)
                        .brightnessDefault,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);
    }

    @Test
    public void testResolutionChangeGetsBackedUp_FeatureFlagFalse() {
        mPermissionEnforcer.grant(MODIFY_USER_PREFERRED_DISPLAY_MODE);
        when(mMockFlags.isResolutionBackupRestoreEnabled()).thenReturn(false);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);

        Display.Mode[] modes = new Display.Mode[2];
        modes[0] = new Display.Mode(/*id=*/ 101, /*width=*/ 100, /*height=*/ 200, /*rr=*/ 60);
        modes[1] = new Display.Mode(/*id=*/ 101, /*width=*/ 200, /*height=*/ 400, /*rr=*/ 60);
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, modes);
        flushHandlers();

        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);

        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(100, 200, 0), true);
        try {
            Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.SCREEN_RESOLUTION_MODE);
            fail("SettingNotFoundException should have been thrown.");
        } catch (SettingNotFoundException expected) {
        }
    }

    @Test
    public void testBrightnessUpdates() {
        mPermissionEnforcer.grant(CONTROL_DISPLAY_BRIGHTNESS);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        final float invalidBrightness = -0.3f;
        final float brightnessOff = -1.0f;
        final float minimumBrightness = 0.0f;
        final float validBrightness = 0.5f;

        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        // set and check valid brightness
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, validBrightness);
        flushHandlers();
        assertEquals(validBrightness,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);

        // set and check invalid brightness
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, invalidBrightness);
        flushHandlers();
        assertEquals(PowerManager.BRIGHTNESS_MIN,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);

        // reset and check valid brightness
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, validBrightness);
        flushHandlers();
        assertEquals(validBrightness,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);

        // set and check brightness off
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, brightnessOff);
        flushHandlers();
        assertEquals(PowerManager.BRIGHTNESS_MIN,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);

        // reset and check valid brightness
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, validBrightness);
        flushHandlers();
        assertEquals(validBrightness,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);

        // set and check minimum brightness
        flushHandlers();
        displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, minimumBrightness);
        flushHandlers();
        assertEquals(PowerManager.BRIGHTNESS_MIN,
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY),
                FLOAT_TOLERANCE);
    }

    @Test
    public void testResolutionChangeGetsBackedUp() throws Exception {
        mPermissionEnforcer.grant(MODIFY_USER_PREFERRED_DISPLAY_MODE);
        when(mMockFlags.isResolutionBackupRestoreEnabled()).thenReturn(true);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);

        Display.Mode[] modes = new Display.Mode[2];
        modes[0] = new Display.Mode(/*id=*/ 101, /*width=*/ 100, /*height=*/ 200, /*rr=*/ 60);
        modes[1] = new Display.Mode(/*id=*/ 101, /*width=*/ 200, /*height=*/ 400, /*rr=*/ 60);
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, modes);
        flushHandlers();

        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);

        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(100, 200, 0), true);
        assertEquals(Settings.Secure.RESOLUTION_MODE_HIGH,
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.SCREEN_RESOLUTION_MODE));

        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(200, 400, 0), true);
        assertEquals(Settings.Secure.RESOLUTION_MODE_FULL,
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.SCREEN_RESOLUTION_MODE));
    }

    @Test
    public void testResolutionChangeDoesNotGetBackedUp() throws Exception {
        mPermissionEnforcer.grant(MODIFY_USER_PREFERRED_DISPLAY_MODE);
        when(mMockFlags.isResolutionBackupRestoreEnabled()).thenReturn(true);
        when(mMockFlags.isModeSwitchWithoutSavingEnabled()).thenReturn(true);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);

        Display.Mode[] modes = new Display.Mode[2];
        modes[0] = new Display.Mode(/*id=*/ 101, /*width=*/ 100, /*height=*/ 200, /*rr=*/ 60);
        modes[1] = new Display.Mode(/*id=*/ 101, /*width=*/ 200, /*height=*/ 400, /*rr=*/ 60);
        createFakeDisplayDevice(
                displayManager, modes, Display.TYPE_EXTERNAL, "testDevice");
        flushHandlers();

        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);

        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        // storeMode = true
        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(100, 200, 60), true);
        assertEquals(Settings.Secure.RESOLUTION_MODE_HIGH,
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.SCREEN_RESOLUTION_MODE));
        // storeMode = false, SCREEN_RESOLUTION_MODE should not change
        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(200, 400, 60), false);
        assertEquals(Settings.Secure.RESOLUTION_MODE_HIGH,
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.SCREEN_RESOLUTION_MODE));
    }

    @Test
    public void testResolutionRestFromSettings() throws Exception {
        mPermissionEnforcer.grant(MODIFY_USER_PREFERRED_DISPLAY_MODE);
        when(mMockFlags.isResolutionBackupRestoreEnabled()).thenReturn(true);
        when(mMockFlags.isModeSwitchWithoutSavingEnabled()).thenReturn(true);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);

        Display.Mode[] modes = new Display.Mode[2];
        modes[0] = new Display.Mode(/*id=*/ 101, /*width=*/ 100, /*height=*/ 200, /*rr=*/ 60);
        modes[1] = new Display.Mode(/*id=*/ 101, /*width=*/ 200, /*height=*/ 400, /*rr=*/ 60);
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(
                displayManager, modes, Display.TYPE_EXTERNAL, "testDevice");
        flushHandlers();

        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);

        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        // storeMode = true, preferredMode changed and persisted
        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(100, 200, 60), true);
        assertEquals(modes[0], displayDevice.mPreferredMode);
        // storeMode = false, preferredMode changed and not persisted
        bs.setUserPreferredDisplayMode(
                Display.DEFAULT_DISPLAY, new Display.Mode(200, 400, 60), false);
        assertEquals(modes[1], displayDevice.mPreferredMode);

        // reset, preferredMode restored from persistence
        bs.resetUserPreferredDisplayMode(Display.DEFAULT_DISPLAY);
        assertEquals(modes[0], displayDevice.mPreferredMode);
    }

    @Test
    public void testResolutionGetsRestored() throws Exception {
        when(mMockFlags.isResolutionBackupRestoreEnabled()).thenReturn(true);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);

        displayManager.systemReady(false /* safeMode */);
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentMatcher<IntentFilter> matchesFilter =
                (filter) -> Intent.ACTION_SETTING_RESTORED.equals(filter.getAction(0));
        verify(mContext).registerReceiver(receiverCaptor.capture(), argThat(matchesFilter));
        BroadcastReceiver receiver = receiverCaptor.getValue();

        Display.Mode emptyMode = new Display.Mode.Builder().build();
        Display.Mode[] modes = new Display.Mode[2];
        modes[0] = new Display.Mode(/*id=*/ 101, /*width=*/ 100, /*height=*/ 200, /*rr=*/ 60);
        modes[1] = new Display.Mode(/*id=*/ 102, /*width=*/ 200, /*height=*/ 400, /*rr=*/ 60);
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, modes);
        flushHandlers();

        displayManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();

        // Get the current display mode, ensure it is null
        Display.Mode prevMode = bs.getUserPreferredDisplayMode(Display.DEFAULT_DISPLAY);
        assertEquals(emptyMode, prevMode);

        // Set a new mode (FULL) via restore
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.SCREEN_RESOLUTION_MODE, Settings.Secure.RESOLUTION_MODE_FULL);
        Intent restoreIntent = new Intent(Intent.ACTION_SETTING_RESTORED);
        restoreIntent.putExtra(Intent.EXTRA_SETTING_NAME, Settings.Secure.SCREEN_RESOLUTION_MODE);
        receiver.onReceive(mContext, restoreIntent);

        Display.Mode newMode = bs.getUserPreferredDisplayMode(Display.DEFAULT_DISPLAY);
        assertEquals(modes[1], newMode);
    }

    @Test
    public void testResolutionGetsRestored_FeatureFlagFalse() throws Exception {
        when(mMockFlags.isResolutionBackupRestoreEnabled()).thenReturn(false);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);

        displayManager.systemReady(false /* safeMode */);
        flushHandlers();
        ArgumentMatcher<IntentFilter> matchesFilter =
                (filter) -> Intent.ACTION_SETTING_RESTORED.equals(filter.getAction(0));
        verify(mContext, times(0)).registerReceiver(any(BroadcastReceiver.class),
                argThat(matchesFilter));
    }

    @Test
    public void testHighestHdrSdrRatio() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        displayDevice.mDisplayDeviceConfig = mMockDisplayDeviceConfig;
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        float highestRatio = 9.5f;
        HdrBrightnessData hdrData = new HdrBrightnessData(Collections.emptyMap(),
                /* brightnessIncreaseDebounceMillis= */ 0, /* screenBrightnessRampIncrease= */ 0,
                /* brightnessDecreaseDebounceMillis= */ 0, /* screenBrightnessRampDecrease= */ 0,
                /* hbmTransitionPoint= */ 0, /* minimumHdrPercentOfScreenForNbm= */ 0,
                /* minimumHdrPercentOfScreenForHbm= */ 0, /* allowInLowPowerMode= */ false,
                mock(Spline.class), highestRatio);
        when(mMockDisplayDeviceConfig.getHdrBrightnessData()).thenReturn(hdrData);

        assertEquals(highestRatio, displayManagerBinderService.getHighestHdrSdrRatio(displayId),
                /* delta= */ 0);
    }

    @Test
    public void testHighestHdrSdrRatio_HdrDataNull() {
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        displayDevice.mDisplayDeviceConfig = mMockDisplayDeviceConfig;
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        when(mMockDisplayDeviceConfig.getHdrBrightnessData()).thenReturn(null);

        assertEquals(1, displayManagerBinderService.getHighestHdrSdrRatio(displayId),
                /* delta= */ 0);
    }

    @Test
    public void testOnDisplayChanged_HbmMetadataNull() {
        DisplayPowerController dpc = mock(DisplayPowerController.class);
        DisplayManagerService.Injector injector = new BasicInjector() {
            @Override
            DisplayPowerController getDisplayPowerController(Context context,
                    DisplayPowerController.Injector injector,
                    DisplayManagerInternal.DisplayPowerCallbacks callbacks, Handler handler,
                    SensorManager sensorManager, DisplayBlanker blanker,
                    LogicalDisplay logicalDisplay, BrightnessTracker brightnessTracker,
                    BrightnessSetting brightnessSetting, Runnable onBrightnessChangeRunnable,
                    HighBrightnessModeMetadata hbmMetadata, boolean bootCompleted,
                    DisplayManagerFlags flags, PluginManager pluginManager) {
                return dpc;
            }
        };
        DisplayManagerService displayManager = new DisplayManagerService(mContext, injector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        // Add the FakeDisplayDevice
        FakeDisplayDevice displayDevice = new FakeDisplayDevice();
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        displayDeviceInfo.state = Display.STATE_ON;
        displayDevice.setDisplayDeviceInfo(displayDeviceInfo);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
        flushHandlers();
        initDisplayPowerController(localService);

        // Simulate DisplayDevice change
        DisplayDeviceInfo displayDeviceInfo2 = new DisplayDeviceInfo();
        displayDeviceInfo2.copyFrom(displayDeviceInfo);
        displayDeviceInfo2.state = Display.STATE_DOZE;
        updateDisplayDeviceInfo(displayManager, displayDevice, displayDeviceInfo2);

        verify(dpc).onDisplayChanged(/* hbmMetadata= */ null, Layout.NO_LEAD_DISPLAY);
    }

    @Test
    public void testCreateAndReleaseVirtualDisplay_CalledWithTheSameUid() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        registerDefaultDisplays(displayManager);
        DisplayManagerService.BinderService bs = displayManager.new BinderService();
        VirtualDisplayConfig config = mock(VirtualDisplayConfig.class);
        Surface surface = mock(Surface.class);
        when(config.getSurface()).thenReturn(surface);
        int callingUid = Binder.getCallingUid();
        IBinder binder = mock(IBinder.class);
        when(mMockAppToken.asBinder()).thenReturn(binder);
        String uniqueId = "123";
        when(config.getUniqueId()).thenReturn(uniqueId);

        bs.createVirtualDisplay(config, mMockAppToken, /* projection= */ null, PACKAGE_NAME);
        verify(mMockVirtualDisplayAdapter).createVirtualDisplayLocked(eq(mMockAppToken),
                /* projection= */ isNull(), eq(callingUid), eq(PACKAGE_NAME),
                eq("virtual:" + PACKAGE_NAME + ":" + uniqueId), eq(surface), /* flags= */ anyInt(),
                eq(config));

        bs.releaseVirtualDisplay(mMockAppToken);
        verify(mMockVirtualDisplayAdapter).releaseVirtualDisplayLocked(binder);
    }

    @Test
    public void testGetDisplayTopology() {
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 1);
        manageDisplaysPermission(/* granted= */ true);
        when(mMockFlags.isDisplayTopologyEnabled()).thenReturn(true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        DisplayTopology topology = displayManagerBinderService.getDisplayTopology();
        assertNotNull(topology);
        DisplayTopology.TreeNode display = topology.getRoot();
        assertNotNull(display);
        assertEquals(Display.DEFAULT_DISPLAY, display.getDisplayId());
    }

    @Test
    public void testGetDisplayTopology_NullIfFlagDisabled() {
        manageDisplaysPermission(/* granted= */ true);
        when(mMockFlags.isDisplayTopologyEnabled()).thenReturn(false);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        DisplayTopology topology = displayManagerBinderService.getDisplayTopology();
        assertNull(topology);
    }

    @Test
    public void testSetDisplayTopology() {
        manageDisplaysPermission(/* granted= */ true);
        when(mMockFlags.isDisplayTopologyEnabled()).thenReturn(true);
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);
        displayManager.windowManagerAndInputReady();

        DisplayTopology topology = mock(DisplayTopology.class);
        when(topology.copy()).thenReturn(topology);
        DisplayTopologyGraph graph = mock(DisplayTopologyGraph.class);
        when(topology.getGraph()).thenReturn(graph);
        displayManagerBinderService.setDisplayTopology(topology);
        flushHandlers();

        verify(mMockInputManagerInternal).setDisplayTopology(graph);
    }

    @Test
    public void testSetDisplayTopology_withoutPermission_shouldThrowException() {
        when(mMockFlags.isDisplayTopologyEnabled()).thenReturn(true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        assertThrows(SecurityException.class,
                () -> displayManagerBinderService.setDisplayTopology(new DisplayTopology()));
    }

    @Test
    public void testShouldNotifyTopologyChanged() {
        manageDisplaysPermission(/* granted= */ true);
        when(mMockFlags.isDisplayTopologyEnabled()).thenReturn(true);
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        DisplayManagerInternal localService = displayManager.new LocalService();
        Handler handler = displayManager.getDisplayHandler();
        flushHandlers();

        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        displayManagerBinderService.registerCallbackWithEventMask(callback,
                INTERNAL_EVENT_FLAG_TOPOLOGY_UPDATED);
        flushHandlers();

        var topology = initDisplayTopology(displayManager, displayManagerBinderService,
                localService, callback, /*shouldEmitTopologyChangeEvent=*/ true);
        callback.clear();
        callback.expectsEvent(TOPOLOGY_CHANGED_EVENT);
        displayManagerBinderService.setDisplayTopology(topology);
        flushHandlers();
        callback.waitForExpectedEvent();
    }

    @Test
    public void testShouldNotNotifyTopologyChanged_WhenClientIsNotSubscribed() {
        manageDisplaysPermission(/* granted= */ true);
        when(mMockFlags.isDisplayTopologyEnabled()).thenReturn(true);
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        DisplayManagerInternal localService = displayManager.new LocalService();
        Handler handler = displayManager.getDisplayHandler();
        flushHandlers();

        // Only subscribe to display events, not topology events
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        displayManagerBinderService.registerCallbackWithEventMask(callback,
                STANDARD_DISPLAY_EVENTS);
        flushHandlers();

        var topology = initDisplayTopology(displayManager, displayManagerBinderService,
                localService, callback, /*shouldEmitTopologyChangeEvent=*/ false);
        callback.clear();
        callback.expectsEvent(TOPOLOGY_CHANGED_EVENT); // should not happen
        displayManagerBinderService.setDisplayTopology(topology);
        flushHandlers();
        callback.waitForNonExpectedEvent(); // checks that event did not happen
        flushHandlers();
        assertThat(callback.receivedEvents()).isEmpty();
    }

    @Test
    public void testMirrorBuiltInDisplay_flagEnabled() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isFalse();

        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(MIRROR_BUILT_IN_DISPLAY));
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isTrue();
    }

    @Test
    public void testMirrorBuiltInDisplay_inLockTaskMode() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        when(mMockFlags.isDisplayMirrorInLockTaskModeEnabled()).thenReturn(true);
        when(mMockActivityTaskManagerInternal.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_LOCKED);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.windowManagerAndInputReady();
        displayManager.systemReady(/* safeMode= */ false);

        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isTrue();
    }

    @Test
    public void testMirrorBuiltInDisplay_isNotInLockTaskMode() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        when(mMockFlags.isDisplayMirrorInLockTaskModeEnabled()).thenReturn(true);
        when(mMockActivityTaskManagerInternal.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);

        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 1);
        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.windowManagerAndInputReady();
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isTrue();

        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(MIRROR_BUILT_IN_DISPLAY));
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isFalse();
    }

    @Test
    public void testMirrorBuiltInDisplay_onLockTaskModeChanged() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        when(mMockFlags.isDisplayMirrorInLockTaskModeEnabled()).thenReturn(true);
        when(mMockActivityTaskManagerInternal.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.windowManagerAndInputReady();
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isFalse();

        when(mMockActivityTaskManagerInternal.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_LOCKED);
        final TaskStackListener taskStackListener = displayManager.getTaskStackListener();
        taskStackListener.onLockTaskModeChanged(ActivityManager.LOCK_TASK_MODE_LOCKED);
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isTrue();

        when(mMockActivityTaskManagerInternal.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);
        taskStackListener.onLockTaskModeChanged(ActivityManager.LOCK_TASK_MODE_NONE);
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isFalse();
    }

    @Test
    public void testMirrorBuiltInDisplay_flagDisabled() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(false);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isFalse();

        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(MIRROR_BUILT_IN_DISPLAY));
        assertThat(displayManager.shouldMirrorBuiltInDisplay()).isFalse();
    }

    @Test
    public void testShouldNotNotifyDefaultDisplayChanges_whenMirrorBuiltInDisplayChanges() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.systemReady(/* safeMode= */ false);

        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        flushHandlers();

        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        displayManagerBinderService.registerCallbackWithEventMask(
                callback, STANDARD_DISPLAY_EVENTS);
        flushHandlers();

        // Create a default display device
        createFakeDisplayDevice(displayManager, new float[] {60f}, Display.TYPE_INTERNAL);

        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(MIRROR_BUILT_IN_DISPLAY));
        flushHandlers();

        assertThat(callback.receivedEvents()).doesNotContain(EVENT_DISPLAY_BASIC_CHANGED);
    }

    @Test
    public void testShouldNotifyNonDefaultDisplayChanges_whenMirrorBuiltInDisplayChanges() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext, mBasicInjector);
        displayManager.systemReady(/* safeMode= */ false);

        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        flushHandlers();

        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback();
        displayManagerBinderService.registerCallbackWithEventMask(
                callback, STANDARD_DISPLAY_EVENTS);
        flushHandlers();

        // Create a default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        // Create a non-default display device
        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);

        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(MIRROR_BUILT_IN_DISPLAY));
        flushHandlers();

        assertThat(callback.receivedEvents()).contains(EVENT_DISPLAY_BASIC_CHANGED);
    }

    @Test
    public void startWifiDisplayScan_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, displayManagerBinderService::startWifiDisplayScan);
    }

    @Test
    public void stopWifiDisplayScan_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, displayManagerBinderService::stopWifiDisplayScan);
    }

    @Test
    public void connectWifiDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                () -> displayManagerBinderService.connectWifiDisplay("someAddress"));
    }

    @Test
    public void renameWifiDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                () -> displayManagerBinderService.renameWifiDisplay("someAddress", "someAlias"));
    }

    @Test
    public void forgetWifiDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                () -> displayManagerBinderService.forgetWifiDisplay("someAddress"));
    }

    @Test
    public void pauseWifiDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, displayManagerBinderService::pauseWifiDisplay);
    }

    @Test
    public void resumeWifiDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, displayManagerBinderService::resumeWifiDisplay);
    }

    @Test
    public void getWifiDisplayStatus_withoutFineLocationPermission_shouldReturnFakeAddress() {
        doNothing().when(mContext).sendBroadcastAsUser(any(), any(), isNull(), any());
        doReturn(mMockedWifiP2pManager).when(mContext).getSystemService(Context.WIFI_P2P_SERVICE);
        doReturn(true).when(mResources).getBoolean(R.bool.config_enableWifiDisplay);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.systemReady(/* safeMode= */ false);
        registerAdditionalDisplays(displayManager);

        var wifiDisplayListener = displayManager.getWifiDisplayListener();

        WifiDisplay[] availableDisplays = new WifiDisplay[1];
        availableDisplays[0] = new WifiDisplay(
                /* deviceAddress = */ "11:22:33:44:55:66",
                /* deviceName= */ "deviceName",
                /* deviceAlias= */ "deviceAlias",
                /* available= */ true,
                /* canConnect= */ true,
                /* remembered= */ false);
        wifiDisplayListener.onScanResults(availableDisplays);

        assertThat(displayManagerBinderService.getWifiDisplayStatus().getDisplays()[0]
                       .getDeviceAddress()).isEqualTo("00:00:00:00:00:00");
    }

    @Test
    public void getWifiDisplayStatus_withFineLocationPermission_shouldReturnRealAddress() {
        doNothing().when(mContext).sendBroadcastAsUser(any(), any(), isNull(), any());
        doReturn(mMockedWifiP2pManager).when(mContext).getSystemService(Context.WIFI_P2P_SERVICE);
        doReturn(true).when(mResources).getBoolean(R.bool.config_enableWifiDisplay);
        when(mContext.checkCallingPermission(ACCESS_FINE_LOCATION)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.systemReady(/* safeMode= */ false);
        registerAdditionalDisplays(displayManager);

        var wifiDisplayListener = displayManager.getWifiDisplayListener();

        WifiDisplay[] availableDisplays = new WifiDisplay[1];
        availableDisplays[0] = new WifiDisplay(
                /* deviceAddress = */ "11:22:33:44:55:66",
                /* deviceName= */ "deviceName",
                /* deviceAlias= */ "deviceAlias",
                /* available= */ true,
                /* canConnect= */ true,
                /* remembered= */ false);
        wifiDisplayListener.onScanResults(availableDisplays);

        assertThat(displayManagerBinderService.getWifiDisplayStatus().getDisplays()[0]
                       .getDeviceAddress()).isEqualTo("11:22:33:44:55:66");
    }

    @Test
    public void setUserDisabledHdrTypes_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setUserDisabledHdrTypes(new int[0]));
    }

    @Test
    public void setAreUserDisabledHdrTypesAllowed_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setAreUserDisabledHdrTypesAllowed(true));
    }

    @Test
    public void requestColorMode_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService.requestColorMode(
                Display.DEFAULT_DISPLAY, Display.COLOR_MODE_DEFAULT));
    }

    @Test
    public void getBrightnessEvents_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.getBrightnessEvents("somePackage"));
    }

    @Test
    public void getAmbientBrightnessStats_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                displayManagerBinderService::getAmbientBrightnessStats);
    }

    @Test
    public void setBrightnessConfigurationForUser_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setBrightnessConfigurationForUser(
                        new BrightnessConfiguration.Builder(/* lux= */ new float[]{0, 100},
                                /* nits= */ new float[]{100, 200}).build(), UserHandle.USER_SYSTEM,
                        "somePackage"));
    }

    @Test
    public void setBrightnessConfigurationForDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setBrightnessConfigurationForDisplay(
                        new BrightnessConfiguration.Builder(/* lux= */ new float[]{0, 100},
                                /* nits= */ new float[]{100, 200}).build(), "uniqueId",
                        UserHandle.USER_SYSTEM, "somePackage"));
    }

    @Test
    public void getBrightnessConfigurationForDisplay_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.getBrightnessConfigurationForDisplay("uniqueId",
                        UserHandle.USER_SYSTEM));
    }

    @Test
    public void getDefaultBrightnessConfiguration_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                displayManagerBinderService::getDefaultBrightnessConfiguration);
    }

    @Test
    public void getBrightnessInfo_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.getBrightnessInfo(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void setTemporaryBrightness_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setTemporaryBrightness(Display.DEFAULT_DISPLAY, 0.3f));
    }

    @Test
    public void setBrightness_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setBrightness(Display.DEFAULT_DISPLAY, 0.3f));
    }

    @Test
    public void getBrightness_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.getBrightness(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void setTemporaryAutoBrightnessAdjustment_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () ->
                displayManagerBinderService.setTemporaryAutoBrightnessAdjustment(0.1f));
    }

    @Test
    public void setUserPreferredDisplayMode_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .setUserPreferredDisplayMode(Display.DEFAULT_DISPLAY, new Display.Mode(
                        /* width= */ 800, /* height= */ 600, /* refreshRate= */ 60), true));
    }

    @Test
    public void setHdrConversionMode_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .setHdrConversionMode(new HdrConversionMode(HDR_CONVERSION_SYSTEM)));
    }

    @Test
    public void setShouldAlwaysRespectAppRequestedMode_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .setShouldAlwaysRespectAppRequestedMode(true));
    }

    @Test
    public void shouldAlwaysRespectAppRequestedMode_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                displayManagerBinderService::shouldAlwaysRespectAppRequestedMode);
    }

    @Test
    public void setRefreshRateSwitchingType_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .setRefreshRateSwitchingType(SWITCHING_TYPE_NONE));
    }

    @Test
    public void requestDisplayModes_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .requestDisplayModes(new Binder(), Display.DEFAULT_DISPLAY, new int[0]));
    }

    @Test
    public void getDozeBrightnessSensorValueToBrightness_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .getDozeBrightnessSensorValueToBrightness(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void getDefaultDozeBrightness_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class, () -> displayManagerBinderService
                .getDefaultDozeBrightness(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void testIncludeDefaultDisplayInTopologySwitch_flagDisabled() {
        when(mMockFlags.isDefaultDisplayInTopologySwitchEnabled()).thenReturn(false);
        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext,
                mBasicInjector);
        setFieldValue(displayManager, "mDisplayTopologyCoordinator",
                mMockDisplayTopologyCoordinator);
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isFalse();

        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY));
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isFalse();
        verify(mMockDisplayTopologyCoordinator, never()).onDisplayAdded(any());
    }

    @Test
    public void testIncludeDefaultDisplayInTopologySwitch_internalDisplayCanHostDesktops() {
        when(mMockFlags.isDefaultDisplayInTopologySwitchEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                0);

        DisplayManagerService displayManager = new DisplayManagerService(
                mContext, new BasicInjector() {
            @Override
            boolean isDesktopModeSupportedOnInternalDisplay(Context context) {
                return true;
            }
        });

        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isTrue();

        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY));
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isTrue();
        verify(mMockDisplayTopologyCoordinator, never()).onDisplayAdded(any());
    }

    @Test
    public void testIncludeDefaultDisplayInTopologySwitch_addDefaultDisplayWhenEnableSwitch() {
        when(mMockFlags.isDefaultDisplayInTopologySwitchEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);
        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext,
                mBasicInjector);
        setFieldValue(displayManager, "mDisplayTopologyCoordinator",
                mMockDisplayTopologyCoordinator);
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isFalse();

        createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_INTERNAL);
        DisplayInfo defaultDisplayInfo = displayManager.getLogicalDisplayMapper().
                getDisplayLocked(Display.DEFAULT_DISPLAY).getDisplayInfoLocked();
        clearInvocations(mMockDisplayTopologyCoordinator);

        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY));
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isTrue();
        verify(mMockDisplayTopologyCoordinator).onDisplayAdded(defaultDisplayInfo);
    }

    @Test
    public void testIncludeDefaultDisplayInTopologySwitch_removeDefaultDisplayWhenDisableSwitch() {
        when(mMockFlags.isDefaultDisplayInTopologySwitchEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 0);
        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                1);

        DisplayManagerService displayManager = new DisplayManagerService(mContext,
                mBasicInjector);
        setFieldValue(displayManager, "mDisplayTopologyCoordinator",
                mMockDisplayTopologyCoordinator);
        displayManager.systemReady(/* safeMode= */ false);
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isTrue();

        DisplayTopology mockTopology = mock(DisplayTopology.class);
        doReturn(true).when(mockTopology).hasMultipleDisplays();
        doReturn(mockTopology).when(mMockDisplayTopologyCoordinator).getTopology();

        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                0);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY));
        assertThat(displayManager.shouldIncludeDefaultDisplayInTopology()).isFalse();
        verify(mMockDisplayTopologyCoordinator).onDisplayRemoved(Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testIncludeDefaultDisplayInTopologySwitch_mirrorBuiltInDisplay() {
        when(mMockFlags.isDisplayContentModeManagementEnabled()).thenReturn(true);
        when(mMockFlags.isDefaultDisplayInTopologySwitchEnabled()).thenReturn(true);
        Settings.Secure.putInt(mContext.getContentResolver(), MIRROR_BUILT_IN_DISPLAY, 1);
        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                0);

        DisplayManagerService displayManager = new DisplayManagerService(mContext,
                mBasicInjector);
        setFieldValue(displayManager, "mDisplayTopologyCoordinator",
                mMockDisplayTopologyCoordinator);
        displayManager.systemReady(/* safeMode= */ false);

        Settings.Secure.putInt(mContext.getContentResolver(), INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
                1);
        final ContentObserver observer = displayManager.getSettingsObserver();
        observer.onChange(false, Settings.Secure.getUriFor(INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY));
        verify(mMockDisplayTopologyCoordinator, never()).onDisplayAdded(any());
    }

    @Test
    @Parameters({"0", "13", "39.1f", "54.56f", "80", "97.31f", "100"})
    public void testGetAndSetBrightness_unitPercentage(float percentage) {
        mPermissionEnforcer.grant(WRITE_SETTINGS);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        // Run DisplayPowerController.updatePowerState, initialize BrightnessInfo
        localService.requestPowerState(Display.DEFAULT_DISPLAY_GROUP,
                new DisplayManagerInternal.DisplayPowerRequest(),
                /* waitForNegativeProximity= */ false);
        flushHandlers();

        displayManagerBinderService.setBrightnessByUnit(Display.DEFAULT_DISPLAY, percentage,
                BRIGHTNESS_UNIT_PERCENTAGE);

        float actualPercentage = displayManagerBinderService.getBrightnessByUnit(
                Display.DEFAULT_DISPLAY, BRIGHTNESS_UNIT_PERCENTAGE);
        assertEquals(percentage, actualPercentage, /* delta= */ 0.05);
    }

    @Test
    @Parameters({"0", "200", "600", "900", "1100", "1300"})
    public void testGetAndSetBrightness_unitNits(float nits) {
        mPermissionEnforcer.grant(WRITE_SETTINGS);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        displayDevice.mDisplayDeviceConfig = mMockDisplayDeviceConfig;
        when(mMockDisplayDeviceConfig.isAutoBrightnessAvailable()).thenReturn(true);
        when(mMockDisplayDeviceConfig.getNits()).thenReturn(new float[]{0, 1300});
        when(mMockDisplayDeviceConfig.getBrightness()).thenReturn(new float[]{0, 1});
        when(mMockDisplayDeviceConfig.getAutoBrightnessBrighteningLevelsLux(anyInt(),
                anyInt())).thenReturn(new float[]{0, 1000});
        when(mMockDisplayDeviceConfig.getAutoBrightnessBrighteningLevelsNits()).thenReturn(
                new float[]{100, 500});
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        // Run DisplayPowerController.updatePowerState, initialize BrightnessInfo
        localService.requestPowerState(Display.DEFAULT_DISPLAY_GROUP,
                new DisplayManagerInternal.DisplayPowerRequest(),
                /* waitForNegativeProximity= */ false);
        flushHandlers();

        displayManagerBinderService.setBrightnessByUnit(Display.DEFAULT_DISPLAY, nits,
                BRIGHTNESS_UNIT_NITS);

        float actualNits = displayManagerBinderService.getBrightnessByUnit(
                Display.DEFAULT_DISPLAY, BRIGHTNESS_UNIT_NITS);
        assertEquals(nits, actualNits, FLOAT_TOLERANCE);
    }

    @Test
    public void testGetAndSetBrightness_unitNits_deviceDoesNotSupportNits() {
        mPermissionEnforcer.grant(WRITE_SETTINGS);
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerInternal localService = displayManager.new LocalService();
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager, new float[]{60f});
        displayDevice.mDisplayDeviceConfig = mMockDisplayDeviceConfig;
        registerDefaultDisplays(displayManager);
        initDisplayPowerController(localService);

        // Run DisplayPowerController.updatePowerState, initialize BrightnessInfo
        localService.requestPowerState(Display.DEFAULT_DISPLAY_GROUP,
                new DisplayManagerInternal.DisplayPowerRequest(),
                /* waitForNegativeProximity= */ false);
        flushHandlers();

        assertEquals(BrightnessMappingStrategy.INVALID_NITS,
                displayManagerBinderService.getBrightnessByUnit(Display.DEFAULT_DISPLAY,
                        BRIGHTNESS_UNIT_NITS), 0);
        assertThrows(IllegalArgumentException.class,
                () -> displayManagerBinderService.setBrightnessByUnit(Display.DEFAULT_DISPLAY, 200,
                        BRIGHTNESS_UNIT_NITS));
    }

    @Test
    public void testSetBrightnessByUnit_withoutPermission_shouldThrowException() {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        assertThrows(SecurityException.class,
                () -> displayManagerBinderService.setBrightnessByUnit(Display.DEFAULT_DISPLAY, 0.3f,
                        BRIGHTNESS_UNIT_PERCENTAGE));
    }

    private void initDisplayPowerController(DisplayManagerInternal localService) {
        localService.initPowerManagement(new DisplayManagerInternal.DisplayPowerCallbacks() {
            @Override
            public void onStateChanged() {

            }

            @Override
            public void onProximityPositive() {

            }

            @Override
            public void onProximityNegative() {

            }

            @Override
            public void onDisplayStateChange(boolean allInactive, boolean allOff) {

            }

            @Override
            public void acquireSuspendBlocker(String id) {

            }

            @Override
            public void releaseSuspendBlocker(String id) {

            }
        }, new Handler(Looper.getMainLooper()), mSensorManager);
    }

    private void testDisplayInfoFrameRateOverrideModeCompat(boolean compatChangeEnabled) {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f, 30f, 20f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(60f, displayInfo.getRefreshRate(), 0.01f);

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(
                                Process.myUid(), 20f),
                        new DisplayEventReceiver.FrameRateOverride(
                                Process.myUid() + 1, 30f)
                });
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(20f, displayInfo.getRefreshRate(), 0.01f);
        Display.Mode expectedMode;
        if (compatChangeEnabled) {
            expectedMode = new Display.Mode(1, 100, 200, 60f);
        } else {
            expectedMode = new Display.Mode(255, 100, 200, 20f);
        }
        assertEquals(expectedMode, displayInfo.getMode());
    }

    private void testDisplayInfoRenderFrameRateModeCompat(boolean compatChangeEnabled)  {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mShortMockedInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f, 30f, 20f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(60f, displayInfo.getRefreshRate(), 0.01f);

        updateRenderFrameRate(displayManager, displayDevice, 20f);
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(20f, displayInfo.getRefreshRate(), 0.01f);
        Display.Mode expectedMode;
        if (compatChangeEnabled) {
            expectedMode = new Display.Mode(1, 100, 200, 60f);
        } else {
            expectedMode = new Display.Mode(255, 100, 200, 20f);
        }
        assertEquals(expectedMode, displayInfo.getMode());
    }

    private void testDisplayInfoNonNativeFrameRateOverrideMode(boolean compatChangeEnabled) {
        DisplayManagerService displayManager =
                new DisplayManagerService(mContext, mBasicInjector);
        DisplayManagerService.BinderService displayManagerBinderService =
                displayManager.new BinderService();
        registerDefaultDisplays(displayManager);
        displayManager.onBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        FakeDisplayDevice displayDevice = createFakeDisplayDevice(displayManager,
                new float[]{60f});
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);
        DisplayInfo displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        assertEquals(60f, displayInfo.getRefreshRate(), 0.01f);

        updateFrameRateOverride(displayManager, displayDevice,
                new DisplayEventReceiver.FrameRateOverride[]{
                        new DisplayEventReceiver.FrameRateOverride(
                                Process.myUid(), 20f)
                });
        displayInfo = displayManagerBinderService.getDisplayInfo(displayId);
        Display.Mode expectedMode;
        if (compatChangeEnabled) {
            expectedMode = new Display.Mode(1, 100, 200, 60f);
        } else {
            expectedMode = new Display.Mode(255, 100, 200, 20f);
        }
        assertEquals(expectedMode, displayInfo.getMode());
    }

    private void performTraversalInternal(DisplayManagerService displayManager) {
        displayManager.performTraversalInternal(mock(SurfaceControl.Transaction.class),
                new SparseArray<>());
    }

    private int getDisplayIdForDisplayDevice(
            DisplayManagerService displayManager,
            DisplayManagerService.BinderService displayManagerBinderService,
            FakeDisplayDevice displayDevice) {

        final int[] displayIds = displayManagerBinderService.getDisplayIds(
                /* includeDisabled= */ true);
        assertTrue(displayIds.length > 0);
        int displayId = Display.INVALID_DISPLAY;
        for (int i = 0; i < displayIds.length; i++) {
            DisplayDeviceInfo ddi = displayManager.getDisplayDeviceInfoInternal(displayIds[i]);
            if (displayDevice.getDisplayDeviceInfoLocked().equals(ddi)) {
                displayId = displayIds[i];
                break;
            }
        }
        assertFalse(displayId == Display.INVALID_DISPLAY);
        return displayId;
    }

    private void updateDisplayDeviceInfo(DisplayManagerService displayManager,
            FakeDisplayDevice displayDevice,
            DisplayDeviceInfo displayDeviceInfo) {
        displayDevice.setDisplayDeviceInfo(displayDeviceInfo);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_CHANGED);
        flushHandlers();
    }

    private void updateFrameRateOverride(DisplayManagerService displayManager,
            FakeDisplayDevice displayDevice,
            DisplayEventReceiver.FrameRateOverride[] frameRateOverrides) {
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        displayDeviceInfo.copyFrom(displayDevice.getDisplayDeviceInfoLocked());
        displayDeviceInfo.frameRateOverrides = frameRateOverrides;
        updateDisplayDeviceInfo(displayManager, displayDevice, displayDeviceInfo);
    }

    private void updateRenderFrameRate(DisplayManagerService displayManager,
            FakeDisplayDevice displayDevice,
            float renderFrameRate) {
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        displayDeviceInfo.copyFrom(displayDevice.getDisplayDeviceInfoLocked());
        displayDeviceInfo.renderFrameRate = renderFrameRate;
        updateDisplayDeviceInfo(displayManager, displayDevice, displayDeviceInfo);
    }

    private void updateModeId(DisplayManagerService displayManager,
            FakeDisplayDevice displayDevice,
            int modeId) {
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        displayDeviceInfo.copyFrom(displayDevice.getDisplayDeviceInfoLocked());
        displayDeviceInfo.modeId = modeId;
        if (modeId > 0 && modeId <= displayDeviceInfo.supportedModes.length) {
            displayDeviceInfo.renderFrameRate =
                displayDeviceInfo.supportedModes[modeId - 1].getRefreshRate();
        }
        updateDisplayDeviceInfo(displayManager, displayDevice, displayDeviceInfo);
    }

    private IDisplayManagerCallback registerDisplayChangeCallback(
            DisplayManagerService displayManager) {
        IDisplayManagerCallback displayChangesCallback = mock(IDisplayManagerCallback.class);
        when(displayChangesCallback.asBinder()).thenReturn(new Binder());
        DisplayManagerService.BinderService binderService = displayManager.new BinderService();
        binderService.registerCallback(displayChangesCallback);
        return displayChangesCallback;
    }

    private FakeDisplayManagerCallback registerDisplayListenerCallback(
            DisplayManagerService displayManager,
            DisplayManagerService.BinderService displayManagerBinderService,
            FakeDisplayDevice displayDevice) {
        return registerDisplayListenerCallback(displayManager, displayManagerBinderService,
                displayDevice, STANDARD_DISPLAY_EVENTS);
    }

    private FakeDisplayManagerCallback registerDisplayListenerCallback(
            DisplayManagerService displayManager,
            DisplayManagerService.BinderService displayManagerBinderService,
            FakeDisplayDevice displayDevice,
            long displayEventsMask) {
        // Find the display id of the added FakeDisplayDevice
        int displayId = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice);

        flushHandlers();

        // register display listener callback
        FakeDisplayManagerCallback callback = new FakeDisplayManagerCallback(displayId);
        displayManagerBinderService.registerCallbackWithEventMask(
                callback, displayEventsMask);
        return callback;
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
            Display.Mode[] modes) {
        return createFakeDisplayDevice(displayManager, modes, Display.TYPE_INTERNAL, "");
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
            Display.Mode[] modes, int type, String uniqueId) {
        FakeDisplayDevice displayDevice = new FakeDisplayDevice(uniqueId);
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        displayDeviceInfo.supportedModes = modes;
        displayDeviceInfo.modeId = 101;
        displayDeviceInfo.type = type;
        displayDeviceInfo.renderFrameRate = displayDeviceInfo.supportedModes[0].getRefreshRate();
        displayDeviceInfo.width = displayDeviceInfo.supportedModes[0].getPhysicalWidth();
        displayDeviceInfo.height = displayDeviceInfo.supportedModes[0].getPhysicalHeight();
        final Rect zeroRect = new Rect();
        displayDeviceInfo.displayCutout = new DisplayCutout(
                Insets.of(0, 10, 0, 0),
                zeroRect, new Rect(0, 0, 10, 10), zeroRect, zeroRect);
        displayDeviceInfo.flags = DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY;
        displayDeviceInfo.address = createTestDisplayAddress();
        displayDevice.setDisplayDeviceInfo(displayDeviceInfo);
        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
        flushHandlers();
        return displayDevice;
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
                                                      float[] refreshRates) {
        return createFakeDisplayDevice(displayManager, refreshRates, Display.TYPE_UNKNOWN);
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
                                                      float[] refreshRates,
                                                      float[] vsyncRates) {
        return createFakeDisplayDevice(displayManager, refreshRates, vsyncRates,
                Display.TYPE_UNKNOWN);
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
            float[] refreshRates,
            int displayType) {
        return createFakeDisplayDevice(displayManager, refreshRates, refreshRates, displayType);
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
                                                      float[] refreshRates,
                                                      float[] vsyncRates,
                                                      int displayType) {
        return createFakeDisplayDevice(displayManager, refreshRates, vsyncRates, displayType, null);
    }

    private FakeDisplayDevice createFakeDisplayDevice(DisplayManagerService displayManager,
                                                      float[] refreshRates,
                                                      float[] vsyncRates,
                                                      int displayType,
                                                      FakeDisplayManagerCallback callback) {
        FakeDisplayDevice displayDevice = new FakeDisplayDevice();
        displayDevice.setCallback(callback);
        DisplayDeviceInfo displayDeviceInfo = new DisplayDeviceInfo();
        int width = 100;
        int height = 200;
        displayDeviceInfo.supportedModes = new Display.Mode[refreshRates.length];
        for (int i = 0; i < refreshRates.length; i++) {
            displayDeviceInfo.supportedModes[i] =
                    new Display.Mode(i + 1, width, height, refreshRates[i], vsyncRates[i],
                            new float[0], new int[0]);
        }
        displayDeviceInfo.name = "" + displayType;
        displayDeviceInfo.uniqueId = "uniqueId" + mUniqueIdCount++;
        displayDeviceInfo.modeId = 1;
        displayDeviceInfo.type = displayType;
        displayDeviceInfo.renderFrameRate = displayDeviceInfo.supportedModes[0].getRefreshRate();
        displayDeviceInfo.width = width;
        displayDeviceInfo.height = height;
        final Rect zeroRect = new Rect();
        displayDeviceInfo.displayCutout = new DisplayCutout(
                Insets.of(0, 10, 0, 0),
                zeroRect, new Rect(0, 0, 10, 10), zeroRect, zeroRect);
        displayDeviceInfo.flags = DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY;
        if (displayType == Display.TYPE_EXTERNAL) {
            displayDeviceInfo.flags |= DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP;
        }
        displayDeviceInfo.address = createTestDisplayAddress();
        displayDevice.setDisplayDeviceInfo(displayDeviceInfo);

        displayManager.getDisplayDeviceRepository()
                .onDisplayDeviceEvent(displayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
        flushHandlers();
        return displayDevice;
    }

    private void registerDefaultDisplays(DisplayManagerService displayManager) {
        Handler handler = displayManager.getDisplayHandler();
        // Would prefer to call displayManager.onStart() directly here but it performs binderService
        // registration which triggers security exceptions when running from a test.
        handler.sendEmptyMessage(MSG_REGISTER_DEFAULT_DISPLAY_ADAPTERS);
        flushHandlers();
    }

    private void registerAdditionalDisplays(DisplayManagerService displayManager) {
        Handler handler = displayManager.getDisplayHandler();
        // Would prefer to call displayManager.onStart() directly here but it performs binderService
        // registration which triggers security exceptions when running from a test.
        handler.sendEmptyMessage(MSG_REGISTER_ADDITIONAL_DISPLAY_ADAPTERS);
        flushHandlers();
    }

    private void flushHandlers() {
        com.android.server.testutils.TestUtils.flushLoopers(mDisplayLooperManager,
                mPowerLooperManager, mBackgroundLooperManager);
    }

    private void resetConfigToIgnoreSensorManager() {
        doReturn(new int[]{-1}).when(mResources).getIntArray(R.array
                .config_ambientThresholdsOfPeakRefreshRate);
        doReturn(new int[]{-1}).when(mResources).getIntArray(R.array
                .config_brightnessThresholdsOfPeakRefreshRate);
        doReturn(new int[]{-1}).when(mResources).getIntArray(R.array
                .config_highDisplayBrightnessThresholdsOfFixedRefreshRate);
        doReturn(new int[]{-1}).when(mResources).getIntArray(R.array
                .config_highAmbientBrightnessThresholdsOfFixedRefreshRate);
    }

    private void manageDisplaysPermission(boolean granted) {
        if (granted) {
            doNothing().when(mContext).enforceCallingOrSelfPermission(eq(MANAGE_DISPLAYS), any());
            mPermissionEnforcer.grant(MANAGE_DISPLAYS);
        } else {
            doThrow(new SecurityException("MANAGE_DISPLAYS permission denied")).when(mContext)
                    .enforceCallingOrSelfPermission(eq(MANAGE_DISPLAYS), any());
            mPermissionEnforcer.revoke(MANAGE_DISPLAYS);
        }
    }

    private DisplayTopology initDisplayTopology(DisplayManagerService displayManager,
            DisplayManagerService.BinderService displayManagerBinderService,
            DisplayManagerInternal localService, FakeDisplayManagerCallback callback,
            boolean shouldEmitTopologyChangeEvent) {
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 1);
        callback.expectsEvent(TOPOLOGY_CHANGED_EVENT);
        FakeDisplayDevice displayDevice0 =
                createFakeDisplayDevice(displayManager, new float[]{60f}, Display.TYPE_EXTERNAL);
        int displayId0 = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice0);
        flushHandlers();
        if (shouldEmitTopologyChangeEvent) {
            callback.waitForExpectedEvent();
        } else {
            callback.waitForNonExpectedEvent();
        }
        callback.clear();

        callback.expectsEvent(TOPOLOGY_CHANGED_EVENT);
        FakeDisplayDevice displayDevice1 = createFakeDisplayDevice(displayManager,
                new float[]{60f}, Display.TYPE_OVERLAY);
        int displayId1 = getDisplayIdForDisplayDevice(displayManager, displayManagerBinderService,
                displayDevice1);
        flushHandlers();
        // Non-default display should not be added until onDisplayBelongToTopologyChanged is called
        // with true
        callback.waitForNonExpectedEvent();
        localService.onDisplayBelongToTopologyChanged(displayId1, true);
        flushHandlers();
        if (shouldEmitTopologyChangeEvent) {
            callback.waitForExpectedEvent();
        } else {
            callback.waitForNonExpectedEvent();
        }

        var topology = new DisplayTopology();
        topology.addDisplay(displayId0, 2048, 800, 160);
        topology.addDisplay(displayId1, 1920, 1080, 160);
        return topology;
    }

    private void setFieldValue(Object o, String fieldName, Object value) {
        try {
            final Field field = o.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(o, value);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static class FakeDisplayManagerCallback extends IDisplayManagerCallback.Stub
            implements DisplayManagerInternal.DisplayGroupListener {
        int mDisplayId;
        List<String> mReceivedEvents = new ArrayList<>();

        @Nullable
        private String mExpectedEvent;

        @NonNull
        private volatile CountDownLatch mLatch = new CountDownLatch(0);

        FakeDisplayManagerCallback(int displayId) {
            mDisplayId = displayId;
        }

        FakeDisplayManagerCallback() {
            mDisplayId = -1;
        }

        void expectsEvent(@NonNull String event) {
            mExpectedEvent = event;
            mLatch = new CountDownLatch(1);
        }

        void waitForExpectedEvent() {
            waitForExpectedEvent(Duration.ofSeconds(1));
        }

        void waitForExpectedEvent(Duration timeout) {
            try {
                assertWithMessage("Expected '" + mExpectedEvent + "'")
                        .that(mLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException ex) {
                throw new AssertionError("Waiting for expected event interrupted", ex);
            }
        }

        void waitForNonExpectedEvent() {
            waitForNonExpectedEvent(Duration.ofSeconds(1));
        }

        void waitForNonExpectedEvent(Duration timeout) {
            try {
                assertWithMessage("Non Expected '" + mExpectedEvent + "'")
                        .that(mLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)).isFalse();
            } catch (InterruptedException ex) {
                throw new AssertionError("Waiting for expected event interrupted", ex);
            }
        }

        private void eventSeen(String event) {
            if (event.equals(mExpectedEvent)) {
                mLatch.countDown();
            }
        }

        @Override
        public void onDisplayEvent(int displayId, int event) {
            if (mDisplayId != -1 && displayId != mDisplayId) {
                return;
            }

            // We convert the event to a string for two reasons:
            // 1 - The error produced is a lot easier to read
            // 2 - The values used for display and group events are the same, strings are used to
            // differentiate them easily.
            String eventName = eventTypeToString(event);
            mReceivedEvents.add(eventName);
            eventSeen(eventName);
        }

        @Override
        public void onDisplayGroupAdded(int groupId) {
            mReceivedEvents.add(DISPLAY_GROUP_EVENT_ADDED);
            eventSeen(DISPLAY_GROUP_EVENT_ADDED);
        }

        @Override
        public void onDisplayGroupRemoved(int groupId) {
            mReceivedEvents.add(DISPLAY_GROUP_EVENT_REMOVED);
            eventSeen(DISPLAY_GROUP_EVENT_REMOVED);
        }

        @Override
        public void onDisplayGroupChanged(int groupId) {
            mReceivedEvents.add(DISPLAY_GROUP_EVENT_CHANGED);
            eventSeen(DISPLAY_GROUP_EVENT_CHANGED);
        }

        @Override
        public void onTopologyChanged(DisplayTopology topology) {
            mReceivedEvents.add(TOPOLOGY_CHANGED_EVENT);
            eventSeen(TOPOLOGY_CHANGED_EVENT);
        }

        public void clear() {
            mReceivedEvents.clear();
        }

        private String eventTypeToString(int eventType) {
            switch (eventType) {
                case DisplayManagerGlobal.EVENT_DISPLAY_ADDED:
                    return EVENT_DISPLAY_ADDED;
                case DisplayManagerGlobal.EVENT_DISPLAY_REMOVED:
                    return EVENT_DISPLAY_REMOVED;
                case DisplayManagerGlobal.EVENT_DISPLAY_BASIC_CHANGED:
                    return EVENT_DISPLAY_BASIC_CHANGED;
                case DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED:
                    return EVENT_DISPLAY_BRIGHTNESS_CHANGED;
                case DisplayManagerGlobal.EVENT_DISPLAY_HDR_SDR_RATIO_CHANGED:
                    return EVENT_DISPLAY_HDR_SDR_RATIO_CHANGED;
                case DisplayManagerGlobal.EVENT_DISPLAY_CONNECTED:
                    return EVENT_DISPLAY_CONNECTED;
                case DisplayManagerGlobal.EVENT_DISPLAY_DISCONNECTED:
                    return EVENT_DISPLAY_DISCONNECTED;
                case DisplayManagerGlobal.EVENT_DISPLAY_REFRESH_RATE_CHANGED:
                    return EVENT_DISPLAY_REFRESH_RATE_CHANGED;
                case DisplayManagerGlobal.EVENT_DISPLAY_STATE_CHANGED:
                    return EVENT_DISPLAY_STATE_CHANGED;
                default:
                    return "UNKNOWN: " + eventType;
            }
        }

        List<String> receivedEvents() {
            return mReceivedEvents;
        }
    }

    private class FakeDisplayDevice extends DisplayDevice {
        static final String COMMITTED_DISPLAY_STATE_CHANGED = "requestDisplayStateLocked";
        private DisplayDeviceInfo mDisplayDeviceInfo;
        private Display.Mode mPreferredMode = new Display.Mode.Builder().build();
        private FakeDisplayManagerCallback mCallback;

        FakeDisplayDevice() {
            this("");
        }

        FakeDisplayDevice(String uniqueId) {
            super(mMockDisplayAdapter, /* displayToken= */ null, uniqueId, mContext);
        }

        public void setDisplayDeviceInfo(DisplayDeviceInfo displayDeviceInfo) {
            mDisplayDeviceInfo = displayDeviceInfo;
            mDisplayDeviceInfo.committedState = Display.STATE_UNKNOWN;
        }

        @Override
        public boolean hasStableUniqueId() {
            return !TextUtils.isEmpty(getUniqueId());
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            return mDisplayDeviceInfo;
        }

        @Override
        public void setUserPreferredDisplayModeLocked(Display.Mode preferredMode) {
            for (Display.Mode mode : mDisplayDeviceInfo.supportedModes) {
                if (mode.matchesIfValid(
                        preferredMode.getPhysicalWidth(),
                        preferredMode.getPhysicalHeight(),
                        preferredMode.getRefreshRate())) {
                    mPreferredMode = mode;
                    break;
                }
            }
        }

        @Override
        public Display.Mode getUserPreferredDisplayModeLocked() {
            return mPreferredMode;
        }

        @Override
        public Runnable requestDisplayStateLocked(
                final int state,
                final float brightnessState,
                final float sdrBrightnessState,
                @Nullable DisplayOffloadSessionImpl displayOffloadSession) {
            return () -> {
                Slog.d("FakeDisplayDevice", mDisplayDeviceInfo.name
                        + " new state=" + state);
                if (state != mDisplayDeviceInfo.committedState) {
                    Slog.d("FakeDisplayDevice", mDisplayDeviceInfo.name
                            + " mDisplayDeviceInfo.committedState="
                            + mDisplayDeviceInfo.committedState + " set to " + state);
                    mDisplayDeviceInfo.committedState = state;
                    if (mCallback != null) {
                        mCallback.eventSeen(COMMITTED_DISPLAY_STATE_CHANGED);
                    }
                }
            };
        }

        void setCallback(FakeDisplayManagerCallback callback) {
            this.mCallback = callback;
        }
    }
}
