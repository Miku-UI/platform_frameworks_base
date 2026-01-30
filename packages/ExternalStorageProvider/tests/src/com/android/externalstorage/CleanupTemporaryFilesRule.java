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

package com.android.externalstorage;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;

public class CleanupTemporaryFilesRule implements TestRule {
    private final File mTemporaryFilesDirectory;

    public CleanupTemporaryFilesRule(File temporaryFilesDirectory) {
        this.mTemporaryFilesDirectory = temporaryFilesDirectory;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } catch (Throwable e) {
                    throw e;    // ensure the test still fails
                } finally {
                    removeFilesRecursively(mTemporaryFilesDirectory);
                }
            }
        };
    }

    /**
     * Remove all files and directories under a given path.
     * @param directory the path to start from
     */
    public static void removeFilesRecursively(File directory) {
        if (directory == null || !directory.exists() || directory.listFiles().length == 0) {
            return;
        }

        for (File childFile : directory.listFiles()) {
            if (childFile.isDirectory()) {
                removeFilesRecursively(childFile);
            }
            childFile.delete();
        }
    }
}
