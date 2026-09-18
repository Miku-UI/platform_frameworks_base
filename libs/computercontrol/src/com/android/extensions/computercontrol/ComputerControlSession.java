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

package com.android.extensions.computercontrol;

import android.annotation.CallbackExecutor;
import android.annotation.DurationMillisLong;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppInteractionAttribution;
import android.app.Notification;
import android.app.PendingIntent;
import android.companion.DeviceId;
import android.companion.virtual.computercontrol.InteractiveMirror;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.media.Image;
import android.media.ImageReader;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.util.Size;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.os.IResultReceiver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Computer control sessions are used to allow a computer of some sort to control applications on
 * the Android device.
 *
 * <p>Applications can be launched in the computer control session and can then be controlled by
 * injecting input events into the session.</p>
 *
 * <p>When the session is no longer used it should be closed by calling {@link #close()} to clean up
 * resources and reduce the system-wide impact computer control sessions can have.</p>
 */
public final class ComputerControlSession implements AutoCloseable {

    /**
     * Unknown session creation error.
     */
    public static final int ERROR_UNKNOWN =
            android.companion.virtual.computercontrol.ComputerControlSession.ERROR_UNKNOWN;

    /**
     * Error code indicating that a new session cannot be created because the maximum number of
     * allowed concurrent sessions has been reached.
     *
     * <p>This is a transient error and the session creation request can be retried later.</p>
     */
    public static final int ERROR_SESSION_LIMIT_REACHED =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .ERROR_SESSION_LIMIT_REACHED;

    /**
     * Error code indicating that a new session cannot be created because the device is currently
     * locked.
     *
     * <p>This is a transient error and the session creation request can be retried later.</p>
     *
     * @see android.app.KeyguardManager#isDeviceLocked()
     */
    public static final int ERROR_DEVICE_LOCKED =
            android.companion.virtual.computercontrol.ComputerControlSession.ERROR_DEVICE_LOCKED;

    /**
     * Error code indicating that the caller does not have permission to create a session, which is
     * possible if the user did not approve the creation of a new session, or if the caller does not
     * have a visible non-toast window on any display.
     */
    public static final int ERROR_PERMISSION_DENIED =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .ERROR_PERMISSION_DENIED;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ERROR_", value = {
            ERROR_UNKNOWN,
            ERROR_SESSION_LIMIT_REACHED,
            ERROR_DEVICE_LOCKED,
            ERROR_PERMISSION_DENIED})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionCreationError {
    }

    /**
     * Unknown session close reason.
     */
    public static final int CLOSE_REASON_UNKNOWN =
            android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_UNKNOWN;

    /**
     * Close reason indicating the session was closed by the caller.
     *
     * @see ComputerControlSession#close()
     */
    public static final int CLOSE_REASON_CALLER_INITIATED =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .CLOSE_REASON_CALLER_INITIATED;

    /**
     * Close reason indicating the session was closed by the user during an auth flow or to take
     * control of the app under automation, etc.
     */
    public static final int CLOSE_REASON_USER_INITIATED =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .CLOSE_REASON_USER_INITIATED;

    /**
     * Close reason indicating the session timed out.
     */
    public static final int CLOSE_REASON_SESSION_TIMED_OUT =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .CLOSE_REASON_SESSION_TIMED_OUT;

    /**
     * Close reason indicating that the session became empty.
     */
    public static final int CLOSE_REASON_SESSION_EMPTY =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .CLOSE_REASON_SESSION_EMPTY;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "CLOSE_REASON_", value = {
            CLOSE_REASON_UNKNOWN,
            CLOSE_REASON_CALLER_INITIATED,
            CLOSE_REASON_USER_INITIATED,
            CLOSE_REASON_SESSION_TIMED_OUT,
            CLOSE_REASON_SESSION_EMPTY,
    })
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionCloseReason {
    }

    /**
     * Unknown session block reason.
     */
    public static final int BLOCK_REASON_UNKNOWN =
            android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_UNKNOWN;

    /**
     * Reason indicating that the session was blocked due to secure content being present.
     */
    public static final int BLOCK_REASON_SECURE_CONTENT =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .BLOCK_REASON_SECURE_CONTENT;

    /**
     * Reason indicating that the session was blocked due to a disallowed activity being launched
     * in the session.
     */
    public static final int BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH;

    /**
     * Reason indicating that the session was blocked due to a {@link #notifyBlocked()} request from
     * the caller.
     */
    public static final int BLOCK_REASON_CALLER_INITIATED =
            android.companion.virtual.computercontrol.ComputerControlSession
                    .BLOCK_REASON_CALLER_INITIATED;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "BLOCK_REASON_", value = {
            BLOCK_REASON_UNKNOWN,
            BLOCK_REASON_SECURE_CONTENT,
            BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
            BLOCK_REASON_CALLER_INITIATED,
    })
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface SessionBlockReason {
    }

    /**
     * Computer control action that performs back navigation.
     */
    public static final int ACTION_GO_BACK =
            android.companion.virtual.computercontrol.ComputerControlSession.ACTION_GO_BACK;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ACTION_", value = {
            ACTION_GO_BACK,
    })
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface Action {
    }

    /**
     * Unknown unstable reason.
     */
    public static final int UNSTABLE_REASON_UNKNOWN = 0;

    /**
     * Reason indicating that the session became unstable due to an interaction performed by the
     * caller, such as {@link #tap(int, int)}.
     */
    public static final int UNSTABLE_REASON_CALLER_INTERACTION = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "UNSTABLE_REASON_", value = {
            UNSTABLE_REASON_UNKNOWN,
            UNSTABLE_REASON_CALLER_INTERACTION,
    })
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface UnstableReason {
    }

    private static final long DEFAULT_STABILITY_TIMEOUT_MS = 500L;

    private final android.companion.virtual.computercontrol.ComputerControlSession mSession;

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    ComputerControlSession(
            @NonNull android.companion.virtual.computercontrol.ComputerControlSession session) {
        mSession = session;
    }

    /**
     * Launches an application's launcher activity in the computer control session.
     *
     * @throws IllegalArgumentException if the package does not have a launcher activity.
     * @see Params#getTargetPackageNames()
     */
    public void launchApplication(@NonNull String packageName) {
        mSession.launchApplication(packageName);
    }

    /**
     * Launches an application's launcher activity in the computer control session.
     *
     * @throws IllegalArgumentException if the component is not a launcher activity.
     * @see Params#getTargetPackageNames()
     */
    public void launchApplication(@NonNull ComponentName component) {
        mSession.launchApplication(component);
    }

    /**
     * Hand over full control of the automation session to the user.
     *
     * <p>All of the applications currently automated in the session are moved from the session's
     * display to the user's default display. No further automation is possible on these tasks,
     * although the session remains active and new applications may be launched via
     * {@link #launchApplication(String)}</p>
     */
    public void handOverApplications() {
        mSession.handOverApplications();
    }

    /**
     * Screenshot the current display content synchronously.
     *
     * <p>The behavior is similar to {@link ImageReader#acquireLatestImage}, meaning that any
     * previously acquired images should be released before attempting to acquire new ones.</p>
     *
     * NOTE: This is a blocking call! If a screenshot is not immediately available, this method
     * will block until the next frame is produced, or until the method times out. A successful
     * screenshot acquisition could take in the order of 10s of milliseconds if one is not
     * immediately available.
     *
     * @return A screenshot of the current display content, or {@code null} if no screenshot is
     *   currently available.
     * @deprecated Use {@link #requestScreenshot(Executor, OutcomeReceiver, CancellationSignal)}
     *   instead.
     */
    @Nullable
    public Image getScreenshot() {
        return mSession.getScreenshot();
    }

    /**
     * Exception used to indicate how a screenshot request failed.
     */
    public static class ScreenshotException extends Exception {

        /** An unknown error occurred when processing this screenshot request. */
        public static final int ERROR_UNKNOWN =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_UNKNOWN;

        /** The request to receive a screenshot timed out. */
        public static final int ERROR_TIMEOUT =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_TIMEOUT;

        /** The request to receive a screenshot was cancelled. */
        public static final int ERROR_CANCELED =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_CANCELED;

        /** The session is in a state where taking screenshots is prohibited. */
        public static final int ERROR_PROHIBITED =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_PROHIBITED;

        /** The session encountered an internal error, and the client may try again later. */
        public static final int ERROR_INTERNAL =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_INTERNAL;

        /** The session encountered a remote error. */
        public static final int ERROR_REMOTE =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_REMOTE;

        /** A previous request to receive a screenshot is still active. */
        public static final int ERROR_DUPLICATE_REQUEST =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_DUPLICATE_REQUEST;

        /** The screenshot request was successful, but the screenshot is unchanged. */
        public static final int ERROR_SCREEN_UNCHANGED =
                android.companion.virtual.computercontrol.ComputerControlSession
                        .ScreenshotException.ERROR_SCREEN_UNCHANGED;

        @ErrorCode
        private final int mErrorCode;

        /**
         * Get the error code that describes the failure mode.
         */
        @ErrorCode
        public int getErrorCode() {
            return mErrorCode;
        }

        /** @hide */
        @IntDef(value = {
                ERROR_UNKNOWN,
                ERROR_TIMEOUT,
                ERROR_CANCELED,
                ERROR_PROHIBITED,
                ERROR_INTERNAL,
                ERROR_REMOTE,
                ERROR_DUPLICATE_REQUEST,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ErrorCode {
        }

        /** @hide */
        public ScreenshotException(@ErrorCode int errorCode) {
            super("errorCode=" + errorCode);
            mErrorCode = errorCode;
        }
    }

    /**
     * Requests a screenshot of the current display content asynchronously.
     *
     * <p>This is a one-shot operation. A new screenshot request must be made for each screenshot.
     *
     * <p>The behavior is similar to {@link ImageReader#acquireLatestImage}, meaning that any
     * previously acquired images should be released before attempting to acquire new ones.
     *
     * <p>Only one screenshot request can be active at a time. If a new screenshot is requested
     * while a previous one is still pending, the new request will be rejected.
     *
     * @param executor The executor on which the callback will be invoked.
     * @param receiver The outcome receiver callback to be invoked when the screenshot is available
     *                or encounters any issues.
     *
     * @see ScreenshotException
     */
    @SuppressLint("SamShouldBeLast")
    public void requestScreenshot(@NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Image, ScreenshotException> receiver,
            @Nullable CancellationSignal cancellationSignal) {
        mSession.requestScreenshot(executor, new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Image result) {
                        receiver.onResult(result);
                    }

            @Override
            public void onError(
                    @NonNull android.companion.virtual.computercontrol.ComputerControlSession
                            .ScreenshotException error) {
                receiver.onError(new ScreenshotException(error.getErrorCode()));
            }
        }, cancellationSignal);
    }

    /**
     * Sends a tap event to the computer control session for the given coordinates.
     */
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
        mSession.tap(x, y);
    }

    /**
     * Sends a swipe event to the computer control session for the given coordinates.
     *
     * <p>To avoid misinterpreting the swipe as a fling, the individual touches are throttled, so
     * the entire action will take ~500ms. However, this is done in the background and this method
     * returns immediately. Any ongoing swipe will be canceled if a new swipe is requested.</p>
     */
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int toY) {
        mSession.swipe(fromX, fromY, toX, toY);
    }

    /**
     * Sends a long press event to the computer control session for the given coordinates.
     */
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
        mSession.longPress(x, y);
    }

    /**
     * Inserts provided text into the currently active text field on the display associated with
     * the {@link ComputerControlSession}.
     *
     * <p> This method expects a text field to be in focus with an active {@link InputConnection}.
     * It inserts text at the current cursor position in the text field and moves the cursor to
     * the end of inserted text. </p>
     *
     * @param text to be inserted
     * @param replaceExisting whether the current text in the text field needs to be overwritten
     * @param commit whether the text should be submitted after insertion
     */
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        mSession.insertText(text, replaceExisting, commit);
    }

    /**
     * Perform provided action on the display associated with the {@link ComputerControlSession}.
     */
    public void performAction(@Action int actionCode) {
        mSession.performAction(actionCode);
    }

    /**
     * Returns A11y information for all windows on the display associated with the
     * {@link ComputerControlSession}, or an empty list if no information is currently available.
     */
    @NonNull
    public List<AccessibilityWindowInfo> getAccessibilityWindows() {
        return mSession.getAccessibilityWindows();
    }

    /**
     * Returns an {@link InteractiveMirror} which mirrors this {@link ComputerControlSession}.
     *
     * @hide
     */
    public InteractiveMirror createInteractiveMirror(
            IResultReceiver a11yEmbeddedConnectionReceiver) {
        return mSession.createInteractiveMirror(a11yEmbeddedConnectionReceiver);
    }

    /**
     * Get the size of the session's display.
     */
    @NonNull
    public Size getDisplaySize() {
        return mSession.getDisplaySize();
    }

    /**
     * Sets a {@link StabilityListener} to be notified when the computer control session is
     * potentially stable.
     *
     * @throws IllegalStateException if a listener was previously set.
     */
    public void setStabilityListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull StabilityListener listener) {
        setStabilityListener(Duration.ofMillis(DEFAULT_STABILITY_TIMEOUT_MS), executor, listener);
    }

    /**
     * Sets a {@link StabilityListener} to be notified when the computer control session is
     * potentially stable.
     *
     * @param timeoutMillis timeout (in millis) after which the session would be considered stable,
     *                      upon any action or any framework event
     * @param executor {@link Executor} on which this listener would be invoked
     * @param listener {@link StabilityListener} which would be invoked
     *
     * @throws IllegalStateException if a listener was previously set.
     */
    public void setStabilityListener(@DurationMillisLong long timeoutMillis,
            @NonNull @CallbackExecutor Executor executor, @NonNull StabilityListener listener) {
        setStabilityListener(Duration.ofMillis(timeoutMillis), executor, listener);
    }

    /**
     * Sets a {@link StabilityListener} to be notified when the computer control session is
     * potentially stable.
     *
     * @param duration {@link Duration} after which the session would be considered stable, upon any
     *                                 action or any framework event
     * @param executor {@link Executor} on which this listener would be invoked
     * @param listener {@link StabilityListener} which would be invoked
     *
     * @throws IllegalStateException if a listener was previously set.
     */
    public void setStabilityListener(@NonNull Duration duration,
            @NonNull @CallbackExecutor Executor executor, @NonNull StabilityListener listener) {
        mSession.setStabilityListener(duration, executor,
                new android.companion.virtual.computercontrol.ComputerControlSession
                        .StabilityListener() {
                    @Override
                    public void onSessionStable() {
                        listener.onSessionStable();
                    }

                    @Override
                    public void onSessionUnstable(int reason) {
                        listener.onSessionUnstable(reason);
                    }
                });
    }

    /**
     * Clears any {@link StabilityListener} that was previously set using
     * {@link #setStabilityListener(Duration, Executor, StabilityListener)}.
     */
    public void clearStabilityListener() {
        mSession.clearStabilityListener();
    }

    /**
     * Sets a {@link LifecycleCallback} to be notified about the computer control session lifecycle
     * changes.
     *
     * @throws IllegalStateException if a callback was previously set.
     */
    public void setLifecycleCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull LifecycleCallback callback) {
        Objects.requireNonNull(callback);
        mSession.setLifecycleCallback(executor,
                new android.companion.virtual.computercontrol.ComputerControlSession
                        .LifecycleCallback() {
                    @Override
                    public void onActive() {
                        callback.onActive();
                    }

                    @Override
                    public void onBlocked(@SessionBlockReason int reason,
                            @Nullable String blockingPackage) {
                        callback.onBlocked(reason, blockingPackage);
                    }

                    @Override
                    public void onClosed(@SessionCloseReason int reason) {
                        callback.onClosed(reason);
                    }
                });
    }

    /**
     * Clears any {@link LifecycleCallback} that was previously set using
     * {@link #setLifecycleCallback(Executor, LifecycleCallback)}.
     *
     * @throws IllegalStateException if a callback was not previously set.
     */
    public void clearLifecycleCallback() {
        mSession.clearLifecycleCallback();
    }

    /**
     * Attaches notification information to the session, to make the notification non-dismissible.
     *
     * <p>This must be called before posting the notification.</p>
     *
     * <p>The caller must still call {@link Notification.Builder#setOngoing(boolean)}
     * with {@code true}, to make the notification non-dismissible.</p>
     *
     * @param notificationId id of the notification, as per
     * {@link android.app.NotificationManager#notify(String, int, Notification)}
     * @param notificationTag tag of the notification, as per
     * {@link android.app.NotificationManager#notify(String, int, Notification)}
     *
     * @throws IllegalStateException if a notification was already attached.
     *
     * @deprecated with ComputerControl v5, use
     * {@link ComputerControlSession.Params.Builder#setNotificationParams(NotificationParams)}
     * instead.
     */
    @Deprecated
    public void attachNotificationInfo(int notificationId, @Nullable String notificationTag) {
        mSession.attachNotificationInfo(notificationId, notificationTag);
    }

    /**
     * Sets the intent launched when the user wants to preview the automation, or null if none.
     *
     * <p>This overrides the intent set in {@link Params.Builder#setPreviewIntent}.
     */
    public void setPreviewIntent(@Nullable PendingIntent previewIntent) {
        mSession.setPreviewIntent(previewIntent);
    }

    /**
     * Notifies the system that the caller is blocked and unable to perform any further
     * interactions in the session, and needs user intervention to unblock the session. If the
     * request is successful, the session will enter the blocked lifecycle state
     * ({@link LifecycleCallback#onBlocked(int, String)}), with
     * {@link #BLOCK_REASON_CALLER_INITIATED}.
     */
    public void notifyBlocked() {
        mSession.notifyBlocked();
    }

    /**
     * Attempts to exit the blocked state when the session is blocked for any reason. This should
     * be called when the user explicitly chooses to end their control of the session.
     * @hide
     */
    // TODO: b/479510954 - Determine if we want to commit this as an API to allow the client to
    //  exit the Blocked state, or if we want to expose this functionality through platform-driven
    //  UI in the MirrorView.
    public void requestUnblock() {
        mSession.requestUnblock();
    }

    /**
     * Closes the ComputerControl session and release resources. The session can no longer be used
     * after calling this method.
     */
    @Override
    public void close() {
        mSession.close();
    }

    /**
     * Parameters for requesting a {@link ComputerControlSession}, using
     * {@link ComputerControlExtensions#requestSession}.
     */
    public static class Params {
        @NonNull private final Context mContext;
        @NonNull private final String mName;
        @NonNull private final List<String> mTargetPackageNames;
        @Nullable private final PendingIntent mPreviewIntent;
        @Nullable private final AppInteractionAttribution mAppInteractionAttribution;
        @Nullable private final DeviceId mCompanionDeviceId;
        @Nullable private final NotificationParams mNotificationParams;

        private Params(
                @NonNull Context context,
                @NonNull String name,
                @NonNull List<String> targetPackageNames,
                @Nullable PendingIntent previewIntent,
                @Nullable AppInteractionAttribution appInteractionAttribution,
                @Nullable DeviceId companionDeviceId,
                @Nullable NotificationParams notificationParams) {
            mContext = context;
            mName = name;
            mTargetPackageNames = targetPackageNames;
            mPreviewIntent = previewIntent;
            mAppInteractionAttribution = appInteractionAttribution;
            mCompanionDeviceId = companionDeviceId;
            mNotificationParams = notificationParams;
        }

        /**
         * Returns the context used to construct the ComputerControlSession.
         */
        @NonNull
        public Context getContext() {
            return mContext;
        }

        /**
         * Returns the name of the computer control session, only used internally and not shown to
         * the user.
         */
        @NonNull
        public String getName() {
            return mName;
        }

        /**
         * Returns the package names of the applications that may be automated during this session.
         *
         * @see Builder#setTargetPackageNames(List)
         */
        @NonNull
        public List<String> getTargetPackageNames() {
            return mTargetPackageNames;
        }

        /**
         * Returns the intent launched when the user wants to preview the automation, or null if
         * none is set.
         *
         * @see Builder#setPreviewIntent(PendingIntent)
         * @hide
         */
        @Nullable
        public PendingIntent getPreviewIntent() {
            return mPreviewIntent;
        }

        /**
         * Returns the attribution for the app interaction that triggered the creation of this
         * session.
         *
         * @see Builder#setAppInteractionAttribution(AppInteractionAttribution)
         */
        @Nullable
        public AppInteractionAttribution getAppInteractionAttribution() {
            return mAppInteractionAttribution;
        }

        /**
         * Returns the companion device id of the device that is controlling this session.
         */
        @Nullable
        public DeviceId getCompanionDeviceId() {
            return mCompanionDeviceId;
        }

        /*
         * Returns the notification parameters for this session.
         */
        @Nullable
        public NotificationParams getNotificationParams() {
            return mNotificationParams;
        }

        /**
         * Builder for {@link Params}.
         */
        public static class Builder {
            @NonNull private final Context mContext;
            private String mName;
            private List<String> mTargetPackageNames = Collections.emptyList();
            private PendingIntent mPreviewIntent;
            private AppInteractionAttribution mAppInteractionAttribution;
            private DeviceId mCompanionDeviceId;
            private NotificationParams mNotificationParams;

            /**
             * Create a new Builder.
             */
            public Builder(@NonNull Context context) {
                mContext = context;
            }

            /**
             * Set the name of the session. Only used internally and not shown to users.
             */
            @NonNull
            public Builder setName(@NonNull String name) {
                mName = name;
                return this;
            }

            /**
             * Set the package names of all applications that may be automated during this session.
             *
             * <p>All package names specified in the list must meet the following requirements:
             * <ol>
             *     <li>The package name has a valid launcher Intent.</li>
             *     <li>The package name is not the device permission controller.</li>
             * </ol>
             */
            @NonNull
            public Builder setTargetPackageNames(@NonNull List<String> targetPackageNames) {
                mTargetPackageNames = targetPackageNames;
                return this;
            }

            /**
             * Set an intent launched when the user wants to preview the automation, or null if
             * none.
             */
            @NonNull
            public Builder setPreviewIntent(@Nullable PendingIntent pendingIntent) {
                mPreviewIntent = pendingIntent;
                return this;
            }

            /**
             * Sets the attribution for the app interaction that triggered the creation of this
             * session.
             */
            @NonNull
            public Builder setAppInteractionAttribution(
                    @Nullable AppInteractionAttribution appInteractionAttribution) {
                mAppInteractionAttribution = appInteractionAttribution;
                return this;
            }

            /**
             * Sets the companion device id of the device that is controlling this session.
             */
            @NonNull
            public Builder setCompanionDeviceId(@Nullable DeviceId companionDeviceId) {
                mCompanionDeviceId = companionDeviceId;
                return this;
            }

            /**
             * Sets the notification parameters for this session.
             *
             * <p>The notification gets posted when the session is created, and canceled when the
             * session is closed. It cannot be dismissed by the user, or canceled by the caller.
             * However, the caller can update the contents of the notification at any time,
             * by using {@link android.app.NotificationManager#notify}. In fact, callers should
             * re-use the same notification for their own foreground service (if any), to avoid any
             * duplicate notifications.
             *
             * <p>{@link Notification#hasPromotableCharacteristics()} must return {@code true} for
             * the notification that is passed, otherwise {@link IllegalArgumentException} is
             * thrown.
             *
             * @param notificationParams The notification parameters.
             * @return This builder.
             */
            @NonNull
            public Builder setNotificationParams(@Nullable NotificationParams notificationParams) {
                mNotificationParams = notificationParams;
                return this;
            }

            /** Build a computer control session. */
            @NonNull
            public Params build() {
                return new Params(
                        mContext,
                        mName,
                        mTargetPackageNames,
                        mPreviewIntent,
                        mAppInteractionAttribution,
                        mCompanionDeviceId,
                        mNotificationParams);
            }
        }
    }

    /**
     * Parameters for the notification associated with this session.
     */
    public static final class NotificationParams {
        @NonNull private final Notification mNotification;
        private final int mNotificationId;
        @Nullable private final String mNotificationTag;

        private NotificationParams(@NonNull Notification notification, int notificationId,
                @Nullable String notificationTag) {
            mNotification = notification;
            mNotificationId = notificationId;
            mNotificationTag = notificationTag;
        }

        /**
         * Returns the notification to be posted.
         */
        @NonNull
        public Notification getNotification() {
            return mNotification;
        }

        /**
         * Returns the id of the notification.
         */
        public int getNotificationId() {
            return mNotificationId;
        }

        /**
         * Returns the tag of the notification.
         */
        @Nullable
        public String getNotificationTag() {
            return mNotificationTag;
        }

        /** Builder for {@link NotificationParams}. */
        public static final class Builder {
            @NonNull private final Notification mNotification;
            private final int mNotificationId;
            @Nullable private String mNotificationTag;

            /**
             * @param notification the {@link Notification} associated with this session
             * @param notificationId the identifier for the notification, as per
             * {@link android.app.NotificationManager#notify(String, int, Notification)}
             */
            public Builder(@NonNull Notification notification, int notificationId) {
                mNotification = notification;
                mNotificationId = notificationId;
            }

            /**
             * Sets the optional tag for the notification.
             *
             * @param notificationTag the tag for the notification, as per
             * {@link android.app.NotificationManager#notify(String, int, Notification)}
             */
            @NonNull
            public Builder setNotificationTag(@Nullable String notificationTag) {
                mNotificationTag = notificationTag;
                return this;
            }

            /** Builds the {@link NotificationParams} instance. */
            @NonNull
            public NotificationParams build() {
                return new NotificationParams(mNotification, mNotificationId, mNotificationTag);
            }
        }
    }

    /** Callback for computer control session events. */
    public interface Callback {

        /**
         * Called when the session request needs to approved by the user.
         *
         * <p>Applications should launch the {@link Activity} "encapsulated" in {@code intentSender}
         * {@link IntentSender} object by calling
         * {@link Activity#startIntentSenderForResult(IntentSender, int, Intent, int, int, int)} or
         * {@link Context#startIntentSender(IntentSender, Intent, int, int, int)}
         *
         * @param intentSender an {@link IntentSender} which applications should use to launch
         *   the UI for the user to allow the creation of the session.
         */
        void onSessionPending(@NonNull IntentSender intentSender);

        /** Called when the session has been successfully created. */
        void onSessionCreated(@NonNull ComputerControlSession session);

        /** Called when the session failed to be created. */
        void onSessionCreationFailed(@SessionCreationError int errorCode);

        /**
         * Called when the session has been closed.
         *
         * @deprecated use {@link LifecycleCallback} instead
         * @see ComputerControlSession#setLifecycleCallback(Executor, LifecycleCallback)
         */
        @Deprecated
        void onSessionClosed();
    }

    /**
     * Listener to be notified of signals indicating that the computer control session is
     * potentially stable.
     *
     * <p>These signals indicate that the session's display content is currently stable, such as the
     * app under automation being idle and no UI animations being under progress. These are useful
     * for tasks that should only run on a static UI, such as taking screenshots to determine the
     * next step.
     *
     * When a stability listener is first added, the caller can assume the session is in a stable
     * state, and it will be notified otherwise.
     */
    public interface StabilityListener {
        /** Called when the computer control session is considered stable. */
        void onSessionStable();

        /** Called when the computer control session is considered unstable. */
        default void onSessionUnstable(@UnstableReason int reason) {}
    }

    /**
     * Callback to be notified about the computer control session lifecycle changes.
     *
     * <p>When the callback is first added, implementers of the callback should not make any
     * assumptions about the starting state of the session. The callback will be notified of the
     * starting state after being added.
     *
     * @see #setLifecycleCallback(Executor, LifecycleCallback)
     */
    public interface LifecycleCallback {

        /**
         * Called when the computer control session enters the active state.
         *
         * <p>When the session is active, the following will apply:
         *
         * <ul>
         *   <li>Interactions with the session (e.g. {@link #tap(int, int)} are allowed.
         *   <li>Taking screenshots of the content using {@link #getScreenshot()} is allowed.
         *   <li>Getting Accessibility windows for the session using
         *     {@link #getAccessibilityWindows()} is allowed.
         * </ul>
         */
        void onActive();

        /**
         * Called when the computer control session enters the blocked state.
         *
         * <p>When the session is blocked, the application will generally not be able to interact
         * with or access the content of the session:
         *
         * <ul>
         *   <li>Interactions with the session (e.g. {@link #tap(int, int)} are NOT allowed.
         *   <li>Taking screenshots of the content using {@link #getScreenshot()} is NOT allowed.
         *   <li>Getting Accessibility windows for the session using
         *     {@link #getAccessibilityWindows()} is NOT allowed.
         * </ul>
         *
         * <p>However, users can still interact with the contents of the session using the
         * interactive mirror. The application may choose to guide users to take over the session
         * using either the {@link #handOverApplications()} API or the interactive mirror.
         *
         * @param reason the reason that the session initially entered the blocked
         *               state.
         * @param blockingPackage the package name of the application that blocked the session,
         *                        or null if the blocking package is not known.
         */
        void onBlocked(@SessionBlockReason int reason, @Nullable String blockingPackage);

        /**
         * Called when the computer control session is closed. This marks the end of the session's
         * lifecycle, and no further lifecycle updates will take place.
         */
        void onClosed(@SessionCloseReason int reason);
    }
}
