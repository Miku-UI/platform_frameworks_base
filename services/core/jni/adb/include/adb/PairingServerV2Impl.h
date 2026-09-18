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

// This class is an implementation of IPairingServer using the libadb_pairing_server APIs with the
// V2 feature set.
class PairingServerV2Impl final : public IPairingServer {
public:
    explicit PairingServerV2Impl(PairingServerCtx* ctx);
    uint16_t Start(pairing_server_result_cb cb, void* opaque) override;
    uint16_t GetPort() override;
    void StopListening() override;

    static bool IsSupported();
}; // PairingServerV2Impl

} // namespace pairing
} // namespace adb
} // namespace android