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
use log::{error, info};
use looper_bindgen::{
    ALooper, ALooper_addFd, ALooper_callbackFunc, ALooper_pollOnce, ALooper_prepare,
    ALooper_removeFd, ALOOPER_EVENT_INPUT, ALOOPER_POLL_CALLBACK, ALOOPER_POLL_ERROR,
};
use std::{
    ffi::{c_int, c_void},
    os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd},
    sync::mpsc::{self, channel, TryRecvError},
    thread,
};

const ALOOPER_CALLBACK_FUNC_RETURN_VALUE_CONTINUE: c_int = 1;

macro_rules! retry_eintr {
    ($libc_call:expr) => {
        loop {
            match $libc_call {
                -1 => {
                    let e = std::io::Error::last_os_error();
                    match e.raw_os_error() {
                        Some(libc::EINTR) => continue,
                        _ => break Err(e),
                    }
                }
                result => {
                    break Ok(result);
                }
            }
        }
    };
}

/// A struct used to send tasks to `Handler`.
pub struct Sender<T: Send> {
    tx: mpsc::Sender<T>,
    waker_fd: OwnedFd,
}

impl<T: Send> Sender<T> {
    /// Send a task to the associated `Handler`.
    pub fn send(&self, task: T) -> Result<()> {
        self.tx.send(task).map_err(|_| anyhow!("Failed to send the task"))?;
        self.wake()
    }

    fn wake(&self) -> Result<()> {
        let res = retry_eintr!(
            // SAFETY: `self.waker_fd` is a valid eventfd.
            unsafe { libc::eventfd_write(self.waker_fd.as_raw_fd(), 1) }
        );
        if let Err(e) = res {
            bail!("Failed to write to the waker fd: {}", e);
        }
        Ok(())
    }
}

/// A trait defining expected behavior of callback functions for `Handler`.
pub trait HandlerCallback<T: Send> {
    /// Handle a task.
    /// This function is called on the same thread that created the `Handler` owning the callback.
    /// If this function returns Err, the handler is deactivated and this function will never be
    /// called anymore even if there is a sent task.
    fn handle_task(&mut self, task: T) -> Result<()>;
}

struct HandlerInner<T: Send, C: HandlerCallback<T>> {
    callback: C,
    event_fd: OwnedFd,
    tx: mpsc::Sender<T>,
    rx: mpsc::Receiver<T>,
}

impl<T: Send, C: HandlerCallback<T>> HandlerInner<T, C> {
    fn handle_tasks(&mut self) -> Result<()> {
        loop {
            let req = self.rx.try_recv();
            match req {
                Ok(req) => self.callback.handle_task(req)?,
                Err(TryRecvError::Empty) => return Ok(()),
                Err(TryRecvError::Disconnected) => bail!("mpsc disconnected"),
            }
        }
    }
}

/// A struct representing a task handler.
pub struct Handler<T: Send, C: HandlerCallback<T>> {
    // This makes Handler !Send.
    looper: *mut ALooper,
    // Wrap members used for task handling with Box to ensure they are alive during the handler is
    // registered to the looper.
    inner: Box<HandlerInner<T, C>>,
}

impl<T: Send, C: HandlerCallback<T>> Handler<T, C> {
    pub fn new_on_current_thread(callback: C) -> Result<Self> {
        // SAFETY: 0 is a valid argument.
        let looper = unsafe { ALooper_prepare(0) };
        assert!(!looper.is_null());

        // SAFETY: Passing valid arguments.
        let fd: RawFd = unsafe { libc::eventfd(0, libc::EFD_CLOEXEC | libc::EFD_NONBLOCK) };
        if fd == -1 {
            bail!("Failed to create an eventfd");
        }
        // SAFETY: `fd` is a valid owned fd.
        let event_fd = unsafe { OwnedFd::from_raw_fd(fd) };

        let (tx, rx) = channel::<T>();
        let mut inner = Box::new(HandlerInner { callback, event_fd, tx, rx });
        let inner_ptr = &mut *inner as *mut HandlerInner<T, C> as *mut c_void;
        let handler = Self { looper, inner };

        // SAFETY: `inner_ptr` outlives the duration `poll_callback` is registered.
        unsafe {
            handler.add_fd(
                handler.inner.event_fd.as_raw_fd(),
                ALOOPER_POLL_CALLBACK,
                ALOOPER_EVENT_INPUT as c_int,
                Some(Self::poll_callback),
                inner_ptr,
            )
        }
        .context("Failed to add the waker fd")?;

        info!("A handler is activated on the thread {:?}", thread::current().id());

        Ok(handler)
    }

