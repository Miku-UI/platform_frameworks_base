/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.companion.transport;

import android.annotation.NonNull;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Slog;

import libcore.io.IoUtils;
import libcore.io.Streams;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class RawTransport extends Transport {
    private volatile boolean mStopped;

    RawTransport(int associationId, ParcelFileDescriptor fd, Context context) {
        super(associationId, fd, context);
    }

    @Override
    void start() {
        if (DEBUG) {
            Slog.d(TAG, "Starting raw transport.");
        }
        mStopped = false;

        // Start sending enqueued messages
        new Thread(() -> {
            try {
                while (!mStopped) {
                    sendMessage(mMessageQueue.take());
                }
            } catch (Exception e) {
                if (!mStopped) {
                    Slog.e(TAG, "Trouble during transport.", e);
                    eventCallback(translateError(e));
                    close();
                }
            }
        }).start();

        // Start listening to incoming messages
        new Thread(() -> {
            try {
                while (!mStopped) {
                    receiveMessage();
                }
            } catch (IOException e) {
                if (!mStopped) {
                    Slog.w(TAG, "Trouble during transport", e);
                    eventCallback(translateError(e));
                    close();
                }
            }
        }).start();
    }

    @Override
    void stop() {
        if (DEBUG) {
            Slog.d(TAG, "Stopping raw transport.");
        }
        mStopped = true;
    }

    @Override
    void close() {
        stop();

        if (DEBUG) {
            Slog.d(TAG, "Closing raw transport.");
        }
        IoUtils.closeQuietly(mRemoteIn);
        IoUtils.closeQuietly(mRemoteOut);

        super.close();
    }

    @Override
    public byte[] getSessionKey() {
        return "CDM_TRANSPORT".getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public String getSessionRole() {
        return "PARTICIPANT";
    }

    private void sendMessage(@NonNull byte[] data)
            throws IOException {
        synchronized (mRemoteOut) {
            mRemoteOut.write(data);
            mRemoteOut.flush();
        }
    }

    private void receiveMessage() throws IOException {
        synchronized (mRemoteIn) {
            final byte[] headerBytes = new byte[HEADER_LENGTH];
            Streams.readFully(mRemoteIn, headerBytes);
            final ByteBuffer header = ByteBuffer.wrap(headerBytes);
            final int message = header.getInt();
            final int sequence = header.getInt();
            final int length = header.getInt();
            final byte[] data = new byte[length];
            Streams.readFully(mRemoteIn, data);

            handleMessage(message, sequence, data);
        }
    }

    @Override
    public String toString() {
        return "RawTransport{"
                + "mAssociationId=" + mAssociationId
                + '}';
    }
}
