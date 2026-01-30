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

#[cfg(test)]
mod pci_authorizer_tests {
    use std::fs;
    use std::os::unix::fs::symlink;
    use std::path::{Path, PathBuf};
    use std::sync::Arc;
    use std::time::Instant;
    use tempfile::TempDir;
    use tokio::time::{sleep, Duration};
    use uevent::netlink::AsyncUEventSocket;
    use usb4_policies::common::{TunnelControl, UserId};
    use usb4_policies::pci_authorizer::PciAuthorizer;
    use usb4_policies::sysfs::SysfsUtils;

    // Time between file reads.
    const POLL_DURATION: Duration = Duration::from_millis(30);

    // Wait for this duration for paths to be updated to desired value.
    const WAIT_FOR_PATH_DURATION: Duration = Duration::from_millis(500);

    fn setup_environment_for_pci_authorizer_new(
    ) -> (TempDir, SysfsUtils, Arc<dyn AsyncUEventSocket>) {
        let temp_dir = TempDir::new().expect("Failed to create temp_dir");
        let root = temp_dir.path();

        fs::create_dir_all(root.join("sys/bus/pci/devices"))
            .expect("Failed to create mock pci devices dir");
        fs::create_dir_all(root.join("sys/bus/thunderbolt/devices"))
            .expect("Failed to create mock tbt devices dir");

        let sysfs_utils = SysfsUtils::with_root_path(root.to_path_buf());

        let uevent_socket_concrete =
            Arc::new(uevent::netlink::AsyncNetlinkKObjectUEventSocket::create().expect(
                "Failed to create AsyncNetlinkKObjectUEventSocket. \
                Test environment might not support netlink, or permissions are insufficient. \
                This is required for PciAuthorizer tests.",
            ));
        let uevent_socket_trait: Arc<dyn AsyncUEventSocket> = uevent_socket_concrete;

        (temp_dir, sysfs_utils, uevent_socket_trait)
    }

    fn create_mock_tbt_device(sysfs_root: &Path, name: &str, initial_authorized: &str) -> PathBuf {
        let dev_path = sysfs_root.join("sys/bus/thunderbolt/devices").join(name);
        fs::create_dir_all(&dev_path).expect("Failed to create mock tbt device dir");

        let authorized_file = dev_path.join("authorized");
        fs::write(authorized_file, initial_authorized)
            .expect("Failed to write mock tbt authorized file");

        let subsystem_symlink_target_dir = sysfs_root.join("sys/bus/thunderbolt");
        fs::create_dir_all(&subsystem_symlink_target_dir)
            .expect("Failed to create mock tbt subsystem dir");

        let subsystem_symlink_path = dev_path.join("subsystem");
        symlink(&subsystem_symlink_target_dir, &subsystem_symlink_path)
            .expect("Failed to create mock tbt subsystem symlink");

        dev_path
    }

    fn create_mock_pci_device(sysfs_root: &Path, name: &str, removable: bool) -> PathBuf {
        let dev_path = sysfs_root.join("sys/bus/pci/devices").join(name);
        fs::create_dir_all(&dev_path).expect("Failed to create mock pci device dir");
        fs::write(dev_path.join("removable"), if removable { "1" } else { "0" })
            .expect("Failed to write mock pci removable file");

        fs::write(dev_path.join("remove"), "0").expect("Failed to write mock pci remove file");
        dev_path
    }

    async fn assert_wait_for_path_eq(path: PathBuf, expected_value: &str, assert_why: &str) {
        let start = Instant::now();
        let mut read_value: String = Default::default();

        // Wait for value to become expected value.
        while Instant::now().duration_since(start) < WAIT_FOR_PATH_DURATION {
            read_value = fs::read_to_string(&path).unwrap();
            if read_value.trim() == expected_value {
                break;
            }

            sleep(POLL_DURATION).await;
        }

        assert_eq!(read_value.trim(), expected_value, "{}", assert_why);
    }

