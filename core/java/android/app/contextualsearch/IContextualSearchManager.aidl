package android.app.contextualsearch;

import android.app.contextualsearch.IContextualSearchCallback;

parcelable ContextualSearchConfig;

/**
 * @hide
 */
interface IContextualSearchManager {
  boolean isContextualSearchAvailable();
  void startContextualSearchForActivity(in IBinder activityToken, in ContextualSearchConfig config);
  oneway void startContextualSearch(int entrypoint, in ContextualSearchConfig config);
  oneway void getContextualSearchState(in IBinder token, in IContextualSearchCallback callback);
}
