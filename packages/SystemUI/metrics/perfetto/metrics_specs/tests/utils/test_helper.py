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

from google.protobuf import text_format
from os.path import dirname as parent
from perfetto.trace_processor import TraceProcessor,TraceProcessorConfig
import io
import os
import stat
import unittest

class TestHelper():
    """
    Helper class for metric tests.

    This class provides utility methods for verifying the output of a metric
    against a given output file.
    """
    def __init__(self, test_case):
        self.test_case = test_case

    def verify_metric(self, spec_file: str, trace_proto_bytes: str, expected_output_file: str, metric_ids: list[str]):
        """
        Verifies the output of a metric against a given output file.

        Args:
            spec_file (str): The metric specification file.
            trace_proto_bytes (str): The bytes of the trace proto.
            expected_output_file (str): The expected output file.
            metric_ids (list[str]): The list of metric ids.
        """

        root_directory = parent(parent(parent(parent(os.path.abspath(__file__)))))

        spec_file_path = os.path.join(root_directory, spec_file)
        expected_output_file_path = os.path.join(root_directory, "tests/data", expected_output_file)
        shell_file = os.path.join(root_directory, "trace_processor_shell")

        current_mode = os.stat(shell_file).st_mode
        # Add the executable bit for owner, group and others.
        new_mode = current_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH
        os.chmod(shell_file, new_mode)

        trace_config = TraceProcessorConfig(bin_path = shell_file)

        with open(spec_file_path, 'r') as f:
                    spec_text = f.read()

        with TraceProcessor(trace = io.BytesIO(trace_proto_bytes), config = trace_config) as tp:
            summary = tp.trace_summary(specs=[spec_text], metric_ids=metric_ids)
            trace_summary = text_format.MessageToString(summary)

            with open(os.path.join(root_directory, expected_output_file_path), 'r') as f:
                expected_output = f.read()
                self.test_case.assertEqual(trace_summary, expected_output)
