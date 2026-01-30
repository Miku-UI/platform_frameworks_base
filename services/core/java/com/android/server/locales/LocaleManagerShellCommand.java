/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.locales;

import android.app.ActivityManager;
import android.app.ILocaleManager;
import android.app.LocaleConfig;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.LocaleList;
import android.os.RemoteException;
import android.os.ShellCommand;
import android.os.SystemProperties;
import android.os.UserHandle;

import com.android.internal.app.LocalePicker;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Shell commands for {@link LocaleManagerService}
 */
public class LocaleManagerShellCommand extends ShellCommand {

    private final ILocaleManager mBinderService;
    private final Context mContext;
    private Set<Locale> mSupportedLocalesSet;
    private static final int ERROR_NO_LOCALE_SPECIFIED = -1;
    private static final int ERROR_INVALID_LOCALE_TAG = -2;
    private static final int ERROR_FETCHING_SYSTEM_LOCALE = -3;
    private static final int ERROR_NO_SUPPORTED_LOCALES_FOUND = -4;
    private static final int SUCCESS = 0;

    LocaleManagerShellCommand(ILocaleManager localeManager, Context context) {
        mBinderService = localeManager;
        mContext = context;
        mSupportedLocalesSet = new HashSet<>();
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        switch (cmd) {
            case "set-app-locales":
                return runSetAppLocales();
            case "get-app-locales":
                return runGetAppLocales();
            case "set-app-localeconfig":
                return runSetAppOverrideLocaleConfig();
            case "get-app-localeconfig":
                return runGetAppOverrideLocaleConfig();
            case "get-app-localeconfig-ignore-override":
                return runGetAppLocaleConfigIgnoreOverride();
            case "set-device-locale":
                return runSetDeviceLocale();
            case "get-device-locale":
                return runGetDeviceLocale();
            case "list-device-locales":
                return runListDeviceLocales();
            default: {
                return handleDefaultCommands(cmd);
            }
        }
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Locale manager (locale) shell commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println("  set-app-locales <PACKAGE_NAME> [--user <USER_ID>] [--locales <LOCALE_INFO>]"
                + "[--delegate <FROM_DELEGATE>]");
        pw.println("    Set the locales for the specified app.");
        pw.println("    --user <USER_ID>: apply for the given user, "
                + "the current user is used when unspecified.");
        pw.println("    --locales <LOCALE_INFO>: The language tags of locale to be included "
                + "as a single String separated by commas.");
        pw.println("            eg. en,en-US,hi ");
        pw.println("            Empty locale list is used when unspecified.");
        pw.println("    --delegate <FROM_DELEGATE>: The locales are set from a delegate, "
                + "the value could be true or false. false is the default when unspecified.");
        pw.println("  get-app-locales <PACKAGE_NAME> [--user <USER_ID>]");
        pw.println("    Get the locales for the specified app.");
        pw.println("    --user <USER_ID>: get for the given user, "
                + "the current user is used when unspecified.");
        pw.println(
                "  set-app-localeconfig <PACKAGE_NAME> [--user <USER_ID>] [--locales "
                        + "<LOCALE_INFO>]");
        pw.println("    Set the override LocaleConfig for the specified app.");
        pw.println("    --user <USER_ID>: apply for the given user, "
                + "the current user is used when unspecified.");
        pw.println("    --locales <LOCALE_INFO>: The language tags of locale to be included "
                + "as a single String separated by commas.");
        pw.println("            eg. en,en-US,hi ");
        pw.println("            Empty locale list is used when typing a 'empty' word");
        pw.println("            NULL is used when unspecified.");
        pw.println("  get-app-localeconfig <PACKAGE_NAME> [--user <USER_ID>]");
        pw.println("    Get the locales within the override LocaleConfig for the specified app.");
        pw.println("    --user <USER_ID>: get for the given user, "
                + "the current user is used when unspecified.");
        pw.println("  set-device-locale");
        pw.println("    Set the locale of the device.");
        pw.println("    <LOCALE_NAME>: The BCP 47 language tag of the locale to set (e.g., "
                + "en-US, es, fr-CA).");
        pw.println("  get-device-locale");
        pw.println("    Get the locale of the device.");
        pw.println("    Outputs the current primary device locale as a BCP 47 language tag.");
        pw.println("  list-device-locales");
        pw.println("    List the locales of the device.");
        pw.println("    Outputs a list of all BCP 47 language tags for locales supported by the "
                + "device.");
    }

