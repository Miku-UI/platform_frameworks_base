/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.composable.elements

import dagger.Module
import dagger.multibindings.Multibinds

/**
 * Dagger module for providing empty placeholder for OEMElements. This gives dagger something to
 * return when the OEM has no custom elements.
 */
@Module
interface OEMElementProviderModule {
    @Multibinds fun oemElementProviders(): Set<OEMElementProvider>
}
