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
package android.proximity;

/** @hide */
@Backing(type="int")
enum ProximityResultCode {
    /** Indicates a successful operation. */
    SUCCESS = 0,
    /** The device is seen in the discovery process. */
    OUT_OF_RANGE = 1,
    /** The device has no associated devices. */
    NO_ASSOCIATED_DEVICE = 2,
    /** The primary device Bluetooth adapter is turned off by the user. */
    PRIMARY_DEVICE_BT_ADAPTER_OFF = 3,
    /** The device has no connected associated device. */
    NO_CONNECTED_ASSOCIATED_DEVICE = 4,
    /** Ranging timed out with no associated device found. */
    NO_RANGING_RESULT = 5,
    /** Indicates that the associated device did not respond. */
    REQUEST_TIMED_OUT = 6,
    /** An associated device was found but is not on-wrist and unlocked. */
    ASSOCIATED_DEVICE_NOT_ELIGIBLE = 7,
    /**
       Indicates that there is no matching ranging method between primary device and associated
       device.
     */
    INVALID_RANGING_METHODS = 8,
    /** Selected ranging method is not available in the country code. */
    RANGING_RESTRICTED_AVAILABILITY = 9,
    /** Some ranging methods may be disabled due to airplane mode. */
    RANGING_RESTRICTED_AIRPLANE_MODE = 10,
    /** User turned off ranging method on primary device. */
    PRIMARY_DEVICE_RANGING_TURNED_OFF = 11,
    /** Ranging could not start on primary device. May succeed on a retry. */
    PRIMARY_DEVICE_RANGING_FAILED_TO_START = 12,
    /** Ranging already running on primary device. */
    PRIMARY_DEVICE_RANGING_ALREADY_RUNNING = 13,
    /** Max retry reached on primary device. */
    PRIMARY_DEVICE_MAX_RETRY_REACHED = 14,
    /** Ranging is unavailable on primary device. */
    PRIMARY_DEVICE_RANGING_UNAVAILABLE = 15,
    /** Ranging could not start on associated device. May succeed on a retry. */
    ASSOCIATED_DEVICE_RANGING_FAILED_TO_START = 16,
    /** Ranging already running on associated device. */
    ASSOCIATED_DEVICE_RANGING_ALREADY_RUNNING = 17,
    /** User turned off ranging method on associated device. */
    ASSOCIATED_DEVICE_RANGING_TURNED_OFF = 18,
    /** Max retry reached on associated device. */
    ASSOCIATED_DEVICE_MAX_RETRY_REACHED = 19,
    /** Ranging is unavailable on associated device. */
    ASSOCIATED_DEVICE_RANGING_UNAVAILABLE = 20,
    /** Request contains invalid parameters. */
    INVALID_PARAMETERS = 21,
    /** An ongoing request was cancelled. */
    REQUEST_CANCELLED = 22,
    /** Unknown error. */
    UNKNOWN = 23,
    /** Requested ranging is not supported on the primary device. */
    PRIMARY_DEVICE_RANGING_NOT_SUPPORTED = 24,
    /** Requested ranging is not supported on the associated device. */
    ASSOCIATED_DEVICE_RANGING_NOT_SUPPORTED = 25,
    /**
       Failed precondition i.e. device not on-body or not locked or ranging method is turned off,
       as reported by the associated device.
    */
    ASSOCIATED_DEVICE_FAILED_PRECONDITION = 26,
}
