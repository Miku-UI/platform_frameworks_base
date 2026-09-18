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
import static android.security.trusttoken.TrustTokenManager.VERIFICATION_SUCCESS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.StatsManager;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.trusttoken.ITrustTokenManager;
import android.security.trusttoken.TrustAnchorUnavailableException;
import android.security.trusttoken.TrustConfiguration;
import android.security.trusttoken.TrustToken;
import android.security.trusttoken.TrustTokenManager;
import android.security.trusttoken.TrustTokenUnavailableException;
import android.testing.TestableContext;
import android.util.Base64;
import android.util.Pair;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.StatsLog;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.R;
import com.android.internal.os.Clock;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AtomsProto.Atom;
import com.android.os.framework.AcquireTrustTokenCalled;
import com.android.os.framework.TrustTokenExtensionAtomsProto;
import com.android.os.framework.TrustTokenState;
import com.android.os.framework.TrustTokenType;
import com.android.os.framework.VerifyTrustTokenCalled;

import com.google.protobuf.ExtensionLite;
import com.google.protobuf.ExtensionRegistryLite;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenManagerServiceTest {
    static final byte[] ROOT_KEY_1 = testdata("root_1_public");
    static final byte[] ROOT_KEY_2 = testdata("root_2_public");
    static final TrustConfiguration TRUST_CONFIGURATION_1 =
            new TrustConfiguration.Builder()
                    .addRootKey(ROOT_KEY_1)
                    .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                    .setUpdatedAt(Instant.now())
                    .build();
    static final TrustConfiguration TRUST_CONFIGURATION_2 =
            new TrustConfiguration.Builder()
                    .addRootKey(ROOT_KEY_2)
                    .addIntermediateCertificate(testdata("root_2_intermediate_1"))
                    .setUpdatedAt(Instant.now())
                    .build();
    static final TrustTokenKey TRUST_TOKEN_KEY_1 =
            new TrustTokenKey(
                    testdata("root_1_intermediate_1_token_1_key_public"), "not-needed".getBytes());
    static final byte[] TRUST_TOKEN_1 = testdata("root_1_intermediate_1_token_1");
    static final TrustTokenKey TRUST_TOKEN_KEY_2 =
            new TrustTokenKey(
                    testdata("root_1_intermediate_1_token_2_key_public"), "not-needed".getBytes());
    static final byte[] TRUST_TOKEN_2 = testdata("root_1_intermediate_1_token_2");
    static final byte[] CHALLENGE = testdata("challenge_1");
    static final byte[] CHALLENGE_RESPONSE =
            testdata("root_1_intermediate_1_token_1_challenge_1_response");
    static final TrustTokenBatchAttestation FAKE_ATTESTATION =
            new TrustTokenBatchAttestation(
                    "batch-hash".getBytes(), "signature".getBytes(), List.of());
    private static final String DATABASE_NAME = "trust_token_test";
    private static final String MASTER_KEY_PREFIX = "trust_token_test_";
    static final long SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS =
            TrustTokenManagerService.SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS;

    @Rule
    public TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getContext(), null);

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;

    @Captor
    ArgumentCaptor<OutcomeReceiver<Pair<TrustConfiguration, List<byte[]>>, Throwable>>
            mProviderOutcomeCaptor;

    ExtensionRegistryLite mLogRegistry = ExtensionRegistryLite.newInstance();

    TrustTokenManager mManager;
    TrustTokenSqliteDatabase mDatabase;
    TrustTokenMasterKey mMasterKey;
    TrustTokenManagerService mService;
    TrustTokenManagerInternal mInternal;

    TrustTokenProvider mProvider = mock(TrustTokenProvider.class);
    OutcomeReceiver<Void, Throwable> mCallback = mock(OutcomeReceiver.class);
    Clock mClock = spy(Clock.SYSTEM_CLOCK);

    static byte[] testdata(String path) {
        try {
            return Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(path)
                    .readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    @Before
    public void setUp() {
        mLogRegistry.add(TrustTokenExtensionAtomsProto.acquireTrustTokenCalled);
        mLogRegistry.add(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        mLogRegistry.add(TrustTokenExtensionAtomsProto.trustTokenState);
        mDatabase =
                TrustTokenSqliteDatabase.create(
                        mContext, mContext.getDatabasePath(DATABASE_NAME), mClock);
        mMasterKey = spy(TrustTokenMasterKey.generateMasterKey(MASTER_KEY_PREFIX));
        doReturn(CHALLENGE_RESPONSE)
                .when(mMasterKey)
                .sign(any(TrustTokenKey.class), aryEq(CHALLENGE));
        doReturn(FAKE_ATTESTATION).when(mMasterKey).attest(any());
        doReturn(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_2).when(mMasterKey).generatePerTokenKey();
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mService = new TrustTokenManagerService(mContext, mMasterKey, mDatabase, mClock);
        mManager = new TrustTokenManager(ITrustTokenManager.Stub.asInterface(mService.getBinder()));
        mInternal = mService.getInternal();
    }

    @After
    public void tearDown() {
        mDatabase.close();
        mContext.deleteDatabase(DATABASE_NAME);
        TrustTokenMasterKey.removeFromKeyStore(MASTER_KEY_PREFIX);
    }

    <T> T getLogEvent(ExtensionLite<Atom, T> extension) throws Exception {
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), atLeast(1));
        for (StatsEvent event : mStatsEventCaptor.getAllValues()) {
            Atom atom =
                    StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue(), mLogRegistry);
            assertThat(atom).isNotNull();
            if (atom.hasExtension(extension)) {
                return atom.getExtension(extension);
            }
        }
        throw new AssertionError(extension.toString() + " not found in the log events");
    }

    TrustTokenState pullState() throws Exception {
        var events = new ArrayList<StatsEvent>();
        assertThat(
                        mService.new PullTrustTokenState()
                                .onPullAtom(MetricsLogger.TrustTokenState.ATOM_TAG, events))
                .isEqualTo(StatsManager.PULL_SUCCESS);
        assertThat(events).hasSize(1);
        Atom atom = StatsEventTestUtils.convertToAtom(events.get(0), mLogRegistry);
        assertThat(atom).isNotNull();
        TrustTokenState state = atom.getExtension(TrustTokenExtensionAtomsProto.trustTokenState);
        assertThat(state).isNotNull();
        return state;
    }

    // Sets the mock TrustTokenProvider to succeed with the given result.
    void setProviderResult(TrustConfiguration configuration, List<byte[]> tokens) {
        doAnswer(
                    new Answer<Void>() {
                        public Void answer(InvocationOnMock invocation) {
                            mProviderOutcomeCaptor
                                    .getValue()
                                    .onResult(new Pair(configuration, tokens));
                            return null;
                        }
                    })
                .when(mProvider)
                .requestVerifiedDeviceTokens(any(), any(), mProviderOutcomeCaptor.capture());
    }

    // Sets the mock TrustTokenProvider to fail.
    void setProviderError(Throwable throwable) {
        doAnswer(
                    new Answer<Void>() {
                        public Void answer(InvocationOnMock invocation) {
                            mProviderOutcomeCaptor.getValue().onError(throwable);
                            return null;
                        }
                    })
                .when(mProvider)
                .requestVerifiedDeviceTokens(any(), any(), any());
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_ENABLE_TALISMAN_SERVICE)
    public void acquireVerifiedDeviceToken_success() throws Exception {
        setProviderResult(TRUST_CONFIGURATION_1, List.of());
        mInternal.refillTokens(mProvider, 0, mCallback);
        assertThrows(
                TrustTokenUnavailableException.class,
                () -> mManager.acquireVerifiedDeviceToken(CHALLENGE));
        AcquireTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.acquireTrustTokenCalled);
        assertThat(log.getTokenType()).isEqualTo(TrustTokenType.TRUST_TOKEN_TYPE_DEVICE);
        assertThat(log.getOutcome())
                .isEqualTo(AcquireTrustTokenCalled.Outcome.OUTCOME_TOKEN_EXHAUSTED);

        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        var token = mManager.acquireVerifiedDeviceToken(CHALLENGE);
        log = getLogEvent(TrustTokenExtensionAtomsProto.acquireTrustTokenCalled);
        assertThat(log.getTokenType()).isEqualTo(TrustTokenType.TRUST_TOKEN_TYPE_DEVICE);
        assertThat(log.getOutcome()).isEqualTo(AcquireTrustTokenCalled.Outcome.OUTCOME_SUCCESS);

        assertThrows(
                TrustTokenUnavailableException.class,
                () -> mManager.acquireVerifiedDeviceToken(CHALLENGE));
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_ENABLE_TALISMAN_SERVICE)
    public void verifyTrustTokenAndChallenge_success() throws Exception {
        setProviderResult(TRUST_CONFIGURATION_1, List.of());
        mInternal.refillTokens(mProvider, 0, mCallback);
        assertThat(
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1), CHALLENGE_RESPONSE, CHALLENGE))
                .isEqualTo(VERIFICATION_SUCCESS);
        VerifyTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome()).isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_SUCCESS);
        assertThat(log.getPolicy())
                .isEqualTo(VerifyTrustTokenCalled.Policy.POLICY_VERIFIED_DEVICE_STRONG);
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_ENABLE_TALISMAN_SERVICE)
    public void verifyTrustTokenAndChallenge_errors() throws Exception {
        setProviderResult(TRUST_CONFIGURATION_1, List.of());
        mInternal.refillTokens(mProvider, 0, mCallback);
        assertThat(
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1),
                                CHALLENGE_RESPONSE,
                                "some-other-challenge".getBytes()))
                .isEqualTo(VERIFICATION_FAILURE_CHALLENGE_INCORRECT);
        VerifyTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome())
                .isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_CHALLENGE_INCORRECT);

        setProviderResult(TRUST_CONFIGURATION_2, List.of());
        mInternal.refillTokens(mProvider, 0, mCallback);
        assertThat(
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1), CHALLENGE_RESPONSE, CHALLENGE))
                .isEqualTo(VERIFICATION_FAILURE_SIGNATURE_INVALID);
        log = getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome())
                .isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_SIGNATURE_INVALID);
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_ENABLE_TALISMAN_SERVICE)
    public void verifyTrustTokenAndChallenge_trustConfigurationUnavailable() throws Exception {
        assertThrows(
                TrustAnchorUnavailableException.class,
                () ->
                        mManager.verifyTrustToken(
                                new TrustToken(TRUST_TOKEN_1), "".getBytes(), "".getBytes()));
        VerifyTrustTokenCalled log =
                getLogEvent(TrustTokenExtensionAtomsProto.verifyTrustTokenCalled);
        assertThat(log.getOutcome())
                .isEqualTo(VerifyTrustTokenCalled.Outcome.OUTCOME_ANCHOR_UNAVAILABLE);
    }

    @Test
    public void refillTokens_success() {
        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        verify(mCallback).onResult(any());
    }

    @Test
    public void refillTokens_invalidToken() {
        setProviderResult(TRUST_CONFIGURATION_2, List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        verify(mCallback).onError(any(IllegalArgumentException.class));
    }

    @Test
    public void refillTokens_useDeviceKeyWhenBothTimestampAreRecent() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_1, Base64.DEFAULT)});
        setProviderResult(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addRootKey(ROOT_KEY_2)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(Instant.ofEpochMilli(300000))
                        .build(),
                List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        verify(mCallback).onResult(any());
        TrustTokenState state = pullState();
        assertFalse(state.getAuthorityFallback());
    }

    @Test
    public void refillTokens_useDeviceKeyWhenOnlyDeviceClockIsRecent() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_1, Base64.DEFAULT)});
        setProviderResult(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addRootKey(ROOT_KEY_2)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(
                                Instant.ofEpochMilli(300000 + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS))
                        .build(),
                List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        verify(mCallback).onResult(any());
        TrustTokenState state = pullState();
        assertFalse(state.getAuthorityFallback());
    }

    @Test
    public void refillTokens_useDeviceKeyWhenServerTimeIsRecent() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 1000);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_1, Base64.DEFAULT)});
        setProviderResult(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addRootKey(ROOT_KEY_2)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(Instant.ofEpochMilli(300000))
                        .build(),
                List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        verify(mCallback).onResult(any());
        TrustTokenState state = pullState();
        assertFalse(state.getAuthorityFallback());
    }

    @Test
    public void refillTokens_doNotUseDeviceKey() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(200000L + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.vendor_required_trust_token_roots_timestamp, 100);
        mContext.getOrCreateTestableResources()
                .addOverride(
                        R.array.vendor_required_trust_token_roots,
                        new String[] {Base64.encodeToString(ROOT_KEY_2, Base64.DEFAULT)});
        setProviderResult(
                new TrustConfiguration.Builder()
                        .addRootKey(ROOT_KEY_1)
                        .addIntermediateCertificate(testdata("root_1_intermediate_1"))
                        .setUpdatedAt(
                                Instant.ofEpochMilli(300000 + SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS))
                        .build(),
                List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 1, mCallback);
        verify(mCallback).onResult(any());
        TrustTokenState state = pullState();
        assertTrue(state.getAuthorityFallback());
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_ENABLE_TALISMAN_SERVICE)
    public void refillTokens_keysAndTokensInOrder() {
        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1, TRUST_TOKEN_2));
        mInternal.refillTokens(mProvider, 2, mCallback);
        verify(mCallback).onResult(any());
        var tokens =
                List.of(
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken(),
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken());
        assertThat(tokens)
                .containsExactly(new TrustToken(TRUST_TOKEN_1), new TrustToken(TRUST_TOKEN_2));
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_ENABLE_TALISMAN_SERVICE)
    public void refillTokens_keysAndTokensOutOfOrder() {
        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_2, TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 2, mCallback);
        verify(mCallback).onResult(any());
        var tokens =
                List.of(
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken(),
                        mManager.acquireVerifiedDeviceToken(CHALLENGE).getToken());
        assertThat(tokens)
                .containsExactly(new TrustToken(TRUST_TOKEN_1), new TrustToken(TRUST_TOKEN_2));
    }

    @Test
    public void refillTokens_missingToken() {
        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 2, mCallback);
        verify(mCallback).onError(any(IllegalArgumentException.class));
    }

    @Test
    public void refillTokens_duplicateToken() {
        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1, TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 2, mCallback);
        verify(mCallback).onError(any(IllegalArgumentException.class));
    }

    @Test
    public void refillTokens_duplicateKey() {
        doReturn(TRUST_TOKEN_KEY_1, TRUST_TOKEN_KEY_1).when(mMasterKey).generatePerTokenKey();
        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1, TRUST_TOKEN_1));
        mInternal.refillTokens(mProvider, 2, mCallback);
        verify(mCallback).onError(any(IllegalArgumentException.class));
    }

    @Test
    public void refillTokens_requestSeemsValid() {
        mInternal.refillTokens(mProvider, 2, mCallback);
        verify(mProvider)
                .requestVerifiedDeviceTokens(
                        argThat((keys) -> keys.size() == 2), notNull(), notNull());
        mInternal.refillTokens(mProvider, 3, mCallback);
        verify(mProvider)
                .requestVerifiedDeviceTokens(
                        argThat((keys) -> keys.size() == 3), notNull(), notNull());
    }

    @Test
    public void statsPullCallback() throws Exception {
        TrustTokenState state = pullState();
        assertFalse(state.getHasTrustAnchor());

        setProviderResult(TRUST_CONFIGURATION_1, List.of());
        mInternal.refillTokens(mProvider, 0, mCallback);
        state = pullState();
        assertTrue(state.getHasTrustAnchor());
        assertThat(state.getRootKeyCount()).isEqualTo(1);

        setProviderResult(TRUST_CONFIGURATION_1, List.of(TRUST_TOKEN_1, TRUST_TOKEN_2));
        mInternal.refillTokens(mProvider, 2, mCallback);
        state = pullState();
        assertThat(state.getDeviceTokenCount()).isEqualTo(2);
    }
}
