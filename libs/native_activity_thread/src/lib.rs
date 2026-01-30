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

//! The crate providing the functionality to manage the native application process.

use activitymanager_structured_aidl::aidl::android::app::IActivityManagerStructured::IActivityManagerStructured;
use anyhow::{Context, Result};
use binder::{BinderFeatures, ProcessState, Strong};
use log::{info, LevelFilter};
use native_application_thread_aidl::aidl::android::app::INativeApplicationThread::BnNativeApplicationThread;

mod library_loader;
mod native_activity_thread;
mod native_application_thread;
mod task;

use crate::native_activity_thread::NativeActivityThread;
use crate::native_application_thread::NativeApplicationThread;
use crate::task::{run_thread_loop, Handler};

static ACTIVITY_MANAGER_SERVICE_NAME: &str = "activity_structured";

/// Start NativeActivityThread to manage the process.
pub fn run_native_activity_thread(start_seq: i64) -> ! {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("native_activity_thread")
            .with_max_level(LevelFilter::Trace),
    );
    info!("Hello from the native activity thread! start_seq={start_seq}");

    // This must be done before creating any Binder client or server.
    ProcessState::start_thread_pool();

    let activity_manager = get_activity_manager_proxy().unwrap();

    // Prepare the handler of INativeApplicationThread requests from the ActivityManager
    let handler = Handler::new_on_current_thread(NativeActivityThread::new(
        activity_manager.clone(),
        start_seq,
    ))
    .unwrap();

    let sender = handler.get_sender().unwrap();
    let binder_node = BnNativeApplicationThread::new_binder(
        NativeApplicationThread::new(sender),
        BinderFeatures::default(),
    );

    // Notify the ActivityManager that this process is ready to be used for application.
    activity_manager.attachNativeApplication(&binder_node.as_binder(), start_seq).unwrap();

    // Start the main thread loop.
    run_thread_loop().unwrap();

    panic!("Shouldn't come here!");
}

fn get_activity_manager_proxy() -> Result<Strong<dyn IActivityManagerStructured>> {
    binder::check_interface(ACTIVITY_MANAGER_SERVICE_NAME).context("Failed to find ActivityManager")
}
