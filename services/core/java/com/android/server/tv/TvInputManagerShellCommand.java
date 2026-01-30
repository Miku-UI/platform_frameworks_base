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

package com.android.server.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.util.Slog;

import java.io.PrintWriter;
import java.util.List;

/**
 * Shell commands for {@link TvInputManagerService}.
 */
final class TvInputManagerShellCommand extends ShellCommand {
    private static final String TAG = "TvInputManagerShellCommand";
    private final TvInputManagerService mService;

    TvInputManagerShellCommand(TvInputManagerService service) {
        mService = service;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        final PrintWriter pw = getOutPrintWriter();
        switch (cmd) {
            case "get-input-list":
                return getTvInputList(pw);
            default:
                return handleDefaultCommands(cmd);
        }
    }

    private int getTvInputList(PrintWriter pw) {
        int targetUserId = UserHandle.USER_CURRENT;
        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                targetUserId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                pw.println("Error: Unknown option: " + opt);
                return -1;
            }
        }

        if (targetUserId == UserHandle.USER_CURRENT) {
            targetUserId = ActivityManager.getCurrentUser();
        }

        Context serviceContext = mService.getContext();
        TvInputManager tvInputManager;

        if (targetUserId == serviceContext.getUserId()) {
            tvInputManager = serviceContext.getSystemService(TvInputManager.class);
        } else {
            try {
                Context userContext = serviceContext.createPackageContextAsUser(
                        serviceContext.getPackageName(), 0, UserHandle.of(targetUserId));
                tvInputManager = userContext.getSystemService(TvInputManager.class);
            } catch (PackageManager.NameNotFoundException e) {
                Slog.e(TAG, "Could not create context for user " + targetUserId, e);
                pw.println("Error: Could not create context for user " + targetUserId);
                return -1;
            }
        }

        if (tvInputManager == null) {
            pw.println("Error: Could not get TvInputManager for user " + targetUserId);
            return -1;
        }

        final List<TvInputInfo> inputs = tvInputManager.getTvInputList();

        if (inputs == null || inputs.isEmpty()) {
            pw.println("No TV inputs found for user " + targetUserId);
            return 0;
        }

        pw.println("TV Inputs for user " + targetUserId + ":");
        for (TvInputInfo info : inputs) {
            pw.println("  " + info.getId() + " (" + info.loadLabel(serviceContext) + ")");

            if (info.getComponent() != null) {
                pw.println("    Component: " + info.getComponent().flattenToString());
            } else {
                pw.println("    Component: null");
            }

            pw.println("    Type: " + info.getType());
            if (info.getHdmiDeviceInfo() != null) {
                pw.println("    HDMI Device Info: " + info.getHdmiDeviceInfo());
            }
            if (info.getParentId() != null) {
                pw.println("    Parent ID: " + info.getParentId());
            }

            Intent setupIntent = info.createSetupIntent();
            if (setupIntent != null && setupIntent.getComponent() != null) {
                pw.println("    Setup Activity: " + setupIntent.getComponent().flattenToString());
            } else {
                pw.println("    Setup Activity: null");
            }

            pw.println("    Is Passthrough: " + info.isPassthroughInput());
        }
        return 0;
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("TvInputManagerService commands:");
        pw.println("  help");
        pw.println("    Prints this help text.");
        pw.println("  list-inputs [--user <USER_ID> | current]");
        pw.println("    Lists all registered TV inputs for the given user.");
        pw.println("    --user <USER_ID>: Specify user ID. 'current' means the current"
                + "foreground user.");
        pw.println("                      (Default: current user)");
    }
}
