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
package android.platform.test.ravenwood;

import static org.junit.Assert.assertNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.RavenwoodActivityDriver;
import android.os.Looper;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.common.RavenwoodInternalUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RavenwoodBugreportManager {
    private static final String TAG = RavenwoodDriver.TAG;
    private static final String TAG_BUGREPORT = "Ravenwood-bugreport";

    private RavenwoodBugreportManager() {
    }

    private static void dumpStacks(
            @NonNull PrintStream out,
            @Nullable Thread exceptionThread, @Nullable
            Throwable throwable) {
        out.println("-----BEGIN ALL THREAD STACKS-----");

        var desc = RavenwoodErrorHandler.getCurrentDescription();
        if (desc != null) {
            out.format("Running test: %s:%s#%s\n",
                    RavenwoodEnvironment.getInstance().getTestModuleName(),
                    desc.getClassName(), desc.getMethodName());
        }

        var stacks = Thread.getAllStackTraces();
        var threads = stacks.keySet().stream().sorted(
                Comparator.comparingLong(Thread::threadId)).collect(Collectors.toList());

        // Put the test and the main thread at the top.
        var testThread = RavenwoodAwareTestRunner.sTestThread;
        var mainThread = Looper.getMainLooper().getThread();
        if (mainThread != null) {
            threads.remove(mainThread);
            threads.addFirst(mainThread);
        }
        threads.remove(testThread);
        threads.addFirst(testThread);
        // Put the exception thread at the top.
        // Also inject the stacktrace from the exception.
        if (exceptionThread != null) {
            threads.remove(exceptionThread);
            threads.add(0, exceptionThread);
            stacks.put(exceptionThread, throwable.getStackTrace());
        }
        for (var th : threads) {
            out.println();

            out.print("Thread");
            if (th == exceptionThread) {
                out.print(" [** EXCEPTION THREAD **]");
            }
            out.print(": " + th.getName() + " / " + th);
            out.println();

            for (StackTraceElement e :  stacks.get(th)) {
                out.println("\tat " + e);
            }
        }
        out.println("-----END ALL THREAD STACKS-----");
    }

    /**
     * All bugreports created in the temp dir, which we print at the end of each test class.
     */
    @GuardedBy("sBugreportFiles")
    private static final List<File> sBugreportFiles = new ArrayList<>();

    /**
     * Print all bugreport filenames generated in the run.
     */
    public static void dumpBugreportFiles() {
        synchronized (sBugreportFiles) {
            var size = sBugreportFiles.size();
            if (size == 0) {
                return;
            }

            RavenwoodLogManager.printRawString(
                    size + " bugreport(s) created throughout the run\n");
            for (var i = 0; i < sBugreportFiles.size(); i++) {
                RavenwoodLogManager.printRawString(
                        "Bugreport #" + i + ": file://" + sBugreportFiles.get(i) + "\n");
            }
        }
    }

    private static volatile File sLastBugreportFile;

    @VisibleForTesting
    public static void resetLastBugreportFile() {
        sLastBugreportFile = null;
    }

    @VisibleForTesting
    public static String readLastBugreportFile() throws IOException {
        var file = sLastBugreportFile;
        assertNotNull("sLastBugreportFile", file);

        return Files.readString(file.toPath());
    }

    /**
     * Create a bugreport file in a given directory.
     * @param forUser if true, we store the filename in {@link #sBugreportFiles} and
     * print the filename in the log. This will be false when we create a file as an artifact,
     * which will be removed by the infra, so there's no point showing it to the user.
     */
    @NonNull
    private static OutputStream createBugreportFile(
            File path, LocalDateTime now, boolean forUser) {
        File outputFile = new File(path.getAbsolutePath(),
                "ravenwood-bugreport_" + now.format(RavenwoodUtils.LOG_FILE_TIMESTAMP_FORMATTER)
                        + ".txt");

        try {
            var ret = new FileOutputStream(outputFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    Log.i(TAG, "Saved bugreport as file://" + outputFile);
                }
            };
            Log.i(TAG, "Saving bugreport as file://" + outputFile);

            if (forUser) {
                synchronized (sBugreportFiles) {
                    sBugreportFiles.add(outputFile);
                    sLastBugreportFile = outputFile;
                }
            }

            return ret;
        } catch (IOException e) {
            Log.w("Failed to create bugreport file. File=" + outputFile, e);

            // Return a dummy stream.
            return RavenwoodInternalUtils.NULL_OUTPUT_STREAM;
        }
    }

    public static void doBugreport(@NonNull String message,
            @Nullable Thread exceptionThread, @Nullable Throwable throwable, boolean killSelf) {
        var logactOut = RavenwoodLogManager.getLogcatOut(TAG_BUGREPORT, Log.ERROR);
        try {
            var outs = new ArrayList<OutputStream>();
            outs.add(logactOut);

            // When we take a bugreport, we create two files.
            // 1. As a test artifact. This will be saved by the test infra, but the file will be
            //    removed, so the user can't see it.
            // 2. In $RAVENWOOD_BUGREPORT_DIR, or the temp directory if not set.
            //    This one won't be removed, so the user can see it,
            //    so we print the filename in the log.
            var ad = RavenwoodEnvironment.getInstance().getArtifactsDir();
            var td = RavenwoodEnvironment.getInstance().getBugreportDir();

            var now = LocalDateTime.now();

            try (var file1 = createBugreportFile(ad, now, false)) {
                try (var file2 = createBugreportFile(td, now, true)) {
                    outs.add(file1);
                    outs.add(file2);
                    var st = RavenwoodInternalUtils.createForkingOutputStream(outs);

                    doBugreportInner(new PrintStream(st, true),
                            message, exceptionThread, throwable, killSelf);
                }
            } catch (IOException e) {
                // This is from close(), so just ignore.
            }
        } finally {
            logactOut.flushCompletely();
        }
    }

    private static void doBugreportInner(
            @NonNull PrintStream out,
            @NonNull String message,
            @Nullable Thread exceptionThread,
            @Nullable Throwable throwable,
            boolean killSelf) {
        out.print("Description: ");
        out.println(message);

        out.println("\n===== BUGREPORT START =====");
        dumpStacks(out, exceptionThread, throwable);

        out.println("\n===== PENDING MESSAGES =====");
        RavenwoodMessageTracker.getInstance().dumpPendingMessages(out, "Test hanging.");

        out.println("\n===== ACTIVITIES =====");
        RavenwoodActivityDriver.getInstance().dumpAllActivities("  ", out);

        out.println("\n===== WARNINGS =====");
        RavenwoodErrorHandler.dumpWarnings(out);

        out.println("\n===== BUGREPORT END =====");
        out.flush();

        if (killSelf) {
            // Before killing self, let's print all generated bugreports.
            dumpBugreportFiles();
            out.println("\n***** KILLING SELF *****");
            out.flush();
            System.exit(13);
        }
    }
}
