/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.pm;

import static android.os.Trace.TRACE_TAG_PACKAGE_MANAGER;

import static com.android.server.pm.InitAppsHelper.ScanParams;

import android.os.Process;
import android.os.Trace;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.parsing.PackageParser2;
import com.android.internal.pm.parsing.PackageParserException;
import com.android.internal.pm.parsing.pkg.ParsedPackage;
import com.android.internal.util.ConcurrentUtils;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Helper class for parallel parsing of packages using {@link PackageParser2}.
 * <p>Parsing requests are processed by a thread-pool of {@link #MAX_THREADS}.
 * At any time, at most {@link #QUEUE_CAPACITY} results are kept in RAM</p>
 */
class ParallelPackageParser {

    private static final int QUEUE_CAPACITY = 30;
    private static final int MAX_THREADS = 4;

    private volatile String mInterruptedInThread;

    private final BlockingQueue<ParseResult> mQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    static ExecutorService makeExecutorService() {
        return ConcurrentUtils.newFixedThreadPool(MAX_THREADS, "package-parsing-thread",
                Process.THREAD_PRIORITY_FOREGROUND);
    }

    private final PackageParser2 mPackageParser;

    private final ExecutorService mExecutorService;

    ParallelPackageParser(PackageParser2 packageParser, ExecutorService executorService) {
        mPackageParser = packageParser;
        mExecutorService = executorService;
    }

    static class ParseResult {

        ParsedPackage parsedPackage; // Parsed package
        File scanFile; // File that was parsed
        Throwable throwable; // Set if an error occurs during parsing
        ScanParams scanParams; // Parameters used when scanning and parsing this scanFile

        @Override
        public String toString() {
            return "ParseResult{" +
                    "parsedPackage=" + parsedPackage +
                    ", scanFile=" + scanFile +
                    ", throwable=" + throwable +
                    '}';
        }
    }

    /**
     * Take the parsed package from the parsing queue, waiting if necessary until the element
     * appears in the queue.
     * @return parsed package
     */
    public ParseResult take() {
        try {
            if (mInterruptedInThread != null) {
                throw new InterruptedException("Interrupted in " + mInterruptedInThread);
            }
            return mQueue.take();
        } catch (InterruptedException e) {
            // We cannot recover from interrupt here
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Submits the file for parsing
     * @param scanFile file to scan
     * @param scanParams scan parameters
     */
    public void submit(File scanFile, ScanParams scanParams) {
        mExecutorService.submit(() -> {
            ParseResult pr = new ParseResult();
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parallel parsePackage [" + scanFile + "]");
            try {
                pr.scanFile = scanFile;
                pr.scanParams = scanParams;
                pr.parsedPackage = parsePackage(scanFile, scanParams.parseFlags);
            } catch (Throwable e) {
                pr.throwable = e;
            } finally {
                Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
            }
            try {
                mQueue.put(pr);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Propagate result to callers of take().
                // This is helpful to prevent main thread from getting stuck waiting on
                // ParallelPackageParser to finish in case of interruption
                mInterruptedInThread = Thread.currentThread().getName();
            }
        });
    }

    static class OrderedResult {
        public final File scanFile; // The file that will be parsed. This is saved in case it needs
                                    // to be logged in an error.
        public final Future<ParseResult> future;
        OrderedResult(File scanFile, Future<ParseResult> future) {
            this.scanFile = scanFile;
            this.future = future;
        }
    }

    /**
     * Submits the file for parsing. The return result should be handled directly by the caller
     * instead of being pulled from take() later.
     * @param scanFile file to scan
     * @param scanParams scan parameters
     */
    public OrderedResult orderedSubmit(File scanFile, ScanParams scanParams) {
        return new OrderedResult(scanFile, mExecutorService.submit(() -> {
            ParseResult pr = new ParseResult();
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parallel parsePackage [" + scanFile + "]");
            try {
                pr.scanFile = scanFile;
                pr.scanParams = scanParams;
                pr.parsedPackage = parsePackage(scanFile, scanParams.parseFlags);
            } catch (Throwable e) {
                pr.throwable = e;
            } finally {
                Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
            }
            return pr;
        }));
    }

    @VisibleForTesting
    protected ParsedPackage parsePackage(File scanFile, int parseFlags)
            throws PackageManagerException {
        try {
            return mPackageParser.parsePackage(scanFile, parseFlags, true);
        } catch (PackageParserException e) {
            throw new PackageManagerException(e.error, e.getMessage(), e);
        }
    }
}
