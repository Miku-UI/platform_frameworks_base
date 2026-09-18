/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets.Type;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;

import com.android.systemui.Dependency;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.res.R;
import com.android.systemui.util.DialogKt;
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * Class for dialogs that should appear over panels and keyguard.
 *
 * <p>DO NOT SUBCLASS THIS. See {@link DialogDelegate} for an interface that enables
 * customizing behavior via composition instead of inheritance. Clients should implement the
 * Delegate class and then pass their implementation into the SystemUIDialog constructor.
 *
 * <p>Provide a {@link SystemUIDialogManager} to its constructor to send signals to listeners on
 * whether this dialog is showing and to update corresponding sysui state flags.
 *
 * <p>The SystemUIDialog registers a listener for the screen off / close system dialogs broadcast,
 * and dismisses itself when it receives the broadcast.
 */
public class SystemUIDialog extends AlertDialog {
    public static final int DEFAULT_THEME = R.style.Theme_SystemUI_Dialog;
    // TODO(b/203389579): Remove this once the dialog width on large screens has been agreed on.
    private static final String FLAG_TABLET_DIALOG_WIDTH =
            "persist.systemui.flag_tablet_dialog_width";
    public static final boolean DEFAULT_DISMISS_ON_DEVICE_LOCK = true;
    public static final int DIALOG_WINDOW_TYPE = LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;

    private final Context mContext;
    private final DialogTransitionAnimator mDialogTransitionAnimator;
    @Nullable
    private final WindowRootViewBlurInteractor mBlurInteractor;
    private final DialogDelegate<SystemUIDialog> mDelegate;
    @Nullable
    private final DismissReceiver mDismissReceiver;
    private final SystemUIDialogManager mDialogManager;
    /**
     * Whether the dialog background should be refreshed when the theme changes between light and
     * dark mode. Note, when set to `true` the content of the dialog should also handle the
     * configuration change. Composables usually handle this by default, but Views may need a manual
     * update.
     */
    private final boolean mRefreshBackgroundOnThemeChange;

    private final boolean mIsTransient;

    private int mLastWidth = Integer.MIN_VALUE;
    private int mLastHeight = Integer.MIN_VALUE;
    private int mLastConfigurationWidthDp = -1;
    private int mLastConfigurationHeightDp = -1;
    private int mLastNightMode = -1;

    private final List<Runnable> mOnCreateRunnables = new ArrayList<>();

    /**
     * @deprecated Don't subclass SystemUIDialog. Please subclass {@link Delegate} and pass it to
     * {@link Factory#create(Delegate)} to create a custom dialog.
     */
    @Deprecated
    public SystemUIDialog(Context context) {
        this(context, DEFAULT_THEME, DEFAULT_DISMISS_ON_DEVICE_LOCK);
    }

    public SystemUIDialog(Context context, int theme) {
        this(context, theme, DEFAULT_DISMISS_ON_DEVICE_LOCK);
    }

    public SystemUIDialog(Context context, int theme, boolean dismissOnDeviceLock) {
        // TODO(b/219008720): Remove those calls to Dependency.get by introducing a
        // SystemUIDialogFactory and make all other dialogs create a SystemUIDialog to which we set
        // the content and attach listeners.
        //
        // When adding WindowRootViewBlurInteractor to Dependency.java, it causes this exception:
        // Scoped provider was invoked recursively returning different results. Since this is what
        // we want to deprecate and we only need blur for bottom sheet dialogs, we can make
        // blurInteractor null here and disable that functionalitiy when constructed not from
        // Factory.
        this(context, theme, dismissOnDeviceLock,
                Dependency.get(SystemUIDialogManager.class),
                Dependency.get(BroadcastDispatcher.class),
                Dependency.get(DialogTransitionAnimator.class),
                null /* blurInteractor */);
    }

    public static class Factory {
        private final Context mContext;
        private final SystemUIDialogManager mSystemUIDialogManager;
        private final BroadcastDispatcher mBroadcastDispatcher;
        private final DialogTransitionAnimator mDialogTransitionAnimator;
        private final WindowRootViewBlurInteractor mBlurInteractor;

        @Inject
        public Factory(
                @Application Context context,
                SystemUIDialogManager systemUIDialogManager,
                BroadcastDispatcher broadcastDispatcher,
                DialogTransitionAnimator dialogTransitionAnimator,
                WindowRootViewBlurInteractor blurInteractor) {
            mContext = context;
            mSystemUIDialogManager = systemUIDialogManager;
            mBroadcastDispatcher = broadcastDispatcher;
            mDialogTransitionAnimator = dialogTransitionAnimator;
            mBlurInteractor = blurInteractor;
        }

