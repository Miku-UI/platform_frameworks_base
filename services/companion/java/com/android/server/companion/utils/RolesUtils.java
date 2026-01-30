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

package com.android.server.companion.utils;

import static android.app.role.RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP;
import static android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING;
import static android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER;
import static android.companion.AssociationRequest.DEVICE_PROFILE_FITNESS_TRACKER;
import static android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES;
import static android.companion.AssociationRequest.DEVICE_PROFILE_MEDICAL;
import static android.companion.AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING;
import static android.companion.AssociationRequest.DEVICE_PROFILE_VIRTUAL_DEVICE;
import static android.companion.AssociationRequest.DEVICE_PROFILE_WATCH;
import static android.companion.CompanionResources.PERMISSION_ADD_MIRROR_DISPLAY;
import static android.companion.CompanionResources.PERMISSION_ADD_TRUSTED_DISPLAY;
import static android.companion.CompanionResources.PERMISSION_BYPASS_DND;
import static android.companion.CompanionResources.PERMISSION_CALENDAR;
import static android.companion.CompanionResources.PERMISSION_CALL_LOGS;
import static android.companion.CompanionResources.PERMISSION_CHANGE_MEDIA_OUTPUT;
import static android.companion.CompanionResources.PERMISSION_CONTACTS;
import static android.companion.CompanionResources.PERMISSION_CREATE_VIRTUAL_DEVICE;
import static android.companion.CompanionResources.PERMISSION_MICROPHONE;
import static android.companion.CompanionResources.PERMISSION_NEARBY_DEVICES;
import static android.companion.CompanionResources.PERMISSION_NOTIFICATIONS;
import static android.companion.CompanionResources.PERMISSION_PHONE;
import static android.companion.CompanionResources.PERMISSION_POST_NOTIFICATIONS;
import static android.companion.CompanionResources.PERMISSION_SCHEDULE_EXACT_ALARM;
import static android.companion.CompanionResources.PERMISSION_SMS;
import static android.companion.CompanionResources.PERMISSION_STORAGE;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.role.RoleManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/** Utility methods for accessing {@link RoleManager} APIs. */
@SuppressLint("LongLogTag")
public final class RolesUtils {

    private static final String TAG = "CDM_RolesUtils";

    private static final Map<String, List<Integer>> PROFILE_PERMISSION_SETS = Map.of(
            DEVICE_PROFILE_COMPUTER, List.of(
                    PERMISSION_NOTIFICATIONS, PERMISSION_STORAGE),

            DEVICE_PROFILE_WATCH, List.of(
                    PERMISSION_NOTIFICATIONS, PERMISSION_PHONE, PERMISSION_CALL_LOGS,
                    PERMISSION_SMS, PERMISSION_CONTACTS, PERMISSION_CALENDAR,
                    PERMISSION_NEARBY_DEVICES, PERMISSION_CHANGE_MEDIA_OUTPUT),

            DEVICE_PROFILE_GLASSES, List.of(
                    PERMISSION_NOTIFICATIONS, PERMISSION_PHONE, PERMISSION_SMS, PERMISSION_CONTACTS,
                    PERMISSION_MICROPHONE, PERMISSION_NEARBY_DEVICES),

            DEVICE_PROFILE_MEDICAL, List.of(
                    PERMISSION_POST_NOTIFICATIONS, PERMISSION_NEARBY_DEVICES,
                    PERMISSION_SCHEDULE_EXACT_ALARM, PERMISSION_BYPASS_DND
            ),

            DEVICE_PROFILE_APP_STREAMING,
            android.companion.virtualdevice.flags.Flags.itemizedVdmPermissions()
                    ? List.of(PERMISSION_CREATE_VIRTUAL_DEVICE, PERMISSION_ADD_MIRROR_DISPLAY,
                            PERMISSION_ADD_TRUSTED_DISPLAY, PERMISSION_POST_NOTIFICATIONS)
                    : Collections.emptyList(),

            DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
            android.companion.virtualdevice.flags.Flags.itemizedVdmPermissions()
                    ? List.of(PERMISSION_CREATE_VIRTUAL_DEVICE, PERMISSION_ADD_TRUSTED_DISPLAY,
                    PERMISSION_POST_NOTIFICATIONS)
                    : Collections.emptyList(),

            DEVICE_PROFILE_VIRTUAL_DEVICE, List.of(PERMISSION_CREATE_VIRTUAL_DEVICE,
                            PERMISSION_NEARBY_DEVICES, PERMISSION_POST_NOTIFICATIONS)
    );

    public static final Set<String> NLS_PROFILES = Set.of(
            DEVICE_PROFILE_WATCH,
            DEVICE_PROFILE_GLASSES,
            DEVICE_PROFILE_APP_STREAMING,
            DEVICE_PROFILE_COMPUTER);

    private static final Set<String> ROLELESS_DEVICE_PROFILES;
    static {
        final Set<String> profiles = new ArraySet<>();
        profiles.add(AssociationRequest.DEVICE_PROFILE_WEARABLE_SENSING);
        ROLELESS_DEVICE_PROFILES = Collections.unmodifiableSet(profiles);
    }

