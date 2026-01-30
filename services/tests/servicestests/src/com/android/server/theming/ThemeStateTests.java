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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.testing.AndroidTestingRunner;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidTestingRunner.class)
public class ThemeStateTests {
    private static final float CONTRAST_DEFAULT = 0.0f;
    private static final float CONTRAST_HIGH = 1.0f;

    private static final int SEED_COLOR_VALID = Color.BLUE;
    private static final int SEED_COLOR_RED = Color.RED;

    private static final Integer STYLE_VALID = ThemeStyle.TONAL_SPOT;
    private static final int STYLE_EXPRESSIVE = ThemeStyle.EXPRESSIVE;

    private static final int USER_ID = 0;

    private ThemeState mState;

    @Before
    public void setUp() {
        mState = new ThemeState(USER_ID, false, SEED_COLOR_VALID, CONTRAST_DEFAULT,
                STYLE_VALID, Collections.emptySet(), 0);
    }

    @Test
    public void testWithSeedColor() {
        ThemeState originalState = mState;
        mState = mState.withSeedColor(SEED_COLOR_RED);
        assertEquals(SEED_COLOR_RED, mState.seedColor());
        Truth.assertThat(mState.seedColor()).isEqualTo(SEED_COLOR_RED);
        verifyImmutability(originalState);
    }

    @Test
    public void testWithStyle() {
        ThemeState originalState = mState;
        mState = mState.withStyle(STYLE_EXPRESSIVE);
        assertEquals(STYLE_EXPRESSIVE, mState.style());
        verifyImmutability(originalState);
    }

    @Test
    public void testWithContrast() {
        ThemeState originalState = mState;
        mState = mState.withContrast(CONTRAST_HIGH);
        assertEquals(CONTRAST_HIGH, mState.contrast(), 0.001f);
        verifyImmutability(originalState);
    }

    @Test
    public void testWithSetupComplete() {
        ThemeState originalState = mState;
        mState = mState.withSetupComplete();
        assertTrue(mState.isSetup());
        verifyImmutability(originalState);
    }

    @Test
    public void testAddProfile() {
        ThemeState originalState = mState;
        mState = mState.addProfile(11);
        assertTrue(mState.childProfiles().contains(11));
        verifyImmutability(originalState);
    }

    @Test
    public void testWithTimeStamp() {
        ThemeState originalState = mState;
        mState = mState.withTimeStamp();
        assertNotEquals(originalState.timeStamp(), mState.timeStamp());
        verifyImmutability(originalState);
    }

    private void verifyImmutability(ThemeState originalState) {
        assertNotEquals(originalState, mState); // Verify immutability
    }
}

