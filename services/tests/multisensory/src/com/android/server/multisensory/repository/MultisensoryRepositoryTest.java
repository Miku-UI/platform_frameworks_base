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

package com.android.server.multisensory.repository;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.VibratorManager;
import android.os.multisensory.MultisensoryManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.multisensory.repository.sound.MultisensorySoundData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class MultisensoryRepositoryTest {

    private MultisensoryRepository mUnderTest;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        Context testContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VibratorManager vibratorManager = testContext.getSystemService(VibratorManager.class);

        mUnderTest = new MultisensoryRepository();
        mUnderTest.initialize(
                vibratorManager.getDefaultVibrator(), testContext.getContentResolver());
    }

    @Test
    public void getHapticEffect_forAllTokens_getsEffectForAll() {
        int[] tokens = MultisensoryManager.getMultisensoryTokens();
        for (int tokenConstant : tokens) {
            assertNotNull(mUnderTest.getHapticEffect(tokenConstant));
        }
    }

    @Test
    public void getSoundEffect_forAllTokensWithSoundDefined_getsSoundUri() {
        List<Integer> tokens = filterTokensBySoundDefinition(/*shouldNameExist*/ true);
        for (int tokenConstant : tokens) {
            assertNotNull(mUnderTest.getSoundEffect(tokenConstant));
        }
    }

    @Test
    public void getSoundEffect_forAllTokensWithOutSoundDefined_getsNull() {
        List<Integer> tokens = filterTokensBySoundDefinition(/*shouldNameExist*/ false);
        for (int tokenConstant : tokens) {
            assertNull(mUnderTest.getSoundEffect(tokenConstant));
        }
    }

    private List<Integer> filterTokensBySoundDefinition(boolean shouldSoundNameExist) {
        ArrayList<Integer> filteredTokens = new ArrayList<>();
        int[] tokens = MultisensoryManager.getMultisensoryTokens();
        for (int tokenConstant : tokens) {
            boolean isSoundNameSpecified =
                    MultisensorySoundData.TOKEN_SOUND_NAMES.get(tokenConstant) != null;
            boolean add =
                    (isSoundNameSpecified && shouldSoundNameExist)
                            || (!isSoundNameSpecified && !shouldSoundNameExist);
            if (add) {
                filteredTokens.add(tokenConstant);
            }
        }
        return filteredTokens;
    }
}
