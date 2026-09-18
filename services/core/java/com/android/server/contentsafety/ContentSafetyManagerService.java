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

package com.android.server.contentsafety;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_FOREGROUND_SERVICE;
import static android.content.Context.BIND_INCLUDE_CAPABILITIES;

import static com.android.server.contentsafety.ContentSafetyBundleUtil.sanitizeCheckContentParams;
import static com.android.server.contentsafety.ContentSafetyBundleUtil.sanitizeCheckLoadFeatureParams;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.SuppressLint;
import android.app.contentsafety.ContentSafetyManager;
import android.app.contentsafety.FeatureException;
import android.app.contentsafety.ICheckContentCallback;
import android.app.contentsafety.IContentSafetyManager;
import android.app.contentsafety.IIsFeatureEnabledCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.contentsafety.ContentSafetySandboxedService;
import android.service.contentsafety.IContentSafetySandboxedService;
import android.service.contentsafety.IContentSafetyService;
import android.service.contentsafety.IContentSafetySettingsService;
import android.service.contentsafety.IGetFeatureCallback;
import android.service.contentsafety.ILoadFeatureCallback;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is the system service for handling calls on the {@link ContentSafetyManager}. This service
 * orchestrates the interactions between three remote services:
 *  1. {@link android.service.contentsafety.ContentSafetyService}: The main service.
 *  2. {@link android.service.contentsafety.ContentSafetySandboxedService}: A sandboxed service for
 *     processing content.
 *  3. {@link android.service.contentsafety.ContentSafetySettingsService}: A service for handling
 *    settings.
 * It manages their lifecycle and forwards connection status updates to the main service.
 *
 * @hide
 */
public class ContentSafetyManagerService extends SystemService {
    private static final String TAG = "ContentSafetyManagerService";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String NAMESPACE_CONTENT_SAFETY = "ondevicesafety";

    /** Handler message to {@link #resetTemporaryServices()} */
    private static final int MSG_RESET_TEMPORARY_SERVICE = 0;

    /** Default value in absence of {@link DeviceConfig} override. */
    private static final boolean DEFAULT_SERVICE_ENABLED = true;

    @SandboxedServiceType private int mSandboxedServiceType;
    private ComponentName mContentSafetyIsolatedComponentName;
    private ComponentName mContentSafetyPccComponentName;
    private ComponentName mContentSafetyComponentName;
    private ComponentName mContentSafetySettingsComponentName;
    private final Executor mResourceClosingExecutor = Executors.newCachedThreadPool();

    private final Context mContext;
    protected final Object mLock = new Object();

    private RemoteContentSafetyService mRemoteContentSafetyService;
    private RemoteContentSafetySandboxedService mRemoteContentSafetyIsolatedService;
    private RemoteContentSafetySandboxedService mRemoteContentSafetyPccService;
    private RemoteContentSafetySandboxedService mRemoteSandboxedService;
    private RemoteContentSafetySettingsService mRemoteContentSafetySettingsService;

    @NonNull private final ServiceConnectionFactory mIsolatedServiceConnectionFactory;
    @NonNull private final ServiceConnectionFactory mPccServiceConnectionFactory;

    volatile boolean mIsServiceEnabled;

    // Caches feature bundles from the remote service to avoid repeated get/load calls.
    private final SparseArray<Bundle> mFeatureBundleMap;

