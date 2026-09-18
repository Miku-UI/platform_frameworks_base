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
package com.android.server.companion.datatransfer.crossdevicesync.data;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_NOT_CHANGING_LOCAL_STATE;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_UPDATES_LOCAL_STATE;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.getException;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureSucceeded;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeFrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.Document;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.Record;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.SetRecord;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.UnmergedRecord;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@SuppressWarnings({"unchecked", "DataFlowIssue", "FutureReturnValueIgnored"})
@RunWith(AndroidJUnit4.class)
public class SubmergeSharedDataStoreTest extends SharedDataStoreTestBase {
    private static final String TAG = "SubmergeSharedDataStoreTest";
    private static final String NETWORK_ID = "test_network";
    private static final int FEATURE = 123;

    private Network mNetwork;
    private RemoteDeviceContext mRemoteDevice1;
    private RemoteDeviceContext mRemoteDevice2;
    private RemoteDeviceContext mRemoteDevice3;

    @Before
    public void setUp() {
        mNetwork = mFakeNetworkManager.createNetwork(NETWORK_ID, FEATURE, device -> true);
        mSubmergeSharedDataStore.init(mNetwork);
        mRemoteDevice1 = new RemoteDeviceContext();
        mRemoteDevice2 = new RemoteDeviceContext();
        mRemoteDevice3 = new RemoteDeviceContext();
        Log.i(TAG, "Finished setUp().");
    }

    @After
    public void tearDown() {
        Log.i(TAG, "Starting tearDown().");
        mSubmergeSharedDataStore.close();
        mRemoteDevice1.close();
        mRemoteDevice2.close();
        mRemoteDevice3.close();
    }

    @Override
    protected SchemaProvider<String> getSchemaProvider() {
        return new TestSchemaProvider(
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema("/root/register", RecordType.TYPE_REGISTER, 1)
                        .putPathSchema("/root/set", RecordType.TYPE_SET, 1)
                        .putPathSchema("/root/unmerged", RecordType.TYPE_UNMERGED, 1)
                        .build());
    }

    @Override
    public boolean useRealSharedDataStoreImpl() {
        return true;
    }

