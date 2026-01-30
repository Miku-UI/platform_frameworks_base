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

SF_PID = 6000
SF_TID_1 = 6020
SF_TID_2 = 6040
SYSUI_PID = 5000
SYSUI_UI_TID = 5020

SF_PACKAGE = "/system/bin/surfaceflinger"
SYSUI_PACKAGE = "com.android.systemui"

SF_UID = 10002
SF_RTID = 1655
SYSUI_UID = 10001
SYSUI_RTID = 1555

FIRST_CUJ = "J<FIRST_CUJ>"
SECOND_CUJ = "J<SECOND_CUJ>"

RENDER_ENGINE_TRACK_NAME = "RenderEngine"
REGION_SAMPLING_TRACK_NAME = "RegionSampling"
RENDER_ENGINE_TRACK_ID = 400
REGION_SAMPLING_TRACK_ID = 500
THREAD_TRACK_1 = 101
THREAD_TRACK_2 = 201

MAIN_SLICE_RENDER_ENGINE = "REThreaded::drawLayers"
INTERNAL_SLICE_RENDER_ENGINE = "drawLayersInternal for RegionSampling"
WAIT_FOREVER_SLICE = "waitForever"

LAYER_1 = "TX - first_layer#0"

def setup_trace():
    trace = trace_proto_builder.TraceProtoBuilder(Trace())
    add_process(trace, package_name=SYSUI_PACKAGE, uid=SYSUI_UID, pid=SYSUI_PID)
    add_process(trace, package_name=SF_PACKAGE, uid=SF_UID, pid=SF_PID)
    trace.add_ftrace_packet(cpu=0)
    return trace

def add_slices_and_track(trace):
    # Add RenderEngine and a RegionSampling track to the SF process through 2 separate threads
    trace.add_process_track_descriptor(
        process_track=RENDER_ENGINE_TRACK_ID,
        track_name=RENDER_ENGINE_TRACK_NAME,
        pid=SF_TID_1,
        process_name=SF_PACKAGE,
    )
    trace.add_thread_track_descriptor(
        process_track=RENDER_ENGINE_TRACK_ID,
        thread_track=THREAD_TRACK_1,
        pid=SF_TID_1,
        tid=SF_TID_1,
        thread_name=RENDER_ENGINE_TRACK_NAME,
    )
    trace.add_process_track_descriptor(
        process_track=REGION_SAMPLING_TRACK_ID,
        track_name=REGION_SAMPLING_TRACK_NAME,
        pid=SF_TID_2,
        process_name=SF_PACKAGE,
    )
    trace.add_thread_track_descriptor(
        process_track=REGION_SAMPLING_TRACK_ID,
        thread_track=THREAD_TRACK_2,
        pid=SF_TID_2,
        tid=SF_TID_2,
        thread_name=REGION_SAMPLING_TRACK_NAME,
    )

    # Add 2 instances of drawLayers slice to the render engine thread and waitForever slice to the regionSampling thread during first CUJ duration.
    trace.add_track_event_slice(
        name=MAIN_SLICE_RENDER_ENGINE,
        ts=27_000_000,
        dur=12_000_000,
        track=THREAD_TRACK_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=INTERNAL_SLICE_RENDER_ENGINE,
        ts=27_500_000,
        dur=11_500_000,
        track=THREAD_TRACK_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=WAIT_FOREVER_SLICE,
        ts=39_500_000,
        dur=10_000_000,
        track=THREAD_TRACK_2,
        trusted_sequence_id=100,
    )

    trace.add_track_event_slice(
        name=MAIN_SLICE_RENDER_ENGINE,
        ts=55_000_000,
        dur=12_000_000,
        track=THREAD_TRACK_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=INTERNAL_SLICE_RENDER_ENGINE,
        ts=55_500_000,
        dur=11_500_000,
        track=THREAD_TRACK_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=WAIT_FOREVER_SLICE,
        ts=67_500_000,
        dur=14_000_000,
        track=THREAD_TRACK_2,
        trusted_sequence_id=100,
    )

    # Add drawLayers slice to the render engine thread and waitForever slice to the regionSampling thread during second CUJ duration.
    trace.add_track_event_slice(
        name=MAIN_SLICE_RENDER_ENGINE,
        ts=86_000_000,
        dur=12_000_000,
        track=THREAD_TRACK_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=INTERNAL_SLICE_RENDER_ENGINE,
        ts=86_500_000,
        dur=11_500_000,
        track=THREAD_TRACK_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=WAIT_FOREVER_SLICE,
        ts=98_500_000,
        dur=14_000_000,
        track=THREAD_TRACK_2,
        trusted_sequence_id=100,
    )
    return trace

def add_process(trace, package_name, uid, pid):
    trace.add_package_list(ts=0, name=package_name, uid=uid, version_code=1)
    trace.add_process(pid=pid, ppid=pid, cmdline=package_name, uid=uid)
    trace.add_thread(tid=pid, tgid=pid, cmdline="MainThread", name="MainThread")

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

def add_actual_surface_frame_events(trace, ts, dur, token, layer, pid):
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
        jank_type=1,
        prediction_type=3,
        layer_name=layer,
    )
    trace.add_frame_end_event(ts=ts + dur, cookie=100002 + cookie)

