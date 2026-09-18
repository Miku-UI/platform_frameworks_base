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

//! # USB Mode Selection
//!
//! This module provides functionality for selecting and managing USB alternate
//! modes.

use std::collections::HashSet;
use std::thread;
use std::time::Duration;
use tokio::sync::mpsc;

use crate::common::{AlternateMode, PolicySourceData, TunnelControl, UserId};
use crate::sysfs::SysfsUtils;
use log::{debug, error, info};

const TBT3_PREFERRED_ORDER: &[AlternateMode] =
    &[AlternateMode::USB4, AlternateMode::TBT3, AlternateMode::DP];
const DP_PREFERRED_ORDER: &[AlternateMode] =
    &[AlternateMode::USB4, AlternateMode::DP, AlternateMode::TBT3];

/// Maximum number of retries for mode selection.
const MAX_MODE_SELECTION_RETRIES: u32 = 3;

const MODE_SELECTION_TIMEOUT: u64 = 1250;

/// Message queue size.
const MESSAGE_QUEUE_SIZE: usize = 24;

/// Event sent from ModeSelector to ModeSelectorTask
#[derive(Debug, Clone)]
enum ModeSelectorEvent {
    EnablePciTunnels(bool),
    UpdateLockState(bool),
    UpdateLoggedInState { logged_in: bool, user_id: UserId },
    ModeSelection { port: String, retries: u32 },
    Shutdown,
}

/// Internal service that runs an async event loop for policy updates.
struct ModeSelectorTask {
    event_sender: mpsc::Sender<ModeSelectorEvent>,
    event_receiver: mpsc::Receiver<ModeSelectorEvent>,
    sysfs_utils: SysfsUtils,
    policy_data: PolicySourceData,
    mode_preference: Vec<AlternateMode>,
}

