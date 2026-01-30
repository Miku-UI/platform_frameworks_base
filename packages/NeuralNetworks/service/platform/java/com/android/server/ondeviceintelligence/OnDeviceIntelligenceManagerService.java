/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ondeviceintelligence;

import static android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService.MODEL_LOADED_BUNDLE_KEY;
import static android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService.MODEL_UNLOADED_BUNDLE_KEY;
import static android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService.MODEL_LOADED_BROADCAST_INTENT;
import static android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService.MODEL_UNLOADED_BROADCAST_INTENT;
import static android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService.REGISTER_MODEL_UPDATE_CALLBACK_BUNDLE_KEY;

import static com.android.server.ondeviceintelligence.BundleUtil.sanitizeInferenceParams;
import static com.android.server.ondeviceintelligence.BundleUtil.sanitizeStateParams;
import static com.android.server.ondeviceintelligence.BundleUtil.wrapWithValidation;


import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.AppGlobals;
import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.FeatureDetails;
import android.app.ondeviceintelligence.IDownloadCallback;
import android.app.ondeviceintelligence.IFeatureCallback;
import android.app.ondeviceintelligence.IFeatureDetailsCallback;
import android.app.ondeviceintelligence.IListFeaturesCallback;
import android.app.ondeviceintelligence.IOnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.IProcessingSignal;
import android.app.ondeviceintelligence.IResponseCallback;
import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.ITokenInfoCallback;
import android.app.ondeviceintelligence.ILifecycleListener;
import android.app.ondeviceintelligence.InferenceInfo;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.ondeviceintelligence.IOnDeviceIntelligenceService;
import android.service.ondeviceintelligence.IOnDeviceSandboxedInferenceService;
import android.service.ondeviceintelligence.IProcessingUpdateStatusCallback;
import android.service.ondeviceintelligence.IRemoteProcessingService;
import android.service.ondeviceintelligence.IRemoteStorageService;
import android.service.ondeviceintelligence.OnDeviceIntelligenceService;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.DumpUtils;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.ondeviceintelligence.callbacks.ListenableDownloadCallback;
import com.android.server.ondeviceintelligence.executors.InferenceServiceExecutor;
import com.android.server.ondeviceintelligence.executors.IntelligenceServiceExecutor;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is the system service for handling calls on the
 * {@link android.app.ondeviceintelligence.OnDeviceIntelligenceManager}. This
 * service holds connection references to the underlying remote services i.e. the isolated
 * service {@link OnDeviceSandboxedInferenceService} and a regular
 * service counter part {@link OnDeviceIntelligenceService}.
 *
 * <p>Note: Both the remote services run under the SYSTEM user, as we cannot have separate
 * instance of the Inference service for each user, due to possible high memory footprint.
 *
 * @hide
 */
public class OnDeviceIntelligenceManagerService extends SystemService {
    private static OnDeviceIntelligenceManagerService sInstance;

    public static final String TAG = OnDeviceIntelligenceManagerService.class.getSimpleName();
    private static final String KEY_SERVICE_ENABLED = "service_enabled";

    /** Handler message to {@link #resetTemporaryServices()} */
    private static final int MSG_RESET_TEMPORARY_SERVICE = 0;
    /** Handler message to clean up temporary broadcast keys. */
    private static final int MSG_RESET_BROADCAST_KEYS = 1;
    /** Handler message to clean up temporary config namespace. */
    private static final int MSG_RESET_CONFIG_NAMESPACE = 2;

    /** Default value in absence of {@link DeviceConfig} override. */
    private static final boolean DEFAULT_SERVICE_ENABLED = true;
    private static final String NAMESPACE_ON_DEVICE_INTELLIGENCE = "ondeviceintelligence";

    private static final String SYSTEM_PACKAGE = "android";
    private static final long MAX_AGE_MS = TimeUnit.HOURS.toMillis(3);

