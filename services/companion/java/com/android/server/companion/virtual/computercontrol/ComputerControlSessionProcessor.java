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

package com.android.server.companion.virtual.computercontrol;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_USER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSessionParams.MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17;

import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__CALLER_NOT_ALLOWED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__INVALID_TARGET_APPLICATION;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__MANAGED_POLICY_DISABLED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__NAME_NOT_UNIQUE;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__SESSION_PENDING_NOTIFICATION_FAILED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles creation and lifecycle of {@link ComputerControlSession}s.
 *
 * <p>This class enforces session creation policies, such as limiting the number of concurrent
 * sessions and preventing creation when the device is locked.
 */
public final class ComputerControlSessionProcessor {

    private static final String TAG = ComputerControlSessionProcessor.class.getSimpleName();

    // TODO(b/419548594): Make this configurable.
    @VisibleForTesting
    static final int MAXIMUM_CONCURRENT_SESSIONS = 1;

    @Nullable
    private final String mReferenceDisplayAddress;

    private final Context mContext;
    private final KeyguardManager mKeyguardManager;
    private final AppOpsManager mAppOpsManager;
    private final PackageManager mPackageManager;
    private final UserManager mUserManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final DevicePolicyManagerInternal mDevicePolicyManagerInternal;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final VirtualDeviceManagerInternal mVirtualDeviceManagerInternal;
    private final VirtualDeviceFactory mVirtualDeviceFactory;
    private final PendingIntentFactory mPendingIntentFactory;
    private final ComputerControlAllowlistController mAllowlistController;

    // Lazily initialized on-demand by getAuthenticationPolicyManager()
    @Nullable
    private AuthenticationPolicyManager mAuthenticationPolicyManager;

    /** The binders of all currently active sessions. */
    @GuardedBy("mSessions")
    private final ArraySet<ComputerControlSessionImpl> mSessions = new ArraySet<>();

    private final Object mHandlerThreadLock = new Object();
    @GuardedBy("mHandlerThreadLock")
    @Nullable
    private ServiceThread mHandlerThread;
    @SuppressWarnings("NullAway") // Session lifecycle makes this @NonNull, though hard to enforce
    private Handler mHandler;

    public ComputerControlSessionProcessor(
            Context context, VirtualDeviceManagerInternal virtualDeviceManagerInternal,
            VirtualDeviceFactory virtualDeviceFactory) {
        this(context, virtualDeviceManagerInternal, virtualDeviceFactory,
                ComputerControlSessionProcessor::createPendingIntent,
                new ComputerControlAllowlistController(context));
    }

    @VisibleForTesting
    ComputerControlSessionProcessor(
            Context context, VirtualDeviceManagerInternal virtualDeviceManagerInternal,
            VirtualDeviceFactory virtualDeviceFactory,
            PendingIntentFactory pendingIntentFactory,
            ComputerControlAllowlistController allowlistController) {
        mContext = context;
        mVirtualDeviceManagerInternal = virtualDeviceManagerInternal;
        mVirtualDeviceFactory = virtualDeviceFactory;
        mPendingIntentFactory = pendingIntentFactory;
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mPackageManager = context.getPackageManager();
        mUserManager = context.getSystemService(UserManager.class);
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mDevicePolicyManagerInternal = LocalServices.getService(DevicePolicyManagerInternal.class);
        mActivityTaskManagerInternal = LocalServices.getService(ActivityTaskManagerInternal.class);
        mAllowlistController = allowlistController;
        mReferenceDisplayAddress = context.getString(
                R.string.config_computerControlReferenceDisplayPhysicalAddress);
    }

    /** Perform initialization tasks (if any). */
    public void initialize() {
        mAllowlistController.initialize();
    }

    @VisibleForTesting
    void processNewSessionRequest(@NonNull IApplicationThread appThread,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        processNewSessionRequest(
                ComputerControlSessionRequest.create(
                        mContext, appThread, attributionSource, params, callback));
    }

