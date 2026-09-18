/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.hint;

import static android.service.personalcontext.hint.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.service.personalcontext.Token;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextHintTest {

    // Tests parceling and unparceling fields on the base ContextHint.
    @Test
    public void testContextHintParcelUnparcelTokensUnique() {
        Token tokenA = new Token();
        Token tokenB = new Token();

        final BundleHint hint = new BundleHint.Builder()
                .addToken(tokenA)
                .addToken(tokenA)
                .addToken(tokenA)
                .addToken(tokenB)
                .addToken(tokenB)
                .build();

        final ContextHint outputHint = assertParcelUnparcel(hint);

        assertThat(outputHint).isInstanceOf(BundleHint.class);
        assertThat(hint.getHintId()).isEqualTo(outputHint.getHintId());
        assertThat(hint.getTokens()).containsExactly(tokenA, tokenB);
        assertThat(hint.getCreationTime()).isGreaterThan(Instant.ofEpochMilli(0));
        assertThat(hint.getCreationTime().toEpochMilli())
                .isEqualTo(outputHint.getCreationTime().toEpochMilli());
    }

    // Tests parceling and unparceling fields on the base ContextHint.
    @Test
    public void testContextHintParcelUnparcelNoTokens() {
        final BundleHint hint = new BundleHint.Builder().build();

        final ContextHint outputHint = assertParcelUnparcel(hint);

        assertThat(outputHint).isInstanceOf(BundleHint.class);
        assertThat(hint.getHintId()).isEqualTo(outputHint.getHintId());
        assertThat(hint.getTokens()).isEmpty();
        assertThat(hint.getCreationTime()).isGreaterThan(Instant.ofEpochMilli(0));
        assertThat(hint.getCreationTime().toEpochMilli())
                .isEqualTo(outputHint.getCreationTime().toEpochMilli());
    }
}
