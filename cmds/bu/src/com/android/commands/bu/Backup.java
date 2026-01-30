/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.commands.bu;

import android.annotation.UserIdInt;
import android.app.backup.IBackupManager;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.system.OsConstants;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public final class Backup {
    static final String TAG = "bu";
    static final String USAGE = """
        WARNING: bu is deprecated and may be removed in a future release

        Usage: bu <command> [options]

        Commands:
          backup [-user USER_ID] [-apk|-noapk] [-obb|-noobb] [-shared|-noshared]
                 [-all] [-system|-nosystem] [-widgets|-nowidgets]
                 [-compress|-nocompress] [-keyvalue|-nokeyvalue] [PACKAGE...]
            Write an archive of the device's data to stdout.
            The package list is optional if -all or -shared is supplied.
            Example: bu backup -all -apk > backup.ab

          restore [-user USER_ID]
            Restore device contents from stdin.
            Example: bu restore < backup.ab

        Global options:
          -user USER_ID: user ID for which to perform the operation (default - system user)

        Options for 'backup':
          -apk/-noapk: do/don't back up .apk files (default -noapk)
          -obb/-noobb: do/don't back up .obb files (default -noobb)
          -shared|-noshared: do/don't back up shared storage (default -noshared)
          -all: back up all installed applications
          -system|-nosystem: include system apps in -all (default -system)
          -widgets|-nowidgets: do/don't back up widget data (default: -nowidgets)
          -compress|-nocompress: enable/disable compression of the backup data (default: -compress)
          -keyvalue|-nokeyvalue: do/don't back up apps that are key/value based
                                 (default: -nokeyvalue)

        'restore' command does not take any package names or options other than -user.""";

    static String[] mArgs;
    int mNextArg;
    IBackupManager mBackupManager;

    @VisibleForTesting
    Backup(IBackupManager backupManager) {
        mBackupManager = backupManager;
    }

    Backup() {
        mBackupManager = IBackupManager.Stub.asInterface(ServiceManager.getService("backup"));
    }

    @FormatMethod
    private static void logAndPrintError(@FormatString String logFormat, Object... args) {
        String message = String.format(Locale.ENGLISH, logFormat, args);
        Log.e(TAG, message);
        System.err.println("Error: " + message);
    }

    public static void main(String[] args) {
        try {
            new Backup().run(args);
        } catch (Exception e) {
            logAndPrintError("Error running backup/restore: %s", e);
        }
    }

    public void run(String[] args) {
        if (mBackupManager == null) {
            logAndPrintError("Can't obtain Backup Manager binder");
            return;
        }

        mArgs = args;
        String firstArg = nextArg();

        boolean isBackupCommand;
        if ("backup".equals(firstArg)) {
            isBackupCommand = true;
        } else if ("restore".equals(firstArg)) {
            isBackupCommand = false;
        } else {
            System.err.println(USAGE);
            return;
        }

        int userId = parseUserId();
        if (!isBackupActiveForUser(userId)) {
            logAndPrintError("BackupManager is not active for user %d", userId);
            return;
        }

        Log.i(TAG, "Beginning " + firstArg + " for user " + userId);
        if (isBackupCommand) {
            doBackup(OsConstants.STDOUT_FILENO, userId);
        } else {
            doRestore(OsConstants.STDIN_FILENO, userId);
        }
        Log.i(TAG, "Finished " + firstArg + " for user " + userId);
    }

    private void doBackup(int socketFd, @UserIdInt int userId) {
        ArrayList<String> packages = new ArrayList<String>();
        boolean saveApks = false;
        boolean saveObbs = false;
        boolean saveShared = false;
        boolean doEverything = false;
        boolean doWidgets = false;
        boolean allIncludesSystem = true;
        boolean doCompress = true;
        boolean doKeyValue = false;

        String arg;
        while ((arg = nextArg()) != null) {
            if (arg.startsWith("-")) {
                if ("-apk".equals(arg)) {
                    saveApks = true;
                } else if ("-noapk".equals(arg)) {
                    saveApks = false;
                } else if ("-obb".equals(arg)) {
                    saveObbs = true;
                } else if ("-noobb".equals(arg)) {
                    saveObbs = false;
                } else if ("-shared".equals(arg)) {
                    saveShared = true;
                } else if ("-noshared".equals(arg)) {
                    saveShared = false;
                } else if ("-system".equals(arg)) {
                    allIncludesSystem = true;
                } else if ("-nosystem".equals(arg)) {
                    allIncludesSystem = false;
                } else if ("-widgets".equals(arg)) {
                    doWidgets = true;
                } else if ("-nowidgets".equals(arg)) {
                    doWidgets = false;
                } else if ("-all".equals(arg)) {
                    doEverything = true;
                } else if ("-compress".equals(arg)) {
                    doCompress = true;
                } else if ("-nocompress".equals(arg)) {
                    doCompress = false;
                } else if ("-keyvalue".equals(arg)) {
                    doKeyValue = true;
                } else if ("-nokeyvalue".equals(arg)) {
                    doKeyValue = false;
                } else if ("-user".equals(arg)) {
                    // User ID has been processed in run(), ignore the next argument.
                    nextArg();
                    continue;
                } else {
                    // Log error and continue.
                    logAndPrintError("Unknown backup flag %s", arg);
                    continue;
                }
            } else {
                // Not a flag; treat as a package name
                packages.add(arg);
            }
        }

        if (doEverything && packages.size() > 0) {
            logAndPrintError("-all passed for backup along with specific package names");
        }

        if (!doEverything && !saveShared && packages.size() == 0) {
            logAndPrintError("no backup packages supplied and neither -shared nor -all given");
            return;
        }

        ParcelFileDescriptor fd = null;
        try {
            fd = ParcelFileDescriptor.adoptFd(socketFd);
            String[] packArray = new String[packages.size()];
            mBackupManager.adbBackup(userId, fd, saveApks, saveObbs, saveShared, doWidgets, doEverything,
                    allIncludesSystem, doCompress, doKeyValue, packages.toArray(packArray));
        } catch (RemoteException e) {
            logAndPrintError("Unable to invoke backup manager for backup (user %d)", userId);
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {
                    logAndPrintError("IO error closing output for backup: %s", e);
                }
            }
        }
    }

    private void doRestore(int socketFd, @UserIdInt int userId) {
        // No arguments to restore
        ParcelFileDescriptor fd = null;
        try {
            fd = ParcelFileDescriptor.adoptFd(socketFd);
            mBackupManager.adbRestore(userId, fd);
        } catch (RemoteException e) {
            logAndPrintError("Unable to invoke backup manager for restore (user %d)", userId);
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {}
            }
        }
    }

    private @UserIdInt int parseUserId() {
        for (int argNumber = 0; argNumber < mArgs.length - 1; argNumber++) {
            if ("-user".equals(mArgs[argNumber])) {
                return UserHandle.parseUserArg(mArgs[argNumber + 1]);
            }
        }

        return UserHandle.USER_SYSTEM;
    }

    private boolean isBackupActiveForUser(int userId) {
        try {
            return mBackupManager.isBackupServiceActive(userId);
        } catch (RemoteException e) {
            logAndPrintError("Could not access BackupManager: %s", e);
            return false;
        }
    }

    private String nextArg() {
        if (mNextArg >= mArgs.length) {
            return null;
        }
        String arg = mArgs[mNextArg];
        mNextArg++;
        return arg;
    }
}
