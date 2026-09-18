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

package android.service.personalcontext.insight;

import static com.google.common.truth.Truth.assertThat;

import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.HintInvalidationHint;
import android.service.personalcontext.hint.PublishedContextHint;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HintInvalidationInsightTest {

    @Test
    public void testInvalidatesCorrectHint() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final PublishedContextHint signedHint =
                new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                        .setOriginatingPackage("packageA")
                        .build();

        final HintInvalidationHint invalidationHint =
                new HintInvalidationHint.Builder(signedHint.getContextHint()).build();

        final PublishedContextHint signedInvalidationHint =
                new PublishedContextHint.Builder(invalidationHint, key)
                        .setOriginatingPackage("packageA")
                        .build();

        HintInvalidationInsight invalidationInsight =
                new HintInvalidationInsight.Builder(signedInvalidationHint).build();

        assertThat(invalidationInsight.getInvalidatedHintId()).isEqualTo(
                signedHint.getContextHint().getHintId());

        assertThat(invalidationInsight.isHintInvalidated(signedHint)).isTrue();
    }

    @Test
    public void testDoesNotInvalidateIncorrectHint() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final PublishedContextHint signedHint1 =
                new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                        .setOriginatingPackage("packageA")
                        .build();

        final PublishedContextHint signedHint2 =
                new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                        .setOriginatingPackage("packageA")
                        .build();

        final HintInvalidationHint invalidationHint =
                new HintInvalidationHint.Builder(signedHint1.getContextHint()).build();

        final PublishedContextHint signedInvalidationHint =
                new PublishedContextHint.Builder(invalidationHint, key)
                        .setOriginatingPackage("packageA")
                        .build();

        HintInvalidationInsight invalidationInsight =
                new HintInvalidationInsight.Builder(signedInvalidationHint).build();

        assertThat(invalidationInsight.getInvalidatedHintId()).isNotEqualTo(
                signedHint2.getContextHint().getHintId());

        assertThat(invalidationInsight.isHintInvalidated(signedHint2)).isFalse();
    }

    @Test
    public void testDoesNotInvalidateIncorrectPackage() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final PublishedContextHint signedHint =
                new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                        .setOriginatingPackage("packageA")
                        .build();

        final HintInvalidationHint invalidationHint =
                new HintInvalidationHint.Builder(signedHint.getContextHint()).build();

        final PublishedContextHint signedInvalidationHint =
                new PublishedContextHint.Builder(invalidationHint, key)
                        .setOriginatingPackage("packageB")
                        .build();

        HintInvalidationInsight invalidationInsight =
                new HintInvalidationInsight.Builder(signedInvalidationHint).build();

        assertThat(invalidationInsight.getInvalidatedHintId()).isEqualTo(
                signedHint.getContextHint().getHintId());

        assertThat(invalidationInsight.isHintInvalidated(signedHint)).isFalse();
    }
}