    @Test
    public void testInit_schemaInitialized() throws Exception {
        transactDataStore(
                doc -> {
                    assertThat(doc.getSchemaVersion()).isEqualTo(1);
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isNull();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(set.entries()).isEmpty();
                    assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                    UnmergedRecord<String> unmerged =
                            (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                    assertThat(unmerged.entries()).isEmpty();
                    assertThat(unmerged.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });

        assertThat(mFakeStorage.getDocument(DOC_ID)).isNotNull();
        assertThat(mFakeStorage.getDocumentSchema(DOC_ID)).isNotNull();
        assertThat(mFakeStorage.getMetadata(DOC_ID)).isNotNull();
        assertThat(mSubmergeSharedDataStore.isOpen()).isTrue();
        assertThat(mSharedDataStoreHandle.isLocked()).isTrue();
    }

    @Test
    public void testInit_schemaVersionUpgraded() throws Exception {
        upgradeSchema(
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/set", RecordType.TYPE_SET, 2)
                        .build());

        transactDataStore(
                doc -> {
                    assertThat(doc.getSchemaVersion()).isEqualTo(2);
                    assertThat(doc.getRecord("/root/register")).isNotNull();
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(set).isNotNull();
                    assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testInit_schemaUpgradeNotOverrideExistingData() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/set", RecordType.TYPE_SET, 2)
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/v2/set", "abc"));

        // Sync to local device.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify sync was successful.
        transactDataStore(
                doc -> {
                    SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(r.entries().contains("abc")).isTrue();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Local device also upgrades the schema.
        upgradeSchema(schema);

        // Verify that the data is still present.
        transactDataStore(
                doc -> {
                    SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(r.entries().contains("abc")).isTrue();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testInit_migrationOverridesPastChanges() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/register", RecordType.TYPE_REGISTER, 2)
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/v2/register", "abc"));

        // Sync to local device.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify sync was successful.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Local device upgrades schema, but overrides the new path.
        upgradeSchema(
                /* migrator= */ doc -> doc.putData("/root/v2/register", null),
                /* validator= */ null,
                schema);

        // Verify that the local change wins.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isNull();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });

        // Verify the remote device 1 data is overridden.
        flushNetworks(this, mRemoteDevice1);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isNull();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testInit_migrationNotOverridingConcurrentChanges() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/register", RecordType.TYPE_REGISTER, 2)
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/v2/register", "abc"));

        // Local device upgrades schema, but overrides the new path.
        upgradeSchema(
                /* migrator= */ doc -> doc.putData("/root/v2/register", null),
                /* validator= */ null,
                schema);

        // Sync happens.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify that the local change is overridden.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Verify the remote device 1 data is NOT overridden.
        flushNetworks(this, mRemoteDevice1);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testInit_upgradePathIsBad_fails() {
        AndroidFuture<Boolean> future =
                upgradeSchema(
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema("/root/register", RecordType.TYPE_SET, 2)
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(IllegalSchemaChangeException.class);
        assertThat(mSubmergeSharedDataStore.isOpen()).isFalse();
        assertThat(mSharedDataStoreHandle.isLocked()).isFalse();
    }

    @Test
    public void testInit_upgradeValidationFails() {
        AndroidFuture<Boolean> future =
                upgradeSchema(
                        /* migrator= */ null,
                        /* validator= */ doc -> {
                            throw new Exception();
                        },
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema("/root/v2/set", RecordType.TYPE_SET, 2)
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        assertThat(mSubmergeSharedDataStore.isOpen()).isFalse();
        assertThat(mSharedDataStoreHandle.isLocked()).isFalse();
    }

    @Test
    public void testInit_upgradeIgnoredWithSameSchemaVersion() throws Exception {
        AndroidFuture<Boolean> future =
                upgradeSchema(getSchemaProvider().getAllDocumentSchema().getFirst());

        assertThat(future.get()).isTrue();
        assertThat(mSubmergeSharedDataStore.isOpen()).isTrue();
        assertThat(mSharedDataStoreHandle.isLocked()).isTrue();
    }

    @Test
    public void testInit_upgradeIncompatibleWithExistingData_fail() throws Exception {
        // Remote device 1 upgrades the schema.
        upgradeSchema(
                mRemoteDevice1,
                /* migrator= */ doc -> doc.putData("/root/v2/new", "abc"),
                /* validator= */ null,
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/new", RecordType.TYPE_REGISTER, 2)
                        .build());

        // Connect with remote device 1 and sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify data is synced.
        transactDataStore(doc -> assertThat(doc.getRecord("/root/v2/new").get()).isEqualTo("abc"));

        // Upgrade local device with incompatible schema.
        AndroidFuture<Boolean> future =
                upgradeSchema(
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema("/root/v2/new", RecordType.TYPE_SET, 2)
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        assertThat(mSubmergeSharedDataStore.isOpen()).isFalse();
        assertThat(mSharedDataStoreHandle.isLocked()).isFalse();
    }

    @Test
    public void testInit_failureRecoveredByDeletingDB() throws Exception {
        // Illegal upgrade.
        DocumentSchemaInfo info =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/register", RecordType.TYPE_SET, 2)
                        .build();
        AndroidFuture<Boolean> future = upgradeSchema(info);
        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(IllegalSchemaChangeException.class);

        // Close and delete the DB
        assertThat(mSubmergeSharedDataStore.close().get()).isTrue();
        assertThat(mSharedDataStoreHandle.delete().get()).isTrue();

        // Re-init after deletion should succeed.
        assertThat(upgradeSchema(info).get()).isTrue();
        transactDataStore(
                doc -> {
                    assertThat(doc.getSchemaVersion()).isEqualTo(2);
                    assertThat(doc.getRecord("/root/register")).isInstanceOf(SetRecord.class);
                });
    }

    @Test
    public void testInit_multipleCallReturnsSameSucceededFuture() {
        closeAndReCreateDataStore();
        AndroidFuture<?> future1 = mSubmergeSharedDataStore.init(mNetwork);
        AndroidFuture<?> future2 = mSubmergeSharedDataStore.init(mNetwork);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(isFutureSucceeded(future1)).isTrue();
        assertThat(isFutureSucceeded(future2)).isTrue();
    }

    @Test
    public void testInit_afterFailure_returnsFailedFuture() {
        closeAndReCreateDataStore();
        mFakeStorage.setRuntimeException(new RuntimeException());
        AndroidFuture<?> future1 = mSubmergeSharedDataStore.init(mNetwork);
        assertThat(isFutureFailed(future1)).isTrue();

        // Second init shall fail
        AndroidFuture<?> future2 = mSubmergeSharedDataStore.init(mNetwork);

        assertThat(isFutureFailed(future2)).isTrue();
    }

    @Test
    public void testInit_afterClose_returnFailedFuture() throws Exception {
        mSubmergeSharedDataStore.close().get();

        AndroidFuture<?> future = mSubmergeSharedDataStore.init(mNetwork);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void testInit_handleLocked_returnFailedFuture() {
        // Verify that the handle is locked.
        assertThat(mSharedDataStoreHandle.isLocked()).isTrue();

        // Init a new data store instance.
        AndroidFuture<?> future =
                mTestSharedDataStoreFactory.create(mSharedDataStoreHandle).init(mNetwork);

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testTransaction_success() throws Exception {
        // Write data.
        transactDataStore(doc -> doc.putData("/root/register", "abc"));

        // Read data and assert it's correct.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r).isNotNull();
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testTransaction_applierFail() throws Exception {
        // Write illegal data
        assertThrows(
                ExecutionException.class,
                () -> transactDataStore(doc -> doc.putUnmergedData("/", "abc")));

        // Read data and assert no data was written.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/");
                    assertThat(r).isNull();
                });
    }

    @Test
    public void testTransaction_storageFail() throws Exception {
        // Write data.
        assertThrows(
                ExecutionException.class,
                () ->
                        transactDataStore(
                                doc -> {
                                    doc.putData("/root/register", "abc");
                                    // Close storage so that accessing it will trigger an exception.
                                    mFakeStorage.close();
                                }));

        // Recover storage and verify nothing is written.
        mFakeStorage.open();
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isNull();
                });
    }

    @Test
    public void testTransaction_documentNotAccessibleOutOfTransaction() throws Exception {
        // Open and cache the doc.
        MutableDocument<String>[] document = new MutableDocument[1];
        transactDataStore(doc -> document[0] = doc);

        assertThrows(
                IllegalStateException.class, () -> document[0].putData("/root/register", "abc"));
        assertThrows(IllegalStateException.class, () -> document[0].getRecord("/root/register"));
    }

    @Test
    public void testTransaction_multiDoc_independentlyUpdate() throws Exception {
        // Create 2 docs.
        upgradeSchema(
                DocumentSchemaInfo.builder()
                        .setDocId("doc_1")
                        .setVersion(1)
                        .putPathSchema("/root", RecordType.TYPE_REGISTER, 1)
                        .build(),
                DocumentSchemaInfo.builder()
                        .setDocId("doc_2")
                        .setVersion(1)
                        .putPathSchema("/root", RecordType.TYPE_SET, 1)
                        .build());

        mSubmergeSharedDataStore.transact(
                "doc_1",
                doc -> {
                    doc.putData("/root", "abc");
                    return true;
                });
        mSubmergeSharedDataStore.transact(
                "doc_2",
                doc -> {
                    doc.addDataToSet("/root", "def");
                    return true;
                });

        // Verify their value are independent.
        AndroidFuture<Boolean> future =
                mSubmergeSharedDataStore.transact(
                        "doc_1",
                        doc -> {
                            Record<String> r = doc.getRecord("/root");
                            assertThat(r).isNotNull();
                            assertThat(r.get()).isEqualTo("abc");
                            return true;
                        });
        assertThat(future.get()).isTrue();
        future =
                mSubmergeSharedDataStore.transact(
                        "doc_2",
                        doc -> {
                            SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root");
                            assertThat(r).isNotNull();
                            assertThat(r.entries().contains("def")).isTrue();
                            return true;
                        });
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testNetworkUpdate_remoteDataVisible() throws Exception {
        // Update initiated from remote device.
        connectNetworks(this, mRemoteDevice1);
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "abc"));
        flushNetworks(this, mRemoteDevice1);

        // Verify it's visible to local device.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void onNetworkMessage_malformattedInput_ignored() throws Exception {
        // Prepare some data.
        transactDataStore(doc -> doc.putData("/root/register", "local_data"));

        // Connect with device 1.
        connectNetworks(this, mRemoteDevice1);

        // mRemoteDevice1 sends a junk message.
        mRemoteDevice1
                .getNetworkManager()
                .findRemoteDevice(mFakeNetworkManager)
                .sendMessage(NETWORK_ID, new byte[] {1, 2, 3});
        flushNetworks(this, mRemoteDevice1);

        // Verify local data is not changed.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("local_data");
                });

        // Verify no outbound messages were sent in response.
        int associationId =
                mFakeNetworkManager
                        .findRemoteDevice(mRemoteDevice1.getNetworkManager())
                        .getAssociationId();
        assertThat(mFakeNetworkManager.getRemoteDevice(associationId).getSentMessages()).isEmpty();
    }

    @Test
    public void testConcurrentNetworkUpdate_differentPath_merged() throws Exception {
        // Concurrent update from different devices.
        transactDataStore(doc -> doc.putData("/root/register", "abc"));
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/set", "def"));
        transactDataStore(mRemoteDevice2, doc -> doc.putUnmergedData("/root/unmerged", "bla"));

        // Connect with device 1 and trigger a sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify the data is merged.
        transactDataStore(
                doc -> {
                    SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(r.entries().contains("def")).isTrue();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Connect with device 2 and trigger a sync.
        connectNetworks(this, mRemoteDevice1, mRemoteDevice2);
        flushNetworks(this, mRemoteDevice1, mRemoteDevice2);

        // Verify the data is merged.
        transactDataStore(
                doc -> {
                    UnmergedRecord<String> r =
                            (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                    assertThat(r.get(mRemoteDevice2.nodeId())).isEqualTo("bla");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    UnmergedRecord<String> r =
                            (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                    assertThat(r.get(mRemoteDevice2.nodeId())).isEqualTo("bla");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice2,
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(set.entries().contains("def")).isTrue();
                    assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_remoteSchemaIncompatible_fail() throws Exception {
        // Remote device 1 upgrades the schema.
        upgradeSchema(
                mRemoteDevice1,
                /* migrator= */ doc -> doc.putData("/root/v2/new", "abc"),
                /* validator= */ null,
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/new", RecordType.TYPE_REGISTER, 2)
                        .build());

        // Local device upgrades with incompatible schema.
        upgradeSchema(
                /* migrator= */ doc -> doc.addDataToSet("/root/v2/new", "abc"),
                /* validator= */ null,
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/new", RecordType.TYPE_SET, 2)
                        .build());

        // Connect and sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify devices stay out of sync.
        transactDataStore(
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/v2/new");
                    assertThat(set.entries().contains("abc")).isTrue();
                });

        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/new");
                    assertThat(r).isNotInstanceOf(SetRecord.class);
                    assertThat(r.get()).isEqualTo("abc");
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_samePath_latestTimestampWins() throws Exception {
        // Concurrent update from different devices.
        mTimestampProvider.setTimeStamp(200);
        transactDataStore(doc -> doc.putData("/root/register", "local"));
        mTimestampProvider.setTimeStamp(300);
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "remote"));

        // Connect with device 1 and trigger a sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify the remote data wins.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("remote");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("remote");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_threeDevices_eventOrderWins() throws Exception {
        // This test verifies that event order is prioritized over timestamp in a 3-device scenario.
        // Let's denote mRemoteDevice1 as A, local device as B, and mRemoteDevice2 as C.
        RemoteDeviceContext a = mRemoteDevice1;
        SubmergeSharedDataStoreTest b = this;
        RemoteDeviceContext c = mRemoteDevice2;

        // 1. A with a larger timestamp writes data.
        mTimestampProvider.setTimeStamp(300);
        transactDataStore(a, doc -> doc.putData("/root/register", "A"));

        // 2. A syncs with B.
        connectNetworks(b, a);
        flushNetworks(b, a);
        transactDataStore(
                b, doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("A"));

        // 3. B writes the data without syncing to A.
        mTimestampProvider.setTimeStamp(200); // B has a smaller timestamp.
        transactDataStore(b, doc -> doc.putData("/root/register", "B"));

        // 4. A syncs with C.
        connectNetworks(a, c);
        flushNetworks(a, c);
        transactDataStore(
                c, doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("A"));

        // 5. B syncs with C.
        connectNetworks(b, c);
        flushNetworks(b, c);
        transactDataStore(
                c, doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("B"));

        // 6. Flush all pending messages.
        flushNetworks(b, a, c);

        // 7. Verify A and B's state are right. All devices should have B's data.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("B");
                };

        transactDataStore(b, verifier);
        transactDataStore(a, verifier);
        transactDataStore(c, verifier);
    }

    @Test
    public void testConcurrentUpdate_upgradedFromDifferentBaseSchemaVersion_merged()
            throws Exception {
        // 1. Define schema v2 with a new set.
        DocumentSchemaInfo schemaV2 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/register", RecordType.TYPE_REGISTER, 1)
                        .putPathSchema("/root/set", RecordType.TYPE_SET, 1)
                        .putPathSchema("/root/unmerged", RecordType.TYPE_UNMERGED, 1)
                        .putPathSchema("/root/v2/set", RecordType.TYPE_SET, 2)
                        .build();

        // 2. Setup Device 1 (local device), upgrading from v1 to v2.
        upgradeSchema(schemaV2).get();
        mTimestampProvider.setTimeStamp(200);
        transactDataStore(doc -> doc.addDataToSet("/root/v2/set", "local"));

        // 3. Setup Device 2 (remote device) to initialize with schema v2 directly.
        reInitWithNewSchema(mRemoteDevice1, schemaV2).get();
        mTimestampProvider.setTimeStamp(300);
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/v2/set", "remote"));

        // 4. Sync devices.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // 5. Assert that both devices have the merged data.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(set.entries()).containsExactly("local", "remote");
                };
        transactDataStore(verifier);
        transactDataStore(mRemoteDevice1, verifier);
    }

    @Test
    public void testEmptySchemaVersionBump_concurrentUpdate_merged() throws Exception {
        // 1. Define schemas.
        // Schema v2 is an "empty" bump, just incrementing the version number.
        DocumentSchemaInfo schemaV2 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/register", RecordType.TYPE_REGISTER, 1)
                        .putPathSchema("/root/set", RecordType.TYPE_SET, 1)
                        .putPathSchema("/root/unmerged", RecordType.TYPE_UNMERGED, 1)
                        .build();
        // Schema v3 adds a new path.
        DocumentSchemaInfo schemaV3 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(3)
                        .putPathSchema("/root/register", RecordType.TYPE_REGISTER, 1)
                        .putPathSchema("/root/set", RecordType.TYPE_SET, 1)
                        .putPathSchema("/root/unmerged", RecordType.TYPE_UNMERGED, 1)
                        .putPathSchema("/root/v3/set", RecordType.TYPE_SET, 3)
                        .build();

        // 2. Setup Device A (local device), starting from the default v1 schema.
        upgradeSchema(schemaV2).get();
        upgradeSchema(schemaV3).get();
        mTimestampProvider.setTimeStamp(200);
        transactDataStore(
                doc -> {
                    doc.addDataToSet("/root/set", "local_v1");
                    doc.addDataToSet("/root/v3/set", "local_v3");
                });

        // 3. Setup Device B (remote device) to initialize with schema v3 directly.
        reInitWithNewSchema(mRemoteDevice1, schemaV3).get();
        mTimestampProvider.setTimeStamp(300);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    doc.addDataToSet("/root/set", "remote_v1");
                    doc.addDataToSet("/root/v3/set", "remote_v3");
                });

        // 4. Sync devices.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // 5. Assert that both devices have the merged data.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    SetRecord<String> setV1 = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(setV1.entries()).containsExactly("local_v1", "remote_v1");
                    SetRecord<String> setV3 = (SetRecord<String>) doc.getRecord("/root/v3/set");
                    assertThat(setV3.entries()).containsExactly("local_v3", "remote_v3");
                };
        transactDataStore(verifier);
        transactDataStore(mRemoteDevice1, verifier);
    }

    @Test
    public void testIncompatibleSchema_concurrentUpdate_notSynced() throws Exception {
        // 1. Define schemas.
        DocumentSchemaInfo schemaAB =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema("/root/set", RecordType.TYPE_SET, 1)
                        .build();
        DocumentSchemaInfo schemaCD =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema("/root/set", RecordType.TYPE_REGISTER, 1)
                        .build();

        // 2. Setup devices.
        SubmergeSharedDataStoreTest a = this;
        reInitWithNewSchema(a, schemaAB).get();
        RemoteDeviceContext b = mRemoteDevice1;
        reInitWithNewSchema(b, schemaAB).get();
        RemoteDeviceContext c = mRemoteDevice2;
        reInitWithNewSchema(c, schemaCD).get();
        RemoteDeviceContext d = mRemoteDevice3;
        reInitWithNewSchema(d, schemaCD).get();
        connectNetworks(a, b, c, d);

        // 3. Perform multiple rounds of concurrent updates and syncs.
        for (int i = 0; i < 3; i++) {
            final int round = i;
            transactDataStore(a, doc -> doc.addDataToSet("/root/set", "A" + round));
            transactDataStore(b, doc -> doc.addDataToSet("/root/set", "B" + round));
            transactDataStore(c, doc -> doc.putData("/root/set", "C" + round));
            transactDataStore(d, doc -> doc.putData("/root/set", "D" + round));
            flushNetworks(a, b, c, d);
        }

        // 4. Verification.
        ThrowingConsumer<MutableDocument<String>> verifierAB =
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(set.entries()).containsExactly("A0", "B0", "A1", "B1", "A2", "B2");
                };
        transactDataStore(a, verifierAB);
        transactDataStore(b, verifierAB);

        ThrowingConsumer<MutableDocument<String>> verifierCD =
                doc -> {
                    Record<String> register = doc.getRecord("/root/set");
                    assertThat(register.get()).isAnyOf("C2", "D2");
                };
        transactDataStore(c, verifierCD);
        transactDataStore(d, verifierCD);
    }

    @Test
    public void testSequentialIncompatibleSchemaUpgrade_formsPartitions() throws Exception {
        // 1. Define schemas.
        DocumentSchemaInfo schemaV1 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema("/root/common", RecordType.TYPE_SET, 1)
                        .build();
        DocumentSchemaInfo schemaV2_AB =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/common", RecordType.TYPE_SET, 1)
                        .putPathSchema("/root/v2/data", RecordType.TYPE_SET, 2)
                        .build();
        DocumentSchemaInfo schemaV2_CD =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/common", RecordType.TYPE_SET, 1)
                        .putPathSchema("/root/v2/data", RecordType.TYPE_REGISTER, 2)
                        .build();

        // 2. Setup devices.
        SubmergeSharedDataStoreTest a = this;
        reInitWithNewSchema(a, schemaV1).get();
        RemoteDeviceContext b = mRemoteDevice1;
        reInitWithNewSchema(b, schemaV1).get();
        RemoteDeviceContext c = mRemoteDevice2;
        reInitWithNewSchema(c, schemaV1).get();
        RemoteDeviceContext d = mRemoteDevice3;
        reInitWithNewSchema(d, schemaV1).get();
        connectNetworks(a, b, c, d);

        // 3. Initial sync on compatible schema.
        transactDataStore(a, doc -> doc.addDataToSet("/root/common", "A"));
        transactDataStore(b, doc -> doc.addDataToSet("/root/common", "B"));
        transactDataStore(c, doc -> doc.addDataToSet("/root/common", "C"));
        transactDataStore(d, doc -> doc.addDataToSet("/root/common", "D"));
        flushNetworks(a, b, c, d);
        ThrowingConsumer<MutableDocument<String>> v1Verifier =
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/common");
                    assertThat(set.entries()).containsExactly("A", "B", "C", "D");
                };
        transactDataStore(a, v1Verifier);
        transactDataStore(b, v1Verifier);
        transactDataStore(c, v1Verifier);
        transactDataStore(d, v1Verifier);

        // 4. Sequential, interleaved upgrade.
        upgradeSchema(a, schemaV2_AB).get();
        transactDataStore(a, doc -> doc.addDataToSet("/root/v2/data", "A_v2"));
        flushNetworks(a, b, c, d);
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc ->
                        assertThat(((SetRecord<String>) doc.getRecord("/root/v2/data")).entries())
                                .containsExactly("A_v2");
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        transactDataStore(c, verifier);
        transactDataStore(d, verifier);

        upgradeSchema(b, schemaV2_AB).get();
        transactDataStore(b, doc -> doc.addDataToSet("/root/v2/data", "B_v2"));
        flushNetworks(a, b, c, d);
        verifier =
                doc ->
                        assertThat(((SetRecord<String>) doc.getRecord("/root/v2/data")).entries())
                                .containsExactly("A_v2", "B_v2");
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        transactDataStore(c, verifier);
        transactDataStore(d, verifier);

        // C upgrade will fail due to incompatible schema.
        AndroidFuture<Boolean> future = upgradeSchema(c, schemaV2_CD);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        // Reinit C from fresh state.
        reInitWithNewSchema(c, schemaV2_CD).get();
        connectNetworks(a, b, c, d);
        transactDataStore(c, doc -> doc.putData("/root/v2/data", "C_v2"));
        flushNetworks(a, b, c, d);
        // C now stays out of sync.
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        transactDataStore(d, verifier);
        transactDataStore(
                c,
                doc -> {
                    assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                            .isEmpty();
                    assertThat(doc.getRecord("/root/v2/data")).isNotInstanceOf(SetRecord.class);
                    assertThat(doc.getRecord("/root/v2/data").get()).isEqualTo("C_v2");
                });

        // D's upgrade will fail due to incompatible schema.
        future = upgradeSchema(d, schemaV2_CD);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        // Reinit D from fresh state.
        reInitWithNewSchema(d, schemaV2_CD).get();
        connectNetworks(a, b, c, d);
        transactDataStore(d, doc -> doc.putData("/root/v2/data", "D_v2"));
        flushNetworks(a, b, c, d);
        // D now stays out of sync from A and B but is in sync with C.
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        ThrowingConsumer<MutableDocument<String>> verifierCD =
                doc -> {
                    assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                            .isEmpty();
                    assertThat(doc.getRecord("/root/v2/data")).isNotInstanceOf(SetRecord.class);
                    assertThat(doc.getRecord("/root/v2/data").get()).isEqualTo("D_v2");
                };
        transactDataStore(c, verifierCD);
        transactDataStore(d, verifierCD);

        // From this point, A/B/C/D forms partition.
        transactDataStore(a, doc -> doc.addDataToSet("/root/common", "A_v2"));
        transactDataStore(b, doc -> doc.addDataToSet("/root/common", "B_v2"));
        transactDataStore(c, doc -> doc.addDataToSet("/root/common", "C_v2"));
        transactDataStore(d, doc -> doc.addDataToSet("/root/common", "D_v2"));
        flushNetworks(a, b, c, d);
        ThrowingConsumer<MutableDocument<String>> verifierAB =
                doc ->
                        assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                                .containsExactly("A", "B", "C", "D", "A_v2", "B_v2");
        verifierCD =
                doc ->
                        assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                                .containsExactly("C_v2", "D_v2");
        transactDataStore(a, verifierAB);
        transactDataStore(b, verifierAB);
        transactDataStore(c, verifierCD);
        transactDataStore(d, verifierCD);
    }

    @Test
    public void testSharedNetwork_multiplexing() throws Exception {
        // Create a new data store sharing the same network.
        SubmergeSharedDataStore<String> newDataStore =
                (SubmergeSharedDataStore<String>) getNewSharedDataStore("newDataStore");
        newDataStore.init(mNetwork).get();

        // Both data store connect to a remote device.
        connectNetworks(this, mRemoteDevice1);

        // Remote device changes.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "val"));
        flushNetworks(this, mRemoteDevice1);

        // Both data store receive the update.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("val");
        transactDataStore(this, verifier);
        transactDataStore(newDataStore, verifier);

        // One of the local data store changes.
        transactDataStore(this, doc -> doc.putData("/root/register", "val2"));
        flushNetworks(this, mRemoteDevice1);

        // Both the remote device and the other local data store have the change.
        verifier = doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("val2");
        transactDataStore(mRemoteDevice1, verifier);
        transactDataStore(newDataStore, verifier);

        // The other local data store changes.
        transactDataStore(newDataStore, doc -> doc.putData("/root/register", "val3"));
        flushNetworks(this, mRemoteDevice1);

        // Both the remote device and the local data store have the change.
        verifier = doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("val3");
        transactDataStore(mRemoteDevice1, verifier);
        transactDataStore(this, verifier);

        // Remote device has a change.
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/set", "val"));
        flushNetworks(this, mRemoteDevice1);

        // Both data store receive the update.
        verifier =
                doc -> {
                    assertThat(doc.getRecord("/root/register").get()).isEqualTo("val3");
                    assertThat(doc.getRecord("/root/set").get()).isEqualTo("val");
                };
        transactDataStore(this, verifier);
        transactDataStore(newDataStore, verifier);
    }

    @Test
    public void testClose() throws Exception {
        transactDataStore(doc -> doc.putData("/root/register", "abc"));

        AndroidFuture<?> future = mSubmergeSharedDataStore.close();

        assertThat(getException(mSubmergeSharedDataStore.transact(DOC_ID, doc -> null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(getException(mSubmergeSharedDataStore.init(mNetwork)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mFakeStorage.isOpen()).isFalse();
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(mSharedDataStoreHandle.isLocked()).isFalse();
    }

    @Test
    public void testClose_multipleCallReturnsSameFuture() {
        AndroidFuture<?> future1 = mSubmergeSharedDataStore.close();
        AndroidFuture<?> future2 = mSubmergeSharedDataStore.close();

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(isFutureSucceeded(future1)).isTrue();
        assertThat(isFutureSucceeded(future2)).isTrue();
    }

    @Test
    public void testRemoteChangeListener_batchCallback() throws Exception {
        OnRemoteChangeListener listener = mock(OnRemoteChangeListener.class);
        mSubmergeSharedDataStore.registerOnRemoteChangeListener(mMainExecutor, listener);
        // Update initiated from remote device.
        connectNetworks(this, mRemoteDevice1);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    doc.putData("/root/register", "abc");
                    doc.addDataToSet("/root/set", "def");
                });
        flushNetworks(this, mRemoteDevice1);

        // Verify we received batch update callback.
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(listener).onRemoteChange(captor.capture());
        List<String> paths = captor.getValue();
        assertThat(paths).containsExactly("/root/register", "/root/set");
    }

    @Test
    public void testSyncWithIndirectlyConnectedDevices() throws Exception {
        // Connect (this, device1) and (this, device2). (device1, device2) is not connected.
        connectNetworks(this, mRemoteDevice1);
        connectNetworks(this, mRemoteDevice2);
        flushNetworks(this, mRemoteDevice1, mRemoteDevice2);

        // Change happens on remote device 1 and remote device 2 for different paths.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "aaa"));
        transactDataStore(mRemoteDevice2, doc -> doc.addDataToSet("/root/set", "bbb"));
        flushNetworks(this, mRemoteDevice1, mRemoteDevice2);

        // Assert that all devices are synced with all changes.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    assertThat(doc.getRecord("/root/register").get()).isEqualTo("aaa");
                    assertThat(doc.getRecord("/root/set").get()).isEqualTo("bbb");
                };
        transactDataStore(this, verifier);
        transactDataStore(mRemoteDevice1, verifier);
        transactDataStore(mRemoteDevice2, verifier);

        // Another change happens on remote device 1.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "ccc"));
        flushNetworks(this, mRemoteDevice1, mRemoteDevice2);

        // Assert that all devices are synced with all changes.
        verifier = doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("ccc");
        transactDataStore(this, verifier);
        transactDataStore(mRemoteDevice1, verifier);
        transactDataStore(mRemoteDevice2, verifier);
    }

    @Test
    public void testRemoteUpdate_logsSyncEvent() throws Exception {
        // 1. Initial state.
        connectNetworks(this, mRemoteDevice1);

        // 2. Remote update that changes local state.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "abc"));
        flushNetworks(this, mRemoteDevice1);

        // Verify log for success.
        var expectedWriteCall =
                new FakeFrameworkStatsLogProxy.WriteCall(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_UPDATES_LOCAL_STATE,
                        FEATURE);
        assertThat(mFakeFrameworkStatsLogProxy.getWrites()).contains(expectedWriteCall);
        mFakeFrameworkStatsLogProxy.clear();

        // 3. Remote update that does NOT change local state.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "abc"));
        flushNetworks(this, mRemoteDevice1);

        // Verify log for no change.
        expectedWriteCall =
                new FakeFrameworkStatsLogProxy.WriteCall(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_NOT_CHANGING_LOCAL_STATE,
                        FEATURE);
        assertThat(mFakeFrameworkStatsLogProxy.getWrites()).contains(expectedWriteCall);
    }

    @Test
    public void testRemoteUpdate_logsNotChangingEvent() throws Exception {
        // 1. Connect and sync the devices
        connectNetworks(this, mRemoteDevice1);
        transactDataStore(this, doc -> doc.putData("/root/register", "abc"));
        flushNetworks(this, mRemoteDevice1);

        // 2. Disconnect the devices from each other temporarily.
        var remoteDevice = mFakeNetworkManager.findRemoteDevice(mRemoteDevice1.getNetworkManager());
        var localDevice = mRemoteDevice1.getNetworkManager().findRemoteDevice(mFakeNetworkManager);
        remoteDevice.setRemoteNetworkManager(null);
        localDevice.setRemoteNetworkManager(null);

        // 3. Make a no-op net change to the remote device
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "def"));
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "abc"));

        // 4. Reconnect the network managers, and flush the network.
        remoteDevice.setRemoteNetworkManager(mRemoteDevice1.getNetworkManager());
        localDevice.setRemoteNetworkManager(mFakeNetworkManager);
        flushNetworks(this, mRemoteDevice1);

        // Verify log for no change.
        var expectedWriteCall =
                new FakeFrameworkStatsLogProxy.WriteCall(
                        CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_NOT_CHANGING_LOCAL_STATE,
                        FEATURE);
        assertThat(mFakeFrameworkStatsLogProxy.getWrites()).contains(expectedWriteCall);
    }

    private static void connectNetworks(SharedDataStoreTestBase... testContexts) {
        for (SharedDataStoreTestBase a : testContexts) {
            for (SharedDataStoreTestBase b : testContexts) {
                if (a == b) {
                    continue;
                }
                a.getNetworkManager().connect(b.getNetworkManager());
            }
        }
    }

    private static void flushNetworks(SharedDataStoreTestBase... testContexts) {
        while (true) {
            boolean allFlushed = true;
            for (SharedDataStoreTestBase t : testContexts) {
                allFlushed &= !t.getNetworkManager().flushAllMessages();
            }
            if (allFlushed) {
                break;
            }
        }
    }

    private AndroidFuture<Boolean> upgradeSchema(DocumentSchemaInfo... schema) {
        return upgradeSchema(null, null, schema);
    }

    private AndroidFuture<Boolean> upgradeSchema(
            @Nullable Consumer<MutableDocument<String>> migrator,
            @Nullable ThrowingConsumer<Document<String>> validator,
            DocumentSchemaInfo... schema) {
        return upgradeSchema(this, migrator, validator, schema);
    }

    private AndroidFuture<Boolean> upgradeSchema(
            SharedDataStoreTestBase testContext, DocumentSchemaInfo... schema) {
        return upgradeSchema(testContext, null, null, schema);
    }

    private AndroidFuture<Boolean> upgradeSchema(
            SharedDataStoreTestBase testContext,
            @Nullable Consumer<MutableDocument<String>> migrator,
            @Nullable ThrowingConsumer<Document<String>> validator,
            DocumentSchemaInfo... schema) {
        // Close the current data store. And build a new one with a higher schema version.
        testContext.mSharedDataStore.close();
        testContext.initialize(
                new TestSchemaProvider(schema).setMigrator(migrator).setValidator(validator));
        return ((SubmergeSharedDataStore<String>) testContext.mSharedDataStore)
                .init(
                        testContext
                                .getNetworkManager()
                                .createNetwork(NETWORK_ID, FEATURE, device -> true));
    }

    private void closeAndReCreateDataStore() {
        closeAndReCreateDataStore(this);
    }

    private void closeAndReCreateDataStore(SharedDataStoreTestBase testContext) {
        testContext.mSharedDataStore.close();
        testContext.initialize(getSchemaProvider());
    }

    private AndroidFuture<Boolean> reInitWithNewSchema(
            SharedDataStoreTestBase testContext, DocumentSchemaInfo... schema) throws Exception {
        testContext.mSharedDataStore.close().get();
        testContext.mSharedDataStoreHandle.delete();
        return upgradeSchema(testContext, schema);
    }

    private void transactDataStore(
            SharedDataStoreTestBase testContext, ThrowingConsumer<MutableDocument<String>> consumer)
            throws Exception {
        transactDataStore((SharedDataStore<String>) testContext.getSharedDataStore(), consumer);
    }

    private void transactDataStore(
            SharedDataStore<String> dataStore, ThrowingConsumer<MutableDocument<String>> consumer)
            throws Exception {
        dataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            consumer.acceptOrThrow(doc);
                            return true;
                        })
                .get();
    }

    private void transactDataStore(ThrowingConsumer<MutableDocument<String>> consumer)
            throws Exception {
        transactDataStore(this, consumer);
    }

    private class RemoteDeviceContext extends SharedDataStoreTestBase {
        RemoteDeviceContext() {
            mSharedDataStore.init(
                    mFakeNetworkManager.createNetwork(NETWORK_ID, FEATURE, device -> true));
        }

        @Override
        protected SchemaProvider<String> getSchemaProvider() {
            return SubmergeSharedDataStoreTest.this.getSchemaProvider();
        }

        @Override
        public boolean useRealSharedDataStoreImpl() {
            return true;
        }

        public void close() {
            mSharedDataStore.close();
        }

        public String nodeId() {
            return mSharedDataStore.getLocalDeviceNodeId();
        }
    }

    private static class TestSchemaProvider implements SchemaProvider<String> {
        private final List<DocumentSchemaInfo> mSchemaList = new ArrayList<>();
        @Nullable private Consumer<MutableDocument<String>> mMigrator;
        @Nullable private ThrowingConsumer<Document<String>> mValidator;

        TestSchemaProvider(DocumentSchemaInfo... schemas) {
            Collections.addAll(mSchemaList, schemas);
        }

        @Override
        public List<DocumentSchemaInfo> getAllDocumentSchema() {
            return mSchemaList;
        }

        public TestSchemaProvider setMigrator(
                @Nullable Consumer<MutableDocument<String>> migrator) {
            mMigrator = migrator;
            return this;
        }

        @Override
        public void migrateDocument(MutableDocument<String> document) {
            if (mMigrator != null) {
                mMigrator.accept(document);
            }
        }

        public TestSchemaProvider setValidator(
                @Nullable ThrowingConsumer<Document<String>> validator) {
            mValidator = validator;
            return this;
        }

        @Override
        public void validateDocument(Document<String> document) throws SchemaValidationException {
            SchemaProvider.super.validateDocument(document);
            if (mValidator != null) {
                try {
                    mValidator.acceptOrThrow(document);
                } catch (Exception e) {
                    throw new SchemaValidationException(e);
                }
            }
        }
    }
}
