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

#pragma once

#include "adb/IPairingServer.h"

namespace android {
namespace adb {
namespace pairing {

// This class is an implementation of IPairingServer using only the initial libadb_pairing_server
// APIs (prior to V2 APIs being introduced).
class PairingServerV1Impl final : public IPairingServer {
public:
    explicit PairingServerV1Impl(PairingServerCtx* ctx);
    ~PairingServerV1Impl() override = default;

    uint16_t Start(pairing_server_result_cb cb, void* opaque) override;
    uint16_t GetPort() override;
    void StopListening() override;

private:
    uint16_t mPort = 0;
}; // PairingServerV1Impl

} // namespace pairing
} // namespace adb
} // namespace android