    private int runSetAppLocales() {
        final PrintWriter err = getErrPrintWriter();
        String packageName = getNextArg();

        if (packageName != null) {
            int userId = ActivityManager.getCurrentUser();
            LocaleList locales = LocaleList.getEmptyLocaleList();
            boolean fromDelegate = false;
            do {
                String option = getNextOption();
                if (option == null) {
                    break;
                }
                switch (option) {
                    case "--user": {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                        break;
                    }
                    case "--locales": {
                        locales = parseLocales();
                        break;
                    }
                    case "--delegate": {
                        fromDelegate = parseFromDelegate();
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("Unknown option: " + option);
                    }
                }
            } while (true);

            try {
                mBinderService.setApplicationLocales(packageName, userId, locales, fromDelegate);
            } catch (RemoteException e) {
                getOutPrintWriter().println("Remote Exception: " + e);
            } catch (IllegalArgumentException e) {
                getOutPrintWriter().println("Unknown package " + packageName
                        + " for userId " + userId);
            }
        } else {
            err.println("Error: no package specified");
            return -1;
        }
        return 0;
    }

    private int runGetAppLocales() {
        final PrintWriter err = getErrPrintWriter();
        String packageName = getNextArg();

        if (packageName != null) {
            int userId = ActivityManager.getCurrentUser();
            do {
                String option = getNextOption();
                if (option == null) {
                    break;
                }
                if ("--user".equals(option)) {
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                } else {
                    throw new IllegalArgumentException("Unknown option: " + option);
                }
            } while (true);
            try {
                LocaleList locales = mBinderService.getApplicationLocales(packageName, userId);
                getOutPrintWriter().println("Locales for " + packageName
                        + " for user " + userId + " are [" + locales.toLanguageTags() + "]");
            } catch (RemoteException e) {
                getOutPrintWriter().println("Remote Exception: " + e);
            } catch (IllegalArgumentException e) {
                getOutPrintWriter().println("Unknown package " + packageName
                        + " for userId " + userId);
            }
        } else {
            err.println("Error: no package specified");
            return -1;
        }
        return 0;
    }

    private int runSetAppOverrideLocaleConfig() {
        String packageName = getNextArg();

        if (packageName != null) {
            int userId = ActivityManager.getCurrentUser();
            LocaleList locales = null;
            do {
                String option = getNextOption();
                if (option == null) {
                    break;
                }
                switch (option) {
                    case "--user": {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                        break;
                    }
                    case "--locales": {
                        locales = parseOverrideLocales();
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("Unknown option: " + option);
                    }
                }
            } while (true);

            try {
                LocaleConfig localeConfig = locales == null ? null : new LocaleConfig(locales);
                mBinderService.setOverrideLocaleConfig(packageName, userId, localeConfig);
            } catch (RemoteException e) {
                getOutPrintWriter().println("Remote Exception: " + e);
            }
        } else {
            final PrintWriter err = getErrPrintWriter();
            err.println("Error: no package specified");
            return -1;
        }
        return 0;
    }

    private int runGetAppOverrideLocaleConfig() {
        String packageName = getNextArg();

        if (packageName != null) {
            int userId = ActivityManager.getCurrentUser();
            do {
                String option = getNextOption();
                if (option == null) {
                    break;
                }
                if ("--user".equals(option)) {
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                } else {
                    throw new IllegalArgumentException("Unknown option: " + option);
                }
            } while (true);
            try {
                LocaleConfig localeConfig = mBinderService.getOverrideLocaleConfig(packageName,
                        userId);
                if (localeConfig == null) {
                    getOutPrintWriter().println("LocaleConfig for " + packageName
                            + " for user " + userId + " is null");
                } else {
                    LocaleList locales = localeConfig.getSupportedLocales();
                    if (locales == null) {
                        getOutPrintWriter().println(
                                "Locales within the LocaleConfig for " + packageName + " for user "
                                        + userId + " are null");
                    } else {
                        getOutPrintWriter().println(
                                "Locales within the LocaleConfig for " + packageName + " for user "
                                        + userId + " are [" + locales.toLanguageTags() + "]");
                    }
                }
            } catch (RemoteException e) {
                getOutPrintWriter().println("Remote Exception: " + e);
            }
        } else {
            final PrintWriter err = getErrPrintWriter();
            err.println("Error: no package specified");
            return -1;
        }
        return 0;
    }

    private int runGetAppLocaleConfigIgnoreOverride() {
        String packageName = getNextArg();
        final PrintWriter err = getErrPrintWriter();

        if (packageName != null) {
            int userId = ActivityManager.getCurrentUser();
            do {
                String option = getNextOption();
                if (option == null) {
                    break;
                }
                if ("--user".equals(option)) {
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                } else {
                    throw new IllegalArgumentException("Unknown option: " + option);
                }
            } while (true);
            LocaleConfig resLocaleConfig = null;
            try {
                resLocaleConfig = LocaleConfig.fromContextIgnoringOverride(
                        mContext.createPackageContextAsUser(packageName, /* flags= */ 0,
                                UserHandle.of(userId)));
            } catch (PackageManager.NameNotFoundException e) {
                err.println("Unknown package name " + packageName + " for user " + userId);
                return -1;
            }
            if (resLocaleConfig == null) {
                getOutPrintWriter().println(
                        "LocaleConfig for " + packageName + " for user " + userId + " is null");
            } else {
                LocaleList locales = resLocaleConfig.getSupportedLocales();
                if (locales == null) {
                    getOutPrintWriter().println(
                            "Locales within the LocaleConfig for " + packageName + " for user "
                                    + userId + " are null");
                } else {
                    getOutPrintWriter().println(
                            "Locales within the LocaleConfig for " + packageName + " for user "
                                    + userId + " are [" + locales.toLanguageTags() + "]");
                }
            }
        } else {
            err.println("Error: no package specified");
            return -1;
        }
        return 0;
    }

