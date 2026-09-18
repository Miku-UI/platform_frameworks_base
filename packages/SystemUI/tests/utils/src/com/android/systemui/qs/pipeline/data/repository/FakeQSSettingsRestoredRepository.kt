package com.android.systemui.qs.pipeline.data.repository

import com.android.systemui.qs.pipeline.data.model.RestoreData
import com.android.systemui.qs.pipeline.shared.InternetTileMigration.migrateInternetTile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeQSSettingsRestoredRepository : QSSettingsRestoredRepository {
    private val _restoreData = MutableSharedFlow<RestoreData>()

    override val restoreData: Flow<RestoreData>
        get() = _restoreData

    suspend fun onDataRestored(restoreData: RestoreData) {
        _restoreData.emit(
            restoreData.copy(
                restoredTiles = restoreData.restoredTiles.migrateInternetTile(),
                restoredAutoAddedTiles = restoreData.restoredAutoAddedTiles.migrateInternetTile(),
            )
        )
    }
}
