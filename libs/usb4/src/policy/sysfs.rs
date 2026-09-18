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

use crate::common::{AlternateMode, TypeCPartnerAltMode, TypecAltMode};
use log::{debug, error, info};

/// A generic Result type for the application's operations,
/// returning `Box<dyn std::error::Error>` on failure.
pub type Result<T> = std::result::Result<T, Box<dyn Error>>;

/// `SysfsUtils` struct.
/// It holds paths to various sysfs entries related to PCI and Thunderbolt devices.
#[derive(Clone)]
pub struct SysfsUtils {
    tbt_devices_path: PathBuf,
    typec_path: PathBuf,
    root: PathBuf,
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
            typec_path: root.join("sys/class/typec"),
            root,
        }
    }

    /// Prepends the root path and "sys" in front of the given path.
    pub fn add_sysfs_prefix(&self, dev_path: &Path) -> PathBuf {
        self.root.join("sys").join(dev_path)
    }

    /// Checks whether USB4/Thunderbolt is supported by checking for the existence
    /// of the thunderbolt sysfs path and whether there are any devices within.
    /// If there are no devices, there is no current support for USB4/TBT.
    pub fn check_pci_tunnels_supported(&self) -> bool {
        self.tbt_devices_path.exists()
            && fs::read_dir(&self.tbt_devices_path)
                .map(|mut rd| rd.any(|dir| dir.map(|d| d.path().is_dir()).unwrap_or(false)))
                .unwrap_or(false)
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
                    debug!(
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
            debug!("Authorizing: {:?}", devpath);
            "1"
        } else {
            debug!("Deauthorizing: {:?}", devpath);
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
    pub fn authorize_all_devices(&self, retries: u8) -> Result<()> {
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
                if let Some(io_err) = e.downcast_ref::<std::io::Error>() {
                    if io_err.kind() == io::ErrorKind::PermissionDenied && retries > 0 {
                        debug!("Returning early due to Permission Error on authorization");
                        return Err(e);
                    }
                }
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
        debug!("Deauthorizing all external PCI devices");

        let mut overall_success = true;

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

    /// Gets a list of all typec ports.
    pub fn get_typec_ports(&self) -> io::Result<Vec<String>> {
        let mut ports = Vec::new();
        if !self.typec_path.exists() {
            return Ok(ports);
        }
        for entry in fs::read_dir(&self.typec_path)? {
            let entry = entry?;
            let path = entry.path();
            if path.is_dir() {
                if let Some(dir_name) = path.file_name().and_then(|s| s.to_str()) {
                    if dir_name.starts_with("port") && !dir_name.contains("-") {
                        ports.push(dir_name.to_string());
                    }
                }
            }
        }
        Ok(ports)
    }

    /// Gets a list of alternate mode directories for a given entity (port or partner).
    fn get_alt_mode_dirs(&self, name: &str) -> io::Result<Vec<(PathBuf, String)>> {
        let mut alt_mode_dirs = Vec::new();
        let path = self.typec_path.join(name);
        if !path.exists() {
            return Ok(alt_mode_dirs);
        }

        for entry in fs::read_dir(path)? {
            let entry = entry?;
            let sysfs_path = entry.path();
            if sysfs_path.is_dir() {
                if let Some(dir_name) = sysfs_path.file_name().and_then(|s| s.to_str()) {
                    if dir_name.starts_with(&format!("{}.", name)) {
                        alt_mode_dirs.push((sysfs_path.clone(), dir_name.to_string()));
                    }
                }
            }
        }
        Ok(alt_mode_dirs)
    }

    /// Gets a list of alternate modes for a given port.
    pub fn get_alternate_modes(&self, port: &str) -> io::Result<Vec<TypecAltMode>> {
        let alt_mode_dirs = self.get_alt_mode_dirs(port)?;
        let mut modes = Vec::new();
        for (sysfs_path, dir_name) in alt_mode_dirs {
            let svid = self.read_svid(port, &dir_name).unwrap_or(0);
            let priority = self.read_priority(port, &dir_name).unwrap_or(u8::MAX);
            let active = self.read_active_status(port, &dir_name).unwrap_or(false);
            modes.push(TypecAltMode { sysfs_path, svid, priority, active });
        }
        Ok(modes)
    }

    /// Gets a list of alternate modes for a given partner.
    pub fn get_partner_alternate_modes(&self, port: &str) -> io::Result<Vec<TypeCPartnerAltMode>> {
        let partner_name = format!("{}-partner", port);
        let alt_mode_dirs = self.get_alt_mode_dirs(&partner_name)?;
        let mut modes = Vec::new();
        for (sysfs_path, dir_name) in alt_mode_dirs {
            // Partner alt modes don't have priority.
            let svid = self.read_svid(&partner_name, &dir_name).unwrap_or(0);
            let active = self.read_active_status(&partner_name, &dir_name).unwrap_or(false);
            modes.push(TypeCPartnerAltMode { sysfs_path, svid, active });
        }
        Ok(modes)
    }

    /// Reads the SVID for a given alternate mode.
    pub fn read_svid(&self, port: &str, alt_mode: &str) -> Result<u16> {
        let svid_path = self.typec_path.join(port).join(alt_mode).join("svid");
        let svid_str = fs::read_to_string(svid_path)?;
        // SVID is in hex format "0x...."
        u16::from_str_radix(svid_str.trim().trim_start_matches("0x"), 16)
            .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e).into())
    }

    /// Reads the active status for a given alternate mode.
    pub fn read_active_status(&self, port: &str, alt_mode: &str) -> Result<bool> {
        let active_path = self.typec_path.join(port).join(alt_mode).join("active");
        let active_str = fs::read_to_string(active_path)?;
        Ok(active_str.trim() == "yes")
    }

    /// Sets the active status for a given partner alternate mode.
    pub fn set_active_status(&self, port: &str, svid: u16, active: bool) -> Result<()> {
        for alt_mode in self.get_partner_alternate_modes(port)? {
            if alt_mode.svid == svid {
                let active_path = alt_mode.sysfs_path.join("active");
                let val = if active { "yes" } else { "no" };
                fs::write(&active_path, val)?;
                return Ok(());
            }
        }
        Err(format!("Typec {} alt mode {:#06x} not found", port.to_owned(), svid).into())
    }

    /// Reads the priority for a given alternate mode.
    pub fn read_priority(&self, port: &str, alt_mode: &str) -> Result<u8> {
        let priority_path = self.typec_path.join(port).join(alt_mode).join("priority");
        let priority_str = fs::read_to_string(priority_path)?;
        priority_str
            .trim()
            .parse::<u8>()
            .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e).into())
    }

    /// Writes the priority for a given alternate mode.
    pub fn write_priority(&self, port: &str, alt_mode: &str, priority: u8) -> Result<()> {
        let priority_path = self.typec_path.join(port).join(alt_mode).join("priority");
        fs::write(priority_path, priority.to_string())?;
        Ok(())
    }

    /// Writes the mode preferences to sysfs to trigger mode selection.
    pub fn write_mode_preferences(&self, mode_preference: &[AlternateMode]) {
        info!("Trying to write USB alt mode order: {:?}", mode_preference);

        let ports = match self.get_typec_ports() {
            Ok(ports) => ports,
            Err(e) => {
                debug!("Failed to get typec ports: {}", e);
                return;
            }
        };

        for port in &ports {
            let alt_modes = match self.get_alternate_modes(port) {
                Ok(alt_modes) => alt_modes,
                Err(e) => {
                    debug!("Failed to get alternate modes for port {}: {}", port, e);
                    continue;
                }
            };

            for alt_mode in alt_modes {
                if let Some(mode) = AlternateMode::from_svid(alt_mode.svid) {
                    if let Some(priority) = mode_preference.iter().position(|&m| m == mode) {
                        if let Some(alt_mode_dir) =
                            alt_mode.sysfs_path.file_name().and_then(|s| s.to_str())
                        {
                            if let Err(e) = self.write_priority(port, alt_mode_dir, priority as u8)
                            {
                                debug!(
                                    "Failed to write priority for {}/{}: {}",
                                    port, alt_mode_dir, e
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}

impl Default for SysfsUtils {
    fn default() -> Self {
        Self::new()
    }
}
