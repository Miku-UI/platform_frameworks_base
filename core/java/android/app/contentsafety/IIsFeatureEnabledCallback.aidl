package android.app.contentsafety;

/**
  * Interface for a IIsFeatureEnabledCallback to be passed to the service implementation.

  * @hide
  */
oneway interface IIsFeatureEnabledCallback {
    void onSuccess(boolean isFeatureEnabledResult) = 1;
    void onFailure(in int errorCode) = 2;
}
