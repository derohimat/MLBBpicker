package ai.zasha.mlbbpicker.service

import android.service.quicksettings.TileService

/** Quick Settings tile: tap to open the overlay panel directly on the Bans tab. */
class BanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        OverlayTiles.updateTile(qsTile, active = false, subtitle = "Ban phase")
    }

    override fun onClick() {
        super.onClick()
        OverlayTiles.sendOverlayAction(this, FloatingOverlayService.ACTION_SHOW_BANS)
    }
}
