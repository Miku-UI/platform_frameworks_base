# BouncyBall Test App

BouncyBall is a simple graphics app that draws a bouncing ball, used for
automated testing to detect dropped frames.

It ensures basic graphics rendering remains smooth even as system resources
fluctuate. For more details on performance evaluation, see
[Evaluating performance](https://source.android.com/docs/core/tests/debug/eval_perf#touchlatency).

## Building and Running

To build, install, and run the app locally:

```bash
# Build
$ mmma -j frameworks/base/tests/BouncyBall

# Install
$ adb install ${ANDROID_PRODUCT_OUT}/system/app/BouncyBallTest/BouncyBallTest.apk

# Start
$ adb shell am start -W com.android.test.bouncyball/com.android.test.bouncyball.BouncyBallActivity

# Stop
$ adb shell am force-stop com.android.test.bouncyball

# Uninstall
$ adb uninstall com.android.test.bouncyball
```

**Note:** The app assumes it is the only foreground app during testing. If not,
it will log an "ASSUMPTION FAILURE" error. On a properly configured device, no
frames should be dropped.

## Debugging

You can change boolean constants in
`app/src/main/java/com/android/test/bouncyball/BouncyBallActivity.java` to aid
debugging. Recompile the app after any changes.

*   `FORCE_DROPPED_FRAMES`: Intentionally drop every 64th frame to test
    detection.
*   `ASSUMPTION_FAILURE_FORCES_EXIT`: Set to `false` to prevent the app from
    exiting when it's not in the foreground.

For detailed performance analysis and debugging frame drops, use on-device
tracing. See
[On-device tracing](https://developer.android.com/topic/performance/tracing/on-device)
for guidance.

## GPU Composition

BouncyBall's simplicity may cause some devices to bypass GPU composition. You
can force it on for testing:

```bash
# Force GPU composition on
$ adb shell sfdo force-client-composition enabled

# Return to default GPU composition
$ adb shell sfdo force-client-composition disabled
```

## Automation Analysis

Automated testing uses Perfetto to trace frame drops. This analysis confirms
whether frames were dropped but is not intended for debugging *why* they were
dropped.

### Running the Trace

```bash
# Ensure app is installed but stopped
$ adb shell am force-stop com.android.test.bouncyball

# Set up and run Perfetto trace
$ adb push automation_config.pbtx /data/misc/perfetto-configs/
$ adb shell /system/bin/perfetto --background --config /data/misc/perfetto-configs/automation_config.pbtx --txt --out /data/misc/perfetto-traces/bouncy_trace
# Note the PERFETTO_PID from the output

# Launch the app
$ adb shell am start -W com.android.test.bouncyball/com.android.test.bouncyball.BouncyBallActivity

# Wait for Perfetto to finish (approx. 2 minutes)
$ adb shell 'while ps -p PERFETTO_PID >/dev/null; do sleep 1; done'

# Pull trace and analyze
$ adb pull /data/misc/perfetto-traces/bouncy_trace .
$ ../../../../prebuilts/tools/linux-x86_64/perfetto/trace_processor_shell \
  --summary --summary-metrics-v2 all \
  --summary-spec trace_metrics_v2_spec.pbtx bouncy_trace
```

A successful run will show `bouncyball_failing_jank_instance_count`, the last
"value" in the output, as 0.

### Cleanup

```bash
# Clean up device
$ adb shell rm /data/misc/perfetto-configs/automation_config.pbtx /data/misc/perfetto-traces/bouncy_trace

# Clean up local directory
$ rm bouncy_trace
```
