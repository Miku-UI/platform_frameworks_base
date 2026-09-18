/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tiles;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;
import android.media.projection.MediaProjectionInfo;
import android.media.projection.StopReason;
import android.os.Handler;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.service.quicksettings.Tile;
import android.testing.TestableLooper;
import android.view.View;
import android.view.Window;
import android.window.OnBackInvokedDispatcher;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.SysuiTestableContext;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.classifier.FalsingManagerFake;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tiles.dialog.CastDetailsViewModel;
import com.android.systemui.shade.domain.interactor.FakeShadeDialogContextInteractor;
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor;
import com.android.systemui.statusbar.pipeline.shared.data.repository.FakeConnectivityRepository;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.CastDevice;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class CastTileTest extends SysuiTestCase {
    private static final int PRIMARY_USER_ID = 0;
    private static final int SECONDARY_USER_ID = 10;

    @Mock
    private CastController mController;
    @Mock
    private ActivityStarter mActivityStarter;
    @Mock
    private KeyguardStateController mKeyguard;
    @Mock
    private QSHost mHost;
    @Mock
    private MetricsLogger mMetricsLogger;
    @Mock
    private StatusBarStateController mStatusBarStateController;
    @Mock
    private HotspotController mHotspotController;
    @Mock
    private HotspotController.Callback mHotspotCallback;
    @Mock
    private QSLogger mQSLogger;
    @Mock
    private DialogTransitionAnimator mDialogTransitionAnimator;
    @Mock
    private QsEventLogger mUiEventLogger;
    @Mock
    private CastDetailsViewModel.Factory mCastDetailsViewModelFactory;

    private final TileJavaAdapter mJavaAdapter = new TileJavaAdapter();
    private final FakeConnectivityRepository mConnectivityRepository =
            new FakeConnectivityRepository();
    private final ShadeDialogContextInteractor mShadeDialogContextInteractor =
            new FakeShadeDialogContextInteractor(mContext);
    private final FakeDialogCreator mDialogCreator = new FakeDialogCreator();

    private TestableLooper mTestableLooper;
    private CastTile mCastTile;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);

        when(mHost.getContext()).thenReturn(mContext);
    }

    @After
    public void tearDown() {
        mCastTile.destroy();
        mTestableLooper.processAllMessages();
    }

    // -------------------------------------------------
    // All these tests for enabled/disabled wifi have hotspot not enabled

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void stateUnavailable_noDefaultNetworks_newPipeline() {
        createAndStartTile();
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_UNAVAILABLE, mCastTile.getState().state);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void stateInactive_noDefaultNetworks_skipWifiCheckEnabled() {
        createAndStartTile();
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void stateUnavailable_mobileConnected_newPipeline() {
        createAndStartTile();
        mConnectivityRepository.setMobileConnected(true);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_UNAVAILABLE, mCastTile.getState().state);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void stateInactive_mobileConnected_skipWifiCheckEnabled() {
        createAndStartTile();
        mConnectivityRepository.setMobileConnected(true);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }

    @Test
    public void stateInactive_wifiConnected_newPipeline() {
        createAndStartTile();
        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }

    @Test
    public void stateInactive_ethernetConnected_newPipeline() {
        createAndStartTile();
        mConnectivityRepository.setEthernetConnected(true);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }

    @Test
    public void stateActive_wifiConnectedAndCasting_newPipeline() {
        createAndStartTile();
        CastDevice device = createConnectedCastDevice();
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);

        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
    }

    @Test
    public void stateActive_ethernetConnectedAndCasting_newPipeline() {
        createAndStartTile();
        CastDevice device = createConnectedCastDevice();
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setEthernetConnected(true);

        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
    }

    // -------------------------------------------------

    // -------------------------------------------------
    // All these tests for enabled/disabled hotspot have wifi not enabled
    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void testStateUnavailable_hotspotDisabled() {
        createAndStartTile();
        mHotspotCallback.onHotspotChanged(false, 0);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_UNAVAILABLE, mCastTile.getState().state);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void testStateInactive_hotspotDisabled_skipWifiCheckEnabled() {
        createAndStartTile();
        mHotspotCallback.onHotspotChanged(false, 0);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void testStateUnavailable_hotspotEnabledNotConnected() {
        createAndStartTile();
        mHotspotCallback.onHotspotChanged(true, 0);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_UNAVAILABLE, mCastTile.getState().state);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void testStateInactive_hotspotEnabledNotConnected_skipWifiCheckEnabled() {
        createAndStartTile();
        mHotspotCallback.onHotspotChanged(true, 0);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }

    @Test
    public void testStateActive_hotspotEnabledAndConnectedAndCasting() {
        createAndStartTile();
        CastDevice device = createConnectedCastDevice();
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mHotspotCallback.onHotspotChanged(true, 1);
        mTestableLooper.processAllMessages();
        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
    }

    @Test
    public void testStateInactive_hotspotEnabledAndConnectedAndNotCasting() {
        createAndStartTile();
        mHotspotCallback.onHotspotChanged(true, 1);
        mTestableLooper.processAllMessages();
        assertEquals(Tile.STATE_INACTIVE, mCastTile.getState().state);
    }
    // -------------------------------------------------

    @Test
    public void testHandleClick_castDevicePresent() {
        createAndStartTile();
        CastDevice device = new CastDevice(
                "id",
                /* name= */ null,
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaRouter,
                /* tag= */ mock(MediaRouter.RouteInfo.class));
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);
        when(mKeyguard.isShowing()).thenReturn(true);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        mCastTile.handleClick(null /* view */);
        mTestableLooper.processAllMessages();

        verify(mActivityStarter, times(1)).postQSRunnableDismissingKeyguard(any());
    }

    @Test
    public void testHandleClick_projectionOnly() {
        createAndStartTile();
        CastDevice device = new CastDevice(
                "id",
                /* name= */ null,
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaProjection,
                /* tag= */ mock(MediaProjectionInfo.class));
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        mCastTile.handleClick(null /* view */);
        mTestableLooper.processAllMessages();

        verify(mController, times(1))
                .stopCasting(same(device), eq(StopReason.STOP_QS_TILE));
    }

    @Test
    public void testUpdateState_projectionOnly() {
        createAndStartTile();
        CastDevice device = new CastDevice(
                "id",
                /* name= */ "Test Projection Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaProjection,
                /* tag= */ mock(MediaProjectionInfo.class));
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
        assertTrue(mCastTile.getState().secondaryLabel.toString()
                .startsWith("Test Projection Device"));
    }

    @Test
    public void testUpdateState_castingAndProjection() {
        createAndStartTile();
        CastDevice casting = new CastDevice(
                "id1",
                /* name= */ "Test Casting Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaRouter,
                /* tag= */ mock(RouteInfo.class));
        CastDevice projection = new CastDevice(
                "id2",
                /* name= */ "Test Projection Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaProjection,
                /* tag= */ mock(MediaProjectionInfo.class));

        List<CastDevice> devices = new ArrayList<>();
        devices.add(casting);
        devices.add(projection);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        // Note here that the tile should be active, and should choose casting over projection.
        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
        assertTrue(mCastTile.getState().secondaryLabel.toString()
                .startsWith("Test Casting Device"));
    }

    @Test
    public void testUpdateState_connectedAndConnecting() {
        createAndStartTile();
        CastDevice connecting = new CastDevice(
                "id",
                /* name= */ "Test Connecting Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connecting,
                /* origin= */ CastDevice.CastOrigin.MediaRouter,
                /* tag= */ mock(RouteInfo.class));
        CastDevice connected = new CastDevice(
                "id",
                /* name= */ "Test Connected Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaRouter,
                /* tag= */ mock(RouteInfo.class));
        List<CastDevice> devices = new ArrayList<>();
        devices.add(connecting);
        devices.add(connected);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        // Tile should be connected and always prefer the connected device.
        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
        assertTrue(mCastTile.getState().secondaryLabel.toString()
                .startsWith("Test Connected Device"));
    }

    @Test
    public void testExpandView_wifiNotConnected() {
        createAndStartTile();
        mCastTile.refreshState();
        mTestableLooper.processAllMessages();

        assertFalse(mCastTile.getState().forceExpandIcon);
    }

    @Test
    public void testExpandView_wifiEnabledNotCasting() {
        createAndStartTile();
        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertTrue(mCastTile.getState().forceExpandIcon);
    }

    @Test
    public void testExpandView_casting_projection() {
        createAndStartTile();
        CastDevice device = createConnectedCastDevice();
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertFalse(mCastTile.getState().forceExpandIcon);
    }

    @Test
    public void testExpandView_connecting_projection() {
        createAndStartTile();
        CastDevice connecting = new CastDevice(
                "id",
                /* name= */
                "Test Projection Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaProjection,
                /* tag= */ mock(MediaProjectionInfo.class));

        List<CastDevice> devices = new ArrayList<>();
        devices.add(connecting);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertFalse(mCastTile.getState().forceExpandIcon);
    }

    @Test
    public void testExpandView_casting_mediaRoute() {
        createAndStartTile();
        CastDevice device = new CastDevice(
                "id",
                /* name= */ "Test Router Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaRouter,
                /* tag= */ mock(RouteInfo.class));

        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertTrue(mCastTile.getState().forceExpandIcon);
    }

    @Test
    public void testExpandView_connecting_mediaRoute() {
        createAndStartTile();
        CastDevice connecting = new CastDevice(
                "id",
                /* name= */ "Test Router Device",
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connecting,
                /* origin= */ CastDevice.CastOrigin.MediaRouter,
                /* tag= */ mock(RouteInfo.class));
        List<CastDevice> devices = new ArrayList<>();
        devices.add(connecting);
        when(mController.getCastDevices()).thenReturn(devices);

        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertTrue(mCastTile.getState().forceExpandIcon);
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_SKIP_WIFI_CHECK)
    public void testDetailsViewUnavailableState_returnsNull() {
        createAndStartTile();
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_UNAVAILABLE, mCastTile.getState().state);
        mCastTile.getDetailsViewModel(Assert::assertNull);
    }

    @Test
    public void testDetailsViewAvailableState_returnsNotNull() {
        createAndStartTile();
        CastDevice device = createConnectedCastDevice();
        List<CastDevice> devices = new ArrayList<>();
        devices.add(device);
        when(mController.getCastDevices()).thenReturn(devices);
        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        assertEquals(Tile.STATE_ACTIVE, mCastTile.getState().state);
        mCastTile.getDetailsViewModel(Assert::assertNotNull);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_HSUM_FIX)
    public void initialize_castControllerUserIdIsSet() {
        when(mHost.getUserId()).thenReturn(PRIMARY_USER_ID);

        createAndStartTile();

        verify(mController).setCurrentUserId(eq(PRIMARY_USER_ID));
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_HSUM_FIX)
    public void switchUser_castControllerUserIdIsSet() {
        when(mHost.getUserId()).thenReturn(PRIMARY_USER_ID);
        createAndStartTile();
        when(mHost.getUserId()).thenReturn(SECONDARY_USER_ID);

        mCastTile.userSwitch(SECONDARY_USER_ID);
        mTestableLooper.processAllMessages();

        verify(mController).setCurrentUserId(eq(SECONDARY_USER_ID));
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_HSUM_FIX)
    public void createDialog_primaryUser_usesCorrectContext() {
        mContext.ensureTestableResources();
        when(mHost.getUserContext()).thenReturn(mContext);

        List<CastDevice> emptyDeviceList = List.of();
        when(mController.getCastDevices()).thenReturn(emptyDeviceList);
        createAndStartTile();
        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        mCastTile.handleClick(null /* view */);
        mTestableLooper.processAllMessages();

        assertEquals(mDialogCreator.mLastContextPassedToCreateDialog, mContext);
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_QS_CAST_TILE_HSUM_FIX)
    public void createDialog_secondaryUser_usesCorrectContext() {
        SysuiTestableContext secondaryUserContext = new SysuiTestableContext(mContext);
        secondaryUserContext.ensureTestableResources();
        when(mHost.getUserContext()).thenReturn(secondaryUserContext);

        List<CastDevice> emptyDeviceList = List.of();
        when(mController.getCastDevices()).thenReturn(emptyDeviceList);
        createAndStartTile();
        mConnectivityRepository.setWifiConnected(true);
        mTestableLooper.processAllMessages();

        mCastTile.handleClick(null /* view */);
        mTestableLooper.processAllMessages();

        assertEquals(mDialogCreator.mLastContextPassedToCreateDialog, secondaryUserContext);
    }

    private void createAndStartTile() {
        mCastTile = new CastTile(
                mHost,
                mUiEventLogger,
                mTestableLooper.getLooper(),
                new Handler(mTestableLooper.getLooper()),
                new FalsingManagerFake(),
                mMetricsLogger,
                mStatusBarStateController,
                mActivityStarter,
                mQSLogger,
                mController,
                mKeyguard,
                mHotspotController,
                mDialogTransitionAnimator,
                mConnectivityRepository,
                mJavaAdapter,
                mShadeDialogContextInteractor,
                mCastDetailsViewModelFactory,
                mDialogCreator
        );
        mCastTile.initialize();

        // Set the state to RESUMED so that TileJavaAdapter is collecting on flows
        mCastTile.setListening(new Object(), true);

        mTestableLooper.processAllMessages();

        ArgumentCaptor<HotspotController.Callback> hotspotCallbackArgumentCaptor =
                ArgumentCaptor.forClass(HotspotController.Callback.class);
        verify(mHotspotController).observe(any(LifecycleOwner.class),
                hotspotCallbackArgumentCaptor.capture());
        mHotspotCallback = hotspotCallbackArgumentCaptor.getValue();
    }

    private CastDevice createConnectedCastDevice() {
        return new CastDevice(
                "id",
                /* name= */ null,
                /* description= */ null,
                /* state= */ CastDevice.CastState.Connected,
                /* origin= */ CastDevice.CastOrigin.MediaProjection,
                /* tag= */ null);
    }

    private static class FakeDialogCreator extends CastTile.DialogCreator {
        Context mLastContextPassedToCreateDialog;

        @Override
        public Dialog createDialog(Context context, int routeTypes, View.OnClickListener listener,
                int theme, boolean showProgressBarWhenEmpty) {
            mLastContextPassedToCreateDialog = context;

            Window window = mock(Window.class);
            View decorView = mock(View.class);
            when(decorView.getResources()).thenReturn(context.getResources());
            when(window.getDecorView()).thenReturn(decorView);
            when(window.getAttributes()).thenReturn(new android.view.WindowManager.LayoutParams());

            Dialog dialog = mock(Dialog.class);
            when(dialog.getContext()).thenReturn(context);
            when(dialog.getWindow()).thenReturn(window);
            OnBackInvokedDispatcher backInvokedDispatcher = mock(OnBackInvokedDispatcher.class);
            when(dialog.getOnBackInvokedDispatcher()).thenReturn(backInvokedDispatcher);

            return dialog;
        }
    }

}
