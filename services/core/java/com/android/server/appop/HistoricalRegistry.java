/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.appop;

import static android.app.AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE;
import static android.app.AppOpsManager.HISTORY_FLAG_AGGREGATE;
import static android.app.AppOpsManager.OP_ACCESS_ACCESSIBILITY;
import static android.app.AppOpsManager.OP_ACCESS_NOTIFICATIONS;
import static android.app.AppOpsManager.OP_BIND_ACCESSIBILITY_SERVICE;
import static android.app.AppOpsManager.OP_CAMERA;
import static android.app.AppOpsManager.OP_COARSE_LOCATION;
import static android.app.AppOpsManager.OP_EMERGENCY_LOCATION;
import static android.app.AppOpsManager.OP_FINE_LOCATION;
import static android.app.AppOpsManager.OP_FLAGS_ALL;
import static android.app.AppOpsManager.OP_FLAG_SELF;
import static android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED;
import static android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY;
import static android.app.AppOpsManager.OP_GPS;
import static android.app.AppOpsManager.OP_MONITOR_HIGH_POWER_LOCATION;
import static android.app.AppOpsManager.OP_MONITOR_LOCATION;
import static android.app.AppOpsManager.OP_PHONE_CALL_CAMERA;
import static android.app.AppOpsManager.OP_PHONE_CALL_MICROPHONE;
import static android.app.AppOpsManager.OP_READ_DEVICE_IDENTIFIERS;
import static android.app.AppOpsManager.OP_READ_HEART_RATE;
import static android.app.AppOpsManager.OP_READ_OXYGEN_SATURATION;
import static android.app.AppOpsManager.OP_READ_SKIN_TEMPERATURE;
import static android.app.AppOpsManager.OP_RECEIVE_AMBIENT_TRIGGER_AUDIO;
import static android.app.AppOpsManager.OP_RECEIVE_SANDBOX_TRIGGER_AUDIO;
import static android.app.AppOpsManager.OP_RECORD_AUDIO;
import static android.app.AppOpsManager.OP_RESERVED_FOR_TESTING;
import static android.app.AppOpsManager.OP_RUN_IN_BACKGROUND;

import static java.lang.Long.min;
import static java.lang.Math.max;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.Trace;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.FgThread;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;


/**
 * A registry to record app op access events, which are generated upon an application's
 * access to private data or system resources. These access events are stored in SQLite with an
 * aggregation-based approach.
 *
 * <p>This class persists app op history in an aggregated format, aggregating access
 * events over time intervals rather than storing each app op access event individually.
 * It uses two databases (but same schema) to handle different aggregation levels and
 * data fidelity requirements:
 * <ul>
 * <li><b>Short Interval Aggregation:</b> Stored in {@link #SHORT_INTERVAL_DATABASE_FILE}.
 * Typically aggregates data over a 1-minute interval (configurable). This database
 * provides higher fidelity tracking for a specific set of sensitive or security-critical
 * app ops. This serves a purpose analogous to older "discrete registry" implementations.</li>
 * <li><b>Long Interval Aggregation:</b> Stored in {@link #LONG_INTERVAL_DATABASE_FILE}.
 * Aggregates data over a longer interval, typically 15 minutes (configurable).
 * Used for app ops not designated for the short-interval database, providing
 * general usage trends.</li>
 * </ul>
 *
 * <p>Note: Some methods inherited from {@link HistoricalRegistryInterface} related to
 * xml implementation are implemented as no-ops.
 */
