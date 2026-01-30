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

package com.android.server.security.authenticationpolicy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteException;
import android.proximity.IProximityProviderService;
import android.proximity.IProximityResultCallback;
import android.proximity.ProximityResultCode;
import android.proximity.RangingParams;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.biometrics.BiometricHandlerProvider;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * System service for managing watch ranging request.
 *
 * @hide
 */
public class WatchRangingService implements WatchRangingServiceInternal {
    private static final String TAG = "WatchRangingServiceInternal";
    private static final String PROXIMITY_PROVIDER_SERVICE_BIND_INTENT_ACTION =
            "android.proximity.ProximityProviderService";

    private final Context mContext;
    private final String mProximityProviderServicePackageName;
    private final String mProximityProviderServiceClassName;
    private final Function<IBinder, IProximityProviderService> mProximityProviderServiceFunction;
    private final Handler mHandler;
    private ServiceConnection mProximityProviderServiceConnection;
    private CancellationSignalForWatchRanging mCancellationSignalForWatchRanging;
    private IProximityProviderService mProximityProviderService;

    WatchRangingService(@NonNull Context context, @NonNull Function<IBinder,
            IProximityProviderService> proximityProviderServiceFunction, @NonNull Handler handler) {
        mContext = context;
        mHandler = handler;
        mProximityProviderServiceFunction = proximityProviderServiceFunction;
        mProximityProviderServicePackageName = mContext.getString(
                R.string.proximity_provider_service_package_name);
        mProximityProviderServiceClassName = mContext.getString(
                R.string.proximity_provider_service_class_name);

        initializeAndScheduleIdentityCheckWatchRangingLogger(context);
    }

    /**
     * Binds to {@link IProximityProviderService} and starts watch ranging request.
     *
     * @param authenticationRequestId request id for authentication session
     * @param proximityResultCallback callback to receive watch ranging results
     */
    @Override
    public void startWatchRangingForIdentityCheck(long authenticationRequestId,
            @NonNull IProximityResultCallback proximityResultCallback) {
        mHandler.post(() -> {
            if (mCancellationSignalForWatchRanging == null) {
                bindAndStartWatchRanging(authenticationRequestId, proximityResultCallback);
            } else {
                Slog.e(TAG, "Watch ranging requested but previous request was not cancelled."
                        + "This is a bug.");
                cancelWatchRangingForRequestId(0 /* authenticationRequestId */);
                bindAndStartWatchRanging(authenticationRequestId, proximityResultCallback);
            }
        });
    }

    /**
     * Cancels watch ranging request and unbinds from {@link IProximityProviderService}.
     *
     * @param authenticationRequestId request id for authentication session
     */
    @Override
    public void cancelWatchRangingForRequestId(long authenticationRequestId) {
        mHandler.post(() -> {
            if (mCancellationSignalForWatchRanging != null) {
                final long currentAuthenticationRequestId =
                        mCancellationSignalForWatchRanging.getAuthenticationRequestId();
                final boolean requestIdsMatch = authenticationRequestId == 0
                        || currentAuthenticationRequestId == authenticationRequestId;
                if (requestIdsMatch) {
                    try {
                        mCancellationSignalForWatchRanging.cancel();
                    } catch (RemoteException e) {
                        Slog.d(TAG, "Couldn't cancel watch ranging " + e);
                    }
                    mCancellationSignalForWatchRanging = null;
                    unbindProximityProviderService();
                } else {
                    Slog.e(TAG, "Watch ranging cancellation requested "
                            + "but auth request ID does not match");
                }
            }
        });
    }

