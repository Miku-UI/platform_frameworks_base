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

import static android.security.trusttoken.TrustTokenManager.VERIFICATION_FAILURE_CHALLENGE_INCORRECT;
import static android.security.trusttoken.TrustTokenManager.VERIFICATION_FAILURE_SIGNATURE_INVALID;
import static android.security.trusttoken.TrustTokenManager.VERIFICATION_FAILURE_UNKNOWN;
import static android.security.trusttoken.TrustTokenManager.VERIFICATION_SUCCESS;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresNoPermission;
import android.app.StatsManager;
import android.app.StatsManager.PullAtomMetadata;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PermissionEnforcer;
import android.security.trusttoken.ITrustTokenManager;
import android.security.trusttoken.TrustAnchorUnavailableException;
import android.security.trusttoken.TrustConfiguration;
import android.security.trusttoken.TrustToken;
import android.security.trusttoken.TrustTokenIdentitySet;
import android.security.trusttoken.TrustTokenUnavailableException;
import android.security.trusttoken.TrustTokenWithChallenge;
import android.util.Base64;
import android.util.Pair;
import android.util.Slog;
import android.util.StatsEvent;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.Clock;
import com.android.internal.util.ConcurrentUtils;
import com.android.server.SystemService;

import com.google.android.security.trusttoken.TrustAnchor;
import com.google.android.security.trusttoken.TrustTokenInvalidSignatureException;
import com.google.android.security.trusttoken.TrustTokenPolicy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** A service that manages trust tokens. */
public class TrustTokenManagerService extends SystemService {
    private static final String TAG = "TrustTokenManagerService";
    private static final String DATABASE_NAME = "trust_token";
    private static final String MASTER_KEY_PREFIX = "trust_token_";
    private static final int MAX_TOKEN_NUM_PER_TYPE = 20000;

    @VisibleForTesting
    static final long SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS = Duration.ofDays(183).toMillis();

    private final Context mContext;
    private final TrustTokenDatabase mDatabase;
    private final Clock mClock;
    private final TrustTokenRefreshService.Scheduler mRefreshScheduler;
    private final TrustTokenCleanUpService.Scheduler mCleanUpScheduler;
    private final AtomicReference<TrustTokenMasterKey> mMasterKey = new AtomicReference<>();
    private final Object mMasterKeyInit = new Object();
    private final AtomicReference<TrustAnchor> mTrustAnchor = new AtomicReference<>();
    private final Stub mBinder;
    private boolean mHasProvider = false;
    private boolean mAuthorityFallback = false;
    private int mNumRootKey = 0;

    /**
     * Creates a new instance of {@link TrustTokenManagerService}.
     *
     * @param context The {@link Context} of the service.
     */
    public TrustTokenManagerService(Context context) {
        this(
                context,
                /* masterKey= */ null,
                TrustTokenSqliteDatabase.create(
                        context, context.getDatabasePath(DATABASE_NAME), Clock.SYSTEM_CLOCK),
                Clock.SYSTEM_CLOCK);
    }

    TrustTokenManagerService(
            Context context,
            TrustTokenMasterKey masterKey,
            TrustTokenDatabase database,
            Clock clock) {
        super(context);
        mDatabase = database;
        if (masterKey != null) {
            mMasterKey.set(masterKey);
        }
        mClock = clock;
        mContext = context;
        mBinder = new Stub(context);
        mRefreshScheduler = new TrustTokenRefreshService.Scheduler(context);
        mCleanUpScheduler = new TrustTokenCleanUpService.Scheduler(context);

        try {
            updateTrustAnchor();
        } catch (TrustAnchorUnavailableException | IllegalArgumentException e) {
            // It's intended to be able to create the service when the TrustAnchor is not available.
            // This can happen on freshly installed system. We need to the service in order to
            // update the trust configuration.
            Slog.w(TAG, "failed to update trust anchor upon creation, this may be intended: ", e);
        }
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting TrustTokenManagerService");
        publishBinderService(Context.TRUST_TOKEN_SERVICE, mBinder);
        publishLocalService(TrustTokenManagerInternal.class, mInternal);
    }

