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

package com.android.server.security;

import static android.os.PowerWhitelistManager.REASON_KEY_CHAIN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.security.Flags;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.security.KeyChainException;
import android.security.keymaster.KeymasterCertificateChain;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.ParcelableKeyGenParameterSpec;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Slog;

import com.android.server.DeviceIdleInternal;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * System service for managing KeyChain operations and certificate storage.
 * It distinguishes between device-scoped and user-scoped certificates:
 *
 * <ul>
 *   <li><b>Device-scoped certificates:</b> This service directly manages the storage,
 *       retrieval, and access control for certificates that are shared across all
 *       affiliated users on the device.
 *   <li><b>User-scoped certificates:</b> Operations on traditional user-specific
 *       certificates are delegated to the {@code com.android.keychain} app running
 *       within the respective user profile, via the {@link android.security.IKeyChainService}
 *       interface.
 * </ul>
 *
 * <p>
 * This service also publishes the {@link KeyChainLocalService} interface, allowing
 * other components within the system server to interact with it efficiently without
 * full Binder IPC overhead.
 * <p>
 * This dual responsibility marks a shift in KeyChain architecture, moving core
 * device-level logic into the system server while still leveraging the existing
 * KeyChain app for per-user credential management.
 * <p>
 * With the introduction of background check, PACKAGE_* broadcasts (_ADDED, _REMOVED, _REPLACED)
 * aren't received when the KeyChain app is in the background, which is bad as it uses those to
 * drive internal cleanup.
 * <p>
 *
 * TODO (b/35968281) The long-term goal is to potentially centralize more logic here.
 *
 * @hide
 */
public class KeyChainSystemService extends SystemService {

    private static final String TAG = "KeyChainSystemService";

    private final Context mContext;

    /**
     * Maximum time limit for the KeyChain app to deal with packages being removed.
     */
    private static final int KEYCHAIN_IDLE_ALLOWLIST_DURATION_MS = 30 * 1000;

    private final KeyChainLocalService mLocalService = new KeyChainLocalService(this);

    public KeyChainSystemService(final Context context) {
        super(context);
        mContext = context;
        LocalServices.addService(KeyChainManagerInternal.class, mLocalService);
    }

