package com.android.server.policy.keyguard;

import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.INTERACTIVE_STATE;
import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.OCCLUDED;
import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.SCREEN_STATE;
import static android.internal.perfetto.protos.Windowmanagerservice.KeyguardServiceDelegateProto.SHOWING;

import static com.android.internal.policy.KeyguardState.INTERACTIVE_STATE_AWAKE;
import static com.android.internal.policy.KeyguardState.INTERACTIVE_STATE_GOING_TO_SLEEP;
import static com.android.internal.policy.KeyguardState.INTERACTIVE_STATE_SLEEP;
import static com.android.internal.policy.KeyguardState.INTERACTIVE_STATE_WAKING;
import static com.android.internal.policy.KeyguardState.SCREEN_STATE_OFF;
import static com.android.internal.policy.KeyguardState.SCREEN_STATE_ON;
import static com.android.internal.policy.KeyguardState.SCREEN_STATE_TURNING_OFF;
import static com.android.internal.policy.KeyguardState.SCREEN_STATE_TURNING_ON;
import static com.android.server.flags.Flags.resetKeyguardFirstStateDispatchOnServiceConnected;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UptimeMillisLong;
import android.annotation.UserIdInt;
import android.app.ActivityManagerInternal;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.dreams.DreamManagerInternal;
import android.util.Log;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardService;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.policy.KeyguardState;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.LocalServices;
import com.android.server.wm.EventLogTags;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * A local class that keeps a cache of keyguard state that can be restored in the event
 * keyguard crashes.
 */
public class KeyguardServiceDelegate {
    private static final String TAG = "KeyguardServiceDelegate";
    private static final boolean DEBUG = false;

    /**
     * Callback interface for observers in the rest of the system server to be notified when
     * either the {@code showing} or {@code trusted} properties reported by the KeyguardService
     * have changed.
     */
    public interface StateCallback {
        /** Indicates that the value of {@link #isTrusted()} has changed recently. */
        void onTrustedChanged();

        /** Indicates that the value of {@link #isShowing()} has changed recently. */
        void onShowingChanged();

        /** Indicates that the KeyguardService has drawn after the screen turned on. */
        void onDrawn();

        /** Indicates that the KeyguardService has connected successfully. */
        void onKeyguardServiceConnected();
    }

    /**
     * Binder connection to the KeyguardService.
     *
     * <p>This is null on devices that do not have a KeyguardService, or during periods where
     * the connection is not established yet (eg. lost due to a crash).
     */
    @Nullable
    private IKeyguardService mKeyguardService;

    @NonNull
    private final LockPatternUtils mLockPatternUtils;

    @NonNull
    private final ActivityManagerInternal mActivityManagerInternal;

    /** Last system-determined state sent to KeyguardService. */
    @NonNull
    private final KeyguardState mKeyguardState = new KeyguardState();
    /** Last keyguard-determined state reported back from KeyguardService. */
    @NonNull
    private final KeyguardReportedState mKeyguardReportedState = new KeyguardReportedState();

    /** Event listener for PhoneWindowManager */
    @NonNull
    private final StateCallback mCallback;

    /**
     * Whether the Keyguard timeout was reached but not handled yet. This is an inactivity
     * timeout while the device is unlocked, after which Keyguard should be shown.
     *
     * @see #doKeyguardTimeout
     */
    private boolean doKeyguardTimeoutRequested;

    /**
     * The options for the Keyguard timeout.
     *
     * @see #doKeyguardTimeout
     */
    @Nullable
    private Bundle doKeyguardTimeoutRequestedOptions;

    private final DreamManagerInternal.DreamManagerStateListener mDreamManagerStateListener =
            new DreamManagerInternal.DreamManagerStateListener() {
                @Override
                public void onDreamingStarted() {
                    KeyguardServiceDelegate.this.onDreamingStarted();
                }

                @Override
                public void onDreamingStopped() {
                    KeyguardServiceDelegate.this.onDreamingStopped();
                }
            };

    /** Callbacks for KeyguardService to report state changes. */
    private final IKeyguardStateCallback mKeyguardStateCallback =
            new IKeyguardStateCallback.Stub() {
                @Override
                public void onShowingStateChanged(boolean showing, @UserIdInt int userId) {
                    if (userId != mKeyguardState.userId) return;
                    KeyguardServiceDelegate.this.onShowingStateChanged(showing);
                }

                @Override
                public void onSimSecureStateChanged(boolean simSecure) {
                    mKeyguardReportedState.simSecure = simSecure;
                }

                @Override
                public void onInputRestrictedStateChanged(boolean inputRestricted) {
                    mKeyguardReportedState.inputRestricted = inputRestricted;
                }

                @Override
                public void onTrustedChanged(boolean trusted) {
                    mKeyguardReportedState.trusted = trusted;
                    mCallback.onTrustedChanged();
                }
            };

