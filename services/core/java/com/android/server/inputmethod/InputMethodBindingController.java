/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.inputmethod;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED;
import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.inputmethodservice.InputMethodService.BACK_DISPOSITION_DEFAULT;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.BACK_DISPOSITION;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.CUR_ID;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.CUR_METHOD_ID;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.CUR_SEQ;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.CUR_TOKEN;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.CUR_TOKEN_DISPLAY_ID;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.HAVE_CONNECTION;
import static android.internal.perfetto.protos.Inputmethodmanagerservice.InputMethodManagerServiceProto.IME_WINDOW_VISIBILITY;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.view.Display.INVALID_DISPLAY;

import android.annotation.DurationMillisLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UptimeMillisLong;
import android.annotation.UserIdInt;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManagerInternal;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.InputMethodService.BackDispositionMode;
import android.inputmethodservice.InputMethodService.ImeWindowVisibility;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.EventLog;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;
import android.view.Display;
import android.view.WindowManager;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.inputmethod.IInputMethod;
import com.android.internal.inputmethod.InlineSuggestionsRequestCallback;
import com.android.internal.inputmethod.InlineSuggestionsRequestInfo;
import com.android.internal.inputmethod.InputBindResult;
import com.android.internal.inputmethod.UnbindReason;
import com.android.server.EventLogTags;
import com.android.server.wm.WindowManagerInternal;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A controller managing the state of the input method binding.
 */
final class InputMethodBindingController {

    private static final boolean DEBUG = false;

    private static final String TAG = InputMethodBindingController.class.getSimpleName();

    /**
     * The threshold in milliseconds since {@link #mLastBindTime} after which we should try to
     * reconnect (unbind and rebind) the IME.
     */
    @DurationMillisLong
    private static final long TIME_TO_RECONNECT_MS = 3 * 1000;

    /** The ID of the user tied to this binding controller. */
    @UserIdInt
    private final int mUserId;

    @NonNull
    private final InputMethodManagerService mService;

    @NonNull
    private final Context mContext;

    @NonNull
    private final AutofillSuggestionsController mAutofillController;

    @NonNull
    private final PackageManagerInternal mPackageManagerInternal;

    @NonNull
    private final WindowManagerInternal mWindowManagerInternal;

    /**
     * The time when the latest IME binding was created. Used to determine whether we should try to
     * reconnect (unbind and rebind) if we didn't receive the
     * {@link ServiceConnection#onServiceConnected} callback from the IME after
     * {@link #TIME_TO_RECONNECT_MS} milliseconds.
     */
    @GuardedBy("ImfLock.class")
    @UptimeMillisLong
    private long mLastBindTime;

    /**
     * The {@link InputMethodInfo#getId ID} of the bound IME. If no IME is currently bound, or in
     * the process of binding, this is {@code null}.
     *
     * <p>This is temporarily different from {@link #mSelectedImeId} when the selected IME just
     * changed, until that IME is bound.
     *
     * @see #mSelectedImeId
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private String mCurImeId;

    /**
     * The {@link InputMethodInfo#getId ID} of the selected IME. This can be non-{@code null} even
     * when no IME is bound.
     *
     * <p>This must be synchronized with the value of
     * {@link android.provider.Settings.Secure#DEFAULT_INPUT_METHOD}.
     *
     * <p>This is temporarily {@code null} while re-initializing IME settings (e.g. system locale
     * changed).
     *
     * <p>This is temporarily different from {@link #mCurImeId} when the selected IME just changed,
     * until that IME is bound.
     *
     * @see #mCurImeId
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private String mSelectedImeId;

    /**
     * The intent used to bind the selected IME. If no IME is currently bound or in the process of
     * binding, this is {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private Intent mCurImeIntent;

    /**
     * The interface used to make calls on the bound IME. This is set when the
     * {@link ServiceConnection#onServiceConnected} callback is received, therefore it can be
     * {@code null} while {@link #hasMainConnection} is {@code true}. If no IME is currently bound,
     * this is {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private IInputMethodInvoker mCurIme;

    /**
     * The UID of the bound IME. If no IME is currently bound, this is {@link Process#INVALID_UID}.
     */
    @GuardedBy("ImfLock.class")
    private int mCurImeUid = Process.INVALID_UID;

    /**
     * The token used to uniquely identify the bound IME and the WindowToken created for it. This is
     * also used to validate that the IME calling privileged operations is the currently bound IME
     * for the user. If no IME is currently bound or in the process of binding, this is
     * {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private IBinder mCurToken;

    /**
     * The current {@link InputMethodSubtype} of the current IME. If no subtype is currently
     * selected, this is {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private InputMethodSubtype mCurrentSubtype;

    /**
     * The ID of the display where the bound IME should be shown. If no IME is currently bound, or
     * in the process of binding, this is {@link Display#INVALID_DISPLAY}.
     *
     * <p>This is temporarily different from {@link #mSelectedDisplayId} when the selected display
     * just changed, until the IME is bound on that display.
     *
     * @see #mSelectedDisplayId
     */
    @GuardedBy("ImfLock.class")
    private int mCurDisplayId = INVALID_DISPLAY;

    /**
     * Whether this binding controller is active. This is {@code true} by default. The IME cannot be
     * bound while this is {@code false}. At most one binding controller across the profiles of each
     * running user may be active and have an IME bound at any given time.
     */
    @GuardedBy("ImfLock.class")
    private boolean mActive = true;

    /** The binding sequence number, incremented every time there is a new bind. */
    @GuardedBy("ImfLock.class")
    private int mCurSeq;

