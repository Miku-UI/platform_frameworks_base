/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.server.audio;

import static android.Manifest.permission.USE_EXACT_ALARM;
import static android.media.audio.Flags.autoPublicVolumeApiHardening;

import static com.android.media.audio.Flags.hardeningPartial;
import static com.android.media.audio.Flags.hardeningPartialVolume;
import static com.android.media.audio.Flags.hardeningStrict;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_FOCUS;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_RINGER;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_VOLUME;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_ALARM;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_FLAG_DISABLED;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_OVERRIDE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_PRIVILEGED_APP;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_SYSTEM_USAGE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_TARGET_SDK;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ALARM;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ANNOUNCEMENT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_SONIFICATION;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_CALL_ASSISTANT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_EMERGENCY;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_GAME;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_MEDIA;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_EVENT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_SAFETY;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VEHICLE_STATUS;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VIRTUAL_SOURCE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.IAudioManagerNative.HardeningExemptionReason;
import android.media.IAudioPolicyService.HardeningOverride;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;

import com.android.media.audio.metrics.AudioAtomsLog;
import com.android.modules.expresslog.Counter;
import com.android.server.DeviceIdleInternal;
import com.android.server.LocalServices;
import com.android.server.utils.EventLogger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to encapsulate all audio API hardening operations
 */
public class HardeningEnforcer {

    private static final String TAG = "AS.HardeningEnforcer";
    private static final boolean DEBUG = false;
    private static final int LOG_NB_EVENTS = 20;

    final Context mContext;
    final AppOpsManager mAppOps;
    final AtomicInteger mHardeningOverride;
    final boolean mIsAutomotive;

    final ActivityManager mActivityManager;
    final PackageManager mPackageManager;
    final AudioServerPermissionProvider mPermissionProvider;

    final EventLogger mEventLogger;

    // capacity = 4 for each of the focus request types
    static final SparseArray<String> METRIC_COUNTERS_FOCUS_DENIAL = new SparseArray<>(4);
    static final SparseArray<String> METRIC_COUNTERS_FOCUS_GRANT = new SparseArray<>(4);

