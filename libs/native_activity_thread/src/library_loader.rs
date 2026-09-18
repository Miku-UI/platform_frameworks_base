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
use libc::{dlclose, dlopen, dlsym, RTLD_GLOBAL, RTLD_LOCAL, RTLD_NOW};
use log::debug;
use native_activity_thread_bindgen::{
    android_create_namespace, android_dlextinfo, android_dlopen_ext,
    android_get_exported_namespace, android_link_namespaces, android_namespace_t,
    ANDROID_DLEXT_USE_NAMESPACE, ANDROID_NAMESPACE_TYPE_EXEMPT_LIST_ENABLED,
    ANDROID_NAMESPACE_TYPE_ISOLATED, ANDROID_NAMESPACE_TYPE_SHARED,
};
use std::{
    ffi::{c_void, CString},
    ptr::NonNull,
    sync::{Mutex, Once, OnceLock},
};

mod public_libraries;
mod utils;
#[cfg(test)]
mod utils_tests;

use crate::library_loader::public_libraries::*;
use crate::library_loader::utils::*;

fn last_dl_error() -> anyhow::Error {
    // SAFETY: trivially safe.
    let error = unsafe { libc::dlerror() };
    if !error.is_null() {
        // SAFETY: `error` is a pointer to a valid C string returned by `dlerror()`.
        let error_cstr = unsafe { std::ffi::CStr::from_ptr(error) };
        anyhow::anyhow!(error_cstr.to_string_lossy().into_owned())
    } else {
        anyhow::anyhow!("unknown dynamic linker error")
    }
}

const SHARED_NAMESPACE_SUFFIX: &str = "-shared";
const DEFAULT_NAMESPACE_NAME: &str = "default";
const SYSTEM_NAMESPACE_NAME: &str = "system";
const ALWAYS_PERMITTED_DIRECTORIES: &str = "/data:/mnt/expand";

/// Safe wrapper of a raw pointer to android_namespace_t.
#[derive(Clone)]
pub struct LinkerNamespace {
    namespace: NonNull<android_namespace_t>,
}

impl LinkerNamespace {
    pub fn create(
        name: String,
        search_paths: &str,
        permitted_libs_dir: &str,
        parent: Option<&LinkerNamespace>,
        flags: u64,
    ) -> Result<Self> {
        let cname = CString::new(name).context("Invalid name")?;
        let search_path = CString::new(search_paths).context("invalid search paths")?;
        let permitted_libs =
            CString::new(permitted_libs_dir).context("invalid permitted libs dir")?;
        let parent_raw = parent.map_or(std::ptr::null_mut(), |p| p.as_ptr());

        // SAFETY: `cname`, `search_path`, `permitted_libs_dir` are valid pointers and this
        // function accepts the null pointer for `ld_library_path` and `parent`. Passed pointers'
        // lifetime is long enough (strings are copied to the created namespace).
        let namespace = unsafe {
            android_create_namespace(
                cname.as_ptr(),
                /* ld_library_path= */ std::ptr::null_mut(),
                search_path.as_ptr(),
                flags,
                permitted_libs.as_ptr(),
                parent_raw,
            )
        };

        match NonNull::new(namespace) {
            Some(namespace) => Ok(LinkerNamespace { namespace }),
            None => Err(last_dl_error().context("android_create_namespace failed")),
        }
    }

    pub fn as_ptr(&self) -> *mut android_namespace_t {
        self.namespace.as_ptr()
    }

    pub fn link(&self, target: Option<&LinkerNamespace>, shared_libs: &str) -> Result<()> {
        let target_ptr = target.map_or(std::ptr::null_mut(), |ns| ns.as_ptr());
        let shared_libs_cstr = CString::new(shared_libs).context("Invalid shared_libs")?;
        // SAFETY: `android_link_namespaces` accepts the null pointer for `namespace_to`. We pass
        // a valid android_namespace_t pointer and a valid C string for other arguments.
        // Passed pointers' lifetime is long enough.
        if !unsafe { android_link_namespaces(self.as_ptr(), target_ptr, shared_libs_cstr.as_ptr()) }
        {
            return Err(last_dl_error().context("android_link_namespaces failed"));
        }
        Ok(())
    }

