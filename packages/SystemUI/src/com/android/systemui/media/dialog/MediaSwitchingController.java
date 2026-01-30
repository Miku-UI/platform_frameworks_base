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

import static android.media.RouteListingPreference.ACTION_TRANSFER_MEDIA;
import static android.media.RouteListingPreference.EXTRA_ROUTE_ID;
import static android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
import static android.provider.Settings.ACTION_BLUETOOTH_SETTINGS;

import static com.android.media.flags.Flags.allowOutputSwitcherListRearrangementWithinTimeout;
import static com.android.media.flags.Flags.enableOutputSwitcherRedesign;
import static com.android.systemui.Flags.enableOutputSwitcherAudioSharingButton;
import static com.android.systemui.media.dialog.MediaItem.MediaItemType.TYPE_GROUP_DIVIDER;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.WallpaperColors;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.INearbyMediaDevicesUpdateCallback;
import android.media.MediaMetadata;
import android.media.MediaRoute2Info;
import android.media.NearbyDevice;
import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerExemptionManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.drawable.IconCompat;

import com.android.media.flags.Flags;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.Utils;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.InfoMediaManager;
import com.android.settingslib.media.InputMediaDevice;
import com.android.settingslib.media.InputRouteManager;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.volume.data.repository.AudioSharingRepository;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.media.nearby.NearbyMediaDevicesManager;
import com.android.systemui.monet.ColorScheme;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.util.time.SystemClock;
import com.android.systemui.volume.panel.domain.interactor.VolumePanelGlobalStateInteractor;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import kotlinx.coroutines.Job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Controller for a dialog that allows users to switch media output and input devices, control
 * volume, connect to new devices, etc.
 */
