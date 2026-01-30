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

package com.android.server.media;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderInfo;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2Utils;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.media.flags.Flags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides routes for local playbacks such as phone speaker, wired headset, or Bluetooth speakers.
 */
// TODO: check thread safety. We may need to use lock to protect variables.
class SystemMediaRoute2Provider extends MediaRoute2Provider {
    // Package-visible to use this tag for all system routing logic (done across multiple classes).
    /* package */ static final String TAG = "MR2SystemProvider";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final ComponentName COMPONENT_NAME = new ComponentName(
            SystemMediaRoute2Provider.class.getPackage().getName(),
            SystemMediaRoute2Provider.class.getName());

    static final String SYSTEM_SESSION_ID = "SYSTEM_SESSION";

    private final AudioManager mAudioManager;
    protected final Handler mHandler;
    private final Context mContext;
    private final UserHandle mUser;

    private final DeviceRouteController mDeviceRouteController;

    @GuardedBy("mLock")
    private List<String> mSelectedRouteIds = Collections.emptyList();

    /**
     * Placeholder {@link MediaRoute2Info} representation of the currently selected route for apps
     * without system routing permission (like MODIFY_AUDIO_ROUTING, of Bluetooth permissions - see
     * {@link MediaRouter2ServiceImpl} for details). It's created by copying the real selected
     * route, but hiding sensitive info like id and bluetooth address.
     */
    MediaRoute2Info mDefaultRoute;

    @GuardedBy("mLock")
    RoutingSessionInfo mSystemSessionInfo;

    RoutingSessionInfo mDefaultSessionInfo;

    private final AudioManagerBroadcastReceiver mAudioReceiver =
            new AudioManagerBroadcastReceiver();

    private final Object mRequestLock = new Object();

    @GuardedBy("mRequestLock")
    private volatile SessionCreationOrTransferRequest mPendingSessionCreationOrTransferRequest;

    private final Object mTransferLock = new Object();

    @GuardedBy("mTransferLock")
    @Nullable
    private volatile SessionCreationOrTransferRequest mPendingTransferRequest;

    private final DeviceRouteEventListener mDeviceRouteEventListener =
            new DeviceRouteEventListener();

    public static SystemMediaRoute2Provider create(
            Context context, UserHandle user, Looper looper) {
        var instance = new SystemMediaRoute2Provider(context, COMPONENT_NAME, user, looper);
        instance.updateProviderState();
        instance.updateSessionInfosIfNeeded();
        return instance;
    }

    protected SystemMediaRoute2Provider(
            Context context, ComponentName componentName, UserHandle user, Looper looper) {
        super(componentName, /* isSystemRouteProvider= */ true);
        mContext = context;
        mUser = user;
        mHandler = new Handler(looper);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mDeviceRouteController =
                DeviceRouteController.createInstance(context, looper, mDeviceRouteEventListener);
    }

