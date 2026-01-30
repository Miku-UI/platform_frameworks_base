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

package android.tracing.perfetto;

import static java.io.File.createTempFile;

import android.tools.traces.io.ResultWriter;

import java.io.File;
import java.io.IOException;

public class TestUtils {

    /**
     * Creates a temporary ResultWriter for testing.
     * @param tracingDirectory The directory to write the temporary file to.
     * @return The temporary ResultWriter.
     */
    public static ResultWriter createTempWriter(File tracingDirectory) {
        try {
            return new ResultWriter()
                    .withName(createTempFile("temp", "").getName())
                    .withOutputDir(tracingDirectory)
                    .setRunComplete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
