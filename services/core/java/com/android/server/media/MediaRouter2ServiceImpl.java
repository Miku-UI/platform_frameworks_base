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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR;
import static android.media.MediaRouter2.SCANNING_STATE_NOT_SCANNING;
import static android.media.MediaRouter2.SCANNING_STATE_SCANNING_FULL;
import static android.media.MediaRouter2.SCANNING_STATE_WHILE_INTERACTIVE;
import static android.media.MediaRouter2Utils.getOriginalId;
import static android.media.MediaRouter2Utils.getProviderId;
import static android.media.RouteListingPreference.Item.FLAG_SUGGESTED;
import static android.media.RoutingChangeInfo.SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP;
import static android.media.RoutingChangeInfo.SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER;
import static android.media.RoutingChangeInfo.SUGGESTION_PROVIDER_RLP;
import static android.media.RoutingChangeInfo.SuggestionProviderFlags;

import static com.android.internal.util.function.pooled.PooledLambda.obtainMessage;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_CREATE_SESSION;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_DESELECT_ROUTE;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_RELEASE_SESSION;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_SELECT_ROUTE;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_TRANSFER_TO_ROUTE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_ROUTE_ID;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_SESSION_ID;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_MANAGER_RECORD_NOT_FOUND;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_PERMISSION_DENIED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.compat.CompatChanges;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.PermissionChecker;
import android.content.pm.PackageManager;
import android.media.AppId;
import android.media.AudioManager;
import android.media.IMediaRouter2;
import android.media.IMediaRouter2Manager;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderInfo;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2.ScanningState;
import android.media.MediaRouter2Manager;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;
import android.media.SuggestedDeviceInfo;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.function.pooled.PooledLambda;
import com.android.media.flags.Flags;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.statusbar.StatusBarManagerInternal;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Implements features related to {@link android.media.MediaRouter2} and
 * {@link android.media.MediaRouter2Manager}.
 */
class MediaRouter2ServiceImpl {
    private static final String TAG = "MR2ServiceImpl";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // TODO: (In Android S or later) if we add callback methods for generic failures
    //       in MediaRouter2, remove this constant and replace the usages with the real request IDs.
    private static final long DUMMY_REQUEST_ID = -1;

    private static final int REQUIRED_PACKAGE_IMPORTANCE_FOR_SCANNING = IMPORTANCE_FOREGROUND;

    /**
     * Contains the list of bluetooth permissions that are required to do system routing.
     *
     * <p>Alternatively, apps that hold {@link android.Manifest.permission#MODIFY_AUDIO_ROUTING} are
     * also allowed to do system routing.
     */
    private static final String[] BLUETOOTH_PERMISSIONS_FOR_SYSTEM_ROUTING =
            new String[] {
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
            };

    private static final AtomicReference<MediaRouter2ServiceImpl> sInstance =
            new AtomicReference<>();

    private final Context mContext;
    private final Looper mLooper;
    private final UserManagerInternal mUserManagerInternal;
    private final Object mLock = new Object();
    private final AppOpsManager mAppOpsManager;
    private final StatusBarManagerInternal mStatusBarManagerInternal;
    final AtomicInteger mNextRouterOrManagerId = new AtomicInteger(1);
    final ActivityManager mActivityManager;
    final PowerManager mPowerManager;

    @GuardedBy("mLock")
    private final SparseArray<UserRecord> mUserRecords = new SparseArray<>();
    @GuardedBy("mLock")
    private final ArrayMap<IBinder, RouterRecord> mAllRouterRecords = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<IBinder, ManagerRecord> mAllManagerRecords = new ArrayMap<>();

    @GuardedBy("mLock")
    private int mCurrentActiveUserId = -1;

    @GuardedBy("mLock")
    private static final MediaRouterMetricLogger mMediaRouterMetricLogger =
            new MediaRouterMetricLogger();

    private final ActivityManager.OnUidImportanceListener mOnUidImportanceListener =
            (uid, importance) -> {
                synchronized (mLock) {
                    final int count = mUserRecords.size();
                    for (int i = 0; i < count; i++) {
                        mUserRecords.valueAt(i).mHandler.maybeUpdateDiscoveryPreferenceForUid(uid);
                    }
                }
            };

