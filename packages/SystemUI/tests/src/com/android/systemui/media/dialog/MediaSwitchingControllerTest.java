/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog;

import static android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;

import static com.android.systemui.media.dialog.MediaItem.MediaItemType.TYPE_DEVICE;
import static com.android.systemui.media.dialog.MediaItem.MediaItemType.TYPE_DEVICE_GROUP;
import static com.android.systemui.media.dialog.MediaItem.MediaItemType.TYPE_GROUP_DIVIDER;

import static com.google.common.truth.Truth.assertThat;

import static kotlinx.coroutines.flow.StateFlowKt.MutableStateFlow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.app.Notification;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaRoute2Info;
import android.media.NearbyDevice;
import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;
import android.media.session.ISessionController;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.PowerExemptionManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.service.notification.StatusBarNotification;
import android.testing.TestableLooper;
import android.view.View;

import androidx.core.graphics.drawable.IconCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.media.flags.Flags;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.settingslib.media.InfoMediaManager;
import com.android.settingslib.media.InputMediaDevice;
import com.android.settingslib.media.InputRouteManager;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;
import com.android.settingslib.volume.data.repository.AudioSharingRepository;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.SysuiTestCaseExtKt;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.kosmos.Kosmos;
import com.android.systemui.media.nearby.NearbyMediaDevicesManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.util.time.FakeSystemClock;
import com.android.systemui.volume.panel.domain.interactor.VolumePanelGlobalStateInteractor;
import com.android.systemui.volume.panel.domain.interactor.VolumePanelGlobalStateInteractorKosmosKt;

import com.google.common.collect.ImmutableList;

