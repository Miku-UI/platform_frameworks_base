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

package com.android.server.inputmethod;

import static android.content.Context.DEVICE_ID_DEFAULT;

import android.annotation.BinderThread;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.inputmethod.DirectBootAwareness;
import com.android.internal.inputmethod.ImeTracing;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * A controller to handle shell commands for
 * {@link InputMethodManagerService#onShellCommand(FileDescriptor, FileDescriptor, FileDescriptor, String[], ShellCallback, ResultReceiver, Binder)}.
 */
final class ImeShellCommandController extends ShellCommand {
    private static final String TAG = ImeShellCommandController.class.getSimpleName();

    @NonNull
    private final InputMethodManagerService mService;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ShellCommandResult.SUCCESS, ShellCommandResult.FAILURE})
    @interface ShellCommandResult {
        int SUCCESS = 0;
        int FAILURE = -1;
    }

    ImeShellCommandController(@NonNull InputMethodManagerService service) {
        mService = service;
    }

    @BinderThread
    @ShellCommandResult
    @Override
    public int onCommand(@Nullable String cmd) {
        final long identity = Binder.clearCallingIdentity();
        try {
            return onCommandWithSystemIdentity(cmd);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @BinderThread
    @ShellCommandResult
    private int onCommandWithSystemIdentity(@Nullable String cmd) {
        switch (TextUtils.emptyIfNull(cmd)) {
            case "tracing":
                return tracing(this);
            case "ime": {  // For "adb shell ime <command>".
                final String imeCommand = TextUtils.emptyIfNull(getNextArg());
                switch (imeCommand) {
                    case "":
                    case "-h":
                    case "help":
                        return help();
                    case "list":
                        return list();
                    case "enable":
                        return enableOrDisable(true /* enable */);
                    case "disable":
                        return enableOrDisable(false /* enable */);
                    case "set":
                        return set();
                    case "reset":
                        return reset();
                    case "tracing":  // TODO(b/180765389): Unsupport "adb shell ime tracing"
                        return tracing(this);
                    default:
                        getOutPrintWriter().println("Unknown command: " + imeCommand);
                        return ShellCommandResult.FAILURE;
                }
            }
            default:
                return handleDefaultCommands(cmd);
        }
    }

    @BinderThread
    @Override
    public void onHelp() {
        try (PrintWriter pw = getOutPrintWriter()) {
            pw.println("InputMethodManagerService commands:");
            pw.println("  help");
            pw.println("    Prints this help text.");
            pw.println("  dump [options]");
            pw.println("    Synonym of dumpsys.");
            pw.println("  ime <command> [options]");
            pw.println("    Manipulate IMEs.  Run \"ime help\" for details.");
            pw.println("  tracing <command>");
            pw.println("    start: Start tracing.");
            pw.println("    stop : Stop tracing.");
            pw.println("    help : Show help.");
        }
    }

    @BinderThread
    @ShellCommandResult
    private int help() {
        try (var pw = new IndentingPrintWriter(getOutPrintWriter(), "  ", 100)) {
            pw.println("ime <command>:");
            pw.increaseIndent();

            pw.println("list [-a] [-s]");
            pw.increaseIndent();
            pw.println("prints all enabled input methods.");
            pw.increaseIndent();
            pw.println("-a: see all input methods");
            pw.println("-s: only a single summary line of each");
            pw.decreaseIndent();
            pw.decreaseIndent();

            pw.println("enable [--user <USER_ID>] <ID>");
            pw.increaseIndent();
            pw.println("allows the given input method ID to be used.");
            pw.increaseIndent();
            pw.print("--user <USER_ID>: Specify which user to enable.");
            pw.println(" Assumes the current user if not specified.");
            pw.decreaseIndent();
            pw.decreaseIndent();

            pw.println("disable [--user <USER_ID>] <ID>");
            pw.increaseIndent();
            pw.println("disallows the given input method ID to be used.");
            pw.increaseIndent();
            pw.print("--user <USER_ID>: Specify which user to disable.");
            pw.println(" Assumes the current user if not specified.");
            pw.decreaseIndent();
            pw.decreaseIndent();

            pw.println("set [--user <USER_ID>] <ID>");
            pw.increaseIndent();
            pw.println("switches to the given input method ID.");
            pw.increaseIndent();
            pw.print("--user <USER_ID>: Specify which user to enable.");
            pw.println(" Assumes the current user if not specified.");
            pw.decreaseIndent();
            pw.decreaseIndent();

            pw.println("reset [--user <USER_ID>]");
            pw.increaseIndent();
            pw.println("reset currently selected/enabled IMEs to the default ones as if "
                    + "the device is initially booted with the current locale.");
            pw.increaseIndent();
            pw.print("--user <USER_ID>: Specify which user to reset.");
            pw.println(" Assumes the current user if not specified.");
            pw.decreaseIndent();

            pw.decreaseIndent();

            pw.decreaseIndent();
        }
        return ShellCommandResult.SUCCESS;
    }

    @BinderThread
    @ShellCommandResult
    private int list() {
        boolean all = false;
        boolean brief = false;
        int userIdToBeResolved = UserHandle.USER_CURRENT;
        while (true) {
            final String nextOption = getNextOption();
            if (nextOption == null) {
                break;
            }
            switch (nextOption) {
                case "-a":
                    all = true;
                    break;
                case "-s":
                    brief = true;
                    break;
                case "-u":
                case "--user":
                    userIdToBeResolved = UserHandle.parseUserArg(getNextArgRequired());
                    break;
            }
        }
        final int[] userIds;
        synchronized (ImfLock.class) {
            userIds = InputMethodUtils.resolveUserId(userIdToBeResolved, mService.mCurrentImeUserId,
                    getErrPrintWriter());
        }
        try (PrintWriter pr = getOutPrintWriter()) {
            for (int userId : userIds) {
                if (userIds.length > 1) {
                    pr.print("User #");
                    pr.print(userId);
                    pr.println(":");
                }
                final List<InputMethodInfo> methods = all
                        ? mService.getInputMethodListInternal(
                        userId, DirectBootAwareness.AUTO, Process.SHELL_UID)
                        : mService.getEnabledInputMethodListInternal(userId, Process.SHELL_UID);
                for (InputMethodInfo info : methods) {
                    if (brief) {
                        pr.println(info.getId());
                    } else {
                        pr.print(info.getId());
                        pr.println(":");
                        info.dump(pr::println, "  ");
                    }
                }
            }
        }
        return ShellCommandResult.SUCCESS;
    }

    @BinderThread
    @ShellCommandResult
    private int enableOrDisable(boolean enable) {
        final int userIdToBeResolved = handleOptionsThatOnlyHaveUserOption(this);
        final String imeId = getNextArgRequired();
        boolean hasFailed = false;
        try (PrintWriter out = getOutPrintWriter();
                PrintWriter error = getErrPrintWriter()) {
            synchronized (ImfLock.class) {
                final int[] userIds = InputMethodUtils.resolveUserId(userIdToBeResolved,
                        mService.mCurrentImeUserId, getErrPrintWriter());
                for (int userId : userIds) {
                    if (!mService.userHasDebugPriv(userId, this)) {
                        continue;
                    }
                    hasFailed |= !enableDisableInternal(
                            userId, imeId, enable, out, error);
                }
            }
        }
        return result(hasFailed);
    }

    @BinderThread
    private boolean enableDisableInternal(
            @UserIdInt int userId, String imeId, boolean enabled, PrintWriter out,
            PrintWriter error) {
        final InputMethodSettings settings = InputMethodSettingsRepository.get(userId);
        if (enabled && !settings.getMethodMap().containsKey(imeId)) {
            error.print("Unknown input method ");
            error.print(imeId);
            error.println(" cannot be enabled for user #" + userId);
            Slog.e(TAG, "\"ime enable " + imeId + "\" for user #" + userId
                    + " failed due to its unrecognized IME ID.");
            return false;
        }

        final var enabledInputMethodsController = mService.getEnabledInputMethodsControllerLocked();
        /** TODO: Currently ImeShellCommandController#enableOrDisable already handles user ID
         *  resolution, and we call into enabledInputMethodsController which handles it again.
         *  We should remove this duplication in the future
         */

        final boolean previouslyEnabled = enabled
                ? enabledInputMethodsController.enableInputMethodForTesting(imeId, userId)
                : enabledInputMethodsController.disableInputMethodForTesting(imeId, userId);
        out.print("Input method ");
        out.print(imeId);
        out.print(": ");
        out.print((enabled == previouslyEnabled) ? "already " : "now ");
        out.print(enabled ? "enabled" : "disabled");
        out.print(" for user #");
        out.println(userId);
        return true;
    }

    @BinderThread
    @ShellCommandResult
    private int set() {
        final int userIdToBeResolved = handleOptionsThatOnlyHaveUserOption(this);
        final String imeId = getNextArgRequired();
        boolean hasFailed = false;
        try (PrintWriter out = getOutPrintWriter();
                PrintWriter error = getErrPrintWriter()) {
            synchronized (ImfLock.class) {
                final int[] userIds = InputMethodUtils.resolveUserId(userIdToBeResolved,
                        mService.mCurrentImeUserId, getErrPrintWriter());
                for (int userId : userIds) {
                    if (!mService.userHasDebugPriv(userId, this)) {
                        continue;
                    }
                    hasFailed |= setInternal(userId, imeId, out, error);
                }
            }
        }
        return result(hasFailed);
    }

    @BinderThread
    private boolean setInternal(
            @UserIdInt int userId, String imeId, PrintWriter out, PrintWriter error) {
        boolean failedToSelectUnknownIme = mService.getEnabledInputMethodsControllerLocked()
                .setInputMethodForTesting(imeId, userId);
        if(failedToSelectUnknownIme){
            error.print("Unknown input method ");
            error.print(imeId);
            error.print(" cannot be selected for user #");
            error.println(userId);
            Slog.e(TAG, "\"ime set " + imeId + "\" for user #" + userId
                    + " failed due to its unrecognized IME ID.");
        } else {
            out.print("Input method ");
            out.print(imeId);
            out.print(" selected for user #");
            out.println(userId);
        }
        return failedToSelectUnknownIme;
    }

    @BinderThread
    @ShellCommandResult
    private int reset() {
        final int userIdToBeResolved = handleOptionsThatOnlyHaveUserOption(this);
        synchronized (ImfLock.class) {
            try (PrintWriter out = getOutPrintWriter()) {
                final int[] userIds = InputMethodUtils.resolveUserId(userIdToBeResolved,
                        mService.mCurrentImeUserId, getErrPrintWriter());
                for (int userId : userIds) {
                    if (!mService.userHasDebugPriv(userId, this)) {
                        continue;
                    }
                    resetInternal(userId, out);
                }
            }
        }
        return ShellCommandResult.SUCCESS;
    }

    @BinderThread
    private void resetInternal(@UserIdInt int userId, PrintWriter out) {
        final UserInfo userInfo = mService.mUserManagerInternal.getUserInfo(userId);
        if (userInfo != null && UserManager.USER_TYPE_SYSTEM_HEADLESS.equals(userInfo.userType)) {
            return;
        }
        mService.getEnabledInputMethodsControllerLocked().resetInputMethodsForTesting(userId);
        final InputMethodSettings settings = InputMethodSettingsRepository.get(userId);
        final String nextIme = settings.getSelectedInputMethod();
        final List<InputMethodInfo> nextEnabledImes = settings.getEnabledInputMethodList();
        out.println("Reset current and enabled IMEs for user #" + userId);
        out.println("  Selected: " + nextIme);
        nextEnabledImes.forEach(ime -> out.println("   Enabled: " + ime.getId()));
    }

    /**
     * Handles {@code adb shell cmd input_method tracing start/stop/save-for-bugreport}.
     *
     * @param shellCommand {@link ShellCommand} object that is handling this command
     * @return Exit code of the command.
     */
    @BinderThread
    @ShellCommandResult
    private int tracing(@NonNull ShellCommand shellCommand) {
        final String cmd = shellCommand.getNextArgRequired();
        try (PrintWriter pw = shellCommand.getOutPrintWriter()) {
            switch (cmd) {
                case "start":
                    ImeTracing.getInstance().startTrace(pw);
                    break;  // proceed to the next step to update the IME client processes.
                case "stop":
                    ImeTracing.getInstance().stopTrace(pw);
                    break;  // proceed to the next step to update the IME client processes.
                case "save-for-bugreport":
                    ImeTracing.getInstance().saveForBugreport(pw);
                    // no need to update the IME client processes.
                    return ShellCommandResult.SUCCESS;
                default:
                    pw.println("Unknown command: " + cmd);
                    pw.println("Input method trace options:");
                    pw.println("  start: Start tracing");
                    pw.println("  stop: Stop tracing");
                    // no need to update the IME client processes.
                    return ShellCommandResult.FAILURE;
            }
        }
        mService.handleShellCommandTraceInputMethod();
        return ShellCommandResult.SUCCESS;
    }

    @ShellCommandResult
    private int result(boolean hasFailed) {
        return hasFailed ? ShellCommandResult.FAILURE : ShellCommandResult.SUCCESS;
    }

    /**
     * A special helper method for commands that only have {@code -u} and {@code --user} options.
     *
     * <p>You cannot use this helper method if the command has other options.</p>
     *
     * <p>CAVEAT: This method must be called only once before any other
     * {@link ShellCommand#getNextArg()} and {@link ShellCommand#getNextArgRequired()} for the
     * main arguments.</p>
     *
     * @param shellCommand {@link ShellCommand} from which options should be obtained
     * @return user ID to be resolved. {@link UserHandle#CURRENT} if not specified
     */
    @UserIdInt
    private static int handleOptionsThatOnlyHaveUserOption(ShellCommand shellCommand) {
        while (true) {
            final String nextOption = shellCommand.getNextOption();
            if (nextOption == null) {
                break;
            }
            switch (nextOption) {
                case "-u":
                case "--user":
                    return UserHandle.parseUserArg(shellCommand.getNextArgRequired());
            }
        }
        return UserHandle.USER_CURRENT;
    }
}
