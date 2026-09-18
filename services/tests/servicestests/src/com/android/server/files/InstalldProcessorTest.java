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

package com.android.server.files;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IInstalld;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.Installer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class InstalldProcessorTest {

    private InstalldProcessor mProcessor;
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private Installer mInstaller;
    @Mock private FileOperationProcessor.StatusCallback mCallback;

    private static final String TEST_PACKAGE = "com.example.app";
    private static final int TEST_UID = 10001;
    private static final int TEST_PCC_UID = 30001; // In FIRST_PCC_UID..LAST_PCC_UID range

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        mProcessor = new InstalldProcessor(mContext, mInstaller);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testProcess_moveAppData_success() throws Exception {
        ApplicationInfo aInfo = new ApplicationInfo();
        aInfo.packageName = TEST_PACKAGE;
        aInfo.pccUid = TEST_PCC_UID;
        aInfo.seInfo = "default";
        aInfo.volumeUuid = "vol1";
        when(mPackageManager.getApplicationInfoAsUser(eq(TEST_PACKAGE), any(), anyInt()))
                .thenReturn(aInfo);

        File sourceFile = new File("/data/user/0/" + TEST_PACKAGE + "/files/data.txt");
        AppDataFileSource source = new AppDataFileSource(sourceFile);
        PccTarget target = new PccTarget("subdir");
        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                        .setSource(source)
                        .setTarget(target)
                        .build();

        FileService.RequestContext ctx =
                new FileService.RequestContext("id1", request, TEST_UID, TEST_PACKAGE);

        mProcessor.process(ctx, mCallback);

        verify(mInstaller).moveAppDataPath(
                eq("vol1"),
                eq(sourceFile.getPath()),
                any(String.class),
                anyInt(),
                anyInt(),
                eq(aInfo.seInfo),
                anyInt(),
                anyInt(),
                any(IInstalld.IAppDataOperationCallback.class));
    }

    @Test
    public void testProcess_packageNotFound_fails() throws Exception {
        when(mPackageManager.getApplicationInfoAsUser(eq(TEST_PACKAGE), any(), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());

        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                        .setSource(new AppDataFileSource(new File("/path")))
                        .setTarget(new PccTarget())
                        .build();

        FileService.RequestContext ctx =
                new FileService.RequestContext("id1", request, TEST_UID, TEST_PACKAGE);

        mProcessor.process(ctx, mCallback);

        ArgumentCaptor<FileOperationResult> resultCaptor =
                ArgumentCaptor.forClass(FileOperationResult.class);
        verify(mCallback).onResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus())
                .isEqualTo(FileOperationResult.STATUS_FAILED);
        assertThat(resultCaptor.getValue().getErrorCode())
                .isEqualTo(FileOperationResult.ERROR_INVALID_REQUEST);
    }

    @Test
    public void testProcess_invalidPccUid_fails() throws Exception {
        ApplicationInfo aInfo = new ApplicationInfo();
        aInfo.pccUid = 12345; // Not a PCC UID
        when(mPackageManager.getApplicationInfoAsUser(eq(TEST_PACKAGE), any(), anyInt()))
                .thenReturn(aInfo);

        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                        .setSource(new AppDataFileSource(new File("/path")))
                        .setTarget(new PccTarget())
                        .build();

        FileService.RequestContext ctx =
                new FileService.RequestContext("id1", request, TEST_UID, TEST_PACKAGE);

        mProcessor.process(ctx, mCallback);

        ArgumentCaptor<FileOperationResult> resultCaptor =
                ArgumentCaptor.forClass(FileOperationResult.class);
        verify(mCallback).onResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus())
                .isEqualTo(FileOperationResult.STATUS_FAILED);
    }

    @Test
    public void testCanHandle_supportedPair_returnsTrue() {
        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                        .setSource(new AppDataFileSource(new File("/path")))
                        .setTarget(new PccTarget())
                        .build();
        assertThat(mProcessor.canHandle(request)).isTrue();
    }
}
