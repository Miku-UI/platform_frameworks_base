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
use log::{error, info, warn, LevelFilter};
use native_activity_thread_bindgen::android_set_application_target_sdk_version;
use native_application_thread_aidl::aidl::android::app::INativeApplicationThread::BnNativeApplicationThread;
use nix::sys::signal::{pthread_sigmask, SigSet, SigmaskHow, Signal};
use rustutils::android::process::{android_mallopt, MalloptOpcode};

use utils::{apply_runtime_flags, setup_process_dumpability};

mod font;
mod library_loader;
mod native_activity_thread;
mod native_application_thread;
mod preload;
mod task;
mod utils;

pub use preload::preload_lib;
pub use utils::get_or_init_debuggable;

use crate::native_activity_thread::NativeActivityThread;
use crate::native_application_thread::NativeApplicationThread;
use crate::task::{run_thread_loop, Handler};

static ACTIVITY_MANAGER_SERVICE_NAME: &str = "activity_structured";

// Must be the same value as `SdkVersion::kUnset` in art/libartbase/base/sdk_version.h.
const SDK_VERSION_UNSET: i32 = 0;

/// Initialize a process for usage with an Android native application. Used when
/// Zygote forks and transition directly into an app process, or when starting
/// an App Zygote.
pub fn app_process_init(target_sdk_version: i32, runtime_flags: u32) {
    setup_process_dumpability().expect("Failed to set up process dumpability");

    // SAFETY: This opcode takes no arguments so a nullptr is passed
    //         instead.
    let ret = unsafe { android_mallopt(MalloptOpcode::SetZygoteChild, std::ptr::null_mut(), 0) };
    if ret.is_err() {
        log::error!("Call to android_mallopt failed: Opcode = M_SET_ZYGOTE_CHILD");
    }

    if let Err(e) = apply_runtime_flags(runtime_flags) {
        panic!("Failed to apply runtime flags: {e}");
    }

    let target = if target_sdk_version <= 0 { SDK_VERSION_UNSET } else { target_sdk_version };
    // SAFETY: target is a valid SDK version validated above.
    unsafe {
        android_set_application_target_sdk_version(target);
    }
}

/// Start NativeActivityThread to manage the process.
pub fn run_native_activity_thread(start_seq: i64) -> ! {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("native_activity_thread")
            .with_max_level(LevelFilter::Trace),
    );
    info!("Hello from the native activity thread! start_seq={start_seq}");

    run_signal_catcher_thread().unwrap();

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

fn run_signal_catcher_thread() -> Result<()> {
    let mut sigset = SigSet::empty();

    // SIGUSR1 is used to notify ANR, and SIGQUIT is used to actually terminate the process due to
    // the ANR. SIGPIPE is also blocked here to follow the design of Zygote.
    for signal in [Signal::SIGUSR1, Signal::SIGQUIT, Signal::SIGPIPE] {
        sigset.add(signal);
    }

    pthread_sigmask(SigmaskHow::SIG_BLOCK, Some(&sigset), None)
        .context("pthread_sigmask failed")?;

    std::thread::Builder::new()
        .name("signal_catcher".to_string())
        .spawn(move || loop {
            // At 50% of the ANR timeout, AMS triggers long method tracing using SIGUSR1.
            // Since we don't support this, we catch the signal and ignore it.
            // (SIGUSR1 is also used to invoke GC explicitly, which is not supported as well.)
            //
            // At 100% of the ANR timeout, AMS executes the following sequence in appNotResponding():
            // 1. debuggerd_trigger_dump(kDebuggerdJavaBacktrace): debuggerd sends SIGQUIT. We must ignore this to allow the native stack trace to proceed.
            // 2. debuggerd_trigger_dump(kDebuggerdNativeBacktrace): debuggerd sends BIONIC_SIGNAL_DEBUGGER which is handled transparently by the signal handler set up by bionic.
            // 3. AMS kills the app.
            match sigset.wait() {
                Ok(Signal::SIGUSR1) => {
                    info!("Received SIGUSR1");
                }
                Ok(Signal::SIGQUIT) => {
                    info!("Received SIGQUIT");
                }
                Ok(signum) => {
                    warn!("Signal catcher thread received unexpected signal: {signum:?}");
                }
                Err(e) => {
                    error!("Error waiting for signal: {e}");
                    break;
                }
            }
        })
        .context("Failed to spawn the signal_catcher thread")?;
    Ok(())
}
