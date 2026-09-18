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

use anyhow::{bail, Context, Result};
use bitflags::bitflags;
use libc::{
    dl_iterate_phdr, dl_phdr_info, mallopt, mprotect, size_t, PROT_EXEC, PROT_READ, PT_LOAD,
};
use native_activity_thread_bindgen::{tzset, PF_X};
use nix::{
    errno::Errno,
    sys::resource::{getrlimit, setrlimit, Resource},
    unistd::{getuid, sysconf, SysconfVar},
};
use rustutils::android::process::android_mallopt;
use rustutils::android::process::MalloptOpcode;
use rustutils::android::system_properties;

use std::ffi::{c_int, c_ulong, c_void};
use std::io::Error;
use std::slice;

const AID_APP_START: u32 = 10000;

static PROP_ZYGOTE_CORE_DUMP: &str = "persist.zygote.core_dump";
static PROP_DEBUGGABLE: &str = "ro.debuggable";

/// A safe wrapper around tzset()
pub fn reset_time_zone() {
    // Refresh Bionic's timezone information.
    // SAFETY: Not passing any function parameter.
    unsafe { tzset() };
}

/// Since Native Zygote only forks isolated process, which cannot read the `ro.debuggable` property,
/// we should call this function and cache the value when the Native Zygote server is initialized.
pub fn get_or_init_debuggable() -> bool {
    static DEBUGGABLE: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *DEBUGGABLE
        .get_or_init(|| system_properties::read_bool(PROP_DEBUGGABLE, false).unwrap_or(false))
}

fn get_page_size() -> usize {
    match sysconf(SysconfVar::PAGE_SIZE) {
        Ok(Some(size)) => size as usize,
        Ok(None) => panic!("sysconf(PAGE_SIZE) returned None"),
        Err(e) => panic!("sysconf(PAGE_SIZE) returned an error: {e}"),
    }
}

#[allow(non_camel_case_types)]
#[cfg(target_pointer_width = "64")]
type ElfW_Phdr = libc::Elf64_Phdr;
#[allow(non_camel_case_types)]
#[cfg(target_pointer_width = "32")]
type ElfW_Phdr = libc::Elf32_Phdr;

/// A callback function for `dl_iterate_phdr` which changes XO regions to RX.
///
/// # Safety
/// Callers must ensure that `info` is a valid pointer to `dl_phdr_info` which outlives this
/// function and contains a shared object information.
unsafe extern "C" fn disable_execute_only(
    info: *mut dl_phdr_info,
    _size: usize,
    _data: *mut c_void,
) -> c_int {
    if info.is_null() {
        return 0;
    }

    // SAFETY: `info` is a valid pointer which outlives this function and is aligned properly.
    let info_ref = unsafe { &*info };

    let phdr_ptr = info_ref.dlpi_phdr;
    let phdr_count = info_ref.dlpi_phnum as usize;

    // SAFETY: `phdr_ptr` points to a valid ElfW_Phdr array of `phdr_count` elements.
    let program_headers: &[ElfW_Phdr] = unsafe { slice::from_raw_parts(phdr_ptr, phdr_count) };

    let page_mask = !(get_page_size() - 1);

    // Search for any execute-only segments and mark them read+execute.
    // This operation only affects RWX flags because of the implementation
    // of mprotect, so other architectural flags (like PROT_BTI) will not be cleared.
    for phdr in program_headers {
        if phdr.p_type == PT_LOAD && phdr.p_flags == PF_X {
            let addr = (info_ref.dlpi_addr + phdr.p_vaddr) as usize;
            let aligned_addr = (addr & page_mask) as *mut c_void;
            let len = phdr.p_memsz as size_t;
            let adjusted_len = addr + len - aligned_addr as usize;

            // SAFETY: `aligned_addr` is aligned to a page boundary and `adjusted_len` is a valid
            // size of the region including the XO segment.
            let ret = unsafe { mprotect(aligned_addr, adjusted_len, PROT_READ | PROT_EXEC) };
            if ret != 0 {
                log::warn!("Failed to mprotect(): {}", Error::last_os_error());
            }
        }
    }

    0
}

