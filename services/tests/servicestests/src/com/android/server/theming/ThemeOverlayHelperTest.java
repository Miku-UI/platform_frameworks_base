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

package com.android.server.theming;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManagerTransaction;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.UserHandle;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class ThemeOverlayHelperTest {

    private static final int PRIMARY_USER_ID = 10;
    private static final int PROFILE_USER_ID = 11;
    private static final int SYSTEM_USER_ID = UserHandle.USER_SYSTEM;

    private static final float CONTRAST_DEFAULT = 0.0f;
    private static final int SEED_COLOR_VALID = Color.BLUE;
    private static final int STYLE_VALID = ThemeStyle.TONAL_SPOT;

    @Mock
    private OverlayManagerInternal mOverlayManager;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Captor
    private ArgumentCaptor<OverlayManagerTransaction> mTransactionCaptor;

    @Rule
    public final TestableContext mContext = new TestableContext(getInstrumentation().getContext(),
            null);

    private ThemeOverlayHelper mThemeOverlayHelper;
    private ThemeEnvironment mEnvironment;

    @Before
    public void setup() {
        // This initializes all fields annotated with @Mock and @Captor
        MockitoAnnotations.initMocks(this);
        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);

        LocalServices.addService(OverlayManagerInternal.class, mOverlayManager);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);

        mThemeOverlayHelper = new ThemeOverlayHelper();

        TestableResources resources = mContext.getOrCreateTestableResources();
        resources.addOverride(R.array.theming_legacy_overlays, new String[]{
                "com.android.systemui|neutral",
                "com.android.systemui|accent",
                "com.android.systemui|dynamic",
                "com.android.systemui|neutral1",
                "com.android.systemui|accent1",
                "com.google.android.apps.wearable.systemui|neutral",
                "com.google.android.apps.wearable.systemui|accent",
                "com.google.android.apps.wearable.systemui|dynamic"
        });

        when(mUserManagerInternal.isHeadlessSystemUserMode()).thenReturn(false);
        mEnvironment = new ThemeEnvironment(mContext, (key, def) -> def);
    }


    @Test
    public void applyCurrentStateOverlays_foregroundUser_enablesForSelfAndSystemAndProfiles() {
        // Setup: A primary user with an associated profile.
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
        statePair.addProfile(PROFILE_USER_ID);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Action: Pass true because this is simulating the Foreground User
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true, true);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        String overlayName = "dynamic";
        // Check that overlays are enabled for the primary user, their profile, and system.
        // The overlay itself is named with the PRIMARY_USER_ID suffix.
        assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, PRIMARY_USER_ID);
        assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, PROFILE_USER_ID);
        assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, SYSTEM_USER_ID);
    }

    @Test
    public void applyCurrentStateOverlays_backgroundUser_doesNotEnableForSystem() {
        // Setup: A background user (simulated by passing false to the helper)
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Action: Pass false because this is simulating a Background User
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, false, true);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        String overlayName = "dynamic";
        // Should be enabled for the user themselves
        assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, PRIMARY_USER_ID);
        // Should NOT be enabled for the system user
        assertNotSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, SYSTEM_USER_ID);
    }

    @Test
    public void applyCurrentStateOverlays_whenUserIsSystem_enablesOnce() {
        // Setup: The user is the system user.
        ThemeStatePair statePair = new ThemeStatePair(SYSTEM_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Is does not matter if we pass true/false here.
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true, true);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        String overlayName = "dynamic";
        // It should be enabled for the system user exactly once.
        // Overlay owner is SYSTEM_USER_ID, target is SYSTEM_USER_ID.
        String expectedSubstring = getSetEnabledSubstring(overlayName, SYSTEM_USER_ID,
                SYSTEM_USER_ID);
        int count = countOccurrences(transactionString, expectedSubstring);
        assertThat(count).isEqualTo(1);
    }

    @Test
    public void applyCurrentStateOverlays_skipRegistration_enablesWithoutRegistering() {
        // Setup: A primary user.
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Setup: Simulate overlays exist so we don't fallback to register
        OverlayIdentifier identifier = new OverlayIdentifier("android", "dynamic_10");
        when(mOverlayManager.getOverlayInfo(eq(identifier), eq(UserHandle.of(PRIMARY_USER_ID))))
                .thenReturn(new OverlayInfo("android", "dynamic_10", "android", null, null,
                        "/path", 0, 0, 0, true, true));

        // Action: Pass true for applyToSystem, but FALSE for shouldRegister.
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true, false);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        String overlayName = "dynamic";
        // Check that REGISTER request is NOT present.
        String registerRequest = "TYPE_REGISTER_FABRICATED";
        assertThat(transactionString).doesNotContain(registerRequest);

        // Check that ENABLE requests ARE present for user and system.
        assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, PRIMARY_USER_ID);
        assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID, SYSTEM_USER_ID);
    }

    @Test
    public void applyCurrentStateOverlays_handlesCommitExceptionGracefully() {
        // Setup a commit failure
        doThrow(new SecurityException("Test Exception")).when(mOverlayManager).commit(any());
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Action & Verification (should not crash)
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true, true);

        // Verify commit was still attempted
        verify(mOverlayManager).commit(any(OverlayManagerTransaction.class));
    }

    @Test
    public void applyCurrentStateOverlays_missingOverlay_recreatesOverlays() {
        // Setup: A primary user.
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Setup: Simulate missing overlay by returning null from getOverlayInfo
        // The helper checks if overlays exist before deciding to skip registration.
        when(mOverlayManager.getOverlayInfo(any(OverlayIdentifier.class), any(UserHandle.class)))
                .thenReturn(null);

        // Action: Pass FALSE for shouldRegister. Normally this would skip registration.
        // But because overlays are missing, it should force registration.
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true, false);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        // Check that REGISTER request IS present (meaning it fell back to heavy path)
        assertThat(transactionString).contains("TYPE_REGISTER_FABRICATED");
        // And enable is also there
        assertSetEnabled(transactionString, "dynamic", PRIMARY_USER_ID, PRIMARY_USER_ID);
    }

    @Test
    public void cleanupLegacyOverlays_unregistersCorrectOverlays() {
        when(mOverlayManager.getOverlayInfo(any(OverlayIdentifier.class), any(UserHandle.class)))
                .thenReturn(new OverlayInfo("com.dummy", "dummy_overlay", "android", null, null,
                        "/path", 0, 0, 0, true, true));

        String[] legacyOverlays = mContext.getResources().getStringArray(
                R.array.theming_legacy_overlays);

        mThemeOverlayHelper.cleanupLegacyOverlays(Arrays.asList(legacyOverlays));

        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        // Verify unregistration of legacy Mobile SystemUI overlays
        assertUnregister(transactionString, "com.android.systemui", "neutral");
        assertUnregister(transactionString, "com.android.systemui", "accent");
        assertUnregister(transactionString, "com.android.systemui", "dynamic");
        assertUnregister(transactionString, "com.android.systemui", "neutral1");
        assertUnregister(transactionString, "com.android.systemui", "accent1");

        // Verify unregistration of legacy Wear SystemUI overlays
        assertUnregister(transactionString, "com.google.android.apps.wearable.systemui", "neutral");
        assertUnregister(transactionString, "com.google.android.apps.wearable.systemui", "accent");
        assertUnregister(transactionString, "com.google.android.apps.wearable.systemui", "dynamic");
    }

    @Test
    public void createDynamicOverlay_containsNeutralAndAccentColors() {
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true,
                List.of(SEED_COLOR_VALID), CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);

        FabricatedOverlay overlay = mThemeOverlayHelper.createDynamicOverlay(
                statePair.getLightScheme(), statePair.getDarkScheme(), PRIMARY_USER_ID);

        Map<String, Color> entries = android.app.ThemeManager.extractColorPairs(overlay);

        // Verify some neutral colors are present
        assertThat(entries).containsKey("android:color/system_neutral1_100_light");
        assertThat(entries).containsKey("android:color/system_neutral1_100_dark");
        assertThat(entries).containsKey("android:color/system_neutral2_500_light");

        // Verify some accent colors are present
        assertThat(entries).containsKey("android:color/system_accent1_100_light");
        assertThat(entries).containsKey("android:color/system_accent1_100_dark");
        assertThat(entries).containsKey("android:color/system_accent2_500_light");
    }

    /**
     * Asserts that a specific UNREGISTER request is present in the transaction string.
     */
    private void assertUnregister(String transactionString, String packageName,
            String overlayName) {
        String overlayId = packageName + ":" + overlayName;
        // Expected format based on OverlayManagerTransaction.toString()
        // Request{type=0x03 (TYPE_UNREGISTER_FABRICATED), overlay=package:name, ...}
        // We match a subset to be robust.
        String expectedSubstring = String.format("overlay=%s", overlayId);
        assertThat(transactionString).contains("TYPE_UNREGISTER_FABRICATED");
        assertThat(transactionString).contains(expectedSubstring);
    }

    /**
     * Builds the expected substring for a SET_ENABLED request to find in the
     * transaction's string representation.
     */
    private String getSetEnabledSubstring(String overlayName, int overlayUserId,
            int targetUserId) {
        // Based on OverlayManager.Request.toString()
        String overlayId = "android:" + overlayName + "_" + overlayUserId; // Owner is now android
        // The format must match OverlayManagerTransaction.Request.toString()
        // e.g., "Request{type=0x00 (TYPE_SET_ENABLED), overlay=android:neutral,
        // userId=10, ...}"
        return String.format("Request{type=0x00 (TYPE_SET_ENABLED), overlay=%s, userId=%d",
                overlayId, targetUserId);
    }

    /**
     * Asserts that a specific SET_ENABLED request is present in the transaction string.
     */
    private void assertSetEnabled(String transactionString, String overlayName, int overlayUserId,
            int targetUserId) {
        String expectedSubstring = getSetEnabledSubstring(overlayName, overlayUserId, targetUserId);
        assertThat(transactionString).contains(expectedSubstring);
    }

    /**
     * Asserts that a specific SET_ENABLED request is NOT present in the transaction string.
     */
    private void assertNotSetEnabled(String transactionString, String overlayName,
            int overlayUserId, int targetUserId) {
        String expectedSubstring = getSetEnabledSubstring(overlayName, overlayUserId, targetUserId);
        assertThat(transactionString).doesNotContain(expectedSubstring);
    }

    /**
     * Counts the number of times a substring appears in a source string.
     */
    private int countOccurrences(String source, String sub) {
        Pattern p = Pattern.compile(Pattern.quote(sub));
        Matcher m = p.matcher(source);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
