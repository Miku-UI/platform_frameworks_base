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

package android.os;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import perfetto.protos.DataSourceConfigOuterClass;
import perfetto.protos.DebugAnnotationOuterClass;
import perfetto.protos.InternedDataOuterClass;
import perfetto.protos.TraceConfigOuterClass;
import perfetto.protos.TraceOuterClass;
import perfetto.protos.TracePacketOuterClass;
import perfetto.protos.TrackEventConfigOuterClass;
import perfetto.protos.TrackEventOuterClass;

import java.util.List;
import java.util.Set;

/**
 * Tests that we correctly initialize and can use the new SDK when the V3 flag is enabled. The
 * correctness of the V3 API tracing in Framework is tested in {@link
 * PerfettoTraceMessageQueueTest}.
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = PerfettoTrace.class)
@RequiresFlagsEnabled({
    android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2,
    android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V3
})
public class PerfettoTraceV3InitializationTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Set<String> mCategoryNames = new ArraySet<>();
    private final Set<String> mEventNames = new ArraySet<>();
    private final Set<String> mDebugAnnotationNames = new ArraySet<>();

    @BeforeClass
    public static void setUpClass() {
        PerfettoTrace.register(true);
        PerfettoTrace.registerCategories();
    }

    @Before
    public void setUp() {
        mCategoryNames.clear();
        mEventNames.clear();
        mDebugAnnotationNames.clear();
    }

    @Test
    public void testSendMessageQueueCategoryEvent() throws Exception {
        // This asserts that we don't accidentally initialize the old API.
        assertThat(PerfettoTrace.MQ_CATEGORY.isRegistered()).isFalse();
        // This asserts that we correctly initialize the new API.
        assertThat(PerfettoTrace.MQ_CATEGORY_V3.isRegistered()).isTrue();

        com.android.internal.dev.perfetto.sdk.PerfettoTrace.Session session =
                new com.android.internal.dev.perfetto.sdk.PerfettoTrace.Session(
                        true, getTraceConfig("mq").toByteArray());
        com.android.internal.dev.perfetto.sdk.PerfettoTrace.instant(
                        PerfettoTrace.MQ_CATEGORY_V3, "my_event")
                .addArg("string_key", "foo")
                .emit();

        TraceOuterClass.Trace trace = TraceOuterClass.Trace.parseFrom(session.close());

        boolean hasDebugAnnotations = false;

        for (TracePacketOuterClass.TracePacket packet : trace.getPacketList()) {
            if (packet.hasTrackEvent()) {
                TrackEventOuterClass.TrackEvent event = packet.getTrackEvent();
                if (TrackEventOuterClass.TrackEvent.Type.TYPE_INSTANT.equals(event.getType())
                        && event.getDebugAnnotationsCount() == 1) {
                    hasDebugAnnotations = true;
                    List<DebugAnnotationOuterClass.DebugAnnotation> annotations =
                            event.getDebugAnnotationsList();
                    assertThat(annotations.getFirst().getStringValue()).isEqualTo("foo");
                }
            }
            collectInternedData(packet);
        }

        assertThat(hasDebugAnnotations).isTrue();
        assertThat(mCategoryNames).containsExactly("mq");
        assertThat(mEventNames).containsExactly("my_event");
        assertThat(mDebugAnnotationNames).containsExactly("string_key");
    }

    private TraceConfigOuterClass.TraceConfig getTraceConfig(String cat) {
        TraceConfigOuterClass.TraceConfig.BufferConfig bufferConfig =
                TraceConfigOuterClass.TraceConfig.BufferConfig.newBuilder().setSizeKb(1024).build();
        TrackEventConfigOuterClass.TrackEventConfig trackEventConfig =
                TrackEventConfigOuterClass.TrackEventConfig.newBuilder()
                        .addEnabledCategories(cat)
                        .build();
        DataSourceConfigOuterClass.DataSourceConfig dsConfig =
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder()
                        .setName("track_event")
                        .setTargetBuffer(0)
                        .setTrackEventConfig(trackEventConfig)
                        .build();
        TraceConfigOuterClass.TraceConfig.DataSource ds =
                TraceConfigOuterClass.TraceConfig.DataSource.newBuilder()
                        .setConfig(dsConfig)
                        .build();
        TraceConfigOuterClass.TraceConfig traceConfig =
                TraceConfigOuterClass.TraceConfig.newBuilder()
                        .addBuffers(bufferConfig)
                        .addDataSources(ds)
                        .build();
        return traceConfig;
    }

    private void collectInternedData(TracePacketOuterClass.TracePacket packet) {
        if (!packet.hasInternedData()) {
            return;
        }

        InternedDataOuterClass.InternedData data = packet.getInternedData();

        for (TrackEventOuterClass.EventCategory cat : data.getEventCategoriesList()) {
            mCategoryNames.add(cat.getName());
        }
        for (TrackEventOuterClass.EventName ev : data.getEventNamesList()) {
            mEventNames.add(ev.getName());
        }
        for (DebugAnnotationOuterClass.DebugAnnotationName dbg :
                data.getDebugAnnotationNamesList()) {
            mDebugAnnotationNames.add(dbg.getName());
        }
    }
}