import kotlinx.coroutines.flow.MutableStateFlow;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class MediaSwitchingControllerTest extends SysuiTestCase {
    private static final String TEST_DEVICE_1_ID = "test_device_1_id";
    private static final String TEST_DEVICE_2_ID = "test_device_2_id";
    private static final String TEST_DEVICE_3_ID = "test_device_3_id";
    private static final String TEST_DEVICE_4_ID = "test_device_4_id";
    private static final String TEST_DEVICE_5_ID = "test_device_5_id";
    private static final String TEST_ARTIST = "test_artist";
    private static final String TEST_SONG = "test_song";
    private static final String TEST_SESSION_ID = "test_session_id";
    private static final String TEST_SESSION_NAME = "test_session_name";
    private static final int MAX_VOLUME = 1;
    private static final int CURRENT_VOLUME = 0;
    private static final boolean VOLUME_FIXED_TRUE = true;
    private static final int CALLBACK_WAIT_TIME_MS = 10_000;
    private static final String PRODUCT_NAME_BUILTIN_MIC = "Built-in Mic";
    private static final String PRODUCT_NAME_WIRED_HEADSET = "My Wired Headset";

    @Mock
    private DialogTransitionAnimator mDialogTransitionAnimator;
    @Mock
    private ActivityTransitionAnimator.Controller mActivityTransitionAnimatorController;
    @Mock
    private NearbyMediaDevicesManager mNearbyMediaDevicesManager;
    // Mock
    @Mock
    private MediaController mSessionMediaController;
    @Mock
    private MediaSessionManager mMediaSessionManager;
    @Mock
    private CachedBluetoothDeviceManager mCachedBluetoothDeviceManager;
    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock private MediaSwitchingController.Callback mCb;
    @Mock
    private MediaDevice mMediaDevice1;
    @Mock
    private MediaDevice mMediaDevice2;
    @Mock private MediaDevice mMediaDevice3;
    @Mock private MediaDevice mMediaDevice4;
    @Mock private MediaDevice mMediaDevice5;
    @Mock private NearbyDevice mNearbyDevice1;
    @Mock
    private NearbyDevice mNearbyDevice2;
    @Mock
    private MediaMetadata mMediaMetadata;
    @Mock
    private RoutingSessionInfo mRemoteSessionInfo;
    @Mock
    private ActivityStarter mStarter;
    @Mock
    private AudioManager mAudioManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private ActivityTransitionAnimator.Controller mController;
    @Mock
    private PowerExemptionManager mPowerExemptionManager;
    @Mock
    private CommonNotifCollection mNotifCollection;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private Drawable mDrawable;
    @Mock
    private PlaybackState mPlaybackState;
    @Mock private InfoMediaManager mInfoMediaManager;

    @Mock
    private UserTracker mUserTracker;
    @Mock private AudioSharingRepository mAudioSharingRepository;

    private final Kosmos mKosmos = SysuiTestCaseExtKt.testKosmos(this);
    @Mock private JavaAdapter mJavaAdapter;
    @Captor private ArgumentCaptor<Consumer<Boolean>> mInAudioSharingCaptor;

    private final FakeSystemClock mClock = new FakeSystemClock();

    private View mDialogLaunchView = mock(View.class);
    private MediaSwitchingController.Callback mCallback =
            mock(MediaSwitchingController.Callback.class);

    final Notification mNotification = mock(Notification.class);
    private final VolumePanelGlobalStateInteractor mVolumePanelGlobalStateInteractor =
            VolumePanelGlobalStateInteractorKosmosKt.getVolumePanelGlobalStateInteractor(
                    mKosmos);

    private Context mSpyContext;
    private String mPackageName = null;
    private MediaSwitchingController mMediaSwitchingController;
    private LocalMediaManager mLocalMediaManager;
    private InputRouteManager mInputRouteManager;
    private List<MediaController> mMediaControllers = new ArrayList<>();
    private List<MediaDevice> mMediaDevices = new ArrayList<>();
    private List<NearbyDevice> mNearbyDevices = new ArrayList<>();
    private MediaDescription mMediaDescription;
    private List<RoutingSessionInfo> mRoutingSessionInfos = new ArrayList<>();

    @Before
    public void setUp() {
        mPackageName = mContext.getPackageName();

        MockitoAnnotations.initMocks(this);
        mContext.setMockPackageManager(mPackageManager);
        mSpyContext = spy(mContext);
        Resources spyResources = spy(mContext.getResources());
        when(mSpyContext.getResources()).thenReturn(spyResources);
        when(spyResources.getBoolean(
                R.bool.config_enableInputRouting)).thenReturn(true);
        final UserHandle userHandle = mock(UserHandle.class);
        when(mUserTracker.getUserHandle()).thenReturn(userHandle);
        when(mSessionMediaController.getPackageName()).thenReturn(mPackageName);
        when(mSessionMediaController.getPlaybackState()).thenReturn(mPlaybackState);
        mMediaControllers.add(mSessionMediaController);
        when(mMediaSessionManager.getActiveSessionsForUser(any(), eq(userHandle)))
                .thenReturn(mMediaControllers);
        doReturn(mMediaSessionManager).when(mSpyContext).getSystemService(
                MediaSessionManager.class);
        when(mLocalBluetoothManager.getCachedDeviceManager()).thenReturn(
                mCachedBluetoothDeviceManager);

        mMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        mPackageName,
                        mContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);
        mLocalMediaManager = spy(mMediaSwitchingController.mLocalMediaManager);
        when(mLocalMediaManager.isPreferenceRouteListingExist()).thenReturn(false);
        mMediaSwitchingController.mLocalMediaManager = mLocalMediaManager;

        mMediaSwitchingController.mInputRouteManager =
                new InputRouteManager(mContext, mAudioManager, mInfoMediaManager);
        mInputRouteManager = spy(mMediaSwitchingController.mInputRouteManager);
        mMediaSwitchingController.mInputRouteManager = mInputRouteManager;
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
                .thenReturn(new AudioDeviceInfo[0]);

        MediaDescription.Builder builder = new MediaDescription.Builder();
        builder.setTitle(TEST_SONG);
        builder.setSubtitle(TEST_ARTIST);
        mMediaDescription = builder.build();
        when(mMediaMetadata.getDescription()).thenReturn(mMediaDescription);
        when(mMediaDevice1.getId()).thenReturn(TEST_DEVICE_1_ID);
        when(mMediaDevice2.getId()).thenReturn(TEST_DEVICE_2_ID);
        mMediaDevices.add(mMediaDevice1);
        mMediaDevices.add(mMediaDevice2);

        when(mNearbyDevice1.getMediaRoute2Id()).thenReturn(TEST_DEVICE_1_ID);
        when(mNearbyDevice1.getRangeZone()).thenReturn(NearbyDevice.RANGE_FAR);
        when(mNearbyDevice2.getMediaRoute2Id()).thenReturn(TEST_DEVICE_2_ID);
        when(mNearbyDevice2.getRangeZone()).thenReturn(NearbyDevice.RANGE_CLOSE);
        mNearbyDevices.add(mNearbyDevice1);
        mNearbyDevices.add(mNearbyDevice2);

        final List<NotificationEntry> entryList = new ArrayList<>();
        final NotificationEntry entry = mock(NotificationEntry.class);
        final StatusBarNotification sbn = mock(StatusBarNotification.class);
        final Bundle bundle = mock(Bundle.class);
        final MediaSession.Token token = mock(MediaSession.Token.class);
        final ISessionController binder = mock(ISessionController.class);
        entryList.add(entry);

        when(mNotification.isMediaNotification()).thenReturn(false);
        when(mNotifCollection.getAllNotifs()).thenReturn(entryList);
        when(entry.getSbn()).thenReturn(sbn);
        when(sbn.getNotification()).thenReturn(mNotification);
        when(sbn.getPackageName()).thenReturn(mPackageName);
        mNotification.extras = bundle;
        when(bundle.getParcelable(Notification.EXTRA_MEDIA_SESSION,
                MediaSession.Token.class)).thenReturn(token);
        when(token.getBinder()).thenReturn(binder);
    }

    @Test
    public void start_verifyLocalMediaManagerInit() {
        mMediaSwitchingController.start(mCb);

        verify(mLocalMediaManager).registerCallback(mMediaSwitchingController);
        verify(mLocalMediaManager).startScan();
    }

    @Test
    public void stop_verifyLocalMediaManagerDeinit() {
        mMediaSwitchingController.start(mCb);
        reset(mLocalMediaManager);

        mMediaSwitchingController.stop();

        verify(mLocalMediaManager).unregisterCallback(mMediaSwitchingController);
        verify(mLocalMediaManager).stopScan();
    }

    @Test
    public void start_notificationNotFound_mediaControllerInitFromSession() {
        mMediaSwitchingController.start(mCb);

        verify(mSessionMediaController).registerCallback(any());
    }

    @Test
    public void start_MediaNotificationFound_mediaControllerNotInitFromSession() {
        when(mNotification.isMediaNotification()).thenReturn(true);
        mMediaSwitchingController.start(mCb);

        verify(mSessionMediaController, never()).registerCallback(any());
        verifyNoMoreInteractions(mMediaSessionManager);
    }

    @Test
    public void start_withoutPackageName_verifyMediaControllerInit() {
        mMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        null,
                        mContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);

        mMediaSwitchingController.start(mCb);

        verify(mSessionMediaController, never()).registerCallback(any());
    }

    @Test
    public void start_nearbyMediaDevicesManagerNotNull_registersNearbyDevicesCallback() {
        mMediaSwitchingController.start(mCb);

        verify(mNearbyMediaDevicesManager).registerNearbyDevicesCallback(any());
    }

    @Test
    public void stop_withPackageName_verifyMediaControllerDeinit() {
        mMediaSwitchingController.start(mCb);
        reset(mSessionMediaController);

        mMediaSwitchingController.stop();

        verify(mSessionMediaController).unregisterCallback(any());
    }

    @Test
    public void stop_withoutPackageName_verifyMediaControllerDeinit() {
        mMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        null,
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);

        mMediaSwitchingController.start(mCb);

        mMediaSwitchingController.stop();

        verify(mSessionMediaController, never()).unregisterCallback(any());
    }

    @Test
    public void stop_nearbyMediaDevicesManagerNotNull_unregistersNearbyDevicesCallback() {
        mMediaSwitchingController.start(mCb);
        reset(mSessionMediaController);

        mMediaSwitchingController.stop();

        verify(mNearbyMediaDevicesManager).unregisterNearbyDevicesCallback(any());
    }

    @Test
    public void tryToLaunchMediaApplication_nullIntent_skip() {
        mMediaSwitchingController.tryToLaunchMediaApplication(mDialogLaunchView);

        verify(mCb, never()).dismissDialog();
    }

    @Test
    public void tryToLaunchMediaApplication_intentNotNull_startActivity() {
        when(mDialogTransitionAnimator.createActivityTransitionController(any(View.class)))
                .thenReturn(mController);
        Intent intent = new Intent(mPackageName);
        doReturn(intent).when(mPackageManager).getLaunchIntentForPackage(mPackageName);
        mMediaSwitchingController.start(mCallback);

        mMediaSwitchingController.tryToLaunchMediaApplication(mDialogLaunchView);

        verify(mStarter).startActivity(any(Intent.class), anyBoolean(), eq(mController));
    }

    @Test
    public void tryToLaunchInAppRoutingIntent_componentNameNotNull_startActivity() {
        when(mDialogTransitionAnimator.createActivityTransitionController(any(View.class)))
                .thenReturn(mController);
        mMediaSwitchingController.start(mCallback);
        when(mLocalMediaManager.getLinkedItemComponentName()).thenReturn(
                new ComponentName(mPackageName, ""));

        mMediaSwitchingController.tryToLaunchInAppRoutingIntent(
                TEST_DEVICE_1_ID, mDialogLaunchView);

        verify(mStarter).startActivity(any(Intent.class), anyBoolean(), eq(mController));
    }

    @Test
    public void onDevicesUpdated_unregistersNearbyDevicesCallback() throws RemoteException {
        mMediaSwitchingController.start(mCb);

        mMediaSwitchingController.onDevicesUpdated(ImmutableList.of());

        verify(mNearbyMediaDevicesManager).unregisterNearbyDevicesCallback(any());
    }

    @Test
    public void onDeviceListUpdate_withNearbyDevices_updatesRangeInformation()
            throws RemoteException {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDevicesUpdated(mNearbyDevices);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        verify(mMediaDevice1).setRangeZone(NearbyDevice.RANGE_FAR);
        verify(mMediaDevice2).setRangeZone(NearbyDevice.RANGE_CLOSE);
    }

    @Test
    public void onDeviceListUpdate_withNearbyDevices_rankByRangeInformation()
            throws RemoteException {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDevicesUpdated(mNearbyDevices);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaDevices.get(0).getId()).isEqualTo(TEST_DEVICE_1_ID);
    }

    @Test
    public void routeProcessSupport_onDeviceListUpdate_preferenceExist_NotUpdatesRangeInformation()
            throws RemoteException {
        when(mLocalMediaManager.isPreferenceRouteListingExist()).thenReturn(true);
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDevicesUpdated(mNearbyDevices);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        verify(mMediaDevice1, never()).setRangeZone(anyInt());
        verify(mMediaDevice2, never()).setRangeZone(anyInt());
    }

    @Test
    @EnableFlags({
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN,
            Flags.FLAG_ALLOW_OUTPUT_SWITCHER_LIST_REARRANGEMENT_WITHIN_TIMEOUT
    })
    public void onDeviceListUpdate_repeatedWithinThresholdPeriod_rearrangesList() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_speakers_and_displays));
        assertThat(items.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice1);
        assertThat(items.get(2).getMediaDevice().get()).isEqualTo(mMediaDevice2);

        mClock.advanceTime(1500); // < 2 seconds.

        // Make the second device suggested
        when(mMediaDevice2.isSuggestedDevice()).thenReturn(true);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        // The list is rearranged - The "Suggested" section added and the order got updated.
        items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_suggested));
        assertThat(items.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice2);
        assertThat(items.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_speakers_and_displays));
        assertThat(items.get(3).getMediaDevice().get()).isEqualTo(mMediaDevice1);
    }

    @Test
    @EnableFlags({
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN,
            Flags.FLAG_ALLOW_OUTPUT_SWITCHER_LIST_REARRANGEMENT_WITHIN_TIMEOUT
    })
    public void onDeviceListUpdate_repeatedAfterThresholdPeriod_appendsItemsToTheList() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_speakers_and_displays));
        assertThat(items.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice1);
        assertThat(items.get(2).getMediaDevice().get()).isEqualTo(mMediaDevice2);

        mClock.advanceTime(2100); // > 2 seconds.

        // Make the second device suggested
        when(mMediaDevice2.isSuggestedDevice()).thenReturn(true);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        // The list remains unchanged.
        items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_speakers_and_displays));
        assertThat(items.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice1);
        assertThat(items.get(2).getMediaDevice().get()).isEqualTo(mMediaDevice2);
    }

    @DisableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void onDeviceListUpdate_verifyDeviceListCallback() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices.containsAll(mMediaDevices)).isTrue();
        assertThat(devices.size()).isEqualTo(mMediaDevices.size());
        // There should be 2 non-MediaDevice items: the "Speakers & Display" title, and the "Connect
        // a device" button.
        assertThat(mMediaSwitchingController.getMediaItemList().size())
                .isEqualTo(mMediaDevices.size() + 2);
        verify(mCb).onDeviceListChanged();
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void onDeviceListUpdate_verifyDeviceListCallback_inputRouting() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices.containsAll(mMediaDevices)).isTrue();
        assertThat(devices.size()).isEqualTo(mMediaDevices.size());
        // When input routing is enabled, there should be 4 non-MediaDevice items: one for
        // the "Output" title, one for the "Speakers & Displays" title, one for the "Connect a
        // device" button, and one for the "Input" title.
        assertThat(mMediaSwitchingController.getMediaItemList().size())
                .isEqualTo(mMediaDevices.size() + 4);
        verify(mCb).onDeviceListChanged();
    }

    @DisableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void advanced_onDeviceListUpdateWithConnectedDeviceRemote_verifyItemSize() {
        when(mMediaDevice1.getFeatures()).thenReturn(
                ImmutableList.of(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK));
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices.containsAll(mMediaDevices)).isTrue();
        assertThat(devices.size()).isEqualTo(mMediaDevices.size());
        // There should be 1 non-MediaDevice item: the "Speakers & Display" title.
        assertThat(mMediaSwitchingController.getMediaItemList().size())
                .isEqualTo(mMediaDevices.size() + 1);
        verify(mCb).onDeviceListChanged();
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void advanced_onDeviceListUpdateWithConnectedDeviceRemote_verifyItemSize_inputRouting() {
        when(mMediaDevice1.getFeatures())
                .thenReturn(ImmutableList.of(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK));
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices.containsAll(mMediaDevices)).isTrue();
        assertThat(devices.size()).isEqualTo(mMediaDevices.size());
        // When input routing is enabled, there should be 3 non-MediaDevice items: one for
        // the "Output" title, one for the "Speakers & Displays" title, and one for the "Input"
        // title.
        assertThat(mMediaSwitchingController.getMediaItemList().size())
                .isEqualTo(mMediaDevices.size() + 3);
        verify(mCb).onDeviceListChanged();
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void onInputDeviceListUpdate_verifyDeviceListCallback() {
        AudioDeviceInfo[] audioDeviceInfos = {};
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
                .thenReturn(audioDeviceInfos);
        mMediaSwitchingController.start(mCb);

        // Output devices have changed.
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        final MediaDevice mediaDevice3 =
                InputMediaDevice.create(
                        mContext,
                        TEST_DEVICE_3_ID,
                        "",
                        AudioDeviceInfo.TYPE_BUILTIN_MIC,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ true,
                        PRODUCT_NAME_BUILTIN_MIC);
        final MediaDevice mediaDevice4 =
                InputMediaDevice.create(
                        mContext,
                        TEST_DEVICE_4_ID,
                        "",
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ false,
                        PRODUCT_NAME_WIRED_HEADSET);
        final List<MediaDevice> inputDevices = new ArrayList<>();
        inputDevices.add(mediaDevice3);
        inputDevices.add(mediaDevice4);

        // Input devices have changed.
        mMediaSwitchingController.mInputDeviceCallback.onInputDeviceListUpdated(inputDevices);

        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices).containsAtLeastElementsIn(mMediaDevices);
        assertThat(devices).hasSize(mMediaDevices.size() + inputDevices.size());
        verify(mCb, atLeastOnce()).onDeviceListChanged();
    }

    @Test
    public void onInputDeviceListUpdate_verifyCurrentInputDevice() {
        mMediaSwitchingController.start(mCb);

        assertThat(mMediaSwitchingController.mCurrentInputDevice).isNull();

        final MediaDevice mediaDevice3 =
                InputMediaDevice.create(
                        mContext,
                        TEST_DEVICE_3_ID,
                        "",
                        AudioDeviceInfo.TYPE_BUILTIN_MIC,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ true,
                        PRODUCT_NAME_BUILTIN_MIC);
        final MediaDevice mediaDevice4 =
                InputMediaDevice.create(
                        mContext,
                        TEST_DEVICE_4_ID,
                        "",
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ false,
                        PRODUCT_NAME_WIRED_HEADSET);
        final List<MediaDevice> inputDevices = new ArrayList<>();
        inputDevices.add(mediaDevice3);
        inputDevices.add(mediaDevice4);

        // Input devices have changed.
        mMediaSwitchingController.mInputDeviceCallback.onInputDeviceListUpdated(inputDevices);

        assertThat(mMediaSwitchingController.mCurrentInputDevice.get()).isEqualTo(mediaDevice3);
    }

    @Test
    public void advanced_categorizeMediaItems_withSuggestedDevice_verifyDeviceListSize() {
        when(mMediaDevice1.isSuggestedDevice()).thenReturn(true);
        when(mMediaDevice2.isSuggestedDevice()).thenReturn(false);

        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = new ArrayList<>();
        int dividerSize = 0;
        for (MediaItem item : mMediaSwitchingController.getMediaItemList()) {
            if (item.getMediaDevice().isPresent()) {
                devices.add(item.getMediaDevice().get());
            }
            if (item.getMediaItemType() == TYPE_GROUP_DIVIDER) {
                dividerSize++;
            }
        }

        assertThat(devices.containsAll(mMediaDevices)).isTrue();
        assertThat(devices.size()).isEqualTo(mMediaDevices.size());
        assertThat(dividerSize).isEqualTo(2);
        verify(mCb).onDeviceListChanged();
    }

    @Test
    public void onDeviceListUpdate_withMutingExpectedDevice_putItOnTop() {
        when(mMediaDevice1.isSuggestedDevice()).thenReturn(false);
        when(mMediaDevice2.isMutingExpectedDevice()).thenReturn(true);

        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices.getFirst().isMutingExpectedDevice()).isTrue();
        assertThat(mMediaSwitchingController.hasMutingExpectedDevice()).isTrue();
    }

    @Test
    public void onDeviceListUpdate_noMutingExpectedDevice_processListNormally() {
        when(mMediaDevice1.isSuggestedDevice()).thenReturn(false);
        when(mMediaDevice2.isMutingExpectedDevice()).thenReturn(false);

        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        final List<MediaDevice> devices = getMediaDevices(
                mMediaSwitchingController.getMediaItemList());

        assertThat(devices.getFirst().isMutingExpectedDevice()).isFalse();
        assertThat(mMediaSwitchingController.hasMutingExpectedDevice()).isFalse();
    }

    @Test
    @EnableFlags({
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN,
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING
    })
    public void onDeviceListUpdate_groupPlaybackAndExpanded_allSelectedDevicesOnTop() {
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.setGroupListCollapsed(false);

        doAnswer(invocation -> {
            LocalMediaManager.DeviceCallback callback = invocation.getArgument(0);
            callback.onDeviceListUpdate(mMediaDevices);
            return null;
        }).when(mLocalMediaManager).registerCallback(any());
        doReturn(true).when(mLocalMediaManager).isMediaSessionAvailableForVolumeControl();

        mMediaSwitchingController.start(mCb);

        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList();

        assertThat(resultList.get(0).getMediaItemType()).isEqualTo(TYPE_GROUP_DIVIDER);
        assertThat(resultList.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_connected_speakers));
        assertThat(resultList.get(0).isExpandableDivider()).isTrue();

        assertThat(resultList.get(1).getMediaItemType()).isEqualTo(TYPE_DEVICE);
        assertThat(resultList.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice1);

        assertThat(resultList.get(2).getMediaItemType()).isEqualTo(TYPE_DEVICE);
        assertThat(resultList.get(2).getMediaDevice().get()).isEqualTo(mMediaDevice2);

        assertThat(resultList.size()).isEqualTo(3);
    }

    @Test
    @EnableFlags({
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN,
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING
    })
    public void onDeviceListUpdate_groupPlaybackAndCollapsed_groupControlAtTheTop() {
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.setGroupListCollapsed(true);

        doAnswer(invocation -> {
            LocalMediaManager.DeviceCallback callback = invocation.getArgument(0);
            callback.onDeviceListUpdate(mMediaDevices);
            return null;
        }).when(mLocalMediaManager).registerCallback(any());
        doReturn(true).when(mLocalMediaManager).isMediaSessionAvailableForVolumeControl();

        mMediaSwitchingController.start(mCb);
        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList();

        assertThat(resultList.get(0).getMediaItemType()).isEqualTo(TYPE_GROUP_DIVIDER);
        assertThat(resultList.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_connected_speakers));
        assertThat(resultList.get(0).isExpandableDivider()).isTrue();

        assertThat(resultList.get(1).getMediaItemType()).isEqualTo(TYPE_DEVICE_GROUP);

        assertThat(resultList.size()).isEqualTo(2);
    }

    @Test
    @EnableFlags({
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN,
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING
    })
    public void onDeviceListUpdate_sessionVolumeUnavailable_noGroupControl() {
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.setGroupListCollapsed(true);

        doAnswer(invocation -> {
            LocalMediaManager.DeviceCallback callback = invocation.getArgument(0);
            callback.onDeviceListUpdate(mMediaDevices);
            return null;
        }).when(mLocalMediaManager).registerCallback(any());
        doReturn(false).when(mLocalMediaManager).isMediaSessionAvailableForVolumeControl();

        mMediaSwitchingController.start(mCb);

        mMediaSwitchingController.setGroupListCollapsed(true);
        mMediaSwitchingController.clearMediaItemList();
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList();

        assertThat(resultList.get(0).getMediaItemType()).isEqualTo(TYPE_DEVICE);
        assertThat(resultList.get(0).getMediaDevice().get()).isEqualTo(mMediaDevice1);

        assertThat(resultList.get(1).getMediaItemType()).isEqualTo(TYPE_DEVICE);
        assertThat(resultList.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice2);

        assertThat(resultList.size()).isEqualTo(2);
    }

    @Test
    @EnableFlags({
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN,
            Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING
    })
    public void onDeviceListUpdate_groupPlaybackCreatedLater_noGroupControl() {
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(false);

        mMediaSwitchingController.setGroupListCollapsed(true);
        doReturn(false).when(mLocalMediaManager).isMediaSessionAvailableForVolumeControl();

        doAnswer(invocation -> {
            LocalMediaManager.DeviceCallback callback = invocation.getArgument(0);
            callback.onDeviceListUpdate(mMediaDevices);
            return null;
        }).when(mLocalMediaManager).registerCallback(any());

        mMediaSwitchingController.start(mCb);

        // Add second selected device after the initial update.
        when(mMediaDevice2.isSelected()).thenReturn(true);
        // Skip 2+ seconds to prevent the list cleanup on refresh.
        mClock.advanceTime(2500);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList();

        assertThat(resultList.get(0).getMediaItemType()).isEqualTo(TYPE_DEVICE);
        assertThat(resultList.get(0).getMediaDevice().get()).isEqualTo(mMediaDevice1);

        assertThat(resultList.get(1).getMediaItemType()).isEqualTo(TYPE_GROUP_DIVIDER);
        assertThat(resultList.get(1).hasTopSeparator()).isTrue();
        assertThat(resultList.get(1).getTitle()).isEqualTo(
                mContext.getString(R.string.media_output_group_title_speakers_and_displays));

        assertThat(resultList.get(2).getMediaItemType()).isEqualTo(TYPE_DEVICE);
        assertThat(resultList.get(2).getMediaDevice().get()).isEqualTo(mMediaDevice2);

        assertThat(resultList.size()).isEqualTo(3);
    }

    @Test
    public void onDeviceListUpdate_isRefreshing_updatesNeedRefreshToTrue() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.mIsRefreshing = true;

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.mNeedRefresh).isTrue();
    }

    @Test
    public void advanced_onDeviceListUpdate_isRefreshing_updatesNeedRefreshToTrue() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.mIsRefreshing = true;

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.mNeedRefresh).isTrue();
    }

    @Test
    public void cancelMuteAwaitConnection_cancelsWithMediaManager() {
        when(mAudioManager.getMutingExpectedDevice()).thenReturn(mock(AudioDeviceAttributes.class));
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.cancelMuteAwaitConnection();

        verify(mAudioManager).cancelMuteAwaitConnection(any());
    }

    @Test
    public void cancelMuteAwaitConnection_audioManagerIsNull_noAction() {
        when(mAudioManager.getMutingExpectedDevice()).thenReturn(null);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.cancelMuteAwaitConnection();

        verify(mAudioManager, never()).cancelMuteAwaitConnection(any());
    }

    @Test
    public void getAppSourceName_packageNameIsNull_returnsNull() {
        MediaSwitchingController testMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        "",
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);
        testMediaSwitchingController.start(mCb);
        reset(mCb);

        testMediaSwitchingController.getAppSourceName();

        assertThat(testMediaSwitchingController.getAppSourceName()).isNull();
    }

    @Test
    public void getNotificationSmallIcon_packageNameIsNull_returnsNull() {
        MediaSwitchingController testMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        "",
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);
        testMediaSwitchingController.start(mCb);
        reset(mCb);

        testMediaSwitchingController.getAppSourceName();

        assertThat(testMediaSwitchingController.getNotificationSmallIcon()).isNull();
    }

    @Test
    public void refreshDataSetIfNeeded_needRefreshIsTrue_setsToFalse() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.mNeedRefresh = true;

        mMediaSwitchingController.refreshDataSetIfNeeded();

        assertThat(mMediaSwitchingController.mNeedRefresh).isFalse();
    }

    @Test
    public void isCurrentConnectedDeviceRemote_containsFeatures_returnsTrue() {
        when(mMediaDevice1.getFeatures()).thenReturn(
                ImmutableList.of(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK));
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);

        assertThat(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).isTrue();
    }

    @Test
    public void addDeviceToPlayMedia_callsLocalMediaManager() {
        MediaSwitchingController testMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        null,
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);

        LocalMediaManager mockLocalMediaManager = mock(LocalMediaManager.class);
        testMediaSwitchingController.mLocalMediaManager = mockLocalMediaManager;

        testMediaSwitchingController.addDeviceToPlayMedia(mMediaDevice2);
        ArgumentCaptor<RoutingChangeInfo> captor = ArgumentCaptor.forClass(RoutingChangeInfo.class);
        verify(mockLocalMediaManager).addDeviceToPlayMedia(eq(mMediaDevice2), captor.capture());
        RoutingChangeInfo capturedInfo = captor.getValue();
        assertThat(capturedInfo.getEntryPoint()).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER);
        assertThat(capturedInfo.isSuggested()).isEqualTo(false);
    }

    @Test
    public void removeDeviceFromPlayMedia_callsLocalMediaManager() {
        MediaSwitchingController testMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        null,
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);

        LocalMediaManager mockLocalMediaManager = mock(LocalMediaManager.class);
        testMediaSwitchingController.mLocalMediaManager = mockLocalMediaManager;

        testMediaSwitchingController.removeDeviceFromPlayMedia(mMediaDevice2);
        ArgumentCaptor<RoutingChangeInfo> argumentCaptor =
                ArgumentCaptor.forClass(RoutingChangeInfo.class);
        verify(mockLocalMediaManager)
                .removeDeviceFromPlayMedia(eq(mMediaDevice2), argumentCaptor.capture());
        RoutingChangeInfo capturedInfo = argumentCaptor.getValue();
        assertThat(capturedInfo.getEntryPoint()).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER);
        assertThat(capturedInfo.isSuggested()).isFalse();
    }

    @Test
    public void adjustSessionVolume_adjustWithoutId_triggersFromLocalMediaManager() {
        int testVolume = 10;
        mMediaSwitchingController.adjustSessionVolume(testVolume);

        verify(mLocalMediaManager).adjustSessionVolume(testVolume);
    }

    @Test
    public void logInteractionAdjustVolume_triggersFromMetricLogger() {
        MediaOutputMetricLogger spyMediaOutputMetricLogger =
                spy(mMediaSwitchingController.mMetricLogger);
        mMediaSwitchingController.mMetricLogger = spyMediaOutputMetricLogger;

        mMediaSwitchingController.logInteractionAdjustVolume(mMediaDevice1);

        verify(spyMediaOutputMetricLogger).logInteractionAdjustVolume(mMediaDevice1);
    }

    @Test
    public void getSessionVolumeMax_triggersFromLocalMediaManager() {
        mMediaSwitchingController.getSessionVolumeMax();

        verify(mLocalMediaManager).getSessionVolumeMax();
    }

    @Test
    public void getSessionVolume_triggersFromLocalMediaManager() {
        mMediaSwitchingController.getSessionVolume();

        verify(mLocalMediaManager).getSessionVolume();
    }

    @Test
    public void getSessionName_triggersFromLocalMediaManager() {
        mMediaSwitchingController.getSessionName();

        verify(mLocalMediaManager).getSessionName();
    }

    @Test
    public void releaseSession_triggersFromLocalMediaManager() {
        mMediaSwitchingController.releaseSession();

        verify(mLocalMediaManager).releaseSession();
    }

    @Test
    public void isAnyDeviceTransferring_noDevicesStateIsConnecting_returnsFalse() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.isAnyDeviceTransferring()).isFalse();
    }

    @Test
    public void isAnyDeviceTransferring_deviceStateIsConnecting_returnsTrue() {
        when(mMediaDevice1.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.isAnyDeviceTransferring()).isTrue();
    }

    @Test
    public void isAnyDeviceTransferring_advancedLayoutSupport() {
        when(mMediaDevice1.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
        mMediaSwitchingController.start(mCb);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.isAnyDeviceTransferring()).isTrue();
    }

    @Test
    public void isPlaying_stateIsNull() {
        when(mSessionMediaController.getPlaybackState()).thenReturn(null);

        assertThat(mMediaSwitchingController.isPlaying()).isFalse();
    }

    @Test
    public void onSelectedDeviceStateChanged_verifyCallback() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice2);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.connectDevice(mMediaDevice1);

        mMediaSwitchingController.onSelectedDeviceStateChanged(
                mMediaDevice1, LocalMediaManager.MediaDeviceState.STATE_CONNECTED);

        verify(mCb).onRouteChanged();
    }

    @Test
    public void onDeviceAttributesChanged_verifyCallback() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        mMediaSwitchingController.onDeviceAttributesChanged();

        verify(mCb).onRouteChanged();
    }

    @Test
    public void onRequestFailed_verifyCallback() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.connectDevice(mMediaDevice2);

        mMediaSwitchingController.onRequestFailed(0 /* reason */);

        verify(mCb, atLeastOnce()).onRouteChanged();
    }

    @Test
    public void getHeaderTitle_withoutMetadata_returnDefaultString() {
        when(mSessionMediaController.getMetadata()).thenReturn(null);

        mMediaSwitchingController.start(mCb);

        assertThat(
                        mMediaSwitchingController
                                .getHeaderTitle()
                                .equals(mContext.getText(R.string.controls_media_title)))
                .isTrue();
    }

    @Test
    public void getHeaderTitle_withMetadata_returnSongName() {
        when(mSessionMediaController.getMetadata()).thenReturn(mMediaMetadata);

        mMediaSwitchingController.start(mCb);

        assertThat(mMediaSwitchingController.getHeaderTitle().equals(TEST_SONG)).isTrue();
    }

    @Test
    public void getHeaderSubTitle_withoutMetadata_returnNull() {
        when(mSessionMediaController.getMetadata()).thenReturn(null);

        mMediaSwitchingController.start(mCb);

        assertThat(mMediaSwitchingController.getHeaderSubTitle()).isNull();
    }

    @Test
    public void getHeaderSubTitle_withMetadata_returnArtistName() {
        when(mSessionMediaController.getMetadata()).thenReturn(mMediaMetadata);

        mMediaSwitchingController.start(mCb);

        assertThat(mMediaSwitchingController.getHeaderSubTitle().equals(TEST_ARTIST)).isTrue();
    }

    @Test
    public void getActiveRemoteMediaDevices() {
        when(mRemoteSessionInfo.getId()).thenReturn(TEST_SESSION_ID);
        when(mRemoteSessionInfo.getName()).thenReturn(TEST_SESSION_NAME);
        when(mRemoteSessionInfo.getVolumeMax()).thenReturn(100);
        when(mRemoteSessionInfo.getVolume()).thenReturn(10);
        when(mRemoteSessionInfo.isSystemSession()).thenReturn(false);
        mRoutingSessionInfos.add(mRemoteSessionInfo);
        when(mLocalMediaManager.getRemoteRoutingSessions()).thenReturn(mRoutingSessionInfos);

        assertThat(mMediaSwitchingController.getActiveRemoteMediaDevices())
                .containsExactly(mRemoteSessionInfo);
    }

    @Test
    public void getNotificationLargeIcon_withoutPackageName_returnsNull() {
        mMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        null,
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);

        assertThat(mMediaSwitchingController.getNotificationIcon()).isNull();
    }

    @Test
    public void getNotificationLargeIcon_withoutLargeIcon_returnsNull() {
        final List<NotificationEntry> entryList = new ArrayList<>();
        final NotificationEntry entry = mock(NotificationEntry.class);
        final StatusBarNotification sbn = mock(StatusBarNotification.class);
        final Notification notification = mock(Notification.class);
        entryList.add(entry);

        when(mNotifCollection.getAllNotifs()).thenReturn(entryList);
        when(entry.getSbn()).thenReturn(sbn);
        when(sbn.getNotification()).thenReturn(notification);
        when(sbn.getPackageName()).thenReturn(mPackageName);
        when(notification.isMediaNotification()).thenReturn(true);
        when(notification.getLargeIcon()).thenReturn(null);

        assertThat(mMediaSwitchingController.getNotificationIcon()).isNull();
    }

    @Test
    public void getNotificationLargeIcon_withPackageNameAndMediaSession_returnsIconCompat() {
        final List<NotificationEntry> entryList = new ArrayList<>();
        final NotificationEntry entry = mock(NotificationEntry.class);
        final StatusBarNotification sbn = mock(StatusBarNotification.class);
        final Notification notification = mock(Notification.class);
        final Icon icon = mock(Icon.class);
        entryList.add(entry);

        when(mNotifCollection.getAllNotifs()).thenReturn(entryList);
        when(entry.getSbn()).thenReturn(sbn);
        when(sbn.getNotification()).thenReturn(notification);
        when(sbn.getPackageName()).thenReturn(mPackageName);
        when(notification.isMediaNotification()).thenReturn(true);
        when(notification.getLargeIcon()).thenReturn(icon);

        assertThat(mMediaSwitchingController.getNotificationIcon()).isInstanceOf(IconCompat.class);
    }

    @Test
    public void getNotificationLargeIcon_withPackageNameAndNoMediaSession_returnsNull() {
        final List<NotificationEntry> entryList = new ArrayList<>();
        final NotificationEntry entry = mock(NotificationEntry.class);
        final StatusBarNotification sbn = mock(StatusBarNotification.class);
        final Notification notification = mock(Notification.class);
        final Icon icon = mock(Icon.class);
        entryList.add(entry);

        when(mNotifCollection.getAllNotifs()).thenReturn(entryList);
        when(entry.getSbn()).thenReturn(sbn);
        when(sbn.getNotification()).thenReturn(notification);
        when(sbn.getPackageName()).thenReturn(mPackageName);
        when(notification.isMediaNotification()).thenReturn(false);
        when(notification.getLargeIcon()).thenReturn(icon);

        assertThat(mMediaSwitchingController.getNotificationIcon()).isNull();
    }

    @Test
    public void getNotificationSmallIcon_withoutSmallIcon_returnsNull() {
        final List<NotificationEntry> entryList = new ArrayList<>();
        final NotificationEntry entry = mock(NotificationEntry.class);
        final StatusBarNotification sbn = mock(StatusBarNotification.class);
        final Notification notification = mock(Notification.class);
        entryList.add(entry);

        when(mNotifCollection.getAllNotifs()).thenReturn(entryList);
        when(entry.getSbn()).thenReturn(sbn);
        when(sbn.getNotification()).thenReturn(notification);
        when(sbn.getPackageName()).thenReturn(mPackageName);
        when(notification.isMediaNotification()).thenReturn(true);
        when(notification.getSmallIcon()).thenReturn(null);

        assertThat(mMediaSwitchingController.getNotificationSmallIcon()).isNull();
    }

    @Test
    public void getNotificationSmallIcon_withPackageNameAndMediaSession_returnsIconCompat() {
        final List<NotificationEntry> entryList = new ArrayList<>();
        final NotificationEntry entry = mock(NotificationEntry.class);
        final StatusBarNotification sbn = mock(StatusBarNotification.class);
        final Notification notification = mock(Notification.class);
        final Icon icon = mock(Icon.class);
        entryList.add(entry);

        when(mNotifCollection.getAllNotifs()).thenReturn(entryList);
        when(entry.getSbn()).thenReturn(sbn);
        when(sbn.getNotification()).thenReturn(notification);
        when(sbn.getPackageName()).thenReturn(mPackageName);
        when(notification.isMediaNotification()).thenReturn(true);
        when(notification.getSmallIcon()).thenReturn(icon);

        assertThat(mMediaSwitchingController.getNotificationSmallIcon())
                .isInstanceOf(IconCompat.class);
    }

    @Test
    public void getDeviceIconCompat_deviceIconIsNotNull_returnsIcon() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice2);
        when(mMediaDevice1.getIcon()).thenReturn(mDrawable);

        assertThat(mMediaSwitchingController.getDeviceIconCompat(mMediaDevice1))
                .isInstanceOf(IconCompat.class);
    }

    @Test
    public void getDeviceIconCompat_deviceIconIsNull_returnsIcon() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice2);
        when(mMediaDevice1.getIcon()).thenReturn(null);

        assertThat(mMediaSwitchingController.getDeviceIconCompat(mMediaDevice1))
                .isInstanceOf(IconCompat.class);
    }

    @Test
    public void isVolumeControlEnabled_isCastWithVolumeFixed_returnsFalse() {
        when(mMediaDevice1.getDeviceType()).thenReturn(
                MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE);

        when(mMediaDevice1.isVolumeFixed()).thenReturn(true);

        assertThat(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).isFalse();
    }

    @Test
    public void isVolumeControlEnabled_isCastWithVolumeNotFixed_returnsTrue() {
        when(mMediaDevice1.getDeviceType()).thenReturn(
                MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE);

        when(mMediaDevice1.isVolumeFixed()).thenReturn(false);

        assertThat(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).isTrue();
    }

    @Test
    public void setTemporaryAllowListExceptionIfNeeded_fromRemoteToBluetooth_addsAllowList() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        when(mMediaDevice1.getDeviceType()).thenReturn(
                MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE);
        when(mMediaDevice1.getFeatures()).thenReturn(
                ImmutableList.of(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK));
        when(mMediaDevice2.getDeviceType()).thenReturn(
                MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE);

        mMediaSwitchingController.setTemporaryAllowListExceptionIfNeeded(mMediaDevice2);

        verify(mPowerExemptionManager).addToTemporaryAllowList(anyString(), anyInt(), anyString(),
                anyLong());
    }

    @Test
    public void setTemporaryAllowListExceptionIfNeeded_packageNameIsNull_NoAction() {
        MediaSwitchingController testMediaSwitchingController =
                new MediaSwitchingController(
                        mSpyContext,
                        null,
                        mSpyContext.getUser(),
                        /* token */ null,
                        mMediaSessionManager,
                        mLocalBluetoothManager,
                        mStarter,
                        mNotifCollection,
                        mDialogTransitionAnimator,
                        mNearbyMediaDevicesManager,
                        mAudioManager,
                        mPowerExemptionManager,
                        mKeyguardManager,
                        mClock,
                        mVolumePanelGlobalStateInteractor,
                        mUserTracker,
                        mJavaAdapter,
                        mAudioSharingRepository);

        testMediaSwitchingController.setTemporaryAllowListExceptionIfNeeded(mMediaDevice2);

        verify(mPowerExemptionManager, never()).addToTemporaryAllowList(anyString(), anyInt(),
                anyString(),
                anyLong());
    }

    @Test
    public void onMetadataChanged_triggersOnMetadataChanged() {
        mMediaSwitchingController.mCallback = this.mCallback;

        mMediaSwitchingController.mCb.onMetadataChanged(mMediaMetadata);

        verify(mMediaSwitchingController.mCallback).onMediaChanged();
    }

    @Test
    public void onPlaybackStateChanged_updateWithNullState_onMediaStoppedOrPaused() {
        when(mPlaybackState.getState()).thenReturn(PlaybackState.STATE_PLAYING);
        mMediaSwitchingController.mCallback = this.mCallback;
        mMediaSwitchingController.start(mCb);

        mMediaSwitchingController.mCb.onPlaybackStateChanged(null);

        verify(mMediaSwitchingController.mCallback).onMediaStoppedOrPaused();
    }

    @Test
    public void launchBluetoothPairing_isKeyguardLocked_dismissDialog() {
        when(mDialogTransitionAnimator.createActivityTransitionController(mDialogLaunchView))
                .thenReturn(mActivityTransitionAnimatorController);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        mMediaSwitchingController.mCallback = this.mCallback;

        mMediaSwitchingController.launchBluetoothPairing(mDialogLaunchView);

        verify(mCallback).dismissDialog();
    }

    @Test
    public void hasGroupPlayback_singleOutputDevice_returnsFalse() {
        when(mMediaDevice1.isSelected()).thenReturn(true);

        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.hasGroupPlayback()).isFalse();
    }

    @Test
    public void hasGroupPlayback_multipleOutputDevices_returnsTrue() {
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);

        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(mMediaSwitchingController.hasGroupPlayback()).isTrue();
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void selectInputDevice() {
        final MediaDevice inputMediaDevice =
                InputMediaDevice.create(
                        mContext,
                        TEST_DEVICE_1_ID,
                        "",
                        AudioDeviceInfo.TYPE_BUILTIN_MIC,
                        MAX_VOLUME,
                        CURRENT_VOLUME,
                        VOLUME_FIXED_TRUE,
                        /* isSelected= */ false,
                        PRODUCT_NAME_BUILTIN_MIC);
        mMediaSwitchingController.connectDevice(inputMediaDevice);

        verify(mLocalMediaManager, after(CALLBACK_WAIT_TIME_MS).never())
                .connectDevice(inputMediaDevice);
        verify(mInputRouteManager, timeout(CALLBACK_WAIT_TIME_MS)).selectDevice(inputMediaDevice);
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void selectOutputDevice() {
        final MediaDevice outputMediaDevice = mock(MediaDevice.class);
        mMediaSwitchingController.connectDevice(outputMediaDevice);

        verify(mInputRouteManager, after(CALLBACK_WAIT_TIME_MS).never())
                .selectDevice(outputMediaDevice);
        ArgumentCaptor<RoutingChangeInfo> captor = ArgumentCaptor.forClass(RoutingChangeInfo.class);
        verify(mLocalMediaManager, timeout(CALLBACK_WAIT_TIME_MS))
                .connectDevice(eq(outputMediaDevice), captor.capture());
        RoutingChangeInfo capturedInfo = captor.getValue();
        assertThat(capturedInfo.getEntryPoint()).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER);
        assertThat(capturedInfo.isSuggested()).isEqualTo(false);
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void selectSuggestedOutputDevice() {
        final MediaDevice outputMediaDevice = mock(MediaDevice.class);
        when(outputMediaDevice.isSuggestedDevice()).thenReturn(true);
        mMediaSwitchingController.connectDevice(outputMediaDevice);
        verify(mInputRouteManager, after(CALLBACK_WAIT_TIME_MS).never())
                .selectDevice(outputMediaDevice);
        ArgumentCaptor<RoutingChangeInfo> captor = ArgumentCaptor.forClass(RoutingChangeInfo.class);
        verify(mLocalMediaManager, timeout(CALLBACK_WAIT_TIME_MS))
                .connectDevice(eq(outputMediaDevice), captor.capture());
        RoutingChangeInfo capturedInfo = captor.getValue();
        assertThat(capturedInfo.getEntryPoint()).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER);
        assertThat(capturedInfo.isSuggested()).isEqualTo(true);
    }

    @Test
    public void connectDeviceButton_remoteDevice_noButton() {
        when(mMediaDevice1.getFeatures()).thenReturn(
                ImmutableList.of(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK));
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        mMediaSwitchingController.start(mCb);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList();

        assertThat(getNumberOfConnectDeviceButtons(resultList)).isEqualTo(0);
    }

    @Test
    public void connectDeviceButton_localDevice_hasButton() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        mMediaSwitchingController.start(mCb);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList();

        assertThat(getNumberOfConnectDeviceButtons(resultList)).isEqualTo(1);
        assertThat(resultList.get(resultList.size() - 1).getMediaItemType()).isEqualTo(
                MediaItem.MediaItemType.TYPE_PAIR_NEW_DEVICE);
    }

    @Test
    public void connectDeviceButton_localDeviceButtonDisabledByParam_noButton() {
        when(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1);
        mMediaSwitchingController.start(mCb);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> resultList = mMediaSwitchingController.getMediaItemList(
                false /* addConnectDeviceButton */);

        assertThat(getNumberOfConnectDeviceButtons(resultList)).isEqualTo(0);
    }

    @Test
    public void connectDeviceButton_presentAtAllTimesForNonGroupOutputs() {
        mMediaSwitchingController.start(mCb);
        reset(mCb);

        // Mock the selected output device.
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(false);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        // Verify that there is initially one "Connect a device" button present.
        assertThat(getNumberOfConnectDeviceButtons(
                mMediaSwitchingController.getMediaItemList())).isEqualTo(1);

        // Change the selected device, and verify that there is still one "Connect a device" button
        // present.
        when(mMediaDevice1.isSelected()).thenReturn(false);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        assertThat(getNumberOfConnectDeviceButtons(
                mMediaSwitchingController.getMediaItemList())).isEqualTo(1);
    }

    @Test
    public void selectedDevicesAddedInSameOrder() {
        when(mLocalMediaManager.isPreferenceRouteListingExist()).thenReturn(true);
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).getMediaDevice().get()).isEqualTo(mMediaDevice1);
        assertThat(items.get(1).getMediaDevice().get()).isEqualTo(mMediaDevice2);
    }

    @Test
    public void selectedDevicesAddedInSameOrderWhenRlpDoesNotExist() {
        setUpSelectedDevicesAndOrdering();

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaDevice> devices =
                mMediaSwitchingController.getMediaItemList().stream()
                        .filter(item -> item.getMediaDevice().isPresent())
                        .map(item -> item.getMediaDevice().orElse(null))
                        .collect(Collectors.toList());
        assertThat(devices)
                .containsExactly(
                        mMediaDevice4,
                        mMediaDevice3,
                        mMediaDevice5,
                        mMediaDevice1,
                        mMediaDevice2)
                .inOrder();
    }

    private void setUpSelectedDevicesAndOrdering() {
        when(mMediaDevice1.getId()).thenReturn(TEST_DEVICE_1_ID);
        when(mMediaDevice2.getId()).thenReturn(TEST_DEVICE_2_ID);
        when(mMediaDevice3.getId()).thenReturn(TEST_DEVICE_3_ID);
        when(mMediaDevice4.getId()).thenReturn(TEST_DEVICE_4_ID);
        when(mMediaDevice5.getId()).thenReturn(TEST_DEVICE_5_ID);
        mMediaDevices.clear();
        Collections.addAll(
                mMediaDevices,
                mMediaDevice2,
                mMediaDevice1,
                mMediaDevice4,
                mMediaDevice3,
                mMediaDevice5);
        when(mMediaDevice3.isSelected()).thenReturn(true);
        when(mMediaDevice4.isSelected()).thenReturn(true);
        when(mMediaDevice5.isSelected()).thenReturn(true);
        // Sort the media devices in the order they appear in the deviceOrder list
        List<MediaDevice> deviceOrder = new ArrayList<>();
        Collections.addAll(
                deviceOrder,
                mMediaDevice1,
                mMediaDevice2,
                mMediaDevice3,
                mMediaDevice4,
                mMediaDevice5);
        for (int i = 0; i < deviceOrder.size(); i++) {
            for (int j = i + 1; j < deviceOrder.size(); j++) {
                when(deviceOrder.get(i).compareTo(deviceOrder.get(j))).thenReturn(-1);
                when(deviceOrder.get(j).compareTo(deviceOrder.get(i))).thenReturn(1);
            }
        }
        when(mLocalMediaManager.isPreferenceRouteListingExist()).thenReturn(false);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();
    }

    @Test
    public void firstSelectedDeviceIsFirstDeviceInGroupIsTrue() {
        when(mLocalMediaManager.isPreferenceRouteListingExist()).thenReturn(true);
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).isFirstDeviceInGroup()).isTrue();
        assertThat(items.get(1).isFirstDeviceInGroup()).isFalse();
    }

    @Test
    public void deviceListUpdateWithDifferentDevices_firstSelectedDeviceIsFirstDeviceInGroup() {
        when(mLocalMediaManager.isPreferenceRouteListingExist()).thenReturn(true);
        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        mMediaSwitchingController.start(mCb);
        reset(mCb);
        mMediaSwitchingController.clearMediaItemList();
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);
        mMediaDevices.clear();
        mMediaDevices.add(mMediaDevice2);
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices);

        List<MediaItem> items = mMediaSwitchingController.getMediaItemList();
        assertThat(items.get(0).isFirstDeviceInGroup()).isTrue();
    }

    @Test
    public void getAudioSharingButtonState_noConnectedBroadcastAssistantDevice_returnsNull() {
        LocalBluetoothProfileManager profileManager = mock(LocalBluetoothProfileManager.class);
        LocalBluetoothLeBroadcastAssistant assistantProfile =
                mock(LocalBluetoothLeBroadcastAssistant.class);
        when(mLocalBluetoothManager.getProfileManager()).thenReturn(profileManager);
        when(profileManager.getLeAudioBroadcastAssistantProfile()).thenReturn(assistantProfile);
        when(assistantProfile.getConnectedDevices()).thenReturn(List.of());
        mMediaSwitchingController.start(mCb);

        AudioSharingButtonState buttonState =
                mMediaSwitchingController.getAudioSharingButtonState();

        assertThat(buttonState).isNull();
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_ENABLE_OUTPUT_SWITCHER_AUDIO_SHARING_BUTTON)
    public void getAudioSharingButtonState_inAudioSharing_returnsVisible() {
        MutableStateFlow<Boolean> inAudioSharingFlow = MutableStateFlow(false);
        when(mAudioSharingRepository.getInAudioSharing()).thenReturn(inAudioSharingFlow);
        mMediaSwitchingController.start(mCb);
        verify(mJavaAdapter)
                .alwaysCollectFlow(same(inAudioSharingFlow), mInAudioSharingCaptor.capture());
        Consumer<Boolean> capturedConsumer = mInAudioSharingCaptor.getValue();
        capturedConsumer.accept(true);

        AudioSharingButtonState buttonState =
                mMediaSwitchingController.getAudioSharingButtonState();

        assertThat(buttonState).isNotNull();
        assertThat(buttonState.isActive()).isTrue();
        assertThat(buttonState.getResId())
                .isEqualTo(R.string.media_output_dialog_button_sharing_audio);
    }

    @Test
    public void getAudioSharingButtonState_hasConnectedBroadcastAssistantDevice_returnsVisible() {
        LocalBluetoothProfileManager profileManager = mock(LocalBluetoothProfileManager.class);
        LocalBluetoothLeBroadcastAssistant assistantProfile =
                mock(LocalBluetoothLeBroadcastAssistant.class);
        BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        MutableStateFlow<Boolean> inAudioSharingFlow = MutableStateFlow(false);
        when(mLocalBluetoothManager.getProfileManager()).thenReturn(profileManager);
        when(profileManager.getLeAudioBroadcastAssistantProfile()).thenReturn(assistantProfile);
        when(assistantProfile.getAllConnectedDevices()).thenReturn(List.of(bluetoothDevice));
        when(mAudioSharingRepository.getInAudioSharing()).thenReturn(inAudioSharingFlow);
        mMediaSwitchingController.start(mCb);

        AudioSharingButtonState buttonState =
                mMediaSwitchingController.getAudioSharingButtonState();

        assertThat(buttonState).isNotNull();
        assertThat(buttonState.isActive()).isFalse();
        assertThat(buttonState.getResId())
                .isEqualTo(R.string.media_output_dialog_button_share_audio);
    }

    private List<MediaDevice> getMediaDevices(List<MediaItem> mediaItemList) {
        return mediaItemList.stream()
                .filter(item -> item.getMediaDevice().isPresent())
                .map(item -> item.getMediaDevice().get())
                .collect(Collectors.toList());
    }

    private int getNumberOfConnectDeviceButtons(List<MediaItem> itemList) {
        int numberOfConnectDeviceButtons = 0;
        for (MediaItem item : itemList) {
            if (item.getMediaItemType() == MediaItem.MediaItemType.TYPE_PAIR_NEW_DEVICE) {
                numberOfConnectDeviceButtons++;
            }
        }
        return numberOfConnectDeviceButtons;
    }
}
