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

#include "adb/IPairingServer.h"

#include <android-base/strings.h>
#include <dlfcn.h>

#include "adb/PairingServerV1Impl.h"
#include "adb/PairingServerV2Impl.h"

namespace android {
namespace adb {
namespace pairing {

namespace {

typedef bool (*pairing_server_is_feature_supported_func)(PairingServerFeature);

// Returns the best available PairingServer implementation.
// If the V2 feature is supported (checked via flag and API availability), returns V2.
// Otherwise, falls back to V1.
IPairingServer* CreatePairingServer(PairingServerCtx* ctx) {
    if (PairingServerV2Impl::IsSupported()) {
        return new PairingServerV2Impl(ctx);
    } else {
        return new PairingServerV1Impl(ctx);
    }
}
} // namespace

IPairingServer::IPairingServer(PairingServerCtx* ctx) : ctx_(ctx) {}

IPairingServer::~IPairingServer() {
    if (ctx_ != nullptr) {
        ::pairing_server_destroy(ctx_);
    }
}

// static
IPairingServer* IPairingServer::Create(const uint8_t* pswd, size_t pswd_len,
                                       const PeerInfo* peer_info, const uint8_t* x509_cert_pem,
                                       size_t x509_size, const uint8_t* priv_key_pem,
                                       size_t priv_size) {
    // Setting port to 0 means OS will select the port.
    auto* ctx = ::pairing_server_new(pswd, pswd_len, peer_info, x509_cert_pem, x509_size,
                                     priv_key_pem, priv_size, 0);
    return CreatePairingServer(ctx);
}

// static
IPairingServer* IPairingServer::CreateNoCert(const uint8_t* pswd, size_t pswd_len,
                                             const PeerInfo* peer_info) {
    // Setting port to 0 means OS will select the port.
    auto* ctx = ::pairing_server_new_no_cert(pswd, pswd_len, peer_info, 0);
    return CreatePairingServer(ctx);
}

// static
bool IPairingServer::IsFeatureSupported(PairingServerFeature feature) {
    static pairing_server_is_feature_supported_func is_feature_supported_func =
            reinterpret_cast<pairing_server_is_feature_supported_func>(
                    dlsym(RTLD_DEFAULT, "pairing_server_is_feature_supported"));
    return is_feature_supported_func != nullptr && is_feature_supported_func(feature);
}

} // namespace pairing
} // namespace adb
} // namespace android