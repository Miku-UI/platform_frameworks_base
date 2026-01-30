/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.biometrics.log;

import static android.hardware.biometrics.BiometricFaceConstants.FACE_ACQUIRED_START;

import static com.android.server.biometrics.log.BiometricFrameworkStatsLogger.actionToString;
import static com.android.server.biometrics.log.BiometricFrameworkStatsLogger.authenticatedStateToString;
import static com.android.server.biometrics.log.BiometricFrameworkStatsLogger.clientToString;
import static com.android.server.biometrics.log.BiometricFrameworkStatsLogger.modalityToString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.SensorManager;
import android.hardware.biometrics.BiometricConstants;
import android.hardware.biometrics.BiometricsProtoEnums;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.biometrics.AuthenticationStatsCollector;
import com.android.server.biometrics.Utils;

import java.util.Arrays;

/**
 * Logger for all reported Biometric framework events.
 */
public class BiometricLogger {

    private static final String TAG = "BiometricLogger";
    private static final boolean VERBOSE = Build.IS_DEBUGGABLE && Log.isLoggable(TAG, Log.VERBOSE);

    private final Handler mHandler;
    private final int mStatsModality;
    private final int mStatsAction;
    private final int mStatsClient;
    private final BiometricFrameworkStatsLogger mSink;
    @Nullable private final AuthenticationStatsCollector mAuthenticationStatsCollector;
    @NonNull private final ALSProbe mALSProbe;

    private long mFirstAcquireTimeMs;
    private boolean mShouldLogMetrics = true;

    /**
     * Get a new logger with all unknown fields (for operations that do not require logs).
     *
     * @param context system_server context
     * @param handler Handler for log events. This is not used for the public "log" methods, but
     *                for internal data collection from SensorManager, etc.
     */
    public static BiometricLogger ofUnknown(@NonNull Context context, @NonNull Handler handler) {
        return new BiometricLogger(context, handler, BiometricsProtoEnums.MODALITY_UNKNOWN,
                BiometricsProtoEnums.ACTION_UNKNOWN, BiometricsProtoEnums.CLIENT_UNKNOWN,
                null /* AuthenticationStatsCollector */);
    }

    /**
     * Creates a new logger for an instance of a biometric operation.
     *
     * Do not reuse across operations. Instead, create a new one or use
     * {@link #swapAction(Context, int)}.
     *
     * @param context system_server context
     * @param handler Handler for log events. This is not used for the public "log" methods, but
     *                for internal data collection from SensorManager, etc.
     * @param statsModality One of {@link BiometricsProtoEnums} MODALITY_* constants.
     * @param statsAction One of {@link BiometricsProtoEnums} ACTION_* constants.
     * @param statsClient One of {@link BiometricsProtoEnums} CLIENT_* constants.
     */
    public BiometricLogger(@NonNull Context context, @NonNull Handler handler,
            int statsModality, int statsAction, int statsClient,
            @Nullable AuthenticationStatsCollector authenticationStatsCollector) {
        this(handler, statsModality, statsAction, statsClient,
                BiometricFrameworkStatsLogger.getInstance(),
                authenticationStatsCollector,
                context.getSystemService(SensorManager.class));
    }

    @VisibleForTesting
    BiometricLogger(@NonNull Handler handler,
            int statsModality, int statsAction, int statsClient,
            BiometricFrameworkStatsLogger logSink,
            @Nullable AuthenticationStatsCollector statsCollector,
            SensorManager sensorManager) {
        mHandler = handler;
        mStatsModality = statsModality;
        mStatsAction = statsAction;
        mStatsClient = statsClient;
        mSink = logSink;
        mAuthenticationStatsCollector = statsCollector;
        mALSProbe = new ALSProbe(sensorManager, handler);
    }

    /** Creates a new logger with the action replaced with the new action. */
    public BiometricLogger swapAction(@NonNull Context context, int statsAction) {
        return new BiometricLogger(context, mHandler, mStatsModality, statsAction, mStatsClient,
                null /* AuthenticationStatsCollector */);
    }

    /** Disable logging metrics and only log critical events, such as system health issues. */
    public void disableMetrics() {
        mShouldLogMetrics = false;
        mALSProbe.destroy();
    }

    /** {@link BiometricsProtoEnums} CLIENT_* constants */
    public int getStatsClient() {
        return mStatsClient;
    }

