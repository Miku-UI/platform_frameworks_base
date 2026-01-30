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

use std::error::Error;
use std::fs;
use std::io::{self};
use std::path::{Path, PathBuf}; // For Box<dyn Error>

// Import logging macros. A logger (e.g., simple_logger) should be initialized
// in the binary (main.rs) that uses this library.
use log::{error, info};

/// A generic Result type for the application's operations,
/// returning `Box<dyn std::error::Error>` on failure.
pub type Result<T> = std::result::Result<T, Box<dyn Error>>;

/// `SysfsUtils` struct.
/// It holds paths to various sysfs entries related to PCI and Thunderbolt devices.
#[derive(Clone)]
pub struct SysfsUtils {
    tbt_devices_path: PathBuf,
    pci_devices_path: PathBuf,
}

impl SysfsUtils {
    /// Creates a new `SysfsUtils` instance, initializing paths relative to the root directory `/`.
    pub fn new() -> Self {
        Self::with_root_path(PathBuf::from("/"))
    }

    /// Creates a `SysfsUtils` instance, initializing paths relative to a specified root directory.
    pub fn with_root_path(root: PathBuf) -> Self {
        SysfsUtils {
            tbt_devices_path: root.join("sys/bus/thunderbolt/devices"),
            pci_devices_path: root.join("sys/bus/pci/devices"),
        }
    }

    /// Sets the "authorized" attribute for a given device path.
    /// Returns `Ok(())` on success, `Err` on failure.
    fn set_authorized_attribute(&self, devpath: &Path, enable: bool) -> Result<()> {
        // Check if the device path exists.
        if !devpath.exists() {
            error!("Path doesn't exist: {:?}", devpath);
            return Err(io::Error::new(
                io::ErrorKind::NotFound,
                format!("Path does not exist: {:?}", devpath),
            )
            .into());
        }

        let symlink_path = devpath.join("subsystem");
        let symlink_target = fs::read_link(&symlink_path).map_err(|e| {
            io::Error::new(e.kind(), format!("Failed to read symlink {:?}: {}", symlink_path, e))
        })?;

        // Check if it's a thunderbolt device path.
        if !symlink_target.to_string_lossy().ends_with("/bus/thunderbolt") {
            error!("Not a thunderbolt devpath: {:?}", devpath);
            return Err(
                io::Error::new(io::ErrorKind::InvalidInput, "Not a thunderbolt devpath").into()
            );
        }

        let authorized_path = devpath.join("authorized");
        let current_authorized_content = fs::read_to_string(&authorized_path);

        // If reading the 'authorized' file fails (e.g., file not found),
        // no action is needed, so return success.
        let current_state_char = match current_authorized_content {
            Ok(s) => s.chars().next(),
            Err(e) => {
                if e.kind() == io::ErrorKind::NotFound {
                    info!(
                        "'authorized' file not found at {:?}, skipping authorization.",
                        authorized_path
                    );
                    return Ok(());
                }
                return Err(io::Error::new(
                    e.kind(),
                    format!("Failed to read {:?}: {}", authorized_path, e),
                )
                .into());
            }
        };

        // If the current state is already the desired state, do nothing and return success.
        // If the file was empty (no chars), proceed to write the desired state.
        if let Some(state_char) = current_state_char {
            if (enable && state_char == '1') || (!enable && state_char == '0') {
                return Ok(());
            }
        }

        let val = if enable {
            info!("Authorizing: {:?}", devpath);
            "1"
        } else {
            info!("Deauthorizing: {:?}", devpath);
            "0"
        };

        // Write the new state to the 'authorized' file.
        fs::write(&authorized_path, val).map_err(|e| {
            io::Error::new(
                e.kind(),
                format!("Couldn't write {} to {:?}: {}", val, authorized_path, e),
            )
        })?;

        Ok(())
    }

    /// Deauthorizes a Thunderbolt device.
    pub fn deauthorize_thunderbolt_dev(&self, devpath: &Path) -> Result<()> {
        self.set_authorized_attribute(devpath, false)
    }

    /// Authorizes a Thunderbolt device.
    pub fn authorize_thunderbolt_dev(&self, devpath: &Path) -> Result<()> {
        self.set_authorized_attribute(devpath, true)
    }

    /// Authorizes all external PCI devices.
    /// Returns `Ok(())` on success, `Err` on failure.
    pub fn authorize_all_devices(&self) -> Result<()> {
        info!("Authorizing all external PCI devices");

        // Collect all thunderbolt device paths.
        let mut thunderbolt_devs: Vec<PathBuf> = Vec::new();
        for entry in fs::read_dir(&self.tbt_devices_path)? {
            let entry = entry?;
            let devpath = entry.path();
            if devpath.is_dir() {
                thunderbolt_devs.push(devpath);
            }
        }

        // Sort thunderbolt devices based on their symbolic link targets to achieve BFS order.
        // Authorization should be parent before children.
        thunderbolt_devs.sort_by(|dev1, dev2| {
            let symlink1 = fs::read_link(dev1).unwrap_or_else(|_| PathBuf::new());
            let symlink2 = fs::read_link(dev2).unwrap_or_else(|_| PathBuf::new());
            symlink1.cmp(&symlink2)
        });

        let mut overall_success = true;
        // Authorize each thunderbolt device.
        for dev in thunderbolt_devs {
            if let Err(e) = self.authorize_thunderbolt_dev(&dev) {
                error!("Failed to authorize thunderbolt device {:?}: {}", dev, e);
                overall_success = false;
            }
        }

        if overall_success {
            Ok(())
        } else {
            Err(io::Error::other("Failed to authorize all thunderbolt devices").into())
        }
    }

    /// Deauthorizes all external PCI devices.
    /// Returns `Ok(())` on success, `Err` on failure.
    pub fn deauthorize_all_devices(&self) -> Result<()> {
        info!("Deauthorizing all external PCI devices");

        let mut overall_success = true;

        // Iterate through all PCI devices.
        for entry in fs::read_dir(&self.pci_devices_path)? {
            let entry = entry?;
            let devpath = entry.path();
            if !devpath.is_dir() {
                continue;
            }

            // It's possible a device was already removed as a child of another.
            if !devpath.exists() {
                continue;
            }

            let removable_path = devpath.join("removable");
            // Read the content of the "removable" file. Use default if read fails.
            let removable_content = fs::read_to_string(&removable_path).unwrap_or_default();

            // Proceed only if the device is marked as "removable"
            if removable_content.trim() != "1" {
                continue;
            }

            // Write "1" to the "remove" file to remove the device.
            let remove_path = devpath.join("remove");
            if let Err(e) = fs::write(&remove_path, "1") {
                error!("Couldn't remove untrusted device {:?}: {}", devpath, e);
                overall_success = false;
            }
        }

        // Deauthorize all thunderbolt devices.
        for entry in fs::read_dir(&self.tbt_devices_path)? {
            let entry = entry?;
            let devpath = entry.path();
            if !devpath.is_dir() {
                continue;
            }
            if let Err(e) = self.deauthorize_thunderbolt_dev(&devpath) {
                error!("Failed to deauthorize thunderbolt device {:?}: {}", devpath, e);
                overall_success = false;
            }
        }

        if overall_success {
            Ok(())
        } else {
            Err(io::Error::other("Failed during deauthorization of all devices").into())
        }
    }
}

impl Default for SysfsUtils {
    fn default() -> Self {
        Self::new()
    }
}
