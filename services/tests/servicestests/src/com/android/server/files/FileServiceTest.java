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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManagerInternal;
import android.os.UserHandle;
import android.os.storage.IFileService;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
import android.os.storage.operations.sources.OperationSource;
import android.os.storage.operations.targets.OperationTarget;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class FileServiceTest {

    private static final String DEFAULT_PACKAGE_NAME = "com.android.server.files.test";

    private FileService mFileService;
    private IFileService.Stub mBinderService;
    private TestFileOperationDispatcher mTestDispatcher;

    @Mock private Context mContext;
    @Mock private PackageManagerInternal mPackageManagerInternal;
    @Mock private FileOperationProcessor mMockProcessor;
    @Mock private OperationSource mMockSource;
    @Mock private OperationTarget mMockTarget;

    private static class TestFileOperationDispatcher extends FileOperationDispatcher {
        private FileOperationProcessor mProcessorToReturn;
        // Capture dispatch calls
        final List<FileOperationRequest> mDispatchedRequests = new ArrayList<>();
        FileOperationProcessor.StatusCallback mLastCallback;

        @Override
        FileOperationProcessor findProcessor(FileOperationRequest req) {
            return mProcessorToReturn;
        }

        @Override
        public void dispatch(
                FileService.RequestContext ctx, FileOperationProcessor.StatusCallback callback) {
            mDispatchedRequests.add(ctx.request());
            mLastCallback = callback;
            // Don't actually run anything async
        }

        void setProcessor(FileOperationProcessor processor) {
            mProcessorToReturn = processor;
        }
    }

    private static final int TEST_UID = 10001;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);

        mTestDispatcher = new TestFileOperationDispatcher();

        mFileService =
                new FileService(
                        mContext,
                        new FileService.Injector() {
                            @Override
                            public FileOperationDispatcher getFileOperationDispatcher() {
                                return mTestDispatcher;
                            }

                            @Override
                            public int getCallingUid() {
                                return TEST_UID;
                            }
                        });
        mBinderService = mFileService.mBinderService;
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
    }

    private FileOperationRequest createValidRequest() {
        when(mMockSource.isValid()).thenReturn(true);
        when(mMockTarget.isValid()).thenReturn(true);
        // Default to returning the mock processor
        mTestDispatcher.setProcessor(mMockProcessor);
        return new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                .setSource(mMockSource)
                .setTarget(mMockTarget)
                .build();
    }

    @Test
    public void testEnqueueOperation_success() throws Exception {
        FileOperationRequest request = createValidRequest();

        FileOperationEnqueueResult result =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getRequestId()).isNotNull();
        assertThat(result.getErrorCode()).isEqualTo(FileOperationResult.ERROR_NONE);
        assertThat(mTestDispatcher.mDispatchedRequests).contains(request);
    }

    @Test
    public void testEnqueueOperation_invalidSource_returnsError() throws Exception {
        FileOperationRequest request = createValidRequest();
        when(mMockSource.isValid()).thenReturn(false);

        FileOperationEnqueueResult result =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(FileOperationResult.ERROR_UNSUPPORTED_SOURCE);
        assertThat(mTestDispatcher.mDispatchedRequests).isEmpty();
    }

    @Test
    public void testEnqueueOperation_invalidTarget_returnsError() throws Exception {
        FileOperationRequest request = createValidRequest();
        when(mMockTarget.isValid()).thenReturn(false);

        FileOperationEnqueueResult result =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(FileOperationResult.ERROR_UNSUPPORTED_TARGET);
        assertThat(mTestDispatcher.mDispatchedRequests).isEmpty();
    }

    @Test
    public void testEnqueueOperation_noProcessor_returnsError() throws Exception {
        FileOperationRequest request = createValidRequest();
        mTestDispatcher.setProcessor(null);

        FileOperationEnqueueResult result =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(FileOperationResult.ERROR_INVALID_REQUEST);
        assertThat(mTestDispatcher.mDispatchedRequests).isEmpty();
    }

    @Test
    public void testFetchResult_returnsCorrectResult() throws Exception {
        FileOperationRequest request = createValidRequest();
        FileOperationEnqueueResult enqueueResult =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);
        String requestId = enqueueResult.getRequestId();

        FileOperationResult result = mBinderService.fetchResult(requestId);

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getStatus()).isEqualTo(FileOperationResult.STATUS_QUEUED);
    }

    @Test
    public void testFetchResult_unknownId_returnsNull() throws Exception {
        FileOperationResult result = mBinderService.fetchResult("unknown_id");
        assertThat(result).isNull();
    }

    @Test
    public void testCompletionListener_receivesBroadcast() throws Exception {
        String packageName = "com.example.app";
        when(mPackageManagerInternal.isSameApp(
                        packageName, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(true);

        FileOperationRequest request = createValidRequest();
        FileOperationEnqueueResult enqueueResult =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);
        String requestId = enqueueResult.getRequestId();

        mBinderService.registerCompletionListener(requestId, packageName);

        // Simulate operation completion
        FileOperationResult result =
                new FileOperationResult.Builder(requestId, request)
                        .setStatus(FileOperationResult.STATUS_FINISHED)
                        .build();

        mTestDispatcher.mLastCallback.onResult(result);

        verify(mContext).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class));
    }

    @Test
    public void testUnregisterCompletionListener_stopsBroadcast() throws Exception {
        String packageName = "com.example.app";
        when(mPackageManagerInternal.isSameApp(
                        packageName, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(true);

        FileOperationRequest request = createValidRequest();
        FileOperationEnqueueResult enqueueResult =
                mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);
        String requestId = enqueueResult.getRequestId();

        mBinderService.registerCompletionListener(requestId, packageName);
        mBinderService.unregisterCompletionListener(requestId);

        // Simulate operation completion
        FileOperationResult result =
                new FileOperationResult.Builder(requestId, request)
                        .setStatus(FileOperationResult.STATUS_FINISHED)
                        .build();
        mTestDispatcher.mLastCallback.onResult(result);

        verify(mContext, never()).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class));
    }

    @Test(expected = SecurityException.class)
    public void testRegisterCompletionListener_wrongPackage_throwsException() throws Exception {
        String packageName = "com.other.app";
        when(mPackageManagerInternal.isSameApp(
                        packageName, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(false);

        mBinderService.registerCompletionListener("some_id", packageName);
    }

    @Test
    public void testResultHistory_evictionPolicy() throws Exception {
        // FileService.MAX_HISTORY_SIZE = 50 + 10 = 60
        int maxHistory = 60;

        // 1. Fill with completed items
        for (int i = 0; i < maxHistory; i++) {
            FileOperationRequest request = createValidRequest();
            FileOperationEnqueueResult res =
                    mBinderService.enqueueOperation(request, DEFAULT_PACKAGE_NAME);
            String id = res.getRequestId();
            // Manually finish it
            FileOperationResult finishResult =
                    new FileOperationResult.Builder(id, request)
                            .setStatus(FileOperationResult.STATUS_FINISHED)
                            .build();
            mTestDispatcher.mLastCallback.onResult(finishResult);
        }

        assertThat(mFileService.mResults.size()).isEqualTo(maxHistory);

        // 2. Add one more. Should evict eldest.
        FileOperationRequest lastRequest = createValidRequest();
        FileOperationEnqueueResult res =
                mBinderService.enqueueOperation(lastRequest, DEFAULT_PACKAGE_NAME);
        String lastId = res.getRequestId();
        // Finish it too
        mTestDispatcher.mLastCallback.onResult(
                new FileOperationResult.Builder(lastId, lastRequest)
                        .setStatus(FileOperationResult.STATUS_FINISHED)
                        .build());

        assertThat(mFileService.mResults.size()).isEqualTo(maxHistory);

        // 3. Test protection of active items
        mFileService.mResults.clear();
        mFileService.mPendingRequests.clear();

        // Add an active item (eldest)
        FileOperationEnqueueResult activeRes =
                mBinderService.enqueueOperation(createValidRequest(), DEFAULT_PACKAGE_NAME);
        String activeId = activeRes.getRequestId();
        // Do NOT finish it. It remains STATUS_QUEUED.

        // Add 'maxHistory' more finished items
        for (int i = 0; i < maxHistory; i++) {
            FileOperationRequest r = createValidRequest();
            FileOperationEnqueueResult enqueueResult =
                    mBinderService.enqueueOperation(r, DEFAULT_PACKAGE_NAME);
            mTestDispatcher.mLastCallback.onResult(
                    new FileOperationResult.Builder(enqueueResult.getRequestId(), r)
                            .setStatus(FileOperationResult.STATUS_FINISHED)
                            .build());
        }

        // Total should be 61 (1 active + 60 finished). The active one (eldest) should NOT be
        // removed.
        assertThat(mFileService.mResults.size()).isEqualTo(maxHistory + 1);
        assertThat(mFileService.mResults.containsKey(activeId)).isTrue();
    }

    @Test
    public void testEnqueueOperation_serviceBusy_returnsError() throws Exception {
        // Fill up the pending requests queue.
        // Our TestFileOperationDispatcher does not trigger callbacks,
        // so requests remain in mPendingRequests.
        for (int i = 0; i < 50; i++) {
            FileOperationEnqueueResult res =
                    mBinderService.enqueueOperation(createValidRequest(), DEFAULT_PACKAGE_NAME);
            assertThat(res.isSuccessful()).isTrue();
        }

        // The 51st request should fail with ERROR_BUSY
        FileOperationRequest extraRequest = createValidRequest();
        FileOperationEnqueueResult result =
                mBinderService.enqueueOperation(extraRequest, DEFAULT_PACKAGE_NAME);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(FileOperationResult.ERROR_BUSY);
    }
}
