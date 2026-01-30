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

# set -e

# Move to the script's directory
cd "${0%/*}"

update_result=0

while getopts "u" opt; do
case "$opt" in
    u)
        # Remove slow tests.
        update_result=1
        ;;
    '?')
        exit 1
        ;;
esac
done
shift $(($OPTIND - 1))

# Find the script directory:
# - `../` when running directly.
# - './scripts/` when running with atest.
script_dir=../
if [[ -e ./scripts/run-ravenwood-tests.sh ]] ; then
    script_dir=./scripts/
fi

target_script="$script_dir/run-ravenwood-tests.sh"

echo "# Installed files:"
echo
ls -lRa .
echo

echo "# Target script:"
echo "$target_script"
echo

# Command to run "run-ravenwood-tests.sh"
run-ravenwood-tests-wrapper() {
    "$target_script" -d "$@"
}

# We "inject" into list-ravenwood-tests.sh and use them as the test module names.
export ALL_TESTS="RavenwoodCoreTest Test1 Test2 Test3 SystemUiRavenTests"

# We use this function instead of list-ravenwood-tests.sh during this test.
list_tests() {
    echo "List test options: $*" 1>&2
    echo "$ALL_TESTS" | tr ' ' '\n'
}
export -f list_tests
export LIST_TEST_COMMAND=list_tests

# Print the whole command line, and execute it.
run() {
    echo "Running: $*"
    echo
    "$@"
}

# Print $1 as a test name, then execute the rest of the arguments as a command.
run_test() {
    echo
    echo "========================================================================="
    echo "  Test: $1"
    echo "========================================================================="

    shift
    run "$@"
}

# Run the target commands.
run_all_commands() {
    unset RAVENWOOD_TEST_ENABLEMENT_POLICY
    unset RAVENWOOD_RUN_DISABLED_TESTS
    unset RAVENWOOD_FORCE_FILTER_REGEX

    run_test "Run with no arguments" run-ravenwood-tests-wrapper

    run_test "Help" run-ravenwood-tests-wrapper -h

    run_test "Smoke tests" run-ravenwood-tests-wrapper -s

    run_test "Exclude large tests" run-ravenwood-tests-wrapper -L

    # This would just change the "list test" option.
    run_test "With -r" run-ravenwood-tests-wrapper -r

    run_test "-r with smoke tests" run-ravenwood-tests-wrapper -r -s

    run_test "Run specific tests" run-ravenwood-tests-wrapper -s TestX TestY

    run_test "Run specific tests with -s" run-ravenwood-tests-wrapper -s TestX TestY

    run_test "Inclusion" run-ravenwood-tests-wrapper -f '(Test[2345])'

    run_test "Exclusion" run-ravenwood-tests-wrapper -x '(Test[2345])'

    ALL_TESTS="DeviceTest1 DeviceTest2" run_test "Run device tests (-D) " run-ravenwood-tests-wrapper -D

    run_test "Run with disabled tests" run-ravenwood-tests-wrapper -R

    RAVENWOOD_RUN_DISABLED_TESTS=xxx RAVENWOOD_FORCE_FILTER_REGEX=yyy run_test "Make sure env vars are printed" run-ravenwood-tests-wrapper

    echo "== All commands finished =="
}

# Test start...

golden_output=./golden-output/output-golden.txt

#=================================================================
# Run the commands, and store the output in this file
#=================================================================

command_out=/tmp/$$.tmp

# We ally this filter to the output to remove unstable parts.
output_filter() {
    # RAVENWOOD_TEST_ENABLEMENT_POLICY has full paths filenames,
    # which is unstable. Let's just remove the value...
    sed -e "/^RAVENWOOD_TEST_ENABLEMENT_POLICY/{s/=.*/=.../}"
}

cat <<'EOF'

** Running the commands...

EOF

run_all_commands |& output_filter | tee "$command_out"

#=================================================================
# Diff
#=================================================================

cat <<'EOF'

** Diffing the output...

EOF

run diff -u "$golden_output" "$command_out"
rc=$?

#=================================================================
# Show / update the result
#=================================================================

echo
if (( $rc == 0 )) ; then
    # Green
    echo -e "\e[38;5;10m*** ALL TESTS PASSED ***\e[0m"
    exit 0
else
    if (( $update_result )) ; then
        # Yellow
        cp "$command_out" "$golden_output"
        echo -e "\e[38;5;11m*** DIFF DETECTED; UPDATED THE GOLDEN FILE ***\e[0m"
        exit 1
    else
        # Red
        echo -e "\e[38;5;9m*** SOME TESTS FAILED ***\e[0m"
        exit 2
    fi
fi

# Shouldn't reach here.
exit 99