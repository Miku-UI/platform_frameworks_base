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
#include "mock_com_android_server_adb_flags.h"

namespace android {
namespace adb {
namespace pairing {

// These tests verify the IPairingServer implementation against the pairing_server
// APIs as they existed prior to the introduction of the v2 APIs.
class IPairingServerV1Test : public ::testing::Test {
protected:
    void SetUp() override {
        g_mock_flag_use_pairing_server_v2 = false;
    }
};

// CreateNoCert should return a V1 implementation, regardless of the use_v2 flag.
TEST_F(IPairingServerV1Test, CreateReturnsV1ImplAlways) {
    // use_pairing_server_v2 flag off
    {
        g_mock_flag_use_pairing_server_v2 = false;
        std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));
        ASSERT_NE(server, nullptr);
        ASSERT_NE(dynamic_cast<PairingServerV1Impl*>(server.get()), nullptr);
    }
    // use_pairing_server_v2 flag on
    {
        g_mock_flag_use_pairing_server_v2 = true;
        std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));
        ASSERT_NE(server, nullptr);
        ASSERT_NE(dynamic_cast<PairingServerV1Impl*>(server.get()), nullptr);
    }
}

TEST_F(IPairingServerV1Test, V1Impl_Smoke) {
    // Ensure V1 is used implicitly due to mock_pairing_server_old not providing V2 APIs
    std::unique_ptr<IPairingServer> server(IPairingServer::CreateNoCert(nullptr, 0, nullptr));
    ASSERT_NE(server, nullptr);

    ASSERT_NE(dynamic_cast<PairingServerV1Impl*>(server.get()), nullptr);

    // Port should be zero before starting pairing server
    ASSERT_EQ(server->GetPort(), 0);

    uint16_t port = server->Start(nullptr, nullptr);
    ASSERT_NE(port, 0);
    // Port returned from start should be the same from IPairingServer::getPort()
    // Note: getPort() in V1Impl uses a stored member variable, not the dlsym'd get_port.
    ASSERT_EQ(server->GetPort(), port);

    server->StopListening();
    // Port should be zero after server stops
    ASSERT_EQ(server->GetPort(), 0);
}

} // namespace pairing
} // namespace adb
} // namespace android
