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
import static android.provider.Settings.Secure.ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR;

import static com.google.common.truth.Truth.assertThat;

import static kotlinx.coroutines.flow.StateFlowKt.MutableStateFlow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compose.animation.scene.OverlayKey;
import com.android.compose.animation.scene.SceneKey;
import com.android.settingslib.bluetooth.HearingAidDeviceManager;
import com.android.systemui.Flags;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver;
import com.android.systemui.accessibility.AccessibilityButtonTargetsObserver;
import com.android.systemui.accessibility.Magnification;
import com.android.systemui.inputdevice.data.repository.FakePointerDeviceRepository;
import com.android.systemui.keyboard.data.repository.FakeKeyboardRepository;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.shared.model.KeyguardState;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.scene.domain.interactor.SceneInteractor;
import com.android.systemui.scene.shared.model.Overlays;
import com.android.systemui.scene.shared.model.Scenes;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode;
import com.android.systemui.util.settings.SecureSettings;

import kotlinx.coroutines.flow.MutableStateFlow;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Set;
import java.util.concurrent.Executor;

/** Test for {@link AccessibilityFloatingMenuController}. */
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class AccessibilityFloatingMenuControllerTest extends SysuiTestCase {

    private static final String TEST_A11Y_BTN_TARGETS = "Magnification";

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();
    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContextWrapper;
    private WindowManager mWindowManager;
    private AccessibilityManager mAccessibilityManager;
    private AccessibilityFloatingMenuController mController;
    private TestableLooper mTestableLooper;
    @Mock
    private AccessibilityButtonTargetsObserver mTargetsObserver;
    @Mock
    private AccessibilityButtonModeObserver mModeObserver;
    @Mock
    private KeyguardTransitionInteractor mKeyguardInteractor;
    private final MutableStateFlow<KeyguardState> mKeyguardStateFlow =
            MutableStateFlow(KeyguardState.GONE);
    @Mock
    private SceneInteractor mSceneInteractor;
    private final MutableStateFlow<SceneKey> mSceneFlow = MutableStateFlow(Scenes.Gone);
    private final MutableStateFlow<Set<OverlayKey>> mOverlayFlow = MutableStateFlow(Set.of());
    @Mock
    private SecureSettings mSecureSettings;
    @Mock
    private NavigationModeController mNavigationModeController;
    @Mock
    private HearingAidDeviceManager mHearingAidDeviceManager;

    @Mock
    private UserTracker mUserTracker;
    @Captor
    private ArgumentCaptor<UserTracker.Callback> mUserTrackerCallbackCaptor;
    private UserTracker.Callback mUserTrackerCallback;
    @Mock
    private HeadlessSystemUserMode mHeadlessSystemUserMode;

    private FakeKeyboardRepository mKeyboardRepository;
    private FakePointerDeviceRepository mPointerDeviceRepository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mKeyboardRepository = new FakeKeyboardRepository();
        mPointerDeviceRepository = new FakePointerDeviceRepository();

        mContextWrapper = new ContextWrapper(mContext) {
            @Override
            public Context createContextAsUser(UserHandle user, int flags) {
                return getBaseContext();
            }
        };

        mWindowManager = mContext.getSystemService(WindowManager.class);
        mAccessibilityManager = mContext.getSystemService(AccessibilityManager.class);
        mTestableLooper = TestableLooper.get(this);

        when(mTargetsObserver.getCurrentAccessibilityButtonTargets())
                .thenReturn("");
        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU, UserHandle.USER_CURRENT);

        when(mHeadlessSystemUserMode.isHeadlessSystemUserMode()).thenReturn(false);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.onAccessibilityButtonTargetsChanged("");
            mController = null;
        }
    }

    @Test
    public void initController_registerListeners() {
        mController = setUpController();

        verify(mTargetsObserver).addListener(
                any(AccessibilityButtonTargetsObserver.TargetsChangedListener.class));
        verify(mModeObserver).addListener(
                any(AccessibilityButtonModeObserver.ModeChangedListener.class));
        verify(mUserTracker).addCallback(any(UserTracker.Callback.class), any(Executor.class));
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onKeyguardState_gone_showFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mKeyguardStateConsumer.accept(KeyguardState.GONE);

        assertThat(mController.mFloatingMenu).isNotNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onScene_gone_showFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mSceneConsumer.accept(Scenes.Gone);

        assertThat(mController.mFloatingMenu).isNotNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onKeyguardState_bouncer_showFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mKeyguardStateConsumer.accept(KeyguardState.PRIMARY_BOUNCER);

        assertThat(mController.mFloatingMenu).isNotNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onKeyguardState_lockScreen_hideFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mKeyguardStateConsumer.accept(KeyguardState.LOCKSCREEN);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onScene_lockScreen_hideFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mSceneConsumer.accept(Scenes.Lockscreen);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onKeyguardState_AOD_hideFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mKeyguardStateConsumer.accept(KeyguardState.AOD);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onScene_dream_hideFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mSceneConsumer.accept(Scenes.Dream);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onUserSwitch_old_hideFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();
        captureUserTrackerCallback();

        mController.mKeyguardStateConsumer.accept(KeyguardState.GONE);

        mUserTrackerCallback.onBeforeUserSwitching(0);
        // User switching should hide the menu

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onUserSwitch_hideFloatingMenu() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();
        captureUserTrackerCallback();

        mController.mSceneConsumer.accept(Scenes.Gone);

        mUserTrackerCallback.onBeforeUserSwitching(0);
        // User switching should hide the menu

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onAccessibilityButtonModeChanged_floatingModeAndHasButtonTargets_showWidget() {
        when(mTargetsObserver.getCurrentAccessibilityButtonTargets())
                .thenReturn(TEST_A11Y_BTN_TARGETS);
        mController = setUpController();

        mController.onAccessibilityButtonModeChanged(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU);

        assertThat(mController.mFloatingMenu).isNotNull();
    }

    @Test
    public void onAccessibilityButtonModeChanged_floatingModeAndNoButtonTargets_destroyWidget() {
        when(mTargetsObserver.getCurrentAccessibilityButtonTargets()).thenReturn("");

        mController = setUpController();

        mController.onAccessibilityButtonModeChanged(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onAccessibilityButtonModeChanged_navBarModeAndHasButtonTargets_destroyWidget() {
        when(mTargetsObserver.getCurrentAccessibilityButtonTargets())
                .thenReturn(TEST_A11Y_BTN_TARGETS);
        mController = setUpController();

        mController.onAccessibilityButtonModeChanged(ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onAccessibilityButtonModeChanged_navBarModeAndNoButtonTargets_destroyWidget() {
        when(mTargetsObserver.getCurrentAccessibilityButtonTargets()).thenReturn("");
        mController = setUpController();

        mController.onAccessibilityButtonModeChanged(ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onAccessibilityButtonTargetsChanged_floatingModeAndHasButtonTargets_showWidget() {
        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU);
        mController = setUpController();

        mController.onAccessibilityButtonTargetsChanged(TEST_A11Y_BTN_TARGETS);

        assertThat(mController.mFloatingMenu).isNotNull();
    }

    @Test
    public void onAccessibilityButtonTargetsChanged_floatingModeAndNoButtonTargets_destroyWidget() {
        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU);
        mController = setUpController();

        mController.onAccessibilityButtonTargetsChanged("");

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onAccessibilityButtonTargetsChanged_navBarModeAndHasButtonTargets_destroyWidget() {
        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR);
        mController = setUpController();

        mController.onAccessibilityButtonTargetsChanged(TEST_A11Y_BTN_TARGETS);

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onAccessibilityButtonTargetsChanged_navBarModeAndNoButtonTargets_destroyWidget() {
        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR);
        mController = setUpController();

        mController.onAccessibilityButtonTargetsChanged("");

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    public void onTargetsChanged_isFloatingViewLayerControllerCreated() {
        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU);

        mController = setUpController();
        mController.onAccessibilityButtonTargetsChanged(TEST_A11Y_BTN_TARGETS);

        assertThat(mController.mFloatingMenu).isInstanceOf(MenuViewLayerController.class);
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    public void onUserInitializationComplete_keyguardNotVisible_destroysOldWidget() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();

        mController.mKeyguardStateConsumer.accept(KeyguardState.GONE);

        IAccessibilityFloatingMenu floatingMenu = mController.mFloatingMenu;

        mController.mUserInitializationCompleteCallback
                .onUserInitializationComplete(mContext.getUserId());
        mTestableLooper.processAllMessages();

        assertThat(mController.mFloatingMenu).isNotNull();
        assertThat(mController.mFloatingMenu).isNotSameInstanceAs(floatingMenu);
    }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    @DisableFlags(Flags.FLAG_FLOATING_MENU_ON_HEADLESS_USER)
    public void onBouncerOverlay_headlessSystem_flagOff_destroysWidget() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();
        when(mHeadlessSystemUserMode.isHeadlessSystemUserMode()).thenReturn(true);
        mController.mSceneConsumer.accept(Scenes.Lockscreen);

        mController.mOverlayConsumer.accept(Set.of(Overlays.Bouncer));

        assertThat(mController.mFloatingMenu).isNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_FLOATING_MENU_ON_HEADLESS_USER})
    public void onBouncerOverlay_headlessSystem_showsWidget() {
        enableAccessibilityFloatingMenuConfig();
        mController = setUpController();
        when(mHeadlessSystemUserMode.isHeadlessSystemUserMode()).thenReturn(true);
        mController.mSceneConsumer.accept(Scenes.Lockscreen);

        mController.mOverlayConsumer.accept(Set.of(Overlays.Bouncer));

        assertThat(mController.mFloatingMenu).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_FLOATING_MENU_ON_HEADLESS_USER})
    public void onBouncerOverlay_notHeadlessSystem_destroysWidget() {
        mController = setUpController();
        enableAccessibilityFloatingMenuConfig();
        when(mHeadlessSystemUserMode.isHeadlessSystemUserMode()).thenReturn(false);
        mController.mSceneConsumer.accept(Scenes.Lockscreen);

        mController.mOverlayConsumer.accept(Set.of(Overlays.Bouncer));

        assertThat(mController.mFloatingMenu).isNull();
    }

    private AccessibilityFloatingMenuController setUpController() {
        final WindowManager windowManager = mContext.getSystemService(WindowManager.class);
        final DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        final FakeDisplayTracker displayTracker = new FakeDisplayTracker(mContext);
        when(mKeyguardInteractor.getCurrentKeyguardState()).thenReturn(mKeyguardStateFlow);
        when(mSceneInteractor.getCurrentScene()).thenReturn(mSceneFlow);
        when(mSceneInteractor.getCurrentOverlays()).thenReturn(mOverlayFlow);
        final AccessibilityFloatingMenuController controller =
                new AccessibilityFloatingMenuController(mContextWrapper, windowManager,
                        displayManager, mAccessibilityManager, mTargetsObserver, mModeObserver,
                        mHearingAidDeviceManager, mSecureSettings,
                        displayTracker, mNavigationModeController,
                        new Handler(mTestableLooper.getLooper()),
                        /*coroutineScope= */ null ,
                        mKeyguardInteractor, mSceneInteractor, mUserTracker,
                        mKeyboardRepository, mPointerDeviceRepository,
                        mHeadlessSystemUserMode,
                        mock(Magnification.class));
        controller.init();
        controller.mUserInitializationCompleteCallback.onUserInitializationComplete(0);
        mTestableLooper.processAllMessages();

        return controller;
    }

    private void enableAccessibilityFloatingMenuConfig() {
        when(mTargetsObserver.getCurrentAccessibilityButtonTargets())
                .thenReturn(TEST_A11Y_BTN_TARGETS);

        when(mModeObserver.getCurrentAccessibilityButtonMode())
                .thenReturn(ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU);
    }

    private void captureUserTrackerCallback() {
        verify(mUserTracker).addCallback(mUserTrackerCallbackCaptor.capture(), any());
        mUserTrackerCallback = mUserTrackerCallbackCaptor.getValue();
    }
}