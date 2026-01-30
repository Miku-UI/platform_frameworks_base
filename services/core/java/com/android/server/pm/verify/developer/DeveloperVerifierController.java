/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.pm.verify.developer;

import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_BYPASSED_REASON_UNSPECIFIED;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN;
import static android.os.Process.INVALID_UID;
import static android.os.Process.SYSTEM_UID;
import static android.provider.DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.DurationMillisLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.SigningInfo;
import android.content.pm.verify.developer.DeveloperVerificationSession;
import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.content.pm.verify.developer.DeveloperVerifierService;
import android.content.pm.verify.developer.IDeveloperVerificationSessionInterface;
import android.content.pm.verify.developer.IDeveloperVerifierService;
import android.net.Uri;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.server.pm.Computer;
import com.android.server.pm.PackageInstallerSession;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * This class manages the bind to the verifier agent installed on the device that implements
 * {@link DeveloperVerifierService} and handles all its interactions.
 */
public class DeveloperVerifierController {
    private static final String TAG = "VerifierController";
    private static final boolean DEBUG = false;

    /**
     * Configurable maximum amount of time in milliseconds to wait for a verifier to respond to
     * a verification request.
     * Flag type: {@code long}
     * Namespace: NAMESPACE_PACKAGE_MANAGER_SERVICE
     */
    private static final String PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS =
            "verification_request_timeout_millis";
    // Default duration to wait for a verifier to respond to a verification request.
    private static final long DEFAULT_VERIFICATION_REQUEST_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(10);
    /**
     * Configurable maximum amount of time in milliseconds that the verifier can request to extend
     * the verification request timeout duration to. This is the maximum amount of time the system
     * can wait for a request before it times out.
     * Flag type: {@code long}
     * Namespace: NAMESPACE_PACKAGE_MANAGER_SERVICE
     */
    private static final String PROPERTY_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS =
            "max_verification_request_extended_timeout_millis";
    // Max duration allowed to wait for a verifier to respond to a verification request.
    private static final long DEFAULT_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS =
            TimeUnit.MINUTES.toMillis(10);
    /**
     * Configurable maximum amount of time in milliseconds for the system to wait from the moment
     * when the installation session requires a verification, till when the request is delivered to
     * the verifier, pending the connection to be established. If the request has not been delivered
     * to the verifier within this amount of time, e.g., because the verifier has crashed or ANR'd,
     * the controller then sends a failure status back to the installation session.
     * Flag type: {@code long}
     * Namespace: NAMESPACE_PACKAGE_MANAGER_SERVICE
     */
    private static final String PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS =
            "verifier_connection_timeout_millis";
    // The maximum amount of time to wait from the moment when the session requires a verification,
    // till when the request is delivered to the verifier, pending the connection to be established.
    private static final long DEFAULT_VERIFIER_CONNECTION_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(10);

    /**
     * After the connection to the verifier is established, if the tracker is empty or becomes empty
     * after all the pending verification requests are resolved, automatically disconnect from the
     * verifier after this amount of time.
     */
    private static final long DISCONNECT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private static DeveloperVerifierController sInstance;

    private final Context mContext;
    private final Handler mHandler;

    // Map of userId -> remote verifier service for the user.
    @NonNull
    @GuardedBy("mRemoteServices")
    private final SparseArray<ServiceConnectorWrapper> mRemoteServices = new SparseArray<>();

    @NonNull
    private final Injector mInjector;

    // The developer verifier as specified by the system. Null if the system does not
    // specify a verifier.
    @Nullable
    private final ComponentName mDeveloperVerificationServiceProvider;

    // Repository of active verification sessions and their status (map of id -> tracker).
    @NonNull
    @GuardedBy("mVerificationStatusTrackers")
    private final SparseArray<DeveloperVerificationRequestStatusTracker>
            mVerificationStatusTrackers = new SparseArray<>();

    @GuardedBy("mVerificationStatusTrackers")
    // Counter of active verification sessions per user; must be synced with the trackers map.
    private final SparseIntArray mSessionsCountPerUser = new SparseIntArray();

    private final DeveloperVerifierExperimentProvider mExperimentProvider;

