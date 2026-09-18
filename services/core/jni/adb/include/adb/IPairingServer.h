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

#include <adb/pairing/pairing_server.h>

namespace android {
namespace adb {
namespace pairing {

// This interface is a wrapper around the libadb_pairing_server APEX interface.
//
// The intention is for the JNI implementation below to go through this wrapper to access the
// pairing_server APIs, so it doesn't have to know about which apex APIs are available to it.
class IPairingServer {
public:
    virtual ~IPairingServer();

    /**
     * Creates a new PairingServer instance with a certificate.
     *
     * @param pswd The password for pairing.
     * @param pswd_len The length of the password.
     * @param peer_info Information about the peer.
     * @param x509_cert_pem The x509 certificate in PEM format.
     * @param x509_size The size of the x509 certificate.
     * @param priv_key_pem The private key in PEM format.
     * @param priv_size The size of the private key.
     * @return A raw pointer to the new IPairingServer instance.
     */
    static IPairingServer* Create(const uint8_t* pswd, size_t pswd_len, const PeerInfo* peer_info,
                                  const uint8_t* x509_cert_pem, size_t x509_size,
                                  const uint8_t* priv_key_pem, size_t priv_size);

    /**
     * Creates a new PairingServer instance without a certificate.
     *
     * @param pswd The password for pairing.
     * @param pswd_len The length of the password.
     * @param peer_info Information about the peer.
     * @return A raw pointer to the new IPairingServer instance.
     */
    static IPairingServer* CreateNoCert(const uint8_t* pswd, size_t pswd_len,
                                        const PeerInfo* peer_info);

    /**
     * Checks if a specific feature is supported by the pairing server.
     *
     * @param feature The feature to check.
     * @return True if the feature is supported, false otherwise.
     */
    static bool IsFeatureSupported(PairingServerFeature feature);

    /**
     * Starts the pairing server.
     *
     * @param cb The callback function to be invoked when a pairing result is available.
     * @param opaque A pointer to opaque data to be passed to the callback function.
     * @return The port the server is listening on, or 0 if it failed to start.
     */
    virtual uint16_t Start(pairing_server_result_cb cb, void* opaque) = 0;

    /**
     * Returns the port the pairing server is listening on.
     *
     * If the pairing server has not started or has finished, this method returns 0.
     *
     * @return The port number, or 0 if not listening.
     */
    virtual uint16_t GetPort() = 0;

    /**
     * Stops the pairing server from listening for new connections.
     */
    virtual void StopListening() = 0;

protected:
    explicit IPairingServer(PairingServerCtx* ctx);
    PairingServerCtx* ctx_ = nullptr;
}; // IPairingServer

} // namespace pairing
} // namespace adb
} // namespace android