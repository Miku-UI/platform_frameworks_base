#!/usr/bin/env -S python3 -B
#
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tool to manage Ravenwood annotations for entire test classes.

To enable all tests that passed:
$ RAVENWOOD_RUN_DISABLED_TESTS=1 atest SystemUiRavenTests
$ ./toggle-test.py enable /path/to/src \
    /tmp/Ravenwood-stats_SystemUiRavenTestsRavenwood_latest.csv

To disable all tests that failed:
$ atest SystemUiRavenTests
$ ./toggle-test.py disable /path/to/src \
    /tmp/Ravenwood-stats_SystemUiRavenTestsRavenwood_latest.csv

Remove all Ravenwood annotations from sources:
$ ./toggle-test.py strip /path/to/src
"""

import pathlib
import sys
import ravenlib


Path = pathlib.Path
ENABLED_ANNOTATION = "android.platform.test.annotations.EnabledOnRavenwood"
DISABLED_ANNOTATION = "android.platform.test.annotations.DisabledOnRavenwood"


def usage():
  print("Usage: toggle-test.py <enable|disable> <src_root> <csv_file>")
  print("       toggle-test.py strip <src_root>")
  exit(1)


def enable_tests(src_root: str, csv_file: str):
  test_sources = ravenlib.load_source_map(src_root)
  test_classes = ravenlib.find_passed_tests(csv_file)
  for test_class in test_classes:
    if test_class not in test_sources:
      # Cannot find source for test
      continue
    src = test_sources[test_class]
    src.remove_annotation(test_class, DISABLED_ANNOTATION)
    src.add_annotation(test_class, ENABLED_ANNOTATION)
    src.write()


def disable_tests(src_root: str, csv_file: str):
  test_sources = ravenlib.load_source_map(src_root)
  test_classes = ravenlib.find_failed_tests(csv_file)
  for test_class in test_classes:
    if test_class not in test_sources:
      # Cannot find source for test
      continue
    src = test_sources[test_class]
    src.remove_annotation(test_class, ENABLED_ANNOTATION)
    src.add_annotation(test_class, DISABLED_ANNOTATION)
    src.write()


def strip_annotations(src_root: str):
  test_sources = ravenlib.load_source_files(src_root)
  for src in test_sources:
    for clazz, _ in src.list_classes():
      src.remove_annotation(clazz, ENABLED_ANNOTATION)
      src.remove_annotation(clazz, DISABLED_ANNOTATION)
      src.remove_import(ENABLED_ANNOTATION)
      src.remove_import(DISABLED_ANNOTATION)
    src.write()


def main():
  action = sys.argv[1]
  if action == "enable":
    enable_tests(sys.argv[2], sys.argv[3])
  elif action == "disable":
    disable_tests(sys.argv[2], sys.argv[3])
  elif action == "strip":
    strip_annotations(sys.argv[2])
  else:
    usage()


if __name__ == "__main__":
  try:
    main()
  except IndexError:
    usage()
