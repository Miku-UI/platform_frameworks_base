/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.util;

public class EventLog_ravenwood {
    public static int writeEvent(int tag, int value) {
        return writeEvent(tag, (Object) value);
    }

    public static int writeEvent(int tag, long value) {
        return writeEvent(tag, (Object) value);
    }

    public static int writeEvent(int tag, float value) {
        return writeEvent(tag, (Object) value);
    }

    public static int writeEvent(int tag, String str) {
        return writeEvent(tag, (Object) str);
    }

    public static int writeEvent(int tag, Object... list) {
        var tagName = android.util.EventLog.getTagName(tag);
        if (tagName == null) {
            tagName = "Event-" + tag;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.length; i++) {
            sb.append(list[i]);
            if (i < list.length - 1) {
                sb.append(',');
            }
        }
        sb.append(']');
        Log_ravenwood.println_native(0, Log.INFO, tagName, sb.toString());
        return sb.length();
    }
}
