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
Reads lines from CSV files and print each colum in a separate line,
which helps taking a per-field diff of CSV files. Columns that are
not the first in each line are indented.

Example:
$ (echo "a,b,c"; echo "d,e,f") | csv-flattener
a
  b
  c
d
  e
  f
"""

import csv
import sys
from signal import signal, SIGPIPE, SIG_DFL

def flatten(infile):
    rd = csv.reader(infile, delimiter=',', quotechar='"')

    for cols in rd:
        prefix = ""
        for col in cols:
            print(prefix + col)
            prefix = "  "

def main(files):
    signal(SIGPIPE, SIG_DFL) # Don't show stacktrace on SIGPIPE

    if len(files) == 0:
        flatten(sys.stdin)
        return

    for f in files:
        with open(f, newline='') as csvfile:
            flatten(csvfile)

if __name__ == "__main__":
    main(sys.argv[1:])
