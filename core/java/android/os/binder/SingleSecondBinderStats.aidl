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

package android.os.binder;

/**
 * Contains detailed statistics for a specific binder API for one second.
 *
 * @hide
 */
parcelable SingleSecondBinderStats {
    /** Interface descriptor of the binder call. */
    String interfaceDescriptor;

    /** Aidl method name of the binder call or "#<transaction code>". */
    String aidlMethod;

    /** The UID of the service being called. */
    int clientUid;

    /** Total number of calls. */
    int callCount;

    /** The number of calls for which duration was measured. */
    int durationCount;

    /** Total execution duration for the measured calls. */
    int durationMicrosSum;

    /** The sum of squared microsecond durations of the measured calls. */
    long durationMicrosSquaredSum;

    /**
     * Number of calls for which CPU time was measured.
     * CPU time is sampled because it is expensive to collect.
     */
    int cpuTimeCount;

    /** The sum of microsecond durations of the measured CPU time. */
    int cpuTimeMicrosSum;

    /** The sum of squared microsecond durations of the measured CPU time. */
    long cpuTimeMicrosSquaredSum;

    /**
     * The latency data. Each data point is the duration of a call
     * represented using a logarithmic scale.
     * There should be exactly durationCount number of data points
     * and the value should lie between [0, 99]
     */
    byte[] durationBinIndices;
}
