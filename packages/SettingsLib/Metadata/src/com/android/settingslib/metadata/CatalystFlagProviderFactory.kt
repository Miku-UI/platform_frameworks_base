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

package com.android.settingslib.metadata

/**
 * Factory to get the correct `CatalystFlagProvider` for the current build environment.
 */
object CatalystFlagProviderFactory : CatalystFlagProvider {
    private var currentProvider: CatalystFlagProvider = AospCatalystFlagProvider()

    /**
     * Sets the global flag provider.
     * WARNING: This sets the provider ONLY for the current process. It will
     * not affect other applications or system services.
     */
    fun setProvider(provider: CatalystFlagProvider) {
        currentProvider = provider
    }

    /**
     * Gets the currently configured flag provider for this process.
     */
    fun getInstance(): CatalystFlagProvider {
        return currentProvider
    }

    override fun catalystUseKeyParameters(): Boolean {
        return currentProvider.catalystUseKeyParameters()
    }
}

/**
 * The default provider that reads the actual flag. This will be used by all
 * processes that do not explicitly set a different provider.
 */
private class AospCatalystFlagProvider : CatalystFlagProvider {
    override fun catalystUseKeyParameters(): Boolean {
        return com.android.settingslib.catalyst.flags.Flags.catalystUseKeyParameters()
    }
}