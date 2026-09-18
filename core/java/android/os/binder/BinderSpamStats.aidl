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
 * Holds spam stats about a particular AIDL API.
 *
 * @hide
 */
parcelable BinderSpamStats {
    /** The worksource UID if known, otherwise the calling process UID. */
    int clientUid;

    /** The interface descriptor of the binder call. */
    String interfaceDescriptor;

    /** The aidl method name of the binder call or "#<transaction code>" if unknown. */
    String aidlMethod;

    /** New seconds of spam since last report. Intended for SUM aggregation. */
    int secondsWithAtLeast125Calls;
    int secondsWithAtLeast250Calls;

    /** The highest observered call count over a one second period. */
    int peakCallCountPerSecond;
}