    pub fn get_sender(&self) -> Result<Sender<T>> {
        let tx = self.inner.tx.clone();
        let waker_fd = self.inner.event_fd.try_clone().context("Failed to clone the eventfd")?;
        Ok(Sender::<T> { tx, waker_fd })
    }

    /// # Safety
    ///
    /// Users must ensure the safety requirements for the callback function to be registered are
    /// met while it's registered.
    unsafe fn add_fd(
        &self,
        fd: RawFd,
        ident: c_int,
        events: c_int,
        callback: ALooper_callbackFunc,
        data: *mut c_void,
    ) -> Result<()> {
        // SAFETY: `self.looper` is a valid ALooper pointer.
        let ret = unsafe { ALooper_addFd(self.looper, fd, ident, events, callback, data) };
        if ret == -1 {
            bail!("ALooper_addFd failed");
        }
        Ok(())
    }

    fn remove_fd(&self, fd: RawFd) -> Result<()> {
        // SAFETY: `self.looper` is a valid ALooper pointer.
        let ret = unsafe { ALooper_removeFd(self.looper, fd) };
        match ret {
            1 => Ok(()),
            0 => bail!("The fd hasn't been added"),
            _ => bail!("ALooper_removeFd failed"),
        }
    }

    /// This function is supposed to be used as a callback function for `ALooper_addFd`.
    /// There's no easy way to tell the caller of `ALooper_pollOnce` that an error occurred, so
    /// this function will panic instead of silently unregistering itself from the looper in such
    /// cases.
    ///
    /// # Safety
    ///
    /// Users must ensure that the associated `data` is a valid pointer to an HandlerInner
    /// instance while this callback is registered.
    unsafe extern "C" fn poll_callback(fd: RawFd, _events: c_int, data: *mut c_void) -> c_int {
        let inner_ptr = data as *mut HandlerInner<T, C>;
        // SAFETY: `inner_ptr` is a valid HandlerInner pointer.
        let inner = unsafe { inner_ptr.as_mut() }.unwrap();
        assert_eq!(fd, inner.event_fd.as_raw_fd());

        let mut val = std::mem::MaybeUninit::<libc::eventfd_t>::uninit();
        let res = retry_eintr!(
            // SAFETY: `inner.event_fd` is a valid eventfd and `val` is properly allocated.
            unsafe { libc::eventfd_read(inner.event_fd.as_raw_fd(), val.as_mut_ptr()) }
        );
        if let Err(e) = res {
            panic!("Failed to read from the event fd: {e}");
        }

        let res = inner.handle_tasks();
        if let Err(e) = res {
            panic!("Failed to handle a task: {e}");
        }
        ALOOPER_CALLBACK_FUNC_RETURN_VALUE_CONTINUE
    }
}

impl<T: Send, C: HandlerCallback<T>> Drop for Handler<T, C> {
    fn drop(&mut self) {
        if self.remove_fd(self.inner.event_fd.as_raw_fd()).is_err() {
            error!("Failed to remove the event fd");
        }
    }
}

/// Run the server loop on this thread.
pub fn run_thread_loop_once() -> Result<()> {
    // SAFETY: `ALooper_pollOnce` accepts the null pointer for `outFd`, `outEvents` and `outData`.
    let ret = unsafe {
        ALooper_pollOnce(-1, std::ptr::null_mut(), std::ptr::null_mut(), std::ptr::null_mut())
    };
    if ret == ALOOPER_POLL_ERROR {
        bail!("ALooper_pollOnce failed");
    }
    Ok(())
}

/// Run the server loop on this thread. This function will never return until an error occurs.
pub fn run_thread_loop() -> Result<()> {
    loop {
        run_thread_loop_once()?;
    }
}