    @Override
    public void isWatchRangingAvailable(@NonNull IProximityResultCallback proximityResultCallback) {
        mHandler.post(() -> bindAndStartRequest(proximityResultCallback,
                ProximityResultCode.PRIMARY_DEVICE_RANGING_NOT_SUPPORTED,
                proximityProviderService -> {
                    try {
                        final int resultCode =
                                proximityProviderService.isProximityCheckingAvailable();
                        onError(proximityResultCallback, resultCode);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Remote exception thrown when"
                                + "isProximityCheckingAvailable triggered");
                        onError(proximityResultCallback,
                                ProximityResultCode.PRIMARY_DEVICE_RANGING_NOT_SUPPORTED);
                    }
                }));
    }

    private void unbindProximityProviderService() {
        if (mProximityProviderServiceConnection != null) {
            mContext.unbindService(mProximityProviderServiceConnection);
            mProximityProviderServiceConnection = null;
        }
        mProximityProviderService = null;
    }

    private void start() {
        // Expose private service for system components to use.
        LocalServices.addService(WatchRangingServiceInternal.class, this);
    }

    private void bindAndStartRequest(IProximityResultCallback proximityResultCallback,
            int defaultError, Consumer<IProximityProviderService> request) {
        mProximityProviderServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service == null) {
                    Slog.d(TAG, "No service found for proximity provider.");
                    onError(proximityResultCallback, defaultError);
                    return;
                }
                mProximityProviderService = mProximityProviderServiceFunction.apply(service);
                if (mProximityProviderService == null) {
                    Slog.e(TAG, "Proximity provider service is null");
                    return;
                }
                request.accept(mProximityProviderService);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Slog.e(TAG, "Proximity provider service disconnected");
                onError(proximityResultCallback, defaultError);
            }
        };

        final Intent intent = new Intent(PROXIMITY_PROVIDER_SERVICE_BIND_INTENT_ACTION)
                .setClassName(mProximityProviderServicePackageName,
                        mProximityProviderServiceClassName);
        final boolean bindSuccessful =
                mContext.bindService(intent,
                        Context.BIND_IMPORTANT | Context.BIND_AUTO_CREATE /* flags */,
                        new HandlerExecutor(mHandler),
                        mProximityProviderServiceConnection);

        if (!bindSuccessful) {
            Slog.d(TAG, "Couldn't find service for ProximityProviderService");
            onError(proximityResultCallback, defaultError);
        }
    }

    private void bindAndStartWatchRanging(long authenticationRequestId,
            IProximityResultCallback proximityResultCallback) {
        Slog.d(TAG, "Binding to ProximityProviderService and starting watch ranging");
        bindAndStartRequest(proximityResultCallback, ProximityResultCode.NO_ASSOCIATED_DEVICE,
                proximityProviderService -> {
                    final ICancellationSignal cancellationSignal =
                            anyWatchNearby(mProximityProviderService, proximityResultCallback);
                    if (cancellationSignal != null) {
                        mCancellationSignalForWatchRanging =
                                new CancellationSignalForWatchRanging(authenticationRequestId,
                                        cancellationSignal);
                    } else {
                        onError(proximityResultCallback,
                                ProximityResultCode.NO_ASSOCIATED_DEVICE);
                        unbindProximityProviderService();
                    }
                });
    }

    private void onError(IProximityResultCallback proximityResultCallback,
            @ProximityResultCode int errorCode) {
        try {
            proximityResultCallback.onError(errorCode);
        } catch (RemoteException e) {
            Slog.e(TAG, "Remote exception thrown when trying to invoke"
                    + " proximity result callback. Error code: " + errorCode);
        }
    }

    private ICancellationSignal anyWatchNearby(IProximityProviderService proximityProviderService,
            IProximityResultCallback proximityResultCallback) {
        final RangingParams rangingParams = new RangingParams.Builder()
                .build();
        try {
            return proximityProviderService.anyWatchNearby(rangingParams, proximityResultCallback);
        } catch (RemoteException e) {
            Slog.e(TAG, "Remote exception thrown when trying to start watch ranging " + e);
        }
        return null;
    }

    private void initializeAndScheduleIdentityCheckWatchRangingLogger(Context context) {
        final IdentityCheckWatchRangingLogger logger = new IdentityCheckWatchRangingLogger(context,
                mProximityProviderServiceClassName, mProximityProviderServicePackageName, mHandler,
                mProximityProviderServiceFunction);
        logger.registerReceiver();
        logger.scheduleLogger();
    }

    /**
     * System service lifecycle for {@link WatchRangingService}.
     */
    public static final class Lifecycle extends SystemService {
        private final WatchRangingService mService;

        public Lifecycle(@android.annotation.NonNull Context context) {
            super(context);
            mService = new WatchRangingService(context, (service) ->
                    IProximityProviderService.Stub.asInterface(Binder.allowBlocking(service)),
                    BiometricHandlerProvider.getInstance().getBiometricCallbackHandler());
        }

        @Override
        public void onStart() {
            Slog.d(TAG, "Starting WatchRangingService");
            mService.start();
            Slog.d(TAG, "Started WatchRangingService");
        }
    }

    private static final class CancellationSignalForWatchRanging {
        private final long mAuthenticationRequestId;
        private final ICancellationSignal mCancellationSignal;

        CancellationSignalForWatchRanging(long authenticationRequestId,
                @NonNull ICancellationSignal cancellationSignal) {
            mAuthenticationRequestId = authenticationRequestId;
            mCancellationSignal = cancellationSignal;
        }

        public long getAuthenticationRequestId() {
            return mAuthenticationRequestId;
        }

        public void cancel() throws RemoteException {
            Slog.d(TAG, "Cancelling watch ranging for auth request " + mAuthenticationRequestId);
            mCancellationSignal.cancel();
        }
    }
}
