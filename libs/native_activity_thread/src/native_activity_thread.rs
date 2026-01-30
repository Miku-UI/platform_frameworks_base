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

use activitymanager_structured_aidl::aidl::android::app::IActivityManagerStructured::{
    IActivityManagerStructured, SERVICE_DONE_EXECUTING_ANON, SERVICE_DONE_EXECUTING_REBIND,
    SERVICE_DONE_EXECUTING_STOP, SERVICE_DONE_EXECUTING_UNBIND,
};
use anyhow::{bail, Context, Result};
use atrace::AtraceTag;
use binder::{
    unstable_api::{new_spibinder, AIBinder as SysAIBinder},
    SpIBinder, Strong,
};
use libactivity_manager_procstate_aidl::aidl::android::app::ProcessStateEnum::ProcessStateEnum;
use native_service_bindgen::{
    ANativeService, ANativeServiceCallbacks,
    ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_BACKGROUND,
    ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_UI_HIDDEN, ANativeService_createFunc,
};
use std::{collections::BTreeMap, ffi::CString};

use crate::library_loader::{LinkerNamespace, LoadedLibrary, NamespaceFactory};
use crate::native_application_thread::{
    BindServiceRequest, CreateServiceRequest, DestroyServiceRequest,
    NativeApplicationThreadRequest, UnbindServiceRequest,
};
use crate::task::HandlerCallback;

struct NativeService {
    /// The linker namespace for the service. All libraries are loaded in this namespace.
    _namespace: LinkerNamespace,
    /// The library which has the ANativeService_createFunc implementation for the service.
    _library: LoadedLibrary,
    /// ANativeService instance associated with the service.
    service: Box<ANativeService>,
}

/// NativeActivityThread manages the lifecycle of a native process. It receives requests through
/// IApplicationThread binder method calls and runs callback functions provided by native services.
pub struct NativeActivityThread {
    activity_manager: Strong<dyn IActivityManagerStructured>,
    start_seq: i64,
    services: BTreeMap<SpIBinder, NativeService>,
    namespace_factory: NamespaceFactory,
    process_state: i32,
}

impl NativeActivityThread {
    pub fn new(activity_manager: Strong<dyn IActivityManagerStructured>, start_seq: i64) -> Self {
        Self {
            activity_manager,
            start_seq,
            services: BTreeMap::new(),
            namespace_factory: NamespaceFactory::new(format!("native_app_{}", start_seq)),
            process_state: ProcessStateEnum::UNKNOWN.0,
        }
    }

    fn handle_create_service_request(&mut self, req: CreateServiceRequest) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        // Create a linker namespace dedicated to the service. A process could host multiple
        // services but their namespaces must be isolated.
        let namespace = self
            .namespace_factory
            .create_linker_namespace(&req.library_paths, &req.permitted_libs_dir)?;

        // SAFETY: The application is responsible for implementing the initialization and
        // termination routines of the library safely.
        let library = unsafe { LoadedLibrary::new(&req.library_name, &namespace)? };
        let create_func_addr = library.find_symbol(&req.base_symbol_name)?;

        // SAFETY:
        // `create_func_addr` is a valid pointer to a function exported by the loaded library and
        // it is guaranteed that it can be transmuted into Option<extern "C" fn>.
        // https://doc.rust-lang.org/std/option/index.html#representation
        // The type signature `ANativeService_createFunc` is a part of NDK API and the application
        // must implement the entry point function of the native service with this type signature.
        let create_func: ANativeService_createFunc =
            unsafe { std::mem::transmute(create_func_addr) };

        let mut service = Box::new(ANativeService {
            callbacks: ANativeServiceCallbacks {
                onBind: None,
                onUnbind: None,
                onRebind: None,
                onDestroy: None,
                onTrimMemory: None,
            },
        });

        if let Some(create_func) = create_func {
            // SAFETY: Passing a reference to a valid variable.
            unsafe { create_func(&mut *service) };
        }

        self.activity_manager
            .serviceDoneExecuting(&req.service_token, SERVICE_DONE_EXECUTING_ANON, 0, 0)
            .context("Failed to call serviceDoneExecuting")?;

