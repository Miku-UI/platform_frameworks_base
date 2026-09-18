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

package com.android.server.security.trusttoken;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.ShellCommand;

import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class TrustTokenShellCommand extends ShellCommand {
    private static final String TAG = "TrustTokenShellCommand";

    @NonNull private final Context mContext;
    @NonNull private final TrustTokenManagerInternal mInternal;
    @NonNull private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    TrustTokenShellCommand(Context context, TrustTokenManagerInternal internal) {
        mContext = context;
        mInternal = internal;
    }

    @Override
    public int onCommand(String cmd) {
        final PrintWriter out = getOutPrintWriter();
        final PrintWriter errOut = getErrPrintWriter();

        try {
            switch (cmd) {
                case "refill" -> {
                    boolean wait = false;
                    int timeoutSeconds = 300;
                    String opt;
                    while ((opt = getNextOption()) != null) {
                        switch (opt) {
                            case "--wait":
                                wait = true;
                                break;
                            case "--timeout":
                                timeoutSeconds = Integer.parseInt(getNextArgRequired());
                                break;
                            default:
                                errOut.println("Error: Unknown option: " + opt);
                                return 1;
                        }
                    }

                    int num = Integer.parseInt(getNextArgRequired());
                    ComponentName providerComponent =
                            TrustTokenProvider.getServiceProvider(mContext);
                    if (providerComponent == null) {
                        errOut.println("no provider found");
                        return 1;
                    }
                    var provider =
                            new TrustTokenProvider(mContext, mExecutorService, providerComponent);
                    if (wait) {
                        var done = new CountDownLatch(1);
                        var callback =
                                new OutcomeReceiver<Void, Throwable>() {
                                    @Override
                                    public void onResult(Void unused) {
                                        done.countDown();
                                        out.println("Refill done");
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        done.countDown();
                                        errOut.println();
                                        errOut.println(
                                                "Exception occurred while refilling tokens: ");
                                        throwable.printStackTrace(errOut);
                                    }
                                };
                        var cancellation = mInternal.refillTokens(provider, num, callback);
                        if (!done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                            errOut.println("Timed out while refilling tokens, cancelling");
                            cancellation.cancel();
                        }
                    } else {
                        mInternal.refillTokens(
                                provider,
                                num,
                                new OutcomeReceiver<Void, Throwable>() {
                                    @Override
                                    public void onResult(Void unused) {}

                                    @Override
                                    public void onError(Throwable throwable) {}
                                });
                        out.println("Refill enqueued. Use --wait to wait for the execution.");
                    }
                }

                default -> {
                    return super.handleDefaultCommands(cmd);
                }
            }
        } catch (Throwable e) {
            errOut.println();
            errOut.println("Exception occurred while executing '" + cmd + "':");
            e.printStackTrace(errOut);
            return 1;
        }
        return 0;
    }

    @Override
    public void onHelp() {
        final PrintWriter out = getOutPrintWriter();
        out.println("TrustToken commands");
        out.println("  refill [--wait] [--timeout=300] <num>");
        out.println("    Refill <num> tokens. Block until completion for --timeout seconds if ");
        out.println("    --wait is specified.");
        out.println("  help");
        out.println("    Print this help text.");
    }
}
