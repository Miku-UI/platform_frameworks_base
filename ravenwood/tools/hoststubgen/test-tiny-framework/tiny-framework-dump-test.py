#!/usr/bin/python3
# Copyright (C) 2023 The Android Open Source Project
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

# Compare the tiny-framework JAR dumps to the golden files.

import sys
import os
import unittest
import subprocess

GOLDEN_DIRS = [
    'golden-output',
]

# This file contains the build flags that may affect javac output.
# If the content of this file is different, we won't check
# the remaining files and just mark the test as "passed".
# (But the diff-and-update-golden.sh script won't do this check and always checks
# all the files.)
BUILD_FLAG_FILE = '00-hoststubgen-build-flags.txt'

EXCLUDE_FILES = {
    '01-hoststubgen-test-tiny-framework-orig-dump.txt',
    '03-hoststubgen-test-tiny-framework-host-dump.txt',
    '13-hoststubgen-test-tiny-framework-host-ext-dump.txt',
}

def log(msg):
    print(msg, file=sys.stdout)
    sys.stdout.flush()

# Run diff.
def run_diff(file1, file2):
    command = ['diff', '-u',
               '--ignore-blank-lines',
               '--ignore-space-change',
               file1, file2]
    log(' '.join(command))
    result = subprocess.run(command, stderr=sys.stdout)

    success = result.returncode == 0

    if success:
        log('No diff found.')
    else:
        log(f'Fail: {file1} and {file2} are different.')

    return success


# Check one golden file.
def check_one_file(golden_dir, filename):
    log(f'= Checking file: {filename}')
    return run_diff(os.path.join(golden_dir, filename), filename)


class TestWithGoldenOutput(unittest.TestCase):

  # Test to check the generated jar files to the golden output.
  # Depending on build flags, the golden output may differ in expected ways.
  # So only expect the files to match one of the possible golden outputs.
  def test_compare_to_golden(self):
    success = False

    for golden_dir in GOLDEN_DIRS:
      if self.matches_golden(golden_dir):
        success = True
        log(f"Test passes for dir: {golden_dir}")
        break

    if not success:
      self.fail('Some files are different. ' +
                'See stdout log for more details.')

  def matches_golden(self, golden_dir):
    files = os.listdir(golden_dir)
    files = sorted(set(files) - EXCLUDE_FILES)

    log(f"Golden files for {golden_dir}: {files}")
    match_success = True

    for file in files:
      if not check_one_file(golden_dir, file):
        if file == BUILD_FLAG_FILE:
          log(f"*** Build flag file ({file}) differs. Skipping the remaining test. ***")
          return True
        match_success = False

    return match_success


if __name__ == "__main__":
    args = sys.argv

    # This script is used by diff-and-update-golden.sh too.
    if len(args) > 1 and args[1] == "run-diff":
        if run_diff(args[2], args[3]):
            sys.exit(0)
        else:
            sys.exit(1)

    unittest.main(verbosity=2)