        /**
         * Creates a new instance of {@link SystemUIDialog} with no customized behavior.
         *
         * When you just need a dialog, call this.
         */
        public SystemUIDialog create() {
            return create(new DialogDelegate<>() {
            }, mContext, DEFAULT_THEME, DEFAULT_DISMISS_ON_DEVICE_LOCK,
                    true /* shouldAcsdDismissDialog */);
        }

        /**
         * Creates a new instance of {@link SystemUIDialog} with no customized behavior.
         *
         * When you just need a dialog created with a specific {@link Context}, call this.
         */
        public SystemUIDialog create(Context context) {
            return create(new DialogDelegate<>() {
            }, context, DEFAULT_THEME, DEFAULT_DISMISS_ON_DEVICE_LOCK,
                    true /* shouldAcsdDismissDialog */);
        }

        /**
         * Creates a new instance of {@link SystemUIDialog} with {@code delegate} as the {@link
         * Delegate}.
         *
         * When you need to customize the dialog, pass it a delegate.
         */
        public SystemUIDialog create(Delegate delegate, Context context) {
            return create(delegate, context, DEFAULT_THEME);
        }

        /**
         * Creates a new instance of {@link SystemUIDialog} with {@code delegate} as the {@link
         * Delegate}. When you need to customize the dialog, pass it a delegate.
         *
         * This method allows the caller to specify if the dialog should be dismissed in response
         * to the ACTION_CLOSE_SYSTEM_DIALOGS intent.
         */
        public SystemUIDialog create(Delegate delegate, Context context,
                boolean shouldAcsdDismissDialog) {
            return create(delegate, context, DEFAULT_THEME, DEFAULT_DISMISS_ON_DEVICE_LOCK,
                    shouldAcsdDismissDialog);
        }

        public SystemUIDialog create(Delegate delegate, Context context, @StyleRes int theme) {
            return create((DialogDelegate<SystemUIDialog>) delegate, context, theme,
                    DEFAULT_DISMISS_ON_DEVICE_LOCK, true /* shouldAcsdDismissDialog */);
        }

        public SystemUIDialog create(Delegate delegate) {
            return create(delegate, mContext);
        }

        public SystemUIDialog create(DialogDelegate<SystemUIDialog> dialogDelegate,
                Context context, @StyleRes int theme, boolean dismissOnDeviceLock,
                boolean shouldAcsdDismissDialog) {
            return new SystemUIDialog(
                    context,
                    theme,
                    dismissOnDeviceLock,
                    false /* refreshBackgroundOnThemeChange */,
                    mSystemUIDialogManager,
                    mBroadcastDispatcher,
                    mDialogTransitionAnimator,
                    mBlurInteractor,
                    dialogDelegate,
                    shouldAcsdDismissDialog,
                    false);
        }
    }

    public SystemUIDialog(
            Context context,
            int theme,
            boolean dismissOnDeviceLock,
            SystemUIDialogManager dialogManager,
            BroadcastDispatcher broadcastDispatcher,
            DialogTransitionAnimator dialogTransitionAnimator,
            WindowRootViewBlurInteractor blurInteractor) {
        this(
                context,
                theme,
                dismissOnDeviceLock,
                false /* refreshBackgroundOnThemeChange */,
                dialogManager,
                broadcastDispatcher,
                dialogTransitionAnimator,
                blurInteractor,
                new DialogDelegate<>() {
                },
                /* shouldAcsdDismissDialog= */ true,
                /* isTransient= */ false);
    }

    public SystemUIDialog(
            Context context,
            int theme,
            boolean dismissOnDeviceLock,
            SystemUIDialogManager dialogManager,
            BroadcastDispatcher broadcastDispatcher,
            DialogTransitionAnimator dialogTransitionAnimator,
            WindowRootViewBlurInteractor blurInteractor,
            Delegate delegate) {
        this(
                context,
                theme,
                dismissOnDeviceLock,
                false /* refreshBackgroundOnThemeChange */,
                dialogManager,
                broadcastDispatcher,
                dialogTransitionAnimator,
                blurInteractor,
                delegate,
                /* shouldAcsdDismissDialog= */ true,
                /* isTransient= */ false);
    }