    /**
     * List of available Sandboxed service types.
     *
     * @hide
     */
    @IntDef(
            value = {
                SandboxedServiceType.SERVICE_TYPE_PCC,
                SandboxedServiceType.SERVICE_TYPE_ISOLATED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SandboxedServiceType {
        /**
         * Indicates that {@link android.service.contentsafety.ContentSafetySandboxedService} is
         * created in PCC process.
         */
        int SERVICE_TYPE_PCC = 0;

        /**
         * Indicates that {@link android.service.contentsafety.ContentSafetySandboxedService} is
         * created in isolated process.
         */
        int SERVICE_TYPE_ISOLATED = 1;
    }

    private boolean mIsSandboxedServicePcc;

    @GuardedBy("mLock")
    private ComponentName[] mTemporaryServiceNames;

    /** Handler used to reset the temporary service names. */
    private Handler mTemporaryHandler;

    public ContentSafetyManagerService(Context context) {
        super(context);
        mContext = context;
        mFeatureBundleMap = new SparseArray<>();
        mTemporaryServiceNames = new ComponentName[0];
        mSandboxedServiceType = getSandboxedServiceType();
        mIsSandboxedServicePcc =
                getSandboxedServiceType() == SandboxedServiceType.SERVICE_TYPE_PCC;

        mPccServiceConnectionFactory =
                new ServiceConnectionFactory(SandboxedServiceType.SERVICE_TYPE_PCC);
        mIsolatedServiceConnectionFactory =
                new ServiceConnectionFactory(SandboxedServiceType.SERVICE_TYPE_ISOLATED);
    }

    @Override
    public void onStart() {
        if (android.app.contentsafety.flags.Flags.enableContentsafety()) {
            publishBinderService(
                    Context.CONTENT_SAFETY_SERVICE,
                    getContentSafetyManagerService(),
                    /* allowIsolated= */ true);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (android.app.contentsafety.flags.Flags.enableContentsafety()) {
            if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
                DeviceConfig.addOnPropertiesChangedListener(
                        NAMESPACE_CONTENT_SAFETY,
                        BackgroundThread.getExecutor(),
                        (properties) -> onDeviceConfigChange(properties.getKeyset()));

                mIsServiceEnabled = isServiceEnabled();
            }
        }
    }

    private void onDeviceConfigChange(@NonNull Set<String> keys) {
        if (keys.contains(KEY_SERVICE_ENABLED)) {
            mIsServiceEnabled = isServiceEnabled();
        }
    }

    private boolean isServiceEnabled() {
        if (android.app.contentsafety.flags.Flags.enableContentsafety()) {
            return DeviceConfig.getBoolean(
                    NAMESPACE_CONTENT_SAFETY, KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED);
        }
        return false;
    }

    private String getRemoteSandboxedServicePackageNameInternal() {
        if (!android.app.contentsafety.flags.Flags.enableContentsafety()) {
            return "";
        }
        setRemoteServiceComponentNames();
        return ContentSafetyManagerService.this.getRemoteConfiguredPackageName(
                mContentSafetyPccComponentName == null
                        ? mContentSafetyPccComponentName
                        : mContentSafetyIsolatedComponentName);
    }

    private String getRemoteSettingsServicePackageNameInternal() {
        if (!android.app.contentsafety.flags.Flags.enableContentsafety()) {
            return "";
        }
        setRemoteServiceComponentNames();
        return ContentSafetyManagerService.this.getRemoteConfiguredPackageName(
                mContentSafetySettingsComponentName);
    }

    private void checkContentInternal(
            int featureType,
            Bundle input,
            @Nullable AndroidFuture cancellationSignalFuture,
            @NonNull ICheckContentCallback callback)
            throws RemoteException {
        if (!android.app.contentsafety.flags.Flags.enableContentsafety()) {
            return;
        }

        if (cancellationSignalFuture != null && cancellationSignalFuture.isCancelled()) {
            Bundle bundle = new Bundle();
            ArrayList<Integer> error = new ArrayList<>();
            error.add(ContentSafetyManager.CONTENT_SAFETY_CHECK_CONTENT_CANCELLED);
            bundle.putIntegerArrayList(Integer.toString(featureType), error);
            callback.onResult(bundle);
            return;
        }

        try {
            sanitizeCheckContentParams(input);
        } catch (BadParcelableException e) {
            Slog.e(TAG, "input Bundle is not Read-only, ", e);
            Bundle bundle = new Bundle();
            ArrayList<Integer> error = new ArrayList<>();
            error.add(ContentSafetyManager.CONTENT_SAFETY_PAYLOAD_NOT_READ_ONLY_ERROR);
            bundle.putIntegerArrayList(Integer.toString(featureType), error);
            callback.onResult(bundle);
            return;
        }

        // Initialize the remote services and set up the lifecycleCallbacks.
        ensureRemoteContentSafetyServicesInitialized();

        if (!mIsServiceEnabled) {
            Slog.w(TAG, "Service not available");
            Bundle bundle = new Bundle();
            ArrayList<Integer> error = new ArrayList<>();
            error.add(ContentSafetyManager
                    .CONTENT_SAFETY_SANDBOXED_SERVICE_ERROR_NOT_AVAILABLE);
            bundle.putIntegerArrayList(Integer.toString(featureType), error);
            callback.onResult(bundle);
            return;
        }

        AndroidFuture checkContentResultFuture = null;
        try {
            if (mFeatureBundleMap.indexOfKey(featureType) >= 0) {
                // Avoid getFeature and LoadFeature calls.
                try {
                    checkContentResultFuture =
                            mRemoteSandboxedService.postAsync(
                                    service -> {
                                        AndroidFuture future = new AndroidFuture();
                                        service.requestCheckContent(
                                                featureType,
                                                input,
                                                wrapCancellationFuture(
                                                        cancellationSignalFuture),
                                                wrapCheckContentCallback(future, callback));
                                        return future.orTimeout(
                                                getIdleTimeoutMsContentSafetySandboxedService(),
                                                TimeUnit.MILLISECONDS);
                                    });

                    checkContentResultFuture =
                            checkContentResultFuture.whenCompleteAsync(
                                    (c, e) -> ContentSafetyBundleUtil.tryCloseResource(input),
                                    mResourceClosingExecutor);
                    return;
                } catch (RuntimeException e) {
                    Slog.e(TAG, "Fail to call CheckContent", e);
                    Bundle bundle = new Bundle();
                    ArrayList<Integer> error = new ArrayList<>();
                    error.add(
                            ContentSafetyManager.CONTENT_SAFETY_CHECK_CONTENT_INVOKE_ERROR);
                    bundle.putIntegerArrayList(Integer.toString(featureType), error);
                    callback.onResult(bundle);
                }
            }
            try {
                var unused =
                        mRemoteContentSafetyService.postAsync(
                                csService -> {
                                    AndroidFuture future = new AndroidFuture();
                                    csService.requestGetFeature(
                                            featureType,
                                            wrapCancellationFuture(
                                                    cancellationSignalFuture),
                                            handleGetFeatureResult(
                                                    featureType,
                                                    input,
                                                    future,
                                                    cancellationSignalFuture,
                                                    callback));
                                    return future.orTimeout(
                                            getIdleTimeoutMsContentService(),
                                            TimeUnit.MILLISECONDS);
                                });
            } catch (RuntimeException e) {
                Slog.e(TAG, "Fail to call getFeature", e);
                Bundle bundle = new Bundle();
                ArrayList<Integer> error = new ArrayList<>();
                error.add(
                        ContentSafetyManager.CONTENT_SAFETY_GET_FEATURE_INVOKE_ERROR);
                bundle.putIntegerArrayList(Integer.toString(featureType), error);
                callback.onResult(bundle);
            }
        } finally {
            if (checkContentResultFuture == null) {
                mResourceClosingExecutor.execute(()
                        -> ContentSafetyBundleUtil.tryCloseResource(input));
            }
        }
    }

    private void isFeatureEnabledInternal(
            int featureType,
            AndroidFuture cancellationSignalFuture,
            IIsFeatureEnabledCallback callback)
            throws RemoteException {
        if (!android.app.contentsafety.flags.Flags.enableContentsafety()) {
            return;
        }
        ensureRemoteContentSafetyServicesInitialized();

        if (!mIsServiceEnabled) {
            Slog.w(TAG, "Service not available");
            callback.onFailure(
                    FeatureException.FEATURE_SETTINGS_SERVICE_UNAVAILABLE);
            return;
        }

        UserHandle userId = Binder.getCallingUserHandle();
        boolean isRun =
                mRemoteContentSafetySettingsService.run(
                        service -> {
                            AndroidFuture future = new AndroidFuture();
                            service.requestIsFeatureEnabled(
                                    featureType,
                                    userId,
                                    wrapCancellationFuture(cancellationSignalFuture),
                                    wrapIsFeatureEnabledCallback(future, callback));
                            future.orTimeout(
                                    getIdleTimeoutMsSettingsService(),
                                    TimeUnit.MILLISECONDS);
                        });
        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "ContentSafetyManagerService isFeatureEnabled is %s scheduled",
                        isRun ? "successfully" : "failed to be "));
    }

