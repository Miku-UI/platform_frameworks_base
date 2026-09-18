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

package com.android.server.companion.datatransfer.continuity;

import android.annotation.NonNull;
import android.companion.AssociationInfo;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import java.util.Objects;

/**
 * Base class for a feature controller. This class is meant to be instantiated once, and then
 * enabled and disabled as needed. This class manages its own instance of TaskContinuityMessenger.
 */
public abstract class FeatureController implements TaskContinuityMessenger.Listener {

    @GuardedBy("this")
    private boolean mEnabled = false;

    protected final int mUserId;

    protected final TaskContinuityMessenger mTaskContinuityMessenger;

    protected FeatureController(
            int userId, @NonNull TaskContinuityMessenger taskContinuityMessenger) {
        this.mUserId = userId;
        this.mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
    }

    /** Returns true if the feature is enabled. */
    public boolean isEnabled() {
        synchronized (this) {
            return mEnabled;
        }
    }

    /** Enables the feature. If the feature is already enabled, this method does nothing. */
    public void enable() {
        synchronized (this) {
            if (mEnabled) {
                Slog.w(getTag(), "Feature is already enabled");
                return;
            }

            Slog.v(getTag(), "Enabling feature");
            mEnabled = true;
            onEnabled();
            mTaskContinuityMessenger.addListener(this);
            Slog.i(getTag(), "Feature enabled");
        }
    }

    /** Disables the feature. If the feature is already disabled, this method does nothing. */
    public void disable() {
        synchronized (this) {
            if (!mEnabled) {
                Slog.w(getTag(), "Feature is already disabled");
                return;
            }

            Slog.v(getTag(), "Disabling feature");
            mEnabled = false;
            onDisabled();
            mTaskContinuityMessenger.removeListener(this);
            Slog.i(getTag(), "Feature disabled");
        }
    }

    /** Override this method to perform any necessary setup when the feature is enabled. */
    protected void onEnabled() {}

    /** Override this method to perform any necessary cleanup when the feature is disabled. */
    protected void onDisabled() {}

    /** Override this method to return the tag to be used for logging. */
    @NonNull
    protected abstract String getTag();

    /**
     * Called when a TaskStackBroadcastMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param taskStackBroadcastMessage The TaskStackBroadcastMessage message.
     */
    protected void onTaskStackBroadcastMessageReceived(
            int associationId, @NonNull TaskStackBroadcastMessage taskStackBroadcastMessage) {}

    /**
     * Called when a HandoffRequestMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param handoffRequestMessage The HandoffRequestMessage message.
     */
    protected void onHandoffRequestMessageReceived(
            int associationId, @NonNull HandoffRequestMessage handoffRequestMessage) {}

    /**
     * Called when a HandoffRequestResultMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param handoffRequestResultMessage The HandoffRequestResultMessage message.
     */
    protected void onHandoffRequestResultMessageReceived(
            int associationId, @NonNull HandoffRequestResultMessage handoffRequestResultMessage) {}

    /**
     * Called when an association is connected.
     *
     * @param associationInfo The AssociationInfo of the connected device.
     */
    @Override
    public void onAssociationConnected(@NonNull AssociationInfo associationInfo) {}

    /**
     * Called when an association is disconnected.
     *
     * @param associationId The association ID of the disconnected device.
     */
    @Override
    public void onAssociationDisconnected(int associationId) {}

    @Override
    public void onMessageReceived(
            int associationId, @NonNull TaskContinuityMessage taskContinuityMessage) {
        Slog.v(getTag(), "Received message from association " + associationId);
        if (taskContinuityMessage.taskStackBroadcastMessage() != null) {
            onTaskStackBroadcastMessageReceived(
                    associationId, taskContinuityMessage.taskStackBroadcastMessage());
        }

        if (taskContinuityMessage.handoffRequestMessage() != null) {
            onHandoffRequestMessageReceived(
                    associationId, taskContinuityMessage.handoffRequestMessage());
        }

        if (taskContinuityMessage.handoffRequestResultMessage() != null) {
            onHandoffRequestResultMessageReceived(
                    associationId, taskContinuityMessage.handoffRequestResultMessage());
        }
    }
}
