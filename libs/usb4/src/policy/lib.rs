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

//! # Device Policy Engine
//!
//! This crate provides a policy engine for managing device authorization,
//! specifically for controlling PCI tunnels based on system state like user login
//! and screen lock status.
//!
//! It operates by listening to kernel Uevents in a background and
//! managing authorization state based on commands received through its public API.
//!
//! The primary entry point for this library is the `PolicyEngine` struct.

/// Defines shared data structures and the primary control trait.
pub mod common;
/// Implements the core authorization logic and Uevent handling.
pub mod pci_authorizer;
/// Provides the main public-facing API for the library.
pub mod policy_engine;
/// Provided sysfs utilities
pub mod sysfs;
