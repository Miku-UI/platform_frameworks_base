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

#include "mock_pairing_server_v2.h"

#include <map>

struct PairingServerCtx {
    uint16_t port_ = 0;
}; // PairingServerCtx

namespace android {
namespace adb {
namespace pairing {

namespace {
std::map<PairingServerFeature, bool> g_supported_features;
constexpr uint16_t kServerPort = 1234;
} // namespace

void SetPairingServerFeatureSupported(PairingServerFeature f, bool supported) {
    g_supported_features[f] = supported;
}

} // namespace pairing
} // namespace adb
} // namespace android

using namespace android::adb::pairing;

// Mock the external C functions from libadb_pairing_server
extern "C" {

bool pairing_server_is_feature_supported(enum PairingServerFeature f) {
    return g_supported_features[f];
}

PairingServerCtx* pairing_server_new(const uint8_t*, size_t, const PeerInfo*, const uint8_t*,
                                     size_t, const uint8_t*, size_t, uint16_t /* port */) {
    return new PairingServerCtx;
}

PairingServerCtx* pairing_server_new_no_cert(const uint8_t*, size_t, const PeerInfo*,
                                             uint16_t /* port */) {
    return new PairingServerCtx;
}

uint16_t pairing_server_start(PairingServerCtx* ctx, pairing_server_result_cb, void*) {
    ctx->port_ = kServerPort;
    return ctx->port_;
}

uint16_t pairing_server_get_port(PairingServerCtx* ctx) {
    return ctx->port_;
}

void pairing_server_stop_listening(PairingServerCtx* ctx) {
    ctx->port_ = 0;
}

void pairing_server_destroy(PairingServerCtx* ctx) {
    delete ctx;
}

} // extern "C"