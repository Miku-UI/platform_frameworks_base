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

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.os.Parcel;
import android.service.personalcontext.RenderToken;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PublishedContextHintTest {
    private static final String SYSTEM_PACKAGE = "android";

    private static void checkPresence(PublishedContextHint signedHint,
            List<ContextHint> hints) {
        final ArraySet<ContextHint> remainingHints = new ArraySet<>(hints);
        final HashSet<PublishedContextHint> attributionHints =
                new HashSet<>(signedHint.getAttributionHints());

        assertThat(remainingHints.size()).isEqualTo(attributionHints.size());
        for (PublishedContextHint targetHint : attributionHints) {
            assertThat(targetHint.getOriginatingPackage()).isEqualTo(SYSTEM_PACKAGE);
            final ContextHint targetContextHint = targetHint.getContextHint();
            final Optional<ContextHint> foundHint = remainingHints.stream().filter(hint ->
                            targetContextHint.getHintId().equals(hint.getHintId()))
                    .findFirst();

            foundHint.ifPresent(remainingHints::remove);
        }

        assertThat(remainingHints).isEmpty();
    }

    @Test
    public void testParcelAndUnparcel() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();
        final RenderToken renderToken1 = new RenderToken(UUID.randomUUID(), null);
        final RenderToken renderToken2 = new RenderToken(UUID.randomUUID(), null);

        final PublishedContextHint signedAttributedHint1 =
                new PublishedContextHint.Builder(attributedHint1, key)
                        .addRenderTokens(List.of(renderToken1, renderToken2))
                        .build();

        final PublishedContextHint signedAttributedHint2 =
                new PublishedContextHint.Builder(attributedHint2, key)
                        .addRenderTokens(List.of(renderToken1, renderToken2))
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(new PublishedContextHintWrapper(
                new PublishedContextHint.Builder(hint, key)
                        .setOriginatingPackage(origin.getPackageName())
                        .addRenderTokens(List.of(renderToken1, renderToken2))
                        .addAttributionHint(signedAttributedHint1)
                        .addAttributionHint(signedAttributedHint2)
                        .build()), 0);

        parcel.setDataPosition(0);

        final PublishedContextHint signedHint =
                parcel.readParcelable(/* loader= */ null, PublishedContextHintWrapper.class)
                        .getPublishedContextHint();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken1, renderToken2);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());

        checkPresence(signedHint, List.of(attributedHint1, attributedHint2));
    }

    @Test
    public void testParcelAndUnparcelWithoutOrigin() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);

        final PublishedContextHint signedAttributedHint1 =
                new PublishedContextHint.Builder(attributedHint1, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final PublishedContextHint signedAttributedHint2 =
                new PublishedContextHint.Builder(attributedHint2, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new PublishedContextHintWrapper(new PublishedContextHint.Builder(hint, key)
                        .addRenderTokens(List.of(renderToken))
                        .addAttributionHint(signedAttributedHint1)
                        .addAttributionHint(signedAttributedHint2)
                        .build()), 0);

        parcel.setDataPosition(0);

        final PublishedContextHint signedHint =
                parcel.readParcelable(/* loader= */ null, PublishedContextHintWrapper.class)
                        .getPublishedContextHint();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(SYSTEM_PACKAGE);

        checkPresence(signedHint, List.of(attributedHint1, attributedHint2));
    }

    @Test
    public void testParcelAndUnparcelWithoutRenderToken() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();

        final PublishedContextHint signedAttributedHint1 =
                new PublishedContextHint.Builder(attributedHint1, key).build();

        final PublishedContextHint signedAttributedHint2 =
                new PublishedContextHint.Builder(attributedHint2, key).build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new PublishedContextHintWrapper(new PublishedContextHint.Builder(hint, key)
                        .setOriginatingPackage(origin.getPackageName())
                        .addAttributionHints(List.of(signedAttributedHint1, signedAttributedHint2))
                        .build()), 0);

        parcel.setDataPosition(0);

        final PublishedContextHint signedHint =
                parcel.readParcelable(/* loader= */ null, PublishedContextHintWrapper.class)
                        .getPublishedContextHint();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).isEmpty();
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());

        checkPresence(signedHint, List.of(attributedHint1, attributedHint2));
    }

    @Test
    public void testParcelAndUnparcelWithoutAttribution() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new PublishedContextHintWrapper(new PublishedContextHint.Builder(hint, key)
                        .setOriginatingPackage(origin.getPackageName())
                        .addRenderTokens(List.of(renderToken))
                        .build()), 0);

        parcel.setDataPosition(0);

        final PublishedContextHint signedHint =
                parcel.readParcelable(/* loader= */ null, PublishedContextHintWrapper.class)
                        .getPublishedContextHint();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(0);
    }

    @Test
    public void testSignatureWrongKey() throws GeneralSecurityException {
        final BundleHint hint = new BundleHint.Builder().build();

        final PublishedContextHint signedHint =
                new PublishedContextHint.Builder(
                        hint, ContextHintTestUtils.generateSignedHintKey()).build();

        assertThat(signedHint.isSignatureValid(ContextHintTestUtils.generateSignedHintKey()))
                .isFalse();
    }

    @Test
    public void testSignatureBadHash() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final BundleHint hint = new BundleHint.Builder().build();

        final Parcel parcel = Parcel.obtain();
        new PublishedContextHint.Builder(hint, key)
                .build()
                .writeToParcel(parcel, 0);

        // Modify hash bytes in parcel.
        parcel.setDataPosition(0);
        final byte[] hash = parcel.createByteArray();

        hash[0] ^= 1;

        parcel.setDataPosition(0);
        parcel.writeByteArray(hash);
        parcel.setDataPosition(0);

        // Re-read the parcel with a bad signature.
        final PublishedContextHint signedHint = new PublishedContextHint(parcel);

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isFalse();
    }
}
