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

use binder::{Interface, ParcelFileDescriptor, SpIBinder, ThreadState};
use log::info;
use native_application_thread_aidl::aidl::android::app::INativeApplicationThread::INativeApplicationThread;
use std::{marker::PhantomData, os::fd::OwnedFd, thread};

use crate::task::Sender;
use crate::utils::reset_time_zone;

const ROOT_UID: u32 = 0;
const SYSTEM_UID: u32 = 1000;

fn check_calling_uid() -> binder::Result<()> {
    let calling_uid = ThreadState::get_calling_uid();
    if calling_uid != ROOT_UID && calling_uid != SYSTEM_UID {
        return Err(binder::Status::new_exception_str(
            binder::ExceptionCode::SECURITY,
            Some(format!(
                "NativeApplicationThread called by non-system process (callingUid: {})",
                calling_uid
            )),
        ));
    }
    Ok(())
}

pub struct CreateServiceRequest {
    pub service_token: SpIBinder,
    pub zip_paths: String,
    pub library_paths: String,
    pub permitted_libs_dir: String,
    pub target_sdk_version: i32,
    pub is_shared: bool,
    pub native_shared_lib_path: String,
    pub library_name: String,
    pub base_symbol_name: String,
    pub _process_state: i32,
    // Have a private field to ensure instances are not created outside the module.
    _marker: PhantomData<()>,
}

impl CreateServiceRequest {
    /// # Safety
    ///
    /// Users must ensure that `library_name` specifies a safe dynamic library and it has a
    /// function named `base_symbol_name` with the type signature `ANativeService_createFunc`.
    #[allow(clippy::too_many_arguments)]
    unsafe fn new(
        service_token: SpIBinder,
        zip_paths: String,
        library_paths: String,
        permitted_libs_dir: String,
        target_sdk_version: i32,
        is_shared: bool,
        native_shared_lib_path: String,
        library_name: String,
        base_symbol_name: String,
        process_state: i32,
    ) -> Self {
        Self {
            service_token,
            zip_paths,
            library_paths,
            permitted_libs_dir,
            target_sdk_version,
            is_shared,
            native_shared_lib_path,
            library_name,
            base_symbol_name,
            _process_state: process_state,
            _marker: PhantomData,
        }
    }
}

pub struct DestroyServiceRequest {
    pub service_token: SpIBinder,
}

pub struct BindServiceRequest {
    pub service_token: SpIBinder,
    pub bind_token: SpIBinder,
    pub action: Option<String>,
    pub data: Option<String>,
    pub rebind: bool,
    pub _process_state: i32,
    pub _bind_seq: i64,
}

pub struct UnbindServiceRequest {
    pub service_token: SpIBinder,
    pub bind_token: SpIBinder,
}

pub struct BindApplicationRequest {
    pub system_font_map_fd: Option<OwnedFd>,
}

pub enum NativeApplicationThreadRequest {
    CreateService(CreateServiceRequest),
    DestroyService(DestroyServiceRequest),
    BindService(BindServiceRequest),
    UnbindService(UnbindServiceRequest),
    TrimMemory(i32),
    BindApplication(BindApplicationRequest),
    SetProcessState(i32),
}

/// NativeApplicationThread is used as a "Binder node" to accept requests for managing the process
/// for application use.
pub struct NativeApplicationThread {
    sender: Sender<NativeApplicationThreadRequest>,
}

impl NativeApplicationThread {
    pub(crate) fn new(sender: Sender<NativeApplicationThreadRequest>) -> NativeApplicationThread {
        Self { sender }
    }
}

impl Interface for NativeApplicationThread {}

impl INativeApplicationThread for NativeApplicationThread {
    fn scheduleCreateService(
        &self,
        service_token: &SpIBinder,
        zip_paths: &str,
        library_paths: &str,
        permitted_libs_dir: &str,
        target_sdk_version: i32,
        is_shared: bool,
        native_shared_lib_path: &str,
        library_name: &str,
        base_symbol_name: &str,
        _process_state: i32,
    ) -> binder::Result<()> {
        info!("scheduleCreateService thread id={:?}", thread::current().id());
        check_calling_uid()?;

        // SAFETY: We trust that the caller of this function requests to load a library specified
        // by the application according to the native service specification. The application is
        // responsible for implementing a safe library and an entry point function of its native
        // service with the type signature `ANativeService_createFunc`.
        let req = unsafe {
            CreateServiceRequest::new(
                service_token.clone(),
                zip_paths.to_string(),
                library_paths.to_string(),
                permitted_libs_dir.to_string(),
                target_sdk_version,
                is_shared,
                native_shared_lib_path.to_string(),
                library_name.to_string(),
                base_symbol_name.to_string(),
                _process_state,
            )
        };
        self.sender.send(NativeApplicationThreadRequest::CreateService(req)).map_err(|e| {
            binder::Status::new_exception_str(
                binder::ExceptionCode::SERVICE_SPECIFIC,
                Some(format!("Failed to send a task: {:?}", e)),
            )
        })?;
        Ok(())
    }

