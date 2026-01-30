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

#
# Run all or selected the ravenwood tests + hoststubgen unit tests.
#
# Use -h to see the help.
#

set -e
shopt -s nullglob # if a glob matches no file, expands to an empty string.

# Move to the script's directory
cd "${0%/*}"
my_command="${0##*/}"

# Find the enablement files. This may be an empty list if there's no match.
default_enablement_policy=(../texts/enablement-policy-*.txt)

# ROLLING_TF_SUBPROCESS_OUTPUT is often quite behind for large tests.
# let's disable it by default.
: ${ROLLING_TF_SUBPROCESS_OUTPUT:=0}
export ROLLING_TF_SUBPROCESS_OUTPUT


show_help() {
    cat <<EOF

$my_command: Run all or specified ravenwood tests

  Usage:
     $my_command [OPTIONS]
        Run all ravenwood tests and relevant host side tests.

     $my_command [OPTIONS] TEST-MODULE-NAME...
        Run specified test module

  Note:
     Tests with @FlakyTest are always ignored.

  Options:
EOF
    sed -n -e '/OPTIONS-START/,/OPTIONS-END/s/^ *\([a-zA-Z]\)) #/   -\1/p' "$my_command"
    echo
    return
}

smoke=0
include_re=""
exclude_re=""
smoke=0
smoke_exclude_re=""
dry_run=""
exclude_large_tests=0
atest_opts=""
list_options=""
with_tools_tests=1

while getopts "sx:f:dtbLa:rDRh" opt; do
case "$opt" in
# OPTIONS-START
    s) # Remove slow tests
        smoke=1
        exclude_large_tests=1
        ;;
    x) # Take a PCRE from the arg, and use it as an exclusion filter. Example: -x '^(Cts|hoststub)' # Exclude CTS and hoststubgen tests.
        exclude_re="$OPTARG"
        ;;
    f) # Take a PCRE from the arg, and use it as an inclusion filter.
        include_re="$OPTARG"
        ;;
    d) # Dry run
        dry_run="echo"
        ;;
    t) # Redirect log to terminal
        export RAVENWOOD_LOG_OUT=-
        ;;
    a) # atest options (e.g. "-t")
        atest_opts="$OPTARG"
        ;;
    L) # exclude large tests
        exclude_large_tests=1
        ;;
    r) # only run tests under frameworks/base/ravenwood/
        list_options="$list_options -r"
        ;;
    D) # Only run device tests under frameworks/base/ravenwood/
        list_options="$list_options -D"
        with_tools_tests=0
        ;;
    R) # Run disabled tests too
        export RAVENWOOD_RUN_DISABLED_TESTS=1
        ;;
    h) # Show help
        show_help
        exit 0
        ;;
    '?')
        exit 1
        ;;
# OPTIONS-END
esac
done
shift $(($OPTIND - 1))

# If the rest of the arguments are available, just run these tests.
targets=("$@")

if (( $with_tools_tests )) ; then
    all_tests=(hoststubgentest tiny-framework-dump-test hoststubgen-invoke-test ravenwood-stats-checker ravenhelpertest ravenwood-scripts-sh-golden-test)
fi

# Allow replacing 'list-ravenwood-tests.sh' with  $LIST_TEST_COMMAND.
all_raven_tests=( $( "${LIST_TEST_COMMAND:=./list-ravenwood-tests.sh}" $list_options ) )

all_tests+=( "${all_raven_tests[@]}" )

# ROLLING_TF_SUBPROCESS_OUTPUT is often quite behind for large tests.
# let's disable it by default.
: ${ROLLING_TF_SUBPROCESS_OUTPUT:=0}
export ROLLING_TF_SUBPROCESS_OUTPUT

# Cat all the files in the argument with all the "#" comments removed.
remove_comments() {
    sed -e '/^#/d; s/[ \t][ \t]*//g; /^$/d' "$@"
}

