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

use log::debug;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::sync::LazyLock;

use crate::library_loader::utils::*;

const DEFAULT_PUBLIC_LIBRARIES_FILE: &str = "/etc/public.libraries.txt";
const APEX_LIBRARIES_CONFIG_FILE: &str = "/linkerconfig/apex.libraries.config.txt";
const VENDOR_PUBLIC_LIBRARIES_FILE: &str = "/vendor/etc/public.libraries.txt";
const LLNDK_LIBRARIES_FILE: &str = "/apex/com.android.vndk.v{}/etc/llndk.libraries.{}.txt";
const LLNDK_LIBRARIES_NO_VNDK_FILE: &str = "/system/etc/llndk.libraries.txt";
static VNDK_LIBRARIES_FILE: &str = "/apex/com.android.vndk.v{}/etc/vndksp.libraries.{}.txt";

pub static DEFAULT_PUBLIC_LIBRARIES: LazyLock<String> =
    LazyLock::new(|| init_default_public_libraries(false));
pub static EXTENDED_PUBLIC_LIBRARIES: LazyLock<String> =
    LazyLock::new(init_extended_public_libraries);
pub static VENDOR_PUBLIC_LIBRARIES: LazyLock<String> = LazyLock::new(init_vendor_public_libraries);
pub static PRODUCT_PUBLIC_LIBRARIES: LazyLock<String> =
    LazyLock::new(init_product_public_libraries);
pub static LLNDK_LIBRARIES_VENDOR: LazyLock<String> = LazyLock::new(init_llndk_libraries_vendor);
pub static LLNDK_LIBRARIES_PRODUCT: LazyLock<String> = LazyLock::new(init_llndk_libraries_product);
pub static VNDKSP_LIBRARIES_VENDOR: LazyLock<String> = LazyLock::new(init_vndksp_libraries_vendor);
pub static VNDKSP_LIBRARIES_PRODUCT: LazyLock<String> =
    LazyLock::new(init_vndksp_libraries_product);

pub static APEX_PUBLIC_LIBRARIES: LazyLock<HashMap<String, String>> =
    LazyLock::new(|| init_apex_libraries("public"));
pub static VENDOR_APEX_PUBLIC_LIBRARIES: LazyLock<HashMap<String, String>> =
    LazyLock::new(|| init_apex_libraries("vendor_public"));

fn init_default_public_libraries(for_preload: bool) -> String {
    let config_file = Path::new(&root_dir()).join(DEFAULT_PUBLIC_LIBRARIES_FILE);
    let mut sonames = read_config(config_file, |entry| Ok(!for_preload || !entry.nopreload))
        .unwrap_or(Vec::new());

    // If this is for preloading libs, don't remove the libs from APEXes.
    if !for_preload {
        // Remove the public libs provided by apexes because these libs are available
        // from apex namespaces.
        let apex_libs: HashSet<_> =
            APEX_PUBLIC_LIBRARIES.values().flat_map(|s| s.split(':')).collect();
        sonames.retain(|soname| !apex_libs.contains(soname.as_str()));
    }

    let libs = sonames.join(":");
    debug!("init_default_public_libraries (for_preload={for_preload}): {libs}");
    libs
}

fn init_vendor_public_libraries() -> String {
    let libs =
        read_config(VENDOR_PUBLIC_LIBRARIES_FILE, |_| Ok(true)).unwrap_or(Vec::new()).join(":");
    debug!("init_vendor_public_libraries: {libs}");
    libs
}

fn init_product_public_libraries() -> String {
    let libs = if is_product_treblelized() {
        read_extension_libraries("/product/etc").unwrap_or_default().join(":")
    } else {
        String::new()
    };
    debug!("init_product_public_libraries: {libs}");
    libs
}

fn init_extended_public_libraries() -> String {
    let mut sonames = read_extension_libraries("/system/etc").unwrap_or_default();
    sonames.extend(read_extension_libraries("/system_ext/etc").unwrap_or_default());
    if !is_product_treblelized() {
        sonames.extend(read_extension_libraries("/product/etc").unwrap_or_default());
    }
    let libs = sonames.join(":");
    debug!("init_extended_public_libraries: {libs}");
    libs
}

fn init_llndk_libraries_vendor() -> String {
    let config_file = if is_vendor_vndk_enabled() {
        let version = get_vndk_version(false).unwrap_or_default();
        LLNDK_LIBRARIES_FILE.replace("{}", &version)
    } else {
        LLNDK_LIBRARIES_NO_VNDK_FILE.to_string()
    };
    let libs = read_config(&config_file, |_| Ok(true)).unwrap_or_default().join(":");
    debug!("init_llndk_libraries_vendor: {libs}");
    libs
}

fn init_llndk_libraries_product() -> String {
    if !is_product_treblelized() {
        return String::new();
    }
    let config_file = if is_product_vndk_enabled() {
        let version = get_vndk_version(true).unwrap_or_default();
        LLNDK_LIBRARIES_FILE.replace("{}", &version)
    } else {
        LLNDK_LIBRARIES_NO_VNDK_FILE.to_string()
    };
    let libs = read_config(&config_file, |_| Ok(true)).unwrap_or_default().join(":");
    debug!("init_llndk_libraries_product: {libs}");
    libs
}

fn init_vndksp_libraries_vendor() -> String {
    if !is_vendor_vndk_enabled() {
        return String::new();
    }
    let version = get_vndk_version(false).unwrap_or_default();
    let config_file = VNDK_LIBRARIES_FILE.replace("{}", &version);
    let libs = read_config(&config_file, |_| Ok(true)).unwrap_or_default().join(":");
    debug!("init_vndksp_libraries_vendor: {libs}");
    libs
}

fn init_vndksp_libraries_product() -> String {
    if !is_product_vndk_enabled() {
        return String::new();
    }
    let version = get_vndk_version(true).unwrap_or_default();
    let config_file = VNDK_LIBRARIES_FILE.replace("{}", &version);
    let libs = read_config(&config_file, |_| Ok(true)).unwrap_or_default().join(":");
    debug!("init_vndksp_libraries_product: {libs}");
    libs
}

fn init_apex_libraries(tag: &str) -> HashMap<String, String> {
    let config_file = Path::new(APEX_LIBRARIES_CONFIG_FILE);
    if !config_file.exists() {
        return HashMap::new();
    }
    let map = if let Ok(content) = fs::read_to_string(config_file) {
        parse_apex_libraries_config(&content, tag).unwrap_or_default()
    } else {
        HashMap::new()
    };
    debug!("init_apex_libraries (tag: {tag}): {map:?}");
    map
}
