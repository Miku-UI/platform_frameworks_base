#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project
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

SYSUI_PID = 5000
SYSUI_PACKAGE = "com.android.systemui"
SYSUI_UID = 10001
SYSUI_UI_TID = SYSUI_PID
SYSUI_RTID = 1555

LAYER_1 = "TX - first_layer#0"
FIRST_CUJ = "J<NOTIFICATION_SHADE_EXPAND_COLLAPSE::Expand>"

# Jank types selected for testing - for full list see FrameTimelineEvent.JankType enum defined in
# external/cronet/stable/third_party/perfetto/protos/perfetto/trace/android
# /frame_timeline_event.proto
JANK_NONE = 1
JANK_SF_SCHEDULING = 2
JANK_APP_DEADLINE_MISSED = 64
JANK_DROPPED = 1024

def add_process(trace, package_name, uid, pid):
    trace.add_package_list(ts=0, name=package_name, uid=uid, version_code=1)
    trace.add_process(pid=pid, ppid=pid, cmdline=package_name, uid=uid)
    trace.add_thread(tid=pid, tgid=pid, cmdline="MainThread", name="MainThread")

def setup_trace():
    trace = trace_proto_builder.TraceProtoBuilder(Trace())
    trace.add_packet()

    add_process(trace, package_name=SYSUI_PACKAGE, uid=SYSUI_UID, pid=SYSUI_PID)

    trace.add_ftrace_packet(cpu=0)
    trace.add_async_atrace_for_thread(
            ts=0, ts_end=5, buf="J<IGNORED>", tid=SYSUI_PID, pid=SYSUI_PID)
    return trace

def add_cuj(trace, cuj_name):
    cuj_begin = 27_000_000
    cuj_end = 90_000_000
    blocking_call_name = "binder transaction"
    pid = SYSUI_PID

    # Add the CUJ track (i.e. J<*>) with its own slice.
    trace.add_async_atrace_for_thread(ts=cuj_begin,
        ts_end=cuj_end, buf=cuj_name, tid=pid, pid=pid)

    # Add the instant events that come with the CUJ.
    trace.add_atrace_instant(
        ts=cuj_begin + 1, buf=cuj_name + "#UIThread", pid=pid, tid=pid)
    trace.add_atrace_instant_for_track(
        ts=cuj_begin + 2,
        buf="FT#beginVsync#20",
        pid=pid,
        tid=pid,
        track_name=cuj_name)
    trace.add_atrace_instant_for_track(
        ts=cuj_begin + 10,
        buf="FT#layerId#0",
        pid=pid,
        tid=pid,
        track_name=cuj_name)
    trace.add_atrace_instant_for_track(
        ts=cuj_end - 1,
        buf="FT#endVsync#65",
        pid=pid,
        tid=pid,
        track_name=cuj_name)
    trace.add_atrace_instant_for_track(
        ts=cuj_end,
        buf="FT#end#0",
        pid=pid,
        tid=pid,
        track_name=cuj_name)



def add_frametimeline(trace, cuj_name):
    # Add expected frames.
    add_expected_surface_frame_events(
        trace,
        ts=27_000_000,
        dur=6_000_000,
        token=20,
        pid=SYSUI_PID,
    )
    add_expected_surface_frame_events(
        trace,
        ts=44_000_000,
        dur=6_000_000,
        token=22,
        pid=SYSUI_PID,
    )
    add_expected_surface_frame_events(
        trace,
        ts=61_000_000,
        dur=5_000_000,
        token=24,
        pid=SYSUI_PID,
    )
    add_expected_surface_frame_events(
        trace,
        ts=84_000_000,
        dur=5_000_000,
        token=65,
        pid=SYSUI_PID,
    )

    # Add four actual frames with jank types in the given order:
    # missed, sf_missed, sf_missed, app_missed.
    add_actual_surface_frame_events(
        trace,
        ts=27_000_000,
        dur=7_000_000,
        token=20,
        layer=LAYER_1,
        pid=SYSUI_PID,
        jank_type=JANK_DROPPED,
    )
    add_actual_surface_frame_events(
        trace,
        ts=44_000_000,
        dur=7_000_000,
        token=22,
        layer=LAYER_1,
        pid=SYSUI_PID,
        jank_type=JANK_SF_SCHEDULING,
    )
    add_actual_surface_frame_events(
        trace,
        ts=61_000_000,
        dur=6_000_000,
        token=24,
        layer=LAYER_1,
        pid=SYSUI_PID,
        jank_type=JANK_SF_SCHEDULING,
    )
    add_actual_surface_frame_events(
        trace,
        ts=84_000_000,
        dur=6_000_000,
        token=65,
        layer=LAYER_1,
        pid=SYSUI_PID,
        jank_type=JANK_APP_DEADLINE_MISSED,
    )

def add_choreographer_and_draw_frames(trace):
    # Choreographer#doFrame() goes on the MainThread
    # and DrawFrame events go on render thread.
    trace.add_frame(
        vsync=20,
        ts_do_frame=28_000_000,
        ts_end_do_frame=32_000_000,
        tid=SYSUI_PID,
        pid=SYSUI_PID,
    )
    trace.add_atrace_for_thread(
        ts=29_000_000,
        ts_end=31_000_000,
        buf="DrawFrames 20",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    trace.add_frame(
        vsync=22,
        ts_do_frame=43_000_000,
        ts_end_do_frame=49_000_000,
        tid=SYSUI_PID,
        pid=SYSUI_PID,
    )
    trace.add_atrace_for_thread(
        ts=44_000_000,
        ts_end=45_000_000,
        buf="DrawFrames 22",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    trace.add_frame(
        vsync=24,
        ts_do_frame=60_000_000,
        ts_end_do_frame=75_000_000,
        tid=SYSUI_PID,
        pid=SYSUI_PID,
    )
    trace.add_atrace_for_thread(
        ts=61_000_000,
        ts_end=62_000_000,
        buf="DrawFrames 24",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    trace.add_frame(
        vsync=65,
        ts_do_frame=84_000_000,
        ts_end_do_frame=89_000_000,
        tid=SYSUI_PID,
        pid=SYSUI_PID,
    )
    trace.add_atrace_for_thread(
        ts=85_000_000,
        ts_end=86_000_000,
        buf="DrawFrames 65",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

def add_actual_surface_frame_events(trace, ts, dur, token, layer, pid, jank_type=JANK_NONE):
    cookie = token + 1
    trace.add_actual_surface_frame_start_event(
        ts=ts,
        cookie=100002 + cookie,
        token=token,
        display_frame_token=token + 100,
        pid=pid,
        present_type=1,
        on_time_finish=1,
        gpu_composition=0,
        jank_type=jank_type,
        prediction_type=3,
        layer_name=layer,
    )
    trace.add_frame_end_event(ts=ts + dur, cookie=100002 + cookie)

def add_expected_surface_frame_events(trace, ts, dur, token, pid):
    trace.add_expected_surface_frame_start_event(
        ts=ts,
        cookie=100000 + token,
        token=token,
        display_frame_token=100 + token,
        pid=pid,
        layer_name="",
    )
    trace.add_frame_end_event(ts=ts + dur, cookie=100000 + token)

# Generates a synthetic trace;
# see https://ui.perfetto.dev/#!/?s=7aefcb528384961948f0f6bc48a8c2bc54a827f2 to view an example.
def get_proto():
    trace = setup_trace()
    add_cuj(trace, FIRST_CUJ)
    add_choreographer_and_draw_frames(trace)
    add_frametimeline(trace, FIRST_CUJ)
    return trace.trace.SerializeToString()
