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

#include <dlfcn.h>
#include <gtest/gtest.h>

#include "adb/IPairingServer.h"
#include "adb/PairingServerV1Impl.h"
#include "adb/PairingServerV2Impl.h"
#include "mock_com_android_server_adb_flags.h"
#include "mock_pairing_server_v2.h"

namespace android {
namespace adb {
namespace pairing {

// These tests verify the IPairingServer implementation against pairing_server
// APIs that support V2 features.
class IPairingServerV2Test : public ::testing::Test {
protected:
    void SetUp() override {
        g_mock_flag_use_pairing_server_v2 = false;
        SetPairingServerFeatureSupported(PairingServerFeature::V2, true);
    }
};

TEST_F(IPairingServerV2Test, CreateReturnsV1ImplWhenV2NotSupported) {
    // TEST: use_pairing_server_v2: false, PairingServerFeature::V2 true
    {
        g_mock_flag_use_pairing_server_v2 = false;
        SetPairingServerFeatureSupported(PairingServerFeature::V2, true);

        std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));

        // Check if it's a V1 implementation
        ASSERT_NE(server, nullptr);
        ASSERT_NE(dynamic_cast<PairingServerV1Impl*>(server.get()), nullptr);
    }
    // TEST: use_pairing_server_v2: true, PairingServerFeature::V2 false
    {
        g_mock_flag_use_pairing_server_v2 = true;
        SetPairingServerFeatureSupported(PairingServerFeature::V2, false);

        std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));

        // Check if it's a V1 implementation
        ASSERT_NE(server, nullptr);
        ASSERT_NE(dynamic_cast<PairingServerV1Impl*>(server.get()), nullptr);
    }
}

TEST_F(IPairingServerV2Test, CreateReturnsV2ImplWhenV2Supported) {
    g_mock_flag_use_pairing_server_v2 = true;
    SetPairingServerFeatureSupported(PairingServerFeature::V2, true);
    std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));

    // Check if it's a V2 implementation
    ASSERT_NE(server, nullptr);
    ASSERT_NE(dynamic_cast<PairingServerV2Impl*>(server.get()), nullptr);
}

TEST_F(IPairingServerV2Test, V1Impl_Smoke) {
    g_mock_flag_use_pairing_server_v2 = false;
    std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));
    ASSERT_NE(server, nullptr);

    ASSERT_NE(dynamic_cast<PairingServerV1Impl*>(server.get()), nullptr);

    // Port should be zero before starting pairing server
    ASSERT_EQ(server->GetPort(), 0);

    uint16_t port = server->Start(nullptr, nullptr);
    ASSERT_NE(port, 0);
    // Port returned from start should be the same from IPairingServer::getPort()
    ASSERT_EQ(server->GetPort(), port);

    server->StopListening();
    // Port should be zero after server stops
    ASSERT_EQ(server->GetPort(), 0);
}

TEST_F(IPairingServerV2Test, V2Impl_Smoke) {
    g_mock_flag_use_pairing_server_v2 = true;
    std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));
    ASSERT_NE(server, nullptr);

    ASSERT_NE(dynamic_cast<PairingServerV2Impl*>(server.get()), nullptr);

    // Port should be zero before starting pairing server
    ASSERT_EQ(server->GetPort(), 0);

    uint16_t port = server->Start(nullptr, nullptr);
    ASSERT_NE(port, 0);
    // Port returned from start should be the same from IPairingServer::getPort()
    ASSERT_EQ(server->GetPort(), port);

    server->StopListening();
    // Port should be zero after server stops
    ASSERT_EQ(server->GetPort(), 0);
}

} // namespace pairing
} // namespace adb
} // namespace android
