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

//! # Common
//!
//! This module contains shared data structures and traits used across the crate.

use std::collections::HashSet;

/// Newtype to hold user ids.
#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub struct UserId(pub usize);

/// Holds the live state variables that determine the authorization policy.
pub struct PolicySourceData {
    /// A flag indicating if the PCI tunneling feature is globally enabled.
    pub pci_tunnels_enabled: bool,
    /// A flag indicating if the user's screen is currently locked.
    pub is_locked: bool,
    /// A set tracking the IDs of all currently logged-in users.
    pub logged_in_users: HashSet<UserId>,
}

impl PolicySourceData {
    /// Creates a new `PolicySourceData` with default, restrictive values.
    ///
    /// By default, tunnels are disabled, the screen is considered locked, and no
    /// users are logged in.
    pub fn new() -> Self {
        Self { pci_tunnels_enabled: false, is_locked: true, logged_in_users: HashSet::new() }
    }
}

impl Default for PolicySourceData {
    fn default() -> Self {
        Self::new()
    }
}

/// The public API for controlling authorization policy.
///
/// This trait is implemented by the `PolicyEngine` and provides the entry points
/// for a consumer of this library to notify the engine of system state changes.
pub trait TunnelControl {
    /// Enables or disables the PCI tunneling feature globally.
    fn enable_pci_tunnels(&mut self, enable: bool);

    /// Notifies the engine of a screen lock state change.
    fn update_lock_state(&mut self, locked: bool);

    /// Notifies the engine of a user login or logout event.
    fn update_logged_in_state(&mut self, logged_in: bool, user_id: UserId);
}
