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
package com.android.server.companion.datatransfer.crossdevicesync.feature.mode;

import static com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ModeSyncDocs.CURRENT_SCHEMA_VERSION;
import static com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ModeSyncMetadata.METADATA_CONTEXTUAL_MODES_DOC_SCHEMA_VERSION;
import static com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.modes.ContextualMode;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.notification.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_ENABLE_DND_SYNC)
public class ContextualModeSyncManagerTest extends SyncServiceTestBase {
    @Rule public final SetFlagsRule rule = new SetFlagsRule();

    private FakeModeSyncControllerFactory mModeSyncControllerFactory;
    private ContextualModeSyncManager mModeSyncManager;

    @Before
    public void setUp() {
        // Default user has DND.
        mFakeContextualModeManager.addOrUpdateMode(
                mContext.getUser(),
                new ContextualMode.Builder("id")
                        .setType(ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(ContextualMode.STATE_INACTIVE)
                        .build());
        mFakeContextualModeManager.setModeSyncSupported(true);
        mFakeMetadataPublisher.init();
        mModeSyncControllerFactory = new FakeModeSyncControllerFactory();
        mModeSyncManager =
                new ContextualModeSyncManager(
                        mModeSyncControllerFactory,
                        mFakeUserHelper,
                        mFakeContextualModeManager,
                        mMainExecutor,
                        mFakeMetadataPublisher);
    }

    @Test
    public void init_modeSyncNotSupported() {
        mFakeContextualModeManager.setModeSyncSupported(false);

        mModeSyncManager.init();

        assertThat(mModeSyncControllerFactory.mControllers).isEmpty();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(UserHandle.USER_ALL)
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED))
                .isFalse();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(UserHandle.USER_ALL)
                                .containsKey(METADATA_CONTEXTUAL_MODES_DOC_SCHEMA_VERSION))
                .isFalse();
    }

    @Test
    public void init_modeSyncSupported() {
        mModeSyncManager.init();

        assertThat(mModeSyncControllerFactory.mControllers).hasSize(1);
        assertThat(mModeSyncControllerFactory.mControllers.containsKey(mContext.getUser()))
                .isTrue();
        verify(mModeSyncControllerFactory.mControllers.get(mContext.getUser())).start();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(UserHandle.USER_ALL)
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED))
                .isTrue();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(UserHandle.USER_ALL)
                                .getInt(METADATA_CONTEXTUAL_MODES_DOC_SCHEMA_VERSION))
                .isEqualTo(CURRENT_SCHEMA_VERSION);
    }

    @Test
    public void onUserAdded_startsControllerForNewUser() {
        mModeSyncManager.init();
        UserHandle newUser = UserHandle.of(1);

        mFakeUserHelper.addUser(newUser);

        assertThat(mModeSyncControllerFactory.mControllers).hasSize(2);
        assertThat(mModeSyncControllerFactory.mControllers.containsKey(newUser)).isTrue();
        verify(mModeSyncControllerFactory.mControllers.get(newUser)).start();
    }

    @Test
    public void onUserRemoved_stopControllerAndDeleteDataStore() {
        mModeSyncManager.init();

        mFakeUserHelper.removeUser(mContext.getUser());

        verify(mModeSyncControllerFactory.mControllers.get(mContext.getUser())).stop(true);
    }

    private static class FakeModeSyncControllerFactory
            implements ContextualModeSyncController.Factory {
        final Map<UserHandle, ContextualModeSyncController> mControllers = new HashMap<>();

        @Override
        public ContextualModeSyncController create(UserHandle user) {
            ContextualModeSyncController controller = mock(ContextualModeSyncController.class);
            mControllers.put(user, controller);
            return controller;
        }
    }
}