    /** Whether the bound IME supports Stylus Handwriting. */
    @GuardedBy("ImfLock.class")
    private boolean mSupportsStylusHw;

    /** Whether the bound IME supports connectionless Stylus Handwriting sessions. */
    @GuardedBy("ImfLock.class")
    private boolean mSupportsConnectionlessStylusHw;

    /**
     * The {@link InputMethodInfo#getId ID} of the IME to restore on the next session (i.e.
     * startInput). This tracks the current IME when the system temporarily switches to a different
     * one to enforce the device policy of the editor.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    private String mImeIdToRestoreOnNextSession;

    /**
     * The ID of the selected display where the IME should be shown. This can be
     * non-{@link Display#INVALID_DISPLAY} even when no IME is bound.
     *
     * <p>This is temporarily different from {@link #mCurDisplayId} when the selected display just
     * changed, until the IME is bound on that display.
     *
     * @see #mCurDisplayId
     */
    @GuardedBy("ImfLock.class")
    private int mSelectedDisplayId = INVALID_DISPLAY;

    /** The ID of the device where the IME should be shown. */
    @GuardedBy("ImfLock.class")
    private int mDeviceId = DEVICE_ID_DEFAULT;

    /** The IME window visibility state of the bound IME. */
    @ImeWindowVisibility
    @GuardedBy("ImfLock.class")
    private int mImeWindowVis;

    /** The disposition mode that indicates the expected back button affordance of the bound IME. */
    @BackDispositionMode
    @GuardedBy("ImfLock.class")
    private int mBackDisposition = BACK_DISPOSITION_DEFAULT;

    /**
     * Latch used in testing to wait for the main binding to receive the service connection. This
     * will be unset after it is notified.
     */
    @Nullable
    private CountDownLatch mLatchForTesting;

    /**
     * Binding flags used only while the binding controller is not {@link #mActive}.
     */
    static final long IME_BACKGROUND_BIND_FLAGS =
            Context.BIND_AUTO_CREATE
                | Context.BIND_REDUCTION_FLAGS
                | Context.BIND_ALLOW_FREEZE;

    /** The set of binding flags used for the {@link #mMainConnection} binding. */
    @VisibleForTesting
    static final int IME_CONNECTION_BIND_FLAGS;
    static {
        if (Flags.lowerImeOomImportance()) {
            IME_CONNECTION_BIND_FLAGS = Context.BIND_AUTO_CREATE
                    | Context.BIND_ALMOST_PERCEPTIBLE
                    | Context.BIND_IMPORTANT_BACKGROUND
                    | Context.BIND_SCHEDULE_LIKE_TOP_APP;
        } else {
            IME_CONNECTION_BIND_FLAGS = Context.BIND_AUTO_CREATE
                    | Context.BIND_NOT_VISIBLE
                    | Context.BIND_NOT_FOREGROUND
                    | Context.BIND_IMPORTANT_BACKGROUND
                    | Context.BIND_SCHEDULE_LIKE_TOP_APP;
        }
    }

    private final int mImeConnectionBindFlags;

    /** The set of binding flags used for the {@link #mVisibleConnection} binding. */
    @VisibleForTesting
    static final int IME_VISIBLE_BIND_FLAGS =
            Context.BIND_AUTO_CREATE
                    | Context.BIND_TREAT_LIKE_ACTIVITY
                    | Context.BIND_FOREGROUND_SERVICE
                    | Context.BIND_INCLUDE_CAPABILITIES
                    | Context.BIND_SHOWING_UI;

    private final long mImeBackgroundBindFlags;

    InputMethodBindingController(@UserIdInt int userId,
            @NonNull InputMethodManagerService service, int imeConnectionBindFlags,
            long imeBackgroundBindFlags) {
        mUserId = userId;
        mService = service;
        mContext = mService.mContext;
        mAutofillController = new AutofillSuggestionsController(this);
        mPackageManagerInternal = mService.mPackageManagerInternal;
        mWindowManagerInternal = mService.mWindowManagerInternal;
        mImeConnectionBindFlags = imeConnectionBindFlags;
        mImeBackgroundBindFlags = imeBackgroundBindFlags;
    }

    /**
     * Returns whether the {@link #mMainConnection} binding was created. Returns {@code true} only
     * while the IME is bound or in the process of binding, as it is set before the
     * {@link ServiceConnection#onServiceConnected} callback is received.
     */
    @GuardedBy("ImfLock.class")
    boolean hasMainConnection() {
        return mMainConnection != null;
    }

    /**
     * The {@link InputMethodInfo#getId ID} of the bound IME. If no IME is currently bound or in
     * the process of binding, returns {@code null}.
     *
     * <p>This is temporarily different from {@link #getSelectedImeId} when the selected IME just
     * changed, until that IME is bound.
     *
     * @see #getSelectedImeId
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    String getCurImeId() {
        return mCurImeId;
    }

    /**
     * The {@link InputMethodInfo#getId ID} of the selected IME. This can be non-{@code null} even
     * when no IME is bound.
     *
     * <p>This must be synchronized with the value of
     * {@link android.provider.Settings.Secure#DEFAULT_INPUT_METHOD}.
     *
     * <p>This is temporarily {@code null} while re-initializing IME settings (e.g. system locale
     * changed).
     *
     * <p>This is temporarily different from {@link #getCurImeId} when the selected IME just
     * changed, until that IME is bound.
     *
     * @see #mCurImeId
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    String getSelectedImeId() {
        return mSelectedImeId;
    }

    /**
     * Sets the {@link InputMethodInfo#getId ID} of the selected IME.
     *
     * @param imeId the ID of the IME to set.
     */
    @GuardedBy("ImfLock.class")
    void setSelectedImeId(@Nullable String imeId) {
        mSelectedImeId = imeId;
    }

