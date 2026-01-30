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

package com.android.server.security.authenticationpolicy;

import static android.security.authenticationpolicy.AuthenticationPolicyManager.SUCCESS;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.RemoteException;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.IAuthenticationPolicyService;

import java.io.PrintWriter;

class AuthenticationPolicyServiceShellCommand extends ShellCommand {
    private IAuthenticationPolicyService mService;
    private UserHandle mCallingUser;

    AuthenticationPolicyServiceShellCommand(IAuthenticationPolicyService service, Context context) {
        mService = service;
        mCallingUser = context.getUser();
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        final PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "help":
                    onHelp();
                    return 0;
                case "enable-secure-lock-device":
                    return enableSecureLockDevice(pw);
                case "disable-secure-lock-device":
                    return disableSecureLockDevice(pw);
                case "is-secure-lock-device-enabled":
                    return isSecureLockDeviceEnabled(pw);
                case "get-secure-lock-device-availability":
                    return getSecureLockDeviceAvailability(pw);
                case "set-secure-lock-device-test-status":
                    return setSecureLockDeviceTestStatus(pw);
            }
        } catch (RemoteException e) {
            pw.println("Remote exception: " + e);
        }
        return -1;
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        dumpHelp(pw);
    }

    private void dumpHelp(@NonNull PrintWriter pw) {
        pw.println("Secure Lock Device commands:");
        pw.println("  help");
        pw.println("      Print this help text.");
        pw.println("  enable-secure-lock-device [some message]");
        pw.println("  disable-secure-lock-device [some message]");
        pw.println("  is-secure-lock-device-enabled");
        pw.println("  get-secure-lock-device-availability");
        pw.println("  on-strong-face-auth-success-confirmed");
        pw.println("  set-secure-lock-device-test-status [true/false]");
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int enableSecureLockDevice(@NonNull PrintWriter pw) throws RemoteException {
        EnableSecureLockDeviceParams enableParams = new EnableSecureLockDeviceParams(
                getNextArgRequired());
        mService.setSecureLockDeviceTestStatus(true);
        @AuthenticationPolicyManager.EnableSecureLockDeviceRequestStatus int status =
                mService.enableSecureLockDevice(mCallingUser, enableParams);
        if (status == SUCCESS) {
            pw.println("Secure lock device enabled");
        } else {
            pw.println("Secure lock device enable failed, returned status = " + status);
        }
        return status;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int disableSecureLockDevice(@NonNull PrintWriter pw) throws RemoteException {
        DisableSecureLockDeviceParams disableParams = new DisableSecureLockDeviceParams(
                getNextArgRequired());
        @AuthenticationPolicyManager.DisableSecureLockDeviceRequestStatus int status =
                mService.disableSecureLockDevice(mCallingUser, disableParams);
        mService.setSecureLockDeviceTestStatus(false);
        if (status == SUCCESS) {
            pw.println("Secure lock device disabled");
        } else {
            pw.println("Secure lock device disable failed, returned status = " + status);
        }
        return status;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int isSecureLockDeviceEnabled(@NonNull PrintWriter pw) throws RemoteException {
        boolean enabled = mService.isSecureLockDeviceEnabled();
        pw.println("isSecureLockDeviceEnabled: " + enabled);
        return 0;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int getSecureLockDeviceAvailability(@NonNull PrintWriter pw) throws RemoteException {
        @AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus int available =
                mService.getSecureLockDeviceAvailability(mCallingUser);
        pw.println("getSecureLockDeviceAvailability: " + available);
        return 0;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int setSecureLockDeviceTestStatus(@NonNull PrintWriter pw) throws RemoteException {
        boolean isTestMode = getNextArgRequired().equals("true");
        mService.setSecureLockDeviceTestStatus(isTestMode);
        pw.println("setSecureLockDeviceTestStatus(isTestMode = " + isTestMode + ")");
        return 0;
    }
}
