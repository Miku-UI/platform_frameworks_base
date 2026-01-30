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

package android.service.security.talisman;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Parameters for an encrypted identity hash, used to obtain a talisman from an identity provider
 * without revealing the client's private key.
 *
 * <p>This class encapsulates the parameters required for a non-interactive zero-knowledge proof,
 * allowing a device to prove to an identity provider that it is requesting a signature for an
 * encrypted identifier that it rightfully owns, without disclosing the private key used for the
 * encryption. This is based on a combination of the Schnorr signature protocol and the Fiat-Shamir
 * transform.
 *
 * <h3>Protocol Overview</h3>
 *
 * <p>Note: The following protocol description uses additive notation for elliptic curve operations,
 * where multiplication represents scalar multiplication and addition represents point addition.
 *
 * <p>The goal is for a device to obtain a signature from an identity provider over {@code y = H(id)
 * * k}, where:
 *
 * <ul>
 *   <li>{@code id} is an identifier for the device (e.g., a phone number), corresponding to the
 *       identities passed to {@link TalismanManager#getTalismanIdentitySet}.
 *   <li>{@code H} is a cryptographic hash-to-curve function that maps strings to points on an
 *       elliptic curve (see RFC 9380). This is the hash function returned by {@link
 *       #getHashAlgorithm()}.
 *   <li>{@code F} is a cryptographic hash function (e.g., SHA-256 or SHA-384). This is the hash
 *       function returned by {@link #getChallengeHashAlgorithm()}.
 *   <li>{@code k} is a private key known only to the device and controlled by the system.
 * </ul>
 *
 * The identity provider must verify that the device knows the private key {@code k} corresponding
 * to the identifier {@code id} before issuing a signature on {@code y}.
 *
 * <h4>Parameter Generation (Device-side)</h4>
 *
 * <p>The device:
 *
 * <ol>
 *   <li>has a private key {@code k} and an identifier {@code id}. The system controls this key.
 *   <li>generates a random nonce {@code r}.
 *   <li>computes {@code y = H(id) * k}. This is the {@link #getEncryptedIdentityHash()}.
 *   <li>computes a commitment {@code a = H(id) * r}. This is the {@link #getNonceCommitment()}.
 *   <li>computes the challenge {@code c = F(id, H(id), y, a)}. The function {@code F} hashes its
 *       inputs using the hash function returned by {@link #getChallengeHashAlgorithm()}. The
 *       resulting digest is interpreted as an integer and reduced modulo
 *       {@code q}, where {@code q} is the order of the elliptic curve group.
 *   <li>computes the proof {@code p = r + k * c}. This is the {@link #getProof()}.
 *   <li>sends {@code (y, a, p)} to the identity provider. These values correspond to the fields in
 *       this class.
 * </ol>
 *
 * <p>All steps except the last one are performed by the system before constructing this object.
 *
 * <h4>Verification and Signing (Identity Provider-side)</h4>
 *
 * A {@link android.service.security.talisman.TalismanService} acts as the identity provider and
 * should perform the following steps on a web service it has access to:
 *
 * <ol>
 *   <li>Receive the parameters {@code y} (from {@link #getEncryptedIdentityHash()}), {@code a}
 *       (from {@link #getNonceCommitment()}), and {@code p} (from {@link #getProof()}).
 *   <li>The identity provider already knows the client's identities ({@code id}).
 *   <li>Compute the challenge {@code c = F(id, H(id), y, a)} as described above.
 *   <li>Verify the proof by checking if {@code H(id) * p == a + y * c}.
 *   <li>If the verification succeeds, the provider can compute and return a verified identity
 *       talisman over {@code y}. If it fails, the request should be rejected.
 * </ol>
 *
 * @hide
 */
public final class EncryptedIdentityHashParameters implements Parcelable {

    // @SystemApi TODO(b/418280383): Make this visible

    /** @hide */
    @IntDef(
            prefix = {"HASH_ALGORITHM_"},
            value = {
                HASH_ALGORITHM_UNKNOWN,
                HASH_ALGORITHM_EC_HASH_TO_CURVE_P256_XMD_SHA256_SSWU,
                HASH_ALGORITHM_EC_HASH_TO_CURVE_P384_XMD_SHA384_SSWU
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface HashAlgorithm {}

    /** The hash algorithm is unknown. */
    public static final int HASH_ALGORITHM_UNKNOWN = 0;

    /**
     * The hash algorithm is EC hash-to-curve with P-256, XMD:SHA-256, and SSWU.
     *
     * <p>See RFC 9380 for details.
     */
    public static final int HASH_ALGORITHM_EC_HASH_TO_CURVE_P256_XMD_SHA256_SSWU = 1;

    /**
     * The hash algorithm is EC hash-to-curve with P-384, XMD:SHA-384, and SSWU.
     *
     * <p>See RFC 9380 for details.
     */
    public static final int HASH_ALGORITHM_EC_HASH_TO_CURVE_P384_XMD_SHA384_SSWU = 2;

    /** @hide */
    @IntDef(
            prefix = {"CHALLENGE_HASH_ALGORITHM_"},
            value = {
                CHALLENGE_HASH_ALGORITHM_UNKNOWN,
                CHALLENGE_HASH_ALGORITHM_SHA256,
                CHALLENGE_HASH_ALGORITHM_SHA384
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChallengeHashAlgorithm {}

    /** The challenge hash algorithm is unknown. */
    public static final int CHALLENGE_HASH_ALGORITHM_UNKNOWN = 0;

    /** The challenge hash algorithm is SHA-256. */
    public static final int CHALLENGE_HASH_ALGORITHM_SHA256 = 1;

    /** The challenge hash algorithm is SHA-384. */
    public static final int CHALLENGE_HASH_ALGORITHM_SHA384 = 2;

    private final @HashAlgorithm int mHashAlgorithm;
    private final @ChallengeHashAlgorithm int mChallengeHashAlgorithm;
    private final byte[] mEncryptedIdentityHash;
    private final byte[] mNonceCommitment;
    private final byte[] mProof;

    /**
     * Creates a new set of parameters for an encrypted identity hash.
     *
     * @param hashAlgorithm The hash algorithm used.
     * @param challengeHashAlgorithm The hash algorithm used for the challenge.
     * @param encryptedIdentityHash The encrypted identity hash ({@code y}).
     * @param nonceCommitment The commitment to the nonce ({@code a}).
     * @param proof The zero-knowledge proof ({@code p}).
     */
    public EncryptedIdentityHashParameters(
            @HashAlgorithm int hashAlgorithm,
            @ChallengeHashAlgorithm int challengeHashAlgorithm,
            @NonNull byte[] encryptedIdentityHash,
            @NonNull byte[] nonceCommitment,
            @NonNull byte[] proof) {
        mHashAlgorithm = hashAlgorithm;
        mChallengeHashAlgorithm = challengeHashAlgorithm;
        mEncryptedIdentityHash = Objects.requireNonNull(encryptedIdentityHash);
        mNonceCommitment = Objects.requireNonNull(nonceCommitment);
        mProof = Objects.requireNonNull(proof);
    }

    private EncryptedIdentityHashParameters(Parcel in) {
        mHashAlgorithm = in.readInt();
        mChallengeHashAlgorithm = in.readInt();
        mEncryptedIdentityHash = in.createByteArray();
        mNonceCommitment = in.createByteArray();
        mProof = in.createByteArray();
    }

    /**
     * Returns the hash algorithm used.
     *
     * <p>This corresponds to the {@code H} function in the protocol described in the class
     * documentation.
     *
     * @return the hash algorithm.
     */
    @HashAlgorithm
    public int getHashAlgorithm() {
        return mHashAlgorithm;
    }

    /**
     * Returns the hash algorithm used for the challenge.
     *
     * <p>This corresponds to the {@code F} function in the protocol described in the class
     * documentation.
     *
     * @return the challenge hash algorithm.
     */
    @ChallengeHashAlgorithm
    public int getChallengeHashAlgorithm() {
        return mChallengeHashAlgorithm;
    }

    /**
     * Returns the encrypted identity hash.
     *
     * <p>This corresponds to {@code y = H(id) * k} in the protocol described in the class
     * documentation. Upon successful verification, the identity provider will sign a talisman with
     * this value set as the {@code encrypted_identity_hash}.
     *
     * @return the encrypted identity hash.
     */
    @NonNull
    public byte[] getEncryptedIdentityHash() {
        return mEncryptedIdentityHash;
    }

    /**
     * Returns the nonce commitment.
     *
     * <p>This corresponds to {@code a = H(id) * r} in the protocol described in the class
     * documentation.
     *
     * @return the nonce commitment.
     */
    @NonNull
    public byte[] getNonceCommitment() {
        return mNonceCommitment;
    }

    /**
     * Returns the proof of knowledge of the private key.
     *
     * <p>This corresponds to {@code p = r + k * c} in the protocol described in the class
     * documentation.
     *
     * @return the proof.
     */
    @NonNull
    public byte[] getProof() {
        return mProof;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mHashAlgorithm);
        dest.writeInt(mChallengeHashAlgorithm);
        dest.writeByteArray(mEncryptedIdentityHash);
        dest.writeByteArray(mNonceCommitment);
        dest.writeByteArray(mProof);
    }

    @NonNull
    public static final Creator<EncryptedIdentityHashParameters> CREATOR =
            new Creator<EncryptedIdentityHashParameters>() {
                @Override
                public EncryptedIdentityHashParameters createFromParcel(Parcel in) {
                    return new EncryptedIdentityHashParameters(in);
                }

                @Override
                public EncryptedIdentityHashParameters[] newArray(int size) {
                    return new EncryptedIdentityHashParameters[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncryptedIdentityHashParameters)) return false;
        EncryptedIdentityHashParameters that = (EncryptedIdentityHashParameters) o;
        return mHashAlgorithm == that.mHashAlgorithm
                && mChallengeHashAlgorithm == that.mChallengeHashAlgorithm
                && Arrays.equals(mEncryptedIdentityHash, that.mEncryptedIdentityHash)
                && Arrays.equals(mNonceCommitment, that.mNonceCommitment)
                && Arrays.equals(mProof, that.mProof);
    }

    @Override
    public int hashCode() {
        int result = mHashAlgorithm;
        result = 31 * result + mChallengeHashAlgorithm;
        result = 31 * result + Arrays.hashCode(mEncryptedIdentityHash);
        result = 31 * result + Arrays.hashCode(mNonceCommitment);
        result = 31 * result + Arrays.hashCode(mProof);
        return result;
    }
}
