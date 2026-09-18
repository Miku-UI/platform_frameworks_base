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

use anyhow::{anyhow, bail, Context, Result};
//use regex::Regex;
use rustutils::android::system_properties;
use std::collections::HashMap;
use std::collections::HashSet;
use std::env;
use std::fs;
use std::path::Path;
//use std::sync::LazyLock;
use std::sync::OnceLock;

use crate::library_loader::public_libraries::LLNDK_LIBRARIES_PRODUCT;
use crate::library_loader::public_libraries::LLNDK_LIBRARIES_VENDOR;

const SDK_VERSION_PUBLIC_LIBRARY_FILTERING_ENABLED: i32 = 31;

#[cfg(target_pointer_width = "32")]
const VENDOR_LIB_PATH: &str = "/vendor/lib";
#[cfg(target_pointer_width = "64")]
const VENDOR_LIB_PATH: &str = "/vendor/lib64";

#[cfg(target_pointer_width = "32")]
const PRODUCT_LIB_PATH: &str = "/product/lib:/system/product/lib";
#[cfg(target_pointer_width = "64")]
const PRODUCT_LIB_PATH: &str = "/product/lib64:/system/product/lib64";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ApiDomain {
    Default,
    Vendor,
    Product,
    System,
}

impl ApiDomain {
    pub fn from_path(path: &str) -> ApiDomain {
        if is_vendor_path(path) {
            ApiDomain::Vendor
        } else if is_product_treblelized() && is_product_path(path) {
            ApiDomain::Product
        } else if is_system_path(path) {
            ApiDomain::System
        } else {
            ApiDomain::Default
        }
    }

    pub fn try_from_path_list(path_list: &str) -> Result<ApiDomain> {
        let mut result = ApiDomain::Default;
        for path in path_list.split(':') {
            let domain = Self::from_path(path);
            if matches!(domain, ApiDomain::Vendor | ApiDomain::Product) {
                if matches!(result, ApiDomain::Vendor | ApiDomain::Product) && result != domain {
                    bail!("Path list crosses vendor/product boundaries: {}", path_list);
                }
                result = domain;
            }
        }
        Ok(result)
    }

    pub fn message(&self) -> &'static str {
        match self {
            ApiDomain::Vendor => "vendor apk",
            ApiDomain::Product => "product apk",
            ApiDomain::System => "system apk",
            ApiDomain::Default => "other apk",
        }
    }

    pub fn namespace_name_prefix(&self) -> &'static str {
        match self {
            ApiDomain::Vendor => "vendor-ns-native",
            ApiDomain::Product => "product-ns-native",
            ApiDomain::System => "system-ns-native",
            ApiDomain::Default => "ns-native",
        }
    }

    pub fn library_path(&self) -> Option<&'static str> {
        match self {
            ApiDomain::Vendor => Some(VENDOR_LIB_PATH),
            ApiDomain::Product => Some(PRODUCT_LIB_PATH),
            _ => None,
        }
    }

    pub fn llndk_library_path(&self) -> Option<&'static str> {
        match self {
            ApiDomain::Vendor => Some(&*LLNDK_LIBRARIES_VENDOR),
            ApiDomain::Product => Some(&*LLNDK_LIBRARIES_PRODUCT),
            _ => None,
        }
    }
}

/// Represents the bitness of a library.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Bitness {
    #[default]
    All,
    Only32,
    Only64,
}

/// Represents a single entry in a `public.libraries.txt` file.
#[derive(Debug, Default)]
pub struct ConfigEntry {
    pub soname: String,
    pub nopreload: bool,
    pub bitness: Bitness,
}

/// Represents a single line in an `apex.libraries.config.txt` file.
#[derive(Debug, PartialEq)]
pub struct ApexLibrariesConfigLine {
    pub tag: String,
    pub apex_namespace: String,
    pub library_list: String,
}

fn is_vendor_path(path: &str) -> bool {
    path.starts_with("/vendor/") || path.starts_with("/system/vendor/")
}

fn is_product_path(path: &str) -> bool {
    path.starts_with("/product/") || path.starts_with("/system/product/")
}

fn is_system_path(path: &str) -> bool {
    path.starts_with("/system/") || path.starts_with("/system_ext/")
}

