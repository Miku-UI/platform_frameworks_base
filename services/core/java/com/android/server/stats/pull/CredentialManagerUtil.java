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

package com.android.server.stats.pull;

import static android.os.Process.INVALID_UID;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.CredentialManager;
import android.credentials.CredentialProviderInfo;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for Credential Manager-related logging preprocessing to be utilized in
 * {@link StatsPullAtomService}.
 */
public final class CredentialManagerUtil {

    // LINT.IfChange(delim)
    private static final String SETTINGS_DELIMITER = ":";
    // LINT.ThenChange(
    // /services/credentials/java/com/android/server/credentials/CredentialManagerService.java:delim
    // )

    private CredentialManagerUtil() {}

    /** Returns the base UID of the preferred autofill service for a given user. */
    public static int getPreferredAutofillServiceBaseUid(Context context, int userId) {
        String flattenedComponentName = Settings.Secure.getStringForUser(
                context.getContentResolver(),
                Settings.Secure.AUTOFILL_SERVICE,
                userId);
        return getBaseUidFromFlattenedComponentName(context, flattenedComponentName);
    }

    /** Returns the base UIDs of the preferred credential services for a given user. */
    public static int[] getPreferredCredentialServicesBaseUids(Context context, int userId) {
        String source = Settings.Secure.getStringForUser(
                context.getContentResolver(),
                Settings.Secure.CREDENTIAL_SERVICE_PRIMARY,
                userId);
        List<String> flattenedComponentNames = splitCredmanSettingsString(source);
        return getBaseUidsFromList(flattenedComponentNames,
                flattenedComponentName -> getBaseUidFromFlattenedComponentName(context,
                        flattenedComponentName));
    }

    /** Returns the base UIDs of the available credential services for a given user. */
    @SuppressLint("MissingPermission")
    public static int[] getAvailableCredentialServicesBaseUids(Context context, int userId) {
        CredentialManager credentialManager = context.getSystemService(CredentialManager.class);
        if (credentialManager == null) {
            return new int[0];
        }
        List<CredentialProviderInfo> credentialServices =
                credentialManager.getCredentialProviderServices(userId,
                        CredentialManager.PROVIDER_FILTER_USER_PROVIDERS_INCLUDING_HIDDEN);
        return getBaseUidsFromList(credentialServices,
                credentialService -> getBaseUidFromPackageName(context,
                        credentialService.getServiceInfo().packageName));
    }

    /** Returns the base UIDs of the enabled credential services for a given user. */
    public static int[] getEnabledCredentialServicesBaseUids(Context context, int userId) {
        String source = Settings.Secure.getStringForUser(
                context.getContentResolver(),
                Settings.Secure.CREDENTIAL_SERVICE,
                userId);
        List<String> flattenedComponentNames = splitCredmanSettingsString(source);
        return getBaseUidsFromList(flattenedComponentNames,
                flattenedComponentName -> getBaseUidFromFlattenedComponentName(context,
                        flattenedComponentName));
    }

    /**
     * Splits a delimited Credential Manager settings string into individual names.
     *
     * <p>Settings such as {@link Settings.Secure#CREDENTIAL_SERVICE} are stored as a single
     * string where multiple flattened component names are separated by {@link #SETTINGS_DELIMITER}
     * (e.g., "pkg1/cls1:pkg2/cls2").
     *
     * @param source The raw delimited settings string for Credential Manager.
     * @return A list of flattened component names, or an empty list if {@code source} is null or
     * empty.
     */
    private static List<String> splitCredmanSettingsString(@Nullable String source) {
        if (TextUtils.isEmpty(source)) {
            return Collections.emptyList();
        }
        return Arrays.asList(source.split(SETTINGS_DELIMITER));
    }

    /**
     * Maps a list of items to their corresponding base UIDs using the provided mapper function.
     *
     * @param <T>       The type of items in the input list.
     * @param items     The list of items to process.
     * @param uidMapper A function that maps an item of type {@code T} to an integer base UID.
     * @return An array of valid base UIDs, or an empty array if the input list is null, empty, or
     * contains no valid UIDs.
     */
    private static <T> int[] getBaseUidsFromList(
            @Nullable List<T> items,
            java.util.function.ToIntFunction<T> uidMapper
    ) {
        if (items == null || items.isEmpty()) {
            return new int[0];
        }
        int[] uids = new int[items.size()];
        int count = 0;
        for (T item : items) {
            int uid = uidMapper.applyAsInt(item);
            if (uid != INVALID_UID) {
                uids[count++] = uid;
            }
        }
        return (count == uids.length) ? uids : Arrays.copyOf(uids, count);
    }

    /**
     * Resolves a flattened component name string into its corresponding package's base UID.
     *
     * @param context                The application context.
     * @param flattenedComponentName The component name in "package/class" format.
     * @return The base UID of the package, or {@link android.os.Process#INVALID_UID} if the string
     * is empty or invalid.
     */
    private static int getBaseUidFromFlattenedComponentName(
            Context context,
            @Nullable String flattenedComponentName
    ) {
        if (TextUtils.isEmpty(flattenedComponentName)) {
            return INVALID_UID;
        }
        ComponentName componentName = ComponentName.unflattenFromString(flattenedComponentName);
        return (componentName != null) ? getBaseUidFromPackageName(context,
                componentName.getPackageName()) : INVALID_UID;
    }

    /**
     * Retrieves the base UID for a given package name.
     *
     * @param context     The application context.
     * @param packageName The name of the package to look up.
     * @return The base UID of the package, or {@link android.os.Process#INVALID_UID} if the package
     * is null or not found on the system.
     */
    private static int getBaseUidFromPackageName(Context context, @Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return INVALID_UID;
        }
        try {
            return context.getPackageManager().getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return INVALID_UID;
        }
    }
}
