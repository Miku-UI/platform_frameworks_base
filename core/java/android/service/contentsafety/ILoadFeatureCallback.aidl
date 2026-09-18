package android.service.contentsafety;

/**
  * Interface for a ILoadFeatureCallback for making sure the retrieved features are loaded and ready to be used to ensure contents are safe.
  *
  * @hide
  */
oneway interface ILoadFeatureCallback {
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    void onResult() = 1;

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    void onError(in int errorCode) = 2;
}
