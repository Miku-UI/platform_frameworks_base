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

import static android.app.Notification.FLAG_COMPUTER_CONTROL;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_EMPTY;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_USER_INITIATED;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.companion.virtual.computercontrol.LifecycleState;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;
import com.android.internal.inputmethod.InputConnectionCommandHeader;
import com.android.internal.os.IResultReceiver;
import com.android.server.FgThread;
import com.android.server.LocalServices;
import com.android.server.UiThread;
import com.android.server.appinteraction.AppInteractionService;
import com.android.server.input.InputManagerInternal;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityAssistInfo;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A computer control session that encapsulates a {@link android.companion.virtual.IVirtualDevice}.
 * The device is created and managed by the system, but it is still owned by the caller.
 *
 * NOTE: Lock ordering precedence: The hierarchy of locks defined in this file is determined by the
 * order in which the locks are declared. If two locks need to be acquired at once, the lock
 * declared earlier in the file needs to be acquired first.
 */
final class ComputerControlSessionImpl extends IComputerControlSession.Stub
        implements IBinder.DeathRecipient, AppOpsManager.OnOpChangedListener {

    private static final String TAG = "ComputerControlSession";
    private static final int TRACE_COOKIE_SESSION = 0;
    private static final int TRACE_COOKIE_WINDOW_DRAW = 1;

    private static final long DEFAULT_GLOBAL_SESSION_TIMEOUT_DURATION_MS =
            TimeUnit.MILLISECONDS.convert(60, TimeUnit.MINUTES);

    // Timeout for waiting for all windows on the display to be drawn before taking a screenshot.
    private static final int WINDOW_DRAW_TIMEOUT_MS = 1000;

    // Input device names are limited to 80 bytes, so keep the prefix shorter than that.
    private static final int MAX_INPUT_DEVICE_NAME_PREFIX_BYTES = 70;

    // Throttle swipe events to avoid misinterpreting them as a fling. Each swipe will
    // consist of a DOWN event, 10 MOVE events spread over 500ms, and an UP event.
    @VisibleForTesting
    static final int SWIPE_STEPS = 10;
    // Delay between consecutive touch events sent during a swipe or a long press gesture.
    @VisibleForTesting
    static final long TOUCH_EVENT_DELAY_MS = 50L;
    // Multiplier for the long press timeout to ensure it's registered as a long press,
    // as some applications might have slightly different thresholds.
    @VisibleForTesting
    static final float LONG_PRESS_TIMEOUT_MULTIPLIER = 1.5f;
    @VisibleForTesting
    static final long KEY_EVENT_DELAY_MS = 50L;
    // The session will be closed whenever the display remains empty for this timeout period.
    // This timeout is used to avoid closing the session immediately upon the display being empty
    // to allow for transient cases of emptiness, like when an Activity is launched in a new task
    // while the current task is finished.
    @VisibleForTesting
    static final long CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS = 100L;

    // Vendor and Product IDs for Computer Control virtual input devices.
    // These values are likely unique within the VIRTUAL bus type, but they are not
    // guaranteed to be globally unique forever.
    // TODO: b/443001754 - Remove setVendorId and setProductId in all input devices below,
    //   in favor of reporting dedicated Computer Control metrics.
    private static final int VENDOR_ID = 0x0000;
    @VisibleForTesting
    static final int PRODUCT_ID_DPAD = 0xCC01;
    @VisibleForTesting
    static final int PRODUCT_ID_TOUCHSCREEN = 0xCC03;

    private final String mTraceTrack = "ComputerControlSessionImpl#"
            + System.identityHashCode(this);

    private final ComputerControlSessionRequest mRequest;

    private final Consumer<ComputerControlSessionImpl> mOnClosedListener;
    private final VirtualDevice mVirtualDevice;
    // The VirtualDisplay is owned by the system and its token must not be leaked to the client.
    private final VirtualDisplay mVirtualDisplay;
    private final int mVirtualDisplayId;
    private final int mVirtualDeviceId;
    private final int mMainDisplayId;
    @Nullable
    private final String mReferenceDisplayAddress;
    private final VirtualTouchscreen mVirtualTouchscreen;
    private final VirtualDpad mVirtualDpad;
    private final ComputerControlAudioCapture mAudioCapture;
    private final ComputerControlAudioInjector mAudioInjector;

    @Override
    protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout,
            @Nullable String[] args) {
        String indent = "        ";
        fout.print(indent);

        fout.print("ComupterControlSession {");
        fout.print(" mDeviceId=" + mVirtualDeviceId);
        fout.print(" mName=" + getName());
        fout.print(" mTargetComputerControlVersion="
                + mRequest.params().getTargetComputerControlVersion());
        fout.print(" mOwnerPackageName=" + mRequest.ownerPackageName());
        fout.print(" mTargetPackageNames=" + mRequest.params().getTargetPackageNames());
        fout.print(" mAppInteractionAttribution="
                + mRequest.params().getAppInteractionAttribution());
        fout.print("}");
        fout.print("\n");
    }

    private final ScheduledExecutorService mScheduler;

    /** Executor for the shared FgThread. */
    private final Executor mFgThreadExecutor;
    private final AppOpsManager mOwnerAppOpsManager;
    private final WindowManagerInternal mWindowManagerInternal;
    private final InputMethodManagerInternal mInputMethodManagerInternal;
    private final UserManagerInternal mUserManagerInternal;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final InputManagerInternal mInputManagerInternal;
    private final DisplayManagerGlobal mDisplayManagerGlobal;
    private final ViewConfiguration mViewConfiguration;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private final ComputerControlAllowlistController mAllowlistController;
    private final ComputerControlStatsController mStatsController;
    @Nullable private final AppInteractionService mAppInteractionService;

    @GuardedBy("mAllowlistedPackages")
    private final Set<String> mAllowlistedPackages = new ArraySet<>();

    /** Task IDs that are authorized for content visibility. */
    @GuardedBy("mAllowedTaskIds")
    private final Set<Integer> mAllowedTaskIds = new ArraySet<>();

    /**
     * Whether screenshot is allowed depending on if the top activity is allowlisted.
     * Null indicates display is empty.
     */
    @GuardedBy("mAllowedTaskIds")
    @Nullable
    private Boolean mIsTopActivityScreenshotAllowed = null;

    // Handle state transitions for the session lifecycle.
    private final ComputerControlSession.LifecycleCallback mStateTransitions =
            new ComputerControlSession.LifecycleCallback() {
                @Override
                public void onActive() {
                    handleStateTransition();
                    mStatsController.onSessionActive();
                    mSessionTimeoutTimer.resume();
                }

                @Override
                public void onBlocked(@ComputerControlSession.SessionBlockReason int reason,
                        @Nullable String blockingPackage) {
                    cancelOngoingInteractions();
                    handleStateTransition();
                    mStatsController.onSessionBlocked(reason);
                    mSessionTimeoutTimer.pause();
                }

                // Shared configuration updates when transitioning between non-closed states.
                private void handleStateTransition() {
                    updatePowerState();
                    updateMirrorInteractivity();
                }

                @Override
                public void onClosed(@ComputerControlSession.SessionCloseReason int reason) {
                    releaseResources();
                    mStatsController.onSessionClosed(reason);
                    Trace.asyncTraceForTrackEnd(mTraceTrack, TRACE_COOKIE_SESSION);
                }
            };

    // Keeps track of the current lifecycle state. Thread safe.
    private final SessionLifecycle mLifecycle;

    private final Object mNotificationLock = new Object();
    @GuardedBy("mNotificationLock")
    @Nullable
    private NotificationInfo mNotificationInfo = null;
    private final Object mPreviewIntentLock = new Object();
    @GuardedBy("mPreviewIntentLock")
    @Nullable
    private PendingIntent mPreviewIntent = null;

    @GuardedBy("mInteractiveMirrors")
    // A list of active interactive mirrors. The presence of mirrors indicates foreground
    // automation, which enables touch visualization.
    private final List<InteractiveMirrorImpl> mInteractiveMirrors = new ArrayList<>();
    @GuardedBy("mInteractiveMirrors")
    private boolean mIsVirtualDeviceAsleep = false;

    @Nullable
    private ScheduledFuture<?> mSwipeFuture;
    @Nullable
    private ScheduledFuture<?> mInsertTextFuture;
    @Nullable
    private ScheduledFuture<?> mDisplayEmptyScheduledAction;
    @Nullable
    private Surface mClientSurface;

    private final PausableTimer mSessionTimeoutTimer;

    // Whether this is a session only intended for testing ComputerControl functionality.
    private final boolean mIsTestSession;

    private final Object mWindowDrawLock = new Object();
    // Whether a window draw as a result of a screenshot request is in progress.
    @GuardedBy("mWindowDrawLock")
    private boolean mIsWaitingForWindowDraw = false;
    @GuardedBy("mWindowDrawLock")
    private boolean mIsWaitingForScreenshotResult = false;

    private final Context mDisplayUiContext;
    // Access on the UI thread only.
    @Nullable
    private View mInsetsProviderView;
    // Access on the UI thread only.
    @NonNull
    private Insets mAppliedInsets = Insets.NONE;
    private final WindowManager.LayoutParams mInsetsProviderLayoutParams =
            createInsetsProviderLayoutParams();

    private final InteractiveMirrorImpl.InteractiveMirrorImplCallback mInteractiveMirrorCallback =
            new InteractiveMirrorImpl.InteractiveMirrorImplCallback() {
                @Override
                public void onInteractiveChanged(boolean isInteractive) {
                    synchronized (mInteractiveMirrors) {
                        var firstMirror = getFirstMirrorThatIsInteractiveLocked();
                        if (firstMirror != null) {
                            // If any mirror is interactive, allow it to steal top focus to allow
                            // key event and IME interactions from the user.
                            mWindowManagerInternal.setCanStealTopFocusForDisplay(
                                    mVirtualDisplayId, /* canStealTopFocus= */ true);
                            mWindowManagerInternal
                                    .setFocusedA11yEmbeddedConnectionReceiverOnDisplay(
                                            mVirtualDisplayId,
                                            firstMirror.getA11yEmbeddedConnectionReceiver());
                            mVirtualDevice.setDisplayImePolicy(mVirtualDisplayId,
                                    WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY);

                        } else {
                            // If all mirrors are non-interactive, disable top focus stealing for
                            // the virtual display by clearing the override.
                            mWindowManagerInternal.setCanStealTopFocusForDisplay(
                                    mVirtualDisplayId, /* canStealTopFocus= */ false);
                            mWindowManagerInternal
                                    .setFocusedA11yEmbeddedConnectionReceiverOnDisplay(
                                            mVirtualDisplayId, null);
                            mVirtualDevice.setDisplayImePolicy(mVirtualDisplayId,
                                    WindowManager.DISPLAY_IME_POLICY_HIDE);
                        }
                    }
                    mStatsController.onMirrorViewInteractive(isInteractive);
                }

                @Override
                public void onRequestedInsetsChanged() {
                    updateInsets();
                }

                @Override
                public void onClose(InteractiveMirrorImpl mirror) {
                    removeInteractiveMirror(mirror);
                }
            };

    ComputerControlSessionImpl(Context context,
            ComputerControlAllowlistController allowlistController,
            ComputerControlSessionRequest request,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Consumer<ComputerControlSessionImpl> onClosedListener,
            @Nullable String referenceDisplayAddress) {
        this(context, DisplayManagerGlobal.getInstance(), allowlistController,
                ViewConfiguration.get(context), DEFAULT_GLOBAL_SESSION_TIMEOUT_DURATION_MS,
                SurfaceControl.Transaction::new, request,
                virtualDeviceFactory, onClosedListener, FgThread.getExecutor(),
                referenceDisplayAddress, Executors.newSingleThreadScheduledExecutor());
    }

    @VisibleForTesting
    ComputerControlSessionImpl(Context context, DisplayManagerGlobal displayManagerGlobal,
            ComputerControlAllowlistController allowlistController,
            ViewConfiguration viewConfiguration, long globalSessionTimeoutDurationMs,
            Supplier<SurfaceControl.Transaction> transactionSupplier,
            ComputerControlSessionRequest request,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            Consumer<ComputerControlSessionImpl> onClosedListener, Executor fgThreadExecutor,
            @Nullable String referenceDisplayAddress, ScheduledExecutorService scheduler) {
        Trace.asyncTraceForTrackBegin(mTraceTrack, "Session", TRACE_COOKIE_SESSION);
        mFgThreadExecutor = fgThreadExecutor;
        mViewConfiguration = viewConfiguration;
        mTransactionSupplier = transactionSupplier;
        mAllowlistController = allowlistController;
        mRequest = request;
        mPreviewIntent = request.params().getPreviewIntent();
        mReferenceDisplayAddress = referenceDisplayAddress;
        mScheduler = scheduler;
        mLifecycle = new SessionLifecycle(mScheduler, mStateTransitions);

        mIsTestSession = mAllowlistController.isTestAgent(request.ownerUid(),
                request.ownerPackageName(), request.ownerPackageManager());

        mOnClosedListener = onClosedListener;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mInputMethodManagerInternal = LocalServices.getService(
                InputMethodManagerInternal.class);
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        mActivityTaskManagerInternal = LocalServices.getService(
                ActivityTaskManagerInternal.class);
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mDisplayManagerGlobal = displayManagerGlobal;
        mStatsController = new ComputerControlStatsController(
                context.getPackageManager(), request.attributionSource(), request.params());
        if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
            mAppInteractionService = LocalServices.getService(AppInteractionService.class);
        } else {
            mAppInteractionService = null;
        }

        // TODO(b/469400179): Consider using the display from the app's context instead.
        mMainDisplayId = mUserManagerInternal.getMainDisplayAssignedToUser(request.ownerUserId());

        // This assumes that {@link ComputerControlSessionParams#getTargetPackageNames()}
        // never contains any packageNames that the session owner should never be able to
        // launch. This is validated in {@link ComputerControlSessionProcessor} prior to
        // creating the session.
        mAllowlistedPackages.addAll(request.params().getTargetPackageNames());

        final VirtualDeviceParams.Builder virtualDeviceParamsBuilder =
                new VirtualDeviceParams.Builder()
                    .setName(getName())
                    .setLocalDeviceOnly(true)
                    .setDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY, DEVICE_POLICY_CUSTOM)
                    .setDevicePolicy(POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS,
                            DEVICE_POLICY_CUSTOM)
                    .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM);
        final VirtualDeviceParams virtualDeviceParams = virtualDeviceParamsBuilder.build();

        final VirtualDisplayConfig virtualDisplayConfig = createSessionDisplayConfig(
                getName() + "-display", getTargetDisplayInfo());
        final int displayWidth = virtualDisplayConfig.getWidth();
        final int displayHeight = virtualDisplayConfig.getHeight();

        VirtualDevice virtualDevice = null;
        try {
            virtualDevice = virtualDeviceFactory.createVirtualDevice(
                    request.appToken(), request.attributionSource(), virtualDeviceParams);
            mVirtualDevice = virtualDevice;
            mVirtualDeviceId = mVirtualDevice.getDeviceId();
            mVirtualDevice.addActivityListener(mScheduler, new ComputerControlActivityListener());

            // Create the display with a clean identity so it can be trusted. The virtual display's
            // token must not be leaked to the client.
            mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                    virtualDisplayConfig, null, null);
            mWindowManagerInternal.setAnimationsDisabledForDisplay(
                    mVirtualDisplay.getDisplay().getDisplayId(), true);
            mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
            mDisplayUiContext = context.createDisplayContext(mVirtualDisplay.getDisplay());
            mWindowManagerInternal.enablePowerOptimizations(mVirtualDisplayId, /* enable = */ true);
            mWindowManagerInternal.enableClientRenderingLimitationsOnDisplay(
                    mVirtualDisplayId, /* enable = */true);
            mWindowManagerInternal.setCanStealTopFocusForDisplay(
                    mVirtualDisplayId, /* canStealTopFocus= */ false);

            mVirtualDevice.setDisplayImePolicy(
                    mVirtualDisplayId, WindowManager.DISPLAY_IME_POLICY_HIDE);

            final String inputDeviceNamePrefix =
                    createInputDeviceNamePrefix(request.ownerPackageName());

            final String dpadName = inputDeviceNamePrefix + "-dpad";
            final VirtualDpadConfig virtualDpadConfig =
                    new VirtualDpadConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(dpadName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_DPAD)
                            .build();
            mVirtualDpad = mVirtualDevice.createVirtualDpad(virtualDpadConfig);

            final String touchscreenName = inputDeviceNamePrefix + "-tscr";
            final VirtualTouchscreenConfig virtualTouchscreenConfig =
                    new VirtualTouchscreenConfig.Builder(displayWidth, displayHeight)
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(touchscreenName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_TOUCHSCREEN)
                            .build();
            mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(virtualTouchscreenConfig);

            // Take control of the audio streams
            VirtualAudioDevice virtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                    mVirtualDisplay, null, null);
            mAudioInjector = new ComputerControlAudioInjector(virtualAudioDevice);
            mAudioInjector.startAudioInjection();
            mAudioCapture = new ComputerControlAudioCapture(virtualAudioDevice);
            mAudioCapture.startAudioCapture();

            request.appToken().linkToDeath(this, 0);
            mSessionTimeoutTimer = new PausableTimer(mScheduler, globalSessionTimeoutDurationMs,
                    () -> close(CLOSE_REASON_SESSION_TIMED_OUT));
        } catch (RemoteException e) {
            if (virtualDevice != null) {
                virtualDevice.close();
            }
            throw e.rethrowFromSystemServer();
        }

        postSessionNotification();

        mOwnerAppOpsManager = request.ownerContext().getSystemService(AppOpsManager.class);
        mOwnerAppOpsManager.startWatchingMode(AppOpsManager.OP_COMPUTER_CONTROL,
                request.ownerPackageName(), this);
    }

    private DisplayInfo getTargetDisplayInfo() {
        if (TextUtils.isEmpty(mReferenceDisplayAddress)) {
            Slog.i(TAG, "No configured reference display, using main display " + mMainDisplayId
                    + " for Computer Control virtual display dimensions");
            return mDisplayManagerGlobal.getDisplayInfo(mMainDisplayId);
        }
        long configAddress = Long.parseLong(mReferenceDisplayAddress);
        int[] displayIds = mDisplayManagerGlobal.getDisplayIds(/* includeDisabled= */ true);
        for (int i = 0; i < displayIds.length; i++) {
            DisplayInfo info = mDisplayManagerGlobal.getDisplayInfo(displayIds[i]);
            if (info != null && info.address != null) {
                long physicalId = info.address.getPhysicalDisplayId();
                if (physicalId == configAddress) {
                    Slog.i(TAG,
                            "Using configured reference display " + displayIds[i]
                                    + " (physical address: " + mReferenceDisplayAddress
                                    + ") for Computer Control virtual display dimensions");
                    return info;
                }
            }
        }
        return mDisplayManagerGlobal.getDisplayInfo(mMainDisplayId);
    }

    /**
     * Create the session's display to have the same size and density as that of the
     * {@code refDisplayInfo} when it is in its natural orientation.
     */
    private static VirtualDisplayConfig createSessionDisplayConfig(String name,
            DisplayInfo refDisplayInfo) {
        final int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;

        final int displayWidth;
        final int displayHeight;
        if (refDisplayInfo.rotation == Surface.ROTATION_90
                || refDisplayInfo.rotation == Surface.ROTATION_270) {
            displayWidth = refDisplayInfo.logicalHeight;
            displayHeight = refDisplayInfo.logicalWidth;
        } else {
            displayWidth = refDisplayInfo.logicalWidth;
            displayHeight = refDisplayInfo.logicalHeight;
        }

        return new VirtualDisplayConfig.Builder(
                name, displayWidth, displayHeight,
                refDisplayInfo.logicalDensityDpi)
                .setFlags(displayFlags)
                .setIgnoreActivitySizeRestrictions(true)
                .build();
    }

    int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    int getDeviceId() {
        return mVirtualDeviceId;
    }

    @NonNull
    String getName() {
        return mRequest.name();
    }

    @NonNull
    String getOwnerPackageName() {
        return mRequest.ownerPackageName();
    }

    boolean isAutomatingPackage(@NonNull String packageName) {
        synchronized (mAllowlistedPackages) {
            return mAllowlistedPackages.contains(packageName);
        }
    }

    @Nullable
    NotificationInfo getNotificationInfo() {
        synchronized (mNotificationLock) {
            return mNotificationInfo;
        }
    }

    @NonNull
    PackageManager getPackageManager() {
        return mRequest.ownerPackageManager();
    }

    @Nullable
    KeyguardManager getKeyguardManager() {
        return mRequest.ownerContext().getSystemService(KeyguardManager.class);
    }

    boolean isTestSession() {
        return mIsTestSession;
    }

    void monitor() {
        synchronized (mAllowlistedPackages) { /* no-op */ }
        synchronized (mAllowedTaskIds) { /* no-op */ }
        synchronized (mNotificationLock) { /* no-op */ }
        synchronized (mPreviewIntentLock) { /* no-op */ }
        synchronized (mWindowDrawLock) { /* no-op */ }
        synchronized (mInteractiveMirrors) {
            for (int i = 0; i < mInteractiveMirrors.size(); i++) {
                mInteractiveMirrors.get(i).monitor();
            }
        }
        mLifecycle.monitor();
        mStatsController.monitor();
        mSessionTimeoutTimer.monitor();
    }

    @Override
    public void initialize(IComputerControlLifecycleCallback callback, Surface clientSurface) {
        if (mClientSurface != null) {
            throw new IllegalStateException("Client surface is already initialized");
        }
        mClientSurface = clientSurface;
        mVirtualDisplay.setSurface(mClientSurface);

        mLifecycle.initializeWithRemoteCallback(callback);
    }

    @Override
    @SuppressLint("AndroidFrameworkRequiresPermission")
    public void launchApplication(@NonNull String packageName, @Nullable String className)
            throws RemoteException {
        final Intent intent = getLaunchIntent(packageName, className);
        if (intent == null) {
            throw new IllegalArgumentException(
                    "Could not find launcher activity for " + packageName + "/" + className);
        }
        if (!mAllowlistController.isPackageAutomatable(packageName, this)) {
            throw new IllegalArgumentException(
                    "Trying to launch " + packageName + " which is not allowlisted");
        }

        synchronized (mAllowlistedPackages) {
            if (!mAllowlistedPackages.contains(packageName)) {
                throw new IllegalArgumentException(
                        "Trying to launch "
                                + packageName
                                + " which is not a target package for the current session");
            }
        }

        // TODO(b/444600407): Remove this once the consent model is per-target app. While the
        // consent is general, the caller can extend the list of target packages dynamically.
        if (!isSessionActive()) {
            Slog.e(TAG, "Cannot launch application: Agent interaction is not available");
            return;
        }
        cancelOngoingInteractions();
        // If we block input and screenshots in the blocked state, we simply allow all
        // activities to launch. We detect blocked state automatically when an activity
        // launch request comes in for a package that's not allowed to launch.
        final Bundle options =
                ActivityOptions.makeBasic().setLaunchDisplayId(mVirtualDisplayId).toBundle();
        Binder.withCleanCallingIdentity(() -> mActivityTaskManagerInternal.startActivityAsUser(
                mRequest.appThread(), mRequest.ownerPackageName(),
                mRequest.attributionSource().getAttributionTag(), intent, null,
                Intent.FLAG_ACTIVITY_NEW_TASK, options, mRequest.ownerUserId()));
        mStatsController.onApplicationLaunched(packageName);
    }

    @Override
    public void handOverApplications() {
        Binder.withCleanCallingIdentity(
                () -> moveAllTasks(mVirtualDisplayId, mMainDisplayId));
        close(CLOSE_REASON_SESSION_EMPTY);
    }

    @Override
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) throws RemoteException {
        if (shouldDisallowInteractions("tap")) {
            return;
        }
        cancelOngoingInteractions();
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_UP));
        mStatsController.onTap();
    }

    @Override
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int  toY) throws RemoteException {
        if (shouldDisallowInteractions("swipe")) {
            return;
        }
        cancelOngoingInteractions();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(fromX, fromY, VirtualTouchEvent.ACTION_DOWN));
        performSwipeStep(fromX, fromY, toX, toY, /* step= */ 0, SWIPE_STEPS);
        mStatsController.onSwipe();
    }

    @Override
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y)
            throws RemoteException {
        if (shouldDisallowInteractions("longPress")) {
            return;
        }
        cancelOngoingInteractions();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        int longPressStepCount =
                (int) Math.ceil(
                        (double) mViewConfiguration.getLongPressTimeoutMillis() *
                                LONG_PRESS_TIMEOUT_MULTIPLIER / TOUCH_EVENT_DELAY_MS);
        performSwipeStep(x, y, x, y, /* step= */ 0, longPressStepCount);
        mStatsController.onLongPress();
    }

    @Override
    public void performAction(@ComputerControlSession.Action int actionCode)
            throws RemoteException {
        if (shouldDisallowInteractions("performAction")) {
            return;
        }
        cancelOngoingInteractions();
        if (actionCode == ComputerControlSession.ACTION_GO_BACK) {
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_DOWN));
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP));
        } else {
            Slog.e(TAG, "Invalid action code for performAction: " + actionCode);
            return;
        }
        mStatsController.onPerformAction(actionCode);
    }

    @Override
    @Nullable
    public IInteractiveMirror createInteractiveMirror(
            IResultReceiver a11yEmbeddedConnectionReceiver, SurfaceControl outMirrorSurface) {
        final var mirror = createInteractiveMirrorImpl(a11yEmbeddedConnectionReceiver);
        if (mirror == null) {
            return null;
        }
        final boolean foregroundMirroringStarted;
        synchronized (mInteractiveMirrors) {
            foregroundMirroringStarted = mInteractiveMirrors.isEmpty();
            mInteractiveMirrors.add(mirror);
            if (foregroundMirroringStarted) {
                updatePowerState();
                // Automation is no longer running in the background. Show touches.
                mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                        true /* enabled */);
                // Automation is happening in the foreground, so enable rendering.
                mWindowManagerInternal.enableClientRenderingLimitationsOnDisplay(
                        mVirtualDisplayId, /* enable = */false);
                mWindowManagerInternal.enablePowerOptimizations(
                        mVirtualDisplayId, /* enable = */false);
                mWindowManagerInternal.requestHardwareRendererOutputEnabled(mVirtualDisplayId,
                        0 /* timeoutMs */, (success) -> {
                        }, mScheduler);
            }
        }
        outMirrorSurface.copyFrom(mirror.getMirrorLeash(),
                "ComputerControlSessionImpl#createInteractiveMirrorDisplay");
        if (foregroundMirroringStarted) {
            mStatsController.onMirroringStarted();
        }
        mStatsController.onMirrorViewCreated();
        return mirror;
    }

    @Override
    public void onOpChanged(String op, String packageName) {}

    @Override
    public void onOpChanged(@NonNull String op, @NonNull String packageName, int userId) {
        if (!AppOpsManager.OPSTR_COMPUTER_CONTROL.equals(op)
                || !Objects.equals(packageName, mRequest.ownerPackageName())
                || userId != mRequest.ownerUserId()) {
            return;
        }

        try {
            final int uid =
                    mRequest.ownerPackageManager().getPackageUidAsUser(packageName, userId);
            final int mode = mOwnerAppOpsManager.checkOpNoThrow(op, uid, packageName);
            Slog.i(TAG, "onOpChanged: Found new mode " + mode + " for package " + packageName
                    + " for user id " + userId);
            if (mode == AppOpsManager.MODE_IGNORED) {
                close(CLOSE_REASON_USER_INITIATED);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "onOpChanged: Failed to get uid for package " + packageName
                    + " for user id " + userId);
        }
    }

    @Nullable
    private InteractiveMirrorImpl createInteractiveMirrorImpl(
            IResultReceiver a11yEmbeddedConnectionReceiver) {
        final var mirror =
                mWindowManagerInternal.createMirrorForDisplayContent(mVirtualDisplayId);
        if (mirror == null) {
            Slog.w(TAG, "Failed to create DisplayMirror from WM for display: " + mVirtualDisplayId);
            return null;
        }
        return new InteractiveMirrorImpl(mirror, a11yEmbeddedConnectionReceiver,
                mTransactionSupplier, mDisplayManagerGlobal.getDisplayInfo(mVirtualDisplayId),
                mInputManagerInternal, isMirrorInteractionAllowed(), mInteractiveMirrorCallback);
    }

    private void removeInteractiveMirror(InteractiveMirrorImpl interactiveMirror) {
        final boolean foregroundMirroringStopped;
        synchronized (mInteractiveMirrors) {
            if (!mInteractiveMirrors.remove(interactiveMirror)) {
                return;
            }
            foregroundMirroringStopped = mInteractiveMirrors.isEmpty();
            if (foregroundMirroringStopped) {
                updatePowerState();
                // Automation is fully running in the background. No need to show touches.
                mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                        false /* enabled */);
                // Disable rendering during background automation, where windows will only draw
                // when the client requests a screenshot.
                mWindowManagerInternal.enableClientRenderingLimitationsOnDisplay(
                        mVirtualDisplayId, /* enable = */true);
                mWindowManagerInternal.enablePowerOptimizations(
                        mVirtualDisplayId, /* enable = */true);
                synchronized (mWindowDrawLock) {
                    if (!mIsWaitingForWindowDraw) {
                        mWindowManagerInternal.requestHardwareRendererOutputDisabled(
                                mVirtualDisplayId);
                    }
                }
            }
        }
        try (var transaction = mTransactionSupplier.get()) {
            interactiveMirror.closeWithTransaction(transaction);
            transaction.apply();
        }
        updateInsets();
        if (foregroundMirroringStopped) {
            mStatsController.onMirroringStopped();
        }
    }

    private void removeAllInteractiveMirrorsOnSessionClose() {
        synchronized (mInteractiveMirrors) {
            if (mInteractiveMirrors.isEmpty()) {
                return;
            }
            try (var transaction = mTransactionSupplier.get()) {
                for (int i = 0; i < mInteractiveMirrors.size(); i++) {
                    mInteractiveMirrors.get(i).closeWithTransaction(transaction);
                }
                transaction.apply();
            }
            mInteractiveMirrors.clear();

            // Automation is fully running in the background. No need to show touches.
            mInputManagerInternal.setForceShowTouchesOnDisplay(mVirtualDisplayId,
                    false /* enabled */);
        }
    }

    private void updatePowerState() {
        synchronized (mInteractiveMirrors) {
            final var state = mLifecycle.getCurrentState();
            if (state instanceof LifecycleState.Closed) {
                return;
            }
            final boolean shouldSleep =
                    state instanceof LifecycleState.Blocked && mInteractiveMirrors.isEmpty();
            if (mIsVirtualDeviceAsleep == shouldSleep) {
                return;
            }
            mIsVirtualDeviceAsleep = shouldSleep;
            Slog.i(TAG, "updatePowerState: " + (shouldSleep ? "sleeping" : "waking up"));
            if (shouldSleep) {
                mVirtualDevice.goToSleep();
            } else {
                mVirtualDevice.wakeUp();
            }
        }
    }

    private void updateMirrorInteractivity() {
        try (var transaction = mTransactionSupplier.get()) {
            synchronized (mInteractiveMirrors) {
                for (int i = 0; i < mInteractiveMirrors.size(); i++) {
                    mInteractiveMirrors.get(i).updateInteractivity(isMirrorInteractionAllowed(),
                            transaction);
                }
            }
            transaction.apply();
        }
    }

    // Policy method for when any mirror is allowed to be interacted with by the user. The user
    // currently must only interact when the client is blocked, to avoid interfering with client
    // interactions.
    private boolean isMirrorInteractionAllowed() {
        return mLifecycle.getCurrentState() instanceof LifecycleState.Blocked;
    }

    /**
     * Returns {@code true} if the agent is actively automating the session.
     * This is the basis for various policies, such as whether autofill or
     * camera, audio etc. is enabled for a session.
     */
    public boolean isSessionActive() {
        return mLifecycle.getCurrentState() instanceof LifecycleState.Active;
    }

    @GuardedBy("mInteractiveMirrors")
    @Nullable
    private InteractiveMirrorImpl getFirstMirrorThatIsInteractiveLocked() {
        for (int i = 0; i < mInteractiveMirrors.size(); i++) {
            var mirror = mInteractiveMirrors.get(i);
            if (mirror.isInteractive()) {
                return mirror;
            }
        }
        return null;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        if (shouldDisallowInteractions("insertText")) {
            return;
        }
        cancelOngoingInteractions();

        InputMethodManagerInternal.ComputerControlInputConnectionData data = getInputConnectionData(
                mVirtualDisplayId);
        if (data == null) {
            Slog.e(TAG, "Unable to insert text: No input connection data found!");
            return;
        }
        final IRemoteComputerControlInputConnection ic = data.inputConnection();
        if (ic == null) {
            Slog.e(TAG, "Unable to insert text: No input connection found!");
            return;
        }
        // TODO(b/422134565): Implement client invoker logic to pass the correct session id when
        //  "client text view" invalidates input while view remains focused.
        //  Currently, if we set text using A11y nodes or the application sets text into the
        //  text field outside of input connection (while text view is focused), CC session will
        //  no longer be able to insert text until the text view restarts the input connection.
        try {
            if (replaceExisting) {
                ic.replaceText(new InputConnectionCommandHeader(0), 0 /* start */,
                        Integer.MAX_VALUE /* end */, text, 1 /* newCursorPosition */);
            } else {
                ic.commitText(new InputConnectionCommandHeader(0), text,
                        1 /* newCursorPosition */);
            }
            if (commit) {
                // Use the saved editor info of the current client on CC display to perform the
                // default editor action (if any). Otherwise fallback to pressing enter key.
                // Introduced a delay for performing editor action/pressing enter key to let the
                // text be committed to text field first. Some apps might be processing input
                // connection actions differently causing race conditions between "insertion of
                // text" and "committing the text" actions. Introducing a small delay (50 ms),
                // would ensure things happen in order.
                final EditorInfo editorInfo = data.editorInfo();
                mInsertTextFuture = mScheduler.schedule(() -> {
                    try {
                        if (!performDefaultEditorAction(editorInfo, ic)) {
                            Slog.w(TAG,
                                    "Unable to perform editor action to commit text: defaulting "
                                            + "to pressing enter key");
                            ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                            ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                                    new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to commit text through InputConnection", e);
                    }
                }, KEY_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to insert text through InputConnection", e);
            return;
        }
        mStatsController.onInsertText();
    }

    @Override
    public void attachNotificationInfo(int notificationId, String notificationTag) {
        synchronized (mNotificationLock) {
            if (mNotificationInfo != null) {
                throw new IllegalStateException("Notification info already set");
            }
            mNotificationInfo = new NotificationInfo(notificationId, notificationTag);
        }
    }

    @Override
    public void setPreviewIntent(@Nullable PendingIntent previewIntent) {
        synchronized (mPreviewIntentLock) {
            mPreviewIntent = previewIntent;
        }
    }

    @Override
    public boolean requestScreenshot() {
        if (mLifecycle.getCurrentState() instanceof LifecycleState.Closed) {
            Slog.e(TAG, "Cannot request screenshot: Session is closed");
            return false;
        }

        // Limit screenshots to the task of allowlisted packages of the automated apps.
        // In the Blocked state, this check ensures the agent only sees authorized content.
        synchronized (mAllowedTaskIds) {
            if (mIsTopActivityScreenshotAllowed == null) {
                Slog.w(TAG, "Screenshot blocked: There is no top activity on the display.");
                return false;
            }
            if (Boolean.FALSE.equals(mIsTopActivityScreenshotAllowed)) {
                Slog.w(TAG, "Screenshot blocked: Top task not part of the initial automated set.");
                return false;
            }
        }

        synchronized (mWindowDrawLock) {
            if (mIsWaitingForWindowDraw) {
                Slog.w(TAG, "Cannot request screenshot: Window draw is already in progress");
                return false;
            }
            if (mIsWaitingForScreenshotResult) {
                Slog.w(TAG, "Cannot request screenshot: Awaiting result for previous request");
                return false;
            }
            final boolean success = mWindowManagerInternal.requestHardwareRendererOutputEnabled(
                    mVirtualDisplayId, WINDOW_DRAW_TIMEOUT_MS, this::onWindowsDrawnCallback,
                    mScheduler);
            if (!success) {
                return false;
            }
            mIsWaitingForWindowDraw = true;
            mIsWaitingForScreenshotResult = true;
            Trace.asyncTraceForTrackBegin(mTraceTrack, "isWaitingForWindowDraw",
                    TRACE_COOKIE_WINDOW_DRAW);
            return true;
        }
    }

    @Override
    public void notifyScreenshotResult() {
        synchronized (mInteractiveMirrors) {
            synchronized (mWindowDrawLock) {
                if (!mIsWaitingForScreenshotResult) {
                    return;
                }
                mIsWaitingForScreenshotResult = false;
                trackWindowDrawFinishLocked();
            }
        }
    }

    /** Retrieves the Task ID for the top activity on a given display. */
    private int getTopTaskId(int displayId) {
        List<ActivityAssistInfo> topActivities =
                mActivityTaskManagerInternal.getTopVisibleActivities(displayId);
        if (topActivities != null && !topActivities.isEmpty()) {
            return topActivities.get(0).getTaskId();
        }
        return -1;
    }

    private void onWindowsDrawnCallback(boolean success) {
        if (!success) {
            Slog.w(TAG, "Timed out waiting for windows to be drawn!");
        }
        synchronized (mInteractiveMirrors) {
            synchronized (mWindowDrawLock) {
                mIsWaitingForWindowDraw = false;
                trackWindowDrawFinishLocked();
            }
        }
    }

    @GuardedBy({"mInteractiveMirrors", "mWindowDrawLock"})
    private void trackWindowDrawFinishLocked() {
        if (mIsWaitingForWindowDraw || mIsWaitingForScreenshotResult) {
            return;
        }
        if (mInteractiveMirrors.isEmpty()) {
            mWindowManagerInternal.requestHardwareRendererOutputDisabled(
                    mVirtualDisplayId);
        }
        Trace.asyncTraceForTrackEnd(mTraceTrack, TRACE_COOKIE_WINDOW_DRAW);
    }

    @Override
    public void notifyBlocked() {
        mLifecycle.updateLifecycleState((config) -> {
            config.mCallerInitiatedBlock = true;
        });
    }

    @Override
    public void requestUnblock() {
        mLifecycle.exitBlockedState();
    }

    @Override
    public void close() throws RemoteException {
        close(CLOSE_REASON_CALLER_INITIATED);
    }

    void close(@ComputerControlSession.SessionCloseReason int closeReason) {
        mLifecycle.updateLifecycleState((config) -> {
            if (config.mClosed != null) {
                return;
            }
            config.mClosed = new LifecycleState.Closed(closeReason);
        });
    }

    @Override
    public void binderDied() {
        try {
            close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void releaseResources() {
        cancelOngoingInteractions();
        mSessionTimeoutTimer.close();
        mAudioInjector.stopAudioInjection();
        mAudioCapture.stopAudioCapture();
        mVirtualDevice.close(); // closes also the VirtualAudioDevice
        mRequest.appToken().unlinkToDeath(this, 0);
        makeSessionNotificationCancellable();
        removeAllInteractiveMirrorsOnSessionClose();
        mOnClosedListener.accept(this);
        mOwnerAppOpsManager.stopWatchingMode(this);
    }

    private void postSessionNotification() {
        ComputerControlSessionParams.NotificationParams notificationParams =
                mRequest.params().getNotificationParams();
        if (notificationParams != null) {
            Notification notification = notificationParams.getNotification();
            notification.flags |= FLAG_COMPUTER_CONTROL;
            mRequest.ownerNotificationManager().notifyAsPackage(mRequest.ownerPackageName(),
                    notificationParams.getNotificationTag(),
                    notificationParams.getNotificationId(),
                    notification);
        }
    }

    private void makeSessionNotificationCancellable() {
        ComputerControlSessionParams.NotificationParams notificationParams =
                mRequest.params().getNotificationParams();
        if (notificationParams != null) {
            LocalServices.getService(NotificationManagerInternal.class)
                    .removeComputerControlFlagFromNotification(mRequest.ownerPackageName(),
                            notificationParams.getNotificationId(), mRequest.ownerUserId());
        }
    }

    private void performSwipeStep(int fromX, int fromY, int toX, int toY, int step, int stepCount) {
        final double fraction = ((double) step) / stepCount;
        // This makes the movement distance smaller towards the end.
        final double easedFraction = Math.sin(fraction * Math.PI / 2);
        final int currentX = (int) (fromX + (toX - fromX) * easedFraction);
        final int currentY = (int) (fromY + (toY - fromY) * easedFraction);
        final int nextStep = step + 1;

        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(currentX, currentY, VirtualTouchEvent.ACTION_MOVE));

        if (nextStep > stepCount) {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(toX, toY, VirtualTouchEvent.ACTION_UP));
            mSwipeFuture = null;
            return;
        }

        mSwipeFuture = mScheduler.schedule(
                () -> performSwipeStep(fromX, fromY, toX, toY, nextStep, stepCount),
                TOUCH_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private boolean performDefaultEditorAction(@Nullable EditorInfo editorInfo,
            @NonNull IRemoteComputerControlInputConnection ic) throws RemoteException {
        // Check if currently active input connection on CC display has a valid editor action
        // provided by the client view
        if (editorInfo != null && editorInfo.imeOptions != EditorInfo.IME_ACTION_UNSPECIFIED
                && (editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION)
                != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(new InputConnectionCommandHeader(0),
                    editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION);
            return true;
        }
        return false;
    }

    private void cancelOngoingInteractions() {
        if (mInsertTextFuture != null) {
            mInsertTextFuture.cancel(false);
            mInsertTextFuture = null;
        }
        if (mSwipeFuture != null && mSwipeFuture.cancel(false)) {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(0, 0, VirtualTouchEvent.ACTION_CANCEL));
        }
    }

    private String createInputDeviceNamePrefix(String packageName) {
        final String prefix = packageName + ":" + getName();

        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_INPUT_DEVICE_NAME_PREFIX_BYTES) {
            return prefix;
        }

        int startIndex = bytes.length - MAX_INPUT_DEVICE_NAME_PREFIX_BYTES;
        while (startIndex < bytes.length) {
            byte currentByte = bytes[startIndex];
            // Check if the byte is a continuation byte (0x80 <= byte <= 0xBF)
            // In Java, bytes are signed, so the range check is: (currentByte & 0xC0) == 0x80
            if ((currentByte & 0xC0) == 0x80) {
                // This is a continuation byte, so we must advance the start index
                startIndex++;
            } else {
                // This is a start byte (or an ASCII byte), which is a safe cut-off point.
                break;
            }
        }
        byte[] truncatedBytes = Arrays.copyOfRange(bytes, startIndex, bytes.length);
        return new String(truncatedBytes, StandardCharsets.UTF_8);
    }

    private boolean isActivityLaunchAllowed(@NonNull ComponentName componentName,
            @UserIdInt int userId) {
        synchronized (mAllowlistedPackages) {
            if (!mAllowlistedPackages.contains(componentName.getPackageName())) {
                return false;
            }
        }

        // TODO: b/451568055 - Support cross-user sessions.
        return userId == UserHandle.USER_SYSTEM || userId == mRequest.ownerUserId();
    }

    @Nullable
    private Intent getLaunchIntent(@NonNull String packageName, @Nullable String className) {
        if (className == null) {
            return mRequest.ownerPackageManager().getLaunchIntentForPackage(packageName);
        }
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(packageName, className);
        final List<ResolveInfo> resolveInfos = mRequest.ownerPackageManager()
                .queryIntentActivities(intent, ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        if (resolveInfos.isEmpty()) {
            return null;
        }
        return intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private InputMethodManagerInternal.ComputerControlInputConnectionData getInputConnectionData(
            int displayId) {
        // getUserAssignedToDisplay returns the main userId, if we want to support cross
        // profile CC interactions and typing on CC display, we need to find the right user
        // profile here for the CC input connection
        return mInputMethodManagerInternal.getComputerControlInputConnectionData(
                mUserManagerInternal.getUserAssignedToDisplay(displayId), displayId);
    }

    private void moveAllTasks(int fromDisplayId, int toDisplayId) {
        mActivityTaskManagerInternal.moveAllTasks(fromDisplayId, toDisplayId);
    }

    private boolean shouldDisallowInteractions(String callSite) {
        // TODO: b/452428736 - Find a long term solution for blocking agent interactions.
        if (!isSessionActive()) {
            Slog.w(TAG, "Computer control interaction blocked since session is not active: "
                    + callSite);
            return true;
        }
        return false;
    }

    private static VirtualTouchEvent createTouchEvent(int x, int y,
            @VirtualTouchEvent.Action int action) {
        return new VirtualTouchEvent.Builder()
                .setX(x)
                .setY(y)
                .setAction(action)
                .setPointerId(4)
                .setToolType(
                        action == VirtualTouchEvent.ACTION_CANCEL
                                ? VirtualTouchEvent.TOOL_TYPE_PALM
                                : VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setPressure(255)
                .setMajorAxisSize(1)
                .build();
    }

    private static VirtualKeyEvent createKeyEvent(int keyCode, @VirtualKeyEvent.Action int action) {
        return new VirtualKeyEvent.Builder()
                .setAction(action)
                .setKeyCode(keyCode)
                .build();
    }

    private void cancelDisplayEmptyScheduledAction() {
        final var action = mDisplayEmptyScheduledAction;
        if (action != null) {
            action.cancel(false);
        }
    }

    private void updateInsets() {
        synchronized (mInteractiveMirrors) {
            var insets = Insets.NONE;
            for (int i = 0; i < mInteractiveMirrors.size(); i++) {
                insets = Insets.max(insets, mInteractiveMirrors.get(i).getRequestedInsets());
            }
            final var finalInsets = insets;
            UiThread.getHandler().post(() -> handleInsetsUpdate(finalInsets));
        }
    }

    // Propagates the insets provided by the interactive mirrors to the virtual display.
    // Currently, MirrorView will only send its systemBars() and ime() insets, and we propagate it
    // to the virtual display as systemOverlays().
    @android.annotation.UiThread
    private void handleInsetsUpdate(@NonNull Insets insets) {
        if (Objects.equals(mAppliedInsets, insets)) {
            return;
        }

        Slog.d(TAG,
                "handleInsetsUpdate: Updating insets from old: " + mAppliedInsets + ", to new: "
                        + insets);
        mAppliedInsets = insets;

        final var wm = mDisplayUiContext.getSystemService(WindowManager.class);
        if (Insets.NONE.equals(mAppliedInsets)) {
            if (mInsetsProviderView != null) {
                wm.removeView(mInsetsProviderView);
                mInsetsProviderView = null;
            }
            return;
        }

        mInsetsProviderLayoutParams.setInsetsParams(
                List.of(new WindowManager.InsetsParams(
                                WindowInsets.Type.systemOverlays())
                                .setInsetsSize(Insets.of(insets.left, 0, 0, 0)),
                        new WindowManager.InsetsParams(
                                WindowInsets.Type.systemOverlays())
                                .setInsetsSize(Insets.of(0, insets.top, 0, 0)),
                        new WindowManager.InsetsParams(
                                WindowInsets.Type.systemOverlays())
                                .setInsetsSize(Insets.of(0, 0, insets.right, 0)),
                        new WindowManager.InsetsParams(
                                WindowInsets.Type.systemOverlays())
                                .setInsetsSize(Insets.of(0, 0, 0, insets.bottom))
                ));
        if (mInsetsProviderView == null) {
            mInsetsProviderView = new View(mDisplayUiContext);
            wm.addView(mInsetsProviderView, mInsetsProviderLayoutParams);
        } else {
            wm.updateViewLayout(mInsetsProviderView, mInsetsProviderLayoutParams);
        }
    }

    private static WindowManager.LayoutParams createInsetsProviderLayoutParams() {
        final var lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSPARENT);
        lp.setTitle("InsetsProviderView");
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return lp;
    }

    private class ComputerControlActivityListener implements VirtualDeviceManager.ActivityListener {
        @Override
        public void onTopActivityChanged(int displayId, @NonNull ComponentName topActivity) {}

        @Override
        public void onTopActivityChanged(int displayId, @NonNull ComponentName topActivity,
                @UserIdInt int userId) {
            Slog.v(TAG, "Top activity changed to " + topActivity + " for user " + userId);
            cancelDisplayEmptyScheduledAction();

            // If this new activity belongs to a package the session is authorized to control,
            // we should trust it, even if it's a secondary task (like a new Chrome window).
            synchronized (mAllowedTaskIds) {
                boolean isPackageAllowed = mRequest.params().getTargetPackageNames()
                        .contains(topActivity.getPackageName());
                int taskId = isPackageAllowed ? getTopTaskId(mVirtualDisplayId) : -1;
                if (taskId != -1) {
                    mAllowedTaskIds.add(taskId);
                }

                // Screenshots are only allowed if the package is valid AND we have a valid Task ID
                mIsTopActivityScreenshotAllowed = (taskId != -1);
            }

            // If we have a new top activity which is allowed, then attempt a transition to the
            // active state.
            if (isActivityLaunchAllowed(topActivity, userId)) {
                mLifecycle.updateLifecycleState((config) -> {
                    config.mBlockingActivityPackage = null;
                });
            }
        }

        @Override
        public void onDisplayEmpty(int displayId) {
            Slog.v(TAG, "Display empty");
            mLifecycle.updateLifecycleState((config) -> {
                config.mBlockingActivityPackage = null;
                config.mSecureWindowPackage = null;
            });
            cancelDisplayEmptyScheduledAction();
            // Close the session if the display remains empty after the timeout.
            mDisplayEmptyScheduledAction = mScheduler.schedule(
                    () -> close(CLOSE_REASON_SESSION_EMPTY),
                    CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            synchronized (mAllowedTaskIds) {
                mAllowedTaskIds.clear();
                mIsTopActivityScreenshotAllowed = null;
            }
        }

        @Override
        @SuppressLint("AndroidFrameworkRequiresPermission")
        public void onActivityLaunchBlocked(int displayId, @NonNull ComponentName componentName,
                @NonNull UserHandle user, IntentSender intentSender) {
            Slog.w(TAG, "Unexpectedly blocked activity launch for " + componentName
                    + " on session " + getName());
        }

        @Override
        public void onSecureWindowShown(int displayId, @NonNull ComponentName componentName,
                @NonNull UserHandle user) {
            Slog.v(TAG, "Secure window shown for " + componentName);
            mLifecycle.updateLifecycleState((config) -> config.mSecureWindowPackage =
                    Objects.requireNonNull(componentName.getPackageName()));
        }

        @Override
        public void onSecureWindowHidden(int displayId) {
            Slog.v(TAG, "Secure window hidden");
            mLifecycle.updateLifecycleState((config) -> config.mSecureWindowPackage = null);
        }

        @Override
        public void onActivityLaunchRequested(int displayId, @NonNull ComponentName componentName,
                @UserIdInt int userId) {
            Slog.v(TAG, "Activity launch requested for " + componentName + " for user "
                    + userId);
            // If we have an activity launch request which is not allowed, then transition to
            // blocked state.
            if (!isActivityLaunchAllowed(componentName, userId)) {
                mLifecycle.updateLifecycleState(
                        (config) -> config.mBlockingActivityPackage =
                                Objects.requireNonNull(componentName.getPackageName()));
                return;
            }
            if (mAppInteractionService != null) {
                long now = System.currentTimeMillis();
                mFgThreadExecutor.execute(
                        () -> {
                            mAppInteractionService.noteAppInteraction(
                                    mRequest.ownerPackageName(),
                                    componentName.getPackageName(),
                                    mRequest.params().getAppInteractionAttribution(),
                                    now,
                                    userId);
                        });
            }
        }

        @Override
        public void onAuthenticationPrompt(int displayId, String packageName) {
            mLifecycle.updateLifecycleState(
                    (config) -> {
                        config.mAuthenticationPromptPackage = packageName;
                    });
        }
    }

    static final class NotificationInfo {
        private final int mNotificationId;
        @Nullable
        private final String mNotificationTag;

        NotificationInfo(int notificationId, @Nullable String notificationTag) {
            this.mNotificationId = notificationId;
            this.mNotificationTag = notificationTag;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NotificationInfo that = (NotificationInfo) o;
            return mNotificationId == that.mNotificationId
                    && Objects.equals(mNotificationTag, that.mNotificationTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mNotificationId, mNotificationTag);
        }
    }
}