    public SystemUIDialog(
            Context context,
            int theme,
            boolean dismissOnDeviceLock,
            boolean refreshBackgroundOnThemeChange,
            SystemUIDialogManager dialogManager,
            BroadcastDispatcher broadcastDispatcher,
            DialogTransitionAnimator dialogTransitionAnimator,
            WindowRootViewBlurInteractor blurInteractor,
            DialogDelegate<SystemUIDialog> delegate,
            boolean shouldAcsdDismissDialog,
            boolean isTransient) {
        super(context, theme);
        mContext = context;
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mBlurInteractor = blurInteractor;
        mDelegate = delegate;
        mIsTransient = isTransient;

        applyFlags(this);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.setTitle(getClass().getSimpleName());
        getWindow().setAttributes(attrs);
        mLastNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        mDismissReceiver = dismissOnDeviceLock ? new DismissReceiver(this, broadcastDispatcher,
                dialogTransitionAnimator, shouldAcsdDismissDialog) : null;
        mDialogManager = dialogManager;
        mRefreshBackgroundOnThemeChange = refreshBackgroundOnThemeChange;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mDelegate.beforeCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        mDelegate.onCreate(this, savedInstanceState);

        Configuration config = getContext().getResources().getConfiguration();
        mLastConfigurationWidthDp = config.screenWidthDp;
        mLastConfigurationHeightDp = config.screenHeightDp;
        final Window window = getWindow();
        updateWindowSize(window);

        for (int i = 0; i < mOnCreateRunnables.size(); i++) {
            mOnCreateRunnables.get(i).run();
        }
        View decorView = window.getDecorView();
        if (decorView instanceof ViewGroup decorViewGroup) {
            decorViewGroup.addView(
                    new ConfigurationListenerView(getContext(), this::onConfigurationChanged));
        }
        DialogKt.registerAnimationOnBackInvoked(
                /* dialog = */ this,
                /* targetView = */ decorView,
                /* backAnimationSpec= */mDelegate.getBackAnimationSpec(
                        () -> decorView.getResources().getDisplayMetrics())
        );
    }

    /**
     * Updates the size of the given window.
     *
     * @param window the window whose size to update.
     */
    private void updateWindowSize(@NonNull Window window) {
        int width = getWidth();
        int height = getHeight();
        if (width == mLastWidth && height == mLastHeight) {
            return;
        }

        mLastWidth = width;
        mLastHeight = height;
        window.setLayout(width, height);
    }

    /**
     * Called when the current configuration of the resources being used by the dialog has changed.
     *
     * @param configuration The new resource configuration.
     */
    public void onConfigurationChanged(Configuration configuration) {
        final Window window = getWindow();
        if (window == null) {
            return;
        }

        if (mLastConfigurationWidthDp != configuration.screenWidthDp
                || mLastConfigurationHeightDp != configuration.screenHeightDp) {
            mLastConfigurationWidthDp = configuration.screenWidthDp;
            mLastConfigurationHeightDp = configuration.screenHeightDp;

            updateWindowSize(window);
        }
        int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (mLastNightMode != nightMode) {
            mLastNightMode = nightMode;

            if (mRefreshBackgroundOnThemeChange) {
                refreshBackground(window);
            }
        }

        mDelegate.onConfigurationChanged(this, configuration);
    }

    /**
     * Return this dialog width. This method will be invoked when this dialog is created and when
     * the device configuration changes, and the result will be used to resize this dialog window.
     */
    protected int getWidth() {
        return mDelegate.getWidth(this);
    }

    /**
     * Return this dialog height. This method will be invoked when this dialog is created and when
     * the device configuration changes, and the result will be used to resize this dialog window.
     */
    protected int getHeight() {
        return mDelegate.getHeight(this);
    }

    @Override
    protected final void onStart() {
        super.onStart();

        if (mDismissReceiver != null) {
            mDismissReceiver.register();
        }

        if (!mIsTransient) {
            // TODO(b/471161535) Register transient dialogs too
            mDialogManager.setShowing(/* dialog= */ this, /* showing= */true);
        }

        mDelegate.onStart(this);
        start();
    }

    /**
     * Called when {@link #onStart} is called. Subclasses wishing to override {@link #onStart()}
     * should override this method instead.
     */
    protected void start() {
        // IMPORTANT: Please do not add anything here, since subclasses are likely to override this.
        // Instead, add things to onStop above.
    }

