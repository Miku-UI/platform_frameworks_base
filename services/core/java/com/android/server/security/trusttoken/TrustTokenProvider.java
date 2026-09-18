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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.OutcomeReceiver;
import android.security.trusttoken.ITrustTokenCallback;
import android.security.trusttoken.ITrustTokenService;
import android.security.trusttoken.TrustConfiguration;
import android.security.trusttoken.TrustTokenRequest;
import android.security.trusttoken.TrustTokenResponse;
import android.security.trusttoken.TrustTokenService;
import android.util.Pair;
import android.util.Slog;

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** This class wraps the communication with TrustTokenService. */
final class TrustTokenProvider {
    private static final String TAG = "TrustTokenProvider";
    @NonNull private final Context mContext;
    @NonNull private final Executor mExecutor;
    @NonNull private final ComponentName mProvider;

    static ComponentName getServiceProvider(Context context) {
        PackageManager pm = context.getPackageManager();
        ResolveInfo resolved =
                pm.resolveService(
                        new Intent(TrustTokenService.SERVICE_INTERFACE),
                        PackageManager.MATCH_SYSTEM_ONLY);
        if (resolved == null || resolved.serviceInfo == null) {
            return null;
        }
        ServiceInfo si = resolved.serviceInfo;
        if (si.permission == null
                || !si.permission.equals(Manifest.permission.BIND_TRUST_TOKEN_SERVICE)) {
            Slog.e(
                    TAG,
                    String.format(
                            "Service %s should be protected with %s permission, found %s"
                                    + " permission",
                            si.getComponentName(),
                            Manifest.permission.BIND_TRUST_TOKEN_SERVICE,
                            si.permission));
            return null;
        }
        return si.getComponentName();
    }

    static class ProviderError extends RuntimeException {
        private final int mCode;

        private ProviderError(int code) {
            mCode = code;
        }

        int getCode() {
            return mCode;
        }
    }

    TrustTokenProvider(Context context, Executor executor, ComponentName provider) {
        mContext = context;
        mExecutor = executor;
        mProvider = provider;
    }

    CancellationSignal requestVerifiedDeviceTokens(
            List<TrustTokenKey> keys,
            TrustTokenBatchAttestation attestation,
            OutcomeReceiver<Pair<TrustConfiguration, List<byte[]>>, Throwable> callback) {
        var cancellationSignal = new CancellationSignal();
        var request =
                new Request(
                        createRequest(keys, attestation),
                        cancellationSignal,
                        new OutcomeReceiver<TrustTokenResponse, Throwable>() {
                            @Override
                            public void onResult(TrustTokenResponse response) {
                                var configuration =
                                        new TrustConfiguration.Builder()
                                                .setUpdatedAt(response.getUpdateTime());
                                for (byte[] rootKey : response.getRootAuthorityKeys()) {
                                    configuration.addRootKey(rootKey);
                                }
                                for (byte[] cert : response.getIntermediateCertificates()) {
                                    configuration.addIntermediateCertificate(cert);
                                }
                                callback.onResult(
                                        new Pair(
                                                configuration.build(),
                                                response.getEncodedTokens()));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                callback.onError(throwable);
                            }
                        });
        request.start();
        return cancellationSignal;
    }

    private class Request implements ServiceConnection {
        private final TrustTokenRequest mRequest;
        private final CancellationSignal mCancellationSignal;
        private final OutcomeReceiver<TrustTokenResponse, Throwable> mCallback;
        private boolean mStarted = false;

        Request(
                TrustTokenRequest request,
                CancellationSignal cancellationSignal,
                OutcomeReceiver<TrustTokenResponse, Throwable> callback) {
            mRequest = request;
            mCancellationSignal = cancellationSignal;
            mCallback = callback;
        }

        void start() {
            if (mStarted) {
                throw new IllegalStateException("the request is already started");
            }
            mStarted = true;
            var intent = new Intent(TrustTokenService.SERVICE_INTERFACE).setComponent(mProvider);
            if (!mContext.bindService(
                    intent, /* flags= */ Context.BIND_AUTO_CREATE, mExecutor, this)) {
                mCallback.onError(new IllegalStateException("failed to bind TrustTokenService"));
            }
        }

        private void close() {
            mContext.unbindService(this);
        }

        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder binder) {
            var service = ITrustTokenService.Stub.asInterface(binder);
            try {
                service.onRequestTrustTokens(
                        mRequest,
                        new ITrustTokenCallback.Stub() {
                            @Override
                            @RequiresNoPermission
                            public void onSuccess(TrustTokenResponse response) {
                                close();
                                mCallback.onResult(response);
                            }

                            @Override
                            @RequiresNoPermission
                            public void onRemoteCancellationSignal(ICancellationSignal signal) {
                                mCancellationSignal.setRemote(signal);
                            }

                            @Override
                            @RequiresNoPermission
                            public void onFailure(int code) {
                                close();
                                mCallback.onError(new ProviderError(code));
                            }
                        });
            } catch (Exception e) {
                mCallback.onError(e);
            }
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            Slog.e(TAG, "TrustTokenService disconnects");
            // We should be reconnected and re-send the request, so do nothing here.
        }

        @Override
        public void onBindingDied(@NonNull ComponentName name) {
            Slog.e(TAG, "TrustTokenService dies");
            close();
            mCallback.onError(new IllegalStateException("TrustTokenService died"));
        }
    }

    private static TrustTokenRequest createRequest(
            List<TrustTokenKey> keys, TrustTokenBatchAttestation attestation) {
        var certificates = new ArrayList<byte[]>(attestation.getCertificates().size());
        try {
            for (Certificate cert : attestation.getCertificates()) {
                certificates.add(cert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("failed to encode attestations", e);
        }
        return new TrustTokenRequest.Builder()
                .setBatchHash(attestation.getBatchHash())
                .setSignature(attestation.getSignature())
                .setAttestation(certificates)
                .setPublicKeys(
                        keys.stream().map((key) -> key.getPublicKey()).collect(Collectors.toList()))
                .build();
    }
}
