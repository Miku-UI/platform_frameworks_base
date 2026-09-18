/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.power;

import android.app.AlarmManager;
import android.app.IAlarmCompleteListener;
import android.app.IAlarmListener;
import android.app.IAlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Flags;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.Pair;
import android.view.Display;

import com.android.server.LocalServices;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

class PowerManagerShellCommand extends ShellCommand {
    private static final int LOW_POWER_MODE_ON = 1;
    private static final int INVALID_WAKELOCK = -1;

    private final Context mContext;
    private final PowerManagerService.BinderService mService;
    private IAlarmManager mAlarmManager;

    class PowerManagerShellCommandAlarmListener extends IAlarmListener.Stub {
        public Runnable mWakeUpCall;

        @Override
        public void doAlarm(IAlarmCompleteListener callback) throws RemoteException {
            mWakeUpCall.run();
        }
    }

    private final PowerManagerShellCommandAlarmListener mAlarmListener;

    // Mapping of Pair<DisplayId, WakelockType> -> Wakelock
    private final ArrayMap<Pair<Integer, Integer>, WakeLock> mWakelocks = new ArrayMap<>();

    PowerManagerShellCommand(Context context, PowerManagerService.BinderService service) {
        mContext = context;
        mService = service;
        mAlarmManager =
                IAlarmManager.Stub.asInterface(ServiceManager.getService(Context.ALARM_SERVICE));
        mAlarmListener = new PowerManagerShellCommandAlarmListener();
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }

        final PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "set-adaptive-power-saver-enabled":
                    return runSetAdaptiveEnabled();
                case "set-mode":
                    return runSetMode();
                case "set-fixed-performance-mode-enabled":
                    return runSetFixedPerformanceModeEnabled();
                case "suppress-ambient-display":
                    return runSuppressAmbientDisplay();
                case "list-ambient-display-suppression-tokens":
                    return runListAmbientDisplaySuppressionTokens();
                case "set-prox":
                    return runSetProx();
                case "set-wakelock":
                    return runSetWakelock(/* proxForLegacy= */ false);
                case "set-face-down-detector":
                    return runSetFaceDownDetector();
                case "sleep":
                    return runSleep();
                case "wakeup":
                    return runWakeUp();
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (RemoteException e) {
            pw.println("Remote exception: " + e);
        }
        return -1;
    }

    private int runSetAdaptiveEnabled() throws RemoteException {
        mService.setAdaptivePowerSaveEnabled(Boolean.parseBoolean(getNextArgRequired()));
        return 0;
    }

    private int runSetMode() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        int mode = -1;
        try {
            mode = Integer.parseInt(getNextArgRequired());
        } catch (RuntimeException ex) {
            pw.println("Error: " + ex.toString());
            return -1;
        }
        mService.setPowerSaveModeEnabled(mode == LOW_POWER_MODE_ON);
        return 0;
    }

    private int runSetFixedPerformanceModeEnabled() throws RemoteException {
        boolean success = mService.setPowerModeChecked(
                PowerManagerInternal.MODE_FIXED_PERFORMANCE,
                Boolean.parseBoolean(getNextArgRequired()));
        if (!success) {
            final PrintWriter ew = getErrPrintWriter();
            ew.println("Failed to set FIXED_PERFORMANCE mode");
            ew.println("This is likely because Power HAL AIDL is not implemented on this device");
        }
        return success ? 0 : -1;
    }

    private int runSuppressAmbientDisplay() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();

        try {
            String token = getNextArgRequired();
            if (Flags.lowLightDreamBehavior()) {
                int suppressFlags = PowerManager.FLAG_AMBIENT_SUPPRESSION_NONE;
                for (String command : getNextArgRequired().toLowerCase().split(",")) {
                    switch (command) {
                        case "true":
                        case "all":
                            suppressFlags |= PowerManager.FLAG_AMBIENT_SUPPRESSION_ALL;
                            break;
                        case "dream":
                            suppressFlags |= PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM;
                            break;
                        case "aod":
                            suppressFlags |= PowerManager.FLAG_AMBIENT_SUPPRESSION_AOD;
                            break;
                        case "false":
                        case "none":
                            suppressFlags |= PowerManager.FLAG_AMBIENT_SUPPRESSION_NONE;
                            break;
                    }
                }
                mService.suppressAmbientDisplayBehavior(token, suppressFlags);
            } else {
                boolean enabled = Boolean.parseBoolean(getNextArgRequired());
                mService.suppressAmbientDisplay(token, enabled);
            }
        } catch (RuntimeException ex) {
            pw.println("Error: " + ex.toString());
            return -1;
        }

        return 0;
    }

    private int runListAmbientDisplaySuppressionTokens() throws RemoteException {
        final PrintWriter pw = getOutPrintWriter();
        List<String> tokens = mService.getAmbientDisplaySuppressionTokens();
        if (tokens.isEmpty()) {
            pw.println("none");
        } else {
            pw.println(String.format("[%s]", String.join(", ", tokens)));
        }

        return 0;
    }

    private int runSetProx() throws RemoteException {
        return runSetWakelock(/* proxForLegacy= */ true);
    }

    private int runSetWakelock(boolean proxForLegacy) throws RemoteException {
        PrintWriter pw = getOutPrintWriter();
        final boolean acquire;
        switch (getNextArgRequired().toLowerCase()) {
            case "list":
                pw.println("Wakelocks:");
                for (int i = 0; i < mWakelocks.size(); i++) {
                    pw.println("Display " + mWakelocks.keyAt(i).first + ", wakelock type: "
                            + PowerManagerInternal.getLockLevelString(mWakelocks.keyAt(i).second)
                            + ": " + mWakelocks.valueAt(i));
                }
                return 0;
            case "acquire":
                acquire = true;
                break;
            case "release":
                acquire = false;
                break;
            default:
                pw.println("Error: Allowed options are 'list' 'acquire' and 'release'.");
                return -1;
        }

        int displayId = Display.INVALID_DISPLAY;
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-d" -> {
                    String idStr = getNextArgRequired();
                    displayId = Integer.parseInt(idStr);
                    if (displayId < 0) {
                        pw.println(
                                "Error: Specified displayId ("
                                        + idStr
                                        + ") must be a non-negative int.");
                        return -1;
                    }
                }
                default -> {
                    pw.println("Error: Unknown option: " + opt);
                    return -1;
                }
            }
        }
        String wakelockTypeString =
                proxForLegacy
                        ? PowerManagerInternal.getLockLevelString(
                                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
                        : getNextArgRequired().toUpperCase(Locale.US);
        WakeLock wakelock = getWakelock(displayId, wakelockTypeString);

        if (acquire) {
            wakelock.acquire();
        } else {
            wakelock.release();
        }
        pw.println(wakelock);
        return 0;
    }

    private WakeLock getWakelock(int displayId, String wakelockString) {
        int wakelockType = stringToWakelockType(wakelockString);
        if (wakelockType == INVALID_WAKELOCK) {
            throw new IllegalArgumentException("Wakelock type invalid: " + wakelockString);
        }
        Pair<Integer, Integer> wakelockIdentifier = new Pair<>(displayId, wakelockType);

        WakeLock wakelockForDisplay = mWakelocks.get(wakelockIdentifier);

        if (wakelockForDisplay == null) {
            PowerManager pm = mContext.getSystemService(PowerManager.class);
            wakelockForDisplay = pm.newWakeLock(wakelockType,
                    "PowerManagerShellCommand[" + displayId + ":"
                            + "0x" + Integer.toHexString(wakelockType) + "]", displayId);
            mWakelocks.put(wakelockIdentifier, wakelockForDisplay);
        }
        return wakelockForDisplay;
    }

    private int stringToWakelockType(String string) {
        return switch (string) {
            case "PARTIAL_WAKE_LOCK" -> PowerManager.PARTIAL_WAKE_LOCK;
            case "SCREEN_DIM_WAKE_LOCK" -> PowerManager.SCREEN_DIM_WAKE_LOCK;
            case "SCREEN_BRIGHT_WAKE_LOCK" -> PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
            case "FULL_WAKE_LOCK" -> PowerManager.FULL_WAKE_LOCK;
            case "DOZE_WAKE_LOCK" -> PowerManager.DOZE_WAKE_LOCK;
            case "DRAW_WAKE_LOCK" -> PowerManager.DRAW_WAKE_LOCK;
            case "SCREEN_TIMEOUT_OVERRIDE_WAKE_LOCK" ->
                    PowerManager.SCREEN_TIMEOUT_OVERRIDE_WAKE_LOCK;
            case "PARTIAL_SLEEP_WAKE_LOCK" -> PowerManager.PARTIAL_SLEEP_WAKE_LOCK;
            case "PROXIMITY_SCREEN_OFF_WAKE_LOCK" -> PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;
            default -> INVALID_WAKELOCK;
        };
    }

    /**
     * To be used for testing - allowing us to disable the usage of face down detector.
     */
    private int runSetFaceDownDetector() {
        try {
            mService.setUseFaceDownDetector(Boolean.parseBoolean(getNextArgRequired()));
        } catch (Exception e) {
            PrintWriter pw = getOutPrintWriter();
            pw.println("Error: " + e);
            return -1;
        }
        return 0;
    }

    private IntArray getFollowingIds() {
        String opt;
        IntArray ids = new IntArray();
        while ((opt = peekNextArg()) != null) {
            try {
                ids.add(Integer.parseInt(opt));
                // Go to next arg, since we only peeked earlier.
                getNextArg();
            } catch (NumberFormatException e) {
                return ids;
            }
        }
        return ids;
    }

    /**
     * Usage:
     * adb shell cmd power sleep
     * adb shell cmd power sleep --group-id 0 1 2 --display-id 3 --disable-wakelocks
     * adb shell cmd power sleep --group-id 0 1 2 --display-id 3
     * adb shell cmd power sleep --disable-wakelocks
     */
    private int runSleep() {
        PowerManagerInternal pmInternal = LocalServices.getService(PowerManagerInternal.class);
        boolean disableWakelocks = false;
        IntArray groupIds = new IntArray();
        IntArray displayIds = new IntArray();

        while (peekNextArg() != null) {
            String arg = getNextArg();
            switch (arg) {
                case "--disable-wakelocks" -> disableWakelocks = true;
                case "--group-id" -> groupIds = getFollowingIds();
                // note: the power groups of the following displays will be awoken
                case "--display-id" -> displayIds = getFollowingIds();
            }
        }

        if (disableWakelocks) {
            if (groupIds.size() > 0) {
                pmInternal.setForceDisableWakelocksByPowerGroup(true, groupIds);
            }
            if (displayIds.size() > 0) {
                pmInternal.setForceDisableWakelocksByDisplay(true, displayIds);
            }
            // If no specific groups/displays were targeted, apply globally.
            if (groupIds.size() == 0 && displayIds.size() == 0) {
                pmInternal.setForceDisableWakelocks(true);
            }
        }

        if (groupIds.size() > 0) {
            pmInternal.goToSleepPerGroup(groupIds,
                    SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                    PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
        }

        if (displayIds.size() > 0) {
            try {
                for (int i = 0; i < displayIds.size(); i++) {
                    mService.goToSleepWithDisplayId(
                            displayIds.get(i),
                            SystemClock.uptimeMillis(),
                            PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
                }
            } catch (Exception e) {
                final PrintWriter pw = getOutPrintWriter();
                pw.println("Error: " + e);
                return -1;
            }
        }

        if (groupIds.size() > 0 || displayIds.size() > 0) {
            return 0;
        }

        // If neither groups nor display ids have been specified, send only default & adjacent
        // groups to sleep.
        try {
            mService.goToSleep(
                    SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                    PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
        } catch (Exception e) {
            final PrintWriter pw = getOutPrintWriter();
            pw.println("Error: " + e);
            return -1;
        }

        return 0;
    }

    private int runWakeUp() {
        final PrintWriter pw = getOutPrintWriter();
        boolean restoreWakelocks = false;
        long delayTime = 0;
        IntArray groupIds = new IntArray();
        IntArray displayIds = new IntArray();

        // Delay should be specified as a numberin millis as the first argument only.
        String firstArg = peekNextArg();
        if (firstArg != null && firstArg.matches("\\d+")) {
            firstArg = getNextArg();
            try {
                delayTime = Long.parseLong(firstArg);
            } catch (NumberFormatException e) {
                pw.println("Error: Can't parse arg " + firstArg + " as a long: " + e);
                return -1;
            }
        }

        while (peekNextArg() != null) {
            String arg = getNextArg();
            switch (arg) {
                case "--restore-wakelocks" ->
                    restoreWakelocks = true;
                case "--group-id" -> groupIds = getFollowingIds();
                // note: the power groups of the following displays will be awoken
                case "--display-id" -> displayIds = getFollowingIds();
            }
        }

        if (delayTime < 0) {
            pw.println("Error: Can't set a negative delay: " + delayTime);
            return -1;
        }

        Runnable wakeUpRunnable = createWakeUpRunnable(groupIds, displayIds, restoreWakelocks);

        if (delayTime == 0) {
            wakeUpRunnable.run();
        } else {
            long wakeUpTime = System.currentTimeMillis() + delayTime;
            if (mAlarmManager == null) {
                // PowerManagerShellCommand may be initialized before AlarmManagerService
                // is brought up. Make sure mAlarmManager exists.
                mAlarmManager = IAlarmManager.Stub.asInterface(
                        ServiceManager.getService(Context.ALARM_SERVICE));
            }
            final long ident = Binder.clearCallingIdentity();
            try {
                pw.println("Schedule an alarm to wakeup in " + delayTime
                        + " ms, on behalf of Android");
                mAlarmListener.mWakeUpCall = wakeUpRunnable;
                mAlarmManager.set("android",
                        AlarmManager.RTC_WAKEUP, wakeUpTime,
                        0, 0, AlarmManager.FLAG_PRIORITIZE,
                        null, mAlarmListener, "PowerManagerShellCommand", null, null);
            } catch (Exception e) {
                pw.println("Error: " + e);
                return -1;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
        return 0;
    }

    private Runnable createWakeUpRunnable(IntArray groupIds, IntArray displayIds,
            boolean restoreWakelocks) {
        final PrintWriter pw = getOutPrintWriter();

        return () -> {
            PowerManagerInternal pmInternal =
                    LocalServices.getService(PowerManagerInternal.class);
            if (groupIds.size() > 0 || displayIds.size() > 0) {
                if (groupIds.size() > 0) {
                    try {
                        pmInternal.wakeupPerGroup(groupIds,
                                SystemClock.uptimeMillis(),
                                PowerManager.WAKE_REASON_APPLICATION,
                                "PowerManagerShellCommand",
                                mContext.getOpPackageName(),
                                Process.SHELL_UID
                        );
                    } catch (Exception e) {
                        pw.println("Error: " + e);
                    }
                }

                if (displayIds.size() > 0) {
                    try {
                        for (int i = 0; i < displayIds.size(); i++) {
                            mService.wakeUpWithDisplayId(
                                    SystemClock.uptimeMillis(),
                                    PowerManager.WAKE_REASON_APPLICATION,
                                    "PowerManagerShellCommand",
                                    mContext.getOpPackageName(),
                                    displayIds.get(i)
                            );
                        }
                    } catch (Exception e) {
                        pw.println("Error: " + e);
                    }
                }
            } else {
                // If neither display groups nor display ids have been specified, then send a wakeup
                // call to default (& adjacent groups) only.
                try {
                    mService.wakeUp(
                            SystemClock.uptimeMillis(),
                            PowerManager.WAKE_REASON_APPLICATION,
                            "PowerManagerShellCommand",
                            mContext.getOpPackageName());
                } catch (Exception e) {
                    pw.println("Error: " + e);
                }
            }

            if (restoreWakelocks) {
                if (groupIds.size() > 0) {
                    pmInternal.setForceDisableWakelocksByPowerGroup(false, groupIds);
                }
                if (displayIds.size() > 0) {
                    pmInternal.setForceDisableWakelocksByDisplay(false, displayIds);
                }
                // If no specific groups/displays were targeted, apply globally.
                if (groupIds.size() == 0 && displayIds.size() == 0) {
                    pmInternal.setForceDisableWakelocks(false);
                }
            }
        };
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("Power manager (power) commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println("");
        pw.println("  set-adaptive-power-saver-enabled [true|false]");
        pw.println("    enables or disables adaptive power saver.");
        pw.println("  set-mode MODE");
        pw.println("    sets the power mode of the device to MODE.");
        pw.println("    1 turns low power mode on and 0 turns low power mode off.");
        pw.println("  set-fixed-performance-mode-enabled [true|false]");
        pw.println("    enables or disables fixed performance mode");
        pw.println("    note: this will affect system performance and should only be used");
        pw.println("          during development");
        if (Flags.lowLightDreamBehavior()) {
            pw.println("  suppress-ambient-display <token> [none|all|dream|aod|true|false]");
        } else {
            pw.println("  suppress-ambient-display <token> [true|false]");
        }
        pw.println("    suppresses the current ambient display configuration and disables");
        pw.println("    ambient display");
        pw.println("  list-ambient-display-suppression-tokens");
        pw.println("    prints the tokens used to suppress ambient display");
        pw.println("  set-prox [list|acquire|release] (-d <display_id>)");
        pw.println("    Acquires the proximity sensor wakelock. Wakelock is associated with");
        pw.println("    a specific display if specified. 'list' lists wakelocks previously");
        pw.println("    created by set-prox including their held status.");
        pw.println("  set-wakelock [list|acquire|release] (-d <display_id>) [wakelock type]");
        pw.println("    Acquires the specified wakelock. Wakelock is associated with");
        pw.println("    a specific display if specified. 'list' lists wakelocks previously");
        pw.println("    created by set-wakelock including their held status.");
        pw.println("    Available wakelocks are described in PowerManager.*_WAKE_LOCK.");
        pw.println("  set-face-down-detector [true|false]");
        pw.println("    sets whether we use face down detector timeouts or not");
        pw.println("  sleep (--disable-wakelocks) (--group-id <group ids>) ");
        pw.println("    requests to sleep the device");
        pw.println("      --disable-wakelocks: Force disable wakelocks before going to sleep.");
        pw.println(
                "        It will only act upon the wakelocks associated with the listed power "
                        + "groups or displays.");
        pw.println(
                "      --group-id <group ids>: Only sleep certain power groups, list with spaces.");
        pw.println(
                "      --display-id <display ids>: Only sleep power groups of listed displays, "
                        + "list with spaces.");
        pw.println(
                "      If no group ids nor display ids are specified, then default & adjacent "
                        + "groups will sleep.");
        pw.println(
                "      Usage: adb shell cmd power sleep --group-id 0 1 2 --display-id 3 "
                        + "--disable-wakelocks");
        pw.println("  wakeup (<delay>) (--restore-wakelocks)");
        pw.println("    requests to wake up the device. If a delay of milliseconds is specified,");
        pw.println("    alarm manager will schedule a wake up after the delay.");
        pw.println(
                "      --group-id <group ids>: Only wake certain power groups, list with spaces.");
        pw.println(
                "      --display-id <display ids>: Only wake power groups of listed displays, "
                        + "list with spaces.");
        pw.println(
                "      If no group ids nor display ids are specified, then default & adjacent "
                        + "groups will sleep.");
        pw.println("      --restore-wakelocks: Restore force-disabled wakelocks after wakeup.");
        pw.println("        It will not restore wakelocks that are generically disabled.");

        pw.println();
        Intent.printIntentArgsHelp(pw, "");
    }
}