    private final IKeyguardDrawnCallback mKeyguardDrawnCallback =
            new IKeyguardDrawnCallback.Stub() {
                @Override
                public void onDrawn() {
                    if (DEBUG) Log.v(TAG, "**** SHOWN CALLED ****");
                    KeyguardServiceDelegate.this.mCallback.onDrawn();
                }
            };

    /**
     * Data that has a source of truth in the KeyguardService and is reported over Binder via
     * {@link IKeyguardStateCallback} for the system server's decision-making.
     */
    private static final class KeyguardReportedState {
        /** Whether Keyguard is currently showing. */
        volatile boolean showing;
        /** Whether input is currently restricted by Keyguard. */
        volatile boolean inputRestricted;
        /** Whether the device has Keyguard. */
        volatile boolean deviceHasKeyguard;
        /** Whether the SIM card is currently secure, requiring an unlock. */
        volatile boolean simSecure;
        /** Whether the device is currently trusted. */
        volatile boolean trusted;

        KeyguardReportedState() {
            reset();
        }

        void reset() {
            // Assume keyguard is showing and secure until we know for sure. This is here in
            // the event something checks before the service is actually started.
            // KeyguardService itself should default to this state until the real state is known.
            showing = true;
            inputRestricted = true;
            deviceHasKeyguard = true;
            simSecure = true;
            trusted = false;
        }

        void disable() {
            showing = false;
            inputRestricted = false;
            deviceHasKeyguard = false;
            simSecure = false;
            trusted = false;
        }
    }

    public KeyguardServiceDelegate(@NonNull Context context, @NonNull StateCallback callback) {
        mCallback = callback;
        mLockPatternUtils = new LockPatternUtils(context);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        initializeKeyguardState();
    }

    public void onShowingStateChanged(boolean showing) {
        mKeyguardReportedState.showing = showing;
        mCallback.onShowingChanged();
    }

    /**
     * Creates a binding to the {@link IKeyguardService}.
     *
     * @param context the context to bind on.
     * @param handler the handler to run the ServiceConnection callbacks on.
     */
    public void bindService(@NonNull Context context, @NonNull Handler handler) {
        Intent intent = new Intent();
        final Resources resources = context.getApplicationContext().getResources();

        final ComponentName keyguardComponent = ComponentName.unflattenFromString(
                resources.getString(com.android.internal.R.string.config_keyguardComponent));
        intent.addFlags(Intent.FLAG_DEBUG_TRIAGED_MISSING);
        intent.setComponent(keyguardComponent);

        if (!context.bindServiceAsUser(intent, mKeyguardConnection, Context.BIND_AUTO_CREATE,
                handler, UserHandle.SYSTEM)) {
            Log.v(TAG, "*** Keyguard: can't bind to " + keyguardComponent);
            mKeyguardReportedState.disable();
        } else {
            if (DEBUG) Log.v(TAG, "*** Keyguard started");
        }

        final DreamManagerInternal dreamManager =
                LocalServices.getService(DreamManagerInternal.class);
        if (dreamManager != null) {
            dreamManager.registerDreamManagerStateListener(mDreamManagerStateListener);
        }
    }

    private final ServiceConnection mKeyguardConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.v(TAG, "*** Keyguard connected (yay!)");

            mKeyguardState.userId = mActivityManagerInternal.getCurrentUserId();

            if (resetKeyguardFirstStateDispatchOnServiceConnected()) {
                mCallback.onKeyguardServiceConnected();
            }

            // Replay the previous KeyguardState for the new KeyguardService.
            mKeyguardService = IKeyguardService.Stub.asInterface(service);
            try {
                mKeyguardService.restoreKeyguardState(mKeyguardState, mKeyguardStateCallback,
                        mKeyguardDrawnCallback, doKeyguardTimeoutRequested,
                        doKeyguardTimeoutRequestedOptions);
                if (doKeyguardTimeoutRequested) {
                    if (isSecure(mKeyguardState.userId)) {
                        onShowingStateChanged(true /* showing */);
                    }
                    doKeyguardTimeoutRequested = false;
                    doKeyguardTimeoutRequestedOptions = null;
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.v(TAG, "*** Keyguard disconnected (boo!)");
            mKeyguardService = null;
            mKeyguardReportedState.reset();
            try {
                ActivityTaskManager.getService().setLockScreenShown(true /* showingKeyguard */,
                        false /* showingAod */);
            } catch (RemoteException e) {
                // Local call.
            }
        }
    };