public class HistoricalRegistry implements HistoricalRegistryInterface {
    private static final String TAG = "HistoricalRegistry";
    public static final boolean DEBUG = false;
    enum AggregationTimeWindow {
        SHORT, LONG
    }
    /**
     * Database file to store aggregated ops at shorter interval i.e. 1 minute. This is equivalent
     * to discrete registry in xml implementation.
     */
    private static final String SHORT_INTERVAL_DATABASE_FILE = "app_op_history_short_interval.db";
    private static final int SHORT_INTERVAL_DATABASE_VERSION = 1;
    /**
     * Database file to store aggregated ops at longer interval i.e. 15 minute. This is equivalent
     * to aggregated/historical ops registry in xml implementation.
     */
    private static final String LONG_INTERVAL_DATABASE_FILE = "app_op_history_long_interval.db";
    private static final int LONG_INTERVAL_DATABASE_VERSION = 1;
    private static final long DEFAULT_SHORT_INTERVAL_QUANTIZATION =
            Duration.ofMinutes(1).toMillis();
    private static final long DEFAULT_LONG_INTERVAL_QUANTIZATION =
            Duration.ofMinutes(15).toMillis();
    private static final long DEFAULT_HISTORY_RETENTION_MILLIS = Duration.ofDays(7).toMillis();
    private static final long MAXIMUM_HISTORY_RETENTION_MILLIS = Duration.ofDays(30).toMillis();
    private static final int DEFAULT_SHORT_INTERVAL_OP_FLAGS =
            OP_FLAG_SELF | OP_FLAG_TRUSTED_PROXIED | OP_FLAG_TRUSTED_PROXY;
    private static final String PROPERTY_LONG_INTERVAL_QUANTIZATION =
            "appops_long_interval_quantization_millis";
    // Below property names are coming from old setup for discrete ops.
    private static final String PROPERTY_SHORT_INTERVAL_QUANTIZATION =
            "discrete_history_quantization_millis";
    // How long the app op history (both long & short interval) should be retained,
    // max retention could be 30 days.
    private static final String PROPERTY_HISTORY_RETENTION_MILLIS =
            "discrete_history_cutoff_millis";
    // TODO Remove op flags config to make it more deterministic i.e. In which table an app op
    //  entry would be stored.
    private static final String PROPERTY_DISCRETE_FLAGS =
            "discrete_history_op_flags";
    // Comma separated app ops list config for testing i.e. "1,2,3,4"
    private static final String PROPERTY_SHORT_INTERVAL_APP_OPS_LIST =
            "discrete_history_ops_cslist";

    // These ops are recorded in short interval ops database for better fidelity.
    private static final int[] SHORT_INTERVAL_OPS = new int[]{
            OP_PHONE_CALL_MICROPHONE,
            OP_RECEIVE_AMBIENT_TRIGGER_AUDIO,
            OP_RECEIVE_SANDBOX_TRIGGER_AUDIO,
            OP_PHONE_CALL_CAMERA,
            OP_EMERGENCY_LOCATION,
            OP_FINE_LOCATION,
            OP_COARSE_LOCATION,
            OP_CAMERA,
            OP_RECORD_AUDIO,
            OP_READ_HEART_RATE,
            OP_READ_OXYGEN_SATURATION,
            OP_READ_SKIN_TEMPERATURE,
            OP_RESERVED_FOR_TESTING
    };
    // These app ops are deemed important for detecting a malicious app, and are recorded
    // in short interval ops database too for better fidelity.
    private static final int[] IMPORTANT_OPS_FOR_SECURITY = new int[]{
            OP_GPS,
            OP_ACCESS_NOTIFICATIONS,
            OP_RUN_IN_BACKGROUND,
            OP_BIND_ACCESSIBILITY_SERVICE,
            OP_ACCESS_ACCESSIBILITY,
            OP_READ_DEVICE_IDENTIFIERS,
            OP_MONITOR_HIGH_POWER_LOCATION,
            OP_MONITOR_LOCATION
    };

    private final Context mContext;
    private final AppOpHistoryHelper mShortIntervalHistoryHelper;
    private final AppOpHistoryHelper mLongIntervalHistoryHelper;
    private int mMode = AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE;
    private long mShortIntervalQuantizationMillis = DEFAULT_SHORT_INTERVAL_QUANTIZATION;
    private long mLongIntervalQuantizationMillis = DEFAULT_LONG_INTERVAL_QUANTIZATION;
    private long mHistoryRetentionMillis = DEFAULT_HISTORY_RETENTION_MILLIS;
    private int mShortIntervalAppOpFlags = DEFAULT_SHORT_INTERVAL_OP_FLAGS;
    private volatile boolean mIsReady = false;
    // App ops code configured to be captured for short interval ops database.
    private int[] mShortIntervalAppOpsArray = new int[0];
    // Attribution chain id is used to identify an attribution source chain, This is
    // set for startOp only. PermissionManagerService resets this ID on device restart, so
    // we use previously persisted chain id as offset, and add it to chain id received from
    // permission manager service.
    private long mChainIdOffset;
    private final File mShortIntervalDatabaseFile;
    private final File mLongIntervalDatabaseFile;

    HistoricalRegistry(Context context) {
        this(context, getDatabaseFile(SHORT_INTERVAL_DATABASE_FILE),
                getDatabaseFile(LONG_INTERVAL_DATABASE_FILE));
    }

