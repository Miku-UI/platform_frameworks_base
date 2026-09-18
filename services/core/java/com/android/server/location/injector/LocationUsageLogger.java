/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.location.injector;

import static com.android.server.location.LocationManagerService.TAG;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Geofence;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.location.flags.Flags;
import android.os.SystemClock;
import android.os.UserHandle;
import android.stats.location.LocationStatsEnums;
import android.util.Log;
import android.util.LongArrayQueue;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;

import java.time.Instant;
import java.util.ArrayDeque;

/**
 * Logger for Location API usage logging.
 */
public class LocationUsageLogger {

    public static final int GRACE_PERIOD_MILLIS = 30_000;

    private static final int ONE_SEC_IN_MILLIS = 1000;
    private static final int ONE_MINUTE_IN_MILLIS = 60000;
    private static final int ONE_HOUR_IN_MILLIS = 3600000;

    private static final int API_USAGE_LOG_HOURLY_CAP = 60;

    @GuardedBy("this")
    private long mLastApiUsageLogHour = 0;
    @GuardedBy("this")
    private int mApiUsageLogHourlyCount = 0;

    // Tracking Location enabled state changes
    @GuardedBy("this")
    private boolean mIsLocationEnabled = false;

    // Tracking emergency session state changes
    @GuardedBy("this")
    private boolean mIsInEmergency = false;

    private final ActivityManager mActivityManager;
    private final PackageManager mPackageManager;
    private final Context mContext;

    // Detailed class for Location AppOps, including state snapshots
    private static class AppOpNotedEvent {
        final long mElapsedRealtimeMillis;
        final String mOpCode;
        final String mPackageName;
        final String mAttributionTag;
        final int mAppOpsFlags;
        final int mAppOpsResult;
        final boolean mIsForeground;
        final int mAppProcessState;
        final int mLocationPermissionLevel;
        final boolean mHasFinePermission;
        final boolean mHasCoarsePermission;
        final boolean mHasBackgroundPermission;
        final boolean mHasBypassPermission;
        final long mLastMlsOnTimeMillis;
        final long mLastEmergencyOnTimeMillis;

        AppOpNotedEvent(long elapsedRealtimeMillis, String opCode, String packageName,
                String attributionTag, int appOpsFlags, int appOpsResult,
                boolean isForeground,
                int appProcessState,
                int locationPermissionLevel,
                boolean hasFinePermission,
                boolean hasCoarsePermission,
                boolean hasBackgroundPermission,
                boolean hasBypassPermission,
                long lastMlsOn, long lastEmergencyOn) {
            this.mElapsedRealtimeMillis = elapsedRealtimeMillis;
            this.mOpCode = opCode;
            this.mPackageName = packageName;
            this.mAttributionTag = attributionTag;
            this.mAppOpsFlags = appOpsFlags;
            this.mAppOpsResult = appOpsResult;
            this.mIsForeground = isForeground;
            this.mAppProcessState = appProcessState;
            this.mLocationPermissionLevel = locationPermissionLevel;
            this.mHasFinePermission = hasFinePermission;
            this.mHasCoarsePermission = hasCoarsePermission;
            this.mHasBackgroundPermission = hasBackgroundPermission;
            this.mHasBypassPermission = hasBypassPermission;
            this.mLastMlsOnTimeMillis = lastMlsOn;
            this.mLastEmergencyOnTimeMillis = lastEmergencyOn;
        }
    }

    @GuardedBy("this")
    private final ArrayDeque<AppOpNotedEvent> mEvents = new ArrayDeque<>();

    // Tracking the last time things were toggled from ON to OFF for "Before" window checks
    @GuardedBy("this") private long mLastMlsToggledOffTimeMillis = 0;
    @GuardedBy("this") private long mLastEmergencyToggledOffTimeMillis = 0;

    // State transition histories: Timestamp when turned ON
    @GuardedBy("this")
    private final LongArrayQueue mMlsHistory = new LongArrayQueue();
    @GuardedBy("this")
    private final LongArrayQueue mEmergencyHistory = new LongArrayQueue();

    private final MockableSystemClock mSystemClock;

    private final MockableStatsLog mStatsLog;

