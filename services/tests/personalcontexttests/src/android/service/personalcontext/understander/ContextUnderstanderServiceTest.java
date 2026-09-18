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

package android.service.personalcontext.understander;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;
import android.service.personalcontext.testutil.FakeExecutor;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextUnderstanderServiceTest {
    private final FakeExecutor mFakeExecutor = new FakeExecutor();
    @Mock
    private IRefineCallback mIRefineCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOnUnderstandList() throws RemoteException, GeneralSecurityException {
        final PublishedContextHint hint1 =
                new PublishedContextHint.Builder(
                        new BundleHint.Builder().build(),
                        ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final PublishedContextHint hint2 =
                new PublishedContextHint.Builder(
                        new BundleHint.Builder().build(),
                        ContextHintTestUtils.generateSignedHintKey())
                        .build();

        final List<PublishedContextHint> hints = Arrays.asList(hint1, hint2);

        final BundleInsight generatedInsight = new BundleInsight.Builder().build();

        final ArrayList<PublishedContextHint> capturedHints = new ArrayList<>();
        final UUID componentId = UUID.randomUUID();
        final ContextUnderstanderService service =
                new ContextUnderstanderService() {
                    @NonNull
                    @Override
                    public HintFilter onInitializeFilter() {
                        return new HintFilter.Builder().build();
                    }

                    @Override
                    public List<ContextInsight> onUnderstand(
                            @NonNull List<PublishedContextHint> hints) {
                        capturedHints.addAll(hints);
                        return List.of(generatedInsight);
                    }
                };
        service.setExecutor(mFakeExecutor);

        final IRefiner refiner = (IRefiner) service.onBind(null);
        final IOpCallback opCallback = mock(IOpCallback.Stub.class);

        refiner.refine(
                new ParcelUuid(componentId),
                PublishedContextHintWrapper.wrapList(hints),
                mIRefineCallback,
                opCallback);

        mFakeExecutor.runAll();
        verify(opCallback).signalCompletion();
        assertThat(capturedHints).containsExactlyElementsIn(hints);

        ArgumentCaptor<List<ContextInsightWrapper>> insightCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mIRefineCallback).onUnderstood(insightCaptor.capture());

        assertThat(insightCaptor.getValue()).hasSize(1);
        assertThat(insightCaptor.getValue().getFirst().getContextInsight())
                .isEqualTo(generatedInsight);
    }
}
