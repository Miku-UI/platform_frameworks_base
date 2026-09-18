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

package com.android.server.lskfreset;

import static android.app.lskfreset.flags.Flags.enableLskfResetManager;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Slog;

import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.subtle.Hkdf;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;

public class LskfResetKeyManager {
    private static final String TAG = "LskfResetKeyManagerStub";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final int LOCAL_KEY_SIZE_BYTES = 32;
    private static final int ACCOUNT_KEY_SIZE_BYTES = 32;
    private static final int DEVICE_KEY_SIZE_BYTES = 32;
    private static final int ESCROW_TOKEN_SIZE_BYTES = 32;
    private static final String EC_CURVE_P256 = "secp256r1";
    private static final String USER_ID = "TEST_USER_ID";

    private SecureRandom mSecureRandom = new SecureRandom();

    private HybridEncrypt mGscEncrypter; // Used to encrypt with GSC's public key
    private HybridEncrypt mHsmEncrypter; // Used to encrypt with HSM's public key


    public LskfResetKeyManager(Context context) throws GeneralSecurityException {
        HybridConfig.register();
        KeysetHandle gscWrappingPublicKey = generateRandomEcPublicKeyHandle();
        KeysetHandle hsmWrappingPublicKey = generateRandomEcPublicKeyHandle();
        mGscEncrypter = gscWrappingPublicKey.getPrimitive(HybridEncrypt.class);
        mHsmEncrypter = hsmWrappingPublicKey.getPrimitive(HybridEncrypt.class);
    }


    /**
     * Generates a random Elliptical Curve random key pair
     *
     * @return Returns the public key
     */
    private KeysetHandle generateRandomEcPublicKeyHandle() throws GeneralSecurityException {
        KeysetHandle privateHandle = KeysetHandle.generateNew(
                KeyTemplates.get("ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM"));
        KeysetHandle publicHandle = privateHandle.getPublicKeysetHandle();
        return publicHandle;
    }
    /**
     * Generates a self signed certificate that signs private key with the public key
     *
     * @param privateKey the key that is going to be signed
     * @param publicKey the key that will be used to sign the certificate
     * @return The generated self signed certificate
     */
    @Nullable
    private Certificate generateSelfSignedCertificate(PrivateKey privateKey, PublicKey publicKey)
            throws OperatorCreationException, CertificateException, IOException {
        // Trying to sign the certificate as close as possible to what Android keystore would.
        X500Name name = new X500Name("CN=Android Keystore Key");
        BigInteger serial = BigInteger.ONE;
        Date notBefore = Date.from(Instant.EPOCH);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2048, 0, 1);
        Date notAfter = calendar.getTime();

