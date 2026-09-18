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
package com.android.server.companion.datatransfer.crossdevicesync.common;

import android.annotation.UserIdInt;
import android.companion.ActionRequest;
import android.companion.ActionResult;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.CompanionDeviceManager.OnAssociationsChangedListener;
import android.companion.DevicePresenceEvent;
import android.os.PersistableBundle;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** A testable interface wrapping {@link CompanionDeviceManager}. */
public interface CompanionDeviceManagerProxy {

    /** See {@link CompanionDeviceManager#getAllAssociations(int)}. */
    List<AssociationInfo> getAllAssociations(@UserIdInt int userId);

    /** See {@link CompanionDeviceManager#getAllAssociationsWithTransports()}. */
    List<AssociationInfo> getAllAssociationsWithTransports();

    /**
     * See {@link CompanionDeviceManager#addOnAssociationsChangedListener(Executor,
     * OnAssociationsChangedListener, int)}.
     */
    void addOnAssociationsChangedListener(
            Executor executor, OnAssociationsChangedListener listener, @UserIdInt int userId);

    /**
     * See {@link
     * CompanionDeviceManager#removeOnAssociationsChangedListener(OnAssociationsChangedListener)}.
     */
    void removeOnAssociationsChangedListener(OnAssociationsChangedListener listener);

    /** See {@link CompanionDeviceManager#addOnTransportsChangedListener(Executor, Consumer)}. */
    void addOnTransportsChangedListener(
            Executor executor, Consumer<List<AssociationInfo>> listener);

    /** See {@link CompanionDeviceManager#removeOnTransportsChangedListener(Consumer)}. */
    void removeOnTransportsChangedListener(Consumer<List<AssociationInfo>> listener);

    /**
     * See {@link CompanionDeviceManager#setOnDevicePresenceEventListener(int[], String, Executor,
     * Consumer)}.
     */
    void setOnDevicePresenceEventListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            Consumer<DevicePresenceEvent> listener);

    /** See {@link CompanionDeviceManager#removeOnDevicePresenceEventListener(String)}. */
    void removeOnDevicePresenceEventListener(String serviceName);

    /** See {@link CompanionDeviceManager#sendMessage(int, byte[], int[])}. */
    void sendMessage(int messageType, byte[] data, int[] associationIds);

    /**
     * See {@link CompanionDeviceManager#addOnMessageReceivedListener(Executor, int, BiConsumer)}.
     */
    void addOnMessageReceivedListener(
            Executor executor, int messageType, BiConsumer<Integer, byte[]> listener);

    /** See {@link CompanionDeviceManager#removeOnMessageReceivedListener(int, BiConsumer)}. */
    void removeOnMessageReceivedListener(int messageType, BiConsumer<Integer, byte[]> listener);

    /** See {@link CompanionDeviceManager#requestAction(ActionRequest, String, int[])}. */
    void requestAction(ActionRequest request, String serviceName, int[] associationIds);

    /**
     * See {@link CompanionDeviceManager#setOnActionResultListener(int[], String, Executor,
     * BiConsumer)}.
     */
    void setOnActionResultListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            BiConsumer<Integer, ActionResult> listener);

    /** See {@link CompanionDeviceManager#clearOnActionResultListener(String)}. */
    void clearOnActionResultListener(String serviceName);

    /**
     * See {@link CompanionDeviceManager#setLocalMetadata(int, java.lang.String,
     * android.os.PersistableBundle)}.
     */
    void setLocalMetadata(int userId, String feature, PersistableBundle value);

    /** See {@link CompanionDeviceManager#getLocalMetadata(int)}. */
    PersistableBundle getLocalMetadata(@UserIdInt int userId);
}
