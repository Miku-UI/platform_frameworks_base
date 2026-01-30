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

//! Rust interface to the dropbox service.
use anyhow::Result;
use binder::{wait_for_interface, Strong};
use dropboxmanager_aidl::aidl::com::android::internal::os::IDropBoxManagerService::IDropBoxManagerService;

const INTERFACE_NAME: &str = "dropbox";

/// Interface to the DropBox system service.
pub struct DropBoxManager {
    binder: Strong<dyn IDropBoxManagerService>,
}

impl DropBoxManager {
    /// Acquires the underlying binder interface.
    pub fn new() -> Result<Self> {
        Ok(Self { binder: wait_for_interface(INTERFACE_NAME)? })
    }

    /// Creates a dropbox entry with the supplied tag. The supplied text is passed as bytes to create the file contents.
    pub fn add_text(&self, tag: &str, text: &str) -> Result<()> {
        self.binder.addData(tag, text.as_bytes(), 2 /* DropBoxManager.java IS_TEXT */)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::PathBuf;

    const DROPBOX_PATH: &str = "/data/system/dropbox";
    const TAG: &str = "foo";
    const CONTENT: &str = "bar\nbaz\n";

    #[test]
    fn add_text() {
        let _ = find_dropbox_files(true).unwrap();
        let manager = DropBoxManager::new().unwrap();
        manager.add_text(TAG, CONTENT).unwrap();
        let path_buf = find_dropbox_files(false).unwrap().unwrap();
        let content = fs::read_to_string(path_buf.as_path()).unwrap();
        assert_eq!(content, CONTENT);
    }

    fn find_dropbox_files(delete_them: bool) -> Result<Option<PathBuf>> {
        let mut found = None;
        for entry in fs::read_dir(DROPBOX_PATH)? {
            let entry = entry?;
            let path_buf = entry.path();
            if !path_buf.is_file() {
                continue;
            }
            let Some(filename) = path_buf.file_name() else {
                continue;
            };
            let filename = filename.to_string_lossy();
            if !filename.starts_with(TAG) {
                continue;
            }

            found = Some(path_buf.clone());
            if delete_them {
                fs::remove_file(&path_buf)?;
            } else {
                break;
            }
        }
        Ok(found)
    }
}
