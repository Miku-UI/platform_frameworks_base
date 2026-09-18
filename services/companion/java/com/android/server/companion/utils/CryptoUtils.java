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

package com.android.server.companion.utils;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for Diffie-Hellman cryptographic operations.
 */
public final class CryptoUtils {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private CryptoUtils() {}

    /**
     * Compute salted HKDF extraction taking the input key material to generate a pseudorandom key.
     */
    @NonNull
    public static byte[] hkdfExtract(@Nullable byte[] salt, @NonNull byte[] ikm)
            throws InvalidKeyException, NoSuchAlgorithmException {
        if (ikm == null) {
            throw new IllegalArgumentException("Input key material cannot be null.");
        }
        if (salt == null || salt.length == 0) {
            salt = new byte[Mac.getInstance(HMAC_SHA256).getMacLength()];
        }
        return getMac(salt).doFinal(ikm);
    }

    /**
     * Expand the pseudorandom key into an HMAC of desired length using the message as info.
     */
    @NonNull
    public static byte[] hkdfExpand(@NonNull byte[] prk, @Nullable byte[] info, int length)
            throws InvalidKeyException, NoSuchAlgorithmException {
        if (prk == null) {
            throw new IllegalArgumentException("Pseudorandom key cannot be null.");
        }
        Mac mac = getMac(prk);
        int macLength = mac.getMacLength();

        byte[] t = new byte[0];
        byte[] output = new byte[length];
        int outputOffset = 0;
        byte[] counter = new byte[] { 0x00 };
        while (outputOffset < length) {
            counter[0]++;
            mac.update(t);
            mac.update(info);
            t = mac.doFinal(counter);
            int size = Math.min(macLength, length - outputOffset);
            System.arraycopy(t, 0, output, outputOffset, size);
            outputOffset += size;
        }
        return output;
    }

    private static Mac getMac(byte[] key) throws InvalidKeyException, NoSuchAlgorithmException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_SHA256);
        mac.init(keySpec);
        return mac;
    }
}