        X509v3CertificateBuilder certBuilder =
                new X509v3CertificateBuilder(
                        name,
                        serial,
                        notBefore,
                        notAfter,
                        name,
                        SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));

        JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA256withECDSA");

        ContentSigner signer = signerBuilder.build(privateKey);

        // JcaX509CertificateConverter is not ported to Android, but in this case, we can
        // manually send the certificate to X509 CertificateFactory.
        X509CertificateHolder certHolder = certBuilder.build(signer);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate certificate =
                cf.generateCertificate(new ByteArrayInputStream(certHolder.getEncoded()));

        return certificate;
    }

    /**
     * Generates and stores an LSKF reset key.
     *
     * @param keyAlias The alias for the key.
     * @return The generated key as a byte array, or null if generation failed.
     */
    @Nullable
    public byte[] generateAndStoreLskfResetKey(@NonNull String keyAlias) {
        try {
            if (enableLskfResetManager()) {
                // We don't use Android keystore key generation because it won't allow us to access
                // the private keys scalar values. We need the scalar values for the added security
                // reason described below.
                KeyPairGenerator keyPairGenerator =
                        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
                ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE_P256);
                keyPairGenerator.initialize(ecSpec);
                KeyPair destinationShareKey = keyPairGenerator.generateKeyPair();
                KeyPair mediatorShareKey = null;
                ECPrivateKey ecDestinationPrivateKey = null, ecMediatorPrivateKey = null;
                BigInteger p = BigInteger.ZERO;
                // This do-while loop won't be running many times as the chance
                // of getting two keys that their addition is divisible by the
                // EC's prime number is extremely unlikely. The addition of these
                // two keys will be used as a separate key, as a result, we check
                // that the addition mod p is not zero as an extra layer of
                // security.
                do {
                    mediatorShareKey = keyPairGenerator.generateKeyPair();

                    ecDestinationPrivateKey = (ECPrivateKey) destinationShareKey.getPrivate();
                    ecMediatorPrivateKey = (ECPrivateKey) mediatorShareKey.getPrivate();

                    p =
                            ((ECFieldFp) ecDestinationPrivateKey.getParams().getCurve().getField())
                                    .getP();
                } while (ecDestinationPrivateKey
                        .getS()
                        .add(ecMediatorPrivateKey.getS())
                        .mod(p)
                        .equals(BigInteger.ZERO));

                // To insert EC keys into AndroidKeyStore the keys should be signed.
                // When the keys are generated through Android keystore itself, the self signing
                // happens inside the key generation itself.
                Certificate selfSignedDestinationCertificate =
                        generateSelfSignedCertificate(
                                ecDestinationPrivateKey, destinationShareKey.getPublic());
                if (selfSignedDestinationCertificate == null) {
                    return null;
                }
                Certificate selfSignedMediatorCertificate =
                        generateSelfSignedCertificate(
                                ecMediatorPrivateKey, mediatorShareKey.getPublic());
                if (selfSignedMediatorCertificate == null) {
                    return null;
                }
                Certificate[] destinationCertificateChain = {selfSignedDestinationCertificate};
                Certificate[] mediatorCertificateChain = {selfSignedMediatorCertificate};

                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);

                KeyProtection keyProtection =
                        new KeyProtection.Builder(
                                        KeyProperties.PURPOSE_SIGN
                                                | KeyProperties.PURPOSE_VERIFY
                                                | KeyProperties.PURPOSE_AGREE_KEY)
                                .setDigests(
                                        KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                                .build();

                KeyStore.PrivateKeyEntry destinationPrivateKeyEntry =
                        new KeyStore.PrivateKeyEntry(
                                ecDestinationPrivateKey, destinationCertificateChain);

                KeyStore.PrivateKeyEntry mediatorPrivateKeyEntry =
                        new KeyStore.PrivateKeyEntry(
                                ecMediatorPrivateKey, mediatorCertificateChain);

                keyStore.setEntry(
                        keyAlias + "Destination", destinationPrivateKeyEntry, keyProtection);
                keyStore.setEntry(keyAlias + "Mediator", mediatorPrivateKeyEntry, keyProtection);

                return destinationShareKey.getPublic().getEncoded();
            } else {
                return null;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to generate key pair " + keyAlias, e);
            return null;
        }
    }

    /**
     * Generates the hkdf of the two parameters
     *
     * @param localKey the key generated for the device
     * @param userId the unique Id for the user
     *
     * @return returns the kdf of the two parameters
     */
    private byte[] deriveLocalKey(byte[] localKey, String userId) throws GeneralSecurityException {
        byte[] salt = "GSC_LOCAL_KEY_SALT".getBytes(StandardCharsets.UTF_8);
        byte[] info = userId.getBytes(StandardCharsets.UTF_8);
        return Hkdf.computeHkdf("HmacSHA256", localKey, salt, info, LOCAL_KEY_SIZE_BYTES);
    }


    /**
     * Generates random bytes to be used as a secret.
     *
     * @param size refers to the size of the secret in bytes.
     * @return returns the key.
     */
    private byte[] generateRandomBytes(int size) {
        byte[] bytes = new byte[size];
        mSecureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a device and an account key which would be used in recovery service.
     *
     * @param keyAlias The alias for the key.
     * @return Returns whether the key generation was successful.
     */

    public boolean generateDeviceAndAccountKeys(@NonNull String keyAlias) {
        try (var localKey = new AutoZeroedBuffer(generateRandomBytes(LOCAL_KEY_SIZE_BYTES));
                var accountKey = new AutoZeroedBuffer(generateRandomBytes(ACCOUNT_KEY_SIZE_BYTES));
                var derivedLocalKey =
                        new AutoZeroedBuffer(deriveLocalKey(localKey.getBuffer(), USER_ID));
                var wrappedLocalKey =
                        new AutoZeroedBuffer(
                                mGscEncrypter.encrypt(derivedLocalKey.getBuffer(), null));
                var wrappedAccountKey =
                        new AutoZeroedBuffer(mHsmEncrypter.encrypt(accountKey.getBuffer(), null))) {
            // TODO: Save the keys to files/Android KeyStore.
            Slog.i(TAG, "Successfully generated, derived, wrapped key");
            return true;
        } catch (Exception e) {
            Slog.e(TAG, "Key generation/wrapping failed for " +  e);
            return false;
        }
    }

    /**
     * Deletes an LSKF reset key.
     *
     * @param keyAlias The alias of the key to delete.
     */
    public void deleteLskfResetKey(@NonNull String keyAlias) {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias);
                Slog.i(TAG, "Deleted key with alias: " + keyAlias);
            } else {
                Slog.w(TAG, "Key with alias " + keyAlias + " not found for deletion.");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to delete key " + keyAlias, e);
        }
    }

    /**
     * Wrap LskfReset data
     *
     * @param data The data to be encrypted.
     */
    public byte[] wrapData(byte[] data) {
        Slog.d(TAG, "wrapData called (STUB)");
        return null; // Stub
    }

    /**
     * Unwrap LskfReset data
     *
     * @param ivAndEncrypted The data to be unencrypted.
     */
    public byte[] unwrapData(byte[] ivAndEncrypted) {
        Slog.d(TAG, "unwrapData called (STUB)");
        return null; // Stub
    }
}