    private boolean shouldSkipLogging() {
        boolean shouldSkipLogging = (mStatsModality == BiometricsProtoEnums.MODALITY_UNKNOWN
                || mStatsAction == BiometricsProtoEnums.ACTION_UNKNOWN);

        if (mStatsModality == BiometricsProtoEnums.MODALITY_UNKNOWN) {
            Slog.w(TAG, "Unknown field detected: MODALITY_UNKNOWN, will not report metric");
        }

        if (mStatsAction == BiometricsProtoEnums.ACTION_UNKNOWN) {
            Slog.w(TAG, "Unknown field detected: ACTION_UNKNOWN, will not report metric");
        }

        if (mStatsClient == BiometricsProtoEnums.CLIENT_UNKNOWN) {
            Slog.w(TAG, "Unknown field detected: CLIENT_UNKNOWN");
        }

        return shouldSkipLogging;
    }

    /** Log an acquisition event. */
    public void logOnAcquired(Context context, OperationContextExt operationContext,
            int acquiredInfo, int vendorCode, int targetUserId) {
        if (!mShouldLogMetrics) {
            return;
        }

        final boolean isFace = mStatsModality == BiometricsProtoEnums.MODALITY_FACE;
        final boolean isFingerprint = mStatsModality == BiometricsProtoEnums.MODALITY_FINGERPRINT;
        if (isFace || isFingerprint) {
            if ((isFingerprint && acquiredInfo == FingerprintManager.FINGERPRINT_ACQUIRED_START)
                    || (isFace && acquiredInfo == FACE_ACQUIRED_START)) {
                mFirstAcquireTimeMs = System.currentTimeMillis();
            }
        } else if (acquiredInfo == BiometricConstants.BIOMETRIC_ACQUIRED_GOOD) {
            if (mFirstAcquireTimeMs == 0) {
                mFirstAcquireTimeMs = System.currentTimeMillis();
            }
        }
        if (VERBOSE) {
            Slog.v(TAG, "Acquired! Modality: " + modalityAsString()
                    + ", User: " + targetUserId
                    + ", IsCrypto: " + operationContext.isCrypto()
                    + ", Action: " + actionAsString()
                    + ", Client: " + clientAsString()
                    + ", AcquiredInfo: " + acquiredInfo
                    + ", VendorCode: " + vendorCode);
        }

        if (shouldSkipLogging()) {
            return;
        }

        mSink.acquired(operationContext,
                mStatsModality, mStatsAction, mStatsClient,
                Utils.isDebugEnabled(context, targetUserId),
                acquiredInfo, vendorCode, targetUserId);
    }

    /** Log an error during an operation. */
    public void logOnError(Context context, OperationContextExt operationContext,
            int error, int vendorCode, int targetUserId) {
        if (!mShouldLogMetrics) {
            return;
        }

        final long latency = mFirstAcquireTimeMs != 0
                ? (System.currentTimeMillis() - mFirstAcquireTimeMs) : -1;

        if (VERBOSE) {
            Slog.v(TAG, "Error! Modality: " + modalityAsString()
                    + ", User: " + targetUserId
                    + ", IsCrypto: " + operationContext.isCrypto()
                    + ", Action: " + actionAsString()
                    + ", Client: " + clientAsString()
                    + ", Error: " + error
                    + ", VendorCode: " + vendorCode
                    + ", Latency: " + latency);
        } else {
            Slog.d(TAG, "Error! Modality: " + modalityAsString()
                    + ", latency: " + latency);
        }

        if (shouldSkipLogging()) {
            return;
        }

        mSink.error(operationContext,
                mStatsModality, mStatsAction, mStatsClient,
                Utils.isDebugEnabled(context, targetUserId), latency,
                error, vendorCode, targetUserId);
    }

    /** Log authentication attempt. */
    public void logOnAuthenticated(Context context, OperationContextExt operationContext,
            boolean authenticated, boolean requireConfirmation, int targetUserId,
            boolean isBiometricPrompt) {
        // Do not log metrics when fingerprint enrollment reason is ENROLL_FIND_SENSOR
        if (!mShouldLogMetrics) {
            return;
        }

        if (mAuthenticationStatsCollector != null) {
            mAuthenticationStatsCollector.authenticate(targetUserId, authenticated);
        }

        int authState = FrameworkStatsLog.BIOMETRIC_AUTHENTICATED__STATE__UNKNOWN;
        if (!authenticated) {
            authState = FrameworkStatsLog.BIOMETRIC_AUTHENTICATED__STATE__REJECTED;
        } else {
            // Authenticated
            if (isBiometricPrompt && requireConfirmation) {
                authState = FrameworkStatsLog.BIOMETRIC_AUTHENTICATED__STATE__PENDING_CONFIRMATION;
            } else {
                authState = FrameworkStatsLog.BIOMETRIC_AUTHENTICATED__STATE__CONFIRMED;
            }
        }

        // Only valid if we have a first acquired time, otherwise set to -1
        final long latency = mFirstAcquireTimeMs != 0
                ? (System.currentTimeMillis() - mFirstAcquireTimeMs)
                : -1;

        if (VERBOSE) {
            Slog.v(TAG, "Authenticated! Modality: " + modalityAsString()
                    + ", User: " + targetUserId
                    + ", IsCrypto: " + operationContext.isCrypto()
                    + ", Client: " + clientAsString()
                    + ", RequireConfirmation: " + requireConfirmation
                    + ", State: " + authenticatedStateToString(authState)
                    + ", Latency: " + latency
                    + ", Lux: " + mALSProbe.getMostRecentLux());
        } else {
            Slog.d(TAG, "Authenticated! Modality: " + modalityAsString()
                    + ", latency: " + latency);
        }

        if (shouldSkipLogging()) {
            return;
        }

        mSink.authenticate(operationContext,
                mStatsModality, mStatsAction, mStatsClient,
                Utils.isDebugEnabled(context, targetUserId),
                latency, authState, requireConfirmation, targetUserId, mALSProbe);
    }

