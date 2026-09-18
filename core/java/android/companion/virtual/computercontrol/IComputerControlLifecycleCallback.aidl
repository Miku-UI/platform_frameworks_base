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

 package android.companion.virtual.computercontrol;

/**
 * Callback for receiving lifecycle changes from a ComputerControlSession.
 * @hide
 */
oneway interface IComputerControlLifecycleCallback {

    /** Called when the session is active. */
    void onActive();

    /** Called when the session is blocked. */
    void onBlocked(int blockReason, String blockingPackage);

    /** Called when the session is closed. */
    void onClosed(int closeReason);
}
