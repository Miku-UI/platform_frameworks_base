/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.scrolltotop

import com.android.systemui.kosmos.Kosmos
import java.util.Optional

var Kosmos.scrollToTopOptional by Kosmos.Fixture { Optional.of(scrollToTop) }
var Kosmos.scrollToTop: ScrollToTop by Kosmos.Fixture { fakeScrollToTop }
val Kosmos.fakeScrollToTop by Kosmos.Fixture { FakeScrollToTop() }
