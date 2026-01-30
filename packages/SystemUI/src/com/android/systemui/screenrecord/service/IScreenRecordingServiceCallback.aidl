/**
 * Copyright (c) 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screenrecord.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.drawable.Icon;

oneway interface IScreenRecordingServiceCallback {

    /**
    * Called when the service actually starts the recording.
    */
    void onRecordingStarted();

    /**
    * Called when the recording is interrupted for some reason.
    */
    void onRecordingInterrupted(int userId, int reason);

    void onRecordingSaved(in Uri recordingUri, in Icon thumbnail);
}