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

use crate::common::{PolicySourceData, TunnelControl, UserId};
use crate::sysfs::SysfsUtils;
use anyhow::Result;
use kobject_uevent::ActionType;
use log::{error, info};
use std::path::Path;
use std::sync::Arc;
use tokio::sync::mpsc;
use uevent::netlink::{AsyncNetlinkKObjectUEventSocket, AsyncUEventSocket};

/// Message queue size.
const MESSAGE_QUEUE_SIZE: usize = 10;

/// Enum for the PCI authorization state machine.
#[derive(Debug, PartialEq, Clone, Copy)]
pub enum PciAuthState {
    /// PCI tunneling is disabled.
    Disabled,
    /// Tunneling is enabled, but no user is logged in.
    DenyNoUser,
    /// Tunneling is enabled and a user is logged in, but the screen is locked.
    DeferNewDevices,
    /// Tunneling is enabled, a user is logged in, and the screen is unlocked.
    Authorized,
}

/// Event sent from PciAuthorizer to PciHotplugService
#[derive(Debug, Clone)]
enum PciServiceEvent {
    EnablePciTunnels(bool),
    UpdateLockState(bool),
    UpdateLoggedInState { logged_in: bool, user_id: UserId },
    Shutdown,
}

/// Internal service that runs an async event loop for uevents and policy updates.
struct PciAuthorizerTask {
    uevent_socket: Arc<dyn AsyncUEventSocket>,
    event_receiver: mpsc::Receiver<PciServiceEvent>,
    sysfs_utils: SysfsUtils,
    policy_data: PolicySourceData,
    current_pci_auth_state: PciAuthState,
}

impl PciAuthorizerTask {
    /// Calculates PciAuthState from PolicySourceData.
    fn calculate_auth_state(policy_data: &PolicySourceData) -> PciAuthState {
        let allow_flag = policy_data.pci_tunnels_enabled;
        let screen_unlocked = !policy_data.is_locked;
        let has_logged_in_users = !policy_data.logged_in_users.is_empty();

        match (allow_flag, has_logged_in_users, screen_unlocked) {
            (false, _, _) => PciAuthState::Disabled,
            (true, false, _) => PciAuthState::DenyNoUser,
            (true, true, false) => PciAuthState::DeferNewDevices,
            (true, true, true) => PciAuthState::Authorized,
        }
    }

    /// Handles a received uevent.
    fn handle_uevent_result(&mut self, uevent_result: Result<kobject_uevent::UEvent>) {
        match uevent_result {
            Ok(uevent) => {
                if self.current_pci_auth_state == PciAuthState::Authorized
                    && uevent.subsystem.as_str() == "thunderbolt"
                    && uevent.action == ActionType::Add
                {
                    let path = uevent.devpath.as_path();
                    let relative_path = path.strip_prefix("/").unwrap();
                    let full_path = Path::new("/sys/").join(relative_path);
                    if let Err(e) = self.sysfs_utils.authorize_thunderbolt_dev(full_path.as_path())
                    {
                        error!(
                            "Failed to authorize device on uevent {}: {}",
                            full_path.display(),
                            e
                        );
                    }
                }
            }
            Err(e) => {
                error!("Error reading uevent: {}. Uevent listener might stop if this persists.", e);
            }
        }
    }

    /// Handles a received service event. Returns true if the service should continue running.
    fn handle_service_event(&mut self, service_event: PciServiceEvent) -> bool {
        match service_event {
            PciServiceEvent::EnablePciTunnels(enable) => {
                self.policy_data.pci_tunnels_enabled = enable;
            }
            PciServiceEvent::UpdateLockState(locked) => {
                self.policy_data.is_locked = locked;
            }
            PciServiceEvent::UpdateLoggedInState { logged_in, user_id } => {
                if logged_in {
                    self.policy_data.logged_in_users.insert(user_id);
                } else {
                    self.policy_data.logged_in_users.remove(&user_id);
                }
            }
            PciServiceEvent::Shutdown => {
                return false; // Signal to stop the loop
            }
        }
        // After any policy update, recalculate and handle state transition
        let old_state = self.current_pci_auth_state;
        let new_state = Self::calculate_auth_state(&self.policy_data);

        if old_state == new_state {
            return true;
        }

        info!("State transition: {:?} -> {:?}", old_state, new_state);
        self.current_pci_auth_state = new_state;

        match (old_state, new_state) {
            (_, PciAuthState::Authorized) => {
                if let Err(e) = self.sysfs_utils.authorize_all_devices() {
                    error!("Failed to authorize all devices: {}", e);
                }
            }
            (_, PciAuthState::DenyNoUser) | (_, PciAuthState::Disabled) => {
                if let Err(e) = self.sysfs_utils.deauthorize_all_devices() {
                    error!("Failed to deauthorize all devices: {}", e);
                }
            }
            _ => { /* Other transitions require no immediate bulk action. */ }
        }
        true // Keep running
    }

