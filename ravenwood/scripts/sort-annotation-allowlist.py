#!/usr/bin/env python3
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

"""
Tool to sort the annotation allowlist.
Full line comments will be removed, but inline comments will be preserved.

Usage:
$ python sort-annotation-allowlist.py /path/to/ravenwood-annotation-allowed-classes.txt
"""

import os
import sys


file_name = sys.argv[1]
output_name = f"{file_name}.tmp"

with open(file_name) as f:
  lines = f.readlines()

wildcards = set()
pkg_dict = {}

for line in lines:
  line = line.strip()
  # Extract class name from line, stripping out comments
  class_name = line.split("#", 1)[0].strip()
  if not class_name:
    continue
  if "*" in class_name:
    wildcards.add(line)
    continue
  pkg = class_name.rsplit(".", 1)[0]
  if pkg not in pkg_dict:
    pkg_dict[pkg] = set()
  pkg_dict[pkg].add(line)


with open(output_name, "w") as f:
  print("# Only classes listed here can use the Ravenwood annotations. Keep the list alphabetically sorted.", file=f)
  print("", file=f)

  # Put wildcards first
  for wildcard in sorted(wildcards):
    print(wildcard, file=f)

  # Then print classes, bundled by package
  for pkg in sorted(pkg_dict.keys()):
    print("", file=f)
    for line in sorted(pkg_dict[pkg]):
      print(line, file=f)

os.rename(output_name, file_name)