    private final BroadcastReceiver mScreenOnOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                final int count = mUserRecords.size();
                for (int i = 0; i < count; i++) {
                    UserHandler userHandler = mUserRecords.valueAt(i).mHandler;
                    userHandler.sendMessage(PooledLambda.obtainMessage(
                            UserHandler::updateDiscoveryPreferenceOnHandler, userHandler));
                }
            }
        }
    };

    private final AppOpsManager.OnOpChangedListener mOnOpChangedListener =
            new AppOpsManager.OnOpChangedListener() {
                @Override
                public void onOpChanged(String op, String packageName) {
                    // Do nothing.
                }

                @Override
                public void onOpChanged(
                        @NonNull String op, @NonNull String packageName, int userId) {
                    if (!TextUtils.equals(op, AppOpsManager.OPSTR_MEDIA_ROUTING_CONTROL)) {
                        return;
                    }
                    synchronized (mLock) {
                        revokeManagerRecordAccessIfNeededLocked(packageName, userId);
                    }
                }
            };

    @RequiresPermission(
            allOf = {
                Manifest.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS,
                Manifest.permission.WATCH_APPOPS
            })
    /* package */ MediaRouter2ServiceImpl(@NonNull Context context, @NonNull Looper looper) {
        mContext = context;
        mLooper = looper;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mActivityManager.addOnUidImportanceListener(mOnUidImportanceListener,
                REQUIRED_PACKAGE_IMPORTANCE_FOR_SCANNING);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mStatusBarManagerInternal = LocalServices.getService(StatusBarManagerInternal.class);

        IntentFilter screenOnOffIntentFilter = new IntentFilter();
        screenOnOffIntentFilter.addAction(ACTION_SCREEN_ON);
        screenOnOffIntentFilter.addAction(ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenOnOffReceiver, screenOnOffIntentFilter);

        // Passing null package name to listen to all events.
        mAppOpsManager.startWatchingMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                /* packageName */ null,
                mOnOpChangedListener);

        mContext.getPackageManager().addOnPermissionsChangeListener(this::onPermissionsChanged);
    }

    /**
     * Called when there's a change in the permissions of an app.
     *
     * @param uid The uid of the app whose permissions changed.
     */
    private void onPermissionsChanged(int uid) {
        synchronized (mLock) {
            Optional<RouterRecord> affectedRouter =
                    mAllRouterRecords.values().stream().filter(it -> it.mUid == uid).findFirst();
            if (affectedRouter.isPresent()) {
                affectedRouter.get().maybeUpdateSystemRoutingPermissionLocked();
            }
        }
    }

    /**
     * Conditionally handles a volume key press, and returns true if handled.
     *
     * <p>A volume key press will only be handled if a routing session subject to volume key presses
     * exists.
     *
     * @param callerLogTag A tag to include in the log line indicating that the event is handled.
     * @param direction One of {@link AudioManager#ADJUST_LOWER}, {@link AudioManager#ADJUST_RAISE},
     *     or {@link AudioManager#ADJUST_SAME}. If the direction is not one of these, the key press
     *     will not be handled.
     * @param suggestedStream The suggested stream to adjust. If the stream is not {@link
     *     AudioManager#USE_DEFAULT_STREAM_TYPE} or {@link AudioManager#STREAM_MUSIC}, the key press
     *     will not be handled.
     * @return Whether the key press was handled.
     * @see SystemMediaRoute2Provider2#maybeHandleVolumeKeyEventForSystemMediaSession
     */

    /* package */ static boolean maybeHandleVolumeKeyEvent(
            String callerLogTag, int direction, int suggestedStream) {
        var service = Flags.enableMirroringInMediaRouter2() ? sInstance.get() : null;
        var isRelevantStream =
                suggestedStream == AudioManager.USE_DEFAULT_STREAM_TYPE
                        || suggestedStream == AudioManager.STREAM_MUSIC;
        if (service == null || !isRelevantStream) {
            return false;
        }
        var handled = service.maybeHandleVolumeKeyEventInternal(direction);
        if (handled) {
            Log.i(
                    TAG,
                    "Volume key press handled by the routing framework. Caller: " + callerLogTag);
        }
        return handled;
    }

    // Start of methods that implement MediaRouter2 operations.

    @NonNull
    public List<MediaRoute2Info> getSystemRoutes(@NonNull String callerPackageName,
            boolean isProxyRouter) {
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();

        boolean hasSystemRoutingPermissions;
        if (!isProxyRouter) {
            hasSystemRoutingPermissions = checkCallerHasSystemRoutingPermissions(pid, uid);
        } else {
            // Request from ProxyRouter.
            hasSystemRoutingPermissions =
                    checkCallerHasPrivilegedRoutingPermissions(pid, uid, callerPackageName);
        }

        final long token = Binder.clearCallingIdentity();
        try {
            Collection<MediaRoute2Info> systemRoutes;
            synchronized (mLock) {
                UserRecord userRecord = getOrCreateUserRecordLocked(userId);
                if (hasSystemRoutingPermissions) {
                    MediaRoute2ProviderInfo providerInfo =
                            userRecord.mHandler.getSystemProvider().getProviderInfo();
                    if (providerInfo != null) {
                        systemRoutes = providerInfo.getRoutes();
                    } else {
                        systemRoutes = Collections.emptyList();
                        Slog.e(
                                TAG,
                                "Returning empty system routes list because "
                                    + "system provider has null providerInfo.");
                    }
                } else {
                    systemRoutes = new ArrayList<>();
                    systemRoutes.add(
                            userRecord.mHandler.getSystemProvider().getDefaultRoute());
                }
            }
            return new ArrayList<>(systemRoutes);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    public boolean showMediaOutputSwitcherWithRouter2(@NonNull String packageName,
            @Nullable MediaSession.Token sessionToken) {
        UserHandle userHandle = Binder.getCallingUserHandle();
        final long token = Binder.clearCallingIdentity();
        try {
            return showOutputSwitcher(packageName, userHandle, sessionToken);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void registerRouter2(@NonNull IMediaRouter2 router, @NonNull String packageName) {
        Objects.requireNonNull(router, "router must not be null");
        if (TextUtils.isEmpty(packageName)) {
            throw new IllegalArgumentException("packageName must not be empty");
        }

        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
        final boolean hasConfigureWifiDisplayPermission =
                mContext.checkCallingOrSelfPermission(Manifest.permission.CONFIGURE_WIFI_DISPLAY)
                        == PackageManager.PERMISSION_GRANTED;
        final boolean hasModifyAudioRoutingPermission =
                checkCallerHasModifyAudioRoutingPermission(pid, uid);
        boolean hasMediaContentControlPermission = checkMediaContentControlPermission(uid, pid);
        boolean hasMediaRoutingControlPermission =
                checkMediaRoutingControlPermission(uid, pid, packageName);

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                registerRouter2Locked(
                        router,
                        uid,
                        pid,
                        packageName,
                        userId,
                        hasConfigureWifiDisplayPermission,
                        hasModifyAudioRoutingPermission,
                        hasMediaContentControlPermission,
                        hasMediaRoutingControlPermission);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void unregisterRouter2(@NonNull IMediaRouter2 router) {
        Objects.requireNonNull(router, "router must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                unregisterRouter2Locked(router, false);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @RequiresPermission(
            anyOf = {
                Manifest.permission.MEDIA_ROUTING_CONTROL,
                Manifest.permission.MEDIA_CONTENT_CONTROL
            },
            conditional = true)
    public void updateScanningState(
            @NonNull IMediaRouter2 router, @ScanningState int scanningState) {
        Objects.requireNonNull(router, "router must not be null");
        validateScanningStateValue(scanningState);

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                updateScanningStateLocked(router, scanningState);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setDiscoveryRequestWithRouter2(
            @NonNull IMediaRouter2 router, @NonNull RouteDiscoveryPreference preference) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(preference, "preference must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                RouterRecord routerRecord = mAllRouterRecords.get(router.asBinder());
                if (routerRecord == null) {
                    Slog.w(TAG, "Ignoring updating discoveryRequest of null routerRecord.");
                    return;
                }
                setDiscoveryRequestWithRouter2Locked(routerRecord, preference);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setRouteListingPreference(
            @NonNull IMediaRouter2 router,
            @Nullable RouteListingPreference routeListingPreference) {
        ComponentName linkedItemLandingComponent =
                routeListingPreference != null
                        ? routeListingPreference.getLinkedItemComponentName()
                        : null;
        if (linkedItemLandingComponent != null) {
            int callingUid = Binder.getCallingUid();
            MediaServerUtils.enforcePackageName(
                    mContext, linkedItemLandingComponent.getPackageName(), callingUid);
            if (!MediaServerUtils.isValidActivityComponentName(
                    mContext,
                    linkedItemLandingComponent,
                    RouteListingPreference.ACTION_TRANSFER_MEDIA,
                    Binder.getCallingUserHandle())) {
                throw new IllegalArgumentException(
                        "Unable to resolve "
                                + linkedItemLandingComponent
                                + " to a valid activity for "
                                + RouteListingPreference.ACTION_TRANSFER_MEDIA);
            }
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                RouterRecord routerRecord = mAllRouterRecords.get(router.asBinder());
                if (routerRecord == null) {
                    Slog.w(TAG, "Ignoring updating route listing of null routerRecord.");
                    return;
                }
                setRouteListingPreferenceLocked(routerRecord, routeListingPreference);
                mMediaRouterMetricLogger.notifyRouteListingPreferenceChanged(
                        routerRecord.mUid, routeListingPreference);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setRouteVolumeWithRouter2(
            @NonNull IMediaRouter2 router, @NonNull MediaRoute2Info route, int volume) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(route, "route must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                setRouteVolumeWithRouter2Locked(router, route, volume);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void requestCreateSessionWithRouter2(
            @NonNull IMediaRouter2 router,
            int requestId,
            long managerRequestId,
            @NonNull RoutingSessionInfo oldSession,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo,
            Bundle sessionHints) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(oldSession, "oldSession must not be null");
        Objects.requireNonNull(route, "route must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                requestCreateSessionWithRouter2Locked(
                        requestId,
                        managerRequestId,
                        router,
                        oldSession,
                        route,
                        routingChangeInfo,
                        sessionHints);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void selectRouteWithRouter2(
            @NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(route, "route must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                selectRouteWithRouter2Locked(router, uniqueSessionId, route, routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void deselectRouteWithRouter2(
            @NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(route, "route must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                deselectRouteWithRouter2Locked(router, uniqueSessionId, route, routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void transferToRouteWithRouter2(
            @NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(route, "route must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        UserHandle userHandle = Binder.getCallingUserHandle();
        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                transferToRouteWithRouter2Locked(
                        router, userHandle, uniqueSessionId, route, routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setSessionVolumeWithRouter2(@NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId, int volume) {
        Objects.requireNonNull(router, "router must not be null");
        Objects.requireNonNull(uniqueSessionId, "uniqueSessionId must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                setSessionVolumeWithRouter2Locked(router, uniqueSessionId, volume);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void releaseSessionWithRouter2(@NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId) {
        Objects.requireNonNull(router, "router must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                releaseSessionWithRouter2Locked(router, uniqueSessionId);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setDeviceSuggestionsWithRouter2(
            @NonNull IMediaRouter2 router,
            @Nullable List<SuggestedDeviceInfo> suggestedDeviceInfo) {
        Objects.requireNonNull(router, "router must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                setDeviceSuggestionsWithRouter2Locked(router, suggestedDeviceInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @NonNull
    public Map<String, List<SuggestedDeviceInfo>> getDeviceSuggestionsWithRouter2(
            @NonNull IMediaRouter2 router) {
        Objects.requireNonNull(router, "router must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                return getDeviceSuggestionsWithRouter2Locked(router);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // End of methods that implement MediaRouter2 operations.

    // Start of methods that implement MediaRouter2Manager operations.

    @NonNull
    public List<RoutingSessionInfo> getRemoteSessions(@NonNull IMediaRouter2Manager manager) {
        Objects.requireNonNull(manager, "manager must not be null");
        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                return getRemoteSessionsLocked(manager);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @NonNull
    public List<AppId> getSystemSessionOverridesAppIds(@NonNull IMediaRouter2Manager manager) {
        Objects.requireNonNull(manager, "manager must not be null");
        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                return getSystemSessionOverridesAppIdsLocked(manager);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @RequiresPermission(Manifest.permission.MEDIA_CONTENT_CONTROL)
    public void registerManager(@NonNull IMediaRouter2Manager manager,
            @NonNull String callerPackageName) {
        Objects.requireNonNull(manager, "manager must not be null");
        if (TextUtils.isEmpty(callerPackageName)) {
            throw new IllegalArgumentException("callerPackageName must not be empty");
        }

        final int callerUid = Binder.getCallingUid();
        final int callerPid = Binder.getCallingPid();
        final UserHandle callerUser = Binder.getCallingUserHandle();

        enforcePrivilegedRoutingPermissions(callerUid, callerPid, callerPackageName);

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                registerManagerLocked(
                        manager,
                        callerUid,
                        callerPid,
                        callerPackageName,
                        /* targetPackageName */ null,
                        /* targetUid= */ Process.INVALID_UID,
                        callerUser);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @RequiresPermission(
            anyOf = {
                Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.MEDIA_ROUTING_CONTROL
            })
    public void registerProxyRouter(
            @NonNull IMediaRouter2Manager manager,
            @NonNull String callerPackageName,
            @NonNull String targetPackageName,
            @NonNull UserHandle targetUser) {
        Objects.requireNonNull(manager, "manager must not be null");
        Objects.requireNonNull(targetUser, "targetUser must not be null");

        if (TextUtils.isEmpty(targetPackageName)) {
            throw new IllegalArgumentException("targetPackageName must not be empty");
        }

        int callerUid = Binder.getCallingUid();
        int callerPid = Binder.getCallingPid();
        final long token = Binder.clearCallingIdentity();

        try {
            enforcePrivilegedRoutingPermissions(callerUid, callerPid, callerPackageName);
            enforceCrossUserPermissions(callerUid, callerPid, targetUser);
            if (!verifyPackageExistsForUser(targetPackageName, targetUser)) {
                throw new IllegalArgumentException(
                        "targetPackageName does not exist: " + targetPackageName);
            }

            synchronized (mLock) {
                registerManagerLocked(
                        manager,
                        callerUid,
                        callerPid,
                        callerPackageName,
                        targetPackageName,
                        getUidForPackage(targetPackageName, targetUser),
                        targetUser);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void unregisterManager(@NonNull IMediaRouter2Manager manager) {
        Objects.requireNonNull(manager, "manager must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                unregisterManagerLocked(manager, false);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void updateScanningState(
            @NonNull IMediaRouter2Manager manager, @ScanningState int scanningState) {
        Objects.requireNonNull(manager, "manager must not be null");
        validateScanningStateValue(scanningState);

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                updateScanningStateLocked(manager, scanningState);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setRouteVolumeWithManager(@NonNull IMediaRouter2Manager manager, int requestId,
            @NonNull MediaRoute2Info route, int volume) {
        Objects.requireNonNull(manager, "manager must not be null");
        Objects.requireNonNull(route, "route must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                setRouteVolumeWithManagerLocked(requestId, manager, route, volume);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void requestCreateSessionWithManager(
            @NonNull IMediaRouter2Manager manager,
            int requestId,
            @NonNull RoutingSessionInfo oldSession,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(manager, "manager must not be null");
        Objects.requireNonNull(oldSession, "oldSession must not be null");
        Objects.requireNonNull(route, "route must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                requestCreateSessionWithManagerLocked(
                        requestId, manager, oldSession, route, routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void selectRouteWithManager(
            @NonNull IMediaRouter2Manager manager,
            int requestId,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(manager, "manager must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }
        Objects.requireNonNull(route, "route must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                selectRouteWithManagerLocked(
                        requestId, manager, uniqueSessionId, route, routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void deselectRouteWithManager(
            @NonNull IMediaRouter2Manager manager,
            int requestId,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(manager, "manager must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }
        Objects.requireNonNull(route, "route must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                deselectRouteWithManagerLocked(
                        requestId, manager, uniqueSessionId, route, routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void transferToRouteWithManager(
            @NonNull IMediaRouter2Manager manager,
            int requestId,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String transferInitiatorPackageName,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        Objects.requireNonNull(manager, "manager must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }
        Objects.requireNonNull(route, "route must not be null");
        Objects.requireNonNull(transferInitiatorUserHandle);
        Objects.requireNonNull(transferInitiatorPackageName);

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                transferToRouteWithManagerLocked(
                        requestId,
                        manager,
                        uniqueSessionId,
                        route,
                        RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST,
                        transferInitiatorUserHandle,
                        transferInitiatorPackageName,
                        routingChangeInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setSessionVolumeWithManager(@NonNull IMediaRouter2Manager manager, int requestId,
            @NonNull String uniqueSessionId, int volume) {
        Objects.requireNonNull(manager, "manager must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                setSessionVolumeWithManagerLocked(requestId, manager, uniqueSessionId, volume);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void releaseSessionWithManager(@NonNull IMediaRouter2Manager manager, int requestId,
            @NonNull String uniqueSessionId) {
        Objects.requireNonNull(manager, "manager must not be null");
        if (TextUtils.isEmpty(uniqueSessionId)) {
            throw new IllegalArgumentException("uniqueSessionId must not be empty");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                releaseSessionWithManagerLocked(requestId, manager, uniqueSessionId);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void setDeviceSuggestionsWithManager(
            @NonNull IMediaRouter2Manager manager,
            @Nullable List<SuggestedDeviceInfo> suggestedDeviceInfo) {
        Objects.requireNonNull(manager, "manager must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                setDeviceSuggestionsWithManagerLocked(manager, suggestedDeviceInfo);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @NonNull
    public Map<String, List<SuggestedDeviceInfo>> getDeviceSuggestionsWithManager(
            @NonNull IMediaRouter2Manager manager) {
        Objects.requireNonNull(manager, "manager must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                return getDeviceSuggestionsWithManagerLocked(manager);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void onDeviceSuggestionRequestedWithManager(@NonNull IMediaRouter2Manager manager) {
        Objects.requireNonNull(manager, "manager must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                onDeviceSuggestionRequestedWithManagerLocked(manager);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    public boolean showMediaOutputSwitcherWithProxyRouter(
            @NonNull IMediaRouter2Manager proxyRouter, @Nullable MediaSession.Token sessionToken) {
        Objects.requireNonNull(proxyRouter, "Proxy router must not be null");

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                final IBinder binder = proxyRouter.asBinder();
                ManagerRecord proxyRouterRecord = mAllManagerRecords.get(binder);

                if (proxyRouterRecord.mTargetPackageName == null) {
                    throw new UnsupportedOperationException(
                            "Only proxy routers can show the Output Switcher.");
                }
                if (!Flags.enableRouteVisibilityControlApi()) {
                    sessionToken = null;
                }
                return showOutputSwitcher(
                        proxyRouterRecord.mTargetPackageName,
                        proxyRouterRecord.mUserRecord.mUserHandle,
                        sessionToken);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // End of methods that implement MediaRouter2Manager operations.

    // Start of methods that implements operations for both MediaRouter2 and MediaRouter2Manager.

    @Nullable
    public RoutingSessionInfo getSystemSessionInfo(
            @NonNull String callerPackageName,
            @Nullable String targetPackageName,
            boolean setDeviceRouteSelected) {
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        final int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();

        boolean hasSystemRoutingPermissions;
        if (targetPackageName == null) {
            hasSystemRoutingPermissions = checkCallerHasSystemRoutingPermissions(pid, uid);
        } else {
            // Request from ProxyRouter.
            hasSystemRoutingPermissions =
                    checkCallerHasPrivilegedRoutingPermissions(pid, uid, callerPackageName);
        }

        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                UserRecord userRecord = getOrCreateUserRecordLocked(userId);
                SystemMediaRoute2Provider systemProvider = userRecord.mHandler.getSystemProvider();
                if (hasSystemRoutingPermissions) {
                    if (!Flags.enableMirroringInMediaRouter2() && setDeviceRouteSelected) {
                        // Return a fake system session that shows the device route as selected and
                        // available bluetooth routes as transferable.
                        return systemProvider.generateDeviceRouteSelectedSessionInfo(
                                targetPackageName);
                    } else {
                        RoutingSessionInfo session =
                                systemProvider.getSessionForPackage(targetPackageName);
                        if (session != null) {
                            return session;
                        } else {
                            Slog.w(TAG, "System provider does not have any session info.");
                            return null;
                        }
                    }
                } else {
                    return new RoutingSessionInfo.Builder(systemProvider.getDefaultSessionInfo())
                            .setClientPackageName(targetPackageName)
                            .build();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private boolean checkCallerHasSystemRoutingPermissions(int pid, int uid) {
        return checkCallerHasModifyAudioRoutingPermission(pid, uid)
                || checkCallerHasBluetoothPermissions(pid, uid);
    }

    private boolean checkCallerHasPrivilegedRoutingPermissions(
            int pid, int uid, @NonNull String callerPackageName) {
        return checkMediaContentControlPermission(uid, pid)
                || checkMediaRoutingControlPermission(uid, pid, callerPackageName);
    }

    private boolean checkCallerHasModifyAudioRoutingPermission(int pid, int uid) {
        return mContext.checkPermission(Manifest.permission.MODIFY_AUDIO_ROUTING, pid, uid)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkCallerHasBluetoothPermissions(int pid, int uid) {
        boolean hasBluetoothRoutingPermission = true;
        for (String permission : BLUETOOTH_PERMISSIONS_FOR_SYSTEM_ROUTING) {
            hasBluetoothRoutingPermission &=
                    mContext.checkPermission(permission, pid, uid)
                            == PackageManager.PERMISSION_GRANTED;
        }
        return hasBluetoothRoutingPermission;
    }

    @RequiresPermission(
            anyOf = {
                Manifest.permission.MEDIA_ROUTING_CONTROL,
                Manifest.permission.MEDIA_CONTENT_CONTROL
            })
    private void enforcePrivilegedRoutingPermissions(
            int callerUid, int callerPid, @NonNull String callerPackageName) {
        if (checkMediaContentControlPermission(callerUid, callerPid)) {
            return;
        }

        if (!checkMediaRoutingControlPermission(callerUid, callerPid, callerPackageName)) {
            throw new SecurityException(
                    "Must hold MEDIA_CONTENT_CONTROL or MEDIA_ROUTING_CONTROL permissions.");
        }
    }

    private boolean checkMediaContentControlPermission(int callerUid, int callerPid) {
        return mContext.checkPermission(
                        Manifest.permission.MEDIA_CONTENT_CONTROL, callerPid, callerUid)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkMediaRoutingControlPermission(
            int callerUid, int callerPid, @NonNull String callerPackageName) {
        if (!Flags.enablePrivilegedRoutingForMediaRoutingControl()) {
            return false;
        }

        return PermissionChecker.checkPermissionForDataDelivery(
                        mContext,
                        Manifest.permission.MEDIA_ROUTING_CONTROL,
                        callerPid,
                        callerUid,
                        callerPackageName,
                        /* attributionTag */ null,
                        /* message */ "Checking permissions for registering manager in"
                                + " MediaRouter2ServiceImpl.")
                == PermissionChecker.PERMISSION_GRANTED;
    }

    @RequiresPermission(value = Manifest.permission.INTERACT_ACROSS_USERS)
    private boolean verifyPackageExistsForUser(
            @NonNull String clientPackageName, @NonNull UserHandle user) {
        try {
            PackageManager pm = mContext.getPackageManager();
            pm.getPackageInfoAsUser(
                    clientPackageName, PackageManager.PackageInfoFlags.of(0), user.getIdentifier());
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    @RequiresPermission(value = Manifest.permission.INTERACT_ACROSS_USERS)
    private int getUidForPackage(@NonNull String clientPackageName, @NonNull UserHandle user) {
        try {
            PackageManager pm = mContext.getPackageManager();
            return pm.getApplicationInfoAsUser(
                            clientPackageName, /* flags= */ 0, user.getIdentifier())
                    .uid;

        } catch (PackageManager.NameNotFoundException ex) {
            return Process.INVALID_UID;
        }
    }

    /**
     * Enforces the caller has {@link Manifest.permission#INTERACT_ACROSS_USERS_FULL} if the
     * caller's user is different from the target user.
     */
    private void enforceCrossUserPermissions(
            int callerUid, int callerPid, @NonNull UserHandle targetUser) {
        int callerUserId = UserHandle.getUserId(callerUid);

        if (targetUser.getIdentifier() != callerUserId) {
            mContext.enforcePermission(
                    Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    callerPid,
                    callerUid,
                    "Must hold INTERACT_ACROSS_USERS_FULL to control an app in a different"
                            + " userId.");
        }
    }

    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    private boolean showOutputSwitcher(
            @NonNull String packageName, @NonNull UserHandle userHandle,
            @Nullable MediaSession.Token sessionToken) {
        if (mActivityManager.getPackageImportance(packageName) > IMPORTANCE_FOREGROUND) {
            Slog.w(TAG, "showMediaOutputSwitcher only works when called from foreground");
            return false;
        }
        mStatusBarManagerInternal.showMediaOutputSwitcher(packageName, userHandle, sessionToken);
        return true;
    }

    // End of methods that implements operations for both MediaRouter2 and MediaRouter2Manager.

    public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + "MediaRouter2ServiceImpl");

        String indent = prefix + "  ";

        synchronized (mLock) {
            pw.println(indent + "mNextRouterOrManagerId=" + mNextRouterOrManagerId.get());
            pw.println(indent + "mCurrentActiveUserId=" + mCurrentActiveUserId);

            pw.println(indent + "UserRecords:");
            if (mUserRecords.size() > 0) {
                for (int i = 0; i < mUserRecords.size(); i++) {
                    mUserRecords.valueAt(i).dump(pw, indent + "  ");
                }
            } else {
                pw.println(indent + "  <no user records>");
            }
        }
    }

    private boolean maybeHandleVolumeKeyEventInternal(int direction) {
        synchronized (mLock) {
            // TODO: b/396399175 - Replace this for-loop with a singleton system provider when
            // multi-user support is properly implemented.
            for (int i = 0; i < mUserRecords.size(); i++) {
                var userRecord = mUserRecords.valueAt(i);
                var systemProvider = userRecord.mHandler.getSystemProvider();
                var handled =
                        systemProvider.maybeHandleVolumeKeyEventForSystemMediaSession(
                                DUMMY_REQUEST_ID, direction);
                if (handled) {
                    return true;
                }
            }
            return false;
        }
    }

    /* package */ void updateRunningUserAndProfiles(int newActiveUserId) {
        synchronized (mLock) {
            if (mCurrentActiveUserId != newActiveUserId) {
                Slog.i(TAG, TextUtils.formatSimple(
                        "switchUser | user: %d", newActiveUserId));

                mCurrentActiveUserId = newActiveUserId;
                // disposeUserIfNeededLocked might modify the collection, hence clone
                final var userRecords = mUserRecords.clone();
                for (int i = 0; i < userRecords.size(); i++) {
                    int userId = userRecords.keyAt(i);
                    UserRecord userRecord = userRecords.valueAt(i);
                    if (isUserActiveLocked(userId)) {
                        // userId corresponds to the active user, or one of its profiles. We
                        // ensure the associated structures are initialized.
                        userRecord.mHandler.sendMessage(
                                obtainMessage(UserHandler::start, userRecord.mHandler));
                    } else {
                        userRecord.mHandler.sendMessage(
                                obtainMessage(UserHandler::stop, userRecord.mHandler));
                        disposeUserIfNeededLocked(userRecord);
                    }
                }
            }
        }
    }

    void routerDied(@NonNull RouterRecord routerRecord) {
        synchronized (mLock) {
            unregisterRouter2Locked(routerRecord.mRouter, true);
        }
    }

    void managerDied(@NonNull ManagerRecord managerRecord) {
        synchronized (mLock) {
            unregisterManagerLocked(managerRecord.mManager, true);
        }
    }

    /**
     * Returns {@code true} if the given {@code userId} corresponds to the active user or a profile
     * of the active user, returns {@code false} otherwise.
     */
    @GuardedBy("mLock")
    private boolean isUserActiveLocked(int userId) {
        return mUserManagerInternal.getProfileParentId(userId) == mCurrentActiveUserId;
    }

    @GuardedBy("mLock")
    private void revokeManagerRecordAccessIfNeededLocked(@NonNull String packageName, int userId) {
        UserRecord userRecord = mUserRecords.get(userId);
        if (userRecord == null) {
            return;
        }

        List<ManagerRecord> managers =
                userRecord.mManagerRecords.stream()
                        .filter(r -> !r.mHasMediaContentControl)
                        .filter(r -> TextUtils.equals(r.mOwnerPackageName, packageName))
                        .collect(Collectors.toList());

        if (managers.isEmpty()) {
            return;
        }

        ManagerRecord record = managers.getFirst();

        // Uid and package name are shared across all manager records in the list.
        boolean isAppOpAllowed =
                mAppOpsManager.unsafeCheckOpNoThrow(
                                AppOpsManager.OPSTR_MEDIA_ROUTING_CONTROL,
                                record.mOwnerUid,
                                record.mOwnerPackageName)
                        == AppOpsManager.MODE_ALLOWED;

        if (isAppOpAllowed) {
            return;
        }

        for (ManagerRecord manager : managers) {
            boolean isRegularPermission =
                    mContext.checkPermission(
                                    Manifest.permission.MEDIA_ROUTING_CONTROL,
                                    manager.mOwnerPid,
                                    manager.mOwnerUid)
                            == PackageManager.PERMISSION_GRANTED;

            if (isRegularPermission) {
                // We should check the regular permission for all manager records, as different PIDs
                // might yield different permission results.
                continue;
            }

            Slog.w(TAG, "Revoking access for " + manager.getDebugString());
            unregisterManagerLocked(manager.mManager, /* died */ false);
            try {
                manager.mManager.invalidateInstance();
            } catch (RemoteException ex) {
                manager.logRemoteException("invalidateInstance", ex);
            }
        }
    }

    // Start of locked methods that are used by MediaRouter2.

    @GuardedBy("mLock")
    private void registerRouter2Locked(
            @NonNull IMediaRouter2 router,
            int uid,
            int pid,
            @NonNull String packageName,
            int userId,
            boolean hasConfigureWifiDisplayPermission,
            boolean hasModifyAudioRoutingPermission,
            boolean hasMediaContentControlPermission,
            boolean hasMediaRoutingControlPermission) {
        final IBinder binder = router.asBinder();
        if (mAllRouterRecords.get(binder) != null) {
            Slog.w(TAG, "registerRouter2Locked: Same router already exists. packageName="
                    + packageName);
            return;
        }

        UserRecord userRecord = getOrCreateUserRecordLocked(userId);
        RouterRecord routerRecord =
                new RouterRecord(
                        mContext,
                        userRecord,
                        router,
                        uid,
                        pid,
                        packageName,
                        hasConfigureWifiDisplayPermission,
                        hasModifyAudioRoutingPermission,
                        hasMediaContentControlPermission,
                        hasMediaRoutingControlPermission);
        try {
            binder.linkToDeath(routerRecord, 0);
        } catch (RemoteException ex) {
            throw new RuntimeException("MediaRouter2 died prematurely.", ex);
        }

        userRecord.addRouterRecord(routerRecord);
        mAllRouterRecords.put(binder, routerRecord);

        userRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::notifyRouterRegistered,
                        userRecord.mHandler, routerRecord));

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "registerRouter2 | package: %s, uid: %d, pid: %d, router id: %d,"
                                + " hasMediaRoutingControl: %b",
                        packageName,
                        uid,
                        pid,
                        routerRecord.mRouterId,
                        hasMediaRoutingControlPermission));
    }

    @GuardedBy("mLock")
    private void unregisterRouter2Locked(@NonNull IMediaRouter2 router, boolean died) {
        RouterRecord routerRecord = mAllRouterRecords.remove(router.asBinder());
        if (routerRecord == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Ignoring unregistering unknown router: %s, died: %b", router, died));
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "unregisterRouter2 | %s, died: %b", routerRecord.getDebugString(), died));

        UserRecord userRecord = routerRecord.mUserRecord;
        userRecord.removeRouterRecord(routerRecord);
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::notifyDiscoveryPreferenceChangedToManagers,
                        routerRecord.mUserRecord.mHandler,
                        routerRecord.mPackageName, null));
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::notifyRouteListingPreferenceChangeToManagers,
                        routerRecord.mUserRecord.mHandler,
                        routerRecord.mPackageName,
                        /* routeListingPreference= */ null));
        userRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::updateDiscoveryPreferenceOnHandler,
                        userRecord.mHandler));
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::notifyDeviceSuggestionsClearedOnHandler,
                        routerRecord.mUserRecord.mHandler,
                        routerRecord.mPackageName,
                        routerRecord.getDeviceSuggestionsLocked().keySet()));
        routerRecord.mDeviceSuggestions.clear();
        mMediaRouterMetricLogger.notifyRouterUnregistered(routerRecord.mUid);
        routerRecord.dispose();
        disposeUserIfNeededLocked(userRecord); // since router removed from user
    }

    @RequiresPermission(
            anyOf = {
                Manifest.permission.MEDIA_ROUTING_CONTROL,
                Manifest.permission.MEDIA_CONTENT_CONTROL
            },
            conditional = true)
    @GuardedBy("mLock")
    private void updateScanningStateLocked(
            @NonNull IMediaRouter2 router, @ScanningState int scanningState) {
        final IBinder binder = router.asBinder();
        RouterRecord routerRecord = mAllRouterRecords.get(binder);
        if (routerRecord == null) {
            Slog.w(TAG, "Router record not found. Ignoring updateScanningState call.");
            return;
        }

        boolean enableScanViaMediaContentControl =
                Flags.enableFullScanWithMediaContentControl()
                        && routerRecord.mHasMediaContentControlPermission;
        if (scanningState == SCANNING_STATE_SCANNING_FULL
                && !enableScanViaMediaContentControl
                && !routerRecord.mHasMediaRoutingControl) {
            throw new SecurityException("Screen off scan requires MEDIA_ROUTING_CONTROL");
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "updateScanningStateLocked | router: %d, packageName: %s, scanningState:"
                            + " %d",
                        routerRecord.mRouterId,
                        routerRecord.mPackageName,
                        getScanningStateString(scanningState)));

        routerRecord.updateScanningState(scanningState);
    }

    @GuardedBy("mLock")
    private void setDiscoveryRequestWithRouter2Locked(@NonNull RouterRecord routerRecord,
            @NonNull RouteDiscoveryPreference discoveryRequest) {
        if (routerRecord.mDiscoveryPreference.equals(discoveryRequest)) {
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "setDiscoveryRequestWithRouter2 | router: %s(id: %d), discovery request:"
                            + " %s",
                        routerRecord.mPackageName,
                        routerRecord.mRouterId,
                        discoveryRequest.toString()));

        routerRecord.mDiscoveryPreference = discoveryRequest;
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::notifyDiscoveryPreferenceChangedToManagers,
                        routerRecord.mUserRecord.mHandler,
                        routerRecord.mPackageName,
                        routerRecord.mDiscoveryPreference));
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::updateDiscoveryPreferenceOnHandler,
                        routerRecord.mUserRecord.mHandler));
    }

    @GuardedBy("mLock")
    private void setRouteListingPreferenceLocked(
            RouterRecord routerRecord, @Nullable RouteListingPreference routeListingPreference) {
        routerRecord.mRouteListingPreference = routeListingPreference;
        String routeListingAsString =
                routeListingPreference != null
                        ? routeListingPreference.getItems().stream()
                                .map(RouteListingPreference.Item::getRouteId)
                                .collect(Collectors.joining(","))
                        : null;

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "setRouteListingPreference | router: %s(id: %d), route listing preference:"
                            + " [%s]",
                        routerRecord.mPackageName, routerRecord.mRouterId, routeListingAsString));

        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::notifyRouteListingPreferenceChangeToManagers,
                        routerRecord.mUserRecord.mHandler,
                        routerRecord.mPackageName,
                        routeListingPreference));
    }

    @GuardedBy("mLock")
    private void setRouteVolumeWithRouter2Locked(@NonNull IMediaRouter2 router,
            @NonNull MediaRoute2Info route, int volume) {
        final IBinder binder = router.asBinder();
        RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord != null) {
            Slog.i(
                    TAG,
                    TextUtils.formatSimple(
                            "setRouteVolumeWithRouter2 | router: %s(id: %d), volume: %d",
                            routerRecord.mPackageName, routerRecord.mRouterId, volume));

            routerRecord.mUserRecord.mHandler.sendMessage(
                    obtainMessage(UserHandler::setRouteVolumeOnHandler,
                            routerRecord.mUserRecord.mHandler,
                            DUMMY_REQUEST_ID, route, volume));
        }
    }

    @GuardedBy("mLock")
    private void requestCreateSessionWithRouter2Locked(
            int requestId,
            long managerRequestId,
            @NonNull IMediaRouter2 router,
            @NonNull RoutingSessionInfo oldSession,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo,
            @Nullable Bundle sessionHints) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            mMediaRouterMetricLogger.logOperationFailure(
                    EVENT_TYPE_CREATE_SESSION,
                    MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND,
                    routingChangeInfo);
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "requestCreateSessionWithRouter2 | router: %s(id: %d), old session id: %s,"
                            + " new session's route id: %s, request id: %d",
                        routerRecord.mPackageName,
                        routerRecord.mRouterId,
                        oldSession.getId(),
                        route.getId(),
                        requestId));

        UserHandler userHandler = routerRecord.mUserRecord.mHandler;
        if (managerRequestId != MediaRoute2ProviderService.REQUEST_ID_NONE) {
            ManagerRecord manager = userHandler.findManagerWithId(toRequesterId(managerRequestId));
            if (manager == null || manager.mLastSessionCreationRequest == null) {
                Slog.w(TAG, "requestCreateSessionWithRouter2Locked: Ignoring unknown request.");
                mMediaRouterMetricLogger.logOperationFailure(
                        EVENT_TYPE_CREATE_SESSION,
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_MANAGER_RECORD_NOT_FOUND,
                        routingChangeInfo);
                routerRecord.notifySessionCreationFailed(requestId);
                return;
            }
            if (!TextUtils.equals(
                    manager.mLastSessionCreationRequest.mOldSession.getId(), oldSession.getId())) {
                Slog.w(
                        TAG,
                        "requestCreateSessionWithRouter2Locked: "
                                + "Ignoring unmatched routing session.");
                mMediaRouterMetricLogger.logOperationFailure(
                        EVENT_TYPE_CREATE_SESSION,
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_SESSION_ID,
                        routingChangeInfo);
                routerRecord.notifySessionCreationFailed(requestId);
                return;
            }
            if (!TextUtils.equals(manager.mLastSessionCreationRequest.mRoute.getId(),
                    route.getId())) {
                // When media router has no permission
                if (!routerRecord.hasSystemRoutingPermission()
                        && manager.mLastSessionCreationRequest.mRoute.isSystemRoute()
                        && route.isSystemRoute()) {
                    route = manager.mLastSessionCreationRequest.mRoute;
                } else {
                    Slog.w(
                            TAG,
                            "requestCreateSessionWithRouter2Locked: "
                                    + "Ignoring unmatched route.");
                    mMediaRouterMetricLogger.logOperationFailure(
                            EVENT_TYPE_CREATE_SESSION,
                            MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_ROUTE_ID,
                            routingChangeInfo);
                    routerRecord.notifySessionCreationFailed(requestId);
                    return;
                }
            }
            manager.mLastSessionCreationRequest = null;
        } else {
            String defaultRouteId = userHandler.getSystemProvider().getDefaultRoute().getId();
            if (route.isSystemRoute()
                    && !routerRecord.hasSystemRoutingPermission()
                    && !TextUtils.equals(route.getId(), defaultRouteId)) {
                Slog.w(TAG, "MODIFY_AUDIO_ROUTING permission is required to transfer to" + route);
                mMediaRouterMetricLogger.logOperationFailure(
                        EVENT_TYPE_CREATE_SESSION,
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_PERMISSION_DENIED,
                        routingChangeInfo);
                routerRecord.notifySessionCreationFailed(requestId);
                return;
            }
        }

        long uniqueRequestId = toUniqueRequestId(routerRecord.mRouterId, requestId);
        @SuggestionProviderFlags
        int suggestionProviderFlags = getSuggestionProviderFlags(routerRecord, route);
        RoutingChangeInfo updatedInfo =
                new RoutingChangeInfo(
                        routingChangeInfo.getEntryPoint(),
                        routingChangeInfo.isSuggested(),
                        suggestionProviderFlags);
        mMediaRouterMetricLogger.addRequestInfo(
                uniqueRequestId, EVENT_TYPE_CREATE_SESSION, updatedInfo);
        mMediaRouterMetricLogger.notifyRoutingChangeRequested(uniqueRequestId, updatedInfo);
        userHandler.sendMessage(
                obtainMessage(
                        UserHandler::requestCreateSessionWithRouter2OnHandler,
                        userHandler,
                        uniqueRequestId,
                        managerRequestId,
                        routerRecord,
                        oldSession,
                        route,
                        sessionHints));
    }

    @GuardedBy("mLock")
    private void selectRouteWithRouter2Locked(
            @NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            mMediaRouterMetricLogger.logOperationFailure(
                    EVENT_TYPE_SELECT_ROUTE,
                    MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND,
                    routingChangeInfo);
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "selectRouteWithRouter2 | router: %s(id: %d), route: %s",
                        routerRecord.mPackageName, routerRecord.mRouterId, route.getId()));
        mMediaRouterMetricLogger.logOperationTriggered(
                EVENT_TYPE_SELECT_ROUTE,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                routingChangeInfo);

        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::selectRouteOnHandler,
                        routerRecord.mUserRecord.mHandler,
                        DUMMY_REQUEST_ID, routerRecord, uniqueSessionId, route));
    }

    @GuardedBy("mLock")
    private void deselectRouteWithRouter2Locked(
            @NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            mMediaRouterMetricLogger.logOperationFailure(
                    EVENT_TYPE_DESELECT_ROUTE,
                    MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND,
                    routingChangeInfo);
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "deselectRouteWithRouter2 | router: %s(id: %d), route: %s",
                        routerRecord.mPackageName, routerRecord.mRouterId, route.getId()));
        mMediaRouterMetricLogger.logOperationTriggered(
                EVENT_TYPE_DESELECT_ROUTE,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                routingChangeInfo);

        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::deselectRouteOnHandler,
                        routerRecord.mUserRecord.mHandler,
                        DUMMY_REQUEST_ID, routerRecord, uniqueSessionId, route));
    }

    @GuardedBy("mLock")
    private void transferToRouteWithRouter2Locked(
            @NonNull IMediaRouter2 router,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            mMediaRouterMetricLogger.logOperationFailure(
                    EVENT_TYPE_TRANSFER_TO_ROUTE,
                    MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND,
                    routingChangeInfo);
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "transferToRouteWithRouter2 | router: %s(id: %d), route: %s",
                        routerRecord.mPackageName, routerRecord.mRouterId, route.getId()));
        mMediaRouterMetricLogger.logOperationTriggered(
                EVENT_TYPE_TRANSFER_TO_ROUTE,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                routingChangeInfo);

        UserHandler userHandler = routerRecord.mUserRecord.mHandler;
        String defaultRouteId = userHandler.getSystemProvider().getDefaultRoute().getId();
        if (route.isSystemRoute()
                && !routerRecord.hasSystemRoutingPermission()
                && !TextUtils.equals(route.getId(), defaultRouteId)) {
            userHandler.sendMessage(
                    obtainMessage(
                            RouterRecord::notifySessionCreationFailed,
                            routerRecord,
                            toOriginalRequestId(DUMMY_REQUEST_ID)));
        } else {
            userHandler.sendMessage(
                    obtainMessage(
                            UserHandler::transferToRouteOnHandler,
                            userHandler,
                            DUMMY_REQUEST_ID,
                            transferInitiatorUserHandle,
                            routerRecord.mPackageName,
                            routerRecord,
                            uniqueSessionId,
                            route,
                            RoutingSessionInfo.TRANSFER_REASON_APP,
                            routingChangeInfo));
        }
    }

    @GuardedBy("mLock")
    private void setSessionVolumeWithRouter2Locked(@NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId, int volume) {
        final IBinder binder = router.asBinder();
        RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "setSessionVolumeWithRouter2 | router: %s(id: %d), session: %s, volume: %d",
                        routerRecord.mPackageName,
                        routerRecord.mRouterId,
                        uniqueSessionId,
                        volume));

        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::setSessionVolumeOnHandler,
                        routerRecord.mUserRecord.mHandler,
                        DUMMY_REQUEST_ID, uniqueSessionId, volume));
    }

    @GuardedBy("mLock")
    private void releaseSessionWithRouter2Locked(@NonNull IMediaRouter2 router,
            @NonNull String uniqueSessionId) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            mMediaRouterMetricLogger.logOperationFailure(
                    EVENT_TYPE_RELEASE_SESSION,
                    MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND,
                    /* routingChangeInfo= */ null);
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "releaseSessionWithRouter2 | router: %s(id: %d), session: %s",
                        routerRecord.mPackageName, routerRecord.mRouterId, uniqueSessionId));

        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::releaseSessionOnHandler,
                        routerRecord.mUserRecord.mHandler,
                        DUMMY_REQUEST_ID, routerRecord, uniqueSessionId));
    }

    @GuardedBy("mLock")
    private void setDeviceSuggestionsWithRouter2Locked(
            @NonNull IMediaRouter2 router,
            @Nullable List<SuggestedDeviceInfo> suggestedDeviceInfo) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Ignoring set device suggestion for unknown router: %s", router));
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "setDeviceSuggestions | router: %d suggestion: %d",
                        routerRecord.mPackageName, suggestedDeviceInfo));

        routerRecord.putDeviceSuggestionsLocked(routerRecord.mPackageName, suggestedDeviceInfo);
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::notifyDeviceSuggestionsUpdatedOnHandler,
                        routerRecord.mUserRecord.mHandler,
                        routerRecord.mPackageName,
                        routerRecord.mPackageName,
                        suggestedDeviceInfo));
        mMediaRouterMetricLogger.notifyDeviceSuggestionsUpdated(
                /* targetPackageUid= */ routerRecord.mUid,
                /* suggestingPackageUid= */ routerRecord.mUid);
    }

    @GuardedBy("mLock")
    @NonNull
    private Map<String, List<SuggestedDeviceInfo>> getDeviceSuggestionsWithRouter2Locked(
            @NonNull IMediaRouter2 router) {
        final IBinder binder = router.asBinder();
        final RouterRecord routerRecord = mAllRouterRecords.get(binder);

        if (routerRecord == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Attempted to get device suggestion for unknown router: %s", router));
            return Collections.emptyMap();
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "getDeviceSuggestions | router: %d", routerRecord.mPackageName));

        return routerRecord.getDeviceSuggestionsLocked();
    }

    // End of locked methods that are used by MediaRouter2.

    // Start of locked methods that are used by MediaRouter2Manager.

    @GuardedBy("mLock")
    private List<RoutingSessionInfo> getRemoteSessionsLocked(
            @NonNull IMediaRouter2Manager manager) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            Slog.w(TAG, "getRemoteSessionLocked: Ignoring unknown manager");
            return Collections.emptyList();
        }

        List<RoutingSessionInfo> sessionInfos = new ArrayList<>();
        for (MediaRoute2Provider provider : managerRecord.mUserRecord.mHandler.mRouteProviders) {
            if (!provider.mIsSystemRouteProvider) {
                sessionInfos.addAll(provider.getSessionInfos());
            }
        }
        return sessionInfos;
    }

    @GuardedBy("mLock")
    public List<AppId> getSystemSessionOverridesAppIdsLocked(
            @NonNull IMediaRouter2Manager manager) {
        IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);
        if (managerRecord == null) {
            Slog.w(TAG, "getSystemSessionOverridesAppIdsLocked: Ignoring unknown manager");
            return Collections.emptyList();
        }
        return managerRecord.mUserRecord.getAppsWithSystemOverridesLocked();
    }

    @RequiresPermission(Manifest.permission.MEDIA_CONTENT_CONTROL)
    @GuardedBy("mLock")
    private void registerManagerLocked(
            @NonNull IMediaRouter2Manager manager,
            int callerUid,
            int callerPid,
            @NonNull String callerPackageName,
            @Nullable String targetPackageName,
            int targetUid,
            @NonNull UserHandle targetUser) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord != null) {
            Slog.w(TAG, "registerManagerLocked: Same manager already exists. callerPackageName="
                    + callerPackageName);
            return;
        }

        boolean hasMediaRoutingControl =
                checkMediaRoutingControlPermission(callerUid, callerPid, callerPackageName);

        boolean hasMediaContentControl = checkMediaContentControlPermission(callerUid, callerPid);

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "registerManager | callerUid: %d, callerPid: %d, callerPackage: %s,"
                            + " targetPackageName: %s, targetUserId: %d, hasMediaRoutingControl:"
                            + " %b",
                        callerUid,
                        callerPid,
                        callerPackageName,
                        targetPackageName,
                        targetUser,
                        hasMediaRoutingControl));

        UserRecord userRecord = getOrCreateUserRecordLocked(targetUser.getIdentifier());

        managerRecord =
                new ManagerRecord(
                        userRecord,
                        manager,
                        callerUid,
                        callerPid,
                        callerPackageName,
                        targetPackageName,
                        targetUid,
                        hasMediaRoutingControl,
                        hasMediaContentControl);
        try {
            binder.linkToDeath(managerRecord, 0);
        } catch (RemoteException ex) {
            throw new RuntimeException("Media router manager died prematurely.", ex);
        }

        userRecord.mManagerRecords.add(managerRecord);
        mAllManagerRecords.put(binder, managerRecord);

        // Note: Features should be sent first before the routes. If not, the
        // RouteCallback#onRoutesAdded() for system MR2 will never be called with initial routes
        // due to the lack of features.
        for (RouterRecord routerRecord : userRecord.mRouterRecords) {
            // Send route listing preferences before discovery preferences and routes to avoid an
            // inconsistent state where there are routes to show, but the manager thinks
            // the app has not expressed a preference for listing.
            userRecord.mHandler.sendMessage(
                    obtainMessage(
                            UserHandler::notifyRouteListingPreferenceChangeToManagers,
                            routerRecord.mUserRecord.mHandler,
                            routerRecord.mPackageName,
                            routerRecord.mRouteListingPreference));
            // TODO: UserRecord <-> routerRecord, why do they reference each other?
            // How about removing mUserRecord from routerRecord?
            routerRecord.mUserRecord.mHandler.sendMessage(
                    obtainMessage(
                            ManagerRecord::notifyDiscoveryPreferenceChanged,
                            managerRecord,
                            routerRecord.mPackageName,
                            routerRecord.mDiscoveryPreference));
        }

        userRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::dispatchRoutesToManagerOnHandler,
                        userRecord.mHandler,
                        managerRecord));
    }

    @GuardedBy("mLock")
    private void unregisterManagerLocked(@NonNull IMediaRouter2Manager manager, boolean died) {
        ManagerRecord managerRecord = mAllManagerRecords.remove(manager.asBinder());
        if (managerRecord == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Ignoring unregistering unknown manager: %s, died: %b", manager, died));
            return;
        }
        UserRecord userRecord = managerRecord.mUserRecord;

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "unregisterManager | %s, user: %d, died: %b",
                        managerRecord.getDebugString(),
                        userRecord.mUserHandle.getIdentifier(),
                        died));

        userRecord.mManagerRecords.remove(managerRecord);
        managerRecord.dispose();
        disposeUserIfNeededLocked(userRecord); // since manager removed from user
    }

    @GuardedBy("mLock")
    private void updateScanningStateLocked(
            @NonNull IMediaRouter2Manager manager, @ScanningState int scanningState) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);
        if (managerRecord == null) {
            Slog.w(TAG, "Manager record not found. Ignoring updateScanningState call.");
            return;
        }

        boolean enableScanViaMediaContentControl =
                Flags.enableFullScanWithMediaContentControl()
                        && managerRecord.mHasMediaContentControl;
        if (!managerRecord.mHasMediaRoutingControl
                && !enableScanViaMediaContentControl
                && scanningState == SCANNING_STATE_SCANNING_FULL) {
            throw new SecurityException("Screen off scan requires MEDIA_ROUTING_CONTROL");
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "updateScanningState | manager: %d, ownerPackageName: %s,"
                            + " targetPackageName: %s, scanningState: %d",
                        managerRecord.mManagerId,
                        managerRecord.mOwnerPackageName,
                        managerRecord.mTargetPackageName,
                        getScanningStateString(scanningState)));

        managerRecord.updateScanningState(scanningState);
    }

    @GuardedBy("mLock")
    private void setRouteVolumeWithManagerLocked(int requestId,
            @NonNull IMediaRouter2Manager manager,
            @NonNull MediaRoute2Info route, int volume) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "setRouteVolumeWithManager | manager: %d, route: %s, volume: %d",
                managerRecord.mManagerId, route.getId(), volume));

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::setRouteVolumeOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        uniqueRequestId, route, volume));
    }

    @GuardedBy("mLock")
    private void requestCreateSessionWithManagerLocked(
            int requestId,
            @NonNull IMediaRouter2Manager manager,
            @NonNull RoutingSessionInfo oldSession,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        ManagerRecord managerRecord = mAllManagerRecords.get(manager.asBinder());
        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "requestCreateSessionWithManager | manager: %d, route: %s",
                managerRecord.mManagerId, route.getId()));

        String packageName = oldSession.getClientPackageName();

        RouterRecord routerRecord = managerRecord.mUserRecord.findRouterRecordLocked(packageName);
        if (routerRecord == null) {
            Slog.w(TAG, "requestCreateSessionWithManagerLocked: Ignoring session creation for "
                    + "unknown router.");
            managerRecord.notifyRequestFailed(requestId, REASON_UNKNOWN_ERROR);
            return;
        }

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        SessionCreationRequest lastRequest = managerRecord.mLastSessionCreationRequest;
        if (lastRequest != null) {
            Slog.i(
                    TAG,
                    TextUtils.formatSimple(
                            "requestCreateSessionWithManagerLocked: Notifying failure for pending"
                                + " session creation request - oldSession: %s, route: %s",
                            lastRequest.mOldSession, lastRequest.mRoute));
            managerRecord.notifyRequestFailed(
                    toOriginalRequestId(lastRequest.mManagerRequestId), REASON_UNKNOWN_ERROR);
        }
        managerRecord.mLastSessionCreationRequest = new SessionCreationRequest(routerRecord,
                MediaRoute2ProviderService.REQUEST_ID_NONE, uniqueRequestId,
                oldSession, route);

        // Before requesting to the provider, get session hints from the media router.
        // As a return, media router will request to create a session.
        routerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        RouterRecord::requestCreateSessionByManager,
                        routerRecord,
                        managerRecord,
                        uniqueRequestId,
                        oldSession,
                        route,
                        routingChangeInfo));
    }

    @GuardedBy("mLock")
    private void selectRouteWithManagerLocked(
            int requestId,
            @NonNull IMediaRouter2Manager manager,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "selectRouteWithManager | manager: %d, session: %s, route: %s",
                managerRecord.mManagerId, uniqueSessionId, route.getId()));

        // Can be null if the session is system's or RCN.
        RouterRecord routerRecord = managerRecord.mUserRecord.mHandler
                .findRouterWithSessionLocked(uniqueSessionId);

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        mMediaRouterMetricLogger.addRequestInfo(
                uniqueRequestId, EVENT_TYPE_SELECT_ROUTE, routingChangeInfo);

        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::selectRouteOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        uniqueRequestId, routerRecord, uniqueSessionId, route));
    }

    @GuardedBy("mLock")
    private void deselectRouteWithManagerLocked(
            int requestId,
            @NonNull IMediaRouter2Manager manager,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "deselectRouteWithManager | manager: %d, session: %s, route: %s",
                managerRecord.mManagerId, uniqueSessionId, route.getId()));

        // Can be null if the session is system's or RCN.
        RouterRecord routerRecord = managerRecord.mUserRecord.mHandler
                .findRouterWithSessionLocked(uniqueSessionId);

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        mMediaRouterMetricLogger.addRequestInfo(
                uniqueRequestId, EVENT_TYPE_DESELECT_ROUTE, routingChangeInfo);

        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::deselectRouteOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        uniqueRequestId, routerRecord, uniqueSessionId, route));
    }

    @GuardedBy("mLock")
    private void transferToRouteWithManagerLocked(
            int requestId,
            @NonNull IMediaRouter2Manager manager,
            @NonNull String uniqueSessionId,
            @NonNull MediaRoute2Info route,
            @RoutingSessionInfo.TransferReason int transferReason,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String transferInitiatorPackageName,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "transferToRouteWithManager | manager: %d, session: %s, route: %s",
                managerRecord.mManagerId, uniqueSessionId, route.getId()));

        // Can be null if the session is system's or RCN.
        RouterRecord routerRecord = managerRecord.mUserRecord.mHandler
                .findRouterWithSessionLocked(uniqueSessionId);

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        mMediaRouterMetricLogger.addRequestInfo(
                uniqueRequestId, EVENT_TYPE_TRANSFER_TO_ROUTE, routingChangeInfo);

        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::transferToRouteOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        uniqueRequestId,
                        transferInitiatorUserHandle,
                        transferInitiatorPackageName,
                        routerRecord,
                        uniqueSessionId,
                        route,
                        transferReason,
                        routingChangeInfo));
    }

    @GuardedBy("mLock")
    private void setSessionVolumeWithManagerLocked(int requestId,
            @NonNull IMediaRouter2Manager manager,
            @NonNull String uniqueSessionId, int volume) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "setSessionVolumeWithManager | manager: %d, session: %s, volume: %d",
                managerRecord.mManagerId, uniqueSessionId, volume));

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::setSessionVolumeOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        uniqueRequestId, uniqueSessionId, volume));
    }

    @GuardedBy("mLock")
    private void releaseSessionWithManagerLocked(int requestId,
            @NonNull IMediaRouter2Manager manager, @NonNull String uniqueSessionId) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null) {
            return;
        }

        Slog.i(TAG, TextUtils.formatSimple(
                "releaseSessionWithManager | manager: %d, session: %s",
                managerRecord.mManagerId, uniqueSessionId));

        RouterRecord routerRecord = managerRecord.mUserRecord.mHandler
                .findRouterWithSessionLocked(uniqueSessionId);

        long uniqueRequestId = toUniqueRequestId(managerRecord.mManagerId, requestId);
        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(UserHandler::releaseSessionOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        uniqueRequestId, routerRecord, uniqueSessionId));
    }

    @GuardedBy("mLock")
    private void setDeviceSuggestionsWithManagerLocked(
            @NonNull IMediaRouter2Manager manager,
            @Nullable List<SuggestedDeviceInfo> suggestedDeviceInfo) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null || managerRecord.mTargetPackageName == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Ignoring set device suggestion for unknown manager: %s", manager));
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "setDeviceSuggestions | manager: %d, suggestingPackageName: %d suggestion:"
                            + " %d",
                        managerRecord.mManagerId,
                        managerRecord.mOwnerPackageName,
                        suggestedDeviceInfo));

        RouterRecord routerRecord =
                managerRecord.mUserRecord.findRouterRecordLocked(managerRecord.mTargetPackageName);
        if (routerRecord == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Router record not found for the target package: %s",
                            managerRecord.mTargetPackageName));
            return;
        }

        routerRecord.putDeviceSuggestionsLocked(
                managerRecord.mOwnerPackageName, suggestedDeviceInfo);
        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::notifyDeviceSuggestionsUpdatedOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        managerRecord.mTargetPackageName,
                        managerRecord.mOwnerPackageName,
                        suggestedDeviceInfo));
        mMediaRouterMetricLogger.notifyDeviceSuggestionsUpdated(
                managerRecord.mTargetUid, managerRecord.mOwnerUid);
    }

    @GuardedBy("mLock")
    @NonNull
    private Map<String, List<SuggestedDeviceInfo>> getDeviceSuggestionsWithManagerLocked(
            @NonNull IMediaRouter2Manager manager) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null || managerRecord.mTargetPackageName == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Attempted to get device suggestion for unknown manager: %s", manager));
            return Collections.emptyMap();
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "getDeviceSuggestionsWithManagerLocked | manager: %d",
                        managerRecord.mManagerId));

        RouterRecord routerRecord =
                managerRecord.mUserRecord.findRouterRecordLocked(managerRecord.mTargetPackageName);
        if (routerRecord == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Router record not found for the target package: %s",
                            managerRecord.mTargetPackageName));
            return Collections.emptyMap();
        }
        return routerRecord.getDeviceSuggestionsLocked();
    }

    @GuardedBy("mLock")
    private void onDeviceSuggestionRequestedWithManagerLocked(
            @NonNull IMediaRouter2Manager manager) {
        final IBinder binder = manager.asBinder();
        ManagerRecord managerRecord = mAllManagerRecords.get(binder);

        if (managerRecord == null || managerRecord.mTargetPackageName == null) {
            Slog.w(
                    TAG,
                    TextUtils.formatSimple(
                            "Ignoring on device suggestion ui visible for unknown manager: %s",
                            manager));
            return;
        }

        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "onDeviceSuggestionRequested | manager: %d", managerRecord.mManagerId));

        managerRecord.mUserRecord.mHandler.sendMessage(
                obtainMessage(
                        UserHandler::notifyDeviceSuggestionRequestedOnHandler,
                        managerRecord.mUserRecord.mHandler,
                        managerRecord.mTargetPackageName));
        mMediaRouterMetricLogger.notifyDeviceSuggestionsRequested(
                managerRecord.mTargetUid, managerRecord.mOwnerUid);
    }

    // End of locked methods that are used by MediaRouter2Manager.

    // Start of locked methods that are used by both MediaRouter2 and MediaRouter2Manager.

    @GuardedBy("mLock")
    private UserRecord getOrCreateUserRecordLocked(int userId) {
        UserRecord userRecord = mUserRecords.get(userId);
        if (userRecord == null) {
            userRecord = new UserRecord(userId, mLooper);
            mUserRecords.put(userId, userRecord);
            userRecord.init();
            if (isUserActiveLocked(userId)) {
                userRecord.mHandler.sendMessage(
                        obtainMessage(UserHandler::start, userRecord.mHandler));
            }
        }
        return userRecord;
    }

    @GuardedBy("mLock")
    private void disposeUserIfNeededLocked(@NonNull UserRecord userRecord) {
        // If there are no records left and the user is no longer current then go ahead
        // and purge the user record and all of its associated state.  If the user is current
        // then leave it alone since we might be connected to a route or want to query
        // the same route information again soon.
        if (!isUserActiveLocked(userRecord.mUserHandle.getIdentifier())
                && userRecord.mRouterRecords.isEmpty()
                && userRecord.mManagerRecords.isEmpty()) {
            if (DEBUG) {
                Slog.d(TAG, userRecord + ": Disposed");
            }
            userRecord.mHandler.sendMessage(
                    obtainMessage(UserHandler::stop, userRecord.mHandler));
            mUserRecords.remove(userRecord.mUserHandle.getIdentifier());
            // Note: User already stopped (by switchUser) so no need to send stop message here.
        }
    }

    // End of locked methods that are used by both MediaRouter2 and MediaRouter2Manager.

    static long toUniqueRequestId(int requesterId, int originalRequestId) {
        return ((long) requesterId << 32) | originalRequestId;
    }

    static int toRequesterId(long uniqueRequestId) {
        return (int) (uniqueRequestId >> 32);
    }

    static int toOriginalRequestId(long uniqueRequestId) {
        return (int) uniqueRequestId;
    }

    private static String getScanningStateString(@ScanningState int scanningState) {
        return switch (scanningState) {
            case SCANNING_STATE_NOT_SCANNING -> "NOT_SCANNING";
            case SCANNING_STATE_WHILE_INTERACTIVE -> "SCREEN_ON_ONLY";
            case SCANNING_STATE_SCANNING_FULL -> "FULL";
            default -> "Invalid scanning state: " + scanningState;
        };
    }

    private static void validateScanningStateValue(@ScanningState int scanningState) {
        if (scanningState != SCANNING_STATE_NOT_SCANNING
                && scanningState != SCANNING_STATE_WHILE_INTERACTIVE
                && scanningState != SCANNING_STATE_SCANNING_FULL) {
            throw new IllegalArgumentException(
                    TextUtils.formatSimple("Scanning state %d is not valid.", scanningState));
        }
    }

    private static @SuggestionProviderFlags int getSuggestionProviderFlags(
            RouterRecord routerRecord, MediaRoute2Info mediaRoute2Info) {
        String routeId = mediaRoute2Info.getId();
        int result = 0;
        if (routerRecord.mRouteListingPreference != null) {
            List<RouteListingPreference.Item> routeListingPreferenceItems =
                    routerRecord.mRouteListingPreference.getItems();
            if (routeListingPreferenceItems.stream()
                    .anyMatch(
                            item ->
                                    (item.getRouteId().equals(routeId))
                                            && (item.getFlags() & FLAG_SUGGESTED) != 0)) {
                result |= SUGGESTION_PROVIDER_RLP;
            }
        }
        Map<String, List<SuggestedDeviceInfo>> suggestionsMap = routerRecord.mDeviceSuggestions;
        List<SuggestedDeviceInfo> suggestionsByApp = suggestionsMap.get(routerRecord.mPackageName);
        if (suggestionsByApp != null
                && suggestionsByApp.stream()
                        .anyMatch(
                                suggestedDeviceInfo ->
                                        suggestedDeviceInfo.getRouteId().equals(routeId))) {
            result |= SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP;
        }
        if (suggestionsMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(routerRecord.mPackageName))
                .anyMatch(
                        entry ->
                                entry.getValue().stream()
                                        .anyMatch(
                                                suggestedDeviceInfo ->
                                                        suggestedDeviceInfo
                                                                .getRouteId()
                                                                .equals(routeId)))) {
            result |= SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER;
        }
        return result;
    }

    /** Invoked when {@link MediaRouterService#systemRunning()} is invoked. */
    /* package */ void systemRunning() {
        sInstance.set(this);
    }

    final class UserRecord {
        public final UserHandle mUserHandle;
        //TODO: make records private for thread-safety
        private final ArrayList<RouterRecord> mRouterRecords = new ArrayList<>();
        final ArrayList<ManagerRecord> mManagerRecords = new ArrayList<>();

        // @GuardedBy("mLock")
        private final Set<String> mLastPackagesWithSystemOverridesLocked = new ArraySet<>();

        RouteDiscoveryPreference mCompositeDiscoveryPreference = RouteDiscoveryPreference.EMPTY;
        Map<String, RouteDiscoveryPreference> mPerAppPreferences = Map.of();
        Set<String> mActivelyScanningPackages = Set.of();
        final UserHandler mHandler;

        UserRecord(int userId, @NonNull Looper looper) {
            mUserHandle = UserHandle.of(userId);
            mHandler = new UserHandler(/* userRecord= */ this, looper);
        }

        void init() {
            mHandler.init();
        }

        void addRouterRecord(RouterRecord routerRecord) {
            mRouterRecords.add(routerRecord);
        }

        void removeRouterRecord(RouterRecord routerRecord) {
            mRouterRecords.remove(routerRecord);
            if (Flags.cleanUpDeadRouterRecordsAfterUnbinding()) {
                mHandler.removeRouterRecord(routerRecord);
            }
        }

        // TODO: This assumes that only one router exists in a package.
        //       Do this in Android S or later.
        @GuardedBy("mLock")
        RouterRecord findRouterRecordLocked(String packageName) {
            for (RouterRecord routerRecord : mRouterRecords) {
                if (TextUtils.equals(routerRecord.mPackageName, packageName)) {
                    return routerRecord;
                }
            }
            return null;
        }

        /** Returns true if the given RouterRecord is binded to the service. */
        boolean isRouterRecordBinded(RouterRecord routerRecordToCheck) {
            for (RouterRecord routerRecord : mRouterRecords) {
                if (routerRecord.mRouterId == routerRecordToCheck.mRouterId) {
                    return true;
                }
            }
            return false;
        }

        @GuardedBy("mLock")
        private List<AppId> getAppsWithSystemOverridesLocked() {
            return mapPackageNamesToAppIdList(mLastPackagesWithSystemOverridesLocked);
        }

        /**
         * Returns a list of {@link AppId app ids} corresponding to the given package names, created
         * by associating each package name with {@link #mUserHandle}.
         */
        private List<AppId> mapPackageNamesToAppIdList(Collection<String> packageNames) {
            return packageNames.stream().map(it -> new AppId(it, mUserHandle)).toList();
        }

        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "UserRecord");

            String indent = prefix + "  ";

            pw.println(indent + "user id=" + mUserHandle.getIdentifier());

            pw.println(indent + "Router Records:");
            if (!mRouterRecords.isEmpty()) {
                for (RouterRecord routerRecord : mRouterRecords) {
                    routerRecord.dump(pw, indent + "  ");
                }
            } else {
                pw.println(indent + "<no router records>");
            }

            pw.println(indent + "Manager Records:");
            if (!mManagerRecords.isEmpty()) {
                for (ManagerRecord managerRecord : mManagerRecords) {
                    managerRecord.dump(pw, indent + "  ");
                }
            } else {
                pw.println(indent + "<no manager records>");
            }

            pw.println(indent + "Composite discovery preference:");
            mCompositeDiscoveryPreference.dump(pw, indent + "  ");
            pw.println(
                    indent
                            + "Packages actively scanning: "
                            + String.join(", ", mActivelyScanningPackages));

            if (!mHandler.runWithScissors(() -> mHandler.dump(pw, indent), 1000)) {
                pw.println(indent + "<could not dump handler state>");
            }
        }
    }

    final class RouterRecord implements IBinder.DeathRecipient {
        public final Context mContext;
        public final UserRecord mUserRecord;
        public final String mPackageName;
        public final List<Integer> mSelectRouteSequenceNumbers;
        public final IMediaRouter2 mRouter;
        public final int mUid;
        public final int mPid;
        public final boolean mHasConfigureWifiDisplayPermission;
        public final boolean mHasModifyAudioRoutingPermission;
        public final boolean mHasMediaContentControlPermission;
        public final boolean mHasMediaRoutingControl;
        public final AtomicBoolean mHasBluetoothRoutingPermission;
        public final int mRouterId;
        public @ScanningState int mScanningState = SCANNING_STATE_NOT_SCANNING;

        public RouteDiscoveryPreference mDiscoveryPreference;
        @Nullable public RouteListingPreference mRouteListingPreference;
        // @GuardedBy("mLock")
        private final Map<String, List<SuggestedDeviceInfo>> mDeviceSuggestions = new HashMap<>();

        RouterRecord(
                Context context,
                UserRecord userRecord,
                IMediaRouter2 router,
                int uid,
                int pid,
                String packageName,
                boolean hasConfigureWifiDisplayPermission,
                boolean hasModifyAudioRoutingPermission,
                boolean hasMediaContentControlPermission,
                boolean hasMediaRoutingControl) {
            mContext = context;
            mUserRecord = userRecord;
            mPackageName = packageName;
            mSelectRouteSequenceNumbers = new ArrayList<>();
            mDiscoveryPreference = RouteDiscoveryPreference.EMPTY;
            mRouter = router;
            mUid = uid;
            mPid = pid;
            mHasConfigureWifiDisplayPermission = hasConfigureWifiDisplayPermission;
            mHasModifyAudioRoutingPermission = hasModifyAudioRoutingPermission;
            mHasMediaContentControlPermission = hasMediaContentControlPermission;
            mHasMediaRoutingControl = hasMediaRoutingControl;
            mHasBluetoothRoutingPermission =
                    new AtomicBoolean(checkCallerHasBluetoothPermissions(mPid, mUid));
            mRouterId = mNextRouterOrManagerId.getAndIncrement();
        }

        /**
         * Returns whether the corresponding router has permission to query and control system
         * routes.
         */
        public boolean hasSystemRoutingPermission() {
            return mHasModifyAudioRoutingPermission || mHasBluetoothRoutingPermission.get();
        }

        public boolean isActivelyScanning() {
            return mScanningState == SCANNING_STATE_WHILE_INTERACTIVE
                    || mScanningState == SCANNING_STATE_SCANNING_FULL
                    || mDiscoveryPreference.shouldPerformActiveScan();
        }

        @GuardedBy("mLock")
        public void maybeUpdateSystemRoutingPermissionLocked() {
            boolean oldSystemRoutingPermissionValue = hasSystemRoutingPermission();
            mHasBluetoothRoutingPermission.set(checkCallerHasBluetoothPermissions(mPid, mUid));
            boolean newSystemRoutingPermissionValue = hasSystemRoutingPermission();
            if (oldSystemRoutingPermissionValue != newSystemRoutingPermissionValue) {
                // TODO: b/379788233 - Ensure access to fields like
                // mLastNotifiedRoutesToPrivilegedRouters happens on the right thread. We might need
                // to run this on the handler.
                Map<String, MediaRoute2Info> routesToReport =
                        newSystemRoutingPermissionValue
                                ? mUserRecord.mHandler.mLastNotifiedRoutesToPrivilegedRouters
                                : mUserRecord.mHandler.mLastNotifiedRoutesToNonPrivilegedRouters;
                notifyRoutesUpdated(routesToReport.values().stream().toList());

                List<RoutingSessionInfo> sessionInfos =
                        mUserRecord.mHandler.getSystemProvider().getSessionInfos();
                RoutingSessionInfo systemSessionToReport =
                        newSystemRoutingPermissionValue && !sessionInfos.isEmpty()
                                ? sessionInfos.get(0)
                                : mUserRecord.mHandler.getSystemProvider().getDefaultSessionInfo();
                notifySessionInfoChanged(systemSessionToReport);
            }
        }

        public void dispose() {
            mRouter.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public void binderDied() {
            routerDied(this);
        }

        public void updateScanningState(@ScanningState int scanningState) {
            if (mScanningState == scanningState) {
                return;
            }

            mScanningState = scanningState;

            mUserRecord.mHandler.sendMessage(
                    obtainMessage(
                            UserHandler::updateDiscoveryPreferenceOnHandler, mUserRecord.mHandler));
        }

        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "RouterRecord");

            String indent = prefix + "  ";

            pw.println(indent + "mPackageName=" + mPackageName);
            pw.println(indent + "mSelectRouteSequenceNumbers=" + mSelectRouteSequenceNumbers);
            pw.println(indent + "mUid=" + mUid);
            pw.println(indent + "mPid=" + mPid);
            pw.println(indent + "mHasConfigureWifiDisplayPermission="
                    + mHasConfigureWifiDisplayPermission);
            pw.println(
                    indent
                            + "mHasModifyAudioRoutingPermission="
                            + mHasModifyAudioRoutingPermission);
            pw.println(
                    indent
                            + "mHasBluetoothRoutingPermission="
                            + mHasBluetoothRoutingPermission.get());
            pw.println(indent + "hasSystemRoutingPermission=" + hasSystemRoutingPermission());
            pw.println(indent + "mRouterId=" + mRouterId);

            mDiscoveryPreference.dump(pw, indent);
        }

        /**
         * Notifies the corresponding router that it was successfully registered.
         *
         * <p>The message sent to the router includes a snapshot of the initial state, including
         * known routes and the system {@link RoutingSessionInfo}.
         *
         * @param currentRoutes All currently known routes, which are filtered according to package
         *     visibility before being sent to the router.
         * @param currentSystemSessionInfo The current system {@link RoutingSessionInfo}.
         */
        public void notifyRegistered(
                List<MediaRoute2Info> currentRoutes, RoutingSessionInfo currentSystemSessionInfo) {
            try {
                mRouter.notifyRouterRegistered(
                        getVisibleRoutes(currentRoutes), currentSystemSessionInfo);
            } catch (RemoteException ex) {
                logRemoteException("notifyRegistered", ex);
            }
        }

        /**
         * Sends the corresponding router an {@link
         * android.media.MediaRouter2.RouteCallback#onRoutesUpdated update} for the given {@code
         * routes}.
         *
         * <p>Only the routes that are visible to the router are sent as part of the update.
         */
        public void notifyRoutesUpdated(List<MediaRoute2Info> routes) {
            try {
                mRouter.notifyRoutesUpdated(getVisibleRoutes(routes));
            } catch (RemoteException ex) {
                logRemoteException("notifyRoutesUpdated", ex);
            }
        }

        public void notifySessionCreated(int requestId, @NonNull RoutingSessionInfo sessionInfo) {
            try {
                mRouter.notifySessionCreated(
                        requestId, maybeClearTransferInitiatorIdentity(sessionInfo));
            } catch (RemoteException ex) {
                logRemoteException("notifySessionCreated", ex);
            }
        }

        /**
         * Notifies the corresponding router of a request failure.
         *
         * @param requestId The id of the request that failed.
         */
        public void notifySessionCreationFailed(int requestId) {
            try {
                mRouter.notifySessionCreated(requestId, /* sessionInfo= */ null);
            } catch (RemoteException ex) {
                logRemoteException("notifySessionCreationFailed", ex);
            }
        }

        /**
         * Notifies the corresponding router of the release of the given {@link RoutingSessionInfo}.
         */
        public void notifySessionReleased(RoutingSessionInfo sessionInfo) {
            try {
                mRouter.notifySessionReleased(sessionInfo);
            } catch (RemoteException ex) {
                logRemoteException("notifySessionReleased", ex);
            }
        }

        public void notifyDeviceSuggestionsUpdated(
                String suggestingPackageName, List<SuggestedDeviceInfo> suggestedDeviceInfo) {
            try {
                mRouter.notifyDeviceSuggestionsUpdated(suggestingPackageName, suggestedDeviceInfo);
            } catch (RemoteException ex) {
                logRemoteException("notifyDeviceSuggestionsUpdated", ex);
            }
        }

        public void notifyDeviceSuggestionRequested() {
            try {
                mRouter.notifyDeviceSuggestionRequested();
            } catch (RemoteException ex) {
                logRemoteException("notifyDeviceSuggestionRequested", ex);
            }
        }

        /**
         * Sends the corresponding router a {@link RoutingSessionInfo session} creation request,
         * with the given {@link MediaRoute2Info} as the initial member.
         *
         * <p>Must be called on the thread of the corresponding {@link UserHandler}.
         *
         * @param managerRecord The record of the manager that made the request.
         * @param uniqueRequestId The id of the request.
         * @param oldSession The session from which the transfer originated.
         * @param route The initial route member of the session to create.
         */
        public void requestCreateSessionByManager(
                ManagerRecord managerRecord,
                long uniqueRequestId,
                RoutingSessionInfo oldSession,
                MediaRoute2Info route,
                RoutingChangeInfo routingChangeInfo) {
            try {
                if (route.isSystemRoute() && !hasSystemRoutingPermission()) {
                    // The router lacks permission to modify system routing, so we hide system
                    // route info from them.
                    route = mUserRecord.mHandler.getSystemProvider().getDefaultRoute();
                }
                mRouter.requestCreateSessionByManager(
                        uniqueRequestId, oldSession, route, routingChangeInfo);
            } catch (RemoteException ex) {
                logRemoteException("requestCreateSessionByManager", ex);
                managerRecord.notifyRequestFailed(
                        toOriginalRequestId(uniqueRequestId), REASON_UNKNOWN_ERROR);
            }
        }

        /**
         * Sends the corresponding router an update for the given session.
         *
         * <p>Note: These updates are not directly visible to the app.
         */
        public void notifySessionInfoChanged(RoutingSessionInfo sessionInfo) {
            try {
                mRouter.notifySessionInfoChanged(maybeClearTransferInitiatorIdentity(sessionInfo));
            } catch (RemoteException ex) {
                logRemoteException("notifySessionInfoChanged", ex);
            }
        }

        /**
         * Updates the device suggestions for the given suggesting package.
         *
         * @param suggestingPackageName The package name of the suggesting app.
         * @param deviceSuggestions The device suggestions. May be null if the caller is clearing
         *     out their suggestions.
         */
        @GuardedBy("mLock")
        public void putDeviceSuggestionsLocked(
                String suggestingPackageName,
                @Nullable List<SuggestedDeviceInfo> deviceSuggestions) {
            if (deviceSuggestions != null) {
                mDeviceSuggestions.put(suggestingPackageName, deviceSuggestions);
            } else {
                mDeviceSuggestions.remove(suggestingPackageName);
            }
        }

        /**
         * Returns the device suggestions for all suggesting packages.
         *
         * @return The device suggestions.
         */
        @NonNull
        @GuardedBy("mLock")
        public Map<String, List<SuggestedDeviceInfo>> getDeviceSuggestionsLocked() {
            return new HashMap<>(mDeviceSuggestions);
        }

        private RoutingSessionInfo maybeClearTransferInitiatorIdentity(
                @NonNull RoutingSessionInfo sessionInfo) {
            UserHandle transferInitiatorUserHandle = sessionInfo.getTransferInitiatorUserHandle();
            String transferInitiatorPackageName = sessionInfo.getTransferInitiatorPackageName();

            if (!Objects.equals(mUserRecord.mUserHandle, transferInitiatorUserHandle)
                    || !Objects.equals(mPackageName, transferInitiatorPackageName)) {
                return new RoutingSessionInfo.Builder(sessionInfo)
                        .setTransferInitiator(null, null)
                        .build();
            }

            return sessionInfo;
        }

        /**
         * Returns a filtered copy of {@code routes} that contains only the routes that are visible
         * to this RouterRecord.
         */
        private List<MediaRoute2Info> getVisibleRoutes(@NonNull List<MediaRoute2Info> routes) {
            List<MediaRoute2Info> filteredRoutes = new ArrayList<>();
            for (MediaRoute2Info route : routes) {
                if (route.isVisibleTo(mPackageName,
                        mHasMediaRoutingControl || mHasMediaContentControlPermission)
                        && hasPermissionsToSeeRoute(route)) {
                    filteredRoutes.add(route);
                }
            }
            return filteredRoutes;
        }

        /**
         * @return whether this RouterRecord has the required permissions to see the given route.
         */
        private boolean hasPermissionsToSeeRoute(MediaRoute2Info route) {
            if (!Flags.enableRouteVisibilityControlApi()) {
                return true;
            }
            if (Flags.enableRouteVisibilityControlCompatFixes()
                    && route.getTemporaryVisibilityPackages().contains(mPackageName)) {
                return true;
            }
            List<Set<String>> permissionSets = route.getRequiredPermissions();
            if (permissionSets.isEmpty()) {
                return true;
            }
            for (Set<String> permissionSet : permissionSets) {
                boolean hasAllInSet = true;
                for (String permission : permissionSet) {
                    if (mContext.checkPermission(permission, mPid, mUid)
                            != PackageManager.PERMISSION_GRANTED
                            && !permissionAllowedForAppCompat(permission)) {
                        hasAllInSet = false;
                        break;
                    }
                }
                if (hasAllInSet) {
                    return true;
                }
            }
            return false;
        }

        /** Logs a {@link RemoteException} occurred during the execution of {@code operation}. */
        private void logRemoteException(String operation, RemoteException exception) {
            String message =
                    TextUtils.formatSimple(
                            "%s failed for %s due to %s",
                            operation, getDebugString(), exception.toString());
            Slog.w(TAG, message);
        }

        /** Returns a human readable representation of this router record for logging purposes. */
        private String getDebugString() {
            return TextUtils.formatSimple(
                    "Router %s (id=%d,pid=%d,userId=%d,uid=%d)",
                    mPackageName, mRouterId, mPid, mUserRecord.mUserHandle.getIdentifier(), mUid);
        }

        /**
         * Returns whether the given permission should be considered to be satisfied because of the
         * app compatibility setting for local networking restrictions.
         *
         * TODO(b/386260596): This is a temporary workaround, which we hope to remove in the next
         * release.
         */
        private boolean permissionAllowedForAppCompat(String permission) {
            if (!Flags.enableRouteVisibilityControlCompatFixes()) {
                return false;
            }
            // TODO(b/386260596) - replace this string with a Manifest.permission constant once
            // one is available.
            if (TextUtils.equals(permission, "android.permission.ACCESS_LOCAL_NETWORK")) {
                // TODO(b/386260596) - this id is defined as RESTRICT_LOCAL_NETWORK in the
                //  connectivity module's ConnectivityCompatChanges.java - see if we can move it to
                //  a shared location so we can avoid duplicating it here.
                if (!CompatChanges.isChangeEnabled(365139289L, mUid)) {
                    return true;
                }
                return mContext.checkPermission(Manifest.permission.NEARBY_WIFI_DEVICES, mPid, mUid)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return false;
        }
    }

    final class ManagerRecord implements IBinder.DeathRecipient {
        @NonNull public final UserRecord mUserRecord;
        @NonNull public final IMediaRouter2Manager mManager;
        public final int mOwnerUid;
        public final int mOwnerPid;
        @NonNull public final String mOwnerPackageName;
        public final int mManagerId;
        // The target package name can be null when the manager does not target a local router.
        @Nullable public final String mTargetPackageName;

        /**
         * The target Uid can be {@link Process.INVALID_UID} if the manager does not target a local
         * router.
         */
        public final int mTargetUid;

        public final boolean mHasMediaRoutingControl;
        public final boolean mHasMediaContentControl;
        @Nullable public SessionCreationRequest mLastSessionCreationRequest;

        public @ScanningState int mScanningState = SCANNING_STATE_NOT_SCANNING;

        ManagerRecord(
                @NonNull UserRecord userRecord,
                @NonNull IMediaRouter2Manager manager,
                int ownerUid,
                int ownerPid,
                @NonNull String ownerPackageName,
                @Nullable String targetPackageName,
                int targetUid,
                boolean hasMediaRoutingControl,
                boolean hasMediaContentControl) {
            mUserRecord = userRecord;
            mManager = manager;
            mOwnerUid = ownerUid;
            mOwnerPid = ownerPid;
            mOwnerPackageName = ownerPackageName;
            mTargetPackageName = targetPackageName;
            mTargetUid = targetUid;
            mManagerId = mNextRouterOrManagerId.getAndIncrement();
            mHasMediaRoutingControl = hasMediaRoutingControl;
            mHasMediaContentControl = hasMediaContentControl;
        }

        public void dispose() {
            mManager.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public void binderDied() {
            managerDied(this);
        }

        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "ManagerRecord");

            String indent = prefix + "  ";

            pw.println(indent + "mOwnerPackageName=" + mOwnerPackageName);
            pw.println(indent + "mTargetPackageName=" + mTargetPackageName);
            pw.println(indent + "mManagerId=" + mManagerId);
            pw.println(indent + "mOwnerUid=" + mOwnerUid);
            pw.println(indent + "mOwnerPid=" + mOwnerPid);
            pw.println(indent + "mScanningState=" + getScanningStateString(mScanningState));

            if (mLastSessionCreationRequest != null) {
                mLastSessionCreationRequest.dump(pw, indent);
            }
        }

        /**
         * Notifies the corresponding manager of a request failure.
         *
         * <p>Must be called on the thread of the corresponding {@link UserHandler}.
         *
         * @param requestId The id of the request that failed.
         * @param reason The reason of the failure. One of
         */
        public void notifyRequestFailed(int requestId, int reason) {
            try {
                mManager.notifyRequestFailed(requestId, reason);
            } catch (RemoteException ex) {
                logRemoteException("notifyRequestFailed", ex);
            }
        }

        /**
         * Notifies the corresponding manager of the creation of the given {@link
         * RoutingSessionInfo}.
         *
         * @param requestId The id of the request that originated the creation of the session.
         * @param session The session that was created.
         */
        public void notifySessionCreated(int requestId, @NonNull RoutingSessionInfo session) {
            try {
                mManager.notifySessionCreated(requestId, session);
            } catch (RemoteException ex) {
                logRemoteException("notifySessionCreated", ex);
            }
        }

        /**
         * Notifies the corresponding manager of the availability of the given routes.
         *
         * @param routes The routes available to the manager that corresponds to this record.
         */
        public void notifyRoutesUpdated(List<MediaRoute2Info> routes) {
            try {
                mManager.notifyRoutesUpdated(getVisibleRoutes(routes));
            } catch (RemoteException ex) {
                logRemoteException("notifyRoutesUpdated", ex);
            }
        }

        /**
         * Notifies the corresponding manager of an update in the given session.
         *
         * @param sessionInfo The updated session info.
         * @param shouldShowVolumeUi Whether a volume UI affordance should be presented as a result
         *     of this session update.
         */
        public void notifySessionUpdated(
                RoutingSessionInfo sessionInfo, boolean shouldShowVolumeUi) {
            try {
                mManager.notifySessionUpdated(sessionInfo, shouldShowVolumeUi);
            } catch (RemoteException ex) {
                logRemoteException("notifySessionUpdated", ex);
            }
        }

        /**
         * Notifies the corresponding manager that the given session has been released.
         *
         * @param sessionInfo The released session info.
         */
        public void notifySessionReleased(RoutingSessionInfo sessionInfo) {
            try {
                mManager.notifySessionReleased(sessionInfo);
            } catch (RemoteException ex) {
                logRemoteException("notifySessionReleased", ex);
            }
        }

        /**
         * Notifies the corresponding manager that the discovery preference has changed for the
         * given {@code packageName}.
         */
        public void notifyDiscoveryPreferenceChanged(
                String packageName, RouteDiscoveryPreference preference) {
            try {
                mManager.notifyDiscoveryPreferenceChanged(packageName, preference);
            } catch (RemoteException ex) {
                logRemoteException("notifyDiscoveryPreferenceChanged", ex);
            }
        }

        /**
         * Notifies the corresponding manager that {@link RouteListingPreference} has changed for
         * the given {@code packageName}.
         */
        public void notifyRouteListingPreferenceChange(
                String routerPackageName, RouteListingPreference routeListingPreference) {
            try {
                mManager.notifyRouteListingPreferenceChange(
                        routerPackageName, routeListingPreference);
            } catch (RemoteException ex) {
                logRemoteException("notifyRouteListingPreferenceChange", ex);
            }
        }

        public void notifyDeviceSuggestionsUpdated(
                String routerPackageName,
                String suggestingPackageName,
                @Nullable List<SuggestedDeviceInfo> suggestedDeviceInfo) {
            try {
                mManager.notifyDeviceSuggestionsUpdated(
                        routerPackageName, suggestingPackageName, suggestedDeviceInfo);
            } catch (RemoteException ex) {
                logRemoteException("notifyDeviceSuggestionsUpdated", ex);
            }
        }

        public void notifyDeviceSuggestionRequested() {
            try {
                mManager.notifyDeviceSuggestionRequested();
            } catch (RemoteException ex) {
                logRemoteException("notifyDeviceSuggestionRequested", ex);
            }
        }

        public void notifySystemSessionOverridesChanged(List<AppId> appsWithOverrides) {
            try {
                mManager.notifySystemSessionOverridesChanged(appsWithOverrides);
            } catch (RemoteException ex) {
                logRemoteException("notifySystemSessionOverridesChanged", ex);
            }
        }

        private List<MediaRoute2Info> getVisibleRoutes(List<MediaRoute2Info> routes) {
            if (TextUtils.isEmpty(mTargetPackageName)) {
                // If the proxy router / manager doesn't target a specific app, it sees all
                // routes.
                return routes;
            }
            List<MediaRoute2Info> filteredRoutes = new ArrayList<>();
            for (MediaRoute2Info route : routes) {
                if (route.isVisibleTo(mTargetPackageName,
                        mHasMediaRoutingControl || mHasMediaContentControl)) {
                    filteredRoutes.add(route);
                }
            }
            return filteredRoutes;
        }

        private void logRemoteException(String operation, RemoteException exception) {
            String message =
                    TextUtils.formatSimple(
                            "%s failed for %s due to %s",
                            operation, getDebugString(), exception.toString());
            Slog.w(TAG, message);
        }

        private void updateScanningState(@ScanningState int scanningState) {
            if (mScanningState == scanningState) {
                return;
            }

            mScanningState = scanningState;

            mUserRecord.mHandler.sendMessage(
                    obtainMessage(
                            UserHandler::updateDiscoveryPreferenceOnHandler, mUserRecord.mHandler));
        }

        /** Returns a human readable representation of this manager record for logging purposes. */
        public String getDebugString() {
            return TextUtils.formatSimple(
                    "Manager %s (id=%d,pid=%d,userId=%d,uid=%d,targetPkg=%s)",
                    mOwnerPackageName,
                    mManagerId,
                    mOwnerPid,
                    mUserRecord.mUserHandle.getIdentifier(),
                    mOwnerUid,
                    mTargetPackageName);
        }
    }

    private final class UserHandler extends Handler
            implements MediaRoute2ProviderWatcher.Callback, MediaRoute2Provider.Callback {
        private final UserRecord mUserRecord;
        private final MediaRoute2ProviderWatcher mWatcher;

        private final SystemMediaRoute2Provider mSystemProvider;
        private final ArrayList<MediaRoute2Provider> mRouteProviders =
                new ArrayList<>();

        private final List<MediaRoute2ProviderInfo> mLastProviderInfos = new ArrayList<>();
        private final CopyOnWriteArrayList<SessionCreationRequest> mSessionCreationRequests =
                new CopyOnWriteArrayList<>();
        private final Map<String, RouterRecord> mSessionToRouterMap = new ArrayMap<>();

        /**
         * Latest list of routes sent to privileged {@link android.media.MediaRouter2 routers} and
         * {@link android.media.MediaRouter2Manager managers}.
         *
         * <p>Privileged routers are instances of {@link android.media.MediaRouter2 MediaRouter2}
         * that have {@code MODIFY_AUDIO_ROUTING} permission.
         *
         * <p>This list contains all routes exposed by route providers. This includes routes from
         * both system route providers and user route providers.
         *
         * <p>See {@link #getRouterRecords(boolean hasModifyAudioRoutingPermission)}.
         *
         * <p>Must be accessed on this handler's thread.
         */
        private final Map<String, MediaRoute2Info> mLastNotifiedRoutesToPrivilegedRouters =
                new ArrayMap<>();

        /**
         * Latest list of routes sent to non-privileged {@link android.media.MediaRouter2 routers}.
         *
         * <p>Non-privileged routers are instances of {@link android.media.MediaRouter2
         * MediaRouter2} that do <i><b>not</b></i> have {@code MODIFY_AUDIO_ROUTING} permission.
         *
         * <p>This list contains all routes exposed by user route providers. It might also include
         * the current default route from {@link #mSystemProvider} to expose local route updates
         * (e.g. volume changes) to non-privileged routers.
         *
         * <p>See {@link SystemMediaRoute2Provider#mDefaultRoute}.
         *
         * <p>Must be accessed on this handler's thread.
         */
        private final Map<String, MediaRoute2Info> mLastNotifiedRoutesToNonPrivilegedRouters =
                new ArrayMap<>();

        private boolean mRunning;

        private SystemMediaRoute2Provider getSystemProvider() {
            return mSystemProvider;
        }

        // TODO: (In Android S+) Pull out SystemMediaRoute2Provider out of UserHandler.
        UserHandler(@NonNull UserRecord userRecord, @NonNull Looper looper) {
            super(looper, /* callback= */ null, /* async= */ true);
            mUserRecord = userRecord;
            mSystemProvider =
                    Flags.enableMirroringInMediaRouter2()
                            ? SystemMediaRoute2Provider2.create(
                                    mContext, userRecord.mUserHandle, looper)
                            : SystemMediaRoute2Provider.create(
                                    mContext, userRecord.mUserHandle, looper);
            mRouteProviders.add(getSystemProvider());
            mWatcher =
                    new MediaRoute2ProviderWatcher(
                            mContext, this, this, mUserRecord.mUserHandle.getIdentifier());
        }

        void init() {
            getSystemProvider().setCallback(this);
        }

        private void start() {
            if (!mRunning) {
                mRunning = true;
                getSystemProvider().start();
                mWatcher.start();
            }
        }

        private void stop() {
            if (mRunning) {
                mRunning = false;
                mWatcher.stop(); // also stops all providers
                getSystemProvider().stop();
            }
        }

        @Override
        public void onAddProviderService(@NonNull MediaRoute2ProviderServiceProxy proxy) {
            proxy.setCallback(this);
            mRouteProviders.add(proxy);
            proxy.updateDiscoveryPreference(
                    mUserRecord.mActivelyScanningPackages,
                    mUserRecord.mCompositeDiscoveryPreference,
                    mUserRecord.mPerAppPreferences);
        }

        @Override
        public void onRemoveProviderService(@NonNull MediaRoute2ProviderServiceProxy proxy) {
            mRouteProviders.remove(proxy);
        }

        @Override
        public void onProviderStateChanged(@NonNull MediaRoute2Provider provider) {
            sendMessage(PooledLambda.obtainMessage(UserHandler::onProviderStateChangedOnHandler,
                    this, provider));
        }

        @Override
        public void onSessionCreated(@NonNull MediaRoute2Provider provider,
                long uniqueRequestId, @NonNull RoutingSessionInfo sessionInfo) {
            Slog.i(
                    TAG,
                    "onSessionCreated with uniqueRequestId: "
                            + uniqueRequestId
                            + ", sessionInfo: "
                            + sessionInfo);
            sendMessage(PooledLambda.obtainMessage(UserHandler::onSessionCreatedOnHandler,
                    this, provider, uniqueRequestId, sessionInfo));
        }

        @Override
        public void onSessionUpdated(
                @NonNull MediaRoute2Provider provider,
                @NonNull RoutingSessionInfo sessionInfo,
                Set<String> packageNamesWithRoutingSessionOverrides,
                boolean shouldShowVolumeUi) {
            sendMessage(
                    PooledLambda.obtainMessage(
                            UserHandler::onSessionInfoChangedOnHandler,
                            this,
                            provider,
                            sessionInfo,
                            packageNamesWithRoutingSessionOverrides,
                            shouldShowVolumeUi));
        }

        @Override
        public void onSessionReleased(@NonNull MediaRoute2Provider provider,
                @NonNull RoutingSessionInfo sessionInfo) {
            sendMessage(PooledLambda.obtainMessage(UserHandler::onSessionReleasedOnHandler,
                    this, provider, sessionInfo));
        }

        @Override
        public void onRequestFailed(@NonNull MediaRoute2Provider provider, long uniqueRequestId,
                int reason) {
            sendMessage(PooledLambda.obtainMessage(UserHandler::onRequestFailedOnHandler,
                    this, provider, uniqueRequestId, reason));
        }

        @GuardedBy("mLock")
        @Nullable
        public RouterRecord findRouterWithSessionLocked(@NonNull String uniqueSessionId) {
            return mSessionToRouterMap.get(uniqueSessionId);
        }

        @Nullable
        public ManagerRecord findManagerWithId(int managerId) {
            for (ManagerRecord manager : getManagerRecords()) {
                if (manager.mManagerId == managerId) {
                    return manager;
                }
            }
            return null;
        }

        public void maybeUpdateDiscoveryPreferenceForUid(int uid) {
            boolean isUidRelevant;
            synchronized (mLock) {
                isUidRelevant =
                        mUserRecord.mRouterRecords.stream().anyMatch(router -> router.mUid == uid)
                                | mUserRecord.mManagerRecords.stream()
                                        .anyMatch(manager -> manager.mOwnerUid == uid);
            }
            if (isUidRelevant) {
                sendMessage(PooledLambda.obtainMessage(
                        UserHandler::updateDiscoveryPreferenceOnHandler, this));
            }
        }

        public void removeRouterRecord(RouterRecord routerRecord) {
            for (String sessionId : mSessionToRouterMap.keySet()) {
                RouterRecord routerRecordWithSession = mSessionToRouterMap.get(sessionId);
                if (routerRecordWithSession.mRouterId == routerRecord.mRouterId) {
                    // Release the session associated with the RouterRecord being removed. The
                    // onSessionReleasedOnHandler callback will then remove the RouterRecord from
                    // mSessionToRouterMap.
                    sendMessage(
                            PooledLambda.obtainMessage(
                                    UserHandler::releaseSessionOnHandler,
                                    this,
                                    DUMMY_REQUEST_ID,
                                    routerRecordWithSession,
                                    sessionId));
                }
            }
        }

        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "UserHandler");

            String indent = prefix + "  ";
            pw.println(indent + "mRunning=" + mRunning);

            getSystemProvider().dump(pw, prefix);
            mWatcher.dump(pw, prefix);
        }

        private void onProviderStateChangedOnHandler(@NonNull MediaRoute2Provider provider) {
            MediaRoute2ProviderInfo newInfo = provider.getProviderInfo();
            int providerInfoIndex =
                    indexOfRouteProviderInfoByUniqueId(provider.getUniqueId(), mLastProviderInfos);
            MediaRoute2ProviderInfo oldInfo =
                    providerInfoIndex == -1 ? null : mLastProviderInfos.get(providerInfoIndex);

            if (oldInfo == newInfo) {
                // Nothing to do.
                return;
            }

            Collection<MediaRoute2Info> newRoutes;
            Set<String> newRouteIds;
            if (newInfo != null) {
                // Adding or updating a provider.
                newRoutes = newInfo.getRoutes();
                newRouteIds =
                        newRoutes.stream().map(MediaRoute2Info::getId).collect(Collectors.toSet());
                if (providerInfoIndex >= 0) {
                    mLastProviderInfos.set(providerInfoIndex, newInfo);
                } else {
                    mLastProviderInfos.add(newInfo);
                }
            } else /* newInfo == null */ {
                // Removing a provider.
                mLastProviderInfos.remove(oldInfo);
                newRouteIds = Collections.emptySet();
                newRoutes = Collections.emptySet();
            }

            if (Flags.enableMirroringInMediaRouter2()
                    && provider instanceof MediaRoute2ProviderServiceProxy proxyProvider) {
                // We notify the system provider of service updates, so that it can update the
                // system routing session by adding them as transferable routes. And we remove those
                // that don't support remote routing.
                mSystemProvider.updateSystemMediaRoutesFromProxy(proxyProvider);
                newRoutes.removeIf(it -> !it.supportsRemoteRouting());
            }

            // Add new routes to the maps.
            ArrayList<MediaRoute2Info> addedRoutes = new ArrayList<>();
            boolean hasAddedOrModifiedRoutes = false;
            for (MediaRoute2Info newRouteInfo : newRoutes) {
                if (!newRouteInfo.isValid()) {
                    Slog.w(TAG, "onProviderStateChangedOnHandler: Ignoring invalid route : "
                            + newRouteInfo);
                    continue;
                }
                if (!provider.mIsSystemRouteProvider) {
                    mLastNotifiedRoutesToNonPrivilegedRouters.put(
                            newRouteInfo.getId(), newRouteInfo);
                }
                MediaRoute2Info oldRouteInfo =
                        mLastNotifiedRoutesToPrivilegedRouters.put(
                                newRouteInfo.getId(), newRouteInfo);
                hasAddedOrModifiedRoutes |= !newRouteInfo.equals(oldRouteInfo);
                if (oldRouteInfo == null) {
                    addedRoutes.add(newRouteInfo);
                }
            }

            // Remove stale routes from the maps.
            ArrayList<MediaRoute2Info> removedRoutes = new ArrayList<>();
            Collection<MediaRoute2Info> oldRoutes =
                    oldInfo == null ? Collections.emptyList() : oldInfo.getRoutes();
            boolean hasRemovedRoutes = false;
            for (MediaRoute2Info oldRoute : oldRoutes) {
                String oldRouteId = oldRoute.getId();
                if (!newRouteIds.contains(oldRouteId)) {
                    hasRemovedRoutes = true;
                    mLastNotifiedRoutesToPrivilegedRouters.remove(oldRouteId);
                    mLastNotifiedRoutesToNonPrivilegedRouters.remove(oldRouteId);
                    removedRoutes.add(oldRoute);
                }
            }

            if (!addedRoutes.isEmpty()) {
                // If routes were added, newInfo cannot be null.
                Slog.i(
                        TAG,
                        toLoggingMessage(
                                /* source= */ "addProviderRoutes",
                                newInfo.getUniqueId(),
                                addedRoutes));
            }
            if (!removedRoutes.isEmpty()) {
                // If routes were removed, oldInfo cannot be null.
                Slog.i(TAG,
                        toLoggingMessage(
                                /* source= */ "removeProviderRoutes",
                                oldInfo.getUniqueId(),
                                removedRoutes));
            }

            dispatchUpdatesOnHandler(
                    hasAddedOrModifiedRoutes,
                    hasRemovedRoutes,
                    provider.mIsSystemRouteProvider,
                    getSystemProvider().getDefaultRoute());
        }

        private static String getPackageNameFromNullableRecord(
                @Nullable RouterRecord routerRecord) {
            return routerRecord != null ? routerRecord.mPackageName : "<null router record>";
        }

        private static String toLoggingMessage(
                String source, String providerId, ArrayList<MediaRoute2Info> routes) {
            String routesString =
                    routes.stream()
                            .map(it -> String.format("%s | %s", it.getOriginalId(), it.getName()))
                            .collect(Collectors.joining(/* delimiter= */ ", "));
            return TextUtils.formatSimple("%s | provider: %s, routes: [%s]",
                    source, providerId, routesString);
        }

        /** Notifies the given manager of the current routes. */
        public void dispatchRoutesToManagerOnHandler(ManagerRecord managerRecord) {
            List<MediaRoute2Info> routes =
                    mLastNotifiedRoutesToPrivilegedRouters.values().stream().toList();
            managerRecord.notifyRoutesUpdated(routes);
        }

        /**
         * Dispatches the latest route updates in {@link #mLastNotifiedRoutesToPrivilegedRouters}
         * and {@link #mLastNotifiedRoutesToNonPrivilegedRouters} to registered {@link
         * android.media.MediaRouter2 routers} and {@link MediaRouter2Manager managers} after a call
         * to {@link #onProviderStateChangedOnHandler(MediaRoute2Provider)}. Ignores if no changes
         * were made.
         *
         * @param hasAddedOrModifiedRoutes whether routes were added or modified.
         * @param hasRemovedRoutes whether routes were removed.
         * @param isSystemProvider whether the latest update was caused by a system provider.
         * @param defaultRoute the current default route in {@link #mSystemProvider}.
         */
        private void dispatchUpdatesOnHandler(
                boolean hasAddedOrModifiedRoutes,
                boolean hasRemovedRoutes,
                boolean isSystemProvider,
                MediaRoute2Info defaultRoute) {

            // Ignore if no changes.
            if (!hasAddedOrModifiedRoutes && !hasRemovedRoutes) {
                return;
            }
            List<RouterRecord> routerRecordsWithSystemRoutingPermission =
                    getRouterRecords(/* hasSystemRoutingPermission= */ true);
            List<RouterRecord> routerRecordsWithoutSystemRoutingPermission =
                    getRouterRecords(/* hasSystemRoutingPermission= */ false);
            List<ManagerRecord> managers = getManagerRecords();

            // Managers receive all provider updates with all routes.
            List<MediaRoute2Info> routesForPrivilegedRouters =
                    mLastNotifiedRoutesToPrivilegedRouters.values().stream().toList();
            for (ManagerRecord manager : managers) {
                manager.notifyRoutesUpdated(routesForPrivilegedRouters);
            }

            // Routers with system routing access (either via {@link MODIFY_AUDIO_ROUTING} or
            // {@link BLUETOOTH_CONNECT} + {@link BLUETOOTH_SCAN}) receive all provider updates
            // with all routes.
            notifyRoutesUpdatedToRouterRecords(
                    routerRecordsWithSystemRoutingPermission, routesForPrivilegedRouters);

            if (!isSystemProvider) {
                // Regular routers receive updates from all non-system providers with all non-system
                // routes.
                notifyRoutesUpdatedToRouterRecords(
                        routerRecordsWithoutSystemRoutingPermission,
                        new ArrayList<>(mLastNotifiedRoutesToNonPrivilegedRouters.values()));
            } else if (hasAddedOrModifiedRoutes) {
                // On system provider updates, routers without system routing access
                // receive the updated default route. This is the only system route they should
                // receive.
                mLastNotifiedRoutesToNonPrivilegedRouters.put(defaultRoute.getId(), defaultRoute);
                notifyRoutesUpdatedToRouterRecords(
                        routerRecordsWithoutSystemRoutingPermission,
                        new ArrayList<>(mLastNotifiedRoutesToNonPrivilegedRouters.values()));
            }
        }

        /**
         * Returns the index of the first element in {@code lastProviderInfos} that matches the
         * specified unique id.
         *
         * @param uniqueId unique id of {@link MediaRoute2ProviderInfo} to be found.
         * @param lastProviderInfos list of {@link MediaRoute2ProviderInfo}.
         * @return index of found element, or -1 if not found.
         */
        private static int indexOfRouteProviderInfoByUniqueId(
                @NonNull String uniqueId,
                @NonNull List<MediaRoute2ProviderInfo> lastProviderInfos) {
            for (int i = 0; i < lastProviderInfos.size(); i++) {
                MediaRoute2ProviderInfo providerInfo = lastProviderInfos.get(i);
                if (TextUtils.equals(providerInfo.getUniqueId(), uniqueId)) {
                    return i;
                }
            }
            return -1;
        }

        private void requestCreateSessionWithRouter2OnHandler(
                long uniqueRequestId,
                long managerRequestId,
                @NonNull RouterRecord routerRecord,
                @NonNull RoutingSessionInfo oldSession,
                @NonNull MediaRoute2Info route,
                @Nullable Bundle sessionHints) {

            final MediaRoute2Provider provider = findProvider(route.getProviderId());
            if (provider == null) {
                Slog.w(TAG, "requestCreateSessionWithRouter2OnHandler: Ignoring session "
                        + "creation request since no provider found for given route=" + route);
                routerRecord.notifySessionCreationFailed(toOriginalRequestId(uniqueRequestId));
                return;
            }

            SessionCreationRequest request =
                    new SessionCreationRequest(routerRecord, uniqueRequestId,
                            managerRequestId, oldSession, route);
            mSessionCreationRequests.add(request);

            int transferReason =
                    managerRequestId != MediaRoute2ProviderService.REQUEST_ID_NONE
                            ? RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST
                            : RoutingSessionInfo.TRANSFER_REASON_APP;

            provider.requestCreateSession(
                    uniqueRequestId,
                    routerRecord.mPackageName,
                    route.getOriginalId(),
                    sessionHints,
                    transferReason,
                    routerRecord.mUserRecord.mUserHandle,
                    routerRecord.mPackageName);
        }

        // routerRecord can be null if the session is system's or RCN.
        private void selectRouteOnHandler(long uniqueRequestId, @Nullable RouterRecord routerRecord,
                @NonNull String uniqueSessionId, @NonNull MediaRoute2Info route) {
            if (!checkArgumentsForSessionControl(routerRecord, uniqueSessionId, route,
                    "selecting", uniqueRequestId)) {
                return;
            }

            final String providerId = route.getProviderId();
            final MediaRoute2Provider provider = findProvider(providerId);
            if (provider == null) {
                return;
            }
            provider.selectRoute(
                    uniqueRequestId, getOriginalId(uniqueSessionId), route.getOriginalId());

            // Log the success result.
            mMediaRouterMetricLogger.logRequestResult(
                    uniqueRequestId, MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS);
        }

        // routerRecord can be null if the session is system's or RCN.
        private void deselectRouteOnHandler(long uniqueRequestId,
                @Nullable RouterRecord routerRecord,
                @NonNull String uniqueSessionId, @NonNull MediaRoute2Info route) {
            if (!checkArgumentsForSessionControl(routerRecord, uniqueSessionId, route,
                    "deselecting", uniqueRequestId)) {
                return;
            }

            final String providerId = route.getProviderId();
            final MediaRoute2Provider provider = findProvider(providerId);
            if (provider == null) {
                return;
            }

            provider.deselectRoute(
                    uniqueRequestId, getOriginalId(uniqueSessionId), route.getOriginalId());

            // Log the success result.
            mMediaRouterMetricLogger.logRequestResult(
                    uniqueRequestId, MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS);
        }

        // routerRecord can be null if the session is system's or RCN.
        private void transferToRouteOnHandler(
                long uniqueRequestId,
                @NonNull UserHandle transferInitiatorUserHandle,
                @NonNull String transferInitiatorPackageName,
                @Nullable RouterRecord routerRecord,
                @NonNull String uniqueSessionId,
                @NonNull MediaRoute2Info route,
                @RoutingSessionInfo.TransferReason int transferReason,
                @NonNull RoutingChangeInfo routingChangeInfo) {
            if (!checkArgumentsForSessionControl(routerRecord, uniqueSessionId, route,
                    "transferring to", uniqueRequestId)) {
                return;
            }

            final String providerId = route.getProviderId();
            final MediaRoute2Provider provider = findProvider(providerId);
            if (provider == null) {
                Slog.w(
                        TAG,
                        "Ignoring transferToRoute due to lack of matching provider for target: "
                                + route);
                return;
            }
            provider.transferToRoute(
                    uniqueRequestId,
                    transferInitiatorUserHandle,
                    transferInitiatorPackageName,
                    getOriginalId(uniqueSessionId),
                    route.getOriginalId(),
                    transferReason);

            // Log the success result.
            mMediaRouterMetricLogger.logRequestResult(
                    uniqueRequestId, MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS);
        }

        // routerRecord is null if and only if the session is created without the request, which
        // includes the system's session and RCN cases.
        private boolean checkArgumentsForSessionControl(@Nullable RouterRecord routerRecord,
                @NonNull String uniqueSessionId, @NonNull MediaRoute2Info route,
                @NonNull String description, long uniqueRequestId) {
            final String providerId = route.getProviderId();
            final MediaRoute2Provider provider = findProvider(providerId);
            if (provider == null) {
                Slog.w(TAG, "Ignoring " + description + " route since no provider found for "
                        + "given route=" + route);
                mMediaRouterMetricLogger.logRequestResult(
                        uniqueRequestId,
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND);
                return false;
            }

            // Bypass checking router if it's the system session (routerRecord should be null)
            if (TextUtils.equals(
                    getProviderId(uniqueSessionId), getSystemProvider().getUniqueId())) {
                return true;
            }

            RouterRecord matchingRecord = mSessionToRouterMap.get(uniqueSessionId);
            if (matchingRecord != routerRecord) {
                Slog.w(
                        TAG,
                        "Ignoring "
                                + description
                                + " route from non-matching router."
                                + " routerRecordPackageName="
                                + getPackageNameFromNullableRecord(routerRecord)
                                + " matchingRecordPackageName="
                                + getPackageNameFromNullableRecord(matchingRecord)
                                + " route="
                                + route);
                mMediaRouterMetricLogger.logRequestResult(
                        uniqueRequestId,
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTER_RECORD_NOT_FOUND);
                return false;
            }

            final String sessionId = getOriginalId(uniqueSessionId);
            if (sessionId == null) {
                Slog.w(TAG, "Failed to get original session id from unique session id. "
                        + "uniqueSessionId=" + uniqueSessionId);
                mMediaRouterMetricLogger.logRequestResult(
                        uniqueRequestId,
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_SESSION_ID);
                return false;
            }

            return true;
        }

        private void setRouteVolumeOnHandler(long uniqueRequestId, @NonNull MediaRoute2Info route,
                int volume) {
            final MediaRoute2Provider provider = findProvider(route.getProviderId());
            if (provider == null) {
                Slog.w(TAG, "setRouteVolumeOnHandler: Couldn't find provider for route=" + route);
                return;
            }
            provider.setRouteVolume(uniqueRequestId, route.getOriginalId(), volume);
        }

        private void setSessionVolumeOnHandler(long uniqueRequestId,
                @NonNull String uniqueSessionId, int volume) {
            final MediaRoute2Provider provider = findProvider(getProviderId(uniqueSessionId));
            if (provider == null) {
                Slog.w(TAG, "setSessionVolumeOnHandler: Couldn't find provider for session id="
                        + uniqueSessionId);
                return;
            }
            provider.setSessionVolume(uniqueRequestId, getOriginalId(uniqueSessionId), volume);
        }

        private void releaseSessionOnHandler(long uniqueRequestId,
                @Nullable RouterRecord routerRecord, @NonNull String uniqueSessionId) {
            final RouterRecord matchingRecord = mSessionToRouterMap.get(uniqueSessionId);
            if (matchingRecord != routerRecord) {
                Slog.w(
                        TAG,
                        "Ignoring releasing session from non-matching router."
                                + " routerRecordPackageName="
                                + getPackageNameFromNullableRecord(routerRecord)
                                + " matchingRecordPackageName="
                                + getPackageNameFromNullableRecord(matchingRecord)
                                + " uniqueSessionId="
                                + uniqueSessionId);
                return;
            }

            final String providerId = getProviderId(uniqueSessionId);
            if (providerId == null) {
                Slog.w(TAG, "Ignoring releasing session with invalid unique session ID. "
                        + "uniqueSessionId=" + uniqueSessionId);
                return;
            }

            final String sessionId = getOriginalId(uniqueSessionId);
            if (sessionId == null) {
                Slog.w(TAG, "Ignoring releasing session with invalid unique session ID. "
                        + "uniqueSessionId=" + uniqueSessionId + " providerId=" + providerId);
                return;
            }

            final MediaRoute2Provider provider = findProvider(providerId);
            if (provider == null) {
                Slog.w(TAG, "Ignoring releasing session since no provider found for given "
                        + "providerId=" + providerId);
                return;
            }

            provider.releaseSession(uniqueRequestId, sessionId);
            mMediaRouterMetricLogger.notifySessionEnd(sessionId);
        }

        private void onSessionCreatedOnHandler(
                @NonNull MediaRoute2Provider provider,
                long uniqueRequestId,
                @NonNull RoutingSessionInfo sessionInfo) {
            SessionCreationRequest matchingRequest = null;

            for (SessionCreationRequest request : mSessionCreationRequests) {
                if (request.mUniqueRequestId == uniqueRequestId
                        && TextUtils.equals(
                        request.mRoute.getProviderId(), provider.getUniqueId())) {
                    matchingRequest = request;
                    break;
                }
            }

            long managerRequestId = (matchingRequest == null)
                    ? MediaRoute2ProviderService.REQUEST_ID_NONE
                    : matchingRequest.mManagerRequestId;
            notifySessionCreatedToManagers(managerRequestId, sessionInfo);

            if (matchingRequest == null) {
                Slog.w(TAG, "Ignoring session creation result for unknown request. "
                        + "uniqueRequestId=" + uniqueRequestId + ", sessionInfo=" + sessionInfo);
                return;
            }

            mSessionCreationRequests.remove(matchingRequest);

            if (Flags.cleanUpDeadRouterRecordsAfterUnbinding()
                    && !mUserRecord.isRouterRecordBinded(matchingRequest.mRouterRecord)) {
                Slog.w(
                        TAG,
                        "Ignoring session creation request for unbound router:"
                                + matchingRequest.mRouterRecord.getDebugString());
                return;
            }

            // Not to show old session
            MediaRoute2Provider oldProvider =
                    findProvider(matchingRequest.mOldSession.getProviderId());
            if (oldProvider != null) {
                oldProvider.prepareReleaseSession(matchingRequest.mOldSession.getId());
            } else {
                Slog.w(TAG, "onSessionCreatedOnHandler: Can't find provider for an old session. "
                        + "session=" + matchingRequest.mOldSession);
            }

            mSessionToRouterMap.put(sessionInfo.getId(), matchingRequest.mRouterRecord);
            if (sessionInfo.isSystemSession()
                    && !matchingRequest.mRouterRecord.hasSystemRoutingPermission()) {
                // The router lacks permission to modify system routing, so we hide system routing
                // session info from them.
                sessionInfo = getSystemProvider().getDefaultSessionInfo();
            }
            matchingRequest.mRouterRecord.notifySessionCreated(
                    toOriginalRequestId(uniqueRequestId), sessionInfo);

            // Log the success result.
            mMediaRouterMetricLogger.logRequestResult(
                    uniqueRequestId, MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS);
            mMediaRouterMetricLogger.notifyRoutingChange(
                    uniqueRequestId, sessionInfo, matchingRequest.mRouterRecord.mUid);
        }

        /**
         * Implementation of {@link MediaRoute2Provider.Callback#onSessionUpdated}.
         *
         * <p>Must run on the thread that corresponds to this {@link UserHandler}.
         */
        private void onSessionInfoChangedOnHandler(
                @NonNull MediaRoute2Provider provider,
                @NonNull RoutingSessionInfo sessionInfo,
                Set<String> packageNamesWithRoutingSessionOverrides,
                boolean shouldShowVolumeUi) {
            List<ManagerRecord> managers = getManagerRecords();
            List<AppId> appsWithOverridesToReport = null;
            boolean isGlobalSession = TextUtils.isEmpty(sessionInfo.getClientPackageName());
            synchronized (mLock) {
                if (isGlobalSession
                        && !Objects.equals(
                                mUserRecord.mLastPackagesWithSystemOverridesLocked,
                                packageNamesWithRoutingSessionOverrides)) {
                    appsWithOverridesToReport =
                            mUserRecord.mapPackageNamesToAppIdList(
                                    packageNamesWithRoutingSessionOverrides);
                    mUserRecord.mLastPackagesWithSystemOverridesLocked.clear();
                    mUserRecord.mLastPackagesWithSystemOverridesLocked.addAll(
                            packageNamesWithRoutingSessionOverrides);
                }
            }
            for (ManagerRecord manager : managers) {
                if (Flags.enableMirroringInMediaRouter2()) {
                    if (appsWithOverridesToReport != null) {
                        manager.notifySystemSessionOverridesChanged(appsWithOverridesToReport);
                    }
                    String targetPackageName = manager.mTargetPackageName;
                    boolean skipDueToOverride =
                            targetPackageName != null
                                    && packageNamesWithRoutingSessionOverrides.contains(
                                            targetPackageName);
                    boolean sessionIsForTargetPackage =
                            isGlobalSession
                                    || TextUtils.equals(
                                            targetPackageName, sessionInfo.getClientPackageName());
                    if (skipDueToOverride || !sessionIsForTargetPackage) {
                        continue;
                    }
                }
                manager.notifySessionUpdated(sessionInfo, shouldShowVolumeUi);
            }

            // For system provider, notify all routers.
            if (provider == getSystemProvider()) {
                notifySessionInfoChangedToRouters(getRouterRecords(true), sessionInfo);
                notifySessionInfoChangedToRouters(
                        getRouterRecords(false), getSystemProvider().getDefaultSessionInfo());
                return;
            }

            RouterRecord routerRecord = mSessionToRouterMap.get(sessionInfo.getId());
            if (routerRecord == null) {
                Slog.w(TAG, "onSessionInfoChangedOnHandler: No matching router found for session="
                        + sessionInfo);
                return;
            }
            if (!Flags.cleanUpDeadRouterRecordsAfterUnbinding()
                    || mUserRecord.isRouterRecordBinded(routerRecord)) {
                notifySessionInfoChangedToRouters(Arrays.asList(routerRecord), sessionInfo);
            }
        }

        private void onSessionReleasedOnHandler(@NonNull MediaRoute2Provider provider,
                @NonNull RoutingSessionInfo sessionInfo) {
            List<ManagerRecord> managers = getManagerRecords();
            for (ManagerRecord manager : managers) {
                manager.notifySessionReleased(sessionInfo);
            }

            RouterRecord routerRecord = mSessionToRouterMap.get(sessionInfo.getId());
            if (routerRecord == null) {
                Slog.w(TAG, "onSessionReleasedOnHandler: No matching router found for session="
                        + sessionInfo);
                return;
            }

            if (Flags.cleanUpDeadRouterRecordsAfterUnbinding()) {
                if (mUserRecord.isRouterRecordBinded(routerRecord)) {
                    routerRecord.notifySessionReleased(sessionInfo);
                }
                mSessionToRouterMap.remove(sessionInfo.getId());
            } else {
                routerRecord.notifySessionReleased(sessionInfo);
            }
        }

        private void onRequestFailedOnHandler(@NonNull MediaRoute2Provider provider,
                long uniqueRequestId, int reason) {
            if (handleSessionCreationRequestFailed(provider, uniqueRequestId, reason)) {
                Slog.w(
                        TAG,
                        TextUtils.formatSimple(
                                "onRequestFailedOnHandler | Finished handling session creation"
                                    + " request failed for provider: %s, uniqueRequestId: %d,"
                                    + " reason: %d",
                                provider.getUniqueId(), uniqueRequestId, reason));
                return;
            }

            final int requesterId = toRequesterId(uniqueRequestId);
            ManagerRecord manager = findManagerWithId(requesterId);
            if (manager != null) {
                manager.notifyRequestFailed(toOriginalRequestId(uniqueRequestId), reason);
            }

            // Currently, only manager records can get notified of failures.
            // TODO(b/282936553): Notify regular routers of request failures.

            // Log the request result.
            mMediaRouterMetricLogger.logRequestResult(
                    uniqueRequestId, MediaRouterMetricLogger.convertResultFromReason(reason));
        }

        private boolean handleSessionCreationRequestFailed(
                @NonNull MediaRoute2Provider provider, long uniqueRequestId, int reason) {
            // Check whether the failure is about creating a session
            SessionCreationRequest matchingRequest = null;
            for (SessionCreationRequest request : mSessionCreationRequests) {
                if (request.mUniqueRequestId == uniqueRequestId && TextUtils.equals(
                        request.mRoute.getProviderId(), provider.getUniqueId())) {
                    matchingRequest = request;
                    break;
                }
            }

            if (matchingRequest == null) {
                // The failure is not about creating a session.
                Slog.w(
                        TAG,
                        TextUtils.formatSimple(
                                "handleSessionCreationRequestFailed | No matching request found for"
                                    + " provider: %s, uniqueRequestId: %d, reason: %d",
                                provider.getUniqueId(), uniqueRequestId, reason));
                return false;
            }

            mSessionCreationRequests.remove(matchingRequest);

            if (Flags.cleanUpDeadRouterRecordsAfterUnbinding()
                    && !mUserRecord.isRouterRecordBinded(matchingRequest.mRouterRecord)) {
                Slog.w(
                        TAG,
                        "handleSessionCreationRequestFailed | Ignoring with unbound router:"
                                + matchingRequest.mRouterRecord.getDebugString());
                return false;
            }

            // Notify the requester about the failure.
            // The call should be made by either MediaRouter2 or MediaRouter2Manager.
            if (matchingRequest.mManagerRequestId == MediaRouter2Manager.REQUEST_ID_NONE) {
                matchingRequest.mRouterRecord.notifySessionCreationFailed(
                        toOriginalRequestId(uniqueRequestId));
            } else {
                final int requesterId = toRequesterId(matchingRequest.mManagerRequestId);
                ManagerRecord manager = findManagerWithId(requesterId);
                if (manager != null) {
                    manager.notifyRequestFailed(
                            toOriginalRequestId(matchingRequest.mManagerRequestId), reason);
                }
            }
            return true;
        }

        private List<RouterRecord> getRouterRecords() {
            synchronized (mLock) {
                return new ArrayList<>(mUserRecord.mRouterRecords);
            }
        }

        private List<RouterRecord> getRouterRecords(boolean hasSystemRoutingPermission) {
            List<RouterRecord> routerRecords = new ArrayList<>();
            synchronized (mLock) {
                for (RouterRecord routerRecord : mUserRecord.mRouterRecords) {
                    if (hasSystemRoutingPermission
                            == routerRecord.hasSystemRoutingPermission()) {
                        routerRecords.add(routerRecord);
                    }
                }
                return routerRecords;
            }
        }

        private List<ManagerRecord> getManagerRecords() {
            synchronized (mLock) {
                return new ArrayList<>(mUserRecord.mManagerRecords);
            }
        }

        private void notifyRouterRegistered(@NonNull RouterRecord routerRecord) {
            List<MediaRoute2Info> currentRoutes = new ArrayList<>();

            MediaRoute2ProviderInfo systemProviderInfo = null;
            for (MediaRoute2ProviderInfo providerInfo : mLastProviderInfos) {
                // TODO: Create MediaRoute2ProviderInfo#isSystemProvider()
                if (TextUtils.equals(
                        providerInfo.getUniqueId(), getSystemProvider().getUniqueId())) {
                    // Adding routes from system provider will be handled below, so skip it here.
                    systemProviderInfo = providerInfo;
                    continue;
                }
                currentRoutes.addAll(providerInfo.getRoutes());
            }

            RoutingSessionInfo currentSystemSessionInfo;
            if (routerRecord.hasSystemRoutingPermission()) {
                if (systemProviderInfo != null) {
                    currentRoutes.addAll(systemProviderInfo.getRoutes());
                } else {
                    // This shouldn't happen.
                    Slog.wtf(TAG, "System route provider not found.");
                }
                currentSystemSessionInfo = getSystemProvider().getSessionInfos().get(0);
            } else {
                currentRoutes.add(getSystemProvider().getDefaultRoute());
                currentSystemSessionInfo = getSystemProvider().getDefaultSessionInfo();
            }

            if (!currentRoutes.isEmpty()) {
                routerRecord.notifyRegistered(currentRoutes, currentSystemSessionInfo);
            }
        }

        private static void notifyRoutesUpdatedToRouterRecords(
                @NonNull List<RouterRecord> routerRecords,
                @NonNull List<MediaRoute2Info> routes) {
            for (RouterRecord routerRecord : routerRecords) {
                routerRecord.notifyRoutesUpdated(routes);
            }
        }

        private void notifySessionInfoChangedToRouters(
                @NonNull List<RouterRecord> routerRecords,
                @NonNull RoutingSessionInfo sessionInfo) {
            for (RouterRecord routerRecord : routerRecords) {
                routerRecord.notifySessionInfoChanged(sessionInfo);
            }
        }

        private void notifySessionCreatedToManagers(
                long managerRequestId, @NonNull RoutingSessionInfo session) {
            int requesterId = toRequesterId(managerRequestId);
            int originalRequestId = toOriginalRequestId(managerRequestId);

            for (ManagerRecord manager : getManagerRecords()) {
                int requestId =
                        manager.mManagerId == requesterId
                                ? originalRequestId
                                : MediaRouter2Manager.REQUEST_ID_NONE;
                manager.notifySessionCreated(requestId, session);
            }
        }

        private void notifyDiscoveryPreferenceChangedToManagers(@NonNull String routerPackageName,
                @Nullable RouteDiscoveryPreference discoveryPreference) {
            synchronized (mLock) {
                for (ManagerRecord managerRecord : mUserRecord.mManagerRecords) {
                    managerRecord.notifyDiscoveryPreferenceChanged(
                            routerPackageName, discoveryPreference);
                }
            }
        }

        private void notifyRouteListingPreferenceChangeToManagers(
                String routerPackageName, @Nullable RouteListingPreference routeListingPreference) {
            synchronized (mLock) {
                for (ManagerRecord managerRecord : mUserRecord.mManagerRecords) {
                    managerRecord.notifyRouteListingPreferenceChange(
                            routerPackageName, routeListingPreference);
                }
            }
        }

        private void notifyDeviceSuggestionsClearedOnHandler(
                String routerPackageName, Set<String> suggestingPackages) {
            for (String suggestingPackage : suggestingPackages) {
                notifyDeviceSuggestionsUpdatedOnHandler(
                        routerPackageName, suggestingPackage, /* suggestedDeviceInfo= */ null);
            }
        }

        private void notifyDeviceSuggestionsUpdatedOnHandler(
                String routerPackageName,
                String suggestingPackageName,
                @Nullable List<SuggestedDeviceInfo> suggestedDeviceInfo) {
            synchronized (mLock) {
                for (ManagerRecord managerRecord : mUserRecord.mManagerRecords) {
                    if (TextUtils.equals(managerRecord.mTargetPackageName, routerPackageName)) {
                        managerRecord.notifyDeviceSuggestionsUpdated(
                                routerPackageName, suggestingPackageName, suggestedDeviceInfo);
                    }
                }
                for (RouterRecord routerRecord : mUserRecord.mRouterRecords) {
                    if (TextUtils.equals(routerRecord.mPackageName, routerPackageName)) {
                        routerRecord.notifyDeviceSuggestionsUpdated(
                                suggestingPackageName, suggestedDeviceInfo);
                    }
                }
            }
        }

        private void notifyDeviceSuggestionRequestedOnHandler(String routerPackageName) {
            synchronized (mLock) {
                for (ManagerRecord managerRecord : mUserRecord.mManagerRecords) {
                    if (TextUtils.equals(managerRecord.mTargetPackageName, routerPackageName)) {
                        managerRecord.notifyDeviceSuggestionRequested();
                    }
                }
                for (RouterRecord routerRecord : mUserRecord.mRouterRecords) {
                    if (TextUtils.equals(routerRecord.mPackageName, routerPackageName)) {
                        routerRecord.notifyDeviceSuggestionRequested();
                    }
                }
            }
        }

        private void updateDiscoveryPreferenceOnHandler() {
            List<RouterRecord> activeRouterRecords;
            List<RouterRecord> allRouterRecords = getRouterRecords();

            boolean areManagersScanning = areManagersScanning(getManagerRecords());

            if (areManagersScanning) {
                activeRouterRecords = allRouterRecords;
            } else {
                activeRouterRecords = getIndividuallyActiveRouters(allRouterRecords);
            }

            Set<String> activelyScanningPackages = new HashSet<>();
            RouteDiscoveryPreference newPreference =
                    buildCompositeDiscoveryPreference(
                            activeRouterRecords, areManagersScanning, activelyScanningPackages);

            Slog.i(
                    TAG,
                    TextUtils.formatSimple(
                            "Updating composite discovery preference | preference: %s, active"
                                    + " routers: %s",
                            newPreference, activelyScanningPackages));

            Map<String, RouteDiscoveryPreference> perAppPreferences = new HashMap<>();
            for (RouterRecord record : activeRouterRecords) {
                perAppPreferences.put(record.mPackageName, record.mDiscoveryPreference);
            }
            if (updateScanningOnUserRecord(
                    activelyScanningPackages, newPreference, perAppPreferences)) {
                updateDiscoveryPreferenceForProviders(activelyScanningPackages);
            }
        }

        private void updateDiscoveryPreferenceForProviders(Set<String> activelyScanningPackages) {
            for (MediaRoute2Provider provider : mRouteProviders) {
                provider.updateDiscoveryPreference(
                        activelyScanningPackages, mUserRecord.mCompositeDiscoveryPreference,
                        mUserRecord.mPerAppPreferences);
            }
        }

        private boolean updateScanningOnUserRecord(
                Set<String> activelyScanningPackages,
                RouteDiscoveryPreference newPreference,
                Map<String, RouteDiscoveryPreference> perAppPreferences) {
            synchronized (mLock) {
                if (newPreference.equals(mUserRecord.mCompositeDiscoveryPreference)
                        && perAppPreferences.equals(mUserRecord.mPerAppPreferences)
                        && activelyScanningPackages.equals(mUserRecord.mActivelyScanningPackages)) {
                    return false;
                }

                var oldShouldPerformActiveScan =
                        mUserRecord.mCompositeDiscoveryPreference.shouldPerformActiveScan();
                var newShouldPerformActiveScan = newPreference.shouldPerformActiveScan();
                if (oldShouldPerformActiveScan != newShouldPerformActiveScan) {
                    // State access is synchronized with service.mLock.
                    // Linter still fails due to b/323906305#comment3
                    mMediaRouterMetricLogger.updateScanningState(newShouldPerformActiveScan);
                }

                mUserRecord.mCompositeDiscoveryPreference = newPreference;
                mUserRecord.mPerAppPreferences = perAppPreferences;
                mUserRecord.mActivelyScanningPackages = activelyScanningPackages;
            }
            return true;
        }

        /**
         * Returns a composite {@link RouteDiscoveryPreference} that aggregates every router
         * record's individual discovery preference.
         *
         * <p>The {@link RouteDiscoveryPreference#shouldPerformActiveScan() active scan value} of
         * the composite discovery preference is true if one of the router records is actively
         * scanning or if {@code shouldForceActiveScan} is true.
         *
         * <p>The composite RouteDiscoveryPreference is used to query route providers once to obtain
         * all the routes of interest, which can be subsequently filtered for the individual
         * discovery preferences.
         */
        @NonNull
        private static RouteDiscoveryPreference buildCompositeDiscoveryPreference(
                List<RouterRecord> activeRouterRecords,
                boolean shouldForceActiveScan,
                Set<String> activelyScanningPackages) {
            Set<String> preferredFeatures = new HashSet<>();
            boolean activeScan = false;
            for (RouterRecord activeRouterRecord : activeRouterRecords) {
                RouteDiscoveryPreference preference = activeRouterRecord.mDiscoveryPreference;
                preferredFeatures.addAll(preference.getPreferredFeatures());

                boolean isRouterRecordActivelyScanning =
                        (activeRouterRecord.isActivelyScanning() || shouldForceActiveScan)
                                && !preference.getPreferredFeatures().isEmpty();

                if (isRouterRecordActivelyScanning) {
                    activeScan = true;
                    activelyScanningPackages.add(activeRouterRecord.mPackageName);
                }
            }
            return new RouteDiscoveryPreference.Builder(
                            List.copyOf(preferredFeatures), activeScan || shouldForceActiveScan)
                    .build();
        }

        @NonNull
        private List<RouterRecord> getIndividuallyActiveRouters(
                List<RouterRecord> allRouterRecords) {
            if (!mPowerManager.isInteractive() && !Flags.enableScreenOffScanning()) {
                return Collections.emptyList();
            }

            return allRouterRecords.stream()
                    .filter(
                            record ->
                                    isPackageImportanceSufficientForScanning(record.mPackageName)
                                            || record.mScanningState
                                                    == SCANNING_STATE_SCANNING_FULL)
                    .collect(Collectors.toList());
        }

        private boolean areManagersScanning(List<ManagerRecord> managerRecords) {
            if (!mPowerManager.isInteractive() && !Flags.enableScreenOffScanning()) {
                return false;
            }

            return managerRecords.stream()
                    .anyMatch(
                            manager ->
                                    (manager.mScanningState == SCANNING_STATE_WHILE_INTERACTIVE
                                                    && isPackageImportanceSufficientForScanning(
                                                            manager.mOwnerPackageName))
                                            || manager.mScanningState
                                                    == SCANNING_STATE_SCANNING_FULL);
        }

        private boolean isPackageImportanceSufficientForScanning(String packageName) {
            return mActivityManager.getPackageImportance(packageName)
                    <= REQUIRED_PACKAGE_IMPORTANCE_FOR_SCANNING;
        }

        private MediaRoute2Provider findProvider(@Nullable String providerId) {
            for (MediaRoute2Provider provider : mRouteProviders) {
                if (TextUtils.equals(provider.getUniqueId(), providerId)) {
                    return provider;
                }
            }
            return null;
        }
    }

    static final class SessionCreationRequest {
        public final RouterRecord mRouterRecord;
        public final long mUniqueRequestId;
        public final long mManagerRequestId;
        public final RoutingSessionInfo mOldSession;
        public final MediaRoute2Info mRoute;

        SessionCreationRequest(@NonNull RouterRecord routerRecord, long uniqueRequestId,
                long managerRequestId, @NonNull RoutingSessionInfo oldSession,
                @NonNull MediaRoute2Info route) {
            mRouterRecord = routerRecord;
            mUniqueRequestId = uniqueRequestId;
            mManagerRequestId = managerRequestId;
            mOldSession = oldSession;
            mRoute = route;
        }

        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "SessionCreationRequest");

            String indent = prefix + "  ";

            pw.println(indent + "mUniqueRequestId=" + mUniqueRequestId);
            pw.println(indent + "mManagerRequestId=" + mManagerRequestId);
            mOldSession.dump(pw, indent);
            mRoute.dump(pw, prefix);
        }
    }
}
