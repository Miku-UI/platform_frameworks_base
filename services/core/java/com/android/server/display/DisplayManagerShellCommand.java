/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.display;

import static android.hardware.display.DisplayManager.brightnessUnitToString;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Display.TYPE_INTERNAL;
import static android.view.Display.TYPE_OVERLAY;
import static android.view.Display.TYPE_UNKNOWN;
import static android.view.Display.TYPE_VIRTUAL;
import static android.view.Display.TYPE_WIFI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.os.ShellCommand;
import android.util.Slog;
import android.view.Display;

import com.android.server.display.feature.DisplayManagerFlags;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class DisplayManagerShellCommand extends ShellCommand {
    private static final String TAG = "DisplayManagerShellCommand";
    private static final String NOTIFICATION_TYPES = "on-hotplug-error, on-link-training-failure, "
            + "on-cable-dp-incapable";
    private static final int INVALID_BRIGHTNESS_UNIT = -1;

    private final DisplayManagerService mService;
    private final DisplayManagerFlags mFlags;

    DisplayManagerShellCommand(DisplayManagerService service, DisplayManagerFlags flags) {
        mService = service;
        mFlags = flags;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        final PrintWriter pw = getOutPrintWriter();
        switch(cmd) {
            case "show-notification":
                return showNotification();
            case "cancel-notifications":
                return cancelNotifications();
            case "get-brightness":
                return getBrightness();
            case "set-brightness":
                return setBrightness();
            case "reset-brightness-configuration":
                return resetBrightnessConfiguration();
            case "ab-logging-enable":
                return setAutoBrightnessLoggingEnabled(true);
            case "ab-logging-disable":
                return setAutoBrightnessLoggingEnabled(false);
            case "dwb-logging-enable":
                return setDisplayWhiteBalanceLoggingEnabled(true);
            case "dwb-logging-disable":
                return setDisplayWhiteBalanceLoggingEnabled(false);
            case "dmd-logging-enable":
                return setDisplayModeDirectorLoggingEnabled(true);
            case "dmd-logging-disable":
                return setDisplayModeDirectorLoggingEnabled(false);
            case "dwb-set-cct":
                return setAmbientColorTemperatureOverride();
            case "set-user-preferred-display-mode":
                return setUserPreferredDisplayMode();
            case "reset-user-preferred-display-mode":
                return resetUserPreferredDisplayMode();
            case "clear-user-preferred-display-mode":
                return clearUserPreferredDisplayMode();
            case "get-user-preferred-display-mode":
                return getUserPreferredDisplayMode();
            case "get-active-display-mode-at-start":
                return getActiveDisplayModeAtStart();
            case "set-match-content-frame-rate-pref":
                return setMatchContentFrameRateUserPreference();
            case "get-match-content-frame-rate-pref":
                return getMatchContentFrameRateUserPreference();
            case "set-user-disabled-hdr-types":
                return setUserDisabledHdrTypes();
            case "get-user-disabled-hdr-types":
                return getUserDisabledHdrTypes();
            case "get-displays":
                return getDisplays();
            case "dock":
                return setDockedAndIdle();
            case "undock":
                return unsetDockedAndIdle();
            case "enable-display":
                return setDisplayEnabled(true);
            case "disable-display":
                return setDisplayEnabled(false);
            case "power-reset":
                return requestDisplayPower(Display.STATE_UNKNOWN);
            case "power-off":
                return requestDisplayPower(Display.STATE_OFF);
            case "override-max-importance-rr-callbacks":
                return overrideMaxImportanceForRRCallbacks();
            case "get-hdr-conversion-mode":
                return getHdrConversionMode();
            case "set-hdr-conversion-mode":
                return setHdrConversionMode();
            case "get-reported-hdr-types":
                return getReportedHdrTypes();
            case "get-supported-modes":
                return getSupportedModes();
            case "get-mode-supported-hdr-types":
                return getModeSupportedHdrTypes();
            case "get-supported-hdr-output-types":
                return getSupportedHdrOutputTypes();
            case "get-active-mode":
                return getActiveMode();
            default:
                return handleDefaultCommands(cmd);
        }
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("Display manager commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println();
        pw.println("  show-notification NOTIFICATION_TYPE");
        pw.println("    Show notification for one of the following types: " + NOTIFICATION_TYPES);
        pw.println("  cancel-notifications");
        pw.println("    Cancel notifications.");
        pw.println("  get-brightness [--id DISPLAY_ID] [--unit UNIT]");
        pw.println("    Gets the current brightness. Can specify the display ID, otherwise the "
                + "default display is used. If no unit is specified, the returned value is in the "
                + "float scale [0, 1]. The unit can be one of the following:");
        pw.println("      " + brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE)
                + " - return the value displayed on the brightness slider");
        pw.println("      " + brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_NITS)
                + " - return the brightness in nits (adjustments such as Reduce Bright Colors "
                + "might be included)");
        pw.println("  set-brightness BRIGHTNESS [--id DISPLAY_ID] [--unit UNIT]");
        pw.println("    Sets the current brightness. Can specify the display ID, otherwise the "
                + "default display is used. If no unit is specified, the value should be in the "
                + "float scale [0, 1]. The unit can be one of the following:");
        pw.println("      " + brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE)
                + " - set the value displayed on the brightness slider");
        pw.println("      " + brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_NITS)
                + " - set the brightness in nits (adjustments such as Reduce Bright Colors might "
                + "be included)");
        pw.println("  reset-brightness-configuration");
        pw.println("    Reset the brightness to its default configuration.");
        pw.println("  ab-logging-enable");
        pw.println("    Enable auto-brightness logging.");
        pw.println("  ab-logging-disable");
        pw.println("    Disable auto-brightness logging.");
        pw.println("  dwb-logging-enable");
        pw.println("    Enable display white-balance logging.");
        pw.println("  dwb-logging-disable");
        pw.println("    Disable display white-balance logging.");
        pw.println("  dmd-logging-enable");
        pw.println("    Enable display mode director logging.");
        pw.println("  dmd-logging-disable");
        pw.println("    Disable display mode director logging.");
        pw.println("  dwb-set-cct CCT");
        pw.println("    Sets the ambient color temperature override to CCT (use -1 to disable).");
        pw.println("  set-user-preferred-display-mode WIDTH HEIGHT REFRESH-RATE "
                + "DISPLAY_ID (optional) STORE_MODE (optional)");
        pw.println("    Sets the user preferred display mode which has fields WIDTH, HEIGHT and "
                + "REFRESH-RATE. If DISPLAY_ID is passed, the mode change is applied to display"
                + "with id = DISPLAY_ID, if DISPLAY_ID == -1 or missing change is applied globally."
                + " If STORE_MODE is true or missing, mode will be persisted, otherwise it will be"
                + "applied without persisting. Should be used only together with DISPLAY_ID.");
        pw.println("  reset-user-preferred-display-mode DISPLAY_ID (optional)");
        pw.println("    Resets the user preferred display mode with stored mode. "
                + "If DISPLAY_ID is passed, the persisted mode is applied to display with "
                + "id = DISPLAY_ID, if DISPLAY_ID == -1 or missing persisted mode "
                + "is applied globally.");
        pw.println("    Sets the user preferred display mode which has fields WIDTH, HEIGHT and "
                + "REFRESH-RATE. If DISPLAY_ID is passed, the mode change is applied to display"
                + "with id = DISPLAY_ID, else mode change is applied globally.");
        pw.println("  clear-user-preferred-display-mode DISPLAY_ID (optional)");
        pw.println("    Clears the user preferred display mode. If DISPLAY_ID is passed, the mode"
                + " is cleared for  display with id = DISPLAY_ID, else mode is cleared globally.");
        pw.println("  get-user-preferred-display-mode DISPLAY_ID (optional)");
        pw.println("    Returns the user preferred display mode or null if no mode is set by user."
                + "If DISPLAY_ID is passed, the mode for display with id = DISPLAY_ID is "
                + "returned, else global display mode is returned.");
        pw.println("  get-active-display-mode-at-start DISPLAY_ID");
        pw.println("    Returns the display mode which was found at boot time of display with "
                + "id = DISPLAY_ID");
        pw.println("  set-match-content-frame-rate-pref PREFERENCE");
        pw.println("    Sets the match content frame rate preference as PREFERENCE ");
        pw.println("  get-match-content-frame-rate-pref");
        pw.println("    Returns the match content frame rate preference");
        pw.println("  set-user-disabled-hdr-types TYPES...");
        pw.println("    Sets the user disabled HDR types as TYPES");
        pw.println("  get-user-disabled-hdr-types");
        pw.println("    Returns the user disabled HDR types");
        pw.println("  get-displays [-c|--category CATEGORY] [-i|--ids-only] [-t|--type TYPE]");
        pw.println("    [CATEGORY]");
        pw.println("    Returns the current displays. Can specify string category among");
        pw.println("    DisplayManager.DISPLAY_CATEGORY_*; must use the actual string value.");
        pw.println("    Can choose to print only the ids of the displays. " + "Can filter by");
        pw.println("    display types. For example, '--type external'");
        pw.println("  dock");
        pw.println("    Sets brightness to docked + idle screen brightness mode");
        pw.println("  undock");
        pw.println("    Sets brightness to active (normal) screen brightness mode");
        pw.println("  enable-display DISPLAY_ID");
        pw.println("    Enable the DISPLAY_ID. Only possible if this is a connected display.");
        pw.println("  disable-display DISPLAY_ID");
        pw.println("    Disable the DISPLAY_ID. Only possible if this is a connected display.");
        pw.println("  power-reset DISPLAY_ID");
        pw.println("    Turn the DISPLAY_ID power to a state the display supposed to have.");
        pw.println("  power-off DISPLAY_ID");
        pw.println("    Turn the display DISPLAY_ID power off.");
        pw.println("  get-hdr-conversion-mode");
        pw.println("    Gets the current HDR conversion mode and preferred output type.");
        pw.println("  get-reported-hdr-types DISPLAY_ID");
        pw.println("    Gets the HDR types supported by the current mode of the specified "
                + "display.");
        pw.println("    Returns an array of integers representing HdrCapabilities.HdrType "
                + "constants");
        pw.println("    (e.g., 1=DOLBY_VISION, 2=HDR10, 3=HLG, 4=HDR10_PLUS).");
        pw.println("  get-supported-modes DISPLAY_ID");
        pw.println("    Lists all supported modes (resolution and refresh rate) for the");
        pw.println("    specified display.");
        pw.println("  set-hdr-conversion-mode MODE [PREFERRED_TYPE]");
        pw.println("    Sets the HDR conversion mode.");
        pw.println("    MODE: Integer representing the HdrConversionMode.ConversionMode");
        pw.println("      0 = UNSUPPORTED");
        pw.println("      1 = PASSTHROUGH");
        pw.println("      2 = SYSTEM");
        pw.println("      3 = FORCE");
        pw.println("    PREFERRED_TYPE: Integer representing the HdrCapabilities.HdrType");
        pw.println("      (e.g., 0=INVALID, 1=DOLBY_VISION, 2=HDR10, 3=HLG, 4=HDR10_PLUS).");
        pw.println("      This argument is ONLY required and used when MODE is FORCE (3).");
        pw.println("      It MUST be a valid HDR type (not 0) in that case.");
        pw.println("      For other modes (0, 1, 2), this argument should NOT be provided.");
        pw.println("  get-hdr-conversion-mode");
        pw.println("    Gets the current HDR conversion mode and preferred output type.");
        pw.println("  get-mode-supported-hdr-types DISPLAY_ID WIDTH HEIGHT REFRESH_RATE");
        pw.println("    Gets the HDR types supported by a specific mode (defined by WIDTH, "
                + "HEIGHT,");
        pw.println("    and REFRESH_RATE) of the specified DISPLAY_ID.");
        pw.println("    Returns an array of integers representing HdrCapabilities.HdrType "
                + "constants");
        pw.println("    (e.g., 1=DOLBY_VISION, 2=HDR10, 3=HLG, 4=HDR10_PLUS).");
        pw.println("  get-supported-hdr-output-types");
        pw.println("    Gets the HDR output types globally supported by the device "
                + "hardware/software.");
        pw.println("    Returns an array of integers representing HdrCapabilities.HdrType "
                + "constants");
        pw.println("    (e.g., 1=DOLBY_VISION, 2=HDR10, 3=HLG, 4=HDR10_PLUS).");
        pw.println("    An empty array [] may indicate no types are supported OR the feature "
                + "is disabled.");
        pw.println("  get-active-mode DISPLAY_ID");
        pw.println("    Gets the current active display mode (resolution, refresh rate)");
        pw.println("    for the specified DISPLAY_ID.");
        pw.println();
        Intent.printIntentArgsHelp(pw, "");
    }

    private int getDisplays() {
        String opt = "", requestedType, category = null;
        PrintWriter out = getOutPrintWriter();

        List<Integer> displayTypeList = new ArrayList<>();
        boolean showIdsOnly = false, filterByType = false;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-i":
                case "--ids-only":
                    showIdsOnly = true;
                    break;
                case "-t":
                case "--type":
                    requestedType = getNextArgRequired();
                    int displayType = getType(requestedType, out);
                    if (displayType == -1) {
                        return 1;
                    }
                    displayTypeList.add(displayType);
                    filterByType = true;
                    break;
                case "-c":
                case "--category":
                    if (category != null) {
                        out.println("Error: the category has been specified more than one time. "
                                + "Please select only one category.");
                        return 1;
                    }
                    category = getNextArgRequired();
                    break;
                case "":
                    break;
                default:
                    out.println("Error: unknown option '" + opt + "'");
                    return 1;
            }
        }

        String lastCategoryArgument = getNextArg();
        if (lastCategoryArgument != null) {
            if (category != null) {
                out.println("Error: the category has been specified both with the -c option and "
                        + "the positional argument. Please select only one category.");
                return 1;
            }
            category = lastCategoryArgument;
        }

        DisplayManager dm = mService.getContext().getSystemService(DisplayManager.class);
        Display[] displays = dm.getDisplays(category);

        if (filterByType) {
            displays = Arrays.stream(displays).filter(d -> displayTypeList.contains(d.getType()))
                    .toArray(Display[]::new);
        }

        if (!showIdsOnly) {
            out.println("Displays:");
        }
        for (int i = 0; i < displays.length; i++) {
            out.println((showIdsOnly ? displays[i].getDisplayId() : displays[i]));
        }
        return 0;
    }

    private int getType(String type, PrintWriter out) {
        type = type.toUpperCase(Locale.ENGLISH);
        switch (type) {
            case "UNKNOWN":
                return TYPE_UNKNOWN;
            case "INTERNAL":
                return TYPE_INTERNAL;
            case "EXTERNAL":
                return TYPE_EXTERNAL;
            case "WIFI":
                return TYPE_WIFI;
            case "OVERLAY":
                return TYPE_OVERLAY;
            case "VIRTUAL":
                return TYPE_VIRTUAL;
            default:
                out.println("Error: argument for display type should be "
                        + "one of 'UNKNOWN', 'INTERNAL', 'EXTERNAL', 'WIFI', 'OVERLAY', 'VIRTUAL', "
                        + "but got '" + type + "' instead.");
                return -1;
        }
    }

    private int showNotification() {
        final String notificationType = getNextArg();
        if (notificationType == null) {
            getErrPrintWriter().println("Error: no notificationType specified, use one of: "
                    + NOTIFICATION_TYPES);
            return 1;
        }

        switch (notificationType) {
            case "on-hotplug-error":
                mService.getDisplayNotificationManager().onHotplugConnectionError();
                break;
            case "on-link-training-failure":
                mService.getDisplayNotificationManager().onDisplayPortLinkTrainingFailure();
                break;
            case "on-cable-dp-incapable":
                mService.getDisplayNotificationManager().onCableNotCapableDisplayPort();
                break;
            default:
                getErrPrintWriter().println(
                        "Error: unexpected notification type=" + notificationType + ", use one of: "
                                + NOTIFICATION_TYPES);
                return 1;
        }

        return 0;
    }

    private int cancelNotifications() {
        mService.getDisplayNotificationManager().cancelNotifications();
        return 0;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int getBrightness() {
        String opt;
        int displayId = Display.DEFAULT_DISPLAY;
        int unit = INVALID_BRIGHTNESS_UNIT;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--id" -> {
                    String displayIdString = getNextArgRequired();
                    try {
                        displayId = Integer.parseInt(displayIdString);
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println(
                                "Error: invalid displayId=" + displayIdString + ", not an int");
                        return 1;
                    }
                }
                case "--unit" -> {
                    String brightnessUnitString = getNextArgRequired();
                    if (brightnessUnitString.equals(
                            brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE))) {
                        unit = DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE;
                    } else if (brightnessUnitString.equals(
                            brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_NITS))) {
                        unit = DisplayManager.BRIGHTNESS_UNIT_NITS;
                    } else {
                        getErrPrintWriter().println(
                                "Unexpected brightness unit: " + brightnessUnitString);
                        return 1;
                    }
                }
            }
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        float brightness;
        if (unit == INVALID_BRIGHTNESS_UNIT) {
            brightness = dm.getBrightness(displayId);
        } else {
            brightness = dm.getBrightness(displayId, unit);
        }
        getOutPrintWriter().println(brightness);
        return 0;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int setBrightness() {
        String brightnessText = getNextArg();
        if (brightnessText == null) {
            getErrPrintWriter().println("Error: no brightness specified");
            return 1;
        }
        float brightness;
        try {
            brightness = Float.parseFloat(brightnessText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println(
                    "Error: invalid brightness=" + brightnessText + ", not a float");
            return 1;
        }

        String opt;
        int displayId = Display.DEFAULT_DISPLAY;
        int unit = INVALID_BRIGHTNESS_UNIT;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--id" -> {
                    String displayIdString = getNextArgRequired();
                    try {
                        displayId = Integer.parseInt(displayIdString);
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println(
                                "Error: invalid displayId=" + displayIdString + ", not an int");
                        return 1;
                    }
                }
                case "--unit" -> {
                    String brightnessUnitString = getNextArgRequired();
                    if (brightnessUnitString.equals(
                            brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE))) {
                        unit = DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE;
                    } else if (brightnessUnitString.equals(
                            brightnessUnitToString(DisplayManager.BRIGHTNESS_UNIT_NITS))) {
                        unit = DisplayManager.BRIGHTNESS_UNIT_NITS;
                    } else {
                        getErrPrintWriter().println(
                                "Unexpected brightness unit: " + brightnessUnitString);
                        return 1;
                    }
                }
            }
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        if (unit == INVALID_BRIGHTNESS_UNIT) {
            dm.setBrightness(displayId, brightness);
        } else {
            dm.setBrightness(displayId, brightness, unit);
        }
        return 0;
    }

    private int resetBrightnessConfiguration() {
        mService.resetBrightnessConfigurations();
        return 0;
    }

    private int setAutoBrightnessLoggingEnabled(boolean enabled) {
        mService.setAutoBrightnessLoggingEnabled(enabled);
        return 0;
    }

    private int setDisplayWhiteBalanceLoggingEnabled(boolean enabled) {
        mService.setDisplayWhiteBalanceLoggingEnabled(enabled);
        return 0;
    }

    private int setDisplayModeDirectorLoggingEnabled(boolean enabled) {
        mService.setDisplayModeDirectorLoggingEnabled(enabled);
        return 0;
    }

    private int setAmbientColorTemperatureOverride() {
        String cctText = getNextArg();
        if (cctText == null) {
            getErrPrintWriter().println("Error: no cct specified");
            return 1;
        }
        float cct;
        try {
            cct = Float.parseFloat(cctText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: cct should be a number");
            return 1;
        }
        mService.setAmbientColorTemperatureOverride(cct);
        return 0;
    }

    private int setUserPreferredDisplayMode() {
        final String widthText = getNextArg();
        if (widthText == null) {
            getErrPrintWriter().println("Error: no width specified");
            return 1;
        }

        final String heightText = getNextArg();
        if (heightText == null) {
            getErrPrintWriter().println("Error: no height specified");
            return 1;
        }

        final String refreshRateText = getNextArg();
        if (refreshRateText == null) {
            getErrPrintWriter().println("Error: no refresh-rate specified");
            return 1;
        }

        final int width, height;
        final float refreshRate;
        try {
            width = Integer.parseInt(widthText);
            height = Integer.parseInt(heightText);
            refreshRate = Float.parseFloat(refreshRateText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid format of width, height or refresh rate");
            return 1;
        }
        if ((width < 0 || height < 0) && refreshRate <= 0.0f) {
            getErrPrintWriter().println("Error: invalid value of resolution (width, height)"
                    + " and refresh rate");
            return 1;
        }

        final String displayIdText = getNextArg();
        int displayId = Display.INVALID_DISPLAY;
        if (displayIdText != null) {
            try {
                displayId = Integer.parseInt(displayIdText);
            } catch (NumberFormatException e) {
                getErrPrintWriter().println("Error: invalid format of display ID");
                return 1;
            }
        }
        final String storeModeText = getNextArg();
        boolean storeMode = true;
        if (storeModeText != null) {
            storeMode = Boolean.parseBoolean(storeModeText);
        }

        mService.setUserPreferredDisplayModeInternal(
                displayId, new Display.Mode(width, height, refreshRate), storeMode);
        return 0;
    }

    private int resetUserPreferredDisplayMode() {
        final String displayIdText = getNextArg();
        int displayId = Display.INVALID_DISPLAY;
        if (displayIdText != null) {
            try {
                displayId = Integer.parseInt(displayIdText);
            } catch (NumberFormatException e) {
                getErrPrintWriter().println("Error: invalid format of display ID");
                return 1;
            }
        }
        mService.resetUserPreferredDisplayModeInternal(displayId);
        return 0;
    }

    private int clearUserPreferredDisplayMode() {
        final String displayIdText = getNextArg();
        int displayId = Display.INVALID_DISPLAY;
        if (displayIdText != null) {
            try {
                displayId = Integer.parseInt(displayIdText);
            } catch (NumberFormatException e) {
                getErrPrintWriter().println("Error: invalid format of display ID");
                return 1;
            }
        }
        mService.setUserPreferredDisplayModeInternal(displayId, null);
        return 0;
    }

    private int getUserPreferredDisplayMode() {
        final String displayIdText = getNextArg();
        int displayId = Display.INVALID_DISPLAY;
        if (displayIdText != null) {
            try {
                displayId = Integer.parseInt(displayIdText);
            } catch (NumberFormatException e) {
                getErrPrintWriter().println("Error: invalid format of display ID");
                return 1;
            }
        }
        final Display.Mode mode = mService.getUserPreferredDisplayModeInternal(displayId);
        if (mode == null) {
            getOutPrintWriter().println("User preferred display mode: null");
            return 0;
        }

        getOutPrintWriter().println("User preferred display mode: " + mode.getPhysicalWidth() + " "
                + mode.getPhysicalHeight() + " " + mode.getRefreshRate());
        return 0;
    }

    private int getActiveDisplayModeAtStart() {
        final String displayIdText = getNextArg();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: no displayId specified");
            return 1;
        }
        final int displayId;
        try {
            displayId = Integer.parseInt(displayIdText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid displayId");
            return 1;
        }

        Display.Mode mode = mService.getActiveDisplayModeAtStart(displayId);
        if (mode == null) {
            getOutPrintWriter().println("Boot display mode: null");
            return 0;
        }
        getOutPrintWriter().println("Boot display mode: " + mode.getPhysicalWidth() + " "
                + mode.getPhysicalHeight() + " " + mode.getRefreshRate());
        return 0;
    }

    private int setMatchContentFrameRateUserPreference() {
        final String matchContentFrameRatePrefText = getNextArg();
        if (matchContentFrameRatePrefText == null) {
            getErrPrintWriter().println("Error: no matchContentFrameRatePref specified");
            return 1;
        }

        final int matchContentFrameRatePreference;
        try {
            matchContentFrameRatePreference = Integer.parseInt(matchContentFrameRatePrefText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid format of matchContentFrameRatePreference");
            return 1;
        }
        if (matchContentFrameRatePreference < 0) {
            getErrPrintWriter().println("Error: invalid value of matchContentFrameRatePreference");
            return 1;
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);

        final int refreshRateSwitchingType =
                toRefreshRateSwitchingType(matchContentFrameRatePreference);
        dm.setRefreshRateSwitchingType(refreshRateSwitchingType);
        return 0;
    }

    private int getMatchContentFrameRateUserPreference() {
        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        getOutPrintWriter().println("Match content frame rate type: "
                + dm.getMatchContentFrameRateUserPreference());
        return 0;
    }

    private int setUserDisabledHdrTypes() {
        String[] userDisabledHdrTypesText = peekRemainingArgs();
        if (userDisabledHdrTypesText == null) {
            getErrPrintWriter().println("Error: no userDisabledHdrTypes specified");
            return 1;
        }

        int[] userDisabledHdrTypes = new int[userDisabledHdrTypesText.length];
        try {
            int index = 0;
            for (String userDisabledHdrType : userDisabledHdrTypesText) {
                userDisabledHdrTypes[index++] = Integer.parseInt(userDisabledHdrType);
            }
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid format of userDisabledHdrTypes");
            return 1;
        }
        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        dm.setUserDisabledHdrTypes(userDisabledHdrTypes);
        return 0;
    }

    private int getUserDisabledHdrTypes() {
        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final int[] userDisabledHdrTypes = dm.getUserDisabledHdrTypes();
        getOutPrintWriter().println("User disabled HDR types: "
                + Arrays.toString(userDisabledHdrTypes));
        return 0;
    }

    @DisplayManager.SwitchingType
    private int toRefreshRateSwitchingType(
            @DisplayManager.MatchContentFrameRateType int matchContentFrameRateType) {
        switch (matchContentFrameRateType) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_UNKNOWN:
            default:
                Slog.e(TAG, matchContentFrameRateType + " is not a valid value of "
                        + "matchContentFrameRate type.");
                return -1;
        }
    }

    private int setDockedAndIdle() {
        mService.setDockedAndIdleEnabled(true, Display.DEFAULT_DISPLAY);
        return 0;
    }

    private int unsetDockedAndIdle() {
        mService.setDockedAndIdleEnabled(false, Display.DEFAULT_DISPLAY);
        return 0;
    }

    private int setDisplayEnabled(boolean enable) {
        final String displayIdText = getNextArg();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: no displayId specified");
            return 1;
        }
        final int displayId;
        try {
            displayId = Integer.parseInt(displayIdText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid displayId: '" + displayIdText + "'");
            return 1;
        }
        if (!enable && displayId == Display.DEFAULT_DISPLAY) {
            getErrPrintWriter().println("Error: cannot disable default display");
            return 1;
        }
        mService.enableConnectedDisplay(displayId, enable);
        return 0;
    }

    private int requestDisplayPower(int state) {
        final String displayIdText = getNextArg();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: no displayId specified");
            return 1;
        }
        final int displayId;
        try {
            displayId = Integer.parseInt(displayIdText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid displayId: '" + displayIdText + "'");
            return 1;
        }
        mService.requestDisplayPower(displayId, state);
        return 0;
    }

    private int overrideMaxImportanceForRRCallbacks() {
        final String importanceString = getNextArg();
        if (importanceString == null) {
            getErrPrintWriter().println("Error: no importance specified");
            return 1;
        }
        final int importance;
        try {
            importance = Integer.parseInt(importanceString);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: invalid importance: '" + importanceString + "'");
            return 1;
        }
        mService.overrideMaxImportanceForRRCallbacks(importance);
        return 0;
    }

    private int getHdrConversionMode() {
        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);

        try {
            HdrConversionMode hdrMode = dm.getHdrConversionMode();
            if (hdrMode == null) {
                getOutPrintWriter().println("HDR Conversion Mode: null");
            } else {
                getOutPrintWriter().println("HDR Conversion Mode: " + hdrMode.getConversionMode());
                getOutPrintWriter().println("Preferred HDR Output Type: "
                        + hdrMode.getPreferredHdrOutputType());
            }
        } catch (NoSuchMethodError e) {
            getErrPrintWriter().println("Error: Could not find getHdrConversionMode method. "
                    + "Check API access.");
            return 1;
        }
        return 0;
    }

    private int getReportedHdrTypes() {
        final String displayIdText = getNextArgRequired();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: Missing required argument DISPLAY_ID.");
            return 1;
        }

        int displayId;
        try {
            displayId = Integer.parseInt(displayIdText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: Invalid format for DISPLAY_ID: " + displayIdText);
            return 1;
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(displayId);

        if (display == null) {
            getErrPrintWriter().println("Error: Display with ID " + displayId + " not found.");
            return 1;
        }

        try {
            int[] hdrTypes = display.getReportedHdrTypes();
            getOutPrintWriter().println("Reported HDR types for display " + displayId
                    + " (current mode): " + Arrays.toString(hdrTypes));
        } catch (Exception e) {
            getErrPrintWriter().println("Error retrieving reported HDR types for display "
                    + displayId + ": " + e.getMessage());
            Slog.e(TAG, "Failed to get reported HDR types for display " + displayId, e);
            return 1;
        }
        return 0;
    }

    private int getSupportedModes() {
        final String displayIdText = getNextArgRequired();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: Missing required argument DISPLAY_ID.");
            return 1;
        }

        int displayId;
        try {
            displayId = Integer.parseInt(displayIdText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: Invalid format for DISPLAY_ID: " + displayIdText);
            return 1;
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(displayId);

        if (display == null) {
            getErrPrintWriter().println("Error: Display with ID " + displayId + " not found.");
            return 1;
        }

        try {
            Display.Mode[] supportedModes = display.getSupportedModes();

            if (supportedModes == null || supportedModes.length == 0) {
                getOutPrintWriter().println("No supported modes reported for display " + displayId
                        + ".");
            } else {
                getOutPrintWriter().println("Supported modes for display " + displayId + ":");
                for (Display.Mode mode : supportedModes) {
                    getOutPrintWriter().printf("  Mode ID: %d, Resolution: %dx%d, Refresh Rate: "
                            + " %.2f Hz\n",
                            mode.getModeId(),
                            mode.getPhysicalWidth(),
                            mode.getPhysicalHeight(),
                            mode.getRefreshRate());
                }
            }
        } catch (Exception e) {
            getErrPrintWriter().println("Error retrieving supported modes for display " + displayId
                    + ": " + e.getMessage());
            Slog.e(TAG, "Failed to get supported modes for display " + displayId, e);
            return 1;
        }
        return 0;
    }

    private int getModeSupportedHdrTypes() {
        final String displayIdText = getNextArgRequired();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: Missing required argument DISPLAY_ID.");
            return 1;
        }
        final String widthText = getNextArgRequired();
        if (widthText == null) {
            getErrPrintWriter().println("Error: Missing required argument WIDTH.");
            return 1;
        }
        final String heightText = getNextArgRequired();
        if (heightText == null) {
            getErrPrintWriter().println("Error: Missing required argument HEIGHT.");
            return 1;
        }
        final String refreshRateText = getNextArgRequired();
        if (refreshRateText == null) {
            getErrPrintWriter().println("Error: Missing required argument REFRESH_RATE.");
            return 1;
        }

        int displayId, width, height;
        float refreshRate;
        try {
            displayId = Integer.parseInt(displayIdText);
            width = Integer.parseInt(widthText);
            height = Integer.parseInt(heightText);
            refreshRate = Float.parseFloat(refreshRateText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: Invalid format for numerical arguments (DISPLAY_ID,"
                    + "WIDTH, HEIGHT, REFRESH_RATE).");
            getErrPrintWriter().println(e.getMessage());
            return 1;
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(displayId);

        if (display == null) {
            getErrPrintWriter().println("Error: Display with ID " + displayId + " not found.");
            return 1;
        }

        Display.Mode targetMode = null;
        Display.Mode[] supportedModes = display.getSupportedModes();

        for (Display.Mode mode : supportedModes) {
            if (mode.getPhysicalWidth() == width
                    && mode.getPhysicalHeight() == height
                    && Math.abs(mode.getRefreshRate() - refreshRate) < 0.001f) {
                targetMode = mode;
                break;
            }
        }

        if (targetMode == null) {
            getErrPrintWriter().println("Error: Mode with resolution " + width + "x" + height
                    + " and refresh rate " + refreshRate + "Hz not supported by display "
                    + displayId);
            return 1;
        }

        try {
            int[] hdrTypes = targetMode.getSupportedHdrTypes();
            getOutPrintWriter().println("Supported HDR types for mode " + width + "x" + height
                    + " @ " + refreshRate + "Hz on display " + displayId + ": "
                    + Arrays.toString(hdrTypes));
        } catch (Exception e) {
            getErrPrintWriter().println("Error retrieving supported HDR types for mode on display "
                    + displayId + ": " + e.getMessage());
            Slog.e(TAG, "Failed to get supported HDR types for specific mode on display "
                    + displayId, e);
            return 1;
        }

        return 0;
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private int setHdrConversionMode() {
        final String modeText = getNextArgRequired();
        if (modeText == null) {
            getErrPrintWriter().println("Error: Missing required argument MODE.");
            return 1;
        }

        int modeInt;
        try {
            modeInt = Integer.parseInt(modeText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: Invalid format for MODE: " + modeText);
            return 1;
        }

        if (modeInt < HdrConversionMode.HDR_CONVERSION_UNSUPPORTED
                || modeInt > HdrConversionMode.HDR_CONVERSION_FORCE) {
            getErrPrintWriter().println("Error: Invalid value for MODE: " + modeInt
                    + ". Use 0 (UNSUPPORTED), 1 (PASSTHROUGH), 2 (SYSTEM), or 3 (FORCE).");
            return 1;
        }

        int preferredTypeInt = Display.HdrCapabilities.HDR_TYPE_INVALID;

        if (modeInt == HdrConversionMode.HDR_CONVERSION_FORCE) {
            final String preferredTypeText = getNextArgRequired();
            if (preferredTypeText == null) {
                getErrPrintWriter().println("Error: Missing required argument PREFERRED_TYPE "
                        + "(needed for FORCE mode).");
                return 1;
            }
            try {
                preferredTypeInt = Integer.parseInt(preferredTypeText);
            } catch (NumberFormatException e) {
                getErrPrintWriter().println("Error: Invalid format for PREFERRED_TYPE: "
                        + preferredTypeText);
                return 1;
            }

            boolean isValidPreferredType =
                    (preferredTypeInt == Display.HdrCapabilities.HDR_TYPE_INVALID)
                    || (preferredTypeInt >= Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
                            && preferredTypeInt <= Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS);

            if (!isValidPreferredType) {
                getErrPrintWriter().println("Error: Invalid value for PREFERRED_TYPE ("
                        + preferredTypeInt
                        + ") for FORCE mode. Must be a valid HDR type (e.g., -1, 1, 2, 3, 4).");
                return 1;
            }
        } else {
            String extraArg = getNextArg();
            if (extraArg != null) {
                getOutPrintWriter().println("Warning: Extra argument '" + extraArg + "' ignored."
                        + " PREFERRED_TYPE is only used for FORCE mode.");
            }
        }

        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        HdrConversionMode hdrMode = null;

        try {
            hdrMode = new HdrConversionMode(modeInt, preferredTypeInt);

            dm.setHdrConversionMode(hdrMode);
            getOutPrintWriter().println("Successfully set " + hdrMode.toString());

        } catch (IllegalArgumentException e) {
            getErrPrintWriter().println("Error: Invalid combination of MODE and PREFERRED_TYPE.");
            getErrPrintWriter().println(e.getMessage());
            return 1;
        } catch (SecurityException e) {
            getErrPrintWriter().println("Error: Permission denied. Requires "
                    + "MODIFY_HDR_CONVERSION_MODE.");
            return 1;
        } catch (NoSuchMethodError e) {
            getErrPrintWriter().println("Error: Could not find setHdrConversionMode method. "
                    + "Check API access.");
            return 1;
        } catch (Exception e) {
            getErrPrintWriter().println("Error setting HDR conversion mode: " + e.getMessage());
            Slog.e(TAG, "Failed to set HDR conversion mode", e);
            return 1;
        }
        return 0;
    }

    private int getSupportedHdrOutputTypes() {
        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);

        try {
            int[] hdrTypes = dm.getSupportedHdrOutputTypes();

            if (hdrTypes == null) {
                getOutPrintWriter().println("Supported HDR Output Types: null (or unable to "
                        + "retrieve)");
            } else {
                getOutPrintWriter().println("Supported HDR Output Types: "
                        + Arrays.toString(hdrTypes));
            }
        } catch (NoSuchMethodError e) {
            getErrPrintWriter().println("Error: Could not find getSupportedHdrOutputTypes method "
                    + "on DisplayManager.");
            getErrPrintWriter()
                    .println("Check API availability or implement access via DisplayManagerService "
                        + "internal state.");
            return 1;
        } catch (Exception e) {
            getErrPrintWriter().println("Error retrieving supported HDR output types: "
                    + e.getMessage());
            Slog.e(TAG, "Failed to get supported HDR output types", e);
            return 1;
        }
        return 0;
    }

    private int getActiveMode() {
        final Context context = mService.getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);

        final String displayIdText = getNextArgRequired();
        if (displayIdText == null) {
            getErrPrintWriter().println("Error: Missing required argument DISPLAY_ID.");
            return 1;
        }

        int displayId;
        try {
            displayId = Integer.parseInt(displayIdText);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println("Error: Invalid format for DISPLAY_ID: " + displayIdText);
            return 1;
        }

        final Display display = dm.getDisplay(displayId);

        if (display == null) {
            getErrPrintWriter().println("Error: Display with ID " + displayId + " not found.");
            return 1;
        }

        try {
            Display.Mode activeMode = display.getMode();

            if (activeMode == null) {
                getOutPrintWriter().println("Active mode for display " + displayId
                        + ": null (or could not be determined)");
            } else {
                getOutPrintWriter().println("Active mode for display " + displayId + ":");
                getOutPrintWriter().printf("  Mode ID: %d, Resolution: %dx%d, "
                        + "Refresh Rate: %.2f Hz\n",
                        activeMode.getModeId(),
                        activeMode.getPhysicalWidth(),
                        activeMode.getPhysicalHeight(),
                        activeMode.getRefreshRate());
            }
        } catch (Exception e) {
            getErrPrintWriter().println("Error retrieving active mode for display "
                    + displayId + ": "
                    + e.getMessage());
            Slog.e(TAG, "Failed to get active mode for display " + displayId, e);
            return 1;
        }
        return 0;
    }
}
