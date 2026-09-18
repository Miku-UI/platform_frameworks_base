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
 * limitations under the License
 */

package com.android.server.backup.fullbackup;

import static com.android.server.backup.BackupManagerService.DEBUG;

import android.app.backup.BackupProgress;
import android.app.backup.BackupTransport;
import android.app.backup.IBackupObserver;
import android.util.Slog;

import com.android.server.backup.BackupManagerConstants;
import com.android.server.backup.transport.BackupTransportClient;
import com.android.server.backup.utils.BackupObserverUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * A multi-threaded pipeline for transferring full backup data from the application's
 * {@link android.app.backup.BackupAgent} to the {@link BackupTransport}.
 *
 * We use two pipes: appDataToInternalBuffer pipe and internalBufferToTransport pipe.
 *
 * The appDataToInternalBuffer copier continuously reads the data written by the app and writes it
 * to a PipedInputStream.
 *
 * The middle thread (this one) reads up to {@link #mBufferSize} chunks from that pipe into the
 * internal buffer, writes the contents to internalBufferToTransport pipe, and calls {@link
 * BackupTransport#sendBackupData(int)} with up to {@link #mBufferSize} read size bytes.
 *
 * The internalBufferToTransport copier continuously writes anything written to it to a {@link
 * android.os.ParcelFileDescriptor} pipe which the transport holds the other end of.
 *
 * This design has two main benefits:
 *
 * 1) It avoids idle time that would be caused by the one thread sequentially reading from the
 * app, writing to the transport pipe, and calling {@link BackupTransport#sendBackupData(int)}. For
 * example the app side would get blocked and the app's thread potentially put to sleep while we are
 * waiting for sendBackupData to finish (i.e. the transport to finish processing the data).
 *
 * 2) We get to call {@link BackupTransport#sendBackupData(int)} with more than the 64KB max
 * Linux pipe size. This means fewer blocking sendBackupData calls that are a massive bottleneck in
 * performance.
 *
 * Note that we have three buffers: the internal buffer and one each inside the
 * appDataToInternalBuffer pipe and internalBufferToTransport pipe. So this class will use 3x {@link
 * #mBufferSize} of memory.
 */
public class MultiThreadedTransportDataSender implements Callable<SendBackupDataResult> {
    private static final String TAG = "MTTDS";

    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private final BackupTransportClient mTransport;
    private final IBackupObserver mBackupObserver;
    private final String mPackageName;
    private final long mPreflightResult;
    private final Object mCancelLock;
    private final BooleanSupplier mCancellationChecker;
    private final int mBufferSize;

    public MultiThreadedTransportDataSender(
            InputStream inputStream,
            OutputStream outputStream,
            BackupTransportClient transport,
            IBackupObserver backupObserver,
            String packageName,
            long preflightResult,
            Object cancelLock,
            BooleanSupplier cancellationChecker,
            int bufferSize) {
        mInputStream = inputStream;
        mOutputStream = outputStream;
        mTransport = transport;
        mBackupObserver = backupObserver;
        mPackageName = packageName;
        mPreflightResult = preflightResult;
        mCancelLock = cancelLock;
        mCancellationChecker = cancellationChecker;
        mBufferSize = bufferSize;
    }

    @Override
    public SendBackupDataResult call() throws Exception {
        return sendAllDataToTransportBlocking();
    }

    /**
     * Sends all data from the backup agent to the transport, blocking until completion.
     *
     * <p>This method initiates the data transfer by starting the reader and writer threads. It then
     * waits for the transfer to complete or for an error to occur.
     *
     * <p>If an error occurs in either the reader or writer thread, the operation is cancelled and
     * the error is propagated.
     *
     * @return A {@link SendBackupDataResult} containing the status of the backup and the total
     *     number of bytes transferred.
     * @throws IOException If an I/O error occurs during the data transfer.
     */
    private SendBackupDataResult sendAllDataToTransportBlocking() throws IOException {
        byte[] buffer = new byte[mBufferSize];
        long totalRead = 0;
        int backupPackageStatus = BackupTransport.TRANSPORT_OK;

        final AtomicBoolean shouldStopDataCopierThreads = new AtomicBoolean(false);
        final PipedOutputStream appDataToInternalBufferOutputStream = new PipedOutputStream();
        final PipedInputStream appDataToInternalBufferInputStream =
                new PipedInputStream(appDataToInternalBufferOutputStream, mBufferSize);
        final AtomicBoolean appToInternalBufferIoSuccessful = new AtomicBoolean(true);

        final PipedOutputStream internalBufferToTransportOutputStream = new PipedOutputStream();
        final PipedInputStream internalBufferToTransportInputStream =
                new PipedInputStream(internalBufferToTransportOutputStream, mBufferSize);
        final AtomicBoolean internalBufferToTransportIoSuccessful = new AtomicBoolean(true);

        // A new thread that reads from BackupAgent's pipe is created. We close the pipe
        // after the thread is done since otherwise it won't be ever closed.
        new Thread(
                        createAppDataToInternalBufferCopier(
                                appDataToInternalBufferOutputStream,
                                shouldStopDataCopierThreads,
                                appToInternalBufferIoSuccessful),
                        "backup-agent-reader")
                .start();

        // A new thread that writes to the transport's pipe is created. We don't close the
        // pipe here to match the behavior of the single-threaded implementation.
        new Thread(
                        createInternalBufferToTransportCopier(
                                internalBufferToTransportInputStream,
                                shouldStopDataCopierThreads,
                                internalBufferToTransportIoSuccessful),
                        "transport-writer")
                .start();

        int nRead = 0;
        try {
            do {
                if (!appToInternalBufferIoSuccessful.get()) {
                    shouldStopDataCopierThreads.set(true);
                    break;
                }
                try {
                    nRead = appDataToInternalBufferInputStream.read(buffer);
                } catch (IOException e) {
                    appToInternalBufferIoSuccessful.set(false);
                    shouldStopDataCopierThreads.set(true);
                    break;
                }

                // Notify the inbound stream to make sure that the backup agent thread keeps reading
                // until it fills the buffer. It is actually important to notify here because the
                // backup agent thread is faster than current & transport-writer threads so it gets
                // blocked on write() calls. So if we don't notify here, a lot of time would be
                // wasted by the backup agent thread waiting.
                synchronized (appDataToInternalBufferInputStream) {
                    appDataToInternalBufferInputStream.notifyAll();
                }

                if (DEBUG) {
                    Slog.v(TAG, "in.read(buffer) from app: " + nRead);
                }
                if (nRead > 0) {
                    if (!internalBufferToTransportIoSuccessful.get()) {
                        shouldStopDataCopierThreads.set(true);
                        break;
                    }
                    try {

                        // We expect that the transport-writer thread is currently blocked on
                        // read() call because there is nothing to read. Also worth mentioning that
                        // the call to sendBackupData() below is blocking and it won't return until
                        // the transport-writer thread has written the data to the transport pipe in
                        // the current iteration.
                        internalBufferToTransportOutputStream.write(buffer, 0, nRead);

                        // Flush the output stream to make sure that the transport-writer thread
                        // doesn't get blocked on read() calls. This is important because in most of
                        // the times transport-writer thread reach the read() call before the write
                        // call above and gets blocked on it until the timeout because there is
                        // currently nothing to read. Thus if we don't flush here, a lot of time
                        // will be wasted by the transport-writer thread waiting.
                        internalBufferToTransportOutputStream.flush();

                    } catch (IOException e) {
                        internalBufferToTransportIoSuccessful.set(false);
                        shouldStopDataCopierThreads.set(true);
                        break;
                    }
                    totalRead += nRead;
                    synchronized (mCancelLock) {
                        if (!mCancellationChecker.getAsBoolean()) {
                            try {
                                backupPackageStatus = mTransport.sendBackupData(nRead);
                            } catch (Exception e) {
                                Slog.w(TAG, "Exception calling sendBackupData", e);
                                internalBufferToTransportIoSuccessful.set(false);
                                shouldStopDataCopierThreads.set(true);
                                break;
                            }
                        }
                    }
                    if (mBackupObserver != null && mPreflightResult > 0) {
                        BackupObserverUtils.sendBackupOnUpdate(
                                mBackupObserver,
                                mPackageName,
                                new BackupProgress(mPreflightResult, totalRead));
                    }
                }
            } while (nRead > 0 && backupPackageStatus == BackupTransport.TRANSPORT_OK);
        } finally {
            try {
                appDataToInternalBufferInputStream.close();
            } catch (IOException e) {
                Slog.w(TAG, "Error closing appDataToInternalBufferInputStream", e);
                appToInternalBufferIoSuccessful.set(false);
                shouldStopDataCopierThreads.set(true);
            }
            try {
                internalBufferToTransportOutputStream.close();
                internalBufferToTransportInputStream.close();
            } catch (IOException e) {
                Slog.w(TAG, "Error closing internalBufferToTransportInputStream/OutputStream", e);
                internalBufferToTransportIoSuccessful.set(false);
                shouldStopDataCopierThreads.set(true);
            }

            if (backupPackageStatus != BackupTransport.TRANSPORT_OK) {
                shouldStopDataCopierThreads.set(true);
            }
            if (!appToInternalBufferIoSuccessful.get()) {
                backupPackageStatus = BackupTransport.AGENT_ERROR;
            }
            if (!internalBufferToTransportIoSuccessful.get()) {
                backupPackageStatus = BackupTransport.TRANSPORT_ERROR;
            }
        }

        return new SendBackupDataResult(backupPackageStatus, totalRead);
    }

    Runnable createAppDataToInternalBufferCopier(
            PipedOutputStream appDataToInternalBufferOutputStream,
            AtomicBoolean shouldStopCopierThreads,
            AtomicBoolean appDataToInternalBufferIoSuccessful) {
        return new FullBackupDataCopier(
                mInputStream,
                appDataToInternalBufferOutputStream,
                shouldStopCopierThreads,
                appDataToInternalBufferIoSuccessful,
                BackupManagerConstants.DEFAULT_FULL_BACKUP_TRANSPORT_READ_SIZE,
                /* closeOutput= */ true,
                "PFTBT.appDataToInternalBuffer");
    }

    Runnable createInternalBufferToTransportCopier(
            PipedInputStream internalBufferToTransportInputStream,
            AtomicBoolean shouldStopCopierThreads,
            AtomicBoolean internalBufferToTransportIoSuccessful) {
        return new FullBackupDataCopier(
                internalBufferToTransportInputStream,
                mOutputStream,
                shouldStopCopierThreads,
                internalBufferToTransportIoSuccessful,
                mBufferSize,
                /* closeOutput= */ false,
                "PFTBT.internalBufferToTransport");
    }
}
