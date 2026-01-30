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

# The content of this file is copied from external/perfetto/test/synth_common.py
from typing import Optional
from perfetto.protos.perfetto.trace.perfetto_trace_pb2 import Trace

TYPE_SLICE_BEGIN = 1
TYPE_SLICE_END = 2

class TraceProtoBuilder(object):
    """Helper class to build a trace proto for testing."""

    def __init__(self, trace):
        self.trace = trace
        self.proc_map = {}
        self.proc_map[0] = 'idle_thread'

    def add_ftrace_packet(self, cpu: int):
        """Adds a new packet with ftrace events for a specific CPU."""
        self.packet = self.trace.packet.add()
        self.packet.ftrace_events.cpu = cpu

    def add_packet(self, ts: Optional[int] = None):
        """Adds a new generic packet to the trace."""
        self.packet = self.trace.packet.add()
        if ts is not None:
            self.packet.timestamp = ts
        return self.packet

    def add_ftrace_event(self, ts: int, tid: int):
        ftrace = self.packet.ftrace_events.event.add()
        ftrace.timestamp = ts
        ftrace.pid = tid
        return ftrace

    def add_package_list(self, ts: int, name: str, uid: int, version_code: int):
        """Adds a package list packet to the trace.

        Args:
            ts: Timestamp for the package list packet.
            name: The package name (e.g., com.google.android.apps.example).
            uid: The User ID associated with the package.
            version_code: The version code of the package.
        """
        packet = self.add_packet()
        packet.timestamp = ts
        plist = packet.packages_list
        pinfo = plist.packages.add()
        pinfo.name = name
        pinfo.uid = uid
        pinfo.version_code = version_code

    def add_process(
        self, pid: int, ppid: int, cmdline: str, uid: Optional[int] = None
    ):
        """Adds a process to the process tree in the current packet.

        Args:
            pid: Process ID.
            ppid: Parent Process ID.
            cmdline: The command line or name of the process.
            uid: Optional User ID of the process.
        """
        process = self.packet.process_tree.processes.add()
        process.pid = pid
        process.ppid = ppid
        process.cmdline.append(cmdline)
        if uid is not None:
            process.uid = uid
        self.proc_map[pid] = cmdline

    def add_thread(self, tid: int, tgid: int, cmdline: str, name: Optional[str] = None):
        """
        Adds a thread to the process tree in the current packet.

        Args:
            tid: Thread ID.
            ppid: Parent thread ID.
            cmdline: The command line or name of the thread.
            name: Optional name of the thread.
        """
        thread = self.packet.process_tree.threads.add()
        thread.tid = tid
        thread.tgid = tgid
        if name is not None:
          thread.name = name
        self.proc_map[tid] = cmdline

    def add_print(self, ts: int, tid: int, buf: str):
        """Adds an ftrace print event to the current ftrace packet.

        Args:
            ts: Timestamp of the event.
            tid: Thread ID of the event.
            buf: The content of the print buffer.
        """
        ftrace = self.add_ftrace_event(ts, tid)
        print_event = getattr(ftrace, 'print')
        print_event.buf = buf

    def add_atrace_counter(
        self, ts: int, pid: int, tid: int, buf: str, cnt: int
    ):
        """Adds new counter track to the trace via an ftrace print event."""
        self.add_print(ts, tid, 'C|{}|{}|{}'.format(pid, buf, cnt))

    def add_atrace_begin(self, ts: int, tid: int, pid: int, buf: str):
        """Adds new atrace event begin packet to the trace via an ftrace print event."""
        self.add_print(ts, tid, 'B|{}|{}'.format(pid, buf))

    def add_atrace_end(self, ts: int, tid: int, pid: int):
        """Adds new atrace event end packet to the trace via an ftrace print event."""
        self.add_print(ts, tid, 'E|{}'.format(pid))

    def add_atrace_async_begin(self, ts: int, tid: int, pid: int, buf: str):
        """Adds an asynchronous atrace begin event to mark the start of a timed event.

        Args:
            ts: The timestamp of the event's start.
            tid: The thread ID where the event began.
            pid: The process ID where the event began.
            buf: The content of the event's buffer, typically a descriptive
              name.
        """
        self.add_print(ts, tid, 'S|{}|{}|0'.format(pid, buf))

    def add_atrace_async_end(self, ts: int, tid: int, pid: int, buf: str):
        """Adds an asynchronous atrace end event to mark the completion of a timed event.

        Args:
            ts: The timestamp of the event's end.
            tid: The thread ID where the event ended.
            pid: The process ID where the event ended.
            buf: The content of the event's buffer, typically a descriptive
              name.
        """
        self.add_print(ts, tid, 'F|{}|{}|0'.format(pid, buf))

    def add_atrace_instant(self, ts: int, tid: int, pid: int, buf: str):
        """Adds an instant atrace event, which represents a single point in time.

        Args:
            ts: The timestamp of the instant event.
            tid: The thread ID where the event occurred.
            pid: The process ID where the event occurred.
            buf: The content of the event's buffer.
        """
        self.add_print(ts, tid, 'I|{}|{}'.format(pid, buf))

    def add_atrace_instant_for_track(
        self, ts: int, tid: int, pid: int, track_name: str, buf: str
    ):
        """Adds an "instant" atrace event associated with a specific track.

        Args:
            ts: The timestamp of the instant event.
            tid: The thread ID where the event occurred.
            pid: The process ID where the event occurred.
            track_name: The name of the track to associate the event with.
            buf: The content of the event's buffer.
        """
        self.add_print(ts, tid, 'N|{}|{}|{}'.format(pid, track_name, buf))

    def add_atrace_for_thread(
        self, ts: int, ts_end: int, buf: str, tid: int, pid: int
    ):
        """Adds new async event packet to the trace via an ftrace print event."""
        self.add_atrace_begin(ts=ts, tid=tid, pid=pid, buf=buf)
        self.add_atrace_end(ts=ts_end, tid=tid, pid=pid)

    def add_async_atrace_for_thread(
        self, ts: int, ts_end: int, buf: str, tid: int, pid: int
    ):
        """Adds a pair of asynchronous atrace events (begin and end) for a thread.

        Args:
            ts: The timestamp of the event's start.
            ts_end: The timestamp of the event's end.
            buf: The content of the event's buffer, typically a description.
            tid: The thread ID associated with the events.
            pid: The process ID associated with the events.
        """
        self.add_atrace_async_begin(ts=ts, tid=tid, pid=pid, buf=buf)
        self.add_atrace_async_end(ts=ts_end, tid=tid, pid=pid, buf=buf)

    def add_frame(
        self,
        vsync: int,
        ts_do_frame: int,
        ts_end_do_frame: int,
        tid: int,
        pid: int,
    ):
        """Adds a Choreographer frame event to the trace.

        Args:
            vsync: The vsync count associated with this frame.
            ts_do_frame: The start timestamp of the frame's execution.
            ts_end_do_frame: The end timestamp of the frame's execution.
            tid: The thread ID where the frame event occurred.
            pid: The process ID where the frame event occurred.
        """
        self.add_atrace_for_thread(
            ts=ts_do_frame,
            ts_end=ts_end_do_frame,
            buf='Choreographer#doFrame %d' % vsync,
            tid=tid,
            pid=pid,
        )

    def add_process(
        self, pid: int, ppid: int, cmdline: str, uid: Optional[int] = None
    ):
        """Adds a process to the process tree in the current packet.

        Args:
            pid: Process ID.
            ppid: Parent Process ID.
            cmdline: The command line or name of the process.
            uid: Optional User ID of the process.
        """
        process = self.packet.process_tree.processes.add()
        process.pid = pid
        process.ppid = ppid
        process.cmdline.append(cmdline)
        if uid is not None:
            process.uid = uid
        self.proc_map[pid] = cmdline

    def add_thread(
        self, tid: int, tgid: int, cmdline: str, name: Optional[str] = None
    ):
        """Adds a thread to the process tree in the current packet.

        Args:
            tid: The thread ID.
            tgid: The thread group ID.
            cmdline: The command line of the process.
            name: Name of the thread.
        """
        thread = self.packet.process_tree.threads.add()
        thread.tid = tid
        thread.tgid = tgid
        if name is not None:
            thread.name = name
        self.proc_map[tid] = cmdline

    def add_package_list(self, ts: int, name: str, uid: int, version_code: int):
        """Adds a package list packet to the trace.

        Args:
            ts: Timestamp for the package list packet.
            name: The package name (e.g., com.google.android.apps.example).
            uid: The User ID associated with the package.
            version_code: The version code of the package.
        """
        packet = self.add_packet()
        packet.timestamp = ts
        plist = packet.packages_list
        pinfo = plist.packages.add()
        pinfo.name = name
        pinfo.uid = uid
        pinfo.version_code = version_code

    def add_track_event(
        self,
        ts: int,
        trusted_sequence_id: int,
        name: Optional[str] = None,
        track: Optional[int] = None,
        cpu_time: Optional[int] = None,
    ):
        """Adds a new track event to the current packet

        Args:
            ts: Timestamp of the event to be added
            name: Optional name of the event
            track: Optional track where event has to be added
            cpu_time: Optional timestamp that represents the CPU time at which
              the event occurred
        """
        packet = self.add_packet(ts=ts)
        if name is not None:
            packet.track_event.name = name
        if track is not None:
            packet.track_event.track_uuid = track
        packet.trusted_packet_sequence_id = trusted_sequence_id
        if cpu_time is not None:
            packet.track_event.extra_counter_values.append(cpu_time)
        return packet

    def add_track_descriptor(self, uuid: int, parent: Optional[int] = None):
        """Adds a new track to the trace to the current packet

        Args:
            uuid: Unique user id of the track
            parent: Optional parent identifier of the track
        """
        packet = self.add_packet()
        track_descriptor = packet.track_descriptor
        if uuid is not None:
            track_descriptor.uuid = uuid
        if parent is not None:
            track_descriptor.parent_uuid = parent
        return packet

    def add_process_track_descriptor(
        self,
        process_track: int,
        track_name: Optional[str] = None,
        pid: Optional[int] = None,
        process_name: Optional[str] = None,
    ):
        """Adds a new track to a process of current packet

        Args:
            process_track: Unique ID of the track to be added
            track_name: Optional name of the trac to be added
            pid: Optional Parent Id
            process_name: Optional process name to which track has to be added
        """
        packet = self.add_track_descriptor(process_track)
        if pid is not None:
            packet.track_descriptor.process.pid = pid
        if process_name is not None:
            packet.track_descriptor.process.process_name = process_name
        if track_name is not None:
            packet.track_descriptor.name = track_name
        return packet

    def add_thread_track_descriptor(
        self,
        process_track: int,
        thread_track: int,
        trusted_packet_sequence_id: Optional[int] = None,
        pid: Optional[int] = None,
        tid: Optional[int] = None,
        thread_name: Optional[str] = None,
    ):
        """Adds a thread track descriptor to the current packet.

        Args:
            process_track: The ID of the parent process track.
            thread_track: The ID of the new thread track.
            trusted_packet_sequence_id: Optional, trusted packet sequence ID.
            pid: Optional, process ID of the thread.
            tid: Optional,thread ID of the thread to be added.
            thread_name: Optional, name of the thread to be added.

        Returns:
            The packet containing the newly added track descriptor.
        """
        packet = self.add_track_descriptor(thread_track, parent=process_track)
        if trusted_packet_sequence_id is not None:
            packet.trusted_packet_sequence_id = trusted_packet_sequence_id
        if pid is not None:
            packet.track_descriptor.thread.pid = pid
        if tid is not None:
            packet.track_descriptor.thread.tid = tid
        if thread_name is not None:
            packet.track_descriptor.thread.thread_name = thread_name
        return packet

    def add_track_event_slice_begin(
        self,
        name: str,
        ts: int,
        trusted_sequence_id: int,
        track: Optional[int] = None,
        cpu_time: Optional[int] = None,
    ):
        """Adds a new slice start packet to the track

        Args:
            name: Name of the slice to be added
            ts: Time stamp of the slice
            track: Optional name of the track to which slice has to be added
            trusted_sequence_id: Unique identifier that provides a sequence
              number for events originating from a single source
            cpu_time: Optional timestamp that represents the CPU time at which
              the event occurred
        """
        packet = self.add_track_event(
            name=name,
            ts=ts,
            track=track,
            trusted_sequence_id=trusted_sequence_id,
            cpu_time=cpu_time,
        )
        packet.track_event.type = TYPE_SLICE_BEGIN
        return packet

    def add_track_event_slice_end(
        self,
        ts: int,
        trusted_sequence_id: int,
        track: Optional[int] = None,
        cpu_time: Optional[int] = None,
    ):
        """Adds a new slice end packet to the track

        Args:
            ts: Time stamp of the slice
            track: Optional name of the track to which slice has to be added
            trusted_sequence_id: Unique identifier that provides a sequence
              number for events originating from a single source.
            cpu_time: Optional timestamp that represents the CPU time at which
              the event occurred.
        """
        packet = self.add_track_event(
            ts=ts,
            track=track,
            trusted_sequence_id=trusted_sequence_id,
            cpu_time=cpu_time,
        )
        packet.track_event.type = TYPE_SLICE_END
        return packet

    def add_track_event_slice(
        self,
        name: str,
        ts: int,
        dur: int,
        trusted_sequence_id: int,
        track: Optional[int] = None,
        cpu_start: Optional[int] = None,
        cpu_delta: Optional[int] = None,
    ):
        """Adds a new slice to the track

        Args:
            name: Name of the slice to be added
            ts: Time stamp of the slice
            dur: Duration of the slice
            track: Optional name of the track to which slice has to be added
            trusted_sequence_id: A unique identifier that provides a sequence
              number for events originating from a single source.
            cpu_time: Optional timestamp that represents the CPU time at which
              the event occurred.
            cpu_time: Optional timestamp that represents the CPU time at which
              the event occurred.
            cpu_delta: Optional duration that represents the duration of cpu
              time consumed by the slice
        """
        packet = self.add_track_event_slice_begin(
            name,
            ts,
            track=track,
            trusted_sequence_id=trusted_sequence_id,
            cpu_time=cpu_start,
        )

        if dur >= 0:
            cpu_end = cpu_start + cpu_delta if cpu_start is not None else None
            self.add_track_event_slice_end(
                ts + dur,
                track=track,
                trusted_sequence_id=trusted_sequence_id,
                cpu_time=cpu_end,
            )

        return packet

    def add_expected_surface_frame_start_event(
        self,
        ts: int,
        cookie: int,
        token: int,
        display_frame_token: int,
        pid: int,
        layer_name: str,
    ):
        """Adds an expected surface frame start event to the current packet.

        Args:
            ts: Timestamp of the event.
            cookie: A unique identifier for the event.
            token: The token associated with the surface frame.
            display_frame_token: The token of the display frame.
            pid: The process ID of the surface.
            layer_name: The name of the layer associated with the frame.
        """
        packet = self.add_packet()
        packet.timestamp = ts
        event = packet.frame_timeline_event.expected_surface_frame_start
        if token != -1 and display_frame_token != -1:
            event.cookie = cookie
            event.token = token
            event.display_frame_token = display_frame_token
            event.pid = pid
            event.layer_name = layer_name

    def add_actual_surface_frame_start_event(
        self,
        ts: int,
        cookie: int,
        token: int,
        display_frame_token: int,
        pid: int,
        layer_name: str,
        present_type: int,
        on_time_finish: int,
        gpu_composition: int,
        jank_type: int,
        prediction_type: int,
        jank_severity_type: Optional[int] = None,
    ):
        """Adds an actual surface frame start event to the current packet.

        Args:
            ts: Timestamp of the event.
            cookie: A unique identifier for the event.
            token: The token associated with the surface frame.
            display_frame_token: The token of the display frame.
            pid: The process ID of the surface.
            layer_name: The name of the layer associated with the frame.
            present_type: The type of presentation for the frame.
            on_time_finish: Whether the frame finished on time (1 for true, 0
              for false).
            gpu_composition: Whether the frame used GPU composition (1 for true,
              0 for false).
            jank_type: The type of jank detected for the frame.
            prediction_type: The type of prediction used for the frame.
            jank_severity_type: Optional argumemt that signifies the severity of
              the jank.
        """
        packet = self.add_packet()
        packet.timestamp = ts
        event = packet.frame_timeline_event.actual_surface_frame_start
        if token != -1 and display_frame_token != -1:
            event.cookie = cookie
            event.token = token
            event.display_frame_token = display_frame_token
            event.pid = pid
            event.layer_name = layer_name
            event.present_type = present_type
            event.on_time_finish = on_time_finish
            event.gpu_composition = gpu_composition
            event.jank_type = jank_type
            # jank severity type is not available on every trace.
            # When not set, default to none if no jank; otherwise default to unknown
            if jank_severity_type is None:
                event.jank_severity_type = 1 if event.jank_type == 1 else 0
            else:
                event.jank_severity_type = jank_severity_type
            event.prediction_type = prediction_type

    def add_frame_end_event(self, ts: int, cookie: int):
        """Adds a frame end event to the current packet.

        Args:
            ts: The timestamp of the event.
            cookie: A unique identifier for the event.
        """
        packet = self.add_packet()
        packet.timestamp = ts
        event = packet.frame_timeline_event.frame_end
        event.cookie = cookie

    def add_binder_transaction(self, transaction_id: int, ts_start: int, ts_end: int, tid: int,
                                 pid: int, reply_id: int, reply_ts_start: int, reply_ts_end: int,
                                 reply_tid: int, reply_pid: int):
        """Adds binder transaction events to the trace.

        This method simulates a complete binder transaction, including the
        start of the transaction, the reply, and the end of both the
        transaction and the reply.

        Args:
            transaction_id: A unique identifier for the binder transaction.
            ts_start: Timestamp (in nanoseconds) when the transaction started.
            ts_end: Timestamp (in nanoseconds) when the transaction finished.
            tid: Thread ID of the caller.
            pid: Process ID of the caller.
            reply_id: A unique identifier for the binder reply.
            reply_ts_start: Timestamp (in nanoseconds) when the reply started.
            reply_ts_end: Timestamp (in nanoseconds) when the reply finished.
            reply_tid: Thread ID of the target process receiving the reply.
            reply_pid: Process ID of the target process receiving the reply.
        """
        # Binder transaction start.
        ftrace = self.add_ftrace_event(ts_start, tid)
        binder_transaction = ftrace.binder_transaction
        binder_transaction.debug_id = transaction_id
        binder_transaction.to_proc = reply_pid
        binder_transaction.to_thread = reply_tid
        binder_transaction.reply = False

        # Binder reply start
        ftrace = self.add_ftrace_event(reply_ts_start, reply_tid)
        binder_transaction_received = ftrace.binder_transaction_received
        binder_transaction_received.debug_id = transaction_id

        # Binder reply finish
        ftrace = self.add_ftrace_event(reply_ts_end, reply_tid)
        reply_binder_transaction = ftrace.binder_transaction
        reply_binder_transaction.debug_id = reply_id
        reply_binder_transaction.to_proc = pid
        reply_binder_transaction.to_thread = tid
        reply_binder_transaction.reply = True

        # Binder transaction finish
        ftrace = self.add_ftrace_event(ts_end, tid)
        reply_binder_transaction_received = ftrace.binder_transaction_received
        reply_binder_transaction_received.debug_id = reply_id
