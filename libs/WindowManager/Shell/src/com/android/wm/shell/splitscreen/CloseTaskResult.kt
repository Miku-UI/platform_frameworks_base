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

package com.android.wm.shell.splitscreen

/** Result of closing a task from a split screen. */
enum class CloseTaskResult {
    // Did not close task because split screen is not active.
    NOT_ACTIVE,

    // Did not close task because the divider fling animation is playing.
    DIVIDER_FLINGING,

    // Did not close task because the other pending dismiss operation is being performed.
    PENDING_DISMISS,

    // Did not close task because RunningTaskInfo for given task ID is not found.
    NO_TASK_INFO,

    // Did not close task because the stage of the given task ID is not found.
    NO_STAGE,

    // Did not close task because the position of stage could not be determined.
    STAGE_POSITION_UNKNOWN,

    // Closed task and split screen remains because the stage still hosts other tasks.
    CLOSED_TASK_SPLIT_REMAINED,

    // Closed task and split screen dismissed because the stage no longer hosts child tasks.
    CLOSED_TASK_SPLIT_DISMISSED,
}
