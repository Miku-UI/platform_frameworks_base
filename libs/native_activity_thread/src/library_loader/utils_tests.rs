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

use std::collections::HashMap;

use crate::library_loader::utils::*;

#[test]
fn test_parse_config() {
    let config = r#"######

libA.so
#libB.so


      libC.so
libD.so
    #### libE.so"#;

    assert_eq!(
        parse_config(config, |_| Ok(true)).unwrap(),
        Vec::from(["libA.so".to_string(), "libC.so".to_string(), "libD.so".to_string(),])
    );
}

#[test]
fn test_parse_config_with_bitness() {
    let config = r#"libA.so 32
libB.so 64
libC.so"#;

    #[cfg(target_pointer_width = "32")]
    assert_eq!(
        parse_config(config, |_| Ok(true)).unwrap(),
        Vec::from(["libA.so".to_string(), "libC.so".to_string()])
    );
    #[cfg(target_pointer_width = "64")]
    assert_eq!(
        parse_config(config, |_| Ok(true)).unwrap(),
        Vec::from(["libB.so".to_string(), "libC.so".to_string()])
    );
}

#[test]
fn test_parse_config_with_nopreload() {
    let config = r#"libA.so nopreload
libB.so nopreload
libC.so"#;

    assert_eq!(
        parse_config(config, |entry| Ok(!entry.nopreload)).unwrap(),
        Vec::from(["libC.so".to_string()])
    );
}

#[test]
fn test_parse_config_with_nopreload_and_bitness() {
    let config = r#"libA.so nopreload 32
libB.so 64 nopreload
libC.so 32
libD.so 64
libE.so nopreload"#;

    #[cfg(target_pointer_width = "32")]
    assert_eq!(
        parse_config(config, |entry| Ok(!entry.nopreload)).unwrap(),
        Vec::from(["libC.so".to_string()])
    );
    #[cfg(target_pointer_width = "64")]
    assert_eq!(
        parse_config(config, |entry| Ok(!entry.nopreload)).unwrap(),
        Vec::from(["libD.so".to_string()])
    );
}

#[test]
fn test_parse_config_reject_malformed() {
    assert!(parse_config("libA.so 32 64", |_| Ok(true)).is_err());
    assert!(parse_config("libA.so 32 32", |_| Ok(true)).is_err());
    assert!(parse_config("libA.so 32 nopreload 64", |_| Ok(true)).is_err());
    assert!(parse_config("32 libA.so nopreload", |_| Ok(true)).is_err());
    assert!(parse_config("nopreload libA.so 32", |_| Ok(true)).is_err());
    assert!(parse_config("libA.so nopreload # comment", |_| Ok(true)).is_err());
}

#[test]
fn test_parse_apex_libraries_config() {
    let config = r#"public com_android_art libnativehelper.so
public com_android_i18n libicui18n.so:libicuuc.so
#public com_android_neuralnetworks libneuralnetworks.so

jni com_android_conscrypt libjavacrypto.so
jni com_android_os_statsd libstats_jni.so
vendor_public com_vendor_service3 libvendorpublic.so"#;

    assert_eq!(
        parse_apex_libraries_config(config, "public").unwrap(),
        HashMap::from([
            ("com_android_art".to_string(), "libnativehelper.so".to_string()),
            ("com_android_i18n".to_string(), "libicui18n.so:libicuuc.so".to_string()),
        ])
    );
    assert_eq!(
        parse_apex_libraries_config(config, "jni").unwrap(),
        HashMap::from([
            ("com_android_conscrypt".to_string(), "libjavacrypto.so".to_string()),
            ("com_android_os_statsd".to_string(), "libstats_jni.so".to_string()),
        ])
    );
    assert_eq!(
        parse_apex_libraries_config(config, "vendor_public").unwrap(),
        HashMap::from([("com_vendor_service3".to_string(), "libvendorpublic.so".to_string()),])
    );
}

#[test]
fn test_parse_apex_libraries_config_reject_malformed_line() {
    let config = r#"jni com_android_foo libfoo
# missing <library list>
jni com_android_bar"#;

    assert!(parse_apex_libraries_config(config, "jni").is_err());
}

#[test]
fn test_parse_apex_libraries_config_reject_invalid_tag() {
    let config = r#"jni apex1 lib
public apex2 lib
# unknown tag
unknown com_android_foo libfoo"#;

    assert!(parse_apex_libraries_config(config, "jni").is_err());
}

#[test]
fn test_parse_apex_libraries_config_reject_invalid_namespace() {
    let config = r#"
# apex linker namespace should be mangled ('.' -> '_')
jni com.android.foo lib"#;

    assert!(parse_apex_libraries_config(config, "jni").is_err());
}