    /**
     * Returns the token used to uniquely identify the bound IME and the WindowToken created for it.
     * If no IME is currently bound or in the process of binding, returns {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    IBinder getCurToken() {
        return mCurToken;
    }

    /**
     * Returns the current {@link InputMethodSubtype} of the current IME. If no subtype is currently
     * selected, returns {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    InputMethodSubtype getCurrentSubtype() {
        return mCurrentSubtype;
    }

    /**
     * Sets the current {@link InputMethodSubtype} of the current IME.
     *
     * @param subtype the subtype to set.
     */
    @GuardedBy("ImfLock.class")
    void setCurrentSubtype(@Nullable InputMethodSubtype subtype) {
        mCurrentSubtype = subtype;
    }

    /**
     * The ID of the display where the bound IME should be shown. If no IME is currently bound or
     * in the process of binding, returns {@link Display#INVALID_DISPLAY}.
     *
     * <p>This is temporarily different from {@link #getSelectedDisplayId} when the selected display
     * just changed, until the IME is bound on that display.
     *
     * @see #getSelectedDisplayId
     */
    @GuardedBy("ImfLock.class")
    int getCurDisplayId() {
        return mCurDisplayId;
    }

    /**
     * Returns the intent used to bind the selected IME. If no IME is currently bound, or in the
     * process of binding, returns {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    Intent getCurImeIntent() {
        return mCurImeIntent;
    }

    /** Returns the binding sequence number. */
    @GuardedBy("ImfLock.class")
    int getSequenceNumber() {
        return mCurSeq;
    }

    /** Increases the {@link #mCurSeq} by one. Reset to 1 on overflow. */
    @GuardedBy("ImfLock.class")
    void advanceSequenceNumber() {
        mCurSeq += 1;
        if (mCurSeq <= 0) {
            mCurSeq = 1;
        }
    }

    /**
     * Returns the interface used to make calls on the bound IME. This is set when the
     * {@link ServiceConnection#onServiceConnected} callback is received, therefore it can return
     * {@code null} while {@link #hasMainConnection} returns {@code true}. If no IME is currently
     * bound, returns {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    IInputMethodInvoker getCurIme() {
        return mCurIme;
    }

    /**
     * Returns the UID of the bound IME. If no IME is currently bound, returns
     * {@link Process#INVALID_UID}.
     */
    @GuardedBy("ImfLock.class")
    int getCurImeUid() {
        return mCurImeUid;
    }

    /**
     * Returns whether the {@link #mBackgroundConnection} binding was created. This is {@code true}
     * together with {@link #mMainConnection} when binding the IME, and {@code false} when
     * unbinding the IME.
     *
     * <p>This can return {@code true} even if {@link #hasMainConnection} returns {@code false},
     * when the binding controller is not {@link #mActive}.
     */
    @GuardedBy("ImfLock.class")
    boolean hasBackgroundConnection() {
        return mBackgroundConnection != null;
    }

    /**
     * Returns whether the {@link #mVisibleConnection} binding was created. Returns {@code true}
     * only while the IME is visible.
     */
    @GuardedBy("ImfLock.class")
    boolean hasVisibleConnection() {
        return mVisibleConnection != null;
    }

    /** Returns whether the bound IME supports Stylus Handwriting. */
    @GuardedBy("ImfLock.class")
    boolean getSupportsStylusHandwriting() {
        return mSupportsStylusHw;
    }

    /**
     * Sets whether the latest bound IME supports Stylus Handwriting.
     *
     * @param supports whether the latest bound IME supports Stylus Handwriting.
     */
    @GuardedBy("ImfLock.class")
    void setSupportsStylusHandwriting(boolean supports) {
        mSupportsStylusHw = supports;
    }

    /** Returns whether the latest bound IME supports connectionless Stylus Handwriting. */
    @GuardedBy("ImfLock.class")
    boolean getSupportsConnectionlessStylusHandwriting() {
        return mSupportsConnectionlessStylusHw;
    }

    /**
     * Sets whether the latest bound IME supports connectionless Stylus Handwriting.
     *
     * @param supports whether the latest bound IME supports connectionless Stylus Handwriting.
     */
    @GuardedBy("ImfLock.class")
    void setSupportsConnectionlessStylusHandwriting(boolean supports) {
        mSupportsConnectionlessStylusHw = supports;
    }

    /**
     * The background service connection, used to lower the binding flags of the IME process to
     * background adjustment (allows freezing) while the binding controller is not {@link #mActive}.
     */
    @Nullable
    @GuardedBy("ImfLock.class")
    private BackgroundConnection mBackgroundConnection;

    /**
     * The visible service connection, used to increase the binding flags of the IME process to
     * visible adjustment while the IME is visible. This avoids killing the IME process while it is
     * visible in low memory scenarios.
     */
    @Nullable
    @GuardedBy("ImfLock.class")
    private VisibleConnection mVisibleConnection;

    /** The main service connection, used to start the IME process. */
    @Nullable
    @GuardedBy("ImfLock.class")
    private MainConnection mMainConnection;

