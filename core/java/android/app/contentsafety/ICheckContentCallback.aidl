package android.app.contentsafety;

import android.os.Bundle;

/**
  * Callback for receiving the result of a content check.
  * Interface for a ICheckContentCallback for receiving response from ContentSafetyService when
  * CheckContent is executed against the provided feature.
  *
  * @hide
  */
oneway interface ICheckContentCallback {

    /**
     * Called with the result of the content check.
     * @param result A bundle where keys are string representations of feature types, and values are
     *     lists of {@link android.app.contentsafety.ContentSafetyManager.CheckContentStatus} codes.
     */
    void onResult(in Bundle result) = 1;

}
