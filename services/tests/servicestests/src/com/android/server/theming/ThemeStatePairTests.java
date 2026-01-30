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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.theming.ThemeStyle;
import android.graphics.Color;

import androidx.test.runner.AndroidJUnit4;

import com.android.systemui.monet.ColorScheme;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeStatePairTests {
    private static final float CONTRAST_DEFAULT = 0.0f;
    private static final float CONTRAST_MEDIUM = 0.5f;

    private static final int SEED_COLOR_VALID = Color.BLUE;
    private static final int SEED_COLOR_RED = Color.RED;

    private static final int STYLE_VALID = ThemeStyle.TONAL_SPOT;

    private static final int USER_ID = 0;

    private ThemeStatePair mStatePair = new ThemeStatePair(USER_ID, true, SEED_COLOR_VALID,
            CONTRAST_DEFAULT,
            STYLE_VALID);

    @Test
    public void testShouldUpdateOverlays_noChanges() {
        assertFalse(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_onlyTimeStampChanges() {
        mStatePair.forceUpdate();
        assertFalse(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_seedColorChanges() {
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_contrastChanges() {
        mStatePair.applyContrast(CONTRAST_MEDIUM);
        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_styleChanges() {
        mStatePair.applyStyle(ThemeStyle.EXPRESSIVE);
        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_everythingChanges() {
        mStatePair.applyStyle(ThemeStyle.EXPRESSIVE);
        mStatePair.applyContrast(CONTRAST_MEDIUM);
        mStatePair.applySeedColor(SEED_COLOR_RED);

        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testSetDeferUpdatesOnLock() {
        mStatePair.setDeferUpdatesOnLock(true);
        assertTrue(mStatePair.areUpdatesDeferredOnLock());

        mStatePair.setDeferUpdatesOnLock(false);
        assertFalse(mStatePair.areUpdatesDeferredOnLock());
    }

    @Test
    public void testUpdate() {
        ColorScheme darkScheme = mStatePair.generatePendingScheme(true);
        ColorScheme lightScheme = mStatePair.generatePendingScheme(false);

        mStatePair.applySeedColor(SEED_COLOR_RED);
        mStatePair.update(darkScheme, lightScheme);

        assertEquals(SEED_COLOR_RED, mStatePair.getCurrentState().seedColor());
        assertFalse(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdate_differentTimestamp_shouldUpdate() {
        mStatePair.forceUpdate();
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_sameState_shouldNotUpdate() {
        assertFalse(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_userNotSetup_shouldNotUpdate() {
        mStatePair = new ThemeStatePair(USER_ID, false, SEED_COLOR_VALID, CONTRAST_DEFAULT,
                STYLE_VALID);
        assertFalse(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_backgroundChangesDeferred_shouldNotUpdate() {
        mStatePair.setDeferUpdatesOnLock(true);
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertFalse(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_backgroundChangesDeferredButForced_shouldUpdate() {
        mStatePair.setDeferUpdatesOnLock(true);
        mStatePair.forceUpdate(); // Force the update by changing the timestamp
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_overlaysShouldUpdate_shouldUpdate() {
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_noChanges_shouldUpdate() {
        mStatePair.forceUpdate(); // This ensures the timestamps are different
        assertTrue(mStatePair.shouldUpdate());
    }
}