    pub fn get_exported_namespace(name: &str) -> Result<LinkerNamespace> {
        let cname = CString::new(name).context("Invalid name")?;

        // SAFETY: Passing a valid C string. Passed pointer's lifetime is long enough.
        let namespace = unsafe { android_get_exported_namespace(cname.as_ptr()) };
        match NonNull::new(namespace) {
            Some(namespace) => Ok(LinkerNamespace { namespace }),
            None => Err(last_dl_error().context("android_get_exported_namespace failed")),
        }
    }

    pub fn get_system_namespace() -> &'static Self {
        static NAMESPACE: OnceLock<LinkerNamespace> = OnceLock::new();
        NAMESPACE.get_or_init(|| {
            Self::get_exported_namespace(SYSTEM_NAMESPACE_NAME).unwrap_or_else(|_| {
                Self::get_exported_namespace(DEFAULT_NAMESPACE_NAME)
                    .expect("default namespace must exist")
            })
        })
    }

    fn link_public_libraries(
        &self,
        api_domain: ApiDomain,
        is_shared: bool,
        target_sdk_version: i32,
        uses_libraries: &[&str],
    ) -> Result<()> {
        let mut system_exposed_libraries = vec![DEFAULT_PUBLIC_LIBRARIES.clone()];
        let mut unbundled_app_domain = ApiDomain::Default;

        if !is_shared {
            unbundled_app_domain = api_domain;
            if let Some(llndk_path) = api_domain.llndk_library_path() {
                system_exposed_libraries.push(llndk_path.to_string());
            }
        }

        if unbundled_app_domain != ApiDomain::Vendor {
            let libs = filter_public_libraries(
                target_sdk_version,
                uses_libraries,
                &EXTENDED_PUBLIC_LIBRARIES,
            );
            if !libs.is_empty() {
                system_exposed_libraries.push(libs);
            }
        }

        self.link(
            Some(LinkerNamespace::get_system_namespace()),
            &system_exposed_libraries.join(":"),
        )
    }

    fn link_apex_public_libraries(&self) -> Result<()> {
        for (apex_ns_name, public_libs) in APEX_PUBLIC_LIBRARIES.iter() {
            if let Ok(ns) = LinkerNamespace::get_exported_namespace(apex_ns_name) {
                self.link(Some(&ns), public_libs)?;
            }
        }
        Ok(())
    }

    fn link_apex_vendor_public_libraries(
        &self,
        target_sdk_version: i32,
        uses_libraries: &[&str],
    ) -> Result<()> {
        // Unlike system public libraries, vendor public libraries should be filtered
        // against <uses-native-library>.
        for (apex_ns_name, public_libs) in VENDOR_APEX_PUBLIC_LIBRARIES.iter() {
            let filtered_public_libs =
                filter_public_libraries(target_sdk_version, uses_libraries, public_libs);
            if filtered_public_libs.is_empty() {
                continue;
            }
            if let Ok(ns) = LinkerNamespace::get_exported_namespace(apex_ns_name) {
                self.link(Some(&ns), &filtered_public_libs)?;
            }
        }
        Ok(())
    }

    fn link_vndksp_libraries(&self, unbundled_app_domain: ApiDomain) -> Result<()> {
        match unbundled_app_domain {
            ApiDomain::Vendor if !VNDKSP_LIBRARIES_VENDOR.is_empty() => {
                // Give access to VNDK-SP libraries from the 'vndk' namespace for unbundled vendor apps.
                if let Ok(vndk_ns) = LinkerNamespace::get_exported_namespace("vndk") {
                    self.link(Some(&vndk_ns), &VNDKSP_LIBRARIES_VENDOR)?;
                }
            }
            ApiDomain::Product if !VNDKSP_LIBRARIES_PRODUCT.is_empty() => {
                // Give access to VNDK-SP libraries from the 'vndk_product' namespace for unbundled product apps.
                if let Ok(vndk_ns) = LinkerNamespace::get_exported_namespace("vndk_product") {
                    self.link(Some(&vndk_ns), &VNDKSP_LIBRARIES_PRODUCT)?;
                }
            }
            _ => {}
        }
        Ok(())
    }

    fn link_vendor_public_libraries(
        &self,
        target_sdk_version: i32,
        uses_libraries: &[&str],
    ) -> Result<()> {
        let vendor_libs =
            filter_public_libraries(target_sdk_version, uses_libraries, &VENDOR_PUBLIC_LIBRARIES);
        if !vendor_libs.is_empty() {
            let target_ns = LinkerNamespace::get_exported_namespace("sphal");
            let target_ns_ref =
                target_ns.as_ref().unwrap_or(LinkerNamespace::get_system_namespace());
            self.link(Some(target_ns_ref), &vendor_libs)?;
        }
        Ok(())
    }

    fn link_product_public_libraries(
        &self,
        target_sdk_version: i32,
        uses_libraries: &[&str],
    ) -> Result<()> {
        let product_libs =
            filter_public_libraries(target_sdk_version, uses_libraries, &PRODUCT_PUBLIC_LIBRARIES);
        if !product_libs.is_empty() {
            let target_ns = if is_product_treblelized() {
                LinkerNamespace::get_exported_namespace("product").ok()
            } else {
                None
            };
            let target_ns_ref =
                target_ns.as_ref().unwrap_or(LinkerNamespace::get_system_namespace());
            self.link(Some(target_ns_ref), &product_libs)?;
        }
        Ok(())
    }
}

