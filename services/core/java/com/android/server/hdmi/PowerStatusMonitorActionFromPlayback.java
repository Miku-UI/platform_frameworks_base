/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.hdmi;

import static android.hardware.hdmi.HdmiControlManager.POWER_STATUS_STANDBY;

import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

/**
 * This action is used by playback devices to query TV's power status such that they can go to
 * standby when the TV reports power off.
 */
public class PowerStatusMonitorActionFromPlayback extends HdmiCecFeatureAction {
    private static final String TAG = "PowerStatusMonitorActionFromPlayback";
    // State that waits for next monitoring.
    private static final int STATE_WAIT_FOR_NEXT_MONITORING = 1;

    // State that waits for <Report Power Status> once sending <Give Device Power Status>
    // to the TV.
    private static final int STATE_WAIT_FOR_REPORT_POWER_STATUS = 2;
    // Number of consecutive standby reports required before going to standby.
    private static final int REQUIRED_CONSECUTIVE_STANDBY_REPORTS = 2;

    // Monitoring interval (60s)
    @VisibleForTesting
    protected static final int MONITORING_INTERVAL_MS = 60000;
    // Timeout once sending <Give Device Power Status>

    // Counter for consecutive standby reports
    private int mConsecutiveStandbyReports = 0;

    PowerStatusMonitorActionFromPlayback(HdmiCecLocalDevice source) {
        super(source);
    }

    @Override
    boolean start() {
        // Start after timeout since the device just finished allocation.
        mState = STATE_WAIT_FOR_NEXT_MONITORING;
        addTimer(mState, MONITORING_INTERVAL_MS);
        mConsecutiveStandbyReports = 0;
        return true;
    }

    @Override
    boolean processCommand(HdmiCecMessage cmd) {
        if (mState == STATE_WAIT_FOR_REPORT_POWER_STATUS
                && cmd.getOpcode() == Constants.MESSAGE_REPORT_POWER_STATUS
                && cmd.getSource() == Constants.ADDR_TV) {
            return handleReportPowerStatusFromTv(cmd);
        }
        return false;
    }

    private boolean handleReportPowerStatusFromTv(HdmiCecMessage cmd) {
        int powerStatus = cmd.getParams()[0] & 0xFF;
        if (powerStatus != POWER_STATUS_STANDBY) {
            mConsecutiveStandbyReports = 0; // Reset counter if TV is not in standby.
        } else {
            mConsecutiveStandbyReports++;  // Increase counter if TV reports standby.
        }

        if (mConsecutiveStandbyReports >= REQUIRED_CONSECUTIVE_STANDBY_REPORTS) {
            HdmiCecLocalDeviceSource source = source();
            Slog.d(TAG, "TV reported standby, going to sleep.");
            // Invalidate the internal active source record before calling the active source logic.
            // This logic allows the user to be visually notified in case they have a faulty CEC
            // setup (e.g. an old TV panel) where they can easily disable the setting that sends
            // their source device to sleep.
            source.mService
                    .setActiveSource(Constants.ADDR_INVALID, Constants.INVALID_PHYSICAL_ADDRESS,
                    "HdmiCecLocalDevicePlayback#onStandby()");
            source.onActiveSourceLost();
            finish();
        }
        // Schedule next monitoring.
        mState = STATE_WAIT_FOR_NEXT_MONITORING;
        addTimer(mState, MONITORING_INTERVAL_MS);
        return true;
    }

    @Override
    void handleTimerEvent(int state) {
        if (mState != state) {
            return;
        }

        switch (mState) {
            case STATE_WAIT_FOR_NEXT_MONITORING:
                queryPowerStatus();
                break;
            case STATE_WAIT_FOR_REPORT_POWER_STATUS:
                // Timer expired while waiting for <Report Power Status>.
                // This means we didn't receive a power status report from the TV in time.
                Slog.d(TAG, "Timeout waiting for <Report Power Status>."
                        + " Resetting standby counter.");
                mConsecutiveStandbyReports = 0; // Reset counter on timeout
                mState = STATE_WAIT_FOR_NEXT_MONITORING;
                addTimer(mState, MONITORING_INTERVAL_MS);
                break;
            default:
                break;
        }
    }

    private void queryPowerStatus() {
        sendCommand(HdmiCecMessageBuilder.buildGiveDevicePowerStatus(getSourceAddress(),
                Constants.ADDR_TV));

        mState = STATE_WAIT_FOR_REPORT_POWER_STATUS;
        addTimer(mState, HdmiConfig.TIMEOUT_MS);
    }
}
