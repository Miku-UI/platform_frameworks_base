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

from metrics_specs.tests.utils import android_bitmap_metric_trace
from metrics_specs.tests.utils import android_sf_critical_work_main_thread_trace
from metrics_specs.tests.utils import android_dmabuf_per_process_metric_trace
from metrics_specs.tests.utils import android_sf_critical_work_region_sampling_trace
from metrics_specs.tests.utils import android_gralloc_buffers_per_process_metric_trace
from metrics_specs.tests.utils import test_helper
import unittest

class MetricsV2Test(unittest.TestCase):
    def setUp(self):
        super().setUp()
        self.helper = test_helper.TestHelper(self)

    def test_android_bitmap_metric(self):
        self.helper.verify_metric(
            spec_file="android_bitmap_metric.textproto",
            trace_proto_bytes = android_bitmap_metric_trace.get_proto(),
            expected_output_file = "android_bitmap_metric_output.txt",
            metric_ids = [
                "android_bitmap_metric_min_val",
                "android_bitmap_metric_max_val",
                "android_bitmap_metric_avg_val",
            ]
        )

    def test_android_sf_critical_work_main_thread_metric(self):
        self.helper.verify_metric(
            spec_file="android_sf_critical_work_main_thread.textproto",
            trace_proto_bytes = android_sf_critical_work_main_thread_trace.get_proto(),
            expected_output_file = "android_sf_critical_work_main_thread_output.txt",
            metric_ids = [
                "android_sf_critical_work_main_thread_cuj_max_dur",
                "android_sf_critical_work_main_thread_cuj_avg_dur",
                "android_sf_critical_work_main_thread_cuj_count",
            ]
        )

    def test_android_dmabuf_per_process_metric(self):
        self.helper.verify_metric(
            spec_file="android_dmabuf_per_process_metric.textproto",
            trace_proto_bytes = android_dmabuf_per_process_metric_trace.get_proto(),
            expected_output_file = "android_dmabuf_per_process_metric_output.txt",
            metric_ids = [
                "android_dmabuf_per_process_metric_min_val",
                "android_dmabuf_per_process_metric_max_val",
                "android_dmabuf_per_process_metric_avg_val",
            ]
        )

    def test_android_sf_critical_work_region_sampling_metric(self):
        self.helper.verify_metric(
            spec_file="android_sf_critical_work_region_sampling.textproto",
             trace_proto_bytes = android_sf_critical_work_region_sampling_trace.get_proto(),
             expected_output_file = "android_sf_critical_work_region_sampling_output.txt",
             metric_ids = [
                 "android_sf_critical_work_region_sampling_cuj_max_dur",
                 "android_sf_critical_work_region_sampling_cuj_avg_dur",
                 "android_sf_critical_work_region_sampling_cuj_count",
             ]
        )

    def test_android_gralloc_buffers_per_process_metric(self):
        self.helper.verify_metric(
            spec_file="android_gralloc_buffers_per_process_metric.textproto",
            trace_proto_bytes = android_gralloc_buffers_per_process_metric_trace.get_proto(),
            expected_output_file = "android_gralloc_buffers_per_process_metric_output.txt",
            metric_ids = [
                "android_gralloc_buffers_per_process_metric_min_val",
                "android_gralloc_buffers_per_process_metric_max_val",
                "android_gralloc_buffers_per_process_metric_avg_val",
            ]
        )

if __name__ == '__main__':
    unittest.main()
