# Copyright 2025 The Android Open Source Project
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

test_name=''
seconds=0
dump_powerhal=false
use_perfetto=false
use_simpleperf=false

print_usage() {
  printf 'Usage:\n    smoke.sh -n TEST_NAME -d [OPTIONAL SECONDS] \n\n'
  printf -- '-p: Enable power dumps. Only if the test duration is finite. \n\n'
  printf -- '-t: Enable perfetto tracing. Only if the test duration is finite. Only works if you run in the same directory as config.pbtx. \n\n'
  printf -- '-s: Enable simpleperf collection for surfaceflinger. Only if the test duration is finite. \n\n'
}

while getopts 'd::n:pst' flag; do
  case "${flag}" in
    d) seconds="${OPTARG}";;
    n) test_name="${OPTARG}";;
    p) dump_powerhal=true;;
    s) use_simpleperf=true;;
    t) use_perfetto=true;;
    *) print_usage
       exit 1 ;;
  esac
done

if [ -z "$test_name" ]; then
    print_usage
    exit 1
fi

if [[ ! "$seconds" =~ ^[0-9]+$ ]]; then
  echo "Seconds was not a positive integer -- defaulting to 0"
  seconds='0'
fi

echo "Launching test: $test_name"

adb shell am start -n com.android.test.transactionflinger/com.android.test.transactionflinger.activities.$test_name

if (( "$seconds" > 0 )); then
    echo "Running test for $seconds seconds"
    if (( "$seconds" < 10 )); then
        echo "Note that running a test for less than 10 seconds may not produce great power data"
    fi

    start_time=$(date +%s)
    if "$dump_powerhal" ; then
         adb shell dumpsys android.hardware.power.stats.IPowerStats/default | grep -A100 "energy meter" > before_energy.txt
         echo "Dumped initial power stats into before_energy.txt"
    fi

    if "$use_simpleperf" ; then
        $(${ANDROID_BUILD_TOP}/system/extras/simpleperf/scripts/app_profiler.py -np surfaceflinger -r "--duration ${seconds} -g --post-unwind=yes" ; ${ANDROID_BUILD_TOP}/system/extras/simpleperf/scripts/pprof_proto_generator.py -i perf.data) &
    fi

    if "$use_perfetto" ; then
        config="$(< config.pbtx)"
        config+=" duration_ms: ${seconds}000"
        echo ${config} | adb shell -t perfetto -c - --txt -o /data/misc/perfetto-traces/trace.pftrace
        adb pull /data/misc/perfetto-traces/trace.pftrace
    else
        sleep $seconds
    fi

    end_time=$(date +%s)
    elapsed=$(( end_time - start_time ))
    if "$dump_powerhal" ; then
        adb shell dumpsys android.hardware.power.stats.IPowerStats/default | grep -A100 "energy meter" > after_energy.txt
        echo "Dumped final power stats into after_energy.txt after $elapsed seconds"
    fi

    echo "Running for a few extra seconds before stopping"
    sleep 5
    echo "Stopping test: $test_name"
    adb shell am force-stop com.android.test.transactionflinger
fi


