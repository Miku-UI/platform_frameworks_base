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

package com.android.systemui.statusbar.policy.vpn.shared.model

/** Represents the current VPN state. */
data class VpnState(
    /** True if there is at least one VPN connected. */
    val isEnabled: Boolean = false,

    /** True if the primary VPN is a branded system app. */
    val isBranded: Boolean = false,

    /**
     * True if the primary VPN is validated, or if all VPNs across all enabled profiles are
     * validated. See [android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED].
     */
    val isValidated: Boolean = false,
)