    private void onShellCommandInternal(
            Binder bClass,
            FileDescriptor in,
            FileDescriptor out,
            FileDescriptor err,
            String[] args,
            ShellCallback callback,
            ResultReceiver resultReceiver) {
        if (!android.app.contentsafety.flags.Flags.enableContentsafety()) {
            return;
        }
        new ContentSafetyShellCommand(ContentSafetyManagerService.this)
                .exec(bClass, in, out, err, args, callback, resultReceiver);
    }
    private IBinder getContentSafetyManagerService() {
        return new IContentSafetyManager.Stub() {

            @Override
            @PermissionManuallyEnforced
            public String getRemoteServicePackageName() {
                if (!android.app.contentsafety.flags.Flags.enableContentsafety()) {
                    return "";
                }
                setRemoteServiceComponentNames();
                return ContentSafetyManagerService.this.getRemoteConfiguredPackageName(
                        mContentSafetyComponentName);
            }

            @Override
            @PermissionManuallyEnforced
            public String getRemoteSandboxedServicePackageName() {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                return getRemoteSandboxedServicePackageNameInternal();

            }

            @Override
            @PermissionManuallyEnforced
            public String getRemoteSettingsServicePackageName() {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                return getRemoteSettingsServicePackageNameInternal();
            }

            @Override
            @PermissionManuallyEnforced
            public void requestCheckContent(
                    int featureType,
                    Bundle input,
                    @Nullable AndroidFuture cancellationSignalFuture,
                    @NonNull ICheckContentCallback callback)
                    throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                checkContentInternal(featureType, input, cancellationSignalFuture, callback);
            }

            @Override
            @PermissionManuallyEnforced
            public void requestIsFeatureEnabled(
                    int featureType,
                    @Nullable AndroidFuture cancellationSignalFuture,
                    @NonNull IIsFeatureEnabledCallback callback)
                    throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                isFeatureEnabledInternal(featureType, cancellationSignalFuture, callback);
            }

