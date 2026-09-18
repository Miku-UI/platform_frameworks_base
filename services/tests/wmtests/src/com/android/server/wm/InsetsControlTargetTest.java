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

package com.android.server.wm;

import static android.internal.perfetto.protos.Windowmanagerservice.InsetsPolicyProto.FAKE_NAV_CONTROL_TARGET;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;
import android.util.proto.ProtoOutputStream;
import android.view.WindowInsets;

import androidx.test.filters.SmallTest;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;
import org.junit.runner.RunWith;

import perfetto.protos.Windowmanagerservice;

/**
 * Tests for the {@link InsetsControlTarget} class.
 *
 * Build/Install/Run:
 *  atest WmTests:InsetsControlTargetTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class InsetsControlTargetTest extends WindowTestsBase {

    @Test
    public void testDumpDebug() throws InvalidProtocolBufferException {
        final var controlTarget = new InsetsControlTarget() {
            @Override
            public int getAnimatingTypes() {
                return WindowInsets.Type.statusBars();
            }

            @Override
            public int getRequestedVisibleTypes() {
                return WindowInsets.Type.defaultVisible();
            }
        };

        final ProtoOutputStream proto = new ProtoOutputStream();

        controlTarget.dumpDebug(proto, FAKE_NAV_CONTROL_TARGET);

        final Windowmanagerservice.InsetsControlTargetProto controlTargetProto =
                Windowmanagerservice.InsetsPolicyProto.parseFrom(
                        proto.getBytes()).getFakeNavControlTarget();
        assertEquals(controlTarget.getAnimatingTypes(), controlTargetProto.getAnimatingTypes());
        assertEquals(controlTarget.getRequestedVisibleTypes(),
                controlTargetProto.getRequestedVisibleTypes());
    }
}