    private void initializeKeyguardState() {
        mKeyguardState.userId = UserHandle.USER_NULL;
    }

    /**
     * Whether Keyguard is currently showing.
     */
    public boolean isShowing() {
        return mKeyguardReportedState.showing;
    }

    /**
     * Whether the device is currently trusted.
     */
    public boolean isTrusted() {
        return mKeyguardReportedState.trusted;
    }

    /**
     * Whether the device has Keyguard.
     */
    public boolean hasKeyguard() {
        return mKeyguardReportedState.deviceHasKeyguard;
    }

    /**
     * Whether input is currently restricted by Keyguard.
     */
    public boolean isInputRestricted() {
        return mKeyguardReportedState.inputRestricted;
    }

    /**
     * Verifies whether Keyguard is unlocked and notifies the given callback.
     *
     * @param onKeyguardExitResult the callback to be informed about the result.
     */
    public void verifyUnlock(@NonNull final Consumer<Boolean> onKeyguardExitResult) {
        final var keyguardService = mKeyguardService;
        if (keyguardService == null) {
            return;
        }
        try {
            keyguardService.verifyUnlock(new IKeyguardExitCallback.Stub() {
                @Override
                public void onKeyguardExitResult(boolean success) {
                    if (DEBUG) Log.v(TAG, "**** onKeyguardExitResult(" + success + ") CALLED ****");
                    onKeyguardExitResult.accept(success);
                }
            });
        } catch (RemoteException e) {
            Slog.w(TAG, "Remote Exception", e);
        }
    }

    /**
     * Sets whether Keyguard is occluded by another window.
     *
     * @param isOccluded the new occluded state.
     */
    public void setOccluded(boolean isOccluded) {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "setOccluded(" + isOccluded + ")");
            EventLogTags.writeWmSetKeyguardOccluded(
                    isOccluded ? 1 : 0,
                    0 /* animate */,
                    0 /* transit */,
                    "setOccluded");
            try {
                mKeyguardService.setOccluded(isOccluded);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.occluded = isOccluded;
    }

    /**
     * Returns whether Keyguard is occluded by another window.
     */
    public boolean isOccluded() {
        return mKeyguardState.occluded;
    }

    /**
     * Dismisses Keyguard, if it is currently shown.
     *
     * @param callback the callback to be informed about the result.
     * @param message  the message that should be displayed in Keyguard.
     */
    public void dismiss(@Nullable IKeyguardDismissCallback callback,
            @Nullable CharSequence message) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.dismiss(callback, message);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    /**
     * Returns whether Keyguard is currently secure for the given user, requiring an unlock.
     *
     * @param userId the ID of the user to check.
     *
     * @see android.app.KeyguardManager#isKeyguardSecure
     */
    public boolean isSecure(@UserIdInt int userId) {
        if (mKeyguardReportedState.simSecure) {
            return true;
        }
        if (userId == UserHandle.USER_NULL) {
            return true;
        }
        return mLockPatternUtils.isSecure(userId);
    }

