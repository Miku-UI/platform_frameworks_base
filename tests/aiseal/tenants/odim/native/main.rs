// Copyright 2025, The Android Open Source Project
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

//! On-device Intelligence Manager payload for AiSeal test.

use odim_aidl_interface::aidl::android::aiseal::tests::odim::IAiSealOdimPayloadService::{
    BnAiSealOdimPayloadService, IAiSealOdimPayloadService, PORT,
};

use anyhow::Result;
use binder::{BinderFeatures, Interface, Result as BinderResult, Strong};
use log::{error, info};
use std::process::exit;

const LOG_TAG: &str = "AiSealOdimTestPayload";

vm_payload::main!(main);

fn main() {
    android_logger::init_once(
        android_logger::Config::default().with_tag(LOG_TAG).with_max_level(log::LevelFilter::Info),
    );
    if let Err(e) = try_main() {
        error!("failed with {:?}", e);
        exit(1);
    }
}

fn try_main() -> Result<()> {
    info!("Starting ODIM test payload");

    vm_payload::run_single_vsock_service(AiSealOdimPayloadService::new_binder(), PORT.try_into()?)
}

struct AiSealOdimPayloadService {}

impl Interface for AiSealOdimPayloadService {}

impl AiSealOdimPayloadService {
    fn new_binder() -> Strong<dyn IAiSealOdimPayloadService> {
        BnAiSealOdimPayloadService::new_binder(
            AiSealOdimPayloadService {},
            BinderFeatures::default(),
        )
    }
}

impl IAiSealOdimPayloadService for AiSealOdimPayloadService {
    fn joinStringsWithSpace(&self, a: &str, b: &str) -> BinderResult<String> {
        Ok(format!("{} {}", a, b))
    }

    fn exit(&self, status: i32) -> BinderResult<()> {
        std::process::exit(status);
    }
}