def add_cujs(trace, cuj_name_1, cuj_name_2):
    # add 2 new CUJs with specified names in trace.
    trace.add_async_atrace_for_thread(
        ts=25_000_000,
        ts_end=77_000_000,
        buf=cuj_name_1,
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_async_atrace_for_thread(
        ts=83_000_000,
        ts_end=120_000_000,
        buf=cuj_name_2,
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_atrace_instant(
        ts=25_000_001,
        buf=cuj_name_1 + "#UIThread",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_atrace_instant(
        ts=83_000_001,
        buf=cuj_name_2 + "#UIThread",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_atrace_instant_for_track(
        ts=25_000_001,
        buf="FT#beginVsync#20",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name_1,
    )

    trace.add_atrace_instant_for_track(
        ts=25_000_010,
        buf="FT#layerId#0",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name_1,
    )

    trace.add_atrace_instant_for_track(
        ts=76_000_001,
        buf="FT#endVsync#30",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name_1,
    )

    trace.add_atrace_instant_for_track(
        ts=83_000_001,
        buf="FT#beginVsync#65",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name_2,
    )

    trace.add_atrace_instant_for_track(
        ts=83_000_010,
        buf="FT#layerId#0",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name_2,
    )

    trace.add_atrace_instant_for_track(
        ts=119_000_001,
        buf="FT#endVsync#70",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name_2,
    )

    # Add Choreographer#doFrame slices within CUJ boundary.
    trace.add_frame(
        vsync=20,
        ts_do_frame=26_000_000,
        ts_end_do_frame=32_000_000,
        tid=SYSUI_UI_TID,
        pid=SYSUI_PID,
    )

    trace.add_atrace_for_thread(
        ts=27_000_000,
        ts_end=28_000_000,
        buf="DrawFrames 20",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    trace.add_frame(
        vsync=22,
        ts_do_frame=43_000_000,
        ts_end_do_frame=49_000_000,
        tid=SYSUI_UI_TID,
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
        tid=SYSUI_UI_TID,
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
        tid=SYSUI_UI_TID,
        pid=SYSUI_PID,
    )

    trace.add_atrace_for_thread(
        ts=85_000_000,
        ts_end=86_000_000,
        buf="DrawFrames 65",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    trace.add_frame(
        vsync=68,
        ts_do_frame=96_000_000,
        ts_end_do_frame=102_000_000,
        tid=SYSUI_UI_TID,
        pid=SYSUI_PID,
    )

    trace.add_atrace_for_thread(
        ts=97_000_000,
        ts_end=98_000_000,
        buf="DrawFrames 68",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    trace.add_frame(
        vsync=70,
        ts_do_frame=107_000_000,
        ts_end_do_frame=112_000_000,
        tid=SYSUI_UI_TID,
        pid=SYSUI_PID,
    )

    trace.add_atrace_for_thread(
        ts=107_000_000,
        ts_end=108_000_000,
        buf="DrawFrames 70",
        tid=SYSUI_RTID,
        pid=SYSUI_PID,
    )

    # Add expected and actual frames.
    add_expected_surface_frame_events(
        trace, ts=27_000_000, dur=16_000_000, token=20, pid=SYSUI_PID
    )
    add_actual_surface_frame_events(
        trace,
        ts=27_000_000,
        dur=7_000_000,
        token=20,
        layer=LAYER_1,
        pid=SYSUI_PID,
    )

    add_expected_surface_frame_events(
        trace, ts=44_000_000, dur=16_000_000, token=22, pid=SYSUI_PID
    )
    add_actual_surface_frame_events(
        trace,
        ts=44_000_000,
        dur=7_000_000,
        token=22,
        layer=LAYER_1,
        pid=SYSUI_PID,
    )

    add_expected_surface_frame_events(
        trace, ts=61_000_000, dur=16_000_000, token=24, pid=SYSUI_PID
    )
    add_actual_surface_frame_events(
        trace,
        ts=61_000_000,
        dur=6_000_000,
        token=24,
        layer=LAYER_1,
        pid=SYSUI_PID,
    )

    add_expected_surface_frame_events(
        trace, ts=84_000_000, dur=10_000_000, token=65, pid=SYSUI_PID
    )
    add_actual_surface_frame_events(
        trace,
        ts=84_000_000,
        dur=6_000_000,
        token=65,
        layer=LAYER_1,
        pid=SYSUI_PID,
    )

    add_expected_surface_frame_events(
        trace, ts=96_000_000, dur=10_000_000, token=68, pid=SYSUI_PID
    )
    add_actual_surface_frame_events(
        trace,
        ts=96_000_000,
        dur=6_000_000,
        token=68,
        layer=LAYER_1,
        pid=SYSUI_PID,
    )

    add_expected_surface_frame_events(
        trace, ts=107_000_000, dur=10_000_000, token=70, pid=SYSUI_PID
    )
    add_actual_surface_frame_events(
        trace,
        ts=107_000_000,
        dur=6_000_000,
        token=70,
        layer=LAYER_1,
        pid=SYSUI_PID,
    )

def get_proto():
    trace = setup_trace()
    add_cujs(trace, FIRST_CUJ, SECOND_CUJ)
    builder = add_slices_and_track(trace)
    return builder.trace.SerializeToString()