get_smoke_re() {
    # Extract tests from smoke-excluded-tests.txt
    # - Skip lines starting with #
    # - Remove all spaces and tabs
    # - Skip empty lines
    local tests=($(remove_comments ../texts/smoke-excluded-tests.txt))

    # Then convert it to a regex.
    # - Wrap in "^( ... )$"
    # - Conact the tests with "|"
    echo -n "^("
    (
        local IFS='|'
        echo -n "${tests[*]}"
    )
    echo -n ")$"
}

if (( $smoke )) ; then
    smoke_exclude_re=$(get_smoke_re)
    echo "smoke_exclude_re=${smoke_exclude_re%Q}" # %Q == shell quote
fi

filter() {
    local re="$1"
    local grep_arg="$2"
    if [[ "$re" == "" ]] ; then
        cat # No filtering
    else
        grep $grep_arg -iP "$re"
    fi
}

filter_in() {
    filter "$1"
}

filter_out() {
    filter "$1" -v
}

# If targets are not specified in the command line, run all tests w/ the filters.
if (( "${#targets[@]}" == 0 )) ; then
    # Filter the tests.
    targets=( $(
        for t in "${all_tests[@]}"; do
            echo $t | filter_in "$include_re" | filter_out "$smoke_exclude_re" | filter_out "$exclude_re"
        done
    ) )
fi

# Show the target tests

echo "Target tests:"
for t in "${targets[@]}"; do
    echo "  $t"
done

# Calculate the removed tests.

diff="$(diff  <(echo "${all_tests[@]}" | tr ' ' '\n') <(echo "${targets[@]}" | tr ' ' '\n') | grep -v '[0-9]' || true)"

if [[ "$diff" != "" ]]; then
    echo "Excluded tests:"
    echo "$diff"
fi

# Build the "enablement" policy by merging all the policy files.
# But if RAVENWOOD_TEST_ENABLEMENT_POLICY is already set, just use it.
if [[ "$RAVENWOOD_TEST_ENABLEMENT_POLICY" == "" ]] && (( "${#default_enablement_policy[@]}" > 0 )) ; then
    export RAVENWOOD_TEST_ENABLEMENT_POLICY="$(readlink -m ${default_enablement_policy[*]} | tr '\n' ' ')"
fi

echo "RAVENWOOD_TEST_ENABLEMENT_POLICY=$RAVENWOOD_TEST_ENABLEMENT_POLICY"

# Set experimental API flag
for test in $(remove_comments ../texts/experimental-api-allowed-tests.txt); do
    echo "Test \"$test\" can use experimental APIs".
    export RAVENWOOD_ENABLE_EXP_API_${test}=1
done

echo "RAVENWOOD_RUN_DISABLED_TESTS=$RAVENWOOD_RUN_DISABLED_TESTS"
echo "RAVENWOOD_FORCE_FILTER_REGEX=$RAVENWOOD_FORCE_FILTER_REGEX"

# =========================================================

run() {
    echo "Running: ${@}"
    "${@}"
}

extra_args=()


# Exclusion filter annotations
exclude_annos=()
# Always ignore flaky tests
exclude_annos+=(
    "android.platform.test.annotations.FlakyTest"
    "androidx.test.filters.FlakyTest"
)
# Maybe ignore large tests
if (( $exclude_large_tests )) ; then
    exclude_annos+=(
        "android.platform.test.annotations.LargeTest"
        "androidx.test.filters.LargeTest"
    )
fi

# Add per-module arguments
extra_args+=("--")

# Need to add the following two options for each module.
# But we can't add it to non-ravenwood tests, so use $all_raven_tests
# instead of $targets.
for module in "${all_raven_tests[@]}" ; do
    for anno in "${exclude_annos[@]}" ; do
        extra_args+=(
            "--module-arg $module:exclude-annotation:$anno"
            )
    done
done

run $dry_run ${ATEST:-atest} --class-level-report $atest_opts "${targets[@]}" "${extra_args[@]}"