    private int runSetDeviceLocale() {
        final PrintWriter err = getErrPrintWriter();
        String inputLocaleTag = getNextArg();

        if (inputLocaleTag == null || inputLocaleTag.isEmpty()) {
            err.println("Error: no locale specified");
            return ERROR_NO_LOCALE_SPECIFIED;
        }

        final Locale requestedLocale = Locale.forLanguageTag(inputLocaleTag);
        if (requestedLocale == null
                || requestedLocale.getLanguage() == null
                || requestedLocale.getLanguage().isEmpty()
                || requestedLocale.getLanguage().equals("und")) {
            err.println("Error: Invalid locale tag: " + inputLocaleTag);
            return ERROR_INVALID_LOCALE_TAG;
        }

        loadSupportedLocales();

        if (mSupportedLocalesSet.isEmpty()) {
            err.println("Error: No supported locales found.");
            return ERROR_NO_SUPPORTED_LOCALES_FOUND;
        }

        if (mSupportedLocalesSet.contains(requestedLocale)) {
            LocalePicker.updateLocale(requestedLocale);
            return SUCCESS;
        } else {
            err.println("Error: Invalid locale tag: " + inputLocaleTag);
            return ERROR_INVALID_LOCALE_TAG;
        }
    }

    private int runGetDeviceLocale() {
        final PrintWriter err = getErrPrintWriter();
        LocaleList systemLocales = LocalePicker.getLocales();
        Locale currentLocale = null;

        if (systemLocales != null && !systemLocales.isEmpty()) {
            currentLocale = systemLocales.get(0);
        }

        if (currentLocale == null
                || currentLocale.getLanguage().isEmpty()
                || currentLocale.getLanguage().equals("und")) {
            String roProductLocale = SystemProperties.get("ro.product.locale");
            if (roProductLocale == null || roProductLocale.isEmpty()) {
                err.println("Error fetching the system locale: No system locales found.");
                return ERROR_FETCHING_SYSTEM_LOCALE;
            }
            currentLocale = Locale.forLanguageTag(roProductLocale.replace('_', '-'));
        }
        if (currentLocale == null
                || currentLocale.getLanguage().isEmpty()
                || currentLocale.getLanguage().equals("und")) {
            err.println("Error: Could not determine a valid device locale.");
            return ERROR_FETCHING_SYSTEM_LOCALE;
        }

        getOutPrintWriter().println(currentLocale.toLanguageTag());
        return SUCCESS;
    }

    private int runListDeviceLocales() {
        final PrintWriter err = getErrPrintWriter();

        loadSupportedLocales();

        if (mSupportedLocalesSet.isEmpty()) {
            err.println("Error: No supported locales found.");
            return ERROR_NO_SUPPORTED_LOCALES_FOUND;
        }

        for (Locale locale : mSupportedLocalesSet) {
            getOutPrintWriter().println(locale.toLanguageTag());
        }
        return SUCCESS;
    }

    private void loadSupportedLocales() {
        if (mSupportedLocalesSet.isEmpty()) {
            String[] supportedLocales = LocalePicker.getSupportedLocales(mContext);
            if (supportedLocales != null) {
                for (String localeTag : supportedLocales) {
                    mSupportedLocalesSet.add(Locale.forLanguageTag(localeTag));
                }
            }
        }
    }

    private LocaleList parseOverrideLocales() {
        String locales = getNextArg();
        if (locales == null) {
            return null;
        } else if (locales.equals("empty")) {
            return LocaleList.getEmptyLocaleList();
        } else {
            if (locales.startsWith("-")) {
                throw new IllegalArgumentException("Unknown locales: " + locales);
            }
            return LocaleList.forLanguageTags(locales);
        }
    }

    private LocaleList parseLocales() {
        String locales = getNextArg();
        if (locales == null) {
            return LocaleList.getEmptyLocaleList();
        } else {
            if (locales.startsWith("-")) {
                throw new IllegalArgumentException("Unknown locales: " + locales);
            }
            return LocaleList.forLanguageTags(locales);
        }
    }

    private boolean parseFromDelegate() {
        String result = getNextArg();
        if (result == null) {
            return false;
        } else {
            if (result.startsWith("-")) {
                throw new IllegalArgumentException("Unknown source: " + result);
            }
            return Boolean.parseBoolean(result);
        }
    }
}
