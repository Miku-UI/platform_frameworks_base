package android.app.ondeviceintelligence;

import android.app.ondeviceintelligence.InferenceInfo;
import android.os.PersistableBundle;
import android.os.Bundle;
import android.os.RemoteCallback;

/**
  * Interface for a IResponseCallback for receiving response from on-device intelligence service.
  *
  * @hide
  */
oneway interface IResponseCallback {
    void onSuccess(in Bundle resultBundle) = 1;
    void onFailure(int errorCode, in String errorMessage, in PersistableBundle errorParams) = 2;
    void onDataAugmentRequest(in Bundle processedContent, in RemoteCallback responseCallback) = 3;
    void onInferenceInfo(in InferenceInfo info) = 4;
}
