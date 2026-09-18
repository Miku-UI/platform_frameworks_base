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

package com.android.server.job;

import android.os.PerfettoCategories;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.dev.perfetto.sdk.PerfettoTrackEventBuilder;

/**
 * Wrapper class for Perfetto tracing to abstract away the v3 and non-v3 APIs.
 */
public abstract class JobPerfettoTracer {
    /**
     * The field ID for the JobSchedulerJob message in the Perfetto trace.
     * See {@code external/perfetto/protos/perfetto/trace/android/android_track_event.proto}.
     */
    static final long JOB_SCHEDULER_JOB_FIELD_ID = 2006L;

    /**
     * Creates a new instance of the appropriate {@link JobPerfettoTracer} implementation.
     */
    public static JobPerfettoTracer create() {
        return new JobPerfettoTracer.JobPerfettoTracerV3();
    }

    /**
     * Starts a new instant event trace for a job.
     *
     * @param eventName The name of the event.
     * @return this The builder to add fields to the trace.
     */
    public abstract JobPerfettoTracer startEvent(String eventName);

    /**
     * Adds a field to the trace event.
     *
     * @param fieldId The ID of the field from {@code
     *     external/perfetto/protos/perfetto/trace/android/android_track_event.proto}.
     * @param value The value of the field.
     * @return this The builder to add more fields to the trace.
     */
    public abstract JobPerfettoTracer addField(long fieldId, long value);

    /**
     * Emits the trace event to the Perfetto buffer.
     */
    public abstract void emit();

    /**
     * Returns whether the JobScheduler Perfetto category is enabled.
     */
    @VisibleForTesting
    protected boolean isTraceEnabled() {
        if (android.os.Flags.perfettoSdkTracingV3()) {
            return PerfettoCategories.JOB_SCHEDULER_CATEGORY.isEnabled();
        }
        return false;
    }

    static final class JobPerfettoTracerV3 extends JobPerfettoTracer {
        private PerfettoTrackEventBuilder mBuilder;

        @Override
        public JobPerfettoTracer startEvent(String eventName) {
            mBuilder =
                    com.android.internal.dev.perfetto.sdk.PerfettoTrace.instant(
                                    PerfettoCategories.JOB_SCHEDULER_CATEGORY,
                                    eventName)
                            .beginProto()
                            .beginNested(JOB_SCHEDULER_JOB_FIELD_ID);
            return this;
        }

        @Override
        public JobPerfettoTracer addField(long fieldId, long value) {
            mBuilder.addField(fieldId, value);
            return this;
        }

        @Override
        public void emit() {
            mBuilder.endNested().endProto().emit();
        }
    }
}