fn is_valid_apex_namespace(s: &str) -> bool {
    !s.is_empty() && s.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}

fn is_valid_library_list(s: &str) -> bool {
    !s.is_empty()
        && s.chars().all(|c| {
            c.is_ascii_alphanumeric()
                || c == '.'
                || c == ':'
                || c == '@'
                || c == '+'
                || c == '_'
                || c == '-'
        })
}

fn parse_line(line: &str) -> Result<ConfigEntry> {
    let tokens: Vec<&str> = line.split_whitespace().collect();
    if tokens.is_empty() || tokens.len() > 3 {
        bail!("Malformed line");
    }

    let mut entry: ConfigEntry = Default::default();

    // Iterate tokens in reverse to correctly parse the line format.
    for (i, &token) in tokens.iter().enumerate().rev() {
        match token {
            "nopreload" => entry.nopreload = true,
            "32" | "64" => {
                if entry.bitness != Bitness::All {
                    bail!("Bitness can be specified only once in line");
                }
                entry.bitness = if token == "32" { Bitness::Only32 } else { Bitness::Only64 };
            }
            _ => {
                if i != 0 {
                    // The soname must be the first token.
                    bail!("Library name must be at the beginning of the line");
                }
                entry.soname = token.to_string();
            }
        }
    }
    Ok(entry)
}

/// Parses the content of a `public.libraries.txt` file.
pub fn parse_config<F>(file_content: &str, mut filter_fn: F) -> Result<Vec<String>>
where
    for<'a> F: FnMut(&'a ConfigEntry) -> Result<bool>,
{
    let mut sonames = Vec::new();
    for line in file_content.lines() {
        let trimmed_line = line.trim();
        if trimmed_line.starts_with('#') || trimmed_line.is_empty() {
            continue;
        }

        let entry =
            parse_line(trimmed_line).with_context(|| "Failed to parse line {trimmed_line}")?;

        // Skip libraries based on target architecture bitness.
        #[cfg(target_pointer_width = "64")]
        if entry.bitness == Bitness::Only32 {
            continue;
        }
        #[cfg(target_pointer_width = "32")]
        if entry.bitness == Bitness::Only64 {
            continue;
        }

        // TODO(b/206676167): Remove this check when renderscript is officially removed.
        #[cfg(target_arch = "riscv64")]
        if entry.soname == "libRS.so" {
            continue;
        }

        if filter_fn(&entry)? {
            sonames.push(entry.soname.clone());
        }
    }
    Ok(sonames)
}

/// Reads and parses a `public.libraries.txt` configuration file.
pub fn read_config<P: AsRef<Path>, F>(config_file: P, filter_fn: F) -> Result<Vec<String>>
where
    F: FnMut(&ConfigEntry) -> Result<bool>,
{
    let path = config_file.as_ref();
    if !path.exists() {
        bail!("{} not found", path.display());
    }

    let file_content =
        fs::read_to_string(path).map_err(|e| anyhow!("Failed to read {:?}: {}", path, e))?;

    parse_config(&file_content, filter_fn).map_err(|e| anyhow!("Cannot parse {:?}: {}", path, e))
}

pub fn extract_company_name_from_file_name(filename: &str) -> Result<&str> {
    let company_name = filename
        .strip_prefix("public.libraries-")
        .and_then(|s| s.strip_suffix(".txt"))
        .with_context(|| format!("Malformed file name: \"{filename}\""))?;
    if company_name.is_empty() {
        bail!("Error extracting company name from \"{filename}\"");
    }
    Ok(company_name)
}

pub fn verify_so_name(soname: &str, company_name: &str) -> Result<()> {
    let _name = soname
        .strip_prefix("lib")
        .and_then(|s| s.strip_suffix(".so"))
        .and_then(|s| s.strip_suffix(company_name))
        .and_then(|s| s.strip_suffix("."))
        .with_context(||
            format!("Library name \"{}\" must start with \"lib\" and end with the company name \".{}.so\"",
            soname,
            company_name))?;
    Ok(())
}

