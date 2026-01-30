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

package android.app;

import android.app.HandoffActivityData;

/** @hide */
oneway interface IHandoffTaskDataReceiver {

    /*
    * Called when the handoff task data request has succeeded.
    *
    * @param taskId The id of the task that the request was made for.
    * @param handoffActivityData A list of {@link HandoffActivityData} for this
    * task, retaining the order of the activities in the task with the topmost
    * activity at index 0. If the topmost activity of the task did not return
    * any HandoffActivityData, this list will be empty.
    */
    void onHandoffTaskDataRequestSucceeded(
        in int taskId,
        in List<HandoffActivityData> handoffActivityData);

    /*
    * Called when the handoff task data request has failed.
    *
    * @param taskId The id of the task that the request was made for.
    * @param resultCode The result code of the failure, represented as an {@link HandoffFailureCode}
    */
    void onHandoffTaskDataRequestFailed(in int taskId, in int resultCode);

}
