/*
 * Copyright 2024 The Android Open Source Project
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

import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.MediaRoute2Info.FEATURE_LIVE_VIDEO;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
import static android.media.MediaRoute2ProviderService.REQUEST_ID_NONE;
import static android.media.RoutingSessionInfo.RELEASE_TYPE_CASTING;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.RunningAppProcessInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderInfo;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRoute2ProviderService.Reason;
import android.media.MediaRouter2Utils;
import android.media.RoutingSessionInfo;
import android.os.Binder;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.media.MediaRoute2ProviderServiceProxy.SystemMediaSessionCallback;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extends {@link SystemMediaRoute2Provider} by adding system routes provided by {@link
 * MediaRoute2ProviderService provider services}.
 *
 * <p>System routes are those which can handle the system audio and/or video.
 */
/* package */ class SystemMediaRoute2Provider2 extends SystemMediaRoute2Provider {

    private static final String UNIQUE_SYSTEM_ID_PREFIX = "SYSTEM";
    private static final String UNIQUE_SYSTEM_ID_SEPARATOR = "-";

    /**
     * The portion of {@link RoutingSessionInfo#getVolumeMax()} that changes as a result of a volume
     * key press.
     */
    private static final float VOLUME_KEY_PRESS_STEP = 0.05f;

    /**
     * The maximum duration during which a routing session volume change is considered the result of
     * a volume key press.
     */
    private static final long SHOW_UI_FOR_VOLUME_CHANGE_TIMEOUT_MS = 3000;

    /**
     * The minimum {@link ActivityManager.RunningAppProcessInfo package importance} that an app must
     * hold for its media to be re-routed.
     *
     * <p>If an app's importance falls below this threshold, any associated routing sessions are
     * released.
     *
     * <p>Note that importance is inversely proportional to the numeric value: A smaller numeric
     * value means more importance.
     */
    private static final int MINIMUM_IMPORTANCE_FOR_REROUTING =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;

    private final PackageManager mPackageManager;
    private final ActivityManager mActivityManager;
    private final Runnable mClearShouldShowVolumeUiFlagRunnable = this::clearShouldShowVolumeUiFlag;

    @GuardedBy("mLock")
    private MediaRoute2ProviderInfo mLastSystemProviderInfo;

    @GuardedBy("mLock")
    private final Map<String, ProviderProxyRecord> mProxyRecords = new ArrayMap<>();

    @GuardedBy("mLock")
    private final Map<String, SystemMediaSessionRecord> mSessionOriginalIdToSessionRecord =
            new ArrayMap<>();

    /**
     * Maps package names to corresponding sessions maintained by {@link MediaRoute2ProviderService
     * provider services}.
     */
    @GuardedBy("mLock")
    private final Map<String, SystemMediaSessionRecord> mPackageNameToSessionRecord =
            new ArrayMap<>();

    /**
     * Maps route {@link MediaRoute2Info#getOriginalId original ids} to the id of the {@link
     * MediaRoute2ProviderService provider service} that manages the corresponding route.
     */
    @GuardedBy("mLock")
    private final Map<String, String> mOriginalRouteIdToProviderId = new ArrayMap<>();

    /** Maps request ids to pending session creation callbacks. */
    @GuardedBy("mLock")
    private final LongSparseArray<SystemMediaSessionCallbackImpl> mPendingSessionCreations =
            new LongSparseArray<>();

    /**
     * Holds the original id of a session that has recently received a volume adjustment request due
     * to a volume key press.
     */
    @GuardedBy("mLock")
    @Nullable
    private String mRecentRecipientOfVolumeKeyPressOriginalId = null;

    private static final ComponentName COMPONENT_NAME =
            new ComponentName(
                    SystemMediaRoute2Provider2.class.getPackage().getName(),
                    SystemMediaRoute2Provider2.class.getName());

    public static SystemMediaRoute2Provider2 create(
            Context context, UserHandle user, Looper looper) {
        var instance = new SystemMediaRoute2Provider2(context, user, looper);
        instance.updateProviderState();
        instance.updateSessionInfosIfNeeded();
        return instance;
    }

    @SuppressLint("MissingPermission") // We are running within the system_server.
    private SystemMediaRoute2Provider2(Context context, UserHandle user, Looper looper) {
        super(context, COMPONENT_NAME, user, looper);
        mPackageManager = context.getPackageManager();
        mActivityManager = Objects.requireNonNull(context.getSystemService(ActivityManager.class));
        mActivityManager.addOnUidImportanceListener(
                this::onUidImportanceChanged, MINIMUM_IMPORTANCE_FOR_REROUTING);
    }

    @SuppressLint("MissingPermission") // We are running within the system_server.
    @Override
    public void transferToRoute(
            long requestId,
            @NonNull UserHandle clientUserHandle,
            @NonNull String clientPackageName,
            String sessionOriginalId,
            String routeOriginalId,
            int transferReason) {
        synchronized (mLock) {
            var targetProviderProxyId = mOriginalRouteIdToProviderId.get(routeOriginalId);
            var targetProviderProxyRecord = mProxyRecords.get(targetProviderProxyId);
            // Holds the target route, if it's managed by a provider service. Holds null otherwise.
            var serviceTargetRoute =
                    targetProviderProxyRecord != null
                            ? targetProviderProxyRecord.getRouteByOriginalId(routeOriginalId)
                            : null;
            var existingSessionRecord = mPackageNameToSessionRecord.get(clientPackageName);
            if (existingSessionRecord != null) {
                var existingSession = existingSessionRecord.mSourceSessionInfo;
                if (targetProviderProxyId != null
                        && TextUtils.equals(
                                targetProviderProxyId, existingSession.getProviderId())) {
                    // The currently selected route and target route both belong to the same
                    // provider. We tell the provider to handle the transfer.
                    if (serviceTargetRoute == null) {
                        notifyRequestFailed(
                                requestId, MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE);
                    } else {
                        targetProviderProxyRecord.mProxy.transferToRoute(
                                requestId,
                                clientUserHandle,
                                clientPackageName,
                                existingSession.getOriginalId(),
                                targetProviderProxyRecord.mNewOriginalIdToSourceOriginalIdMap.get(
                                        routeOriginalId),
                                transferReason);
                    }
                    return;
                } else {
                    // The target route is handled by a provider other than the target one. We need
                    // to release the existing session.
                    var currentProxyRecord = existingSessionRecord.getProxyRecord();
                    if (currentProxyRecord != null) {
                        currentProxyRecord.releaseSession(
                                requestId, existingSession.getOriginalId());
                        existingSessionRecord.removeSelfFromSessionMaps();
                    }
                }
            }

            if (serviceTargetRoute != null) {
                int uid = fetchUid(clientPackageName, clientUserHandle);
                int packageImportance =
                        uid != Process.INVALID_UID
                                ? mActivityManager.getUidImportance(uid)
                                : RunningAppProcessInfo.IMPORTANCE_GONE;
                if (packageImportance > MINIMUM_IMPORTANCE_FOR_REROUTING) {
                    String message =
                            TextUtils.formatSimple(
                                    "Ignoring transfer request for '%s' uid=%d due to package"
                                            + " importance=%d",
                                    clientPackageName, uid, packageImportance);
                    Log.w(TAG, message);
                    notifyRequestFailed(requestId, MediaRoute2ProviderService.REASON_REJECTED);
                    return;
                }
                var pendingCreationCallback =
                        new SystemMediaSessionCallbackImpl(
                                targetProviderProxyId, requestId, clientPackageName);
                mPendingSessionCreations.put(requestId, pendingCreationCallback);
                targetProviderProxyRecord.requestCreateSystemMediaSession(
                        requestId,
                        uid,
                        clientPackageName,
                        routeOriginalId,
                        pendingCreationCallback);
            } else {
                // The target route is not provided by any of the services. Assume it's a system
                // provided route.
                super.transferToRoute(
                        requestId,
                        clientUserHandle,
                        clientPackageName,
                        sessionOriginalId,
                        routeOriginalId,
                        transferReason);
            }
        }
    }

    @Nullable
    @Override
    public RoutingSessionInfo getSessionForPackage(String packageName) {
        synchronized (mLock) {
            var systemSession = super.getSessionForPackage(packageName);
            if (systemSession == null) {
                return null;
            }
            var overridingSession = mPackageNameToSessionRecord.get(packageName);
            if (overridingSession != null) {
                var builder =
                        new RoutingSessionInfo.Builder(overridingSession.mTranslatedSessionInfo)
                                .setClientPackageName(packageName)
                                .setProviderId(mUniqueId)
                                .setSystemSession(true);
                for (var systemRoute : mLastSystemProviderInfo.getRoutes()) {
                    builder.addTransferableRoute(systemRoute.getOriginalId());
                }
                return builder.build();
            } else {
                return systemSession;
            }
        }
    }

    @Override
    public void setRouteVolume(long requestId, String routeOriginalId, int volume) {
        synchronized (mLock) {
            var targetProviderProxyId = mOriginalRouteIdToProviderId.get(routeOriginalId);
            var targetProviderProxyRecord = mProxyRecords.get(targetProviderProxyId);
            // Holds the target route, if it's managed by a provider service. Holds null otherwise.
            if (targetProviderProxyRecord != null) {
                var serviceTargetRoute =
                        targetProviderProxyRecord.mNewOriginalIdToSourceOriginalIdMap.get(
                                routeOriginalId);
                if (serviceTargetRoute != null) {
                    targetProviderProxyRecord.mProxy.setRouteVolume(
                            requestId, serviceTargetRoute, volume);
                } else {
                    notifyRequestFailed(
                            requestId, MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE);
                }
            }
        }
        super.setRouteVolume(requestId, routeOriginalId, volume);
    }

    @Override
    public void setSessionVolume(long requestId, String sessionOriginalId, int volume) {
        if (SYSTEM_SESSION_ID.equals(sessionOriginalId)) {
            super.setSessionVolume(requestId, sessionOriginalId, volume);
            return;
        }
        synchronized (mLock) {
            var sessionRecord = mSessionOriginalIdToSessionRecord.get(sessionOriginalId);
            var proxyRecord = sessionRecord != null ? sessionRecord.getProxyRecord() : null;
            if (proxyRecord != null) {
                proxyRecord.mProxy.setSessionVolume(
                        requestId, sessionRecord.getServiceSessionId(), volume);
                return;
            }
        }
        notifyRequestFailed(requestId, MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE);
    }

    @Override
    public void selectRoute(long requestId, String sessionOriginalId, String routeId) {
        if (SYSTEM_SESSION_ID.equals(sessionOriginalId)) {
            super.selectRoute(requestId, sessionOriginalId, routeId);
            return;
        }
        synchronized (mLock) {
            var sessionRecord = mSessionOriginalIdToSessionRecord.get(sessionOriginalId);
            var proxyRecord = sessionRecord != null ? sessionRecord.getProxyRecord() : null;
            if (proxyRecord != null) {
                var targetSourceRouteId =
                        proxyRecord.mNewOriginalIdToSourceOriginalIdMap.get(routeId);
                if (targetSourceRouteId != null) {
                    proxyRecord.mProxy.selectRoute(
                            requestId, sessionRecord.getServiceSessionId(), targetSourceRouteId);
                }
                return;
            }
        }
        notifyRequestFailed(requestId, MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE);
    }

    @Override
    public void deselectRoute(long requestId, String sessionOriginalId, String routeId) {
        if (SYSTEM_SESSION_ID.equals(sessionOriginalId)) {
            super.selectRoute(requestId, sessionOriginalId, routeId);
            return;
        }
        synchronized (mLock) {
            var sessionRecord = mSessionOriginalIdToSessionRecord.get(sessionOriginalId);
            var proxyRecord = sessionRecord != null ? sessionRecord.getProxyRecord() : null;
            if (proxyRecord != null) {
                var targetSourceRouteId =
                        proxyRecord.mNewOriginalIdToSourceOriginalIdMap.get(routeId);
                if (targetSourceRouteId != null) {
                    proxyRecord.mProxy.deselectRoute(
                            requestId, sessionRecord.getServiceSessionId(), targetSourceRouteId);
                }
                return;
            }
        }
        notifyRequestFailed(requestId, MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE);
    }

    @Override
    public void releaseSession(long requestId, String sessionOriginalId) {
        if (SYSTEM_SESSION_ID.equals(sessionOriginalId)) {
            super.releaseSession(requestId, sessionOriginalId);
            return;
        }
        synchronized (mLock) {
            if (TextUtils.equals(sessionOriginalId, mRecentRecipientOfVolumeKeyPressOriginalId)) {
                mHandler.removeCallbacks(mClearShouldShowVolumeUiFlagRunnable);
                mRecentRecipientOfVolumeKeyPressOriginalId = null;
            }
            var sessionRecord = mSessionOriginalIdToSessionRecord.get(sessionOriginalId);
            if (sessionRecord != null) {
                sessionRecord.removeSelfFromSessionMaps();
                var proxyRecord = sessionRecord.getProxyRecord();
                if (proxyRecord != null) {
                    proxyRecord.releaseSession(requestId, sessionRecord.getServiceSessionId());
                }
                updateSessionInfo();
                return;
            }
        }
        notifyRequestFailed(requestId, MediaRoute2ProviderService.REASON_REJECTED);
    }

    /**
     * Returns the uid that corresponds to the given name and user handle, or {@link
     * Process#INVALID_UID} if a uid couldn't be found.
     */
    @SuppressLint("MissingPermission")
    // We clear the calling identity before calling the package manager, and we are running on the
    // system_server.
    private int fetchUid(String clientPackageName, UserHandle clientUserHandle) {
        final long token = Binder.clearCallingIdentity();
        try {
            return mPackageManager.getApplicationInfoAsUser(
                            clientPackageName, /* flags= */ 0, clientUserHandle)
                    .uid;
        } catch (PackageManager.NameNotFoundException e) {
            return Process.INVALID_UID;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    protected void onSystemSessionInfoUpdated() {
        updateSessionInfo();
    }

    @Override
    public void updateSystemMediaRoutesFromProxy(MediaRoute2ProviderServiceProxy serviceProxy) {
        var proxyRecord = ProviderProxyRecord.createFor(serviceProxy);
        synchronized (mLock) {
            if (proxyRecord == null) {
                mProxyRecords.remove(serviceProxy.mUniqueId);
            } else {
                mProxyRecords.put(serviceProxy.mUniqueId, proxyRecord);
            }
            updateProviderInfo();
        }
        updateSessionInfo();
        notifyProviderStateChanged();
        notifyGlobalSessionInfoUpdated();
    }

    @Override
    public void onSystemProviderRoutesChanged(MediaRoute2ProviderInfo providerInfo) {
        synchronized (mLock) {
            mLastSystemProviderInfo = providerInfo;
            updateProviderInfo();
        }
        updateSessionInfo();
        notifyGlobalSessionInfoUpdated();
    }

    /**
     * Cleans up any ongoing service-managed routing sessions for apps that fall below the {@link
     * #MINIMUM_IMPORTANCE_FOR_REROUTING importance threshold}.
     */
    private void onUidImportanceChanged(int uid, int importance) {
        if (importance <= MINIMUM_IMPORTANCE_FOR_REROUTING) {
            // We only care about packages that have dropped their importance below the threshold.
            return;
        }
        releaseSessionsForUid(uid);
    }

    /** Releases any sessions associated with the given uid. */
    private void releaseSessionsForUid(int uid) {
        var packageNamesForUid = mPackageManager.getPackagesForUid(uid);
        if (packageNamesForUid == null) {
            return;
        }
        synchronized (mLock) {
            for (String packageName : packageNamesForUid) {
                var sessionRecord = mPackageNameToSessionRecord.get(packageName);
                if (sessionRecord != null) {
                    mHandler.post(
                            () ->
                                    releaseSession(
                                            REQUEST_ID_NONE, sessionRecord.getServiceSessionId()));
                }
            }
        }
    }

    /**
     * Updates the {@link #mSessionInfos} by expanding the {@link SystemMediaRoute2Provider} session
     * with information from the {@link MediaRoute2ProviderService provider services}.
     */
    private void updateSessionInfo() {
        synchronized (mLock) {
            var systemSessionInfo = mSystemSessionInfo;
            if (systemSessionInfo == null) {
                // The system session info hasn't been initialized yet. Do nothing.
                return;
            }
            var builder = new RoutingSessionInfo.Builder(systemSessionInfo);
            mProxyRecords.values().stream()
                    .flatMap(ProviderProxyRecord::getRoutesStream)
                    .map(MediaRoute2Info::getOriginalId)
                    .forEach(builder::addTransferableRoute);
            mSessionInfos.clear();
            mSessionInfos.add(builder.build());
            for (var sessionRecords : mPackageNameToSessionRecord.values()) {
                mSessionInfos.add(sessionRecords.mTranslatedSessionInfo);
            }
        }
    }

    /**
     * Returns a new a provider info that includes all routes from the system provider {@link
     * SystemMediaRoute2Provider}, along with system routes from {@link MediaRoute2ProviderService
     * provider services}.
     */
    @GuardedBy("mLock")
    private void updateProviderInfo() {
        MediaRoute2ProviderInfo.Builder builder =
                new MediaRoute2ProviderInfo.Builder(mLastSystemProviderInfo);
        mOriginalRouteIdToProviderId.clear();
        for (var proxyRecord : mProxyRecords.values()) {
            String proxyId = proxyRecord.mProxy.mUniqueId;
            proxyRecord
                    .getRoutesStream()
                    .forEach(
                            route -> {
                                builder.addRoute(route);
                                mOriginalRouteIdToProviderId.put(route.getOriginalId(), proxyId);
                            });
        }
        setProviderState(builder.build());
    }

    @Override
    /* package */ void notifyGlobalSessionInfoUpdated() {
        if (!haveCallback()) {
            return;
        }

        RoutingSessionInfo sessionInfo;
        Set<String> packageNamesWithRoutingSessionOverrides;
        synchronized (mLock) {
            if (mSessionInfos.isEmpty()) {
                return;
            }
            packageNamesWithRoutingSessionOverrides = mPackageNameToSessionRecord.keySet();
            sessionInfo = mSessionInfos.getFirst();
        }

        notifySessionUpdated(
                this,
                sessionInfo,
                packageNamesWithRoutingSessionOverrides,
                /* shouldShowVolumeUi= */ false);
    }

    @Override
    public boolean maybeHandleVolumeKeyEventForSystemMediaSession(long requestId, int direction) {
        synchronized (mLock) {
            var sessionCount = mSessionOriginalIdToSessionRecord.size();
            if (mSessionOriginalIdToSessionRecord.size() != 1) {
                // There's either no system media sessions, or too many for us to decide for one.
                if (sessionCount > 1) {
                    Log.i(
                            TAG,
                            "Ignoring volume adjustment request due to multiple simultaneous"
                                + " sessions.");
                }
                return false;
            }
            var volumeAdjustmentTargetSessionRecord =
                    mSessionOriginalIdToSessionRecord.values().stream().findFirst().get();
            var proxyRecord = volumeAdjustmentTargetSessionRecord.getProxyRecord();
            if (proxyRecord == null) {
                Log.w(TAG, "Ignoring volume adjustment because proxy record is not present");
                return false;
            }
            Integer factor =
                    switch (direction) {
                        case AudioManager.ADJUST_RAISE -> 1;
                        case AudioManager.ADJUST_LOWER -> -1;
                        case AudioManager.ADJUST_SAME -> 0;
                        default -> null;
                    };
            if (factor == null) {
                Log.w(
                        TAG,
                        "Ignoring volume adjustment event due to unexpected direction: "
                                + direction);
                return false;
            }
            var currentSessionInfo = volumeAdjustmentTargetSessionRecord.mSourceSessionInfo;
            if (currentSessionInfo.getVolumeHandling() == PLAYBACK_VOLUME_FIXED) {
                Log.w(TAG, "Ignoring volume adjustment event due to fixed session volume");
                return false;
            }
            int volumeStep = Math.round(VOLUME_KEY_PRESS_STEP * currentSessionInfo.getVolumeMax());
            volumeStep = Math.max(1, volumeStep) * factor;
            int oldVolume = currentSessionInfo.getVolume();
            int newVolume = oldVolume + volumeStep;
            newVolume = Math.clamp(newVolume, /* min= */ 0, currentSessionInfo.getVolumeMax());
            if (oldVolume != newVolume) {
                String logMessage =
                        TextUtils.formatSimple(
                                "Setting volume to %d/%d on system media session managed by '%s'",
                                newVolume,
                                currentSessionInfo.getVolumeMax(),
                                currentSessionInfo.getOwnerPackageName());
                Log.i(TAG, logMessage);
                mHandler.removeCallbacks(mClearShouldShowVolumeUiFlagRunnable);
                mHandler.postDelayed(
                        mClearShouldShowVolumeUiFlagRunnable, SHOW_UI_FOR_VOLUME_CHANGE_TIMEOUT_MS);
                mRecentRecipientOfVolumeKeyPressOriginalId =
                        volumeAdjustmentTargetSessionRecord.mOriginalId;
                proxyRecord.mProxy.setSessionVolume(
                        requestId,
                        volumeAdjustmentTargetSessionRecord.getServiceSessionId(),
                        newVolume);
            } else {
                String logMessage =
                        TextUtils.formatSimple(
                                "Ignoring request to set volume to %d/%d on system media session"
                                        + " managed by '%s'",
                                newVolume,
                                currentSessionInfo.getVolumeMax(),
                                currentSessionInfo.getOwnerPackageName());
                Log.i(TAG, logMessage);
            }
            return true;
        }
    }

    private void clearShouldShowVolumeUiFlag() {
        synchronized (mLock) {
            mRecentRecipientOfVolumeKeyPressOriginalId = null;
        }
    }

    private void onSessionOverrideUpdated(RoutingSessionInfo sessionInfo) {
        // TODO: b/362507305 - Consider adding routes from other provider services. This is not a
        // trivial change because a provider1-route to provider2-route transfer has seemingly two
        // possible approachies. Either we first release the current session and then create the new
        // one, in which case the audio is briefly going to leak through the system route. On the
        // other hand, if we first create the provider2 session, then there will be a period during
        // which there will be two overlapping routing policies asking for the exact same media
        // stream.
        var builder = new RoutingSessionInfo.Builder(sessionInfo);
        MediaRoute2ProviderInfo providerInfo;
        boolean shouldShowVolumeUi;
        synchronized (mLock) {
            providerInfo = mLastSystemProviderInfo;
            shouldShowVolumeUi =
                    TextUtils.equals(
                            sessionInfo.getOriginalId(),
                            mRecentRecipientOfVolumeKeyPressOriginalId);
        }
        providerInfo.getRoutes().stream()
                .map(MediaRoute2Info::getOriginalId)
                .forEach(builder::addTransferableRoute);
        notifySessionUpdated(
                /* provider= */ this,
                builder.build(),
                /* packageNamesWithRoutingSessionOverrides= */ Set.of(),
                shouldShowVolumeUi);
    }

    /**
     * Equivalent to {@link #asUniqueSystemId}, except it takes a unique id instead of an original
     * id.
     */
    private static String uniqueIdAsSystemRouteId(String providerId, String uniqueRouteId) {
        return asUniqueSystemId(providerId, MediaRouter2Utils.getOriginalId(uniqueRouteId));
    }

    /**
     * Returns a unique {@link MediaRoute2Info#getOriginalId() original id} for this provider to
     * publish system media routes and sessions from {@link MediaRoute2ProviderService provider
     * services}.
     *
     * <p>This provider will publish system media routes as part of the system routing session.
     * However, said routes may also support {@link MediaRoute2Info#FLAG_ROUTING_TYPE_REMOTE remote
     * routing}, meaning we cannot use the same id, or there would be an id collision. As a result,
     * we derive a {@link MediaRoute2Info#getOriginalId original id} that is unique among all
     * original route ids used by this provider.
     */
    private static String asUniqueSystemId(String providerId, String originalId) {
        return UNIQUE_SYSTEM_ID_PREFIX
                + UNIQUE_SYSTEM_ID_SEPARATOR
                + providerId
                + UNIQUE_SYSTEM_ID_SEPARATOR
                + originalId;
    }

    /**
     * Holds information about {@link MediaRoute2ProviderService provider services} registered in
     * the system.
     *
     * @param mProxy The corresponding {@link MediaRoute2ProviderServiceProxy}.
     * @param mSystemMediaRoutes The last snapshot of routes from the service that support system
     *     media routing, as defined by {@link MediaRoute2Info#supportsSystemMediaRouting()}.
     * @param mNewOriginalIdToSourceOriginalIdMap Maps the {@link #mSystemMediaRoutes} ids to the
     *     original ids of corresponding {@link MediaRoute2ProviderService service} route.
     */
    private record ProviderProxyRecord(
            MediaRoute2ProviderServiceProxy mProxy,
            Map<String, MediaRoute2Info> mSystemMediaRoutes,
            Map<String, String> mNewOriginalIdToSourceOriginalIdMap) {

        /** Returns a stream representation of the {@link #mSystemMediaRoutes}. */
        public Stream<MediaRoute2Info> getRoutesStream() {
            return mSystemMediaRoutes.values().stream();
        }

        @Nullable
        public MediaRoute2Info getRouteByOriginalId(String routeOriginalId) {
            return mSystemMediaRoutes.get(routeOriginalId);
        }

        /**
         * Requests the creation of a system media routing session.
         *
         * @param requestId The request id.
         * @param uid The uid of the package whose media to route, or {@link Process#INVALID_UID} if
         *     not applicable.
         * @param packageName The name of the package whose media to route.
         * @param originalRouteId The {@link MediaRoute2Info#getOriginalId() original route id} of
         *     the route that should be initially selected.
         * @param callback A {@link SystemMediaSessionCallback} for events.
         * @see MediaRoute2ProviderService#onCreateSystemRoutingSession
         */
        public void requestCreateSystemMediaSession(
                long requestId,
                int uid,
                String packageName,
                String originalRouteId,
                SystemMediaSessionCallback callback) {
            var targetRouteId = mNewOriginalIdToSourceOriginalIdMap.get(originalRouteId);
            if (targetRouteId == null) {
                Log.w(
                        TAG,
                        "Failed system media session creation due to lack of mapping for id: "
                                + originalRouteId);
                callback.onRequestFailed(
                        requestId, MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE);
            } else {
                mProxy.requestCreateSystemMediaSession(
                        requestId,
                        uid,
                        packageName,
                        targetRouteId,
                        /* sessionHints= */ null,
                        callback);
            }
        }

        public void releaseSession(long requestId, String originalSessionId) {
            mProxy.releaseSession(requestId, originalSessionId);
        }

        /**
         * Returns a new instance, or null if the given {@code serviceProxy} doesn't have an
         * associated {@link MediaRoute2ProviderInfo}.
         */
        @Nullable
        public static ProviderProxyRecord createFor(MediaRoute2ProviderServiceProxy serviceProxy) {
            MediaRoute2ProviderInfo providerInfo = serviceProxy.getProviderInfo();
            if (providerInfo == null) {
                return null;
            }
            Map<String, MediaRoute2Info> routesMap = new ArrayMap<>();
            Map<String, String> idMap = new ArrayMap<>();
            for (MediaRoute2Info sourceRoute : providerInfo.getRoutes()) {
                if (!sourceRoute.supportsSystemMediaRouting()) {
                    continue;
                }
                String id =
                        asUniqueSystemId(providerInfo.getUniqueId(), sourceRoute.getOriginalId());
                var newRouteBuilder = new MediaRoute2Info.Builder(id, sourceRoute);
                if ((sourceRoute.getSupportedRoutingTypes()
                                & MediaRoute2Info.FLAG_ROUTING_TYPE_SYSTEM_AUDIO)
                        != 0) {
                    newRouteBuilder.addFeature(FEATURE_LIVE_AUDIO);
                }
                if ((sourceRoute.getSupportedRoutingTypes()
                                & MediaRoute2Info.FLAG_ROUTING_TYPE_SYSTEM_VIDEO)
                        != 0) {
                    newRouteBuilder.addFeature(FEATURE_LIVE_VIDEO);
                }
                routesMap.put(id, newRouteBuilder.build());
                idMap.put(id, sourceRoute.getOriginalId());
            }
            return new ProviderProxyRecord(
                    serviceProxy,
                    Collections.unmodifiableMap(routesMap),
                    Collections.unmodifiableMap(idMap));
        }
    }

    private class SystemMediaSessionCallbackImpl implements SystemMediaSessionCallback {

        private final String mProviderId;
        private final long mRequestId;
        private final String mClientPackageName;
        // Accessed only on mHandler.
        @Nullable private SystemMediaSessionRecord mSessionRecord;

        private SystemMediaSessionCallbackImpl(
                String providerId, long requestId, String clientPackageName) {
            mProviderId = providerId;
            mRequestId = requestId;
            mClientPackageName = clientPackageName;
        }

        @Override
        public void onSessionUpdate(@NonNull RoutingSessionInfo sessionInfo) {
            mHandler.post(
                    () -> {
                        if (mSessionRecord != null) {
                            mSessionRecord.onSessionUpdate(sessionInfo);
                        } else {
                            SystemMediaSessionRecord systemMediaSessionRecord =
                                    new SystemMediaSessionRecord(mProviderId, sessionInfo);
                            RoutingSessionInfo translatedSession;
                            synchronized (mLock) {
                                mSessionRecord = systemMediaSessionRecord;
                                mSessionOriginalIdToSessionRecord.put(
                                        systemMediaSessionRecord.mOriginalId,
                                        systemMediaSessionRecord);
                                mPackageNameToSessionRecord.put(
                                        mClientPackageName, systemMediaSessionRecord);
                                mPendingSessionCreations.remove(mRequestId);
                                translatedSession = systemMediaSessionRecord.mTranslatedSessionInfo;
                            }
                            onSessionOverrideUpdated(translatedSession);
                        }
                    });
        }

        @Override
        public void onRequestFailed(long requestId, @Reason int reason) {
            mHandler.post(
                    () -> {
                        if (mSessionRecord != null) {
                            mSessionRecord.onRequestFailed(requestId, reason);
                        }
                        synchronized (mLock) {
                            mPendingSessionCreations.remove(mRequestId);
                        }
                        notifyRequestFailed(requestId, reason);
                    });
        }

        @Override
        public void onSessionReleased() {
            mHandler.post(
                    () -> {
                        if (mSessionRecord != null) {
                            mSessionRecord.onSessionReleased();
                        } else {
                            // Should never happen. The session hasn't yet been created.
                            throw new IllegalStateException();
                        }
                    });
        }
    }

    private class SystemMediaSessionRecord implements SystemMediaSessionCallback {

        private final String mProviderId;

        /**
         * The {@link RoutingSessionInfo#getOriginalId() original id} with which this session is
         * published.
         *
         * <p>Derived from the service routing session, using {@link #asUniqueSystemId}.
         */
        private final String mOriginalId;

        // @GuardedBy("SystemMediaRoute2Provider2.this.mLock")
        @NonNull private RoutingSessionInfo mSourceSessionInfo;

        /**
         * The same as {@link #mSourceSessionInfo}, except ids are {@link #asUniqueSystemId system
         * provider ids}.
         */
        // @GuardedBy("SystemMediaRoute2Provider2.this.mLock")
        @NonNull private RoutingSessionInfo mTranslatedSessionInfo;

        SystemMediaSessionRecord(
                @NonNull String providerId, @NonNull RoutingSessionInfo sessionInfo) {
            mProviderId = providerId;
            mSourceSessionInfo = sessionInfo;
            mOriginalId =
                    asUniqueSystemId(sessionInfo.getProviderId(), sessionInfo.getOriginalId());
            mTranslatedSessionInfo = asSystemProviderSession(sessionInfo);
        }

        // @GuardedBy("SystemMediaRoute2Provider2.this.mLock")

        /** Returns the session's original id, as published by the service. */
        public String getServiceSessionId() {
            return mSourceSessionInfo.getOriginalId();
        }

        @Override
        public void onSessionUpdate(@NonNull RoutingSessionInfo sessionInfo) {
            RoutingSessionInfo translatedSessionInfo = asSystemProviderSession(sessionInfo);
            synchronized (mLock) {
                mSourceSessionInfo = sessionInfo;
                mTranslatedSessionInfo = translatedSessionInfo;
            }
            onSessionOverrideUpdated(translatedSessionInfo);
        }

        @Override
        public void onRequestFailed(long requestId, @Reason int reason) {
            notifyRequestFailed(requestId, reason);
        }

        @Override
        public void onSessionReleased() {
            synchronized (mLock) {
                removeSelfFromSessionMaps();
            }
            notifyGlobalSessionInfoUpdated();
        }

        // @GuardedBy("SystemMediaRoute2Provider2.this.mLock")
        @Nullable
        public ProviderProxyRecord getProxyRecord() {
            ProviderProxyRecord provider = mProxyRecords.get(mProviderId);
            if (provider == null) {
                // Unexpected condition where the proxy is no longer available while there's an
                // ongoing session. Could happen due to a crash in the provider process.
                removeSelfFromSessionMaps();
            }
            return provider;
        }

        // @GuardedBy("SystemMediaRoute2Provider2.this.mLock")
        private void removeSelfFromSessionMaps() {
            mSessionOriginalIdToSessionRecord.remove(mOriginalId);
            mPackageNameToSessionRecord.remove(mSourceSessionInfo.getClientPackageName());
        }

        private RoutingSessionInfo asSystemProviderSession(RoutingSessionInfo session) {
            var builder =
                    new RoutingSessionInfo.Builder(session, mOriginalId)
                            .setProviderId(mUniqueId)
                            .setSystemSession(true)
                            .clearSelectedRoutes()
                            .clearSelectableRoutes()
                            .clearDeselectableRoutes()
                            .clearTransferableRoutes()
                            .setReleaseType(RELEASE_TYPE_CASTING);
            session.getSelectedRoutes().stream()
                    .map(it -> uniqueIdAsSystemRouteId(session.getProviderId(), it))
                    .forEach(builder::addSelectedRoute);
            session.getSelectableRoutes().stream()
                    .map(it -> uniqueIdAsSystemRouteId(session.getProviderId(), it))
                    .forEach(builder::addSelectableRoute);
            session.getDeselectableRoutes().stream()
                    .map(it -> uniqueIdAsSystemRouteId(session.getProviderId(), it))
                    .forEach(builder::addDeselectableRoute);
            session.getTransferableRoutes().stream()
                    .map(it -> uniqueIdAsSystemRouteId(session.getProviderId(), it))
                    .forEach(builder::addTransferableRoute);
            return builder.build();
        }
    }
}
