#!/bin/bash
# Copyright (C) 2024 The Android Open Source Project
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

# List all the ravenwood test modules.

set -e

fbr_only=0
device_test=0
while getopts "rD" opt; do
case "$opt" in
    r)
        # Only print tests under frameworks/base/ravenwood/
        fbr_only=1
        ;;
    D)
        # Print device side tests under f/b/r.
        fbr_only=1
        device_test=1
        ;;
    '?')
        exit 1
        ;;
esac
done
shift $(($OPTIND - 1))

in="$OUT/module-info.json"
cache="$OUT/ravenwood-test-list-fbr${fbr_only}-dev${device_test}.cached.txt"
cache_temp="${cache}.tmp"

suite="ravenwood-tests"
if (( $device_test )) ; then
    suite="device-tests"
fi

extra_select=""
if (( $fbr_only )) ; then
    extra_select='| select( .value.path.[] | startswith("frameworks/base/ravenwood"))'
fi

run() {
    # echo "Running: $*" 1>&2 # for debugging
    "$@"
}

# If module-info.json or this script itself is newer than the cache file,
# then re-generate it.
if [[ "$in" -nt "$cache" ]] || [[ "$0" -nt "$cache" ]] ; then
    rm -f "$cache_temp" "$cache"

    # First, create to a temp file, and once it's completed, rename it
    # to the actual cache file, so that if the command failed or is interrupted,
    # we don't update the cache.
    run jq -r 'to_entries[] | select( .value.compatibility_suites | index("'$suite'") ) '"$extra_select"' | .key' \
            "$OUT/module-info.json" | sort > "$cache_temp"
    mv "$cache_temp" "$cache"
fi

cat "$cache"
