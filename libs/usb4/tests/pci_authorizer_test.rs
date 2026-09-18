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
    use anyhow::{anyhow, Result};
    use async_trait::async_trait;
    use kobject_uevent;
    use std::fs;
    use std::os::unix::fs::{symlink, PermissionsExt};
    use std::path::{Path, PathBuf};
    use std::time::Instant;
    use tempfile::TempDir;
    use tokio::sync::mpsc;
    use tokio::time::{sleep, Duration};
    use uevent::netlink::AsyncUEventSocket;
    use usb4_policies::common::{TunnelControl, UserId};
    use usb4_policies::pci_authorizer::PciAuthorizer;
    use usb4_policies::sysfs::SysfsUtils;

    // Time between file reads.
    const POLL_DURATION: Duration = Duration::from_millis(30);

    // Wait for this duration for paths to be updated to desired value.
    const WAIT_FOR_PATH_DURATION: Duration = Duration::from_millis(500);

    // Delay by 50ms to emulate ueventd delay.
    const UEVENTD_MOCK_DELAY: Duration = Duration::from_millis(50);

    struct FakeUeventSocket {
        pub tx: mpsc::Sender<Result<kobject_uevent::UEvent>>,
        rx: mpsc::Receiver<Result<kobject_uevent::UEvent>>,
    }

    impl FakeUeventSocket {
        fn new() -> Self {
            // Create a channel that handles up to 10 messages. Arbitrary value for testing.
            let (tx, rx) = mpsc::channel::<Result<kobject_uevent::UEvent>>(10);
            Self { tx, rx }
        }

        fn into_box_trait(self) -> Box<dyn AsyncUEventSocket> {
            Box::new(self)
        }
    }

    #[async_trait]
    impl AsyncUEventSocket for FakeUeventSocket {
        async fn read(&mut self) -> Result<kobject_uevent::UEvent> {
            match self.rx.recv().await {
                Some(result) => result,
                None => Err(anyhow!("Channel dropped for FakeUeventSocket")),
            }
        }
    }

    fn setup_environment_for_pci_authorizer_new() -> (TempDir, SysfsUtils, FakeUeventSocket) {
        let temp_dir = TempDir::new().expect("Failed to create temp_dir");
        let root = temp_dir.path();

        fs::create_dir_all(root.join("sys/bus/pci/devices"))
            .expect("Failed to create mock pci devices dir");
        fs::create_dir_all(root.join("sys/bus/thunderbolt/devices"))
            .expect("Failed to create mock tbt devices dir");

        let sysfs_utils = SysfsUtils::with_root_path(root.to_path_buf());
        let fake_uevent_socket = FakeUeventSocket::new();

        (temp_dir, sysfs_utils, fake_uevent_socket)
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

    fn create_tbt_device_no_writable(
        sysfs_root: &Path,
        name: &str,
        initial_authorized: &str,
    ) -> PathBuf {
        let dev_path = create_mock_tbt_device(sysfs_root, name, initial_authorized);

        let authorized_file = dev_path.join("authorized");
        let metadata = fs::metadata(&authorized_file).expect("Failed to get tbt file metadata");
        let mut permissions = metadata.permissions();

        // Make the file read-only.
        permissions.set_mode(0o444);
        fs::set_permissions(&authorized_file, permissions)
            .expect("Failed to set tbt device as not writable");

        dev_path
    }

    fn make_tbt_authorized_writable(dev_path: PathBuf) {
        let authorized_file = dev_path.join("authorized");
        let metadata = fs::metadata(&authorized_file).expect("Failed to get tbt file metadata");
        let mut permissions = metadata.permissions();

        // Make the file read-write.
        permissions.set_mode(0o666);
        fs::set_permissions(&authorized_file, permissions)
            .expect("Failed to set tbt device as not writable");
    }

    fn devpath_to_add_uevent(dev_path: PathBuf) -> kobject_uevent::UEvent {
        kobject_uevent::UEvent {
            action: kobject_uevent::ActionType::Add,
            devpath: dev_path,
            subsystem: "thunderbolt".to_string(),
            env: Default::default(),
            seq: Default::default(),
        }
    }

    // Wait until the expected value is seen (Ok) or timeout (Err).
    async fn wait_timeout_for_path_eq(
        path: PathBuf,
        expected_value: &str,
    ) -> Result<String, String> {
        let start = Instant::now();
        let mut read_value: String = Default::default();

        // Wait for value to become expected value.
        while Instant::now().duration_since(start) < WAIT_FOR_PATH_DURATION {
            read_value = fs::read_to_string(&path).unwrap().trim().into();
            if read_value == expected_value {
                return Ok(read_value);
            }

            sleep(POLL_DURATION).await;
        }

        Err(read_value)
    }

    async fn assert_wait_for_path_eq(path: PathBuf, expected_value: &str, assert_why: &str) {
        let result = wait_timeout_for_path_eq(path, expected_value).await;
        let read_value = match result {
            Ok(v) => v,
            Err(v) => v,
        };

        assert_eq!(read_value, expected_value, "{}", assert_why);
    }

    #[tokio::test]
    async fn test_pci_tunnels_supported() {
        let _ = env_logger::try_init();
        let (temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let root = temp_dir.path();
        let _pci_authorizer =
            PciAuthorizer::new(sysfs_utils.clone(), uevent_socket.into_box_trait());

        // Without any devices, tunnels are not supported.
        assert!(!sysfs_utils.check_pci_tunnels_supported());

        let _tbt_dev_path = create_mock_tbt_device(root, "0-0", "0");

        // Now that there's a device in the device path, tunnels should be considered as supported.
        assert!(sysfs_utils.check_pci_tunnels_supported());
    }

    #[tokio::test]
    async fn test_full_authorization_flow() {
        let _ = env_logger::try_init();
        let (temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let root = temp_dir.path();
        let mut pci_authorizer =
            PciAuthorizer::new(sysfs_utils.clone(), uevent_socket.into_box_trait());

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
        let mut pci_authorizer =
            PciAuthorizer::new(sysfs_utils.clone(), uevent_socket.into_box_trait());

        let tbt_dev_path = create_mock_tbt_device(root, "1-0", "0");

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

        // Re-setup to Authorized state for the next step
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

        drop(pci_authorizer);
    }

    #[tokio::test]
    async fn test_drop_shuts_down_task() {
        let _ = env_logger::try_init();
        let (_temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let pci_authorizer =
            PciAuthorizer::new(sysfs_utils.clone(), uevent_socket.into_box_trait());

        // Drop the PciAuthorizer, its Drop impl should signal and await the service task.
        drop(pci_authorizer);

        // The test passes if drop completes without panic.
        // A panic in the task during shutdown would be propagated by the await in Drop.
        // Allow a bit of time for async runtime to fully process the drop and task completion.
    }

    #[tokio::test]
    async fn test_delayed_uevent_handling() {
        let _ = env_logger::try_init();
        let (temp_dir, sysfs_utils, uevent_socket) = setup_environment_for_pci_authorizer_new();
        let root = temp_dir.path();

        let uevent_tx = uevent_socket.tx.clone();
        let mut pci_authorizer =
            PciAuthorizer::new(sysfs_utils.clone(), uevent_socket.into_box_trait());

        let tbt_dev_path = create_mock_tbt_device(root, "0-0", "0");

        // 1. Enable PCI Tunnels (State -> DenyNoUser)
        pci_authorizer.enable_pci_tunnels(true);
        // 2. User logs in (State -> DeferNewDevices)
        pci_authorizer.update_logged_in_state(true, UserId(1));
        // 3. Screen unlocks (State -> Authorized)
        pci_authorizer.update_lock_state(false);
        assert_wait_for_path_eq(
            tbt_dev_path.join("authorized"),
            "1",
            "TBT device should be authorized on Authorized state",
        )
        .await;

        // Now create a non-writable mock device and send via uevent.
        let bad_dev_path = create_tbt_device_no_writable(root, "1-1", "0");
        let tbt_path = PathBuf::from("/bus/thunderbolt/devices/1-1");
        let _ = uevent_tx.send(Ok(devpath_to_add_uevent(tbt_path))).await;

        let dev_path_clone = bad_dev_path.clone();
        tokio::spawn(async move {
            sleep(UEVENTD_MOCK_DELAY).await;
            make_tbt_authorized_writable(dev_path_clone);
        });
        let assert_future = assert_wait_for_path_eq(
            bad_dev_path.join("authorized"),
            "1",
            "TBT device should be eventually authorized",
        );
        assert_future.await;

        drop(pci_authorizer);
    }
}
