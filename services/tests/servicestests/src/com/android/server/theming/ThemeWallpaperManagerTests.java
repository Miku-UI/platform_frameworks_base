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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.graphics.Color;
import android.os.Handler;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.wallpaper.WallpaperManagerInternal;
import com.android.systemui.monet.ColorScheme;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ThemeWallpaperManagerTests {

    private static final int USER_ID = 10;

    @Mock
    private WallpaperManagerInternal mWallpaperManagerInternal;
    @Mock
    private WallpaperManagerInternal.ColorsChangedCallbackInternal mMockListener;
    @Mock
    private Handler mMockHandler;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
    }

    @Test
    public void isWallpaperManagerAvailable_withService_returnsTrue() {
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);
        ThemeWallpaperManager manager = new ThemeWallpaperManager();
        assertThat(manager.isWallpaperManagerAvailable()).isTrue();
    }

    @Test
    public void isWallpaperManagerAvailable_nullService_returnsFalse() {
        ThemeWallpaperManager manager = new ThemeWallpaperManager();
        assertThat(manager.isWallpaperManagerAvailable()).isFalse();
    }

    @Test
    public void getWallpaperColors_withService_delegatesToService() {
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);
        ThemeWallpaperManager manager = new ThemeWallpaperManager();
        WallpaperColors expectedColors = new WallpaperColors(Color.valueOf(Color.RED), null, null);

        when(mWallpaperManagerInternal.getWallpaperColors(eq(WallpaperManager.FLAG_SYSTEM),
                eq(USER_ID))).thenReturn(expectedColors);

        WallpaperColors result = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM, USER_ID);

        assertThat(result).isEqualTo(expectedColors);
        verify(mWallpaperManagerInternal).getWallpaperColors(eq(WallpaperManager.FLAG_SYSTEM),
                eq(USER_ID));
    }

    @Test
    public void getWallpaperColors_nullService_returnsNull() {
        ThemeWallpaperManager manager = new ThemeWallpaperManager();

        WallpaperColors result = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM, USER_ID);

        assertThat(result).isNull();
    }

    @Test
    public void getSeedColor_withService_returnsCorrectSeed() {
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);
        ThemeWallpaperManager manager = new ThemeWallpaperManager();
        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.BLUE), null, null);

        when(mWallpaperManagerInternal.getWallpaperColors(eq(WallpaperManager.FLAG_SYSTEM),
                eq(USER_ID))).thenReturn(colors);

        Integer result = manager.getSeedColor(USER_ID);

        assertThat(result).isEqualTo(ColorScheme.getSeedColor(colors));
    }

    @Test
    public void getSeedColor_nullService_returnsNull() {
        ThemeWallpaperManager manager = new ThemeWallpaperManager();

        Integer result = manager.getSeedColor(USER_ID);

        assertThat(result).isNull();
    }

    @Test
    public void addOnColorsChangedListener_withService_delegatesToService() {
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);
        ThemeWallpaperManager manager = new ThemeWallpaperManager();

        manager.addOnColorsChangedListener(mMockListener, mMockHandler);

        verify(mWallpaperManagerInternal).addOnColorsChangedListener(eq(mMockListener),
                eq(mMockHandler));
    }
}