    /// Runs the event loop.
    async fn run(mut self) {
        info!("PciAuthorizerTask started.");
        loop {
            tokio::select! {
                uevent_result = self.uevent_socket.read() => {
                    self.handle_uevent_result(uevent_result);
                }
                Some(service_event) = self.event_receiver.recv() => {
                    if !self.handle_service_event(service_event) {
                        info!("Shutdown event received.");
                        break;
                    }
                }
                else => {
                    info!("Event channel closed. Shutting down.");
                    break;
                }
            }
        }
    }
}

/// Orchestrates authorization policy and interacts with the PciAuthorizerTask.
pub struct PciAuthorizer {
    event_sender: mpsc::Sender<PciServiceEvent>,
    service_task_handle: Option<tokio::task::JoinHandle<()>>,
}

impl PciAuthorizer {
    /// Creates a new PciAuthorizer.
    pub fn new(sysfs_utils: SysfsUtils, uevent_socket: Arc<dyn AsyncUEventSocket>) -> Self {
        let (tx, rx) = mpsc::channel(MESSAGE_QUEUE_SIZE);

        let service_policy_data = PolicySourceData::default();
        let initial_auth_state = PciAuthorizerTask::calculate_auth_state(&service_policy_data);

        let service = PciAuthorizerTask {
            uevent_socket,
            event_receiver: rx,
            sysfs_utils,
            policy_data: service_policy_data,
            current_pci_auth_state: initial_auth_state,
        };
        let service_task_handle = tokio::spawn(service.run());

        Self { event_sender: tx, service_task_handle: Some(service_task_handle) }
    }

    fn send_event(&mut self, event: PciServiceEvent) {
        match self.event_sender.try_send(event) {
            Ok(_) => {}
            Err(mpsc::error::TrySendError::Full(_)) => {
                error!("Event channel full. Policy update might be delayed/lost.");
            }
            Err(mpsc::error::TrySendError::Closed(_)) => {
                error!("Event channel closed. Service might have crashed.");
            }
        }
    }
}

impl Default for PciAuthorizer {
    /// Creates a default `PciAuthorizer`.
    fn default() -> Self {
        let sysfs_utils = SysfsUtils::default();
        let uevent_socket_concrete =
            Arc::new(AsyncNetlinkKObjectUEventSocket::create().expect(
                "Failed to create AsyncNetlinkKObjectUEventSocket in PciAuthorizer default",
            ));
        let uevent_socket_trait: Arc<dyn AsyncUEventSocket> = uevent_socket_concrete;
        Self::new(sysfs_utils, uevent_socket_trait)
    }
}

impl TunnelControl for PciAuthorizer {
    fn enable_pci_tunnels(&mut self, enable: bool) {
        self.send_event(PciServiceEvent::EnablePciTunnels(enable));
    }

    fn update_lock_state(&mut self, locked: bool) {
        self.send_event(PciServiceEvent::UpdateLockState(locked));
    }

    fn update_logged_in_state(&mut self, logged_in: bool, user_id: UserId) {
        self.send_event(PciServiceEvent::UpdateLoggedInState { logged_in, user_id });
    }
}

impl Drop for PciAuthorizer {
    fn drop(&mut self) {
        info!("PciAuthorizer dropping. Shutting down PciAuthorizerTask.");

        if self.event_sender.try_send(PciServiceEvent::Shutdown).is_err() {
            error!("Failed to send shutdown signal to PciAuthorizerTask or channel already closed. Task might not shut down via signal.");
        }

        if let Some(_handle) = self.service_task_handle.take() {
            info!("PciAuthorizerTask shutdown initiated. The task will be managed by the Tokio runtime.");
        }
    }
}