    @Override
    public void onStart() {
        IntentFilter packageFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        try {
            getContext().registerReceiverAsUser(mPackageReceiver, UserHandle.ALL,
                    packageFilter, null /*broadcastPermission*/, null /*handler*/);
        } catch (RuntimeException e) {
            Slog.w(TAG, "Unable to register for package removed broadcast", e);
        }
    }

    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent broadcastIntent) {
            if (broadcastIntent.getPackage() != null) {
                return;
            }

            try {
                final Intent intent = new Intent(IKeyChainService.class.getName());
                ComponentName service =
                        intent.resolveSystemService(getContext().getPackageManager(), 0 /*flags*/);
                if (service == null) {
                    return;
                }
                intent.setComponent(service);
                intent.setAction(broadcastIntent.getAction());
                startServiceInBackgroundAsUser(intent, UserHandle.of(getSendingUserId()));
            } catch (RuntimeException e) {
                Slog.e(TAG, "Unable to forward package removed broadcast to KeyChain", e);
            }
        }
    };


    private void startServiceInBackgroundAsUser(final Intent intent, final UserHandle user) {
        if (intent.getComponent() == null) {
            return;
        }

        final String packageName = intent.getComponent().getPackageName();
        final DeviceIdleInternal idleController =
                LocalServices.getService(DeviceIdleInternal.class);
        idleController.addPowerSaveTempWhitelistApp(Process.myUid(), packageName,
                KEYCHAIN_IDLE_ALLOWLIST_DURATION_MS, user.getIdentifier(), false,
                REASON_KEY_CHAIN, "keychain");

        getContext().startServiceAsUser(intent, user);
    }

    /**
     * Delegates key pair installation to the per-user KeyChain app.
     *
     * @param userId The target user.
     * @return {@code true} on success.
     */
    public boolean installUserKeyPair(@NonNull byte[] pkcs8PrivateKey, @NonNull byte[] certificate,
            @Nullable byte[][] certificateChain, @NonNull String alias, int userId) {
        // TODO(b/475733339): Implement user-scoped delegation to IKeyChainService
        Slog.i(TAG, "User-scoped installKeyPair not yet implemented.");
        return false;
    }

    /**
     * Installs a key pair into the device-wide keychain.
     *
     * @return {@code true} on success.
     */
    public boolean installDeviceKeyPair(@NonNull byte[] pkcs8PrivateKey,
            @NonNull byte[] certificate, @Nullable byte[][] certificateChain,
            @NonNull String alias) {
        if (!Flags.enableDeviceCertificates()) {
            Slog.w(TAG, "Device-scoped certificate installation requires "
                    + "enable_device_certificates flag.");
            return false;
        }
        // TODO(b/476375280): Implement device-scoped install
        Slog.i(TAG, "Device-scoped installKeyPair not yet implemented.");
        return false;
    }

    /**
     * Delegates key pair generation to the per-user KeyChain app.
     *
     * @param userId The target user.
     * @return The {@link KeymasterCertificateChain} for the generated key pair, or {@code null} if
     *     generation failed.
     */
    public KeymasterCertificateChain generateUserKeyPair(
            @NonNull String algorithm,
            @NonNull KeyGenParameterSpec keySpec,
            int userId) throws KeyChainException {
        return withKeyChainService(
                userId,
                (keyChainService) -> {
                    String alias = keySpec.getKeystoreAlias();

                    final int generationResult =
                            keyChainService.generateKeyPair(
                                    algorithm, new ParcelableKeyGenParameterSpec(keySpec));
                    if (generationResult != KeyChain.KEY_GEN_SUCCESS) {
                        switch (generationResult) {
                            case KeyChain.KEY_GEN_NO_SUCH_ALGORITHM:
                                throw new IllegalArgumentException(
                                        "Invalid algorithm: " + algorithm);
                            case KeyChain.KEY_GEN_INVALID_ALGORITHM_PARAMETERS:
                                throw new IllegalArgumentException(
                                        "Invalid KeyGenParameterSpec for alias: " + alias);
                            case KeyChain.KEY_GEN_STRONGBOX_UNAVAILABLE:
                                throw new StrongBoxUnavailableException(
                                        "StrongBox is not available for key pair generation for: "
                                                + alias);
                            case KeyChain.KEY_GEN_NO_KEYSTORE_PROVIDER:
                                Slog.e(TAG, "Keystore provider not found for alias: " + alias);
                                throw new KeyChainException(
                                        "Internal error: Keystore provider not found.");
                            case KeyChain.KEY_GEN_FAILURE:
                            default:
                                Slog.e(
                                        TAG,
                                        "Key generation failed for alias: "
                                                + alias
                                                + " with code: "
                                                + generationResult);
                                throw new KeyChainException(
                                        "Internal key generation error in KeyChain service.");
                        }
                    }
                    return getCertificateChain(keyChainService, alias);
                });
    }

    /**
     * Generates a key pair directly in the device-wide keychain.
     *
     * @return The {@link KeymasterCertificateChain} for the generated key pair, or {@code null}
     *         if generation failed.
     */
    public KeymasterCertificateChain generateDeviceKeyPair(@NonNull String algorithm,
            @NonNull KeyGenParameterSpec keySpec) {
        if (!Flags.enableDeviceCertificates()) {
            Slog.w(TAG, "Device-scoped certificate generation requires "
                    + "enable_device_certificates flag.");
            return null;
        }
        // TODO(b/476375280): Implement device-scoped generation
        Slog.i(TAG, "Device-scoped generateKeyPair not yet implemented.");
        return null;
    }

    /**
     * Delegates setting the key pair certificate to the per-user KeyChain app.
     *
     * @param userId The target user.
     * @return {@code true} on success.
     */
    public boolean setUserKeyPairCertificate(@NonNull String alias,
            @NonNull byte[] clientCertificate,
            @Nullable byte[] clientCertificateChain, int userId) {
        // TODO(b/475733339): Implement user-scoped delegation to IKeyChainService
        Slog.i(TAG, "User-scoped setKeyPairCertificate not yet implemented.");
        return false;
    }

    /**
     * Sets the certificate for a key pair in the device-wide keychain.
     *
     * @return {@code true} on success.
     */
    public boolean setDeviceKeyPairCertificate(@NonNull String alias,
            @NonNull byte[] clientCertificate,
            @Nullable byte[] clientCertificateChain) {
        if (!Flags.enableDeviceCertificates()) {
            Slog.w(TAG, "Device-scoped certificate setting requires "
                    + "enable_device_certificates flag.");
            return false;
        }
        // TODO(b/476375280): Implement device-scoped operation
        Slog.i(TAG, "Device-scoped setKeyPairCertificate not yet implemented.");
        return false;
    }

    /**
     * Set a grant for the specified UID for an existing user-scoped key pair.
     *
     * @return {@code true} on success.
     */
    public boolean setUserGrant(int uid, @NonNull String alias, int userId, boolean granted) {
        try {
            return withKeyChainService(userId, (keyChainService) -> {
                return keyChainService.setGrant(uid, alias, granted);
            });
        } catch (KeyChainException e) {
            Slog.e(TAG, "Error setting user grant for alias: " + alias, e);
            return false;
        }
    }

    /**
     * Set a grant for the specified UID for an existing device-wide key pair.
     *
     * @return {@code true} on success.
     */
    public boolean setDeviceGrant(int uid, @NonNull String alias, boolean granted) {
        if (!Flags.enableDeviceCertificates()) {
            Slog.w(TAG, "Device-scoped grant setting requires "
                    + "enable_device_certificates flag.");
            return false;
        }
        // TODO(b/476375280): Implement device-scoped operation
        Slog.i(TAG, "Device-scoped setGrant not yet implemented.");
        return false;
    }

    @FunctionalInterface
    private interface KeyChainAction<T> {
        T run(IKeyChainService service) throws RemoteException, KeyChainException;
    }

    private <T> T withKeyChainService(int userId, KeyChainAction<T> action)
            throws KeyChainException {
        UserHandle userHandle = UserHandle.of(userId);
        try (KeyChainConnection keyChainConnection = KeyChain.bindAsUser(mContext, userHandle)) {
            IKeyChainService keyChain = keyChainConnection.getService();
            if (keyChain == null) {
                Slog.e(TAG, "IKeyChainService interface is null for user " + userId);
                throw new KeyChainException(
                        "KeyChainService interface is unexpectedly null for user " + userId);
            }
            return action.run(keyChain);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyChainException("Interrupted during KeyChain service operation.", e);
        } catch (RemoteException e) {
            throw new KeyChainException("Remote exception from KeyChain service.", e);
        }
    }

    private KeymasterCertificateChain getCertificateChain(IKeyChainService keyChain, String alias)
            throws KeyChainException {
        try {
            final List<byte[]> encodedCerts = new ArrayList<>();
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            final byte[] certChainBytes = keyChain.getCaCertificates(alias);
            encodedCerts.add(keyChain.getCertificate(alias));
            if (certChainBytes != null) {
                final Collection<X509Certificate> certs =
                        (Collection<X509Certificate>) certFactory.generateCertificates(
                                new ByteArrayInputStream(certChainBytes));
                for (X509Certificate cert : certs) {
                    encodedCerts.add(cert.getEncoded());
                }
            }

            return new KeymasterCertificateChain(encodedCerts);
        } catch (RemoteException | CertificateException e) {
            Slog.e(TAG, "While retrieving certificate chain.", e);
            throw new KeyChainException("While retrieving certificate chain.", e);
        }
    }
}