    private final Executor resourceClosingExecutor = Executors.newCachedThreadPool();
    private final Executor callbackExecutor = Executors.newCachedThreadPool();
    private final Executor broadcastExecutor = Executors.newCachedThreadPool();
    private final Executor mConfigExecutor = Executors.newCachedThreadPool();
    /** Executor to run lifecycle event broadcasts sequentially. */
    private final Executor mLifecycleExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "odi-lifecycle-broadcast"));


    private final Context mContext;
    protected final Object mLock = new Object();

    private final InferenceInfoStore mInferenceInfoStore;
    private RemoteOnDeviceSandboxedInferenceService mRemoteInferenceService;
    private RemoteOnDeviceIntelligenceService mRemoteOnDeviceIntelligenceService;
    volatile boolean mIsServiceEnabled;

    @GuardedBy("mLock")
    private int remoteInferenceServiceUid = -1;

    @GuardedBy("mLock")
    private String[] mTemporaryServiceNames;
    @GuardedBy("mLock")
    private String[] mTemporaryBroadcastKeys;
    @GuardedBy("mLock")
    private String mBroadcastPackageName = SYSTEM_PACKAGE;
    @GuardedBy("mLock")
    private String mTemporaryConfigNamespace;

    private final DeviceConfig.OnPropertiesChangedListener mOnPropertiesChangedListener =
            this::sendUpdatedConfig;

    private final RemoteCallbackList<ILifecycleListener> mLifecycleListeners =
            new RemoteCallbackList.Builder<ILifecycleListener>(
                    RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL)
                    .setExecutor(callbackExecutor).build();
    private final ILifecycleListener mSystemLifecycleListener = new ILifecycleListener.Stub() {
        @Override
        public void onLifecycleEvent(int event, Feature feature) {
            // Broadcasts are sent on a dedicated single-threaded executor to ensure ordering.
            // This ensures that if onLifecycleEvent is called for multiple events, they are
            // broadcast to clients in the same order.
            // TODO - b/427938935: Add coverage for this logic.
            mLifecycleExecutor.execute(() -> {
                mLifecycleListeners.broadcast(listener -> {
                    try {
                        Slog.d(TAG, "Invoking onLifecycleEvent from system-server.");
                        listener.onLifecycleEvent(event, feature);
                    } catch (RemoteException e) {
                        Slog.d(TAG, "RemoteException when invoking onLifecycleEvent", e);
                    }
                });
            });
        }
    };

    /**
     * Handler used to reset the temporary service names.
     */
    private Handler mTemporaryHandler;
    private final @NonNull Handler mMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Returns the singleton instance of {@link OnDeviceIntelligenceManagerService}.
     */
    public static OnDeviceIntelligenceManagerService getInstance() {
        Objects.requireNonNull(sInstance, "OnDeviceIntelligenceManagerService is not available.");
        return sInstance;
    }

    public OnDeviceIntelligenceManagerService(Context context) {
        super(context);
        mContext = context;
        sInstance = this;
        mTemporaryServiceNames = new String[0];
        mInferenceInfoStore = new InferenceInfoStore(MAX_AGE_MS);
    }

    @Override
    public void onStart() {
        publishBinderService(
                Context.ON_DEVICE_INTELLIGENCE_SERVICE,
                getOnDeviceIntelligenceManagerService(), /* allowIsolated = */ true);
        LocalManagerRegistry.addManager(OnDeviceIntelligenceManagerLocal.class,
                this::getRemoteInferenceServiceUid);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            DeviceConfig.addOnPropertiesChangedListener(
                    NAMESPACE_ON_DEVICE_INTELLIGENCE,
                    BackgroundThread.getExecutor(),
                    (properties) -> onDeviceConfigChange(properties.getKeyset()));
            mIsServiceEnabled = isServiceEnabled();
        }
    }

    private void onDeviceConfigChange(@NonNull Set<String> keys) {
        if (keys.contains(KEY_SERVICE_ENABLED)) {
            mIsServiceEnabled = isServiceEnabled();
        }
    }

    public boolean isServiceEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_ON_DEVICE_INTELLIGENCE,
                KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED);
    }

    private IBinder getOnDeviceIntelligenceManagerService() {
        return new IOnDeviceIntelligenceManager.Stub() {
            @Override
            public String getRemoteServicePackageName() {
                return OnDeviceIntelligenceManagerService.this.getRemoteConfiguredPackageName();
            }

            @Override
            public List<InferenceInfo> getLatestInferenceInfo(long startTimeEpochMillis) {
                mContext.enforceCallingPermission(
                        Manifest.permission.DUMP, TAG);
                return OnDeviceIntelligenceManagerService.this.getLatestInferenceInfo(
                        startTimeEpochMillis);
            }

            @Override
            public void getVersion(RemoteCallback remoteCallback) {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getVersion");
                Objects.requireNonNull(remoteCallback);
                var executor =
                        new IntelligenceServiceExecutor.Builder()
                                .onFailure(type -> remoteCallback.sendResult(null))
                                .build();
                executor.execute(
                        service -> {
                            AndroidFuture<Void> future = new AndroidFuture<>();
                            service.getVersion(
                                    new RemoteCallback(
                                            result -> {
                                                remoteCallback.sendResult(result);
                                                future.complete(null);
                                            }));
                            return future.orTimeout(getIdleTimeoutMs(),
                                        TimeUnit.MILLISECONDS);
                        });
            }

            @Override
            public void getFeature(int id, IFeatureCallback featureCallback)
                    throws RemoteException {
                int callerUid = Binder.getCallingUid();
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getFeatures");
                Objects.requireNonNull(featureCallback);
                var executor = new IntelligenceServiceExecutor.Builder()
                        .onFailure(
                                type -> {
                                    switch (type) {
                                        case SERVICE_UNAVAILABLE -> featureCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                                                "OnDeviceIntelligenceManagerService is unavailable",
                                                PersistableBundle.EMPTY);
                                        case REMOTE_FAILURE -> featureCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                "Remote call failed",
                                                PersistableBundle.EMPTY);
                                        case TIMEOUT -> featureCallback.onFailure(
                                            OnDeviceIntelligenceException.PROCESSING_ERROR_CANCELLED,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                    }
                                })
                        .build();
                executor.execute(service -> {
                            AndroidFuture<Void> future = new AndroidFuture<>();
                            service.getFeature(callerUid, id,
                                    new IFeatureCallback.Stub() {
                                        @Override
                                        public void onSuccess(Feature result)
                                                throws RemoteException {
                                            featureCallback.onSuccess(result);
                                            future.complete(null);
                                        }
                                        @Override
                                        public void onFailure(int errorCode, String errorMessage,
                                                PersistableBundle errorParams)
                                                throws RemoteException {
                                            featureCallback.onFailure(errorCode, errorMessage,
                                                    errorParams);
                                            future.complete(null);
                                        }
                                    });
                            return future.orTimeout(getIdleTimeoutMs(),
                                    TimeUnit.MILLISECONDS);
                        });
            }
            @Override
            public void listFeatures(IListFeaturesCallback listFeaturesCallback)
                    throws RemoteException {
                int callerUid = Binder.getCallingUid();
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal listFeatures");
                Objects.requireNonNull(listFeaturesCallback);
                var executor = new IntelligenceServiceExecutor.Builder()
                        .onFailure(
                                type -> {
                                    switch (type) {
                                        case SERVICE_UNAVAILABLE -> listFeaturesCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                                                "OnDeviceIntelligenceManagerService is unavailable",
                                                PersistableBundle.EMPTY);
                                        case REMOTE_FAILURE -> listFeaturesCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                "Remote call failed",
                                                PersistableBundle.EMPTY);
                                        case TIMEOUT -> listFeaturesCallback.onFailure(
                                                OnDeviceIntelligenceException.PROCESSING_ERROR_CANCELLED,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                    }
                                })
                        .build();
                executor.execute(service -> {
                            AndroidFuture<Void> future = new AndroidFuture<>();
                            service.listFeatures(callerUid,
                                    new IListFeaturesCallback.Stub() {
                                        @Override
                                        public void onSuccess(List<Feature> result)
                                                throws RemoteException {
                                            listFeaturesCallback.onSuccess(result);
                                            future.complete(null);
                                        }

                                        @Override
                                        public void onFailure(int errorCode, String errorMessage,
                                                PersistableBundle errorParams)
                                                throws RemoteException {
                                            listFeaturesCallback.onFailure(errorCode, errorMessage,
                                                    errorParams);
                                            future.complete(null);
                                        }
                                    });
                            return future.orTimeout(getIdleTimeoutMs(), TimeUnit.MILLISECONDS);
                        });
            }

            @Override
            public void listFeaturesWithFilter(PersistableBundle featureParamsFilter,
                    IListFeaturesCallback listFeaturesCallback)
                    throws RemoteException {
                int callerUid = Binder.getCallingUid();
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal listFeaturesWithFilter");
                Objects.requireNonNull(featureParamsFilter);
                Objects.requireNonNull(listFeaturesCallback);
                var executor = new IntelligenceServiceExecutor.Builder()
                        .onFailure(
                                type -> {
                                    switch (type) {
                                        case SERVICE_UNAVAILABLE -> listFeaturesCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                                                "OnDeviceIntelligenceManagerService is unavailable",
                                                PersistableBundle.EMPTY);
                                        case REMOTE_FAILURE -> listFeaturesCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                "Remote call failed",
                                                PersistableBundle.EMPTY);
                                        case TIMEOUT -> listFeaturesCallback.onFailure(
                                                OnDeviceIntelligenceException.PROCESSING_ERROR_CANCELLED,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                    }
                                })
                        .build();
                executor.execute(service -> {
                            AndroidFuture<Void> future = new AndroidFuture<>();
                            service.listFeaturesWithFilter(callerUid, featureParamsFilter,
                                    new IListFeaturesCallback.Stub() {
                                        @Override
                                        public void onSuccess(List<Feature> result)
                                                throws RemoteException {
                                            listFeaturesCallback.onSuccess(result);
                                            future.complete(null);
                                        }

                                        @Override
                                        public void onFailure(int errorCode, String errorMessage,
                                                PersistableBundle errorParams)
                                                throws RemoteException {
                                            listFeaturesCallback.onFailure(errorCode, errorMessage,
                                                    errorParams);
                                             future.complete(null);
                                        }
                                    });
                            return future.orTimeout(getIdleTimeoutMs(),
                                    TimeUnit.MILLISECONDS);
                        });
            }
            @Override
            public void getFeatureDetails(Feature feature,
                    IFeatureDetailsCallback featureDetailsCallback)
                    throws RemoteException {
                int callerUid = Binder.getCallingUid();
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getFeatureDetails");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(featureDetailsCallback);
                new IntelligenceServiceExecutor.Builder()
                        .onFailure(
                                type -> {
                                    switch (type) {
                                        case SERVICE_UNAVAILABLE ->
                                            featureDetailsCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                                                "OnDeviceIntelligenceManagerService is unavailable",
                                                PersistableBundle.EMPTY);
                                        case REMOTE_FAILURE -> featureDetailsCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                "Remote call failed",
                                                PersistableBundle.EMPTY);
                                        case TIMEOUT -> featureDetailsCallback.onFailure(
                                                OnDeviceIntelligenceException.
                                                    PROCESSING_ERROR_CANCELLED,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                    }
                                })
                        .build()
                        .execute(service -> {
                            AndroidFuture<Void> future = new AndroidFuture<>();
                            service.getFeatureDetails(callerUid, feature,
                                    new IFeatureDetailsCallback.Stub() {
                                        @Override
                                        public void onSuccess(FeatureDetails result)
                                                throws RemoteException {
                                            future.complete(null);
                                            featureDetailsCallback.onSuccess(result);
                                        }
                                        @Override
                                        public void onFailure(int errorCode, String errorMessage,
                                                PersistableBundle errorParams)
                                                throws RemoteException {
                                            future.complete(null);
                                            featureDetailsCallback.onFailure(errorCode,
                                                    errorMessage, errorParams);
                                        }
                                    });
                            return future.orTimeout(getIdleTimeoutMs(), TimeUnit.MILLISECONDS);
                        });
            }
            @Override
            public void requestFeatureDownload(Feature feature,
                    AndroidFuture cancellationSignalFuture,
                    IDownloadCallback downloadCallback) throws RemoteException {
                int callerUid = Binder.getCallingUid();
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal requestFeatureDownload");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(downloadCallback);
                new IntelligenceServiceExecutor.Builder()
                        .onFailure(
                                type -> {
                                    switch (type) {
                                        case SERVICE_UNAVAILABLE ->
                                            downloadCallback.onDownloadFailed(
                                                DownloadCallback.DOWNLOAD_FAILURE_STATUS_UNAVAILABLE,
                                                "OnDeviceIntelligenceManagerService is unavailable",
                                                PersistableBundle.EMPTY);
                                        case REMOTE_FAILURE -> downloadCallback.onDownloadFailed(
                                                DownloadCallback.DOWNLOAD_FAILURE_STATUS_UNAVAILABLE,
                                                "Remote call failed",
                                                PersistableBundle.EMPTY);
                                        case TIMEOUT -> downloadCallback.onDownloadFailed(
                                                DownloadCallback.DOWNLOAD_FAILURE_STATUS_UNKNOWN,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                    }
                                })
                        .build()
                        .execute(service -> {
                            AndroidFuture<Void> future = new AndroidFuture<>();
                            ListenableDownloadCallback listenableDownloadCallback =
                                    new ListenableDownloadCallback(
                                            downloadCallback,
                                            mMainHandler, future, getIdleTimeoutMs());
                            service.requestFeatureDownload(callerUid, feature,
                                    wrapCancellationFuture(cancellationSignalFuture),
                                    listenableDownloadCallback);
                            return future; // this future has no timeout because, actual download
                            // might take long, we fail early if there is no progress callbacks
                        }
                );
            }
            @Override
            public void requestTokenInfo(Feature feature,
                    Bundle request,
                    AndroidFuture cancellationSignalFuture,
                    ITokenInfoCallback tokenInfoCallback) throws RemoteException {
                int callerUid = Binder.getCallingUid();
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal requestTokenInfo");
                AndroidFuture<?> result = null;
                try {
                    Objects.requireNonNull(feature);
                    sanitizeInferenceParams(request);
                    Objects.requireNonNull(tokenInfoCallback);
                    var executor = new InferenceServiceExecutor.Builder()
                            .onFailure(
                                    type -> {
                                        switch (type) {
                                            case SERVICE_UNAVAILABLE -> tokenInfoCallback.onFailure(
                                                OnDeviceIntelligenceException
                                                        .ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                                                    "OnDeviceIntelligenceManagerService is unavailable",
                                                    PersistableBundle.EMPTY);
                                            case REMOTE_FAILURE -> tokenInfoCallback.onFailure(
                                                    OnDeviceIntelligenceException
                                                        .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                    "Remote call failed",
                                                    PersistableBundle.EMPTY);
                                            case TIMEOUT -> tokenInfoCallback.onFailure(
                                                OnDeviceIntelligenceException.PROCESSING_ERROR_CANCELLED,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                        }
                                    })
                            .build();
                    result = executor.execute(service -> {
                                AndroidFuture<Void> future = new AndroidFuture<>();
                                service.requestTokenInfo(callerUid, feature,
                                        request,
                                        wrapCancellationFuture(cancellationSignalFuture),
                                        wrapWithValidation(tokenInfoCallback, future,
                                                mInferenceInfoStore));
                                return future.orTimeout(getIdleTimeoutMs(),
                                        TimeUnit.MILLISECONDS);
                            });
                    if (result != null) {
                        result.whenCompleteAsync((c, e) -> BundleUtil.tryCloseResource(request),
                                resourceClosingExecutor);
                    }
                } finally {
                    if (result == null) {
                        resourceClosingExecutor.execute(() -> BundleUtil.tryCloseResource(request));
                    }
                }
            }

            @Override
            public void processRequest(Feature feature,
                    Bundle request,
                    int requestType,
                    AndroidFuture cancellationSignalFuture,
                    AndroidFuture processingSignalFuture,
                    IResponseCallback responseCallback)
                    throws RemoteException {
                AndroidFuture<?> result = null;
                int callerUid = Binder.getCallingUid();
                try {
                    Slog.i(TAG, "OnDeviceIntelligenceManagerInternal processRequest");
                    Objects.requireNonNull(feature);
                    sanitizeInferenceParams(request);
                    Objects.requireNonNull(responseCallback);
                    var executor = new InferenceServiceExecutor.Builder()
                            .onFailure(
                                    type -> {
                                        switch (type) {
                                            case SERVICE_UNAVAILABLE -> responseCallback.onFailure(
                                                    OnDeviceIntelligenceException
                                                            .PROCESSING_ERROR_SERVICE_UNAVAILABLE,
                                                    "OnDeviceIntelligenceManagerService is unavailable",
                                                    PersistableBundle.EMPTY);
                                            case REMOTE_FAILURE -> responseCallback.onFailure(
                                                    OnDeviceIntelligenceException
                                                            .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                    "Remote call failed",
                                                    PersistableBundle.EMPTY);
                                            case TIMEOUT -> responseCallback.onFailure(
                                                OnDeviceIntelligenceException.PROCESSING_ERROR_CANCELLED,
                                                "Remote call timed out",
                                                PersistableBundle.EMPTY);
                                        }
                                    })
                            .build();
                    result = executor.execute(service -> {
                                AndroidFuture<Void> future = new AndroidFuture<>();
                                boolean shouldAddInferenceInfo = request.getBoolean(
                                        OnDeviceIntelligenceManager.KEY_REQUEST_INFERENCE_INFO,
                                        false);
                                service.processRequest(callerUid, feature,
                                        request,
                                        requestType,
                                        wrapCancellationFuture(cancellationSignalFuture),
                                        wrapProcessingFuture(processingSignalFuture),
                                        wrapWithValidation(responseCallback,
                                                resourceClosingExecutor, future,
                                                mInferenceInfoStore, shouldAddInferenceInfo));
                                return future.orTimeout(getIdleTimeoutMs(),
                                        TimeUnit.MILLISECONDS);
                            });
                    if (result != null) {
                        result.whenCompleteAsync((c, e) -> BundleUtil.tryCloseResource(request),
                                resourceClosingExecutor);
                    }
                } finally {
                    if (result == null) {
                        resourceClosingExecutor.execute(() -> BundleUtil.tryCloseResource(request));
                    }
                }
            }

            @Override
            public void processRequestStreaming(Feature feature,
                    Bundle request,
                    int requestType,
                    AndroidFuture cancellationSignalFuture,
                    AndroidFuture processingSignalFuture,
                    IStreamingResponseCallback streamingCallback) throws RemoteException {
                AndroidFuture<?> result = null;
                int callerUid = Binder.getCallingUid();
                try {
                    Slog.i(TAG, "OnDeviceIntelligenceManagerInternal processRequestStreaming");
                    Objects.requireNonNull(feature);
                    sanitizeInferenceParams(request);
                    Objects.requireNonNull(streamingCallback);
                    var executor = new InferenceServiceExecutor.Builder()
                            .onFailure(
                                    type -> {
                                        switch (type) {
                                            case SERVICE_UNAVAILABLE -> streamingCallback.onFailure(
                                                    OnDeviceIntelligenceException
                                                            .PROCESSING_ERROR_SERVICE_UNAVAILABLE,
                                                    "OnDeviceIntelligenceManagerService is unavailable",
                                                    PersistableBundle.EMPTY);
                                            case REMOTE_FAILURE -> streamingCallback.onFailure(
                                                    OnDeviceIntelligenceException
                                                            .PROCESSING_UPDATE_STATUS_CONNECTION_FAILED,
                                                    "Remote call failed",
                                                    PersistableBundle.EMPTY);
                                            case TIMEOUT -> streamingCallback.onFailure(
                                                OnDeviceIntelligenceException.PROCESSING_ERROR_CANCELLED,
                                                    "Remote call timed out",
                                                    PersistableBundle.EMPTY);
                                        }
                                    })
                            .build();
                    result = executor.execute(service -> {
                                AndroidFuture<Void> future = new AndroidFuture<>();
                                boolean shouldAddInferenceInfo = request.getBoolean(
                                        OnDeviceIntelligenceManager.KEY_REQUEST_INFERENCE_INFO,
                                        false);
                                service.processRequestStreaming(callerUid,
                                        feature,
                                        request, requestType,
                                        wrapCancellationFuture(cancellationSignalFuture),
                                        wrapProcessingFuture(processingSignalFuture),
                                        wrapWithValidation(streamingCallback,
                                                resourceClosingExecutor, future,
                                                mInferenceInfoStore, shouldAddInferenceInfo));
                                return future.orTimeout(getIdleTimeoutMs(),
                                        TimeUnit.MILLISECONDS);
                            });
                    if (result != null) {
                        result.whenCompleteAsync((c, e) -> BundleUtil.tryCloseResource(request),
                                resourceClosingExecutor);
                    }
                } finally {
                    if (result == null) {
                        resourceClosingExecutor.execute(() -> BundleUtil.tryCloseResource(request));
                    }
                }
            }

            @Override
            public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
                    String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
                new OnDeviceIntelligenceShellCommand(OnDeviceIntelligenceManagerService.this)
                        .exec(this, in, out, err, args, callback, resultReceiver);
            }

            @Override
            public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
                if (!DumpUtils.checkDumpPermission(mContext, TAG, writer)) return;

                String prefix = "  ";
                writer.println("OnDeviceIntelligenceManagerService");
                writer.println();
                writer.println(prefix + "Configurations:");
                final String configPrefix = prefix + "  ";
                try {
                    String[] serviceNames = getServiceNames();
                    writer.println(
                            configPrefix + "OnDeviceIntelligenceService: " + serviceNames[0]);
                    writer.println(configPrefix + "OnDeviceSandboxedInferenceService: "
                            + serviceNames[1]);
                } catch (Resources.NotFoundException e) {
                    writer.println(configPrefix + "Could not get service names: " + e);
                }
                writer.println();
                if (mRemoteOnDeviceIntelligenceService != null) {
                    writer.println("  mRemoteOnDeviceIntelligenceService: "
                            + mRemoteOnDeviceIntelligenceService);
                    mRemoteOnDeviceIntelligenceService.dump(prefix, writer);
                }
                if (mRemoteInferenceService != null) {
                    writer.println("  mRemoteInferenceService: " + mRemoteInferenceService);
                    mRemoteInferenceService.dump(prefix, writer);
                }
                mInferenceInfoStore.dump(prefix, writer);
                writer.println();
                writer.println(prefix + "Lifecycle Listeners:");
                mLifecycleListeners.dump(writer, prefix + "  ");
            }
            @Override
            public void registerInferenceServiceLifecycleListener(ILifecycleListener listener)
                    throws RemoteException {
                Slog.d(TAG, "OnDeviceIntelligenceManagerInternal"
                                + "registerInferenceServiceLifecycleListener");
                Objects.requireNonNull(listener);
                mContext.enforceCallingPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available, not registering lifecycle listener.");
                    return;
                }
                mLifecycleListeners.register(listener);
            }
            @Override
            public void unregisterInferenceServiceLifecycleListener(ILifecycleListener listener)
                    throws RemoteException {
                Slog.d(TAG, "OnDeviceIntelligenceManagerInternal "
                            + "unregisterInferenceServiceLifecycleListener");
                Objects.requireNonNull(listener);
                mContext.enforceCallingPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available, not unregistering lifecycle listener.");
                    return;
                }
                mLifecycleListeners.unregister(listener);
            }
        };
    }

    /**
     * Ensures the remote {@link OnDeviceIntelligenceService} is initialized and connected.
     *
     * @param shouldThrow If {@code true}, throws a {@link RuntimeException} if initialization
     *     fails. If {@code false}, logs the error and returns {@code false}.
     * @return {@code true} if the service is initialized, {@code false} otherwise.
     */
    public boolean ensureRemoteIntelligenceServiceInitialized(boolean shouldThrow) {
        synchronized (mLock) {
            if (mRemoteOnDeviceIntelligenceService != null) {
                return true;
            }
            try {
                String serviceName = getServiceNames()[0];
                Binder.withCleanCallingIdentity(
                        () -> validateServiceElevated(serviceName, false));
                mRemoteOnDeviceIntelligenceService = new RemoteOnDeviceIntelligenceService(
                        mContext,
                        ComponentName.unflattenFromString(serviceName),
                        UserHandle.SYSTEM.getIdentifier());
                mRemoteOnDeviceIntelligenceService.setServiceLifecycleCallbacks(
                        new ServiceConnector.ServiceLifecycleCallbacks<>() {
                            @Override
                            public void onConnected(
                                    @NonNull IOnDeviceIntelligenceService service) {
                                try {
                                    service.registerRemoteServices(
                                            getRemoteProcessingService());
                                    service.ready();
                                } catch (RemoteException ex) {
                                    Slog.w(TAG, "Failed to send connected event", ex);
                                }
                            }
                });
            } catch (RuntimeException e) {
                if (shouldThrow) {
                    throw e;
                }
                Slog.e(TAG, "Failed to initialize remote intelligence service", e);
                return false;
            }
        }
        return true;
    }

    @NonNull
    private IRemoteProcessingService.Stub getRemoteProcessingService() {
        return new IRemoteProcessingService.Stub() {
            @Override
            public void updateProcessingState(
                    Bundle processingState,
                    IProcessingUpdateStatusCallback callback) {
              sanitizeStateParams(processingState);
              callbackExecutor.execute(() -> {
                    AndroidFuture<Void> result = null;
                    try {
                        // Reserved for use by system-server.
                        processingState.remove(
                                OnDeviceSandboxedInferenceService.KEY_DEVICE_CONFIG_UPDATE);
                        processingState.remove(
                                OnDeviceSandboxedInferenceService.DEPRECATED_KEY_DEVICE_CONFIG_UPDATE);
                        if (!ensureRemoteInferenceServiceInitialized(/* shouldThrow= */ false)) {
                            Slog.e(TAG, "Remote inference service not available for"
                                    + "updateProcessingState");
                            try {
                                callback.onFailure(
                                        OnDeviceIntelligenceException
                                                .PROCESSING_ERROR_SERVICE_UNAVAILABLE,
                                        "Remote inference service not available");
                            } catch (RemoteException e) {
                                Slog.e(TAG,
                                        "Failed to call onFailure for updateProcessingState", e);
                            }
                            return;
                        }
                        result = mRemoteInferenceService.post(
                                service -> service.updateProcessingState(
                                        processingState,
                                        callback));
                        if (result != null) {
                            result.whenCompleteAsync((c, e) -> BundleUtil.tryCloseResource(
                                    processingState), resourceClosingExecutor);
                        }
                    } finally {
                        if (result == null) {
                            resourceClosingExecutor.execute(
                                    () -> BundleUtil.tryCloseResource(processingState));
                        }
                    }
                });
            }
        };
    }

    /**
     * Ensures the remote {@link OnDeviceSandboxedInferenceService} is initialized and connected.
     *
     * @param shouldThrow If {@code true}, throws a {@link RuntimeException} if initialization
     *     fails. If {@code false}, logs the error and returns {@code false}.
     * @return {@code true} if the service is initialized, {@code false} otherwise.
     */
    public boolean ensureRemoteInferenceServiceInitialized(boolean shouldThrow) {
        synchronized (mLock) {
            if (mRemoteInferenceService != null) {
                return true;
            }
            try {
                String serviceName = getServiceNames()[1];
                Binder.withCleanCallingIdentity(
                        () -> validateServiceElevated(serviceName, true));
                mRemoteInferenceService = new RemoteOnDeviceSandboxedInferenceService(
                        mContext,
                        ComponentName.unflattenFromString(serviceName),
                        UserHandle.SYSTEM.getIdentifier());
                mRemoteInferenceService.setServiceLifecycleCallbacks(
                        new ServiceConnector.ServiceLifecycleCallbacks<>() {
                            @Override
                            public void onConnected(
                                    @NonNull IOnDeviceSandboxedInferenceService service) {
                                if (!ensureRemoteIntelligenceServiceInitialized(
                                        /* shouldThrow= */false)) {
                                    Slog.e(TAG,
                                            "Failed to initialize remote intelligence service"
                                            + " from inference service connection.");
                                    return;
                                }
                                try {
                                    service.registerRemoteStorageService(
                                            getRemoteStorageService(),
                                            new IRemoteCallback.Stub() {
                                                @Override
                                                public void sendResult(Bundle bundle) {
                                                    final int uid = Binder.getCallingUid();
                                                    setRemoteInferenceServiceUid(uid);
                                                }
                                            });
                                    mRemoteOnDeviceIntelligenceService.run(
                                            IOnDeviceIntelligenceService
                                                    ::notifyInferenceServiceConnected);
                                    broadcastExecutor.execute(
                                            () -> registerModelLoadingBroadcasts(service));
                                    mConfigExecutor.execute(
                                            () -> registerDeviceConfigChangeListener());
                                    service.registerInferenceServiceLifecycleListener(
                                            mSystemLifecycleListener);
                                } catch (RemoteException ex) {
                                    Slog.w(TAG, "Failed to send connected event", ex);
                                }
                            }

                            @Override
                            public void onDisconnected(
                                    @NonNull IOnDeviceSandboxedInferenceService service) {
                                ensureRemoteIntelligenceServiceInitialized(false);
                                mRemoteOnDeviceIntelligenceService.run(IOnDeviceIntelligenceService
                                        ::notifyInferenceServiceDisconnected);
                            }

                            @Override
                            public void onBinderDied() {
                                ensureRemoteIntelligenceServiceInitialized(false);
                                mRemoteOnDeviceIntelligenceService.run(
                                        IOnDeviceIntelligenceService
                                                ::notifyInferenceServiceDisconnected);
                            }
                            });
            } catch (RuntimeException e) {
                if (shouldThrow) {
                    throw e;
                }
                Slog.e(TAG, "Failed to initialize remote inference service", e);
                return false;
            }
        }
        return true;
    }

    private void registerModelLoadingBroadcasts(IOnDeviceSandboxedInferenceService service) {
        String[] modelBroadcastKeys;
        try {
            modelBroadcastKeys = getBroadcastKeys();
        } catch (Resources.NotFoundException e) {
            Slog.d(TAG, "Skipping model broadcasts as broadcast intents configured.");
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putBoolean(REGISTER_MODEL_UPDATE_CALLBACK_BUNDLE_KEY, true);
        try {
            service.updateProcessingState(bundle, new IProcessingUpdateStatusCallback.Stub() {
                @Override
                public void onSuccess(PersistableBundle statusParams) {
                    Binder.clearCallingIdentity();
                    synchronized (mLock) {
                        if (statusParams.containsKey(MODEL_LOADED_BUNDLE_KEY)) {
                            String modelLoadedBroadcastKey = modelBroadcastKeys[0];
                            if (modelLoadedBroadcastKey != null
                                    && !modelLoadedBroadcastKey.isEmpty()) {
                                final Intent intent = new Intent(modelLoadedBroadcastKey);
                                intent.setPackage(mBroadcastPackageName);
                                mContext.sendBroadcast(intent,
                                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
                            }
                        } else if (statusParams.containsKey(MODEL_UNLOADED_BUNDLE_KEY)) {
                            String modelUnloadedBroadcastKey = modelBroadcastKeys[1];
                            if (modelUnloadedBroadcastKey != null
                                    && !modelUnloadedBroadcastKey.isEmpty()) {
                                final Intent intent = new Intent(modelUnloadedBroadcastKey);
                                intent.setPackage(mBroadcastPackageName);
                                mContext.sendBroadcast(intent,
                                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
                            }
                        }
                    }
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    Slog.e(
                            TAG, "Failed to register model loading callback with status code: "
                                    + errorMessage,
                            new OnDeviceIntelligenceException(errorCode));
                }
            });
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register model loading callback with status code",
                    e);
        }
    }

    private void registerDeviceConfigChangeListener() {
        Log.d(TAG, "registerDeviceConfigChangeListener");
        String configNamespace = getConfigNamespace();
        if (configNamespace.isEmpty()) {
            Slog.e(TAG, "config_defaultOnDeviceIntelligenceDeviceConfigNamespace is empty");
            return;
        }
        DeviceConfig.addOnPropertiesChangedListener(
                configNamespace,
                mConfigExecutor,
                mOnPropertiesChangedListener);
    }

    private String getConfigNamespace() {
        synchronized (mLock) {
            if (mTemporaryConfigNamespace != null) {
                return mTemporaryConfigNamespace;
            }
            return mContext.getResources()
                    .getString(
                        R.string.config_defaultOnDeviceIntelligenceDeviceConfigNamespace);
        }
    }

    private void sendUpdatedConfig(
            DeviceConfig.Properties props) {
        Log.d(TAG, "sendUpdatedConfig");
        PersistableBundle persistableBundle = new PersistableBundle();
        for (String key : props.getKeyset()) {
            persistableBundle.putString(key, props.getString(key, ""));
        }
        Bundle bundle = new Bundle();
        bundle.putParcelable(OnDeviceSandboxedInferenceService
                .KEY_DEVICE_CONFIG_UPDATE,
                persistableBundle);
        bundle.putParcelable(OnDeviceSandboxedInferenceService
                .DEPRECATED_KEY_DEVICE_CONFIG_UPDATE,
                persistableBundle);
        if (!ensureRemoteInferenceServiceInitialized(/* shouldThrow= */ false)) {
            Slog.e(TAG,
                    "Remote inference service not available for sendUpdatedConfig");
            return;
        }
        mRemoteInferenceService.run(service -> service.updateProcessingState(
                bundle,
                new IProcessingUpdateStatusCallback.Stub() {
                    @Override
                    public void onSuccess(PersistableBundle result) {
                        Slog.d(TAG, "Config update successful." + result);
                    }
                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        Slog.e(TAG, "Config update failed with code ["
                                        + errorCode
                                        + "] and message = " + errorMessage);
                    }
                }));
    }

    @NonNull
    private IRemoteStorageService.Stub getRemoteStorageService() {
        return new RemoteStorageService(
                () -> ensureRemoteIntelligenceServiceInitialized(false),
                mRemoteOnDeviceIntelligenceService,
                callbackExecutor,
                resourceClosingExecutor);
    }

    private void validateServiceElevated(String serviceName, boolean checkIsolated) {
        try {
            if (TextUtils.isEmpty(serviceName)) {
                throw new IllegalStateException(
                        "Remote service is not configured to complete the request");
            }
            ComponentName serviceComponent = ComponentName.unflattenFromString(
                    serviceName);
            ServiceInfo serviceInfo =
                    AppGlobals.getPackageManager().getServiceInfo(
                            serviceComponent,
                            PackageManager.MATCH_DIRECT_BOOT_AWARE
                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                            UserHandle.SYSTEM.getIdentifier());
            if (serviceInfo == null) {
                throw new IllegalStateException(
                        "Remote service is not configured to complete the request.");
            }
            if (!checkIsolated) {
                checkServiceRequiresPermission(
                        serviceInfo,
                        Manifest.permission.BIND_ON_DEVICE_INTELLIGENCE_SERVICE);
                return;
            }
            checkServiceRequiresPermission(
                    serviceInfo,
                    Manifest.permission.BIND_ON_DEVICE_SANDBOXED_INFERENCE_SERVICE);
            if (!isIsolatedService(serviceInfo)) {
                throw new SecurityException(
                        "Call required an isolated service, but the configured service: "
                                + serviceName + ", is not isolated");
            }
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Could not fetch service info for remote services", e);
        }
    }
    private static void checkServiceRequiresPermission(ServiceInfo serviceInfo,
            String requiredPermission) {
        final String permission = serviceInfo.permission;
        if (!requiredPermission.equals(permission)) {
            throw new SecurityException(String.format(
                    "Service %s requires %s permission. Found %s permission",
                    serviceInfo.getComponentName(),
                    requiredPermission,
                    serviceInfo.permission));
        }
    }
    private static boolean isIsolatedService(@NonNull ServiceInfo serviceInfo) {
        return (serviceInfo.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0
                && (serviceInfo.flags & ServiceInfo.FLAG_EXTERNAL_SERVICE) == 0;
    }
    private List<InferenceInfo> getLatestInferenceInfo(long startTimeEpochMillis) {
        return mInferenceInfoStore.getLatestInferenceInfo(startTimeEpochMillis);
    }
    @Nullable
    public String getRemoteConfiguredPackageName() {
        try {
            String[] serviceNames = getServiceNames();
            ComponentName componentName = ComponentName.unflattenFromString(serviceNames[1]);
            if (componentName != null) {
                return componentName.getPackageName();
            }
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "Could not find resource", e);
        }
        return null;
    }
    protected String[] getServiceNames() throws Resources.NotFoundException {
        // TODO 329240495 : Consider a small class with explicit field names for the two services
        synchronized (mLock) {
            if (mTemporaryServiceNames != null && mTemporaryServiceNames.length == 2) {
                return mTemporaryServiceNames;
            }
        }
        return new String[]{
                mContext.getResources().getString(
                        R.string.config_defaultOnDeviceIntelligenceService),
                mContext.getResources().getString(
                        R.string.config_defaultOnDeviceSandboxedInferenceService)
        };
    }

    protected String[] getBroadcastKeys() throws Resources.NotFoundException {
        // TODO 329240495 : Consider a small class with explicit field names for the two services
        synchronized (mLock) {
            if (mTemporaryBroadcastKeys != null && mTemporaryBroadcastKeys.length == 2) {
                return mTemporaryBroadcastKeys;
            }
        }
        return new String[]{MODEL_LOADED_BROADCAST_INTENT, MODEL_UNLOADED_BROADCAST_INTENT};
    }

    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void setTemporaryServices(@NonNull String[] componentNames, int durationMs) {
        Objects.requireNonNull(componentNames);
        enforceShellOnly(Binder.getCallingUid(), "setTemporaryServices");
        mContext.enforceCallingPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
        synchronized (mLock) {
            mTemporaryServiceNames = componentNames;
            if (mRemoteInferenceService != null) {
                mRemoteInferenceService.unbind();
                mRemoteInferenceService = null;
            }
            if (mRemoteOnDeviceIntelligenceService != null) {
                mRemoteOnDeviceIntelligenceService.unbind();
                mRemoteOnDeviceIntelligenceService = null;
            }
            if (durationMs != -1) {
                getTemporaryHandler().sendEmptyMessageDelayed(
                        MSG_RESET_TEMPORARY_SERVICE, durationMs);
            }
        }
    }

    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void setModelBroadcastKeys(@NonNull String[] broadcastKeys, String receiverPackageName,
            int durationMs) {
        Objects.requireNonNull(broadcastKeys);
        enforceShellOnly(Binder.getCallingUid(), "setModelBroadcastKeys");
        mContext.enforceCallingPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
        synchronized (mLock) {
            mTemporaryBroadcastKeys = broadcastKeys;
            mBroadcastPackageName = receiverPackageName;
            if (durationMs != -1) {
                getTemporaryHandler().sendEmptyMessageDelayed(
                        MSG_RESET_BROADCAST_KEYS, durationMs);
            }
        }
    }

    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void setTemporaryDeviceConfigNamespace(@NonNull String configNamespace,
            int durationMs) {
        Objects.requireNonNull(configNamespace);
        enforceShellOnly(Binder.getCallingUid(), "setTemporaryDeviceConfigNamespace");
        mContext.enforceCallingPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
        synchronized (mLock) {
            mTemporaryConfigNamespace = configNamespace;
            if (durationMs != -1) {
                getTemporaryHandler().sendEmptyMessageDelayed(
                        MSG_RESET_CONFIG_NAMESPACE, durationMs);
            }
        }
    }

    /**
     * Reset the temporary services set in CTS tests, this method is primarily used to only revert
     * the changes caused by CTS tests.
     */
    public void resetTemporaryServices() {
        synchronized (mLock) {
            if (mTemporaryHandler != null) {
                mTemporaryHandler.removeMessages(MSG_RESET_TEMPORARY_SERVICE);
                mTemporaryHandler = null;
            }
            mRemoteInferenceService = null;
            mRemoteOnDeviceIntelligenceService = null;
            mTemporaryServiceNames = new String[0];
        }
    }

    /**
     * Throws if the caller is not of a shell (or root) UID.
     *
     * @param callingUid pass Binder.callingUid().
     */
    public static void enforceShellOnly(int callingUid, String message) {
        if (callingUid == android.os.Process.SHELL_UID
                || callingUid == android.os.Process.ROOT_UID) {
            return; // okay
        }
        throw new SecurityException(message + ": Only shell user can call it");
    }

    private AndroidFuture<IBinder> wrapCancellationFuture(
            AndroidFuture future) {
        if (future == null) {
            return null;
        }
        AndroidFuture<IBinder> cancellationFuture = new AndroidFuture<>();
        cancellationFuture.whenCompleteAsync((c, e) -> {
            if (e != null) {
                Log.e(TAG, "Error forwarding ICancellationSignal "
                        + "to manager layer", e);
                future.completeExceptionally(e);
            } else {
                future.complete(new ICancellationSignal.Stub() {
                    @Override
                    public void cancel() throws RemoteException {
                        ICancellationSignal.Stub.asInterface(c).cancel();
                    }
                });
            }
        });
        return cancellationFuture;
    }

    private AndroidFuture<IBinder> wrapProcessingFuture(
            AndroidFuture future) {
        if (future == null) {
            return null;
        }
        AndroidFuture<IBinder> processingSignalFuture = new AndroidFuture<>();
        processingSignalFuture.whenCompleteAsync((c, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
            } else {
                future.complete(new IProcessingSignal.Stub() {
                    @Override
                    public void sendSignal(PersistableBundle actionParams) throws RemoteException {
                        IProcessingSignal.Stub.asInterface(c).sendSignal(actionParams);
                    }
                });
            }
        });
        return processingSignalFuture;
    }

    private synchronized Handler getTemporaryHandler() {
        if (mTemporaryHandler == null) {
            mTemporaryHandler = new Handler(Looper.getMainLooper(), null, true) {
                @Override
                public void handleMessage(Message msg) {
                    synchronized (mLock) {
                        if (msg.what == MSG_RESET_TEMPORARY_SERVICE) {
                            resetTemporaryServices();
                        } else if (msg.what == MSG_RESET_BROADCAST_KEYS) {
                            mTemporaryBroadcastKeys = null;
                            mBroadcastPackageName = SYSTEM_PACKAGE;
                        } else if (msg.what == MSG_RESET_CONFIG_NAMESPACE) {
                            mTemporaryConfigNamespace = null;
                        } else {
                            Slog.wtf(TAG, "invalid handler msg: " + msg);
                        }
                    }
                }
            };
        }
        return mTemporaryHandler;
    }

    private long getIdleTimeoutMs() {
        return Settings.Secure.getLongForUser(
                mContext.getContentResolver(),
                Settings.Secure.ON_DEVICE_INTELLIGENCE_IDLE_TIMEOUT_MS,
                TimeUnit.HOURS.toMillis(1),
                mContext.getUserId());
    }

    private int getRemoteInferenceServiceUid() {
        synchronized (mLock) {
            return remoteInferenceServiceUid;
        }
    }

    private void setRemoteInferenceServiceUid(int remoteInferenceServiceUid) {
        synchronized (mLock) {
            this.remoteInferenceServiceUid = remoteInferenceServiceUid;
        }
    }

    /** Returns the remote sandboxed inference service connector. */
    public RemoteOnDeviceSandboxedInferenceService getRemoteInferenceService() {
        return mRemoteInferenceService;
    }

    /** Returns the remote intelligence service connector. */
    public RemoteOnDeviceIntelligenceService getRemoteOnDeviceIntelligenceService() {
        return mRemoteOnDeviceIntelligenceService;
    }
}
