/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.settingslib.media;

import static android.media.MediaRoute2Info.CONNECTION_STATE_CONNECTING;
import static android.media.MediaRoute2Info.TYPE_BLE_HEADSET;
import static android.media.MediaRoute2Info.TYPE_BLUETOOTH_A2DP;
import static android.media.MediaRoute2Info.TYPE_BUILTIN_SPEAKER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_TV;
import static android.media.MediaRoute2Info.TYPE_USB_DEVICE;
import static android.media.MediaRoute2Info.TYPE_WIRED_HEADSET;
import static android.media.MediaRoute2ProviderService.REASON_NETWORK_ERROR;
import static android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR;

import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING;
import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_SELECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.DeviceSuggestionsUpdatesCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2Manager;
import android.media.RouteListingPreference;
import android.media.RoutingSessionInfo;
import android.media.SuggestedDeviceInfo;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.media.flags.Flags;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.InfoMediaManager.Api34Impl;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class InfoMediaManagerTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private static final String TEST_PACKAGE_NAME = "com.test.packagename";
    private static final String TEST_ID = "test_id";
    private static final String TEST_ID_1 = "test_id_1";
    private static final String TEST_ID_2 = "test_id_2";
    private static final String TEST_ID_3 = "test_id_3";
    private static final String TEST_ID_4 = "test_id_4";

    private static final String TEST_NAME = "test_name";
    private static final String TEST_DUPLICATED_ID_1 = "test_duplicated_id_1";
    private static final String TEST_DUPLICATED_ID_2 = "test_duplicated_id_2";
    private static final String TEST_DUPLICATED_ID_3 = "test_duplicated_id_3";

    private static final String TEST_SYSTEM_ROUTE_ID = "TEST_SYSTEM_ROUTE_ID";
    private static final String TEST_BLUETOOTH_ROUTE_ID = "TEST_BT_ROUTE_ID";

    private static final RoutingSessionInfo TEST_SYSTEM_ROUTING_SESSION =
            new RoutingSessionInfo.Builder("FAKE_SYSTEM_ROUTING_SESSION_ID", TEST_PACKAGE_NAME)
                    .addSelectedRoute(TEST_SYSTEM_ROUTE_ID)
                    .addTransferableRoute(TEST_BLUETOOTH_ROUTE_ID)
                    .setSystemSession(true)
                    .build();

    private static final MediaRoute2Info TEST_SELECTED_SYSTEM_ROUTE =
            new MediaRoute2Info.Builder(TEST_SYSTEM_ROUTE_ID, "SELECTED_SYSTEM_ROUTE")
                    .setSystemRoute(true)
                    .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
                    .build();

    private static final MediaRoute2Info TEST_BLUETOOTH_ROUTE =
            new MediaRoute2Info.Builder(TEST_BLUETOOTH_ROUTE_ID, "BLUETOOTH_ROUTE")
                    .setSystemRoute(true)
                    .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
                    .setType(TYPE_BLUETOOTH_A2DP)
                    .setAddress("00:00:00:00:00:00")
                    .build();

    private static final RoutingSessionInfo TEST_REMOTE_ROUTING_SESSION =
            new RoutingSessionInfo.Builder("FAKE_REMOTE_ROUTING_SESSION_ID", TEST_PACKAGE_NAME)
                    .addSelectedRoute(TEST_ID_1)
                    .build();

    private static final MediaRoute2Info TEST_REMOTE_ROUTE =
            new MediaRoute2Info.Builder(TEST_ID_1, "REMOTE_ROUTE")
                    .setSystemRoute(true)
                    .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
                    .build();

    private static final int ASYNC_TIMEOUT_SECONDS = 5;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private MediaRouter2Manager mRouterManager;
    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock
    private InfoMediaManager.MediaDeviceCallback mCallback;
    @Mock
    private MediaSessionManager mMediaSessionManager;
    @Mock
    private ComponentName mComponentName;
    @Mock private MediaRouter2 mRouter2;
    @Mock private RoutingController mRoutingController;

    @Captor
    private ArgumentCaptor<DeviceSuggestionsUpdatesCallback> mDeviceSuggestionsUpdatesCallback;

    @Captor
    private ArgumentCaptor<List<SuggestedDeviceInfo>> mSuggestedDeviceInfoListCaptor;

    private RouterInfoMediaManager mInfoMediaManager;
    private Context mContext;

    @Before
    public void setUp() throws InfoMediaManager.PackageNotAvailableException {
        mContext = spy(RuntimeEnvironment.application);

        doReturn(mMediaSessionManager).when(mContext).getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        mInfoMediaManager = createRouterInfoMediaManager();
        mInfoMediaManager.mRouterManager = mRouterManager;
        when(mRouter2.getController(any())).thenReturn(mRoutingController);
        when(mRouter2.getControllers()).thenReturn(List.of(mRoutingController));
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(TEST_SYSTEM_ROUTING_SESSION);
    }

    @Test
    public void stopScan_notStartFirst_notCallsUnregister() {
        mInfoMediaManager.stopScan();

        verify(mRouter2, never()).cancelScanRequest(any());
    }

    @Test
    public void stopScan_startFirst_callsUnregister() {
        // Since test is running in Robolectric, return a fake session to avoid NPE.
        when(mRouterManager.getRoutingSessions(anyString()))
                .thenReturn(List.of(TEST_SYSTEM_ROUTING_SESSION));
        when(mRoutingController.getSelectedRoutes())
                .thenReturn(List.of(TEST_SELECTED_SYSTEM_ROUTE));

        MediaRouter2.ScanToken scanToken = ReflectionHelpers.callConstructor(
                MediaRouter2.ScanToken.class,
                ReflectionHelpers.ClassParameter.from(int.class, 1));
        when(mRouter2.requestScan(any())).thenReturn(scanToken);

        mInfoMediaManager.startScan();
        mInfoMediaManager.stopScan();

        verify(mRouter2).cancelScanRequest(scanToken);
    }

    @Test
    public void onRouteAdded_getAvailableRoutes_shouldAddMediaDevice() {
        final RoutingSessionInfo sessionInfo = mock(RoutingSessionInfo.class);
        final List<String> selectedRoutes = new ArrayList<>();
        selectedRoutes.add(TEST_ID);
        when(sessionInfo.getSelectedRoutes()).thenReturn(selectedRoutes);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(sessionInfo);

        final MediaRoute2Info info = mock(MediaRoute2Info.class);
        when(info.getId()).thenReturn(TEST_ID);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getDeduplicationIds()).thenReturn(Set.of());

        final List<MediaRoute2Info> routes = new ArrayList<>();
        routes.add(info);
        when(mRoutingController.getTransferableRoutes()).thenReturn(routes);

        final MediaDevice mediaDevice = mInfoMediaManager.findMediaDevice(TEST_ID);
        assertThat(mediaDevice).isNull();

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(routes);

        final MediaDevice infoDevice = mInfoMediaManager.mMediaDevices.get(0);
        assertThat(infoDevice.getId()).isEqualTo(TEST_ID);
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(infoDevice);
        assertThat(mInfoMediaManager.mMediaDevices).hasSize(routes.size());
    }

    @Test
    public void onSessionReleased_shouldUpdateConnectedDevice() {
        MediaRouter2.RoutingController remoteSessionController = mock(
                MediaRouter2.RoutingController.class);
        when(remoteSessionController.getRoutingSessionInfo()).thenReturn(
                TEST_REMOTE_ROUTING_SESSION);
        when(remoteSessionController.getSelectedRoutes()).thenReturn(List.of(TEST_REMOTE_ROUTE));
        when(mRouter2.getController(TEST_REMOTE_ROUTING_SESSION.getId())).thenReturn(
                remoteSessionController);

        MediaRouter2.RoutingController systemSessionController = mock(
                MediaRouter2.RoutingController.class);
        when(systemSessionController.getRoutingSessionInfo()).thenReturn(
                TEST_SYSTEM_ROUTING_SESSION);
        when(systemSessionController.getSelectedRoutes()).thenReturn(
                List.of(TEST_SELECTED_SYSTEM_ROUTE));
        when(mRouter2.getController(TEST_SYSTEM_ROUTING_SESSION.getId())).thenReturn(
                systemSessionController);

        // Active routing session is last one in list.
        when(mRouter2.getControllers()).thenReturn(
                List.of(systemSessionController, remoteSessionController));

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(new ArrayList<>());
        MediaDevice remoteDevice = mInfoMediaManager.findMediaDevice(TEST_REMOTE_ROUTE.getId());
        assertThat(remoteDevice).isNotNull();
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(remoteDevice);

        when(mRouter2.getControllers()).thenReturn(List.of(systemSessionController));
        mInfoMediaManager.mTransferCallback.onStop(remoteSessionController);
        MediaDevice systemRoute = mInfoMediaManager.findMediaDevice(TEST_SYSTEM_ROUTE_ID);
        assertThat(systemRoute).isNotNull();
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(systemRoute);
    }

    @Test
    public void onPreferredFeaturesChanged_samePackageName_shouldAddMediaDevice() {
        final RoutingSessionInfo sessionInfo = mock(RoutingSessionInfo.class);
        final List<String> selectedRoutes = new ArrayList<>();
        selectedRoutes.add(TEST_ID);
        when(sessionInfo.getSelectedRoutes()).thenReturn(selectedRoutes);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(sessionInfo);

        final MediaRoute2Info info = mock(MediaRoute2Info.class);
        when(info.getId()).thenReturn(TEST_ID);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getDeduplicationIds()).thenReturn(Set.of());

        final List<MediaRoute2Info> routes = new ArrayList<>();
        routes.add(info);
        when(mRoutingController.getTransferableRoutes()).thenReturn(routes);

        final MediaDevice mediaDevice = mInfoMediaManager.findMediaDevice(TEST_ID);
        assertThat(mediaDevice).isNull();

        mInfoMediaManager.mRouteCallback.onPreferredFeaturesChanged(new ArrayList<>());

        final MediaDevice infoDevice = mInfoMediaManager.mMediaDevices.get(0);
        assertThat(infoDevice.getId()).isEqualTo(TEST_ID);
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(infoDevice);
        assertThat(mInfoMediaManager.mMediaDevices).hasSize(routes.size());
    }

    @Test
    public void onRoutesChanged_getAvailableRoutes_shouldAddMediaDevice() {
        final RoutingSessionInfo sessionInfo = mock(RoutingSessionInfo.class);
        final List<String> selectedRoutes = new ArrayList<>();
        selectedRoutes.add(TEST_ID);
        when(sessionInfo.getSelectedRoutes()).thenReturn(selectedRoutes);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(sessionInfo);

        final MediaRoute2Info info = mock(MediaRoute2Info.class);
        when(info.getId()).thenReturn(TEST_ID);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getDeduplicationIds()).thenReturn(Set.of());

        final List<MediaRoute2Info> routes = new ArrayList<>();
        routes.add(info);
        when(mRoutingController.getTransferableRoutes()).thenReturn(routes);

        final MediaDevice mediaDevice = mInfoMediaManager.findMediaDevice(TEST_ID);
        assertThat(mediaDevice).isNull();

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(routes);

        final MediaDevice infoDevice = mInfoMediaManager.mMediaDevices.get(0);
        assertThat(infoDevice.getId()).isEqualTo(TEST_ID);
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(infoDevice);
        assertThat(mInfoMediaManager.mMediaDevices).hasSize(routes.size());
    }

    @Test
    public void onRoutesChanged_getAvailableRoutes_shouldFilterDevice() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT",
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        final RoutingSessionInfo sessionInfo = mock(RoutingSessionInfo.class);

        final List<String> selectedRoutes = new ArrayList<>();
        selectedRoutes.add(TEST_ID);
        when(sessionInfo.getSelectedRoutes()).thenReturn(selectedRoutes);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(sessionInfo);
        List<MediaRoute2Info> routes = getRoutesListWithDuplicatedIds();
        when(mRoutingController.getSelectedRoutes()).thenReturn(routes.subList(0, 1));
        when(mRoutingController.getTransferableRoutes()).thenReturn(routes);

        final MediaDevice mediaDevice = mInfoMediaManager.findMediaDevice(TEST_ID);
        assertThat(mediaDevice).isNull();

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(routes);

        final MediaDevice infoDevice = mInfoMediaManager.mMediaDevices.get(0);
        assertThat(infoDevice.getId()).isEqualTo(TEST_ID);
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(infoDevice);
        assertThat(mInfoMediaManager.mMediaDevices).hasSize(2);
    }

    @Test
    public void onRouteChanged_getAvailableRoutesWithPreferenceListExit_ordersRoutes() {
        RouteListingPreference routeListingPreference = setUpPreferenceList(
                true /* useSystemOrdering */);
        setUpSelectedRoutes(TEST_PACKAGE_NAME);

        final RoutingSessionInfo sessionInfo = mock(RoutingSessionInfo.class);

        when(mRoutingController.getRoutingSessionInfo()).thenReturn(sessionInfo);
        when(sessionInfo.getSelectedRoutes()).thenReturn(ImmutableList.of(TEST_ID));

        setAvailableRoutesList(TEST_PACKAGE_NAME);

        mInfoMediaManager.mRouteListingPreferenceCallback.accept(routeListingPreference);
        mInfoMediaManager.mRouteCallback.onRoutesUpdated(getRoutesListWithDuplicatedIds());

        assertThat(mInfoMediaManager.mMediaDevices).hasSize(4);
        assertThat(mInfoMediaManager.mMediaDevices.get(0).getId()).isEqualTo(TEST_ID);
        assertThat(mInfoMediaManager.mMediaDevices.get(1).getId()).isEqualTo(TEST_ID_1);
        assertThat(mInfoMediaManager.mMediaDevices.get(2).getId()).isEqualTo(TEST_ID_3);
        assertThat(mInfoMediaManager.mMediaDevices.get(3).getId()).isEqualTo(TEST_ID_4);
    }

    private RouteListingPreference setUpPreferenceList(boolean useSystemOrdering) {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT",
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        final List<RouteListingPreference.Item> preferenceItemList = new ArrayList<>();
        RouteListingPreference.Item item1 = new RouteListingPreference.Item.Builder(
                TEST_ID_3).build();
        RouteListingPreference.Item item2 = new RouteListingPreference.Item.Builder(
                TEST_ID_4).build();
        preferenceItemList.add(item1);
        preferenceItemList.add(item2);

        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder().setItems(
                        preferenceItemList).setUseSystemOrdering(useSystemOrdering).build();
        when(mRouter2.getRouteListingPreference())
                .thenReturn(routeListingPreference);
        return routeListingPreference;
    }

    private void setUpSelectedRoutes(String packageName) {
        final List<MediaRoute2Info> selectedRoutes = new ArrayList<>();
        final MediaRoute2Info info = mock(MediaRoute2Info.class);
        when(info.getId()).thenReturn(TEST_ID);
        when(info.getClientPackageName()).thenReturn(packageName);
        when(info.isSystemRoute()).thenReturn(true);
        selectedRoutes.add(info);
        when(mRoutingController.getSelectedRoutes()).thenReturn(selectedRoutes);
    }

    private List<MediaRoute2Info> setAvailableRoutesList(String packageName) {
        final List<MediaRoute2Info> availableRoutes = new ArrayList<>();
        final MediaRoute2Info availableInfo1 = mock(MediaRoute2Info.class);
        when(availableInfo1.getId()).thenReturn(TEST_ID_2);
        when(availableInfo1.getClientPackageName()).thenReturn(packageName);
        when(availableInfo1.getType()).thenReturn(TYPE_REMOTE_TV);
        availableRoutes.add(availableInfo1);

        final MediaRoute2Info availableInfo2 = mock(MediaRoute2Info.class);
        when(availableInfo2.getId()).thenReturn(TEST_ID_3);
        when(availableInfo2.getClientPackageName()).thenReturn(packageName);
        availableRoutes.add(availableInfo2);

        final MediaRoute2Info availableInfo3 = mock(MediaRoute2Info.class);
        when(availableInfo3.getId()).thenReturn(TEST_ID_4);
        when(availableInfo3.getClientPackageName()).thenReturn(packageName);
        availableRoutes.add(availableInfo3);

        final MediaRoute2Info availableInfo4 = mock(MediaRoute2Info.class);
        when(availableInfo4.getId()).thenReturn(TEST_ID_1);
        when(availableInfo4.isSystemRoute()).thenReturn(true);
        when(availableInfo4.getClientPackageName()).thenReturn(packageName);
        availableRoutes.add(availableInfo4);

        when(mRouter2.getRoutes()).thenReturn(availableRoutes);
        when(mRoutingController.getSelectableRoutes()).thenReturn(availableRoutes);

        return availableRoutes;
    }

    @Test
    public void hasPreferenceRouteListing_oldSdkVersion_returnsFalse() {
        assertThat(mInfoMediaManager.preferRouteListingOrdering()).isFalse();
    }

    @Test
    public void hasPreferenceRouteListing_newSdkVersionWithPreferenceExist_returnsTrue() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT",
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mRouter2.getRouteListingPreference()).thenReturn(
                new RouteListingPreference.Builder().setItems(
                        ImmutableList.of()).setUseSystemOrdering(false).build());

        assertThat(mInfoMediaManager.preferRouteListingOrdering()).isTrue();
    }

    @Test
    public void hasPreferenceRouteListing_newSdkVersionWithPreferenceNotExist_returnsFalse() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT",
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        when(mRouter2.getRouteListingPreference()).thenReturn(null);

        assertThat(mInfoMediaManager.preferRouteListingOrdering()).isFalse();
    }

    @Test
    public void getInAppOnlyItemRoutingReceiver_oldSdkVersion_returnsNull() {
        assertThat(mInfoMediaManager.getLinkedItemComponentName()).isNull();
    }

    @Test
    public void getInAppOnlyItemRoutingReceiver_newSdkVersionWithReceiverExist_returns() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT",
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mRouter2.getRouteListingPreference()).thenReturn(
                new RouteListingPreference.Builder().setItems(
                        ImmutableList.of()).setUseSystemOrdering(
                        false).setLinkedItemComponentName(mComponentName).build());

        assertThat(mInfoMediaManager.getLinkedItemComponentName()).isEqualTo(mComponentName);
    }

    private List<MediaRoute2Info> getRoutesListWithDuplicatedIds() {
        final List<MediaRoute2Info> routes = new ArrayList<>();
        final MediaRoute2Info info = mock(MediaRoute2Info.class);
        when(info.getId()).thenReturn(TEST_ID);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.isSystemRoute()).thenReturn(true);
        when(info.getDeduplicationIds()).thenReturn(
                Set.of(TEST_DUPLICATED_ID_1, TEST_DUPLICATED_ID_2));
        routes.add(info);

        final MediaRoute2Info info1 = mock(MediaRoute2Info.class);
        when(info1.getId()).thenReturn(TEST_ID_1);
        when(info1.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info1.isSystemRoute()).thenReturn(true);
        when(info1.getDeduplicationIds()).thenReturn(Set.of(TEST_DUPLICATED_ID_3));
        routes.add(info1);

        final MediaRoute2Info info2 = mock(MediaRoute2Info.class);
        when(info2.getId()).thenReturn(TEST_ID_2);
        when(info2.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info2.isSystemRoute()).thenReturn(true);
        when(info2.getDeduplicationIds()).thenReturn(Set.of(TEST_DUPLICATED_ID_3));
        routes.add(info2);

        final MediaRoute2Info info3 = mock(MediaRoute2Info.class);
        when(info3.getId()).thenReturn(TEST_ID_3);
        when(info3.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info3.isSystemRoute()).thenReturn(true);
        when(info3.getDeduplicationIds()).thenReturn(Set.of(TEST_DUPLICATED_ID_1));
        routes.add(info3);

        final MediaRoute2Info info4 = mock(MediaRoute2Info.class);
        when(info4.getId()).thenReturn(TEST_ID_4);
        when(info4.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info4.isSystemRoute()).thenReturn(true);
        when(info4.getDeduplicationIds()).thenReturn(Set.of(TEST_DUPLICATED_ID_2));
        routes.add(info4);

        return routes;
    }

    @Test
    public void onRoutesRemoved_getAvailableRoutes_shouldAddMediaDevice() {
        final RoutingSessionInfo sessionInfo = mock(RoutingSessionInfo.class);
        final List<String> selectedRoutes = new ArrayList<>();
        selectedRoutes.add(TEST_ID);
        when(sessionInfo.getSelectedRoutes()).thenReturn(selectedRoutes);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(sessionInfo);

        final MediaRoute2Info info = mock(MediaRoute2Info.class);
        when(info.getId()).thenReturn(TEST_ID);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getDeduplicationIds()).thenReturn(Set.of());

        final List<MediaRoute2Info> routes = new ArrayList<>();
        routes.add(info);
        when(mRoutingController.getTransferableRoutes()).thenReturn(routes);

        final MediaDevice mediaDevice = mInfoMediaManager.findMediaDevice(TEST_ID);
        assertThat(mediaDevice).isNull();

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(routes);

        final MediaDevice infoDevice = mInfoMediaManager.mMediaDevices.get(0);
        assertThat(infoDevice.getId()).isEqualTo(TEST_ID);
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(infoDevice);
        assertThat(mInfoMediaManager.mMediaDevices).hasSize(routes.size());
    }

    @Test
    public void addDeviceToPlayMedia_containSelectableRoutes_returnTrue() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);

        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        final MediaDevice device = new InfoMediaDevice(mContext,
                route2Info, /* dynamicRouteAttributes= */ null, /* item */ null);

        final List<String> list = new ArrayList<>();
        list.add(TEST_ID);

        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getSelectableRoutes()).thenReturn(list);
        when(route2Info.getId()).thenReturn(TEST_ID);
        when(route2Info.getName()).thenReturn(TEST_NAME);
        when(route2Info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        assertThat(mInfoMediaManager.addDeviceToPlayMedia(device, /* routingChangeInfo= */ null))
                .isTrue();
    }

    @Test
    public void addDeviceToPlayMedia_notContainSelectableRoutes_returnFalse() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);

        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        final MediaDevice device = new InfoMediaDevice(mContext,
                route2Info, /* dynamicRouteAttributes= */ null, /* item */ null);

        final List<String> list = new ArrayList<>();
        list.add("fake_id");

        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getSelectableRoutes()).thenReturn(list);
        when(route2Info.getId()).thenReturn(TEST_ID);
        when(route2Info.getName()).thenReturn(TEST_NAME);
        when(route2Info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        assertThat(mInfoMediaManager.addDeviceToPlayMedia(device, /* routingChangeInfo= */ null))
                .isFalse();
    }

    @Test
    public void removeDeviceFromMedia_containSelectedRoutes_returnTrue() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);

        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        final MediaDevice device = new InfoMediaDevice(mContext,
                route2Info, /* dynamicRouteAttributes= */ null, /* item */ null);

        final List<String> list = new ArrayList<>();
        list.add(TEST_ID);

        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getSelectedRoutes()).thenReturn(list);
        when(route2Info.getId()).thenReturn(TEST_ID);
        when(route2Info.getName()).thenReturn(TEST_NAME);
        when(route2Info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        assertThat(
                        mInfoMediaManager.removeDeviceFromPlayMedia(
                                device, /* routingChangeInfo= */ null))
                .isTrue();
    }

    @Test
    public void removeDeviceFromMedia_notContainSelectedRoutes_returnFalse() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);

        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        final MediaDevice device = new InfoMediaDevice(mContext,
                route2Info, /* dynamicRouteAttributes= */ null, /* item */ null);

        final List<String> list = new ArrayList<>();
        list.add("fake_id");

        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getSelectedRoutes()).thenReturn(list);
        when(route2Info.getId()).thenReturn(TEST_ID);
        when(route2Info.getName()).thenReturn(TEST_NAME);
        when(route2Info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        assertThat(
                        mInfoMediaManager.removeDeviceFromPlayMedia(
                                device, /* routingChangeInfo= */ null))
                .isFalse();
    }

    @Test
    public void populateDynamicRouteAttributes_checkList() {
        final CachedBluetoothDeviceManager cachedBluetoothDeviceManager =
                mock(CachedBluetoothDeviceManager.class);
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        final List<MediaRoute2Info> mediaRoute2Infos = new ArrayList<>();

        final MediaRoute2Info phoneRoute = mock(MediaRoute2Info.class);
        when(phoneRoute.getName()).thenReturn("PHONE");
        when(phoneRoute.isSystemRoute()).thenReturn(true);
        when(phoneRoute.getId()).thenReturn(TEST_ID_1);
        when(phoneRoute.getType()).thenReturn(TYPE_BUILTIN_SPEAKER);
        mediaRoute2Infos.add(phoneRoute);

        final MediaRoute2Info bluetoothRoute = mock(MediaRoute2Info.class);
        when(cachedDevice.getName()).thenReturn("BLUETOOTH");
        when(cachedDevice.getAddress()).thenReturn("00:00:00:00:00:00");
        when(bluetoothRoute.isSystemRoute()).thenReturn(true);
        when(bluetoothRoute.getName()).thenReturn("BLUETOOTH");
        when(bluetoothRoute.getId()).thenReturn(TEST_ID_2);
        when(bluetoothRoute.getType()).thenReturn(TYPE_BLE_HEADSET);
        when(bluetoothRoute.getAddress()).thenReturn("00:00:00:00:00:00");
        when(mLocalBluetoothManager.getCachedDeviceManager())
                .thenReturn(cachedBluetoothDeviceManager);
        when(cachedBluetoothDeviceManager.findDevice(any(BluetoothDevice.class)))
                .thenReturn(cachedDevice);

        mediaRoute2Infos.add(bluetoothRoute);

        final MediaRoute2Info complexRoute = mock(MediaRoute2Info.class);
        when(complexRoute.getName()).thenReturn("COMPLEX");
        when(complexRoute.isSystemRoute()).thenReturn(false);
        when(complexRoute.getId()).thenReturn(TEST_ID_3);
        when(complexRoute.getType()).thenReturn(TYPE_REMOTE_AUDIO_VIDEO_RECEIVER);
        mediaRoute2Infos.add(complexRoute);

        final MediaRoute2Info infoRoute = mock(MediaRoute2Info.class);
        when(infoRoute.getName()).thenReturn("INFO");
        when(infoRoute.isSystemRoute()).thenReturn(false);
        when(infoRoute.getId()).thenReturn(TEST_ID_4);
        when(infoRoute.getType()).thenReturn(TYPE_REMOTE_SPEAKER);
        mediaRoute2Infos.add(infoRoute);

        when(mRouter2.getRoutes()).thenReturn(mediaRoute2Infos);

        when(mRoutingController.getTransferableRoutes()).thenReturn(List.of(phoneRoute, infoRoute));
        when(mRoutingController.getSelectedRoutes()).thenReturn(List.of(bluetoothRoute));
        when(mRoutingController.getSelectableRoutes()).thenReturn(List.of(complexRoute));
        when(mRoutingController.getDeselectableRoutes()).thenReturn(List.of(infoRoute));

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(mediaRoute2Infos);

        when(mRouterManager.getRoutingSessions(TEST_PACKAGE_NAME))
                .thenReturn(List.of(TEST_SYSTEM_ROUTING_SESSION));
        List<MediaDevice> transferableDevices = mInfoMediaManager.mMediaDevices.stream().filter(
                MediaDevice::isTransferable).toList();
        List<MediaDevice> selectedDevices = mInfoMediaManager.mMediaDevices.stream().filter(
                MediaDevice::isSelected).toList();
        List<MediaDevice> selectableDevices = mInfoMediaManager.mMediaDevices.stream().filter(
                MediaDevice::isSelectable).toList();
        List<MediaDevice> deselectableDevices = mInfoMediaManager.mMediaDevices.stream().filter(
                MediaDevice::isDeselectable).toList();

        assertThat(transferableDevices.size()).isEqualTo(3);
        // The "COMPLEX" device is transferable because it's a non-system route for a system session
        assertThat(transferableDevices.get(0).getName()).isEqualTo("COMPLEX");
        assertThat(transferableDevices.get(0).getId()).isEqualTo(TEST_ID_3);
        assertThat(transferableDevices.get(1).getName()).isEqualTo("This phone");
        assertThat(transferableDevices.get(1).getId()).isEqualTo(TEST_ID_1);
        assertThat(transferableDevices.get(2).getName()).isEqualTo("INFO");
        assertThat(transferableDevices.get(2).getId()).isEqualTo(TEST_ID_4);
        MediaDevice phoneDevice = transferableDevices.get(1);
        assertThat(phoneDevice.isTransferable()).isTrue();
        assertThat(phoneDevice.isSelected()).isFalse();
        assertThat(phoneDevice.isSelectable()).isFalse();
        assertThat(phoneDevice.isDeselectable()).isFalse();

        assertThat(selectedDevices.size()).isEqualTo(1);
        MediaDevice selectedDevice = selectedDevices.getFirst();
        assertThat(selectedDevice.getName()).isEqualTo("BLUETOOTH");
        assertThat(selectedDevice.getId()).isEqualTo("00:00:00:00:00:00");
        assertThat(selectedDevice.isTransferable()).isFalse();
        assertThat(selectedDevice.isSelected()).isTrue();
        assertThat(selectedDevice.isSelectable()).isFalse();
        assertThat(selectedDevice.isDeselectable()).isFalse();

        assertThat(selectableDevices.size()).isEqualTo(1);
        MediaDevice selectableDevice = selectableDevices.getFirst();
        assertThat(selectableDevice.getName()).isEqualTo("COMPLEX");
        assertThat(selectableDevice.getId()).isEqualTo(TEST_ID_3);
        assertThat(selectableDevice.isTransferable()).isTrue();
        assertThat(selectableDevice.isSelected()).isFalse();
        assertThat(selectableDevice.isSelectable()).isTrue();
        assertThat(selectableDevice.isDeselectable()).isFalse();

        assertThat(deselectableDevices.size()).isEqualTo(1);
        MediaDevice deselectableDevice = deselectableDevices.getFirst();
        assertThat(deselectableDevice.getName()).isEqualTo("INFO");
        assertThat(deselectableDevice.getId()).isEqualTo(TEST_ID_4);
        assertThat(deselectableDevice.isDeselectable()).isTrue();
        assertThat(deselectableDevice.isTransferable()).isTrue();
        assertThat(deselectableDevice.isSelected()).isFalse();
        assertThat(deselectableDevice.isSelectable()).isFalse();
        assertThat(deselectableDevice.isDeselectable()).isTrue();
    }

    @Test
    public void adjustSessionVolume_routingSessionInfoIsNull_noCrash() {
        mInfoMediaManager.adjustSessionVolume(null, 10);
    }

    @Test
    public void getSessionVolumeMax_containPackageName_returnMaxVolume() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        mInfoMediaManager.getSessionVolumeMax();

        verify(info).getVolumeMax();
    }

    @Test
    public void getSessionVolume_containPackageName_returnMaxVolume() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        mInfoMediaManager.getSessionVolume();

        verify(info).getVolume();
    }

    @Test
    public void getRemoteSessions_returnsRemoteSessions() {
        final List<RoutingSessionInfo> infos = new ArrayList<>();
        infos.add(mock(RoutingSessionInfo.class));
        when(mRouterManager.getRemoteSessions()).thenReturn(infos);

        assertThat(mInfoMediaManager.getRemoteSessions()).containsExactlyElementsIn(infos);
    }

    @Test
    public void getSessionReleaseType_returnCorrectType() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getReleaseType()).thenReturn(RoutingSessionInfo.RELEASE_TYPE_SHARING);

        assertThat(mInfoMediaManager.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_TYPE_SHARING);
    }

    @Test
    public void releaseSession_removeSuccessfully_returnTrue() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);

        assertThat(mInfoMediaManager.releaseSession()).isTrue();
    }

    @Test
    public void getSessionName_containPackageName_returnName() {
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(mRoutingController.getRoutingSessionInfo()).thenReturn(info);
        when(info.getClientPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(info.getName()).thenReturn(TEST_NAME);

        assertThat(mInfoMediaManager.getSessionName()).isEqualTo(TEST_NAME);
    }

    @Test
    public void onTransferFailed_notDispatchOnRequestFailed() {
        mInfoMediaManager.registerCallback(mCallback);

        mInfoMediaManager.mTransferCallback.onTransferFailure(
                getRoutesListWithDuplicatedIds().getFirst());

        verify(mCallback, never()).onRequestFailed(REASON_UNKNOWN_ERROR);
    }

    @Test
    public void onRequestFailed_shouldDispatchOnRequestFailed() {
        mInfoMediaManager.registerCallback(mCallback);

        mInfoMediaManager.mTransferCallback.onRequestFailed(REASON_NETWORK_ERROR);

        verify(mCallback).onRequestFailed(REASON_NETWORK_ERROR);
    }

    @Test
    public void onTransferred_getAvailableRoutes_shouldAddMediaDevice() {
        // Since test is running in Robolectric, return a fake session to avoid NPE.
        when(mRouterManager.getRoutingSessions(anyString()))
                .thenReturn(List.of(TEST_SYSTEM_ROUTING_SESSION));
        when(mRoutingController.getSelectedRoutes())
                .thenReturn(List.of(TEST_SELECTED_SYSTEM_ROUTE));

        mInfoMediaManager.registerCallback(mCallback);

        MediaDevice mediaDevice = mInfoMediaManager.getCurrentConnectedDevice();
        assertThat(mediaDevice).isNotNull();
        assertThat(mediaDevice.getId()).isEqualTo(TEST_SYSTEM_ROUTE_ID);

        when(mRouterManager.getRoutingSessions(anyString()))
                .thenReturn(List.of(TEST_SYSTEM_ROUTING_SESSION, TEST_REMOTE_ROUTING_SESSION));
        when(mRoutingController.getSelectedRoutes()).thenReturn(List.of(TEST_REMOTE_ROUTE));

        mInfoMediaManager.mTransferCallback.onTransfer(mRoutingController, mRoutingController);

        final MediaDevice infoDevice = mInfoMediaManager.mMediaDevices.get(0);
        assertThat(infoDevice).isNotNull();
        assertThat(infoDevice.getId()).isEqualTo(TEST_REMOTE_ROUTE.getId());
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(infoDevice);
        verify(mCallback).onConnectedDeviceChanged(TEST_REMOTE_ROUTE.getId());
    }

    @Test
    public void onSessionUpdated_shouldDispatchDeviceListAdded() {
        // Since test is running in Robolectric, return a fake session to avoid NPE.
        when(mRouterManager.getRoutingSessions(anyString()))
                .thenReturn(List.of(TEST_SYSTEM_ROUTING_SESSION));
        when(mRouterManager.getSelectedRoutes(any()))
                .thenReturn(List.of(TEST_SELECTED_SYSTEM_ROUTE));

        mInfoMediaManager.registerCallback(mCallback);

        mInfoMediaManager.mControllerCallback.onControllerUpdated(mRoutingController);

        // Expecting 1st call after registerCallback() and 2nd call after onSessionUpdated().
        verify(mCallback, times(2)).onDeviceListAdded(any());
    }

    @Test
    public void addMediaDevice_verifyDeviceTypeCanCorrespondToMediaDevice() {
        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        final CachedBluetoothDeviceManager cachedBluetoothDeviceManager =
                mock(CachedBluetoothDeviceManager.class);
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);

        when(route2Info.getType()).thenReturn(TYPE_REMOTE_SPEAKER);
        when(route2Info.getId()).thenReturn(TEST_ID);
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);
        assertThat(mInfoMediaManager.mMediaDevices.get(0) instanceof InfoMediaDevice).isTrue();

        when(route2Info.getType()).thenReturn(TYPE_USB_DEVICE);
        when(route2Info.getId()).thenReturn(TEST_ID);
        mInfoMediaManager.mMediaDevices.clear();
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);
        assertThat(mInfoMediaManager.mMediaDevices.get(0) instanceof PhoneMediaDevice).isTrue();

        when(route2Info.getType()).thenReturn(TYPE_WIRED_HEADSET);
        when(route2Info.getId()).thenReturn(TEST_ID);
        mInfoMediaManager.mMediaDevices.clear();
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);
        assertThat(mInfoMediaManager.mMediaDevices.get(0) instanceof PhoneMediaDevice).isTrue();

        when(route2Info.getType()).thenReturn(TYPE_BLUETOOTH_A2DP);
        when(route2Info.getAddress()).thenReturn("00:00:00:00:00:00");
        when(route2Info.getId()).thenReturn(TEST_ID);
        when(mLocalBluetoothManager.getCachedDeviceManager())
                .thenReturn(cachedBluetoothDeviceManager);
        when(cachedBluetoothDeviceManager.findDevice(any(BluetoothDevice.class)))
                .thenReturn(cachedDevice);
        mInfoMediaManager.mMediaDevices.clear();
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);
        assertThat(mInfoMediaManager.mMediaDevices.get(0) instanceof BluetoothMediaDevice).isTrue();

        when(route2Info.getType()).thenReturn(TYPE_BUILTIN_SPEAKER);
        mInfoMediaManager.mMediaDevices.clear();
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);
        assertThat(mInfoMediaManager.mMediaDevices.get(0) instanceof PhoneMediaDevice).isTrue();
    }

    @Test
    public void addMediaDevice_routeInConnectingState_setsConnectingToDevice() {
        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        when(route2Info.getConnectionState()).thenReturn(CONNECTION_STATE_CONNECTING);
        when(route2Info.getType()).thenReturn(TYPE_REMOTE_SPEAKER);
        when(route2Info.getId()).thenReturn(TEST_ID);
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);

        assertThat(mInfoMediaManager.mMediaDevices.get(0).getState()).isEqualTo(STATE_CONNECTING);
    }

    @Test
    public void addMediaDevice_cachedBluetoothDeviceIsNull_shouldNotAdded() {
        final MediaRoute2Info route2Info = mock(MediaRoute2Info.class);
        final CachedBluetoothDeviceManager cachedBluetoothDeviceManager =
                mock(CachedBluetoothDeviceManager.class);

        when(route2Info.getType()).thenReturn(TYPE_BLUETOOTH_A2DP);
        when(route2Info.getAddress()).thenReturn("00:00:00:00:00:00");
        when(mLocalBluetoothManager.getCachedDeviceManager())
                .thenReturn(cachedBluetoothDeviceManager);
        when(cachedBluetoothDeviceManager.findDevice(any(BluetoothDevice.class)))
                .thenReturn(null);

        mInfoMediaManager.mMediaDevices.clear();
        mInfoMediaManager.addMediaDeviceLocked(route2Info, TEST_SYSTEM_ROUTING_SESSION, null);

        assertThat(mInfoMediaManager.mMediaDevices.size()).isEqualTo(0);
    }

    @Test
    public void addMediaDevice_withAddresslessBluetoothDevice_shouldIgnoreDeviceAndNotCrash() {
        MediaRoute2Info bluetoothRoute =
                new MediaRoute2Info.Builder(TEST_BLUETOOTH_ROUTE).setAddress(null).build();

        final CachedBluetoothDeviceManager cachedBluetoothDeviceManager =
                mock(CachedBluetoothDeviceManager.class);
        when(mLocalBluetoothManager.getCachedDeviceManager())
                .thenReturn(cachedBluetoothDeviceManager);
        when(cachedBluetoothDeviceManager.findDevice(any(BluetoothDevice.class))).thenReturn(null);

        mInfoMediaManager.mMediaDevices.clear();
        mInfoMediaManager.addMediaDeviceLocked(bluetoothRoute, TEST_SYSTEM_ROUTING_SESSION, null);

        assertThat(mInfoMediaManager.mMediaDevices.size()).isEqualTo(0);
    }

    @Test
    public void onRoutesUpdated_setsFirstSelectedRouteAsCurrentConnectedDevice() {
        final CachedBluetoothDeviceManager cachedBluetoothDeviceManager =
                mock(CachedBluetoothDeviceManager.class);

        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        RoutingSessionInfo selectedBtSession =
                new RoutingSessionInfo.Builder(TEST_SYSTEM_ROUTING_SESSION)
                        .clearSelectedRoutes()
                        .clearTransferableRoutes()
                        .addSelectedRoute(TEST_BLUETOOTH_ROUTE_ID)
                        .addTransferableRoute(TEST_SYSTEM_ROUTE_ID)
                        .build();

        when(mRoutingController.getRoutingSessionInfo()).thenReturn(selectedBtSession);
        when(mRoutingController.getSelectedRoutes()).thenReturn(List.of(TEST_BLUETOOTH_ROUTE));
        when(mLocalBluetoothManager.getCachedDeviceManager())
                .thenReturn(cachedBluetoothDeviceManager);
        when(cachedBluetoothDeviceManager.findDevice(any(BluetoothDevice.class)))
                .thenReturn(cachedDevice);

        mInfoMediaManager.mRouteCallback.onRoutesUpdated(getRoutesListWithDuplicatedIds());

        MediaDevice device = mInfoMediaManager.mMediaDevices.get(0);

        assertThat(device instanceof BluetoothMediaDevice).isTrue();
        assertThat(device.getState()).isEqualTo(STATE_SELECTED);
        assertThat(mInfoMediaManager.getCurrentConnectedDevice()).isEqualTo(device);
    }

    private RouterInfoMediaManager createRouterInfoMediaManager() {
        return new RouterInfoMediaManager(
                mContext,
                TEST_PACKAGE_NAME,
                mContext.getUser(),
                mLocalBluetoothManager,
                /* mediaController */ null,
                mRouter2,
                mRouterManager);
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_listenersNotified() {
        SuggestedDeviceInfo suggestedDeviceInfo =
                new SuggestedDeviceInfo.Builder("device_name", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        setAvailableRoutesList(TEST_PACKAGE_NAME);
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", List.of(suggestedDeviceInfo));

        verify(mCallback).onDeviceListAdded(any());
        verify(mCallback).onDeviceSuggestionsUpdated(mSuggestedDeviceInfoListCaptor.capture());
        assertThat(mSuggestedDeviceInfoListCaptor.getValue()).isEqualTo(
                List.of(suggestedDeviceInfo));
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_routesNotSet_listenersNotified() {
        SuggestedDeviceInfo suggestedDeviceInfo =
                new SuggestedDeviceInfo.Builder("device_name", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", List.of(suggestedDeviceInfo));

        verify(mCallback).onDeviceSuggestionsUpdated(mSuggestedDeviceInfoListCaptor.capture());
        assertThat(mSuggestedDeviceInfoListCaptor.getValue()).isEqualTo(
                List.of(suggestedDeviceInfo));
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_mediaDeviceIsSuggested() {
        SuggestedDeviceInfo suggestedDeviceInfo =
                new SuggestedDeviceInfo.Builder("device_name", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        setAvailableRoutesList(TEST_PACKAGE_NAME);
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", List.of(suggestedDeviceInfo));

        MediaDevice mediaDevice = mediaManager.mMediaDevices.get(1);
        assertThat(mediaDevice.getId()).isEqualTo(TEST_ID_3);
        assertThat(mediaDevice.isSuggestedDevice()).isTrue();
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_noSuggestedDevices_noSuggestedMediaDevices() {
        SuggestedDeviceInfo suggestedDeviceInfo =
                new SuggestedDeviceInfo.Builder("device_name", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        setAvailableRoutesList(TEST_PACKAGE_NAME);
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", List.of(suggestedDeviceInfo));
        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", null);

        MediaDevice mediaDevice = mediaManager.mMediaDevices.get(1);
        assertThat(mediaDevice.getId()).isEqualTo(TEST_ID_3);
        assertThat(mediaDevice.isSuggestedDevice()).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_multipleProviders_noSuggestedMediaDevices() {
        SuggestedDeviceInfo suggestedDeviceInfo1 =
                new SuggestedDeviceInfo.Builder("device_name_1", TEST_ID_3, 0).build();
        SuggestedDeviceInfo suggestedDeviceInfo2 =
                new SuggestedDeviceInfo.Builder("device_name_2", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        setAvailableRoutesList(TEST_PACKAGE_NAME);
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name_1", List.of(suggestedDeviceInfo1));
        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name_2", List.of(suggestedDeviceInfo2));

        MediaDevice mediaDevice = mediaManager.mMediaDevices.get(1);
        assertThat(mediaDevice.getId()).isEqualTo(TEST_ID_3);
        assertThat(mediaDevice.isSuggestedDevice()).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_firstSuggestionFromSamePackage_suggestionIsFromSamePackage() {
        SuggestedDeviceInfo suggestedDeviceInfo =
                new SuggestedDeviceInfo.Builder("device_name", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        setAvailableRoutesList(TEST_PACKAGE_NAME);
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated(TEST_PACKAGE_NAME, List.of(suggestedDeviceInfo));
        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", null);

        MediaDevice mediaDevice = mediaManager.mMediaDevices.get(1);
        assertThat(mediaDevice.getId()).isEqualTo(TEST_ID_3);
        assertThat(mediaDevice.isSuggestedDevice()).isTrue();
    }

    @EnableFlags(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API)
    @Test
    public void onSuggestionsUpdated_laterSuggestionFromSamePackage_suggestionIsFromSamePackage() {
        SuggestedDeviceInfo suggestedDeviceInfo =
                new SuggestedDeviceInfo.Builder("device_name", TEST_ID_3, 0).build();
        RouterInfoMediaManager mediaManager = createRouterInfoMediaManager();
        setAvailableRoutesList(TEST_PACKAGE_NAME);
        mediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
        verify(mRouter2)
                .registerDeviceSuggestionsUpdatesCallback(
                        any(), mDeviceSuggestionsUpdatesCallback.capture());

        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated("random_package_name", null);
        mDeviceSuggestionsUpdatesCallback
                .getValue()
                .onSuggestionsUpdated(TEST_PACKAGE_NAME, List.of(suggestedDeviceInfo));

        MediaDevice mediaDevice = mediaManager.mMediaDevices.get(1);
        assertThat(mediaDevice.getId()).isEqualTo(TEST_ID_3);
        assertThat(mediaDevice.isSuggestedDevice()).isTrue();
    }

    @Test
    public void arrangeRouteListByPreference_useSystemOrderingIsFalse() {
        RouteListingPreference routeListingPreference = setUpPreferenceList(false);
        List<MediaRoute2Info> routes = setAvailableRoutesList(TEST_PACKAGE_NAME);
        when(mRoutingController.getSelectedRoutes()).thenReturn(routes);

        List<MediaRoute2Info> routeOrder =
                Api34Impl.arrangeRouteListByPreference(
                        routes, routes, routeListingPreference);

        assertThat(routeOrder.get(0).getId()).isEqualTo(TEST_ID_3);
        assertThat(routeOrder.get(1).getId()).isEqualTo(TEST_ID_4);
        assertThat(routeOrder.get(2).getId()).isEqualTo(TEST_ID_2);
        assertThat(routeOrder.get(3).getId()).isEqualTo(TEST_ID_1);
    }

    @Test
    public void arrangeRouteListByPreference_useSystemOrderingIsTrue() {
        RouteListingPreference routeListingPreference = setUpPreferenceList(true);
        List<MediaRoute2Info> routes = setAvailableRoutesList(TEST_PACKAGE_NAME);
        when(mRoutingController.getSelectedRoutes()).thenReturn(routes);

        List<MediaRoute2Info> routeOrder =
                Api34Impl.arrangeRouteListByPreference(
                        routes, routes, routeListingPreference);

        assertThat(routeOrder.get(0).getId()).isEqualTo(TEST_ID_2);
        assertThat(routeOrder.get(1).getId()).isEqualTo(TEST_ID_3);
        assertThat(routeOrder.get(2).getId()).isEqualTo(TEST_ID_4);
        assertThat(routeOrder.get(3).getId()).isEqualTo(TEST_ID_1);
    }

    @Test
    public void selectedRouteAppearsFirst() {
        RouteListingPreference routeListingPreference = setUpPreferenceList(true);
        List<MediaRoute2Info> routes = setAvailableRoutesList(TEST_PACKAGE_NAME);
        List<MediaRoute2Info> selectedRoutes = List.of(routes.get(2));

        List<MediaRoute2Info> routeOrder =
                Api34Impl.arrangeRouteListByPreference(
                        selectedRoutes, routes, routeListingPreference);

        assertThat(routeOrder.stream().map(MediaRoute2Info::getId).toArray())
                .asList()
                .containsExactly(TEST_ID_4, TEST_ID_1, TEST_ID_3)
                .inOrder();
    }
}