    @Override
    protected final void onStop() {
        super.onStop();

        if (mDismissReceiver != null) {
            mDismissReceiver.unregister();
        }

        if (!mIsTransient) {
            // TODO(b/471161535) Register transient dialogs too
            mDialogManager.setShowing(/* dialog= */ this, /* showing= */false);
        }

        mDelegate.onStop(this);
        stop();
    }

    /**
     * Called when {@link #onStop} is called. Subclasses wishing to override {@link #onStop()}
     * should override this method instead.
     */
    protected void stop() {
        // IMPORTANT: Please do not add anything here, since subclasses are likely to override this.
        // Instead, add things to onStop above.
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mDelegate.onWindowFocusChanged(this, hasFocus);
        if (hasFocus) {
            // Update SysUI state to reflect that a dialog is showing. This ensures the state is
            // correct when this dialog regains focus after another dialog was closed. This is
            // otherwise implicitly handled by the `SystemUIDialogManager.setShowing` call.
            // See b/386871258
            if (!mIsTransient) {
                // TODO(b/471161535) Register transient dialogs too
                mDialogManager.setShowing(/* dialog= */ this, /* showing= */true);
            }
        }
    }

    public void setShowForAllUsers(boolean show) {
        setShowForAllUsers(this, show);
    }

    public void setMessage(int resId) {
        setMessage(mContext.getString(resId));
    }

    /**
     * Set a listener to be invoked when the positive button of the dialog is pressed. The dialog
     * will automatically be dismissed when the button is clicked.
     */
    public void setPositiveButton(int resId, OnClickListener onClick) {
        setPositiveButton(resId, onClick, true /* dismissOnClick */);
    }

    /**
     * Set a listener to be invoked when the positive button of the dialog is pressed. The dialog
     * will be dismissed when the button is clicked iff {@code dismissOnClick} is true.
     */
    public void setPositiveButton(int resId, OnClickListener onClick, boolean dismissOnClick) {
        setButton(BUTTON_POSITIVE, resId, onClick, dismissOnClick);
    }

    /**
     * Set a listener to be invoked when the negative button of the dialog is pressed. The dialog
     * will automatically be dismissed when the button is clicked.
     */
    public void setNegativeButton(int resId, OnClickListener onClick) {
        setNegativeButton(resId, onClick, true /* dismissOnClick */);
    }

    /**
     * Set a listener to be invoked when the negative button of the dialog is pressed. The dialog
     * will be dismissed when the button is clicked iff {@code dismissOnClick} is true.
     */
    public void setNegativeButton(int resId, OnClickListener onClick, boolean dismissOnClick) {
        setButton(BUTTON_NEGATIVE, resId, onClick, dismissOnClick);
    }

    /**
     * Set a listener to be invoked when the neutral button of the dialog is pressed. The dialog
     * will automatically be dismissed when the button is clicked.
     */
    public void setNeutralButton(int resId, OnClickListener onClick) {
        setNeutralButton(resId, onClick, true /* dismissOnClick */);
    }

    /**
     * Set a listener to be invoked when the neutral button of the dialog is pressed. The dialog
     * will be dismissed when the button is clicked iff {@code dismissOnClick} is true.
     */
    public void setNeutralButton(int resId, OnClickListener onClick, boolean dismissOnClick) {
        setButton(BUTTON_NEUTRAL, resId, onClick, dismissOnClick);
    }

