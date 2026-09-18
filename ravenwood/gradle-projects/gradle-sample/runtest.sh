#!/bin/bash
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


# Make sure the gradle script works as expected.

set -e

SCRIPT_DIR="${0%/*}"

cd "$SCRIPT_DIR"

run() {
    echo "Running: $*"
    "$@"
}


# Run tests on Ravenwood
run ./gradlew clean

# Run tests on Ravenwood
run ./gradlew :app:testDebugUnitTest --info -PenableRavenwood=true --rerun

# Run tests on Robolectric
run ./gradlew :app:testDebugUnitTest --info -PenableRavenwood=false --rerun

# Run TestLibrary's test (which runs on Robolectric)
run ./gradlew :TestLibrary:testDebugUnitTest --info --rerun
