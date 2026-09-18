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
import android.content.Context;
import android.os.PersistableBundle;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Companion device manager wrapper. */
public class CompanionDeviceManagerProxyImpl implements CompanionDeviceManagerProxy {

    private final CompanionDeviceManager mCompanionDeviceManager;

    public CompanionDeviceManagerProxyImpl(Context context) {
        mCompanionDeviceManager = context.getSystemService(CompanionDeviceManager.class);
    }

    @Override
    public List<AssociationInfo> getAllAssociations(@UserIdInt int userId) {
        return mCompanionDeviceManager.getAllAssociations(userId);
    }

    @Override
    public List<AssociationInfo> getAllAssociationsWithTransports() {
        return mCompanionDeviceManager.getAllAssociationsWithTransports();
    }

    @Override
    public void addOnAssociationsChangedListener(
            Executor executor, OnAssociationsChangedListener listener, @UserIdInt int userId) {
        mCompanionDeviceManager.addOnAssociationsChangedListener(executor, listener, userId);
    }

    @Override
    public void removeOnAssociationsChangedListener(OnAssociationsChangedListener listener) {
        mCompanionDeviceManager.removeOnAssociationsChangedListener(listener);
    }

    @Override
    public void addOnTransportsChangedListener(
            Executor executor, Consumer<List<AssociationInfo>> listener) {
        mCompanionDeviceManager.addOnTransportsChangedListener(executor, listener);
    }

    @Override
    public void removeOnTransportsChangedListener(Consumer<List<AssociationInfo>> listener) {
        mCompanionDeviceManager.removeOnTransportsChangedListener(listener);
    }

    @Override
    public void setOnDevicePresenceEventListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            Consumer<DevicePresenceEvent> listener) {
        mCompanionDeviceManager.setOnDevicePresenceEventListener(
                associationIds, serviceName, executor, listener);
    }

    @Override
    public void removeOnDevicePresenceEventListener(String serviceName) {
        mCompanionDeviceManager.removeOnDevicePresenceEventListener(serviceName);
    }

    @Override
    public void sendMessage(int messageType, byte[] data, int[] associationIds) {
        mCompanionDeviceManager.sendMessage(messageType, data, associationIds);
    }

    @Override
    public void addOnMessageReceivedListener(
            Executor executor, int messageType, BiConsumer<Integer, byte[]> listener) {
        mCompanionDeviceManager.addOnMessageReceivedListener(executor, messageType, listener);
    }

    @Override
    public void removeOnMessageReceivedListener(
            int messageType, BiConsumer<Integer, byte[]> listener) {
        mCompanionDeviceManager.removeOnMessageReceivedListener(messageType, listener);
    }

    @Override
    public void requestAction(ActionRequest request, String serviceName, int[] associationIds) {
        mCompanionDeviceManager.requestAction(request, serviceName, associationIds);
    }

    @Override
    public void setOnActionResultListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            BiConsumer<Integer, ActionResult> listener) {
        mCompanionDeviceManager.setOnActionResultListener(
                associationIds, serviceName, executor, listener);
    }

    @Override
    public void clearOnActionResultListener(String serviceName) {
        mCompanionDeviceManager.clearOnActionResultListener(serviceName);
    }

    @Override
    public void setLocalMetadata(int userId, String feature, PersistableBundle value) {
        mCompanionDeviceManager.setLocalMetadata(userId, feature, value);
    }

    @Override
    public PersistableBundle getLocalMetadata(int userId) {
        return mCompanionDeviceManager.getLocalMetadata(userId);
    }
}