    /**
     * Process a new session creation request.
     *
     * <p>A new session will be created. In case of failure, the
     * {@link IComputerControlSessionCallback#onSessionCreationFailed} method on the provided
     * {@code callback} will be invoked.
     */
    public void processNewSessionRequest(@NonNull ComputerControlSessionRequest request) {
        validateRequest(request);
        startHandlerThreadIfNeeded();

        final int opResult = mAppOpsManager.noteOpNoThrow(
                AppOpsManager.OP_COMPUTER_CONTROL, request.attributionSource(),
                "create session");
        final List<String> targetPackagesForConsentRequest = new ArrayList<>();
        if (opResult == AppOpsManager.MODE_IGNORED || opResult == AppOpsManager.MODE_ERRORED) {
            Slog.w(TAG, "No permission to request computer control session: " + request.name());
            dispatchSessionCreationFailed(request, ComputerControlSession.ERROR_PERMISSION_DENIED);
            return;
        }
        if (opResult == AppOpsManager.MODE_ALLOWED) {
            if (android.companion.virtualdevice.flags.Flags.computerControlPerAppConsent()) {
                for (int i = 0; i < request.params().getTargetPackageNames().size(); i++) {
                    final String packageName = request.params().getTargetPackageNames().get(i);
                    if (!mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(
                            request.ownerUid(), request.ownerPackageName(), packageName)) {
                        targetPackagesForConsentRequest.add(packageName);
                    }
                }
            }
        } else {
            targetPackagesForConsentRequest.addAll(request.params().getTargetPackageNames());
        }
        if (targetPackagesForConsentRequest.isEmpty()) {
            // Start session if no additional consent required
            mHandler.post(() -> createSession(request));
            return;
        }

        synchronized (mSessions) {
            if (!checkSessionCreationPreconditionsLocked(request)) {
                return;
            }
        }

        final ResultReceiver resultReceiver = new ConsentResultReceiver(request).prepareForIpc();
        final Intent intent = new Intent(ComputerControlSession.ACTION_REQUEST_ACCESS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_UID, request.ownerUid())
                .putExtra(Intent.EXTRA_PACKAGE_NAME, request.ownerPackageName())
                .putExtra(Intent.EXTRA_PACKAGES,
                        targetPackagesForConsentRequest.toArray(new String[0]))
                .putExtra(Intent.EXTRA_RESULT_RECEIVER, resultReceiver);
        final PendingIntent pendingIntent =
                mPendingIntentFactory.create(mContext, request.ownerUid(), intent);
        try {
            request.callback().onSessionPending(pendingIntent);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + request.name()
                    + " about pending session");
            ComputerControlStatsController.writeFailedSessionWithStatsReason(
                    mPackageManager, request.attributionSource(), request.params(),
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__SESSION_PENDING_NOTIFICATION_FAILED
            );
        }
    }

    private void validateRequest(@NonNull ComputerControlSessionRequest request) {
        // TODO: b/445856399 - Support managed profiles.
        Binder.withCleanCallingIdentity(() -> {
            if (!isComputerControlAvailableForUser(request.ownerUserId())) {
                ComputerControlStatsController.writeFailedSessionWithStatsReason(
                        mPackageManager, request.attributionSource(), request.params(),
                        COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__MANAGED_POLICY_DISABLED);
                throw new SecurityException(
                        "Managed profiles are not allowed to use Computer Control.");
            }
        });

        if (!mAllowlistController.isPackageAllowedToCreateSession(
                request.ownerPackageName(), request.ownerPackageManager(),
                request.ownerUser(), request.params().getTargetComputerControlVersion())) {
            ComputerControlStatsController.writeFailedSessionWithStatsReason(
                    mPackageManager, request.attributionSource(), request.params(),
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__CALLER_NOT_ALLOWED);
            throw new SecurityException("Caller " + request.ownerPackageName()
                    + " is not allowed to create a session");
        }

        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (Objects.equals(request.ownerPackageName(), session.getOwnerPackageName())
                        && Objects.equals(request.name(), session.getName())) {
                    ComputerControlStatsController.writeFailedSessionWithStatsReason(
                            mPackageManager, request.attributionSource(), request.params(),
                            COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__NAME_NOT_UNIQUE);
                    throw new IllegalArgumentException("Session name must be unique");
                }
            }
        }

        for (int i = 0; i < request.params().getTargetPackageNames().size(); i++) {
            final String packageName = request.params().getTargetPackageNames().get(i);

            if (!mAllowlistController.isPackageAutomatable(
                    packageName, request.ownerPackageName(), request.ownerPackageManager())) {
                ComputerControlStatsController.writeFailedSessionWithStatsReason(
                        mPackageManager, request.attributionSource(), request.params(),
                        COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__INVALID_TARGET_APPLICATION
                );
                throw new IllegalArgumentException(
                        "Invalid target package for ComputerControl: " + packageName);
            }
        }
    }

    /**
     * Returns whether the computer control functionality is available for the caller.
     */
    public boolean isComputerControlAvailable(@NonNull AttributionSource attributionSource,
            int targetComputerControlVersion) {
        final UserHandle ownerUser = UserHandle.getUserHandleForUid(attributionSource.getUid());
        final Context ownerContext;
        final long token = Binder.clearCallingIdentity();
        try {
            if (!isComputerControlAvailableForUser(ownerUser.getIdentifier())) {
                return false;
            }
            ownerContext = mContext.createContextAsUser(ownerUser, /* flags = */ 0);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        final PackageManager ownerPackageManager = ownerContext.getPackageManager();
        final String ownerPackageName = attributionSource.getPackageName();
        try {
            return mAllowlistController.isPackageAllowedToCreateSession(ownerPackageName,
                    ownerPackageManager, ownerUser, targetComputerControlVersion);
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean isComputerControlAvailableForUser(@UserIdInt int userId) {
        // On fully managed devices, follow nearbyAppStreamingPolicy.
        if (mDevicePolicyManagerInternal.getDeviceOwnerComponent(/* callingUser= */ false)
                != null) {
            return mDevicePolicyManager.getNearbyAppStreamingPolicy(userId)
                    != DevicePolicyManager.NEARBY_STREAMING_DISABLED;
        }

        // TODO: b/445856399 - Support managed profiles. For now they are blocked.
        if (mUserManager.isManagedProfile(userId)) {
            return false;
        }

        // Organization-owned devices with managed profiles are allowed to use Computer Control
        // if the parent profile allows it.
        if (mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()) {
            UserInfo userInfo = mUserManager.getUserInfo(userId);
            return mDevicePolicyManager.getParentProfileInstance(userInfo)
                    .getNearbyAppStreamingPolicy() != DevicePolicyManager.NEARBY_STREAMING_DISABLED;
        }
        return true;
    }

    /**
     * Returns whether the virtual device with the given ID represents a computer control session.
     */
    public boolean isComputerControlSession(int deviceId) {
        return findSession(deviceId) != null;
    }

    /**
     * Adds a package to the automatable app list for the specified agent.
     */
    public void addAppToAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName,
            @NonNull String packageName) {
        mAllowlistController.addAppToAutomatableAppListForAgent(agentUid, agentPackageName,
                packageName);
    }

    /**
     * Removes a package from the automatable app list for the specified agent.
     */
    public void removeAppFromAutomatableAppListForAgent(int agentUid,
            @NonNull String agentPackageName,
            @NonNull String packageName) {
        mAllowlistController.removeAppFromAutomatableAppListForAgent(agentUid, agentPackageName,
                packageName);
        // Close any ongoing automation session for the agent where the agent is automating the
        // removed automatable app.
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (session.getOwnerPackageName().equals(agentPackageName)
                        && session.isAutomatingPackage(packageName)) {
                    session.close(CLOSE_REASON_USER_INITIATED);
                }
            }
        }
    }

    /**
     * Clears the automatable app list for the specified agent.
     */
    public void clearAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName) {
        mAllowlistController.clearAutomatableAppListForAgent(agentUid, agentPackageName);
        // Close any ongoing automation session for the agent
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (session.getOwnerPackageName().equals(agentPackageName)) {
                    session.close(CLOSE_REASON_USER_INITIATED);
                }
            }
        }
    }

    /**
     * Returns the automatable app list for the specified agent.
     */
    public String[] getAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName) {
        return mAllowlistController.getAutomatableAppListForAgent(agentUid, agentPackageName);
    }

    /**
     * Returns whether the given package is an approved computer control agent.
     */
    public boolean isPackageApprovedToRunAutomation(@NonNull String packageName,
            int userId) {
        return mAllowlistController.isPackageApprovedToRunAutomation(packageName, userId);
    }

    /**
     * Returns whether the given package is an approved computer control session target.
     */
    public boolean isPackageTargetableForAutomation(@NonNull String packageName,
            int userId) {
        return mAllowlistController.isPackageTargetableForAutomation(packageName, userId);
    }

    private void startHandlerThreadIfNeeded() {
        synchronized (mHandlerThreadLock) {
            if (mHandlerThread != null) {
                return;
            }
            mHandlerThread =
                    new ServiceThread(TAG, Process.THREAD_PRIORITY_FOREGROUND, /* allowTo= */false);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }
    }

    private void createSession(@NonNull ComputerControlSessionRequest request) {
        if (!request.appToken().pingBinder()) {
            Slog.w(TAG, "Binder is dead for ComputerControlSession " + request.name()
                    + ", aborting session creation");
            // Don't bother creating the session if the requester is not around anymore.
            return;
        }

        synchronized (mSessions) {
            if (!checkSessionCreationPreconditionsLocked(request)) {
                return;
            }
        }

        Slog.d(TAG, "Creating ComputerControlSession " + request.name());
        final ComputerControlSessionImpl session;
        final long token = Binder.clearCallingIdentity();
        try {
            session = new ComputerControlSessionImpl(
                    mContext, mAllowlistController, request, mVirtualDeviceFactory,
                    (closedSession) -> {
                        synchronized (mSessions) {
                            mSessions.remove(closedSession);
                        }
                    }, mReferenceDisplayAddress);
            synchronized (mSessions) {
                mSessions.add(session);
            }
        } catch (RuntimeException e) {
            // The virtual device for a ComputerControlSession lives in the system server.
            // The app requesting the session may die anytime, invalidating the device.
            // If this happens during the initialization of the session it will produce
            // an Exception when trying to access the virtual device.
            Slog.e(TAG, "Exception creating ComputerControlSession " + request.name(), e);
            dispatchSessionCreationFailed(request, ComputerControlSession.ERROR_UNKNOWN);
            return;
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        try {
            request.callback().onSessionCreated(session.getVirtualDisplayId(), session);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + request.name()
                    + " about session creation success");
        }
    }

    /** Closes the session with the given ID. */
    public void closeSessionByUserIntent(int deviceId) {
        final var session = findSession(deviceId);
        if (session == null) {
            Slog.w(TAG, "Failed to close ComputerControlSession for unknown deviceId " + deviceId);
            return;
        }
        session.close(CLOSE_REASON_USER_INITIATED);
    }

    @Nullable
    private ComputerControlSessionImpl findSession(int deviceId) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                final var session = mSessions.valueAt(i);
                if (session.getDeviceId() == deviceId) {
                    return session;
                }
            }
        }
        return null;
    }

    @GuardedBy("mSessions")
    private boolean checkSessionCreationPreconditionsLocked(
            @NonNull ComputerControlSessionRequest request) {
        if (mSessions.size() >= MAXIMUM_CONCURRENT_SESSIONS) {
            dispatchSessionCreationFailed(request,
                    ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);
            return false;
        }

        // If the caller claims to be running on a virtual device, make sure that this is actually
        // the case and this is not just an explicitly created device context. If the uid is not
        // seen on the device they claim to be running on, fallback to default.
        final int deviceId;
        if (request.attributionSource().getDeviceId() != Context.DEVICE_ID_DEFAULT
                && mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                        request.ownerUid(), request.attributionSource().getDeviceId())) {
            deviceId = request.attributionSource().getDeviceId();
        } else {
            deviceId = Context.DEVICE_ID_DEFAULT;
        }

        // For cross-device requests rely on AuthenticationPolicyManager
        var authenticationPolicyManager = getAuthenticationPolicyManager();
        if (android.companion.virtualdevice.flags.Flags.computerControlCrossDevice()
                && android.companion.Flags.supportAiAgent()
                && authenticationPolicyManager != null
                && request.params().getCompanionDeviceId() != null) {
            boolean crossDeviceAuthorized =
                    Binder.withCleanCallingIdentity(
                            () ->
                                    authenticationPolicyManager.isAgentAuthorized(
                                            request.ownerUserId(),
                                            deviceId,
                                            request.params().getCompanionDeviceId().getDeviceId()));
            if (!crossDeviceAuthorized) {
                Slog.e(
                        TAG,
                        "Agent app "
                                + request.ownerPackageName()
                                + " is not allowed to start cross-device automation.");
                dispatchSessionCreationFailed(
                        request, ComputerControlSession.ERROR_PERMISSION_DENIED);
            }
            return crossDeviceAuthorized;
        }

        boolean isDeviceLocked =
                Binder.withCleanCallingIdentity(
                        () -> mKeyguardManager.isDeviceLocked(request.ownerUserId(), deviceId));
        if (isDeviceLocked) {
            dispatchSessionCreationFailed(request, ComputerControlSession.ERROR_DEVICE_LOCKED);
            return false;
        }

        // Enforce that the caller has a visible non-toast Window on any display AND that the
        // reported deviceId is valid.
        if (request.params().getTargetComputerControlVersion()
                        >= MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17
                // This returns true only if the uid has a visible non-toast window on any display.
                && !isUidForeground(
                        request.ownerUid(), request.attributionSource().getDeviceId())) {
            Slog.e(TAG, "Agent app " + request.ownerPackageName()
                    + " does not have a non-toast visible window on any display.");
            dispatchSessionCreationFailed(request, ComputerControlSession.ERROR_PERMISSION_DENIED);
            return false;
        }
        return true;
    }

    private boolean isUidForeground(int ownerUid, int deviceId) {
        // TODO(b/493529664)
        // 1) If deviceId is a VirtualDevice, enforce display is owned by the device
        // 2) Enforce that uid is visible on THAT display (make isUidForeground display-aware)
        // 3) If deviceID is a VirtualDevice and the above 2 failed,
        //    check service binding from VirtualDevice owner to ownerUid

        if (deviceId != Context.DEVICE_ID_DEFAULT
                && !mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(ownerUid, deviceId)) {
            return false;
        }
        return mActivityTaskManagerInternal.isUidForeground(ownerUid);
    }

    @Nullable
    private AuthenticationPolicyManager getAuthenticationPolicyManager() {
        if (mAuthenticationPolicyManager == null) {
            mAuthenticationPolicyManager =
                    mContext.getSystemService(AuthenticationPolicyManager.class);
        }
        return mAuthenticationPolicyManager;
    }

    /** Notifies the client that session creation failed. */
    private void dispatchSessionCreationFailed(
            @NonNull ComputerControlSessionRequest request, int reason) {
        try {
            request.callback().onSessionCreationFailed(reason);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + request.name()
                    + " about session creation failure");
        }
        ComputerControlStatsController.writeFailedSessionWithSessionCreationError(
                mPackageManager, request.attributionSource(), request.params(), reason);
    }

    private static PendingIntent createPendingIntent(Context context, int uid, Intent intent) {
        return Binder.withCleanCallingIdentity(() ->
                PendingIntent.getActivityAsUser(
                        context, /*requestCode= */ uid, intent,
                        FLAG_ONE_SHOT | FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE,
                        ActivityOptions.makeBasic()
                                .setPendingIntentCreatorBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                .toBundle(),
                        UserHandle.CURRENT));
    }

    /**
     * Dump debug information about the state of ComputerControl sessions.
     */
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout,
            @Nullable String[] args) {
        String indent = "    ";
        fout.println(indent + "Computer Control Version: " +
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION);
        fout.println(indent + "Maximum Concurrent Sessions: " + MAXIMUM_CONCURRENT_SESSIONS);
        fout.println(indent + "Active computer control sessions: ");
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                mSessions.valueAt(i).dump(fd, fout, args);
            }
        }
    }

    public void monitor() {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                mSessions.valueAt(i).monitor();
            }
        }
        synchronized (mHandlerThreadLock) { /* no-op */ }
        mAllowlistController.monitor();
    }

    private final class ConsentResultReceiver extends ResultReceiver {

        private final ComputerControlSessionRequest mRequest;

        ConsentResultReceiver(@NonNull ComputerControlSessionRequest request) {
            super(mHandler);
            mRequest = request;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle data) {
            if (resultCode == Activity.RESULT_OK) {
                mHandler.post(() -> createSession(mRequest));
            } else {
                dispatchSessionCreationFailed(mRequest,
                        ComputerControlSession.ERROR_PERMISSION_DENIED);
            }
        }

        /**
         * Convert this instance of a "locally-defined" ResultReceiver to an instance of
         * {@link android.os.ResultReceiver} itself, which the receiving process will be able to
         * unmarshall.
         */
        private ResultReceiver prepareForIpc() {
            final Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            final ResultReceiver ipcFriendly = ResultReceiver.CREATOR.createFromParcel(parcel);
            parcel.recycle();

            return ipcFriendly;
        }
    }

    /**
     * Returns the session associated with the provided displayId, or {@code null} if not found.
     */
    @GuardedBy("mSessions")
    @Nullable
    private ComputerControlSessionImpl findSessionByDisplayIdLocked(int displayId) {
        for (int i = 0; i < mSessions.size(); i++) {
            ComputerControlSessionImpl session = mSessions.valueAt(i);
            if (session.getVirtualDisplayId() == displayId) {
                return session;
            }
        }
        return null;
    }

    /**
     * Returns {@code true}, if any of the ongoing computer control sessions are running on the
     * provided virtual display ID, {@code false} otherwise.
     */
    public boolean isComputerControlDisplay(int displayId) {
        synchronized (mSessions) {
            return findSessionByDisplayIdLocked(displayId) != null;
        }
    }

    /**
     * Check if the specified virtual display is currently being actively automated by an agent.
     *
     * This is used to enforce security and privacy policies (such as Autofill
     * restrictions or camera blocking) that apply when an automated agent is in
     * control, but should be relaxed when a user is interacting with the session via
     * an interactive mirror.
     *
     * @param displayId The ID of the virtual display to check.
     * @return {@code true} if the display is associated with an active computer control
     *         automation session; {@code false} otherwise.
     */
    public boolean isActiveComputerControlDisplay(int displayId) {
        synchronized (mSessions) {
            ComputerControlSessionImpl session = findSessionByDisplayIdLocked(displayId);
            return session != null && session.isSessionActive();
        }
    }

    /**
     * Returns if the provided notification id and tag are used for a computer control session by
     * the given package.
     */
    public boolean isComputerControlNotification(int notificationId,
            @Nullable String notificationTag, @NonNull String packageName) {
        final ComputerControlSessionImpl.NotificationInfo notificationInfo =
                new ComputerControlSessionImpl.NotificationInfo(notificationId, notificationTag);
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (Objects.equals(session.getOwnerPackageName(), packageName)
                        && Objects.equals(session.getNotificationInfo(), notificationInfo)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Interface for creating a virtual device for a computer control session.
     */
    public interface VirtualDeviceFactory {
        /**
         * Creates a new virtual device.
         */
        VirtualDeviceManager.VirtualDevice createVirtualDevice(
                IBinder token,
                AttributionSource attributionSource,
                VirtualDeviceParams params);
    }

    /**
     * Interface for creating a pending intent for a computer control session.
     */
    @VisibleForTesting
    public interface PendingIntentFactory {
        /**
         * Creates a new pending intent.
         */
        PendingIntent create(Context context, int uid, Intent intent);
    }
}
