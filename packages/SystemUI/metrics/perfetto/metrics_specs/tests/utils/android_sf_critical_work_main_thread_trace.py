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

SYSUI_PID = 5000
SYSUI_UI_TID = 5020
SF_PID = 6000
SF_TID = 6020
RANDOM_PROCESS_PID = 7000
RANDOM_PROCESS_TID = 7020

SYSUI_PACKAGE = "com.android.systemui"
SF_PACKAGE = "/system/bin/surfaceflinger"
RANDOM_PROCESS_PACKAGE = "random_process"

SYSUI_UID = 10001
SF_UID = 10002
RANDOM_PROCESS_UID = 10003
SYSUI_RTID = 1555
SF_RTID = 1655
RANDOM_PROCESS_RTID = 1755

FIRST_CUJ = "J<BACK_PANEL_ARROW>"

TRACK_NAME_1 = "CriticalWorkload"
TRACK_ID_1 = 400
TRACK_NAME_2 = "random_track"
TRACK_ID_2 = 500
TRACK_ID_3 = 600

COMMIT_SLICE = "Commit"
COMPOSITION_SLICE = "Composition"
POST_COMPOSITION_SLICE = "Post Composition"
TRANSACTIONAL_HANDLING_SLICE = "Transaction Handling"
REFRESH_RATE_SELECTION_SLICE = "Refresh Rate Selection"

LAYER_1 = "TX - first_layer#0"

def setup_trace():
    trace = trace_proto_builder.TraceProtoBuilder(Trace())
    add_process(trace, package_name=SYSUI_PACKAGE, uid=SYSUI_UID, pid=SYSUI_PID)
    add_process(trace, package_name=SF_PACKAGE, uid=SF_UID, pid=SF_PID)
    trace.add_ftrace_packet(cpu=0)
    return trace

def add_slices_and_track(trace):
    # Add CriticalWorkload and a random track to the SF process
    trace.add_process_track_descriptor(
        process_track=TRACK_ID_1,
        track_name=TRACK_NAME_1,
        pid=SF_PID,
        process_name=SF_PACKAGE,
    )
    trace.add_process_track_descriptor(
        process_track=TRACK_ID_2,
        track_name=TRACK_NAME_2,
        pid=SF_PID,
        process_name=SF_PACKAGE,
    )

    # Add slices within cuj duration to the CriticalWorkload track in SF process
    trace.add_track_event_slice(
        name=COMMIT_SLICE,
        ts=27_000_000,
        dur=12_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=TRANSACTIONAL_HANDLING_SLICE,
        ts=27_500_000,
        dur=5_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=REFRESH_RATE_SELECTION_SLICE,
        ts=34_000_000,
        dur=3_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=COMPOSITION_SLICE,
        ts=39_000_000,
        dur=4_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=POST_COMPOSITION_SLICE,
        ts=43_000_000,
        dur=3_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )

    trace.add_track_event_slice(
        name=COMMIT_SLICE,
        ts=84_000_000,
        dur=2_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )
    trace.add_track_event_slice(
        name=COMPOSITION_SLICE,
        ts=86_000_000,
        dur=3_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )

    # Add Post Composition slice outside the CUJ duration ( should not be tracked )
    trace.add_track_event_slice(
        name=POST_COMPOSITION_SLICE,
        ts=89_000_000,
        dur=3_000_000,
        track=TRACK_ID_1,
        trusted_sequence_id=100,
    )

    # Add slices to the random track ( should not be tracked)
    trace.add_track_event_slice(
        name=COMMIT_SLICE,
        ts=27_000_000,
        dur=12_000_000,
        track=TRACK_ID_2,
        trusted_sequence_id=100,
    )

    # Add CriticalWorkload track to the random process
    trace.add_process_track_descriptor(
        process_track=TRACK_ID_3,
        track_name=TRACK_NAME_1,
        pid=RANDOM_PROCESS_PID,
        process_name=RANDOM_PROCESS_PACKAGE,
    )

    # Add slices within cuj duration to the CriticalWorkload track in random process ( should not be tracked )
    trace.add_track_event_slice(
        name=COMMIT_SLICE,
        ts=27_000_000,
        dur=12_000_000,
        track=TRACK_ID_3,
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

def add_cuj(trace, cuj_name):
    # Add 2 CUJs in the trace with the specified cuj_name.

    trace.add_async_atrace_for_thread(
        ts=25_000_000,
        ts_end=77_000_000,
        buf=cuj_name,
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_async_atrace_for_thread(
        ts=83_000_000,
        ts_end=102_000_000,
        buf=cuj_name,
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_atrace_instant(
        ts=25_000_001,
        buf=cuj_name + "#UIThread",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_atrace_instant(
        ts=83_000_001,
        buf=cuj_name + "#UIThread",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
    )

    trace.add_atrace_instant_for_track(
        ts=25_000_001,
        buf="FT#beginVsync#20",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name,
    )

    trace.add_atrace_instant_for_track(
        ts=25_000_010,
        buf="FT#layerId#0",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name,
    )

    trace.add_atrace_instant_for_track(
        ts=76_000_001,
        buf="FT#endVsync#30",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name,
    )

    trace.add_atrace_instant_for_track(
        ts=83_000_001,
        buf="FT#beginVsync#65",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name,
    )

    trace.add_atrace_instant_for_track(
        ts=83_000_010,
        buf="FT#layerId#0",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name,
    )

    trace.add_atrace_instant_for_track(
        ts=101_000_001,
        buf="FT#endVsync#70",
        pid=SYSUI_PID,
        tid=SYSUI_UI_TID,
        track_name=cuj_name,
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
        ts_end_do_frame=65_000_000,
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

def get_proto():
    trace = setup_trace()
    add_cuj(trace, FIRST_CUJ)
    builder = add_slices_and_track(trace)
    return builder.trace.SerializeToString()