    /**
     * Called when dreaming has started.
     */
    public void onDreamingStarted() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onDreamingStarted();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.dreaming = true;
    }

    /**
     * Called when dreaming has stopped.
     */
    public void onDreamingStopped() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onDreamingStopped();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.dreaming = false;
    }

    /**
     * Called when the device has started waking up.
     *
     * @param pmWakeReason                      the reason the device started waking up.
     * @param powerButtonLaunchGestureTriggered whether the device is waking up due to a power
     *                                          button double tap gesture.
     */
    public void onStartedWakingUp(
            @PowerManager.WakeReason int pmWakeReason, boolean powerButtonLaunchGestureTriggered) {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onStartedWakingUp()");
            try {
                mKeyguardService.onStartedWakingUp(pmWakeReason, powerButtonLaunchGestureTriggered);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_WAKING;
    }

    /**
     * Called when the device has finished waking up.
     */
    public void onFinishedWakingUp() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onFinishedWakingUp()");
            try {
                mKeyguardService.onFinishedWakingUp();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_AWAKE;
    }

    /**
     * Called when the device screen has started turning off.
     */
    public void onScreenTurningOff() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurningOff()");
            try {
                mKeyguardService.onScreenTurningOff();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.screenState = SCREEN_STATE_TURNING_OFF;
    }

    /**
     * Called when the device screen has finished turning off.
     */
    public void onScreenTurnedOff() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurnedOff()");
            try {
                mKeyguardService.onScreenTurnedOff();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.screenState = SCREEN_STATE_OFF;
    }

    /**
     * Called when the device screen has started turning on.
     *
     * @param reason the reason for the screen turning on.
     */
    public void onScreenTurningOn(int reason) {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurnedOn(reason = " + reason + ")");
            try {
                mKeyguardService.onScreenTurningOn(reason, mKeyguardDrawnCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        } else {
            // try again when we establish a connection
            Slog.w(TAG, "onScreenTurningOn(): no keyguard service!");
        }
        mKeyguardState.screenState = SCREEN_STATE_TURNING_ON;
    }

    /**
     * Called when the device screen has finished turning on.
     */
    public void onScreenTurnedOn() {
        if (mKeyguardService != null) {
            if (DEBUG) Log.v(TAG, "onScreenTurnedOn()");
            try {
                mKeyguardService.onScreenTurnedOn();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.screenState = SCREEN_STATE_ON;
    }

    /**
     * Called when the device has started going to sleep.
     *
     * @param pmSleepReason the reason the device started going to sleep.
     */
    public void onStartedGoingToSleep(@PowerManager.GoToSleepReason int pmSleepReason) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onStartedGoingToSleep(pmSleepReason);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_GOING_TO_SLEEP;
    }

    /**
     * Called when the device has finished going to sleep.
     *
     * @param pmSleepReason                     the reason the device went to sleep.
     * @param powerButtonLaunchGestureTriggered whether the power button double tap gesture was
     *                                          triggered between {@link #onStartedGoingToSleep} and
     *                                          this method; if it's been triggered, we shouldn't
     *                                          lock the device.
     */
    public void onFinishedGoingToSleep(
            @PowerManager.GoToSleepReason int pmSleepReason,
            boolean powerButtonLaunchGestureTriggered) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onFinishedGoingToSleep(pmSleepReason,
                        powerButtonLaunchGestureTriggered);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.interactiveState = INTERACTIVE_STATE_SLEEP;
    }

    /**
     * Sets whether Keyguard is enabled. While disabled it is prevented from showing.
     *
     * <p>If disabled while it is currently showing, it will be hidden. If disabling lead to a hide,
     * re-enabling will show it again.
     *
     * <p>This has no effect if it {@link #isSecure}.
     *
     * @param enabled the new enabled state.
     */
    public void setKeyguardEnabled(boolean enabled) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.setKeyguardEnabled(enabled);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.enabled = enabled;
    }

    /**
     * Called when the system is mostly done booting.
     */
    public void onSystemReady() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onSystemReady();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        } else {
            mKeyguardState.systemReady = true;
        }
    }

    /**
     * Handle the inactivity timeout while the device is unlocked, after which Keyguard should be
     * shown.
     *
     * @param options the Keyguard timeout options.
     */
    public void doKeyguardTimeout(@Nullable Bundle options) {
        if (mKeyguardService != null) {
            if (isSecure(mKeyguardState.userId)) {
                // Preemptively inform the cache that the keyguard will soon be showing, as calls to
                // doKeyguardTimeout are a signal to lock the device as soon as possible.
                onShowingStateChanged(true);
            }
            try {
                mKeyguardService.doKeyguardTimeout(options);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        } else {
            doKeyguardTimeoutRequested = true;
            if (options != null) {
                doKeyguardTimeoutRequestedOptions = options;
            }
        }
    }

    /**
     * Requests to show Keyguard immediately without locking the device. It will show regardless
     * whether a screen lock was configured or not (including if screen lock is SWIPE or NONE).
     */
    public void showDismissibleKeyguard() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.showDismissibleKeyguard();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    /**
     * Sets the current user (or user profile) to the given one.
     *
     * @param userId the ID of the new user.
     */
    public void setCurrentUser(@UserIdInt int userId) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.setCurrentUser(userId);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.userId = userId;
    }

    /**
     * Sets whether a user switch is in progress.
     *
     * @param switching the new switching state.
     */
    public void setSwitchingUser(boolean switching) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.setSwitchingUser(switching);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    /**
     * Notifies that the activity behind has now been drawn and it's safe to remove the wallpaper
     * and Keyguard flag.
     *
     * @param startTime the start time of the animation in uptime milliseconds
     */
    public void startKeyguardExitAnimation(@UptimeMillisLong long startTime) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.startKeyguardExitAnimation(startTime, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    /**
     * Called when the system is fully done booting.
     */
    public void onBootCompleted() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onBootCompleted();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
        mKeyguardState.bootCompleted = true;
    }

    /**
     * Notifies Keyguard that the power key was pressed while locked and launched Home rather than
     * putting the device to sleep or waking up. Note that it's called only if the device is
     * interactive.
     */
    public void onShortPowerPressedGoHome() {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onShortPowerPressedGoHome();
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    /**
     * Notifies Keyguard that it needs to bring up a bouncer and then launch the intent as soon as
     * user unlocks the watch.
     *
     * @param intent the Intent to launch.
     */
    public void dismissKeyguardToLaunch(@NonNull Intent intent) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.dismissKeyguardToLaunch(intent);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    /**
     * Notifies Keyguard that a key was pressed while locked so Keyguard can handle it. Note that
     * it's called only if the device is interactive.
     *
     * @param keycode the key that was pressed.
     */
    public void onSystemKeyPressed(int keycode) {
        if (mKeyguardService != null) {
            try {
                mKeyguardService.onSystemKeyPressed(keycode);
            } catch (RemoteException e) {
                Slog.w(TAG, "Remote Exception", e);
            }
        }
    }

    public void dumpDebug(ProtoOutputStream proto, long fieldId) {
        final long token = proto.start(fieldId);
        proto.write(SHOWING, mKeyguardReportedState.showing);
        proto.write(OCCLUDED, mKeyguardState.occluded);
        proto.write(SCREEN_STATE, mKeyguardState.screenState);
        proto.write(INTERACTIVE_STATE, mKeyguardState.interactiveState);
        proto.end(token);
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + TAG);
        prefix += "  ";
        pw.println(prefix + "showing=" + mKeyguardReportedState.showing);
        pw.println(prefix + "inputRestricted=" + mKeyguardReportedState.inputRestricted);
        pw.println(prefix + "occluded=" + mKeyguardState.occluded);
        pw.println(prefix + "trusted=" + mKeyguardReportedState.trusted);
        pw.println(prefix + "simSecure=" + mKeyguardReportedState.simSecure);
        pw.println(prefix + "dreaming=" + mKeyguardState.dreaming);
        pw.println(prefix + "systemReady=" + mKeyguardState.systemReady);
        pw.println(prefix + "deviceHasKeyguard=" + mKeyguardReportedState.deviceHasKeyguard);
        pw.println(prefix + "enabled=" + mKeyguardState.enabled);
        pw.println(prefix + "userId=" + mKeyguardState.userId);
        pw.println(prefix + "bootCompleted=" + mKeyguardState.bootCompleted);
        pw.println(prefix + "screenState=" + screenStateToString(mKeyguardState.screenState));
        pw.println(prefix + "interactiveState=" +
                interactiveStateToString(mKeyguardState.interactiveState));
    }

    /**
     * Returns the string representation of the given screen state.
     *
     * @param screenState the screen state to convert to a string.
     */
    @NonNull
    private static String screenStateToString(int screenState) {
        return switch (screenState) {
            case SCREEN_STATE_OFF -> "SCREEN_STATE_OFF";
            case SCREEN_STATE_TURNING_ON -> "SCREEN_STATE_TURNING_ON";
            case SCREEN_STATE_ON -> "SCREEN_STATE_ON";
            case SCREEN_STATE_TURNING_OFF -> "SCREEN_STATE_TURNING_OFF";
            default -> Integer.toString(screenState);
        };
    }

    /**
     * Returns the string representation of the given interactive state.
     *
     * @param interactiveState the interactive state to convert to a string.
     */
    @NonNull
    private static String interactiveStateToString(int interactiveState) {
        return switch (interactiveState) {
            case INTERACTIVE_STATE_SLEEP -> "INTERACTIVE_STATE_SLEEP";
            case INTERACTIVE_STATE_WAKING -> "INTERACTIVE_STATE_WAKING";
            case INTERACTIVE_STATE_AWAKE -> "INTERACTIVE_STATE_AWAKE";
            case INTERACTIVE_STATE_GOING_TO_SLEEP -> "INTERACTIVE_STATE_GOING_TO_SLEEP";
            default -> Integer.toString(interactiveState);
        };
    }
}
