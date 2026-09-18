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

package com.android.systemui.accessibility.floatingmenu;

import static android.provider.Settings.Secure.ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.systemui.util.kotlin.JavaAdapterKt.collectFlow;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IUserInitializationCompleteCallback;

import androidx.annotation.MainThread;

import com.android.compose.animation.scene.OverlayKey;
import com.android.compose.animation.scene.SceneKey;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.bluetooth.HearingAidDeviceManager;
import com.android.systemui.Flags;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver.AccessibilityButtonMode;
import com.android.systemui.accessibility.AccessibilityButtonTargetsObserver;
import com.android.systemui.accessibility.Magnification;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.inputdevice.data.repository.PointerDeviceRepository;
import com.android.systemui.keyboard.data.repository.KeyboardRepository;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.shared.model.KeyguardState;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.res.R;
import com.android.systemui.scene.domain.interactor.SceneInteractor;
import com.android.systemui.scene.shared.model.KeyguardScenesKt;
import com.android.systemui.scene.shared.model.Overlays;
import com.android.systemui.scene.shared.model.Scenes;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode;
import com.android.systemui.util.settings.SecureSettings;

import kotlinx.coroutines.CoroutineScope;

import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

/** A controller to handle the lifecycle of accessibility floating menu. */
@MainThread
@SysUISingleton
public class AccessibilityFloatingMenuController implements
        AccessibilityButtonModeObserver.ModeChangedListener,
        AccessibilityButtonTargetsObserver.TargetsChangedListener {

    private final AccessibilityButtonModeObserver mAccessibilityButtonModeObserver;
    private final AccessibilityButtonTargetsObserver mAccessibilityButtonTargetsObserver;
    private final SceneInteractor mSceneInteractor;
    private final KeyguardTransitionInteractor mKeyguardInteractor;
    private final UserTracker mUserTracker;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final DisplayManager mDisplayManager;
    private final AccessibilityManager mAccessibilityManager;
    private final HearingAidDeviceManager mHearingAidDeviceManager;
    private final CoroutineScope mCoroutineScope;

    private final SecureSettings mSecureSettings;
    private final DisplayTracker mDisplayTracker;
    private final Magnification mMagnification;
    private final NavigationModeController mNavigationModeController;
    private final KeyboardRepository mKeyboardRepository;
    private final PointerDeviceRepository mPointerDeviceRepository;
    @VisibleForTesting IAccessibilityFloatingMenu mFloatingMenu;
    private final HeadlessSystemUserMode mHeadlessSystemUserMode;
    private int mBtnMode;
    private String mBtnTargets;
    private boolean mIsBouncerVisible;
    private KeyguardState mKeyguardState = KeyguardState.GONE;
    private SceneKey mSceneKey = Scenes.Gone;
    private boolean mIsUserInInitialization;
    @VisibleForTesting
    Handler mHandler;

    @VisibleForTesting
    final UserInitializationCompleteCallback mUserInitializationCompleteCallback =
            new UserInitializationCompleteCallback();

    @VisibleForTesting
    final Consumer<KeyguardState> mKeyguardStateConsumer = (state) -> {
        mKeyguardState = state;
        handleFloatingMenuVisibility();
    };

    final Consumer<SceneKey> mSceneConsumer = (scene) -> {
        mSceneKey = scene;
        handleFloatingMenuVisibility();
    };

    final Consumer<Set<OverlayKey>> mOverlayConsumer = (set) -> {
        mIsBouncerVisible = set.contains(Overlays.Bouncer);
        handleFloatingMenuVisibility();
    };

    final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onBeforeUserSwitching(int newUser) {
            destroyFloatingMenu();
            mIsUserInInitialization = true;
        }
    };

    @Inject
    public AccessibilityFloatingMenuController(
            Context context,
            WindowManager windowManager,
            DisplayManager displayManager,
            AccessibilityManager accessibilityManager,
            AccessibilityButtonTargetsObserver accessibilityButtonTargetsObserver,
            AccessibilityButtonModeObserver accessibilityButtonModeObserver,
            @Nullable HearingAidDeviceManager hearingAidDeviceManager,
            SecureSettings secureSettings,
            DisplayTracker displayTracker,
            NavigationModeController navigationModeController,
            @Main Handler handler,
            @Application CoroutineScope coroutineScope,
            KeyguardTransitionInteractor keyguardInteractor,
            SceneInteractor sceneInteractor,
            UserTracker userTracker,
            KeyboardRepository keyboardRepository,
            PointerDeviceRepository pointerDeviceRepository,
            HeadlessSystemUserMode headlessSystemUserMode,
            Magnification magnification) {
        mContext = context;
        mWindowManager = windowManager;
        mDisplayManager = displayManager;
        mAccessibilityManager = accessibilityManager;
        mAccessibilityButtonTargetsObserver = accessibilityButtonTargetsObserver;
        mAccessibilityButtonModeObserver = accessibilityButtonModeObserver;
        mHearingAidDeviceManager = hearingAidDeviceManager;
        mSecureSettings = secureSettings;
        mDisplayTracker = displayTracker;
        mNavigationModeController = navigationModeController;
        mHandler = handler;
        mKeyguardInteractor = keyguardInteractor;
        mSceneInteractor = sceneInteractor;
        mCoroutineScope = coroutineScope;
        mUserTracker = userTracker;
        mKeyboardRepository = keyboardRepository;
        mPointerDeviceRepository = pointerDeviceRepository;
        mHeadlessSystemUserMode = headlessSystemUserMode;
        mMagnification = magnification;
    }

    /**
     * Handles visibility of the accessibility floating menu when accessibility button mode changes.
     *
     * @param mode Current accessibility button mode.
     */
    @Override
    public void onAccessibilityButtonModeChanged(@AccessibilityButtonMode int mode) {
        mBtnMode = mode;
        handleFloatingMenuVisibility();
    }

    /**
     * Handles visibility of the accessibility floating menu when accessibility button targets
     * changes.
     * List should come from {@link Settings.Secure#ACCESSIBILITY_BUTTON_TARGETS}.
     * @param targets Current accessibility button list.
     */
    @Override
    public void onAccessibilityButtonTargetsChanged(String targets) {
        mBtnTargets = targets;
        handleFloatingMenuVisibility();
    }

    /** Initializes the AccessibilityFloatingMenuController configurations. */
    public void init() {
        mBtnMode = mAccessibilityButtonModeObserver.getCurrentAccessibilityButtonMode();
        mBtnTargets = mAccessibilityButtonTargetsObserver.getCurrentAccessibilityButtonTargets();
        registerContentObservers();
    }

    private void registerContentObservers() {
        mAccessibilityButtonModeObserver.addListener(this);
        mAccessibilityButtonTargetsObserver.addListener(this);
        if (mCoroutineScope != null) {
            if (Flags.sceneContainer()) {
                collectFlow(mCoroutineScope,
                        mSceneInteractor.getCurrentScene(), mSceneConsumer);
                collectFlow(mCoroutineScope,
                        mSceneInteractor.getCurrentOverlays(), mOverlayConsumer);
            } else {
                collectFlow(mCoroutineScope,
                        mKeyguardInteractor.getCurrentKeyguardState(), mKeyguardStateConsumer);
            }
        }
        mUserTracker.addCallback(mUserTrackerCallback, new HandlerExecutor(mHandler));
        mAccessibilityManager.registerUserInitializationCompleteCallback(
                mUserInitializationCompleteCallback);
    }

    /**
     * Handles the accessibility floating menu visibility with the given values.
     */
    private void handleFloatingMenuVisibility() {
        if (hasValidScene() && hasValidSettings()) {
            showFloatingMenu();
        } else {
            destroyFloatingMenu();
        }
    }

    private boolean hasValidScene() {
        if (mIsUserInInitialization) {
            return false; // Not allowed during user initialization.
        }

        // Evaluate based on scene
        if (Flags.sceneContainer()) {
            // Condition for HSUM with visible bouncer
            if (Flags.floatingMenuOnHeadlessUser()
                    && mHeadlessSystemUserMode.isHeadlessSystemUserMode()
                    && mIsBouncerVisible) {
                return true;
            }

            return !KeyguardScenesKt.isKeyguardScene(mSceneKey);
        }

        // Evaluate based on keyguardState
        return switch (mKeyguardState) {
            case KeyguardState.LOCKSCREEN, KeyguardState.DOZING, KeyguardState.AOD -> false;
            default -> true;
        };
    }

    private boolean hasValidSettings() {
        return mBtnMode == ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU
                && !TextUtils.isEmpty(mBtnTargets);
    }

    private void showFloatingMenu() {
        if (mFloatingMenu == null) {
            final Display defaultDisplay =
                    mDisplayManager.getDisplay(mDisplayTracker.getDefaultDisplayId());
            final Context windowContext =
                    mContext.createWindowContext(
                            defaultDisplay, TYPE_NAVIGATION_BAR_PANEL, /* options= */ null);
            windowContext.setTheme(R.style.Theme_SystemUI);
            mFloatingMenu =
                    new MenuViewLayerController(
                            windowContext,
                            mWindowManager,
                            mAccessibilityManager,
                            mSecureSettings,
                            mNavigationModeController,
                            mHearingAidDeviceManager,
                            mKeyboardRepository,
                            mPointerDeviceRepository,
                            mMagnification,
                            mKeyguardInteractor,
                            mSceneInteractor);
        }

        mFloatingMenu.show();
    }

    private void destroyFloatingMenu() {
        if (mFloatingMenu == null) {
            return;
        }

        mFloatingMenu.hide();
        mFloatingMenu = null;
    }

    class UserInitializationCompleteCallback extends IUserInitializationCompleteCallback.Stub {
        @Override
        public void onUserInitializationComplete(int userId) {
            mIsUserInInitialization = false;
            mBtnMode = mAccessibilityButtonModeObserver.getCurrentAccessibilityButtonMode();
            mBtnTargets =
                    mAccessibilityButtonTargetsObserver.getCurrentAccessibilityButtonTargets();
            mHandler.post(
                    () -> {
                        // Force a refresh by destroying the menu if it exists.
                        destroyFloatingMenu();
                        handleFloatingMenuVisibility();
                    });
        }
    }
}
