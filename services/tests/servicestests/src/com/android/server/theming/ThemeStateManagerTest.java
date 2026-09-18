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

package com.android.server.theming;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.systemui.monet.ColorScheme.GOOGLE_BLUE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.content.om.OverlayManagerTransaction;
import android.content.theming.ThemeStyle;
import android.os.UserHandle;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;


@RunWith(AndroidJUnit4.class)
public class ThemeStateManagerTest {
    private static final int DEFAULT_USER_ID = 11;
    private static final List<Integer> DEFAULT_SEED_COLORS = List.of(0xFFFF0000); // RED
    private static final float DEFAULT_CONTRAST = 0.0f;
    private static final int DEFAULT_STYLE = ThemeStyle.TONAL_SPOT;
    private static final int PROFILE_ID = 10;
    private static final float DELTA_CHECK_RESOLUTION = 0.001f;

    @Mock
    private UserManagerInternal mUserManager;
    @Mock
    private OverlayManagerInternal mOverlayManager;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private ThemeOverlayHelper mThemeOverlayHelper;
    @Mock
    private ThemeUserLifecycle mUserLifecycle;
    @Mock
    private android.app.KeyguardManager mKeyguardManager;
    @Captor
    private ArgumentCaptor<OverlayManagerTransaction> mTransactionCaptor;

    @Rule
    public final TestableContext mMainContext = spy(
            new TestableContext(getInstrumentation().getContext(), null));
    @Rule
    public final TestableContext mUserContext = new TestableContext(
            getInstrumentation().getContext(), null);

    private ThemeStateManager mThemeStateManager;
    private FakeScheduledExecutorService mSchedulerExecutor;
    private ThemeEnvironment mEnvironment;

