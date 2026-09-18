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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.backup.BackupProgress;
import android.app.backup.BackupTransport;
import android.app.backup.IBackupObserver;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.backup.transport.BackupTransportClient;

import java.util.function.BooleanSupplier;

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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class MultiThreadedTransportDataSenderTest {

    private static final String PACKAGE_NAME = "com.example.package";
    private static final int BUFFER_SIZE = 1024;

    @Mock private BackupTransportClient mTransport;
    @Mock private IBackupObserver mObserver;
    @Mock private BooleanSupplier mCancellationChecker;

    private final Object mCancelLock = new Object();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mCancellationChecker.getAsBoolean()).thenReturn(false);
    }

    @Test
    public void testCall_success() throws Exception {
        byte[] data = new byte[BUFFER_SIZE * 2 + 10]; // 2 full buffers + 10 bytes
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        InputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(mTransport.sendBackupData(anyInt())).thenReturn(BackupTransport.TRANSPORT_OK);

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE);

        SendBackupDataResult result = sender.call();

        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.TRANSPORT_OK);
        assertThat(result.getTotalRead()).isEqualTo(data.length);
        assertThat(outputStream.toByteArray()).isEqualTo(data);

        verify(mTransport, times(3)).sendBackupData(anyInt()); // 1024, 1024, 10
    }

    @Test
    public void testCall_agentError() throws Exception {
        InputStream inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Agent read error");
            }
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("Agent read error");
            }
        };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE);

        SendBackupDataResult result = sender.call();

        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.AGENT_ERROR);
    }

    @Test
    public void testCall_transportError_sendBackupData()
            throws Exception {
        byte[] data = new byte[100];
        InputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(mTransport.sendBackupData(anyInt())).thenReturn(BackupTransport.TRANSPORT_ERROR);

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE);

        SendBackupDataResult result = sender.call();

        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.TRANSPORT_ERROR);
    }

    @Test
    public void testCall_transportError_outputStream() throws Exception {
        byte[] data = new byte[100];
        InputStream inputStream = new ByteArrayInputStream(data);
        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Transport write error");
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("Transport write error");
            }
        };

        when(mTransport.sendBackupData(anyInt())).thenReturn(BackupTransport.TRANSPORT_OK);

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE);

        SendBackupDataResult result = sender.call();

        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.TRANSPORT_ERROR);
    }

    @Test
    public void testCall_transportThrowsException() throws Exception {
        byte[] data = new byte[100];
        InputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(mTransport.sendBackupData(anyInt()))
                .thenThrow(new RuntimeException("Transport error"));

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE);

        SendBackupDataResult result = sender.call();

        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.TRANSPORT_ERROR);
    }

    @Test
    public void testCall_transportReturnsErrorAndAgentReadFails()
            throws Exception {
        // Orchestrate agent failure:
        // 1. Success read (allows sendBackupData to be called)
        // 2. Wait for signal
        // 3. Fail
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch agentErrorLatch = new CountDownLatch(1);
        InputStream inputStream = new InputStream() {
            int count = 0;
            @Override
            public int read() throws IOException { return 0; }
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (count == 0) {
                    count++;
                    return 10; // First read success
                }
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for signal");
                }
                throw new IOException("Agent read error");
            }
        };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(mTransport.sendBackupData(anyInt())).thenAnswer(invocation -> {
            latch.countDown(); // Signal agent to fail
            agentErrorLatch.await(5, TimeUnit.SECONDS);
            return BackupTransport.TRANSPORT_ERROR;
        });

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE) {
            @Override
            Runnable createAppDataToInternalBufferCopier(
                    PipedOutputStream appDataToInternalBufferOutputStream,
                    AtomicBoolean shouldStopCopierThreads,
                    AtomicBoolean appDataToInternalBufferIoSuccessful) {
                Runnable runnable =
                        super.createAppDataToInternalBufferCopier(
                                appDataToInternalBufferOutputStream,
                                shouldStopCopierThreads,
                                appDataToInternalBufferIoSuccessful);
                return () -> {
                    runnable.run();
                    agentErrorLatch.countDown();
                };
            }
        };

        SendBackupDataResult result = sender.call();

        // Agent error takes precedence over Transport return error
        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.AGENT_ERROR);
    }

    @Test
    public void testCall_agentReadFailsAndTransportWriteFails()
            throws Exception {
        CountDownLatch transportWriteLatch = new CountDownLatch(1);
        CountDownLatch transportThreadFinishedLatch = new CountDownLatch(1);

        // InputStream fails on second read
        InputStream inputStream = new InputStream() {
            int count = 0;
            @Override
            public int read() { return 0; }
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (count == 0) {
                    count++;
                    return 10;
                }
                // Wait for the writer to attempt writing before failing
                try {
                    transportWriteLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for signal");
                }
                throw new IOException("Agent fail");
            }
        };

        // OutputStream fails immediately
        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                transportWriteLatch.countDown();
                throw new IOException("Transport fail");
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                transportWriteLatch.countDown();
                throw new IOException("Transport fail");
            }
        };

        // Delay sendBackupData to allow Transport writer thread to fail
        when(mTransport.sendBackupData(anyInt())).thenAnswer(invocation -> {
            // Wait for the writer thread to finish (fail)
            transportThreadFinishedLatch.await(5, TimeUnit.SECONDS);
            return BackupTransport.TRANSPORT_OK;
        });

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, 0, mCancelLock,
                mCancellationChecker, BUFFER_SIZE) {
            @Override
            Runnable createInternalBufferToTransportCopier(
                    PipedInputStream internalBufferToTransportInputStream,
                    AtomicBoolean shouldStopCopierThreads,
                    AtomicBoolean internalBufferToTransportIoSuccessful) {
                Runnable runnable =
                        super.createInternalBufferToTransportCopier(
                                internalBufferToTransportInputStream,
                                shouldStopCopierThreads,
                                internalBufferToTransportIoSuccessful);
                return () -> {
                    runnable.run();
                    transportThreadFinishedLatch.countDown();
                };
            }
        };

        SendBackupDataResult result = sender.call();

        // Transport write error takes precedence over Agent error
        assertThat(result.getBackupPackageStatus()).isEqualTo(BackupTransport.TRANSPORT_ERROR);
    }

    @Test
    public void testCall_notifiesObserver() throws Exception {
        byte[] data = new byte[100];
        InputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        long preflightResult = 1000;

        when(mTransport.sendBackupData(anyInt())).thenReturn(BackupTransport.TRANSPORT_OK);

        MultiThreadedTransportDataSender sender = new MultiThreadedTransportDataSender(
                inputStream, outputStream, mTransport, mObserver, PACKAGE_NAME, preflightResult,
                mCancelLock, mCancellationChecker, BUFFER_SIZE);

        sender.call();

        verify(mObserver).onUpdate(eq(PACKAGE_NAME), any(BackupProgress.class));
    }
}
