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

package com.android.server.backup.fullbackup;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class FullBackupDataCopierTest {

    @Mock private OutputStream mOutputStream;
    @Mock private InputStream mInputStream;

    private AtomicBoolean mStopRunning;
    private AtomicBoolean mIsIoSuccessful;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mStopRunning = new AtomicBoolean(false);
        mIsIoSuccessful = new AtomicBoolean(true);
    }

    @Test
    public void testRun_copiesDataSuccessfully() throws Exception {
        byte[] data = "test data".getBytes();
        InputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        FullBackupDataCopier copier = new FullBackupDataCopier(
                inputStream, outputStream, mStopRunning, mIsIoSuccessful, 1024, false, "test");
        copier.run();

        assertThat(outputStream.toByteArray()).isEqualTo(data);
        assertThat(mIsIoSuccessful.get()).isTrue();
    }

    @Test
    public void testRun_handlesReadException() throws Exception {
        when(mInputStream.read(any())).thenThrow(new IOException("Read error"));

        FullBackupDataCopier copier = new FullBackupDataCopier(
                mInputStream, mOutputStream, mStopRunning, mIsIoSuccessful, 1024, false, "test");
        copier.run();

        assertThat(mIsIoSuccessful.get()).isFalse();
    }

    @Test
    public void testRun_handlesWriteException() throws Exception {
        when(mInputStream.read(any())).thenReturn(5); // Simulate read success
        doThrow(new IOException("Write error")).when(mOutputStream)
                .write(any(), anyInt(), anyInt());

        FullBackupDataCopier copier = new FullBackupDataCopier(
                mInputStream, mOutputStream, mStopRunning, mIsIoSuccessful, 1024, false, "test");
        copier.run();

        assertThat(mIsIoSuccessful.get()).isFalse();
    }

    @Test
    public void testRun_closesOutputWhenRequested() throws Exception {
        byte[] data = "test data".getBytes();
        InputStream inputStream = new ByteArrayInputStream(data);

        FullBackupDataCopier copier = new FullBackupDataCopier(
                inputStream, mOutputStream, mStopRunning, mIsIoSuccessful, 1024, true, "test");
        copier.run();

        verify(mOutputStream).close();
    }

    @Test
    public void testRun_doesNotCloseOutputWhenNotRequested() throws Exception {
        byte[] data = "test data".getBytes();
        InputStream inputStream = new ByteArrayInputStream(data);

        FullBackupDataCopier copier = new FullBackupDataCopier(
                inputStream, mOutputStream, mStopRunning, mIsIoSuccessful, 1024, false, "test");
        copier.run();

        verify(mOutputStream, never()).close();
    }

    @Test
    public void testRun_stopsWhenStopRunningIsSet() throws Exception {
        when(mInputStream.read(any())).thenAnswer(invocation -> {
            mStopRunning.set(true);
            return 5;
        });

        FullBackupDataCopier copier = new FullBackupDataCopier(
                mInputStream, mOutputStream, mStopRunning, mIsIoSuccessful, 1024, false, "test");
        copier.run();

        verify(mOutputStream, never()).write(any(), anyInt(), anyInt());
    }

    @Test
    public void testRun_doesNotReadWhenStopRunningIsInitiallyTrue() throws Exception {
        mStopRunning.set(true);

        FullBackupDataCopier copier = new FullBackupDataCopier(
                mInputStream, mOutputStream, mStopRunning, mIsIoSuccessful, 1024, false, "test");
        copier.run();

        verify(mInputStream, never()).read(any());
    }
}