    /**
     * "Alias" refers to a device profile that reuses the same permission sets as another device
     * profile. The role holder for the alias is the same as the role holder for the actual device
     * profile.
     *
     * Useful for supporting backwards compatibility in case of renaming, deprecation, or
     * supporting multiple names for the same role. For example, a fitness tracker device profile
     * may want to reuse the same permissions as the watch device profile without having to
     * share the same profile name and device icon.
     */
    private static final Map<String, String> ROLE_ALIASES = Map.of(
            DEVICE_PROFILE_FITNESS_TRACKER, DEVICE_PROFILE_WATCH
    );

    /**
     * Check if the package holds the role.
     */
    public static boolean isRoleHolder(@NonNull Context context, @UserIdInt int userId,
            @NonNull String packageName, @NonNull String role) {
        // Check if the role has an alias.
        if (ROLE_ALIASES.containsKey(role)) {
            role = ROLE_ALIASES.get(role);
        }

        final RoleManager roleManager = context.getSystemService(RoleManager.class);
        final List<String> roleHolders = roleManager.getRoleHoldersAsUser(
                role, UserHandle.of(userId));
        return roleHolders.contains(packageName);
    }

    /**
     * Attempt to add the association's companion app as the role holder for the device profile
     * specified in the association. If the association does not have any device profile specified,
     * then the operation will always be successful as a no-op.
     *
     * @param context
     * @param associationInfo the association for which the role should be granted to the app
     * @param roleGrantResult the result callback for adding role holder. True if successful, and
     *                        false if failed. If the association does not have any device profile
     *                        specified, then the operation will always be successful as a no-op.
     */
    public static void addRoleHolderForAssociation(
            @NonNull Context context, @NonNull AssociationInfo associationInfo,
            @NonNull Consumer<Boolean> roleGrantResult) {
        String deviceProfile = associationInfo.getDeviceProfile();
        if (deviceProfile == null) {
            // If no device profile is specified, then no-op and resolve callback with success.
            roleGrantResult.accept(true);
            return;
        }

        // Check if the device profile has an alias.
        if (ROLE_ALIASES.containsKey(deviceProfile)) {
            deviceProfile = ROLE_ALIASES.get(deviceProfile);
        }

        final RoleManager roleManager = context.getSystemService(RoleManager.class);

        final String packageName = associationInfo.getPackageName();
        final int userId = associationInfo.getUserId();
        final UserHandle userHandle = UserHandle.of(userId);

        roleManager.addRoleHolderAsUser(deviceProfile, packageName,
                MANAGE_HOLDERS_FLAG_DONT_KILL_APP, userHandle, context.getMainExecutor(),
                roleGrantResult);
    }

    /**
     * Return true if the role is in use by other active associations.
     */
    public static boolean isRoleInUseByAssociations(
            @NonNull List<AssociationInfo> associations,
            String deviceProfile) {
        if (deviceProfile == null) return false;

        // Consider both cases where the device profile has an alias and where
        // the device profile is an alias of another device profile.
        Set<String> aliasedDeviceProfiles = new HashSet<>();
        aliasedDeviceProfiles.add(deviceProfile);
        for (Map.Entry<String, String> entry : ROLE_ALIASES.entrySet()) {
            if (entry.getKey().equals(deviceProfile)
                    || entry.getValue().equals(deviceProfile)) {
                aliasedDeviceProfiles.add(entry.getKey());
                aliasedDeviceProfiles.add(entry.getValue());
                break;
            }
        }

        return CollectionUtils.any(associations,
                it -> aliasedDeviceProfiles.contains(it.getDeviceProfile()));
    }

    /**
     * Remove the role for the package association.
     */
    public static void removeRoleHolderForAssociation(
            @NonNull Context context, int userId, String packageName, String deviceProfile) {
        if (deviceProfile == null) return;

        // Check if the device profile has an alias.
        final String aliasedDeviceProfile =
                ROLE_ALIASES.getOrDefault(deviceProfile, deviceProfile);

        final RoleManager roleManager = context.getSystemService(RoleManager.class);

        final UserHandle userHandle = UserHandle.of(userId);

        Slog.i(TAG, "Removing CDM role=" + deviceProfile
                + " for userId=" + userId + ", packageName=" + packageName);

        Binder.withCleanCallingIdentity(() ->
            roleManager.removeRoleHolderAsUser(aliasedDeviceProfile, packageName,
                    MANAGE_HOLDERS_FLAG_DONT_KILL_APP, userHandle, context.getMainExecutor(),
                    success -> {
                        if (!success) {
                            Slog.e(TAG, "Failed to remove userId=" + userId + ", packageName="
                                    + packageName + " from the list of " + aliasedDeviceProfile
                                    + " holders.");
                        }
                    })
        );
    }

    /**
     * Get the list of permissions for the profile.
     */
    @NonNull
    public static List<Integer> getPermsForProfile(String profile) {
        if (profile == null) {
            return Collections.emptyList();
        }

        // Check if the device profile has an alias.
        if (ROLE_ALIASES.containsKey(profile)) {
            profile = ROLE_ALIASES.get(profile);
        }

        return PROFILE_PERMISSION_SETS.getOrDefault(profile, Collections.emptyList());
    }

    /**
     * Return true if the device profile is not tied to an Android role.
     */
    public static boolean isRolelessProfile(String deviceProfile) {
        return ROLELESS_DEVICE_PROFILES.contains(deviceProfile);
    }

    private RolesUtils() {}
}
