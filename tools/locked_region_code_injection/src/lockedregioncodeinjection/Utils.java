/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package lockedregioncodeinjection;

import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static final int ASM_VERSION = Opcodes.ASM9;

    /**
     * Reads configuration from a config file with key:value format.
     *
     * Each target begins with "target:" followed by lock class descriptor.
     * Optional fields: pre, post, trace-before-acquire, trace-after-acquire,
     * trace-before-release, trace-after-release, scoped.
     * Empty lines and lines starting with # are ignored.
     */
    public static List<LockTarget> getTargetsFromConfig(String configPath) throws IOException {
        List<LockTarget> targets = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            String line;
            TargetBuilder builder = null;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int colonIndex = line.indexOf(':');
                if (colonIndex == -1) {
                    continue;
                }

                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                if ("target".equals(key)) {
                    if (builder != null) {
                        targets.add(builder.build());
                    }
                    builder = new TargetBuilder(value, lineNum);
                } else {
                    if (builder == null) {
                        throw new RuntimeException("Line " + lineNum + ": Key '" + key
                                + "' found before 'target'");
                    }
                    builder.setProperty(key, value, lineNum);
                }
            }
            if (builder != null) {
                targets.add(builder.build());
            }
        }
        return targets;
    }

    private static class TargetBuilder {
        private final String mTargetDesc;
        private final int mStartLine;
        private String mPre;
        private String mPost;
        private String mTraceBeforeAcquire;
        private String mTraceAfterAcquire;
        private String mTraceBeforeRelease;
        private String mTraceAfterRelease;
        private boolean mScoped = false;

        TargetBuilder(String desc, int line) {
            mTargetDesc = desc;
            mStartLine = line;
        }

        void setProperty(String key, String value, int lineNum) {
            switch (key) {
                case "pre" -> mPre = value;
                case "post" -> mPost = value;
                case "trace-before-acquire" -> mTraceBeforeAcquire = value;
                case "trace-after-acquire" -> mTraceAfterAcquire = value;
                case "trace-before-release" -> mTraceBeforeRelease = value;
                case "trace-after-release" -> mTraceAfterRelease = value;
                case "scoped" -> mScoped = Boolean.parseBoolean(value);
                default -> throw new RuntimeException("Line " + lineNum + ": Unknown key: " + key);
            }
        }

        LockTarget build() {
            if ((mPre == null) != (mPost == null)) {
                throw new RuntimeException("Target " + mTargetDesc + " starting at line "
                        + mStartLine + ": pre and post must both be specified or both be null");
            }
            return new LockTarget(mTargetDesc, mPre, mPost, mTraceBeforeAcquire,
                    mTraceAfterAcquire, mTraceBeforeRelease, mTraceAfterRelease, mScoped);
        }
    }
}
