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

use crate::library_loader::{LinkerNamespace, LoadedLibrary, NamespaceFactory};
use std::sync::OnceLock;

struct PreloadedLib {
    namespace: LinkerNamespace,
    _library: LoadedLibrary,
    _library_name: String,
    library_paths: String,
    allowed_lib_dirs: String,
}

static PRELOAD_INFO: OnceLock<PreloadedLib> = OnceLock::new();

/// Preload the specified lib.
///
/// # Safety
///
/// The caller must ensure that initialization routine of the library is safe.
#[allow(clippy::too_many_arguments)]
pub unsafe fn preload_lib(
    library_name: &str,
    library_paths: &str,
    allowed_lib_dirs: &str,
    target_sdk_version: i32,
    is_shared: bool,
    zip_paths: &str,
    native_shared_lib_path: &str,
    preload_func: Option<&str>,
) {
    let namespace = NamespaceFactory::create_linker_namespace(
        target_sdk_version,
        is_shared,
        zip_paths,
        library_paths,
        allowed_lib_dirs,
        native_shared_lib_path,
    )
    .expect("failed to create namespace");

    let library =
    // SAFETY: We are assuming the caller has specified valid library name and namespace parameters.
        unsafe { LoadedLibrary::new(library_name, &namespace) }.expect("failed to load library");

    if let Some(preload_func) = preload_func {
        let preload_fun_handle =
            library.find_symbol(preload_func).expect("failed to find preload func");

        // SAFETY: The caller guarantees that the symbol pointed to by `preload_func`
        // has the correct signature (takes no arguments, returns nothing).
        // Null-check of the handle is performed in `find_symbol`.
        let preload_fn: extern "C" fn() = unsafe { std::mem::transmute(preload_fun_handle) };
        preload_fn();
    }

    let _ = PRELOAD_INFO.set(PreloadedLib {
        namespace,
        _library: library,
        _library_name: library_name.to_owned(),
        library_paths: library_paths.to_owned(),
        allowed_lib_dirs: allowed_lib_dirs.to_owned(),
    });
}

/// Return the previously registered LinkerNamespace if it exists.
pub fn reuse_namespace(library_paths: &str, allowed_lib_dirs: &str) -> Option<LinkerNamespace> {
    PRELOAD_INFO
        .get()
        .filter(|lib| {
            lib.library_paths == library_paths && lib.allowed_lib_dirs == allowed_lib_dirs
        })
        .map(|lib| lib.namespace.clone())
}
