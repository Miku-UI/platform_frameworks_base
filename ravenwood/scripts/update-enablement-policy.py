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

"""Tool to update Ravenwood test enablement policy.

To enable all tests that passed:
$ RAVENWOOD_RUN_DISABLED_TESTS=1 ./run-sysui-tests.sh
$ ./update-enablement-policy.py enable ../texts/sysui-enablement-policy.txt \
    /tmp/Ravenwood-stats_SystemUiRavenTestsRavenwood_latest.csv

To disable all tests that failed:
$ ./run-sysui-tests.sh
$ ./update-enablement-policy.py disable ../texts/sysui-enablement-policy.txt \
    /tmp/Ravenwood-stats_SystemUiRavenTestsRavenwood_latest.csv
"""

import pathlib
import sys
import ravenlib


Path = pathlib.Path
ENABLED_ANNOTATION = "android.platform.test.annotations.EnabledOnRavenwood"
DISABLED_ANNOTATION = "android.platform.test.annotations.DisabledOnRavenwood"


def usage():
  print("Usage: update-enablement-policy.py <enable|disable> <policy_file> <csv_file>")
  exit(1)


def load_policy(policy_file: str) -> dict[str, bool]:
  policy = {}
  with open(policy_file) as f:
    for line in f:
      clazz, enabled = line.strip().split(" ")
      policy[clazz] = enabled == "true"
  return policy


def write_policy(policy_file: str, policy: dict[str, bool]):
  with open(policy_file, "w") as f:
    for clazz, enabled in sorted(policy.items()):
      f.write(f"{clazz} {str(enabled).lower()}\n")


def enable_tests(policy_file: str, csv_file: str):
  policy = load_policy(policy_file)
  default_policy = policy.get("*", True)
  test_classes = ravenlib.find_passed_tests(csv_file)
  for test_class in test_classes:
    if default_policy:
      policy.pop(test_class, None)
    else:
      policy[test_class] = True
  write_policy(policy_file, policy)


def disable_tests(policy_file: str, csv_file: str):
  policy = load_policy(policy_file)
  default_policy = policy.get("*", True)
  test_classes = ravenlib.find_failed_tests(csv_file)
  for test_class in test_classes:
    if default_policy:
      policy[test_class] = False
    else:
      policy.pop(test_class, None)
  write_policy(policy_file, policy)


def main():
  action = sys.argv[1]
  if action == "enable":
    enable_tests(sys.argv[2], sys.argv[3])
  elif action == "disable":
    disable_tests(sys.argv[2], sys.argv[3])
  else:
    usage()


if __name__ == "__main__":
  try:
    main()
  except IndexError:
    usage()
