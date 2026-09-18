/**
 * Copyright (c) 2026, The Android Open Source Project
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

package com.android.server.personalcontext.component.client;

import android.os.ParcelUuid;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.renderer.IGetFilterCallback;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.IOpCallback;

oneway interface ITestService {
    void runOp(in ParcelUuid componentId, in IOpCallback opCallback);
}
