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
use native_activity_thread_bindgen::{
    ANativeService, ANativeServiceCallbacks,
    ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_BACKGROUND,
    ANativeService_createFunc,
};
use std::{collections::BTreeMap, ffi::CString};

use crate::font::load_system_font_map;
use crate::library_loader::{LinkerNamespace, LoadedLibrary, NamespaceFactory};
use crate::native_application_thread::{
    BindApplicationRequest, BindServiceRequest, CreateServiceRequest, DestroyServiceRequest,
    NativeApplicationThreadRequest, UnbindServiceRequest,
};
use crate::preload;
use crate::task::HandlerCallback;
use crate::utils::reset_time_zone;

struct BindTokenMap {
    tokens: BTreeMap<SpIBinder, u64>,
}

impl BindTokenMap {
    fn new() -> Self {
        Self { tokens: BTreeMap::new() }
    }

    fn get(&self, bind_token: &SpIBinder) -> Option<&u64> {
        self.tokens.get(bind_token)
    }

    fn insert(&mut self, bind_token: SpIBinder) -> Result<u64> {
        let token: u64 = self
            .tokens
            .len()
            .checked_add(1)
            .context("too many service bindings")?
            .try_into()
            .context("too many service bindings")?;
        self.tokens.insert(bind_token, token);
        Ok(token)
    }
}

struct NativeService {
    /// The linker namespace for the service. All libraries are loaded in this namespace.
    _namespace: LinkerNamespace,
    /// The library which has the ANativeService_createFunc implementation for the service.
    _library: LoadedLibrary,
    /// ANativeService instance associated with the service.
    service: Box<ANativeService>,
    /// A map converting bindToken from the internal representation to the external representation visible to apps.
    bind_tokens: BindTokenMap,
}

/// NativeActivityThread manages the lifecycle of a native process. It receives requests through
/// IApplicationThread binder method calls and runs callback functions provided by native services.
pub struct NativeActivityThread {
    activity_manager: Strong<dyn IActivityManagerStructured>,
    start_seq: i64,
    services: BTreeMap<SpIBinder, NativeService>,
    process_state: i32,
}

impl NativeActivityThread {
    pub fn new(activity_manager: Strong<dyn IActivityManagerStructured>, start_seq: i64) -> Self {
        Self {
            activity_manager,
            start_seq,
            services: BTreeMap::new(),
            process_state: ProcessStateEnum::UNKNOWN.0,
        }
    }

    // Create a linker namespace dedicated to the service. A process could host multiple
    // services but their namespaces must be isolated.
    fn create_linker_namespace(
        &self,
        zip_paths: &str,
        library_paths: &str,
        permitted_libs_dir: &str,
        target_sdk_version: i32,
        is_shared: bool,
        native_shared_lib_path: &str,
    ) -> Result<LinkerNamespace> {
        match preload::reuse_namespace(library_paths, permitted_libs_dir) {
            Some(namespace) => Ok(namespace),
            None => NamespaceFactory::create_linker_namespace(
                target_sdk_version,
                is_shared,
                zip_paths,
                library_paths,
                permitted_libs_dir,
                native_shared_lib_path,
            ),
        }
    }

    fn handle_create_service_request(&mut self, req: CreateServiceRequest) -> Result<()> {
        // Do not modify this slice name. It's referred to by a perfetto module
        // (android.app_process_starts).
        let _atrace_trace_method_guard = atrace::begin_scoped_event(
            AtraceTag::ActivityManager,
            &format!(
                "serviceCreate: library_name: {}, base_symbol_name: {}",
                req.library_name, req.base_symbol_name
            ),
        );

        let namespace = self.create_linker_namespace(
            &req.zip_paths,
            &req.library_paths,
            &req.permitted_libs_dir,
            req.target_sdk_version,
            req.is_shared,
            &req.native_shared_lib_path,
        )?;

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
            NativeService {
                _namespace: namespace,
                _library: library,
                service,
                bind_tokens: BindTokenMap::new(),
            },
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
        // Do not modify this slice name. It's referred to by a perfetto module
        // (android.services).
        let _atrace_trace_method_guard = atrace::begin_scoped_event(
            AtraceTag::ActivityManager,
            &format!(
                "serviceBind: action: {:?}, data: {:?}, rebind: {}",
                req.action, req.data, req.rebind
            ),
        );

        let service = self.services.get_mut(&req.service_token).context("service not found")?;

        let external_token = match service.bind_tokens.get(&req.bind_token) {
            Some(token) => *token,
            None => {
                if req.rebind {
                    bail!("bindToken must exist");
                }
                service.bind_tokens.insert(req.bind_token.clone())?
            }
        };

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
                unsafe { on_bind(native_service, external_token, action_ptr, data_ptr) };
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
                    on_rebind(native_service, external_token);
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

        // bind_token must be maintained for the service's lifetime even if it's unbound because
        //  1. AMS retain the corresponding IntentBindRecord and it may be reused when rebinding to
        //  the service with the same Intent, and
        //  2. we need to ensure that the same AIBinder instance will not be reused for a different
        //  binding.
        let external_token =
            *service.bind_tokens.get(&req.bind_token).context("bindToken not found")?;

        let request_on_rebind = if let Some(on_unbind) = service.service.callbacks.onUnbind {
            let native_service = service.service.as_mut() as *mut ANativeService;
            // SAFETY: Passing a reference to a valid variable.
            unsafe { on_unbind(native_service, external_token) }
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
        if self.process_state <= ProcessStateEnum::IMPORTANT_FOREGROUND.0
            && level >= ANativeServiceTrimMemoryLevel_ANATIVE_SERVICE_TRIM_MEMORY_BACKGROUND
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

    fn handle_bind_application_request(&mut self, req: BindApplicationRequest) -> Result<()> {
        // Do not modify this slice name. It's referred to by a perfetto module
        // (android.app_process_starts).
        let _atrace_trace_method_guard =
            atrace::begin_scoped_event(AtraceTag::ActivityManager, "bindApplication");

        // Reset the time zone to be the system time zone. This needs to be done because the system
        // time zone could have changed after the spawning of this process. Without doing this, this
        // process would have the incorrect system time zone.
        reset_time_zone();

        if let Some(fd) = req.system_font_map_fd {
            load_system_font_map(fd)?;
        }

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
            NativeApplicationThreadRequest::BindApplication(req) => {
                self.handle_bind_application_request(req)
            }
            NativeApplicationThreadRequest::SetProcessState(state) => {
                self.handle_set_process_state(state)
            }
        }
    }
}
