# BouncyBall test app

This is a simple graphics app which draws a ball bouncing around the screen.

This app's primary use is in automated testing, to make sure no frames are
dropped while running.

The graphics tested here are quite simple.  This app is not just intended to
assure very basic graphics work, but that the system does not drop frames as
CPUs/GPU get turned off and downclocked while in the steady state.

See https://source.android.com/docs/core/tests/debug/eval_perf#touchlatency
for more details.

## Manual usage basics

This app can be used outside of automation to check and debug this behavior.

This app fundamentally assumes that it is the only foreground app running on
the device while testing.  If that assumption is broken, this app will log
to logcat, at the "E"rror level, noting an "ASSUMPTION FAILURE".

This app will log, at the "E"rror level, each time a frame is dropped.

On a properly set up device, it is expected that this app never drops a frame.

### Helpful "flags" to flip

The source code (in
`app/src/main/java/com/android/test/bouncyball/BouncyBallActivity.java`) has a
few constants which can be changed to help with debugging and testing.  The
app needs to be recompiled after any of these have been changed.

* `LOG_EVERY_FRAME`  If changed to `true`, the app will log, at the "D"ebug
level, every (non-dropped) frame.
* `FORCE_DROPPED_FRAMES`  If changed to `true`, the app will drop every 64th
frame.  This can be helpful for debugging automation pipelines and confirming
app behavior.
* `ASSUMPTION_FAILURE_FORCES_EXIT`  If changed to `false`, if the app fails
the assumption that it is always in foreground focus, then the app will
keep running (even though we know the results will be wrong).


## Local build and install

From the top of tree, in a shell that has been set up for building:

```
$ mmma -j frameworks/base/tests/BouncyBall
$ adb install ${ANDROID_PRODUCT_OUT}/system/app/BouncyBallTest/BouncyBallTest.apk
```

## Launch app from command line

Assuming the app is installed on the device:

```
$ adb shell am start com.android.test.bouncyball/com.android.test.bouncyball.BouncyBallActivity
```

## Using Perfetto for analysis

While the app will self-report when it drops frames, indicating an issue, that's
not very helpful for figuring out why.

TODO(b/408044970): Document how automation uses Perfetto, and how it can be used
here.

