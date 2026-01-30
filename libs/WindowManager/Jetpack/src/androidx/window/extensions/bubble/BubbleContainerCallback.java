/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.window.extensions.bubble;

import android.os.IBinder;

/**
 * Public interface used to notify applications about the changes to the bubble containers they
 * manage.
 */
public interface BubbleContainerCallback {
    /** Notifies about removal of a Bubble that was previously opened by the client. */
    void onBubbleRemoved(IBinder token);
}