            @Override
            @PermissionManuallyEnforced
            @SuppressLint("SimpleManualPermissionEnforcement")
            public void onShellCommand(
                    FileDescriptor in,
                    FileDescriptor out,
                    FileDescriptor err,
                    String[] args,
                    ShellCallback callback,
                    ResultReceiver resultReceiver) {
                if (android.app.contentsafety.flags.Flags.enableContentsafety()) {
                    mContext.enforceCallingOrSelfPermission(
                            Manifest.permission.CHECK_CONTENT_SAFETY,
                            TAG);
                    onShellCommandInternal(
                            this,
                            in,
                            out,
                            err,
                            args,
                            callback,
                            resultReceiver);
                    return;
                }
            }
        };
    }

    private ICheckContentCallback wrapCheckContentCallback(
            AndroidFuture future, @NonNull ICheckContentCallback callback) {
        return new ICheckContentCallback.Stub() {
            @Override
            @PermissionManuallyEnforced
            public void onResult(Bundle contentSafetyResult) throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                callback.onResult(contentSafetyResult);
                future.complete(null);
            }
        };
    }

    private IIsFeatureEnabledCallback wrapIsFeatureEnabledCallback(
            AndroidFuture future, IIsFeatureEnabledCallback callback) {
        return new IIsFeatureEnabledCallback.Stub() {
            @Override
            @PermissionManuallyEnforced
            public void onSuccess(boolean result) throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                callback.onSuccess(result);
                future.complete(null);
            }

            @Override
            @PermissionManuallyEnforced
            public void onFailure(int failureStatus)
                    throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                callback.onFailure(failureStatus);
                future.completeExceptionally(new TimeoutException());
            }
        };
    }

    // Delegate onResult logic to a separate function to ensure the permission check is not
    // accidentally bypassed, raised by MissingEnforcePermissionHelper.
    private void getFeatureCallbackOnResult(@CheckLoadFeatureParams Bundle result, int featureType,
            Bundle input,
            AndroidFuture future,
            AndroidFuture cancellationSignalFuture,
            @NonNull ICheckContentCallback checkContentCallback)
            throws RemoteException {
        try {
            sanitizeCheckLoadFeatureParams(result);
            var unused =
                    mRemoteSandboxedService.postAsync(
                            service -> {
                                try {
                                    mFeatureBundleMap.put(featureType, result);
                                } catch (Exception ex) {
                                    Slog.w(
                                            TAG,
                                            TextUtils.formatSimple(
                                                    "Fail to store feature files into local"
                                                            + " map. %s",
                                                    ex));
                                }
                                service.requestLoadFeature(
                                        result,
                                        wrapCancellationFuture(
                                                cancellationSignalFuture),
                                        handleLoadFeatureCallback(
                                                featureType,
                                                input,
                                                future,
                                                cancellationSignalFuture,
                                                checkContentCallback));
                                return future.orTimeout(
                                        getIdleTimeoutMsContentSafetySandboxedService(),
                                        TimeUnit.MILLISECONDS);
                            });
            future.complete(null);
        } catch (BadParcelableException e) {
            Slog.w(TAG, "Feature files are not read-only", e);
            Bundle bundle = new Bundle();
            ArrayList<Integer> error = new ArrayList<>();
            error.add(
                    ContentSafetyManager.CONTENT_SAFETY_FEATURE_NOT_READ_ONLY_ERROR);
            bundle.putIntegerArrayList(Integer.toString(featureType), error);
            checkContentCallback.onResult(bundle);
            future.completeExceptionally(new TimeoutException());
        } catch (RuntimeException e) {
            Slog.e(TAG, "Fail to call loadFeature", e);
            Bundle bundle = new Bundle();
            ArrayList<Integer> error = new ArrayList<>();
            error.add(
                    ContentSafetyManager.CONTENT_SAFETY_LOAD_FEATURE_INVOKE_ERROR);
            bundle.putIntegerArrayList(Integer.toString(featureType), error);
            checkContentCallback.onResult(bundle);
        }
    }

    private void getFeatureOnError(int featureType, ICheckContentCallback checkContentCallback)
            throws RemoteException {
        Slog.e(TAG, "getFeature is failed");
        Bundle bundle = new Bundle();
        ArrayList<Integer> error = new ArrayList<>();
        error.add(ContentSafetyManager.CONTENT_SAFETY_LOAD_FEATURE_INTERNAL_ERROR);
        bundle.putIntegerArrayList(Integer.toString(featureType), error);
        checkContentCallback.onResult(bundle);
    }

    @NonNull
    private IGetFeatureCallback handleGetFeatureResult(
            int featureType,
            Bundle input,
            AndroidFuture future,
            AndroidFuture cancellationSignalFuture,
            @NonNull ICheckContentCallback checkContentCallback) {
        return new IGetFeatureCallback.Stub() {
            @Override
            @PermissionManuallyEnforced
            public void onResult(@CheckLoadFeatureParams Bundle result) throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                getFeatureCallbackOnResult(result,
                        featureType,
                        input,
                        future,
                        cancellationSignalFuture,
                        checkContentCallback);
            }

            @Override
            @PermissionManuallyEnforced
            public void onError(int errorCode) throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                getFeatureOnError(featureType, checkContentCallback);
            }
        };
    }

    private void loadFeatureCallbackOnResult(int featureType,
            Bundle input,
            AndroidFuture future,
            AndroidFuture cancellationSignalFuture,
            ICheckContentCallback checkContentCallback) throws RemoteException {
        Slog.i(TAG, "loadFeature is completed successfully");

        // call checkContent
        try {
            AndroidFuture checkContentResultFuture =
                    mRemoteSandboxedService.postAsync(
                            service -> {
                                service.requestCheckContent(
                                        featureType,
                                        input,
                                        wrapCancellationFuture(
                                                cancellationSignalFuture),
                                        wrapCheckContentCallback(
                                                future, checkContentCallback));
                                return future.orTimeout(
                                        getIdleTimeoutMsContentSafetySandboxedService(),
                                        TimeUnit.MILLISECONDS);
                            });
            Slog.i(TAG, "checkContent is completed successfully ");

            checkContentResultFuture =
                    checkContentResultFuture.whenCompleteAsync(
                            (c, e) -> ContentSafetyBundleUtil.tryCloseResource(input),
                            mResourceClosingExecutor);
            future.complete(null);
        } catch (RuntimeException e) {
            Slog.e(TAG, "Fail to call CheckContent", e);
            Bundle bundle = new Bundle();
            ArrayList<Integer> error = new ArrayList<>();
            error.add(
                    ContentSafetyManager.CONTENT_SAFETY_CHECK_CONTENT_INVOKE_ERROR);
            bundle.putIntegerArrayList(Integer.toString(featureType), error);
            checkContentCallback.onResult(bundle);
        }
    }

    private void loadFeatureCallbackOnError(
            int featureType,
            ICheckContentCallback checkContentCallback) throws RemoteException {
        Slog.e(TAG, "loadFeature failed");
        Bundle bundle = new Bundle();
        ArrayList<Integer> error = new ArrayList<>();
        error.add(ContentSafetyManager.CONTENT_SAFETY_LOAD_FEATURE_INTERNAL_ERROR);
        bundle.putIntegerArrayList(Integer.toString(featureType), error);
        checkContentCallback.onResult(bundle);
    }

    @NonNull
    private ILoadFeatureCallback handleLoadFeatureCallback(
            int featureType,
            Bundle input,
            AndroidFuture future,
            AndroidFuture cancellationSignalFuture,
            ICheckContentCallback checkContentCallback) {
        return new ILoadFeatureCallback.Stub() {
            @Override
            @PermissionManuallyEnforced
            public void onResult() throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                loadFeatureCallbackOnResult(
                        featureType,
                        input,
                        future,
                        cancellationSignalFuture,
                        checkContentCallback);
            }

            @Override
            @PermissionManuallyEnforced
            public void onError(int errorCode) throws RemoteException {
                mContext.enforceCallingPermission(Manifest.permission.CHECK_CONTENT_SAFETY, TAG);
                loadFeatureCallbackOnError(featureType, checkContentCallback);
            }
        };
    }

    /**
     * Returning the package name of the remote service.
     *
     * @param componentName the component name to retrieve the packageName.
     * @return a package name
     */
    @Nullable
    public String getRemoteConfiguredPackageName(ComponentName componentName) {
        try {
            if (componentName != null) {
                return componentName.getPackageName();
            }
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "Could not find resource", e);
        }

        return null;
    }

    private long getIdleTimeoutMsContentSafetySandboxedService() {
        return Settings.Secure.getLongForUser(
                mContext.getContentResolver(),
                // set 5 seconds idle time before initiate timeout
                Settings.Secure.CONTENT_SAFETY_SANDBOXED_IDLE_TIMEOUT_MS,
                TimeUnit.SECONDS.toMillis(5),
                mContext.getUserId());
    }

    private long getIdleTimeoutMsContentService() {
        return Settings.Secure.getLongForUser(
                mContext.getContentResolver(),
                // set 15 seconds before initiate timeout since download feature is called from
                // content safety service
                Settings.Secure.CONTENT_SAFETY_IDLE_TIMEOUT_MS,
                TimeUnit.SECONDS.toMillis(15),
                mContext.getUserId());
    }

    private long getIdleTimeoutMsSettingsService() {
        return Settings.Secure.getLongForUser(
                mContext.getContentResolver(),
                // set 5 seconds idle time before initiate timeout
                Settings.Secure.CONTENT_SAFETY_SETTINGS_IDLE_TIMEOUT_MS,
                TimeUnit.SECONDS.toMillis(5),
                mContext.getUserId());
    }

    private AndroidFuture<IBinder> wrapCancellationFuture(AndroidFuture future) {
        if (future == null) {
            return null;
        }
        AndroidFuture<IBinder> cancellationFuture = new AndroidFuture<>();
        var unused =
                cancellationFuture.whenCompleteAsync(
                        (c, e) -> {
                            if (e != null) {
                                Slog.e(
                                        TAG,
                                        "Error forwarding ICancellationSignal to manager layer",
                                        e);
                                future.completeExceptionally(e);
                            } else {
                                future.complete(
                                    new ICancellationSignal.Stub() {
                                        // Suppressing Lint check
                                        // SimpleManualPermissionEnforcement since cancel() is
                                        // not annotated with @EnforcePermission
                                        @Override
                                        @PermissionManuallyEnforced
                                        @SuppressLint("SimpleManualPermissionEnforcement")
                                        public void cancel() throws RemoteException {
                                            if (android.app.contentsafety.flags.Flags
                                                    .enableContentsafety()) {
                                                mContext.enforceCallingOrSelfPermission(
                                                        Manifest.permission.CHECK_CONTENT_SAFETY,
                                                        TAG);
                                                ICancellationSignal.Stub.asInterface(c).cancel();
                                            }
                                        }
                                    });
                            }
                        });
        return cancellationFuture;
    }

    private void ensureRemoteContentSafetyServicesInitialized() {
        synchronized (mLock) {
            setRemoteServiceComponentNames();

            if (mRemoteContentSafetySettingsService == null) {
                // System Service connect to the remote settings service with its own permission.
                Binder.withCleanCallingIdentity(
                        () ->
                                validateServiceRequiresPermission(
                                        mContentSafetySettingsComponentName,
                                        Manifest.permission.BIND_SETTINGS_CONTENT_SAFETY_SERVICE));
                mRemoteContentSafetySettingsService =
                        new RemoteContentSafetySettingsService(
                                mContext,
                                mContentSafetySettingsComponentName,
                                UserHandle.SYSTEM.getIdentifier());
            }

            if (mIsSandboxedServicePcc) {
                if (mRemoteContentSafetyPccService == null) {
                    // System Service connect to the remote PCC service with its own
                    // permission.
                    Binder.withCleanCallingIdentity(
                            () ->
                                    validateServiceRequiresPermission(
                                            mContentSafetyPccComponentName,
                                            Manifest.permission
                                                    .BIND_SANDBOXED_CONTENT_SAFETY_SERVICE));
                    mRemoteContentSafetyPccService = mPccServiceConnectionFactory.create(
                            mContentSafetyPccComponentName);
                }
                mRemoteSandboxedService = mRemoteContentSafetyPccService;
            } else {
                if (mRemoteContentSafetyIsolatedService == null) {
                    // System Service connect to the remote isolated service with its own
                    // permission.
                    Binder.withCleanCallingIdentity(
                            () ->
                                    validateServiceRequiresPermission(
                                            mContentSafetyIsolatedComponentName,
                                            Manifest.permission
                                                    .BIND_SANDBOXED_CONTENT_SAFETY_SERVICE));
                    mRemoteContentSafetyIsolatedService =
                            mIsolatedServiceConnectionFactory.create(
                                    mContentSafetyIsolatedComponentName);
                }
                mRemoteSandboxedService = mRemoteContentSafetyIsolatedService;
            }

            if (mRemoteContentSafetyService == null) {
                Binder.withCleanCallingIdentity(
                        () ->
                                validateServiceRequiresPermission(
                                        mContentSafetyComponentName,
                                        Manifest.permission.BIND_CONTENT_SAFETY_SERVICE));
                mRemoteContentSafetyService =
                        new RemoteContentSafetyService(
                                mContext,
                                mContentSafetyComponentName,
                                UserHandle.SYSTEM.getIdentifier());
            }

            // setup ServiceLifeCycleCallbacks for the Settings Service
            mRemoteContentSafetySettingsService.setServiceLifecycleCallbacks(
                    new ServiceConnector.ServiceLifecycleCallbacks<>() {
                        @Override
                        public void onConnected(@NonNull IContentSafetySettingsService service) {
                            mRemoteContentSafetyService.run(
                                    IContentSafetyService::notifySettingsServiceConnected);
                        }

                        @Override
                        public void onDisconnected(@NonNull IContentSafetySettingsService service) {
                            if (mRemoteContentSafetyService != null) {
                                mRemoteContentSafetyService.run(
                                        IContentSafetyService::notifySettingsServiceDisconnected);
                            }
                        }

                        @Override
                        public void onBinderDied() {
                            if (mRemoteContentSafetyService != null) {
                                mRemoteContentSafetyService.run(
                                        IContentSafetyService::notifySettingsServiceDisconnected);
                            }
                        }
                    });

            // Setup ServiceLifeCycleCallbacks for the Sandboxed Service
            mRemoteSandboxedService.setServiceLifecycleCallbacks(
                new ServiceConnector.ServiceLifecycleCallbacks<>() {
                    @Override
                    public void onConnected(
                            @NonNull IContentSafetySandboxedService service) {
                        mRemoteContentSafetyService.run(
                                IContentSafetyService::notifySandboxedServiceConnected);
                    }

                    @Override
                    public void onDisconnected(
                            @NonNull IContentSafetySandboxedService service) {
                        if (mRemoteContentSafetyService != null) {
                            mRemoteContentSafetyService.run(
                                    IContentSafetyService::notifySandboxedServiceDisconnected);
                        }
                    }

                    @Override
                    public void onBinderDied() {
                        if (mRemoteContentSafetyService != null) {
                            mRemoteContentSafetyService.run(
                                    IContentSafetyService::notifySandboxedServiceDisconnected);
                        }
                    }
                });
        }
    }

    private void validateServiceRequiresPermission(
            ComponentName serviceComponent,
            String requiredPermission) {
        try {
            ServiceInfo serviceInfo =
                    mContext.getPackageManager()
                            .getServiceInfo(
                                    serviceComponent,
                                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            checkServiceRequiresPermission(serviceInfo, requiredPermission);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(
                    TextUtils.formatSimple(
                            "Could not fetch service info for the remote service %s: %s",
                            serviceComponent.getClassName(), e),
                    e);

        }
    }

    private static void checkServiceRequiresPermission(
            ServiceInfo serviceInfo, String requiredPermission) {
        final String permission = serviceInfo.permission;
        if (!requiredPermission.equals(permission)) {
            throw new SecurityException(
                    TextUtils.formatSimple(
                            "Service %s requires %s permission. Found %s permission",
                            serviceInfo.getComponentName(),
                            requiredPermission,
                            serviceInfo.permission));
        }
    }

    protected void setRemoteServiceComponentNames() {
        try {
            synchronized (mLock) {
                if (mTemporaryServiceNames != null && mTemporaryServiceNames.length == 4) {
                    mContentSafetyComponentName = mTemporaryServiceNames[0];
                    mContentSafetySettingsComponentName = mTemporaryServiceNames[1];
                    mContentSafetyPccComponentName = mTemporaryServiceNames[2];
                    mContentSafetyIsolatedComponentName = mTemporaryServiceNames[3];
                    return;
                }

                mContentSafetyComponentName =
                        ComponentName.unflattenFromString(
                                mContext.getResources()
                                        .getString(R.string.config_defaultContentSafetyService));
                mContentSafetySettingsComponentName =
                        ComponentName.unflattenFromString(
                                mContext.getResources()
                                        .getString(
                                            R.string.config_defaultContentSafetySettingsService));
                mContentSafetyIsolatedComponentName =
                        ComponentName.unflattenFromString(
                                mContext.getResources()
                                        .getString(
                                            R.string.config_defaultContentSafetyIsolatedService));
                mContentSafetyPccComponentName =
                        ComponentName.unflattenFromString(
                                mContext.getResources()
                                    .getString(R.string.config_defaultContentSafetyPccService));
            }
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "Could not find resource", e);
        }
    }

    protected String[] getServiceNames() {
        try {
            synchronized (mLock) {
                if (mTemporaryServiceNames != null && mTemporaryServiceNames.length == 4) {
                    ComponentName isolatedServiceName = mTemporaryServiceNames[3];
                    return new String[] {
                            mTemporaryServiceNames[0].flattenToString(),
                            mTemporaryServiceNames[1].flattenToString(),
                            mTemporaryServiceNames[2].flattenToString(),
                            isolatedServiceName == null ? null
                                    : isolatedServiceName.flattenToString()
                    };
                }
                return new String [] {
                        mContext.getResources()
                                .getString(R.string.config_defaultContentSafetyService),
                        mContext.getResources()
                                .getString(R.string.config_defaultContentSafetySettingsService),
                        mContext.getResources()
                                .getString(R.string.config_defaultContentSafetyPccService),
                        mContext.getResources()
                                .getString(R.string.config_defaultContentSafetyIsolatedService)
                };
            }
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "Could not find resource", e);
            return new String[0];
        }
    }

    private int getSandboxedServiceType() {
        setRemoteServiceComponentNames();
        return (mContentSafetyPccComponentName != null
                || mContentSafetyIsolatedComponentName == null)
                ? SandboxedServiceType.SERVICE_TYPE_PCC
                : SandboxedServiceType.SERVICE_TYPE_ISOLATED;
    }

    /**
     * Set the names of the temporary services that will be used for testing purposes.
     *
     * @param componentNames temporary service names, should listed with the following order:
     *     ContentSafetyService, ContentSafetySettings, ContentSafetyPccService,
     *     ContentSafetyIsolatedService. Either Pcc or Isolated service name should be set at a time
     *     and set the other name null.
     * @param durationMs duration needed.
     */
    public void setTemporaryServices(@NonNull ComponentName[] componentNames, int durationMs) {
        Objects.requireNonNull(componentNames);
        enforceShellOnly(Binder.getCallingUid(), "setTemporaryServices");
        synchronized (mLock) {
            mTemporaryServiceNames = componentNames;
            if (mRemoteContentSafetyService != null) {
                mRemoteContentSafetyService.unbind();
                mRemoteContentSafetyService = null;
            }
            if (mRemoteContentSafetySettingsService != null) {
                mRemoteContentSafetySettingsService.unbind();
                mRemoteContentSafetySettingsService = null;
            }
            if (mRemoteContentSafetyPccService != null) {
                mRemoteContentSafetyPccService.unbind();
                mRemoteContentSafetyPccService = null;
            }
            if (mRemoteContentSafetyIsolatedService != null) {
                mRemoteContentSafetyIsolatedService.unbind();
                mRemoteContentSafetyIsolatedService = null;
            }
            if (mRemoteSandboxedService != null) {
                mRemoteSandboxedService.unbind();
                mRemoteSandboxedService = null;
            }

            if (durationMs != -1) {
                getTemporaryHandler()
                        .sendEmptyMessageDelayed(MSG_RESET_TEMPORARY_SERVICE, durationMs);
            }
        }
    }

    private synchronized Handler getTemporaryHandler() {
        if (mTemporaryHandler == null) {
            mTemporaryHandler =
                    new Handler(Looper.getMainLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            synchronized (mLock) {
                                if (msg.what == MSG_RESET_TEMPORARY_SERVICE) {
                                    resetTemporaryServices();
                                } else {
                                    Slog.wtf(TAG, "invalid handler msg: " + msg);
                                }
                            }
                        }
                    };
        }

        return mTemporaryHandler;
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

            mRemoteContentSafetyService = null;
            mRemoteContentSafetyIsolatedService = null;
            mRemoteContentSafetyPccService = null;
            mRemoteSandboxedService = null;
            mRemoteContentSafetySettingsService = null;
            mTemporaryServiceNames = new ComponentName[0];
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

    /**
     * {@link Bundle}s annotated with this type will be validated that they are in-effect read-only
     * when passed via Binder IPC. Following restrictions apply :
     *
     * <ul>
     *   <li>{@link PersistableBundle}s are allowed.
     *   <li>Any primitive types or their collections can be added as usual.
     *   <li>IBinder objects should *not* be added.
     *   <li>Parcelable data which has no active-objects, should be added as {@link
     *       Bundle#putByteArray}
     *   <li>Parcelables have active-objects, only following types will be allowed
     *       <ul>
     *         <li>{@link android.os.ParcelFileDescriptor} opened in {@link
     *             android.os.ParcelFileDescriptor#MODE_READ_ONLY}
     *       </ul>
     * </ul>
     *
     * In all other scenarios the system-server might throw a {@link
     * android.os.BadParcelableException} if the Bundle validation fails.
     *
     * @hide
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    public @interface CheckLoadFeatureParams {}

    private class ServiceConnectionFactory {
        private final int mBindingFlags;
        private final int mSandboxedServiceType;

        ServiceConnectionFactory(int sandboxedServiceType) {
            mSandboxedServiceType = sandboxedServiceType;
            int flags = BIND_FOREGROUND_SERVICE | BIND_INCLUDE_CAPABILITIES | BIND_AUTO_CREATE;
            // TODO: Should we add the following line?
            // flags |= Context.BIND_SHARED_ISOLATED_PROCESS;
            mBindingFlags = flags;
        }


        RemoteContentSafetySandboxedService create(@NonNull ComponentName componentName) {
            Intent remoteSandboxedServiceIntent =
                    new Intent(ContentSafetySandboxedService.SERVICE_INTERFACE)
                            .setComponent(componentName)
                            .setPackage(componentName.getPackageName());
            RemoteContentSafetySandboxedService connection =
                    new RemoteContentSafetySandboxedService(
                            mContext,
                            remoteSandboxedServiceIntent,
                            mBindingFlags,
                            UserHandle.SYSTEM.getIdentifier(),
                            IContentSafetySandboxedService.Stub::asInterface,
                            mSandboxedServiceType);
            connection.connect();
            return connection;
        }
    }
}