    @VisibleForTesting
    HistoricalRegistry(Context context, File shortIntervalDbFile, File longIntervalDbFile) {
        mContext = context;
        mShortIntervalDatabaseFile = shortIntervalDbFile;
        mLongIntervalDatabaseFile = longIntervalDbFile;
        mShortIntervalHistoryHelper = new AppOpHistoryHelper(context, shortIntervalDbFile,
                AggregationTimeWindow.SHORT, SHORT_INTERVAL_DATABASE_VERSION);
        mLongIntervalHistoryHelper = new AppOpHistoryHelper(context, longIntervalDbFile,
                AggregationTimeWindow.LONG, LONG_INTERVAL_DATABASE_VERSION);
    }

    HistoricalRegistry(HistoricalRegistry other) {
        mContext = other.mContext;
        mChainIdOffset = other.mChainIdOffset;
        mShortIntervalAppOpsArray = other.mShortIntervalAppOpsArray;
        mShortIntervalAppOpFlags = other.mShortIntervalAppOpFlags;
        mIsReady = other.mIsReady;
        mHistoryRetentionMillis = other.mHistoryRetentionMillis;
        mShortIntervalQuantizationMillis = other.mShortIntervalQuantizationMillis;
        mLongIntervalQuantizationMillis = other.mLongIntervalQuantizationMillis;
        mMode = other.mMode;
        mLongIntervalHistoryHelper = other.mLongIntervalHistoryHelper;
        mShortIntervalHistoryHelper = other.mShortIntervalHistoryHelper;
        mShortIntervalDatabaseFile = other.mShortIntervalDatabaseFile;
        mLongIntervalDatabaseFile = other.mLongIntervalDatabaseFile;
    }

