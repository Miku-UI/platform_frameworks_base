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

package android.security.talisman;

import android.annotation.CheckResult;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages talismans, which provide a secure and private way for devices to establish mutual trust
 * offline.
 *
 * <p>A talisman is a cryptographically-verifiable claim (a "policy") about a device or an identity.
 * These claims are verified and signed by a "validation service" trusted by the device and packaged
 * into a CBOR Web Token (CWT). The system is designed to be privacy-preserving: talismans prove
 * properties without revealing personally- or device-identifying information. To prevent tracking,
 * the underlying cryptographic keys are rotated periodically.
 *
 * <p>This class allows applications to obtain talismans, use them to prove identity to a peer, and
 * verify talismans received from a peer.
 *
 * <h3>Talisman Types</h3>
 *
 * There are two types of talismans:
 *
 * <ul>
 *   <li><b>Verified Device Talismans:</b> These talismans attest to properties of the device
 *       itself, for example, that it is a genuine Android device running trusted software. They are
 *       anonymous and can be shared with untrusted parties without revealing sensitive information.
 *       Obtained via {@link #acquireVerifiedDeviceTalisman()}.
 *   <li><b>Identity Talismans:</b> These talismans prove ownership of a specific identity (like a
 *       phone number or an account) without revealing the identity itself. They are always issued
 *       in a {@link TalismanIdentitySet}, which cryptographically binds them to a Verified Device
 *       Talisman through a shared public key. The set also includes the secret key that was used to
 *       create the encrypted identity hash present in the identity talismans. Obtained via {@link
 *       #acquirePreparedIdentitySet()} after declaring needs with {@link
 *       #updatePreparedIdentities(List)}.
 * </ul>
 *
 * <h3>Common Usage Flow: Verified Device</h3>
 *
 * <p>A simple challenge-response flow is used to prove device integrity.
 *
 * <h4>On the Prover Device:</h4>
 *
 * <ol>
 *   <li><b>Obtain a talisman:</b> Get a {@link Talisman} using {@link
 *       #acquireVerifiedDeviceTalisman()}
 *   <li><b>Receive a challenge:</b> The verifier will send a unique, random byte array (a "nonce"
 *       or "challenge").
 *   <li><b>Sign the challenge:</b> Use {@link #signChallenge(Talisman, byte[])} to sign the
 *       challenge with the talisman's private key. This proves possession of the key.
 *   <li><b>Send response to verifier:</b> Send the encoded talisman (from {@link
 *       Talisman#encoded()}) and the signature back to the verifier.
 * </ol>
 *
 * <h4>On the Verifier Device:</h4>
 *
 * <ol>
 *   <li><b>Generate and send a challenge:</b> Create a unique, random byte array and send it to the
 *       prover.
 *   <li><b>Receive the response:</b> Get the encoded talisman (as a byte array) and the signature
 *       from the prover.
 *   <li><b>Verify the response:</b> Construct a {@link Talisman} from the encoded bytes and then
 *       use {@link #verifyTalisman(Talisman, byte[], byte[])} with the new object, the signature,
 *       and the original challenge you sent. A successful verification ({@link
 *       #VERIFICATION_SUCCESS}) proves that:
 *       <ul>
 *         <li>The prover possesses the private key for the talisman.
 *         <li>The talisman was signed by a party that this device trusts.
 *       </ul>
 * </ol>
 *
 * <h3>Common Usage Flow: Identity Verification</h3>
 *
 * <p>Verifying an identity is a more complex flow that uses a {@link TalismanIdentitySet} and a
 * protocol to match identities without revealing them.
 *
 * <h4>On the Prover Device:</h4>
 *
 * <ol>
 *   <li><b>Declare needs:</b>As they change, call {@link #updatePreparedIdentities(List)} with all
 *       identities that might be needed. This allows the system to fetch the necessary talismans in
 *       the background.
 *   <li><b>Get the set:</b> When ready to connect, call {@link #acquirePreparedIdentitySet()} to
 *       retrieve the pre-cached set.
 *   <li><b>Authenticate the channel:</b> Use the {@link Talisman} from {@link
 *       TalismanIdentitySet#getVerifiedDeviceTalisman()} to perform the challenge-response flow
 *       described above, establishing a trusted channel with the verifier.
 *   <li><b>Perform identity matching:</b> Use the key from {@link
 *       TalismanIdentitySet#getIdentitySecretKey()} with the identity talismans in a client-side
 *       protocol to securely determine if the verifier is a known contact.
 *   <li><b>Present proof:</b> If the PSI protocol finds a match, send the corresponding {@link
 *       Talisman} for that identity to the verifier over the secure channel.
 * </ol>
 *
 * <h4>On the Verifier Device:</h4>
 *
 * <ol>
 *   <li><b>Authenticate the channel:</b> Perform the verifier role in the challenge-response flow
 *       to establish a trusted channel and get the prover's verified device talisman.
 *   <li><b>Perform identity matching:</b> Participate in the identity matching protocol.
 *   <li><b>Receive proof:</b> After a successful match, receive the prover's identity talisman.
 *   <li><b>Verify the identity talisman:</b> Call {@link #verifyIdentityTalismans(Talisman,
 *       Talisman...)} with the verified device talisman from step 1 and the received identity
 *       talisman. A successful result confirms the identity is cryptographically bound to the
 *       prover's device.
 * </ol>
 *
 * <h3>Resource Management</h3>
 *
 * <p>The system maintains a limited cache of talismans, which are fetched by a {@link
 * android.service.security.talisman.TalismanService}. Fetching new talismans can consume network
 * and battery resources.
 *
 * <p>For {@link TalismanIdentitySet}s, clients <b>should</b> declare their needs ahead of time
 * using {@link #updatePreparedIdentities(List)}. This allows the system to efficiently manage the
 * cache and ensure talismans are available when requested via {@link
 * #acquirePreparedIdentitySet()}.
 *
 * <p>Anonymous "verified device" talismans are managed separately by the system, and {@link
 * #acquireVerifiedDeviceTalisman()} can be called as needed without prior declaration.
 *
 * @hide
 */
@SystemService(Context.TALISMAN_SERVICE)
public class TalismanManager {
    private final ITalismanManager mService;

    /** Verification was successful. */
    public static final int VERIFICATION_SUCCESS = 0;

    /** Verification failed because the signature was invalid. */
    public static final int VERIFICATION_FAILURE_SIGNATURE_INVALID = 1;

    /** Verification failed because the challenge was incorrect. */
    public static final int VERIFICATION_FAILURE_CHALLENGE_INCORRECT = 2;

    /** Verification failed for an unknown reason. */
    public static final int VERIFICATION_FAILURE_UNKNOWN = -1;

    /** @hide */
    @IntDef(
            prefix = {"VERIFICATION_"},
            value = {
                VERIFICATION_SUCCESS,
                VERIFICATION_FAILURE_SIGNATURE_INVALID,
                VERIFICATION_FAILURE_CHALLENGE_INCORRECT,
                VERIFICATION_FAILURE_UNKNOWN
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VerificationResult {}

    /** @hide */
    public TalismanManager(ITalismanManager service) {
        mService = service;
    }

    /**
     * Consumes and returns a verified device talisman from the system's cache.
     *
     * <p>This talisman is anonymous and can be treated with the same care as a public key.
     *
     * <p>The system maintains a limited number of talismans and must use the internet & system
     * battery to refresh the cache. Therefore, clients should only request talismans that they will
     * use imminently.
     */
    public Talisman acquireVerifiedDeviceTalisman() {
        try {
            return mService.acquireVerifiedDeviceTalisman();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Consumes and returns a {@link TalismanIdentitySet} from the system's cache.
     *
     * <p>The system maintains a limited number of talismans and must use the internet & system
     * battery to refresh the cache. Therefore, clients should only request talismans that they will
     * use imminently.
     */
    public TalismanIdentitySet acquirePreparedIdentitySet() {
        try {
            return mService.acquirePreparedIdentitySet();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Updates the set of identities for which talismans should be kept ready.
     *
     * <p>The system uses this list to continuously and proactively fetch and cache {@link
     * TalismanIdentitySet}s in the background. This helps ensure that when {@link
     * #acquirePreparedIdentitySet()} is called, the required talismans are already available
     * without a long wait.
     *
     * <p>Clients only need to call this method when the set of required identities changes. Each
     * call replaces the previous list. The list of identities should be comprehensive, as any
     * identities not in the last-provided list may not have talismans cached. Calling this method
     * with an empty list will clear the set and stop background preparation.
     *
     * @param identities A list of identity strings (e.g., phone numbers, email addresses) that the
     *     client will need talismans for.
     */
    public void updatePreparedIdentities(@NonNull List<String> identities) {
        try {
            mService.updatePreparedIdentities(identities);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Signs the given challenge with the talisman's private key.
     *
     * <p>This proves possession of the private key corresponding to the public key in the talisman.
     * This operation requires the {@link android.Manifest.permission#SIGN_WITH_TALISMAN}
     * permission.
     *
     * @param talisman the talisman to sign with.
     * @param challenge the challenge to sign.
     * @return the signature.
     */
    @NonNull
    // @RequiresPermission(android.Manifest.permission.SIGN_WITH_TALISMAN)
    public byte[] signChallenge(Talisman talisman, byte[] challenge) {
        throw new UnsupportedOperationException("Signing not implemented.");
    }

    /**
     * Verifies the talisman is trusted by this device & that the remote response is a signature of
     * expected challenge by the talisman's secret key.
     *
     * <p>The {@code expectedChallenge} should be a nonce generated by this device and sent to the
     * remote party, or a value derived from the secure channel's connection parameters (e.g., a
     * hash of the session key). The remote party must sign this challenge with the secret key
     * corresponding to the talisman's public key. That signature is provided as the {@code
     * remoteResponse}.
     *
     * @return a {@link VerificationResult} code.
     */
    @CheckResult
    @VerificationResult
    public int verifyTalisman(
            @NonNull Talisman talisman,
            @NonNull byte[] remoteResponse,
            @NonNull byte[] expectedChallenge) {
        try {
            return mService.verifyTalismanAndChallenge(talisman, remoteResponse, expectedChallenge);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Verifies identity talismans against a previously-verified device talisman.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Verifies that the identity talisman itself is trusted by this device.
     *   <li>Verifies that the public key of the identity talisman matches the public key of the
     *       already-verified device talisman.
     * </ol>
     *
     * <p>This does <b>not</b> verify that the issuer of an identity talisman is the same as the
     * issuer of the verified device talisman or the other identity talismans.
     *
     * <p>This method MUST only be called after the caller has already verified the {@code
     * verifiedDeviceTalisman} using {@link #verifyTalisman(Talisman, byte[], byte[])}.
     *
     * @param verifiedDeviceTalisman The device talisman that has already been verified.
     * @param identityTalismans A list of identity talismans to verify against the device talisman.
     * @return a list of {@link VerificationResult} codes, one for each identity talisman provided.
     */
    @CheckResult
    @NonNull
    public List<Integer> verifyIdentityTalismans(
            @NonNull Talisman verifiedDeviceTalisman, @NonNull Talisman... identityTalismans) {
        try {
            int[] results =
                    mService.verifyIdentityTalismans(verifiedDeviceTalisman, identityTalismans);
            List<Integer> resultList = new ArrayList<>(results.length);
            for (int result : results) {
                resultList.add(result);
            }
            return resultList;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