#[test]
fn test_parse_apex_libraries_config_reject_invalid_library_list() {
    let config = r#"
# library list is ":" separated list of filenames
jni com_android_foo lib64/libfoo.so"#;

    assert!(parse_apex_libraries_config(config, "jni").is_err());
}

#[test]
fn test_filter_public_libraries() {
    let uses_libraries = vec!["libA.so", "libB.so", "libC.so"];
    let public_libraries = "libA.so:libC.so:libD.so";

    assert_eq!(filter_public_libraries(30, &uses_libraries, public_libraries), public_libraries);
    assert_eq!(filter_public_libraries(31, &uses_libraries, public_libraries), "libA.so:libC.so");
    assert_eq!(filter_public_libraries(31, &["ALL"], public_libraries), public_libraries);
}

fn get_product_api_domain(fallback_domain: ApiDomain) -> ApiDomain {
    // GetApiDomainFromPath returns API_DOMAIN_PRODUCT only if the device is
    // trebleized and has an unbundled product partition.
    if is_product_treblelized() {
        return ApiDomain::Product;
    }
    fallback_domain
}

#[test]
fn test_api_domain_try_from_path_list() {
    assert_eq!(ApiDomain::try_from_path_list("/data/somewhere").unwrap(), ApiDomain::Default);
    assert_eq!(ApiDomain::try_from_path_list("/system/somewhere").unwrap(), ApiDomain::Default);
    assert_eq!(ApiDomain::try_from_path_list("/system_ext/somewhere").unwrap(), ApiDomain::Default);
    assert_eq!(
        ApiDomain::try_from_path_list("/product/somewhere").unwrap(),
        get_product_api_domain(ApiDomain::Default)
    );
    assert_eq!(ApiDomain::try_from_path_list("/vendor/somewhere").unwrap(), ApiDomain::Vendor);
    assert_eq!(
        ApiDomain::try_from_path_list("/system/product/somewhere").unwrap(),
        get_product_api_domain(ApiDomain::Default)
    );
    assert_eq!(
        ApiDomain::try_from_path_list("/system/vendor/somewhere").unwrap(),
        ApiDomain::Vendor
    );

    assert_eq!(ApiDomain::try_from_path_list("").unwrap(), ApiDomain::Default);
    assert_eq!(ApiDomain::try_from_path_list(":").unwrap(), ApiDomain::Default);
    assert_eq!(ApiDomain::try_from_path_list(":/vendor/somewhere").unwrap(), ApiDomain::Vendor);
    assert_eq!(ApiDomain::try_from_path_list("/vendor/somewhere:").unwrap(), ApiDomain::Vendor);

    assert_eq!(
        ApiDomain::try_from_path_list("/data/somewhere:/product/somewhere").unwrap(),
        get_product_api_domain(ApiDomain::Default)
    );
    assert_eq!(
        ApiDomain::try_from_path_list("/system/somewhere:/product/somewhere").unwrap(),
        get_product_api_domain(ApiDomain::Default)
    );
    assert_eq!(
        ApiDomain::try_from_path_list("/product/somewhere:/system/somewhere").unwrap(),
        get_product_api_domain(ApiDomain::Default)
    );
    assert_eq!(
        ApiDomain::try_from_path_list("/data/somewhere:/vendor/somewhere").unwrap(),
        ApiDomain::Vendor
    );
    assert_eq!(
        ApiDomain::try_from_path_list("/system/somewhere:/vendor/somewhere").unwrap(),
        ApiDomain::Vendor
    );
    assert_eq!(
        ApiDomain::try_from_path_list("/vendor/somewhere:/system/somewhere").unwrap(),
        ApiDomain::Vendor
    );

    if get_product_api_domain(ApiDomain::Default) == ApiDomain::Product {
        assert!(ApiDomain::try_from_path_list("/vendor/somewhere:/product/somewhere").is_err());
        assert!(ApiDomain::try_from_path_list(
            "/system/somewhere:/product/somewhere:/vendor/somewhere"
        )
        .is_err());
    }
}

#[test]
fn test_extract_company_name_from_file_name() {
    assert_eq!(extract_company_name_from_file_name("public.libraries-abcde.txt").unwrap(), "abcde");
    assert!(extract_company_name_from_file_name("public.libraries.abcde.txt").is_err());
    assert!(extract_company_name_from_file_name("public.libraries-.txt").is_err());
}

#[test]
fn test_verify_so_name() {
    assert!(verify_so_name("libtest.abcde.so", "abcde").is_ok());
    assert!(verify_so_name("libtest-abcde.so", "abcde").is_err());
    assert!(verify_so_name("libabcde.so", "abcde").is_err());
}
