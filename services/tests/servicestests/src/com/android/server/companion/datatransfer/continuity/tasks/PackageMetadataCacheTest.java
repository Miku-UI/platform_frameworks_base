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

package com.android.server.companion.datatransfer.continuity.tasks;

import static com.android.server.companion.datatransfer.contextsync.BitmapUtils.renderDrawableToByteArray;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.android.frameworks.servicestests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import androidx.test.InstrumentationRegistry;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class PackageMetadataCacheTest {

    @Mock private PackageManager mockPackageManager;

    private PackageMetadataCache packageMetadataCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        packageMetadataCache = new PackageMetadataCache(mockPackageManager);
    }

    @Test
    public void testGetMetadataForPackage_returnsMetadata() {
        String packageName = "com.example.app";
        String label = "label";
        Drawable icon = createTestDrawable();
        setupMockApplicationInfo(packageName, label, icon);

        PackageMetadata metadata = packageMetadataCache.getMetadataForPackage(packageName);
        assertThat(metadata.label()).isEqualTo(label);
        assertThat(metadata.icon()).isEqualTo(renderDrawableToByteArray(icon));
    }

    @Test
    public void testGetMetadataForPackage_packageManagerReturnsNullLabel_returnsNull() {
        String packageName = "com.example.app";
        Drawable icon = createTestDrawable();
        setupMockApplicationInfo(packageName, null, icon);
        assertThat(packageMetadataCache.getMetadataForPackage(packageName)).isNull();
    }

    @Test
    public void testGetMetadataForPackage_packageManagerReturnsNullIcon_returnsNull() {
        String packageName = "com.example.app";
        String label = "label";
        setupMockApplicationInfo(packageName, label, null);
        assertThat(packageMetadataCache.getMetadataForPackage(packageName)).isNull();
    }

    @Test
    public void testGetMetadataForPackage_packageManagerThrowsException_returnsNull()
        throws PackageManager.NameNotFoundException {

        when(mockPackageManager.getPackageInfo(anyString(), anyInt()))
            .thenThrow(new PackageManager.NameNotFoundException());

        assertThat(packageMetadataCache.getMetadataForPackage("com.example.app")).isNull();
    }

    private void setupMockApplicationInfo(String packageName, String label, Drawable icon) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.name = packageName;
        try {
            when(mockPackageManager
                    .getPackageInfo(eq(packageName), eq(PackageManager.GET_META_DATA)))
            .thenReturn(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
        }
        when(mockPackageManager.getApplicationLabel(eq(packageInfo.applicationInfo)))
            .thenReturn(label);
        when(mockPackageManager.getApplicationIcon(eq(packageInfo.applicationInfo)))
            .thenReturn(icon);
    }

    private Drawable createTestDrawable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap bitmap = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.black_32x32);
        return new BitmapDrawable(context.getResources(), bitmap);
    }
}