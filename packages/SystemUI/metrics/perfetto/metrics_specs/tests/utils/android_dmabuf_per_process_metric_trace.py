#!/usr/bin/env python3
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from metrics_specs.tests.utils import trace_proto_builder
from perfetto.protos.perfetto.trace.perfetto_trace_pb2 import Trace
from typing import Optional

def add_dmabuf_alloc_event(builder, ts: int, buf_size: int, tid: int, inode: int, total_allocated: int):
    """Adds a dma_heap_stat event as an ftrace_event."""
    pid = tid
    ftrace = builder.add_ftrace_event(ts, tid)
    dma_heap_stat = ftrace.dma_heap_stat
    dma_heap_stat.inode = inode
    dma_heap_stat.len = buf_size
    dma_heap_stat.total_allocated = total_allocated

def add_binder_transaction_events(builder, ts_start: int, duration: int, client_tid: int, server_tid: int, flow_id: int):
    reply_ts_start = ts_start + int(duration/2)
    reply_ts_end = ts_start + duration
    transaction_id = flow_id
    reply_id = flow_id + 1000
    builder.add_binder_transaction(transaction_id, ts_start, ts_start + duration, client_tid, client_tid, reply_id, reply_ts_start, reply_ts_end, server_tid, server_tid)

def add_process(builder, package_name: str, uid: int, pid: int):
  builder.add_package_list(ts=0, name=package_name, uid=uid, version_code=1)
  builder.add_process(pid=pid, ppid=pid, cmdline=package_name, uid=uid)
  builder.add_thread(tid=pid, tgid=pid, cmdline="MainThread", name="MainThread")

def get_proto():
    SYSUI_PID = 1000
    SYSUI_PROCESS_NAME = "com.android.systemui"

    SYSTEM_SERVER_PID = 2000
    SYSTEM_SERVER_PROCESS_NAME = "system_server"

    GRALLOC_PID = 4000
    GRALLOC_PROCESS_NAME = "/vendor/bin/hw/android.hardware.graphics.allocator"

    RANDOM_PROCESS_PID = 5000
    RANDOM_PROCESS_NAME = "random_process"

    INODE = 1111

    trace = Trace()
    builder = trace_proto_builder.TraceProtoBuilder(trace)

    # Add a generic packet, this is needed to add the processes and threads
    builder.add_packet()

    # Add processes
    add_process(builder, package_name=SYSUI_PROCESS_NAME, uid=10001, pid=SYSUI_PID)
    add_process(builder, package_name=SYSTEM_SERVER_PROCESS_NAME, uid=20001, pid=SYSTEM_SERVER_PID)
    add_process(builder, package_name=GRALLOC_PROCESS_NAME, uid=40001, pid=GRALLOC_PID)
    add_process(builder, package_name=RANDOM_PROCESS_NAME, uid=50001, pid=RANDOM_PROCESS_PID)

    # Add ftrace packet
    builder.add_ftrace_packet(cpu=0)

    current_ts = 0
    flow_id_counter = 1
    total_allocated = 0

    # Simulate DMABuf events, some with binder attribution
    # Process 1: Multiple allocations
    total_allocated += 1024
    add_dmabuf_alloc_event(builder, current_ts, 1024, SYSUI_PID, INODE, total_allocated)
    current_ts += 1000

    # Gralloc Allocation, with binder attribution
    total_allocated += 2048
    add_dmabuf_alloc_event(builder, current_ts, 2048, GRALLOC_PID, INODE, total_allocated)
    add_binder_transaction_events(builder, current_ts, 50, SYSUI_PID, GRALLOC_PID, flow_id_counter)
    flow_id_counter += 1
    current_ts += 1500

    total_allocated -= 1024
    add_dmabuf_alloc_event(builder, current_ts, -1024, SYSUI_PID, INODE, total_allocated)
    current_ts += 500

    total_allocated += 512
    add_dmabuf_alloc_event(builder, current_ts, 512, SYSUI_PID, INODE, total_allocated)
    current_ts += 2000

    # Process 2: Allocation and free
    total_allocated += 8192
    add_dmabuf_alloc_event(builder, current_ts, 8192, SYSTEM_SERVER_PID, INODE, total_allocated)
    current_ts += 1000

    total_allocated -= 8192
    add_dmabuf_alloc_event(builder, current_ts, -8192, SYSTEM_SERVER_PID, INODE, total_allocated)
    current_ts += 500

    # Random process allocation, should not be tracked
    total_allocated += 256
    add_dmabuf_alloc_event(builder, current_ts, 256, RANDOM_PROCESS_PID, INODE, total_allocated)
    current_ts += 500

    # Gralloc release, with binder attribution
    total_allocated -= 2048
    add_dmabuf_alloc_event(builder, current_ts, -2048, GRALLOC_PID, INODE, total_allocated)
    add_binder_transaction_events(builder, current_ts, 20, SYSUI_PID, GRALLOC_PID, flow_id_counter)
    flow_id_counter += 1
    current_ts += 500

    return builder.trace.SerializeToString()