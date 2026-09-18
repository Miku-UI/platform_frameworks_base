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

#include "adb/PairingServerV2Impl.h"

#include <com_android_server_adb_flags.h>

namespace android {
namespace adb {
namespace pairing {

PairingServerV2Impl::PairingServerV2Impl(PairingServerCtx* ctx) : IPairingServer(ctx) {}

uint16_t PairingServerV2Impl::Start(pairing_server_result_cb cb, void* opaque) {
    return ::pairing_server_start(ctx_, cb, opaque);
}

uint16_t PairingServerV2Impl::GetPort() {
    return ::pairing_server_get_port(ctx_);
}

void PairingServerV2Impl::StopListening() {
    ::pairing_server_stop_listening(ctx_);
}

// static
bool PairingServerV2Impl::IsSupported() {
    // Check both the feature flag and whether the underlying library supports V2 features.
    return com_android_server_adb_flags_use_pairing_server_v2() &&
            IPairingServer::IsFeatureSupported(PairingServerFeature::V2);
}

} // namespace pairing
} // namespace adb
} // namespace android