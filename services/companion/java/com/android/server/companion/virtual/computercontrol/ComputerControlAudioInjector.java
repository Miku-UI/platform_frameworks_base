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

import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

import android.annotation.SuppressLint;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Slog;

/**
 * Helper class to inject silence to all audio recorders on the virtual device of the session so
 * they don't access the default device microphones.
 */
final class ComputerControlAudioInjector {
    private static final String TAG = "ComputerControlAudioInjector";
    private static final int SAMPLE_RATE = 48000;
    private static final AudioFormat AUDIO_INJECT_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(ENCODING_PCM_16BIT)
                    .setChannelMask(CHANNEL_OUT_MONO)
                    .build();
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            CHANNEL_OUT_MONO, ENCODING_PCM_16BIT);
    // Time to sleep when the audio buffer is full and not blocked.
    // Calculate the duration of audio held in the buffer in milliseconds.
    // Duration (ms) = (Buffer Size (bytes) * 1000) / (Sample Rate * Channels * Bytes per Sample)
    // Channels = 1 (Mono), Bytes per Sample = 2 (16-bit PCM)
    private static final long SLEEP_MS = (long) BUFFER_SIZE * 1000 / (SAMPLE_RATE * 2);

    private final AudioInjection mAudioInjection;
    private final Thread mAudioInjectionThread;
    private volatile boolean mIsRunning = false;

    @SuppressLint("MissingPermission")
    ComputerControlAudioInjector(VirtualAudioDevice virtualAudioDevice) {
        mAudioInjection = virtualAudioDevice.startAudioInjection(AUDIO_INJECT_FORMAT);
        mAudioInjectionThread = new Thread(() -> {
            Slog.v(TAG, "Audio injection Thread starting.");
            final byte[] buffer = new byte[BUFFER_SIZE]; // empty buffer is silence
            mIsRunning = true;
            while (mIsRunning) {
                try {
                    int ret = mAudioInjection.write(buffer, 0, buffer.length,
                            AudioTrack.WRITE_NON_BLOCKING);
                    if (ret < 0) {
                        mIsRunning = false;
                        Slog.e(TAG, "Error injecting audio data: " + ret);
                        break;
                    }

                    if (ret == 0) {
                        Thread.sleep(SLEEP_MS);
                    }
                } catch (InterruptedException e) {
                    Slog.i(TAG, "Audio injection Thread interrupted. Ignoring.");
                } catch (Exception e) {
                    mIsRunning = false;
                    Slog.e(TAG, "Exception injecting audio data", e);
                }
            }
            Slog.v(TAG, "Audio injection Thread exiting.");
        }, "ComputerSessionAudioInjectionThread");
    }

    void startAudioInjection() {
        mAudioInjectionThread.start();
        mAudioInjection.play();
    }

    void stopAudioInjection() {
        mAudioInjection.stop();
        mIsRunning = false;
        mAudioInjectionThread.interrupt();
        try {
            mAudioInjectionThread.join();
        } catch (InterruptedException e) {
            Slog.e(TAG, "Exception stopping the audio injection Thread.", e);
        }
    }
}