impl ModeSelectorTask {
    fn send_event(&self, event: ModeSelectorEvent) {
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

    /// Handles a received service event. Returns true if the service should continue running.
    fn handle_service_event(&mut self, service_event: ModeSelectorEvent) -> bool {
        match service_event {
            ModeSelectorEvent::EnablePciTunnels(enable) => {
                self.policy_data.pci_tunnels_enabled = enable;
                /* When the PCI tunnel flag is updated, both mode selection and pci authorization need to
                 * apply immediately (so they don't get out of sync. */
                self.update_mode_preference(/*trigger_selection=*/ true);
            }
            ModeSelectorEvent::UpdateLockState(locked) => {
                self.policy_data.is_locked = locked;
                // Per policy, trigger mode selection only on screen unlock.
                let trigger = !locked;
                self.update_mode_preference(trigger);
            }
            ModeSelectorEvent::UpdateLoggedInState { logged_in, user_id } => {
                if logged_in {
                    self.policy_data.logged_in_users.insert(user_id);
                } else {
                    self.policy_data.logged_in_users.remove(&user_id);
                }
                // Per policy, trigger mode selection on both login and logout.
                self.update_mode_preference(true);
            }
            ModeSelectorEvent::ModeSelection { port, retries } => {
                if retries > 0 {
                    self.trigger_mode_selection_for_port(&port, retries);
                } else {
                    error!(
                        "Mode selection failed for port {} after {} retries",
                        port, MAX_MODE_SELECTION_RETRIES
                    );
                }
            }
            ModeSelectorEvent::Shutdown => {
                return false; // Signal to stop the loop
            }
        }
        true // Keep running
    }

    /// Updates the mode preference based on the current policy data.
    fn update_mode_preference(&mut self, trigger_selection: bool) {
        let pci_tunnels_enabled = self.policy_data.pci_tunnels_enabled;
        let has_logged_in_users = !self.policy_data.logged_in_users.is_empty();
        let screen_unlocked = !self.policy_data.is_locked;

        // Prefer TBT3 only when pci_tunnels are enabled, a user is logged in and the screen is
        // unlocked.
        let new_preference = if pci_tunnels_enabled && has_logged_in_users && screen_unlocked {
            TBT3_PREFERRED_ORDER.to_vec()
        } else {
            DP_PREFERRED_ORDER.to_vec()
        };

        if self.mode_preference != new_preference {
            self.mode_preference = new_preference;
            self.sysfs_utils.write_mode_preferences(&self.mode_preference);
        }

        if trigger_selection {
            self.trigger_mode_selection_all();
        }
    }

    /// Triggers a mode selection event in the kernel for all ports.
    fn trigger_mode_selection_all(&self) {
        let ports = match self.sysfs_utils.get_typec_ports() {
            Ok(ports) => ports,
            Err(e) => {
                error!("Failed to get typec ports: {}", e);
                return;
            }
        };

        debug!("Try typec mode selection on all ports");
        for port in &ports {
            self.send_event(ModeSelectorEvent::ModeSelection {
                port: port.clone(),
                retries: MAX_MODE_SELECTION_RETRIES,
            });
        }
    }

    /// Triggers a mode selection event in the kernel for a single port.
    fn trigger_mode_selection_for_port(&mut self, port: &str, retries: u32) {
        let port_alt_modes = match self.sysfs_utils.get_alternate_modes(port) {
            Ok(modes) => modes,
            Err(e) => {
                error!("Failed to get alternate modes for port {}: {}", port, e);
                return;
            }
        };

        let partner_alt_modes = match self.sysfs_utils.get_partner_alternate_modes(port) {
            Ok(modes) => modes,
            Err(e) => {
                error!("Failed to get partner alternate modes for port {}: {}", port, e);
                return;
            }
        };

        let partner_svids: HashSet<u16> = partner_alt_modes.iter().map(|m| m.svid).collect();
        // Ignore alt modes that are disabled on the port side.
        let mut common_modes: Vec<_> = port_alt_modes
            .iter()
            .filter(|m| partner_svids.contains(&m.svid))
            .filter(|m| m.active)
            .cloned()
            .collect();
        let active_mode = partner_alt_modes.iter().find(|m| m.active);

        if common_modes.is_empty() {
            info!("Typec {} and partner don't have common modes", port);
            return;
        }

        common_modes.sort_by_key(|m| m.priority);
        let highest_priority_mode = &common_modes[0];

        // If there is an active mode already compare it with the highest priority common mode.
        // If they don't match, we need to do mode selection. Deactive the active mode first and then enter
        // the highest priority mode. If there is no active mode enter the highest priority mode.
        // Returns true/false + error depending on the mode selection result.
        let mode_selection_triggered = match active_mode {
            Some(active) => {
                if active.svid != highest_priority_mode.svid {
                    self.sysfs_utils
                        .set_active_status(port, active.svid, false)
                        .and_then(|_| {
                            // Writing to sysfs must be synchronous (so multiple events don't try to write at once)
                            // thus use a synchronous sleep here.
                            thread::sleep(Duration::from_millis(MODE_SELECTION_TIMEOUT));
                            self.sysfs_utils.set_active_status(
                                port,
                                highest_priority_mode.svid,
                                true,
                            )
                        })
                        .map(|_| true)
                } else {
                    info!(
                        "Typec {} is in expected alternate mode {:#06x}",
                        port, highest_priority_mode.svid
                    );
                    Ok(false)
                }
            }
            None => {
                // No alternate mode was active, active the highest common one
                self.sysfs_utils
                    .set_active_status(port, highest_priority_mode.svid, true)
                    .map(|_| true)
            }
        };

        match mode_selection_triggered {
            // Mode selection was triggered, lunch verification task.
            Ok(true) => {
                let sender = self.event_sender.clone();
                let sysfs_utils = self.sysfs_utils.clone();
                let port_string = port.to_string();
                tokio::spawn(async move {
                    Self::handle_mode_selection_success_async(
                        sender,
                        sysfs_utils,
                        port_string,
                        retries,
                    )
                    .await;
                });
            }
            // There was an error while triggering mode selection. Try again.
            Err(e) => {
                error!("Failed to trigger mode selection for typec {}: {}", port, e);
                let sender = self.event_sender.clone();
                let port_string = port.to_string();
                tokio::spawn(async move {
                    Self::handle_mode_selection_failure_async(sender, port_string, retries).await;
                });
            }
            // Mode selection was not needed, do nothing.
            Ok(false) => {}
        };
    }

    /// Handles the successful triggering of mode selection by verifying the active mode.
    async fn handle_mode_selection_success_async(
        sender: mpsc::Sender<ModeSelectorEvent>,
        sysfs_utils: SysfsUtils,
        port: String,
        retries: u32,
    ) {
        // Wait for mode selection to finish.
        tokio::time::sleep(Duration::from_millis(MODE_SELECTION_TIMEOUT)).await;
        let partner_alt_modes = match sysfs_utils.get_partner_alternate_modes(&port) {
            Ok(partner_alt_modes) => partner_alt_modes,
            Err(e) => {
                error!(
                    "Failed to get partner alternate modes for port {} during verification: {}",
                    port, e
                );
                return;
            }
        };

        match partner_alt_modes.iter().find(|m| m.active) {
            Some(active_mode) => {
                info!(
                    "Successfully entered alternate mode (SVID: {:#06x}) for port {}",
                    active_mode.svid, port
                );
            }
            None => {
                Self::retrigger_event(sender, port, retries).await;
            }
        }
    }

    /// Handles a failure to trigger mode selection by scheduling a retry.
    async fn handle_mode_selection_failure_async(
        sender: mpsc::Sender<ModeSelectorEvent>,
        port: String,
        retries: u32,
    ) {
        // Wait for mode selection to finish.
        tokio::time::sleep(Duration::from_millis(MODE_SELECTION_TIMEOUT)).await;
        Self::retrigger_event(sender, port, retries).await;
    }

    /// Queues a retry event for mode selection.
    async fn retrigger_event(sender: mpsc::Sender<ModeSelectorEvent>, port: String, retries: u32) {
        info!("Retrying mode selection for port {} ({} retries left)", port, retries - 1);
        let event = ModeSelectorEvent::ModeSelection { port, retries: retries - 1 };
        if sender.send(event).await.is_err() {
            error!("Failed to queue retry for mode selection: channel closed.");
        }
    }

    /// Runs the event loop.
    async fn run(mut self) {
        debug!("ModeSelectorTask started.");
        while let Some(service_event) = self.event_receiver.recv().await {
            if !self.handle_service_event(service_event) {
                debug!("Shutdown event received.");
                break;
            }
        }
        debug!("Event channel closed. Shutting down.");
    }
}

/// Manages the selection of USB alternate modes.
pub struct ModeSelector {
    event_sender: mpsc::Sender<ModeSelectorEvent>,
    service_task_handle: Option<tokio::task::JoinHandle<()>>,
}

impl ModeSelector {
    /// Creates a new `ModeSelector` with a default mode.
    pub fn new() -> Self {
        Self::with_sysfs_utils(SysfsUtils::new())
    }