    /** Interface for abstracting SystemClock for testability. */
    @VisibleForTesting
    interface MockableSystemClock {
        /** Returns the elapsed realtime time in milliseconds. */
        long elapsedRealtime();
    }

    /** Production implementation of MockableSystemClock. */
    private static class SystemClockWrapper implements MockableSystemClock {
        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }
    }

    /** Interface for abstracting FrameworkStatsLog for testability. */
    @VisibleForTesting
    interface MockableStatsLog {
        /** Writes a location app op noted event. */
        void write(
                int eventCode,
                String opCode, String packageName,
                String attributionTag, int appOpsFlags, int appOpsResult,
                boolean isForeground,
                int appProcessState,
                int locationPermissionLevel,
                boolean hasFinePermission,
                boolean hasCoarsePermission,
                boolean hasBackgroundPermission,
                boolean hasBypassPermission,
                int gracePeriodMillis, boolean inGraceWindow);
    }

    /** Production implementation of MockableStatsLog. */
    private static class FrameworkStatsLogWrapper implements MockableStatsLog {
        @Override
        public void write(
                int eventCode,
                String opCode, String packageName,
                String attributionTag, int appOpsFlags, int appOpsResult,
                boolean isForeground,
                int appProcessState,
                int locationPermissionLevel,
                boolean hasFinePermission,
                boolean hasCoarsePermission,
                boolean hasBackgroundPermission,
                boolean hasBypassPermission,
                int gracePeriodMillis, boolean inGraceWindow) {
            FrameworkStatsLog.write(
                    FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED,
                    opCode,
                    packageName,
                    attributionTag,
                    appOpsFlags,
                    appOpsResult,
                    isForeground,
                    appProcessState,
                    locationPermissionLevel,
                    hasFinePermission,
                    hasCoarsePermission,
                    hasBackgroundPermission,
                    hasBypassPermission,
                    gracePeriodMillis, inGraceWindow);
        }
    }

    public LocationUsageLogger(Context context) {
        this(context, new SystemClockWrapper(), new FrameworkStatsLogWrapper());
    }

    @VisibleForTesting
    LocationUsageLogger(Context context, MockableSystemClock systemClock,
            MockableStatsLog statsLog) {
        mContext = context;
        mActivityManager = context.getSystemService(ActivityManager.class);
        mPackageManager = context.getPackageManager();
        mSystemClock = systemClock;
        mStatsLog = statsLog;
    }

    /**
     * Log a location API usage event.
     */
    public void logLocationApiUsage(int usageType, int apiInUse,
            String packageName, String attributionTag, String provider,
            LocationRequest locationRequest, boolean hasListener,
            boolean hasIntent, Geofence geofence, boolean foreground) {
        try {
            if (hitApiUsageLogCap()) {
                return;
            }

            boolean isLocationRequestNull = locationRequest == null;
            boolean isGeofenceNull = geofence == null;

            FrameworkStatsLog.write(FrameworkStatsLog.LOCATION_MANAGER_API_USAGE_REPORTED,
                    usageType, apiInUse, packageName,
                    isLocationRequestNull
                        ? LocationStatsEnums.PROVIDER_UNKNOWN
                        : bucketizeProvider(provider),
                    isLocationRequestNull
                        ? LocationStatsEnums.QUALITY_UNKNOWN
                        : locationRequest.getQuality(),
                    isLocationRequestNull
                        ? LocationStatsEnums.INTERVAL_UNKNOWN
                        : bucketizeInterval(locationRequest.getIntervalMillis()),
                    isLocationRequestNull
                        ? LocationStatsEnums.DISTANCE_UNKNOWN
                        : bucketizeDistance(
                                locationRequest.getMinUpdateDistanceMeters()),
                    isLocationRequestNull ? 0 : locationRequest.getMaxUpdates(),
                    // only log expireIn for USAGE_STARTED
                    isLocationRequestNull || usageType == LocationStatsEnums.USAGE_ENDED
                        ? LocationStatsEnums.EXPIRATION_UNKNOWN
                        : bucketizeExpireIn(locationRequest.getDurationMillis()),
                    getCallbackType(apiInUse, hasListener, hasIntent),
                    isGeofenceNull
                        ? LocationStatsEnums.RADIUS_UNKNOWN
                        : bucketizeRadius(geofence.getRadius()),
                    categorizeActivityImportance(foreground),
                    attributionTag);
        } catch (Exception e) {
            // Swallow exceptions to avoid crashing LMS.
            Log.w(TAG, "Failed to log API usage to statsd.", e);
        }
    }

    /**
     * Log a location API usage event.
     */
    public void logLocationApiUsage(int usageType, int apiInUse, String providerName) {
        try {
            if (hitApiUsageLogCap()) {
                return;
            }

            FrameworkStatsLog.write(FrameworkStatsLog.LOCATION_MANAGER_API_USAGE_REPORTED,
                    usageType, apiInUse,
                    /* package_name= */ null,
                    bucketizeProvider(providerName),
                    LocationStatsEnums.QUALITY_UNKNOWN,
                    LocationStatsEnums.INTERVAL_UNKNOWN,
                    LocationStatsEnums.DISTANCE_UNKNOWN,
                    /* numUpdates= */ 0,
                    LocationStatsEnums.EXPIRATION_UNKNOWN,
                    getCallbackType(
                            apiInUse,
                            /* isListenerNull= */ true,
                            /* isIntentNull= */ true),
                    /* bucketizedRadius= */ 0,
                    LocationStatsEnums.IMPORTANCE_UNKNOWN,
                    /* attribution_tag */ null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to log API usage to statsd.", e);
        }
    }

    /**
     * Logs a location op noted event if it is a potential MLS violation.
     *
     * <p>This method is called when a locationOp is noted. If MLS is disabled in non-emergency
     * mode, it logs the event to statsd to detect potential MLS violations. A grace period is
     * provided for MLS to be enabled before logging a possible violation, to account for race
     * conditions.
     *
     * @param opCode The specific app operation code that was noted (e.g.,
     *     AppOpsManager.OP_FINE_LOCATION).
     * @param uid The UID of the app that received the location.
     * @param packageName The package name of the app that received the location.
     * @param attributionTag An optional string provided by the application to further specify the
     *     source of the operation within its package.
     * @param appOpsFlags Flags from AppOpsManager.OnNotedCallback. This is a bitmask of OP_FLAG_*
     *     constants defined in AppOpsManager.OpFlags, indicating whether the operation is on behalf
     *     of the app itself (OP_FLAG_SELF), or proxied by a trusted (OP_FLAG_TRUSTED_PROXY) or
     *     untrusted (OP_FLAG_UNTRUSTED_PROXY) app, or blamed on the app by a trusted
     *     (OP_FLAG_TRUSTED_PROXIED) or untrusted (OP_FLAG_UNTRUSTED_PROXIED) app. See {@link
     *     android.app.AppOpsManager.OpFlags} for more details.
     * @param appOpsResult Result from AppOpsManager.OnNotedCallback. This is one of the MODE_*
     *     constants defined in AppOpsManager.Mode, indicating the result of the operation. Possible
     *     values include MODE_ALLOWED, MODE_IGNORED (silently fail), MODE_ERRORED (fatal error),
     *     MODE_DEFAULT (use default security check), and MODE_FOREGROUND (allow only when app is in
     *     foreground). See {@link android.app.AppOpsManager.Mode} for more details.
     */
    public synchronized void logLocationOpNoted(
            @NonNull String opCode,
            int uid,
            @NonNull String packageName,
            @Nullable String attributionTag,
            int appOpsFlags,
            int appOpsResult) {

        if (!Flags.locationAuditing()) {
            return;
        }

        // Only log the violation
        if (mIsLocationEnabled || mIsInEmergency) {
            return;
        }

        final int appProcessState = mActivityManager.getUidProcessState(uid);
        final boolean isForeground = AppForegroundHelper.isForeground(appProcessState);

        boolean hasFinePermission =
                mContext.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, -1, uid)
                        == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarsePermission =
                mContext.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, -1, uid)
                        == PackageManager.PERMISSION_GRANTED;
        boolean hasBackgroundPermission =
                mContext.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, -1, uid)
                        == PackageManager.PERMISSION_GRANTED;
        boolean hasBypassPermission =
                mContext.checkPermission(Manifest.permission.LOCATION_BYPASS, -1, uid)
                        == PackageManager.PERMISSION_GRANTED;

        int locationPermissionLevel =
                getLocationPermissionLevel(
                        mPackageManager,
                        uid,
                        packageName,
                        hasFinePermission,
                        hasCoarsePermission,
                        hasBackgroundPermission);

        mEvents.add(new AppOpNotedEvent(mSystemClock.elapsedRealtime(),
                opCode,
                packageName,
                attributionTag,
                appOpsFlags,
                appOpsResult,
                isForeground,
                appProcessState,
                locationPermissionLevel,
                hasFinePermission,
                hasCoarsePermission,
                hasBackgroundPermission,
                hasBypassPermission,
                mLastMlsToggledOffTimeMillis,
                mLastEmergencyToggledOffTimeMillis));

        processEvents();
    }

    private synchronized void processEvents() {
        long now = mSystemClock.elapsedRealtime();

        // Prune history older than 2 grace periods to save memory
        long cutoff = now - 2 * GRACE_PERIOD_MILLIS;
        pruneHistory(mMlsHistory, cutoff);
        pruneHistory(mEmergencyHistory, cutoff);

        // Audit matured events
        while (!mEvents.isEmpty()
                && now - mEvents.peek().mElapsedRealtimeMillis >= GRACE_PERIOD_MILLIS) {
            AppOpNotedEvent event = mEvents.poll();
            logMlsViolation(event, inGraceWindow(event));
        }
    }

    private static void pruneHistory(LongArrayQueue history, long cutoff) {
        while (history.size() != 0 && history.peekFirst() < cutoff) {
            long ignored = history.removeFirst();
        }
    }

    private synchronized boolean inGraceWindow(AppOpNotedEvent event) {
        // 1. Check "Before" window: Was either state ON within the grace period before the event?
        boolean mlsOnWithinBeforeWindow =
                (event.mElapsedRealtimeMillis - event.mLastMlsOnTimeMillis < GRACE_PERIOD_MILLIS);
        boolean emergencyOnWithinBeforeWindow =
                (event.mElapsedRealtimeMillis - event.mLastEmergencyOnTimeMillis
                        < GRACE_PERIOD_MILLIS);

        // 2. Check "After" window: Did either state turn ON within grace period after the event?
        boolean mlsOnWithinAfterWindow =
                hasActiveTransition(event.mElapsedRealtimeMillis, mMlsHistory);
        boolean emergencyOnWithinAfterWindow =
                hasActiveTransition(event.mElapsedRealtimeMillis, mEmergencyHistory);

        return (mlsOnWithinBeforeWindow || emergencyOnWithinBeforeWindow
                || mlsOnWithinAfterWindow || emergencyOnWithinAfterWindow);
    }

    private static boolean hasActiveTransition(long timestamp, LongArrayQueue history) {
        // Elements are chronologically ordered, so we just iterate from the oldest
        for (int i = 0; i < history.size(); i++) {
            long next = history.get(i);
            if (next > timestamp) {
                return next <= timestamp + GRACE_PERIOD_MILLIS;
            }
        }
        return false;
    }

    private void logMlsViolation(AppOpNotedEvent event, boolean inGraceWindow) {
        mStatsLog.write(
                FrameworkStatsLog.LOCATION_MLS_VIOLATION_OBSERVED,
                event.mOpCode,
                event.mPackageName,
                event.mAttributionTag,
                event.mAppOpsFlags,
                event.mAppOpsResult,
                event.mIsForeground,
                event.mAppProcessState,
                event.mLocationPermissionLevel,
                event.mHasFinePermission,
                event.mHasCoarsePermission,
                event.mHasBackgroundPermission,
                event.mHasBypassPermission,
                GRACE_PERIOD_MILLIS,
                inGraceWindow);
    }


    /**
     * Returns the location permission level for the given app.
     *
     * <p>This is one of the LOCATION_PERMISSION_LEVEL_* constants defined in LocationStatsEnums,
     * which indicate whether the app has one-time, while-in-use, or always-on location permissions.
     */
    private static int getLocationPermissionLevel(
            PackageManager packageManager,
            int uid,
            String packageName,
            boolean hasFinePermission,
            boolean hasCoarsePermission,
            boolean hasBackgroundPermission) {
        int locationPermissionLevel = LocationStatsEnums.LOCATION_PERMISSION_LEVEL_NONE;
        if (hasBackgroundPermission) {
            locationPermissionLevel = LocationStatsEnums.LOCATION_PERMISSION_LEVEL_ALWAYS_ON;
        } else if (hasFinePermission || hasCoarsePermission) {
            int permFlags;
            // It is not possible to have both one-time fine and coarse permissions, so we only need
            // to check one.
            if (hasFinePermission) {
                permFlags =
                        packageManager.getPermissionFlags(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                packageName,
                                UserHandle.getUserHandleForUid(uid));
            } else { // hasCoarsePermission
                permFlags =
                        packageManager.getPermissionFlags(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                packageName,
                                UserHandle.getUserHandleForUid(uid));
            }
            if ((permFlags & PackageManager.FLAG_PERMISSION_ONE_TIME) != 0) {
                locationPermissionLevel =
                        LocationStatsEnums.LOCATION_PERMISSION_LEVEL_ONLY_THIS_TIME;
            } else {
                locationPermissionLevel = LocationStatsEnums.LOCATION_PERMISSION_LEVEL_WHILE_IN_USE;
            }
        }
        return locationPermissionLevel;
    }


    /** Log a location enabled state change event. */
    public synchronized void logLocationEnabledStateChanged(boolean enabled) {
        if (Flags.locationAuditing()) {
            long nowMillis = mSystemClock.elapsedRealtime();
            if (mIsLocationEnabled && !enabled) {
                mLastMlsToggledOffTimeMillis = nowMillis;
            }

            mIsLocationEnabled = enabled;

            if (enabled) {
                mMlsHistory.addLast(nowMillis);
            }
            processEvents();
        }

        FrameworkStatsLog.write(FrameworkStatsLog.LOCATION_ENABLED_STATE_CHANGED, enabled);
    }

    /**
     * Log emergency location state change event
     */
    public synchronized void logEmergencyStateChanged(boolean isInEmergency) {
        if (Flags.locationAuditing()) {
            long nowMillis = mSystemClock.elapsedRealtime();
            if (mIsInEmergency && !isInEmergency) {
                mLastEmergencyToggledOffTimeMillis = nowMillis;
            }

            mIsInEmergency = isInEmergency;

            if (isInEmergency) {
                mEmergencyHistory.addLast(nowMillis);
            }
            processEvents();
        }

        FrameworkStatsLog.write(FrameworkStatsLog.EMERGENCY_STATE_CHANGED, isInEmergency);
    }

    private static int bucketizeProvider(String provider) {
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
            return LocationStatsEnums.PROVIDER_NETWORK;
        } else if (LocationManager.GPS_PROVIDER.equals(provider)) {
            return LocationStatsEnums.PROVIDER_GPS;
        } else if (LocationManager.PASSIVE_PROVIDER.equals(provider)) {
            return LocationStatsEnums.PROVIDER_PASSIVE;
        } else if (LocationManager.FUSED_PROVIDER.equals(provider)) {
            return LocationStatsEnums.PROVIDER_FUSED;
        } else {
            return LocationStatsEnums.PROVIDER_UNKNOWN;
        }
    }

    private static int bucketizeInterval(long interval) {
        if (interval < ONE_SEC_IN_MILLIS) {
            return LocationStatsEnums.INTERVAL_BETWEEN_0_SEC_AND_1_SEC;
        } else if (interval < ONE_SEC_IN_MILLIS * 5) {
            return LocationStatsEnums.INTERVAL_BETWEEN_1_SEC_AND_5_SEC;
        } else if (interval < ONE_MINUTE_IN_MILLIS) {
            return LocationStatsEnums.INTERVAL_BETWEEN_5_SEC_AND_1_MIN;
        } else if (interval < ONE_MINUTE_IN_MILLIS * 10) {
            return LocationStatsEnums.INTERVAL_BETWEEN_1_MIN_AND_10_MIN;
        } else if (interval < ONE_HOUR_IN_MILLIS) {
            return LocationStatsEnums.INTERVAL_BETWEEN_10_MIN_AND_1_HOUR;
        } else {
            return LocationStatsEnums.INTERVAL_LARGER_THAN_1_HOUR;
        }
    }

    private static int bucketizeDistance(float smallestDisplacement) {
        if (smallestDisplacement <= 0) {
            return LocationStatsEnums.DISTANCE_ZERO;
        } else if (smallestDisplacement > 0 && smallestDisplacement <= 100) {
            return LocationStatsEnums.DISTANCE_BETWEEN_0_AND_100;
        } else {
            return LocationStatsEnums.DISTANCE_LARGER_THAN_100;
        }
    }

    private static int bucketizeRadius(float radius) {
        if (radius < 0) {
            return LocationStatsEnums.RADIUS_NEGATIVE;
        } else if (radius < 100) {
            return LocationStatsEnums.RADIUS_BETWEEN_0_AND_100;
        } else if (radius < 200) {
            return LocationStatsEnums.RADIUS_BETWEEN_100_AND_200;
        } else if (radius < 300) {
            return LocationStatsEnums.RADIUS_BETWEEN_200_AND_300;
        } else if (radius < 1000) {
            return LocationStatsEnums.RADIUS_BETWEEN_300_AND_1000;
        } else if (radius < 10000) {
            return LocationStatsEnums.RADIUS_BETWEEN_1000_AND_10000;
        } else {
            return LocationStatsEnums.RADIUS_LARGER_THAN_100000;
        }
    }

    private static int bucketizeExpireIn(long expireIn) {
        if (expireIn == Long.MAX_VALUE) {
            return LocationStatsEnums.EXPIRATION_NO_EXPIRY;
        }

        if (expireIn < 20 * ONE_SEC_IN_MILLIS) {
            return LocationStatsEnums.EXPIRATION_BETWEEN_0_AND_20_SEC;
        } else if (expireIn < ONE_MINUTE_IN_MILLIS) {
            return LocationStatsEnums.EXPIRATION_BETWEEN_20_SEC_AND_1_MIN;
        } else if (expireIn < ONE_MINUTE_IN_MILLIS * 10) {
            return LocationStatsEnums.EXPIRATION_BETWEEN_1_MIN_AND_10_MIN;
        } else if (expireIn < ONE_HOUR_IN_MILLIS) {
            return LocationStatsEnums.EXPIRATION_BETWEEN_10_MIN_AND_1_HOUR;
        } else {
            return LocationStatsEnums.EXPIRATION_LARGER_THAN_1_HOUR;
        }
    }

    private static int categorizeActivityImportance(boolean foreground) {
        if (foreground) {
            return LocationStatsEnums.IMPORTANCE_TOP;
        } else {
            return LocationStatsEnums.IMPORTANCE_BACKGROUND;
        }
    }

    private static int getCallbackType(
            int apiType, boolean hasListener, boolean hasIntent) {
        if (apiType == LocationStatsEnums.API_SEND_EXTRA_COMMAND) {
            return LocationStatsEnums.CALLBACK_NOT_APPLICABLE;
        }

        // Listener and PendingIntent will not be set at
        // the same time.
        if (hasIntent) {
            return LocationStatsEnums.CALLBACK_PENDING_INTENT;
        } else if (hasListener) {
            return LocationStatsEnums.CALLBACK_LISTENER;
        } else {
            return LocationStatsEnums.CALLBACK_UNKNOWN;
        }
    }

    private synchronized boolean hitApiUsageLogCap() {
        long currentHour = Instant.now().toEpochMilli() / ONE_HOUR_IN_MILLIS;
        if (currentHour > mLastApiUsageLogHour) {
            mLastApiUsageLogHour = currentHour;
            mApiUsageLogHourlyCount = 0;
            return false;
        } else {
            mApiUsageLogHourlyCount = Math.min(
                    mApiUsageLogHourlyCount + 1, API_USAGE_LOG_HOURLY_CAP);
            return mApiUsageLogHourlyCount >= API_USAGE_LOG_HOURLY_CAP;
        }
    }
}
