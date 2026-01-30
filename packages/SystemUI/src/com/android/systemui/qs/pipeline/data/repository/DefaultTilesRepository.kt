package com.android.systemui.qs.pipeline.data.repository

import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.pipeline.data.model.AllowedTiles
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

interface DefaultTilesRepository {
    /**
     * Returns the list of default tiles for a user.
     *
     * @param isHeadlessSystemUser indicates if the user is the headless system user (HSU) and if
     *   the HSU default tiles should be returned
     */
    fun getDefaultTiles(isHeadlessSystemUser: Boolean): List<TileSpec>
}

@SysUISingleton
class DefaultTilesQSHostRepository
@Inject
constructor(
    @param:ShadeDisplayAware private val resources: Resources,
    private val hsuTilesRepository: HsuTilesRepository,
) : DefaultTilesRepository {
    private val defaultTiles: List<TileSpec>
        get() =
            QSHost.getDefaultSpecs(resources).map(TileSpec::create).filter {
                it != TileSpec.Invalid
            }

    override fun getDefaultTiles(isHeadlessSystemUser: Boolean): List<TileSpec> {
        return if (
            isHeadlessSystemUser && hsuTilesRepository.allowedTiles is AllowedTiles.SpecificTiles
        ) {
            hsuTilesRepository.allowedTiles.tiles
        } else {
            defaultTiles
        }
    }
}
