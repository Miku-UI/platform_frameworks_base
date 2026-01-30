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

use binder::{Interface, SpIBinder};
use log::info;
use native_application_thread_aidl::aidl::android::app::INativeApplicationThread::INativeApplicationThread;
use std::{marker::PhantomData, thread};

use crate::task::Sender;

pub struct CreateServiceRequest {
    pub service_token: SpIBinder,
    pub library_paths: Vec<String>,
    pub permitted_libs_dir: String,
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
    unsafe fn new(
        service_token: SpIBinder,
        library_paths: Vec<String>,
        permitted_libs_dir: String,
        library_name: String,
        base_symbol_name: String,
        process_state: i32,
    ) -> Self {
        Self {
            service_token,
            library_paths,
            permitted_libs_dir,
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
    pub intent_hash: i32,
    pub action: Option<String>,
    pub data: Option<String>,
    pub rebind: bool,
    pub _process_state: i32,
    pub _bind_seq: i64,
}

pub struct UnbindServiceRequest {
    pub service_token: SpIBinder,
    pub bind_token: SpIBinder,
    pub intent_hash: i32,
}

pub enum NativeApplicationThreadRequest {
    CreateService(CreateServiceRequest),
    DestroyService(DestroyServiceRequest),
    BindService(BindServiceRequest),
    UnbindService(UnbindServiceRequest),
    TrimMemory(i32),
    BindApplication,
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
        library_paths: &[String],
        permitted_libs_dir: &str,
        library_name: &str,
        base_symbol_name: &str,
        _process_state: i32,
    ) -> binder::Result<()> {
        info!("scheduleCreateService thread id={:?}", thread::current().id());
        // SAFETY: We trust that the caller of this function requests to load a library specified
        // by the application according to the native service specification. The application is
        // responsible for implementing a safe library and an entry point function of its native
        // service with the type signature `ANativeService_createFunc`.
        let req = unsafe {
            CreateServiceRequest::new(
                service_token.clone(),
                library_paths.to_vec(),
                permitted_libs_dir.to_string(),
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
        intent_hash: i32,
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
        self.sender
            .send(NativeApplicationThreadRequest::BindService(BindServiceRequest {
                service_token: service_token.clone(),
                bind_token: bind_token.clone(),
                intent_hash,
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
        intent_hash: i32,
    ) -> binder::Result<()> {
        info!("scheduleUnbindService thread id={:?}", thread::current().id());
        self.sender
            .send(NativeApplicationThreadRequest::UnbindService(UnbindServiceRequest {
                service_token: service_token.clone(),
                bind_token: bind_token.clone(),
                intent_hash,
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
        info!("scheduleLowMemory thread id={:?}", thread::current().id());
        self.sender.send(NativeApplicationThreadRequest::TrimMemory(level)).map_err(|e| {
            binder::Status::new_exception_str(
                binder::ExceptionCode::SERVICE_SPECIFIC,
                Some(format!("Failed to send a task: {:?}", e)),
            )
        })?;
        Ok(())
    }

    fn bindApplication(&self) -> binder::Result<()> {
        info!("bindApplication thread id={:?}", thread::current().id());
        self.sender.send(NativeApplicationThreadRequest::BindApplication).map_err(|e| {
            binder::Status::new_exception_str(
                binder::ExceptionCode::SERVICE_SPECIFIC,
                Some(format!("Failed to send a task: {:?}", e)),
            )
        })?;
        Ok(())
    }

    fn setProcessState(&self, state: i32) -> binder::Result<()> {
        info!("setProcessState thread id={:?}", thread::current().id());
        self.sender.send(NativeApplicationThreadRequest::SetProcessState(state)).map_err(|e| {
            binder::Status::new_exception_str(
                binder::ExceptionCode::SERVICE_SPECIFIC,
                Some(format!("Failed to send a task: {:?}", e)),
            )
        })?;
        Ok(())
    }
}