    #[tokio::test]
    async fn test_full_authorization_flow() {
        let _ = env_logger::try_init();
        let (temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let root = temp_dir.path();
        let mut pci_authorizer = PciAuthorizer::new(sysfs_utils.clone(), uevent_socket);

        let tbt_dev_path = create_mock_tbt_device(root, "0-0", "0");

        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "0",
            "TBT device should initially be deauthorized",
        )
        .await;

        // 1. Enable PCI Tunnels (State -> DenyNoUser)
        pci_authorizer.enable_pci_tunnels(true);
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "0",
            "TBT device should remain deauthorized on DenyNoUser",
        )
        .await;

        // 2. User logs in (State -> DeferNewDevices)
        pci_authorizer.update_logged_in_state(true, UserId(1));
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "0",
            "TBT device should remain deauthorized on DeferNewDevices",
        )
        .await;

        // 3. Screen unlocks (State -> Authorized)
        pci_authorizer.update_lock_state(false);
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "1",
            "TBT device should be authorized on Authorized state",
        )
        .await;

        drop(pci_authorizer);
    }

    #[tokio::test]
    async fn test_deauthorization_flow() {
        let _ = env_logger::try_init();
        let (temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let root = temp_dir.path();
        let mut pci_authorizer = PciAuthorizer::new(sysfs_utils.clone(), uevent_socket);

        let tbt_dev_path = create_mock_tbt_device(root, "1-0", "0");
        let removable_pci_dev_path = create_mock_pci_device(root, "pci0", true);

        // Setup: Go to Authorized state first
        pci_authorizer.enable_pci_tunnels(true);
        pci_authorizer.update_logged_in_state(true, UserId(1));
        pci_authorizer.update_lock_state(false);
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "1",
            "TBT device should be authorized",
        )
        .await;
        assert_eq!(
            fs::read_to_string(removable_pci_dev_path.join("remove")).unwrap().trim(),
            "1",
            "Removable PCI device 'remove' file should be '1'"
        );

        // 1. Screen locks (State -> DeferNewDevices)
        pci_authorizer.update_lock_state(true);
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "1",
            "TBT device should remain authorized on DeferNewDevices",
        )
        .await;

        // 2. User logs out (State -> DenyNoUser)
        pci_authorizer.update_logged_in_state(false, UserId(1)); // Last user logs out
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "0",
            "TBT device should be deauthorized on DenyNoUser",
        )
        .await;
        assert_eq!(
            fs::read_to_string(removable_pci_dev_path.join("remove")).unwrap().trim(),
            "1",
            "Removable PCI device should be removed on DenyNoUser"
        );

        // Re-setup to Authorized state for the next step
        fs::write(removable_pci_dev_path.join("remove"), "0").unwrap(); // Reset remove state
        pci_authorizer.update_logged_in_state(true, UserId(1)); // Log back in
        pci_authorizer.update_lock_state(false); // Unlock screen (State -> Authorized)
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "1",
            "TBT device should be re-authorized",
        )
        .await;

        // 3. Disable PCI Tunnels (State -> Disabled)
        pci_authorizer.enable_pci_tunnels(false);
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "0",
            "TBT device should be deauthorized when tunnels are disabled",
        )
        .await;
        assert_eq!(
            fs::read_to_string(removable_pci_dev_path.join("remove")).unwrap().trim(),
            "1",
            "Removable PCI device should be removed when tunnels are disabled"
        );

        drop(pci_authorizer);
    }

    #[tokio::test]
    async fn test_drop_shuts_down_task() {
        let _ = env_logger::try_init();
        let (_temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let pci_authorizer = PciAuthorizer::new(sysfs_utils.clone(), uevent_socket);

        // Drop the PciAuthorizer, its Drop impl should signal and await the service task.
        drop(pci_authorizer);

        // The test passes if drop completes without panic.
        // A panic in the task during shutdown would be propagated by the await in Drop.
        // Allow a bit of time for async runtime to fully process the drop and task completion.
    }
}
