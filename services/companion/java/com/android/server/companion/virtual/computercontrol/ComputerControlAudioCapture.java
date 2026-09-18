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

package com.android.server.companion.virtual.computercontrol;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

import android.annotation.SuppressLint;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Slog;

/**
 * Helper class to capture and consume all audio streams played on the virtual device of the
 * session so they don't interfere with audio playback on the default device context.
 */
final class ComputerControlAudioCapture {
    private static final String TAG = "ComputerControlAudioCapture";
    private static final int SAMPLE_RATE = 48000;
    private static final AudioFormat AUDIO_CAPTURE_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(ENCODING_PCM_16BIT)
                    .setChannelMask(CHANNEL_IN_MONO)
                    .build();
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            CHANNEL_IN_MONO, ENCODING_PCM_16BIT);
    // Time to sleep when nothing is captured and not blocked.
    // Calculate the duration of audio held in the buffer in milliseconds.
    // Duration (ms) = (Buffer Size (bytes) * 1000) / (Sample Rate * Channels * Bytes per Sample)
    // Channels = 1 (Mono), Bytes per Sample = 2 (16-bit PCM)
    private static final long SLEEP_MS = (long) BUFFER_SIZE * 1000 / (SAMPLE_RATE * 2);

    private final AudioCapture mAudioCapture;
    private final Thread mAudioCaptureThread;
    private volatile boolean mIsRunning = false;

    @SuppressLint("MissingPermission")
    ComputerControlAudioCapture(VirtualAudioDevice virtualAudioDevice) {
        mAudioCapture = virtualAudioDevice.startAudioCapture(AUDIO_CAPTURE_FORMAT);
        mAudioCaptureThread = new Thread(() -> {
            Slog.v(TAG, "Audio capture Thread starting.");
            final byte[] buffer = new byte[BUFFER_SIZE];
            mIsRunning = true;
            while (mIsRunning) {
                try {
                    int ret = mAudioCapture.read(buffer, 0, buffer.length,
                            AudioRecord.READ_NON_BLOCKING);
                    if (ret < 0) {
                        mIsRunning = false;
                        Slog.e(TAG, "Error capturing audio data: " + ret);
                        break;
                    }

                    if (ret == 0) {
                        Thread.sleep(SLEEP_MS);
                    }
                } catch (InterruptedException e) {
                    Slog.i(TAG, "Audio capture Thread interrupted. Ignoring.");
                } catch (Exception e) {
                    mIsRunning = false;
                    Slog.e(TAG, "Exception capturing audio data", e);
                }
            }
            Slog.v(TAG, "Audio capture Thread exiting.");
        }, "ComputerSessionAudioCaptureThread");
    }

    void startAudioCapture() {
        mAudioCaptureThread.start();
        mAudioCapture.startRecording();
    }

    void stopAudioCapture() {
        mAudioCapture.stop();
        mIsRunning = false;
        mAudioCaptureThread.interrupt();
        try {
            mAudioCaptureThread.join();
        } catch (InterruptedException e) {
            Slog.e(TAG, "Exception stopping the audio capture Thread.", e);
        }
    }
}
