package android.app.privatecompute;

import android.app.privatecompute.IMigrationRequestResultSender;

/**
 * @hide
 */
oneway interface IDataMigrationToPccService {
    void onMigrationRequested(in IMigrationRequestResultSender callback);
}