// SAFETY: dlopen(3) is thread-safe (https://man7.org/linux/man-pages/man3/dlopen.3.html)
unsafe impl Send for LinkerNamespace {}
// SAFETY: dlopen(3) is thread-safe (https://man7.org/linux/man-pages/man3/dlopen.3.html)
unsafe impl Sync for LinkerNamespace {}

/// NamespaceFactory creates linker namespaces.
pub struct NamespaceFactory {
    // Used to assign a serial number to each namespace name to make it unique.
    serial: u32,
}

static NAMESPACE_FACTORY: OnceLock<Mutex<NamespaceFactory>> = OnceLock::new();

impl NamespaceFactory {
    fn new() -> Mutex<Self> {
        Mutex::new(Self { serial: 0 })
    }

    /// Ensure the denylist is loaded, which overrides unsupported NDK APIs with abort().
    /// It does so by RTLD_GLOBAL, DF_1_GLOBAL and ELF symbol interposition.
    fn load_denylist_once() {
        static LOAD_DENYLIST_ONCE: Once = Once::new();
        LOAD_DENYLIST_ONCE.call_once(|| {
            // SAFETY: `libandroid_native_denylist.so` is a system library, and
            //         remains loaded for the lifetime of the process.
            let library_handle = unsafe {
                dlopen(c"libandroid_native_denylist.so".as_ptr(), RTLD_GLOBAL | RTLD_NOW)
            };
            if library_handle.is_null() {
                panic!("Failed to preload the denylist library: {}", last_dl_error());
            }
        });
    }

