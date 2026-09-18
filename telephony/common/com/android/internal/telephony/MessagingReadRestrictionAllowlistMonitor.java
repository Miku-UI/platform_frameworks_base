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

package com.android.internal.telephony;

import android.annotation.ArrayRes;
import android.annotation.SuppressLint;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.SigningDetails;
import android.content.pm.SignedPackage;
import android.content.pm.UserInfo;
import android.net.Uri;

import android.content.res.Resources.NotFoundException;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Slog;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.android.internal.os.BackgroundThread;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.R;

/**
 * A class that monitors the DeviceConfig key for the read restriction allowlist and grants the
 * {@link AppOpsManager#OP_READ_RESTRICTED_MESSAGES} to the selected applications.
 */
public class MessagingReadRestrictionAllowlistMonitor
    implements DeviceConfig.OnPropertiesChangedListener {
    private static final String TAG = "MessagingReadRestrictionAllowlistMonitor";
    private static final String SIGNED_PACKAGE_SEPARATOR = ",";
    private static final String CERTIFICATE_SEPARATOR = ":";
    private static final String KEY_READ_RESTRICTION_ALLOWLIST
        = "messaging_read_restriction_allowlist";
    private static final String READ_RESTRICTION_ALLOWLIST_FILE_NAME
        = "messaging_read_restriction_allowlist.txt";
    private static final String TELEPHONY_DIR = "telephony";

    private final Executor mBackgroundExecutor;
    private final List<AllowlistProvider> mAllowlistProviders;
    private final ValueStorage mStorage;
    private final Context mContext;
    private Allowlist mAllowlist = null;

    public MessagingReadRestrictionAllowlistMonitor(Context context) {
        this(context, new File(context.getFilesDir(), READ_RESTRICTION_ALLOWLIST_FILE_NAME),
            BackgroundThread.getExecutor());
    }

    @VisibleForTesting
    public MessagingReadRestrictionAllowlistMonitor(Context context, File allowlistFile,
            Executor executor) {
        mContext = context;
        mBackgroundExecutor = executor;
        mStorage = new ValueStorage(allowlistFile);
        mAllowlistProviders = List.of(
            new StaticResourcesAllowlist(context,
                R.array.config_messaging_read_restriction_allowlist),
            new DeviceConfigAllowlist(context, KEY_READ_RESTRICTION_ALLOWLIST));
    }

    /**
     * Interface for providing an allowlist of packages that should be granted with {@link
     * AppOpsManager#OP_READ_RESTRICTED_MESSAGES} app op.
     */
    interface AllowlistProvider {
        Set<AllowlistedPackage> getAllowlistedPackages();
    }

    /**
     * Updates the allowlist by gathering all allowlisted packages from all declared {@link
     * AllowlistProvider} instances, merging them into a single allowlist and updating the AppOps
     * modes for the packages in the allowlist.
     *
     * This method is synchronized to prevent concurrent updates.
     */
    private synchronized void refreshAllowlistAndApplyAppOps() {
        if (mAllowlist == null) {
            initializeAllowlistFromStorage();
        }
        final Allowlist updatedAllowlist = computeAllowlistLocked();
        if (!updateAppOps(mContext, mAllowlist, updatedAllowlist)) {
            Slog.v(TAG, "No AppOps modes were updated. Skipping storage update.");
            return;
        }
        mStorage.writeValue(updatedAllowlist.getEncodedValue());
        mAllowlist = updatedAllowlist;
    }

    /**
     * Initializes the allowlist by reading the allowlist from the storage and updating the AppOps
     * modes for the packages in the allowlist. If the storage is empty or the read operation fails,
     * it will be initialized with an empty allowlist.
     *
     * This method is called only once at the initialization time.
     */
    private void initializeAllowlistFromStorage() {
        Allowlist storedAllowlist = mStorage.readPersistedValue();
        if (storedAllowlist == null) {
            Slog.w(TAG, "Failed to read allowlist from storage. Fallback to empty allowlist.");
            mAllowlist = Allowlist.EMPTY_ALLOWLIST;
            return;
        }
        updateAppOps(mContext, Allowlist.EMPTY_ALLOWLIST, storedAllowlist);
        mAllowlist = storedAllowlist;
    }


    /**
     * Gathers all allowlisted packages from all declared {@link AllowlistProvider}, merges them
     * into a single allowlist and returns the result.
     *
     * This method is not thread-safe and the access to it should be synchronized by the caller.
     */
    public Allowlist computeAllowlistLocked() {
        Set<AllowlistedPackage> mergedPackages = mAllowlistProviders.stream()
                .flatMap(provider -> provider.getAllowlistedPackages().stream())
                .collect(Collectors.toSet());
        return new Allowlist(mergedPackages);
    }

    /**
     * Initializes the allowlist providers and updates the AppOps modes for the packages in the
     * allowlist.
     */
    public void initialize() {
        mBackgroundExecutor.execute(() -> {
            refreshAllowlistAndApplyAppOps();
        });

        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_TELEPHONY,
            mBackgroundExecutor, this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiverAsUser(mPackageReceiver, UserHandle.ALL, filter, null,
            BackgroundThread.getHandler());
    }

    @Override
    public void onPropertiesChanged(DeviceConfig.Properties properties) {
        if (!properties.getKeyset().contains(KEY_READ_RESTRICTION_ALLOWLIST)) {
            return;
        }
        refreshAllowlistAndApplyAppOps();
    }

    /**
     * Receiver to track package changes. If the package is in the allowlist, update the AppOp mode
     * accordingly.
     */
    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (MessagingReadRestrictionAllowlistMonitor.this) {
                final Uri data = intent.getData();
                if (data == null) {
                    Slog.w(TAG, "onBroadcastReceived: data is null. Ignoring the broadcast.");
                    return;
                }
                String packageName = data.getSchemeSpecificPart();
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                onPackageInstalled(packageName, userId);
            }
        }
    };

    /**
     * Handles the broadcast received from the package manager when a package is added or changed.
     * If the package is in the allowlist, update the AppOp mode accordingly.
     */
    @VisibleForTesting
    public void onPackageInstalled(String packageName, int userId) {
        if (mAllowlist == null) {
            // Allowlist is not initialized yet. AppOp will be set at the init time.
            Slog.v(TAG, "onBroadcastReceived: Allowlist is not initialized yet.");
            return;
        }
        List<AppOpChange> appOpChanges = mAllowlist.packages().stream()
            .filter(entry -> entry.packageName.equals(packageName))
            .map(entry -> new AppOpChange(AppOpsManager.MODE_ALLOWED, entry))
            .collect(Collectors.toList());

        if (appOpChanges.isEmpty()) {
            Slog.v(TAG, "onBroadcastReceived: No AppOp changes for package: " + packageName);
            return;
        }
        PackageManager packageManager = mContext.getPackageManager();
        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        appOpChanges.forEach(change -> {
            change.apply(packageManager, appOpsManager, userId);
        });
    }

    /**
     * Class that represents a static allowlist retrieved from the {@link Context} resources.
     * It handles reading the allowlist from the resources using provided {@param mResourceId} key.
     * The static allowlist contains only package names, and accepts any certificate.
     *
     * This allowlist is static and is not updated at runtime.
     */
    private static final class StaticResourcesAllowlist implements AllowlistProvider {
        private final Context mContext;
        private final int mResourceId;
        private Allowlist mAllowlist = null;

        StaticResourcesAllowlist(@NonNull Context context, @ArrayRes int resourceId) {
            mContext = context;
            mResourceId = resourceId;
        }

        @Override
        public Set<AllowlistedPackage> getAllowlistedPackages() {
            if (mAllowlist == null) {
                mAllowlist = readFromResources();
            }
            return mAllowlist.packages();
        }

        private @NonNull Allowlist readFromResources() {
            if (mResourceId <= 0) {
                Slog.w(TAG, "Invalid resource ID for allowlist.");
                return Allowlist.EMPTY_ALLOWLIST;
            }
            List<String> allowlistedPackages = null;
            try {
                allowlistedPackages = Arrays.asList(
                    mContext.getResources().getStringArray(mResourceId));
            } catch (NotFoundException e) {
                Slog.e(TAG, "Failed to read allowlist from resources.");
                return Allowlist.EMPTY_ALLOWLIST;
            }
            return new Allowlist(allowlistedPackages.stream()
                .map(StaticResourcesAllowlist::parseResourceArrayItemEntry)
                .filter(entry -> entry != null)
                .collect(Collectors.toSet()));
        }

        /**
         * Parses a single entry from the resource array. If the entry is invalid, returns {@code
         * null}. If the entry contains a package name only, returns an {@link AllowlistedPackage}
         * with any certificate. If the entry contains a package name and a certificate hash,
         * returns an {@link AllowlistedPackage} with the package name and certificate hash.
         *
         * @param input The entry to parse.
         * @return The parsed entry or {@code null} if the entry is invalid.
         */
        @Nullable
        private static AllowlistedPackage parseResourceArrayItemEntry(@NonNull String input) {
            if (input == null || input.isEmpty()) {
                return null;
            }
            String[] parts = input.split(":");
            if (parts.length > 2) {
                Slog.w(TAG, "Failed to parse allowlisted package: " + input);
                return null;
            }
            if (parts.length == 1) {
                return new AllowlistedPackage(parts[0], AllowlistedPackage.ANY_CERTIFICATE);
            }
            return new AllowlistedPackage(parts[0], parts[1]);
        }
    }

    /**
     * Class that represents a remote allowlist. It handles reading the current state of the
     * allowlist from DeviceConfig.
     */
    private static final class DeviceConfigAllowlist implements AllowlistProvider {
        private final String mDeviceConfigKey;

        DeviceConfigAllowlist(@NonNull Context context, @NonNull String deviceConfigKey) {
            mDeviceConfigKey = deviceConfigKey;
        }

        @Override
        public Set<AllowlistedPackage> getAllowlistedPackages() {
            return readDeviceConfig().packages();
        }

        @SuppressLint("MissingPermission")
        private @NonNull Allowlist readDeviceConfig() {
            try {
                final String value = DeviceConfig.getString(DeviceConfig.NAMESPACE_TELEPHONY,
                        mDeviceConfigKey, null /* defaultValue */);
                Slog.v(TAG, "Value retrieved from DeviceConfig: for key " + mDeviceConfigKey);
                if (value == null) {
                    Slog.w(TAG, "No value found in DeviceConfig for key " + mDeviceConfigKey);
                    return Allowlist.EMPTY_ALLOWLIST;
                }
                return Allowlist.parse(value);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to parse value from DeviceConfig for key "
                        + mDeviceConfigKey);
            }
            return Allowlist.EMPTY_ALLOWLIST;
        }
    }

    /**
     * Updates the AppOps modes for the packages that have changed between the two allowlists.
     *
     * @return True if any AppOps modes were updated, false otherwise.
     */
    private static boolean updateAppOps(Context context, Allowlist storedAllowlist,
            Allowlist updatedAllowlist) {
        final PackageManager packageManager = context.getPackageManager();
        final AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        final UserManager userManager = context.getSystemService(UserManager.class);
        final List<UserInfo> users = userManager.getUsers();

        List<AppOpChange> changes = storedAllowlist.getAppOpChanges(updatedAllowlist);

        for (AppOpChange change : changes) {
            for (UserInfo user : users) {
                change.apply(packageManager, appOpsManager, user.id);
            }
        }
        return !changes.isEmpty();
    }

    private static void setAppOpMode(AppOpsManager appOpsManager, @NonNull String packageName,
            int uid, int mode) {
        try {
            appOpsManager.setMode(AppOpsManager.OP_READ_RESTRICTED_MESSAGES,
                    uid, packageName, mode);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set AppOp mode for " + packageName, e);
        }
    }

    @Nullable
    private static ApplicationInfo getApplicationInfoAsUser(@Nullable String packageName,
            @NonNull PackageManager packageManager, int userId) {
        try {
            return packageManager.getApplicationInfoAsUser(packageName,
                    PackageManager.ApplicationInfoFlags.of(0), userId);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "getApplicationInfoAsUser: Failed to fetch application info for "
                    + packageName);
            return null;
        }
    }

    @Nullable
    private static SigningDetails getSigningDetails(@NonNull String packageName,
            @NonNull PackageManager packageManager, int userId) {
        try {
            final PackageInfo packageInfo = packageManager.getPackageInfoAsUser(packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES, userId);
            if (packageInfo == null || packageInfo.signingInfo == null) {
                return null;
            }
            return packageInfo.signingInfo.getSigningDetails();
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Failed to get package info for " + packageName);
        }
        return null;
    }

    /**
     * Class that handles reading and writing the allowlist to persistent storage. It persists the
     * allowlist to a file in the system directory. The allowlist is written in a format that is
     * parsable by the {@link Allowlist} class - a comma separated list of package names and
     * certificate hashes, e.g. "package1:sha256_cert1,package2:sha256_cert2".
     *
     * The class is not thread-safe and the access to it should be synchronized by the caller.
     */
    private static final class ValueStorage {
        @NonNull
        private final AtomicFile mAtomicFile;

        /**
         * Creates an instance that manages the value at the specified file path.
         *
         * @param file The file to read from and write to.
         */
        ValueStorage(@NonNull File file) {
            mAtomicFile = new AtomicFile(file);
        }

        /**
         * Reads and parses the value from persistent storage.
         *
         * <p>If the file is found to be corrupt during reading or parsing, it will be deleted.
         *
         * This method is not thread-safe and read/write atomicity has to be handled by the
         * caller.
         *
         * @return A list of {@link AllowlistEntry} wrapped in an {@link Allowlist} object if the
         * file exists and is valid, or {@code null}
         */
        @Nullable
        Allowlist readPersistedValue() {
            if (!mAtomicFile.exists()) {
                Slog.w(TAG, "Allowlist file does not exist.");
                return null;
            }

            try {
                byte[] data = mAtomicFile.readFully();
                final String value = new String(data, StandardCharsets.UTF_8);
                return Allowlist.parse(value);
            } catch (Exception e) {
                Slog.e(TAG, "Error reading or parsing allowlist file", e);
                mAtomicFile.delete();
                return null;
            }
        }

        /**
         * Writes the given value to persistent storage atomically.
         *
         * This method is not thread-safe and read/write atomicity has to be handled by the
         * caller.
         *
         * @param value The raw string representation of the allowlist/denylist to be written.
         */
        void writeValue(@NonNull String value) {
            FileOutputStream fos = null;
            try {
                fos = mAtomicFile.startWrite();
                fos.write(value.getBytes(StandardCharsets.UTF_8));
                mAtomicFile.finishWrite(fos);
            } catch (IOException e) {
                Slog.e(TAG, "Error writing allowlist file", e);
                if (fos != null) {
                    mAtomicFile.failWrite(fos);
                }
            }
        }
    }

    record Allowlist(Set<AllowlistedPackage> packages) {
        static final Allowlist EMPTY_ALLOWLIST = new Allowlist(Collections.emptySet());

        static Allowlist parse(String input) {
            if (input == null || input.isEmpty()) {
                return EMPTY_ALLOWLIST;
            }
            return new Allowlist(
                Arrays.stream(input.split(SIGNED_PACKAGE_SEPARATOR))
                    .map(AllowlistedPackage::parse)
                    .collect(Collectors.toSet()));
        }

        String getEncodedValue() {
            return packages.stream().map(AllowlistedPackage::toString)
                    .collect(Collectors.joining(SIGNED_PACKAGE_SEPARATOR));
        }

        List<AppOpChange> getAppOpChanges(@NonNull Allowlist updatedAllowlist) {
            List<AppOpChange> changes = new ArrayList<>();
            Set<AllowlistedPackage> updatedPackages = updatedAllowlist.packages();

            // Identify packages that are no longer allowlisted, set to MODE_DEFAULT
            for (AllowlistedPackage pkg : this.packages) {
                if (!updatedPackages.contains(pkg)) {
                    changes.add(new AppOpChange(AppOpsManager.MODE_DEFAULT, pkg));
                }
            }

            // Identify packages that are newly allowlisted, set to MODE_ALLOWED
            for (AllowlistedPackage pkg : updatedPackages) {
                if (!this.packages.contains(pkg)) {
                    changes.add(new AppOpChange(AppOpsManager.MODE_ALLOWED, pkg));
                }
            }
            return changes;
        }
    }

    /**
     * Class that represents a package that is allowlisted for reading restricted messages.
     * It contains the package name and the sha256 hash of the signature certificate.
     *
     * The wildcard certificate "*" indicates that any certificate is allowed. Using the wildcard
     * certificate is only allowed for system (preinstalled) apps.
     */
    record AllowlistedPackage(String packageName, String sha256Certificate) {
        static final String ANY_CERTIFICATE = "*";

        static AllowlistedPackage parse(String input) {
            String[] parts = input.split(CERTIFICATE_SEPARATOR);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid allowlist entry: " + input);
            }
            return new AllowlistedPackage(parts[0], parts[1]);
        }

        @Override
        public String toString() {
            return packageName + CERTIFICATE_SEPARATOR + sha256Certificate;
        }
    }

    record AppOpChange(@AppOpsManager.Mode int mode, AllowlistedPackage packageEntry) {
        void apply(@NonNull PackageManager packageManager, @NonNull AppOpsManager appOpsManager,
                int userId) {
            final String packageName = packageEntry.packageName();

            int uid;
            try {
                uid = packageManager.getPackageUidAsUser(packageName, userId);
            } catch (PackageManager.NameNotFoundException e) {
                Slog.v(TAG, "No package " + packageName + " found for userId " + userId);
                return;
            }

            if (mode == AppOpsManager.MODE_DEFAULT) {
                Slog.v(TAG, "Setting AppOp mode to default for " + packageName + " userId: "
                    + userId);
                setAppOpMode(appOpsManager, packageName, uid, mode);
            } else if (mode == AppOpsManager.MODE_ALLOWED) {
                // Fetch ApplicationInfo for this user to check system app status
                ApplicationInfo appInfo = getApplicationInfoAsUser(packageName, packageManager,
                        userId);
                if (appInfo == null) {
                    return;
                }
                SigningDetails signingDetails = getSigningDetails(packageName, packageManager,
                        userId);
                if (signingDetails == null) {
                    return;
                }
                if (packageSignatureMatches(packageEntry, signingDetails,
                        isPreInstalledApp(appInfo))) {
                    Slog.v(TAG, "Setting AppOp mode to allowed for " + packageName + ", userId: "
                        + userId);
                    setAppOpMode(appOpsManager, packageName, uid, mode);
                }
            }
        }
    }

    private static boolean packageSignatureMatches(
            @NonNull AllowlistedPackage entry,
            @NonNull SigningDetails signingDetails,
            boolean isPreInstalledApp) {
        // Wildcard certificate is allowed only for preinstalled apps.
        if (Objects.equals(entry.sha256Certificate(), AllowlistedPackage.ANY_CERTIFICATE)) {
            return isPreInstalledApp;
        }
        return signingDetails.hasAncestorOrSelfWithDigest(Set.of(entry.sha256Certificate()));
    }

    private static boolean isPreInstalledApp(@Nullable ApplicationInfo applicationInfo) {
        return applicationInfo != null && applicationInfo.isSystemApp();
    }
}
