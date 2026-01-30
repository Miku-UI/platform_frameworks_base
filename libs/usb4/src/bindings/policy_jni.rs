// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! # Policy Engine java bindings
use jni::objects::JObject;
use jni::sys::{jboolean, jint};
use jni::JNIEnv;
use log::trace;
use std::sync::{Arc, LazyLock, Mutex};
use usb4_policies::{
    common::{TunnelControl, UserId},
    policy_engine::PolicyEngine,
};

// Singleton of PolicyEngine to use for JNI. Will get created on first use.
static POLICY_ENGINE: LazyLock<Arc<Mutex<PolicyEngine>>> =
    LazyLock::new(|| Arc::new(Mutex::new(PolicyEngine::new())));

/// Initializes policy engine.
#[no_mangle]
pub extern "system" fn Java_com_android_server_usb_Usb4Manager_nativeInit<'a>(
    _env: JNIEnv<'a>,
    _obj: JObject<'a>,
) {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("Usb4Policy")
            .with_max_level(log::LevelFilter::Info),
    );

    // Initialize policy engine.
    let _unused = POLICY_ENGINE.lock().unwrap();
    trace!("Native init complete!");
}

/// Enables or disables PCI tunnels.
#[no_mangle]
pub extern "system" fn Java_com_android_server_usb_Usb4Manager_enablePciTunnels<'a>(
    _env: JNIEnv<'a>,
    _obj: JObject<'a>,
    enable: jboolean,
) {
    trace!("enablePciTunnels with {}", enable != 0);
    let mut engine = POLICY_ENGINE.lock().unwrap();
    engine.enable_pci_tunnels(enable != 0);
}

/// Updates the screen lock state.
#[no_mangle]
pub extern "system" fn Java_com_android_server_usb_Usb4Manager_updateLockState<'a>(
    _env: JNIEnv<'a>,
    _obj: JObject<'a>,
    locked: jboolean,
) {
    trace!("updateLockState with {}", locked != 0);
    let mut engine = POLICY_ENGINE.lock().unwrap();
    engine.update_lock_state(locked != 0);
}

/// Updates the logged-in state for a user.
#[no_mangle]
pub extern "system" fn Java_com_android_server_usb_Usb4Manager_updateLoggedInState<'a>(
    _env: JNIEnv<'a>,
    _obj: JObject<'a>,
    logged_in: jboolean,
    user_id: jint,
) {
    trace!("updateLoggedInstate with {} = {}", user_id as usize, logged_in != 0);
    let mut engine = POLICY_ENGINE.lock().unwrap();
    engine.update_logged_in_state(logged_in != 0, UserId(user_id as usize));
}
