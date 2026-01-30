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

package com.android.server.pm;

import android.content.pm.PackageManager;
import android.content.pm.SigningDetails.SignatureSchemeVersion;
import android.os.SystemClock;

import com.android.internal.util.FrameworkStatsLog;

/**
 * A helper class to collect and log metrics for the initial scan of a single package during system
 * boot. This class uses a builder pattern to gather metric data before logging.
 */
public final class InitAppScanMetrics {

    private boolean mIsFsiEnabled;
    private int mNumApkSplits;
    private int mSignatureSchemeVersion =
            FrameworkStatsLog.INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__UNKNOWN;

    private final long mTotalScanStartTimeMillis;
    private long mTotalScanDurationMillis;

    private int mInitAppScanOutcome =
            FrameworkStatsLog.INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__UNSPECIFIED;

    /** Starts the timer for the total scan duration. */
    public InitAppScanMetrics() {
        this.mTotalScanStartTimeMillis = SystemClock.uptimeMillis();
    }

    /**
     * Translates a package managers signature scheme version into the corresponding init app scan
     * metric signature scheme version.
     *
     * @param signatureSchemeVersion A package manager SigningDetails.SignatureScheme.* enum value.
     * @return The corresponding init app scan metric signature scheme enum value.
     */
    private static int translateToSignatureSchemeVersion(int signatureSchemeVersion) {
        switch (signatureSchemeVersion) {
            case SignatureSchemeVersion.UNKNOWN:
                return FrameworkStatsLog.INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__UNKNOWN;
            case SignatureSchemeVersion.JAR:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__JAR;
            case SignatureSchemeVersion.SIGNING_BLOCK_V2:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__V2;
            case SignatureSchemeVersion.SIGNING_BLOCK_V3:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__V3;
            case SignatureSchemeVersion.SIGNING_BLOCK_V4:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__V4;

            default:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__SIGNATURE_SCHEME_VERSION__UNKNOWN;
        }
    }

    /**
     * Translates a package manager installation error code into the corresponding init app scan
     * outcome for metrics logging.
     *
     * @param returnCode A PackageManager.INSTALL_* error code.
     * @return The corresponding app scan outcome enum value.
     */
    private static int translateToInitAppScanOutcome(int returnCode) {
        switch (returnCode) {
            case PackageManager.INSTALL_SUCCEEDED:
                return FrameworkStatsLog.INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__SUCCESS;
            case PackageManager.INSTALL_PARSE_FAILED_NO_CERTIFICATES:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_NO_CERTIFICATES;
            case PackageManager.INSTALL_FAILED_VERIFICATION_FAILURE:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_VERIFICATION;
            case PackageManager.INSTALL_FAILED_UPDATE_INCOMPATIBLE:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_UPDATE_INCOMPATIBLE;
            case PackageManager.INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_INCONSISTENT_CERTIFICATES;
            case PackageManager.INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_CERTIFICATE_ENCODING;
            case PackageManager.INSTALL_FAILED_DUPLICATE_PACKAGE:
            case PackageManager.INSTALL_FAILED_INVALID_APK:
            case PackageManager.INSTALL_FAILED_PACKAGE_CHANGED:
            case PackageManager.INSTALL_FAILED_DUPLICATE_PERMISSION:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_SCAN_VALIDATION;

            default:
                return FrameworkStatsLog
                        .INIT_APP_SCAN_REPORTED__INIT_APP_SCAN_OUTCOME__FAILURE_OTHER;
        }
    }

    /**
     * Sets whether the scanned package is allow-listed for FSI check.
     *
     * @param isFsiEnabled True if the package has FSI enabled, false otherwise.
     * @return This {@link InitAppScanMetrics} instance for chaining.
     */
    public InitAppScanMetrics setIsFsiEnabled(boolean isFsiEnabled) {
        this.mIsFsiEnabled = isFsiEnabled;
        return this;
    }

    /**
     * Sets the number of APK splits for the scanned package.
     *
     * @param numApkSplits number of APK splits.
     * @return This {@link InitAppScanMetrics} instance for chaining.
     */
    public InitAppScanMetrics setNumApkSplits(int numApkSplits) {
        this.mNumApkSplits = numApkSplits;
        return this;
    }

    /**
     * Sets the signature scheme version used for package verification.
     *
     * @param signatureSchemeVersion The version of the signature scheme.
     * @return This {@link InitAppScanMetrics} instance for chaining.
     */
    public InitAppScanMetrics setSignatureSchemeVersion(int signatureSchemeVersion) {
        this.mSignatureSchemeVersion =
            translateToSignatureSchemeVersion(signatureSchemeVersion);
        return this;
    }

    /**
     * Sets the final outcome of the APK scan.
     *
     * @param returnCode A PackageManager.INSTALL_* error code.
     * @return This {@link InitAppScanMetrics} instance for chaining.
     */
    public InitAppScanMetrics setInitAppScanOutcome(int returnCode) {
        this.mInitAppScanOutcome = translateToInitAppScanOutcome(returnCode);
        return this;
    }

    /** Finalizes and logs the collected metrics to FrameworkStatsLog. */
    public void log() {
        this.mTotalScanDurationMillis = SystemClock.uptimeMillis() - mTotalScanStartTimeMillis;

        FrameworkStatsLog.write(
                FrameworkStatsLog.INIT_APP_SCAN_REPORTED,
                mIsFsiEnabled,
                mNumApkSplits,
                mSignatureSchemeVersion,
                mTotalScanDurationMillis,
                mInitAppScanOutcome);
    }
}
