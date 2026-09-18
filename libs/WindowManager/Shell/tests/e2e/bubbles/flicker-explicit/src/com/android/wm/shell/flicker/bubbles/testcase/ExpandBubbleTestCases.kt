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
 * Test cases to verify bubble expansion from a collapsed state.
 *
 * This set of test cases covers the following:
 * - [BubbleAlwaysVisibleTestCases]: Verifies the behavior of bubbles that are always visible.
 * - [BubbleAppBecomesExpandedTestCases]: Ensures that app bubble expands properly when triggered.
 *
 * This interface is used in scenarios where the bubble may be expanded from various contexts (home
 * screen, other apps, etc.).
 */
interface ExpandBubbleTestCases : BubbleAlwaysVisibleTestCases, BubbleAppBecomesExpandedTestCases
