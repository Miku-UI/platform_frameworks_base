/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.service.personalcontext.ParcelUtils.roundTripThroughParcel;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HintFilterTest {
    private static final String HINT_CLASS_A =
            "android.service.personalcontext.hint.HintFilterTest.A";
    private static final String HINT_CLASS_B =
            "android.service.personalcontext.hint.HintFilterTest.B";
    private static final String HINT_CLASS_C =
            "android.service.personalcontext.hint.HintFilterTest.C";
    private static final String HINT_CLASS_D =
            "android.service.personalcontext.hint.HintFilterTest.D";
    private static final String HINT_CLASS_E =
            "android.service.personalcontext.hint.HintFilterTest.E";

    private static PublishedContextHint makeHint(String hintClass)
            throws GeneralSecurityException {
        return new PublishedContextHint.Builder(
                new BundleHint.Builder().setHintTypeName(hintClass).build(),
                ContextHintTestUtils.generateSignedHintKey())
            .setOriginatingPackage("personalcontext.cts")
            .build();
    }

    @Test
    public void testHintFilterRequireAll() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_C, HintFilter.FILTER_TYPE_REQUIRED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @Test
    public void testHintFilterRequireSome() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_REQUIRED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }

    @Test
    public void testHintFilterParceling() throws GeneralSecurityException {
        final HintFilter filter = roundTripThroughParcel(new HintFilter.Builder()
                .addBundleHintTypeName("hintTypeName", HintFilter.FILTER_TYPE_ALLOWED)
                .build());

        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(filter, 0);

        final HintFilter filter2 = parcel.readParcelable(HintFilter.class.getClassLoader());
    }


    @Test
    public void testHintFilterRequireMissingSome() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_C, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_D, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_E, HintFilter.FILTER_TYPE_REQUIRED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @Test
    public void testHintFilterRequireNone() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_D, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_E, HintFilter.FILTER_TYPE_REQUIRED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @Test
    public void testHintFilterAllowOne() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_D, HintFilter.FILTER_TYPE_ALLOWED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA);
    }

    @Test
    public void testHintFilterAllowMany() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_C, HintFilter.FILTER_TYPE_ALLOWED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @Test
    public void testHintFilterAllowSome() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_ALLOWED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }

    @Test
    public void testHintFilterMultipleFilterTypesOneHint() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addHintType(BundleHint.class, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addPackage("personalcontext.cts", HintFilter.FILTER_TYPE_REQUIRED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA);
    }

    @Test
    public void testHintFilterMultipleFilterTypesMultipleHints() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                roundTripThroughParcel(new HintFilter.Builder()
                        .addHintType(BundleHint.class, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addPackage("personalcontext.cts", HintFilter.FILTER_TYPE_REQUIRED)
                        .build())
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }
}