/// Reads all extension library configuration files from a given directory.
pub fn read_extension_libraries<P: AsRef<Path>>(dirname: P) -> Result<Vec<String>> {
    let dir_path = dirname.as_ref();
    let mut sonames = Vec::new();
    let Ok(dir) = fs::read_dir(dir_path) else {
        // In the original code, failing to open the directory is not an error.
        return Ok(sonames);
    };

    for entry in dir.filter_map(Result::ok) {
        let file_type = match entry.file_type() {
            Ok(ft) => ft,
            Err(_) => continue,
        };

        if !file_type.is_file() && !file_type.is_symlink() {
            continue;
        }

        let filename = entry.file_name().to_string_lossy().into_owned();
        if let Ok(company_name) = extract_company_name_from_file_name(&filename) {
            let config_file_path = entry.path();
            let filter = |entry: &ConfigEntry| -> Result<bool> {
                verify_so_name(&entry.soname, company_name).map(|_| true)
            };

            let mut new_sonames = read_config(&config_file_path, filter).map_err(|e| {
                anyhow!("Error reading extension library list {:?}: {}", config_file_path, e)
            })?;
            sonames.append(&mut new_sonames);
        }
    }
    Ok(sonames)
}

/// Filter colon-separated public library list `public` based on the allowlist `uses`. Skips
/// filtering if the target SDK version <= 30 because this check was introduced in 31.
pub fn filter_public_libraries(target_sdk: i32, uses: &[&str], public: &str) -> String {
    if target_sdk < SDK_VERSION_PUBLIC_LIBRARY_FILTERING_ENABLED || uses.contains(&"ALL") {
        return public.to_string();
    }
    let public_set: HashSet<_> = public.split(':').collect();
    uses.iter().copied().filter(|lib| public_set.contains(lib)).collect::<Vec<&str>>().join(":")
}

fn parse_apex_libraries_config_line(line: &str) -> Result<ApexLibrariesConfigLine> {
    let tokens: Vec<&str> = line.split_whitespace().collect();
    if tokens.len() != 3 {
        bail!("Malformed line: {line}");
    }

    let tag = tokens[0];
    let apex_namespace = tokens[1];
    let library_list = tokens[2];

    if tag != "jni" && tag != "public" && tag != "vendor_public" {
        bail!("Malformed tag {tag} in line: {line}");
    }

    if !is_valid_apex_namespace(apex_namespace) {
        bail!("Malformed namespace {apex_namespace} in line: {line}");
    }

    if !is_valid_library_list(library_list) {
        bail!("Malformed library list {library_list} in line: {line}");
    }

    Ok(ApexLibrariesConfigLine {
        tag: tag.to_string(),
        apex_namespace: apex_namespace.to_string(),
        library_list: library_list.to_string(),
    })
}

/// Parses the content of an `apex.libraries.config.txt` file.
pub fn parse_apex_libraries_config(
    file_content: &str,
    tag: &str,
) -> Result<HashMap<String, String>> {
    let mut entries = HashMap::new();
    for line in file_content.lines() {
        let trimmed_line = line.trim();
        if trimmed_line.starts_with('#') || trimmed_line.is_empty() {
            continue;
        }
        let config_line = parse_apex_libraries_config_line(trimmed_line)
            .with_context(|| format!("Malformed line: {line}"))?;
        if config_line.tag == tag {
            entries.insert(config_line.apex_namespace, config_line.library_list);
        }
    }
    Ok(entries)
}

pub fn root_dir() -> String {
    env::var("ANDROID_ROOT").unwrap_or_else(|_| "/system".to_string())
}

pub fn get_vndk_version(is_product_vndk: bool) -> Option<String> {
    let key = if is_product_vndk { "ro.product.vndk.version" } else { "ro.vndk.version" };
    system_properties::read(key).unwrap_or_default()
}

pub fn is_vendor_vndk_enabled() -> bool {
    get_vndk_version(false).is_some_and(|version| !version.is_empty())
}

pub fn is_product_vndk_enabled() -> bool {
    get_vndk_version(true).is_some_and(|version| !version.is_empty())
}

pub fn is_product_treblelized() -> bool {
    static RESULT: OnceLock<bool> = OnceLock::new();
    *RESULT.get_or_init(|| {
        if let Ok(Some(version_str)) = system_properties::read("ro.product.first_api_level") {
            let version = version_str.parse::<i32>().unwrap_or(0);
            if version >= 30 {
                return true;
            }
        }
        is_product_vndk_enabled()
    })
}