    private final class BackgroundConnection implements ServiceConnection {
        @Override
        public void onBindingDied(ComponentName name) {
            synchronized (ImfLock.class) {
                if (this != mBackgroundConnection) {
                    Slog.w(TAG, "Ignoring onBindingDied on obsolete BackgroundConnection.");
                    return;
                }
                if (DEBUG) {
                    Slog.v(TAG, "Binding died: " + name + " mCurImeIntent: " + mCurImeIntent);
                }
                unbindIme();
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // This is handled by the mMainConnection.
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Note that mContext.unbindService(this) does not trigger this.  Hence if we are here
            // the disconnection is not intended by IMMS (e.g. triggered because the current IMS
            // crashed), which is irregular but can eventually happen for everyone just by
            // continuing using the device. Thus it is important to make sure that all the internal
            // states are properly refreshed when this method is called back. Running
            //   adb install -r <APK that implements the current IME>
            // would be a good way to trigger such a situation.
            synchronized (ImfLock.class) {
                if (this != mBackgroundConnection) {
                    Slog.w(TAG, "Ignoring onServiceDisconnected on obsolete BackgroundConnection.");
                    return;
                }
                if (DEBUG) {
                    Slog.v(TAG, "Service disconnected: " + name + " mCurImeIntent: "
                            + mCurImeIntent);
                }
                if (mCurIme != null && mCurImeIntent != null
                        && name.equals(mCurImeIntent.getComponent())) {
                    // We consider this to be a new bind attempt, since the system
                    // should now try to restart the service for us.
                    mLastBindTime = SystemClock.uptimeMillis();
                    clearIme();
                    mService.unbindCurrentClientLocked(UnbindReason.DISCONNECT_IME, mUserId);
                }
            }
        }
    }

    private static final class VisibleConnection implements ServiceConnection {
        @Override
        public void onBindingDied(@NonNull ComponentName name) {
            // This is handled by the mMainConnection.
        }

        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
            // This is handled by the mMainConnection.
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            // This is handled by the mMainConnection.
        }
    }

    private final class MainConnection implements ServiceConnection {
        @Override
        public void onBindingDied(@NonNull ComponentName name) {
            if (Flags.warmWorkProfileIme()) {
                // This is now handled in the mBackgroundConnection, as that is the new
                // "guaranteed" connection.
                return;
            }
            synchronized (ImfLock.class) {
                if (this != mMainConnection) {
                    Slog.w(TAG, "Ignoring onBindingDied on obsolete MainConnection.");
                    return;
                }
                if (DEBUG) {
                    Slog.v(TAG, "Binding died: " + name + " mCurImeIntent: " + mCurImeIntent);
                }
                unbindIme();
            }
        }

        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "IMMS.onServiceConnected");
            try {
                synchronized (ImfLock.class) {
                    if (this != mMainConnection) {
                        Slog.w(TAG, "Ignoring onServiceConnected on obsolete MainConnection.");
                        return;
                    }
                    if (Flags.warmWorkProfileIme() && mCurIme != null) {
                        // IME was bound, binding became inactive and now active again without
                        // unbinding so the IME can be re-used.
                        return;
                    }
                    // If IME is unbound before this callback, the intent becomes null.
                    if (mCurImeIntent != null && name.equals(mCurImeIntent.getComponent())) {
                        mCurIme = IInputMethodInvoker.create(
                                IInputMethod.Stub.asInterface(service));
                        mCurImeUid = getPackageUid(name.getPackageName());
                        if (DEBUG) {
                            Slog.v(TAG, "Initializing IME with token: " + mCurToken
                                    + " on display: " + mCurDisplayId);
                        }
                        mService.initializeImeLocked(mCurIme, mCurToken, mUserId);
                        mService.onImeConnected(mCurImeId, mCurImeUid, mUserId);
                        mAutofillController.performOnCreateInlineSuggestionsRequest();
                    }
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
                if (mLatchForTesting != null) {
                    mLatchForTesting.countDown(); // Notify the finish to tests
                    mLatchForTesting = null;
                }
            }
        }

