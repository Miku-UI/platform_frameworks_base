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
 * Holds calls stats about a particular binder API.
 *
 * @hide
 */
parcelable BinderCallsStats {
    /** The worksource UID if known, otherwise the calling process UID. */
    int clientUid;

    /** The interface descriptor of the binder call. */
    String interfaceDescriptor;

    /** The aidl method name of the binder call or "#<transaction code>" if unknown. */
    String aidlMethod;

    /** The number of calls received since last report. */
    long callCount;

    /** The total duration for executing the calls since last report. */
    long durationSumMicros;

    /** Duration stats for low-rate spam since last report. Only reported for the AIDL
     *  targets for which call stats are collected. */
    int secondsWithAtLeast10Calls;
    int secondsWithAtLeast50Calls;

    /** Sum of squared call duration in microseconds for the sampled calls. */
    long callDurationSumSquaredMicros;

    /**
     * Number of calls for which CPU time was sampled.
     * CPU time is sampled because it is expensive to collect.
     */
    long cpuTimeCount;
    /** Total CPU time in microseconds for the sampled calls. */
    long cpuTimeSumMicros;
    /** Sum of squared CPU time in microseconds for the sampled calls. */
    long cpuTimeSumSquaredMicros;
}
