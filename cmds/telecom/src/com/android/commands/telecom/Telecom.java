/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.commands.telecom;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * @deprecated Use {@code com.android.server.telecom.TelecomShellCommand} instead and execute the
 * shell command using {@code adb shell cmd telecom...}. Commands sent here are proxied to telecom
 * for backwards compatibility reasons.
 */
@Deprecated
public final class Telecom {

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        PrintWriter outputWriter = new PrintWriter(new FileOutputStream(FileDescriptor.out));
        try {
            // proxy args to "cmd telecom" and send response to output.
            Process proc = Runtime.getRuntime().exec("cmd telecom " + String.join(" ", args));
            BufferedReader stdInput = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            proc.waitFor();
            while (stdInput.ready()) {
                outputWriter.print((char) stdInput.read());
            }
        } catch (Exception e) {
            outputWriter.print("error executing command: " + e);
        }
        outputWriter.flush();
    }
}
