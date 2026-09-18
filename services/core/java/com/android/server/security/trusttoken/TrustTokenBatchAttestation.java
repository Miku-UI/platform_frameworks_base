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

import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Attestation for a batch of TrustToken keys. */
class TrustTokenBatchAttestation {
    private final byte[] mBatchHash;
    private final byte[] mSignature;
    private final List<Certificate> mCertificates;

    TrustTokenBatchAttestation(byte[] batchHash, byte[] signature, List<Certificate> certificates) {
        mBatchHash = Arrays.copyOf(batchHash, batchHash.length);
        mSignature = Arrays.copyOf(signature, signature.length);
        mCertificates = List.copyOf(certificates);
    }

    byte[] getBatchHash() {
        return mBatchHash;
    }

    byte[] getSignature() {
        return mSignature;
    }

    List<Certificate> getCertificates() {
        return mCertificates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustTokenBatchAttestation)) return false;
        TrustTokenBatchAttestation that = (TrustTokenBatchAttestation) o;
        return Arrays.equals(mBatchHash, that.mBatchHash)
                && Arrays.equals(mSignature, that.mSignature)
                && Objects.equals(mCertificates, that.mCertificates);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mCertificates);
        result = 31 * result + Arrays.hashCode(mBatchHash);
        result = 31 * result + Arrays.hashCode(mSignature);
        return result;
    }

    @Override
    public String toString() {
        return "TrustTokenBatchAttestation["
                + "batchHash="
                + Arrays.toString(mBatchHash)
                + ", "
                + "signature="
                + Arrays.toString(mSignature)
                + ", "
                + "certificates="
                + mCertificates
                + "]";
    }
}
