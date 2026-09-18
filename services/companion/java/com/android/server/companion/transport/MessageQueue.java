/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.companion.CompanionDeviceManager.MessageType;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Thin wrapper around a basic blocking message queue that waits until contents are available.
 */
public class MessageQueue {
    private static final int DEFAULT_MAX_QUEUE_SIZE = 500;

    private final BlockingQueue<byte[]> mRequestQueue;

    public MessageQueue() {
        this(DEFAULT_MAX_QUEUE_SIZE);
    }

    public MessageQueue(int capacity) {
        mRequestQueue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Blocking operation that removes the first item in the message queue if present. Blocks the
     * thread if there is no item present in the queue.
     *
     * @return first item in the queue.
     * @throws InterruptedException if the thread waiting for the first item is interrupted.
     */
    public byte[] take() throws InterruptedException {
        return mRequestQueue.take();
    }

    /**
     * Non-blocking operation that adds an item to the back of the queue. Throws if the queue is at
     * its maximum capacity.
     *
     * <p>If the message type is restricted, then the message will be instead enqueued into a
     * separate queue that only gets added to the main message queue after the restriction is
     * lifted.</p>
     *
     * @param messageType Type of message being enqueued.
     * @param message Message to enqueue.
     */
    public void add(@MessageType int messageType, byte[] message) {
        mRequestQueue.add(message);
    }
}