    static {
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN,
                "media_audio.value_audio_focus_gain_granted");
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                "media_audio.value_audio_focus_gain_transient_granted");
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                "media_audio.value_audio_focus_gain_transient_duck_granted");
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                "media_audio.value_audio_focus_gain_transient_excl_granted");

        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN,
                "media_audio.value_audio_focus_gain_appops_denial");
        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                "media_audio.value_audio_focus_gain_transient_appops_denial");
        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                "media_audio.value_audio_focus_gain_transient_duck_appops_denial");
        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                "media_audio.value_audio_focus_gain_transient_excl_appops_denial");
    }

    /**
     * Matches calls from {@link AudioManager#setStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_SET_STREAM_VOLUME = 100;
    /**
     * Matches calls from {@link AudioManager#adjustVolume(int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_VOLUME = 101;
    /**
     * Matches calls from {@link AudioManager#adjustSuggestedStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_SUGGESTED_STREAM_VOLUME = 102;
    /**
     * Matches calls from {@link AudioManager#adjustStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_STREAM_VOLUME = 103;
    /**
     * Matches calls from {@link AudioManager#setRingerMode(int)}
     */
    public static final int METHOD_AUDIO_MANAGER_SET_RINGER_MODE = 200;
    /**
     * Matches calls from {@link AudioManager#requestAudioFocus(AudioFocusRequest)}
     * and legacy variants
     */
    public static final int METHOD_AUDIO_MANAGER_REQUEST_AUDIO_FOCUS = 300;

    private static final int ALLOWED = 0;
    private static final int DENIED_IF_PARTIAL = 1;
    private static final int DENIED_IF_FULL = 2;

    public HardeningEnforcer(Context ctxt, boolean isAutomotive,
            AtomicInteger hardeningOverride, AppOpsManager appOps, PackageManager pm,
            EventLogger logger, AudioServerPermissionProvider permissionProvider) {
        mContext = ctxt;
        mIsAutomotive = isAutomotive;
        mHardeningOverride = hardeningOverride;
        mAppOps = appOps;
        mActivityManager = ctxt.getSystemService(ActivityManager.class);
        mPackageManager = pm;
        mEventLogger = logger;
        mPermissionProvider = permissionProvider;
    }

    /**
     * Translates the AudioAttributes usage to the corresponding proto usage enum
     * @param usage the usage from AudioAttributes
     * @return the proto usage enum
     */
    public static int getUsageForProtoLog(int usage) {
        return switch (usage) {
            case AudioAttributes.USAGE_MEDIA -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_MEDIA;
            case AudioAttributes.USAGE_VOICE_COMMUNICATION ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION;
            case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING;
            case AudioAttributes.USAGE_ALARM -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ALARM;
            case AudioAttributes.USAGE_NOTIFICATION ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION;
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE;
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            case AudioAttributes.USAGE_NOTIFICATION_EVENT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_EVENT;
            case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY;
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_SONIFICATION;
            case AudioAttributes.USAGE_GAME -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_GAME;
            case AudioAttributes.USAGE_VIRTUAL_SOURCE ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VIRTUAL_SOURCE;
            case AudioAttributes.USAGE_ASSISTANT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANT;
            case AudioAttributes.USAGE_CALL_ASSISTANT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_CALL_ASSISTANT;
            case AudioAttributes.USAGE_EMERGENCY ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_EMERGENCY;
            case AudioAttributes.USAGE_SAFETY ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_SAFETY;
            case AudioAttributes.USAGE_VEHICLE_STATUS ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VEHICLE_STATUS;
            case AudioAttributes.USAGE_ANNOUNCEMENT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ANNOUNCEMENT;
            default -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
        };
    }

    /**
     * Translates the HardeningExemptionReason to the corresponding proto usage enum
     * @param exemption the exemption reason from IAudioManagerNative
     * @return the proto usage enum
     */
    public static int getExemptionReasonForProtoLog(int exemption) {
        return switch (exemption) {
            case HardeningExemptionReason.NONE ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE;
            case HardeningExemptionReason.SYSTEM_USAGE ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_SYSTEM_USAGE;
            case HardeningExemptionReason.PRIVILEGED_APP ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_PRIVILEGED_APP;
            case HardeningExemptionReason.FLAG_DISABLED ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_FLAG_DISABLED;
            case HardeningExemptionReason.ALARM ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_ALARM;
            case HardeningExemptionReason.OVERRIDE ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_OVERRIDE;
            case HardeningExemptionReason.TARGET_SDK ->
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_TARGET_SDK;
            default -> AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE;
        };
    }

    public void updateScheduleExactAlarmCache(int uid, String packageName) {
        if (!mPermissionProvider.hasScheduleExactAlarm(uid)) {
            if (packageName != null) {
                long ident = Binder.clearCallingIdentity();
                try {
                    boolean canScheduleExactAlarms = mContext.getSystemService(AlarmManager.class)
                            .hasScheduleExactAlarm(packageName, UserHandle.getUserId(uid));

                    if (!canScheduleExactAlarms) {
                        DeviceIdleInternal deviceIdle = LocalServices.getService(DeviceIdleInternal.class);
                        if (deviceIdle != null && deviceIdle.isAppOnWhitelist(UserHandle.getAppId(uid))) {
                            canScheduleExactAlarms = true;
                        }
                    }

                    if (canScheduleExactAlarms) {
                        mPermissionProvider.addScheduleExactAlarm(uid);
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    /**
     * Checks whether the call in the current thread should be allowed or blocked
     * @param volumeMethod name of the method to check, for logging purposes
     * @return false if the method call is allowed, true if it should be a no-op
     */
    @SuppressWarnings("AndroidFrameworkCompatChange")  // primary enforcement in native
    protected boolean blockVolumeMethod(int volumeMethod, String packageName, int uid) {
        // Regardless of flag state, always permit callers with privileged audio permissions
        // Prevent them from showing up in metrics as well
        boolean isPrivileged = isPrivileged(uid);
        if (packageName == null || packageName.isEmpty()) {
            packageName = getPackNameForUid(uid);
        }

        // for Auto, volume methods require MODIFY_AUDIO_SETTINGS_PRIVILEGED
        if (mIsAutomotive) {
            if (!autoPublicVolumeApiHardening()) {
                // automotive hardening flag disabled, no blocking on auto
                return false;
            }
            if (isPrivileged) {
                return false;
            }
            // TODO metrics?
            // TODO log for audio dumpsys?
            Slog.e(TAG, "Preventing volume method " + volumeMethod + " for "
                    + packageName);
            return true;
        } else {
            int blockState;
            if (!noteOp(AppOpsManager.OP_CONTROL_AUDIO_PARTIAL, uid, packageName, null)) {
                // blocked by partial
                Counter.logIncrementWithUid(
                        "media_audio.value_audio_volume_hardening_partial_restriction", uid);
                blockState = DENIED_IF_PARTIAL;
            } else if (!noteOp(AppOpsManager.OP_CONTROL_AUDIO, uid, packageName, null)) {
                // blocked by full, permitted by partial
                Counter.logIncrementWithUid(
                        "media_audio.value_audio_volume_hardening_strict_restriction", uid);
                blockState = DENIED_IF_FULL;
            } else {
                // permitted with strict hardening, log anyway for API metrics
                Counter.logIncrementWithUid(
                        "media_audio.value_audio_volume_hardening_allowed", uid);
                blockState = ALLOWED;
            }

            if (blockState == ALLOWED) {
                return false;
            }

            int targetSdk = Build.VERSION_CODES.CUR_DEVELOPMENT;
            try {
                targetSdk = mPackageManager.getApplicationInfoAsUser(
                        packageName, 0, UserHandle.getUserId(uid)).targetSdkVersion;
            } catch (PackageManager.NameNotFoundException e) {
                // keep default
            }
            // This flag is misnamed: it blocks volume changes at the strict level: app usage of
            // volume modifications between partial and strict level should be extremely limited
            // given we don't want to encourage apps modify volume regardless.
            var overrideState = mHardeningOverride.get();
            boolean isPreCinnamonBun = targetSdk < Build.VERSION_CODES.CINNAMON_BUN;

            updateScheduleExactAlarmCache(uid, packageName);

            int[] enforcedState = switch (overrideState) {
                case HardeningOverride.ENABLE, AudioManager.HARDENING_THROW ->
                    new int[]{DENIED_IF_FULL,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE};
                case HardeningOverride.DISABLE ->
                    new int[]{ALLOWED,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_OVERRIDE};
                default -> {
                    if (isPrivileged) {
                        yield new int[]{ALLOWED,
                            AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_PRIVILEGED_APP};
                    } else if (holdsPermission(USE_EXACT_ALARM, uid) ||
                            mPermissionProvider.hasScheduleExactAlarm(uid)) {
                        yield new int[]{DENIED_IF_PARTIAL,
                            AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_ALARM};
                    } else if (!hardeningPartialVolume()) {
                        yield new int[]{ALLOWED,
                            AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_FLAG_DISABLED};
                    } else if (isPreCinnamonBun) {
                        yield new int[]{DENIED_IF_PARTIAL,
                            AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_TARGET_SDK};
                    }
                    yield new int[]{DENIED_IF_FULL,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE};
                }
            };
            int enforcedLevel = enforcedState[0];
            int exemption = enforcedState[1];

            boolean blocked = switch (enforcedLevel) {
                case DENIED_IF_PARTIAL -> blockState == DENIED_IF_PARTIAL;
                case DENIED_IF_FULL -> true;
                default -> false;
            };

            int usage = AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            boolean isStrict = blockState == DENIED_IF_FULL;
            String msg = "AudioHardening volume control for api "
                    + volumeMethod
                    + (!blocked ? " would be " : " ")
                    + "ignored for "
                    + packageName + " (" + uid + "), "
                    + "level: " + (isStrict ? "full" : "partial")
                    + " usage: " + usage + " exemption: " + exemption;
            mEventLogger.enqueueAndSlog(msg, EventLogger.Event.ALOGW, TAG);
            if (overrideState == AudioManager.HARDENING_THROW) {
                throw new IllegalStateException(msg);
            }
            AudioAtomsLog.write(AudioAtomsLog.AUDIO_HARDENING_REPORTED, uid,
                volumeMethod == METHOD_AUDIO_MANAGER_SET_RINGER_MODE ?
                    AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_RINGER
                    : AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_VOLUME,
                    isStrict, blocked,
                    usage,
                    exemption);
            return blocked;
        }
    }

    /**
     * Checks whether the call in the current thread should be allowed or blocked
     * @param focusMethod name of the method to check, for logging purposes
     * @param clientId id of the requester
     * @param focusReqType focus type being requested
     * @param attributionTag attribution of the caller
     * @param targetSdk target SDK of the caller
     * @param aa attributes of the request
     * @return false if the method call is allowed, true if it should be a no-op
     */
    @SuppressWarnings("AndroidFrameworkCompatChange")
    protected boolean blockFocusMethod(int callingUid, int focusMethod, @NonNull String clientId,
            int focusReqType, @NonNull String packageName, String attributionTag, int targetSdk,
            @NonNull AudioAttributes aa) {
        if (packageName.isEmpty()) {
            packageName = getPackNameForUid(callingUid);
        }
        int blockState = ALLOWED;
        if (!noteOp(AppOpsManager.OP_TAKE_AUDIO_FOCUS, callingUid, packageName, attributionTag)) {
            blockState = DENIED_IF_PARTIAL;
        } else if (!noteOp(AppOpsManager.OP_CONTROL_AUDIO, callingUid, packageName,
                           attributionTag)) {
            blockState = DENIED_IF_FULL;
        }

        if (blockState == ALLOWED) {
            metricsLogFocusReq(false, focusReqType, callingUid);
            return false;
        }

        var overrideState = mHardeningOverride.get();
        boolean isPreVic = targetSdk < Build.VERSION_CODES.VANILLA_ICE_CREAM;
        boolean isPreCinnamonBun = targetSdk < Build.VERSION_CODES.CINNAMON_BUN;
        boolean isPrivileged = isPrivileged(callingUid);

        updateScheduleExactAlarmCache(callingUid, packageName);

        int[] enforcedState = switch (overrideState) {
            case HardeningOverride.ENABLE, AudioManager.HARDENING_THROW ->
                new int[]{DENIED_IF_FULL,
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE};
            case HardeningOverride.DISABLE ->
                new int[]{ALLOWED,
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_OVERRIDE};
            default -> {
                if (isPrivileged) {
                    yield new int[]{ALLOWED,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_PRIVILEGED_APP};
                } else if (aa.getUsage() == AudioAttributes.USAGE_ALARM &&
                        (holdsPermission(USE_EXACT_ALARM, callingUid) ||
                            mPermissionProvider.hasScheduleExactAlarm(callingUid))) {
                    yield new int[]{DENIED_IF_PARTIAL,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_ALARM};
                } else if (!hardeningPartial() && isPreVic) {
                    yield new int[]{ALLOWED,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_FLAG_DISABLED};
                } else if (holdsPermission(Manifest.permission.BLUETOOTH_CONNECT, callingUid)) {
                    yield new int[]{DENIED_IF_PARTIAL,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_PRIVILEGED_APP};
                } else if (isPreCinnamonBun) {
                    yield new int[]{DENIED_IF_PARTIAL,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_TARGET_SDK};
                } else if (!hardeningStrict()) {
                    yield new int[]{DENIED_IF_PARTIAL,
                        AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_FLAG_DISABLED};
                }
                yield new int[]{DENIED_IF_FULL,
                    AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE};
            }
        };

        int enforcedLevel = enforcedState[0];
        int exemption = enforcedState[1];

        boolean blocked = switch (enforcedLevel) {
            case DENIED_IF_PARTIAL -> blockState == DENIED_IF_PARTIAL;
            case DENIED_IF_FULL -> true;
            default -> false;
        };

        int usage = getUsageForProtoLog(aa.getUsage());
        boolean isStrict = blockState == DENIED_IF_FULL;
        String msg = "AudioHardening focus request for req "
                + focusReqType
                + (!blocked ? " would be " : " ")
                + "ignored for "
                + packageName + " (" + callingUid + "), "
                + clientId
                + ", level: " + (isStrict ? "full" : "partial")
                + " usage: " + usage + " exemption: " + exemption;
        mEventLogger.enqueueAndSlog(msg, EventLogger.Event.ALOGW, TAG);
        if (overrideState == AudioManager.HARDENING_THROW) {
            throw new IllegalStateException(msg);
        }
        AudioAtomsLog.write(AudioAtomsLog.AUDIO_HARDENING_REPORTED, callingUid,
                AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_FOCUS,
                isStrict, blocked,
                usage, exemption);

        metricsLogFocusReq(blocked, focusReqType, callingUid);
        return blocked;
    }

    /**
     * Log metrics for the focus request
     * @param blocked true if the call blocked
     * @param focusReq the type of focus request
     * @param callingUid the UID of the caller
     * @param unblockedBySdk if blocked is false,
     *                       true indicates it was unblocked thanks to an older SDK
     */
    /*package*/ void metricsLogFocusReq(boolean blocked, int focusReq, int callingUid) {
        final String metricId = blocked ? METRIC_COUNTERS_FOCUS_DENIAL.get(focusReq)
                : METRIC_COUNTERS_FOCUS_GRANT.get(focusReq);
        if (TextUtils.isEmpty(metricId)) {
            Slog.e(TAG, "Bad string for focus metrics gain:" + focusReq + " blocked:" + blocked);
            return;
        }
        try {
            Counter.logIncrementWithUid(metricId, callingUid);
        } catch (Exception e) {
            Slog.e(TAG, "Counter error metricId:" + metricId + " for focus req:" + focusReq
                    + " from uid:" + callingUid, e);
        }
    }

    private String getPackNameForUid(int uid) {
        final long token = Binder.clearCallingIdentity();
        try {
            final String[] names = mPackageManager.getPackagesForUid(uid);
            if (names == null
                    || names.length == 0
                    || TextUtils.isEmpty(names[0])) {
                return "[" + uid + "]";
            }
            return names[0];
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Notes the given op without throwing
     * @param op the appOp code
     * @param uid the calling uid
     * @param packageName the package name of the caller
     * @param attributionTag attribution of the caller
     * @return return false if the operation is not allowed
     */
    private boolean noteOp(int op, int uid, @NonNull String packageName,
            @Nullable String attributionTag) {
        if (mAppOps.noteOpNoThrow(op, uid, packageName, attributionTag, null)
                != AppOpsManager.MODE_ALLOWED) {
            return false;
        }
        return true;
    }

    private boolean isPrivileged(int uid) {
        return holdsPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED, uid)
                || holdsPermission(Manifest.permission.MODIFY_AUDIO_ROUTING, uid)
                || holdsPermission(Manifest.permission.MODIFY_PHONE_STATE, uid)
                || uid < UserHandle.AID_APP_START;

    }
    private boolean holdsPermission(String permission, int uid) {
        return mContext.checkPermission(permission, -1, uid) == PackageManager.PERMISSION_GRANTED;
    }
}
