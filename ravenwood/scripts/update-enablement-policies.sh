#!/bin/bash
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

# Update all "enablement policy" files, using the latest result CSV files.
#
# Usage:
#   Update a single policy file:
#    $ANDROID_BUILD_TOP/frameworks/base/ravenwood/scripts/update-enablement-policies.sh \
#        $ANDROID_BUILD_TOP/frameworks/base/ravenwood/texts/enablement-policy-....txt
#
#  If no args are given, update all policy files.

set -e
shopt -s nullglob # if a glob matches no file, expands to an empty string.

# Query for csvsql(1).
query='
select
    Class || "#" || RawMethodName ||
    (case when (sum(failed) + sum(skipped)) = 0 then "" else " disable" end )
    as "# Per method enable/disable"
from stdin
where type="m"
group by Class, RawMethodName
order by Class, RawMethodName
'

summary_query='
select
    "# Total=" || printf("%d",sum(passed) + sum(failed) + sum(skipped)) || " " ||
    "Passed=" || printf("%d", sum(passed)) || " " ||
    "Failed=" || printf("%d", sum(failed)) || " " ||
    "Skipped=" || printf("%d", sum(skipped))
    as "# Summary"
from stdin
where type="m"
'

# Return the test module name from a policy file.
get_module_name() {
    local file="$1"

    # Print the 2nd field of the first line
    awk 'NR==1 {print $2; exit}' "$1"
}

# Return the "header", which what we don't want to change, from a policy file.
get_header() {
    local file="$1"

    # Print until the marker line. (which is the header line from csvcut)
    # (if there's no such line, print the whole content.)
    sed -e '/^# AUTO-GENERATED START/,$d' $1
}

# Normalize the csvsql output.
normalize() {
    # Remove the header, and convert to space-delimited.
    csvformat -E -M $'\n' -D $'\t'
}

do_main() {
    local policies="$@"
    for policy in $policies ; do
        echo "Updating policy file: $policy"
        module=$(get_module_name "$policy")

        csv="/tmp/Ravenwood-stats_${module}_latest.csv"

        if ! [[ -e "$csv" ]] ; then
            echo "$csv not found" 1>&2
            continue
        fi
        echo "Test stats found: $csv"

        new="$policy.tmp"
        {
            get_header "$policy"
            echo "# AUTO-GENERATED START"
            echo

            # Summary
            csvsql --query "$summary_query" < "$csv" | normalize

            echo
            # Per-method enable/disable
            csvsql --query "$query" < "$csv" | normalize
            echo "Success" 1>&2
        } >"$new"

        mv "$new" "$policy"
    done
}

if [[ "$*" == "" ]] ; then
    # No args given.
    do_main "${0%/*}"/../texts/enablement-policy-*.txt
else
    do_main "$@"
fi