    /// Creates a `ModeSelector` with a specific `SysfsUtils` instance.
    pub fn with_sysfs_utils(sysfs_utils: SysfsUtils) -> Self {
        let (tx, rx) = mpsc::channel(MESSAGE_QUEUE_SIZE);

        let service = ModeSelectorTask {
            event_sender: tx.clone(),
            event_receiver: rx,
            sysfs_utils,
            policy_data: PolicySourceData::new(),
            mode_preference: Vec::new(),
        };
        let service_task_handle = tokio::spawn(service.run());

        Self { event_sender: tx, service_task_handle: Some(service_task_handle) }
    }

    fn send_event(&mut self, event: ModeSelectorEvent) {
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

impl Default for ModeSelector {
    fn default() -> Self {
        Self::new()
    }
}

impl TunnelControl for ModeSelector {
    fn enable_pci_tunnels(&mut self, enable: bool) {
        self.send_event(ModeSelectorEvent::EnablePciTunnels(enable));
    }

    fn update_lock_state(&mut self, locked: bool) {
        self.send_event(ModeSelectorEvent::UpdateLockState(locked));
    }

    fn update_logged_in_state(&mut self, logged_in: bool, user_id: UserId) {
        self.send_event(ModeSelectorEvent::UpdateLoggedInState { logged_in, user_id });
    }
}

impl Drop for ModeSelector {
    fn drop(&mut self) {
        debug!("ModeSelector dropping. Shutting down ModeSelectorTask.");

        if self.event_sender.try_send(ModeSelectorEvent::Shutdown).is_err() {
            error!("Failed to send shutdown signal to ModeSelectorTask or channel already closed. Task might not shut down via signal.");
        }

        if let Some(_handle) = self.service_task_handle.take() {
            debug!(
                "ModeSelectorTask shutdown initiated. The task will be managed by the Tokio runtime."
            );
        }
    }
}
