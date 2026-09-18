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

package com.android.server.security.trusttoken;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnsignedInteger;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenMasterKeyTest {
    // COSE IANA IDs needed for Ed25519 CoseKey
    private static final DataItem KEY_PARAMETER_KEY_TYPE = new UnsignedInteger(1);
    private static final DataItem KEY_TYPE_EC2 = new UnsignedInteger(2);
    private static final DataItem KEY_PARAMETER_CURVE = new NegativeInteger(-1);
    private static final DataItem KEY_PARAMETER_X = new NegativeInteger(-2);
    private static final DataItem CURVE_OKP_ED25519 = new UnsignedInteger(6);

    @Test
    public void generateMasterKey_success() throws Exception {
        TrustTokenMasterKey.generateMasterKey("trust_token-");
    }

    @Test
    public void generatePerTokenKey_keyShouldBeEncrypted() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        TrustTokenKey tokenKey = masterKey.generatePerTokenKey();
        // One should not be able to use the private key directly.
        var keyFactory = KeyFactory.getInstance("Ed25519");
        assertThrows(
                InvalidKeySpecException.class,
                () ->
                        keyFactory.generatePrivate(
                                new PKCS8EncodedKeySpec(tokenKey.getPrivateKey())));
    }

    @Test
    public void sign_success() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        TrustTokenKey tokenKey = masterKey.generatePerTokenKey();
        PublicKey key = decodeCoseKey(tokenKey.getPublicKey());
        byte[] message = "trust_token".getBytes();
        byte[] signature = masterKey.sign(tokenKey, message);
        Signature verifier = Signature.getInstance(key.getAlgorithm());
        verifier.initVerify(key);
        verifier.update(message);
        assertTrue(verifier.verify(signature));
    }

    @Test
    public void sign_unmatched_key() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        var tokenKey =
                new TrustTokenKey(
                        masterKey.generatePerTokenKey().getPublicKey(),
                        masterKey.generatePerTokenKey().getPrivateKey());
        byte[] message = "trust_token".getBytes();
        assertThrows(IllegalArgumentException.class, () -> masterKey.sign(tokenKey, message));
    }

    @Test
    public void sign_different_master_key() throws Exception {
        var masterKey1 = TrustTokenMasterKey.generateMasterKey("trust_token1-");
        var masterKey2 = TrustTokenMasterKey.generateMasterKey("trust_token2-");
        TrustTokenKey tokenKey = masterKey1.generatePerTokenKey();
        byte[] message = "trust_token".getBytes();
        assertThrows(IllegalArgumentException.class, () -> masterKey2.sign(tokenKey, message));
    }

    @Test
    public void attest_success() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        TrustTokenKey tokenKey = masterKey.generatePerTokenKey();
        TrustTokenBatchAttestation attestation = masterKey.attest(Arrays.asList(tokenKey));
        assertThat(attestation.getBatchHash().length).isGreaterThan(0);
        assertThat(attestation.getSignature().length).isGreaterThan(0);
        assertThat(attestation.getCertificates().size()).isAtLeast(2);
        // TODO(b/468195017): Change to Ed25519 once the issue with loading Ed25519 keys is
        // resolved.
        var verifier = Signature.getInstance("SHA256WithECDSA");
        verifier.initVerify(
                attestation.getCertificates().get(attestation.getCertificates().size() - 1));
        verifier.update(attestation.getBatchHash());
        assertThat(verifier.verify(attestation.getSignature())).isTrue();
    }

    @Test
    public void attest_key_not_owned() throws Exception {
        var masterKey1 = TrustTokenMasterKey.generateMasterKey("trust_token1-");
        var masterKey2 = TrustTokenMasterKey.generateMasterKey("trust_token2-");
        TrustTokenKey tokenKey = masterKey1.generatePerTokenKey();
        assertThrows(
                IllegalArgumentException.class, () -> masterKey2.attest(Arrays.asList(tokenKey)));
    }

    private PublicKey decodeCoseKey(byte[] encoded) throws Exception {
        List<DataItem> items;
        try {
            items = CborDecoder.decode(encoded);
        } catch (CborException e) {
            throw new IllegalArgumentException("invalid CBOR: ", e);
        }
        if (items.size() > 1) {
            throw new IllegalArgumentException("the encoded key contains more than one DataItem");
        }
        if (items.get(0).getMajorType() != MajorType.MAP) {
            throw new IllegalArgumentException("invalid CBOR: not a map");
        }
        var coseKey = (Map) items.get(0);
        var keyType = coseKey.get(KEY_PARAMETER_KEY_TYPE);
        var curve = coseKey.get(KEY_PARAMETER_CURVE);
        if (!keyType.equals(KEY_TYPE_EC2) || !curve.equals(CURVE_OKP_ED25519)) {
            throw new IllegalArgumentException(
                    "invalid key: not a Ed25519 key, key_type=" + keyType + ", curve=" + curve);
        }
        var rawKey = coseKey.get(KEY_PARAMETER_X);
        if (rawKey == null || rawKey.getMajorType() != MajorType.BYTE_STRING) {
            throw new IllegalArgumentException("invalid key: invalid parameter");
        }
        return KeyFactory.getInstance("ED25519")
                .generatePublic(new RawEncodedKeySpec(((ByteString) rawKey).getBytes()));
    }
}
