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

package com.android.server.contentrestriction;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.util.IndentingPrintWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** User specific data, used internally by the {@link ContentRestrictionService}. */
public class ContentRestrictionUserData {
    public final @UserIdInt int userId;
    public boolean contentRestrictionEnabled;
    public Map<String, List<String>> contentRestrictionPackages = new HashMap<>();

    public ContentRestrictionUserData(@UserIdInt int userId) {
        this.userId = userId;
    }

    void dump(@NonNull IndentingPrintWriter pw) {
        pw.println();
        pw.println("User " + userId + ":");
        pw.increaseIndent();
        pw.println("contentRestrictionEnabled: " + contentRestrictionEnabled);
        pw.println("contentRestrictionPackages: " + contentRestrictionPackages);
        pw.decreaseIndent();
    }
}
