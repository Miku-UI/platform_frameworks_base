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

package android.os.vibrator;

/**
 * Represents a single active haptic-to-PCM conversion stream.
 * @hide
 */
interface IHapticChannelStream {
    // The end of the stream has been reached.
    const int READ_STATUS_EOF = -1;
    // An I/O error occurred in the underlying native stream.
    const int READ_STATUS_ERROR_IO = -5; // Corresponds to -EIO
    // The operation was attempted on a closed stream or session
    const int READ_STATUS_ERROR_CLOSED = -32; // Corresponds to -EPIPE

    /**
     * Reads haptic PCM data into a portion of a byte array. This method will
     * block until data is available, an I/O error occurs, or the end of the
     * stream is reached.
     *
     * @param buffer The buffer into which the data is read. The service will
     *               write data into this buffer.
     * @return The total number of bytes read into the buffer, or -1 if there
     *         is no more data because the end of the stream has been reached.
     */
    @EnforcePermission("USE_VIBRATOR_HAPTIC_GENERATOR")
    int read(inout byte[] buffer);

    /**
     * Closes this stream and releases any system resources associated with it.
     *
     * @return True if the stream was successfully closed. False otherwise.
     */
    @EnforcePermission("USE_VIBRATOR_HAPTIC_GENERATOR")
    boolean close();
}