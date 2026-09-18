/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.security.trusttoken.TrustConfiguration;
import android.security.trusttoken.TrustTokenRequest;
import android.security.trusttoken.TrustTokenResponse;
import android.security.trusttoken.TrustTokenService;
import android.security.trusttoken.TrustTokenServiceException;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenProviderTest {
    static final Correspondence<byte[], byte[]> BYTE_ARRAY_EQUALS =
            Correspondence.from(Arrays::equals, "equals");
    Context mContext;
    ExecutorService mExecutor;
    TrustTokenProvider mProvider;
    TrustTokenService mService;

    abstract static class MockCallback
            implements OutcomeReceiver<Pair<TrustConfiguration, List<byte[]>>, Throwable> {
        abstract void await();

        public abstract void onResult(Pair<TrustConfiguration, List<byte[]>> result);

        public abstract void onError(Throwable throwable);
    }

    MockCallback createMockCallback() {
        var callback = mock(MockCallback.class);
        var mCalled = new CountDownLatch(1);
        doAnswer(
                        (invocation) -> {
                            mCalled.countDown();
                            return null;
                        })
                .when(callback)
                .onResult(any());
        doAnswer(
                        (invocation) -> {
                            mCalled.countDown();
                            return null;
                        })
                .when(callback)
                .onError(any());
        doAnswer(
                        (invocation) -> {
                            mCalled.await();
                            return null;
                        })
                .when(callback)
                .await();
        return callback;
    }

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mProvider =
                new TrustTokenProvider(
                        mContext,
                        mExecutor,
                        new ComponentName(mContext, MockTrustTokenService.class));
        mService = MockTrustTokenService.MOCK;
        // mService is a singleton, so we need to reset the mock.
        reset();
    }

    @Test(timeout = 5000)
    public void requestVerifiedDeviceTokens_success() throws Exception {
        var key1 =
                new TrustTokenKey("some-public-key-1".getBytes(), "some-private-key-1".getBytes());
        var key2 =
                new TrustTokenKey("some-public-key-2".getBytes(), "some-private-key-2".getBytes());
        var cert = mock(Certificate.class);
        when(cert.getEncoded()).thenReturn("some-attestation".getBytes());
        var attestation =
                new TrustTokenBatchAttestation(
                        "some-batch-hash".getBytes(), "some-signature".getBytes(), List.of(cert));
        doAnswer(
                        invocation -> {
                            var callback = invocation.getArgument(2, OutcomeReceiver.class);
                            callback.onResult(
                                    new TrustTokenResponse.Builder()
                                            .addEncodedToken("some-token".getBytes())
                                            .addEncodedToken("some-other-token".getBytes())
                                            .addRootAuthorityKey("some-key".getBytes())
                                            .addRootAuthorityKey("some-other-key".getBytes())
                                            .addIntermediateCertificate("some-cert".getBytes())
                                            .addIntermediateCertificate(
                                                    "some-other-cert".getBytes())
                                            .build());
                            return null;
                        })
                .when(mService)
                .onRequestTrustTokens(any(), any(), any());

        var callback = createMockCallback();
        mProvider.requestVerifiedDeviceTokens(List.of(key1, key2), attestation, callback);
        callback.await();

        var request = ArgumentCaptor.forClass(TrustTokenRequest.class);
        verify(mService).onRequestTrustTokens(request.capture(), any(), any());
        assertThat(request.getValue().getBatchHash()).isEqualTo("some-batch-hash".getBytes());
        assertThat(request.getValue().getSignature()).isEqualTo("some-signature".getBytes());
        assertThat(request.getValue().getAttestation())
                .comparingElementsUsing(BYTE_ARRAY_EQUALS)
                .containsExactly("some-attestation".getBytes());
        assertThat(request.getValue().getPublicKeys())
                .comparingElementsUsing(BYTE_ARRAY_EQUALS)
                .containsExactly(key1.getPublicKey(), key2.getPublicKey());
        var result = ArgumentCaptor.forClass(Pair.class);
        verify(callback).onResult(result.capture());
        var configuration = (TrustConfiguration) result.getValue().first;
        assertThat(configuration.getRootKeys())
                .comparingElementsUsing(BYTE_ARRAY_EQUALS)
                .containsExactly("some-key".getBytes(), "some-other-key".getBytes());
        assertThat(configuration.getIntermediateCertificates())
                .comparingElementsUsing(BYTE_ARRAY_EQUALS)
                .containsExactly("some-cert".getBytes(), "some-other-cert".getBytes());
        var tokens = (List<byte[]>) result.getValue().second;
        assertThat(tokens)
                .comparingElementsUsing(BYTE_ARRAY_EQUALS)
                .containsExactly("some-token".getBytes(), "some-other-token".getBytes());
    }

    @Test(timeout = 5000)
    public void requestVerifiedDeviceTokens_bindError() throws Exception {
        var key = new TrustTokenKey("some-public-key".getBytes(), "some-private-key".getBytes());
        var cert = mock(Certificate.class);
        when(cert.getEncoded()).thenReturn("some-attestation".getBytes());
        var attestation =
                new TrustTokenBatchAttestation(
                        "some-batch-hash".getBytes(), "some-signature".getBytes(), List.of(cert));
        var provider =
                new TrustTokenProvider(
                        mContext, mExecutor, new ComponentName(mContext, "nonexist.class"));
        var callback = createMockCallback();
        provider.requestVerifiedDeviceTokens(List.of(key), attestation, callback);
        callback.await();

        verify(callback).onError(any(IllegalStateException.class));
    }

    @Test(timeout = 5000)
    public void requestVerifiedDeviceTokens_serviceError() throws Exception {
        var key = new TrustTokenKey("some-public-key".getBytes(), "some-private-key".getBytes());
        var cert = mock(Certificate.class);
        when(cert.getEncoded()).thenReturn("some-attestation".getBytes());
        var attestation =
                new TrustTokenBatchAttestation(
                        "some-batch-hash".getBytes(), "some-signature".getBytes(), List.of(cert));
        doThrow(new IllegalArgumentException(""))
                .when(mService)
                .onRequestTrustTokens(any(), any(), any());

        var callback = createMockCallback();
        mProvider.requestVerifiedDeviceTokens(List.of(key), attestation, callback);
        callback.await();

        verify(callback).onError(any(IllegalArgumentException.class));
    }

    @Test(timeout = 5000)
    public void requestVerifiedDeviceTokens_serverError() throws Exception {
        var key = new TrustTokenKey("some-public-key".getBytes(), "some-private-key".getBytes());
        var cert = mock(Certificate.class);
        when(cert.getEncoded()).thenReturn("some-attestation".getBytes());
        var attestation =
                new TrustTokenBatchAttestation(
                        "some-batch-hash".getBytes(), "some-signature".getBytes(), List.of(cert));
        doAnswer(
                        invocation -> {
                            var callback = invocation.getArgument(2, OutcomeReceiver.class);
                            callback.onError(
                                    new TrustTokenServiceException(
                                            TrustTokenServiceException.ERROR_INTERNAL, ""));
                            return null;
                        })
                .when(mService)
                .onRequestTrustTokens(any(), any(), any());

        var callback = createMockCallback();
        mProvider.requestVerifiedDeviceTokens(List.of(key), attestation, callback);
        callback.await();

        verify(callback).onError(any(TrustTokenProvider.ProviderError.class));
    }
}