    public void start() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
        intentFilter.addAction(AudioManager.STREAM_DEVICES_CHANGED_ACTION);
        mContext.registerReceiverAsUser(mAudioReceiver, mUser,
                intentFilter, null, null);
        mHandler.post(() -> mDeviceRouteController.start(mUser));
        updateVolume();
    }

    public void stop() {
        mContext.unregisterReceiver(mAudioReceiver);
        mHandler.post(
                () -> {
                    mDeviceRouteController.stop();
                    notifyProviderStateChanged();
                });
    }

    @Override
    public void setCallback(Callback callback) {
        super.setCallback(callback);
        notifyProviderStateChanged();
        notifyGlobalSessionInfoUpdated();
    }

    @Override
    public void requestCreateSession(
            long requestId,
            String packageName,
            String routeOriginalId,
            Bundle sessionHints,
            @RoutingSessionInfo.TransferReason int transferReason,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String transferInitiatorPackageName) {
        // Assume a router without MODIFY_AUDIO_ROUTING permission can't request with
        // a route ID different from the default route ID. The service should've filtered.
        if (TextUtils.equals(routeOriginalId, MediaRoute2Info.ROUTE_ID_DEFAULT)) {
            notifySessionCreated(requestId, mDefaultSessionInfo);
            return;
        }

        synchronized (mLock) {
            if (!Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()) {
                if (mSelectedRouteIds.size() == 1 && mSelectedRouteIds.contains(routeOriginalId)) {
                    RoutingSessionInfo currentSessionInfo =
                            Flags.enableMirroringInMediaRouter2()
                                    ? mSystemSessionInfo
                                    : mSessionInfos.get(0);
                    notifySessionCreated(requestId, currentSessionInfo);
                    return;
                }
            }
        }

        synchronized (mRequestLock) {
            // Handle the previous request as a failure if exists.
            if (mPendingSessionCreationOrTransferRequest != null) {
                notifyRequestFailed(
                        mPendingSessionCreationOrTransferRequest.mRequestId,
                        MediaRoute2ProviderService.REASON_UNKNOWN_ERROR);
            }
            mPendingSessionCreationOrTransferRequest =
                    new SessionCreationOrTransferRequest(
                            requestId,
                            routeOriginalId,
                            RoutingSessionInfo.TRANSFER_REASON_FALLBACK,
                            transferInitiatorUserHandle,
                            transferInitiatorPackageName);
        }

        // Only unprivileged routers call this method, therefore we use TRANSFER_REASON_APP.
        transferToRoute(
                requestId,
                transferInitiatorUserHandle,
                transferInitiatorPackageName,
                SYSTEM_SESSION_ID,
                routeOriginalId,
                transferReason);
    }

    @Override
    public void releaseSession(long requestId, String sessionId) {
        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
            mDeviceRouteController.releaseRoutingSession();
        }
    }

    @Override
    public void updateDiscoveryPreference(
            Set<String> activelyScanningPackages, RouteDiscoveryPreference discoveryPreference,
            Map<String, RouteDiscoveryPreference> perAppPreferences) {
        // Do nothing
    }

    @Override
    public void selectRoute(long requestId, String sessionId, String routeId) {
        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
            // Pass params to DeviceRouteController to start the broadcast
            mDeviceRouteController.selectRoute(requestId, routeId);
        }
    }

    @Override
    public void deselectRoute(long requestId, String sessionId, String routeId) {
        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
            mDeviceRouteController.deselectRoute(requestId, routeId);
        }
    }

    @Override
    public void transferToRoute(
            long requestId,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String transferInitiatorPackageName,
            String sessionOriginalId,
            String routeOriginalId,
            @RoutingSessionInfo.TransferReason int transferReason) {
        String selectedDeviceRouteId =
                mDeviceRouteController.getSelectedRoutes().getFirst().getId();
        if (TextUtils.equals(routeOriginalId, MediaRoute2Info.ROUTE_ID_DEFAULT)) {
            if (Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()) {
                // Transfer to the default route (which is the selected route). We replace the id to
                // be the selected route id so that the transfer reason gets updated.
                routeOriginalId = selectedDeviceRouteId;
            } else {
                Log.w(TAG, "Ignoring transfer to " + MediaRoute2Info.ROUTE_ID_DEFAULT);
                return;
            }
        }

        if (Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()) {
            synchronized (mTransferLock) {
                mPendingTransferRequest =
                        new SessionCreationOrTransferRequest(
                                requestId,
                                routeOriginalId,
                                transferReason,
                                transferInitiatorUserHandle,
                                transferInitiatorPackageName);
            }
        }
        mDeviceRouteController.transferTo(requestId, routeOriginalId);

        if (Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()
                && updateSessionInfosIfNeeded()) {
            notifyGlobalSessionInfoUpdated();
        }
    }

    @Override
    public void setRouteVolume(long requestId, String routeOriginalId, int volume) {
        synchronized (mLock) {
            if (!mSelectedRouteIds.contains(routeOriginalId)) {
                notifyRequestFailed(requestId, MediaRoute2ProviderService.REASON_INVALID_COMMAND);
                return;
            }
        }
        if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
            mDeviceRouteController.setVolume(requestId, routeOriginalId, volume);
        } else {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        }
    }

    @Override
    public void setSessionVolume(long requestId, String sessionOriginalId, int volume) {
        // Do nothing since we don't support grouping volume yet.
    }

    @Override
    public void prepareReleaseSession(String sessionUniqueId) {
        // Do nothing since the system session persists.
    }

    /**
     * Adjusts the volume of {@link MediaRoute2ProviderService service-provided} system media
     * routing sessions.
     *
     * <p>This method does not affect the volume of the system, which is managed by {@link
     * AudioManager}.
     *
     * @param requestId The id of the request, or {@link MediaRoute2ProviderService#REQUEST_ID_NONE}
     *     if there's no associated id.
     * @param direction One of {@link AudioManager#ADJUST_LOWER}, {@link AudioManager#ADJUST_RAISE},
     *     or {@link AudioManager#ADJUST_SAME}.
     * @return Whether the volume key pressed was handled.
     */
    public boolean maybeHandleVolumeKeyEventForSystemMediaSession(long requestId, int direction) {
        return false;
    }

    public MediaRoute2Info getDefaultRoute() {
        return mDefaultRoute;
    }

    public RoutingSessionInfo getDefaultSessionInfo() {
        return mDefaultSessionInfo;
    }

    /**
     * Returns the {@link RoutingSessionInfo} that corresponds to the package with the given name.
     */
    public RoutingSessionInfo getSessionForPackage(String targetPackageName) {
        synchronized (mLock) {
            if (!mSessionInfos.isEmpty()) {
                // Return a copy of the current system session with no modification,
                // except setting the client package name.
                return new RoutingSessionInfo.Builder(mSessionInfos.get(0))
                        .setClientPackageName(targetPackageName)
                        .build();
            } else {
                return null;
            }
        }
    }

    /**
     * Builds a system {@link RoutingSessionInfo} with the selected route set to the currently
     * selected <b>device</b> route (wired or built-in, but not bluetooth) and transferable routes
     * set to the currently available (connected) bluetooth routes.
     *
     * <p>The session's client package name is set to the provided package name.
     *
     * <p>Returns {@code null} if there are no registered system sessions.
     */
    @Nullable
    public RoutingSessionInfo generateDeviceRouteSelectedSessionInfo(String packageName) {
        synchronized (mLock) {
            if (mSessionInfos.isEmpty()) {
                return null;
            }

            List<MediaRoute2Info> selectedDeviceRoutes = mDeviceRouteController.getSelectedRoutes();

            RoutingSessionInfo.Builder builder =
                    new RoutingSessionInfo.Builder(SYSTEM_SESSION_ID, packageName)
                            .setSystemSession(true);
            Set<String> selectedRouteIds =
                    new ArraySet<>(/* capacity= */ selectedDeviceRoutes.size());
            for (var selectedRoute : selectedDeviceRoutes) {
                var routeId = selectedRoute.getId();
                selectedRouteIds.add(routeId);
                builder.addSelectedRoute(routeId);
            }

            for (MediaRoute2Info route : mDeviceRouteController.getAvailableRoutes()) {
                String routeId = route.getId();
                if (!selectedRouteIds.contains(routeId)) {
                    builder.addTransferableRoute(routeId);
                }
            }

            if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                for (MediaRoute2Info route : mDeviceRouteController.getSelectableRoutes()) {
                    builder.addSelectableRoute(route.getId());
                }

                for (MediaRoute2Info route : mDeviceRouteController.getDeselectableRoutes()) {
                    builder.addDeselectableRoute(route.getId());
                }
            }

            if (Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()) {
                var oldSessionInfo =
                        Flags.enableMirroringInMediaRouter2()
                                ? mSystemSessionInfo
                                : mSessionInfos.get(0);
                builder.setTransferReason(oldSessionInfo.getTransferReason())
                        .setTransferInitiator(oldSessionInfo.getTransferInitiatorUserHandle(),
                                oldSessionInfo.getTransferInitiatorPackageName());
            }

            if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                builder.setReleaseType(mDeviceRouteController.getSessionReleaseType());
            } else {
                // Releasing the system routing session only makes sense in the context of
                // Flags.enableOutputSwitcherPersonalAudioSharing.
                builder.setReleaseType(RoutingSessionInfo.RELEASE_UNSUPPORTED);
            }

            return builder.setProviderId(mUniqueId).build();
        }
    }

    /**
     * Notifies the system provider of a {@link MediaRoute2ProviderServiceProxy} update.
     *
     * <p>To be overridden so as to generate system media routes for {@link
     * MediaRoute2ProviderService} routes that {@link MediaRoute2Info#supportsSystemMediaRouting()
     * support system media routing}).
     *
     * @param serviceProxy The proxy of the service that updated its state.
     */
    public void updateSystemMediaRoutesFromProxy(MediaRoute2ProviderServiceProxy serviceProxy) {
        // Do nothing. This implementation doesn't support MR2ProviderService system media routes.
        // The subclass overrides this method to implement app-managed system media routing (aka
        // mirroring).
    }

    /**
     * Called when the system provider state changes.
     *
     * <p>To be overridden by {@link SystemMediaRoute2Provider2}, so that app-provided system media
     * routing routes are added before setting the provider state.
     */
    public void onSystemProviderRoutesChanged(MediaRoute2ProviderInfo providerInfo) {
        setProviderState(providerInfo);
    }

    protected void updateProviderState() {
        MediaRoute2ProviderInfo.Builder builder = new MediaRoute2ProviderInfo.Builder();

        List<MediaRoute2Info> deviceRoutes = mDeviceRouteController.getAvailableRoutes();
        for (MediaRoute2Info route : deviceRoutes) {
            builder.addRoute(route);
        }
        if (!Flags.enableMirroringInMediaRouter2()) {
            setProviderState(builder.build());
        }
        MediaRoute2ProviderInfo providerInfo = builder.build();
        onSystemProviderRoutesChanged(providerInfo);
        if (DEBUG) {
            Slog.d(TAG, "Updating system provider info : " + providerInfo);
        }
    }

    /**
     * Updates the mSessionInfo. Returns true if the session info is changed.
     */
    boolean updateSessionInfosIfNeeded() {
        synchronized (mLock) {
            RoutingSessionInfo oldSessionInfo;
            if (Flags.enableMirroringInMediaRouter2()) {
                oldSessionInfo = mSystemSessionInfo;
            } else {
                oldSessionInfo = mSessionInfos.isEmpty() ? null : mSessionInfos.get(0);
            }

            RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                    SYSTEM_SESSION_ID, "" /* clientPackageName */)
                    .setSystemSession(true);

            List<MediaRoute2Info> selectedRoutes = mDeviceRouteController.getSelectedRoutes();
            mSelectedRouteIds = selectedRoutes.stream().map(MediaRoute2Info::getId).toList();
            List<String> transferableRoutes = new ArrayList<>();

            for (String selectedRouteId : mSelectedRouteIds) {
                builder.addSelectedRoute(selectedRouteId);
            }

            for (MediaRoute2Info route : mDeviceRouteController.getAvailableRoutes()) {
                String routeId = route.getId();
                if (!mSelectedRouteIds.contains(routeId)) {
                    transferableRoutes.add(routeId);
                }
            }

            for (String route : transferableRoutes) {
                builder.addTransferableRoute(route);
            }

            if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                for (MediaRoute2Info route : mDeviceRouteController.getSelectableRoutes()) {
                    builder.addSelectableRoute(route.getId());
                }

                for (MediaRoute2Info route : mDeviceRouteController.getDeselectableRoutes()) {
                    builder.addDeselectableRoute(route.getId());
                }
            }

            // Handle the default route
            var defaultRouteBuilder =
                    new MediaRoute2Info.Builder(
                                    MediaRoute2Info.ROUTE_ID_DEFAULT, selectedRoutes.getFirst())
                            .setSystemRoute(true)
                            .setProviderId(mUniqueId);
            if (Flags.hideBtAddressFromAppsWithoutBtPermission()) {
                defaultRouteBuilder.setAddress(null); // We clear the address field.
            }
            mDefaultRoute = defaultRouteBuilder.build();

            if (Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()) {
                int transferReason = RoutingSessionInfo.TRANSFER_REASON_FALLBACK;
                UserHandle transferInitiatorUserHandle = null;
                String transferInitiatorPackageName = null;

                if (oldSessionInfo != null
                        && containsSelectedRouteWithId(
                                oldSessionInfo, mSelectedRouteIds.getFirst())) {
                    transferReason = oldSessionInfo.getTransferReason();
                    transferInitiatorUserHandle = oldSessionInfo.getTransferInitiatorUserHandle();
                    transferInitiatorPackageName = oldSessionInfo.getTransferInitiatorPackageName();
                }

                synchronized (mTransferLock) {
                    if (mPendingTransferRequest != null) {
                        boolean isTransferringToTheSelectedRoute =
                                mPendingTransferRequest.isTargetRoute(selectedRoutes.getFirst());
                        boolean canBePotentiallyTransferred =
                                mPendingTransferRequest.isTargetRouteIdInRouteOriginalIdList(
                                        transferableRoutes);

                        if (isTransferringToTheSelectedRoute) {
                            transferReason = mPendingTransferRequest.mTransferReason;
                            transferInitiatorUserHandle =
                                    mPendingTransferRequest.mTransferInitiatorUserHandle;
                            transferInitiatorPackageName =
                                    mPendingTransferRequest.mTransferInitiatorPackageName;

                            mPendingTransferRequest = null;
                        } else if (!canBePotentiallyTransferred) {
                            mPendingTransferRequest = null;
                        }
                    }
                }

                if (Flags.enableOutputSwitcherPersonalAudioSharing()) {
                    builder.setReleaseType(mDeviceRouteController.getSessionReleaseType());
                }

                builder.setTransferReason(transferReason)
                        .setTransferInitiator(
                                transferInitiatorUserHandle, transferInitiatorPackageName);
            }

            RoutingSessionInfo newSessionInfo = builder.setProviderId(mUniqueId).build();

            synchronized (mRequestLock) {
                reportPendingSessionRequestResultLockedIfNeeded(newSessionInfo);
            }

            if (Objects.equals(oldSessionInfo, newSessionInfo)) {
                return false;
            } else {
                if (DEBUG) {
                    Slog.d(TAG, "Updating system routing session info : " + newSessionInfo);
                }
                mSystemSessionInfo = newSessionInfo;
                onSystemSessionInfoUpdated();
                mDefaultSessionInfo =
                        new RoutingSessionInfo.Builder(
                                        SYSTEM_SESSION_ID, "" /* clientPackageName */)
                                .setProviderId(mUniqueId)
                                .setSystemSession(true)
                                .addSelectedRoute(MediaRoute2Info.ROUTE_ID_DEFAULT)
                                .setTransferReason(newSessionInfo.getTransferReason())
                                .setTransferInitiator(
                                        newSessionInfo.getTransferInitiatorUserHandle(),
                                        newSessionInfo.getTransferInitiatorPackageName())
                                .setReleaseType(RoutingSessionInfo.RELEASE_UNSUPPORTED)
                                .build();
                return true;
            }
        }
    }

    @GuardedBy("mLock")
    protected void onSystemSessionInfoUpdated() {
        mSessionInfos.clear();
        mSessionInfos.add(mSystemSessionInfo);
    }

    @GuardedBy("mRequestLock")
    private void reportPendingSessionRequestResultLockedIfNeeded(
            RoutingSessionInfo newSessionInfo) {
        if (mPendingSessionCreationOrTransferRequest == null) {
            // No pending request, nothing to report.
            return;
        }

        long pendingRequestId = mPendingSessionCreationOrTransferRequest.mRequestId;
        if (mSelectedRouteIds.contains(
                mPendingSessionCreationOrTransferRequest.mTargetOriginalRouteId)) {
            if (DEBUG) {
                Slog.w(
                        TAG,
                        "Session creation success to route "
                                + mPendingSessionCreationOrTransferRequest.mTargetOriginalRouteId);
            }
            mPendingSessionCreationOrTransferRequest = null;
            notifySessionCreated(pendingRequestId, newSessionInfo);
        } else {
            if (DEBUG) {
                Slog.w(
                        TAG,
                        "Session creation failed to route "
                                + mPendingSessionCreationOrTransferRequest.mTargetOriginalRouteId);
            }
            mPendingSessionCreationOrTransferRequest = null;
            notifyRequestFailed(
                    pendingRequestId, MediaRoute2ProviderService.REASON_UNKNOWN_ERROR);
        }
    }

    private boolean containsSelectedRouteWithId(
            @Nullable RoutingSessionInfo sessionInfo, @NonNull String selectedRouteId) {
        if (sessionInfo == null) {
            return false;
        }

        List<String> selectedRoutes = sessionInfo.getSelectedRoutes();

        if (!Flags.enableOutputSwitcherPersonalAudioSharing()) {
            if (selectedRoutes.size() != 1) {
                throw new IllegalStateException(
                        "Selected routes list should contain only 1 route id.");
            }
        }

        String oldSelectedRouteId = MediaRouter2Utils.getOriginalId(selectedRoutes.get(0));
        return oldSelectedRouteId != null && oldSelectedRouteId.equals(selectedRouteId);
    }

    void publishProviderState() {
        updateProviderState();
        notifyProviderStateChanged();
    }

    void notifyGlobalSessionInfoUpdated() {
        if (!haveCallback()) {
            return;
        }

        RoutingSessionInfo sessionInfo;
        synchronized (mLock) {
            if (mSessionInfos.isEmpty()) {
                return;
            }
            sessionInfo = mSessionInfos.get(0);
        }

        notifySessionUpdated(
                this,
                sessionInfo,
                /* packageNamesWithRoutingSessionOverrides= */ Set.of(),
                /* shouldShowVolumeUi= */ false);
    }

    @Override
    protected String getDebugString() {
        return TextUtils.formatSimple(
                "%s - package: %s, selected route ids: %s",
                getClass().getSimpleName(), mComponentName.getPackageName(), mSelectedRouteIds);
    }

    void updateVolume() {
        int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        if (mDefaultRoute.getVolume() != volume) {
            mDefaultRoute = new MediaRoute2Info.Builder(mDefaultRoute)
                    .setVolume(volume)
                    .build();
        }
        mDeviceRouteController.updateVolume(volume);
        publishProviderState();
    }

    private class DeviceRouteEventListener implements DeviceRouteController.EventListener {

        @Override
        public void onDeviceRouteChanged() {
            mHandler.post(
                    () -> {
                        publishProviderState();
                        if (updateSessionInfosIfNeeded()) {
                            notifyGlobalSessionInfoUpdated();
                        }
                    });
        }

        @Override
        public void onDeviceRouteRequestFailed(long requestId, int reason) {
            notifyRequestFailed(requestId, reason);
        }
    }

    private class AudioManagerBroadcastReceiver extends BroadcastReceiver {
        // This will be called in the main thread.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(AudioManager.VOLUME_CHANGED_ACTION)
                    && !intent.getAction().equals(AudioManager.STREAM_DEVICES_CHANGED_ACTION)) {
                return;
            }

            int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
            if (streamType != AudioManager.STREAM_MUSIC) {
                return;
            }

            if (Flags.enableMr2ServiceNonMainBgThread()) {
                mHandler.post(SystemMediaRoute2Provider.this::updateVolume);
            } else {
                updateVolume();
            }
        }
    }
}