        self.services.insert(
            req.service_token,
            NativeService { _namespace: namespace, _library: library, service },
        );
        Ok(())
    }

    fn handle_destroy_service_request(&mut self, req: DestroyServiceRequest) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        // Remove the service not to process requests for it anymore.
        let mut service = self.services.remove(&req.service_token).context("service not found")?;
        if let Some(on_destroy) = service.service.callbacks.onDestroy {
            let native_service = service.service.as_mut();
            // SAFETY: Passing a reference to a valid variable.
            unsafe { on_destroy(native_service) };
        }
        self.activity_manager
            .serviceDoneExecuting(&req.service_token, SERVICE_DONE_EXECUTING_STOP, 0, 0)
            .context("Failed to call serviceDoneExecuting")?;
        Ok(())
    }

    fn handle_bind_service_request(&mut self, req: BindServiceRequest) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        let service = self.services.get_mut(&req.service_token).context("service not found")?;
        let intent_token = req.intent_hash;

        if !req.rebind {
            let on_bind = service.service.callbacks.onBind.context("onBind must be implemented")?;
            let native_service = service.service.as_mut();
            let action_cstr = req.action.and_then(|s| CString::new(s).ok());
            let action_ptr = action_cstr.as_ref().map_or(std::ptr::null(), |s| s.as_ptr());
            let data_cstr = req.data.and_then(|s| CString::new(s).ok());
            let data_ptr = data_cstr.as_ref().map_or(std::ptr::null(), |s| s.as_ptr());

            // SAFETY: `ANativeService_onBindCallback` accepts the null pointer or
            // a pointer to a valid C string for `action` and `data`. We pass a reference to a valid
            // vairble for `service`.
            let service_binder_ptr =
                unsafe { on_bind(native_service, intent_token, action_ptr, data_ptr) };
            if service_binder_ptr.is_null() {
                bail!("onBind returned the null pointer");
            }

            let service_binder =
                // SAFETY: The application is responsible for implementing `onBind` to return a
                // valid ABinder pointer.
                unsafe { new_spibinder(service_binder_ptr as *mut SysAIBinder) }
                    .context("Failed to create SpIBinder from ABinder")?;
            self.activity_manager
                .publishService(&req.service_token, &req.bind_token, &service_binder)
                .context("Failed to call publishService")?;
        } else {
            if let Some(on_rebind) = service.service.callbacks.onRebind {
                let native_service = service.service.as_mut();

                // SAFETY: Passing a reference to a valid variable.
                unsafe {
                    on_rebind(native_service, intent_token);
                }
            }
            self.activity_manager
                .serviceDoneExecuting(&req.service_token, SERVICE_DONE_EXECUTING_REBIND, 0, 0)
                .context("Failed to call serviceDoneExecuting")?;
        }
        Ok(())
    }

    fn handle_unbind_service_request(&mut self, req: UnbindServiceRequest) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        let service = self.services.get_mut(&req.service_token).context("service not found")?;
        let intent_token = req.intent_hash;

        let request_on_rebind = if let Some(on_unbind) = service.service.callbacks.onUnbind {
            let native_service = service.service.as_mut() as *mut ANativeService;
            // SAFETY: Passing a reference to a valid variable.
            unsafe { on_unbind(native_service, intent_token) }
        } else {
            false
        };
        if request_on_rebind {
            self.activity_manager
                .unbindFinished(&req.service_token, &req.bind_token)
                .context("Failed to call unbindFinished")?;
        } else {
            self.activity_manager
                .serviceDoneExecuting(&req.service_token, SERVICE_DONE_EXECUTING_UNBIND, 0, 0)
                .context("Failed to call serviceDoneExecuting")?;
        }
        Ok(())
    }

    fn handle_trim_memory_request(&mut self, level: i32) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        if level != ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_BACKGROUND
            && level != ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_UI_HIDDEN
        {
            bail!("Received an unexpected level: {}", level);
        }
        if self.process_state <= ProcessStateEnum::IMPORTANT_FOREGROUND.0
            && level == ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_BACKGROUND
        {
            return Ok(());
        }
        for service in self.services.values_mut() {
            if let Some(on_trim_memory) = service.service.callbacks.onTrimMemory {
                let native_service = service.service.as_mut();
                // SAFETY: Passing a reference to a valid variable.
                unsafe { on_trim_memory(native_service, level) };
            }
        }
        Ok(())
    }

    fn handle_bind_application_request(&mut self) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        // We don't support calling Application.onCreate in native processes.
        self.activity_manager
            .finishAttachApplication(self.start_seq, 0)
            .context("Failed to call finishAttachApplication")
    }

    fn handle_set_process_state(&mut self, state: i32) -> Result<()> {
        atrace::trace_method!(AtraceTag::ActivityManager);
        self.process_state = state;
        Ok(())
    }
}

impl HandlerCallback<NativeApplicationThreadRequest> for NativeActivityThread {
    fn handle_task(&mut self, task: NativeApplicationThreadRequest) -> Result<()> {
        match task {
            NativeApplicationThreadRequest::CreateService(req) => {
                self.handle_create_service_request(req)
            }
            NativeApplicationThreadRequest::DestroyService(req) => {
                self.handle_destroy_service_request(req)
            }
            NativeApplicationThreadRequest::BindService(req) => {
                self.handle_bind_service_request(req)
            }
            NativeApplicationThreadRequest::UnbindService(req) => {
                self.handle_unbind_service_request(req)
            }
            NativeApplicationThreadRequest::TrimMemory(level) => {
                self.handle_trim_memory_request(level)
            }
            NativeApplicationThreadRequest::BindApplication => {
                self.handle_bind_application_request()
            }
            NativeApplicationThreadRequest::SetProcessState(state) => {
                self.handle_set_process_state(state)
            }
        }
    }
}
