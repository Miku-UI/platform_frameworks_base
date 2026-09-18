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

package com.android.server.companion.datatransfer.crossdevicesync.services;

import android.os.ShellCommand;

import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelper;

import java.io.PrintWriter;

/** {@link ShellCommand} Handler for commands passed to the {@link SyncService}. */
public class SyncServiceShellCommand extends ShellCommand {
    private final NotificationHelper mNotificationHelper;

    public SyncServiceShellCommand(NotificationHelper notificationHelper) {
        mNotificationHelper = notificationHelper;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null || "dump".equals(cmd)) {
            return -1;
        }
        switch (cmd) {
            case "reset":
                String type = getNextArg();
                if ("notifications".equals(type)) {
                    mNotificationHelper.reset();
                    getOutPrintWriter().println("Reset all Notifications for crossdevicesync");
                    return 0;
                } else {
                    getOutPrintWriter().println("Unknown command : reset " + type);
                    return -1;
                }
            case "help":
            case "-h":
                onHelp();
                return 0;
            default:
                return handleDefaultCommands(cmd);
        }
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("SyncService commands:");
        pw.println("  help");
        pw.println("      Print this help text.");
        pw.println("  reset notifications");
        pw.println("      Resets all notifications to show them again if needed.");
        pw.println();
    }
}
