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

#include "adb/PairingServerV1Impl.h"

namespace android {
namespace adb {
namespace pairing {

PairingServerV1Impl::PairingServerV1Impl(PairingServerCtx* ctx) : IPairingServer(ctx) {}

uint16_t PairingServerV1Impl::Start(pairing_server_result_cb cb, void* opaque) {
    // We need to store the port information to implement getPort.
    mPort = ::pairing_server_start(ctx_, cb, opaque);
    return mPort;
}

uint16_t PairingServerV1Impl::GetPort() {
    return mPort;
}

void PairingServerV1Impl::StopListening() {
    if (ctx_ == nullptr) {
        return;
    }

    // v1 APIs don't have a way to stop listening without destroying the server.
    ::pairing_server_destroy(ctx_);
    mPort = 0;
    ctx_ = nullptr;
}

} // namespace pairing
} // namespace adb
} // namespace android