public class MediaSwitchingController
        implements LocalMediaManager.DeviceCallback, INearbyMediaDevicesUpdateCallback {

    private static final String TAG = "MediaSwitchingController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String PAGE_CONNECTED_DEVICES_KEY =
            "top_level_connected_devices";
    private static final long ALLOWLIST_DURATION_MS = 20000;
    private static final long LIST_CHANGE_ALLOWED_TIMEOUT_MS = 2000;
    private static final String ALLOWLIST_REASON = "mediaoutput:remote_transfer";
    private static final String ACTION_AUDIO_SHARING =
            "com.android.settings.BLUETOOTH_AUDIO_SHARING_SETTINGS";
    private static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args";

    private final String mPackageName;
    private final UserHandle mUserHandle;
    private final Context mContext;
    private final MediaSessionManager mMediaSessionManager;
    private final LocalBluetoothManager mLocalBluetoothManager;
    private final ActivityStarter mActivityStarter;
    private final DialogTransitionAnimator mDialogTransitionAnimator;
    private final CommonNotifCollection mNotifCollection;
    protected final Object mMediaDevicesLock = new Object();
    protected final Object mInputMediaDevicesLock = new Object();
    @VisibleForTesting
    final List<MediaDevice> mCachedMediaDevices = new CopyOnWriteArrayList<>();
    private final OutputMediaItemListProxy mOutputMediaItemListProxy;
    private final List<MediaItem> mInputMediaItemList = new CopyOnWriteArrayList<>();
    private final AudioManager mAudioManager;
    private final PowerExemptionManager mPowerExemptionManager;
    private final KeyguardManager mKeyGuardManager;
    private final NearbyMediaDevicesManager mNearbyMediaDevicesManager;
    private final Map<String, Integer> mNearbyDeviceInfoMap = new ConcurrentHashMap<>();
    private final MediaSession.Token mToken;
    @Inject @Main Executor mMainExecutor;
    @Inject @Background Executor mBackgroundExecutor;
    @VisibleForTesting
    boolean mIsRefreshing = false;
    @VisibleForTesting
    boolean mNeedRefresh = false;
    private MediaController mMediaController;
    @VisibleForTesting InputRouteManager mInputRouteManager;
    @VisibleForTesting
    Callback mCallback;
    @VisibleForTesting
    LocalMediaManager mLocalMediaManager;
    private final InfoMediaManager mInfoMediaManager;
    @VisibleForTesting
    MediaOutputMetricLogger mMetricLogger;
    private int mCurrentState;
    private final SystemClock mClock;
    private final UserTracker mUserTracker;
    private final VolumePanelGlobalStateInteractor mVolumePanelGlobalStateInteractor;
    @NonNull private MediaOutputColorScheme mMediaOutputColorScheme;
    @NonNull private MediaOutputColorSchemeLegacy mMediaOutputColorSchemeLegacy;
    private boolean mIsGroupListCollapsed = true;
    private boolean mHasAdjustVolumeUserRestriction = false;
    private long mStartTime;
    @Nullable private Boolean mGroupSelectedItems = null; // Unset until the first render.
    private final JavaAdapter mJavaAdapter;
    private final AudioSharingRepository mAudioSharingRepository;
    private boolean mInAudioSharing = false;
    @Nullable private Job mAudioShareJob = null;

    protected Optional<MediaDevice> mCurrentInputDevice;

    @VisibleForTesting
    final InputRouteManager.InputDeviceCallback mInputDeviceCallback =
            new InputRouteManager.InputDeviceCallback() {
                @Override
                public void onInputDeviceListUpdated(@NonNull List<MediaDevice> devices) {
                    synchronized (mInputMediaDevicesLock) {
                        buildInputMediaItems(devices);
                        mCurrentInputDevice =
                                devices.stream().filter(MediaDevice::isSelected).findFirst();
                        mCallback.onDeviceListChanged();
                    }
                }
            };

    @AssistedInject
    public MediaSwitchingController(
            Context context,
            @Assisted String packageName,
            @Assisted @Nullable UserHandle userHandle,
            @Assisted @Nullable MediaSession.Token token,
            MediaSessionManager mediaSessionManager,
            @Nullable LocalBluetoothManager lbm,
            ActivityStarter starter,
            CommonNotifCollection notifCollection,
            DialogTransitionAnimator dialogTransitionAnimator,
            NearbyMediaDevicesManager nearbyMediaDevicesManager,
            AudioManager audioManager,
            PowerExemptionManager powerExemptionManager,
            KeyguardManager keyGuardManager,
            SystemClock clock,
            VolumePanelGlobalStateInteractor volumePanelGlobalStateInteractor,
            UserTracker userTracker,
            JavaAdapter javaAdapter,
            AudioSharingRepository audioSharingRepository) {
        mContext = context;
        mPackageName = packageName;
        mUserHandle = userHandle;
        mMediaSessionManager = mediaSessionManager;
        mLocalBluetoothManager = lbm;
        mActivityStarter = starter;
        mNotifCollection = notifCollection;
        mAudioManager = audioManager;
        mPowerExemptionManager = powerExemptionManager;
        mKeyGuardManager = keyGuardManager;
        mClock = clock;
        mUserTracker = userTracker;
        mToken = token;
        mVolumePanelGlobalStateInteractor = volumePanelGlobalStateInteractor;
        mInfoMediaManager =
                InfoMediaManager.createInstance(mContext, packageName, userHandle, lbm, token);
        mLocalMediaManager = new LocalMediaManager(mContext, lbm, mInfoMediaManager, packageName);
        mMetricLogger = new MediaOutputMetricLogger(mContext, mPackageName);
        mOutputMediaItemListProxy = new OutputMediaItemListProxy(context);
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mNearbyMediaDevicesManager = nearbyMediaDevicesManager;
        mMediaOutputColorScheme = MediaOutputColorScheme.fromSystemColors(mContext);
        mMediaOutputColorSchemeLegacy = MediaOutputColorSchemeLegacy.fromSystemColors(mContext);

        if (enableInputRouting()) {
            mInputRouteManager = new InputRouteManager(mContext, audioManager, mInfoMediaManager);
        }

        mJavaAdapter = javaAdapter;
        mAudioSharingRepository = audioSharingRepository;
    }

    @AssistedFactory
    public interface Factory {
        /** Construct a MediaSwitchingController */
        MediaSwitchingController create(
                String packageName, UserHandle userHandle, MediaSession.Token token);
    }

    protected void start(@NonNull Callback cb) {
        mStartTime = mClock.elapsedRealtime();
        synchronized (mMediaDevicesLock) {
            mCachedMediaDevices.clear();
            mOutputMediaItemListProxy.clear();
        }
        mNearbyDeviceInfoMap.clear();
        if (mNearbyMediaDevicesManager != null) {
            mNearbyMediaDevicesManager.registerNearbyDevicesCallback(this);
        }
        if (!TextUtils.isEmpty(mPackageName)) {
            mMediaController = getMediaController();
            if (mMediaController != null) {
                mMediaController.unregisterCallback(mCb);
                if (mMediaController.getPlaybackState() != null) {
                    mCurrentState = mMediaController.getPlaybackState().getState();
                }
                mMediaController.registerCallback(mCb);
            }
        }
        if (mMediaController == null) {
            if (DEBUG) {
                Log.d(TAG, "No media controller for " + mPackageName);
            }
        }
        mCallback = cb;
        mLocalMediaManager.registerCallback(this);
        mLocalMediaManager.startScan();

        if (enableInputRouting()) {
            mInputRouteManager.registerCallback(mInputDeviceCallback);
        }
        mHasAdjustVolumeUserRestriction = checkIfAdjustVolumeRestrictionEnforced();

        if (enableOutputSwitcherAudioSharingButton()) {
            mAudioShareJob =
                    mJavaAdapter.alwaysCollectFlow(
                            mAudioSharingRepository.getInAudioSharing(),
                            inAudioSharing -> {
                                mInAudioSharing = inAudioSharing;
                                mCallback.onQuickAccessButtonsChanged();
                            });
        }
    }

    public boolean isRefreshing() {
        return mIsRefreshing;
    }

    public void setRefreshing(boolean refreshing) {
        mIsRefreshing = refreshing;
    }

    protected void stop() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mCb);
        }
        mLocalMediaManager.unregisterCallback(this);
        mLocalMediaManager.stopScan();
        synchronized (mMediaDevicesLock) {
            mCachedMediaDevices.clear();
            mOutputMediaItemListProxy.clear();
        }
        if (mNearbyMediaDevicesManager != null) {
            mNearbyMediaDevicesManager.unregisterNearbyDevicesCallback(this);
        }
        mNearbyDeviceInfoMap.clear();

        if (enableInputRouting()) {
            mInputRouteManager.unregisterCallback(mInputDeviceCallback);
            synchronized (mInputMediaDevicesLock) {
                mInputMediaItemList.clear();
            }
        }

        if (mAudioShareJob != null) {
            mAudioShareJob.cancel(new CancellationException("MediaSwitchingController stopped"));
        }
    }

    private MediaController getMediaController() {
        if (mToken != null) {
            return new MediaController(mContext, mToken);
        } else {
            for (NotificationEntry entry : mNotifCollection.getAllNotifs()) {
                final Notification notification = entry.getSbn().getNotification();
                if (notification.isMediaNotification()
                        && TextUtils.equals(entry.getSbn().getPackageName(), mPackageName)) {
                    MediaSession.Token token =
                            notification.extras.getParcelable(
                                    Notification.EXTRA_MEDIA_SESSION, MediaSession.Token.class);
                    return new MediaController(mContext, token);
                }
            }
            for (MediaController controller :
                    mMediaSessionManager.getActiveSessionsForUser(
                            null, mUserTracker.getUserHandle())) {
                if (TextUtils.equals(controller.getPackageName(), mPackageName)) {
                    return controller;
                }
            }
            return null;
        }
    }

    @Override
    public void onDeviceListUpdate(List<MediaDevice> devices) {
        boolean isListEmpty = mOutputMediaItemListProxy.isEmpty();
        if (isListEmpty || !mIsRefreshing) {
            buildMediaItems(devices);
            if (mGroupSelectedItems == null) {
                // Decide whether to group devices only during the initial render.
                // Avoid grouping broadcast devices because grouped volume control is not
                // available for broadcast session.
                mGroupSelectedItems =
                        hasGroupPlayback() && (!Flags.enableOutputSwitcherPersonalAudioSharing()
                                || isVolumeControlEnabledForSession());
            }
            mCallback.onDeviceListChanged();
        } else {
            synchronized (mMediaDevicesLock) {
                mNeedRefresh = true;
                mCachedMediaDevices.clear();
                mCachedMediaDevices.addAll(devices);
            }
        }
    }

    @Override
    public void onSelectedDeviceStateChanged(
            MediaDevice device, @LocalMediaManager.MediaDeviceState int state) {
        mCallback.onRouteChanged();
        mMetricLogger.logOutputItemSuccess(
                device.toString(),
                new ArrayList<>(mOutputMediaItemListProxy.getOutputMediaItemList()));
    }

    @Override
    public void onDeviceAttributesChanged() {
        mCallback.onRouteChanged();
    }

    @Override
    public void onRequestFailed(int reason) {
        mCallback.onRouteChanged();
        mMetricLogger.logOutputItemFailure(
                new ArrayList<>(mOutputMediaItemListProxy.getOutputMediaItemList()), reason);
    }

    /**
     * Checks if there's any muting expected devices in the current MediaItem list.
     */
    public boolean hasMutingExpectedDevice() {
        return mOutputMediaItemListProxy.getOutputMediaItemList().stream().anyMatch(
                MediaItem::isMutingExpectedDevice);
    }

    /**
     * Checks if there's any muting expected device in the provided device list.
     */
    private boolean containsMutingExpectedDevice(List<MediaDevice> devices) {
        return devices.stream().anyMatch(MediaDevice::isMutingExpectedDevice);
    }

    /**
     * Cancels mute await connection action in follow up request
     */
    public void cancelMuteAwaitConnection() {
        if (mAudioManager.getMutingExpectedDevice() == null) {
            return;
        }
        try {
            synchronized (mMediaDevicesLock) {
                mOutputMediaItemListProxy.removeMutingExpectedDevices();
            }
            mAudioManager.cancelMuteAwaitConnection(mAudioManager.getMutingExpectedDevice());
        } catch (Exception e) {
            Log.d(TAG, "Unable to cancel mute await connection");
        }
    }

    Drawable getAppSourceIconFromPackage() {
        if (TextUtils.isEmpty(mPackageName)) {
            return null;
        }
        try {
            Log.d(TAG, "try to get app icon");
            return mContext.getPackageManager()
                    .getApplicationIcon(mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "icon not found");
            return null;
        }
    }

    String getAppSourceName() {
        if (TextUtils.isEmpty(mPackageName)) {
            return null;
        }
        final PackageManager packageManager = mContext.getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(mPackageName,
                    PackageManager.ApplicationInfoFlags.of(0));
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        final String applicationName =
                (String) (applicationInfo != null ? packageManager.getApplicationLabel(
                        applicationInfo)
                        : mContext.getString(R.string.media_output_dialog_unknown_launch_app_name));
        return applicationName;
    }

    Intent getAppLaunchIntent() {
        if (TextUtils.isEmpty(mPackageName)) {
            return null;
        }
        return mContext.getPackageManager().getLaunchIntentForPackage(mPackageName);
    }

    void tryToLaunchInAppRoutingIntent(String routeId, View view) {
        ComponentName componentName = mLocalMediaManager.getLinkedItemComponentName();
        if (componentName != null) {
            ActivityTransitionAnimator.Controller controller =
                    mDialogTransitionAnimator.createActivityTransitionController(view);
            Intent launchIntent = new Intent(ACTION_TRANSFER_MEDIA);
            launchIntent.setComponent(componentName);
            launchIntent.putExtra(EXTRA_ROUTE_ID, routeId);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mCallback.dismissDialog();
            startActivity(launchIntent, controller);
        }
    }

    void tryToLaunchMediaApplication(View view) {
        ActivityTransitionAnimator.Controller controller =
                mDialogTransitionAnimator.createActivityTransitionController(view);
        Intent launchIntent = getAppLaunchIntent();
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mCallback.dismissDialog();
            startActivity(launchIntent, controller);
        }
    }

    CharSequence getHeaderTitle() {
        if (mMediaController != null) {
            final MediaMetadata metadata = mMediaController.getMetadata();
            if (metadata != null) {
                return metadata.getDescription().getTitle();
            }
        }
        return mContext.getText(R.string.controls_media_title);
    }

    CharSequence getHeaderSubTitle() {
        if (mMediaController == null) {
            return null;
        }
        final MediaMetadata metadata = mMediaController.getMetadata();
        if (metadata == null) {
            return null;
        }
        return metadata.getDescription().getSubtitle();
    }

    IconCompat getHeaderIcon() {
        if (mMediaController == null) {
            return null;
        }
        final MediaMetadata metadata = mMediaController.getMetadata();
        if (metadata != null) {
            final Bitmap bitmap = metadata.getDescription().getIconBitmap();
            if (bitmap != null) {
                final Bitmap roundBitmap = Utils.convertCornerRadiusBitmap(mContext, bitmap,
                        (float) mContext.getResources().getDimensionPixelSize(
                                R.dimen.media_output_dialog_icon_corner_radius));
                return IconCompat.createWithBitmap(roundBitmap);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Media meta data does not contain icon information");
        }
        return getNotificationIcon();
    }

    Drawable getDeviceIconDrawable(MediaDevice device) {
        Drawable drawable = device.getIcon();
        if (drawable == null) {
            if (DEBUG) {
                Log.d(TAG, "getDeviceIconCompat() device : " + device.getName()
                        + ", drawable is null");
            }
            // Use default Bluetooth device icon to handle getIcon() is null case.
            drawable = mContext.getDrawable(com.android.internal.R.drawable.ic_bt_headphones_a2dp);
        }
        return drawable;
    }

    IconCompat getDeviceIconCompat(MediaDevice device) {
        return BluetoothUtils.createIconWithDrawable(getDeviceIconDrawable(device));
    }

    public void setGroupListCollapsed(boolean isCollapsed) {
        mIsGroupListCollapsed = isCollapsed;
    }

    public boolean isGroupListCollapsed() {
        return mIsGroupListCollapsed;
    }

    IconCompat getNotificationSmallIcon() {
        if (TextUtils.isEmpty(mPackageName)) {
            return null;
        }
        for (NotificationEntry entry : mNotifCollection.getAllNotifs()) {
            final Notification notification = entry.getSbn().getNotification();
            if (notification.isMediaNotification()
                    && TextUtils.equals(entry.getSbn().getPackageName(), mPackageName)) {
                final Icon icon = notification.getSmallIcon();
                if (icon == null) {
                    break;
                }
                return IconCompat.createFromIcon(icon);
            }
        }
        return null;
    }

    IconCompat getNotificationIcon() {
        if (TextUtils.isEmpty(mPackageName)) {
            return null;
        }
        for (NotificationEntry entry : mNotifCollection.getAllNotifs()) {
            final Notification notification = entry.getSbn().getNotification();
            if (notification.isMediaNotification()
                    && TextUtils.equals(entry.getSbn().getPackageName(), mPackageName)) {
                final Icon icon = notification.getLargeIcon();
                if (icon == null) {
                    break;
                }
                return IconCompat.createFromIcon(icon);
            }
        }
        return null;
    }

    void updateCurrentColorScheme(WallpaperColors wallpaperColors, boolean isDarkTheme) {
        ColorScheme currentColorScheme = new ColorScheme(wallpaperColors,
                isDarkTheme);
        mMediaOutputColorScheme = MediaOutputColorScheme.fromDynamicColors(
                currentColorScheme);
        mMediaOutputColorSchemeLegacy = MediaOutputColorSchemeLegacy.fromDynamicColors(
                currentColorScheme, isDarkTheme);
    }

    MediaOutputColorScheme getColorScheme() {
        return mMediaOutputColorScheme;
    }

    MediaOutputColorSchemeLegacy getColorSchemeLegacy() {
        return mMediaOutputColorSchemeLegacy;
    }

    public void refreshDataSetIfNeeded() {
        if (mNeedRefresh) {
            buildMediaItems(mCachedMediaDevices);
            mCallback.onDeviceListChanged();
            mNeedRefresh = false;
        }
    }

    private void buildMediaItems(List<MediaDevice> devices) {
        synchronized (mMediaDevicesLock) {
            if (!mLocalMediaManager.isPreferenceRouteListingExist()) {
                attachRangeInfo(devices);
                List<MediaDevice> selectedDevices =
                        devices.stream().filter(MediaDevice::isSelected).toList();
                devices.removeAll(selectedDevices);
                devices.sort(Comparator.naturalOrder());
                devices.addAll(0, selectedDevices);
            }

            // For the first time building list, to make sure the top device is the connected
            // device.
            boolean needToHandleMutingExpectedDevice =
                    containsMutingExpectedDevice(devices) && !isCurrentConnectedDeviceRemote();
            final MediaDevice connectedMediaDevice =
                    needToHandleMutingExpectedDevice ? null : getCurrentConnectedMediaDevice();
            if (isDeviceListRearrangementAllowed()) {
                // We erase all the items from the previous render so that the sorting and
                // categorization are run from a clean slate.
                mOutputMediaItemListProxy.clear();
            }
            mOutputMediaItemListProxy.updateMediaDevices(
                    devices,
                    connectedMediaDevice,
                    needToHandleMutingExpectedDevice);
        }
    }

    /**  Whether it's allowed to change device list order and categories. */
    private boolean isDeviceListRearrangementAllowed() {
        return allowOutputSwitcherListRearrangementWithinTimeout()
                && mClock.elapsedRealtime() - mStartTime <= LIST_CHANGE_ALLOWED_TIMEOUT_MS;
    }

    private boolean enableInputRouting() {
        return Flags.enableAudioInputDeviceRoutingAndVolumeControl()
                && mContext.getResources().getBoolean(R.bool.config_enableInputRouting);
    }

    private void buildInputMediaItems(List<MediaDevice> devices) {
        synchronized (mInputMediaDevicesLock) {
            List<MediaItem> updatedInputMediaItems =
                    devices.stream().map(MediaItem::createDeviceMediaItem).toList();
            mInputMediaItemList.clear();
            mInputMediaItemList.addAll(updatedInputMediaItems);
        }
    }

    private void attachConnectNewDeviceItemIfNeeded(List<MediaItem> mediaItems) {
        MediaItem connectNewDeviceItem = getConnectNewDeviceItem();
        if (connectNewDeviceItem != null) {
            mediaItems.add(connectNewDeviceItem);
        }
    }

    @NonNull
    MediaItem getConnectedSpeakersExpandableGroupDivider() {
        return MediaItem.createExpandableGroupDividerMediaItem(
                mContext.getString(R.string.media_output_group_title_connected_speakers));
    }

    boolean hasGroupPlayback() {
        return getSelectedDeviceItems().size() > 1;
    }

    List<MediaItem> getSelectedDeviceItems() {
        return mOutputMediaItemListProxy.getOutputMediaItemList().stream()
                .filter(item -> item.getMediaDevice().map(MediaDevice::isSelected).orElse(
                        false)).toList();
    }

    @Nullable
    MediaItem getConnectNewDeviceItem() {
        // Attach "Connect a device" item only when current output is not remote and not a group
        return (!isCurrentConnectedDeviceRemote() && !hasGroupPlayback())
                ? MediaItem.createPairNewDeviceMediaItem()
                : null;
    }

    private void attachRangeInfo(List<MediaDevice> devices) {
        for (MediaDevice mediaDevice : devices) {
            if (mNearbyDeviceInfoMap.containsKey(mediaDevice.getId())) {
                mediaDevice.setRangeZone(mNearbyDeviceInfoMap.get(mediaDevice.getId()));
            }
        }
    }

    boolean isCurrentConnectedDeviceRemote() {
        MediaDevice currentConnectedMediaDevice = getCurrentConnectedMediaDevice();
        return currentConnectedMediaDevice != null && isActiveRemoteDevice(
                currentConnectedMediaDevice);
    }

    boolean isCurrentOutputDeviceHasSessionOngoing() {
        MediaDevice currentConnectedMediaDevice = getCurrentConnectedMediaDevice();
        return currentConnectedMediaDevice != null
                && (currentConnectedMediaDevice.isHostForOngoingSession());
    }

    protected void connectDevice(MediaDevice device) {
        mInfoMediaManager.setDeviceState(
                device, LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
        // If input routing is supported and the device is an input device, call mInputRouteManager
        // to handle routing.
        if (enableInputRouting() && device instanceof InputMediaDevice) {
            var unused =
                    ThreadUtils.postOnBackgroundThread(
                            () -> {
                                mInputRouteManager.selectDevice(device);
                            });
            return;
        }

        mMetricLogger.updateOutputEndPoints(getCurrentConnectedMediaDevice(), device);

        ThreadUtils.postOnBackgroundThread(
                () -> {
                    mLocalMediaManager.connectDevice(
                            device,
                            new RoutingChangeInfo(
                                    ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                                    device.isSuggestedDevice()));
                });
    }

    private List<MediaItem> getOutputDeviceList(boolean addConnectDeviceButton) {
        List<MediaItem> mediaItems = new ArrayList<>(
                mOutputMediaItemListProxy.getOutputMediaItemList());
        if (enableOutputSwitcherRedesign()) {
            addSeparatorForTheFirstGroupDivider(mediaItems);
            coalesceSelectedDevices(mediaItems);
        }
        if (addConnectDeviceButton) {
            attachConnectNewDeviceItemIfNeeded(mediaItems);
        }
        return mediaItems;
    }


    private void addSeparatorForTheFirstGroupDivider(List<MediaItem> outputList) {
        for (int i = 0; i < outputList.size(); i++) {
            MediaItem item = outputList.get(i);
            if (item.getMediaItemType() == TYPE_GROUP_DIVIDER) {
                outputList.set(i,
                        MediaItem.createGroupDividerWithSeparatorMediaItem(item.getTitle()));
                break;
            }
        }
    }

    /**
     * If there are 2+ selected devices, adds an "Connected speakers" expandable group divider and
     * displays a single session control instead of individual device controls.
     */
    private void coalesceSelectedDevices(List<MediaItem> outputList) {
        List<MediaItem> selectedDevices = getSelectedDeviceItems();

        if (Boolean.TRUE.equals(mGroupSelectedItems) && hasGroupPlayback()) {
            outputList.removeAll(selectedDevices);
            if (isGroupListCollapsed()) {
                outputList.addFirst(MediaItem.createDeviceGroupMediaItem());
            } else {
                outputList.addAll(0, selectedDevices);
            }
            outputList.addFirst(getConnectedSpeakersExpandableGroupDivider());
        }
    }

    private void addInputDevices(List<MediaItem> mediaItems) {
        mediaItems.add(
                MediaItem.createGroupDividerMediaItem(
                        mContext.getString(R.string.media_input_group_title)));
        mediaItems.addAll(mInputMediaItemList);
    }

    private void addOutputDevices(List<MediaItem> mediaItems, boolean addConnectDeviceButton) {
        mediaItems.add(
                MediaItem.createGroupDividerMediaItem(
                        mContext.getString(R.string.media_output_group_title)));
        mediaItems.addAll(getOutputDeviceList(addConnectDeviceButton));
    }

    /**
     * Returns a list of media items to be rendered in the device list. For backward compatibility
     * reasons, adds a "Connect a device" button by default.
     */
    public List<MediaItem> getMediaItemList() {
        return getMediaItemList(true /* addConnectDeviceButton */);
    }

    /**
     * Returns a list of media items to be rendered in the device list.
     * @param addConnectDeviceButton Whether to add a "Connect a device" button to the list.
     */
    public List<MediaItem> getMediaItemList(boolean addConnectDeviceButton) {
        // If input routing is not enabled, only return output media items.
        if (!enableInputRouting()) {
            return getOutputDeviceList(addConnectDeviceButton);
        }

        // If input routing is enabled, return both output and input media items.
        List<MediaItem> mediaItems = new ArrayList<>();
        addOutputDevices(mediaItems, addConnectDeviceButton);
        addInputDevices(mediaItems);
        return mediaItems;
    }

    public MediaDevice getCurrentConnectedMediaDevice() {
        return mLocalMediaManager.getCurrentConnectedDevice();
    }

    @VisibleForTesting
    void clearMediaItemList() {
        mOutputMediaItemListProxy.clear();
    }

    boolean addDeviceToPlayMedia(MediaDevice device) {
        mMetricLogger.logInteractionExpansion(device);
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER, device.isSuggestedDevice());
        return mLocalMediaManager.addDeviceToPlayMedia(device, routingChangeInfo);
    }

    boolean removeDeviceFromPlayMedia(MediaDevice device) {
        mMetricLogger.logInteractionContraction(device);
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER, device.isSuggestedDevice());
        return mLocalMediaManager.removeDeviceFromPlayMedia(device, routingChangeInfo);
    }

    void adjustSessionVolume(int volume) {
        mLocalMediaManager.adjustSessionVolume(volume);
    }

    int getSessionVolumeMax() {
        return mLocalMediaManager.getSessionVolumeMax();
    }

    int getSessionVolume() {
        return mLocalMediaManager.getSessionVolume();
    }

    @Nullable
    CharSequence getSessionName() {
        return mLocalMediaManager.getSessionName();
    }

    @RoutingSessionInfo.ReleaseType
    int getSessionReleaseType() {
        return mLocalMediaManager.getSessionReleaseType();
    }

    void releaseSession() {
        if (Flags.enableOutputSwitcherPersonalAudioSharing()
                && getSessionReleaseType() == RoutingSessionInfo.RELEASE_TYPE_SHARING) {
            mMetricLogger.logInteractionStopSharing();
        } else {
            mMetricLogger.logInteractionStopCasting();
        }
        mLocalMediaManager.releaseSession();
    }

    List<RoutingSessionInfo> getActiveRemoteMediaDevices() {
        return new ArrayList<>(mLocalMediaManager.getRemoteRoutingSessions());
    }

    void adjustVolume(MediaDevice device, int volume) {
        ThreadUtils.postOnBackgroundThread(() -> {
            mLocalMediaManager.adjustDeviceVolume(device, volume);
        });
    }

    void logInteractionAdjustVolume(MediaDevice device) {
        mMetricLogger.logInteractionAdjustVolume(device);
    }

    void logInteractionMuteDevice(MediaDevice device) {
        mMetricLogger.logInteractionMute(device);
    }

    void logInteractionUnmuteDevice(MediaDevice device) {
        mMetricLogger.logInteractionUnmute(device);
    }

    boolean hasAdjustVolumeUserRestriction() {
        return mHasAdjustVolumeUserRestriction;
    }

    private boolean checkIfAdjustVolumeRestrictionEnforced() {
        if (RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                mContext, UserManager.DISALLOW_ADJUST_VOLUME, UserHandle.myUserId()) != null) {
            return true;
        }
        final UserManager um = mContext.getSystemService(UserManager.class);
        return um.hasBaseUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME,
                UserHandle.of(UserHandle.myUserId()));
    }

    public boolean isAnyDeviceTransferring() {
        synchronized (mMediaDevicesLock) {
            for (MediaItem mediaItem : mOutputMediaItemListProxy.getOutputMediaItemList()) {
                if (mediaItem.getMediaDevice().isPresent()
                        && mediaItem.getMediaDevice().get().getState()
                        == LocalMediaManager.MediaDeviceState.STATE_CONNECTING) {
                    return true;
                }
            }
        }
        return false;
    }

    void launchBluetoothPairing(View view) {
        ActivityTransitionAnimator.Controller controller =
                mDialogTransitionAnimator.createActivityTransitionController(view);

        if (controller == null || (mKeyGuardManager != null
                && mKeyGuardManager.isKeyguardLocked())) {
            mCallback.dismissDialog();
        }

        Intent launchIntent =
                new Intent(ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final Intent deepLinkIntent =
                new Intent(Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY);
        if (deepLinkIntent.resolveActivity(mContext.getPackageManager()) != null) {
            Log.d(TAG, "Device support split mode, launch page with deep link");
            deepLinkIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            deepLinkIntent.putExtra(
                    Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                    launchIntent.toUri(Intent.URI_INTENT_SCHEME));
            deepLinkIntent.putExtra(
                    Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY,
                    PAGE_CONNECTED_DEVICES_KEY);
            startActivity(deepLinkIntent, controller);
            return;
        }
        startActivity(launchIntent, controller);
    }

    void launchAudioSharing(View view) {
        ActivityTransitionAnimator.Controller controller =
                mDialogTransitionAnimator.createActivityTransitionController(view);

        if (controller == null
                || (mKeyGuardManager != null && mKeyGuardManager.isKeyguardLocked())) {
            mCallback.dismissDialog();
        }

        Intent launchIntent = new Intent(ACTION_AUDIO_SHARING);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Bundle bundle = new Bundle();
        bundle.putBoolean(LocalBluetoothLeBroadcast.EXTRA_START_LE_AUDIO_SHARING, true);
        launchIntent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle);
        startActivity(launchIntent, controller);
    }

    protected void setTemporaryAllowListExceptionIfNeeded(MediaDevice targetDevice) {
        if (mPowerExemptionManager == null || mPackageName == null) {
            Log.w(TAG, "powerExemptionManager or package name is null");
            return;
        }
        mPowerExemptionManager.addToTemporaryAllowList(mPackageName,
                PowerExemptionManager.REASON_MEDIA_NOTIFICATION_TRANSFER,
                ALLOWLIST_REASON,
                ALLOWLIST_DURATION_MS);
    }

    boolean isActiveRemoteDevice(@NonNull MediaDevice device) {
        final List<String> features = device.getFeatures();
        return (features.contains(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
                || features.contains(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK)
                || features.contains(MediaRoute2Info.FEATURE_REMOTE_VIDEO_PLAYBACK)
                || features.contains(MediaRoute2Info.FEATURE_REMOTE_GROUP_PLAYBACK));
    }

    boolean isPlaying() {
        if (mMediaController == null) {
            return false;
        }

        PlaybackState state = mMediaController.getPlaybackState();
        if (state == null) {
            return false;
        }

        return (state.getState() == PlaybackState.STATE_PLAYING);
    }

    boolean isVolumeControlEnabled(@NonNull MediaDevice device) {
        return !device.isVolumeFixed();
    }

    boolean isVolumeControlEnabledForSession() {
        return mLocalMediaManager.isMediaSessionAvailableForVolumeControl();
    }

    /**
     * Determines and gets the audio sharing button state.
     *
     * <p>This function indicates visible status only when the device is audio sharing
     * (broadcasting) or has a remote Bluetooth device connected on Bluetooth LE Audio Assistant
     * profile.
     *
     * @return non-null {@link AudioSharingButtonState} if the device is in audio sharing or ready
     *     for audio sharing, else null.
     */
    @Nullable
    protected AudioSharingButtonState getAudioSharingButtonState() {
        if (mInAudioSharing) {
            return new AudioSharingButtonState(
                    /* resId= */ R.string.media_output_dialog_button_sharing_audio,
                    /* isActive= */ true);
        } else if (BluetoothUtils.hasConnectedBroadcastAssistantDevice(mLocalBluetoothManager)) {
            return new AudioSharingButtonState(
                    /* resId= */ R.string.media_output_dialog_button_share_audio,
                    /* isActive= */ false);
        }

        return null;
    }

    private void startActivity(Intent intent, ActivityTransitionAnimator.Controller controller) {
        // Media Output dialog can be shown from the volume panel. This makes sure the panel is
        // closed when navigating to another activity, so it doesn't stays on top of it
        mVolumePanelGlobalStateInteractor.setVisible(false);
        mActivityStarter.startActivity(intent, true, controller);
    }

    @Override
    public void onDevicesUpdated(List<NearbyDevice> nearbyDevices) throws RemoteException {
        mNearbyDeviceInfoMap.clear();
        for (NearbyDevice nearbyDevice : nearbyDevices) {
            mNearbyDeviceInfoMap.put(nearbyDevice.getMediaRoute2Id(), nearbyDevice.getRangeZone());
        }
        mNearbyMediaDevicesManager.unregisterNearbyDevicesCallback(this);
    }

    @Override
    public IBinder asBinder() {
        return null;
    }

    @VisibleForTesting
    final MediaController.Callback mCb = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            mCallback.onMediaChanged();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState playbackState) {
            final int newState =
                    playbackState == null ? PlaybackState.STATE_STOPPED : playbackState.getState();
            if (mCurrentState == newState) {
                return;
            }

            if (newState == PlaybackState.STATE_STOPPED) {
                mCallback.onMediaStoppedOrPaused();
            }
            mCurrentState = newState;
        }
    };

    public interface Callback {
        /**
         * Override to handle the media content updating.
         */
        void onMediaChanged();

        /**
         * Override to handle the media state updating.
         */
        void onMediaStoppedOrPaused();

        /**
         * Override to handle the device status or attributes updating.
         */
        void onRouteChanged();

        /**
         * Override to handle the devices set updating.
         */
        void onDeviceListChanged();

        /**
         * Override to dismiss dialog.
         */
        void dismissDialog();

        /** Override to handle quick access button changes. */
        void onQuickAccessButtonsChanged();
    }
}