bitflags! {
    /// Runtime flag constants.
    /// Must match values in com.android.internal.os.Zygote.
    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    pub struct RuntimeFlags: u32 {
        const DEBUG_ENABLE_JDWP = 1;
        const DEBUG_ENABLE_CHECKJNI = 1 << 1;
        const DEBUG_ENABLE_ASSERT = 1 << 2;
        const DEBUG_ENABLE_SAFEMODE = 1 << 3;
        const DEBUG_ENABLE_JNI_LOGGING = 1 << 4;
        const DEBUG_GENERATE_DEBUG_INFO = 1 << 5;
        const DEBUG_ALWAYS_JIT = 1 << 6;
        const DEBUG_NATIVE_DEBUGGABLE = 1 << 7;
        const DEBUG_JAVA_DEBUGGABLE = 1 << 8;
        const DISABLE_VERIFIER = 1 << 9;
        const ONLY_USE_SYSTEM_OAT_FILES = 1 << 10;
        const DEBUG_GENERATE_MINI_DEBUG_INFO = 1 << 11;
        const API_ENFORCEMENT_POLICY_MASK = (1 << 12) | (1 << 13);
        const PROFILE_SYSTEM_SERVER = 1 << 14;
        const PROFILE_FROM_SHELL = 1 << 15;
        const USE_APP_IMAGE_STARTUP_CACHE = 1 << 16;
        const DEBUG_IGNORE_APP_SIGNAL_HANDLER = 1 << 17;
        const DISABLE_TEST_API_ENFORCEMENT_POLICY = 1 << 18;
        const MEMORY_TAG_LEVEL_MASK = (1 << 19) | (1 << 20);
        const MEMORY_TAG_LEVEL_TBI = 1 << 19;
        const MEMORY_TAG_LEVEL_ASYNC = 2 << 19;
        const MEMORY_TAG_LEVEL_SYNC = 3 << 19;
        const GWP_ASAN_LEVEL_MASK = (1 << 21) | (1 << 22);
        const GWP_ASAN_LEVEL_NEVER = 0 << 21;
        const GWP_ASAN_LEVEL_LOTTERY = 1 << 21;
        const GWP_ASAN_LEVEL_ALWAYS = 2 << 21;
        const GWP_ASAN_LEVEL_DEFAULT = 3 << 21;
        const NATIVE_HEAP_ZERO_INIT_ENABLED = 1 << 23;
        const PROFILEABLE = 1 << 24;
        const DEBUG_ENABLE_PTRACE = 1 << 25;
        const ENABLE_PAGE_SIZE_APP_COMPAT = 1 << 26;
        const ENABLE_EXECUTE_ONLY_MEMORY = 1 << 27;
    }
}

impl RuntimeFlags {
    pub fn get_heap_tagging_level(&self) -> c_int {
        match self.intersection(Self::MEMORY_TAG_LEVEL_MASK) {
            Self::MEMORY_TAG_LEVEL_TBI => libc::M_HEAP_TAGGING_LEVEL_TBI,
            Self::MEMORY_TAG_LEVEL_ASYNC => libc::M_HEAP_TAGGING_LEVEL_ASYNC,
            Self::MEMORY_TAG_LEVEL_SYNC => libc::M_HEAP_TAGGING_LEVEL_SYNC,
            _ => libc::M_HEAP_TAGGING_LEVEL_NONE,
        }
    }

    pub fn is_native_heap_zero_init_enabled(&self) -> bool {
        self.contains(Self::NATIVE_HEAP_ZERO_INIT_ENABLED)
    }

    pub fn is_ptrace_enabled(&self) -> bool {
        self.contains(Self::DEBUG_ENABLE_PTRACE)
    }

    pub fn is_profileable_from_shell(&self) -> bool {
        self.contains(Self::PROFILE_FROM_SHELL)
    }

    pub fn is_profileable(&self) -> bool {
        self.contains(Self::PROFILEABLE)
    }

    pub fn is_execute_only_memory_enabled(&self) -> bool {
        self.contains(Self::ENABLE_EXECUTE_ONLY_MEMORY)
    }
}

pub fn apply_runtime_flags(runtime_flags: u32) -> Result<()> {
    let Some(flags) = RuntimeFlags::from_bits(runtime_flags) else {
        bail!("runtime_flags doesn't have a valid representation: {:#x}", runtime_flags);
    };

    // Set process properties to enable debugging if required.
    if flags.is_ptrace_enabled() {
        if let Err(e) = enable_debugger() {
            log::warn!("Failed to enable debbuger: {e}");
        }
    }

    if flags.is_profileable_from_shell() {
        // simpleperf needs the process to be dumpable to profile it.
        prctl_set_dumpable(Dumpable::ByUser).expect("prctl(PR_SET_DUMPABLE) failed");
    }

    if get_or_init_debuggable() || flags.is_profileable() {
        // SAFETY: This opcode takes no arguments so a nullptr is passed
        //         instead.
        let ret = unsafe {
            android_mallopt(MalloptOpcode::InitZygoteChildProfiling, std::ptr::null_mut(), 0)
        };
        if ret.is_err() {
            log::error!(
                "Failed to android_mallopt(M_INIT_ZYGOTE_CHILD_HEAP_PROFILING): {}",
                Error::last_os_error()
            );
        }
    }

    if !flags.is_native_heap_zero_init_enabled() {
        // SAFETY: Setting configuration with valid argument (0 = disable).
        let ret = unsafe { mallopt(libc::M_BIONIC_ZERO_INIT, 0) };
        if ret == 0 {
            log::warn!("Failed to mallopt(M_BIONIC_ZERO_INIT): {}", Error::last_os_error());
        }
    }

    // SAFETY: Setting configuration with valid argument (as defined in get_heap_tagging_level).
    let ret =
        unsafe { mallopt(libc::M_BIONIC_SET_HEAP_TAGGING_LEVEL, flags.get_heap_tagging_level()) };
    if ret == 0 {
        log::warn!(
            "Failed to mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL): {}",
            Error::last_os_error()
        );
    }

    // If the app does not support execute-only memory, then iterate through
    // the shared objects and mark them readable.
    if cfg!(feature = "build_execute_only_memory") && !flags.is_execute_only_memory_enabled() {
        // SAFETY: Passes a callback function which has the expected function signature. The
        // callback doesn't use `data`. The libc runtime guarantees to meet the safety requirements
        // of the callback.
        unsafe { dl_iterate_phdr(Some(disable_execute_only), std::ptr::null_mut()) };
    }

    Ok(())
}