    private final HashMap<Integer, Object> mUserResourceOverrides = new HashMap<>(
            new ImmutableMap.Builder<Integer, Object>()
                    .put(R.color.system_accent1_500_light, 0xFF6476A5)
                    .put(R.color.system_accent2_500_light, 0xFF70778B)
                    .put(R.color.system_accent3_500_light, 0xFF836E99)
                    .put(R.color.system_neutral1_500_light, 0xFF76777C)
                    .put(R.color.system_neutral2_500_light, 0xFF757780)
                    .put(R.color.system_accent1_500_dark, 0xFF69769B)
                    .put(R.color.system_accent2_500_dark, 0xFF70778B)
                    .put(R.color.system_accent3_500_dark, 0xFF836E99)
                    .put(R.color.system_neutral1_500_dark, 0xFF76777C)
                    .put(R.color.system_neutral2_500_dark, 0xFF757780)
                    .put(R.color.system_outline_variant_dark, 0xFF454850)
                    .put(R.color.system_outline_variant_light, 0xFFB0B1BC)
                    .put(R.color.system_primary_container_dark, 0xFF445274)
                    .put(R.color.system_primary_container_light, 0xFFB9CBFF)
                    .build());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);

        LocalServices.addService(OverlayManagerInternal.class, mOverlayManager);
        LocalServices.addService(UserManagerInternal.class, mUserManager);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);

        // create user testable resources
        TestableResources userResources = mUserContext.getOrCreateTestableResources();
        mUserResourceOverrides.forEach(userResources::addOverride);

        doReturn(mUserContext).when(mMainContext).createContextAsUser(any(UserHandle.class),
                anyInt());

        when(mUserManager.getProfileParentId(eq(DEFAULT_USER_ID))).thenReturn(DEFAULT_USER_ID);
        when(mUserManager.getProfileParentId(eq(PROFILE_ID))).thenReturn(DEFAULT_USER_ID);
        when(mUserManager.getProfileIds(anyInt(), anyBoolean())).thenAnswer(invocation -> {
            int requestedUserId = invocation.getArgument(0);
            return new int[]{requestedUserId};
        });

        mEnvironment = new ThemeEnvironment(mMainContext, (key, def) -> def);
        mEnvironment.setBootingComplete(mUserLifecycle);
        mEnvironment.onServicesReady(mKeyguardManager);

        mSchedulerExecutor = new FakeScheduledExecutorService();
        mThemeStateManager = new ThemeStateManager(mMainContext, mSchedulerExecutor, mEnvironment);
        mThemeStateManager.setThemeOverlayHelper(mThemeOverlayHelper);
        mThemeStateManager.onServicesReady();
    }

    @After
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
    }

    @Test
    public void test_userStartsCorrectly() {
        // start first user
        ThemeStatePair pair = startProvisionedUser();
        assertThat(pair).isNotNull();
    }

    @Test
    public void test_startingUserTwice_isIgnored() {
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true,
                List.of(0xFFFF0000 /* RED */), DEFAULT_CONTRAST, DEFAULT_STYLE);

        ThemeStatePair firstState = mThemeStateManager.getState(DEFAULT_USER_ID);
        assertThat(firstState.getCurrentState().seedColor()).isEqualTo(0xFFFF0000);

        // Try to start duplicate user with BLUE
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true,
                List.of(0xFF0000FF /* BLUE */), DEFAULT_CONTRAST, DEFAULT_STYLE);

        // Verify the state was NOT overwritten
        ThemeStatePair stateAfterDup = mThemeStateManager.getState(DEFAULT_USER_ID);
        assertThat(stateAfterDup).isSameInstanceAs(firstState);
        assertThat(stateAfterDup.getCurrentState().seedColor()).isEqualTo(0xFFFF0000);
    }

    @Test
    public void test_startChildUserAsProfile() {
        // start first user
        ThemeStatePair pair = startProvisionedUser();

        // initialize user that has parent
        mThemeStateManager.onUserStart(UserHandle.of(PROFILE_ID), true, DEFAULT_SEED_COLORS,
                DEFAULT_CONTRAST, DEFAULT_STYLE);

        // fails when trying to get non existing state.
        assertThrows(IllegalStateException.class, () -> mThemeStateManager.getState(PROFILE_ID));

        // the profile was added to the main user's child list.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingChildProfiles().contains(PROFILE_ID)).isTrue();
    }


    @Test
    public void test_seedColorChange() {
        List<Integer> newSeedColors = List.of(0xFF0000FF);
        ThemeStatePair pair = startProvisionedUser();

        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, true);

        // checks state is the same but there is a pending update.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isTrue();

        waitForThemeUpdate();

        // checks new state and there are no pending updates.
        assertThat(pair.getPendingState()).isNull(); // nothing to update
        assertThat(pair.getCurrentState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_seedColorChange_defersWithUnprovisionedUser() {
        List<Integer> newSeedColors = List.of(0xFF0000FF);
        ThemeStatePair pair = startUnprovisionedUser();
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, true);

        // checks state is the same but there is a pending update that CANNOT be applied yet.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        waitForThemeUpdate();

        // checks state is the same but there is a pending update that CANNOT be applied yet.
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        mThemeStateManager.onFinishSetup(DEFAULT_USER_ID);
        waitForThemeUpdate();

        // checks new state and there are no pending updates.
        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.getCurrentState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_styleChange() {
        int newStyle = ThemeStyle.EXPRESSIVE;
        ThemeStatePair pair = startProvisionedUser();

        mThemeStateManager.onStyleChange(DEFAULT_USER_ID, newStyle);

        // checks state is the same but there is a pending update.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().style()).isEqualTo(newStyle);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isTrue();

        waitForThemeUpdate();

        // checks new state and there are no pending updates.
        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.getCurrentState().style()).isEqualTo(newStyle);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_styleChange_defersWithUnprovisionedUser() {
        int newStyle = ThemeStyle.EXPRESSIVE;
        ThemeStatePair pair = startUnprovisionedUser();

        mThemeStateManager.onStyleChange(DEFAULT_USER_ID, newStyle);

        // checks state is the same but there is a pending update that CANNOT be applied yet.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().style()).isEqualTo(newStyle);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        waitForThemeUpdate();

        // checks state is the same but there is a pending update that CANNOT be applied yet.
        assertThat(pair.getPendingState().style()).isEqualTo(newStyle);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        mThemeStateManager.onFinishSetup(DEFAULT_USER_ID);
        waitForThemeUpdate();

        // checks new state and there are no pending updates.
        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.getCurrentState().style()).isEqualTo(newStyle);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_contrastChange() {
        float newContrast = 0.5f;
        ThemeStatePair pair = startProvisionedUser();

        mThemeStateManager.onContrastChange(DEFAULT_USER_ID, newContrast);

        // checks state is the same but there is a pending update.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().contrast()).isWithin(DELTA_CHECK_RESOLUTION).of(
                newContrast);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isTrue();

        waitForThemeUpdate();

        // checks new state and there are no pending updates.
        assertThat(pair.getPendingState()).isNull(); // nothing to update
        assertThat(pair.getCurrentState().contrast()).isWithin(DELTA_CHECK_RESOLUTION).of(
                newContrast);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_contrastChange_defersWithUnprovisionedUser() {
        float newContrast = 0.5f;
        ThemeStatePair pair = startUnprovisionedUser();

        mThemeStateManager.onContrastChange(DEFAULT_USER_ID, newContrast);

        // checks state is the same but there is a pending update that CANNOT be applied yet.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().contrast()).isWithin(DELTA_CHECK_RESOLUTION).of(
                newContrast);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        waitForThemeUpdate();

        // checks state is the same but there is a pending update that CANNOT be applied yet.
        assertThat(pair.getPendingState().contrast()).isWithin(DELTA_CHECK_RESOLUTION).of(
                newContrast);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        mThemeStateManager.onFinishSetup(DEFAULT_USER_ID);
        waitForThemeUpdate();

        // checks new state and there are no pending updates.
        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.getCurrentState().contrast()).isWithin(DELTA_CHECK_RESOLUTION).of(
                newContrast);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_finishUserSetup() {
        ThemeStatePair pair = startUnprovisionedUser();

        mThemeStateManager.onFinishSetup(DEFAULT_USER_ID);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().isSetup()).isTrue();
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());

        waitForThemeUpdate();

        assertThat(pair.getCurrentState().isSetup()).isTrue();
        assertThat(pair.shouldUpdate()).isFalse();
        assertThat(pair.getPendingState()).isNull();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_profileAdd() {
        int profileId = PROFILE_ID;
        ThemeStatePair pair = startProvisionedUser();

        mThemeStateManager.onProfileAdd(DEFAULT_USER_ID, profileId);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingChildProfiles()).contains(profileId);
        assertThat(pair.shouldUpdate()).isTrue();

        waitForThemeUpdate();

        assertThat(pair.getPendingState()).isNull(); // nothing to update
        assertThat(pair.getCurrentState().childProfiles()).contains(profileId);

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_reevaluateSystemTheme_noUpdates() {
        ThemeStatePair pair = startProvisionedUser();
        mThemeStateManager.reevaluateSystemTheme();
        assertThat(mSchedulerExecutor.getFutures()).isEmpty();
        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.shouldUpdate()).isFalse();
    }

    @Test
    public void test_reevaluateSystemTheme_withUpdates() {
        ThemeStatePair pair = startProvisionedUser();

        List<Integer> newSeedColors = List.of(0xFF0000FF);
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, true);

        mThemeStateManager.reevaluateSystemTheme();
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isTrue();

        waitForThemeUpdate();

        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.getCurrentState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.shouldUpdate()).isFalse();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_reevaluateSystemTheme_withDebouncing() {
        ThemeStatePair pair = startProvisionedUser();

        List<Integer> newSeedColors = List.of(0xFF0000FF);
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, true);

        mThemeStateManager.reevaluateSystemTheme();
        mThemeStateManager.reevaluateSystemTheme();
        mThemeStateManager.reevaluateSystemTheme();
        mThemeStateManager.reevaluateSystemTheme();

        assertThat(pair.shouldUpdate()).isTrue();
        assertThat(mSchedulerExecutor.getFutures()).hasSize(1);

        waitForThemeUpdate();

        assertThat(pair.getPendingState()).isNull();
        assertThat(pair.getCurrentState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.shouldUpdate()).isFalse();
    }

    @Test
    public void test_onLockStateChange_locked_shouldApplyDeferredChanges() {
        ThemeStatePair pair = startProvisionedUser();
        pair.setDeferUpdatesOnLock(true);

        List<Integer> newSeedColors = List.of(0xFF0000FF); // Blue
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, false);

        // The change is deferred.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        mThemeStateManager.onLockStateChange(true);
        waitForThemeUpdate();

        // The change is applied immediately.
        assertThat(pair.getCurrentState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.shouldUpdate()).isFalse();
        assertThat(pair.getPendingState()).isNull();

        // Verify that the overlays were actually applied.
        verify(mThemeOverlayHelper).applyCurrentStateOverlays(any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void test_onLockStateChange_unlocked_shouldNotApplyDeferredChanges() {
        ThemeStatePair pair = startProvisionedUser();
        pair.setDeferUpdatesOnLock(true);

        List<Integer> newSeedColors = List.of(0xFF0000FF); // Blue
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, false);

        // The change is deferred.
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();

        mThemeStateManager.onLockStateChange(false);
        waitForThemeUpdate();

        // The change is still deferred.
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse();
    }

    @Test
    public void test_onProfileAdd_shouldAddProfileToParentState() {
        ThemeStatePair pair = startProvisionedUser();

        mThemeStateManager.onProfileAdd(DEFAULT_USER_ID, PROFILE_ID);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingChildProfiles()).contains(PROFILE_ID);
    }

    @Test
    public void test_onProfileAdd_shouldNotAddDuplicateProfile() {
        ThemeStatePair pair = startProvisionedUser();

        mThemeStateManager.onProfileAdd(DEFAULT_USER_ID, PROFILE_ID);
        mThemeStateManager.onProfileAdd(DEFAULT_USER_ID, PROFILE_ID); // Add again

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingChildProfiles()).contains(PROFILE_ID);
        // Additional checks can be added to ensure no duplicates or errors occurred
    }

    @Test
    public void testOnSeedColorChange_fromBackgroundApp_shouldDeferChange() {
        ThemeStatePair pair = startProvisionedUser();

        List<Integer> newSeedColors = List.of(0xFF0000FF); // Blue
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, false);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isFalse(); // Change deferred
    }

    @Test
    public void testOnSeedColorChange_fromForegroundApp_shouldNotDeferChange() {
        ThemeStatePair pair = startProvisionedUser();

        List<Integer> newSeedColors = List.of(0xFF0000FF); // Blue
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, true);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().seedColors()).isEqualTo(newSeedColors);
        assertThat(pair.getPendingState()).isNotEqualTo(pair.getCurrentState());
        assertThat(pair.shouldUpdate()).isTrue(); // Change not deferred
    }

    @Test
    public void testOnSeedColorChange_deferredChangeAppliedAfterUnlock() {
        ThemeStatePair pair = startProvisionedUser();
        pair.setDeferUpdatesOnLock(true);

        List<Integer> newSeedColors = List.of(0xFF0000FF); // Blue
        mThemeStateManager.onSeedColorChange(DEFAULT_USER_ID, newSeedColors, false);
        assertThat(pair.shouldUpdate()).isFalse(); // Change deferred

        pair.setDeferUpdatesOnLock(false); // Simulate unlock
        assertThat(pair.shouldUpdate()).isTrue(); // Should be applied now
    }

    @Test
    public void testOnBootComplete_colorSchemeApplied_shouldNotForceUpdate() {
        // creates user with seed color same as the default google_blue
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true, List.of(GOOGLE_BLUE),
                DEFAULT_CONTRAST, DEFAULT_STYLE);
        ThemeStatePair pair = mThemeStateManager.getState(DEFAULT_USER_ID);

        // Mock color scheme as applied AND enabled
        when(mThemeOverlayHelper.isColorSchemeApplied(any(), anyInt(), any(), any())).thenReturn(
                true);
        when(mThemeOverlayHelper.isOverlayEnabled(anyInt())).thenReturn(true);

        mThemeStateManager.evaluateAllUsers(false);
        assertThat(pair.getPendingState()).isNull(); // there is no update
    }

    @Test
    public void testOnBootComplete_colorSchemeNotApplied_shouldForceUpdate() {
        // creates user with seed color red, not the same as the default google_blue
        ThemeStatePair pair = startProvisionedUser();

        // Mock color scheme as NOT applied
        when(mThemeOverlayHelper.isColorSchemeApplied(any(), anyInt(), any(), any())).thenReturn(
                false);

        mThemeStateManager.evaluateAllUsers(false);
        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().timeStamp()).isNotEqualTo(
                pair.getCurrentState().timeStamp());
    }

    @Test
    public void testOnBootComplete_paletteOutdated_shouldForceUpdate() {
        // creates user with seed color same as the default google_blue
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true, List.of(GOOGLE_BLUE),
                DEFAULT_CONTRAST, DEFAULT_STYLE);
        ThemeStatePair pair = mThemeStateManager.getState(DEFAULT_USER_ID);

        mThemeStateManager.evaluateAllUsers(true);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().timeStamp()).isNotEqualTo(
                pair.getCurrentState().timeStamp());
    }

    private void waitForThemeUpdate() {
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);
    }

    private ThemeStatePair startProvisionedUser() {
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true, DEFAULT_SEED_COLORS,
                DEFAULT_CONTRAST, DEFAULT_STYLE);
        return mThemeStateManager.getState(DEFAULT_USER_ID);
    }

    private ThemeStatePair startUnprovisionedUser() {
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), false, DEFAULT_SEED_COLORS,
                DEFAULT_CONTRAST, DEFAULT_STYLE);
        return mThemeStateManager.getState(DEFAULT_USER_ID);
    }
}
