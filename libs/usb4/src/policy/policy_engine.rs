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

//! # Policy Engine
//!
//! This module provides the main public-facing API for the library.
//!
//! The `PolicyEngine` struct is the primary entry point for consumers of this
//! crate. It encapsulates the `PciAuthorizer`.

use crate::common::{TunnelControl, UserId};
use crate::pci_authorizer::PciAuthorizer;
use tokio::runtime::Runtime;

/// The main engine that encapsulates all policy and authorization logic.
///
/// This struct is the primary entry point for the library.
pub struct PolicyEngine {
    /// The embedded `PciAuthorizer` that handles core logic.
    pub pci_authorizer: PciAuthorizer,
    /// The Tokio runtime for the PciAuthorizer's async tasks.
    _runtime: Runtime,
}

impl PolicyEngine {
    /// Create a new PolicyEngine and associated members.
    pub fn new() -> Self {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime for PolicyEngine");
        let pci_authorizer = runtime.block_on(async { PciAuthorizer::default() });

        Self { pci_authorizer, _runtime: runtime }
    }
}
impl Default for PolicyEngine {
    /// Same as ::new()
    fn default() -> Self {
        Self::new()
    }
}

impl TunnelControl for PolicyEngine {
    /// Enables or disables the PCI tunneling feature globally.
    fn enable_pci_tunnels(&mut self, enable: bool) {
        self.pci_authorizer.enable_pci_tunnels(enable);
    }

    /// Notifies the engine of a screen lock state change.
    fn update_lock_state(&mut self, locked: bool) {
        self.pci_authorizer.update_lock_state(locked);
    }

    /// Notifies the engine of a user login or logout event.
    fn update_logged_in_state(&mut self, logged_in: bool, user_id: UserId) {
        self.pci_authorizer.update_logged_in_state(logged_in, user_id);
    }
}
