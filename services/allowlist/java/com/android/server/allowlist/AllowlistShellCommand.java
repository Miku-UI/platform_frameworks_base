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

package com.android.server.allowlist;

import android.annotation.NonNull;
import android.app.appfunctions.flags.Flags;
import android.content.pm.SignedPackage;
import android.os.Binder;
import android.os.Process;
import android.os.ShellCommand;
import android.os.allowlist.AllowlistManager;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Shell command handler for {@link AllowlistService}.
 */
public final class AllowlistShellCommand extends ShellCommand {

    private final AllowlistService mService;

    public AllowlistShellCommand(AllowlistService service) {
        mService = service;
    }

    @Override
    public int onCommand(String cmd) {
        if (!Flags.enableAppFunctionPermissionV2()) {
            getErrPrintWriter().println("Allowlist feature flag is not enabled.");
            return -1;
        }
        if (cmd == null) {
            return handleDefaultCommands(null);
        }

        PrintWriter pw = getOutPrintWriter();
        return switch (cmd) {
            case "add-packages" -> runAddPackages(pw);
            case "add-package-multimap" -> runAddPackageMultiMap(pw);
            case "remove-package" -> runRemovePackage(pw);
            case "clear-shell-allowlist" -> runClearShellAllowlist(pw);
            case "list-shell-allowlist" -> runListShellAllowlist(pw);
            default -> handleDefaultCommands(cmd);
        };
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Allowlist service (allowlist) commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println(
                "  add-packages <ALLOWLIST_ID> <PACKAGE_NAME_1>:<CERT_DIGEST_HEX_1>,"
                        + "<PACKAGE_NAME_2>:<CERT_DIGEST_HEX_2>,...");
        pw.println(
                "    Add a list of packages with their optional certificate digests in hex format"
                        + " to the shell allowlist for a given ID. Use a wildcard * to "
                        + "represent all packages.");
        pw.println(
                "  add-package-multimap <ALLOWLIST_ID> <PACKAGE_NAME>:<CERT_DIGEST_HEX> "
                        + "<TARGET_APP_1>:<CERT_DEGEST_HEX_1>,<TARGET_APP_2>:<CERT_DEGEST_HEX_2>,"
                        + "...");
        pw.println(
                "    Add a package with its certificate digest in hex format and a list of "
                        + "target apps with their optional certificate digests to the shell "
                        + "allowlist for a given ID. Use a wildcard * to represent all "
                        + "target apps.");
        pw.println("  remove-package <ALLOWLIST_ID> <PACKAGE_NAME>:<CERT_DIGEST>");
        pw.println(
                "    Remove a package along with its associated target apps if any from the shell"
                        + " allowlist for a given ID.");
        pw.println("  clear-shell-allowlist <ALLOWLIST_ID>");
        pw.println("    Clear shell allowlist for a given allowlist ID.");
        pw.println("  list-shell-allowlist <ALLOWLIST_ID>");
        pw.println("    List shell allowlist for a given allowlist ID.");
    }

    private int runAddPackages(PrintWriter pw) {
        int allowlistId = Integer.parseInt(getNextArgRequired());
        checkRootRequirementForAllowlist(allowlistId);

        String packageArg = getNextArgRequired();
        ArrayList<SignedPackage> signedPackages = new ArrayList<>();

        if (Objects.equals(packageArg.trim(), "*")) {
            signedPackages.add(new SignedPackage("*", null));
        } else {
            String[] packagesWithCerts = packageArg.split(",");

            for (String packageWithCert : packagesWithCerts) {
                signedPackages.add(getSignedPackageFromShell(packageWithCert));
            }
        }

        mService.addPackagesToShellAllowlist(allowlistId, signedPackages);
        pw.println("Added " + signedPackages + " to the Shell allowlist " + allowlistId);
        return 0;
    }

    private int runAddPackageMultiMap(PrintWriter pw) {
        int allowlistId = Integer.parseInt(getNextArgRequired());
        checkRootRequirementForAllowlist(allowlistId);

        String packageArg = getNextArgRequired();
        SignedPackage signedPackage;
        if (Objects.equals(packageArg.trim(), "*")) {
            signedPackage = new SignedPackage("*", null);
        } else {
            signedPackage = getSignedPackageFromShell(packageArg);
            if (!signedPackage.hasCertificateDigest()) {
                pw.println("Missing certificate digest.");
                return -1;
            }
        }

        ArrayList<SignedPackage> signedTargetApps = new ArrayList<>();
        String targetAppsStr = getNextArgRequired();
        if (Objects.equals(targetAppsStr.trim(), "*")) {
            signedTargetApps.add(new SignedPackage("*", null));
        } else {
            String[] targetApps = targetAppsStr.split(",");
            for (String targetApp : targetApps) {
                signedTargetApps.add(getSignedPackageFromShell(targetApp));
            }
        }

        mService.addPackageMultiMapToShellAllowlist(allowlistId, signedPackage, signedTargetApps);
        pw.println("Added " + signedPackage + " => " + signedTargetApps + " to the Shell allowlist "
                + allowlistId);
        return 0;
    }

    @NonNull
    private SignedPackage getSignedPackageFromShell(@NonNull String packageWithCert) {
        String[] parts = packageWithCert.trim().split(":");
        String packageName;
        byte[] certDigestHex;

        if (parts.length == 1) {
            packageName = parts[0];
            certDigestHex = null;
        } else if (parts.length == 2) {
            packageName = parts[0];
            try {
                certDigestHex = HexFormat.of().parseHex(parts[1]);
            } catch (IllegalArgumentException e) {
                getErrPrintWriter().println(
                        "Invalid certificate digest. It needs to be in Hex format.");
                throw e;
            }
        } else {
            throw new IllegalArgumentException("Invalid package and cert format.");
        }

        return new SignedPackage(packageName, certDigestHex);
    }

    private int runRemovePackage(PrintWriter pw) {
        int allowlistId = Integer.parseInt(getNextArgRequired());
        checkRootRequirementForAllowlist(allowlistId);

        String packageWithCert = getNextArgRequired();
        SignedPackage signedPackage = getSignedPackageFromShell(packageWithCert);

        mService.removePackageFromShellAllowlist(allowlistId, signedPackage);
        pw.println("Removed " + signedPackage + " from Shell allowlist " + allowlistId);
        return 0;
    }

    private int runClearShellAllowlist(PrintWriter pw) {
        int allowlistId = Integer.parseInt(getNextArgRequired());
        checkRootRequirementForAllowlist(allowlistId);

        mService.clearShellAllowlist(allowlistId);
        pw.println("Cleared Shell allowlist " + allowlistId);
        return 0;
    }

    private int runListShellAllowlist(PrintWriter pw) {
        int allowlistId = Integer.parseInt(getNextArgRequired());
        checkRootRequirementForAllowlist(allowlistId);

        mService.dumpShellAllowlist(pw, allowlistId);
        return 0;
    }

    private void checkRootRequirementForAllowlist(int allowlistId) {
        if (allowlistId == AllowlistManager.ALLOWLIST_ID_COMPUTER_CONTROL) {
            if (Binder.getCallingUid() != Process.ROOT_UID) {
                throw new SecurityException(
                        "Root privileges are required to use this command for allowlist "
                                + allowlistId + ".");
            }
        }
    }
}
