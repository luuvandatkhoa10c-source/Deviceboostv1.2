package dev.garen.deviceboost

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Quick Settings tile: toggles animations between 1.0x and 0.5x. */
class AnimTileService : TileService() {

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        if (!AnimSettings.hasPermission(this)) {
            Toast.makeText(this, "Open Device Boost and finish setup first", Toast.LENGTH_LONG).show()
            return
        }
        val fast = isFast()
        AnimSettings.setAll(this, if (fast) 1f else 0.5f)
        update()
    }

    private fun isFast(): Boolean = AnimSettings.read(this).first <= 0.6f

    private fun update() {
        val tile = qsTile ?: return
        tile.state = if (isFast()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
