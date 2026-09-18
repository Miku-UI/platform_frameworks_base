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

package com.android.server.security.advancedprotection;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.security.advancedprotection.AdvancedProtectionManager;

import android.test.mock.MockContentResolver;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.security.advancedprotection.AdvancedProtectionStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.io.File;

@RunWith(JUnit4.class)
public class AdvancedProtectionStoreTest {
    private static final String PREFERENCE = "advanced_protection_preference";
    private static final int FEATURE_ID =
            AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;
    private static final int DIALOG_TYPE =
            AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION;
    private static final String ADMIN_PREFIX = "feature_admin_provisioned_";
    private static final String ADB_PREFIX = "feature_adb_provisioned_";

    private Context mContext;
    private Context mDeviceContext;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private AdvancedProtectionStore mStore;

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        MockContentResolver contentResolver = new MockContentResolver();
        contentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(contentResolver);
        mDeviceContext = mock(Context.class);
        mSharedPreferences = mock(SharedPreferences.class);
        mEditor = mock(SharedPreferences.Editor.class);
        when(mContext.createDeviceProtectedStorageContext()).thenReturn(mDeviceContext);
        when(mDeviceContext.getSharedPreferences(any(File.class), anyInt()))
                .thenReturn(mSharedPreferences);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putLong(anyString(), anyLong())).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
        when(mEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mEditor);
        when(mEditor.remove(anyString())).thenReturn(mEditor);
        mStore = new AdvancedProtectionStore(mContext);
    }

    @Test
    public void retrieveAdvancedProtectionModeEnabled_returnsFalseByDefault() {
        assertThat(mStore.retrieveAdvancedProtectionModeEnabled()).isFalse();
    }

    @Test
    public void saveAdvancedProtectionModeEnabled_savesToSettings() {
        mStore.saveAdvancedProtectionModeEnabled(true);

        assertThat(mStore.retrieveAdvancedProtectionModeEnabled()).isTrue();
    }

    @Test
    public void saveAdvancedProtectionModeDisabled_savesToSettings() {
        mStore.saveAdvancedProtectionModeEnabled(false);

        assertThat(mStore.retrieveAdvancedProtectionModeEnabled()).isFalse();
    }

    @Test
    public void retrieveUsbDataProtectionEnabled_returnsTrueByDefault() {
        assertThat(mStore.retrieveUsbDataProtectionEnabled()).isTrue();
    }

    @Test
    public void saveUsbDataProtectionDisabled_savesToSettings() {
        mStore.saveUsbDataProtectionEnabled(false);

        assertThat(mStore.retrieveUsbDataProtectionEnabled()).isFalse();
    }

    @Test
    public void saveUsbDataProtectionEnabled_savesToSettings() {
        mStore.saveUsbDataProtectionEnabled(true);

        assertThat(mStore.retrieveUsbDataProtectionEnabled()).isTrue();
    }

    @Test
    public void saveFeatureAdminProvisioningMode_savesToSharedPreferences() {
        mStore.saveFeatureAdminProvisioned(FEATURE_ID, true);

        verify(mEditor).putBoolean(eq(ADMIN_PREFIX + FEATURE_ID), eq(true));
        verify(mEditor).apply();
    }

    @Test
    public void retrieveFeatureAdminProvisioningMode_retrievesFromSharedPreferences() {
        when(mSharedPreferences.contains(eq(ADMIN_PREFIX + FEATURE_ID))).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq(ADMIN_PREFIX + FEATURE_ID), eq(false)))
                .thenReturn(true);

        Boolean provisioningMode = mStore.retrieveFeatureAdminProvisioned(FEATURE_ID);

        verify(mSharedPreferences).getBoolean(eq(ADMIN_PREFIX + FEATURE_ID), eq(false));
        assertThat(provisioningMode).isTrue();
    }

    @Test
    public void saveFeatureAdminProvisioningMode_savesToCache() {
        when(mSharedPreferences.contains(eq(ADMIN_PREFIX + FEATURE_ID))).thenReturn(true);

        mStore.saveFeatureAdminProvisioned(FEATURE_ID, true);

        assertThat(mStore.retrieveFeatureAdminProvisioned(FEATURE_ID)).isTrue();
        verify(mSharedPreferences, never()).getBoolean(eq(ADMIN_PREFIX + FEATURE_ID), eq(true));
    }

    @Test
    public void retrieveFeatureAdminProvisioningMode_retrievesFromCache() {
        when(mSharedPreferences.contains(eq(ADMIN_PREFIX + FEATURE_ID))).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq(ADMIN_PREFIX + FEATURE_ID), eq(false)))
                .thenReturn(true);

        Boolean isProvisioned = mStore.retrieveFeatureAdminProvisioned(FEATURE_ID);
        Boolean isProvisioned2 = mStore.retrieveFeatureAdminProvisioned(FEATURE_ID);

        assertThat(isProvisioned).isTrue();
        assertThat(isProvisioned2).isTrue();
        verify(mSharedPreferences, times(1)).getBoolean(eq(ADMIN_PREFIX + FEATURE_ID), eq(false));
    }

    @Test
    public void retrieveFeatureAdminProvisioningMode_returnsNullIfSharedPreferencesNotSet() {
        when(mSharedPreferences.contains(eq(ADMIN_PREFIX + FEATURE_ID))).thenReturn(false);

        Boolean provisioningMode = mStore.retrieveFeatureAdminProvisioned(FEATURE_ID);

        assertThat(provisioningMode).isNull();
    }

    @Test
    public void saveFeatureAdbProvisioningMode_savesToSharedPreferences() {
        mStore.saveFeatureAdbProvisioned(FEATURE_ID, true);

        verify(mEditor).putBoolean(eq(ADB_PREFIX + FEATURE_ID), eq(true));
        verify(mEditor).apply();
    }

    @Test
    public void retrieveFeatureAdbProvisioningMode_retrievesFromSharedPreferences() {
        when(mSharedPreferences.contains(eq(ADB_PREFIX + FEATURE_ID))).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq(ADB_PREFIX + FEATURE_ID), eq(false)))
                .thenReturn(true);

        Boolean provisioningMode = mStore.retrieveFeatureAdbProvisioned(FEATURE_ID);

        verify(mSharedPreferences).getBoolean(eq(ADB_PREFIX + FEATURE_ID), eq(false));
        assertThat(provisioningMode).isTrue();
    }

    @Test
    public void retrieveFeatureAdbProvisioningMode_returnsNullIfSharedPreferencesNotSet() {
        when(mSharedPreferences.contains(eq(ADB_PREFIX + FEATURE_ID))).thenReturn(false);

        Boolean provisioningMode = mStore.retrieveFeatureAdbProvisioned(FEATURE_ID);

        assertThat(provisioningMode).isNull();
    }

    @Test
    public void saveFeatureAdbProvisioningMode_savesToCache() {
        when(mSharedPreferences.contains(eq(ADB_PREFIX + FEATURE_ID))).thenReturn(true);

        mStore.saveFeatureAdbProvisioned(FEATURE_ID, true);

        assertThat(mStore.retrieveFeatureAdbProvisioned(FEATURE_ID)).isTrue();
        verify(mSharedPreferences, never()).getBoolean(eq(ADB_PREFIX + FEATURE_ID), eq(false));
    }

    @Test
    public void retrieveFeatureAdbProvisioningMode_retrievesFromCache() {
        when(mSharedPreferences.contains(eq(ADB_PREFIX + FEATURE_ID))).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq(ADB_PREFIX + FEATURE_ID), eq(false)))
                .thenReturn(true);

        Boolean isProvisioned = mStore.retrieveFeatureAdbProvisioned(FEATURE_ID);
        Boolean isProvisioned2 = mStore.retrieveFeatureAdbProvisioned(FEATURE_ID);

        assertThat(isProvisioned).isTrue();
        assertThat(isProvisioned2).isTrue();
        verify(mSharedPreferences, times(1)).getBoolean(eq(ADB_PREFIX + FEATURE_ID), eq(false));
    }

    @Test
    public void removeFeatureAdbProvisioning_removesFromSharedPreferences() {
        mStore.removeFeatureAdbProvisioning(FEATURE_ID);

        verify(mEditor).remove(eq(ADB_PREFIX + FEATURE_ID));
        verify(mEditor).apply();
    }

    @Test
    public void saveEnabledChangeTime_savesToSharedPreferences() {
        long testTime = System.currentTimeMillis();
        mStore.saveEnabledChangeTime(testTime);

        verify(mEditor).putLong(eq("enabled_change_time"), eq(testTime));
        verify(mEditor).apply();
    }

    @Test
    public void retrieveEnabledChangeTime_retrievesFromSharedPreferences() {
        long testTime = System.currentTimeMillis();
        when(mSharedPreferences.getLong(eq("enabled_change_time"), eq(-1L))).thenReturn(testTime);

        long retrievedTime = mStore.retrieveEnabledChangeTime();

        assertThat(retrievedTime).isEqualTo(testTime);
    }

    @Test
    public void saveDialogShown_savesToSharedPreferences() {
        mStore.saveDialogShown(FEATURE_ID, DIALOG_TYPE, true, 24);

        verify(mEditor).putInt(eq("last_dialog_feature_id"), eq(FEATURE_ID));
        verify(mEditor).putInt(eq("last_dialog_type"), eq(DIALOG_TYPE));
        verify(mEditor).putBoolean(eq("last_dialog_learn_more_clicked"), eq(true));
        verify(mEditor).putInt(eq("last_dialog_hours_since_enabled"), eq(24));
        verify(mEditor).apply();
    }

    @Test
    public void retrieveLastDialogFeatureId_retrievesFromSharedPreferences() {
        when(mSharedPreferences.getInt(eq("last_dialog_feature_id"), eq(-1)))
                .thenReturn(FEATURE_ID);

        int featureId = mStore.retrieveLastDialogFeatureId();

        assertThat(featureId).isEqualTo(FEATURE_ID);
    }

    @Test
    public void retrieveLastDialogType_retrievesFromSharedPreferences() {
        when(mSharedPreferences.getInt(eq("last_dialog_type"), eq(-1))).thenReturn(DIALOG_TYPE);

        int dialogType = mStore.retrieveLastDialogType();

        assertThat(dialogType).isEqualTo(DIALOG_TYPE);
    }

    @Test
    public void retrieveLastDialogLearnMoreClicked_retrievesFromSharedPreferences() {
        when(mSharedPreferences.getBoolean(eq("last_dialog_learn_more_clicked"), eq(false)))
                .thenReturn(true);

        boolean clicked = mStore.retrieveLastDialogLearnMoreClicked();

        assertThat(clicked).isTrue();
    }

    @Test
    public void retrieveLastDialogHoursSinceEnabled_retrievesFromSharedPreferences() {
        when(mSharedPreferences.getInt(eq("last_dialog_hours_since_enabled"), eq(-1)))
                .thenReturn(24);

        int hours = mStore.retrieveLastDialogHoursSinceEnabled();

        assertThat(hours).isEqualTo(24);
    }
}