// Enum corresponding to SUID_DUMP_* defined in linux/sched/coredump.h.
#[derive(PartialEq)]
enum Dumpable {
    Disable,
    ByUser,
    ByRoot,
}

impl TryFrom<i32> for Dumpable {
    type Error = ();

    fn try_from(value: i32) -> Result<Self, Self::Error> {
        let dumpable = match value {
            0 => Dumpable::Disable,
            1 => Dumpable::ByUser,
            2 => Dumpable::ByRoot,
            _ => return Err(()),
        };
        Ok(dumpable)
    }
}

impl From<Dumpable> for i32 {
    fn from(value: Dumpable) -> Self {
        match value {
            Dumpable::Disable => 0,
            Dumpable::ByUser => 1,
            Dumpable::ByRoot => 2,
        }
    }
}

// TODO(b/450462991): Use nix::sys::prctl::get_dumpable once `prctl` module is available to android
// and this issue is fixed: https://github.com/nix-rust/nix/issues/2684.
fn prctl_get_dumpable() -> nix::Result<Dumpable> {
    // SAFETY: Trivially safe. See `man PR_GET_DUMPABLE`.
    let res = unsafe { libc::prctl(libc::PR_GET_DUMPABLE) };
    if res == -1 {
        return Err(Errno::last());
    }
    Ok(Dumpable::try_from(res)
        .unwrap_or_else(|_| panic!("prctl(PR_GET_DUMPABLE) returned an unexpected value: {res}")))
}

// TODO(b/450462991): Use nix::sys::prctl::set_dumpable once `prctl` module is available to
// android.
fn prctl_set_dumpable(dumpable: Dumpable) -> nix::Result<()> {
    let val: i32 = dumpable.into();
    // SAFETY: Trivially safe. See `man PR_SET_DUMPABLE`.
    let res = unsafe { libc::prctl(libc::PR_SET_DUMPABLE, val) };
    if res == -1 {
        return Err(Errno::last());
    }
    Ok(())
}

fn prctl_set_ptracer(pid: c_ulong) -> nix::Result<()> {
    // SAFETY: Trivially safe. See `man PR_SET_PTRACER`.
    let res = unsafe { libc::prctl(libc::PR_SET_PTRACER, pid) };
    if res == -1 {
        return Err(Errno::last());
    }
    Ok(())
}

pub fn setup_process_dumpability() -> Result<()> {
    if prctl_get_dumpable().context("prctl(PR_GET_DUMPABLE) failed")? == Dumpable::ByRoot
        && getuid().as_raw() >= AID_APP_START
    {
        prctl_set_dumpable(Dumpable::Disable).context("prctl(PR_SET_DUMPABLE) failed")?;
    }
    Ok(())
}

fn enable_debugger() -> Result<()> {
    // To let a non-privileged gdbserver attach to this process,
    // we must set our dumpable flag.
    prctl_set_dumpable(Dumpable::ByUser).context("prctl(PR_SET_DUMPABLE) failed")?;

    // A non-privileged native debugger should be able to attach to the debuggable app,
    // even if Yama is enabled (see kernel/Documentation/security/Yama.txt).
    if let Err(errno) = prctl_set_ptracer(libc::PR_SET_PTRACER_ANY) {
        // if Yama is off prctl(PR_SET_PTRACER) returns EINVAL -
        // don't log in this case since it's expected behaviour.
        if errno != Errno::EINVAL {
            bail!("prctl(PR_SET_TRACER, PR_SET_PTRACER_ANY) failed: {errno}");
        }
    }

    // Set the core dump size to zero unless wanted
    // (see also coredump_setup in build/envsetup.sh).
    if system_properties::read_bool(PROP_ZYGOTE_CORE_DUMP, false)
        .with_context(|| format!("Failed to read {PROP_ZYGOTE_CORE_DUMP}"))?
    {
        // Set the soft limit on core dump size to 0 without changing the hard limit.
        let (_, hard_limit) =
            getrlimit(Resource::RLIMIT_CORE).context("getrlimit(RLIMIT_CORE) failed")?;
        setrlimit(Resource::RLIMIT_CORE, 0, hard_limit).context("setrlimit(RLIMIT_CORE) failed")?;
    }

    Ok(())
}
