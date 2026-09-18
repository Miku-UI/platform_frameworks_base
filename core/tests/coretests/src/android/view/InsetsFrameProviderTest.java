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

package android.view;


import static android.internal.perfetto.protos.Windowlayoutparams.WindowLayoutParamsProto.PROVIDED_INSETS;
import static android.server.wm.ProtoExtractors.extract;
import static android.view.InsetsFrameProvider.SOURCE_ARBITRARY_RECTANGLE;
import static android.view.WindowInsets.Type.statusBars;

import static org.junit.Assert.assertEquals;

import android.graphics.Insets;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.util.proto.ProtoOutputStream;
import android.view.WindowManager.LayoutParams;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import perfetto.protos.Rect.RectProto;
import perfetto.protos.Windowlayoutparams;

/**
 * Tests for {@link InsetsFrameProvider}.
 *
 * <p>Build/Install/Run:
 *  atest FrameworksCoreTests:InsetsFrameProviderTest
 * <p>This test class is a part of Window Manager Service tests and specified in
 * {@link com.android.server.wm.test.filters.FrameworksTestsFilter}.
 */

@Presubmit
@RunWith(AndroidJUnit4.class)
public class InsetsFrameProviderTest {

    @Test
    public void testDumpDebug() throws InvalidProtocolBufferException {
        final InsetsFrameProvider provider = new InsetsFrameProvider(null, 0, statusBars())
                .setSource(SOURCE_ARBITRARY_RECTANGLE)
                .setArbitraryRectangle(new Rect(1, 2, 3, 4))
                .setInsetsSize(Insets.of(5, 6, 7, 8))
                .setInsetsSizeOverrides(new InsetsFrameProvider.InsetsSizeOverride[] {
                        new InsetsFrameProvider.InsetsSizeOverride(LayoutParams.TYPE_APPLICATION,
                                Insets.of(9, 10, 11, 12))})
                .setMinimalInsetsSizeInDisplayCutoutSafe(Insets.of(13, 14, 15, 16))
                .setBoundingRects(new Rect[] { new Rect(17, 18, 19, 20) });

        final ProtoOutputStream proto = new ProtoOutputStream();
        provider.dumpDebug(proto, PROVIDED_INSETS);

        final List<Windowlayoutparams.InsetsFrameProviderProto> providerProtoList =
                Windowlayoutparams.WindowLayoutParamsProto.parseFrom(
                        proto.getBytes()).getProvidedInsetsList();

        assertEquals(1, providerProtoList.size());
        final Windowlayoutparams.InsetsFrameProviderProto providerProto =
                providerProtoList.getFirst();
        assertEquals(provider.getSource(), providerProto.getSource());
        assertEquals(provider.getArbitraryRectangle(),
                extract(providerProto.getArbitraryRectangle()));
        assertEquals(provider.getInsetsSize(), extract(providerProto.getInsetsSize()));
        assertEquals(1, providerProto.getInsetsSizeOverrideCount());
        final Windowlayoutparams.InsetsSizeOverrideProto insetsSizeOverrideProto =
                providerProto.getInsetsSizeOverrideList().getFirst();
        assertEquals(provider.getInsetsSizeOverrides()[0].getInsetsSize(),
                extract(insetsSizeOverrideProto.getInsetsSize()));
        assertEquals(provider.getMinimalInsetsSizeInDisplayCutoutSafe(),
                extract(providerProto.getMinimalInsetsSizeInDisplayCutoutSafe()));
        assertEquals(1, providerProto.getBoundingRectsCount());
        final RectProto boundingRectProto = providerProto.getBoundingRectsList().getFirst();
        assertEquals(provider.getBoundingRects()[0], extract(boundingRectProto));
    }
}
