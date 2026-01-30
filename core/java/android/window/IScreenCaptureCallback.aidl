package android.window;

import android.window.ScreenCapture;

/**
 * @hide
 */
interface IScreenCaptureCallback {
    oneway void onSuccess(in ScreenCapture.ScreenCaptureResult result);
    oneway void onFailure(int errorCode);
}