    fn scheduleDestroyService(&self, service_token: &SpIBinder) -> binder::Result<()> {
        info!("scheduleDestroyService thread id={:?}", thread::current().id());
        check_calling_uid()?;

        self.sender
            .send(NativeApplicationThreadRequest::DestroyService(DestroyServiceRequest {
                service_token: service_token.clone(),
            }))
            .map_err(|e| {
                binder::Status::new_exception_str(
                    binder::ExceptionCode::SERVICE_SPECIFIC,
                    Some(format!("Failed to send a task: {:?}", e)),
                )
            })?;
        Ok(())
    }

    fn scheduleBindService(
        &self,
        service_token: &SpIBinder,
        bind_token: &SpIBinder,
        action: Option<&str>,
        data: Option<&str>,
        rebind: bool,
        process_state: i32,
        bind_seq: i64,
    ) -> binder::Result<()> {
        info!("scheduleBindService thread id={:?}", thread::current().id());
        if let Some(s) = action {
            info!("scheduleBindService action={}", s);
        }
        check_calling_uid()?;

        self.sender
            .send(NativeApplicationThreadRequest::BindService(BindServiceRequest {
                service_token: service_token.clone(),
                bind_token: bind_token.clone(),
                action: action.map(|s| s.to_string()),
                data: data.map(|s| s.to_string()),
                rebind,
                _process_state: process_state,
                _bind_seq: bind_seq,
            }))
            .map_err(|e| {
                binder::Status::new_exception_str(
                    binder::ExceptionCode::SERVICE_SPECIFIC,
                    Some(format!("Failed to send a task: {:?}", e)),
                )
            })?;
        Ok(())
    }

    fn scheduleUnbindService(
        &self,
        service_token: &SpIBinder,
        bind_token: &SpIBinder,
    ) -> binder::Result<()> {
        info!("scheduleUnbindService thread id={:?}", thread::current().id());
        check_calling_uid()?;

        self.sender
            .send(NativeApplicationThreadRequest::UnbindService(UnbindServiceRequest {
                service_token: service_token.clone(),
                bind_token: bind_token.clone(),
            }))
            .map_err(|e| {
                binder::Status::new_exception_str(
                    binder::ExceptionCode::SERVICE_SPECIFIC,
                    Some(format!("Failed to send a task: {:?}", e)),
                )
            })?;
        Ok(())
    }

    fn scheduleTrimMemory(&self, level: i32) -> binder::Result<()> {
        info!("scheduleTrimMemory thread id={:?}", thread::current().id());
        check_calling_uid()?;

        self.sender.send(NativeApplicationThreadRequest::TrimMemory(level)).map_err(|e| {
            binder::Status::new_exception_str(
                binder::ExceptionCode::SERVICE_SPECIFIC,
                Some(format!("Failed to send a task: {:?}", e)),
            )
        })?;
        Ok(())
    }

    fn bindApplication(
        &self,
        system_font_map_fd: Option<&ParcelFileDescriptor>,
    ) -> binder::Result<()> {
        info!("bindApplication thread id={:?}", thread::current().id());
        check_calling_uid()?;

        let system_font_map_fd = system_font_map_fd
            .map(|fd| {
                fd.as_ref().try_clone().map_err(|e| {
                    binder::Status::new_exception_str(
                        binder::ExceptionCode::SERVICE_SPECIFIC,
                        Some(format!("Failed to dup the FD for system font map: {:?}", e)),
                    )
                })
            })
            .transpose()?;
        self.sender
            .send(NativeApplicationThreadRequest::BindApplication(BindApplicationRequest {
                system_font_map_fd,
            }))
            .map_err(|e| {
                binder::Status::new_exception_str(
                    binder::ExceptionCode::SERVICE_SPECIFIC,
                    Some(format!("Failed to send a task: {:?}", e)),
                )
            })?;
        Ok(())
    }

    fn setProcessState(&self, state: i32) -> binder::Result<()> {
        info!("setProcessState thread id={:?}", thread::current().id());
        check_calling_uid()?;

        self.sender.send(NativeApplicationThreadRequest::SetProcessState(state)).map_err(|e| {
            binder::Status::new_exception_str(
                binder::ExceptionCode::SERVICE_SPECIFIC,
                Some(format!("Failed to send a task: {:?}", e)),
            )
        })?;
        Ok(())
    }

    fn updateTimeZone(&self) -> binder::Result<()> {
        info!("updateTimeZone thread id={:?}", thread::current().id());
        check_calling_uid()?;

        reset_time_zone();
        Ok(())
    }
}
