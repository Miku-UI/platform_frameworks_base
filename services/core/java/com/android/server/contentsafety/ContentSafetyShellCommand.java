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

package com.android.server.contentsafety;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.os.Binder;
import android.os.ShellCommand;

import java.io.PrintWriter;
import java.util.Objects;

/** @hide */
final class ContentSafetyShellCommand extends ShellCommand {
    private static final String TAG = "ContentSafetyShellCommand";

    @NonNull private final ContentSafetyManagerService mService;

    ContentSafetyShellCommand(@NonNull ContentSafetyManagerService service) {
        mService = service;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }

        return switch (cmd) {
            case "set-temporary-services" -> setTemporaryServices();
            case "get-services" -> getConfiguredServices();
            default-> handleDefaultCommands(cmd);
        };
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("ContentSafetyShellCommand commands: ");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println();
        pw.println(
                "  set-temporary-services [ContentSafetyServiceComponentName] "
                        + "[ContentSafetySandBoxedServiceComponentName] "
                        + "[ContentSafetySettingsServiceComponentName] [DURATION]");
        pw.println("    Temporarily (for DURATION ms) changes the service implementations.");
        pw.println("    To reset, call without any arguments.");

        pw.println("  get-services To get the names of services that are currently being used.");
    }

    private int setTemporaryServices() {
        final PrintWriter out = getOutPrintWriter();
        final String contentSafetyServiceName = getNextArg();
        final String contentSafetySettingsServiceName = getNextArg();
        final String contentSafetyPccServiceName = getNextArg();
        final String contentSafetyIsolatedServiceName = getNextArg();


        if (getRemainingArgsCount() == 0
                && contentSafetyServiceName == null
                && contentSafetySettingsServiceName == null
                && (contentSafetyPccServiceName == null
                   || contentSafetyIsolatedServiceName == null)) {
            ContentSafetyManagerService.enforceShellOnly(
                    Binder.getCallingUid(), "resetTemporaryServices");
            mService.resetTemporaryServices();
            out.println("ContentSafetyManagerService temporary reset. ");
            return 0;
        }

        Objects.requireNonNull(contentSafetyServiceName);
        Objects.requireNonNull(contentSafetySettingsServiceName);
        if (!isSandboxedPccService(contentSafetyPccServiceName)) {
            Objects.requireNonNull(contentSafetyIsolatedServiceName);
        }

        final int duration = Integer.parseInt(getNextArgRequired());
        mService.setTemporaryServices(
                new ComponentName[] {
                        ComponentName.unflattenFromString(contentSafetyServiceName),
                        ComponentName.unflattenFromString(contentSafetySettingsServiceName),
                        isSandboxedPccService(contentSafetyPccServiceName)
                        ? ComponentName.unflattenFromString(contentSafetyPccServiceName) : null,
                        isSandboxedPccService(contentSafetyPccServiceName)
                        ? null : ComponentName.unflattenFromString(
                                contentSafetyIsolatedServiceName),
                },
                duration);
        out.println(
                "ContentSafetyService temporarily set to "
                        + contentSafetyServiceName
                        + " \n and \n ContentSafetySettingsService set to "
                        + contentSafetySettingsServiceName
                        + " \n and \n contentSafetyPccServiceName set to "
                        + contentSafetyPccServiceName
                        + " \n and \n contentSafetyIsolatedServiceName set to "
                        + contentSafetyIsolatedServiceName
                        + " for "
                        + duration
                        + "ms");
        return 0;
    }

    private int getConfiguredServices() {
        final PrintWriter out = getOutPrintWriter();
        String[] services = mService.getServiceNames();
        if (services.length == 4) {
            out.println(
                    "ContentSafetyService set to :  "
                            + services[0]
                            + " \n and \n ContentSafetySettingsService set to : "
                            + services[1]
                            + " \n and \n ContentSafetyPccService set to : "
                            + services[2]
                            + " \n and \n ContentSafetyIsolatedService set to : "
                            + services[3]);
        }
        return 0;
    }

    private boolean isSandboxedPccService(String pccServiceName) {
        return pccServiceName != null && !pccServiceName.isEmpty();
    }
}