    private void setButton(int whichButton, int resId, OnClickListener onClick,
            boolean dismissOnClick) {
        if (dismissOnClick) {
            setButton(whichButton, mContext.getString(resId), onClick);
        } else {
            // Set a null OnClickListener to make sure the button is still created and shown.
            setButton(whichButton, mContext.getString(resId), (OnClickListener) null);

            // When the dialog is created, set the click listener but don't dismiss the dialog when
            // it is clicked.
            mOnCreateRunnables.add(() -> getButton(whichButton).setOnClickListener(
                    view -> onClick.onClick(this, whichButton)));
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (mDelegate.dispatchTouchEvent(this, ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent motionEvent) {
        if (mDelegate.onTouchEvent(this, motionEvent)) {
            return true;
        } else {
            return super.onTouchEvent(motionEvent);
        }
    }

    @Override
    public void dismiss() {
        mDelegate.beforeDismiss(this);
        super.dismiss();
    }

    /** Dismisses the dialog without animation. */
    public void dismissWithoutAnimation() {
        mDialogTransitionAnimator.disableAllCurrentDialogsExitAnimations();
        dismiss();
    }

    /**
     * Set a dialog's elevation to 0. This is needed for dialogs that are not
     * {@link SystemUIDialog}, as launching an activity from them will result in a weird flicker if
     * they have an elevation, due to some legacy inner Window Manager behaviors.
     */
    public static void resetElevation(Dialog dialog) {
        dialog.getWindow().setElevation(0);
    }

    @Nullable
    public WindowRootViewBlurInteractor getBlurInteractor() {
        return mBlurInteractor;
    }

    public static void setShowForAllUsers(Dialog dialog, boolean show) {
        if (show) {
            dialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        } else {
            dialog.getWindow().getAttributes().privateFlags &=
                    ~WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        }
    }

    /**
     * Ensure the window type is set properly to show over all other screens
     */
    public static void setWindowOnTop(Dialog dialog, boolean isKeyguardShowing) {
        final Window window = dialog.getWindow();
        window.setType(DIALOG_WINDOW_TYPE);
        if (isKeyguardShowing) {
            window.getAttributes().setFitInsetsTypes(
                    window.getAttributes().getFitInsetsTypes() & ~Type.statusBars());
        }
    }

    public static AlertDialog applyFlags(AlertDialog dialog) {
        return applyFlags(dialog, true);
    }

    public static AlertDialog applyFlags(AlertDialog dialog, boolean showWhenLocked) {
        final Window window = dialog.getWindow();
        window.setType(DIALOG_WINDOW_TYPE);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        if (showWhenLocked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        window.getAttributes().setFitInsetsTypes(
                window.getAttributes().getFitInsetsTypes() & ~Type.statusBars());
        return dialog;
    }

    /**
     * Registers a listener that dismisses the given dialog when it receives
     * the screen off / close system dialogs broadcast.
     * <p>
     * <strong>Note:</strong> Don't call dialog.setOnDismissListener() after
     * calling this because it causes a leak of BroadcastReceiver. Instead, call the version that
     * takes an extra Runnable as a parameter.
     *
     * @param dialog The dialog to be associated with the listener.
     */
    public static void registerDismissListener(Dialog dialog) {
        registerDismissListener(dialog, null);
    }

    /**
     * Registers a listener that dismisses the given dialog when it receives
     * the screen off / close system dialogs broadcast.
     * <p>
     * <strong>Note:</strong> Don't call dialog.setOnDismissListener() after
     * calling this because it causes a leak of BroadcastReceiver.
     *
     * @param dialog        The dialog to be associated with the listener.
     * @param dismissAction An action to run when the dialog is dismissed.
     */
    public static void registerDismissListener(Dialog dialog, @Nullable Runnable dismissAction) {
        // TODO(b/219008720): Remove those calls to Dependency.get.
        DismissReceiver dismissReceiver = new DismissReceiver(dialog,
                Dependency.get(BroadcastDispatcher.class),
                Dependency.get(DialogTransitionAnimator.class),
                true /* shouldAcsdDismissDialog */);
        dialog.setOnDismissListener(d -> {
            dismissReceiver.unregister();
            if (dismissAction != null) dismissAction.run();
        });
        dismissReceiver.register();
    }

    /** Set an appropriate size to {@code dialog} depending on the current configuration. */
    public static void setDialogSize(Dialog dialog) {
        // We need to create the dialog first, otherwise the size will be overridden when it is
        // created.
        dialog.create();
        dialog.getWindow().setLayout(getDefaultDialogWidth(dialog), getDefaultDialogHeight());
    }

    static int getDefaultDialogWidth(Dialog dialog) {
        Context context = dialog.getContext();
        int flagValue = SystemProperties.getInt(FLAG_TABLET_DIALOG_WIDTH, 0);
        if (flagValue == -1) {
            // The width of bottom sheets (624dp).
            return calculateDialogWidthWithInsets(dialog, 624);
        } else if (flagValue == -2) {
            // The suggested small width for all dialogs (348dp)
            return calculateDialogWidthWithInsets(dialog, 348);
        } else if (flagValue > 0) {
            // Any given width.
            return calculateDialogWidthWithInsets(dialog, flagValue);
        } else {
            // By default we use the same width as the notification shade in portrait mode.
            int width = context.getResources().getDimensionPixelSize(R.dimen.large_dialog_width);
            if (width > 0) {
                // If we are neither WRAP_CONTENT or MATCH_PARENT, add the background insets so that
                // the dialog is the desired width.
                width += getHorizontalInsets(dialog);
            }
            return width;
        }
    }

    /**
     * Return the pixel width {@code dialog} should be so that it is {@code widthInDp} wide,
     * taking its background insets into consideration.
     */
    public static int calculateDialogWidthWithInsets(Dialog dialog, int widthInDp) {
        float widthInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthInDp,
                dialog.getContext().getResources().getDisplayMetrics());
        return Math.round(widthInPixels + getHorizontalInsets(dialog));
    }

    private static int getHorizontalInsets(Dialog dialog) {
        final View viewWithBackground = getViewWithBackground(dialog);
        if (viewWithBackground == null) {
            return 0;
        }
        Drawable background = viewWithBackground.getBackground();
        Insets insets = background != null ? background.getOpticalInsets() : Insets.NONE;
        return insets.left + insets.right;
    }

    static int getDefaultDialogHeight() {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    /**
     * Refreshes the background of the dialog, applying the theme change between light and dark
     * mode.
     *
     * @param window the dialog window to refresh the background of.
     */
    private void refreshBackground(@NonNull Window window) {
        final View viewWithBackground = getViewWithBackground(this);
        if (viewWithBackground == null) {
            return;
        }
        // Use the Window's Context as it already has the configuration change applied, the View's
        // Context is updated with a delay.
        final Context context = window.getContext();

        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.windowBackground, outValue, true);
        viewWithBackground.setBackground(context.getDrawable(outValue.resourceId));
    }

    /**
     * Returns the view that draws the background of the dialog, or {@code null} if no suitable view
     * was found. If non-{@code null}, this is either the view added by
     * {@link DialogTransitionAnimator}, or the {@code decorView}.
     *
     * @param dialog the dialog to get the view with background for.
     */
    @Nullable
    private static View getViewWithBackground(Dialog dialog) {
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        final View decorView = window.getDecorView();
        Objects.requireNonNull(decorView, "DecorView should not be null");
        // We first look for the background on the dialogContentWithBackground added by
        // DialogTransitionAnimator. If it's not there, we use the background of the DecorView.
        final View viewWithBackground = decorView.findViewByPredicate(
                view -> view.getTag(
                        com.android.systemui.animation.R.id.tag_dialog_background) != null);
        return viewWithBackground != null ? viewWithBackground : decorView;
    }

    private static class DismissReceiver extends BroadcastReceiver {
        private final IntentFilter mIntentFilter = new IntentFilter();

        private final Dialog mDialog;
        private boolean mRegistered;
        private final BroadcastDispatcher mBroadcastDispatcher;
        private final DialogTransitionAnimator mDialogTransitionAnimator;

        DismissReceiver(Dialog dialog, BroadcastDispatcher broadcastDispatcher,
                DialogTransitionAnimator dialogTransitionAnimator,
                boolean shouldAcsdDismissDialog) {
            mDialog = dialog;
            mBroadcastDispatcher = broadcastDispatcher;
            mDialogTransitionAnimator = dialogTransitionAnimator;

            mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            if (shouldAcsdDismissDialog) {
                mIntentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            }
        }

        void register() {
            mBroadcastDispatcher.registerReceiver(this, mIntentFilter, null, UserHandle.CURRENT);
            mRegistered = true;
        }

        void unregister() {
            if (mRegistered) {
                mBroadcastDispatcher.unregisterReceiver(this);
                mRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // These broadcast are usually received when locking the device, swiping up to home
            // (which collapses the shade), etc. In those cases, we usually don't want to animate
            // back into the view.
            mDialogTransitionAnimator.disableAllCurrentDialogsExitAnimations();
            mDialog.dismiss();
        }
    }

    /**
     * A delegate class that should be implemented in place of sublcassing {@link SystemUIDialog}.
     *
     * <p>Implement this interface and then pass an instance of your implementation to
     * {@link SystemUIDialog.Factory#create(Delegate)}.
     */
    public interface Delegate extends DialogDelegate<SystemUIDialog> {
        /**
         * Returns a new {@link SystemUIDialog} which has been passed this Delegate in its
         * construction.
         */
        SystemUIDialog createDialog();
    }

    /** Empty view, used only to receive configuration changes. */
    static final class ConfigurationListenerView extends View {

        /** The callback to be invoked when the configuration changes. */
        @NonNull
        private final Consumer<Configuration> mConfigChangedCallback;

        ConfigurationListenerView(Context context,
                @NonNull Consumer<Configuration> configChangedCallback) {
            super(context);
            setWillNotDraw(true);
            setVisibility(View.GONE);
            mConfigChangedCallback = configChangedCallback;
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            mConfigChangedCallback.accept(newConfig);
        }
    }
}
