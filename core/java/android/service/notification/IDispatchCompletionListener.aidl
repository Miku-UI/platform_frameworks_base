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

package android.service.notification;

import android.os.IBinder;

/**
 * Callback from app into system process to indicate that the "dispatch" of a notification
 * event (by calling the corresponding NotificationListenerService API method on the main thread)
 * was completed or there was an error that resulted in no dispatch. This does not signal that the
 * app completed processing the event. It may still be doing that off the main thread.
 *
 * @hide
 */
oneway interface IDispatchCompletionListener {
    void notifyDispatchComplete(in long dispatchToken);
}
