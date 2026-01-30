/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.locksettings;

import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import com.android.internal.util.ArrayUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.server.utils.Slogf;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Helpers for the cryptography related to managing unified passwords for child profiles.
 */
class UnifiedProfilePasswordCrypto {

    private static final String TAG = "UnifiedProfilePasswordCrypto";
    private static final String PROFILE_KEY_NAME_ENCRYPT = "profile_key_name_encrypt_";
    private static final String PROFILE_KEY_NAME_DECRYPT = "profile_key_name_decrypt_";
    private static final int PROFILE_KEY_IV_SIZE = 12;

    private UnifiedProfilePasswordCrypto() {
    }

    /**
     * Decrypts the given byte array using the parent-bound key into a {@link LockscreenCredential}.
     * The input format is expected to match the output of the
     * {@link #encryptProfilePassword(KeyStore, int, long, LockscreenCredential)} method.
     */
    static LockscreenCredential decryptProfilePassword(KeyStore keyStore,
            int userId, byte[] storedData)
            throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, UnrecoverableKeyException, KeyStoreException {
        byte[] iv = Arrays.copyOfRange(storedData, 0, PROFILE_KEY_IV_SIZE);
        byte[] encryptedPassword = Arrays.copyOfRange(storedData, PROFILE_KEY_IV_SIZE,
                storedData.length);
        byte[] decryptionResult;
        SecretKey decryptionKey = (SecretKey) keyStore.getKey(
                profilePasswordDecryptAlias(userId), null);

        Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE);

        cipher.init(Cipher.DECRYPT_MODE, decryptionKey, new GCMParameterSpec(128, iv));
        decryptionResult = cipher.doFinal(encryptedPassword);
        LockscreenCredential credential = LockscreenCredential.createUnifiedProfilePassword(
                decryptionResult);
        ArrayUtils.zeroize(decryptionResult);
        return credential;
    }

    /**
     * Creates a parent-bound key and encrypts the given password with it. The result is a byte
     * array as follows:
     *
     * <pre>
     * <- PROFILE_KEY_IV_SIZE ->
     * [        iv              , ciphertext ]
     * </pre>
     */
    static byte[] encryptProfilePassword(KeyStore keyStore, int profileUserId, long parentSid,
            LockscreenCredential password) {
        final byte[] iv;
        final byte[] ciphertext;
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
            keyGenerator.init(new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            try {
                keyStore.setEntry(
                        profilePasswordEncryptAlias(profileUserId),
                        new KeyStore.SecretKeyEntry(secretKey),
                        new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build());
                keyStore.setEntry(
                        profilePasswordDecryptAlias(profileUserId),
                        new KeyStore.SecretKeyEntry(secretKey),
                        new KeyProtection.Builder(KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setUserAuthenticationRequired(true)
                                .setBoundToSpecificSecureUserId(parentSid)
                                .setUserAuthenticationValidityDurationSeconds(30)
                                .build());
                // Key imported, obtain a reference to it.
                SecretKey keyStoreEncryptionKey = (SecretKey) keyStore.getKey(
                        profilePasswordEncryptAlias(profileUserId), null);
                Cipher cipher = Cipher.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES + "/"
                                + KeyProperties.BLOCK_MODE_GCM + "/"
                                + KeyProperties.ENCRYPTION_PADDING_NONE);
                cipher.init(Cipher.ENCRYPT_MODE, keyStoreEncryptionKey);
                ciphertext = cipher.doFinal(password.getCredential());
                iv = cipher.getIV();
            } finally {
                // The original key can now be discarded.
                keyStore.deleteEntry(profilePasswordEncryptAlias(profileUserId));
            }
        } catch (UnrecoverableKeyException
                 | BadPaddingException | IllegalBlockSizeException | KeyStoreException
                 | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to encrypt key", e);
        }
        if (iv.length != PROFILE_KEY_IV_SIZE) {
            throw new IllegalArgumentException("Invalid iv length: " + iv.length);
        }
        return ArrayUtils.concat(iv, ciphertext);
    }

    // TODO: b/412331826 Add protectorId param
    static String profilePasswordEncryptAlias(int profileUserId) {
        return PROFILE_KEY_NAME_ENCRYPT + profileUserId;
    }

    // TODO: b/412331826 Add protectorId param
    static String profilePasswordDecryptAlias(int profileUserId) {
        return PROFILE_KEY_NAME_DECRYPT + profileUserId;
    }

    /** Cleans up the keystore entries for the profile password's encrypt/decrypt keys. */
    static void removeKeystoreProfileKey(KeyStore keyStore, int targetUserId) {
        final String encryptAlias = profilePasswordEncryptAlias(targetUserId);
        final String decryptAlias = profilePasswordDecryptAlias(targetUserId);
        try {
            if (keyStore.containsAlias(encryptAlias) || keyStore.containsAlias(decryptAlias)) {
                Slogf.i(TAG, "Removing keystore profile key for user %d", targetUserId);
                keyStore.deleteEntry(encryptAlias);
                keyStore.deleteEntry(decryptAlias);
            }
        } catch (KeyStoreException e) {
            // We have tried our best to remove the key.
            Slogf.e(TAG, e, "Error removing keystore profile key for user %d", targetUserId);
        }
    }
}
