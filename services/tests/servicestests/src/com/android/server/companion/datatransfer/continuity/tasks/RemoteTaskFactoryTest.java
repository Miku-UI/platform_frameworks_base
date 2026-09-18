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

package com.android.server.companion.datatransfer.continuity.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.companion.datatransfer.continuity.RemoteTask;
import android.content.pm.ActivityInfo;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.TaskContinuityTest;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@Presubmit
public class RemoteTaskFactoryTest extends TaskContinuityTest {

    @Mock private ActivityInfo mockBrowserActivityInfo;

    private final String BROWSER_PACKAGE_NAME = "com.android.browser";
    private final String BROWSER_LABEL = "Browser";
    private final int ASSOCIATION_ID = 1;
    private final String ASSOCIATION_DISPLAY_NAME = "test_device";

    @Before
    public void setUp() {
        when(mMockPackageManager.getDefaultBrowserPackageNameAsUser(USER_ID))
                .thenReturn(BROWSER_PACKAGE_NAME);
        setApplicationInfo(BROWSER_PACKAGE_NAME, BROWSER_LABEL);
    }

    @Test
    public void testCreate_packageInstalled_returnsRemoteTask() {
        String packageName = "com.example.app";
        String label = "label";
        setApplicationInfo(packageName, label);
        RemoteTask remoteTask =
                tryCreate(
                        new RemoteTaskInfo(
                                1, packageName, true, 100, new HandoffOptions(true, false)));

        assertThat(remoteTask.getAssociationDisplayName()).isEqualTo(ASSOCIATION_DISPLAY_NAME);
        assertThat(remoteTask.getPackageName()).isEqualTo(packageName);
        assertThat(remoteTask.getLabel()).isEqualTo(label);
        assertThat(remoteTask.isHandoffEnabled()).isTrue();
        assertThat(remoteTask.isTaskInForeground()).isTrue();
    }

    @Test
    public void testCreate_handoffDisabled_returnsNull() {
        String packageName = "com.example.app";
        String label = "label";
        setApplicationInfo(packageName, label);
        assertThat(
                        tryCreate(
                                new RemoteTaskInfo(
                                        1,
                                        packageName,
                                        true,
                                        100,
                                        new HandoffOptions(false, false))))
                .isNull();
    }

    @Test
    public void testCreate_packageNotInstalledAndWebHandoffDisabled_returnsNull() {
        assertThat(
                        tryCreate(
                                new RemoteTaskInfo(
                                        1,
                                        "com.example.uninstalled_app",
                                        true,
                                        100,
                                        new HandoffOptions(true, true))))
                .isNull();
    }

    @Test
    public void testCreate_packageNotInstalledButHandoffAllowedWithoutPackage_returnsRemoteTask() {
        String packageName = "com.example.uninstalled_app";
        RemoteTask remoteTask =
                tryCreate(
                        new RemoteTaskInfo(
                                1, packageName, true, 100, new HandoffOptions(true, false)));

        assertThat(remoteTask).isNotNull();
        assertThat(remoteTask.getAssociationDisplayName()).isEqualTo(ASSOCIATION_DISPLAY_NAME);
        assertThat(remoteTask.getPackageName()).isEqualTo(BROWSER_PACKAGE_NAME);
        assertThat(remoteTask.getLabel()).isEqualTo(BROWSER_LABEL);
        assertThat(remoteTask.isHandoffEnabled()).isTrue();
        assertThat(remoteTask.isTaskInForeground()).isTrue();
    }

    private RemoteTask tryCreate(RemoteTaskInfo remoteTaskInfo) {
        return RemoteTaskFactory.create(
                mMockPackageManager,
                USER_ID,
                ASSOCIATION_ID,
                ASSOCIATION_DISPLAY_NAME,
                remoteTaskInfo);
    }
}
