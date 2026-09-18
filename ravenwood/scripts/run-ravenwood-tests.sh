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

# Show a command and then execute it.
run() {
    echo "Running: ${@}"
    "${@}"
}


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

disable_tf_rolling_log() {
    run export ROLLING_TF_SUBPROCESS_OUTPUT=0
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
no_experimental_api=0
target_args=()

while getopts "sx:f:dltbLa:rDRhXcTtwPF:Im:E" opt; do
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
    l) # Redirect log to terminal (live logcat)
        run export RAVENWOOD_LOG_OUT=-
        disable_tf_rolling_log
        ;;
    T) # Disable live logcat
        run unset RAVENWOOD_LOG_OUT
        ;;
    a) # Set atest options (e.g. "-t")
        atest_opts="$atest_opts $OPTARG"
        ;;
    t) # Run only, no building (i.e. "atest -t"). Non-ravenwood host tests may not work with this.
        atest_opts="$atest_opts -t"
        ;;
    w) # Enable the debugger (i.e. "atest -w")
        atest_opts="$atest_opts -w"
        ;;
    L) # Exclude large tests (@LargeTest and :large tests in the policy files)
        exclude_large_tests=1
        ;;
    r) # Only run tests under frameworks/base/ravenwood/
        list_options="$list_options -r"
        ;;
    D) # Only run device tests under frameworks/base/ravenwood/
        list_options="$list_options -D"
        with_tools_tests=0
        ;;
    R) # Run disabled tests too
        run export RAVENWOOD_RUN_DISABLED_TESTS=1

        # When we're running all tests, we usually want to see skipped tests
        # in the result too, so clear this.
        run export RAVENWOOD_HIDE_DISABLED_TESTS=0
        ;;
    X) # Run only disabled tests
        run export RAVENWOOD_RUN_DISABLED_TESTS=2
        ;;
    c) # Clean output -- don't show disabled tests in atest output
        run export RAVENWOOD_HIDE_DISABLED_TESTS=1
        ;;
    P) # Dump tests only
        run export RAVENWOOD_DUMP_TESTS_ONLY=1
        ;;
    F) # Set enable filter regex
        run export RAVENWOOD_FILTER_REGEX="$OPTARG"
        ;;
    I) # Inpatient mode -- shorten "slow test" timeout (-II to shorten more)
        if [[ "$RAVENWOOD_SLOW_TIMEOUT_SECONDS" == "" ]] ; then
            # $RAVENWOOD_SLOW_TIMEOUT_SECONDS isn't set
            run export RAVENWOOD_SLOW_TIMEOUT_SECONDS=4
            run export RAVENWOOD_DIE_TIMEOUT_SECONDS=8
        else
            # $RAVENWOOD_SLOW_TIMEOUT_SECONDS is set, so assume it's -II.
            run export RAVENWOOD_SLOW_TIMEOUT_SECONDS=2
            run export RAVENWOOD_DIE_TIMEOUT_SECONDS=3
        fi
        ;;
    m) # Specify target test mode
        # It's the same thing as just putting target modules at the end
        # (without -m), but it'll allow adding flags after test module
        # names, which is sometimes handy.
        target_args+=($OPTARG)
        ;;
    E) # Do not enable experimental API
        no_experimental_api=1
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

# Test start time. In the golden file test, we inject it via $RRT_START_TIME.
start_time=${RRT_START_TIME:-$(date '+%Y%m%d-%H%M%S')}

# The $target array contains all the tests we're actually going to execute.
# If the rest of the arguments are available, just run these tests.
targets=("$@" "${target_args[@]}")

# Collect all executable tests, which we'll set to $targets later if it's empty.
all_tests=()

# Host tests under f/b/r.
host_tests=(
    hoststubgentest
    tiny-framework-dump-test
    hoststubgen-invoke-test
    ravenwood-stats-checker
    ravenhelpertest
    ravenwood-scripts-sh-golden-test
)

if (( $with_tools_tests )) ; then
    all_tests+=("${host_tests[@]}")
fi

# Allow replacing 'list-ravenwood-tests.sh' with  $LIST_TEST_COMMAND.
all_raven_tests=( $( "${LIST_TEST_COMMAND:=./list-ravenwood-tests.sh}" $list_options ) )

all_tests+=( "${all_raven_tests[@]}" )

# ROLLING_TF_SUBPROCESS_OUTPUT is often quite behind for large tests.
# let's disable it by default.
: ${ROLLING_TF_SUBPROCESS_OUTPUT:=0}
run export ROLLING_TF_SUBPROCESS_OUTPUT

# The tests that'd break if executed with RAVENWOOD_HIDE_DISABLED_TESTS=1
run export RAVENWOOD_HIDE_DISABLED_TESTS_RavenwoodCoreTest=0
run export RAVENWOOD_HIDE_DISABLED_TESTS_RavenwoodBivalentTest=0

# Set bugreport dir
run export RAVENWOOD_BUGREPORT_DIR=/tmp/ravenwood-bugreports/$start_time
mkdir -p $RAVENWOOD_BUGREPORT_DIR

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
if (( $no_experimental_api )) ; then
    echo "Not enabling experimental APIs".
else
    for test in $(remove_comments ../texts/experimental-api-allowed-tests.txt); do
        echo "Test \"$test\" can use experimental APIs".
        export RAVENWOOD_ENABLE_EXP_API_${test}=1
    done
fi

if (( $exclude_large_tests )) ; then
    run export RAVENWOOD_SKIP_LARGE_TESTS=1
fi


echo "RAVENWOOD_RUN_DISABLED_TESTS=$RAVENWOOD_RUN_DISABLED_TESTS"
echo "RAVENWOOD_FILTER_REGEX=$RAVENWOOD_FILTER_REGEX"
echo "RAVENWOOD_HIDE_DISABLED_TESTS=$RAVENWOOD_HIDE_DISABLED_TESTS"
echo "RAVENWOOD_SKIP_LARGE_TESTS=$RAVENWOOD_SKIP_LARGE_TESTS"
echo "RAVENWOOD_DUMP_TESTS_ONLY=$RAVENWOOD_DUMP_TESTS_ONLY"
echo "RAVENWOOD_BUGREPORT_DIR=$RAVENWOOD_BUGREPORT_DIR"

# =========================================================

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

set +e # Do not exit even if the atest command fails
run $dry_run ${ATEST:-atest} --class-level-report $atest_opts "${targets[@]}" "${extra_args[@]}"
rc=$?

if [[ "$(ls -A $RAVENWOOD_BUGREPORT_DIR >&1 )" != "" ]] ; then
    # If $RAVENWOOD_BUGREPORT_DIR contains any files, these are bugreports.
    # Show all of them in full paths.
    echo "Generated bugreports:"
    find $RAVENWOOD_BUGREPORT_DIR -type f | sort
fi
