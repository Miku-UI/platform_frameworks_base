// Copyright (C) 2025 The Android Open Source Project
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

//! Read kernel Uevents through netlink
//!

use anyhow::{anyhow, bail, Context, Result};
use kobject_uevent;
use nix::poll;
use nix::sys::socket;
use tokio::io::unix::AsyncFd;

use async_trait::async_trait;
use std::os::fd::{AsFd, AsRawFd, OwnedFd};

// ueventd uses buffer size of 16M by default - but we go with 1MB buffer.
// If the consumer of this library is really slow to dequeue packets we risk
// buffer overflow and missing events. Since USB HAL is the only consumer which
// is relatively lightweight and should be dequeuing events at a faster pace the
// 1MB should not be a concern here.
const UEVENT_BUF_SIZE: usize = 1024 * 1024;

fn create_socket() -> Result<OwnedFd> {
    let addr = socket::NetlinkAddr::new(0, 0xffffffff);
    let s = socket::socket(
        socket::AddressFamily::Netlink,
        socket::SockType::Datagram,
        socket::SockFlag::SOCK_NONBLOCK | socket::SockFlag::SOCK_CLOEXEC,
        socket::SockProtocol::NetlinkKObjectUEvent,
    )?;
    socket::setsockopt(&s, socket::sockopt::RcvBuf, &UEVENT_BUF_SIZE)?;
    socket::setsockopt(&s, socket::sockopt::PassCred, &true)?;
    socket::bind(s.as_raw_fd(), &addr)?;

    Ok(s)
}

/// Socket for listening on KObject Uevents
pub struct NetlinkKObjectUEventSocket {
    fd: OwnedFd,
}

impl NetlinkKObjectUEventSocket {
    /// Create a listener on NetLink for kernel events.
    pub fn create() -> Result<Self> {
        let fd = create_socket()?;
        Ok(Self { fd })
    }

    /// Wait for one or more kernel events to appear on the NetLink
    fn wait(&self) -> Result<()> {
        loop {
            let mut fds = [poll::PollFd::new(self.fd.as_fd(), poll::PollFlags::POLLIN)];
            // TODO - creating epoll fd in create() and using epoll_wait() might be faster than poll()?
            let nr = poll::poll(&mut fds, poll::PollTimeout::NONE)?;
            // TODO - check will this condition ever occur - may be because of spurious wakeup?
            if nr == 0 {
                continue;
            }
            // Fetch returned event which caused this wakeup.
            let revents = fds[0].revents().context("Invalid revents found")?;
            if revents.contains(poll::PollFlags::POLLIN) {
                break;
            }
        }
        Ok(())
    }

    /// Wait and read uevent.
    pub fn read(&self) -> Result<kobject_uevent::UEvent> {
        self.wait()?;
        let mut buffer = [0u8; UEVENT_BUF_SIZE];
        // TODO - use recvmsg and validate credentials
        let count = socket::recv(self.fd.as_raw_fd(), &mut buffer, socket::MsgFlags::empty())?;
        if count == 0 {
            bail!("Netlink socket recv return 0 bytes");
        }
        kobject_uevent::UEvent::from_netlink_packet(&buffer[0..count]).map_err(|e| anyhow!("{e}"))
    }
}

/// Asynchronous UEvent socket operations.
#[async_trait]
pub trait AsyncUEventSocket: Send + Sync {
    /// Waits for data from netlink socket and returns parsed uevent from read data.
    async fn read(&self) -> Result<kobject_uevent::UEvent>;
}

/// Asynchronous implementation of uevent socket listener.
pub struct AsyncNetlinkKObjectUEventSocket {
    afd: AsyncFd<OwnedFd>,
}

impl AsyncNetlinkKObjectUEventSocket {
    /// Create async listener on netlink socket for uevents.
    pub fn create() -> Result<Self> {
        let fd = create_socket()?;
        let afd = AsyncFd::new(fd)?;

        Ok(Self { afd })
    }
}
#[async_trait]
impl AsyncUEventSocket for AsyncNetlinkKObjectUEventSocket {
    /// Waits for data from netlink socket and returns parsed uevent from read data.
    async fn read(&self) -> Result<kobject_uevent::UEvent> {
        let mut buffer = [0u8; UEVENT_BUF_SIZE];

        loop {
            let mut guard = self.afd.readable().await?;

            if let Ok(result) = guard.try_io(|inner| {
                Ok(socket::recv(inner.as_raw_fd(), &mut buffer, socket::MsgFlags::empty())?)
            }) {
                let bytes_read = result?;

                if bytes_read == 0 {
                    bail!("Netlink socket read returned 0 bytes");
                }

                return kobject_uevent::UEvent::from_netlink_packet(&buffer[0..bytes_read])
                    .map_err(|e| anyhow!("{e}"));
            }
        }
    }
}
