# VirtualGamepadDemo demo app #

## Overview ##

This application serves as a demonstration and manual testing tool for the virtual gamepad feature in Android. It creates a virtual `InputDevice` that emulates a physical gamepad and provides a simple on-screen UI with analog sticks, a D-pad, and buttons to control it.

It is intended to be used as a reference for future framework implementation and to validate the behavior of the virtual gamepad service.

## Usage and Limitations ##

This app uses the `FLAG_NOT_FOCUSABLE` window flag. This is a crucial setting that allows the user to interact with the virtual gamepad buttons and sticks without the controller app stealing input focus from the game or application being controlled.

However, this has a critical side effect: **the app will cause an ANR (Application Not Responding) error if no other window has focus.**

To use this app correctly:
1.  Open it in split-screen mode with the target game or app.
2.  **Crucially, tap on the target app's window first to ensure it has focus** before you begin using the virtual gamepad.

## Installation ##
Install this using:
```
APP=VirtualGamepadDemo; m $APP && adb install $ANDROID_PRODUCT_OUT/system/app/$APP/$APP.apk
```

## Features ##

* Functional left and right analog sticks.
* A D-pad that correctly handles directional and diagonal input.
* A standard set of gamepad buttons (A, B, X, Y, L1, R1, L2, R2, Start, Select).

## Performance Optimizations ##

This reference implementation includes several non-obvious optimizations to ensure the best
possible performance and lowest latency, which should be carried over to the final framework
implementation.

### 1. Low-Latency Touch Input ###

To minimize touch latency, the app requests unbuffered dispatch for the touchscreen input source by
calling `rootView.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)` once on the root view.
This ensures that motion events are delivered to the app as quickly as possible, bypassing the
usual VSYNC-based batching.

### 2. Efficient Event Batching ###

To avoid making multiple expensive Binder calls when the user touches multiple buttons
simultaneously (within the same dispatch cycle), the app overrides the `Activity.dispatchTouchEvent`
method. This allows the app to handle all touch events for a given `MotionEvent` centrally. The
individual `OnTouchListener`s only update a shared state object. After the default view hierarchy
dispatch is complete, the override method sends a single, consolidated `MotionEvent` to the system
via the Binder interface.

### 3. State Change Optimization ###

To prevent sending redundant events and reduce unnecessary system overhead, the `dispatchTouchEvent`
override checks if the gamepad's state has actually changed before making the Binder call. It does
this by copying the state before the `super.dispatchTouchEvent` call and comparing it to the state
after the call. A `MotionEvent` is only sent if the state is different, ensuring that Binder calls
are only made for meaningful input changes.
