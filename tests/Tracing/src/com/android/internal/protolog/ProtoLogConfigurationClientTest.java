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

package com.android.internal.protolog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import com.android.internal.protolog.common.IProtoLogGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutorService;

@Presubmit
@RunWith(JUnit4.class)
public class ProtoLogConfigurationClientTest {

    @Mock
    private ProtoLogConfigurationClient.LogcatToggleListener mLogcatToggleListener;

    @Mock
    private IProtoLogConfigurationService mConfigurationService;

    @Mock
    private ExecutorService mExecutorService;

    private ProtoLogConfigurationClient mProtoLogConfigurationClient;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Execute tasks immediately on the calling thread
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mExecutorService).execute(any(Runnable.class));

        mProtoLogConfigurationClient = new ProtoLogConfigurationClient("viewer_config.json",
            mLogcatToggleListener, mExecutorService);
    }

    @Test
    public void start_registersClient() throws RemoteException {
        IProtoLogGroup[] groups =
                new IProtoLogGroup[] {
                    mockGroup("Group1", false),
                    mockGroup("Group2", true),
                    mockGroup("Group3", false),
                };

        mProtoLogConfigurationClient.start(groups, true, mConfigurationService);

        IProtoLogConfigurationService.RegisterClientArgs expectedArgs =
                new IProtoLogConfigurationService.RegisterClientArgs();
        expectedArgs.groups = new String[] {"Group1", "Group2", "Group3"};
        expectedArgs.groupsDefaultLogcatStatus = new boolean[] {false, true, false};
        expectedArgs.viewerConfigFile = "viewer_config.json";
        verify(mConfigurationService)
                .registerClient(eq(mProtoLogConfigurationClient), eq(expectedArgs));
    }

    @Test
    public void start_doesNotRegisterTwice() throws RemoteException {
        IProtoLogGroup[] groups = new IProtoLogGroup[] {};
        mProtoLogConfigurationClient.start(groups, true, mConfigurationService);
        mProtoLogConfigurationClient.start(groups, true, mConfigurationService);

        verify(mConfigurationService).registerClient(eq(mProtoLogConfigurationClient), any());
    }

    @Test
    public void addGroups_registersGroups() throws RemoteException {
        IProtoLogGroup[] initialGroups = new IProtoLogGroup[] {};
        mProtoLogConfigurationClient.start(initialGroups, true, mConfigurationService);

        IProtoLogGroup[] newGroups =
                new IProtoLogGroup[] {
                    mockGroup("Group1", true), mockGroup("Group2", false), mockGroup("Group3", true)
                };
        mProtoLogConfigurationClient.addGroups(newGroups);

        IProtoLogConfigurationService.RegisterGroupsArgs expectedArgs =
                new IProtoLogConfigurationService.RegisterGroupsArgs();
        expectedArgs.groups = new String[] {"Group1", "Group2", "Group3"};
        expectedArgs.groupsDefaultLogcatStatus = new boolean[] {true, false, true};
        verify(mConfigurationService)
                .registerGroups(eq(mProtoLogConfigurationClient), eq(expectedArgs));
    }

    @Test
    public void toggleLogcat_notifiesListener() {
        String[] groups = new String[] { "Group1" };
        mProtoLogConfigurationClient.toggleLogcat(true, groups);

        verify(mLogcatToggleListener).onLogcatToggle(true, groups);
    }

    @Test
    public void stop_unregistersClient() throws RemoteException {
        // First start to set service
        mProtoLogConfigurationClient.start(new IProtoLogGroup[0], true, mConfigurationService);

        mProtoLogConfigurationClient.stop();

        verify(mConfigurationService).unregisterClient(eq(mProtoLogConfigurationClient));
    }

    private IProtoLogGroup mockGroup(String name, boolean logToLogcat) {
        IProtoLogGroup group = mock(IProtoLogGroup.class);
        when(group.name()).thenReturn(name);
        when(group.isLogToLogcat()).thenReturn(logToLogcat);
        return group;
    }
}