    /**
     * Get an instance of VerifierController.
     */
    public static DeveloperVerifierController getInstance(@NonNull Context context,
            @NonNull Handler handler,
            @Nullable ComponentName developerVerificationServiceProvider) {
        if (sInstance == null) {
            sInstance = new DeveloperVerifierController(
                    context, handler, developerVerificationServiceProvider, new Injector());
        }
        return sInstance;
    }

    @VisibleForTesting
    public DeveloperVerifierController(@NonNull Context context, @NonNull Handler handler,
            @Nullable ComponentName developerVerificationServiceProvider,
            @NonNull Injector injector) {
        mContext = context;
        mHandler = handler;
        mDeveloperVerificationServiceProvider = developerVerificationServiceProvider;
        mInjector = injector;
        mExperimentProvider = new DeveloperVerifierExperimentProvider(mHandler);
    }

    /**
     * Used by the public API that queries the component name of the developer verification service
     * provider.
     */
    @Nullable
    public ComponentName getVerifierComponentName() {
        return mDeveloperVerificationServiceProvider;
    }

    /**
     * Used by the installation session to get the package name of the developer verifier.
     * Note: there can be only one active verifier for all the users on the device.
     */
    @Nullable
    public String getVerifierPackageName() {
        if (mDeveloperVerificationServiceProvider == null) {
            return null;
        }
        return mDeveloperVerificationServiceProvider.getPackageName();
    }