    @Override
    public void systemReady(@NonNull ContentResolver resolver) {
        setHistoricalRegistryMode(resolver);
        setHistoryParameters(DeviceConfig.getProperties(DeviceConfig.NAMESPACE_PRIVACY));
        if (mMode != AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE) {
            Slog.w(TAG, "App op access events are not recorded, the config isn't active.");
            return;
        }
        mShortIntervalHistoryHelper.systemReady(mShortIntervalQuantizationMillis,
                mHistoryRetentionMillis);
        mLongIntervalHistoryHelper.systemReady(mLongIntervalQuantizationMillis,
                mHistoryRetentionMillis);
        // migrate discrete ops from xml or sqlite to unified-schema sqlite database.
        if (DiscreteOpsXmlRegistry.getDiscreteOpsDir().exists()) {
            Slog.i(TAG, "migrate discrete ops from xml to unified sqlite.");
            // We don't really need to use AppOpsService lock here as this is a one time migration.
            Object lock = new Object();
            DiscreteOpsXmlRegistry xmlRegistry = new DiscreteOpsXmlRegistry(lock);
            DiscreteOpsMigrationHelper.migrateFromXmlToUnifiedSchemaSqlite(
                    xmlRegistry, mShortIntervalHistoryHelper);
        } else if (DiscreteOpsDbHelper.getDatabaseFile().exists()) {
            Slog.i(TAG, "migrate discrete ops from sqlite to unified sqlite.");
            DiscreteOpsSqlRegistry sqlRegistry = new DiscreteOpsSqlRegistry(mContext);
            DiscreteOpsMigrationHelper.migrateFromSqliteToUnifiedSchemaSqlite(
                    sqlRegistry, mShortIntervalHistoryHelper);
        }

        if (LegacyHistoricalRegistry.historicalOpsDirExist()) {
            LegacyHistoricalRegistry.deleteHistoricalOpsDir();
        }

        mChainIdOffset = mShortIntervalHistoryHelper.getLargestAttributionChainId();

        // Set up listener for quantization, op flags or app ops list config for testing
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_PRIVACY,
                AsyncTask.THREAD_POOL_EXECUTOR, this::setHistoryParameters);
        // Set up a listener to refresh history mode for testing.
        final Uri uri = Settings.Global.getUriFor(Settings.Global.APPOP_HISTORY_PARAMETERS);
        resolver.registerContentObserver(uri, false, new ContentObserver(
                FgThread.getHandler()) {
            @Override
            public void onChange(boolean selfChange) {
                setHistoricalRegistryMode(resolver);
            }
        });
        mIsReady = true;
    }

    private void setHistoricalRegistryMode(@NonNull ContentResolver resolver) {
        final String setting = Settings.Global.getString(resolver,
                Settings.Global.APPOP_HISTORY_PARAMETERS);
        if (setting == null) {
            return;
        }
        String modeValue = null;
        final String[] parameters = setting.split(",");
        for (String parameter : parameters) {
            final String[] parts = parameter.split("=");
            if (parts.length == 2) {
                final String key = parts[0].trim();
                if (key.equals(Settings.Global.APPOP_HISTORY_MODE)) {
                    modeValue = parts[1].trim();
                } else {
                    Slog.w(TAG, "Unknown or Deprecated parameter: " + parameter);
                }
            }
        }

        if (DEBUG) {
            Slog.d(TAG, "Historical registry mode " + modeValue);
        }
        if (modeValue != null) {
            int oldMode = mMode;
            mMode = AppOpsManager.parseHistoricalMode(modeValue);
            if (oldMode != mMode && mMode == AppOpsManager.HISTORICAL_MODE_DISABLED) {
                Slog.i(TAG, "Historical registry mode is disabled, clearing history.");
                clearAllHistory();
            }
        }
    }

    private void setHistoryParameters(DeviceConfig.Properties p) {
        // read history retention millis.
        mHistoryRetentionMillis = getAppOpsHistoryRetentionMillis(p);
        // read quantization millis for short interval app ops db.
        mShortIntervalQuantizationMillis = getShortIntervalQuantizationMillis(p);
        // read quantization millis for long interval app ops db.
        if (p.getKeyset().contains(PROPERTY_LONG_INTERVAL_QUANTIZATION)) {
            mLongIntervalQuantizationMillis = p.getLong(
                    PROPERTY_LONG_INTERVAL_QUANTIZATION,
                    DEFAULT_LONG_INTERVAL_QUANTIZATION);
            if (!Build.IS_DEBUGGABLE) {
                mLongIntervalQuantizationMillis = max(
                        DEFAULT_LONG_INTERVAL_QUANTIZATION,
                        mLongIntervalQuantizationMillis);
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "Long interval quantization millis: " + mLongIntervalQuantizationMillis);
        }

        // read app op flags to be captured in short interval app ops db.
        if (p.getKeyset().contains(PROPERTY_DISCRETE_FLAGS)) {
            mShortIntervalAppOpFlags = p.getInt(PROPERTY_DISCRETE_FLAGS,
                    DEFAULT_SHORT_INTERVAL_OP_FLAGS);
        }

        if (p.getKeyset().contains(PROPERTY_SHORT_INTERVAL_APP_OPS_LIST)) {
            String opsListConfig = p.getString(PROPERTY_SHORT_INTERVAL_APP_OPS_LIST, null);
            if (DEBUG) {
                Slog.d(TAG, "Short interval ops list : " + opsListConfig);
            }
            mShortIntervalAppOpsArray = opsListConfig == null
                    ? getShortIntervalAppOpsList() : parseOpsList(opsListConfig);
        } else {
            mShortIntervalAppOpsArray = getShortIntervalAppOpsList();
        }
        Arrays.sort(mShortIntervalAppOpsArray);
    }

    /**
     * @return an array of app ops captured into short interval app ops database.
     */
    private int[] getShortIntervalAppOpsList() {
        IntArray shortIntervalOps = new IntArray();
        shortIntervalOps.addAll(SHORT_INTERVAL_OPS);
        shortIntervalOps.addAll(IMPORTANT_OPS_FOR_SECURITY);
        return shortIntervalOps.toArray();
    }

    private int[] parseOpsList(String opsList) {
        String[] strArr;
        if (opsList == null || opsList.isEmpty()) {
            strArr = new String[0];
        } else {
            strArr = opsList.split(",");
        }
        int nOps = strArr.length;
        int[] result = new int[nOps];
        try {
            for (int i = 0; i < nOps; i++) {
                result[i] = Integer.parseInt(strArr[i]);
            }
        } catch (NumberFormatException e) {
            Slog.e(TAG, "Failed to parse short interval ops list: " + e.getMessage());
            return getShortIntervalAppOpsList();
        }
        return result;
    }

    @Override
    public void shutdown() {
        if (isNotReadyOrDisabled()) {
            return;
        }
        mShortIntervalHistoryHelper.shutdown();
        mLongIntervalHistoryHelper.shutdown();
    }

    @Override
    public void dump(String prefix, PrintWriter pw, int filterUid, @Nullable String filterPackage,
            @Nullable String filterAttributionTag, int filterOp, int filter,
            @NonNull SimpleDateFormat sdf, @NonNull Date date, boolean includeDiscreteOps,
            int limit, boolean dumpHistory) {
        if (isNotReadyOrDisabled()) {
            return;
        }
        pw.println();
        pw.print(prefix);
        pw.print("History:");
        pw.print("  mode=");
        pw.println(AppOpsManager.historicalModeToString(mMode));

        long shortIntervalMinute = Duration.ofMillis(mShortIntervalQuantizationMillis).toMinutes();
        long longIntervalMinute = Duration.ofMillis(mLongIntervalQuantizationMillis).toMinutes();
        pw.print("SHORT_INTERVAL(Aggregation Bucket)=");
        pw.print(shortIntervalMinute + " Minute");
        pw.print(", LONG_INTERVAL(Aggregation Bucket)=");
        pw.println(longIntervalMinute + " Minutes");

        pw.print("Database size(bytes): SHORT_INTERVAL=");
        pw.print(getDatabaseFile(SHORT_INTERVAL_DATABASE_FILE).length());
        pw.print(", LONG_INTERVAL=");
        pw.println(getDatabaseFile(LONG_INTERVAL_DATABASE_FILE).length());

        pw.print("Database Total records: SHORT_INTERVAL=");
        pw.print(mShortIntervalHistoryHelper.getTotalRecordsCount());
        pw.print(", LONG_INTERVAL=");
        pw.println(mLongIntervalHistoryHelper.getTotalRecordsCount());
        IntArray opCodes = new IntArray();
        long endTimeMillis = System.currentTimeMillis();
        if (!dumpHistory && !includeDiscreteOps) {
            filter = AppOpsManager.FILTER_BY_OP_NAMES;
            // print privacy indicator records for last 1 hour (or up to 500 records)
            long beginTimeMillis = endTimeMillis - Duration.ofHours(1).toMillis();
            opCodes.add(OP_CAMERA);
            opCodes.add(OP_PHONE_CALL_CAMERA);

            opCodes.add(OP_RECORD_AUDIO);
            opCodes.add(OP_PHONE_CALL_MICROPHONE);
            opCodes.add(OP_RECEIVE_AMBIENT_TRIGGER_AUDIO);
            opCodes.add(OP_RECEIVE_SANDBOX_TRIGGER_AUDIO);

            opCodes.add(OP_FINE_LOCATION);
            opCodes.add(OP_COARSE_LOCATION);
            opCodes.add(OP_EMERGENCY_LOCATION);
            pw.println("----Location, Microphone, and Camera App Ops (Last 1 Hour)----");
            mShortIntervalHistoryHelper.dump(beginTimeMillis, endTimeMillis, pw,
                    Process.INVALID_UID, null, null, opCodes, filter,
                    sdf, date, 500);
            return;
        }

        if ((filter & AppOpsManager.FILTER_BY_OP_NAMES) != 0
                && filterOp != AppOpsManager.OP_NONE) {
            opCodes.add(filterOp);
        }
        long beginTimeMillis = endTimeMillis - mHistoryRetentionMillis;
        if (includeDiscreteOps) {
            pw.println("------------Discrete App Ops-------------");
            mShortIntervalHistoryHelper.dump(beginTimeMillis, endTimeMillis, pw, filterUid,
                    filterPackage, filterAttributionTag, opCodes, filter, sdf, date, limit);
        } else {
            pw.println("------------Aggregated App Ops-------------");
            mLongIntervalHistoryHelper.dump(beginTimeMillis, endTimeMillis, pw, filterUid,
                    filterPackage, filterAttributionTag, opCodes, filter, sdf, date, limit);
        }
    }

    @Override
    public void dumpAggregatedData(String prefix, PrintWriter pw, int filterUid,
            @Nullable String filterPackage, @Nullable String filterAttributionTag, int filterOp,
            int filter, @NonNull SimpleDateFormat sdf, @NonNull Date date) {
        // no-op
    }

    @Override
    public void dumpDiscreteData(@NonNull PrintWriter pw, int uidFilter,
            @Nullable String packageNameFilter, @Nullable String attributionTagFilter, int filter,
            int dumpOp, @NonNull SimpleDateFormat sdf, @NonNull Date date, @NonNull String prefix,
            int nDiscreteOps) {
        // no-op
    }

    private boolean isOpCapturedInShortIntervalDatabase(int op, int opFlags) {
        if (Arrays.binarySearch(mShortIntervalAppOpsArray, op) < 0) {
            return false;
        }
        if ((opFlags & (mShortIntervalAppOpFlags)) == 0) {
            return false;
        }
        return true;
    }

    @Override
    public void increaseOpAccessDuration(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState, int flags,
            long accessTime, long duration, int attributionFlags, int attributionChainId) {
        if (isNotReadyOrDisabled()) {
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "increaseOpAccessDuration called with params: "
                    + "op=" + AppOpsManager.opToName(op)
                    + ", uid=" + uid
                    + ", packageName=" + packageName
                    + ", deviceId=" + deviceId
                    + ", attributionTag=" + attributionTag
                    + ", uidState=" + AppOpsManager.uidStateToString(uidState)
                    + ", flags=" + AppOpsManager.flagsToString(flags)
                    + ", accessTimeMillis=" + accessTime
                    + ", durationMillis=" + duration
                    + ", attributionFlags=" + attributionFlags
                    + ", attributionChainId=" + attributionChainId);
        }
        if (isOpCapturedInShortIntervalDatabase(op, flags)) {
            long offsetChainId = getOffsetChainId(attributionChainId);
            mShortIntervalHistoryHelper.recordOpAccessDuration(op, uid, packageName,
                    deviceId, attributionTag, uidState, flags, accessTime, attributionFlags,
                    offsetChainId, duration);
        } else {
            mLongIntervalHistoryHelper.recordOpAccessDuration(op, uid, packageName,
                    deviceId, attributionTag, uidState, flags, accessTime,
                    AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                    duration);
        }
    }

    @Override
    public void incrementOpAccessedCount(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState, int flags,
            long accessTime, int attributionFlags, int attributionChainId, int accessCount,
            boolean isStartOrResume) {
        if (isNotReadyOrDisabled()) {
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "incrementOpAccessedCount called with params: "
                    + "op=" + AppOpsManager.opToName(op)
                    + ", uid=" + uid
                    + ", packageName=" + packageName
                    + ", deviceId=" + deviceId
                    + ", attributionTag=" + attributionTag
                    + ", uidState=" + AppOpsManager.uidStateToString(uidState)
                    + ", flags=" + AppOpsManager.flagsToString(flags)
                    + ", accessTimeMillis=" + accessTime
                    + ", access count=" + accessCount
                    + ", isStartOrResume=" + isStartOrResume
                    + ", attributionFlags=" + attributionFlags
                    + ", attributionChainId=" + attributionChainId);
        }
        if (isOpCapturedInShortIntervalDatabase(op, flags)) {
            long offsetChainId = getOffsetChainId(attributionChainId);
            mShortIntervalHistoryHelper.incrementOpAccessedCount(op, uid, packageName,
                    deviceId, attributionTag, uidState, flags, accessTime, attributionFlags,
                    offsetChainId, accessCount, isStartOrResume);
        } else {
            mLongIntervalHistoryHelper.incrementOpAccessedCount(op, uid, packageName,
                    deviceId, attributionTag, uidState, flags, accessTime,
                    AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                    accessCount, isStartOrResume);
        }
    }

    @Override
    public void incrementOpRejectedCount(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState,
            int flags, long rejectTime, int rejectCount) {
        if (isNotReadyOrDisabled()) {
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "incrementOpRejectedCount called with params: "
                    + "op=" + AppOpsManager.opToName(op)
                    + ", uid=" + uid
                    + ", packageName=" + packageName
                    + ", deviceId=" + deviceId
                    + ", attributionTag=" + attributionTag
                    + ", uidState=" + AppOpsManager.uidStateToString(uidState)
                    + ", flags=" + AppOpsManager.flagsToString(flags)
                    + ", rejectTimeMillis=" + rejectTime
                    + ", reject count=" + rejectCount);
        }
        if (isOpCapturedInShortIntervalDatabase(op, flags)) {
            mShortIntervalHistoryHelper.incrementOpRejectedCount(op, uid, packageName,
                    deviceId, attributionTag, uidState, flags, rejectTime,
                    AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                    rejectCount);
        } else {
            mLongIntervalHistoryHelper.incrementOpRejectedCount(op, uid, packageName,
                    deviceId, attributionTag, uidState, flags, rejectTime,
                    AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                    rejectCount);
        }
    }

    private long getOffsetChainId(int attributionChainId) {
        if (attributionChainId == ATTRIBUTION_CHAIN_ID_NONE) {
            return attributionChainId;
        }
        synchronized (this) {
            long offsetChainId = attributionChainId + mChainIdOffset;
            // PermissionManagerService chain id reached the max value,
            // reset offset, it's going to be very rare.
            if (attributionChainId == Integer.MAX_VALUE) {
                mChainIdOffset = offsetChainId;
            }
            return offsetChainId;
        }
    }

    @Override
    public void getHistoricalOps(int uid, @Nullable String packageName,
            @Nullable String attributionTag, @Nullable String[] opNames, int historyFlags,
            int filter, long beginTimeMillis, long endTimeMillis, int flags,
            @Nullable String[] attributionExemptPkgs, @NonNull RemoteCallback callback) {
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "AppOpHistory#SQLiteGetHistoricalOps");
        try {
            final long currentTimeMillis = System.currentTimeMillis();
            if (endTimeMillis == Long.MAX_VALUE) {
                endTimeMillis = currentTimeMillis;
            }
            final AppOpsManager.HistoricalOps result =
                    new AppOpsManager.HistoricalOps(beginTimeMillis, endTimeMillis);
            final Bundle payload = new Bundle();

            if (isNotReadyOrDisabled()) {
                payload.putParcelable(AppOpsManager.KEY_HISTORICAL_OPS, result);
                callback.sendResult(payload);
                return;
            }

            mShortIntervalHistoryHelper.addShortIntervalOpsToHistoricalOpsResult(result,
                    beginTimeMillis, endTimeMillis, filter, uid, packageName, opNames,
                    attributionTag, flags, new ArraySet<>(attributionExemptPkgs), historyFlags);
            if ((historyFlags & HISTORY_FLAG_AGGREGATE) != 0) {
                mLongIntervalHistoryHelper.addLongIntervalOpsToHistoricalOpsResult(result,
                        beginTimeMillis, endTimeMillis, filter, uid, packageName, opNames,
                        attributionTag, flags);
            }
            // Send back the result.
            payload.putParcelable(AppOpsManager.KEY_HISTORICAL_OPS, result);
            callback.sendResult(payload);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }

    @Override
    @NonNull
    public ArraySet<String> getRecentlyUsedPackageNames(@NonNull String[] opNames, int historyFlags,
            int filter, long beginTimeMillis, long endTimeMillis, int opFlags) {
        ArraySet<String> packageNames = new ArraySet<>();

        if ((historyFlags & AppOpsManager.HISTORY_FLAG_DISCRETE) != 0) {
            String[] shortIntervalOps = filterOpNames(opNames,
                    op -> isOpCapturedInShortIntervalDatabase(AppOpsManager.strOpToOp(op),
                            OP_FLAGS_ALL));
            packageNames.addAll(mShortIntervalHistoryHelper.getRecentlyUsedPackageNames(
                    shortIntervalOps, filter, beginTimeMillis, endTimeMillis, opFlags));
        }
        if ((historyFlags & HISTORY_FLAG_AGGREGATE) != 0) {
            String[] longIntervalOps = filterOpNames(opNames,
                    op -> !isOpCapturedInShortIntervalDatabase(AppOpsManager.strOpToOp(op),
                            OP_FLAGS_ALL));
            packageNames.addAll(mLongIntervalHistoryHelper.getRecentlyUsedPackageNames(
                    longIntervalOps, filter, beginTimeMillis, endTimeMillis, opFlags));
        }
        return packageNames;
    }

    private String[] filterOpNames(@NonNull String[] opNames, @NonNull Predicate<String> filter) {
        List<String> filteredOpNames = new ArrayList<>();
        for (String op : opNames) {
            if (filter.test(op)) {
                filteredOpNames.add(op);
            }
        }
        return filteredOpNames.toArray(new String[0]);
    }

    @Override
    public void clearHistory(int uid, String packageName) {
        if (isNotReadyOrDisabled()) {
            return;
        }
        mShortIntervalHistoryHelper.clearHistory(uid, packageName);
        mLongIntervalHistoryHelper.clearHistory(uid, packageName);
    }

    @Override
    public void clearAllHistory() {
        if (isNotReadyOrDisabled()) {
            return;
        }
        mShortIntervalHistoryHelper.clearHistory();
        mLongIntervalHistoryHelper.clearHistory();
    }

    @Override
    public void offsetHistory(long offsetMillis) {
        // no-op
    }

    @Override
    public void offsetDiscreteHistory(long offsetMillis) {
        // no-op
    }

    @Override
    public void getHistoricalOpsFromDiskRaw(int uid, @Nullable String packageName,
            @Nullable String attributionTag, @Nullable String[] opNames, int historyFlags,
            int filter, long beginTimeMillis, long endTimeMillis, int flags,
            String[] attributionExemptedPackages, @NonNull RemoteCallback callback) {
        // no-op
    }

    @Override
    public void addHistoricalOps(AppOpsManager.HistoricalOps ops) {
        // no-op
    }

    @Override
    public void writeAndClearDiscreteHistory() {
        // no-op
    }

    @Override
    public void persistPendingHistory() {
        if (isNotReadyOrDisabled()) {
            return;
        }
        mLongIntervalHistoryHelper.persistPendingHistory();
        mShortIntervalHistoryHelper.persistPendingHistory();
    }

    @Override
    public void setHistoryParameters(int mode, long baseSnapshotInterval,
            long intervalCompressionMultiplier) {
        // baseSnapshotInterval and intervalCompressionMultiplier aren't used anymore.
        if (mode == mMode) {
            return;
        }
        if (mode == AppOpsManager.HISTORICAL_MODE_DISABLED) {
            clearAllHistory();
        }
        mMode = mode;
    }

    @Override
    public void resetHistoryParameters() {
        mMode = AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE;
    }


    static boolean historicalOpsDbExist() {
        return getDatabaseFile(LONG_INTERVAL_DATABASE_FILE).exists();
    }

    static void deleteHistoricalOpsDb(Context context) {
        context.deleteDatabase(getDatabaseFile(LONG_INTERVAL_DATABASE_FILE).getAbsolutePath());
    }

    @NonNull
    // This is used during rollback in LegacyHistoricalRegistry, will be removed during flag
    // cleanup
    static File getDiscreteOpsDatabaseFile() {
        return getDatabaseFile(SHORT_INTERVAL_DATABASE_FILE);
    }

    // This is used during rollback in LegacyHistoricalRegistry, will be removed during flag
    // cleanup
    static int getDiscreteOpsDatabaseVersion() {
        return SHORT_INTERVAL_DATABASE_VERSION;
    }

    // This is used during rollback in LegacyHistoricalRegistry, will be removed during flag
    // cleanup
    static long getDiscreteOpsQuantizationMillis() {
        return getShortIntervalQuantizationMillis(
                DeviceConfig.getProperties(DeviceConfig.NAMESPACE_PRIVACY));
    }

    private static long getShortIntervalQuantizationMillis(DeviceConfig.Properties p) {
        long quantizationMillis = DEFAULT_SHORT_INTERVAL_QUANTIZATION;
        if (p.getKeyset().contains(PROPERTY_SHORT_INTERVAL_QUANTIZATION)) {
            quantizationMillis = p.getLong(
                    PROPERTY_SHORT_INTERVAL_QUANTIZATION,
                    DEFAULT_SHORT_INTERVAL_QUANTIZATION);
            if (!Build.IS_DEBUGGABLE) {
                quantizationMillis = max(DEFAULT_SHORT_INTERVAL_QUANTIZATION, quantizationMillis);
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "Short interval quantization millis: " + quantizationMillis);
        }
        return quantizationMillis;
    }

    static long getAppOpsHistoryRetentionMillis() {
        return getAppOpsHistoryRetentionMillis(
                DeviceConfig.getProperties(DeviceConfig.NAMESPACE_PRIVACY));
    }

    private static long getAppOpsHistoryRetentionMillis(DeviceConfig.Properties p) {
        long historyRetentionMillis = DEFAULT_HISTORY_RETENTION_MILLIS;
        if (p.getKeyset().contains(PROPERTY_HISTORY_RETENTION_MILLIS)) {
            historyRetentionMillis = p.getLong(PROPERTY_HISTORY_RETENTION_MILLIS,
                    DEFAULT_HISTORY_RETENTION_MILLIS);
            if (!Build.IS_DEBUGGABLE) {
                historyRetentionMillis = min(historyRetentionMillis,
                        MAXIMUM_HISTORY_RETENTION_MILLIS);
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "History retention millis: " + historyRetentionMillis);
        }
        return historyRetentionMillis;
    }

    private boolean isNotReadyOrDisabled() {
        return !mIsReady || mMode != AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE;
    }

    @NonNull
    private static File getDatabaseFile(String databaseName) {
        return new File(new File(Environment.getDataSystemDirectory(), "appops"), databaseName);
    }
}
