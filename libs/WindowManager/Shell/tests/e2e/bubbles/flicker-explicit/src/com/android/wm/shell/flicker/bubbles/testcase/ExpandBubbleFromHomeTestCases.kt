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

package com.android.wm.shell.flicker.bubbles.testcase

/**
 * Test cases to verify bubble expansion specifically from the home screen.
 *
 * This set of test cases includes the following:
 * - [ExpandBubbleTestCases]: General test cases for expanding bubbles.
 * - [LauncherAlwaysVisibleTestCases]: Verifies that Launcher (home screen) is always visible in the
 *   background throughout the expansion.
 *
 * This interface is used when the bubble expansion is specifically tested from the home screen.
 */
interface ExpandBubbleFromHomeTestCases : ExpandBubbleTestCases, LauncherAlwaysVisibleTestCases