    /// Create a linker namespace. Basically this is equivalent to `LibraryNamespaces::Create` in
    /// art/libnativeloader.
    pub fn create_linker_namespace(
        target_sdk_version: i32,
        is_shared: bool,
        dex_paths: &str,
        library_paths: &str,
        permitted_libs_dir: &str,
        uses_library_list: &str,
    ) -> Result<LinkerNamespace> {
        Self::load_denylist_once();

        let api_domain = ApiDomain::try_from_path_list(dex_paths)?;
        let mut final_library_path = vec![library_paths.to_string()];
        let mut permitted_path = vec![ALWAYS_PERMITTED_DIRECTORIES.to_string()];
        if !permitted_libs_dir.is_empty() {
            permitted_path.push(permitted_libs_dir.to_string());
        }
        let mut namespace_name_prefix = ApiDomain::Default.namespace_name_prefix();
        let mut unbundled_app_domain = ApiDomain::Default;
        let mut api_domain_msg = ApiDomain::Default.message().to_string();

        if !is_shared {
            unbundled_app_domain = api_domain;
            api_domain_msg = format!("unbundled {}", api_domain.message());
            if let Some(library_path) = api_domain.library_path() {
                final_library_path.push(library_path.to_string());
                permitted_path.push(library_path.to_string());
            }
            namespace_name_prefix = api_domain.namespace_name_prefix();
        }

        let mut namespace_name = namespace_name_prefix.to_string();
        if is_shared {
            namespace_name.push_str(SHARED_NAMESPACE_SUFFIX);
        }

        let mut factory = NAMESPACE_FACTORY.get_or_init(Self::new).lock().unwrap();
        namespace_name = format!("{}-{}", namespace_name, factory.serial);

        debug!(
            "Configuring namespace '{}' for {}. target_sdk={}, api_domain: {:?}, is_shared: {}, uses_libraries='{}', library_path='{:?}', permitted_path='{:?}'",
            namespace_name, api_domain_msg, target_sdk_version, api_domain, is_shared, uses_library_list, final_library_path, permitted_path
        );
        let uses_libraries: Vec<&str> = uses_library_list.split(':').collect();

        let mut ns_flags = ANDROID_NAMESPACE_TYPE_ISOLATED as u64;
        if is_shared {
            ns_flags |= ANDROID_NAMESPACE_TYPE_SHARED as u64;
        }
        if target_sdk_version < 24 {
            ns_flags |= ANDROID_NAMESPACE_TYPE_EXEMPT_LIST_ENABLED as u64;
        }

        let app_ns = LinkerNamespace::create(
            namespace_name,
            &final_library_path.join(":"),
            &permitted_path.join(":"),
            None,
            ns_flags,
        )?;

        app_ns
            .link_public_libraries(api_domain, is_shared, target_sdk_version, &uses_libraries)
            .context("Failed to link public libraries")?;

        app_ns.link_apex_public_libraries().context("Failed to link apex public libraries")?;

        app_ns
            .link_apex_vendor_public_libraries(target_sdk_version, &uses_libraries)
            .context("Failed to link apex vendor public libraries")?;

        app_ns
            .link_vndksp_libraries(unbundled_app_domain)
            .context("Failed to link vndksp libraries")?;

        app_ns
            .link_vendor_public_libraries(target_sdk_version, &uses_libraries)
            .context("Failed to link vendor public libraries")?;

        app_ns
            .link_product_public_libraries(target_sdk_version, &uses_libraries)
            .context("Failed to link product public libraries")?;

        if let Some(new_serial) = factory.serial.checked_add(1) {
            factory.serial = new_serial;
        } else {
            bail!("too many namespaces were created");
        }

        Ok(app_ns)
    }
}

/// LoadedLibrary represents a library loaded to the memory space of the process.
pub struct LoadedLibrary {
    library_handle: *mut c_void,
}

// SAFETY: dlopen(3) is thread-safe (https://man7.org/linux/man-pages/man3/dlopen.3.html)
unsafe impl Send for LoadedLibrary {}
// SAFETY: dlopen(3) is thread-safe (https://man7.org/linux/man-pages/man3/dlopen.3.html)
unsafe impl Sync for LoadedLibrary {}

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
            android_dlopen_ext(library.as_ptr(), RTLD_LOCAL, &dlextinfo as *const android_dlextinfo)
        };
        if library_handle.is_null() {
            return Err(
                last_dl_error().context(format!("Failed to open the library {}", library_name))
            );
        }

        Ok(Self { library_handle })
    }

    pub fn find_symbol(&self, symbol_name: &str) -> Result<*mut c_void> {
        let symbol = CString::new(symbol_name).context("Invalid symbol name")?;
        // SAFETY: `self.library_handle` is a valid library handle and `symbol` is a valid C
        // string.
        let symbol_handle = unsafe { dlsym(self.library_handle, symbol.as_ptr()) };
        if symbol_handle.is_null() {
            return Err(
                last_dl_error().context(format!("Failed to find the symbol {}", symbol_name))
            );
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
