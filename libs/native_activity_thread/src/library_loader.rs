//
// Copyright (C) 2025 The Android Open-Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use anyhow::{bail, Context, Result};
use dlext_bindgen::{
    android_create_namespace, android_dlextinfo, android_dlopen_ext, android_namespace_t, dlclose,
    dlsym, ANDROID_DLEXT_USE_NAMESPACE, ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED, RTLD_LOCAL,
};
use std::{
    ffi::{c_void, CString},
    ptr::NonNull,
};

macro_rules! bail_with_dlerror {
    ($fmt:literal $(, $($arg:tt)+)?) => {
        {
            // SAFETY: trivially safe.
            let error = unsafe { libc::dlerror() };
            if !error.is_null() {
                // SAFETY: `error` is a pointer to a valid C string returned by `dlerror()`.
                let error_cstr = unsafe { std::ffi::CStr::from_ptr(error) };
                let dl_error_msg = error_cstr.to_string_lossy();

                anyhow::bail!(
                    concat!($fmt, ": {}"),
                    $($($arg)+,)?
                    dl_error_msg
                );
            } else {
                anyhow::bail!($fmt $(, $($arg)+)?);
            }
        }
    };
}

/// Safe wrapper of a raw pointer to android_namespace_t.
pub struct LinkerNamespace {
    namespace: NonNull<android_namespace_t>,
}

impl LinkerNamespace {
    pub fn as_ptr(&self) -> *mut android_namespace_t {
        self.namespace.as_ptr()
    }
}

/// NamespaceFactory creates linker namespaces.
pub struct NamespaceFactory {
    base_name: String,
    // Used to assign a serial number to each namespace name to make it unique.
    serial: u32,
}

impl NamespaceFactory {
    pub fn new(base_name: String) -> Self {
        Self { base_name, serial: 0 }
    }

    /// Create a linker namespace.
    pub fn create_linker_namespace(
        &mut self,
        library_paths: &[String],
        permitted_libs_dir: &str,
    ) -> Result<LinkerNamespace> {
        let name = CString::new(format!("{}-{}", self.base_name, self.serial))
            .context("invalid namespace name")?;
        let ld_path = CString::new(library_paths.join(":")).context("invalid library paths")?;
        let permitted_libs_dir =
            CString::new(permitted_libs_dir).context("invalid permitted libs dir")?;
        // SAFETY: `name`, `ld_path`, `permitted_libs_dir` are valid pointers and this function
        // accepts the null pointer for `default_library_path` and `parent`.
        let namespace = unsafe {
            android_create_namespace(
                name.as_ptr(),
                ld_path.as_ptr(),
                /* default_library_path= */ std::ptr::null_mut(),
                ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED as u64,
                permitted_libs_dir.as_ptr(),
                /* parent= */ std::ptr::null_mut(),
            )
        };
        match NonNull::new(namespace) {
            Some(namespace) => {
                if let Some(new_serial) = self.serial.checked_add(1) {
                    self.serial = new_serial;
                } else {
                    bail!("too many namespaces were created");
                }
                Ok(LinkerNamespace { namespace })
            }
            None => bail_with_dlerror!("android_create_namespace failed"),
        }
    }
}

/// LoadedLibrary represents a library loaded to the memory space of the process.
pub struct LoadedLibrary {
    library_handle: *mut c_void,
}

impl LoadedLibrary {
    /// Load a library to the process memory space.
    ///
    /// # Safety
    ///
    /// Users must ensure that the initialization and termination routines of the library are safe.
    pub unsafe fn new(library_name: &str, namespace: &LinkerNamespace) -> Result<Self> {
        let dlextinfo = android_dlextinfo {
            flags: ANDROID_DLEXT_USE_NAMESPACE as u64,
            reserved_addr: std::ptr::null_mut(),
            reserved_size: 0,
            relro_fd: 0,
            library_fd: 0,
            library_fd_offset: 0,
            library_namespace: namespace.as_ptr(),
        };
        let library = CString::new(library_name).context("Invalid library name")?;

        // SAFETY: `library` and `dlextinfo` are valid pointers. The caller ensured that the
        // library is safe to be loaded.
        let library_handle = unsafe {
            android_dlopen_ext(
                library.as_ptr(),
                RTLD_LOCAL as i32,
                &dlextinfo as *const android_dlextinfo,
            )
        };
        if library_handle.is_null() {
            bail_with_dlerror!("Failed to open the library {}", library_name);
        }

        Ok(Self { library_handle })
    }

    pub fn find_symbol(&self, symbol_name: &str) -> Result<*mut c_void> {
        let symbol = CString::new(symbol_name).context("Invalid symbol name")?;
        // SAFETY: `self.library_handle` is a valid library handle and `symbol` is a valid C
        // string.
        let symbol_handle = unsafe { dlsym(self.library_handle, symbol.as_ptr()) };
        if symbol_handle.is_null() {
            bail_with_dlerror!("Failed to find the symbol {}", symbol_name);
        }
        Ok(symbol_handle)
    }
}

impl Drop for LoadedLibrary {
    fn drop(&mut self) {
        // SAFETY: the instance owns a valid handle to the opened library. The termination routine
        // is ensured to be safe.
        unsafe { dlclose(self.library_handle) };
    }
}
