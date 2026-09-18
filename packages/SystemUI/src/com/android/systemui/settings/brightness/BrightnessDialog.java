/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.settings.brightness;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.Intent.EXTRA_BRIGHTNESS_DIALOG_IS_FULL_WIDTH;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManagerPolicyConstants.EXTRA_FROM_BRIGHTNESS_KEY;

import static com.android.systemui.shared.Flags.brightnessDialogOnSystemUser;
import static com.android.systemui.util.kotlin.JavaAdapterKt.collectFlow;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityManager;

import androidx.activity.ComponentActivity;
import androidx.compose.ui.platform.ComposeView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.res.R;
import com.android.systemui.shade.domain.interactor.ShadeInteractor;
import com.android.systemui.statusbar.policy.AccessibilityManagerWrapper;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor;

import dagger.Lazy;

import java.util.List;

import javax.inject.Inject;

/**
 * A dialog that provides controls for adjusting the screen brightness.
 *
 * This class extends `ComponentActivity` instead of the base `android.app.Activity` to support
 * hosting Jetpack Compose content. `ComponentActivity` provides the necessary
 * `LifecycleOwner`and `SavedStateRegistryOwner` that Compose requires to function correctly,
 * preventing crashes and simplifying lifecycle management. See b/417544544.
 */
public class BrightnessDialog extends ComponentActivity {

    public static final String ACTION_BRIGHTNESS_DIALOG_SHOWING =
            "com.android.systemui.settings.brightness.BRIGHTNESS_DIALOG_SHOWING";
    public static final String PERMISSION_SELF = "com.android.systemui.permission.SELF";

    @VisibleForTesting
    static final int DIALOG_TIMEOUT_MILLIS = 3000;

    private final DelayableExecutor mMainExecutor;
    private final AccessibilityManagerWrapper mAccessibilityMgr;
    private Runnable mCancelTimeoutRunnable;
    private final Lazy<ShadeInteractor> mShadeInteractorLazy;
    private final BrightnessSliderViewModel.Factory mBrightnessSliderViewModelFactory;
    private final BroadcastSender mBroadcastSender;
    private final boolean mIsExpandedAudioTileDetailsEnabled;

    @Inject
    public BrightnessDialog(
            @Main DelayableExecutor mainExecutor,
            AccessibilityManagerWrapper accessibilityMgr,
            Lazy<ShadeInteractor> shadeInteractorLazy,
            BrightnessSliderViewModel.Factory brightnessSliderViewModelFactory,
            BroadcastSender broadcastSender,
            ExpandedAudioTileDetailsFeatureInteractor expandedAudioTileDetailsFeatureInteractor
    ) {
        mMainExecutor = mainExecutor;
        mAccessibilityMgr = accessibilityMgr;
        mShadeInteractorLazy = shadeInteractorLazy;
        mBrightnessSliderViewModelFactory = brightnessSliderViewModelFactory;
        mBroadcastSender = broadcastSender;
        mIsExpandedAudioTileDetailsEnabled = expandedAudioTileDetailsFeatureInteractor.isEnabled();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (brightnessDialogOnSystemUser() && getUserId() != UserHandle.USER_SYSTEM) {
            requestFinish();
            return;
        }

        boolean isShadeExpanded = mIsExpandedAudioTileDetailsEnabled
                ? mShadeInteractorLazy.get().isAnyExpanded().getValue()
                : mShadeInteractorLazy.get().isQsExpanded().getValue();

        if (isShadeExpanded) {
            requestFinish();
        } else {
            initializeDialog();
        }
    }

    private void initializeDialog() {
        setWindowAttributes();
        ComposeView composeView = new ComposeView(this);
        ComposeDialogComposableProvider.INSTANCE.setComposableBrightness(
                composeView,
                new ComposableProvider(
                        mBrightnessSliderViewModelFactory,
                        mIsExpandedAudioTileDetailsEnabled)

        );
        composeView.setId(R.id.brightness_dialog_slider);
        setContentView(composeView);
        ((ViewGroup) composeView.getParent()).setClipChildren(false);
        setBrightnessDialogViewAttributes(composeView);

        collectFlow(
                composeView,
                mIsExpandedAudioTileDetailsEnabled ? mShadeInteractorLazy.get().isAnyExpanded()
                        : mShadeInteractorLazy.get().isQsExpanded(),
                this::onShadeStateChange
        );
    }

    private void onShadeStateChange(boolean isExpanded) {
        if (isExpanded) {
            requestFinish();
        }
    }

