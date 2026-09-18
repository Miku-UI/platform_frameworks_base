package android.service.contentsafety;

import android.os.Bundle;

/**
  *  Callback for {@link IContentSafetyService#getFeature} to handle downloaded feature files.
  *  Interface for a IGetFeatureCallback to handle the downloaded feature files/bytes from the
  *  romete service.
  *
  * @hide
  */
oneway interface IGetFeatureCallback {
     /**
     * Called with a bundle containing feature file descriptors.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    void onResult(in Bundle result) = 1;
    /**
     * Called on error.
     * @param errorCode A {@link ContentSafetyException.ContentSafetyError} code.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    void onError(in int errorCode) = 2;
}
