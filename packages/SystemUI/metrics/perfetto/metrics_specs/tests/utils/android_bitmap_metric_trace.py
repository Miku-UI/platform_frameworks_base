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

def get_proto():
    SYSUI_PID = 1000
    SYSUI_PROCESS_NAME = "com.android.systemui"

    LAUNCHER_PID = 2000
    LAUNCHER_PROCESS_NAME = "com.google.android.apps.nexuslauncher"

    THIRD_PROCESS_PID = 3000
    THIRD_PROCESS_NAME = "random_process"

    trace = Trace()
    builder = trace_proto_builder.TraceProtoBuilder(trace)

    # Add all processes
    builder.add_packet()
    builder.add_process(pid=SYSUI_PID, ppid=SYSUI_PID, cmdline=SYSUI_PROCESS_NAME, uid=10001)
    builder.add_process(pid=LAUNCHER_PID, ppid=LAUNCHER_PID, cmdline=LAUNCHER_PROCESS_NAME, uid=20001)
    builder.add_process(pid=THIRD_PROCESS_PID, ppid=THIRD_PROCESS_PID, cmdline=THIRD_PROCESS_NAME, uid=30001)

    # Add counters in the com.android.systemui process
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=1000, pid=SYSUI_PID, tid=SYSUI_PID, buf="Bitmap Count", cnt=10)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=2000, pid=SYSUI_PID, tid=SYSUI_PID, buf="Bitmap Count", cnt=20)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=1000, pid=SYSUI_PID, tid=SYSUI_PID, buf="Bitmap Memory", cnt=100)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=2000, pid=SYSUI_PID, tid=SYSUI_PID, buf="Bitmap Memory", cnt=200)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=1000, pid=SYSUI_PID, tid=SYSUI_PID, buf="Random Counter", cnt=111)

    # Add counters in the com.google.android.apps.nexuslauncher process
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=1500, pid=LAUNCHER_PID, tid=LAUNCHER_PID, buf="Bitmap Count", cnt=15)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=2500, pid=LAUNCHER_PID, tid=LAUNCHER_PID, buf="Bitmap Count", cnt=25)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=1500, pid=LAUNCHER_PID, tid=LAUNCHER_PID, buf="Bitmap Memory", cnt=150)
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=2500, pid=LAUNCHER_PID, tid=LAUNCHER_PID, buf="Bitmap Memory", cnt=250)

    # Add counter to third random process
    builder.add_ftrace_packet(cpu=0)
    builder.add_atrace_counter(ts=1000, pid=THIRD_PROCESS_PID, tid=THIRD_PROCESS_PID, buf="Bitmap Memory", cnt=150)

    return builder.trace.SerializeToString()