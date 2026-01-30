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

package com.android.server.bitmapoffload;

import android.annotation.IntDef;
import android.net.Uri;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class BitmapOffloadContract {
    public static final String AUTHORITY = "com.android.bitmapoffload";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/bitmaps");

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SOURCE = "source";
    public static final String COLUMN_FILE_NAME = "filename";
    public static final String COLUMN_FILE_SIZE = "filesize";
    public static final String COLUMN_WIDTH = "width";
    public static final String COLUMN_HEIGHT = "height";
    public static final String COLUMN_OWNER_UID = "owner_uid";
    public static final String COLUMN_STATUS = "status";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "OFFLOAD_STATUS_", value = {
            OFFLOAD_STATUS_PENDING,
            OFFLOAD_STATUS_COMPLETED,
            OFFLOAD_STATUS_FAILED
    })
    public @interface OffloadStatus {}
    public static final int OFFLOAD_STATUS_PENDING = 0;
    public static final int OFFLOAD_STATUS_COMPLETED = 1;
    public static final int OFFLOAD_STATUS_FAILED = 2;
}