    @Override
    public void onBootPhase(@BootPhase int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mHasProvider = TrustTokenProvider.getServiceProvider(getContext()) != null;
            if (mHasProvider) {
                mRefreshScheduler.scheduleRegularRefresh();
                mCleanUpScheduler.scheduleRegularCleanUp();
            }
            var statsManager = mContext.getSystemService(StatsManager.class);
            statsManager.setPullAtomCallback(
                    MetricsLogger.TrustTokenState.ATOM_TAG,
                    new PullAtomMetadata.Builder().setCoolDownMillis(60000).build(),
                    ConcurrentUtils.DIRECT_EXECUTOR,
                    new PullTrustTokenState());
        }
    }

    private void updateTrustAnchor() throws TrustAnchorUnavailableException {
        TrustConfiguration configuration = mDatabase.getTrustConfiguration();
        // Convert to ByteBuffer so that they are compared by the content.
        List<ByteBuffer> rootKeys = new ArrayList<>(configuration.getRootKeys().size());
        for (byte[] key : configuration.getRootKeys()) {
            rootKeys.add(ByteBuffer.wrap(key));
        }
        if (isOnDeviceKeyUpToDate(configuration)) {
            // Use the root keys that are both in the configuration and in the firmware, if the
            // firmware is up-to-date.
            String[] deviceRootKeys =
                    mContext.getResources()
                            .getStringArray(R.array.vendor_required_trust_token_roots);
            if (deviceRootKeys.length > 0) {
                var deviceRootKeySet = new HashSet<>(deviceRootKeys.length);
                for (String key : deviceRootKeys) {
                    deviceRootKeySet.add(ByteBuffer.wrap(Base64.decode(key, Base64.DEFAULT)));
                }
                rootKeys.retainAll(deviceRootKeySet);
            }
            mAuthorityFallback = false;
            Slog.i(TAG, "The number of root keys: " + rootKeys.size());
            if (rootKeys.isEmpty()) {
                Slog.e(
                        TAG,
                        "No common root keys between the device and the configuration."
                                + " TrustTokenService won't function!");
            }
        } else {
            mAuthorityFallback = true;
        }

        mNumRootKey = rootKeys.size();
        var newAnchor =
                new TrustAnchor(
                        rootKeys.stream().map(key -> key.array()).collect(Collectors.toList()),
                        configuration.getIntermediateCertificates());
        TrustAnchor oldAnchor = mTrustAnchor.getAndSet(newAnchor);
        if (oldAnchor != null) {
            oldAnchor.close();
        }
    }

    @NonNull
    private TrustAnchor getTrustAnchor() throws TrustAnchorUnavailableException {
        TrustAnchor anchor = mTrustAnchor.get();
        if (anchor == null) {
            throw new TrustAnchorUnavailableException();
        }
        return anchor;
    }

    @NonNull
    private TrustTokenMasterKey getOrInitMasterKey() {
        TrustTokenMasterKey key = mMasterKey.get();
        if (key != null) {
            return key;
        }
        synchronized (mMasterKeyInit) {
            if (key != null) {
                return key;
            }
            key = TrustTokenMasterKey.fromKeyStore(MASTER_KEY_PREFIX);
            if (key != null) {
                mMasterKey.set(key);
                return key;
            }
            key = TrustTokenMasterKey.generateMasterKey(MASTER_KEY_PREFIX);
            mMasterKey.set(key);
            return key;
        }
    }

    private boolean isOnDeviceKeyUpToDate(TrustConfiguration configuration) {
        int keyTimestamp =
                mContext.getResources()
                                .getInteger(R.integer.vendor_required_trust_token_roots_timestamp)
                        * 1000;
        return Math.min(configuration.getUpdatedAt().toEpochMilli(), mClock.currentTimeMillis())
                        - keyTimestamp
                <= SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS;
    }

    @VisibleForTesting
    Binder getBinder() {
        return mBinder;
    }

    @VisibleForTesting
    TrustTokenManagerInternal getInternal() {
        return mInternal;
    }

    private final class Stub extends ITrustTokenManager.Stub {
        Stub(Context ctx) {
            super(PermissionEnforcer.fromContext(ctx));
        }

        @Override
        @EnforcePermission(
                allOf = {
                    android.Manifest.permission.ACQUIRE_VERIFIED_DEVICE_TOKEN,
                    android.Manifest.permission.SIGN_WITH_TRUST_TOKEN
                })
        public TrustTokenWithChallenge acquireVerifiedDeviceToken(byte[] challenge) {
            acquireVerifiedDeviceToken_enforcePermission();
            var logger =
                    new MetricsLogger.AcquireTokenCalled()
                            .setTokenType(MetricsLogger.AcquireTokenCalled.TYPE_DEVICE);
            TrustTokenSetWithKey setWithKey;
            try {
                setWithKey = mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE);
            } catch (TrustTokenUnavailableException e) {
                logger.setOutcome(MetricsLogger.AcquireTokenCalled.OUTCOME_TOKEN_EXHAUSTED).log();
                if (mHasProvider) {
                    mRefreshScheduler.scheduleUrgentRefresh();
                }
                throw e;
            }
            try (var token =
                    new com.google.android.security.trusttoken.TrustToken(
                            getTrustAnchor(), setWithKey.getTokenSet().getTokenSet())) {
                // No exception means the token is valid.
            } catch (TrustAnchorUnavailableException e) {
                logger.setOutcome(MetricsLogger.AcquireTokenCalled.OUTCOME_ANCHOR_UNAVAILABLE)
                        .log();
                throw e;
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Fetched an invalid token: " + e.toString());
                logger.setOutcome(MetricsLogger.AcquireTokenCalled.OUTCOME_TOKEN_INVALID).log();
                // Since the tokens are fetched in a batch, we are likely to have a large
                // amount of invalid tokens. We return immediately to avoid keeping the
                // client waiting.
                //
                // Note that the database doesn't return expired tokens, so this only
                // happens when something goes wrong.
                throw new TrustTokenUnavailableException(
                        "Cannot acquire valid tokens. Consider the service unavailable.");
            }
            byte[] challengeResponse = getOrInitMasterKey().sign(setWithKey.getKey(), challenge);
            logger.setOutcome(MetricsLogger.AcquireTokenCalled.OUTCOME_SUCCESS).log();
            return new TrustTokenWithChallenge(
                    setWithKey.getTokenSet().asVerifiedDeviceToken(), challengeResponse);
        }

        @RequiresNoPermission
        @Override
        public TrustTokenIdentitySet acquirePreparedIdentitySet(byte[] challenge) {
            // TODO(b/472383812): Implement this method.
            // TODO(b/472383812): Protect with permissions
            Slog.w(TAG, "acquirePreparedIdentitySet is not yet implemented.");
            return null;
        }

        @Override
        @EnforcePermission(android.Manifest.permission.ACQUIRE_VERIFIED_DEVICE_TOKEN)
        public int verifyTrustTokenAndChallenge(
                TrustToken token, byte[] remoteResponse, byte[] expectedChallenge) {
            // TODO(b/418280383): Replace with correct permissions.
            verifyTrustTokenAndChallenge_enforcePermission();
            var logger = new MetricsLogger.VerifyTokenCalled();
            try (var parsedToken =
                    new com.google.android.security.trusttoken.TrustToken(
                            getTrustAnchor(), token.encoded())) {
                var policy = parsedToken.getPolicy();
                logger.setPolicy(policy);
                if (policy != TrustTokenPolicy.VERIFIED_DEVICE
                        && policy != TrustTokenPolicy.VERIFIED_DEVICE_STRONG) {
                    // The method only takes a verified device token. Identity tokens should
                    // be verified with |verifyIdentityTokens| after the device is
                    // authenticated.
                    logger.setOutcome(MetricsLogger.VerifyTokenCalled.OUTCOME_INVALID_POLICY).log();
                    throw new IllegalArgumentException("not a verified device token");
                }
                if (parsedToken.verifyChallenge(expectedChallenge, remoteResponse)) {
                    logger.setOutcome(MetricsLogger.VerifyTokenCalled.OUTCOME_SUCCESS).log();
                    return VERIFICATION_SUCCESS;
                } else {
                    logger.setOutcome(MetricsLogger.VerifyTokenCalled.OUTCOME_CHALLENGE_INCORRECT)
                            .log();
                    return VERIFICATION_FAILURE_CHALLENGE_INCORRECT;
                }
            } catch (TrustTokenInvalidSignatureException e) {
                logger.setOutcome(MetricsLogger.VerifyTokenCalled.OUTCOME_SIGNATURE_INVALID).log();
                Slog.e(TAG, "Trust token signature error: " + e.toString());
                return VERIFICATION_FAILURE_SIGNATURE_INVALID;
            } catch (IllegalArgumentException e) {
                logger.setOutcome(MetricsLogger.VerifyTokenCalled.OUTCOME_FAILURE_UNKNOWN).log();
                Slog.e(TAG, "Failed to verify the trust token: " + e.toString());
                return VERIFICATION_FAILURE_UNKNOWN;
            } catch (TrustAnchorUnavailableException e) {
                logger.setOutcome(MetricsLogger.VerifyTokenCalled.OUTCOME_ANCHOR_UNAVAILABLE).log();
                if (mHasProvider) {
                    mRefreshScheduler.scheduleUrgentRefresh();
                }
                throw e;
            }
        }

        @RequiresNoPermission
        @Override
        public int[] verifyIdentityTokens(
                TrustToken verifiedDeviceToken, TrustToken[] identityTokens) {
            // TODO(b/472383812): Implement this method.
            // TODO(b/472383812): Protect with permissions
            Slog.w(TAG, "verifyIdentityTokens is not yet implemented.");
            int[] results = new int[identityTokens.length];
            Arrays.fill(results, VERIFICATION_FAILURE_UNKNOWN);
            return results;
        }

        @RequiresNoPermission
        @Override
        public void updatePreparedIdentities(List<String> identities) {
            // TODO(b/472383812): Implement this method.
            // TODO(b/472383812): Protect with permissions
            Slog.w(TAG, "updatePreparedIdentities is not yet implemented.");
        }

        @PermissionManuallyEnforced
        @Override
        protected void dump(
                @NonNull FileDescriptor fd, @NonNull PrintWriter fout, @Nullable String[] args) {
            fout.println("hasProvider: " + mHasProvider);
            ComponentName provider = TrustTokenProvider.getServiceProvider(mContext);
            if (provider == null) {
                fout.println("provider: null");
            } else {
                fout.println(
                        "provider: " + provider.getPackageName() + "/" + provider.getClassName());
            }
            fout.println("hasTrustAnchor: " + (mTrustAnchor.get() != null));
            fout.println("authorityFallback: " + mAuthorityFallback);
            fout.println("numRootKey: " + mNumRootKey);
            fout.println(
                    "deviceTokenCounts: "
                            + mDatabase.countTrustTokenSets(TrustTokenSet.TYPE_VERIFIED_DEVICE));
        }

        @PermissionManuallyEnforced
        @Override
        public int handleShellCommand(
                @NonNull ParcelFileDescriptor in,
                @NonNull ParcelFileDescriptor out,
                @NonNull ParcelFileDescriptor err,
                @NonNull String[] args) {
            return new TrustTokenShellCommand(mContext, mInternal)
                    .exec(
                            this,
                            in.getFileDescriptor(),
                            out.getFileDescriptor(),
                            err.getFileDescriptor(),
                            args);
        }
    }

    private final TrustTokenManagerInternal mInternal =
            new TrustTokenManagerInternal() {
                @Override
                public CancellationSignal refillTokens(
                        TrustTokenProvider provider,
                        int num,
                        OutcomeReceiver<Void, Throwable> callback) {
                    List<TrustTokenKey> keys = generateKeys(num);
                    TrustTokenBatchAttestation attestation = attestKeys(keys);
                    return provider.requestVerifiedDeviceTokens(
                            keys,
                            attestation,
                            new OutcomeReceiver<
                                    Pair<TrustConfiguration, List<byte[]>>, Throwable>() {
                                @Override
                                public void onResult(
                                        Pair<TrustConfiguration, List<byte[]>> result) {
                                    try {
                                        updateTrustConfiguration(result.first);
                                        addTrustTokens(keys, result.second);
                                        callback.onResult(null);
                                    } catch (Exception e) {
                                        callback.onError(e);
                                    }
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onError(throwable);
                                }
                            });
                }

                @Override
                public void cleanUpDatabase() {
                    TrustAnchor anchor = getTrustAnchor();
                    mDatabase.cleanUpTrustTokenSets(
                            TrustTokenSet.TYPE_VERIFIED_DEVICE,
                            MAX_TOKEN_NUM_PER_TYPE,
                            (tokenSet) -> {
                                try (var token =
                                        new com.google.android.security.trusttoken.TrustToken(
                                                anchor, tokenSet.getTokenSet())) {

                                    return true;
                                } catch (IllegalArgumentException e) {
                                    return false;
                                }
                            });
                }

                private List<TrustTokenKey> generateKeys(int num) {
                    var keys = new ArrayList<TrustTokenKey>(num);
                    for (int i = 0; i < num; ++i) {
                        keys.add(getOrInitMasterKey().generatePerTokenKey());
                    }
                    return keys;
                }

                private TrustTokenBatchAttestation attestKeys(List<TrustTokenKey> keys) {
                    return getOrInitMasterKey().attest(keys);
                }

                private void addTrustTokens(
                        @NonNull List<TrustTokenKey> keys, @NonNull List<byte[]> tokens)
                        throws TrustAnchorUnavailableException {
                    // We only supported trusted device tokens at the moment, so there should be
                    // 1-to-1 mapping between the keys and the tokens
                    if (keys.size() != tokens.size()) {
                        throw new IllegalArgumentException(
                                "the number of keys and the number of tokens mismatch");
                    }
                    var tokenByKey = new HashMap<ByteBuffer, TrustTokenSet>();
                    TrustAnchor anchor = getTrustAnchor();
                    for (byte[] encoded : tokens) {
                        try (var token =
                                new com.google.android.security.trusttoken.TrustToken(
                                        anchor, encoded)) {
                            if (token.getPolicy() != TrustTokenPolicy.VERIFIED_DEVICE
                                    && token.getPolicy()
                                            != TrustTokenPolicy.VERIFIED_DEVICE_STRONG) {
                                throw new IllegalArgumentException("not a verified device token");
                            }
                            var tokenSet =
                                    new TrustTokenSet.Builder()
                                            .setType(TrustTokenSet.TYPE_VERIFIED_DEVICE)
                                            .setTokenSet(encoded)
                                            .setExpireAt(token.getExpirationTime())
                                            .setCreatedAt(token.getIssuedAt())
                                            .setPublicKey(token.getPublicKey())
                                            .build();
                            if (tokenByKey.putIfAbsent(
                                            ByteBuffer.wrap(tokenSet.getPublicKey()), tokenSet)
                                    != null) {
                                throw new IllegalArgumentException(
                                        "more than one trust device token per key");
                            }
                        }
                    }
                    var validTokens = new ArrayList<TrustTokenSetWithKey>(tokens.size());
                    for (TrustTokenKey key : keys) {
                        TrustTokenSet token =
                                tokenByKey.remove(ByteBuffer.wrap(key.getPublicKey()));
                        if (token == null) {
                            throw new IllegalArgumentException(
                                    "missing token for at least one key");
                        }
                        validTokens.add(new TrustTokenSetWithKey(key, token));
                    }
                    mDatabase.addTrustTokenSets(validTokens);
                }

                private void updateTrustConfiguration(@NonNull TrustConfiguration configuration) {
                    mDatabase.updateTrustConfiguration(configuration);
                    updateTrustAnchor();
                }
            };

    @VisibleForTesting
    class PullTrustTokenState implements StatsManager.StatsPullAtomCallback {
        @Override
        public int onPullAtom(int atomTag, List<StatsEvent> data) {
            if (atomTag != MetricsLogger.TrustTokenState.ATOM_TAG) {
                return StatsManager.PULL_SKIP;
            }
            var state =
                    new MetricsLogger.TrustTokenState()
                            .setHasProvider(mHasProvider)
                            .setHasTrustAnchor(mTrustAnchor.get() != null)
                            .setAuthorityFallback(mAuthorityFallback)
                            .setNumRootKey(mNumRootKey)
                            .setDeviceTokenCounts(
                                    mDatabase.countTrustTokenSets(
                                            TrustTokenSet.TYPE_VERIFIED_DEVICE));
            data.add(state.build());
            return StatsManager.PULL_SUCCESS;
        }
    }
}