        /**
         * Returns the UID of the package with the given name, or {@link Process#INVALID_UID} if
         * this fails.
         *
         * @param packageName the name of the package to get the UID of.
         */
        @GuardedBy("ImfLock.class")
        private int getPackageUid(@NonNull String packageName) {
            final int uid = mPackageManagerInternal.getPackageUid(packageName, 0 /* flags */,
                    mUserId);
            if (uid < 0) {
                Slog.e(TAG, "Failed to get UID for package: " + packageName);
                return Process.INVALID_UID;
            } else {
                return uid;
            }
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            if (Flags.warmWorkProfileIme()) {
                // This is now handled in the mBackgroundConnection, as that is the new
                // "guaranteed" connection.
                return;
            }
            // Note that mContext.unbindService(this) does not trigger this.  Hence if we are
            // here the
            // disconnection is not intended by IMMS (e.g. triggered because the current IMS
            // crashed),
            // which is irregular but can eventually happen for everyone just by continuing
            // using the
            // device.  Thus it is important to make sure that all the internal states are
            // properly
            // refreshed when this method is called back.  Running
            //    adb install -r <APK that implements the current IME>
            // would be a good way to trigger such a situation.
            synchronized (ImfLock.class) {
                if (this != mMainConnection) {
                    Slog.w(TAG, "Ignoring onServiceDisconnected on obsolete MainConnection.");
                    return;
                }
                if (DEBUG) {
                    Slog.v(TAG, "Service disconnected: " + name + " mCurImeIntent: "
                            + mCurImeIntent);
                }
                if (mCurIme != null && mCurImeIntent != null
                        && name.equals(mCurImeIntent.getComponent())) {
                    // We consider this to be a new bind attempt, since the system
                    // should now try to restart the service for us.
                    mLastBindTime = SystemClock.uptimeMillis();
                    clearIme();
                    mService.unbindCurrentClientLocked(UnbindReason.DISCONNECT_IME, mUserId);
                }
            }
        }
    }

    @GuardedBy("ImfLock.class")
    void onCreateInlineSuggestionsRequest(@NonNull InlineSuggestionsRequestInfo requestInfo,
            @NonNull InlineSuggestionsRequestCallback callback, boolean touchExplorationEnabled) {
        mAutofillController.onCreateInlineSuggestionsRequest(requestInfo, callback,
                touchExplorationEnabled);
    }

    /**
     * Returns the host input token of the IME that is currently associated with the autofill
     * controller.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    IBinder getCurHostInputToken() {
        return mAutofillController.getCurHostInputToken();
    }

    /**
     * Unbinds the IME by removing the {@link #mVisibleConnection}, {@link #mMainConnection} and
     * {@link #mBackgroundConnection} bindings, allowing the service to be destroyed and the process
     * to be killed. Also removes the WindowToken of the IME, resets the binding state, and finishes
     * and clears the IME sessions from the user's IME client.
     */
    @GuardedBy("ImfLock.class")
    void unbindIme() {
        unbindVisibleConnection();
        unbindMainConnection();
        unbindBackgroundConnection();

        if (mCurToken == null) {
            // No IME is currently bound, or in the process of binding.
            return;
        }

        if (DEBUG) {
            Slog.v(TAG, "Removing window token: " + mCurToken + " on display: "
                    + mCurDisplayId);
        }

        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "InputMethodBindingController.unbindIme");
        try {
            mWindowManagerInternal.removeWindowToken(mCurToken, true /* removeWindows */,
                    false /* animateExit */, mCurDisplayId);
            // ImeWindowToken#removeImmediately will call setImeWindowToken if it's the current one.

            mCurImeIntent = null;
            mCurImeId = null;
            mCurToken = null;
            mCurDisplayId = INVALID_DISPLAY;

            clearIme();
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Notifies the system that the IME has disconnected, and then clears the state of the bound
     * IME.
     */
    @GuardedBy("ImfLock.class")
    private void clearIme() {
        if (mActive) {
            // If inactive, the IME was already disconnected.
            mService.onImeDisconnected(mUserId);
            mAutofillController.invalidateAutofillSession();
        }
        mCurIme = null;
        mCurImeUid = Process.INVALID_UID;
    }

    /**
     * Binds the selected IME by creating the {@link #mMainConnection} binding, allowing the process
     * to be started and the service to be created. If successful, also creates a WindowToken for
     * the IME.
     *
     * @return the binding result.
     */
    @GuardedBy("ImfLock.class")
    @NonNull
    InputBindResult bindIme() {
        if (Flags.warmWorkProfileIme() && !mActive) {
            Slog.e(TAG, "Cannot bind IME on inactive binding controller");
            return InputBindResult.NO_IME;
        }
        if (mSelectedImeId == null) {
            Slog.e(TAG, "Binding IME failed, mSelectedImeId is null");
            return InputBindResult.NO_IME;
        }

        final InputMethodInfo selectedImi = InputMethodSettingsRepository.get(mUserId)
                .getMethodMap().get(mSelectedImeId);
        if (selectedImi == null) {
            throw new IllegalArgumentException("Binding IME with unknown mSelectedImeId: "
                    + mSelectedImeId);
        }

        if (mCurToken != null) {
            Slog.e(TAG, "Binding IME failed, mCurToken is already set. mCurImeId:" + mCurImeId
                    + " mCurToken:" + mCurToken + " mCurDisplayId:" + mCurDisplayId);
            return InputBindResult.IME_NOT_CONNECTED;
        }

        if (mMainConnection != null) {
            // This should not happen, but just in case.
            Slog.wtf(TAG, "Main connection already exists when trying to bind to a new IME");
            return InputBindResult.IME_NOT_CONNECTED;
        }

        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "InputMethodBindingController.bindIme");
        try {
            mCurImeIntent = createIntent(selectedImi.getComponent());
            if (!bindMainConnection()) {
                Slog.w(TAG, "Binding IME failed for: " + mCurImeIntent);
                mCurImeIntent = null;
                return InputBindResult.IME_NOT_CONNECTED;
            }

            if (Flags.warmWorkProfileIme() && mBackgroundConnection == null) {
                // Always bind the background connection together with the main connection.
                // TODO(b/456469810): combine the three bindings into one.
                final BackgroundConnection conn = new BackgroundConnection();
                if (bindConnection(conn, mImeBackgroundBindFlags)) {
                    mBackgroundConnection = conn;
                }
            }

            mLastBindTime = SystemClock.uptimeMillis();

            // Selected state becomes current (bound or in process of binding).
            mCurImeId = selectedImi.getId();
            mCurToken = new Binder();
            mCurDisplayId = mSelectedDisplayId;
            if (DEBUG) {
                Slog.v(TAG, "Adding window token: " + mCurToken + " on display: " + mCurDisplayId);
            }
            if (Flags.warmWorkProfileIme()) {
                mWindowManagerInternal.addImeWindowToken(mCurToken, mCurDisplayId, mUserId,
                        null /* options */);
                mWindowManagerInternal.setImeWindowToken(mCurToken, mCurDisplayId);
            } else {
                mWindowManagerInternal.addWindowToken(mCurToken,
                        WindowManager.LayoutParams.TYPE_INPUT_METHOD, mCurDisplayId,
                        null /* options */);
            }
            return new InputBindResult(InputBindResult.ResultCode.SUCCESS_WAITING_IME_BINDING,
                    null /* method */, null /* accessibilitySessions */, null /* channel */,
                    mCurImeId, mCurSeq, false /* isInputMethodSuppressingSpellChecker */);
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Creates an intent to bind to an IME with the given component name.
     *
     * @param component the component name of the IME to create the binding intent for.
     */
    @NonNull
    private Intent createIntent(@NonNull ComponentName component) {
        final Intent intent = new Intent(InputMethod.SERVICE_INTERFACE);
        intent.setComponent(component);
        intent.putExtra(Intent.EXTRA_CLIENT_LABEL,
                com.android.internal.R.string.input_method_binding_label);
        final var options = ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                        MODE_BACKGROUND_ACTIVITY_START_DENIED);
        intent.putExtra(Intent.EXTRA_CLIENT_INTENT, PendingIntent.getActivity(
                mContext, 0, new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE, options.toBundle()));
        return intent;
    }

    /** Removes the {@link #mBackgroundConnection} binding. */
    @GuardedBy("ImfLock.class")
    private void unbindBackgroundConnection() {
        if (mBackgroundConnection == null) {
            return;
        }
        mContext.unbindService(mBackgroundConnection);
        mBackgroundConnection = null;
    }

    /** Removes the {@link #mMainConnection} binding. */
    @GuardedBy("ImfLock.class")
    private void unbindMainConnection() {
        if (mMainConnection == null) {
            return;
        }
        mContext.unbindService(mMainConnection);
        mMainConnection = null;
    }

    /** Removes the {@link #mVisibleConnection} binding. */
    @GuardedBy("ImfLock.class")
    void unbindVisibleConnection() {
        if (mVisibleConnection == null) {
            return;
        }
        mContext.unbindService(mVisibleConnection);
        mVisibleConnection = null;
    }

    /**
     * Creates the binding on the given connection to the IME specified by {@link #mCurImeIntent}.
     *
     * @param conn  the connection to receive the IME binding on.
     * @param flags the binding flags.
     * @return whether the binding was successful.
     */
    @GuardedBy("ImfLock.class")
    private boolean bindConnection(@NonNull ServiceConnection conn, int flags) {
        if (mCurImeIntent == null) {
            Slog.e(TAG, "bindConnection failed, mCurImeIntent is null, conn: " + conn);
            return false;
        }
        final boolean hasBound = mContext.bindServiceAsUser(mCurImeIntent, conn, flags,
                UserHandle.of(mUserId));
        if (!hasBound) {
            Slog.e(TAG, "bindConnection failed, mCurImeIntent: " + mCurImeIntent + ", conn: "
                    + conn);
            // As per javadoc, unbind even if the binding failed.
            mContext.unbindService(conn);
        }
        return hasBound;
    }

    /**
     * Creates the binding on the given connection to the IME specified by {@link #mCurImeIntent}.
     *
     * @param conn  the connection to receive the IME binding on.
     * @param flags the binding flags.
     * @return whether the binding was successful.
     */
    @GuardedBy("ImfLock.class")
    private boolean bindConnection(@NonNull ServiceConnection conn, long flags) {
        if (mCurImeIntent == null) {
            Slog.e(TAG, "bindConnection failed, mCurImeIntent is null, conn: " + conn);
            return false;
        }
        final boolean hasBound = mContext.bindServiceAsUser(mCurImeIntent, conn,
                Context.BindServiceFlags.of(flags), UserHandle.of(mUserId));
        if (!hasBound) {
            Slog.e(TAG, "bindConnection failed, mCurImeIntent: " + mCurImeIntent + ", conn: "
                    + conn);
            // As per javadoc, unbind even if the binding failed.
            mContext.unbindService(conn);
        }
        return hasBound;
    }

    /**
     * Creates the {@link MainConnection} binding to the IME specified by {@link #mCurImeIntent}.
     *
     * @return whether the binding was created successfully.
     */
    @GuardedBy("ImfLock.class")
    private boolean bindMainConnection() {
        final MainConnection conn = new MainConnection();
        if (bindConnection(conn, mImeConnectionBindFlags)) {
            mMainConnection = conn;
            return true;
        }
        return false;
    }

    /**
     * Sets the bound IME as visible, raising its binding priority by creating the
     * {@link #mVisibleConnection} binding, or reconnects the IME if needed.
     *
     * <p>If no IME is currently bound or in the process of binding, this fails.
     *
     * <p>If the IME is not bound, but in the process of binding, this will reconnect the
     * {@link #mMainConnection} binding if at least {@link #TIME_TO_RECONNECT_MS} milliseconds
     * passed since the binding was created. Otherwise, this won't have any effect.
     */
    @GuardedBy("ImfLock.class")
    void setImeVisibleOrReconnect() {
        if (mCurIme != null) {
            // IME is bound, create mVisibleConnection binding.
            if (DEBUG) {
                Slog.d(TAG, "setImeVisibleOrReconnect: mCurToken=" + mCurToken);
            }
            if (mVisibleConnection == null) {
                final VisibleConnection conn = new VisibleConnection();
                if (bindConnection(conn, IME_VISIBLE_BIND_FLAGS)) {
                    mVisibleConnection = conn;
                }
            }
            return;
        }

        if (mCurToken == null) {
            // IME is not bound and not in the process of binding.
            if (DEBUG) {
                Slog.d(TAG, "setImeVisibleOrReconnect failed, no IME is bound");
            }
            return;
        }

        // IME is not bound, but in the process of binding.
        final long bindingDuration = SystemClock.uptimeMillis() - mLastBindTime;
        if (bindingDuration >= TIME_TO_RECONNECT_MS) {
            // It has been too long since the binding was created, reconnect (unbind and rebind) the
            // main connection.
            EventLog.writeEvent(EventLogTags.IMF_FORCE_RECONNECT_IME, mCurImeId, bindingDuration,
                    1 /* showing */);
            Slog.w(TAG, "setImeVisibleOrReconnect will reconnect the main connection"
                    + ", bindingDuration: " + bindingDuration);
            unbindMainConnection();
            bindMainConnection();
        } else {
            // It hasn't been too long since the binding was created.
            if (DEBUG) {
                Slog.d(TAG, "setImeVisibleOrReconnect failed, IME was bound recently but not"
                        + " connected yet");
            }
        }
    }

    /**
     * Sets the binding controller as not {@link #mActive}. This removes the
     * {@link #mMainConnection} and {@link #mVisibleConnection} bindings, and disallows showing and
     * binding the IME, but it keeps the {@link #mBackgroundConnection} binding to enable a warm
     * start if this becomes {@link #mActive}.
     */
    @GuardedBy("ImfLock.class")
    void setInactive() {
        if (!Flags.warmWorkProfileIme()) {
            return;
        }
        if (!mActive) {
            return;
        }
        mActive = false;
        unbindVisibleConnection();
        unbindMainConnection();

        if (mCurToken == null) {
            // No IME is currently bound, or in the process of binding.
            return;
        }

        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "InputMethodBindingController.setInactive");
        try {
            mWindowManagerInternal.setImeWindowToken(null /* token */, mCurDisplayId);

            mService.onImeDisconnected(mUserId);
            mAutofillController.invalidateAutofillSession();
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Sets the binding controller as {@link #mActive}. This re-allows binding and showing the IME.
     * If the IME is still bound, this re-creates the {@link #mMainConnection} binding, enabling a
     * warm start. Otherwise, the binding will be created when next requested by the system.
     */
    @GuardedBy("ImfLock.class")
    void setActive() {
        if (!Flags.warmWorkProfileIme()) {
            return;
        }
        if (mActive) {
            return;
        }
        mActive = true;
        if (mCurIme == null) {
            // IME is not bound.
            return;
        }

        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "InputMethodBindingController.setActive");
        try {
            if (mMainConnection == null) {
                // IME is bound while inactive, re-bind the main connection.
                bindMainConnection();
            }

            mWindowManagerInternal.setImeWindowToken(mCurToken, mCurDisplayId);

            mService.onImeConnected(mCurImeId, mCurImeUid, mUserId);
            mAutofillController.performOnCreateInlineSuggestionsRequest();
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    /**
     * Attempt to re-use the existing connection (if any) to the IME.
     *
     * @return a non-{@code null} result if the connection should be reused. Otherwise {@code null},
     * in which case the caller should reconnect (unbind and rebind).
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    InputBindResult tryReuseConnection() {
        if (mCurToken != null) {
            // IME is bound or in the process of binding.
            if (mCurIme != null) {
                // IME is bound, re-use connection.
                return new InputBindResult(InputBindResult.ResultCode.SUCCESS_WAITING_IME_SESSION,
                        null /* method */, null /* accessibilitySessions */, null /* channel */,
                        mCurImeId, mCurSeq, false /* isInputMethodSuppressingSpellChecker */);
            } else {
                // IME is not bound, but in the process of binding.
                final long bindingDuration = SystemClock.uptimeMillis() - mLastBindTime;
                if (bindingDuration < TIME_TO_RECONNECT_MS) {
                    // It hasn't been too long since the binding was created, return to client
                    // and wait for the IME to bind, to report back the connection.
                    return new InputBindResult(
                            InputBindResult.ResultCode.SUCCESS_WAITING_IME_BINDING,
                            null /* method */, null /* accessibilitySessions */, null /* channel */,
                            mCurImeId, mCurSeq, false /* isInputMethodSuppressingSpellChecker */);
                } else {
                    // It has been too long since the binding was created, fall through to reconnect
                    // (unbind and rebind) the IME.
                    EventLog.writeEvent(EventLogTags.IMF_FORCE_RECONNECT_IME, mCurImeId,
                            bindingDuration, 0 /* showing */);
                }
            }
        }
        return null;
    }

    /**
     * Sets the {@link InputMethodInfo#getId ID} of the IME to restore on the next session (i.e.
     * startInput). This tracks the current IME when the system temporarily switches to a different
     * one to enforce the device policy of the editor.
     *
     * @param imeId the ID of the IME to restore.
     */
    @GuardedBy("ImfLock.class")
    void setImeIdToRestoreOnNextSession(@Nullable String imeId) {
        mImeIdToRestoreOnNextSession = imeId;
    }

    /**
     * Returns the {@link InputMethodInfo#getId ID} of the IME to restore on the next session (i.e.
     * startInput).
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    String getImeIdToRestoreOnNextSession() {
        return mImeIdToRestoreOnNextSession;
    }

    /**
     * Returns the current {@link InputMethodSubtype}.
     *
     * <p>Also this method has had questionable behaviors:</p>
     * <ul>
     *     <li>Calling this method can update {@link #mCurrentSubtype}.</li>
     *     <li>This method may return {@link #mCurrentSubtype} as-is, even if it does not belong to
     *     the current IME.</li>
     * </ul>
     * <p>TODO(b/347083680): Address above issues.</p>
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    InputMethodSubtype getCurrentInputMethodSubtype() {
        if (mSelectedImeId == null) {
            return null;
        }
        final InputMethodSettings settings = InputMethodSettingsRepository.get(mUserId);
        final InputMethodInfo selectedImi = settings.getMethodMap().get(mSelectedImeId);
        if (selectedImi == null || selectedImi.getSubtypeCount() == 0) {
            return null;
        }
        final var subtype = SubtypeUtils.getCurrentInputMethodSubtype(selectedImi, settings,
                mCurrentSubtype);
        mCurrentSubtype = subtype;
        return subtype;
    }

    /**
     * Returns whether this binding controller is active. This is {@code true} by default. The IME
     * cannot be bound while this is {@code false}. At most one binding controller across the
     * profiles of each running user may be active and have an IME bound at any given time.
     */
    @GuardedBy("ImfLock.class")
    boolean isActive() {
        return mActive;
    }

    /**
     * Sets the ID of the selected display where the IME should be shown.
     *
     * @param displayId the display ID to set.
     */
    @GuardedBy("ImfLock.class")
    void setSelectedDisplayId(int displayId) {
        mSelectedDisplayId = displayId;
    }

    /**
     * Returns the ID of the selected display where the IME should be shown. This can be
     * non-{@link Display#INVALID_DISPLAY} even when no IME is bound.
     *
     * <p>This is temporarily different from {@link #getCurDisplayId} when the selected display
     * just changed, until the IME is bound on that display.
     *
     * @see #getCurDisplayId
     */
    @GuardedBy("ImfLock.class")
    int getSelectedDisplayId() {
        return mSelectedDisplayId;
    }

    /**
     * Sets the ID of the device where the IME should be shown.
     *
     * @param deviceId the device ID to set.
     */
    @GuardedBy("ImfLock.class")
    void setDeviceId(int deviceId) {
        mDeviceId = deviceId;
    }

    /** Returns the ID of the device where the IME should be shown. */
    @GuardedBy("ImfLock.class")
    int getDeviceId() {
        return mDeviceId;
    }

    /** Returns the ID of the user tied to this binding controller. */
    @UserIdInt
    int getUserId() {
        return mUserId;
    }

    /**
     * Sets the IME window visibility state of the bound IME.
     *
     * @param vis the state to set.
     */
    @GuardedBy("ImfLock.class")
    void setImeWindowVis(@ImeWindowVisibility int vis) {
        mImeWindowVis = vis;
    }

    /**
     * Returns the IME window visibility state of the bound IME. If no IME is currently bound,
     * returns {@code 0}.
     */
    @ImeWindowVisibility
    @GuardedBy("ImfLock.class")
    int getImeWindowVis() {
        return mImeWindowVis;
    }

    /**
     * Returns the disposition mode that indicates the expected back button affordance of the bound
     * IME. If no IME is currently bound, returns
     * {@link InputMethodService#BACK_DISPOSITION_DEFAULT}.
     */
    @BackDispositionMode
    @GuardedBy("ImfLock.class")
    int getBackDisposition() {
        return mBackDisposition;
    }

    /**
     * Sets the disposition mode that indicates the expected back button affordance of the bound
     * IME.
     *
     * @param backDisposition the back disposition to set.
     */
    @GuardedBy("ImfLock.class")
    void setBackDisposition(@BackDispositionMode int backDisposition) {
        mBackDisposition = backDisposition;
    }

    /**
     * Sets a latch used to wait for the main binding to receive the service connection. It will
     * be unset after it is notified.
     *
     * @param latch the latch to set.
     */
    @VisibleForTesting
    void setLatchForTesting(@NonNull CountDownLatch latch) {
        mLatchForTesting = latch;
    }

    /**
     * Writes the current state to the given print writer.
     *
     * @param pw     the print writer to write the state to.
     * @param prefix the prefix to add for output line.
     */
    @GuardedBy("ImfLock.class")
    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + "mSelectedImeId=" + mSelectedImeId);
        pw.println(prefix + "mCurrentSubtype=" + mCurrentSubtype);
        pw.println(prefix + "mCurSeq=" + mCurSeq);
        pw.println(prefix + "mCurImeId=" + mCurImeId);
        pw.println(prefix + "mBackgroundConnection=" + mBackgroundConnection);
        pw.println(prefix + "mMainConnection=" + mMainConnection);
        pw.println(prefix + "mVisibleConnection=" + mVisibleConnection);
        pw.println(prefix + "mCurToken=" + mCurToken);
        pw.println(prefix + "mCurDisplayId=" + mCurDisplayId);
        pw.println(prefix + "mActive=" + mActive);
        pw.println(prefix + "mCurHostInputToken=" + getCurHostInputToken());
        pw.println(prefix + "mCurImeIntent=" + mCurImeIntent);
        pw.println(prefix + "mCurIme=" + mCurIme);
        pw.println(prefix + "mImeWindowVis=" + mImeWindowVis);
        pw.println(prefix + "mBackDisposition=" + mBackDisposition);
        pw.println(prefix + "mSelectedDisplayId=" + mSelectedDisplayId);
        pw.println(prefix + "mDeviceId=" + mDeviceId);
        pw.println(prefix + "mSupportsStylusHw=" + mSupportsStylusHw);
        pw.println(prefix + "mSupportsConnectionlessStylusHw=" + mSupportsConnectionlessStylusHw);
    }

    /**
     * Writes the current state to the given proto output stream.
     *
     * @param proto the stream to write the state in.
     */
    @GuardedBy("ImfLock.class")
    void dumpDebug(@NonNull ProtoOutputStream proto) {
        proto.write(CUR_METHOD_ID, mSelectedImeId);
        proto.write(CUR_SEQ, mCurSeq);
        proto.write(CUR_ID, mCurImeId);
        proto.write(CUR_TOKEN, Objects.toString(mCurToken));
        proto.write(CUR_TOKEN_DISPLAY_ID, mCurDisplayId);
        proto.write(HAVE_CONNECTION, mMainConnection != null);
        proto.write(BACK_DISPOSITION, mBackDisposition);
        proto.write(IME_WINDOW_VISIBILITY, mImeWindowVis);
    }
}