    private void setWindowAttributes() {
        final Window window = getWindow();
        if (brightnessDialogOnSystemUser()) {
            window.addPrivateFlags(WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
        }

        if (mIsExpandedAudioTileDetailsEnabled) {
            window.setGravity(Gravity.TOP | Gravity.END);
        } else {
            window.setGravity(Gravity.TOP | Gravity.START);
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        // Calling this creates the decor View, so setLayout takes proper effect
        // (see Dialog#onWindowAttributesChanged)
        window.getDecorView();
        window.setLayout(WRAP_CONTENT, WRAP_CONTENT);
        getTheme().applyStyle(R.style.Theme_SystemUI_QuickSettings, false);
    }

    void setBrightnessDialogViewAttributes(View container) {
        Configuration configuration = getResources().getConfiguration();
        // The brightness mirror container is INVISIBLE by default.
        container.setVisibility(View.VISIBLE);
        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) container.getLayoutParams();
        // Remove the margin. Have the container take all the space. Instead, insert padding.
        // This allows for the background to be visible around the slider.
        int margin = 0;
        lp.topMargin = margin;
        lp.bottomMargin = margin;
        lp.leftMargin = margin;
        lp.rightMargin = margin;
        if (mIsExpandedAudioTileDetailsEnabled) {
            int padding = getResources().getDimensionPixelSize(
                    R.dimen.rounded_slider_horizontal_padding
            );
            container.setPadding(padding, 0, padding, 0);
        } else {
            int padding = getResources().getDimensionPixelSize(
                    R.dimen.rounded_slider_background_padding
            );
            container.setPadding(padding, padding, padding, padding);
        }
        // If in multi-window or freeform, increase the top margin so the brightness dialog
        // doesn't get cut off.
        final int windowingMode = configuration.windowConfiguration.getWindowingMode();
        if (windowingMode == WINDOWING_MODE_MULTI_WINDOW
                || windowingMode == WINDOWING_MODE_FREEFORM) {
            lp.topMargin += 50;
        }

        /*
          If ExpandedAudioDetailedView is enabled, then we need to set some dimensions manually.
          This aligns the slider size and positioning with the volume dialog
          1. Dialog width at 364dpn
          2. End margin at 24dp
          3. Top margin at 0dp
         */
        if (mIsExpandedAudioTileDetailsEnabled) {
            lp.width = getResources().getDimensionPixelSize(
                    R.dimen.brightness_dialog_expanded_width
            );

            lp.setMarginEnd(getResources().getDimensionPixelSize(
                    R.dimen.brightness_dialog_margin_end));

            lp.topMargin = getResources().getDimensionPixelSize(
                    R.dimen.brightness_dialog_margin_top);
        } else {
            int orientation = configuration.orientation;
            int windowWidth = getWindowAvailableWidth();

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                boolean shouldBeFullWidth = getIntent()
                        .getBooleanExtra(EXTRA_BRIGHTNESS_DIALOG_IS_FULL_WIDTH, false);
                lp.width = shouldBeFullWidth ? windowWidth : windowWidth / 2;
            } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                lp.width = windowWidth;
            }
        }

        container.setLayoutParams(lp);
        Rect bounds = new Rect();
        container.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    // Exclude this view (and its horizontal margins) from triggering gestures.
                    // This prevents back gesture from being triggered by dragging close to the
                    // edge of the slider (0% or 100%).
                    bounds.set(0, 0, right - left, bottom - top);
                    v.setSystemGestureExclusionRects(List.of(bounds));
                });
    }

    private int getWindowAvailableWidth() {
        final WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
        // Gets all excluding insets
        final WindowInsets windowInsets = metrics.getWindowInsets();
        Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()
                | WindowInsets.Type.displayCutout());
        int insetsWidth = insets.right + insets.left;
        return metrics.getBounds().width() - insetsWidth;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mIsExpandedAudioTileDetailsEnabled) {
            mBroadcastSender.sendBroadcastAsUser(
                    new Intent(ACTION_BRIGHTNESS_DIALOG_SHOWING),
                    UserHandle.SYSTEM,
                    PERMISSION_SELF);
            MetricsLogger.visible(this, MetricsEvent.BRIGHTNESS_DIALOG);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (triggeredByBrightnessKey()) {
            scheduleTimeout();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MetricsLogger.hidden(this, MetricsEvent.BRIGHTNESS_DIALOG);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (mCancelTimeoutRunnable != null) {
                mCancelTimeoutRunnable.run();
            }
            requestFinish();
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (triggeredByBrightnessKey()) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // Cancel timeout if active
                if (mCancelTimeoutRunnable != null) {
                    mCancelTimeoutRunnable.run();
                    mCancelTimeoutRunnable = null;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                // Schedule timeout when done
                scheduleTimeout();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    protected void requestFinish() {
        finish();
    }

    private boolean triggeredByBrightnessKey() {
        return getIntent().getBooleanExtra(EXTRA_FROM_BRIGHTNESS_KEY, false);
    }

    private void scheduleTimeout() {
        if (mCancelTimeoutRunnable != null) {
            mCancelTimeoutRunnable.run();
        }
        final int timeout = mAccessibilityMgr.getRecommendedTimeoutMillis(DIALOG_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS);
        mCancelTimeoutRunnable = mMainExecutor.executeDelayed(this::requestFinish, timeout);
    }
}