    /**
     * Return the UID of the verifier that is bound to the system. If the verifier has not been
     * bound, return INVALID_UID.
     */
    public int getVerifierUidIfBound(int userId) {
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService == null) {
                return INVALID_UID;
            }
            return remoteService.getUid();
        }
    }

    /**
     * Called to start querying and binding to a qualified verifier agent.
     *
     * @return False if a qualified verifier agent doesn't exist on device, so that the system can
     * handle this situation immediately after the call.
     * <p>
     * Notice that since this is an async call, even if this method returns true, it doesn't
     * necessarily mean that the binding connection was successful. However, the system will only
     * try to bind once per installation session, so that it doesn't waste resource by repeatedly
     * trying to bind if the verifier agent isn't available during a short amount of time.
     * <p>
     * If the verifier agent exists but cannot be started for some reason, all the notify* methods
     * in this class will fail asynchronously and quietly. The system will learn about the failure
     * after receiving the failure from
     * {@link PackageInstallerSession.DeveloperVerifierCallback#onConnectionFailed}.
     */
    public boolean bindToVerifierServiceIfNeeded(Supplier<Computer> snapshotSupplier, int userId,
            Runnable onConnectionEstablished) {
        if (DEBUG) {
            Slog.i(TAG, "Requesting to bind to the verifier service for user " + userId);
        }
        final String verifierPackageName = getVerifierPackageName();
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService != null) {
                // The system has already bound to a verifier. Check if it's still valid.
                if (!remoteService.getVerifierPackageName().equals(verifierPackageName)) {
                    // The verifier name has been overridden. Clear the connected remote service.
                    destroy(userId);
                } else {
                    // The requested verifier is still connected. Directly return.
                    if (DEBUG) {
                        Slog.i(TAG, "Verifier service is already connected for user " + userId);
                    }
                    return true;
                }
            }
        }
        if (verifierPackageName == null) {
            // The system has no default verifier specified, and it has not been overridden either
            return false;
        }
        // Start establishing a new connection. First check if the verifier is installed.
        final int verifierUid = snapshotSupplier.get().getPackageUidInternal(
                verifierPackageName, 0, userId, /* callingUid= */ SYSTEM_UID);
        if (verifierUid == INVALID_UID) {
            if (DEBUG) {
                Slog.i(TAG, "Unable to find the UID of the qualified verifier."
                        + " Is it installed on " + userId + "?");
            }
            return false;
        }
        final var remoteService = mInjector.getRemoteService(
                verifierPackageName, mContext, userId, mHandler);
        final var remoteServiceWrapper = new ServiceConnectorWrapper(
                remoteService, verifierUid, verifierPackageName);
        remoteService.setServiceLifecycleCallbacks(
                new ServiceConnector.ServiceLifecycleCallbacks<>() {
                    @Override
                    public void onConnected(@NonNull IDeveloperVerifierService service) {
                        Slog.i(TAG, "Verifier " + verifierPackageName + " is connected"
                                + " on user " + userId);
                        // Logging the success of connecting to the verifier.
                        onConnectionEstablished.run();
                        // Aggressively auto-disconnect until verification requests are sent out
                        startAutoDisconnectCountdown(
                                remoteServiceWrapper.getAutoDisconnectCallback());
                    }

                    @Override
                    public void onDisconnected(@NonNull IDeveloperVerifierService service) {
                        Slog.w(TAG,
                                "Verifier " + verifierPackageName + " is disconnected"
                                        + " on user " + userId);
                        destroy(userId);
                        // Cancel auto-disconnect because the verifier is already disconnected
                        stopAutoDisconnectCountdown(
                                remoteServiceWrapper.getAutoDisconnectCallback());
                    }

                    @Override
                    public void onBinderDied() {
                        Slog.w(TAG, "Verifier " + verifierPackageName + " has died"
                                + " on user " + userId);
                        destroy(userId);
                        // Cancel auto-disconnect because the binder has already died
                        stopAutoDisconnectCountdown(
                                remoteServiceWrapper.getAutoDisconnectCallback());
                    }
                });
        synchronized (mRemoteServices) {
            mRemoteServices.put(userId, remoteServiceWrapper);
        }
        if (DEBUG) {
            Slog.i(TAG, "Connecting to a qualified verifier: " + verifierPackageName
                    + " on user " + userId);
        }
        AndroidFuture<IDeveloperVerifierService> unusedFuture = remoteService.connect();
        return true;
    }

    private void destroy(int userId) {
        synchronized (mRemoteServices) {
            if (mRemoteServices.contains(userId)) {
                var remoteService = mRemoteServices.get(userId);
                if (remoteService != null) {
                    remoteService.getService().unbind();
                    mRemoteServices.remove(userId);
                }
            }
        }
    }

    private void startAutoDisconnectCountdown(Runnable autoDisconnectCallback) {
        // If there is already a task to disconnect, remove it and restart the countdown
        stopAutoDisconnectCountdown(autoDisconnectCallback);
        mHandler.postDelayed(autoDisconnectCallback, DISCONNECT_TIMEOUT_MILLIS);
    }

    private void stopAutoDisconnectCountdown(Runnable autoDisconnectCallback) {
        mInjector.removeCallbacks(mHandler, autoDisconnectCallback);
    }

    /**
     * Called to notify the bound verifier agent that a package name is available and will soon be
     * requested for verification.
     */
    public void notifyPackageNameAvailable(@NonNull String packageName, int userId) {
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService == null) {
                if (DEBUG) {
                    Slog.i(TAG, "Verifier is not connected. Not notifying package name available");
                }
                return;
            }
            // Best effort. We don't check for the result.
            remoteService.getService().run(service -> {
                if (DEBUG) {
                    Slog.i(TAG, "Notifying package name available for " + packageName);
                }
                service.onPackageNameAvailable(packageName);
            });
        }
    }

    /**
     * Called to notify the bound verifier agent that a package previously notified via
     * {@link DeveloperVerifierService#onPackageNameAvailable(String)}
     * will no longer be requested for verification, possibly because the installation is canceled.
     */
    public void notifyVerificationCancelled(@NonNull String packageName, int userId) {
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService == null) {
                if (DEBUG) {
                    Slog.i(TAG, "Verifier is not connected. Not notifying verification cancelled");
                }
                return;
            }
            // Best effort. We don't check for the result.
            remoteService.getService().run(service -> {
                if (DEBUG) {
                    Slog.i(TAG, "Notifying verification cancelled for " + packageName);
                }
                service.onVerificationCancelled(packageName);
            });
        }
    }

    /**
     * Called to notify the bound verifier agent that a package that's pending installation needs
     * to be verified right now.
     * <p>The verification request must be sent to the verifier as soon as the verifier is
     * connected. If the connection cannot be made within the specified time limit from
     * when the request is sent out, we consider the verification to be failed and notify the
     * installation session.</p>
     * <p>If a response is not returned from the verifier agent within a timeout duration from the
     * time the request is sent to the verifier, the verification will be considered a failure.</p>
     *
     * @param retry whether this request is for retrying a previously incomplete verification.
     */
    public boolean startVerificationSession(Supplier<Computer> snapshotSupplier, int userId,
            int installationSessionId, String packageName,
            Uri stagedPackageUri, SigningInfo signingInfo,
            List<SharedLibraryInfo> declaredLibraries,
            @PackageInstaller.DeveloperVerificationPolicy int verificationPolicy,
            @Nullable PersistableBundle extensionParams,
            PackageInstallerSession.DeveloperVerifierCallback callback,
            Runnable onConnectionEstablished, boolean retry) {
        // Try connecting to the verifier if not already connected
        if (!bindToVerifierServiceIfNeeded(snapshotSupplier, userId, onConnectionEstablished)) {
            return false;
        }
        // For now, the verification id is the same as the installation session id.
        final int verificationId = installationSessionId;
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService == null) {
                if (DEBUG) {
                    Slog.i(TAG, "Verifier is not connected. Not notifying verification required");
                }
                // Normally this should not happen because we just tried to bind. But if the
                // verifier just crashed or just became unavailable, we should notify the
                // installation session so it can finish with a verification failure.
                return false;
            }
            final DeveloperVerificationSession session = new DeveloperVerificationSession(
                    /* id= */ verificationId,
                    /* installSessionId= */ installationSessionId,
                    packageName, stagedPackageUri, signingInfo, declaredLibraries, extensionParams,
                    verificationPolicy, new DeveloperVerificationSessionInterface(callback));
            AndroidFuture<Void> unusedFuture = remoteService.getService().post(service -> {
                if (!retry) {
                    if (DEBUG) {
                        Slog.i(TAG, "Notifying verification required for session "
                                + verificationId);
                    }
                    service.onVerificationRequired(session);
                } else {
                    if (DEBUG) {
                        Slog.i(TAG, "Notifying verification retry for session "
                                + verificationId);
                    }
                    service.onVerificationRetry(session);
                }
            }).orTimeout(mInjector.getVerifierConnectionTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((res, err) -> {
                        if (err != null) {
                            Slog.e(TAG, "Error notifying verification request for session "
                                    + verificationId, err);
                            // Notify the installation session so it can finish with verification
                            // failure.
                            callback.onConnectionFailed();
                        }
                    });
            // We've sent out a new verification request, so stop the auto-disconnection countdown.
            stopAutoDisconnectCountdown(remoteService.getAutoDisconnectCallback());
        }
        // Keep track of the session status with the ID. Start counting down the session timeout.
        final long defaultTimeoutMillis = mInjector.getVerificationRequestTimeoutMillis();
        final long maxExtendedTimeoutMillis = mInjector.getMaxVerificationExtendedTimeoutMillis();
        final DeveloperVerificationRequestStatusTracker
                tracker = new DeveloperVerificationRequestStatusTracker(
                defaultTimeoutMillis, maxExtendedTimeoutMillis, mInjector, userId);
        synchronized (mVerificationStatusTrackers) {
            mVerificationStatusTrackers.put(verificationId, tracker);
            if (mSessionsCountPerUser.indexOfKey(userId) < 0) {
                mSessionsCountPerUser.put(userId, 0);
            }
            final int sessionsCount = mSessionsCountPerUser.get(userId);
            mSessionsCountPerUser.put(userId, sessionsCount + 1);
        }
        startTimeoutCountdown(verificationId, tracker, callback, defaultTimeoutMillis);
        return true;
    }

    private void startTimeoutCountdown(int verificationId,
            DeveloperVerificationRequestStatusTracker tracker,
            PackageInstallerSession.DeveloperVerifierCallback callback, long delayMillis) {
        mHandler.postDelayed(() -> {
            if (DEBUG) {
                Slog.i(TAG, "Checking request timeout for " + verificationId);
            }
            if (!tracker.isTimeout()) {
                if (DEBUG) {
                    Slog.i(TAG, "Timeout is not met for " + verificationId + "; check later.");
                }
                // If the current session is not timed out yet, check again later.
                startTimeoutCountdown(verificationId, tracker, callback,
                        /* delayMillis= */ tracker.getRemainingTime());
            } else {
                if (DEBUG) {
                    Slog.i(TAG, "Request " + verificationId + " has timed out.");
                }
                // The request has timed out. Notify the installation session.
                callback.onTimeout();
                // Remove status tracking and stop the timeout countdown
                removeStatusTracker(verificationId);
            }
        }, /* token= */ tracker, delayMillis);
    }

    /**
     * Called to notify the bound verifier agent that a verification request has timed out.
     */
    public void notifyVerificationTimeout(int verificationId, int userId) {
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService == null) {
                if (DEBUG) {
                    Slog.i(TAG,
                            "Verifier is not connected. Not notifying timeout for "
                                    + verificationId);
                }
                return;
            }
            AndroidFuture<Void> unusedFuture = remoteService.getService().post(service -> {
                if (DEBUG) {
                    Slog.i(TAG, "Notifying timeout for " + verificationId);
                }
                service.onVerificationTimeout(verificationId);
            }).whenComplete((res, err) -> {
                if (err != null) {
                    Slog.e(TAG, "Error notifying VerificationTimeout for session "
                            + verificationId, err);
                }
            });
        }
    }

    /**
     * Remove a status tracker after it's no longer needed.
     */
    private void removeStatusTracker(int verificationId) {
        if (DEBUG) {
            Slog.i(TAG, "Removing status tracking for verification " + verificationId);
        }
        synchronized (mVerificationStatusTrackers) {
            final DeveloperVerificationRequestStatusTracker trackerRemoved =
                    mVerificationStatusTrackers.removeReturnOld(verificationId);
            if (trackerRemoved != null) {
                // Stop the request timeout countdown
                mInjector.stopTimeoutCountdown(mHandler, /* token= */ trackerRemoved);
                final int userId = trackerRemoved.getUserId();
                final int sessionCountForUser = mSessionsCountPerUser.get(userId);
                if (sessionCountForUser >= 1) {
                    // Decrement the sessions count but don't go beyond zero
                    mSessionsCountPerUser.put(userId, sessionCountForUser - 1);
                }
                // Schedule auto-disconnect if there's no more active session on the user
                if (mSessionsCountPerUser.get(userId) == 0) {
                    maybeScheduleAutoDisconnect(userId);
                }
            }
        }
    }

    private void maybeScheduleAutoDisconnect(int userId) {
        synchronized (mRemoteServices) {
            final ServiceConnectorWrapper service = mRemoteServices.get(userId);
            if (service == null) {
                // Already unbound on this user
                return;
            }
            // Schedule a job to disconnect from the verifier on this user
            startAutoDisconnectCountdown(service.getAutoDisconnectCallback());
        }
    }

    /**
     * Assert that the calling UID is the same as the UID of the currently connected verifier.
     */
    public void assertCallerIsCurrentVerifier(int callingUid) {
        final int userId = UserHandle.getUserId(callingUid);
        synchronized (mRemoteServices) {
            var remoteService = mRemoteServices.get(userId);
            if (remoteService == null) {
                throw new IllegalStateException(
                        "Unable to proceed because the verifier has been disconnected"
                                + " for user " + userId);
            }
            if (callingUid != remoteService.getUid()) {
                throw new IllegalStateException(
                        "Calling uid " + callingUid + " is not the current verifier.");
            }
        }
    }

    // This class handles requests from the remote verifier
    private class DeveloperVerificationSessionInterface extends
            IDeveloperVerificationSessionInterface.Stub {
        private final PackageInstallerSession.DeveloperVerifierCallback mCallback;

        DeveloperVerificationSessionInterface(
                PackageInstallerSession.DeveloperVerifierCallback callback) {
            mCallback = callback;
        }

        public @CurrentTimeMillisLong long getTimeoutTimeMillis(int verificationId) {
            assertCallerIsCurrentVerifier(getCallingUid());
            synchronized (mVerificationStatusTrackers) {
                final DeveloperVerificationRequestStatusTracker tracker =
                        mVerificationStatusTrackers.get(verificationId);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + verificationId
                            + " doesn't exist or has finished");
                }
                return tracker.getTimeoutTime();
            }
        }

        public @DurationMillisLong long extendTimeoutMillis(int verificationId,
                @DurationMillisLong long additionalMillis) {
            assertCallerIsCurrentVerifier(getCallingUid());
            synchronized (mVerificationStatusTrackers) {
                final DeveloperVerificationRequestStatusTracker tracker =
                        mVerificationStatusTrackers.get(verificationId);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + verificationId
                            + " doesn't exist or has finished");
                }
                mCallback.onTimeoutExtensionRequested();
                return tracker.extendTimeoutMillis(additionalMillis);
            }
        }

        @Override
        public boolean setVerificationPolicy(int verificationId,
                @PackageInstaller.DeveloperVerificationPolicy int policy) {
            assertCallerIsCurrentVerifier(getCallingUid());
            synchronized (mVerificationStatusTrackers) {
                final DeveloperVerificationRequestStatusTracker tracker =
                        mVerificationStatusTrackers.get(verificationId);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + verificationId
                            + " doesn't exist or has finished");
                }
            }
            return mCallback.onVerificationPolicyOverridden(policy);
        }

        @Override
        public void reportVerificationIncomplete(int id,
                @DeveloperVerificationSession.DeveloperVerificationIncompleteReason int reason) {
            assertCallerIsCurrentVerifier(getCallingUid());
            if (reason < DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN
                    || reason > DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE) {
                throw new IllegalArgumentException("Verification session " + id
                        + " reported invalid incomplete_reason code " + reason);
            }
            final DeveloperVerificationRequestStatusTracker tracker;
            synchronized (mVerificationStatusTrackers) {
                tracker = mVerificationStatusTrackers.get(id);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + id
                            + " doesn't exist or has finished");
                }
            }
            mCallback.onVerificationIncompleteReceived(reason);
            // Remove status tracking and stop the timeout countdown
            removeStatusTracker(id);
        }

        @Override
        public void reportVerificationComplete(int id,
                DeveloperVerificationStatus verificationStatus,
                @Nullable PersistableBundle extensionResponse) {
            assertCallerIsCurrentVerifier(getCallingUid());
            final DeveloperVerificationRequestStatusTracker tracker;
            synchronized (mVerificationStatusTrackers) {
                tracker = mVerificationStatusTrackers.get(id);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + id
                            + " doesn't exist or has finished");
                }
            }
            mCallback.onVerificationCompleteReceived(verificationStatus, extensionResponse);
            // Remove status tracking and stop the timeout countdown
            removeStatusTracker(id);
        }

        @Override
        public void reportVerificationBypassed(int id, int bypassReason) {
            assertCallerIsCurrentVerifier(getCallingUid());
            final DeveloperVerificationRequestStatusTracker tracker;
            synchronized (mVerificationStatusTrackers) {
                tracker = mVerificationStatusTrackers.get(id);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + id
                            + " doesn't exist or has finished");
                }
            }
            if (bypassReason <= DEVELOPER_VERIFICATION_BYPASSED_REASON_UNSPECIFIED) {
                throw new IllegalArgumentException("Verification session " + id
                        + " reported invalid bypass_reason code " + bypassReason);
            }
            mCallback.onVerificationBypassedReceived(bypassReason);
            // Remove status tracking and stop the timeout countdown
            removeStatusTracker(id);
        }
    }

    /**
     * Add an experiment to the experiment provider.
     * <p>Notice that invalid status codes will be ignored. Valid status codes are defined in
     * {@link DeveloperVerificationStatusInternal}.
     * </p>
     */
    public void addExperiment(String packageName, int verificationPolicy, List<Integer> status) {
        mExperimentProvider.addExperiment(packageName, verificationPolicy, status);
    }

    /**
     * Clear the experiment associated with a package name if it exists. If the package name is
     * null, clear all experiments.
     */
    public void clearExperiment(@Nullable String packageName) {
        mExperimentProvider.clearExperiment(packageName);
    }

    /**
     * Check if there is an experiment for the given package.
     */
    public boolean hasExperiments(String packageName) {
        return mExperimentProvider.hasExperiments(packageName);
    }

    /**
     * Start a local experiment for the given package.
     */
    public void startLocalExperiment(String packageName,
            PackageInstallerSession.DeveloperVerifierCallback callback) {
        mExperimentProvider.runNextExperiment(packageName, callback);
    }

    private static class ServiceConnectorWrapper {
        // Remote service that receives verification requests
        private final @NonNull ServiceConnector<IDeveloperVerifierService> mRemoteService;
        // UID of the remote service which includes the userId as part of it
        private final int mUid;
        // Package name of the verifier that was bound to. This can be different from the verifier
        // originally specified by the system.
        private final @NonNull String mVerifierPackageName;
        private final @NonNull Runnable mAutoDisconnectCallback;
        ServiceConnectorWrapper(@NonNull ServiceConnector<IDeveloperVerifierService> service,
                int uid, @NonNull String verifierPackageName) {
            mRemoteService = service;
            mUid = uid;
            mVerifierPackageName = verifierPackageName;
            mAutoDisconnectCallback = mRemoteService::unbind;
        }
        ServiceConnector<IDeveloperVerifierService> getService() {
            return mRemoteService;
        }
        int getUid() {
            return mUid;
        }
        @NonNull String getVerifierPackageName() {
            return mVerifierPackageName;
        }
        @NonNull Runnable getAutoDisconnectCallback() {
            return mAutoDisconnectCallback;
        }
    }

    @VisibleForTesting
    public static class Injector {
        /**
         * Mock this method to inject the remote service to enable unit testing.
         */
        @NonNull
        public ServiceConnector<IDeveloperVerifierService> getRemoteService(
                @NonNull String verifierPackageName, @NonNull Context context, int userId,
                @NonNull Handler handler) {
            final Intent intent = new Intent(PackageManager.ACTION_VERIFY_DEVELOPER);
            intent.setPackage(verifierPackageName);
            return new ServiceConnector.Impl<>(
                    context, intent, Context.BIND_AUTO_CREATE, userId,
                    IDeveloperVerifierService.Stub::asInterface) {
                @Override
                protected Handler getJobHandler() {
                    return handler;
                }

                @Override
                protected long getRequestTimeoutMs() {
                    return getVerificationRequestTimeoutMillis();
                }

                @Override
                protected long getAutoDisconnectTimeoutMs() {
                    // Do not auto-disconnect here; let VerifierController decide when to disconnect
                    return -1;
                }
            };
        }

        /**
         * This is added so we can mock timeouts in the unit tests.
         */
        public long getCurrentTimeMillis() {
            return System.currentTimeMillis();
        }

        /**
         * This is added so that we don't need to mock Handler.removeCallbacksAndEqualMessages
         * which is final.
         */
        public void stopTimeoutCountdown(Handler handler, Object token) {
            handler.removeCallbacksAndEqualMessages(token);
        }

        /**
         * This is added so that we don't need to mock Handler.removeCallbacks which is final.
         */
        public void removeCallbacks(Handler handler, Runnable callback) {
            handler.removeCallbacks(callback);
        }


        /**
         * This is added so that we can mock the verification request timeout duration without
         * calling into DeviceConfig.
         */
        public long getVerificationRequestTimeoutMillis() {
            return getVerificationRequestTimeoutMillisFromDeviceConfig();
        }

        /**
         * This is added so that we can mock the maximum request timeout duration without
         * calling into DeviceConfig.
         */
        public long getMaxVerificationExtendedTimeoutMillis() {
            return getMaxVerificationExtendedTimeoutMillisFromDeviceConfig();
        }

        /**
         * This is added so that we can mock the maximum connection timeout duration without
         * calling into DeviceConfig.
         */
        public long getVerifierConnectionTimeoutMillis() {
            return getVerifierConnectionTimeoutMillisFromDeviceConfig();
        }

        private static long getVerificationRequestTimeoutMillisFromDeviceConfig() {
            return DeviceConfig.getLong(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                    PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                    DEFAULT_VERIFICATION_REQUEST_TIMEOUT_MILLIS);
        }

        private static long getMaxVerificationExtendedTimeoutMillisFromDeviceConfig() {
            return DeviceConfig.getLong(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                    PROPERTY_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS,
                    DEFAULT_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS);
        }

        private static long getVerifierConnectionTimeoutMillisFromDeviceConfig() {
            return DeviceConfig.getLong(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                    PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS,
                    DEFAULT_VERIFIER_CONNECTION_TIMEOUT_MILLIS);
        }
    }
}