    /** Log enrollment outcome. */
    public void logOnEnrolled(int targetUserId, long latency, boolean enrollSuccessful,
            int source, int templateId) {
        if (!mShouldLogMetrics) {
            return;
        }

        if (VERBOSE) {
            Slog.v(TAG, "Enrolled! Modality: " + modalityAsString()
                    + ", User: " + targetUserId
                    + ", Client: " + clientAsString()
                    + ", Latency: " + latency
                    + ", Lux: " + mALSProbe.getMostRecentLux()
                    + ", Success: " + enrollSuccessful
                    + ", TemplateId: " + templateId);
        } else {
            Slog.d(TAG, "Enrolled! Modality: " + modalityAsString()
                    + ", latency: " + latency);
        }

        if (shouldSkipLogging()) {
            return;
        }

        mSink.enroll(mStatsModality, mStatsAction, mStatsClient,
                targetUserId, latency, enrollSuccessful, mALSProbe.getMostRecentLux(), source,
                templateId);
    }

    /** Log un-enrollment. */
    public void logOnUnEnrolled(int targetUserId, int reason, int templateId) {
        if (!mShouldLogMetrics) {
            return;
        }

        if (VERBOSE) {
            Slog.v(TAG, "UnEnrolled! Modality: " + modalityAsString()
                    + ", User: " + targetUserId
                    + ", reason: " + reason
                    + ", templateId: " + templateId);
        } else {
            Slog.d(TAG, "UnEnrolled! Modality: " + modalityAsString());
        }

        if (shouldSkipLogging()) {
            return;
        }

        mSink.unenrolled(mStatsModality, targetUserId, reason, templateId);
    }

    /** Log enumeration. */
    public void logOnEnumerated(int targetUserId, int result, int[] templateIdsHal,
            int[] templateIdsFramework) {
        if (!mShouldLogMetrics) {
            return;
        }

        if (VERBOSE) {
            Slog.v(TAG, "Enumerated! Modality: " + modalityAsString()
                    + ", User: " + targetUserId
                    + ", result: " + result
                    + ", templateIdsHal: " + Arrays.toString(templateIdsHal)
                    + ", templateIdsFramework: " + Arrays.toString(templateIdsFramework));
        }

        if (shouldSkipLogging()) {
            return;
        }

        mSink.enumerated(mStatsModality, targetUserId, result, templateIdsHal,
                templateIdsFramework);
    }

    /** Report unexpected enrollment reported by the HAL. */
    public void logUnknownEnrollmentInHal() {
        if (shouldSkipLogging()) {
            return;
        }

        mSink.reportUnknownTemplateEnrolledHal(mStatsModality);
    }

    /** Report unknown enrollment in framework settings */
    public void logUnknownEnrollmentInFramework() {
        if (shouldSkipLogging()) {
            return;
        }

        mSink.reportUnknownTemplateEnrolledFramework(mStatsModality);
    }

    /** Report unknown enrollment in framework settings */
    public void logFingerprintsLoe() {
        if (shouldSkipLogging()) {
            return;
        }

        mSink.reportFingerprintsLoe(mStatsModality);
    }

    /**
     * Get a callback to start/stop ALS capture when the client runs. Do not create
     * multiple callbacks since there is at most one light sensor (they will all share
     * a single probe sampling from that sensor).
     *
     * If the probe should not run for the entire operation, do not set startWithClient and
     * start/stop the problem when needed.
     *
     * @param startWithClient if probe should start automatically when the operation starts.
     */
    @NonNull
    public CallbackWithProbe<Probe> getAmbientLightProbe(boolean startWithClient) {
        return new CallbackWithProbe<>(mALSProbe, startWithClient);
    }

    private String modalityAsString() {
        return modalityToString(mStatsModality);
    }

    private String actionAsString() {
        return actionToString(mStatsAction);
    }

    private String clientAsString() {
        return clientToString(mStatsClient);
    }
}
